package com.example.game

enum class PlatformInputMode(val labelFr: String) {
    MOBILE_TOUCH("MOBILE"),
    PC_KEYBOARD_MOUSE("PC / CLAVIER"),
}

enum class ArenaGameMode(val id: String, val labelFr: String, val targetScore: Int) {
    TDM("tdm", "Match à mort 5v5", 3000),
    DOMINATION("domination", "Domination", 200),
}

data class Combatant(
    val id: String,
    val callsign: String,
    val team: String, // "alpha" or "bravo"
    val isHumanRemote: Boolean = false,
    val x: Float,
    val y: Float,
    val yaw: Float,
    val health: Int = 100,
    val isAlive: Boolean = true,
    val respawnTimer: Float = 0f,
    val fireCooldown: Float = 0f,
    val hitFlashTimer: Float = 0f,
    val kills: Int = 0,
    val deaths: Int = 0,
    val patrolTargetX: Float = 9f,
    val patrolTargetY: Float = 9f,
    val damagedByPlayerRecently: Float = 0f,
)

data class CapturePoint(
    val label: String, // "A", "B", "C"
    val x: Float,
    val y: Float,
    val ownerTeam: String? = null, // null, "alpha", "bravo"
    val progress: Float = 0f, // -100 (bravo) .. +100 (alpha)
    val isContested: Boolean = false,
)

data class SentryTurret(
    val x: Float,
    val y: Float,
    val yaw: Float,
    val remainingSeconds: Float,
    val fireCooldown: Float = 0f,
)

data class AirstrikeStrike(
    val x: Float,
    val y: Float,
    val remainingSeconds: Float,
)

data class KillFeedEntry(
    val id: Long,
    val killer: String,
    val victim: String,
    val weaponName: String,
    val isPlayerKiller: Boolean,
    val isHeadshot: Boolean,
)

data class TracerBolt(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val remainingSeconds: Float,
    val colorHex: Long,
)

data class FpsHudState(
    val mode: ArenaGameMode = ArenaGameMode.TDM,
    val inputMode: PlatformInputMode = PlatformInputMode.MOBILE_TOUCH,
    val autoFireEnabled: Boolean = false,
    val playerX: Float = 2.5f,
    val playerY: Float = 2.5f,
    val playerYaw: Float = 0.78f,
    val playerPitchOffset: Float = 0f,
    val cameraHeightOffset: Float = 0f, // jump (+), slide (-)
    val walkBobPhase: Float = 0f,
    val health: Int = 100,
    val isSprinting: Boolean = false,
    val isSliding: Boolean = false,
    val isAds: Boolean = false,
    val isReloading: Boolean = false,
    val ammoInMag: Int = 30,
    val reserveAmmo: Int = 150,
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
    val killPoints: Int = 0, // current streak points (resets on death)
    val totalScore: Int = 0,
    val scorestreaksCalled: Int = 0,
    val uavReady: Boolean = false,
    val turretReady: Boolean = false,
    val airstrikeReady: Boolean = false,
    val uavActiveSeconds: Float = 0f,
    val turret: SentryTurret? = null,
    val airstrike: AirstrikeStrike? = null,
    val hitmarkerTimer: Float = 0f,
    val hitmarkerCrit: Boolean = false,
    val hitmarkerKill: Boolean = false,
    val damageFlashTimer: Float = 0f,
    val muzzleFlashTimer: Float = 0f,
    val recoilKick: Float = 0f,
    val bannerMessage: String? = null,
    val bannerTimer: Float = 0f,
    val alphaScore: Int = 0,
    val bravoScore: Int = 0,
    val timeRemainingSeconds: Float = 180f,
    val isDead: Boolean = false,
    val respawnCountdown: Float = 0f,
    val matchFinished: Boolean = false,
    val isVictory: Boolean = false,
    val combatants: List<Combatant> = emptyList(),
    val capturePoints: List<CapturePoint> = emptyList(),
    val killFeed: List<KillFeedEntry> = emptyList(),
    val tracers: List<TracerBolt> = emptyList(),
)
