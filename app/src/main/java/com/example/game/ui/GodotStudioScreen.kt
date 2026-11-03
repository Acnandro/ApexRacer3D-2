package com.example.game.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.godot.GodotProjectData
import com.example.game.physics.VehiclePhysics
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GodotStudioScreen(
    physics: VehiclePhysics,
    onBackToRace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("GDScript Files", "Scene Tree", "Export Guides", "Physics Tuner")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Godot 4 Arcade Studio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToRace,
                        modifier = Modifier.testTag("btn_back_to_game")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to Game",
                            tint = NeonCyan
                        )
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
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = NeonCyan,
                edgePadding = 12.dp
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) NeonCyan else Color.LightGray
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> GDScriptFilesViewer(context)
                1 -> SceneTreeViewer()
                2 -> ExportGuidesViewer()
                3 -> PhysicsTunerTab(physics)
            }
        }
    }
}

@Composable
private fun GDScriptFilesViewer(context: Context) {
    val scripts = listOf(
        "car_controller.gd" to GodotProjectData.CAR_CONTROLLER_GD,
        "touch_hud.gd" to GodotProjectData.TOUCH_HUD_GD,
        "network_manager.gd" to GodotProjectData.NETWORK_MANAGER_GD,
        "minimap.gd" to GodotProjectData.MINIMAP_GD,
        "lap_manager.gd" to GodotProjectData.LAP_MANAGER_GD,
        "ghost_manager.gd" to GodotProjectData.GHOST_MANAGER_GD,
        "project.godot" to GodotProjectData.PROJECT_GODOT
    )
    var selectedIndex by remember { mutableIntStateOf(0) }
    val (filename, code) = scripts[selectedIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal file selector chip row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scripts.forEachIndexed { idx, pair ->
                FilterChip(
                    selected = selectedIndex == idx,
                    onClick = { selectedIndex = idx },
                    label = { Text(pair.first, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        // Action Header: File Name + Copy + Share
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = filename,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Copy Button
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(filename, code))
                        Toast.makeText(context, "$filename copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp)
                }

                // Share Button
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, filename)
                            putExtra(Intent.EXTRA_TEXT, code)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share $filename"))
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 11.sp)
                }
            }
        }

        // Code Content with Line Numbers
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
        ) {
            val lines = code.trim().lines()
            items(lines.size) { lineIdx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${lineIdx + 1}".padStart(3, ' '),
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = lines[lineIdx],
                        color = if (lines[lineIdx].trim().startsWith("#")) Color(0xFF6EE7B7)
                        else if (lines[lineIdx].trim().startsWith("@") || lines[lineIdx].trim().startsWith("func") || lines[lineIdx].trim().startsWith("class_name")) NeonCyan
                        else Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneTreeViewer() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Complete Scene Tree Outline",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hierarchical breakdown of Main.tscn, Car.tscn (VehicleBody3D with 4 VehicleWheel3D nodes), FollowCamera3D, Checkpoints (Area3D), and TouchHUD (CanvasLayer).",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFF090D16),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = GodotProjectData.SCENE_HIERARCHY_TEXT.trim(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF93C5FD),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportGuidesViewer() {
    var selectedGuide by remember { mutableIntStateOf(0) }
    val guides = listOf(
        "Web (HTML5)" to GodotProjectData.EXPORT_GUIDE_WEB,
        "Android (.apk)" to GodotProjectData.EXPORT_GUIDE_ANDROID,
        "iOS (Xcode)" to GodotProjectData.EXPORT_GUIDE_IOS
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            guides.forEachIndexed { index, pair ->
                Button(
                    onClick = { selectedGuide = index },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedGuide == index) NeonCyan else Color(0xFF1E293B),
                        contentColor = if (selectedGuide == index) Color.Black else Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(pair.first, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                item {
                    Text(
                        text = guides[selectedGuide].second.trim(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhysicsTunerTab(physics: VehiclePhysics) {
    val tuning = physics.tuning
    var engineForce by remember { mutableFloatStateOf(tuning.maxEngineForce) }
    var nitroForce by remember { mutableFloatStateOf(tuning.nitroBoostForce) }
    var steerDampening by remember { mutableFloatStateOf(tuning.highSpeedSteerDampening) }
    var driftFriction by remember { mutableFloatStateOf(tuning.driftRearFriction) }
    var maxSteerDeg by remember { mutableFloatStateOf(tuning.maxSteerAngleDeg) }
    var suspensionStiffness by remember { mutableFloatStateOf(tuning.suspensionStiffness) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Vehicle Physics Tuner",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Text(
                        text = "Adjust these parameters in real time. Changes take immediate effect in the 3D racing simulator!",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        item {
            TuningSliderCard(
                label = "Max Engine Force: ${engineForce.toInt()} N",
                description = "Acceleration torque applied to drive wheels (VehicleBody3D.engine_force)",
                value = engineForce,
                range = 200f..700f,
                onValueChange = {
                    engineForce = it
                    tuning.maxEngineForce = it
                }
            )
        }

        item {
            TuningSliderCard(
                label = "Nitro Boost Force: ${nitroForce.toInt()} N",
                description = "Extra thrust added when Nitro is engaged (pushes top speed to 245 km/h)",
                value = nitroForce,
                range = 250f..800f,
                onValueChange = {
                    nitroForce = it
                    tuning.nitroBoostForce = it
                }
            )
        }

        item {
            TuningSliderCard(
                label = "High-Speed Steer Dampening: ${(steerDampening * 100).toInt()}%",
                description = "Dampens steering angle at high speeds to prevent rollover (lerp max_steer_angle)",
                value = steerDampening,
                range = 0.2f..0.85f,
                onValueChange = {
                    steerDampening = it
                    tuning.highSpeedSteerDampening = it
                }
            )
        }

        item {
            TuningSliderCard(
                label = "Handbrake Drift Friction: ${String.format("%.2f", driftFriction)}",
                description = "Lateral rear wheel friction slip when handbraking (VehicleWheel3D.wheel_friction_slip)",
                value = driftFriction,
                range = 0.2f..1.0f,
                onValueChange = {
                    driftFriction = it
                    tuning.driftRearFriction = it
                }
            )
        }

        item {
            TuningSliderCard(
                label = "Max Steering Angle: ${maxSteerDeg.toInt()}°",
                description = "Maximum wheel lock angle at low speeds",
                value = maxSteerDeg,
                range = 18f..40f,
                onValueChange = {
                    maxSteerDeg = it
                    tuning.maxSteerAngleDeg = it
                }
            )
        }

        item {
            TuningSliderCard(
                label = "Suspension Stiffness: ${suspensionStiffness.toInt()}",
                description = "Vehicle spring stiffness dampening pitch & roll during cornering",
                value = suspensionStiffness,
                range = 20f..80f,
                onValueChange = {
                    suspensionStiffness = it
                    tuning.suspensionStiffness = it
                }
            )
        }
    }
}

@Composable
private fun TuningSliderCard(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color(0xFF334155)
                )
            )
        }
    }
}
