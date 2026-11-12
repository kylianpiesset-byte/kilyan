package com.example.ui.arena

import android.content.res.Configuration
import android.view.InputDevice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GameCatalog
import com.example.data.model.MatchPlayerState
import com.example.data.model.PlayerProfile
import com.example.game.ArenaGameMode
import com.example.game.FpsArenaMap
import com.example.game.FpsEngineController
import com.example.game.FpsHudState
import com.example.game.PlatformInputMode
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SquadGreen
import com.example.ui.theme.StrikeAmber
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun FpsArenaScreen(
    profile: PlayerProfile,
    mode: ArenaGameMode,
    remotePlayers: List<MatchPlayerState>,
    onSyncTick: (FpsHudState) -> Unit,
    onMatchFinished: (FpsHudState) -> Unit,
    onExitEarly: () -> Unit,
) {
    BackHandler { onExitEarly() }

    val config = LocalConfiguration.current
    val initialMode = remember(config) {
        val hasHardKeyboard = config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        val hasMouse = InputDevice.getDeviceIds().any { id ->
            val dev = InputDevice.getDevice(id)
            dev != null && (dev.sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
        }
        if (hasHardKeyboard || hasMouse) PlatformInputMode.PC_KEYBOARD_MOUSE
        else PlatformInputMode.MOBILE_TOUCH
    }

    val weapon = remember(profile.equippedPrimaryId) {
        GameCatalog.weaponById(profile.equippedPrimaryId)
    }
    val weaponAccentColor = remember(profile.equippedPrimarySkinId) {
        GameCatalog.skinAccentColor(profile.equippedPrimarySkinId)
    }
    val weaponAccentHex = remember(profile.equippedPrimarySkinId) {
        when (profile.equippedPrimarySkinId) {
            "skin_cyber_flux" -> 0xFF00E5FF
            "skin_obsidian_gold" -> 0xFFFFB800
            "skin_crimson_spec" -> 0xFFFF3B4E
            else -> 0xFF94A3B8
        }
    }

    val engine = remember(mode) {
        FpsEngineController(
            playerCallsign = profile.callsign,
            weapon = weapon,
            weaponAccentHex = weaponAccentHex,
            mode = mode,
            initialInputMode = initialMode,
        )
    }

    val hud by engine.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val pressedKeys = remember { mutableSetOf<Key>() }

    // Push remote Firestore human players into the local arena simulation
    LaunchedEffect(remotePlayers, profile.userId) {
        engine.updateRemotePlayers(remotePlayers, profile.userId)
    }

    // 60 FPS Game Loop
    LaunchedEffect(engine) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = now
            engine.step(dt)
        }
    }

    // Periodic Firestore multiplayer position & score sync (every 280ms)
    LaunchedEffect(engine) {
        while (!hud.matchFinished) {
            delay(280L)
            onSyncTick(engine.state.value)
        }
    }

    // Notify completion once when match finishes
    var reportedEnd by remember { mutableStateOf(false) }
    LaunchedEffect(hud.matchFinished) {
        if (hud.matchFinished && !reportedEnd) {
            reportedEnd = true
            onMatchFinished(hud)
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun recomputeKeyboardMovement() {
        var fwd = 0f
        var strafe = 0f
        if (Key.W in pressedKeys || Key.Z in pressedKeys || Key.DirectionUp in pressedKeys) fwd += 1f
        if (Key.S in pressedKeys || Key.DirectionDown in pressedKeys) fwd -= 1f
        if (Key.A in pressedKeys || Key.Q in pressedKeys || Key.DirectionLeft in pressedKeys) strafe -= 1f
        if (Key.D in pressedKeys || Key.DirectionRight in pressedKeys) strafe += 1f
        engine.moveForwardInput = fwd.coerceIn(-1f, 1f)
        engine.moveStrafeInput = strafe.coerceIn(-1f, 1f)
        engine.isSprintInput = (Key.ShiftLeft in pressedKeys || Key.ShiftRight in pressedKeys)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .focusRequester(focusRequester)
            .focusTarget()
            .onPreviewKeyEvent { event ->
                val key = event.key
                if (event.type == KeyEventType.KeyDown) {
                    if (hud.inputMode != PlatformInputMode.PC_KEYBOARD_MOUSE &&
                        key in setOf(Key.W, Key.Z, Key.A, Key.Q, Key.S, Key.D, Key.Spacebar)
                    ) {
                        engine.setInputMode(PlatformInputMode.PC_KEYBOARD_MOUSE)
                    }
                    pressedKeys.add(key)
                    when (key) {
                        Key.Spacebar -> engine.triggerJump()
                        Key.C -> engine.triggerSlide()
                        Key.R -> engine.triggerReload()
                        Key.F -> engine.isFiringInput = true
                        Key.E -> engine.isAdsInput = true
                        Key.One -> engine.activateScorestreak("uav_drone")
                        Key.Two -> engine.activateScorestreak("sentry_turret")
                        Key.Three -> engine.activateScorestreak("airstrike")
                        else -> Unit
                    }
                    recomputeKeyboardMovement()
                    true
                } else if (event.type == KeyEventType.KeyUp) {
                    pressedKeys.remove(key)
                    when (key) {
                        Key.F -> engine.isFiringInput = false
                        Key.E -> engine.isAdsInput = false
                        else -> Unit
                    }
                    recomputeKeyboardMovement()
                    true
                } else false
            }
    ) {
        // 1. 3D Raycasted Arena Viewport + Camera Look Pointer Layer
        FpsArenaCanvas(
            hud = hud,
            weapon = weapon,
            weaponAccentColor = weaponAccentColor,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hud.inputMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var prevPos = down.position
                        if (hud.inputMode == PlatformInputMode.PC_KEYBOARD_MOUSE) {
                            engine.isFiringInput = true
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            if (hud.inputMode == PlatformInputMode.PC_KEYBOARD_MOUSE) {
                                engine.isAdsInput = event.buttons.isSecondaryPressed
                            }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                if (hud.inputMode == PlatformInputMode.PC_KEYBOARD_MOUSE) {
                                    engine.isFiringInput = false
                                    engine.isAdsInput = false
                                }
                                break
                            }
                            if (event.type == PointerEventType.Move) {
                                val delta = change.position - prevPos
                                prevPos = change.position
                                engine.applyLookDelta(delta.x * 0.0042f, delta.y * 0.0028f)
                                change.consume()
                            }
                        }
                    }
                }
        )

        // 2. Tactical HUD Overlay
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val isCompact = maxWidth < 680.dp

            // TOP BAR: Minimap + Scoreboard + KillFeed/Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Tactical Minimap Radar
                TacticalMinimap(
                    hud = hud,
                    modifier = Modifier.size(if (isCompact) 96.dp else 118.dp),
                )

                // Match Scoreboard & Domination Flags
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurface.copy(alpha = 0.86f),
                        border = BorderStroke(1.dp, TacticalBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "ALPHA ${hud.alphaScore}",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyberCyan,
                                fontWeight = FontWeight.Bold,
                            )
                            val secs = hud.timeRemainingSeconds.toInt()
                            val clock = "%02d:%02d".format(secs / 60, secs % 60)
                            Text(
                                text = clock,
                                style = MaterialTheme.typography.labelLarge,
                                color = StrikeAmber,
                            )
                            Text(
                                text = "${hud.bravoScore} BRAVO",
                                style = MaterialTheme.typography.titleMedium,
                                color = AlertCrimson,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (hud.capturePoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            hud.capturePoints.forEach { zone ->
                                val zColor = when (zone.ownerTeam) {
                                    "alpha" -> CyberCyan
                                    "bravo" -> AlertCrimson
                                    else -> StrikeAmber
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = zColor.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, zColor),
                                ) {
                                    Text(
                                        text = if (zone.isContested) "${zone.label}!" else zone.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = zColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (hud.bannerMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StrikeAmber.copy(alpha = 0.92f),
                        ) {
                            Text(
                                text = hud.bannerMessage!!,
                                style = MaterialTheme.typography.labelLarge,
                                color = ObsidianVoid,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Top-Right: Platform Switch + Exit + Kill Feed
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianCard.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val next = if (hud.inputMode == PlatformInputMode.MOBILE_TOUCH)
                                        PlatformInputMode.PC_KEYBOARD_MOUSE
                                    else PlatformInputMode.MOBILE_TOUCH
                                    engine.setInputMode(next)
                                }
                                .testTag("toggle_input_mode_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (hud.inputMode == PlatformInputMode.PC_KEYBOARD_MOUSE)
                                        Icons.Filled.Computer else Icons.Filled.Smartphone,
                                    contentDescription = "Changer de mode de contrôle",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = hud.inputMode.labelFr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AlertCrimson.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, AlertCrimson),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onExitEarly() }
                                .testTag("exit_match_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Quitter la partie",
                                tint = AlertCrimson,
                                modifier = Modifier.padding(6.dp).size(18.dp),
                            )
                        }
                    }

                    // Compact Kill Feed
                    hud.killFeed.take(3).forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianVoid.copy(alpha = 0.68f),
                        ) {
                            Text(
                                text = "${entry.killer} [${if (entry.isHeadshot) "HS" else entry.weaponName}] ${entry.victim}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isPlayerKiller) StrikeAmber else TextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // LEFT-CENTER: Kill Points & Scorestreaks Bar
            ScorestreakHudColumn(
                hud = hud,
                onActivate = { engine.activateScorestreak(it) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 24.dp),
            )

            // BOTTOM: Health, Ammo & Platform-Adaptive Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hud.inputMode == PlatformInputMode.MOBILE_TOUCH) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // Left Virtual Joystick
                        VirtualJoystickPad(
                            onMove = { fwd, strafe, sprint ->
                                engine.moveForwardInput = fwd
                                engine.moveStrafeInput = strafe
                                engine.isSprintInput = sprint
                            },
                        )

                        // Center Vitals & Ammo Pill
                        VitalsAndAmmoPill(hud = hud, weaponName = weapon.name)

                        // Right Tactical Action Buttons
                        MobileActionCluster(
                            hud = hud,
                            onFireChanged = { engine.isFiringInput = it },
                            onToggleAutoFire = { engine.toggleAutoFire() },
                            onAdsChanged = { engine.isAdsInput = it },
                            onJump = { engine.triggerJump() },
                            onSlide = { engine.triggerSlide() },
                            onReload = { engine.triggerReload() },
                        )
                    }
                } else {
                    // PC Keyboard & Mouse HUD
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurface.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, TacticalBorder),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ZQSD / WASD : Bouger  •  SHIFT : Sprint  •  C : Glissade",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "CLIC G / F : Tir  •  CLIC D / E : Visée  •  ESPACE : Saut  •  R : Recharger  •  1/2/3 : Bonus",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan,
                                )
                            }
                        }

                        VitalsAndAmmoPill(hud = hud, weaponName = weapon.name)
                    }
                }
            }
        }

        // 3. Respawn Overlay
        if (hud.isDead && !hud.matchFinished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AlertCrimson.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianCard.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, AlertCrimson),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "ÉLIMINÉ — SÉRIE RÉINITIALISÉE",
                            style = MaterialTheme.typography.titleLarge,
                            color = AlertCrimson,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Redéploiement dans ${"%.1f".format(hud.respawnCountdown)}s",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TacticalMinimap(
    hud: FpsHudState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurface.copy(alpha = 0.84f),
        border = BorderStroke(
            1.5.dp,
            if (hud.uavActiveSeconds > 0f) StrikeAmber else TacticalBorder,
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val cellW = size.width / FpsArenaMap.WIDTH
            val cellH = size.height / FpsArenaMap.HEIGHT

            // Walls
            for (y in 0 until FpsArenaMap.HEIGHT) {
                for (x in 0 until FpsArenaMap.WIDTH) {
                    val t = FpsArenaMap.grid[y][x]
                    if (t > 0) {
                        drawRect(
                            color = if (t == 3) Color(0xFF1E4D63) else Color(0xFF2B374D),
                            topLeft = Offset(x * cellW, y * cellH),
                            size = Size(cellW, cellH),
                        )
                    }
                }
            }

            // Domination Capture Points
            hud.capturePoints.forEach { z ->
                val zColor = when (z.ownerTeam) {
                    "alpha" -> CyberCyan
                    "bravo" -> AlertCrimson
                    else -> StrikeAmber
                }
                drawCircle(
                    color = zColor.copy(alpha = 0.45f),
                    radius = cellW * 1.1f,
                    center = Offset(z.x * cellW, z.y * cellH),
                )
            }

            // UAV Active Sweep Ring
            if (hud.uavActiveSeconds > 0f) {
                drawCircle(
                    color = StrikeAmber.copy(alpha = 0.45f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 1.8f),
                )
            }

            // Combatants
            hud.combatants.forEach { c ->
                if (!c.isAlive) return@forEach
                val isAlly = c.team == "alpha"
                val showEnemy = hud.uavActiveSeconds > 0f ||
                    hypot(c.x - hud.playerX, c.y - hud.playerY) < 6.5f
                if (isAlly || showEnemy) {
                    drawCircle(
                        color = if (isAlly) CyberCyan else AlertCrimson,
                        radius = 3.5f,
                        center = Offset(c.x * cellW, c.y * cellH),
                    )
                }
            }

            // Local Player Marker + Look Cone
            val px = hud.playerX * cellW
            val py = hud.playerY * cellH
            val dirLen = cellW * 2.2f
            val tip = Offset(px + cos(hud.playerYaw) * dirLen, py + sin(hud.playerYaw) * dirLen)
            drawLine(SquadGreen, Offset(px, py), tip, strokeWidth = 2.5f)
            drawCircle(SquadGreen, radius = 4.5f, center = Offset(px, py))
        }
    }
}

