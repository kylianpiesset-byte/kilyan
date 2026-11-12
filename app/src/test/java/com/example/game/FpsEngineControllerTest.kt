package com.example.game

import com.example.data.model.GameCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FpsEngineControllerTest {

    @Test
    fun playerMovementAndReload_updatesTransformAndAmmo() {
        val weapon = GameCatalog.weaponById("m4a1_tactical")
        val controller = FpsEngineController(
            playerCallsign = "Tester",
            weapon = weapon,
            weaponAccentHex = 0xFF00E5FF,
            mode = ArenaGameMode.TDM,
            initialInputMode = PlatformInputMode.PC_KEYBOARD_MOUSE,
        )

        val startX = controller.state.value.playerX
        val startY = controller.state.value.playerY

        controller.moveForwardInput = 1f
        controller.isSprintInput = true
        repeat(10) { controller.step(0.016f) }

        val moved = controller.state.value
        assertTrue(moved.playerX != startX || moved.playerY != startY)
        assertTrue(moved.isSprinting)

        // Fire 3 shots then reload
        controller.moveForwardInput = 0f
        controller.isFiringInput = true
        repeat(30) { controller.step(0.016f) }
        controller.isFiringInput = false
        assertTrue(controller.state.value.ammoInMag < weapon.magazineSize)

        controller.triggerReload()
        assertTrue(controller.state.value.isReloading)
        repeat(120) { controller.step(0.016f) }
        assertFalse(controller.state.value.isReloading)
        assertEquals(weapon.magazineSize, controller.state.value.ammoInMag)
    }

    @Test
    fun scorestreaks_airstrikeCanOnlyTriggerWhenUnlocked() {
        val weapon = GameCatalog.weaponById("m4a1_tactical")
        val controller = FpsEngineController(
            playerCallsign = "Tester",
            weapon = weapon,
            weaponAccentHex = 0xFF00E5FF,
            mode = ArenaGameMode.DOMINATION,
            initialInputMode = PlatformInputMode.MOBILE_TOUCH,
        )

        controller.activateScorestreak("uav_drone")
        assertEquals(0, controller.state.value.scorestreaksCalled)
        assertEquals(0f, controller.state.value.uavActiveSeconds, 0.001f)
    }
}
