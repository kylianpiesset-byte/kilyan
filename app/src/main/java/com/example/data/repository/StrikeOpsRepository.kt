package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BattlePassTier
import com.example.data.model.CurrencyType
import com.example.data.model.GameCatalog
import com.example.data.model.ItemCategory
import com.example.data.model.MatchPlayerState
import com.example.data.model.MultiplayerMatch
import com.example.data.model.PlayerProfile
import com.example.data.model.ShopItem
import com.example.data.model.SquadLobby
import com.example.data.model.SquadMemberInfo
import com.example.data.model.StrikePointsPack
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StrikeOpsRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun usersCollection() = db.collection("users")
    private fun lobbiesCollection() = db.collection("lobbies")
    private fun matchesCollection() = db.collection("matches")

    fun observePlayerProfile(userId: String): Flow<PlayerProfile?> {
        val docRef = usersCollection().document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val currentUid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
                if (currentUid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun ensurePlayerProfile(userId: String, defaultCallsign: String): Result<PlayerProfile> {
        val docRef = usersCollection().document(userId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) return Result.success(existing)
            }
            val cleanCallsign = defaultCallsign
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .ifBlank { "Striker_${userId.take(4)}" }
                .take(20)
                .let { if (it.length < 3) "${it}Ops" else it }

            val payload = mapOf(
                "userId" to userId,
                "callsign" to cleanCallsign,
                "level" to 1L,
                "xp" to 0L,
                "credits" to 600L,
                "strikePoints" to 150L,
                "kills" to 0L,
                "deaths" to 0L,
                "assists" to 0L,
                "wins" to 0L,
                "matchesPlayed" to 0L,
                "scorestreaksCalled" to 0L,
                "ownedItemIds" to listOf("m4a1_tactical", "skin_m4_standard", "op_vanguard"),
                "claimedPassTiers" to emptyList<Long>(),
                "equippedPrimaryId" to "m4a1_tactical",
                "equippedPrimarySkinId" to "skin_m4_standard",
                "equippedOperatorSkinId" to "op_vanguard",
                "battlePassPremium" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            val created = docRef.get().await().toObject(
                PlayerProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: PlayerProfile(id = userId, userId = userId, callsign = cleanCallsign)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCallsign(userId: String, newCallsign: String): Result<Unit> {
        val docRef = usersCollection().document(userId)
        val trimmed = newCallsign.trim().take(24)
        if (trimmed.length < 3) {
            return Result.failure(IllegalArgumentException("L'indicatif doit contenir au moins 3 caractères."))
        }
        return try {
            docRef.update(
                mapOf(
                    "callsign" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun purchaseShopItem(userId: String, profile: PlayerProfile, item: ShopItem): Result<Unit> {
        val docRef = usersCollection().document(userId)
        if (profile.ownedItemIds.contains(item.id)) {
            return Result.failure(IllegalStateException("Objet déjà possédé."))
        }
        val currentBalance = when (item.currency) {
            CurrencyType.CREDITS -> profile.credits
            CurrencyType.STRIKE_POINTS -> profile.strikePoints
        }
        if (currentBalance < item.price) {
            return Result.failure(IllegalStateException("Solde insuffisant pour cet achat."))
        }

        val itemsToUnlock = buildSet {
            addAll(profile.ownedItemIds)
            add(item.id)
            addAll(item.includedItemIds)
        }.toList().take(100)

        val updates = mutableMapOf<String, Any>(
            "ownedItemIds" to itemsToUnlock,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        when (item.currency) {
            CurrencyType.CREDITS -> updates["credits"] = profile.credits - item.price
            CurrencyType.STRIKE_POINTS -> updates["strikePoints"] = profile.strikePoints - item.price
        }
        when (item.category) {
            ItemCategory.WEAPON -> updates["equippedPrimaryId"] = item.id
            ItemCategory.WEAPON_SKIN -> updates["equippedPrimarySkinId"] = item.id
            ItemCategory.OPERATOR -> updates["equippedOperatorSkinId"] = item.id
            ItemCategory.BUNDLE -> {
                item.includedItemIds.firstOrNull { it.startsWith("skin_") }?.let {
                    updates["equippedPrimarySkinId"] = it
                }
                item.includedItemIds.firstOrNull { it.startsWith("op_") }?.let {
                    updates["equippedOperatorSkinId"] = it
                }
            }
        }

        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun purchaseStrikePointsPack(userId: String, profile: PlayerProfile, pack: StrikePointsPack): Result<Unit> {
        val docRef = usersCollection().document(userId)
        return try {
            docRef.update(
                mapOf(
                    "strikePoints" to profile.strikePoints + pack.strikePoints,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun equipLoadout(
        userId: String,
        weaponId: String? = null,
        skinId: String? = null,
        operatorSkinId: String? = null,
    ): Result<Unit> {
        val docRef = usersCollection().document(userId)
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (weaponId != null) updates["equippedPrimaryId"] = weaponId
        if (skinId != null) updates["equippedPrimarySkinId"] = skinId
        if (operatorSkinId != null) updates["equippedOperatorSkinId"] = operatorSkinId
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun unlockBattlePassPremium(userId: String, profile: PlayerProfile, costSp: Long = 150L): Result<Unit> {
        val docRef = usersCollection().document(userId)
        if (profile.battlePassPremium) return Result.success(Unit)
        if (profile.strikePoints < costSp) {
            return Result.failure(IllegalStateException("Solde de Points Strike insuffisant."))
        }
        return try {
            docRef.update(
                mapOf(
                    "strikePoints" to profile.strikePoints - costSp,
                    "battlePassPremium" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun claimBattlePassTier(userId: String, profile: PlayerProfile, tier: BattlePassTier): Result<Unit> {
        val docRef = usersCollection().document(userId)
        val tierLong = tier.tier.toLong()
        if (profile.level < tierLong) {
            return Result.failure(IllegalStateException("Niveau ${tier.tier} requis pour ce palier."))
        }
        if (tier.isPremiumOnly && !profile.battlePassPremium) {
            return Result.failure(IllegalStateException("Passe de combat Premium requis."))
        }
        if (profile.claimedPassTiers.contains(tierLong)) {
            return Result.failure(IllegalStateException("Récompense déjà réclamée."))
        }

        val updatedTiers = (profile.claimedPassTiers + tierLong).distinct().take(50)
        val updatedItems = if (tier.itemRewardId != null) {
            (profile.ownedItemIds + tier.itemRewardId).distinct().take(100)
        } else {
            profile.ownedItemIds
        }

        return try {
            docRef.update(
                mapOf(
                    "claimedPassTiers" to updatedTiers,
                    "ownedItemIds" to updatedItems,
                    "credits" to profile.credits + tier.creditsReward,
                    "strikePoints" to profile.strikePoints + tier.strikePointsReward,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchResult(
        userId: String,
        profile: PlayerProfile,
        kills: Int,
        deaths: Int,
        assists: Int,
        scorestreaksCalled: Int,
        won: Boolean,
        xpEarned: Long,
        creditsEarned: Long,
    ): Result<Unit> {
        val docRef = usersCollection().document(userId)
        var newLevel = profile.level
        var newXp = profile.xp + xpEarned
        while (newXp >= newLevel * 1000L && newLevel < 999L) {
            newXp -= newLevel * 1000L
            newLevel += 1L
        }

        return try {
            docRef.update(
                mapOf(
                    "level" to newLevel,
                    "xp" to newXp,
                    "credits" to profile.credits + creditsEarned,
                    "kills" to profile.kills + kills.toLong(),
                    "deaths" to profile.deaths + deaths.toLong(),
                    "assists" to profile.assists + assists.toLong(),
                    "wins" to profile.wins + if (won) 1L else 0L,
                    "matchesPlayed" to profile.matchesPlayed + 1L,
                    "scorestreaksCalled" to profile.scorestreaksCalled + scorestreaksCalled.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Squad Lobby Operations ---

    suspend fun createLobby(userId: String, callsign: String, level: Int, mode: String): Result<SquadLobby> {
        val lobbyId = "lobby_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val code = generateLobbyCode()
        val member = SquadMemberInfo(userId, callsign, level, isReady = true, team = "alpha")
        val docRef = lobbiesCollection().document(lobbyId)
        val payload = mapOf(
            "hostId" to userId,
            "lobbyCode" to code,
            "mode" to mode,
            "status" to "waiting",
            "activeMatchId" to "",
            "memberUids" to listOf(userId),
            "memberRoster" to listOf(member.encode()),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            val created = docRef.get().await().toObject(
                SquadLobby::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: SquadLobby(
                id = lobbyId,
                hostId = userId,
                lobbyCode = code,
                mode = mode,
                status = "waiting",
                memberUids = listOf(userId),
                memberRoster = listOf(member.encode()),
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinLobbyByCode(userId: String, callsign: String, level: Int, code: String): Result<SquadLobby> {
        val normalized = code.trim().uppercase()
        if (!normalized.matches(Regex("^[A-Z0-9]{6}$"))) {
            return Result.failure(IllegalArgumentException("Le code de groupe doit contenir 6 caractères alphanumériques."))
        }
        return try {
            val snap = lobbiesCollection()
                .whereEqualTo("lobbyCode", normalized)
                .whereEqualTo("status", "waiting")
                .limit(1)
                .get()
                .await()

            val doc = snap.documents.firstOrNull()
                ?: return Result.failure(IllegalStateException("Aucun groupe actif trouvé avec le code $normalized."))
            val lobby = doc.toObject(SquadLobby::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Impossible de lire le groupe."))

            if (!lobby.memberUids.contains(userId)) {
                if (lobby.memberUids.size >= 10) {
                    return Result.failure(IllegalStateException("Le groupe est complet (10 joueurs max)."))
                }
                val newTeam = if (lobby.memberUids.size % 2 == 0) "alpha" else "bravo"
                val newMember = SquadMemberInfo(userId, callsign, level, isReady = false, team = newTeam)
                val newUids = (lobby.memberUids + userId).distinct().take(10)
                val newRoster = (lobby.memberRoster + newMember.encode()).take(10)
                lobbiesCollection().document(lobby.id).update(
                    mapOf(
                        "memberUids" to newUids,
                        "memberRoster" to newRoster,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                return Result.success(lobby.copy(memberUids = newUids, memberRoster = newRoster))
            }
            Result.success(lobby)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "lobbies")
            Result.failure(e)
        }
    }

    fun observeLobby(lobbyId: String): Flow<SquadLobby?> {
        val docRef = lobbiesCollection().document(lobbyId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    SquadLobby::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun toggleLobbyReady(lobby: SquadLobby, userId: String): Result<Unit> {
        val docRef = lobbiesCollection().document(lobby.id)
        val updatedRoster = lobby.parsedMembers.map { m ->
            if (m.uid == userId) m.copy(isReady = !m.isReady).encode() else m.encode()
        }
        return try {
            docRef.update(
                mapOf(
                    "memberRoster" to updatedRoster,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateLobbyMode(lobbyId: String, mode: String): Result<Unit> {
        val docRef = lobbiesCollection().document(lobbyId)
        return try {
            docRef.update(
                mapOf(
                    "mode" to mode,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun leaveLobby(lobby: SquadLobby, userId: String): Result<Unit> {
        val docRef = lobbiesCollection().document(lobby.id)
        return try {
            if (lobby.hostId == userId) {
                docRef.update(
                    mapOf(
                        "status" to "closed",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                val newUids = lobby.memberUids.filterNot { it == userId }
                val newRoster = lobby.parsedMembers.filterNot { it.uid == userId }.map { it.encode() }
                if (newUids.isEmpty()) {
                    docRef.delete().await()
                } else {
                    docRef.update(
                        mapOf(
                            "memberUids" to newUids,
                            "memberRoster" to newRoster,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Match & Real-time Multiplayer Sync ---

    suspend fun createMatch(
        hostId: String,
        mode: String,
        participantUids: List<String>,
        lobbyId: String? = null,
    ): Result<MultiplayerMatch> {
        val matchId = "match_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val matchRef = matchesCollection().document(matchId)
        val safeParticipants = (participantUids + hostId).distinct().take(10)
        val payload = mapOf(
            "hostId" to hostId,
            "mode" to mode,
            "status" to "active",
            "alphaScore" to 0L,
            "bravoScore" to 0L,
            "participantUids" to safeParticipants,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            matchRef.set(payload).await()
            if (lobbyId != null) {
                lobbiesCollection().document(lobbyId).update(
                    mapOf(
                        "status" to "in_match",
                        "activeMatchId" to matchId,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            val created = matchRef.get().await().toObject(
                MultiplayerMatch::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: MultiplayerMatch(
                id = matchId,
                hostId = hostId,
                mode = mode,
                status = "active",
                participantUids = safeParticipants,
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, matchRef.path)
            Result.failure(e)
        }
    }

    fun observeMatch(matchId: String): Flow<MultiplayerMatch?> {
        val docRef = matchesCollection().document(matchId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    MultiplayerMatch::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    fun observeMatchPlayers(matchId: String, currentUserId: String): Flow<List<MatchPlayerState>> {
        val playersCol = matchesCollection().document(matchId).collection("players")
        return playersCol
            .whereArrayContains("participantUids", currentUserId)
            .snapshots()
            .map { querySnap ->
                querySnap.toObjects(
                    MatchPlayerState::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, playersCol.path)
                throw error
            }
    }

    suspend fun syncLocalPlayerState(
        matchId: String,
        participantUids: List<String>,
        state: MatchPlayerState,
        isInitialCreate: Boolean,
    ): Result<Unit> {
        val playerRef = matchesCollection().document(matchId).collection("players").document(state.userId)
        return try {
            if (isInitialCreate) {
                val payload = mapOf(
                    "userId" to state.userId,
                    "callsign" to state.callsign.take(24).let { if (it.length < 3) "${it}Ops" else it },
                    "team" to state.team,
                    "x" to state.x,
                    "y" to state.y,
                    "yaw" to state.yaw,
                    "health" to state.health.coerceIn(0L, 100L),
                    "kills" to state.kills.coerceAtLeast(0L),
                    "deaths" to state.deaths.coerceAtLeast(0L),
                    "assists" to state.assists.coerceAtLeast(0L),
                    "killPoints" to state.killPoints.coerceAtLeast(0L),
                    "activeStreak" to state.activeStreak.take(32),
                    "participantUids" to participantUids.take(10),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                playerRef.set(payload).await()
            } else {
                playerRef.update(
                    mapOf(
                        "callsign" to state.callsign.take(24).let { if (it.length < 3) "${it}Ops" else it },
                        "team" to state.team,
                        "x" to state.x,
                        "y" to state.y,
                        "yaw" to state.yaw,
                        "health" to state.health.coerceIn(0L, 100L),
                        "kills" to state.kills.coerceAtLeast(0L),
                        "deaths" to state.deaths.coerceAtLeast(0L),
                        "assists" to state.assists.coerceAtLeast(0L),
                        "killPoints" to state.killPoints.coerceAtLeast(0L),
                        "activeStreak" to state.activeStreak.take(32),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isInitialCreate) OperationType.CREATE else OperationType.UPDATE, playerRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMatchScore(
        matchId: String,
        alphaScore: Long,
        bravoScore: Long,
        status: String,
    ): Result<Unit> {
        val docRef = matchesCollection().document(matchId)
        return try {
            docRef.update(
                mapOf(
                    "alphaScore" to alphaScore.coerceAtLeast(0L),
                    "bravoScore" to bravoScore.coerceAtLeast(0L),
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    private fun generateLobbyCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