@Composable
private fun ScorestreakHudColumn(
    hud: FpsHudState,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = ObsidianSurface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, TacticalBorder),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).width(142.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("KILL POINTS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("${hud.killPoints} / 800", style = MaterialTheme.typography.labelSmall, color = StrikeAmber)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (hud.killPoints / 800f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = StrikeAmber,
                    trackColor = ObsidianElevatedBarTrack,
                )
            }
        }

        ScorestreakSlotButton(
            label = "300 • DRONE",
            activeText = if (hud.uavActiveSeconds > 0f) "${hud.uavActiveSeconds.toInt()}s" else null,
            isReady = hud.uavReady,
            icon = Icons.Filled.Radar,
            testTag = "streak_uav_button",
            onClick = { onActivate("uav_drone") },
        )
        ScorestreakSlotButton(
            label = "500 • TOURELLE",
            activeText = hud.turret?.let { "${it.remainingSeconds.toInt()}s" },
            isReady = hud.turretReady,
            icon = Icons.Filled.GpsFixed,
            testTag = "streak_turret_button",
            onClick = { onActivate("sentry_turret") },
        )
        ScorestreakSlotButton(
            label = "800 • FRAPPE",
            activeText = hud.airstrike?.let { "ACTIF" },
            isReady = hud.airstrikeReady,
            icon = Icons.Filled.RocketLaunch,
            testTag = "streak_airstrike_button",
            onClick = { onActivate("airstrike") },
        )
    }
}

