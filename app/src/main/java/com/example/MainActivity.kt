package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.PlayerProfile
import com.example.data.repository.StrikeOpsRepository
import com.example.game.ArenaGameMode
import com.example.ui.arena.FpsArenaScreen
import com.example.ui.armory.ArmoryScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.battlepass.BattlePassScreen
import com.example.ui.hub.HubScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.shop.ShopScreen
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StrikeAmber
import com.example.ui.theme.StrikeOpsTheme
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.StrikeOpsViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class HubTab(
    val route: String,
    val labelFr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    PLAY("play", "Jouer", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
    ARMORY("armory", "Armurerie", Icons.Filled.Build, Icons.Outlined.Build),
    SHOP("shop", "Boutique", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    PASS("battlepass", "Passe", Icons.Filled.MilitaryTech, Icons.Outlined.MilitaryTech),
    PROFILE("profile", "Profil", Icons.Filled.Person, Icons.Outlined.Person),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StrikeOpsTheme {
                AppNavigation()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            StrikeOpsRootShell(
                currentUserId = user.uid,
                defaultDisplayName = user.displayName ?: "Striker",
            )
        }
    }
}

@Composable
fun StrikeOpsRootShell(
    currentUserId: String,
    defaultDisplayName: String,
) {
    val viewModel: StrikeOpsViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StrikeOpsViewModel(StrikeOpsRepository(db), currentUserId, defaultDisplayName)
            }
        },
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val activeLobby by viewModel.activeLobby.collectAsStateWithLifecycle()
    val activeMatchId by viewModel.activeMatchId.collectAsStateWithLifecycle()
    val activeMatch by viewModel.activeMatch.collectAsStateWithLifecycle()
    val remotePlayers by viewModel.remoteMatchPlayers.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val lastMatchSummary by viewModel.lastMatchSummary.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    // Automatically deploy squad members when host starts the match
    LaunchedEffect(activeLobby?.status, activeLobby?.activeMatchId) {
        val lobby = activeLobby
        if (lobby != null && lobby.status == "in_match" && lobby.activeMatchId.isNotBlank()) {
            viewModel.joinMatchIfLobbyStarted(lobby.activeMatchId)
        }
    }

    when (val state = profileState) {
        UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(ObsidianVoid),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = StrikeAmber)
                    Text("SYNCHRONISATION DU PROFIL...", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().background(ObsidianVoid).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AlertCrimson,
                )
            }
        }
        is UiState.Success -> {
            val profile = state.data
            if (activeMatchId != null) {
                val matchMode = when (activeMatch?.mode) {
                    "domination" -> ArenaGameMode.DOMINATION
                    "tdm" -> ArenaGameMode.TDM
                    else -> selectedMode
                }
                FpsArenaScreen(
                    profile = profile,
                    mode = matchMode,
                    remotePlayers = remotePlayers,
                    onSyncTick = { viewModel.syncMatchTick(it) },
                    onMatchFinished = { viewModel.completeMatch(it) },
                    onExitEarly = { viewModel.exitMatchEarly() },
                )
            } else {
                var selectedTab by rememberSaveable { mutableStateOf(HubTab.PLAY) }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().background(ObsidianVoid),
                ) {
                    val useRail = maxWidth >= 720.dp

                    Scaffold(
                        containerColor = ObsidianVoid,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        topBar = {
                            HubTopCurrencyBar(profile = profile)
                        },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = ObsidianSurface,
                                    windowInsets = WindowInsets.navigationBars,
                                ) {
                                    HubTab.entries.forEach { tab ->
                                        val selected = tab == selectedTab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.labelFr,
                                                )
                                            },
                                            label = {
                                                Text(tab.labelFr, style = MaterialTheme.typography.labelSmall)
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = StrikeAmber,
                                                selectedTextColor = StrikeAmber,
                                                indicatorColor = StrikeAmber.copy(alpha = 0.18f),
                                                unselectedIconColor = TextMuted,
                                                unselectedTextColor = TextMuted,
                                            ),
                                            modifier = Modifier.testTag("nav_${tab.route}"),
                                        )
                                    }
                                }
                            }
                        },
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            if (useRail) {
                                NavigationRail(
                                    containerColor = ObsidianSurface,
                                    modifier = Modifier.fillMaxHeight(),
                                ) {
                                    HubTab.entries.forEach { tab ->
                                        val selected = tab == selectedTab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.labelFr,
                                                )
                                            },
                                            label = { Text(tab.labelFr, style = MaterialTheme.typography.labelSmall) },
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = StrikeAmber,
                                                selectedTextColor = StrikeAmber,
                                                indicatorColor = StrikeAmber.copy(alpha = 0.18f),
                                                unselectedIconColor = TextMuted,
                                                unselectedTextColor = TextMuted,
                                            ),
                                            modifier = Modifier.testTag("rail_${tab.route}"),
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                when (selectedTab) {
                                    HubTab.PLAY -> HubScreen(
                                        profile = profile,
                                        selectedMode = selectedMode,
                                        activeLobby = activeLobby,
                                        lastMatchSummary = lastMatchSummary,
                                        isBusy = isBusy,
                                        onSelectMode = { viewModel.selectMode(it) },
                                        onCreateLobby = { viewModel.createSquadLobby() },
                                        onJoinLobbyByCode = { viewModel.joinSquadLobby(it) },
                                        onToggleReady = { viewModel.toggleReady() },
                                        onLeaveLobby = { viewModel.leaveSquadLobby() },
                                        onLaunchMatch = { viewModel.launchMatch() },
                                        onDismissSummary = { viewModel.clearLastMatchSummary() },
                                        onOpenArmory = { selectedTab = HubTab.ARMORY },
                                    )
                                    HubTab.ARMORY -> ArmoryScreen(
                                        profile = profile,
                                        onEquipWeapon = { viewModel.equipWeapon(it) },
                                        onEquipSkin = { viewModel.equipWeaponSkin(it) },
                                        onEquipOperator = { viewModel.equipOperator(it) },
                                        onGoToShop = { selectedTab = HubTab.SHOP },
                                    )
                                    HubTab.SHOP -> ShopScreen(
                                        profile = profile,
                                        isBusy = isBusy,
                                        onPurchaseItem = { viewModel.purchaseShopItem(it) },
                                        onPurchasePack = { viewModel.purchaseStrikePointsPack(it) },
                                    )
                                    HubTab.PASS -> BattlePassScreen(
                                        profile = profile,
                                        isBusy = isBusy,
                                        onUnlockPremium = { viewModel.unlockBattlePassPremium() },
                                        onClaimTier = { viewModel.claimBattlePassTier(it) },
                                    )
                                    HubTab.PROFILE -> ProfileScreen(
                                        profile = profile,
                                        isBusy = isBusy,
                                        onUpdateCallsign = { viewModel.updateCallsign(it) },
                                        onSignOut = { signOutUser(credentialManager, scope) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubTopCurrencyBar(profile: PlayerProfile) {
    Surface(
        color = ObsidianSurface,
        border = BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.let { WindowInsets(top = 0.dp) }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StrikeAmber.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, StrikeAmber),
                ) {
                    Text(
                        text = "NIV ${profile.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = StrikeAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Column {
                    Text(
                        text = "STRIKE OPS",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = profile.callsign.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                    )
                }
            }

            // Top-Right Currencies (Free Credits + Premium Strike Points)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyBadge(label = "CR", amount = profile.credits, color = StrikeAmber, testTag = "credits_badge")
                CurrencyBadge(label = "SP", amount = profile.strikePoints, color = CyberCyan, testTag = "strike_points_badge")
            }
        }
    }
}

@Composable
private fun CurrencyBadge(
    label: String,
    amount: Long,
    color: androidx.compose.ui.graphics.Color,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        modifier = Modifier.testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text("$amount", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}
