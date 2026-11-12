package com.example.game

import com.example.data.model.GameCatalog
import com.example.data.model.MatchPlayerState
import com.example.data.model.WeaponSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class FpsEngineController(
    private val playerCallsign: String,
    private val weapon: WeaponSpec,
    private val weaponAccentHex: Long,
    mode: ArenaGameMode,
    initialInputMode: PlatformInputMode,
) {
    private val _state = MutableStateFlow(createInitialState(mode, initialInputMode))
    val state: StateFlow<FpsHudState> = _state.asStateFlow()

    // Live cross-platform input state
    @Volatile var moveForwardInput: Float = 0f
    @Volatile var moveStrafeInput: Float = 0f
    @Volatile var isFiringInput: Boolean = false
    @Volatile var isAdsInput: Boolean = false
    @Volatile var isSprintInput: Boolean = false

    private var verticalVel = 0f
    private var verticalPos = 0f
    private var slideTimer = 0f
    private var slideCooldown = 0f
    private var fireCooldown = 0f
    private var reloadTimer = 0f
    private var timeSinceDamage = 5f
    private var domTickAccumulator = 0f
    private var killFeedSeq = 1L
    private var remoteHumans: Map<String, MatchPlayerState> = emptyMap()

    private fun createInitialState(mode: ArenaGameMode, inputMode: PlatformInputMode): FpsHudState {
        val bots = buildList {
            // 4 Alpha allies + 5 Bravo enemies = 5v5
            val allyNames = listOf("Ghost-FR", "Valkyrie", "Kestrel", "Nova")
            val enemyNames = listOf("Viper-01", "Reaper", "Cobalt", "Kraken", "Specter")
            allyNames.forEachIndexed { idx, name ->
                val (sx, sy) = FpsArenaMap.alphaSpawns[(idx + 1) % FpsArenaMap.alphaSpawns.size]
                add(
                    Combatant(
                        id = "bot_alpha_$idx",
                        callsign = name,
                        team = "alpha",
                        x = sx,
                        y = sy,
                        yaw = 0.78f,
                        patrolTargetX = 9f,
                        patrolTargetY = 9f,
                    )
                )
            }
            enemyNames.forEachIndexed { idx, name ->
                val (sx, sy) = FpsArenaMap.bravoSpawns[idx % FpsArenaMap.bravoSpawns.size]
                add(
                    Combatant(
                        id = "bot_bravo_$idx",
                        callsign = name,
                        team = "bravo",
                        x = sx,
                        y = sy,
                        yaw = -2.35f,
                        patrolTargetX = 9f,
                        patrolTargetY = 9f,
                    )
                )
            }
        }

        val zones = if (mode == ArenaGameMode.DOMINATION) {
            listOf(
                CapturePoint("A", 4.5f, 4.5f, ownerTeam = "alpha", progress = 100f),
                CapturePoint("B", 9.0f, 9.0f, ownerTeam = null, progress = 0f),
                CapturePoint("C", 13.5f, 13.5f, ownerTeam = "bravo", progress = -100f),
            )
        } else {
            emptyList()
        }

        return FpsHudState(
            mode = mode,
            inputMode = inputMode,
            ammoInMag = weapon.magazineSize,
            reserveAmmo = weapon.reserveAmmo,
            combatants = bots,
            capturePoints = zones,
            bannerMessage = if (mode == ArenaGameMode.DOMINATION) "DOMINATION • CAPTUREZ A, B, C" else "MATCH À MORT 5v5 • ÉLIMINEZ L'ESCOUADE BRAVO",
            bannerTimer = 3.2f,
        )
    }

    fun setInputMode(mode: PlatformInputMode) {
        _state.value = _state.value.copy(inputMode = mode)
    }

    fun toggleAutoFire() {
        _state.value = _state.value.copy(autoFireEnabled = !_state.value.autoFireEnabled)
    }

    fun applyLookDelta(deltaYaw: Float, deltaPitch: Float) {
        val current = _state.value
        if (current.matchFinished || current.isDead) return
        val sens = if (current.isAds) 0.55f else 1.0f
        val newYaw = normalizeAngle(current.playerYaw + deltaYaw * sens)
        val newPitch = (current.playerPitchOffset + deltaPitch * sens).coerceIn(-0.42f, 0.42f)
        _state.value = current.copy(playerYaw = newYaw, playerPitchOffset = newPitch)
    }

    fun triggerJump() {
        val s = _state.value
        if (s.matchFinished || s.isDead) return
        if (verticalPos <= 0.01f && slideTimer <= 0f) {
            verticalVel = 3.4f
        }
    }

    fun triggerSlide() {
        val s = _state.value
        if (s.matchFinished || s.isDead) return
        if (slideCooldown <= 0f && slideTimer <= 0f && (abs(moveForwardInput) > 0.2f || abs(moveStrafeInput) > 0.2f)) {
            slideTimer = 0.55f
            slideCooldown = 1.15f
        }
    }

    fun triggerReload() {
        val s = _state.value
        if (s.matchFinished || s.isDead || s.isReloading) return
        if (s.ammoInMag < weapon.magazineSize && s.reserveAmmo > 0) {
            reloadTimer = weapon.reloadMs / 1000f
            _state.value = s.copy(isReloading = true)
        }
    }

    fun activateScorestreak(streakId: String) {
        val s = _state.value
        if (s.matchFinished || s.isDead) return
        when (streakId) {
            "uav_drone" -> {
                if (!s.uavReady) return
                _state.value = s.copy(
                    uavReady = false,
                    uavActiveSeconds = 15f,
                    scorestreaksCalled = s.scorestreaksCalled + 1,
                    bannerMessage = "DRONE RADAR DÉPLOYÉ • ENNEMIS RÉVÉLÉS (15s)",
                    bannerTimer = 2.8f,
                )
            }
            "sentry_turret" -> {
                if (!s.turretReady) return
                _state.value = s.copy(
                    turretReady = false,
                    turret = SentryTurret(
                        x = s.playerX,
                        y = s.playerY,
                        yaw = s.playerYaw,
                        remainingSeconds = 20f,
                    ),
                    scorestreaksCalled = s.scorestreaksCalled + 1,
                    bannerMessage = "TOURELLE AUTOMATIQUE ACTIVE (20s)",
                    bannerTimer = 2.8f,
                )
            }
            "airstrike" -> {
                if (!s.airstrikeReady) return
                executeAirstrike(s)
            }
        }
    }

    private fun executeAirstrike(s: FpsHudState) {
        val enemies = s.combatants.filter { it.team != "alpha" && it.isAlive }
        val targetX = if (enemies.isNotEmpty()) enemies.map { it.x }.average().toFloat() else 9f
        val targetY = if (enemies.isNotEmpty()) enemies.map { it.y }.average().toFloat() else 9f

        var gainedKills = 0
        var gainedPoints = 0
        val feedAdditions = mutableListOf<KillFeedEntry>()
        val updatedCombatants = s.combatants.map { c ->
            if (c.team != "alpha" && c.isAlive && hypot(c.x - targetX, c.y - targetY) <= 5.2f) {
                gainedKills++
                gainedPoints += 100
                feedAdditions += KillFeedEntry(
                    id = killFeedSeq++,
                    killer = playerCallsign,
                    victim = c.callsign,
                    weaponName = "Frappe Aérienne",
                    isPlayerKiller = true,
                    isHeadshot = false,
                )
                c.copy(health = 0, isAlive = false, respawnTimer = 3.2f, deaths = c.deaths + 1, hitFlashTimer = 0.2f)
            } else c
        }

        val newPoints = s.killPoints + gainedPoints
        val newAlpha = if (s.mode == ArenaGameMode.TDM) s.alphaScore + gainedKills * 100 else s.alphaScore

        _state.value = applyStreakUnlocks(
            s.copy(
                airstrikeReady = false,
                airstrike = AirstrikeStrike(targetX, targetY, 3.2f),
                scorestreaksCalled = s.scorestreaksCalled + 1,
                kills = s.kills + gainedKills,
                killPoints = newPoints,
                totalScore = s.totalScore + gainedPoints,
                alphaScore = newAlpha,
                combatants = updatedCombatants,
                killFeed = (feedAdditions + s.killFeed).take(5),
                hitmarkerTimer = if (gainedKills > 0) 0.25f else s.hitmarkerTimer,
                hitmarkerKill = gainedKills > 0,
                bannerMessage = "FRAPPE AÉRIENNE SUR ZONE • $gainedKills ÉLIMINATION(S)",
                bannerTimer = 3.0f,
            )
        )
    }

    fun updateRemotePlayers(remoteList: List<MatchPlayerState>, localUserId: String) {
        remoteHumans = remoteList
            .filter { it.userId != localUserId }
            .associateBy { it.userId }
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        var s = _state.value
        if (s.matchFinished) return

        // Match clock
        val nextTime = (s.timeRemainingSeconds - dt).coerceAtLeast(0f)
        if (nextTime <= 0f) {
            _state.value = s.copy(
                timeRemainingSeconds = 0f,
                matchFinished = true,
                isVictory = s.alphaScore >= s.bravoScore,
            )
            return
        }

        // Timers decay
        if (fireCooldown > 0f) fireCooldown -= dt
        if (slideCooldown > 0f) slideCooldown -= dt
        timeSinceDamage += dt

        var nextBannerTimer = (s.bannerTimer - dt).coerceAtLeast(0f)
        var nextBanner = if (nextBannerTimer <= 0f) null else s.bannerMessage
        var nextHitmarker = (s.hitmarkerTimer - dt).coerceAtLeast(0f)
        var nextDmgFlash = (s.damageFlashTimer - dt).coerceAtLeast(0f)
        var nextMuzzle = (s.muzzleFlashTimer - dt).coerceAtLeast(0f)
        var nextRecoil = (s.recoilKick - dt * 4.5f).coerceAtLeast(0f)
        var nextUav = (s.uavActiveSeconds - dt).coerceAtLeast(0f)
        var nextAirstrike = s.airstrike?.let {
            val rem = it.remainingSeconds - dt
            if (rem <= 0f) null else it.copy(remainingSeconds = rem)
        }
        val nextTracers = s.tracers.mapNotNull {
            val rem = it.remainingSeconds - dt
            if (rem <= 0f) null else it.copy(remainingSeconds = rem)
        }.toMutableList()

        // Respawn handling if dead
        if (s.isDead) {
            val remRespawn = s.respawnCountdown - dt
            if (remRespawn <= 0f) {
                val (sx, sy) = FpsArenaMap.alphaSpawns.first()
                s = s.copy(
                    isDead = false,
                    respawnCountdown = 0f,
                    health = 100,
                    playerX = sx,
                    playerY = sy,
                    playerYaw = 0.78f,
                    ammoInMag = weapon.magazineSize,
                    isReloading = false,
                    killPoints = 0, // Streak resets on death
                    uavReady = false,
                    turretReady = false,
                    airstrikeReady = false,
                )
            } else {
                _state.value = s.copy(
                    timeRemainingSeconds = nextTime,
                    respawnCountdown = remRespawn,
                    damageFlashTimer = nextDmgFlash,
                    tracers = nextTracers,
                )
                return
            }
        }

        // Reload timer
        var ammoMag = s.ammoInMag
        var reserve = s.reserveAmmo
        var isReloading = s.isReloading
        if (isReloading) {
            reloadTimer -= dt
            if (reloadTimer <= 0f) {
                val needed = (weapon.magazineSize - ammoMag).coerceAtMost(reserve)
                ammoMag += needed
                reserve -= needed
                isReloading = false
            }
        }

        // Jump physics
        if (verticalPos > 0f || verticalVel != 0f) {
            verticalPos += verticalVel * dt
            verticalVel -= 9.8f * dt
            if (verticalPos <= 0f) {
                verticalPos = 0f
                verticalVel = 0f
            }
        }

        // Movement & Slide
        val isSliding = slideTimer > 0f
        if (slideTimer > 0f) slideTimer = (slideTimer - dt).coerceAtLeast(0f)
        val isSprinting = isSprintInput && !isAdsInput && !isSliding && moveForwardInput > 0.35f
        val mobilityFactor = weapon.mobility / 80f
        val baseSpeed = 3.6f * mobilityFactor
        val speed = when {
            isSliding -> 6.4f
            isAdsInput -> baseSpeed * 0.62f
            isSprinting -> baseSpeed * 1.48f
            else -> baseSpeed
        }

        val fwd = if (isSliding) 1f else moveForwardInput
        val strafe = if (isSliding) 0f else moveStrafeInput
        val cosY = cos(s.playerYaw)
        val sinY = sin(s.playerYaw)
        val vx = (cosY * fwd - sinY * strafe) * speed * dt
        val vy = (sinY * fwd + cosY * strafe) * speed * dt

        var nextX = s.playerX
        var nextY = s.playerY
        if (!FpsArenaMap.isSolid(nextX + vx, nextY)) nextX += vx
        if (!FpsArenaMap.isSolid(nextX, nextY + vy)) nextY += vy

        val moveMag = hypot(vx, vy)
        val nextBob = if (moveMag > 0.002f && verticalPos <= 0.01f && !isSliding) {
            s.walkBobPhase + dt * (if (isSprinting) 13f else 9f)
        } else s.walkBobPhase

        val camOffset = when {
            isSliding -> -0.26f
            verticalPos > 0f -> (verticalPos * 0.25f).coerceAtMost(0.32f)
            else -> 0f
        }

        // Auto health regeneration after 3.5s out of combat
        var playerHealth = s.health
        if (timeSinceDamage >= 3.5f && playerHealth < 100) {
            playerHealth = (playerHealth + (22f * dt).toInt().coerceAtLeast(1)).coerceAtMost(100)
        }

        // Merge remote humans if any
        val mergedCombatants = mergeRemoteCombatants(s.combatants).toMutableList()
        var kills = s.kills
        var deaths = s.deaths
        var assists = s.assists
        var killPoints = s.killPoints
        var totalScore = s.totalScore
        var alphaScore = s.alphaScore
        var bravoScore = s.bravoScore
        val feed = s.killFeed.toMutableList()
        var hitCrit = s.hitmarkerCrit
        var hitKill = s.hitmarkerKill

        // Auto-Fire target acquisition on Mobile when enabled
        val autoTargetInCrosshair = if (s.autoFireEnabled && !isReloading) {
            hasEnemyInCrosshair(nextX, nextY, s.playerYaw, mergedCombatants)
        } else false

        // Player Weapon Fire
        if ((isFiringInput || autoTargetInCrosshair) && !isReloading && fireCooldown <= 0f) {
            if (ammoMag > 0) {
                ammoMag--
                fireCooldown = weapon.fireRateMs / 1000f
                nextMuzzle = 0.07f
                nextRecoil = 1.0f

                val spread = if (isAdsInput) weapon.spreadAds else weapon.spreadHip
                val shotYaw = s.playerYaw + (Random.nextFloat() - 0.5f) * spread
                val wallHit = FpsArenaMap.castRay(nextX, nextY, shotYaw, 24f)

                // Find closest enemy along ray
                var hitIdx = -1
                var closestDist = wallHit.distance
                val dirX = cos(shotYaw)
                val dirY = sin(shotYaw)
                for (i in mergedCombatants.indices) {
                    val c = mergedCombatants[i]
                    if (!c.isAlive || c.team == "alpha") continue
                    val toX = c.x - nextX
                    val toY = c.y - nextY
                    val proj = toX * dirX + toY * dirY
                    if (proj <= 0.2f || proj >= closestDist) continue
                    val perp = abs(toX * (-dirY) + toY * dirX)
                    if (perp < 0.38f) {
                        closestDist = proj
                        hitIdx = i
                    }
                }

                nextTracers += TracerBolt(
                    startX = nextX + cos(s.playerYaw + 0.3f) * 0.25f,
                    startY = nextY + sin(s.playerYaw + 0.3f) * 0.25f,
                    endX = nextX + dirX * closestDist,
                    endY = nextY + dirY * closestDist,
                    remainingSeconds = 0.08f,
                    colorHex = weaponAccentHex,
                )

                if (hitIdx >= 0) {
                    val target = mergedCombatants[hitIdx]
                    val isHeadshot = abs(s.playerPitchOffset) < 0.08f && Random.nextFloat() < 0.28f
                    val dmg = if (isHeadshot) (weapon.damage * 1.45f).toInt() else weapon.damage
                    val newHp = (target.health - dmg).coerceAtLeast(0)
                    nextHitmarker = 0.16f
                    hitCrit = isHeadshot
                    hitKill = newHp == 0
                    if (newHp == 0) {
                        kills++
                        killPoints += 100
                        totalScore += 100
                        if (s.mode == ArenaGameMode.TDM) alphaScore += 100
                        feed.add(
                            0,
                            KillFeedEntry(
                                id = killFeedSeq++,
                                killer = playerCallsign,
                                victim = target.callsign,
                                weaponName = weapon.name,
                                isPlayerKiller = true,
                                isHeadshot = isHeadshot,
                            )
                        )
                        mergedCombatants[hitIdx] = target.copy(
                            health = 0,
                            isAlive = false,
                            respawnTimer = 3.2f,
                            deaths = target.deaths + 1,
                            hitFlashTimer = 0.2f,
                            damagedByPlayerRecently = 0f,
                        )
                    } else {
                        mergedCombatants[hitIdx] = target.copy(
                            health = newHp,
                            hitFlashTimer = 0.14f,
                            damagedByPlayerRecently = 5f,
                        )
                    }
                }

                // Apply vertical pitch recoil
                val recoilKickPitch = (weapon.recoilAmount * (if (isAdsInput) 0.55f else 1f))
                s = s.copy(playerPitchOffset = (s.playerPitchOffset - recoilKickPitch).coerceIn(-0.42f, 0.42f))

                if (ammoMag == 0 && reserve > 0) {
                    isReloading = true
                    reloadTimer = weapon.reloadMs / 1000f
                }
            } else if (reserve > 0) {
                isReloading = true
                reloadTimer = weapon.reloadMs / 1000f
            }
        }

        // Sentry Turret Scorestreak AI
        var nextTurret = s.turret?.let { t ->
            val rem = t.remainingSeconds - dt
            if (rem <= 0f) null
            else {
                var cd = (t.fireCooldown - dt).coerceAtLeast(0f)
                var turretYaw = t.yaw + dt * 0.8f
                if (cd <= 0f) {
                    val targetIdx = mergedCombatants.indexOfFirst { c ->
                        c.team != "alpha" && c.isAlive &&
                            hypot(c.x - t.x, c.y - t.y) < 10.5f &&
                            FpsArenaMap.hasLineOfSight(t.x, t.y, c.x, c.y)
                    }
                    if (targetIdx >= 0) {
                        val enemy = mergedCombatants[targetIdx]
                        turretYaw = atan2(enemy.y - t.y, enemy.x - t.x)
                        cd = 0.35f
                        nextTracers += TracerBolt(t.x, t.y, enemy.x, enemy.y, 0.09f, 0xFFFFB800)
                        val newHp = (enemy.health - 24).coerceAtLeast(0)
                        if (newHp == 0) {
                            kills++
                            killPoints += 100
                            totalScore += 100
                            if (s.mode == ArenaGameMode.TDM) alphaScore += 100
                            feed.add(
                                0,
                                KillFeedEntry(killFeedSeq++, playerCallsign, enemy.callsign, "Tourelle Auto", true, false)
                            )
                            mergedCombatants[targetIdx] = enemy.copy(
                                health = 0, isAlive = false, respawnTimer = 3.2f, deaths = enemy.deaths + 1
                            )
                        } else {
                            mergedCombatants[targetIdx] = enemy.copy(health = newHp, hitFlashTimer = 0.14f)
                        }
                    }
                }
                t.copy(remainingSeconds = rem, fireCooldown = cd, yaw = turretYaw)
            }
        }

        // Update AI Combatants
        for (i in mergedCombatants.indices) {
            val bot = mergedCombatants[i]
            if (bot.isHumanRemote) continue
            val flash = (bot.hitFlashTimer - dt).coerceAtLeast(0f)
            val dmgRecent = (bot.damagedByPlayerRecently - dt).coerceAtLeast(0f)
            if (!bot.isAlive) {
                val r = bot.respawnTimer - dt
                if (r <= 0f) {
                    val spawns = if (bot.team == "alpha") FpsArenaMap.alphaSpawns else FpsArenaMap.bravoSpawns
                    val (sx, sy) = spawns[i % spawns.size]
                    mergedCombatants[i] = bot.copy(
                        isAlive = true,
                        health = 100,
                        x = sx,
                        y = sy,
                        respawnTimer = 0f,
                        hitFlashTimer = 0f,
                        damagedByPlayerRecently = 0f,
                    )
                } else {
                    mergedCombatants[i] = bot.copy(respawnTimer = r, hitFlashTimer = flash)
                }
                continue
            }

            // Pick target: opposing live combatant or player
            var botX = bot.x
            var botY = bot.y
            var botYaw = bot.yaw
            var botCd = (bot.fireCooldown - dt).coerceAtLeast(0f)
            var tgtX = bot.patrolTargetX
            var tgtY = bot.patrolTargetY

            if (hypot(tgtX - botX, tgtY - botY) < 0.8f) {
                tgtX = (2..15).random().toFloat() + 0.5f
                tgtY = (2..15).random().toFloat() + 0.5f
            }

            // Nearest enemy check
            var enemyDist = 99f
            var enemyX = 0f
            var enemyY = 0f
            var targetIsLocalPlayer = false
            var targetBotIdx = -1

            if (bot.team != "alpha") {
                val dp = hypot(nextX - botX, nextY - botY)
                if (dp < 11f && FpsArenaMap.hasLineOfSight(botX, botY, nextX, nextY)) {
                    enemyDist = dp
                    enemyX = nextX
                    enemyY = nextY
                    targetIsLocalPlayer = true
                }
            }
            for (j in mergedCombatants.indices) {
                val other = mergedCombatants[j]
                if (!other.isAlive || other.team == bot.team) continue
                val d = hypot(other.x - botX, other.y - botY)
                if (d < enemyDist && d < 11f && FpsArenaMap.hasLineOfSight(botX, botY, other.x, other.y)) {
                    enemyDist = d
                    enemyX = other.x
                    enemyY = other.y
                    targetIsLocalPlayer = false
                    targetBotIdx = j
                }
            }

            if (enemyDist < 11f) {
                botYaw = atan2(enemyY - botY, enemyX - botX)
                if (botCd <= 0f) {
                    botCd = 0.65f + Random.nextFloat() * 0.45f
                    nextTracers += TracerBolt(botX, botY, enemyX, enemyY, 0.06f, if (bot.team == "alpha") 0xFF00E5FF else 0xFFFF3B4E)
                    if (Random.nextFloat() < 0.42f) {
                        if (targetIsLocalPlayer) {
                            playerHealth = (playerHealth - 12).coerceAtLeast(0)
                            timeSinceDamage = 0f
                            nextDmgFlash = 0.25f
                            if (playerHealth == 0) {
                                deaths++
                                if (s.mode == ArenaGameMode.TDM) bravoScore += 100
                                feed.add(0, KillFeedEntry(killFeedSeq++, bot.callsign, playerCallsign, "Rifle", false, false))
                            }
                        } else if (targetBotIdx >= 0) {
                            val victim = mergedCombatants[targetBotIdx]
                            val newHp = (victim.health - 22).coerceAtLeast(0)
                            if (newHp == 0) {
                                if (s.mode == ArenaGameMode.TDM) {
                                    if (bot.team == "alpha") alphaScore += 100 else bravoScore += 100
                                }
                                // Check assist for local player
                                if (victim.damagedByPlayerRecently > 0f && bot.team == "alpha") {
                                    assists++
                                    killPoints += 50
                                    totalScore += 50
                                    nextBanner = "+50 ASSISTANCE"
                                    nextBannerTimer = 1.6f
                                }
                                feed.add(0, KillFeedEntry(killFeedSeq++, bot.callsign, victim.callsign, "Assaut", false, false))
                                mergedCombatants[targetBotIdx] = victim.copy(
                                    health = 0, isAlive = false, respawnTimer = 3.2f, deaths = victim.deaths + 1
                                )
                            } else {
                                mergedCombatants[targetBotIdx] = victim.copy(health = newHp, hitFlashTimer = 0.1f)
                            }
                        }
                    }
                }
            } else {
                val moveAngle = atan2(tgtY - botY, tgtX - botX)
                botYaw = moveAngle
                val stepLen = 2.05f * dt
                val nx = botX + cos(moveAngle) * stepLen
                val ny = botY + sin(moveAngle) * stepLen
                if (!FpsArenaMap.isSolid(nx, botY)) botX = nx else tgtX = (2..15).random() + 0.5f
                if (!FpsArenaMap.isSolid(botX, ny)) botY = ny else tgtY = (2..15).random() + 0.5f
            }

            mergedCombatants[i] = bot.copy(
                x = botX,
                y = botY,
                yaw = botYaw,
                fireCooldown = botCd,
                hitFlashTimer = flash,
                damagedByPlayerRecently = dmgRecent,
                patrolTargetX = tgtX,
                patrolTargetY = tgtY,
            )
        }

        // Domination Capture Points logic
        val updatedZones = if (s.mode == ArenaGameMode.DOMINATION) {
            domTickAccumulator += dt
            val awardTick = domTickAccumulator >= 2.0f
            if (awardTick) domTickAccumulator = 0f

            s.capturePoints.map { zone ->
                val alphaCount = (if (hypot(nextX - zone.x, nextY - zone.y) < 1.8f && playerHealth > 0) 1 else 0) +
                    mergedCombatants.count { it.team == "alpha" && it.isAlive && hypot(it.x - zone.x, it.y - zone.y) < 1.8f }
                val bravoCount = mergedCombatants.count { it.team == "bravo" && it.isAlive && hypot(it.x - zone.x, it.y - zone.y) < 1.8f }

                val contested = alphaCount > 0 && bravoCount > 0
                var prog = zone.progress
                var owner = zone.ownerTeam

                if (!contested) {
                    if (alphaCount > 0) prog = (prog + 36f * dt * alphaCount).coerceIn(-100f, 100f)
                    else if (bravoCount > 0) prog = (prog - 36f * dt * bravoCount).coerceIn(-100f, 100f)
                }

                if (prog >= 100f && owner != "alpha") {
                    owner = "alpha"
                    if (hypot(nextX - zone.x, nextY - zone.y) < 2.1f) {
                        killPoints += 150
                        totalScore += 150
                        nextBanner = "ZONE ${zone.label} CAPTURÉE (+150 PTS)"
                        nextBannerTimer = 2.4f
                    }
                } else if (prog <= -100f && owner != "bravo") {
                    owner = "bravo"
                }

                if (awardTick) {
                    if (owner == "alpha") alphaScore += 4
                    else if (owner == "bravo") bravoScore += 4
                }
                zone.copy(ownerTeam = owner, progress = prog, isContested = contested)
            }
        } else s.capturePoints

        val playerJustDied = playerHealth <= 0
        val winTarget = s.mode.targetScore
        val finished = alphaScore >= winTarget || bravoScore >= winTarget

        val nextState = applyStreakUnlocks(
            s.copy(
                timeRemainingSeconds = nextTime,
                playerX = nextX,
                playerY = nextY,
                playerYaw = s.playerYaw,
                cameraHeightOffset = camOffset,
                walkBobPhase = nextBob,
                health = playerHealth,
                isSprinting = isSprinting,
                isSliding = isSliding,
                isAds = isAdsInput,
                isReloading = isReloading,
                ammoInMag = ammoMag,
                reserveAmmo = reserve,
                kills = kills,
                deaths = deaths,
                assists = assists,
                killPoints = killPoints,
                totalScore = totalScore,
                uavActiveSeconds = nextUav,
                turret = nextTurret,
                airstrike = nextAirstrike,
                hitmarkerTimer = nextHitmarker,
                hitmarkerCrit = hitCrit,
                hitmarkerKill = hitKill,
                damageFlashTimer = nextDmgFlash,
                muzzleFlashTimer = nextMuzzle,
                recoilKick = nextRecoil,
                bannerMessage = nextBanner,
                bannerTimer = nextBannerTimer,
                alphaScore = alphaScore,
                bravoScore = bravoScore,
                isDead = playerJustDied,
                respawnCountdown = if (playerJustDied) 3.0f else 0f,
                matchFinished = finished,
                isVictory = alphaScore >= bravoScore,
                combatants = mergedCombatants,
                capturePoints = updatedZones,
                killFeed = feed.take(5),
                tracers = nextTracers,
            )
        )
        _state.value = nextState
    }

    private fun mergeRemoteCombatants(existing: List<Combatant>): List<Combatant> {
        if (remoteHumans.isEmpty()) return existing
        val result = existing.filterNot { it.isHumanRemote }.toMutableList()
        remoteHumans.values.forEach { remote ->
            result.add(
                0,
                Combatant(
                    id = remote.userId,
                    callsign = remote.callsign,
                    team = remote.team,
                    isHumanRemote = true,
                    x = remote.x.toFloat(),
                    y = remote.y.toFloat(),
                    yaw = remote.yaw.toFloat(),
                    health = remote.health.toInt(),
                    isAlive = remote.health > 0L,
                    kills = remote.kills.toInt(),
                    deaths = remote.deaths.toInt(),
                )
            )
        }
        return result
    }

    private fun hasEnemyInCrosshair(px: Float, py: Float, yaw: Float, combatants: List<Combatant>): Boolean {
        val wallDist = FpsArenaMap.castRay(px, py, yaw, 20f).distance
        val dirX = cos(yaw)
        val dirY = sin(yaw)
        return combatants.any { c ->
            if (!c.isAlive || c.team == "alpha") return@any false
            val toX = c.x - px
            val toY = c.y - py
            val proj = toX * dirX + toY * dirY
            if (proj <= 0.3f || proj >= wallDist) return@any false
            val perp = abs(toX * (-dirY) + toY * dirX)
            perp < 0.34f
        }
    }

    private fun applyStreakUnlocks(s: FpsHudState): FpsHudState {
        val uav = s.uavReady || s.killPoints >= GameCatalog.scorestreaks[0].costPoints
        val turret = s.turretReady || s.killPoints >= GameCatalog.scorestreaks[1].costPoints
        val strike = s.airstrikeReady || s.killPoints >= GameCatalog.scorestreaks[2].costPoints
        return s.copy(
            uavReady = uav,
            turretReady = turret,
            airstrikeReady = strike,
        )
    }

    private fun normalizeAngle(a: Float): Float {
        var r = a
        val twoPi = (2.0 * PI).toFloat()
        while (r > PI) r -= twoPi
        while (r < -PI) r += twoPi
        return r
    }
}
