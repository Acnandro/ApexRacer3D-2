package com.example

import com.example.game.ghost.GhostManager
import com.example.game.godot.GodotProjectData
import com.example.game.physics.VehiclePhysics
import com.example.game.track.CircuitTrack
import com.example.game.track.LapSystem
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class ExampleUnitTest {

    @Test
    fun testGodotScriptDeliverablesIntegrity() {
        // Verify car_controller.gd contains key Godot 4 features + Nitro Boost
        assertTrue(GodotProjectData.CAR_CONTROLLER_GD.contains("class_name CarController"))
        assertTrue(GodotProjectData.CAR_CONTROLLER_GD.contains("extends VehicleBody3D"))
        assertTrue(GodotProjectData.CAR_CONTROLLER_GD.contains("nitro_boost_force"))
        assertTrue(GodotProjectData.CAR_CONTROLLER_GD.contains("nitro_top_speed_kmh"))
        assertTrue(GodotProjectData.CAR_CONTROLLER_GD.contains("high_speed_steer_dampening"))
        assertTrue(GodotProjectData.CAR_CONTROLLER_GD.contains("drift_rear_friction"))
        assertTrue(GodotProjectData.CAR_CONTROLLER_GD.contains("follow_camera"))

        // Verify touch_hud.gd contains dual touch, Nitro gauge, Ghost delta
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("class_name TouchHUD"))
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("nitro_bar"))
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("ghost_delta_label"))
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("update_ghost_delta"))
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("update_nitro"))
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("OS.has_feature(\"mobile\")"))
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("btn_toggle_touch_debug"))
        assertTrue(GodotProjectData.TOUCH_HUD_GD.contains("update_speed"))

        // Verify ghost_manager.gd contains recording, interpolation, and disk save
        assertTrue(GodotProjectData.GHOST_MANAGER_GD.contains("class_name GhostManager"))
        assertTrue(GodotProjectData.GHOST_MANAGER_GD.contains("user://ghost_lap.dat"))
        assertTrue(GodotProjectData.GHOST_MANAGER_GD.contains("ghost_delta_updated"))
        assertTrue(GodotProjectData.GHOST_MANAGER_GD.contains("StandardMaterial3D"))

        // Verify minimap.gd contains radar drawing and waypoint projections
        assertTrue(GodotProjectData.MINIMAP_GD.contains("class_name Minimap"))
        assertTrue(GodotProjectData.MINIMAP_GD.contains("_world_to_minimap"))
        assertTrue(GodotProjectData.MINIMAP_GD.contains("_draw_directional_arrow"))

        // Verify lap_manager.gd contains checkpoint sequencing and save records
        assertTrue(GodotProjectData.LAP_MANAGER_GD.contains("class_name LapManager"))
        assertTrue(GodotProjectData.LAP_MANAGER_GD.contains("checkpoints: Array[Area3D]"))
        assertTrue(GodotProjectData.LAP_MANAGER_GD.contains("user://records.cfg"))

        // Verify network_manager.gd contains Godot 4 High-Level Multiplayer API
        assertTrue(GodotProjectData.NETWORK_MANAGER_GD.contains("class_name NetworkManager"))
        assertTrue(GodotProjectData.NETWORK_MANAGER_GD.contains("ENetMultiplayerPeer"))
        assertTrue(GodotProjectData.NETWORK_MANAGER_GD.contains("sync_car_telemetry"))
        assertTrue(GodotProjectData.NETWORK_MANAGER_GD.contains("submit_checkpoint_pass"))
        assertTrue(GodotProjectData.NETWORK_MANAGER_GD.contains("start_race"))

        // Verify project.godot includes gl_compatibility and InputMap with nitro
        assertTrue(GodotProjectData.PROJECT_GODOT.contains("renderer/rendering_method=\"gl_compatibility\""))
        assertTrue(GodotProjectData.PROJECT_GODOT.contains("steer_left"))
        assertTrue(GodotProjectData.PROJECT_GODOT.contains("steer_right"))
        assertTrue(GodotProjectData.PROJECT_GODOT.contains("accelerate"))
        assertTrue(GodotProjectData.PROJECT_GODOT.contains("brake"))
        assertTrue(GodotProjectData.PROJECT_GODOT.contains("handbrake"))
        assertTrue(GodotProjectData.PROJECT_GODOT.contains("nitro"))
    }

    @Test
    fun testVehiclePhysicsAccelerationAndBraking() {
        val physics = VehiclePhysics()
        physics.reset(posX = 0f, posZ = 0f, headingRad = -PI.toFloat() / 2f)

        for (i in 1..60) {
            physics.update(
                deltaSec = 1f / 60f,
                steerInput = 0f,
                accelInput = 1f,
                brakeInput = 0f,
                handbrakeInput = false,
                nitroInput = false
            )
        }

        assertTrue("Car should accelerate to positive speed", physics.state.speedKmh > 10f)
        val initialSpeed = physics.state.speedKmh

        for (i in 1..60) {
            physics.update(
                deltaSec = 1f / 60f,
                steerInput = 0f,
                accelInput = 0f,
                brakeInput = 1f,
                handbrakeInput = false,
                nitroInput = false
            )
        }

        assertTrue("Car speed should decrease after braking", physics.state.speedKmh < initialSpeed)
    }

    @Test
    fun testNitroBoostMechanic() {
        val physics = VehiclePhysics()
        physics.reset(posX = 0f, posZ = 0f, headingRad = -PI.toFloat() / 2f)

        assertEquals("Nitro fuel should start at 100%", 100f, physics.state.nitroFuel, 0.01f)
        assertFalse("Nitro should be inactive initially", physics.state.isBoosting)

        for (i in 1..30) {
            physics.update(
                deltaSec = 1f / 60f,
                steerInput = 0f,
                accelInput = 1f,
                brakeInput = 0f,
                handbrakeInput = false,
                nitroInput = true
            )
        }

        assertTrue("Nitro should be actively boosting", physics.state.isBoosting)
        assertTrue("Nitro fuel should be consumed", physics.state.nitroFuel < 100f)
        assertTrue("Nitro particles should be spawned", physics.getNitroParticles().isNotEmpty())
        val fuelAfterBoost = physics.state.nitroFuel

        for (i in 1..30) {
            physics.update(
                deltaSec = 1f / 60f,
                steerInput = 0f,
                accelInput = 1f,
                brakeInput = 0f,
                handbrakeInput = false,
                nitroInput = false
            )
        }

        assertFalse("Nitro should be inactive when released", physics.state.isBoosting)
        assertTrue("Nitro fuel should recharge when idle", physics.state.nitroFuel > fuelAfterBoost)
    }

    @Test
    fun testGhostCarRecordingAndPlayback() {
        val ghostManager = GhostManager()
        assertFalse("Ghost should not be available initially", ghostManager.isGhostAvailable)

        // Record a simulated lap of 20 seconds
        val totalDuration = 20.0f
        var t = 0f
        while (t <= totalDuration) {
            val posX = t * 10f
            val posZ = 0f
            ghostManager.recordSample(
                timeSec = t,
                posX = posX,
                posZ = posZ,
                headingRad = 0f,
                steerAngleRad = 0f,
                isBraking = false,
                isBoosting = false,
                deltaSec = 0.05f
            )
            t += 0.05f
        }

        // Lap completed as new best lap
        ghostManager.onLapCompleted(lapTime = totalDuration, isBest = true)
        assertTrue("Ghost should be available after completing best lap", ghostManager.isGhostAvailable)
        assertEquals(totalDuration, ghostManager.bestGhostLapTime, 0.01f)

        // Sample ghost at midpoint (10s): position should interpolate near (100, 0)
        val sampled = ghostManager.sampleGhost(10.0f)
        assertNotNull("Sampled ghost state should not be null", sampled)
        assertEquals(100f, sampled!!.posX, 2.0f)
        assertEquals(0f, sampled.posZ, 0.1f)

        // Test delta split calculation: player is at posX = 120 (ahead) at time 10s
        val deltaAhead = ghostManager.computeTimeDelta(playerX = 120f, playerZ = 0f, playerTimeSec = 10f)
        assertNotNull(deltaAhead)
        assertTrue("Player ahead of ghost should return positive delta", deltaAhead!! > 0f)

        // Test Serialization & Deserialization
        val serialized = ghostManager.serializeGhost()
        assertTrue("Serialized ghost string should not be empty", serialized.isNotEmpty())

        val ghostManagerRestored = GhostManager()
        ghostManagerRestored.deserializeGhost(serialized)
        assertTrue("Restored ghost should be available", ghostManagerRestored.isGhostAvailable)
        assertEquals(totalDuration, ghostManagerRestored.bestGhostLapTime, 0.01f)
    }

    @Test
    fun testLapSystemSequentialCheckpointsAndAntiCutting() {
        val track = CircuitTrack()
        var lapCompletedCount = 0
        var wrongWayTriggered = false

        val lapSystem = LapSystem(
            track = track,
            totalLaps = 3,
            onLapComplete = { _, _, _ -> lapCompletedCount++ },
            onWrongWay = { wrongWayTriggered = true }
        )
        lapSystem.startRace()

        // 1. Trying to cross Finish line (checkpoint 0) without passing intermediate checkpoints
        lapSystem.update(0.016f, track.checkpoints[0].x, track.checkpoints[0].z)
        assertTrue("Attempting to cut track must trigger wrong-way alarm", wrongWayTriggered)
        assertEquals("Lap must not increment on track-cutting", 0, lapCompletedCount)

        // 2. Clear checkpoints in correct sequence: 1 -> 2 -> 3 -> 0
        lapSystem.update(0.016f, track.checkpoints[1].x, track.checkpoints[1].z)
        assertEquals(2, lapSystem.nextCheckpointIndex)

        lapSystem.update(0.016f, track.checkpoints[2].x, track.checkpoints[2].z)
        assertEquals(3, lapSystem.nextCheckpointIndex)

        lapSystem.update(0.016f, track.checkpoints[3].x, track.checkpoints[3].z)
        assertEquals(0, lapSystem.nextCheckpointIndex)

        lapSystem.update(0.016f, track.checkpoints[0].x, track.checkpoints[0].z)
        assertEquals("Legitimate lap completion should increment lap completed count", 1, lapCompletedCount)
        assertEquals(2, lapSystem.currentLap)
    }

    @Test
    fun testMinimapCoordinateNormalization() {
        val track = CircuitTrack()
        assertTrue("Track must have waypoints", track.waypoints.isNotEmpty())

        val minX = -180f
        val maxX = 250f
        val minZ = -220f
        val maxZ = 230f
        val spanX = maxX - minX
        val spanZ = maxZ - minZ

        // All track waypoints must fall within the minimap bounding box [0, 1]
        for (wp in track.waypoints) {
            val nx = (wp.x - minX) / spanX
            val nz = (wp.z - minZ) / spanZ
            assertTrue("Waypoint X ${wp.x} normalized $nx must be in [0, 1]", nx in 0f..1f)
            assertTrue("Waypoint Z ${wp.z} normalized $nz must be in [0, 1]", nz in 0f..1f)
        }

        // All checkpoints must also fall within the minimap bounds
        for (cp in track.checkpoints) {
            val nx = (cp.x - minX) / spanX
            val nz = (cp.z - minZ) / spanZ
            assertTrue("Checkpoint ${cp.name} X normalized $nx must be in [0, 1]", nx in 0f..1f)
            assertTrue("Checkpoint ${cp.name} Z normalized $nz must be in [0, 1]", nz in 0f..1f)
        }
    }

    @Test
    fun testMultiplayerModelsAndColorPalette() {
        val lobby = com.example.game.multiplayer.Lobby(
            id = "test_lobby_1",
            lobbyCode = "APX999",
            name = "Championship",
            hostId = "host_123",
            hostName = "HostRacer",
            status = "waiting",
            maxPlayers = 4,
            playerCount = 1,
            trackId = "pro_speedway"
        )
        assertEquals("APX999", lobby.lobbyCode)
        assertEquals("waiting", lobby.status)
        assertEquals(4, lobby.maxPlayers)

        val player = com.example.game.multiplayer.PlayerState(
            id = "p1",
            userId = "user_456",
            displayName = "ApexSpeed",
            carColor = "#00E5FF",
            isReady = true,
            isHost = false,
            posX = 15f,
            posZ = 45f,
            rotY = 1.57f,
            speedKmh = 160f,
            isNitro = true,
            isBraking = false,
            currentLap = 2,
            checkpointIndex = 1
        )
        assertEquals("user_456", player.userId)
        assertTrue(player.isReady)
        assertTrue(player.isNitro)
        assertEquals(2, player.currentLap)

        // Verify all CarColorOptions have valid hex color strings
        for (option in com.example.game.multiplayer.CarColorOption.entries) {
            assertTrue("Hex must start with #", option.hex.startsWith("#"))
            assertEquals("Hex length must be 7", 7, option.hex.length)
            assertTrue("Label must not be empty", option.label.isNotEmpty())
        }
    }
}

