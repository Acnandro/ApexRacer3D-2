package com.example.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.game.ghost.GhostInterpolatedState
import com.example.game.multiplayer.PlayerState
import com.example.game.physics.VehiclePhysics
import com.example.game.physics.VehicleState
import com.example.game.track.CircuitTrack
import kotlin.math.*

class Camera3DState {
    var posX: Float = 0f
    var posY: Float = 4.2f
    var posZ: Float = 0f
    var yawRad: Float = 0f
    var pitchRad: Float = -0.22f // slight downward tilt
    var boostStretch: Float = 0f
    
    fun updateFollow(carState: VehicleState, deltaSec: Float) {
        val dt = deltaSec.coerceIn(0.001f, 0.05f)
        val carForwardX = cos(carState.headingRad)
        val carForwardZ = sin(carState.headingRad)
        
        val targetStretch = if (carState.isBoosting) 1.25f else 0f
        boostStretch += (targetStretch - boostStretch) * (8f * dt).coerceAtMost(1f)

        val distance = 6.8f + (carState.speedKmh / 190f) * 1.5f + boostStretch * 1.0f
        val height = 3.6f - boostStretch * 0.2f
        val targetX = carState.posX - carForwardX * distance
        val targetZ = carState.posZ - carForwardZ * distance
        
        val lerpFactor = (7.5f * dt).coerceAtMost(1f)
        posX += (targetX - posX) * lerpFactor
        posZ += (targetZ - posZ) * lerpFactor
        posY = height
        
        val lookDx = carState.posX - posX
        val lookDz = carState.posZ - posZ
        val targetYaw = atan2(lookDz, lookDx)
        
        var diff = targetYaw - yawRad
        while (diff > PI) diff -= (2 * PI).toFloat()
        while (diff < -PI) diff += (2 * PI).toFloat()
        yawRad += diff * lerpFactor
    }
}

@Composable
fun GameCanvasView(
    physics: VehiclePhysics,
    track: CircuitTrack,
    camera: Camera3DState,
    ghostState: GhostInterpolatedState? = null,
    remotePlayers: List<PlayerState> = emptyList(),
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B12))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val fovFactor = w * (0.95f + camera.boostStretch * 0.12f)

        // 1. Draw Horizon & Dynamic Sky
        drawSky(w, h, camera.yawRad)

        // 2. Draw 3D Ground & Track
        drawTrackRibbon(track, camera, cx, cy, fovFactor)

        // 3. Draw Checkpoints
        drawCheckpoints(track, camera, cx, cy, fovFactor)

        // 4. Draw Tire Smoke Particles
        drawTireSmoke(physics, camera, cx, cy, fovFactor)

        // 5. Draw Depth-Sorted Cars (Player, Ghost, and Competitors) using Painter's Algorithm
        data class SortableCar(
            val distSq: Float,
            val drawAction: () -> Unit
        )

        val carsToDraw = mutableListOf<SortableCar>()

        // Local Player Car
        val pDistSq = (physics.state.posX - camera.posX).pow(2) + (physics.state.posZ - camera.posZ).pow(2)
        carsToDraw.add(SortableCar(pDistSq) {
            drawPlayerCar(physics.state, camera, cx, cy, fovFactor)
        })

        // Ghost Car
        if (ghostState != null) {
            val gDistSq = (ghostState.posX - camera.posX).pow(2) + (ghostState.posZ - camera.posZ).pow(2)
            carsToDraw.add(SortableCar(gDistSq) {
                drawGhostCar(ghostState, camera, cx, cy, fovFactor)
            })
        }

        // Competitor Cars
        for (competitor in remotePlayers) {
            val cDistSq = (competitor.posX - camera.posX).pow(2) + (competitor.posZ - camera.posZ).pow(2)
            carsToDraw.add(SortableCar(cDistSq) {
                drawCompetitorCar(competitor, camera, cx, cy, fovFactor)
            })
        }

        // Sort descending: farthest drawn first, nearest drawn in front
        carsToDraw.sortByDescending { it.distSq }
        for (car in carsToDraw) {
            car.drawAction()
        }

        // 6. Draw Nitro Flame Particles
        drawNitroFlames(physics, camera, cx, cy, fovFactor)

        // 7. Draw Nitro Warp Speed Lines
        if (physics.state.isBoosting) {
            drawSpeedWarpLines(w, h, cx, cy)
        }
    }
}

