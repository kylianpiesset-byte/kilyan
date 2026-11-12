package com.example.ui.arena

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.model.WeaponSpec
import com.example.game.FpsArenaMap
import com.example.game.FpsHudState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun FpsArenaCanvas(
    hud: FpsHudState,
    weapon: WeaponSpec,
    weaponAccentColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val fov = when {
            hud.isAds -> 0.72f
            hud.isSprinting -> 1.18f
            else -> 1.04f
        }
        val bobY = sin(hud.walkBobPhase) * 9f
        val horizonY = h * 0.5f - hud.playerPitchOffset * h * 0.7f + hud.cameraHeightOffset * h * 0.25f

        // 1. Tactical Night Sky & Atmospheric Horizon
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF060911),
                    Color(0xFF10192A),
                    Color(0xFF192438),
                ),
                startY = 0f,
                endY = horizonY.coerceAtLeast(10f),
            ),
            topLeft = Offset.Zero,
            size = Size(w, horizonY.coerceIn(0f, h)),
        )

        // Perspective Grid Floor
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF141A24),
                    Color(0xFF0D1118),
                    Color(0xFF07090E),
                ),
                startY = horizonY.coerceIn(0f, h),
                endY = h,
            ),
            topLeft = Offset(0f, horizonY.coerceIn(0f, h)),
            size = Size(w, (h - horizonY).coerceAtLeast(0f)),
        )

        // Subtle perspective floor lines
        val floorLines = 8
        for (i in 1..floorLines) {
            val t = (i / floorLines.toFloat())
            val y = horizonY + (h - horizonY) * (t * t)
            if (y in 0f..h) {
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.08f * t),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f,
                )
            }
        }

        // 2. Raycasted 3D Arena Walls with Z-Buffer
        val numCols = 112
        val colWidth = w / numCols + 1f
        val zBuffer = FloatArray(numCols)

        for (col in 0 until numCols) {
            val cameraX = 2f * col / numCols.toFloat() - 1f
            val rayAngle = hud.playerYaw + atan2(cameraX * kotlin.math.tan(fov * 0.5f), 1f)
            val hit = FpsArenaMap.castRay(hud.playerX, hud.playerY, rayAngle, 26f)
            zBuffer[col] = hit.distance

            val wallHeight = (h / hit.distance) * (1.0f / fov)
            val top = horizonY - wallHeight * 0.5f
            val left = col * (w / numCols)

            val shade = (1f - (hit.distance / 22f)).coerceIn(0.18f, 1f) * if (hit.side == 1) 0.78f else 1f
            val baseColor = when (hit.tileType) {
                1 -> Color(0xFF283448)
                2 -> Color(0xFF3E3528)
                3 -> Color(0xFF163B4D)
                else -> Color(0xFF283448)
            }
            val litColor = Color(
                red = baseColor.red * shade,
                green = baseColor.green * shade,
                blue = baseColor.blue * shade,
                alpha = 1f,
            )

            drawRect(
                color = litColor,
                topLeft = Offset(left, top),
                size = Size(colWidth, wallHeight),
            )

            // Tactical edge trim on tiles
            if (hit.tileType == 3 && hit.wallX in 0.42f..0.58f) {
                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.65f * shade),
                    topLeft = Offset(left, top + wallHeight * 0.1f),
                    size = Size(colWidth, wallHeight * 0.8f),
                )
            } else if (hit.tileType == 2) {
                drawRect(
                    color = Color(0xFFFFB800).copy(alpha = 0.35f * shade),
                    topLeft = Offset(left, top + wallHeight * 0.12f),
                    size = Size(colWidth, wallHeight * 0.08f),
                )
            }
        }

        // 3. Project Domination Capture Zones & Sentry Turret & Combatants (sorted back to front)
        data class SpriteRender(val dist: Float, val drawAction: DrawScope.() -> Unit)
        val sprites = mutableListOf<SpriteRender>()

        // Capture zone beacons
        for (zone in hud.capturePoints) {
            val dx = zone.x - hud.playerX
            val dy = zone.y - hud.playerY
            val dist = hypot(dx, dy)
            if (dist < 0.5f) continue
            val angleTo = normalizeAngle(atan2(dy, dx) - hud.playerYaw)
            if (abs(angleTo) < fov * 0.75f) {
                val screenX = (0.5f + (angleTo / fov)) * w
                val colIdx = ((screenX / w) * numCols).toInt().coerceIn(0, numCols - 1)
                if (dist < zBuffer[colIdx] + 0.6f) {
                    val zoneColor = when (zone.ownerTeam) {
                        "alpha" -> Color(0xFF00E5FF)
                        "bravo" -> Color(0xFFFF3B4E)
                        else -> Color(0xFFFFB800)
                    }
                    sprites += SpriteRender(dist) {
                        val pillarH = (h / dist) * 0.9f
                        val pillarW = (h / dist) * 0.28f
                        drawRect(
                            color = zoneColor.copy(alpha = 0.28f),
                            topLeft = Offset(screenX - pillarW / 2f, horizonY - pillarH * 0.5f),
                            size = Size(pillarW, pillarH),
                        )
                        drawCircle(
                            color = zoneColor.copy(alpha = 0.85f),
                            radius = (pillarW * 0.45f).coerceIn(6f, 26f),
                            center = Offset(screenX, horizonY - pillarH * 0.35f),
                        )
                    }
                }
            }
        }

        // Sentry Turret sprite
        hud.turret?.let { t ->
            val dx = t.x - hud.playerX
            val dy = t.y - hud.playerY
            val dist = hypot(dx, dy)
            if (dist > 0.6f) {
                val angleTo = normalizeAngle(atan2(dy, dx) - hud.playerYaw)
                if (abs(angleTo) < fov * 0.75f) {
                    val screenX = (0.5f + (angleTo / fov)) * w
                    val colIdx = ((screenX / w) * numCols).toInt().coerceIn(0, numCols - 1)
                    if (dist < zBuffer[colIdx]) {
                        sprites += SpriteRender(dist) {
                            val sh = (h / dist) * 0.42f
                            val sw = sh * 0.75f
                            val baseY = horizonY + sh * 0.55f
                            drawRect(
                                color = Color(0xFFFFB800),
                                topLeft = Offset(screenX - sw * 0.4f, baseY - sh),
                                size = Size(sw * 0.8f, sh * 0.5f),
                            )
                            drawLine(
                                color = Color(0xFF00E5FF),
                                start = Offset(screenX - sw * 0.35f, baseY),
                                end = Offset(screenX, baseY - sh * 0.6f),
                                strokeWidth = 5f,
                            )
                            drawLine(
                                color = Color(0xFF00E5FF),
                                start = Offset(screenX + sw * 0.35f, baseY),
                                end = Offset(screenX, baseY - sh * 0.6f),
                                strokeWidth = 5f,
                            )
                        }
                    }
                }
            }
        }

        // Combatants (Allies & Enemies)
        for (c in hud.combatants) {
            if (!c.isAlive) continue
            val dx = c.x - hud.playerX
            val dy = c.y - hud.playerY
            val dist = hypot(dx, dy)
            if (dist < 0.45f) continue
            val angleTo = normalizeAngle(atan2(dy, dx) - hud.playerYaw)
            if (abs(angleTo) < fov * 0.7f) {
                val screenX = (0.5f + (angleTo / fov)) * w
                val colIdx = ((screenX / w) * numCols).toInt().coerceIn(0, numCols - 1)
                if (dist < zBuffer[colIdx]) {
                    sprites += SpriteRender(dist) {
                        val spriteH = (h / dist) * (0.86f / fov)
                        val spriteW = spriteH * 0.44f
                        val top = horizonY - spriteH * 0.44f
                        val isEnemy = c.team != "alpha"
                        val bodyColor = when {
                            c.hitFlashTimer > 0f -> Color.White
                            isEnemy -> Color(0xFF3A1620)
                            else -> Color(0xFF132D3F)
                        }
                        val accent = if (isEnemy) Color(0xFFFF3B4E) else Color(0xFF00E5FF)

                        // Legs
                        drawRect(
                            color = Color(0xFF1A1F29),
                            topLeft = Offset(screenX - spriteW * 0.32f, top + spriteH * 0.58f),
                            size = Size(spriteW * 0.26f, spriteH * 0.40f),
                        )
                        drawRect(
                            color = Color(0xFF1A1F29),
                            topLeft = Offset(screenX + spriteW * 0.06f, top + spriteH * 0.58f),
                            size = Size(spriteW * 0.26f, spriteH * 0.40f),
                        )
                        // Torso tactical vest
                        drawRect(
                            color = bodyColor,
                            topLeft = Offset(screenX - spriteW * 0.42f, top + spriteH * 0.22f),
                            size = Size(spriteW * 0.84f, spriteH * 0.38f),
                        )
                        // Shoulder accent strip
                        drawRect(
                            color = accent,
                            topLeft = Offset(screenX - spriteW * 0.42f, top + spriteH * 0.22f),
                            size = Size(spriteW * 0.84f, spriteH * 0.06f),
                        )
                        // Helmet + glowing tactical visor
                        drawCircle(
                            color = Color(0xFF232B38),
                            radius = spriteW * 0.26f,
                            center = Offset(screenX, top + spriteH * 0.12f),
                        )
                        drawRect(
                            color = accent,
                            topLeft = Offset(screenX - spriteW * 0.16f, top + spriteH * 0.09f),
                            size = Size(spriteW * 0.32f, spriteH * 0.045f),
                        )

                        // Overhead HP bar for damaged or targeted enemies
                        if (dist < 14f) {
                            val barW = spriteW.coerceIn(28f, 76f)
                            val barTop = top - 12f
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(screenX - barW / 2f, barTop),
                                size = Size(barW, 5f),
                            )
                            drawRect(
                                color = accent,
                                topLeft = Offset(screenX - barW / 2f, barTop),
                                size = Size(barW * (c.health / 100f), 5f),
                            )
                        }
                    }
                }
            }
        }

        sprites.sortedByDescending { it.dist }.forEach { it.drawAction(this) }

        // 4. Airstrike Shockwave Effect
        hud.airstrike?.let {
            val alpha = (it.remainingSeconds / 3.2f).coerceIn(0f, 1f) * 0.32f
            drawRect(color = Color(0xFFFFB800).copy(alpha = alpha))
        }

        // 5. First-Person Weapon Viewmodel
        if (!hud.isDead) {
            drawFirstPersonWeapon(
                w = w,
                h = h,
                hud = hud,
                weapon = weapon,
                accentColor = weaponAccentColor,
                bobY = bobY,
            )
        }

        // 6. Crosshair & Hitmarkers
        val cx = w / 2f
        val cy = h / 2f
        if (hud.isAds) {
            // Crisp Holographic ADS Sight
            drawCircle(
                color = weaponAccentColor.copy(alpha = 0.7f),
                radius = 34f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f),
            )
            drawCircle(
                color = Color(0xFFFF3B4E),
                radius = 3.5f,
                center = Offset(cx, cy),
            )
        } else {
            val spreadGap = if (hud.isSprinting) 22f else 11f + hud.recoilKick * 8f
            val tickLen = 10f
            val crossColor = Color.White.copy(alpha = 0.88f)
            drawLine(crossColor, Offset(cx - spreadGap - tickLen, cy), Offset(cx - spreadGap, cy), 2.5f)
            drawLine(crossColor, Offset(cx + spreadGap, cy), Offset(cx + spreadGap + tickLen, cy), 2.5f)
            drawLine(crossColor, Offset(cx, cy - spreadGap - tickLen), Offset(cx, cy - spreadGap), 2.5f)
            drawLine(crossColor, Offset(cx, cy + spreadGap), Offset(cx, cy + spreadGap + tickLen), 2.5f)
        }

        if (hud.hitmarkerTimer > 0f) {
            val hmColor = when {
                hud.hitmarkerKill -> Color(0xFFFF3B4E)
                hud.hitmarkerCrit -> Color(0xFFFFB800)
                else -> Color.White
            }
            val inner = 7f
            val outer = 19f
            drawLine(hmColor, Offset(cx - outer, cy - outer), Offset(cx - inner, cy - inner), 3.5f)
            drawLine(hmColor, Offset(cx + outer, cy - outer), Offset(cx + inner, cy - inner), 3.5f)
            drawLine(hmColor, Offset(cx - outer, cy + outer), Offset(cx - inner, cy + inner), 3.5f)
            drawLine(hmColor, Offset(cx + outer, cy + outer), Offset(cx + inner, cy + inner), 3.5f)
        }

        // 7. Damage & Low Health Vignette
        val lowHpAlpha = ((100 - hud.health) / 100f) * 0.35f
        val dmgAlpha = (hud.damageFlashTimer * 1.4f).coerceAtMost(0.45f)
        val totalVignette = (lowHpAlpha + dmgAlpha).coerceIn(0f, 0.55f)
        if (totalVignette > 0.01f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0xFFFF1E38).copy(alpha = totalVignette)),
                    center = Offset(cx, cy),
                    radius = maxOf(w, h) * 0.72f,
                )
            )
        }
    }
}

