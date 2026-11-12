package com.example.ui.hub

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GameCatalog
import com.example.data.model.PlayerProfile
import com.example.data.model.SquadLobby
import com.example.game.ArenaGameMode
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SquadGreen
import com.example.ui.theme.StrikeAmber
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.MatchSummary

@Composable
fun HubScreen(
    profile: PlayerProfile,
    selectedMode: ArenaGameMode,
    activeLobby: SquadLobby?,
    lastMatchSummary: MatchSummary?,
    isBusy: Boolean,
    onSelectMode: (ArenaGameMode) -> Unit,
    onCreateLobby: () -> Unit,
    onJoinLobbyByCode: (String) -> Unit,
    onToggleReady: () -> Unit,
    onLeaveLobby: () -> Unit,
    onLaunchMatch: () -> Unit,
    onDismissSummary: () -> Unit,
    onOpenArmory: () -> Unit,
) {
    val equippedWeapon = remember(profile.equippedPrimaryId) {
        GameCatalog.weaponById(profile.equippedPrimaryId)
    }
    val skinName = remember(profile.equippedPrimarySkinId) {
        GameCatalog.skinDisplayName(profile.equippedPrimarySkinId)
    }
    val skinAccent = remember(profile.equippedPrimarySkinId) {
        GameCatalog.skinAccentColor(profile.equippedPrimarySkinId)
    }
    val operatorName = remember(profile.equippedOperatorSkinId) {
        GameCatalog.operatorDisplayName(profile.equippedOperatorSkinId)
    }

    var joinCodeInput by remember { mutableStateOf("") }

    val pulseTransition = rememberInfiniteTransition(label = "operator_pulse")
    val operatorScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OperatorShowcaseCard(
                    profile = profile,
                    operatorName = operatorName,
                    weaponName = equippedWeapon.name,
                    skinName = skinName,
                    skinAccent = skinAccent,
                    operatorScale = operatorScale,
                    onOpenArmory = onOpenArmory,
                    modifier = Modifier.weight(1.05f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ModeSelectorCard(selectedMode = selectedMode, onSelectMode = onSelectMode)
                    SquadLobbyCard(
                        profile = profile,
                        activeLobby = activeLobby,
                        joinCodeInput = joinCodeInput,
                        onJoinCodeChange = { joinCodeInput = it.uppercase().take(6) },
                        isBusy = isBusy,
                        onCreateLobby = onCreateLobby,
                        onJoinLobby = { onJoinLobbyByCode(joinCodeInput) },
                        onToggleReady = onToggleReady,
                        onLeaveLobby = onLeaveLobby,
                        onLaunchMatch = onLaunchMatch,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OperatorShowcaseCard(
                    profile = profile,
                    operatorName = operatorName,
                    weaponName = equippedWeapon.name,
                    skinName = skinName,
                    skinAccent = skinAccent,
                    operatorScale = operatorScale,
                    onOpenArmory = onOpenArmory,
                    modifier = Modifier.fillMaxWidth(),
                )
                ModeSelectorCard(selectedMode = selectedMode, onSelectMode = onSelectMode)
                SquadLobbyCard(
                    profile = profile,
                    activeLobby = activeLobby,
                    joinCodeInput = joinCodeInput,
                    onJoinCodeChange = { joinCodeInput = it.uppercase().take(6) },
                    isBusy = isBusy,
                    onCreateLobby = onCreateLobby,
                    onJoinLobby = { onJoinLobbyByCode(joinCodeInput) },
                    onToggleReady = onToggleReady,
                    onLeaveLobby = onLeaveLobby,
                    onLaunchMatch = onLaunchMatch,
                )
            }
        }
    }

    if (lastMatchSummary != null) {
        AlertDialog(
            onDismissRequest = onDismissSummary,
            containerColor = ObsidianCard,
            title = {
                Text(
                    text = if (lastMatchSummary.victory) "VICTOIRE DE L'ESCOUADE" else "FIN DE MISSION",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (lastMatchSummary.victory) StrikeAmber else AlertCrimson,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${lastMatchSummary.mode.labelFr} • Alpha ${lastMatchSummary.alphaScore} - ${lastMatchSummary.bravoScore} Bravo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberCyan,
                    )
                    Text(
                        text = "Éliminations : ${lastMatchSummary.kills}  |  Morts : ${lastMatchSummary.deaths}  |  Assistances : ${lastMatchSummary.assists}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Séries de points déployées : ${lastMatchSummary.scorestreaksCalled}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianElevated,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("+${lastMatchSummary.xpEarned} XP", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                            Text("+${lastMatchSummary.creditsEarned} CR", style = MaterialTheme.typography.labelLarge, color = StrikeAmber)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissSummary,
                    colors = ButtonDefaults.buttonColors(containerColor = StrikeAmber, contentColor = ObsidianVoid),
                ) {
                    Text("CONTINUER", fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun OperatorShowcaseCard(
    profile: PlayerProfile,
    operatorName: String,
    weaponName: String,
    skinName: String,
    skinAccent: Color,
    operatorScale: Float,
    onOpenArmory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(320.dp),
        shape = RoundedCornerShape(16.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, TacticalBorder),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_arena_nightfall),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.32f),
            )
            Image(
                painter = painterResource(id = R.drawable.img_operator_vanguard),
                contentDescription = "Opérateur équipé : $operatorName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = operatorScale, scaleY = operatorScale),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ObsidianVoid.copy(alpha = 0.25f),
                                ObsidianVoid.copy(alpha = 0.94f),
                            )
                        )
                    )
            )

            // Top-left operator badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ObsidianVoid.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f)),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("OPÉRATEUR ACTIF", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    Text(operatorName.uppercase(), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
            }

            // Bottom Equipped Loadout Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = profile.callsign.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = StrikeAmber,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(skinAccent)
                        )
                        Text(
                            text = "$weaponName • $skinName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenArmory,
                    border = BorderStroke(1.dp, CyberCyan),
                    modifier = Modifier.testTag("hub_armory_shortcut"),
                ) {
                    Text("ARMURERIE", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                }
            }
        }
    }
}