private fun DrawScope.drawSky(w: Float, h: Float, yawRad: Float) {
    val horizonY = h * 0.42f
    
    val skyGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF030712),
            Color(0xFF0F172A),
            Color(0xFF1E1B4B),
            Color(0xFF311042),
            Color(0xFF4C1D95)
        ),
        startY = 0f,
        endY = horizonY
    )
    drawRect(brush = skyGradient, size = androidx.compose.ui.geometry.Size(w, horizonY))

    drawLine(
        color = Color(0xFF00E5FF).copy(alpha = 0.45f),
        start = Offset(0f, horizonY),
        end = Offset(w, horizonY),
        strokeWidth = 2.5f
    )

    val mountainPath = Path()
    val scrollOffset = (yawRad / (2 * PI).toFloat()) * w * 2f
    mountainPath.moveTo(0f, horizonY)
    for (i in 0..12) {
        val mx = ((i * 120f + scrollOffset) % (w + 240f)) - 120f
        val my = horizonY - 24f - abs(sin(i * 1.7f)) * 45f
        mountainPath.lineTo(mx, my)
    }
    mountainPath.lineTo(w, horizonY)
    mountainPath.close()
    drawPath(mountainPath, color = Color(0xFF111827).copy(alpha = 0.85f))

    val groundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F1C15),
            Color(0xFF0A130E),
            Color(0xFF050806)
        ),
        startY = horizonY,
        endY = h
    )
    drawRect(
        brush = groundGradient,
        topLeft = Offset(0f, horizonY),
        size = androidx.compose.ui.geometry.Size(w, h - horizonY)
    )
}

private fun project3D(
    wx: Float, wy: Float, wz: Float,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
): Offset? {
    val dx = wx - cam.posX
    val dy = wy - cam.posY
    val dz = wz - cam.posZ

    val cosY = cos(-cam.yawRad + PI.toFloat() / 2f)
    val sinY = sin(-cam.yawRad + PI.toFloat() / 2f)
    val rx = dx * cosY - dz * sinY
    val rz = dx * sinY + dz * cosY

    val cosP = cos(-cam.pitchRad)
    val sinP = sin(-cam.pitchRad)
    val py = dy * cosP - rz * sinP
    val pz = dy * sinP + rz * cosP

    if (pz <= 1.0f || pz > 350f) return null

    val sx = cx + (rx / pz) * fovFactor
    val sy = cy - (py / pz) * fovFactor
    return Offset(sx, sy)
}

private fun DrawScope.drawTrackRibbon(
    track: CircuitTrack,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
) {
    val waypoints = track.waypoints
    val n = waypoints.size

    for (i in 0 until n) {
        val wp1 = waypoints[i]
        val wp2 = waypoints[(i + 1) % n]

        val dirX = wp2.x - wp1.x
        val dirZ = wp2.z - wp1.z
        val len = max(0.1f, sqrt(dirX * dirX + dirZ * dirZ))
        val normX = -dirZ / len
        val normZ = dirX / len

        val halfW = wp1.width / 2f
        val curbW = halfW + 2.5f

        val p1L = project3D(wp1.x - normX * halfW, 0f, wp1.z - normZ * halfW, cam, cx, cy, fovFactor)
        val p1R = project3D(wp1.x + normX * halfW, 0f, wp1.z + normZ * halfW, cam, cx, cy, fovFactor)
        val p2L = project3D(wp2.x - normX * halfW, 0f, wp2.z - normZ * halfW, cam, cx, cy, fovFactor)
        val p2R = project3D(wp2.x + normX * halfW, 0f, wp2.z + normZ * halfW, cam, cx, cy, fovFactor)

        if (p1L != null && p1R != null && p2L != null && p2R != null) {
            val roadPath = Path().apply {
                moveTo(p1L.x, p1L.y)
                lineTo(p1R.x, p1R.y)
                lineTo(p2R.x, p2R.y)
                lineTo(p2L.x, p2L.y)
                close()
            }
            val isAlt = i % 2 == 0
            val roadColor = if (isAlt) Color(0xFF252A36) else Color(0xFF2A303D)
            drawPath(roadPath, color = roadColor)

            val c1L = project3D(wp1.x - normX * curbW, 0.05f, wp1.z - normZ * curbW, cam, cx, cy, fovFactor)
            val c2L = project3D(wp2.x - normX * curbW, 0.05f, wp2.z - normZ * curbW, cam, cx, cy, fovFactor)
            if (c1L != null && c2L != null) {
                val curbPath = Path().apply {
                    moveTo(c1L.x, c1L.y)
                    lineTo(p1L.x, p1L.y)
                    lineTo(p2L.x, p2L.y)
                    lineTo(c2L.x, c2L.y)
                    close()
                }
                drawPath(curbPath, color = if (isAlt) Color(0xFFFF3D00) else Color(0xFFF1F5F9))
            }

            val center1 = project3D(wp1.x, 0.02f, wp1.z, cam, cx, cy, fovFactor)
            val center2 = project3D(wp2.x, 0.02f, wp2.z, cam, cx, cy, fovFactor)
            if (center1 != null && center2 != null && isAlt) {
                drawLine(
                    color = Color(0xFFFFD600).copy(alpha = 0.75f),
                    start = center1,
                    end = center2,
                    strokeWidth = 3f
                )
            }
        }
    }
}

