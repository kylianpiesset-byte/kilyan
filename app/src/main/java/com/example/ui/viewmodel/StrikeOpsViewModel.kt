package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BattlePassTier
import com.example.data.model.MatchPlayerState
import com.example.data.model.MultiplayerMatch
import com.example.data.model.PlayerProfile
import com.example.data.model.ShopItem
import com.example.data.model.SquadLobby
import com.example.data.model.StrikePointsPack
import com.example.data.repository.StrikeOpsRepository
import com.example.game.ArenaGameMode
import com.example.game.FpsHudState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class StrikeOpsViewModel(
    private val repository: StrikeOpsRepository,
    val currentUserId: String,
    private val defaultDisplayName: String,
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(ArenaGameMode.TDM)
    val selectedMode: StateFlow<ArenaGameMode> = _selectedMode.asStateFlow()

    private val _activeLobbyId = MutableStateFlow<String?>(null)
    private val _activeMatchId = MutableStateFlow<String?>(null)
    val activeMatchId: StateFlow<String?> = _activeMatchId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _lastMatchSummary = MutableStateFlow<MatchSummary?>(null)
    val lastMatchSummary: StateFlow<MatchSummary?> = _lastMatchSummary.asStateFlow()

    val profileState: StateFlow<UiState<PlayerProfile>> = repository.observePlayerProfile(currentUserId)
        .map<PlayerProfile?, UiState<PlayerProfile>> { profile ->
            if (profile != null) UiState.Success(profile) else UiState.Loading
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing player profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Erreur de chargement du profil"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeLobby: StateFlow<SquadLobby?> = _activeLobbyId
        .flatMapLatest { lobbyId ->
            if (lobbyId == null) flowOf(null)
            else repository.observeLobby(lobbyId).catch { err ->
                Log.w(TAG, "Error observing lobby", err)
                emit(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMatch: StateFlow<MultiplayerMatch?> = _activeMatchId
        .flatMapLatest { matchId ->
            if (matchId == null) flowOf(null)
            else repository.observeMatch(matchId).catch { err ->
                Log.w(TAG, "Error observing match", err)
                emit(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val remoteMatchPlayers: StateFlow<List<MatchPlayerState>> = _activeMatchId
        .flatMapLatest { matchId ->
            if (matchId == null) flowOf(emptyList())
            else repository.observeMatchPlayers(matchId, currentUserId).catch { err ->
                Log.w(TAG, "Error observing match players", err)
                emit(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            repository.ensurePlayerProfile(currentUserId, defaultDisplayName)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearLastMatchSummary() {
        _lastMatchSummary.value = null
    }

    fun selectMode(mode: ArenaGameMode) {
        _selectedMode.value = mode
        val lobby = activeLobby.value
        if (lobby != null && lobby.hostId == currentUserId) {
            viewModelScope.launch {
                repository.updateLobbyMode(lobby.id, mode.id)
            }
        }
    }

    fun updateCallsign(newCallsign: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateCallsign(currentUserId, newCallsign)
                .onSuccess { _statusBanner.value = "Indicatif mis à jour : $newCallsign" }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun purchaseShopItem(item: ShopItem) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.purchaseShopItem(currentUserId, profile, item)
                .onSuccess { _statusBanner.value = "${item.name} débloqué et équipé !" }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun purchaseStrikePointsPack(pack: StrikePointsPack) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.purchaseStrikePointsPack(currentUserId, profile, pack)
                .onSuccess { _statusBanner.value = "+${pack.strikePoints} Points Strike ajoutés !" }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun equipWeapon(weaponId: String) {
        viewModelScope.launch {
            repository.equipLoadout(currentUserId, weaponId = weaponId)
                .onSuccess { _statusBanner.value = "Arme principale équipée" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun equipWeaponSkin(skinId: String) {
        viewModelScope.launch {
            repository.equipLoadout(currentUserId, skinId = skinId)
                .onSuccess { _statusBanner.value = "Camouflage équipé" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun equipOperator(operatorId: String) {
        viewModelScope.launch {
            repository.equipLoadout(currentUserId, operatorSkinId = operatorId)
                .onSuccess { _statusBanner.value = "Opérateur déployé" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun unlockBattlePassPremium() {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.unlockBattlePassPremium(currentUserId, profile)
                .onSuccess { _statusBanner.value = "Passe de combat Premium activé !" }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun claimBattlePassTier(tier: BattlePassTier) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.claimBattlePassTier(currentUserId, profile, tier)
                .onSuccess { _statusBanner.value = "Palier ${tier.tier} réclamé : ${tier.rewardTitle}" }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun createSquadLobby() {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createLobby(currentUserId, profile.callsign, profile.level.toInt(), _selectedMode.value.id)
                .onSuccess { lobby ->
                    _activeLobbyId.value = lobby.id
                    _statusBanner.value = "Groupe créé • Code : ${lobby.lobbyCode}"
                }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun joinSquadLobby(code: String) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.joinLobbyByCode(currentUserId, profile.callsign, profile.level.toInt(), code)
                .onSuccess { lobby ->
                    _activeLobbyId.value = lobby.id
                    _statusBanner.value = "Escouade rejointe (${lobby.lobbyCode})"
                }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun toggleReady() {
        val lobby = activeLobby.value ?: return
        viewModelScope.launch {
            repository.toggleLobbyReady(lobby, currentUserId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun leaveSquadLobby() {
        val lobby = activeLobby.value ?: return
        viewModelScope.launch {
            repository.leaveLobby(lobby, currentUserId)
            _activeLobbyId.value = null
        }
    }

    fun joinMatchIfLobbyStarted(matchId: String) {
        if (matchId.isNotBlank() && _activeMatchId.value != matchId) {
            _activeMatchId.value = matchId
        }
    }

    fun launchMatch() {
        val lobby = activeLobby.value
        val participants = lobby?.memberUids ?: listOf(currentUserId)
        val mode = lobby?.mode ?: _selectedMode.value.id
        viewModelScope.launch {
            _isBusy.value = true
            repository.createMatch(currentUserId, mode, participants, lobby?.id)
                .onSuccess { match ->
                    _activeMatchId.value = match.id
                    val profile = (profileState.value as? UiState.Success)?.data
                    val initialState = MatchPlayerState(
                        userId = currentUserId,
                        callsign = profile?.callsign ?: "Striker",
                        team = "alpha",
                        x = 2.5,
                        y = 2.5,
                        yaw = 0.78,
                        health = 100L,
                        participantUids = match.participantUids,
                    )
                    repository.syncLocalPlayerState(match.id, match.participantUids, initialState, isInitialCreate = true)
                }
                .onFailure { _statusBanner.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun syncMatchTick(hud: FpsHudState) {
        val match = activeMatch.value ?: return
        val profile = (profileState.value as? UiState.Success)?.data
        val activeStreak = when {
            hud.airstrike != null -> "airstrike"
            hud.turret != null -> "sentry_turret"
            hud.uavActiveSeconds > 0f -> "uav_drone"
            else -> ""
        }
        viewModelScope.launch {
            val state = MatchPlayerState(
                userId = currentUserId,
                callsign = profile?.callsign ?: "Striker",
                team = "alpha",
                x = hud.playerX.toDouble(),
                y = hud.playerY.toDouble(),
                yaw = hud.playerYaw.toDouble(),
                health = hud.health.toLong(),
                kills = hud.kills.toLong(),
                deaths = hud.deaths.toLong(),
                assists = hud.assists.toLong(),
                killPoints = hud.killPoints.toLong(),
                activeStreak = activeStreak,
                participantUids = match.participantUids,
            )
            repository.syncLocalPlayerState(match.id, match.participantUids, state, isInitialCreate = false)
            if (match.hostId == currentUserId && match.status == "active") {
                repository.updateMatchScore(
                    matchId = match.id,
                    alphaScore = hud.alphaScore.toLong(),
                    bravoScore = hud.bravoScore.toLong(),
                    status = if (hud.matchFinished) "finished" else "active",
                )
            }
        }
    }

    fun completeMatch(hud: FpsHudState) {
        val match = activeMatch.value
        val profile = (profileState.value as? UiState.Success)?.data
        val xpEarned = (hud.kills * 120L + hud.assists * 50L + if (hud.isVictory) 350L else 150L).coerceAtLeast(100L)
        val creditsEarned = (hud.kills * 25L + if (hud.isVictory) 120L else 50L).coerceAtLeast(50L)

        _lastMatchSummary.value = MatchSummary(
            mode = hud.mode,
            victory = hud.isVictory,
            alphaScore = hud.alphaScore,
            bravoScore = hud.bravoScore,
            kills = hud.kills,
            deaths = hud.deaths,
            assists = hud.assists,
            scorestreaksCalled = hud.scorestreaksCalled,
            xpEarned = xpEarned,
            creditsEarned = creditsEarned,
        )

        viewModelScope.launch {
            if (match != null && match.hostId == currentUserId && match.status == "active") {
                repository.updateMatchScore(
                    matchId = match.id,
                    alphaScore = hud.alphaScore.toLong(),
                    bravoScore = hud.bravoScore.toLong(),
                    status = "finished",
                )
            }
            if (profile != null) {
                repository.recordMatchResult(
                    userId = currentUserId,
                    profile = profile,
                    kills = hud.kills,
                    deaths = hud.deaths,
                    assists = hud.assists,
                    scorestreaksCalled = hud.scorestreaksCalled,
                    won = hud.isVictory,
                    xpEarned = xpEarned,
                    creditsEarned = creditsEarned,
                )
            }
            _activeMatchId.value = null
        }
    }

    fun exitMatchEarly() {
        _activeMatchId.value = null
    }

    private companion object {
        const val TAG = "StrikeOpsVM"
    }
}

data class MatchSummary(
    val mode: ArenaGameMode,
    val victory: Boolean,
    val alphaScore: Int,
    val bravoScore: Int,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val scorestreaksCalled: Int,
    val xpEarned: Long,
    val creditsEarned: Long,
)