@Composable
private fun ModeSelectorCard(
    selectedMode: ArenaGameMode,
    onSelectMode: (ArenaGameMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, TacticalBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MODE DE COMBAT", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ArenaGameMode.entries.forEach { mode ->
                    val selected = mode == selectedMode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) StrikeAmber.copy(alpha = 0.18f) else ObsidianElevated,
                        border = BorderStroke(1.5.dp, if (selected) StrikeAmber else TacticalBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectMode(mode) }
                            .testTag("mode_${mode.id}_card"),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = if (mode == ArenaGameMode.DOMINATION) Icons.Filled.Flag else Icons.Filled.Groups,
                                contentDescription = mode.labelFr,
                                tint = if (selected) StrikeAmber else TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = mode.labelFr,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) StrikeAmber else TextPrimary,
                                )
                                Text(
                                    text = if (mode == ArenaGameMode.DOMINATION) "Zones A/B/C • 200 pts" else "5v5 • Objectif 3000 pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquadLobbyCard(
    profile: PlayerProfile,
    activeLobby: SquadLobby?,
    joinCodeInput: String,
    onJoinCodeChange: (String) -> Unit,
    isBusy: Boolean,
    onCreateLobby: () -> Unit,
    onJoinLobby: () -> Unit,
    onToggleReady: () -> Unit,
    onLeaveLobby: () -> Unit,
    onLaunchMatch: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, TacticalBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = CyberCyan)
                    Text("GROUPE D'ESCOUADE (LOBBY)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                if (activeLobby != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CyberCyan.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, CyberCyan),
                    ) {
                        Text(
                            text = "CODE: ${activeLobby.lobbyCode}",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (activeLobby == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = onJoinCodeChange,
                        singleLine = true,
                        label = { Text("Code ami (6 car.)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = TacticalBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                        modifier = Modifier.weight(1f).testTag("join_lobby_code_input"),
                    )
                    Button(
                        onClick = onJoinLobby,
                        enabled = !isBusy && joinCodeInput.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                        modifier = Modifier.height(52.dp).testTag("join_lobby_button"),
                    ) {
                        Text("REJOINDRE", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onCreateLobby,
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier.weight(1f).height(52.dp).testTag("create_lobby_button"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CRÉER UN GROUPE", color = CyberCyan)
                    }
                    Button(
                        onClick = onLaunchMatch,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = StrikeAmber, contentColor = ObsidianVoid),
                        modifier = Modifier.weight(1f).height(52.dp).testTag("quick_deploy_button"),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("JOUER", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeLobby.parsedMembers.forEach { member ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, if (member.isReady) SquadGreen.copy(alpha = 0.5f) else TacticalBorder),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = member.callsign + if (member.uid == activeLobby.hostId) " (CHEF)" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = "Niveau ${member.level} • Équipe ${member.team.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (member.isReady) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = if (member.isReady) SquadGreen else StrikeAmber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = if (member.isReady) "PRÊT" else "ATTENTE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (member.isReady) SquadGreen else StrikeAmber,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onLeaveLobby,
                        border = BorderStroke(1.dp, AlertCrimson),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("QUITTER", color = AlertCrimson)
                    }
                    OutlinedButton(
                        onClick = onToggleReady,
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_ready_button"),
                    ) {
                        Text("PRÊT / ATTENTE", color = CyberCyan)
                    }
                    if (activeLobby.hostId == profile.userId) {
                        Button(
                            onClick = onLaunchMatch,
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = StrikeAmber, contentColor = ObsidianVoid),
                            modifier = Modifier.weight(1.2f).height(48.dp).testTag("launch_lobby_match_button"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LANCER", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
