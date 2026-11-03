package com.example.game.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ghost.GhostInterpolatedState
import com.example.game.multiplayer.PlayerState
import com.example.game.track.CircuitTrack
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TouchHUDView(
    speedKmh: Float,
    nitroFuel: Float,
    isBoosting: Boolean,
    currentLap: Int,
    totalLaps: Int,
    currentLapTimeSec: Float,
    bestLapTimeSec: Float,
    ghostDeltaSec: Float?,
    isGhostAvailable: Boolean,
    isGhostVisible: Boolean,
    onToggleGhostVisible: () -> Unit,
    track: CircuitTrack,
    playerX: Float,
    playerZ: Float,
    playerHeadingRad: Float,
    ghostState: GhostInterpolatedState? = null,
    remotePlayers: List<PlayerState> = emptyList(),
    activeLobbyCode: String? = null,
    onOpenMultiplayerHub: () -> Unit = {},
    countdownText: String?,
    isRaceFinished: Boolean,
    isWrongWay: Boolean,
    touchControlsEnabled: Boolean,
    onToggleTouchControls: () -> Unit,
    onSteerChange: (Float) -> Unit, // -1f (left), +1f (right), 0f (neutral)
    onGasChange: (Float) -> Unit,    // 1f pressed, 0f released
    onBrakeChange: (Float) -> Unit,  // 1f pressed, 0f released
    onHandbrakeChange: (Boolean) -> Unit,
    onNitroChange: (Boolean) -> Unit,
    onResetCar: () -> Unit,
    onRestartRace: () -> Unit,
    onOpenGodotHub: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // --- Top Bar: Telemetry, Lap Counters, Ghost Benchmark & Controls ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Lap, Time & Ghost Card
            Surface(
                color = Color(0xDD0B0F19),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.testTag("lap_telemetry_card")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LAP $currentLap / $totalLaps",
                            color = NeonCyan,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "TIME: ${formatTime(currentLapTimeSec)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "BEST: ${if (bestLapTimeSec <= 0f) "--:--.--" else formatTime(bestLapTimeSec)}",
                        color = if (bestLapTimeSec > 0f) NeonYellow else Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Ghost Car Split Delta
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ghostDeltaText = when {
                            !isGhostAvailable -> "GHOST: REC..."
                            !isGhostVisible -> "GHOST: HIDDEN"
                            ghostDeltaSec == null -> "GHOST: --.--"
                            ghostDeltaSec >= 0f -> String.format(java.util.Locale.US, "GHOST: +%.2fs", ghostDeltaSec)
                            else -> String.format(java.util.Locale.US, "GHOST: -%.2fs", -ghostDeltaSec)
                        }
                        val ghostColor = when {
                            !isGhostAvailable -> Color(0xFF94A3B8)
                            !isGhostVisible -> Color.Gray
                            ghostDeltaSec != null && ghostDeltaSec >= 0f -> NeonGreen
                            ghostDeltaSec != null && ghostDeltaSec < 0f -> NeonRed
                            else -> NeonCyan
                        }

                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Ghost benchmark",
                            tint = ghostColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ghostDeltaText,
                            color = ghostColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Live Multiplayer Position Standing Pill
                    if (activeLobbyCode != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Surface(
                            color = Color(0x33A855F7),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple)
                        ) {
                            Text(
                                text = "ONLINE RACE • ${1 + remotePlayers.size} CARS",
                                color = NeonPurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Top Right: Ghost Toggle, Sound Toggle, Multiplayer Hub, Godot Code Hub, Reset Car
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ghost Visibility Toggle
                IconButton(
                    onClick = onToggleGhostVisible,
                    modifier = Modifier
                        .background(Color(0xCC0F172A), CircleShape)
                        .size(42.dp)
                        .testTag("btn_ghost_toggle")
                ) {
                    Icon(
                        imageVector = if (isGhostVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Ghost Car",
                        tint = if (isGhostVisible && isGhostAvailable) NeonCyan else Color.Gray
                    )
                }

                // Sound Mute Toggle
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .background(Color(0xCC0F172A), CircleShape)
                        .size(42.dp)
                        .testTag("btn_mute_toggle")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Toggle Audio",
                        tint = if (isMuted) Color.Gray else NeonCyan
                    )
                }

                // Multiplayer Lobbies Button
                FilledTonalButton(
                    onClick = onOpenMultiplayerHub,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (activeLobbyCode != null) NeonPurple else Color(0xFF7C3AED),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(42.dp).testTag("btn_multiplayer_hub")
                ) {
                    Icon(Icons.Default.Groups, contentDescription = "Multiplayer", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeLobbyCode != null) "Room: $activeLobbyCode" else "Multiplayer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Godot Studio & Scripts Button
                FilledTonalButton(
                    onClick = onOpenGodotHub,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(42.dp).testTag("btn_godot_hub")
                ) {
                    Icon(Icons.Default.Code, contentDescription = "Godot Scripts", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Godot 4 Hub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Reset Car Button
                IconButton(
                    onClick = onResetCar,
                    modifier = Modifier
                        .background(Color(0xCC334155), CircleShape)
                        .size(42.dp)
                        .testTag("btn_reset_car")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Vehicle",
                        tint = NeonOrange
                    )
                }
            }
        }

        // --- Top-Left: Real-Time Track Minimap Radar & HUD Toggle ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 112.dp)
        ) {
            MinimapView(
                track = track,
                playerX = playerX,
                playerZ = playerZ,
                playerHeadingRad = playerHeadingRad,
                ghostState = ghostState,
                remotePlayers = remotePlayers
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onToggleTouchControls,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (touchControlsEnabled) NeonCyan else Color.Gray
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("btn_toggle_touch_hud")
            ) {
                Text(
                    text = "Touch HUD: ${if (touchControlsEnabled) "ON" else "OFF"}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // --- Center Bottom: Speedometer & Rechargeable Nitro Gauge ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .testTag("speedometer_hud")
        ) {
            // Nitro Boost Gauge
            Surface(
                color = Color(0xDD0B0F19),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBoosting) NeonCyan else Color(0xFF1E293B)
                ),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("nitro_gauge_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Nitro",
                        tint = if (isBoosting) Color(0xFF00E5FF) else if (nitroFuel > 20f) Color(0xFF38BDF8) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NITRO",
                        color = if (isBoosting) NeonCyan else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    val nitroRatio = (nitroFuel / 100f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(nitroRatio)
                                .background(
                                    if (isBoosting) {
                                        Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFC084FC), Color.White))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)))
                                    }
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${nitroFuel.roundToInt()}%",
                        color = if (isBoosting) NeonCyan else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Speedometer Widget
            Surface(
                color = Color(0xEE0B0F19),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isBoosting) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.5f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    val gear = when {
                        speedKmh < 0.5f -> "N"
                        speedKmh < 35f -> "1"
                        speedKmh < 70f -> "2"
                        speedKmh < 110f -> "3"
                        speedKmh < 145f -> "4"
                        speedKmh < 175f -> "5"
                        else -> "6"
                    }
                    Text(
                        text = "GEAR $gear",
                        color = NeonOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 3.dp, end = 12.dp)
                    )
                    Text(
                        text = "${speedKmh.roundToInt()}",
                        color = if (isBoosting) NeonCyan else Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "KM/H",
                        color = NeonCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val speedProgress = (speedKmh / 245f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(speedProgress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeonCyan, NeonYellow, NeonRed)
                            )
                        )
                )
            }
        }

        // --- Left Touch Controls: Steer Left & Steer Right ---
        if (touchControlsEnabled) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TouchActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "LEFT",
                    accentColor = NeonCyan,
                    testTag = "btn_touch_steer_left",
                    onPress = { onSteerChange(-1f) },
                    onRelease = { onSteerChange(0f) }
                )
                TouchActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = "RIGHT",
                    accentColor = NeonCyan,
                    testTag = "btn_touch_steer_right",
                    onPress = { onSteerChange(1f) },
                    onRelease = { onSteerChange(0f) }
                )
            }

            // --- Right Touch Controls: Handbrake Drift, Nitro Boost, Brake/Reverse, Gas Pedal ---
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Handbrake Button
                TouchActionButton(
                    icon = Icons.Default.FlashOn,
                    label = "DRIFT",
                    accentColor = NeonYellow,
                    isSmall = true,
                    testTag = "btn_touch_handbrake",
                    onPress = { onHandbrakeChange(true) },
                    onRelease = { onHandbrakeChange(false) }
                )

                // Nitro Boost Button
                TouchActionButton(
                    icon = Icons.Default.Bolt,
                    label = "NITRO",
                    accentColor = Color(0xFF00E5FF),
                    isProminent = false,
                    isSmall = false,
                    testTag = "btn_touch_nitro",
                    onPress = { onNitroChange(true) },
                    onRelease = { onNitroChange(false) }
                )

                // Brake / Reverse Pedal
                TouchActionButton(
                    icon = Icons.Default.VerticalAlignBottom,
                    label = "BRAKE",
                    accentColor = NeonRed,
                    testTag = "btn_touch_brake",
                    onPress = { onBrakeChange(1f) },
                    onRelease = { onBrakeChange(0f) }
                )

                // Gas Pedal (Accelerate)
                TouchActionButton(
                    icon = Icons.Default.VerticalAlignTop,
                    label = "GAS",
                    accentColor = NeonGreen,
                    isProminent = true,
                    testTag = "btn_touch_gas",
                    onPress = { onGasChange(1f) },
                    onRelease = { onGasChange(0f) }
                )
            }
        }

        // --- Wrong Way / Missed Checkpoint Banner ---
        AnimatedVisibility(
            visible = isWrongWay,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp)
        ) {
            Surface(
                color = Color(0xEEB71C1C),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, NeonRed)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WRONG WAY / MISSED CHECKPOINT!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- 3-2-1-GO! Countdown Animation ---
        if (countdownText != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Text(
                    text = countdownText,
                    color = if (countdownText == "GO!") NeonGreen else NeonCyan,
                    fontSize = if (countdownText == "GO!") 84.sp else 96.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.scale(pulseScale)
                )
            }
        }

        // --- Race Finished Modal ---
        if (isRaceFinished) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xBB000000))
                    .clickable(enabled = false) {}
            ) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, NeonCyan),
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .padding(24.dp)
                        .testTag("race_finish_modal")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Trophy",
                            tint = NeonYellow,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "RACE FINISHED!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Best Lap: ${formatTime(bestLapTimeSec)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonYellow,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All 3 Laps Completed Safely",
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onRestartRace,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_modal_restart")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RESTART RACE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    testTag: String,
    isProminent: Boolean = false,
    isSmall: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }

    val btnSize = when {
        isProminent -> 72.dp
        isSmall -> 52.dp
        else -> 60.dp
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(btnSize)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPressed) accentColor else Color(0xDD0F172A)
            )
            .border(
                width = if (isPressed) 2.5.dp else 1.5.dp,
                color = accentColor,
                shape = RoundedCornerShape(18.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress()
                        tryAwaitRelease()
                        isPressed = false
                        onRelease()
                    }
                )
            }
            .testTag(testTag)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPressed) Color.Black else accentColor,
                modifier = Modifier.size(if (isSmall) 20.dp else 26.dp)
            )
            Text(
                text = label,
                fontSize = if (isSmall) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) Color.Black else Color.White
            )
        }
    }
}

fun formatTime(seconds: Float): String {
    val sec = seconds.coerceAtLeast(0f)
    val mins = (sec / 60).toInt()
    val remainderSec = (sec % 60).toInt()
    val millis = ((sec - sec.toInt()) * 100).toInt()
    return String.format("%02d:%02d.%02d", mins, remainderSec, millis)
}