private val ObsidianElevatedBarTrack = Color(0xFF222D40)

@Composable
private fun ScorestreakSlotButton(
    label: String,
    activeText: String?,
    isReady: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    val borderColor = when {
        isReady -> StrikeAmber
        activeText != null -> CyberCyan
        else -> TacticalBorder
    }
    val bgColor = when {
        isReady -> StrikeAmber.copy(alpha = 0.25f)
        activeText != null -> CyberCyan.copy(alpha = 0.2f)
        else -> ObsidianSurface.copy(alpha = 0.72f)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isReady, onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isReady) StrikeAmber else if (activeText != null) CyberCyan else TextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReady) StrikeAmber else TextPrimary,
                )
            }
            if (activeText != null) {
                Text(
                    text = activeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan,
                )
            }
        }
    }
}

@Composable
private fun VitalsAndAmmoPill(
    hud: FpsHudState,
    weaponName: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, TacticalBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.width(105.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("SANTÉ", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        "${hud.health} HP",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hud.health <= 35) AlertCrimson else SquadGreen,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (hud.health / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (hud.health <= 35) AlertCrimson else SquadGreen,
                    trackColor = ObsidianElevatedBarTrack,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (hud.isReloading) "RECHARGEMENT..." else "${hud.ammoInMag} / ${hud.reserveAmmo}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hud.ammoInMag <= 6) AlertCrimson else StrikeAmber,
                )
                Text(
                    text = "$weaponName • ${hud.kills}K/${hud.deaths}D",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun VirtualJoystickPad(
    onMove: (fwd: Float, strafe: Float, sprint: Boolean) -> Unit,
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = 110f

    Box(
        modifier = Modifier
            .size(124.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f, false)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f, false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val raw = knobOffset + dragAmount
                        val len = hypot(raw.x, raw.y)
                        val clamped = if (len > radiusPx) raw * (radiusPx / len) else raw
                        knobOffset = clamped
                        val normX = (clamped.x / radiusPx).coerceIn(-1f, 1f)
                        val normY = (-clamped.y / radiusPx).coerceIn(-1f, 1f)
                        onMove(normY, normX, normY > 0.82f)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = ObsidianSurface.copy(alpha = 0.65f),
                radius = size.minDimension * 0.48f,
                center = center,
            )
            drawCircle(
                color = CyberCyan.copy(alpha = 0.55f),
                radius = size.minDimension * 0.48f,
                center = center,
                style = Stroke(width = 2.5f),
            )
            drawCircle(
                color = StrikeAmber.copy(alpha = 0.9f),
                radius = 24f,
                center = center + knobOffset * 0.48f,
            )
        }
    }
}

@Composable
private fun MobileActionCluster(
    hud: FpsHudState,
    onFireChanged: (Boolean) -> Unit,
    onToggleAutoFire: () -> Unit,
    onAdsChanged: (Boolean) -> Unit,
    onJump: () -> Unit,
    onSlide: () -> Unit,
    onReload: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (hud.autoFireEnabled) SquadGreen.copy(alpha = 0.25f) else ObsidianCard.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, if (hud.autoFireEnabled) SquadGreen else TacticalBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleAutoFire)
                    .testTag("auto_fire_toggle"),
            ) {
                Text(
                    text = if (hud.autoFireEnabled) "TIR AUTO: ON" else "TIR AUTO: OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hud.autoFireEnabled) SquadGreen else TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            SmallTacticalButton(
                label = "GLISSER",
                onClick = onSlide,
                testTag = "slide_button",
            )
            SmallTacticalButton(
                icon = Icons.Filled.Refresh,
                contentDesc = "Recharger",
                onClick = onReload,
                testTag = "reload_button",
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTacticalButton(
                icon = Icons.Filled.KeyboardArrowUp,
                contentDesc = "Sauter",
                onClick = onJump,
                testTag = "jump_button",
            )

            // ADS Hold/Toggle Button
            Surface(
                shape = CircleShape,
                color = if (hud.isAds) CyberCyan.copy(alpha = 0.35f) else ObsidianCard.copy(alpha = 0.84f),
                border = BorderStroke(1.5.dp, CyberCyan),
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .clickable { onAdsChanged(!hud.isAds) }
                    .testTag("ads_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "Viser (ADS)",
                        tint = CyberCyan,
                    )
                }
            }

            // Primary Fire Hold Button
            Surface(
                shape = CircleShape,
                color = AlertCrimson.copy(alpha = 0.86f),
                border = BorderStroke(2.dp, StrikeAmber),
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onFireChanged(true)
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.none { it.pressed }) {
                                    onFireChanged(false)
                                    break
                                }
                            }
                        }
                    }
                    .testTag("fire_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Tirer",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallTacticalButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDesc: String? = null,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianCard.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, TacticalBorder),
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = contentDesc, tint = TextPrimary, modifier = Modifier.size(22.dp))
            } else if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }
        }
    }
}
