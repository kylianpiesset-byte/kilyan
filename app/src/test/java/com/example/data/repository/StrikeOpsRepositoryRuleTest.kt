package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.GameCatalog
import com.example.data.model.MatchPlayerState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class StrikeOpsRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueEmail(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(8)}@test.com"

    @Test
    fun ensurePlayerProfile_authenticatedOwner_createsAndObservesProfile() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("alice_profile"))
        val repo = StrikeOpsRepository(firestore)

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensurePlayerProfile(aliceUid, "VanguardAlice").getOrThrow()
        }
        assertEquals(aliceUid, created.userId)
        assertTrue(created.credits >= 500L)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePlayerProfile(aliceUid).first { it != null }
        }
        assertEquals(aliceUid, observed?.userId)
    }

    @Test
    fun purchaseShopItem_validBalance_deductsCurrencyAndAddsToInventory() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("alice_shop"))
        val repo = StrikeOpsRepository(firestore)
        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensurePlayerProfile(aliceUid, "VanguardAlice").getOrThrow()
        }

        val skin = GameCatalog.shopItems.first { it.id == "skin_crimson_spec" }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.purchaseShopItem(aliceUid, profile, skin).getOrThrow()
        }
        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePlayerProfile(aliceUid).first { it?.ownedItemIds?.contains(skin.id) == true }
        }
        assertTrue(updated!!.ownedItemIds.contains(skin.id))
        assertEquals(profile.credits - skin.price, updated.credits)
    }

    @Test
    fun squadLobby_hostCreatesAndFriendJoinsByCode() = runBlocking {
        val aliceEmail = uniqueEmail("alice_lobby")
        val bobEmail = uniqueEmail("bob_lobby")
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = StrikeOpsRepository(firestore)

        val lobby = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createLobby(aliceUid, "VanguardAlice", 3, "tdm").getOrThrow()
        }
        assertEquals(6, lobby.lobbyCode.length)
        assertTrue(lobby.memberUids.contains(aliceUid))

        val bobUid = signInTestUser(bobEmail)
        val bobRepo = StrikeOpsRepository(firestore)
        val joined = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinLobbyByCode(bobUid, "ShadowBob", 2, lobby.lobbyCode).getOrThrow()
        }
        assertTrue(joined.memberUids.contains(bobUid))
        assertTrue(joined.memberUids.contains(aliceUid))
    }

    @Test
    fun multiplayerMatch_participantsSyncAndNonParticipantDenied() = runBlocking {
        val aliceEmail = uniqueEmail("alice_match")
        val bobEmail = uniqueEmail("bob_match")
        val charlieEmail = uniqueEmail("charlie_match")

        val aliceUid = signInTestUser(aliceEmail)
        val bobUid = signInTestUser(bobEmail)
        signInTestUser(aliceEmail)
        val aliceRepo = StrikeOpsRepository(firestore)

        val participants = listOf(aliceUid, bobUid)
        val match = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createMatch(aliceUid, "domination", participants).getOrThrow()
        }

        val state = MatchPlayerState(
            userId = aliceUid,
            callsign = "VanguardAlice",
            team = "alpha",
            x = 4.5,
            y = 4.5,
            yaw = 0.5,
            health = 100L,
            kills = 1L,
            killPoints = 100L,
            participantUids = participants,
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.syncLocalPlayerState(match.id, participants, state, isInitialCreate = true).getOrThrow()
        }

        val players = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMatchPlayers(match.id, aliceUid).first { it.isNotEmpty() }
        }
        assertEquals(1, players.size)
        assertEquals(100L, players.first().killPoints)

        signInTestUser(charlieEmail)
        val charlieRepo = StrikeOpsRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                charlieRepo.observeMatch(match.id).first()
            }
            fail("Expected PERMISSION_DENIED for non-participant")
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
        }
    }

    @Test
    fun observePlayerProfile_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = StrikeOpsRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePlayerProfile("alice_unauth_test").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
