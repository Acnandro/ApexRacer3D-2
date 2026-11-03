package com.example.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ghost.GhostInterpolatedState
import com.example.game.multiplayer.PlayerState
import com.example.game.track.CircuitTrack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonYellow
import kotlin.math.*

@Composable
fun MinimapView(
    track: CircuitTrack,
    playerX: Float,
    playerZ: Float,
    playerHeadingRad: Float,
    ghostState: GhostInterpolatedState? = null,
    remotePlayers: List<PlayerState> = emptyList(),
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xDD090D16),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
        modifier = modifier
            .size(136.dp)
            .testTag("track_minimap")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val w = size.width
                val h = size.height

                // Bounding box of CircuitTrack
                // X: -160 to +230, Z: -200 to +210
                val minX = -180f
                val maxX = 250f
                val minZ = -220f
                val maxZ = 230f
                val spanX = maxX - minX
                val spanZ = maxZ - minZ

                fun toMinimapOffset(x: Float, z: Float): Offset {
                    val nx = ((x - minX) / spanX).coerceIn(0f, 1f)
                    val nz = ((z - minZ) / spanZ).coerceIn(0f, 1f)
                    return Offset(nx * w, nz * h)
                }

                // 1. Radar background grid rings
                drawRadarRings(w, h)

                // 2. Track ribbon path
                drawMinimapTrack(track, ::toMinimapOffset)

                // 3. Checkpoints
                drawMinimapCheckpoints(track, ::toMinimapOffset)

                // 4. Ghost Car Marker (if available)
                if (ghostState != null) {
                    val ghostPos = toMinimapOffset(ghostState.posX, ghostState.posZ)
                    drawGhostMarker(ghostPos, ghostState.headingRad)
                }

                // 4b. Remote Competitor Markers
                for (competitor in remotePlayers) {
                    val compPos = toMinimapOffset(competitor.posX, competitor.posZ)
                    val compColor = try {
                        Color(android.graphics.Color.parseColor(competitor.carColor))
                    } catch (_: Exception) {
                        Color(0xFFE53935)
                    }
                    drawCircle(
                        color = compColor.copy(alpha = 0.45f),
                        radius = 6.5f,
                        center = compPos
                    )
                    drawCircle(
                        color = compColor,
                        radius = 3.5f,
                        center = compPos
                    )
                }

                // 5. Player Car Marker
                val playerPos = toMinimapOffset(playerX, playerZ)
                drawPlayerMarker(playerPos, playerHeadingRad)
            }

            // Subtitle header
            Text(
                text = "GPS RADAR",
                color = Color(0xFF64748B),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 6.dp)
            )
        }
    }
}

private fun DrawScope.drawRadarRings(w: Float, h: Float) {
    val cx = w / 2f
    val cy = h / 2f
    val maxRadius = min(w, h) / 2f

    // Concentric radar guide circles
    drawCircle(
        color = Color(0xFF1E293B).copy(alpha = 0.6f),
        radius = maxRadius * 0.5f,
        center = Offset(cx, cy),
        style = Stroke(width = 1f)
    )
    drawCircle(
        color = Color(0xFF1E293B).copy(alpha = 0.6f),
        radius = maxRadius * 0.95f,
        center = Offset(cx, cy),
        style = Stroke(width = 1f)
    )

    // Crosshairs
    drawLine(
        color = Color(0xFF1E293B).copy(alpha = 0.4f),
        start = Offset(cx, 0f),
        end = Offset(cx, h),
        strokeWidth = 1f
    )
    drawLine(
        color = Color(0xFF1E293B).copy(alpha = 0.4f),
        start = Offset(0f, cy),
        end = Offset(w, cy),
        strokeWidth = 1f
    )
}

private fun DrawScope.drawMinimapTrack(
    track: CircuitTrack,
    toMinimapOffset: (Float, Float) -> Offset
) {
    val waypoints = track.waypoints
    if (waypoints.isEmpty()) return

    val trackPath = Path()
    val firstPt = toMinimapOffset(waypoints[0].x, waypoints[0].z)
    trackPath.moveTo(firstPt.x, firstPt.y)

    for (i in 1 until waypoints.size) {
        val pt = toMinimapOffset(waypoints[i].x, waypoints[i].z)
        trackPath.lineTo(pt.x, pt.y)
    }
    trackPath.close()

    // Outer glow for track
    drawPath(
        path = trackPath,
        color = Color(0xFF00E5FF).copy(alpha = 0.25f),
        style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Dark asphalt road core
    drawPath(
        path = trackPath,
        color = Color(0xFF1E293B),
        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Center neon path line
    drawPath(
        path = trackPath,
        color = Color(0xFF38BDF8),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawMinimapCheckpoints(
    track: CircuitTrack,
    toMinimapOffset: (Float, Float) -> Offset
) {
    track.checkpoints.forEach { cp ->
        val pos = toMinimapOffset(cp.x, cp.z)
        if (cp.id == 0) {
            // Finish line marker (Gold)
            drawCircle(
                color = NeonYellow,
                radius = 4f,
                center = pos
            )
            drawCircle(
                color = Color.White,
                radius = 2f,
                center = pos
            )
        } else {
            // Intermediate Sector Checkpoints
            drawCircle(
                color = Color(0xFF0284C7),
                radius = 3f,
                center = pos
            )
        }
    }
}

private fun DrawScope.drawGhostMarker(pos: Offset, headingRad: Float) {
    // Semi-transparent purple/cyan holographic pip with subtle radar halo
    drawCircle(
        color = Color(0xFFC084FC).copy(alpha = 0.45f),
        radius = 6.5f,
        center = pos
    )
    drawCircle(
        color = Color(0xFFE879F9),
        radius = 3.5f,
        center = pos
    )
}

private fun DrawScope.drawPlayerMarker(pos: Offset, headingRad: Float) {
    val fwdX = cos(headingRad)
    val fwdZ = sin(headingRad)
    val rightX = -sin(headingRad)
    val rightZ = cos(headingRad)

    val arrowLength = 7.5f
    val arrowWidth = 5f

    // Tip pointing forward in travel direction
    val tip = Offset(pos.x + fwdX * arrowLength, pos.y + fwdZ * arrowLength)
    val leftWing = Offset(pos.x - fwdX * (arrowLength * 0.5f) - rightX * arrowWidth, pos.y - fwdZ * (arrowLength * 0.5f) - rightZ * arrowWidth)
    val rightWing = Offset(pos.x - fwdX * (arrowLength * 0.5f) + rightX * arrowWidth, pos.y - fwdZ * (arrowLength * 0.5f) + rightZ * arrowWidth)

    val playerPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(leftWing.x, leftWing.y)
        lineTo(pos.x, pos.y) // Inset notch
        lineTo(rightWing.x, rightWing.y)
        close()
    }

    // Radar ping pulse
    drawCircle(
        color = NeonCyan.copy(alpha = 0.35f),
        radius = 9f,
        center = pos
    )

    // Directional chevron
    drawPath(playerPath, color = NeonCyan)
    drawPath(playerPath, color = Color.White, style = Stroke(width = 1f))
}
