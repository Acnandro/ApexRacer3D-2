package com.example

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import com.example.game.audio.ArcadeSoundManager
import com.example.game.auth.GoogleAuthManager
import com.example.game.ghost.GhostInterpolatedState
import com.example.game.ghost.GhostManager
import com.example.game.multiplayer.*
import com.example.game.physics.VehiclePhysics
import com.example.game.track.CircuitTrack
import com.example.game.track.LapSystem
import com.example.game.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.PI

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: ArcadeSoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        soundManager = ArcadeSoundManager(this)

        setContent {
            MyApplicationTheme {
                MainRacingApp(soundManager = soundManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}

enum class ScreenState {
    RACE_SIMULATOR,
    MULTIPLAYER_LOBBY,
    GODOT_STUDIO
}

@Composable
fun MainRacingApp(soundManager: ArcadeSoundManager) {
    var currentScreen by remember { mutableStateOf(ScreenState.RACE_SIMULATOR) }

    // Persistent storage for Best Lap and Ghost Trajectory
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("apex_racer_records", Context.MODE_PRIVATE) }
    var bestLapRecord by remember { mutableFloatStateOf(prefs.getFloat("best_lap_time", 0f)) }

    // Firebase Auth & Firestore Multiplayer Repository
    val authManager = remember { GoogleAuthManager(context, coroutineScope) }
    val currentUser by authManager.currentUserState.collectAsState()
    val multiplayerRepo = remember { MultiplayerRepository(context) }

    var currentLobbyId by remember { mutableStateOf<String?>(null) }
    var currentLobby by remember { mutableStateOf<Lobby?>(null) }
    var playersInLobby by remember { mutableStateOf<List<PlayerState>>(emptyList()) }
    var leaderboardEntries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isMultiplayerLoading by remember { mutableStateOf(false) }
    var multiplayerErrorMessage by remember { mutableStateOf<String?>(null) }

    // Observe Lobby when inside room
    LaunchedEffect(currentLobbyId) {
        val lobbyId = currentLobbyId
        if (lobbyId != null) {
            multiplayerRepo.observeLobby(lobbyId).collect { lobby ->
                currentLobby = lobby
                if (lobby != null && lobby.status == "racing" && currentScreen == ScreenState.MULTIPLAYER_LOBBY) {
                    currentScreen = ScreenState.RACE_SIMULATOR
                }
            }
        } else {
            currentLobby = null
            playersInLobby = emptyList()
        }
    }

    // Observe Players in Room
    LaunchedEffect(currentLobbyId) {
        val lobbyId = currentLobbyId
        if (lobbyId != null) {
            multiplayerRepo.observePlayers(lobbyId).collect { players ->
                playersInLobby = players
            }
        }
    }

    // Observe Leaderboard
    LaunchedEffect(Unit) {
        multiplayerRepo.observeLeaderboard("pro_speedway")
            .catch { emit(emptyList()) }
            .collect { records ->
                leaderboardEntries = records
            }
    }

    // Physics, Track, and Camera instances
    val physics = remember { VehiclePhysics() }
    val track = remember { CircuitTrack() }
    val camera = remember { Camera3DState() }

    // Ghost Car Manager
    val ghostManager = remember {
        GhostManager().apply {
            val savedGhost = prefs.getString("saved_ghost_trajectory", null)
            if (!savedGhost.isNullOrEmpty()) {
                deserializeGhost(savedGhost)
            }
        }
    }
    var isGhostVisible by remember { mutableStateOf(ghostManager.isGhostVisible) }
    var ghostStateForRender by remember { mutableStateOf<GhostInterpolatedState?>(null) }
    var ghostDeltaSplitSec by remember { mutableStateOf<Float?>(null) }

    // Lap System
    val lapSystem = remember {
        LapSystem(
            track = track,
            totalLaps = 3,
            onLapComplete = { lap, lapTime, isBest ->
                if (isBest) {
                    bestLapRecord = lapTime
                    prefs.edit().putFloat("best_lap_time", lapTime).apply()
                }
                ghostManager.onLapCompleted(lapTime, isBest)
                if (isBest && ghostManager.isGhostAvailable) {
                    prefs.edit().putString("saved_ghost_trajectory", ghostManager.serializeGhost()).apply()
                }
                // Post high score to Firebase Leaderboard if authenticated
                val user = currentUser
                if (user != null && isBest) {
                    coroutineScope.launch {
                        multiplayerRepo.postLapRecord(
                            LeaderboardEntry(
                                userId = user.uid,
                                displayName = user.displayName ?: "Racer",
                                trackId = currentLobby?.trackId ?: "pro_speedway",
                                bestLapTime = lapTime,
                                vehicleClass = "Formula V"
                            )
                        )
                    }
                }
                soundManager.playCheckpointChime()
            },
            onRaceFinish = { _, _ ->
                soundManager.playCheckpointChime()
            },
            onWrongWay = {
                soundManager.playWrongWayBuzzer()
            }
        ).apply {
            bestLapTimeSec = bestLapRecord
        }
    }

    // Input States
    var steerInput by remember { mutableFloatStateOf(0f) }
    var gasInput by remember { mutableFloatStateOf(0f) }
    var brakeInput by remember { mutableFloatStateOf(0f) }
    var handbrakeInput by remember { mutableStateOf(false) }
    var nitroInput by remember { mutableStateOf(false) }

    // HUD & Countdown States
    var countdownText by remember { mutableStateOf<String?>(null) }
    var touchControlsEnabled by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Start 3-2-1-GO! Countdown sequence
    fun startCountdown() {
        lapSystem.reset()
        ghostManager.resetCurrentLapRecording()
        physics.reset(posX = 0f, posZ = 30f, headingRad = -PI.toFloat() / 2f)
        camera.posX = 0f
        camera.posZ = 38f
        camera.yawRad = -PI.toFloat() / 2f
        nitroInput = false
    }

    LaunchedEffect(Unit) {
        startCountdown()
        val steps = listOf("3", "2", "1", "GO!")
        for (step in steps) {
            countdownText = step
            soundManager.playCountdownBeep(isHighPitch = step == "GO!")
            delay(900L)
        }
        countdownText = null
        lapSystem.startRace()
    }

    // 60 FPS Game Loop
    LaunchedEffect(lapSystem.isRaceActive, currentScreen) {
        var lastTime = System.nanoTime()
        var multiplayerSyncTimer = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastTime = now

                if (currentScreen == ScreenState.RACE_SIMULATOR) {
                    val (trackOffset, _) = track.getTrackOffset(physics.state.posX, physics.state.posZ)

                    // Physics Step
                    if (countdownText == null) {
                        physics.update(
                            deltaSec = dt,
                            steerInput = steerInput,
                            accelInput = gasInput,
                            brakeInput = brakeInput,
                            handbrakeInput = handbrakeInput,
                            nitroInput = nitroInput,
                            trackWidth = 24f,
                            trackCenterOffset = trackOffset
                        )

                        // Ghost Recording during active race lap
                        ghostManager.recordSample(
                            timeSec = lapSystem.currentLapTimeSec,
                            posX = physics.state.posX,
                            posZ = physics.state.posZ,
                            headingRad = physics.state.headingRad,
                            steerAngleRad = physics.state.steerAngleRad,
                            isBraking = physics.state.isBraking,
                            isBoosting = physics.state.isBoosting,
                            deltaSec = dt
                        )

                        // Ghost Playback Sampling & Delta Comparison
                        ghostStateForRender = ghostManager.sampleGhost(lapSystem.currentLapTimeSec)
                        ghostDeltaSplitSec = ghostManager.computeTimeDelta(
                            playerX = physics.state.posX,
                            playerZ = physics.state.posZ,
                            playerTimeSec = lapSystem.currentLapTimeSec
                        )
                    } else {
                        ghostStateForRender = null
                        ghostDeltaSplitSec = null
                    }

                    // Camera follow step with nitro boost FOV warp
                    camera.updateFollow(physics.state, dt)

                    // Lap and Checkpoint verification
                    lapSystem.update(dt, physics.state.posX, physics.state.posZ)

                    // Sound update with dynamic nitro rocket hiss
                    soundManager.updateEngineAudio(
                        speedKmh = physics.state.speedKmh,
                        throttle = gasInput,
                        isDrifting = physics.state.isDrifting,
                        isBoosting = physics.state.isBoosting
                    )

                    // Real-Time Multiplayer Telemetry Broadcast (~15Hz)
                    if (currentLobbyId != null && lapSystem.isRaceActive) {
                        multiplayerSyncTimer += dt
                        if (multiplayerSyncTimer >= 0.066f) {
                            multiplayerSyncTimer = 0f
                            val user = currentUser
                            val lobbyId = currentLobbyId
                            if (user != null && lobbyId != null) {
                                val myColor = playersInLobby.find { it.userId == user.uid }?.carColor ?: "#00E5FF"
                                coroutineScope.launch {
                                    multiplayerRepo.updatePlayerSync(
                                        lobbyId = lobbyId,
                                        player = PlayerState(
                                            userId = user.uid,
                                            displayName = user.displayName ?: "Racer",
                                            carColor = myColor,
                                            isReady = true,
                                            isHost = currentLobby?.hostId == user.uid,
                                            posX = physics.state.posX,
                                            posY = 0f,
                                            posZ = physics.state.posZ,
                                            rotY = physics.state.headingRad,
                                            speedKmh = physics.state.speedKmh,
                                            isNitro = physics.state.isBoosting,
                                            isBraking = physics.state.isBraking,
                                            currentLap = lapSystem.currentLap,
                                            checkpointIndex = lapSystem.nextCheckpointIndex,
                                            lapTime = lapSystem.currentLapTimeSec,
                                            bestLapTime = bestLapRecord
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = currentScreen != ScreenState.RACE_SIMULATOR) {
        currentScreen = ScreenState.RACE_SIMULATOR
    }

    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                val isDown = keyEvent.type == KeyEventType.KeyDown
                when (keyEvent.key) {
                    Key.DirectionLeft, Key.A -> {
                        steerInput = if (isDown) -1f else 0f
                        true
                    }
                    Key.DirectionRight, Key.D -> {
                        steerInput = if (isDown) 1f else 0f
                        true
                    }
                    Key.DirectionUp, Key.W -> {
                        gasInput = if (isDown) 1f else 0f
                        true
                    }
                    Key.DirectionDown, Key.S -> {
                        brakeInput = if (isDown) 1f else 0f
                        true
                    }
                    Key.Spacebar -> {
                        handbrakeInput = isDown
                        true
                    }
                    Key.ShiftLeft, Key.ShiftRight, Key.N -> {
                        nitroInput = isDown
                        true
                    }
                    Key.G -> {
                        if (isDown) {
                            isGhostVisible = !isGhostVisible
                            ghostManager.isGhostVisible = isGhostVisible
                        }
                        true
                    }
                    Key.R -> {
                        if (isDown) {
                            physics.reset(posX = 0f, posZ = 30f, headingRad = -PI.toFloat() / 2f)
                            ghostManager.resetCurrentLapRecording()
                            nitroInput = false
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        when (currentScreen) {
            ScreenState.RACE_SIMULATOR -> {
                val competitors = playersInLobby.filter { it.userId != currentUser?.uid }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 3D Canvas with Depth-Sorted Player, Ghost, and Competitor Cars
                    GameCanvasView(
                        physics = physics,
                        track = track,
                        camera = camera,
                        ghostState = ghostStateForRender,
                        remotePlayers = competitors,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Touch HUD & Overlays
                    TouchHUDView(
                        speedKmh = physics.state.speedKmh,
                        nitroFuel = physics.state.nitroFuel,
                        isBoosting = physics.state.isBoosting,
                        currentLap = lapSystem.currentLap,
                        totalLaps = lapSystem.totalLaps,
                        currentLapTimeSec = lapSystem.currentLapTimeSec,
                        bestLapTimeSec = bestLapRecord,
                        ghostDeltaSec = ghostDeltaSplitSec,
                        isGhostAvailable = ghostManager.isGhostAvailable,
                        isGhostVisible = isGhostVisible,
                        onToggleGhostVisible = {
                            isGhostVisible = !isGhostVisible
                            ghostManager.isGhostVisible = isGhostVisible
                        },
                        track = track,
                        playerX = physics.state.posX,
                        playerZ = physics.state.posZ,
                        playerHeadingRad = physics.state.headingRad,
                        ghostState = ghostStateForRender,
                        remotePlayers = competitors,
                        activeLobbyCode = currentLobby?.lobbyCode,
                        onOpenMultiplayerHub = {
                            currentScreen = ScreenState.MULTIPLAYER_LOBBY
                        },
                        countdownText = countdownText,
                        isRaceFinished = lapSystem.isRaceFinished,
                        isWrongWay = lapSystem.wrongWayTimer > 0f,
                        touchControlsEnabled = touchControlsEnabled,
                        onToggleTouchControls = { touchControlsEnabled = !touchControlsEnabled },
                        onSteerChange = { steerInput = it },
                        onGasChange = { gasInput = it },
                        onBrakeChange = { brakeInput = it },
                        onHandbrakeChange = { handbrakeInput = it },
                        onNitroChange = { nitroInput = it },
                        onResetCar = {
                            physics.reset(posX = 0f, posZ = 30f, headingRad = -PI.toFloat() / 2f)
                            ghostManager.resetCurrentLapRecording()
                            nitroInput = false
                        },
                        onRestartRace = {
                            startCountdown()
                            lapSystem.startRace()
                        },
                        onOpenGodotHub = {
                            currentScreen = ScreenState.GODOT_STUDIO
                        },
                        isMuted = isMuted,
                        onToggleMute = {
                            isMuted = !isMuted
                            soundManager.setMuted(isMuted)
                        }
                    )
                }
            }

            ScreenState.MULTIPLAYER_LOBBY -> {
                MultiplayerLobbyScreen(
                    currentUser = currentUser,
                    currentLobby = currentLobby,
                    playersInLobby = playersInLobby,
                    leaderboardEntries = leaderboardEntries,
                    isLoading = isMultiplayerLoading,
                    errorMessage = multiplayerErrorMessage,
                    onSignInClick = {
                        activity?.let { act ->
                            coroutineScope.launch {
                                isMultiplayerLoading = true
                                multiplayerErrorMessage = null
                                val result = authManager.signInWithGoogle(act)
                                if (result.isFailure) {
                                    multiplayerErrorMessage = result.exceptionOrNull()?.localizedMessage
                                }
                                isMultiplayerLoading = false
                            }
                        }
                    },
                    onSignOutClick = {
                        authManager.signOut()
                        currentLobbyId = null
                    },
                    onCreateLobby = { name, trackId, carColor, nickname ->
                        val user = currentUser
                        if (user != null) {
                            coroutineScope.launch {
                                isMultiplayerLoading = true
                                multiplayerErrorMessage = null
                                try {
                                    val (lobbyId, _) = multiplayerRepo.createLobby(
                                        name = name,
                                        hostName = nickname,
                                        trackId = trackId,
                                        carColor = carColor
                                    )
                                    currentLobbyId = lobbyId
                                } catch (e: Exception) {
                                    multiplayerErrorMessage = e.localizedMessage ?: "Failed to create match room"
                                } finally {
                                    isMultiplayerLoading = false
                                }
                            }
                        }
                    },
                    onJoinLobby = { code, carColor, nickname ->
                        val user = currentUser
                        if (user != null) {
                            coroutineScope.launch {
                                isMultiplayerLoading = true
                                multiplayerErrorMessage = null
                                try {
                                    val pair = multiplayerRepo.joinLobbyByCode(
                                        code = code,
                                        displayName = nickname,
                                        carColor = carColor
                                    )
                                    if (pair != null) {
                                        currentLobbyId = pair.first
                                    } else {
                                        multiplayerErrorMessage = "Room '$code' not found or is already full."
                                    }
                                } catch (e: Exception) {
                                    multiplayerErrorMessage = e.localizedMessage ?: "Failed to join room"
                                } finally {
                                    isMultiplayerLoading = false
                                }
                            }
                        }
                    },
                    onToggleReady = { ready ->
                        val lobbyId = currentLobbyId
                        if (lobbyId != null) {
                            coroutineScope.launch {
                                multiplayerRepo.setPlayerReady(lobbyId, ready)
                            }
                        }
                    },
                    onLaunchRace = {
                        val lobbyId = currentLobbyId
                        if (lobbyId != null) {
                            coroutineScope.launch {
                                multiplayerRepo.startRace(lobbyId)
                                currentScreen = ScreenState.RACE_SIMULATOR
                                startCountdown()
                            }
                        }
                    },
                    onLeaveLobby = {
                        val lobbyId = currentLobbyId
                        if (lobbyId != null) {
                            coroutineScope.launch {
                                multiplayerRepo.leaveLobby(lobbyId)
                                currentLobbyId = null
                            }
                        }
                    },
                    onBackToRace = {
                        currentScreen = ScreenState.RACE_SIMULATOR
                    }
                )
            }

            ScreenState.GODOT_STUDIO -> {
                GodotStudioScreen(
                    physics = physics,
                    onBackToRace = { currentScreen = ScreenState.RACE_SIMULATOR }
                )
            }
        }
    }
}