private fun DrawScope.drawCheckpoints(
    track: CircuitTrack,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
) {
    track.checkpoints.forEach { cp ->
        val isFinish = cp.id == 0
        val archTopL = project3D(cp.x - 12f, 7.5f, cp.z, cam, cx, cy, fovFactor)
        val archTopR = project3D(cp.x + 12f, 7.5f, cp.z, cam, cx, cy, fovFactor)
        val archBaseL = project3D(cp.x - 12f, 0f, cp.z, cam, cx, cy, fovFactor)
        val archBaseR = project3D(cp.x + 12f, 0f, cp.z, cam, cx, cy, fovFactor)

        if (archTopL != null && archTopR != null && archBaseL != null && archBaseR != null) {
            val glowColor = if (isFinish) Color(0xFFFFD600) else Color(0xFF00E5FF)
            drawLine(glowColor, archBaseL, archTopL, strokeWidth = 5f)
            drawLine(glowColor, archBaseR, archTopR, strokeWidth = 5f)
            drawLine(glowColor, archTopL, archTopR, strokeWidth = 7f)

            val bannerPath = Path().apply {
                val bannerBottomL = project3D(cp.x - 12f, 5.5f, cp.z, cam, cx, cy, fovFactor)
                val bannerBottomR = project3D(cp.x + 12f, 5.5f, cp.z, cam, cx, cy, fovFactor)
                if (bannerBottomL != null && bannerBottomR != null) {
                    moveTo(archTopL.x, archTopL.y)
                    lineTo(archTopR.x, archTopR.y)
                    lineTo(bannerBottomR.x, bannerBottomR.y)
                    lineTo(bannerBottomL.x, bannerBottomL.y)
                    close()
                }
            }
            drawPath(bannerPath, color = glowColor.copy(alpha = 0.35f))
        }
    }
}

private fun DrawScope.drawTireSmoke(
    physics: VehiclePhysics,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
) {
    physics.getSmokeParticles().forEach { particle ->
        val p = project3D(particle.x, 0.3f, particle.z, cam, cx, cy, fovFactor)
        if (p != null) {
            val alpha = (particle.life / 0.45f).coerceIn(0f, 0.7f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = particle.size * 22f,
                center = p
            )
        }
    }
}

private fun DrawScope.drawNitroFlames(
    physics: VehiclePhysics,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
) {
    physics.getNitroParticles().forEach { particle ->
        val p = project3D(particle.x, particle.y, particle.z, cam, cx, cy, fovFactor)
        if (p != null) {
            val lifeRatio = (particle.life / particle.maxLife).coerceIn(0f, 1f)
            val radius = particle.size * 28f * (1f + (1f - lifeRatio) * 0.8f)

            drawCircle(
                color = Color(0xFF7C3AED).copy(alpha = lifeRatio * 0.45f),
                radius = radius * 1.6f,
                center = p
            )
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = lifeRatio * 0.8f),
                radius = radius,
                center = p
            )
            drawCircle(
                color = Color.White.copy(alpha = lifeRatio),
                radius = radius * 0.45f,
                center = p
            )
        }
    }
}

