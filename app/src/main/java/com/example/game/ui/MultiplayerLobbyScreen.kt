package com.example.game.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.multiplayer.*
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerLobbyScreen(
    currentUser: FirebaseUser?,
    currentLobby: Lobby?,
    playersInLobby: List<PlayerState>,
    leaderboardEntries: List<LeaderboardEntry>,
    isLoading: Boolean,
    errorMessage: String?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onCreateLobby: (name: String, trackId: String, carColor: String, nickname: String) -> Unit,
    onJoinLobby: (code: String, carColor: String, nickname: String) -> Unit,
    onToggleReady: (Boolean) -> Unit,
    onLaunchRace: () -> Unit,
    onLeaveLobby: () -> Unit,
    onBackToRace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Join Match", "Host Match", "Leaderboard")

    // Form inputs
    var joinCodeInput by remember { mutableStateOf("") }
    var hostRoomName by remember { mutableStateOf("Speedway Grand Prix") }
    var selectedTrack by remember { mutableStateOf("pro_speedway") }
    var selectedCarColor by remember { mutableStateOf(CarColorOption.CRIMSON_RED) }
    var nicknameInput by remember(currentUser) {
        mutableStateOf(currentUser?.displayName?.take(16) ?: "Racer ${((100..999).random())}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (currentLobby != null) "Room: ${currentLobby.lobbyCode}" else "Multiplayer Lobbies",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentLobby != null) {
                                onLeaveLobby()
                            }
                            onBackToRace()
                        },
                        modifier = Modifier.testTag("btn_multiplayer_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to race",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (currentUser != null) {
                        FilledTonalButton(
                            onClick = onSignOutClick,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0x33EF4444),
                                contentColor = NeonRed
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Sign Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Error banner if any
            if (errorMessage != null) {
                Surface(
                    color = Color(0x33EF4444),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errorMessage, color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // Auth State & User Card
            if (currentUser == null) {
                UnauthenticatedBanner(onSignInClick = onSignInClick)
            } else if (currentLobby == null) {
                // Not in room yet: Matchmaking Hub
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = NeonPurple,
                    edgePadding = 16.dp
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) NeonPurple else Color.LightGray
                                )
                            }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> JoinMatchTab(
                        joinCode = joinCodeInput,
                        onCodeChange = { joinCodeInput = it.take(6).uppercase() },
                        nickname = nicknameInput,
                        onNicknameChange = { nicknameInput = it },
                        selectedColor = selectedCarColor,
                        onSelectColor = { selectedCarColor = it },
                        isLoading = isLoading,
                        onJoinClick = {
                            if (joinCodeInput.length >= 4) {
                                onJoinLobby(joinCodeInput, selectedCarColor.hex, nicknameInput)
                            } else {
                                Toast.makeText(context, "Please enter a valid 6-character room code", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    1 -> HostMatchTab(
                        roomName = hostRoomName,
                        onRoomNameChange = { hostRoomName = it },
                        selectedTrack = selectedTrack,
                        onSelectTrack = { selectedTrack = it },
                        nickname = nicknameInput,
                        onNicknameChange = { nicknameInput = it },
                        selectedColor = selectedCarColor,
                        onSelectColor = { selectedCarColor = it },
                        isLoading = isLoading,
                        onCreateClick = {
                            onCreateLobby(hostRoomName, selectedTrack, selectedCarColor.hex, nicknameInput)
                        }
                    )
                    2 -> LeaderboardTab(leaderboardEntries = leaderboardEntries)
                }
            } else {
                // Inside an active Lobby Room
                LobbyRoomView(
                    lobby = currentLobby,
                    players = playersInLobby,
                    currentUserId = currentUser.uid,
                    isLoading = isLoading,
                    onToggleReady = onToggleReady,
                    onLaunchRace = onLaunchRace,
                    onLeaveLobby = onLeaveLobby
                )
            }
        }
    }
}

@Composable
private fun UnauthenticatedBanner(onSignInClick: () -> Unit) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = NeonPurple,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Multiplayer Racing Network",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sign in with Google to create or join private circuits, synchronize live car transforms with competitors, and race on global leaderboards.",
                fontSize = 13.sp,
                color = Color.LightGray,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onSignInClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_google_signin")
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun JoinMatchTab(
    joinCode: String,
    onCodeChange: (String) -> Unit,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    selectedColor: CarColorOption,
    onSelectColor: (CarColorOption) -> Unit,
    isLoading: Boolean,
    onJoinClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Enter Room Code", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = onCodeChange,
                        placeholder = { Text("e.g. APX789", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_room_code")
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Racer Nickname", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = onNicknameChange,
                        placeholder = { Text("Racer Name", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Car Team Paint", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    CarColorPickerRow(selected = selectedColor, onSelect = onSelectColor)

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onJoinClick,
                        enabled = !isLoading && joinCode.length >= 4,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_join_room")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join Race Room", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostMatchTab(
    roomName: String,
    onRoomNameChange: (String) -> Unit,
    selectedTrack: String,
    onSelectTrack: (String) -> Unit,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    selectedColor: CarColorOption,
    onSelectColor: (CarColorOption) -> Unit,
    isLoading: Boolean,
    onCreateClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Match Title", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = onRoomNameChange,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_room_name")
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Circuit Selection", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "pro_speedway" to "Speedway",
                            "coastal_drift" to "Coastal",
                            "neon_city" to "Neon City"
                        ).forEach { (id, title) ->
                            val isSel = selectedTrack == id
                            FilterChip(
                                selected = isSel,
                                onClick = { onSelectTrack(id) },
                                label = { Text(title, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Host Racer Nickname", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = onNicknameChange,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonPurple,
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Car Team Paint", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    CarColorPickerRow(selected = selectedColor, onSelect = onSelectColor)

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onCreateClick,
                        enabled = !isLoading && roomName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_create_room")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Host Race Room", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarColorPickerRow(
    selected: CarColorOption,
    onSelect: (CarColorOption) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CarColorOption.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(option.colorValue))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else Color(0x66FFFFFF),
                        shape = CircleShape
                    )
                    .clickable { onSelect(option) }
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun LobbyRoomView(
    lobby: Lobby,
    players: List<PlayerState>,
    currentUserId: String,
    isLoading: Boolean,
    onToggleReady: (Boolean) -> Unit,
    onLaunchRace: () -> Unit,
    onLeaveLobby: () -> Unit
) {
    val context = LocalContext.current
    val isHost = lobby.hostId == currentUserId
    val myPlayer = players.find { it.userId == currentUserId }
    val isReady = myPlayer?.isReady ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Room Code Banner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column {
                    Text("ROOM CODE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = lobby.lobbyCode,
                        color = NeonCyan,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${lobby.name} • 3 Laps",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Apex Racer Room Code", lobby.lobbyCode))
                        Toast.makeText(context, "Room code copied!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Players List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RACERS IN LOBBY (${players.size}/${lobby.maxPlayers})",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (lobby.status == "racing") "RACE IN PROGRESS" else "WAITING FOR LAUNCH",
                color = if (lobby.status == "racing") NeonGreen else NeonYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Connected Racers
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(players) { player ->
                val isMe = player.userId == currentUserId
                val playerColor = try {
                    Color(android.graphics.Color.parseColor(player.carColor))
                } catch (_: Exception) {
                    Color(0xFFE53935)
                }

                Surface(
                    color = if (isMe) Color(0xFF26334D) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isMe) NeonCyan else Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(playerColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = player.displayName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (player.isHost) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = "Host",
                                            tint = NeonYellow,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(You)", color = NeonCyan, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Ready status badge
                        Surface(
                            color = if (player.isReady) Color(0x3322C55E) else Color(0x33F59E0B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (player.isReady) "READY" else "WAITING",
                                color = if (player.isReady) NeonGreen else NeonYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lobby action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onLeaveLobby,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("btn_leave_lobby")
            ) {
                Text("Leave Room", fontWeight = FontWeight.Bold)
            }

            if (!isHost) {
                Button(
                    onClick = { onToggleReady(!isReady) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReady) Color(0xFF334155) else NeonGreen,
                        contentColor = if (isReady) Color.White else Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .testTag("btn_toggle_ready")
                ) {
                    Text(if (isReady) "Cancel Ready" else "Ready Up!", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onLaunchRace,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .testTag("btn_launch_race")
                ) {
                    Icon(Icons.Default.SportsScore, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch Race", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTab(leaderboardEntries: List<LeaderboardEntry>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "GLOBAL CIRCUIT RECORDS",
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (leaderboardEntries.isEmpty()) {
            item {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(
                        text = "No records recorded yet. Complete laps in multiplayer to post high scores!",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(leaderboardEntries) { entry ->
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column {
                            Text(entry.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${entry.trackId} • ${entry.vehicleClass}", color = Color.Gray, fontSize = 11.sp)
                        }
                        Text(
                            text = String.format("%.2fs", entry.bestLapTime),
                            color = NeonYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
