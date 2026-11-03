package com.example.game.track

import kotlin.math.*

data class TrackWaypoint(
    val x: Float,
    val z: Float,
    val width: Float = 22f
)

data class Checkpoint(
    val id: Int,
    val name: String,
    val x: Float,
    val z: Float,
    val radius: Float = 18f
)

class CircuitTrack {
    // 3D Track spline defined by sequence of waypoints
    val waypoints: List<TrackWaypoint> = listOf(
        // Main Straight (Start/Finish at Z = -20)
        TrackWaypoint(0f, 60f),
        TrackWaypoint(0f, 0f),
        TrackWaypoint(0f, -60f),
        TrackWaypoint(0f, -120f),
        
        // Turn 1 & 2 (Fast right-left chicane)
        TrackWaypoint(20f, -170f),
        TrackWaypoint(70f, -200f),
        TrackWaypoint(130f, -200f),
        TrackWaypoint(170f, -160f),
        
        // Straight 2
        TrackWaypoint(180f, -90f),
        TrackWaypoint(180f, -20f),
        
        // Hairpin Turn (Sector 2)
        TrackWaypoint(210f, 40f),
        TrackWaypoint(230f, 100f),
        TrackWaypoint(190f, 150f),
        TrackWaypoint(120f, 160f),
        TrackWaypoint(70f, 130f),
        
        // Back S-curve
        TrackWaypoint(30f, 180f),
        TrackWaypoint(-30f, 210f),
        TrackWaypoint(-90f, 210f),
        TrackWaypoint(-140f, 170f),
        TrackWaypoint(-160f, 110f),
        
        // Final Turn back to Main Straight
        TrackWaypoint(-160f, 30f),
        TrackWaypoint(-140f, -30f),
        TrackWaypoint(-100f, -50f),
        TrackWaypoint(-50f, 0f),
        TrackWaypoint(-20f, 40f)
    )

    // Sequential Checkpoints (Matching Godot Area3D checkpoints)
    val checkpoints = listOf(
        Checkpoint(0, "START / FINISH", 0f, 0f, 20f),
        Checkpoint(1, "SECTOR 1", 170f, -160f, 25f),
        Checkpoint(2, "HAIRPIN EXIT", 120f, 160f, 25f),
        Checkpoint(3, "FINAL CHICANE", -140f, 110f, 25f)
    )

    /**
     * Finds nearest point on track and returns distance from center line.
     */
    fun getTrackOffset(x: Float, z: Float): Pair<Float, TrackWaypoint> {
        var minDistanceSq = Float.MAX_VALUE
        var nearestWaypoint = waypoints[0]

        for (wp in waypoints) {
            val dx = x - wp.x
            val dz = z - wp.z
            val distSq = dx * dx + dz * dz
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                nearestWaypoint = wp
            }
        }
        return Pair(sqrt(minDistanceSq), nearestWaypoint)
    }

    /**
     * Checks if coordinates are inside a checkpoint zone.
     */
    fun checkCheckpointHit(x: Float, z: Float, checkpoint: Checkpoint): Boolean {
        val dx = x - checkpoint.x
        val dz = z - checkpoint.z
        return (dx * dx + dz * dz) <= (checkpoint.radius * checkpoint.radius)
    }
}

class LapSystem(
    val track: CircuitTrack,
    val totalLaps: Int = 3,
    var onLapComplete: (lap: Int, lapTime: Float, isBest: Boolean) -> Unit = { _, _, _ -> },
    var onRaceFinish: (totalTime: Float, bestLapTime: Float) -> Unit = { _, _ -> },
    var onWrongWay: () -> Unit = {}
) {
    var currentLap: Int = 1
    var nextCheckpointIndex: Int = 1
    var currentLapTimeSec: Float = 0f
    var bestLapTimeSec: Float = 0f
    var totalRaceTimeSec: Float = 0f
    var isRaceActive: Boolean = false
    var isRaceFinished: Boolean = false
    var wrongWayTimer: Float = 0f

    fun update(deltaSec: Float, carX: Float, carZ: Float) {
        if (!isRaceActive || isRaceFinished) return

        currentLapTimeSec += deltaSec
        totalRaceTimeSec += deltaSec

        if (wrongWayTimer > 0f) {
            wrongWayTimer -= deltaSec
        }

        // Check current expected checkpoint
        val targetCp = track.checkpoints[nextCheckpointIndex]
        if (track.checkCheckpointHit(carX, carZ, targetCp)) {
            if (nextCheckpointIndex != 0) {
                // Passed intermediate checkpoint
                nextCheckpointIndex++
                if (nextCheckpointIndex >= track.checkpoints.size) {
                    nextCheckpointIndex = 0 // Next is Finish Line
                }
            } else {
                // Reached finish line with all checkpoints cleared
                completeLap()
            }
        } else {
            // Check if player crossed finish line prematurely without intermediate checkpoints
            if (nextCheckpointIndex != 0 && track.checkCheckpointHit(carX, carZ, track.checkpoints[0])) {
                wrongWayTimer = 2.0f
                onWrongWay()
            }
        }
    }

    private fun completeLap() {
        val lapTime = currentLapTimeSec
        val isNewBest = bestLapTimeSec <= 0f || lapTime < bestLapTimeSec
        if (isNewBest) {
            bestLapTimeSec = lapTime
        }

        onLapComplete(currentLap, lapTime, isNewBest)

        if (currentLap >= totalLaps) {
            isRaceFinished = true
            isRaceActive = false
            onRaceFinish(totalRaceTimeSec, bestLapTimeSec)
        } else {
            currentLap++
            nextCheckpointIndex = 1
            currentLapTimeSec = 0f
        }
    }

    fun startRace() {
        currentLap = 1
        nextCheckpointIndex = 1
        currentLapTimeSec = 0f
        totalRaceTimeSec = 0f
        isRaceActive = true
        isRaceFinished = false
        wrongWayTimer = 0f
    }

    fun reset() {
        currentLap = 1
        nextCheckpointIndex = 1
        currentLapTimeSec = 0f
        totalRaceTimeSec = 0f
        isRaceActive = false
        isRaceFinished = false
        wrongWayTimer = 0f
    }
}