private fun DrawScope.drawSpeedWarpLines(w: Float, h: Float, cx: Float, cy: Float) {
    val random = java.util.Random(System.currentTimeMillis() / 40)
    for (i in 0..16) {
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val distInner = min(w, h) * (0.35f + random.nextFloat() * 0.15f)
        val distOuter = min(w, h) * (0.55f + random.nextFloat() * 0.3f)
        
        val pStart = Offset(cx + cos(angle) * distInner, cy + sin(angle) * distInner)
        val pEnd = Offset(cx + cos(angle) * distOuter, cy + sin(angle) * distOuter)

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF00E5FF).copy(alpha = 0f), Color(0xFF00E5FF).copy(alpha = 0.65f)),
                start = pStart,
                end = pEnd
            ),
            start = pStart,
            end = pEnd,
            strokeWidth = 2.5f
        )
    }
}

/**
 * Renders the competitive Holographic Ghost Car with semi-transparency and neon edges.
 */
private fun DrawScope.drawGhostCar(
    ghost: GhostInterpolatedState,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
) {
    val fwdX = cos(ghost.headingRad)
    val fwdZ = sin(ghost.headingRad)
    val rightX = -sin(ghost.headingRad)
    val rightZ = cos(ghost.headingRad)

    val halfLen = 2.1f
    val halfW = 1.05f
    val baseH = 0.35f
    val roofH = 1.25f

    val noseX = ghost.posX + fwdX * halfLen
    val noseZ = ghost.posZ + fwdZ * halfLen
    val tailX = ghost.posX - fwdX * halfLen
    val tailZ = ghost.posZ - fwdZ * halfLen

    val fl3D = project3D(noseX - rightX * halfW, baseH, noseZ - rightZ * halfW, cam, cx, cy, fovFactor)
    val fr3D = project3D(noseX + rightX * halfW, baseH, noseZ + rightZ * halfW, cam, cx, cy, fovFactor)
    val rl3D = project3D(tailX - rightX * halfW, baseH, tailZ - rightZ * halfW, cam, cx, cy, fovFactor)
    val rr3D = project3D(tailX + rightX * halfW, baseH, tailZ + rightZ * halfW, cam, cx, cy, fovFactor)

    val rfl3D = project3D(ghost.posX + fwdX * 0.3f - rightX * 0.75f, roofH, ghost.posZ + fwdZ * 0.3f - rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rfr3D = project3D(ghost.posX + fwdX * 0.3f + rightX * 0.75f, roofH, ghost.posZ + fwdZ * 0.3f + rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rrl3D = project3D(ghost.posX - fwdX * 1.1f - rightX * 0.75f, roofH, ghost.posZ - fwdZ * 1.1f - rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rrr3D = project3D(ghost.posX - fwdX * 1.1f + rightX * 0.75f, roofH, ghost.posZ - fwdZ * 1.1f + rightZ * 0.75f, cam, cx, cy, fovFactor)

    if (fl3D != null && fr3D != null && rl3D != null && rr3D != null) {
        val ghostHoloBody = Color(0xFF00E5FF).copy(alpha = 0.32f)
        val ghostWireframe = Color(0xFF67E8F9).copy(alpha = 0.85f)
        val ghostGlass = Color(0xFF93C5FD).copy(alpha = 0.22f)

        // Rear face
        val rearPath = Path().apply {
            moveTo(rl3D.x, rl3D.y)
            lineTo(rr3D.x, rr3D.y)
            if (rrr3D != null && rrl3D != null) {
                lineTo(rrr3D.x, rrr3D.y)
                lineTo(rrl3D.x, rrl3D.y)
            }
            close()
        }
        drawPath(rearPath, color = ghostHoloBody)
        // Wireframe edges
        drawLine(ghostWireframe, rl3D, rr3D, strokeWidth = 2f)

        if (rfl3D != null && rfr3D != null && rrl3D != null && rrr3D != null) {
            val roofPath = Path().apply {
                moveTo(rfl3D.x, rfl3D.y)
                lineTo(rfr3D.x, rfr3D.y)
                lineTo(rrr3D.x, rrr3D.y)
                lineTo(rrl3D.x, rrl3D.y)
                close()
            }
            drawPath(roofPath, color = ghostGlass)
            drawLine(ghostWireframe, rfl3D, rfr3D, strokeWidth = 1.5f)
            drawLine(ghostWireframe, rfr3D, rrr3D, strokeWidth = 1.5f)
            drawLine(ghostWireframe, rrr3D, rrl3D, strokeWidth = 1.5f)
            drawLine(ghostWireframe, rrl3D, rfl3D, strokeWidth = 1.5f)

            // Holographic scanlines across cabin
            val scanMidL = Offset((rfl3D.x + rrl3D.x) / 2f, (rfl3D.y + rrl3D.y) / 2f)
            val scanMidR = Offset((rfr3D.x + rrr3D.x) / 2f, (rfr3D.y + rrr3D.y) / 2f)
            drawLine(ghostWireframe.copy(alpha = 0.5f), scanMidL, scanMidR, strokeWidth = 1.2f)
        }

        // Taillights
        val ghostTailColor = if (ghost.isBraking) Color(0xFFFF5252).copy(alpha = 0.85f) else Color(0xFF38BDF8).copy(alpha = 0.65f)
        drawLine(ghostTailColor, rl3D, rr3D, strokeWidth = 5f)

        // Ghost Spoiler
        val spoilerL = project3D(tailX - rightX * 1.15f, baseH + 0.9f, tailZ - rightZ * 1.15f, cam, cx, cy, fovFactor)
        val spoilerR = project3D(tailX + rightX * 1.15f, baseH + 0.9f, tailZ + rightZ * 1.15f, cam, cx, cy, fovFactor)
        if (spoilerL != null && spoilerR != null) {
            drawLine(ghostWireframe, spoilerL, spoilerR, strokeWidth = 2.5f)
        }

        // Hologram Wheels
        val wlPos = project3D(ghost.posX - fwdX * 1.2f - rightX * 1.05f, 0.3f, ghost.posZ - fwdZ * 1.2f - rightZ * 1.05f, cam, cx, cy, fovFactor)
        val wrPos = project3D(ghost.posX - fwdX * 1.2f + rightX * 1.05f, 0.3f, ghost.posZ - fwdZ * 1.2f + rightZ * 1.05f, cam, cx, cy, fovFactor)
        if (wlPos != null) {
            drawCircle(Color(0xFF0284C7).copy(alpha = 0.3f), radius = 13f, center = wlPos)
            drawCircle(ghostWireframe, radius = 13f, center = wlPos, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }
        if (wrPos != null) {
            drawCircle(Color(0xFF0284C7).copy(alpha = 0.3f), radius = 13f, center = wrPos)
            drawCircle(ghostWireframe, radius = 13f, center = wrPos, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }
    }
}

private fun DrawScope.drawPlayerCar(
    car: VehicleState,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
) {
    val fwdX = cos(car.headingRad)
    val fwdZ = sin(car.headingRad)
    val rightX = -sin(car.headingRad)
    val rightZ = cos(car.headingRad)

    val halfLen = 2.1f
    val halfW = 1.05f
    val baseH = 0.35f + car.suspensionPitch * 0.5f
    val roofH = 1.25f

    val noseX = car.posX + fwdX * halfLen
    val noseZ = car.posZ + fwdZ * halfLen
    val tailX = car.posX - fwdX * halfLen
    val tailZ = car.posZ - fwdZ * halfLen

    val fl3D = project3D(noseX - rightX * halfW, baseH, noseZ - rightZ * halfW, cam, cx, cy, fovFactor)
    val fr3D = project3D(noseX + rightX * halfW, baseH, noseZ + rightZ * halfW, cam, cx, cy, fovFactor)
    val rl3D = project3D(tailX - rightX * halfW, baseH, tailZ - rightZ * halfW, cam, cx, cy, fovFactor)
    val rr3D = project3D(tailX + rightX * halfW, baseH, tailZ + rightZ * halfW, cam, cx, cy, fovFactor)

    val rfl3D = project3D(car.posX + fwdX * 0.3f - rightX * 0.75f, roofH, car.posZ + fwdZ * 0.3f - rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rfr3D = project3D(car.posX + fwdX * 0.3f + rightX * 0.75f, roofH, car.posZ + fwdZ * 0.3f + rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rrl3D = project3D(car.posX - fwdX * 1.1f - rightX * 0.75f, roofH, car.posZ - fwdZ * 1.1f - rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rrr3D = project3D(car.posX - fwdX * 1.1f + rightX * 0.75f, roofH, car.posZ - fwdZ * 1.1f + rightZ * 0.75f, cam, cx, cy, fovFactor)

    if (fl3D != null && fr3D != null && rl3D != null && rr3D != null) {
        val bodyPrimary = if (car.isBoosting) Color(0xFF00F0FF) else Color(0xFF00E5FF)
        val bodyShade = Color(0xFF00838F)
        val windshield = Color(0xFF1E293B)
        val carbonTrim = Color(0xFF111827)

        val rearPath = Path().apply {
            moveTo(rl3D.x, rl3D.y)
            lineTo(rr3D.x, rr3D.y)
            if (rrr3D != null && rrl3D != null) {
                lineTo(rrr3D.x, rrr3D.y)
                lineTo(rrl3D.x, rrl3D.y)
            }
            close()
        }
        drawPath(rearPath, color = bodyShade)

        if (rfl3D != null && rfr3D != null && rrl3D != null && rrr3D != null) {
            val roofPath = Path().apply {
                moveTo(rfl3D.x, rfl3D.y)
                lineTo(rfr3D.x, rfr3D.y)
                lineTo(rrr3D.x, rrr3D.y)
                lineTo(rrl3D.x, rrl3D.y)
                close()
            }
            drawPath(roofPath, color = carbonTrim)

            val rearGlass = Path().apply {
                moveTo(rrl3D.x, rrl3D.y)
                lineTo(rrr3D.x, rrr3D.y)
                lineTo(rr3D.x, rr3D.y - 12f)
                lineTo(rl3D.x, rl3D.y - 12f)
                close()
            }
            drawPath(rearGlass, color = windshield)
        }

        // Taillights
        val brakeGlow = if (car.isBraking) Color(0xFFFF1744) else Color(0xFFB71C1C)
        drawLine(brakeGlow, rl3D, rr3D, strokeWidth = if (car.isBraking) 9f else 4f)

        // Rear Spoiler
        val spoilerL = project3D(tailX - rightX * 1.15f, baseH + 0.9f, tailZ - rightZ * 1.15f, cam, cx, cy, fovFactor)
        val spoilerR = project3D(tailX + rightX * 1.15f, baseH + 0.9f, tailZ + rightZ * 1.15f, cam, cx, cy, fovFactor)
        if (spoilerL != null && spoilerR != null) {
            drawLine(carbonTrim, spoilerL, spoilerR, strokeWidth = 8f)
            drawLine(bodyPrimary, spoilerL, spoilerR, strokeWidth = 3f)
        }

        // Dual Exhaust Tips & Boost Flames
        val pipeSpread = 0.42f
        val pipeBaseL = project3D(tailX - rightX * pipeSpread, baseH - 0.05f, tailZ - rightZ * pipeSpread, cam, cx, cy, fovFactor)
        val pipeBaseR = project3D(tailX + rightX * pipeSpread, baseH - 0.05f, tailZ + rightZ * pipeSpread, cam, cx, cy, fovFactor)

        if (pipeBaseL != null) drawCircle(Color(0xFF334155), radius = 6f, center = pipeBaseL)
        if (pipeBaseR != null) drawCircle(Color(0xFF334155), radius = 6f, center = pipeBaseR)

        // Twin direct exhaust jet cones when actively boosting
        if (car.isBoosting) {
            val flameLen = 1.3f + (sin(System.currentTimeMillis() * 0.05f) * 0.25f).toFloat()
            val flameTipL = project3D(tailX - fwdX * flameLen - rightX * pipeSpread, baseH - 0.05f, tailZ - fwdZ * flameLen - rightZ * pipeSpread, cam, cx, cy, fovFactor)
            val flameTipR = project3D(tailX - fwdX * flameLen + rightX * pipeSpread, baseH - 0.05f, tailZ - fwdZ * flameLen + rightZ * pipeSpread, cam, cx, cy, fovFactor)

            if (pipeBaseL != null && flameTipL != null) {
                drawLine(Color(0xFF00E5FF), pipeBaseL, flameTipL, strokeWidth = 8f)
                drawLine(Color.White, pipeBaseL, flameTipL, strokeWidth = 3.5f)
            }
            if (pipeBaseR != null && flameTipR != null) {
                drawLine(Color(0xFF00E5FF), pipeBaseR, flameTipR, strokeWidth = 8f)
                drawLine(Color.White, pipeBaseR, flameTipR, strokeWidth = 3.5f)
            }
        }

        // 3D Wheels
        val wlPos = project3D(car.posX - fwdX * 1.2f - rightX * 1.05f, 0.3f, car.posZ - fwdZ * 1.2f - rightZ * 1.05f, cam, cx, cy, fovFactor)
        val wrPos = project3D(car.posX - fwdX * 1.2f + rightX * 1.05f, 0.3f, car.posZ - fwdZ * 1.2f + rightZ * 1.05f, cam, cx, cy, fovFactor)
        if (wlPos != null) drawCircle(Color(0xFF0F172A), radius = 14f, center = wlPos)
        if (wrPos != null) drawCircle(Color(0xFF0F172A), radius = 14f, center = wrPos)
    }
}

/**
 * Renders a remote competitor's 3D vehicle with custom team paint, active nitro flames, and a floating nametag.
 */
private fun DrawScope.drawCompetitorCar(
    player: PlayerState,
    cam: Camera3DState,
    cx: Float, cy: Float, fovFactor: Float
) {
    val fwdX = cos(player.rotY)
    val fwdZ = sin(player.rotY)
    val rightX = -sin(player.rotY)
    val rightZ = cos(player.rotY)

    val halfLen = 2.1f
    val halfW = 1.05f
    val baseH = 0.35f
    val roofH = 1.25f

    val noseX = player.posX + fwdX * halfLen
    val noseZ = player.posZ + fwdZ * halfLen
    val tailX = player.posX - fwdX * halfLen
    val tailZ = player.posZ - fwdZ * halfLen

    val fl3D = project3D(noseX - rightX * halfW, baseH, noseZ - rightZ * halfW, cam, cx, cy, fovFactor)
    val fr3D = project3D(noseX + rightX * halfW, baseH, noseZ + rightZ * halfW, cam, cx, cy, fovFactor)
    val rl3D = project3D(tailX - rightX * halfW, baseH, tailZ - rightZ * halfW, cam, cx, cy, fovFactor)
    val rr3D = project3D(tailX + rightX * halfW, baseH, tailZ + rightZ * halfW, cam, cx, cy, fovFactor)

    val rfl3D = project3D(player.posX + fwdX * 0.3f - rightX * 0.75f, roofH, player.posZ + fwdZ * 0.3f - rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rfr3D = project3D(player.posX + fwdX * 0.3f + rightX * 0.75f, roofH, player.posZ + fwdZ * 0.3f + rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rrl3D = project3D(player.posX - fwdX * 1.1f - rightX * 0.75f, roofH, player.posZ - fwdZ * 1.1f - rightZ * 0.75f, cam, cx, cy, fovFactor)
    val rrr3D = project3D(player.posX - fwdX * 1.1f + rightX * 0.75f, roofH, player.posZ - fwdZ * 1.1f + rightZ * 0.75f, cam, cx, cy, fovFactor)

    if (fl3D != null && fr3D != null && rl3D != null && rr3D != null) {
        val carColorParsed = try {
            Color(android.graphics.Color.parseColor(player.carColor))
        } catch (_: Exception) {
            Color(0xFFE53935)
        }
        val carShade = carColorParsed.copy(alpha = 0.8f)
        val carbonTrim = Color(0xFF1E293B)

        // Rear chassis
        val rearPath = Path().apply {
            moveTo(rl3D.x, rl3D.y)
            lineTo(rr3D.x, rr3D.y)
            if (rrr3D != null && rrl3D != null) {
                lineTo(rrr3D.x, rrr3D.y)
                lineTo(rrl3D.x, rrl3D.y)
            }
            close()
        }
        drawPath(rearPath, color = carShade)

        if (rfl3D != null && rfr3D != null && rrl3D != null && rrr3D != null) {
            val roofPath = Path().apply {
                moveTo(rfl3D.x, rfl3D.y)
                lineTo(rfr3D.x, rfr3D.y)
                lineTo(rrr3D.x, rrr3D.y)
                lineTo(rrl3D.x, rrl3D.y)
                close()
            }
            drawPath(roofPath, color = carbonTrim)
        }

        // Taillights
        val brakeGlow = if (player.isBraking) Color(0xFFFF1744) else Color(0xFFB71C1C)
        drawLine(brakeGlow, rl3D, rr3D, strokeWidth = if (player.isBraking) 8f else 4f)

        // Spoiler
        val spoilerL = project3D(tailX - rightX * 1.15f, baseH + 0.9f, tailZ - rightZ * 1.15f, cam, cx, cy, fovFactor)
        val spoilerR = project3D(tailX + rightX * 1.15f, baseH + 0.9f, tailZ + rightZ * 1.15f, cam, cx, cy, fovFactor)
        if (spoilerL != null && spoilerR != null) {
            drawLine(carColorParsed, spoilerL, spoilerR, strokeWidth = 3.5f)
        }

        // Nitro exhaust flames if competitor is boosting
        if (player.isNitro) {
            val flameLen = 1.2f
            val flameTipL = project3D(tailX - fwdX * flameLen - rightX * 0.42f, baseH - 0.05f, tailZ - fwdZ * flameLen - rightZ * 0.42f, cam, cx, cy, fovFactor)
            val flameTipR = project3D(tailX - fwdX * flameLen + rightX * 0.42f, baseH - 0.05f, tailZ - fwdZ * flameLen + rightZ * 0.42f, cam, cx, cy, fovFactor)
            val pipeL = project3D(tailX - rightX * 0.42f, baseH - 0.05f, tailZ - rightZ * 0.42f, cam, cx, cy, fovFactor)
            val pipeR = project3D(tailX + rightX * 0.42f, baseH - 0.05f, tailZ + rightZ * 0.42f, cam, cx, cy, fovFactor)

            if (pipeL != null && flameTipL != null) {
                drawLine(Color(0xFF00E5FF), pipeL, flameTipL, strokeWidth = 6f)
            }
            if (pipeR != null && flameTipR != null) {
                drawLine(Color(0xFF00E5FF), pipeR, flameTipR, strokeWidth = 6f)
            }
        }

        // Wheels
        val wlPos = project3D(player.posX - fwdX * 1.2f - rightX * 1.05f, 0.3f, player.posZ - fwdZ * 1.2f - rightZ * 1.05f, cam, cx, cy, fovFactor)
        val wrPos = project3D(player.posX - fwdX * 1.2f + rightX * 1.05f, 0.3f, player.posZ - fwdZ * 1.2f + rightZ * 1.05f, cam, cx, cy, fovFactor)
        if (wlPos != null) drawCircle(Color(0xFF111827), radius = 12f, center = wlPos)
        if (wrPos != null) drawCircle(Color(0xFF111827), radius = 12f, center = wrPos)

        // Floating Nametag billboard badge above car
        val tagPos = project3D(player.posX, 2.5f, player.posZ, cam, cx, cy, fovFactor)
        if (tagPos != null) {
            val badgeW = 74f
            val badgeH = 20f
            drawRoundRect(
                color = Color(0xDD0F172A),
                topLeft = Offset(tagPos.x - badgeW / 2f, tagPos.y - badgeH / 2f),
                size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = carColorParsed,
                topLeft = Offset(tagPos.x - badgeW / 2f, tagPos.y - badgeH / 2f),
                size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)
            )
            // Color pip
            drawCircle(
                color = carColorParsed,
                radius = 4f,
                center = Offset(tagPos.x - badgeW / 2f + 8f, tagPos.y)
            )
        }
    }
}