private fun DrawScope.drawFirstPersonWeapon(
    w: Float,
    h: Float,
    hud: FpsHudState,
    weapon: WeaponSpec,
    accentColor: Color,
    bobY: Float,
) {
    val adsLerp = if (hud.isAds) 1f else 0f
    val kick = hud.recoilKick * 24f
    val slideTilt = if (hud.isSliding) -28f else 0f

    val baseX = w * (0.74f - adsLerp * 0.24f)
    val baseY = h * (0.86f - adsLerp * 0.08f) + bobY + kick + if (hud.isReloading) 45f else 0f

    // Muzzle flash
    if (hud.muzzleFlashTimer > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF5B8), Color(0xFFFFB800).copy(alpha = 0.6f), Color.Transparent),
                center = Offset(baseX - 75f, baseY - 125f),
                radius = 75f,
            ),
            radius = 75f,
            center = Offset(baseX - 75f, baseY - 125f),
        )
    }

    // Receiver body
    val gunPath = Path().apply {
        moveTo(baseX - 95f + slideTilt, baseY - 120f)
        lineTo(baseX - 45f + slideTilt, baseY - 135f)
        lineTo(baseX + 120f, baseY + 55f)
        lineTo(baseX + 45f, baseY + 95f)
        close()
    }
    drawPath(gunPath, color = Color(0xFF1A2230))

    // Skin accent rail along upper receiver
    val accentPath = Path().apply {
        moveTo(baseX - 88f + slideTilt, baseY - 115f)
        lineTo(baseX - 52f + slideTilt, baseY - 128f)
        lineTo(baseX + 75f, baseY + 12f)
        lineTo(baseX + 52f, baseY + 26f)
        close()
    }
    drawPath(accentPath, color = accentColor.copy(alpha = 0.85f))

    // Holographic sight housing
    drawRect(
        color = Color(0xFF0E141D),
        topLeft = Offset(baseX - 58f + slideTilt, baseY - 148f),
        size = Size(34f, 22f),
    )
    drawRect(
        color = weapon.accentColor.copy(alpha = 0.55f),
        topLeft = Offset(baseX - 52f + slideTilt, baseY - 144f),
        size = Size(22f, 12f),
        style = Stroke(width = 2f),
    )
}

private fun normalizeAngle(a: Float): Float {
    var r = a
    val twoPi = (2.0 * PI).toFloat()
    while (r > PI) r -= twoPi
    while (r < -PI) r += twoPi
    return r
}
