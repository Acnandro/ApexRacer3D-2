package com.example.game.ghost

import kotlin.math.*

data class GhostFrame(
    val timeSec: Float,
    val posX: Float,
    val posZ: Float,
    val headingRad: Float,
    val steerAngleRad: Float,
    val isBraking: Boolean,
    val isBoosting: Boolean
)

data class GhostInterpolatedState(
    val posX: Float,
    val posZ: Float,
    val headingRad: Float,
    val steerAngleRad: Float,
    val isBraking: Boolean,
    val isBoosting: Boolean
)

class GhostManager {
    // Current ongoing lap recording buffer (sampled every ~0.05s)
    private val currentLapFrames = mutableListOf<GhostFrame>()
    private var recordSampleTimer: Float = 0f
    private val sampleIntervalSec: Float = 0.05f // 20 Hz recording rate

    // Best lap ghost data
    var bestGhostFrames: List<GhostFrame> = emptyList()
        private set
    var bestGhostLapTime: Float = 0f
        private set
    var isGhostAvailable: Boolean = false
        private set
    var isGhostVisible: Boolean = true

    fun resetCurrentLapRecording() {
        currentLapFrames.clear()
        recordSampleTimer = 0f
    }

    fun recordSample(
        timeSec: Float,
        posX: Float,
        posZ: Float,
        headingRad: Float,
        steerAngleRad: Float,
        isBraking: Boolean,
        isBoosting: Boolean,
        deltaSec: Float
    ) {
        recordSampleTimer += deltaSec
        if (recordSampleTimer >= sampleIntervalSec || currentLapFrames.isEmpty()) {
            recordSampleTimer = 0f
            currentLapFrames.add(
                GhostFrame(
                    timeSec = timeSec,
                    posX = posX,
                    posZ = posZ,
                    headingRad = headingRad,
                    steerAngleRad = steerAngleRad,
                    isBraking = isBraking,
                    isBoosting = isBoosting
                )
            )
        }
    }

    fun onLapCompleted(lapTime: Float, isBest: Boolean) {
        if (isBest && currentLapFrames.isNotEmpty()) {
            bestGhostFrames = ArrayList(currentLapFrames)
            bestGhostLapTime = lapTime
            isGhostAvailable = true
        }
        resetCurrentLapRecording()
    }

    /**
     * Replays the ghost at the specified lap timestamp with linear and angular interpolation.
     */
    fun sampleGhost(lapTimeSec: Float): GhostInterpolatedState? {
        if (!isGhostAvailable || !isGhostVisible || bestGhostFrames.size < 2) return null

        val frames = bestGhostFrames
        if (lapTimeSec <= frames.first().timeSec) {
            val f = frames.first()
            return GhostInterpolatedState(f.posX, f.posZ, f.headingRad, f.steerAngleRad, f.isBraking, f.isBoosting)
        }
        if (lapTimeSec >= frames.last().timeSec) {
            val f = frames.last()
            return GhostInterpolatedState(f.posX, f.posZ, f.headingRad, f.steerAngleRad, f.isBraking, f.isBoosting)
        }

        // Binary search to find surrounding frames
        var low = 0
        var high = frames.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (frames[mid].timeSec < lapTimeSec) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        val idxA = (low - 1).coerceIn(0, frames.size - 2)
        val idxB = idxA + 1
        val frameA = frames[idxA]
        val frameB = frames[idxB]

        val span = max(0.0001f, frameB.timeSec - frameA.timeSec)
        val t = ((lapTimeSec - frameA.timeSec) / span).coerceIn(0f, 1f)

        val posX = frameA.posX + (frameB.posX - frameA.posX) * t
        val posZ = frameA.posZ + (frameB.posZ - frameA.posZ) * t
        val steer = frameA.steerAngleRad + (frameB.steerAngleRad - frameA.steerAngleRad) * t

        // Slerp heading angle
        var diff = frameB.headingRad - frameA.headingRad
        while (diff > PI) diff -= (2 * PI).toFloat()
        while (diff < -PI) diff += (2 * PI).toFloat()
        val heading = frameA.headingRad + diff * t

        return GhostInterpolatedState(
            posX = posX,
            posZ = posZ,
            headingRad = heading,
            steerAngleRad = steer,
            isBraking = if (t > 0.5f) frameB.isBraking else frameA.isBraking,
            isBoosting = if (t > 0.5f) frameB.isBoosting else frameA.isBoosting
        )
    }

    /**
     * Calculates the distance and time delta between player and ghost.
     * Returns: positive if player is ahead of ghost (leading), negative if falling behind.
     */
    fun computeTimeDelta(playerX: Float, playerZ: Float, playerTimeSec: Float): Float? {
        if (!isGhostAvailable || bestGhostFrames.size < 2) return null

        // Find closest point in ghost trajectory to player's current location
        var minDistanceSq = Float.MAX_VALUE
        var closestGhostTime = playerTimeSec

        for (frame in bestGhostFrames) {
            val dx = playerX - frame.x_or_posX(frame)
            val dz = playerZ - frame.posZ
            val dSq = dx * dx + dz * dz
            if (dSq < minDistanceSq) {
                minDistanceSq = dSq
                closestGhostTime = frame.timeSec
            }
        }

        // If player reached this position earlier than ghost reached it, delta is positive (ahead)
        return closestGhostTime - playerTimeSec
    }

    private fun GhostFrame.x_or_posX(f: GhostFrame) = f.posX

    fun serializeGhost(): String {
        if (!isGhostAvailable || bestGhostFrames.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append(bestGhostLapTime).append(";")
        for (f in bestGhostFrames) {
            sb.append(String.format(java.util.Locale.US, "%.2f,%.2f,%.2f,%.2f;", f.timeSec, f.posX, f.posZ, f.headingRad))
        }
        return sb.toString()
    }

    fun deserializeGhost(serialized: String) {
        if (serialized.isEmpty()) return
        try {
            val parts = serialized.split(";")
            if (parts.size < 3) return
            bestGhostLapTime = parts[0].toFloatOrNull() ?: return
            val frames = mutableListOf<GhostFrame>()
            for (i in 1 until parts.size) {
                val token = parts[i].trim()
                if (token.isEmpty()) continue
                val coords = token.split(",")
                if (coords.size >= 4) {
                    val time = coords[0].toFloat()
                    val x = coords[1].toFloat()
                    val z = coords[2].toFloat()
                    val heading = coords[3].toFloat()
                    frames.add(GhostFrame(time, x, z, heading, 0f, false, false))
                }
            }
            if (frames.size >= 2) {
                bestGhostFrames = frames
                isGhostAvailable = true
            }
        } catch (_: Exception) {}
    }
}
