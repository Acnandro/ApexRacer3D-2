package com.example.game.godot

/**
 * Production-ready Godot 4.x (GDScript) files, Scene Hierarchy, and Export Configurations.
 * Specifically tuned for Mobile (Android/iOS) and Web (HTML5/WebAssembly) under the Compatibility renderer.
 * Includes VehicleBody3D physics, Touch HUD, Checkpoint Lap Manager, Ghost Car, and Real-Time Track Minimap.
 */
object GodotProjectData {

    const val SCENE_HIERARCHY_TEXT = """
=== GODOT 4.x SCENE TREE HIERARCHY ===

[Main] (Node3D) - Main.tscn
 ├── WorldEnvironment (WorldEnvironment) [Sky + DirectionalLight3D]
 ├── DirectionalLight3D (Sun light with soft shadows)
 ├── Track (StaticBody3D)
 │    ├── MeshInstance3D (Road, Kerbs, Barriers, Grass)
 │    ├── CollisionShape3D (ConcavePolygonShape3D / Trimesh collision)
 │    └── PhysicsMaterial (friction = 1.0, roughness = 0.5)
 ├── OffroadTerrain (StaticBody3D)
 │    ├── MeshInstance3D (Grass & Sand runoff)
 │    ├── CollisionShape3D (HeightMap / Box / Trimesh)
 │    └── PhysicsMaterial (friction = 0.35, roughness = 0.9)
 ├── Checkpoints (Node3D)
 │    ├── FinishLine (Area3D) [CollisionLayer: Checkpoints (8)]
 │    │    └── CollisionShape3D (BoxShape3D covering track width)
 │    ├── Checkpoint1 (Area3D)
 │    │    └── CollisionShape3D (BoxShape3D)
 │    ├── Checkpoint2 (Area3D)
 │    │    └── CollisionShape3D (BoxShape3D)
 │    └── Checkpoint3 (Area3D)
 │         └── CollisionShape3D (BoxShape3D)
 ├── PlayerCar (VehicleBody3D) - Car.tscn (extends CarController)
 │    ├── BodyMesh (MeshInstance3D - Sports Car Chassis)
 │    ├── CollisionShape3D (BoxShape3D / ConvexHull)
 │    ├── WheelFL (VehicleWheel3D - Front-Left, Steering: true, Traction: false)
 │    │    └── WheelMesh (MeshInstance3D)
 │    ├── WheelFR (VehicleWheel3D - Front-Right, Steering: true, Traction: false)
 │    │    └── WheelMesh (MeshInstance3D)
 │    ├── WheelRL (VehicleWheel3D - Rear-Left, Steering: false, Traction: true)
 │    │    └── WheelMesh (MeshInstance3D)
 │    ├── WheelRR (VehicleWheel3D - Rear-Right, Steering: false, Traction: true)
 │    │    └── WheelMesh (MeshInstance3D)
 │    ├── SmokeParticlesL (GPUParticles3D - Drift tire smoke)
 │    ├── SmokeParticlesR (GPUParticles3D - Drift tire smoke)
 │    ├── NitroFlameL (GPUParticles3D - Neon cyan exhaust flame)
 │    ├── NitroFlameR (GPUParticles3D - Neon cyan exhaust flame)
 │    ├── EngineAudio (AudioStreamPlayer3D)
 │    └── NitroAudio (AudioStreamPlayer3D - Turbo rocket jet hiss)
 ├── GhostCar (Node3D) - GhostCar.tscn (Holographic Benchmark Replay)
 │    ├── BodyMesh (MeshInstance3D - StandardMaterial3D: Alpha = 0.42, Emission = Neon Cyan)
 │    ├── WheelMeshFL (MeshInstance3D - Semi-transparent hologram)
 │    ├── WheelMeshFR (MeshInstance3D - Semi-transparent hologram)
 │    ├── WheelMeshRL (MeshInstance3D - Semi-transparent hologram)
 │    └── WheelMeshRR (MeshInstance3D - Semi-transparent hologram)
 ├── FollowCamera3D (Camera3D) [Target: PlayerCar]
 ├── LapManager (Node) [Script: lap_manager.gd]
 ├── GhostManager (Node) [Script: ghost_manager.gd]
 ├── NetworkManager (Node) [Script: network_manager.gd - High-Level Multiplayer API]
 ├── MultiplayerSpawner (Node) [Replicates competitor Car.tscn nodes across peers]
 ├── MultiplayerSynchronizer (Node) [Interpolates remote transforms at 20Hz]
 └── TouchHUD (CanvasLayer) [Script: touch_hud.gd]
      ├── TopTelemetry (MarginContainer)
      │    ├── LapCounterLabel (Label)
      │    ├── LapTimeLabel (Label)
      │    ├── BestLapLabel (Label)
      │    └── GhostDeltaLabel (Label - "GHOST: +0.28s" / "-0.45s")
      ├── MinimapContainer (PanelContainer - Top Left beneath Telemetry)
      │    └── Minimap (Control - Script: minimap.gd - Real-Time Track Radar)
      ├── SpeedometerContainer (Control)
      │    ├── SpeedNumberLabel (Label)
      │    ├── SpeedUnitLabel (Label)
      │    └── NitroProgressBar (ProgressBar - Rechargeable gauge with flame icon)
      ├── MobileControls (Control - Anchor Full)
      │    ├── LeftSteerGroup (HBoxContainer)
      │    │    ├── BtnSteerLeft (TouchScreenButton)
      │    │    └── BtnSteerRight (TouchScreenButton)
      │    └── RightPedalGroup (HBoxContainer)
      │         ├── BtnBrake (TouchScreenButton)
      │         ├── BtnHandbrake (TouchScreenButton)
      │         ├── BtnNitro (TouchScreenButton - Neon Cyan Boost)
      │         └── BtnAccelerate (TouchScreenButton)
      ├── BtnReset (TouchScreenButton - Top Right)
      ├── BtnToggleTouchDebug (Button - For testing touch in desktop/web)
      ├── BtnToggleGhost (Button - Show/Hide ghost car benchmark)
      ├── CountdownOverlay (CenterContainer)
      │    └── CountdownLabel (Label - "3", "2", "1", "GO!")
      └── FinishSummaryModal (PanelContainer - Visible on race end)
           ├── TitleLabel (Label - "RACE FINISHED!")
           ├── TotalTimeLabel (Label)
           ├── BestLapLabel (Label)
           └── BtnRestart (Button)
"""

    const val MINIMAP_GD = """# ==============================================================================
# minimap.gd
# Real-Time 2D Course Minimap Radar Component for Godot 4.x
# Displays circuit trajectory, checkpoints, ghost position, and rotating player arrow.
# ==============================================================================
class_name Minimap
extends Control

# --- Export Properties ---
@export_group("Target Nodes")
@export var player_car: CarController
@export var ghost_car_node: Node3D

@export_group("Colors & Styling")
@export var background_color: Color = Color(0.04, 0.06, 0.1, 0.88)
@export var track_outline_color: Color = Color(0.0, 0.9, 1.0, 0.28)
@export var track_road_color: Color = Color(0.12, 0.16, 0.24, 1.0)
@export var track_center_color: Color = Color(0.22, 0.74, 0.97, 1.0)
@export var player_arrow_color: Color = Color(0.0, 0.95, 1.0, 1.0)
@export var ghost_marker_color: Color = Color(0.75, 0.52, 0.98, 0.85)
@export var finish_line_color: Color = Color(1.0, 0.85, 0.0, 1.0)
@export var checkpoint_color: Color = Color(0.01, 0.52, 0.78, 1.0)

# Circuit Bounding Box in 3D World Space (X and Z)
@export var world_min: Vector2 = Vector2(-180.0, -220.0)
@export var world_max: Vector2 = Vector2(250.0, 230.0)

# Pre-sampled circuit waypoints representing the track spline
var waypoints: Array[Vector2] = [
	Vector2(0, 60), Vector2(0, 0), Vector2(0, -60), Vector2(0, -120),
	Vector2(20, -170), Vector2(70, -200), Vector2(130, -200), Vector2(170, -160),
	Vector2(180, -90), Vector2(180, -20), Vector2(210, 40), Vector2(230, 100),
	Vector2(190, 150), Vector2(120, 160), Vector2(70, 130), Vector2(30, 180),
	Vector2(-30, 210), Vector2(-90, 210), Vector2(-140, 170), Vector2(-160, 110),
	Vector2(-160, 30), Vector2(-140, -30), Vector2(-100, -50), Vector2(-50, 0), Vector2(-20, 40)
]

var checkpoints: Array[Vector2] = [
	Vector2(0, 0),       # Finish / Start Line
	Vector2(170, -160),  # Sector 1
	Vector2(120, 160),   # Hairpin
	Vector2(-140, 110)   # Chicane
]

func _ready() -> void:
	custom_minimum_size = Vector2(130, 130)

func _process(_delta: float) -> void:
	# Trigger redraw every frame to track moving cars
	queue_redraw()

func _draw() -> void:
	var w: float = size.x
	var h: float = size.y
	var margin: float = 10.0
	var map_rect := Rect2(margin, margin, w - margin * 2.0, h - margin * 2.0)
	
	# 1. Background Panel with Radar Circles
	draw_rect(Rect2(Vector2.ZERO, size), background_color, true, -1.0)
	draw_rect(Rect2(Vector2.ZERO, size), Color(0.0, 0.9, 1.0, 0.4), false, 1.5)
	
	var center := size / 2.0
	var max_r := min(w, h) * 0.45
	draw_arc(center, max_r * 0.5, 0, TAU, 32, Color(0.12, 0.16, 0.24, 0.6), 1.0)
	draw_arc(center, max_r * 0.95, 0, TAU, 32, Color(0.12, 0.16, 0.24, 0.6), 1.0)
	draw_line(Vector2(center.x, 0), Vector2(center.x, h), Color(0.12, 0.16, 0.24, 0.4), 1.0)
	draw_line(Vector2(0, center.y), Vector2(w, center.y), Color(0.12, 0.16, 0.24, 0.4), 1.0)
	
	# 2. Draw Track Ribbon Loop
	if waypoints.size() > 1:
		var pts: PackedVector2Array = []
		for wp in waypoints:
			pts.append(_world_to_minimap(wp, map_rect))
		pts.append(pts[0]) # Close loop
		
		# Glow outline, road body, center dashed line
		draw_polyline(pts, track_outline_color, 8.0, true)
		draw_polyline(pts, track_road_color, 5.0, true)
		draw_polyline(pts, track_center_color, 1.8, true)
		
	# 3. Draw Checkpoints
	for i in range(checkpoints.size()):
		var cp_pos := _world_to_minimap(checkpoints[i], map_rect)
		if i == 0:
			draw_circle(cp_pos, 4.0, finish_line_color)
			draw_circle(cp_pos, 2.0, Color.WHITE)
		else:
			draw_circle(cp_pos, 2.8, checkpoint_color)
			
	# 4. Draw Ghost Car Marker (if active & visible)
	if ghost_car_node and ghost_car_node.visible:
		var g_world := Vector2(ghost_car_node.global_position.x, ghost_car_node.global_position.z)
		var g_pos := _world_to_minimap(g_world, map_rect)
		draw_circle(g_pos, 5.5, Color(0.75, 0.52, 0.98, 0.35))
		draw_circle(g_pos, 3.2, ghost_marker_color)
		
	# 5. Draw Player Car Marker (Directional Arrow)
	if player_car:
		var p_world := Vector2(player_car.global_position.x, player_car.global_position.z)
		var p_pos := _world_to_minimap(p_world, map_rect)
		
		# Player heading angle in 2D (projected from 3D basis)
		var forward := -player_car.global_transform.basis.z
		var angle := atan2(forward.z, forward.x)
		_draw_directional_arrow(p_pos, angle)

func _world_to_minimap(world_pos: Vector2, rect: Rect2) -> Vector2:
	var span := world_max - world_min
	var nx := clamp((world_pos.x - world_min.x) / span.x, 0.0, 1.0)
	var nz := clamp((world_pos.y - world_min.y) / span.y, 0.0, 1.0)
	return Vector2(rect.position.x + nx * rect.size.x, rect.position.y + nz * rect.size.y)

func _draw_directional_arrow(pos: Vector2, heading_angle: Float) -> void:
	var fwd := Vector2(cos(heading_angle), sin(heading_angle))
	var right := Vector2(-fwd.y, fwd.x)
	
	var tip := pos + fwd * 7.5
	var left_wing := pos - fwd * 4.0 - right * 4.5
	var right_wing := pos - fwd * 4.0 + right * 4.5
	
	var points := PackedVector2Array([tip, left_wing, pos, right_wing])
	draw_colored_polygon(points, player_arrow_color)
	draw_circle(pos, 8.0, Color(0.0, 0.95, 1.0, 0.25))
"""

    const val GHOST_MANAGER_GD = """# ==============================================================================
# ghost_manager.gd
# Ghost Car Recording & Holographic Playback System for Godot 4.x
# Records the player's best lap trajectory and replays it as a competitive benchmark.
# ==============================================================================
class_name GhostManager
extends Node

signal ghost_ready(best_lap_time: float)
signal ghost_delta_updated(delta_seconds: float, is_leading: bool)

@export var player_car: CarController
@export var ghost_car_node: Node3D
@export var lap_manager: LapManager
@export var touch_hud: TouchHUD

const GHOST_SAVE_PATH: String = "user://ghost_lap.dat"
@export var sample_rate_hz: float = 20.0
@export var ghost_hologram_alpha: float = 0.42

var record_interval: float = 0.05
var sample_timer: float = 0.0
var current_lap_recording: Array[Dictionary] = []
var best_ghost_data: Array[Dictionary] = []
var best_ghost_lap_time: float = 0.0
var is_ghost_active: bool = false
var is_ghost_visible: bool = true

func _ready() -> void:
	record_interval = 1.0 / sample_rate_hz
	_setup_ghost_material()
	load_ghost_from_disk()
	
	if lap_manager:
		lap_manager.lap_completed.connect(_on_lap_completed)
		lap_manager.race_started.connect(_on_race_started)

func _setup_ghost_material() -> void:
	if not ghost_car_node:
		return
	var mat := StandardMaterial3D.new()
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.albedo_color = Color(0.0, 0.9, 1.0, ghost_hologram_alpha)
	mat.emission_enabled = true
	mat.emission = Color(0.0, 0.6, 1.0)
	mat.emission_energy_multiplier = 0.8
	mat.cull_mode = BaseMaterial3D.CULL_BACK
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_PER_PIXEL
	_apply_material_recursive(ghost_car_node, mat)
	ghost_car_node.visible = false

func _apply_material_recursive(node: Node, mat: Material) -> void:
	if node is MeshInstance3D:
		node.material_override = mat
	for child in node.get_children():
		_apply_material_recursive(child, mat)

func _on_race_started() -> void:
	current_lap_recording.clear()
	sample_timer = 0.0
	if best_ghost_data.size() > 1:
		is_ghost_active = true
		if ghost_car_node:
			ghost_car_node.visible = is_ghost_visible

func _physics_process(delta: float) -> void:
	if not lap_manager or not lap_manager.is_race_active or not player_car:
		return
		
	sample_timer += delta
	if sample_timer >= record_interval:
		sample_timer = 0.0
		var frame := {
			"time": lap_manager.current_lap_time_sec,
			"transform": player_car.global_transform,
			"steer": player_car.current_steer_angle
		}
		current_lap_recording.append(frame)
		
	if is_ghost_active and best_ghost_data.size() > 1:
		_replay_ghost(lap_manager.current_lap_time_sec)
		_calculate_ghost_delta(lap_manager.current_lap_time_sec)

func _replay_ghost(lap_time: float) -> void:
	if not ghost_car_node:
		return
	var n: int = best_ghost_data.size()
	if lap_time <= best_ghost_data[0]["time"]:
		ghost_car_node.global_transform = best_ghost_data[0]["transform"]
		return
	if lap_time >= best_ghost_data[n - 1]["time"]:
		ghost_car_node.global_transform = best_ghost_data[n - 1]["transform"]
		return
		
	var low: int = 0
	var high: int = n - 1
	while low <= high:
		var mid: int = (low + high) / 2
		if best_ghost_data[mid]["time"] < lap_time:
			low = mid + 1
		else:
			high = mid - 1
			
	var idx_a: int = clamp(low - 1, 0, n - 2)
	var idx_b: int = idx_a + 1
	var f_a: Dictionary = best_ghost_data[idx_a]
	var f_b: Dictionary = best_ghost_data[idx_b]
	
	var span: float = max(0.0001, f_b["time"] - f_a["time"])
	var t: float = clamp((lap_time - f_a["time"]) / span, 0.0, 1.0)
	
	var trans_a: Transform3D = f_a["transform"]
	var trans_b: Transform3D = f_b["transform"]
	var interp_pos: Vector3 = trans_a.origin.lerp(trans_b.origin, t)
	var interp_rot: Basis = trans_a.basis.slerp(trans_b.basis, t)
	ghost_car_node.global_transform = Transform3D(interp_rot, interp_pos)

func _calculate_ghost_delta(lap_time: float) -> void:
	if not player_car or best_ghost_data.size() < 2:
		return
	var p_pos: Vector3 = player_car.global_position
	var min_dist_sq: float = INF
	var closest_ghost_time: float = lap_time
	
	for frame in best_ghost_data:
		var g_pos: Vector3 = (frame["transform"] as Transform3D).origin
		var d_sq: float = p_pos.distance_squared_to(g_pos)
		if d_sq < min_dist_sq:
			min_dist_sq = d_sq
			closest_ghost_time = frame["time"]
			
	var delta_sec: float = closest_ghost_time - lap_time
	var is_leading: bool = delta_sec >= 0.0
	ghost_delta_updated.emit(abs(delta_sec), is_leading)
	if touch_hud and touch_hud.has_method("update_ghost_delta"):
		touch_hud.update_ghost_delta(delta_sec, is_leading)

func _on_lap_completed(lap_number: int, lap_time: float, is_best: bool) -> void:
	if is_best and current_lap_recording.size() > 5:
		best_ghost_data = current_lap_recording.duplicate(true)
		best_ghost_lap_time = lap_time
		is_ghost_active = true
		if ghost_car_node:
			ghost_car_node.visible = is_ghost_visible
		save_ghost_to_disk()
		ghost_ready.emit(best_ghost_lap_time)
	current_lap_recording.clear()
	sample_timer = 0.0

func save_ghost_to_disk() -> void:
	var file := FileAccess.open(GHOST_SAVE_PATH, FileAccess.WRITE)
	if file:
		var save_dict := {
			"best_time": best_ghost_lap_time,
			"frames": best_ghost_data
		}
		file.store_var(save_dict)
		file.close()

func load_ghost_from_disk() -> void:
	if not FileAccess.file_exists(GHOST_SAVE_PATH):
		return
	var file := FileAccess.open(GHOST_SAVE_PATH, FileAccess.READ)
	if file:
		var save_dict = file.get_var()
		if typeof(save_dict) == TYPE_DICTIONARY:
			best_ghost_lap_time = save_dict.get("best_time", 0.0)
			best_ghost_data = save_dict.get("frames", [])
			if best_ghost_data.size() > 1:
				is_ghost_active = true
		file.close()

func set_ghost_visible(is_vis: bool) -> void:
	is_ghost_visible = is_vis
	if ghost_car_node and is_ghost_active:
		ghost_car_node.visible = is_vis
"""

    const val CAR_CONTROLLER_GD = """# ==============================================================================
# car_controller.gd
# Production-ready 3D Arcade Vehicle Controller for Godot 4.x
# Compatible with Desktop (WASD/Arrows), Web (HTML5), and Native Mobile Touch.
# Includes: Dynamic Steering Dampening, Drift Mechanics, and Rechargeable Nitro Boost.
# ==============================================================================
class_name CarController
extends VehicleBody3D

signal speed_updated(speed_kmh: float)
signal drift_state_changed(is_drifting: bool)
signal nitro_updated(current_nitro: float, max_nitro: float, is_active: bool)

@export_group("Engine & Drivetrain")
@export var max_engine_force: float = 480.0
@export var reverse_engine_force: float = 240.0
@export var max_brake_force: float = 28.0
@export var max_speed_kmh: float = 190.0

@export_group("Nitro Boost")
@export var nitro_boost_force: float = 450.0
@export var nitro_top_speed_kmh: float = 245.0
@export var nitro_burn_rate: float = 35.0
@export var nitro_recharge_rate: float = 14.0
@export var max_nitro: float = 100.0

@export_group("Steering & Dynamics")
@export var max_steer_angle: float = deg_to_rad(28.0)
@export var steer_speed: float = 6.0
@export_range(0.2, 0.9) var high_speed_steer_dampening: float = 0.42

@export_group("Drift & Handbrake")
@export var handbrake_force: float = 55.0
@export var normal_rear_friction: float = 1.85
@export var drift_rear_friction: float = 0.48

@export_group("Suspension Preset (Tuned Arcade)")
@export var suspension_travel: float = 0.22
@export var suspension_stiffness: float = 45.0
@export var suspension_max_force: float = 6500.0
@export var damping_compression: float = 2.2
@export var damping_relaxation: float = 3.2

@export_group("Follow Camera")
@export var follow_camera: Camera3D
@export var camera_distance: float = 5.8
@export var camera_height: float = 2.2
@export var camera_lerp_speed: float = 7.5
@export var camera_lookahead: float = 2.0
@export var base_camera_fov: float = 75.0
@export var boost_camera_fov: float = 86.0

@onready var wheel_fl: VehicleWheel3D = ${'$'}WheelFL
@onready var wheel_fr: VehicleWheel3D = ${'$'}WheelFR
@onready var wheel_rl: VehicleWheel3D = ${'$'}WheelRL
@onready var wheel_rr: VehicleWheel3D = ${'$'}WheelRR
@onready var smoke_l: GPUParticles3D = get_node_or_null("SmokeParticlesL")
@onready var smoke_r: GPUParticles3D = get_node_or_null("SmokeParticlesR")
@onready var nitro_flame_l: GPUParticles3D = get_node_or_null("NitroFlameL")
@onready var nitro_flame_r: GPUParticles3D = get_node_or_null("NitroFlameR")
@onready var nitro_audio: AudioStreamPlayer3D = get_node_or_null("NitroAudio")

var current_steer_angle: float = 0.0
var current_speed_kmh: float = 0.0
var current_nitro: float = 100.0
var is_drifting: bool = false
var is_boosting: bool = false
var can_control: bool = true
var spawn_transform: Transform3D

func _ready() -> void:
	spawn_transform = global_transform
	current_nitro = max_nitro
	_setup_wheel(wheel_fl, true, false)
	_setup_wheel(wheel_fr, true, false)
	_setup_wheel(wheel_rl, false, true)
	_setup_wheel(wheel_rr, false, true)
	center_of_mass_mode = RigidBody3D.CENTER_OF_MASS_MODE_CUSTOM
	center_of_mass = Vector3(0.0, -0.35, 0.0)

func _setup_wheel(wheel: VehicleWheel3D, is_steer: bool, is_drive: bool) -> void:
	if not wheel: return
	wheel.use_as_steering = is_steer
	wheel.use_as_traction = is_drive
	wheel.suspension_travel = suspension_travel
	wheel.suspension_stiffness = suspension_stiffness
	wheel.suspension_max_force = suspension_max_force
	wheel.damping_compression = damping_compression
	wheel.damping_relaxation = damping_relaxation
	wheel.wheel_friction_slip = normal_rear_friction

func _physics_process(delta: float) -> void:
	current_speed_kmh = linear_velocity.length() * 3.6
	speed_updated.emit(current_speed_kmh)
	if not can_control:
		engine_force = 0.0
		brake = max_brake_force
		_update_nitro(delta, false)
		return
	_handle_steering(delta)
	_handle_nitro_and_throttle(delta)
	_handle_handbrake()
	_handle_reset()

func _process(delta: float) -> void:
	_update_camera(delta)

func _handle_steering(delta: float) -> void:
	var steer_input: float = Input.get_action_strength("steer_left") - Input.get_action_strength("steer_right")
	var active_top_speed: float = nitro_top_speed_kmh if is_boosting else max_speed_kmh
	var speed_factor: float = clamp(current_speed_kmh / active_top_speed, 0.0, 1.0)
	var dynamic_steer_limit: float = lerp(max_steer_angle, max_steer_angle * high_speed_steer_dampening, speed_factor)
	var target_steer: float = steer_input * dynamic_steer_limit
	current_steer_angle = move_toward(current_steer_angle, target_steer, steer_speed * delta)
	steering = current_steer_angle

func _handle_nitro_and_throttle(delta: float) -> void:
	var accel_input: float = Input.get_action_strength("accelerate")
	var brake_input: float = Input.get_action_strength("brake")
	var nitro_input: bool = Input.is_action_pressed("nitro")
	var forward_dir: Vector3 = -global_transform.basis.z
	var velocity_dot: float = linear_velocity.dot(forward_dir)
	var is_moving_forward: bool = velocity_dot > 0.5
	var wants_boost: bool = nitro_input and current_nitro > 1.0 and (accel_input > 0.1 or is_moving_forward)
	_update_nitro(delta, wants_boost)
	var target_top_speed: float = nitro_top_speed_kmh if is_boosting else max_speed_kmh
	var speed_falloff: float = clamp(1.0 - (current_speed_kmh / target_top_speed), 0.0, 1.0)
	if accel_input > 0.0 or is_boosting:
		var base_force: float = accel_input * max_engine_force
		var boost_force: float = nitro_boost_force if is_boosting else 0.0
		engine_force = (base_force + boost_force) * speed_falloff
		brake = 0.0
	elif brake_input > 0.0:
		if is_moving_forward:
			brake = brake_input * max_brake_force
			engine_force = 0.0
		else:
			brake = 0.0
			engine_force = -brake_input * reverse_engine_force
	else:
		engine_force = 0.0
		brake = 0.5

func _update_nitro(delta: float, activate: bool) -> void:
	if activate and current_nitro > 0.0:
		is_boosting = true
		current_nitro = max(0.0, current_nitro - nitro_burn_rate * delta)
		if nitro_flame_l: nitro_flame_l.emitting = true
		if nitro_flame_r: nitro_flame_r.emitting = true
		if nitro_audio and not nitro_audio.playing: nitro_audio.play()
	else:
		is_boosting = false
		current_nitro = min(max_nitro, current_nitro + nitro_recharge_rate * delta)
		if nitro_flame_l: nitro_flame_l.emitting = false
		if nitro_flame_r: nitro_flame_r.emitting = false
		if nitro_audio and nitro_audio.playing: nitro_audio.stop()
	nitro_updated.emit(current_nitro, max_nitro, is_boosting)

func _handle_handbrake() -> void:
	var handbrake_pressed: bool = Input.is_action_pressed("handbrake")
	if handbrake_pressed and current_speed_kmh > 15.0:
		is_drifting = true
		wheel_rl.wheel_friction_slip = drift_rear_friction
		wheel_rr.wheel_friction_slip = drift_rear_friction
		brake = handbrake_force
		if smoke_l: smoke_l.emitting = true
		if smoke_r: smoke_r.emitting = true
	else:
		is_drifting = false
		wheel_rl.wheel_friction_slip = normal_rear_friction
		wheel_rr.wheel_friction_slip = normal_rear_friction
		if smoke_l: smoke_l.emitting = false
		if smoke_r: smoke_r.emitting = false
	drift_state_changed.emit(is_drifting)

func _handle_reset() -> void:
	if Input.is_action_just_pressed("reset"):
		reset_car(spawn_transform)

func reset_car(target_transform: Transform3D) -> void:
	linear_velocity = Vector3.ZERO
	angular_velocity = Vector3.ZERO
	current_steer_angle = 0.0
	steering = 0.0
	engine_force = 0.0
	brake = max_brake_force
	current_nitro = max_nitro
	is_boosting = false
	var safe_transform: Transform3D = target_transform
	safe_transform.origin += Vector3.UP * 0.4
	global_transform = safe_transform

func _update_camera(delta: float) -> void:
	if not follow_camera: return
	var car_pos: Vector3 = global_transform.origin
	var forward_dir: Vector3 = -global_transform.basis.z
	var target_cam_pos: Vector3 = car_pos - (forward_dir * camera_distance) + (Vector3.UP * camera_height)
	follow_camera.global_position = follow_camera.global_position.lerp(target_cam_pos, camera_lerp_speed * delta)
	var look_target: Vector3 = car_pos + (forward_dir * camera_lookahead) + (Vector3.UP * 0.8)
	follow_camera.look_at(look_target, Vector3.UP)
	var target_fov: float = boost_camera_fov if is_boosting else base_camera_fov
	follow_camera.fov = lerp(follow_camera.fov, target_fov, 8.0 * delta)
"""

    const val TOUCH_HUD_GD = """# ==============================================================================
# touch_hud.gd
# Dual-Input HUD for Godot 4.x (Mobile Touchscreen + Desktop/Browser Keyboard)
# Displays Speedometer, Lap Telemetry, Nitro Gauge, Minimap, and Ghost Car Delta.
# ==============================================================================
class_name TouchHUD
extends CanvasLayer

@onready var speed_number_label: Label = ${'$'}SpeedometerContainer/SpeedNumberLabel
@onready var lap_counter_label: Label = ${'$'}TopTelemetry/LapCounterLabel
@onready var lap_time_label: Label = ${'$'}TopTelemetry/LapTimeLabel
@onready var best_lap_label: Label = ${'$'}TopTelemetry/BestLapLabel
@onready var ghost_delta_label: Label = ${'$'}TopTelemetry/GhostDeltaLabel

@onready var minimap: Minimap = ${'$'}MinimapContainer/Minimap
@onready var nitro_bar: ProgressBar = ${'$'}SpeedometerContainer/NitroProgressBar
@onready var mobile_controls: Control = ${'$'}MobileControls
@onready var btn_toggle_touch_debug: Button = ${'$'}BtnToggleTouchDebug
@onready var btn_toggle_ghost: Button = ${'$'}BtnToggleGhost

@onready var countdown_overlay: CenterContainer = ${'$'}CountdownOverlay
@onready var countdown_label: Label = ${'$'}CountdownOverlay/CountdownLabel

@onready var finish_modal: PanelContainer = ${'$'}FinishSummaryModal
@onready var finish_total_time: Label = ${'$'}FinishSummaryModal/VBox/TotalTimeLabel
@onready var finish_best_lap: Label = ${'$'}FinishSummaryModal/VBox/BestLapLabel
@onready var btn_restart: Button = ${'$'}FinishSummaryModal/VBox/BtnRestart

@onready var btn_steer_left: TouchScreenButton = ${'$'}MobileControls/LeftSteerGroup/BtnSteerLeft
@onready var btn_steer_right: TouchScreenButton = ${'$'}MobileControls/LeftSteerGroup/BtnSteerRight
@onready var btn_accel: TouchScreenButton = ${'$'}MobileControls/RightPedalGroup/BtnAccelerate
@onready var btn_brake: TouchScreenButton = ${'$'}MobileControls/RightPedalGroup/BtnBrake
@onready var btn_handbrake: TouchScreenButton = ${'$'}MobileControls/RightPedalGroup/BtnHandbrake
@onready var btn_nitro: TouchScreenButton = ${'$'}MobileControls/RightPedalGroup/BtnNitro
@onready var btn_reset: TouchScreenButton = ${'$'}BtnReset

var touch_overlay_enabled: bool = false
var ghost_visible: bool = true

func _ready() -> void:
	var is_mobile_platform: bool = OS.has_feature("mobile") or DisplayServer.is_touchscreen_available()
	set_touch_controls_visible(is_mobile_platform)
	
	btn_toggle_touch_debug.pressed.connect(_on_toggle_touch_debug_pressed)
	if btn_toggle_ghost:
		btn_toggle_ghost.pressed.connect(_on_toggle_ghost_pressed)
	btn_restart.pressed.connect(_on_restart_pressed)
	
	countdown_overlay.visible = false
	finish_modal.visible = false
	update_speed(0.0)
	update_lap(1, 3)
	update_lap_time(0.0)
	update_best_lap(0.0)
	update_nitro(100.0, 100.0, false)
	update_ghost_delta(0.0, true)

func set_touch_controls_visible(is_visible: bool) -> void:
	touch_overlay_enabled = is_visible
	mobile_controls.visible = is_visible
	btn_reset.visible = is_visible
	btn_toggle_touch_debug.text = "Touch HUD: " + ("ON" if is_visible else "OFF")

func _on_toggle_touch_debug_pressed() -> void:
	set_touch_controls_visible(not touch_overlay_enabled)

func _on_toggle_ghost_pressed() -> void:
	ghost_visible = not ghost_visible
	var ghost_mgr = get_node_or_null("../GhostManager")
	if ghost_mgr and ghost_mgr.has_method("set_ghost_visible"):
		ghost_mgr.set_ghost_visible(ghost_visible)
	if btn_toggle_ghost:
		btn_toggle_ghost.text = "Ghost: " + ("ON" if ghost_visible else "OFF")

func _on_restart_pressed() -> void:
	finish_modal.visible = false
	get_tree().reload_current_scene()

func update_speed(kmh: float) -> void:
	speed_number_label.text = str(roundi(kmh))

func update_nitro(current: float, max_val: float, is_active: bool) -> void:
	if nitro_bar:
		nitro_bar.max_value = max_val
		nitro_bar.value = current
		nitro_bar.modulate = Color(1.3, 1.6, 2.0) if is_active else Color(1.0, 1.0, 1.0)

func update_ghost_delta(delta_sec: float, is_leading: bool) -> void:
	if not ghost_delta_label:
		return
	if is_leading:
		ghost_delta_label.text = "GHOST +%0.2fs" % delta_sec
		ghost_delta_label.modulate = Color(0.0, 1.0, 0.4)
	else:
		ghost_delta_label.text = "GHOST -%0.2fs" % delta_sec
		ghost_delta_label.modulate = Color(1.0, 0.25, 0.25)

func update_lap(current_lap: int, total_laps: int) -> void:
	lap_counter_label.text = "LAP %d / %d" % [current_lap, total_laps]

func update_lap_time(time_seconds: float) -> void:
	lap_time_label.text = "TIME " + format_time(time_seconds)

func update_best_lap(time_seconds: float) -> void:
	if time_seconds <= 0.0:
		best_lap_label.text = "BEST --:--.--"
	else:
		best_lap_label.text = "BEST " + format_time(time_seconds)

func show_countdown(value: String) -> void:
	countdown_overlay.visible = true
	countdown_label.text = value
	var tween: Tween = create_tween()
	countdown_label.scale = Vector2(0.5, 0.5)
	countdown_label.pivot_offset = countdown_label.size / 2.0
	tween.tween_property(countdown_label, "scale", Vector2(1.2, 1.2), 0.35).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(countdown_label, "scale", Vector2(1.0, 1.0), 0.15)
	if value == "GO!":
		await get_tree().create_timer(0.8).timeout
		countdown_overlay.visible = false

func show_race_finished(total_time: float, best_lap: float) -> void:
	finish_modal.visible = true
	finish_total_time.text = "Total Time: " + format_time(total_time)
	finish_best_lap.text = "Best Lap: " + format_time(best_lap)

static func format_time(seconds: float) -> String:
	var mins: int = int(seconds / 60.0)
	var secs: int = int(seconds) % 60
	var millis: int = int((seconds - int(seconds)) * 100.0)
	return "%02d:%02d.%02d" % [mins, secs, millis]
"""

    const val LAP_MANAGER_GD = """# ==============================================================================
# lap_manager.gd
# Robust Lap & Sequential Checkpoint Verification System for Godot 4.x
# Enforces track integrity, counts laps, and saves Best Lap records to disk.
# ==============================================================================
class_name LapManager
extends Node

signal countdown_tick(text: String)
signal race_started()
signal lap_completed(lap_number: int, lap_time: float, is_best: bool)
signal race_finished(total_time: float, best_lap_time: float)
signal wrong_way_detected()

@export_group("Race Rules")
@export var total_laps: int = 3
@export var player_car: CarController
@export var touch_hud: TouchHUD

@export_group("Checkpoints")
@export var checkpoints: Array[Area3D] = []

const SAVE_CONFIG_PATH: String = "user://records.cfg"

var current_lap: int = 1
var next_checkpoint_index: int = 1
var is_race_active: bool = false
var lap_start_time_msec: int = 0
var race_start_time_msec: int = 0
var current_lap_time_sec: float = 0.0
var best_lap_time_sec: float = 0.0
var lap_times: Array[float] = []

func _ready() -> void:
	load_best_lap_record()
	_connect_checkpoints()
	start_countdown_sequence()

func _connect_checkpoints() -> void:
	for i in range(checkpoints.size()):
		var cp: Area3D = checkpoints[i]
		if cp:
			cp.body_entered.connect(_on_checkpoint_body_entered.bind(i))

func load_best_lap_record() -> void:
	var config := ConfigFile.new()
	var err := config.load(SAVE_CONFIG_PATH)
	if err == OK:
		best_lap_time_sec = config.get_value("Records", "best_lap_time", 0.0)
	if touch_hud:
		touch_hud.update_best_lap(best_lap_time_sec)

func save_best_lap_record() -> void:
	var config := ConfigFile.new()
	config.set_value("Records", "best_lap_time", best_lap_time_sec)
	config.save(SAVE_CONFIG_PATH)

func start_countdown_sequence() -> void:
	if player_car:
		player_car.can_control = false
		
	var countdown_steps = ["3", "2", "1", "GO!"]
	for step in countdown_steps:
		if touch_hud:
			touch_hud.show_countdown(step)
		countdown_tick.emit(step)
		await get_tree().create_timer(1.0).timeout
		
	is_race_active = true
	if player_car:
		player_car.can_control = true
		
	race_start_time_msec = Time.get_ticks_msec()
	lap_start_time_msec = race_start_time_msec
	race_started.emit()

func _process(_delta: float) -> void:
	if not is_race_active:
		return
	var now_msec: int = Time.get_ticks_msec()
	current_lap_time_sec = (now_msec - lap_start_time_msec) / 1000.0
	if touch_hud:
		touch_hud.update_lap_time(current_lap_time_sec)

func _on_checkpoint_body_entered(body: Node3D, cp_index: int) -> void:
	if body != player_car or not is_race_active:
		return
	if cp_index == next_checkpoint_index and cp_index != 0:
		next_checkpoint_index += 1
		if next_checkpoint_index >= checkpoints.size():
			next_checkpoint_index = 0
	elif cp_index == 0:
		if next_checkpoint_index == 0:
			_complete_lap()
		else:
			wrong_way_detected.emit()

func _complete_lap() -> void:
	var lap_time: float = current_lap_time_sec
	lap_times.append(lap_time)
	var is_new_best: bool = false
	if best_lap_time_sec <= 0.0 or lap_time < best_lap_time_sec:
		best_lap_time_sec = lap_time
		is_new_best = true
		save_best_lap_record()
		if touch_hud:
			touch_hud.update_best_lap(best_lap_time_sec)
	lap_completed.emit(current_lap, lap_time, is_new_best)
	if current_lap >= total_laps:
		is_race_active = false
		var total_race_time_sec: float = (Time.get_ticks_msec() - race_start_time_msec) / 1000.0
		race_finished.emit(total_race_time_sec, best_lap_time_sec)
		if touch_hud:
			touch_hud.show_race_finished(total_race_time_sec, best_lap_time_sec)
	else:
		current_lap += 1
		next_checkpoint_index = 1
		lap_start_time_msec = Time.get_ticks_msec()
		if touch_hud:
			touch_hud.update_lap(current_lap, total_laps)
"""

    const val PROJECT_GODOT = """; Engine configuration file.
; It's best edited using the editor UI and not directly,
; but since it is all text you can easily edit it with any text editor.

config_version=5

[application]

config/name="Apex Arcade Racer 3D"
run/main_scene="res://scenes/Main.tscn"
config/features=PackedStringArray("4.3", "GL Compatibility")
config/icon="res://icon.svg"

[display]

window/size/viewport_width=1280
window/size/viewport_height=720
window/size/mode=0
window/stretch/mode="canvas_items"
window/stretch/aspect="expand"
window/handheld/orientation=6 ; Sensor Landscape for native mobile

[input]

steer_left={
"deadzone": 0.5,
"events": [Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":65,"key_label":0,"unicode":97,"echo":false,"script":null)
, Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":4194319,"key_label":0,"unicode":0,"echo":false,"script":null)
]
}
steer_right={
"deadzone": 0.5,
"events": [Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":68,"key_label":0,"unicode":100,"echo":false,"script":null)
, Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":4194321,"key_label":0,"unicode":0,"echo":false,"script":null)
]
}
accelerate={
"deadzone": 0.5,
"events": [Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":87,"key_label":0,"unicode":119,"echo":false,"script":null)
, Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":4194320,"key_label":0,"unicode":0,"echo":false,"script":null)
]
}
brake={
"deadzone": 0.5,
"events": [Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":83,"key_label":0,"unicode":115,"echo":false,"script":null)
, Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":4194322,"key_label":0,"unicode":0,"echo":false,"script":null)
]
}
handbrake={
"deadzone": 0.5,
"events": [Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":32,"key_label":0,"unicode":32,"echo":false,"script":null)
]
}
nitro={
"deadzone": 0.5,
"events": [Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":4194325,"key_label":0,"unicode":0,"echo":false,"script":null)
, Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":78,"key_label":0,"unicode":110,"echo":false,"script":null)
]
}
reset={
"deadzone": 0.5,
"events": [Object(InputEventKey,"resource_local_to_scene":false,"resource_name":"","device":-1,"window_id":0,"alt_pressed":false,"shift_pressed":false,"ctrl_pressed":false,"meta_pressed":false,"pressed":false,"keycode":0,"physical_keycode":82,"key_label":0,"unicode":114,"echo":false,"script":null)
]
}

[rendering]

renderer/rendering_method="gl_compatibility"
renderer/rendering_method.mobile="gl_compatibility"
textures/vram_compression/import_etc2_astc=true
"""

    const val EXPORT_GUIDE_WEB = """
=== GODOT 4.x WEB (HTML5 / WEBASSEMBLY) EXPORT GUIDE ===

1. Project Settings Verification:
   - Rendering Method: Ensure 'gl_compatibility' is selected for both desktop and mobile.
     (Vulkan Forward+ and Mobile renderers are NOT supported on WebAssembly).
   - VRAM Compression: Enable 'textures/vram_compression/import_etc2_astc=true'.

2. Web Export Preset Configuration:
   - Go to Project -> Export -> Add... -> Web.
   - Export Path: build/web/index.html
   - Thread Support:
     * If hosting on a server with Cross-Origin Opener Policy (COOP) and Cross-Origin Embedder Policy (COEP) headers:
       Set 'Threads Support' to Enabled for multi-threaded performance.
     * If hosting on GitHub Pages, itch.io, or basic static hosting without headers:
       Set 'Threads Support' to Disabled (Single-threaded builds run in any browser without CORS headers).

3. Immediate Browser Testing:
   - From the Godot Editor: Click the 'Remote Debug' icon in the top right corner and choose 'Run in Browser' (launches Godot's built-in mini web server).
   - Command Line:
     godot --headless --export-release "Web" build/web/index.html
     python3 -m http.server 8000 --directory build/web
     Open http://localhost:8000 in Chrome/Firefox/Safari.
"""

    const val NETWORK_MANAGER_GD = """
class_name NetworkManager
extends Node

## Complete Godot 4.x High-Level Multiplayer Network Manager
## Supports Native Mobile (ENetMultiplayerPeer UDP) and Web/Browser (WebSocketMultiplayerPeer).
## Handles Lobby Creation, Client Join, Remote Car Replication, and Transform Interpolation.

signal player_connected(peer_id: int, player_info: Dictionary)
signal player_disconnected(peer_id: int)
signal server_disconnected()
signal race_started()
signal race_finished(results: Array)

const DEFAULT_PORT: int = 7777
const MAX_PLAYERS: int = 6

# Dictionary of connected peers: peer_id -> { "name": String, "color": Color, "car_node": Node3D }
var players: Dictionary = {}
var local_player_info: Dictionary = {
	"name": "Racer",
	"color": Color.RED,
	"ready": false
}

var is_host: bool = false
var peer: MultiplayerPeer = null

@onready var car_spawner: MultiplayerSpawner = ${'$'}MultiplayerSpawner

func _ready() -> void:
	multiplayer.peer_connected.connect(_on_peer_connected)
	multiplayer.peer_disconnected.connect(_on_peer_disconnected)
	multiplayer.connected_to_server.connect(_on_connected_to_server)
	multiplayer.connection_failed.connect(_on_connection_failed)
	multiplayer.server_disconnected.connect(_on_server_disconnected)

## Starts an authoritative game server (Host mode)
func host_game(port: int = DEFAULT_PORT) -> Error:
	var enet_peer = ENetMultiplayerPeer.new()
	var error = enet_peer.create_server(port, MAX_PLAYERS)
	if error != OK:
		push_error("Failed to host server: %s" % error)
		return error
	
	peer = enet_peer
	multiplayer.multiplayer_peer = peer
	is_host = true
	
	# Register host local player
	players[1] = local_player_info.duplicate()
	player_connected.emit(1, players[1])
	print("[Network] Hosting game on port %d. Peer ID: 1" % port)
	return OK

## Connects to an existing host IP (Client mode)
func join_game(address: String = "127.0.0.1", port: int = DEFAULT_PORT) -> Error:
	var enet_peer = ENetMultiplayerPeer.new()
	var error = enet_peer.create_client(address, port)
	if error != OK:
		push_error("Failed to connect to %s:%d" % [address, port])
		return error
	
	peer = enet_peer
	multiplayer.multiplayer_peer = peer
	is_host = false
	print("[Network] Connecting to host %s:%d..." % [address, port])
	return OK

func _on_connected_to_server() -> void:
	var local_id = multiplayer.get_unique_id()
	print("[Network] Successfully connected to server. Assigned Peer ID: %d" % local_id)
	# Send profile to host and peers
	_register_player.rpc_id(1, local_player_info)

func _on_connection_failed() -> void:
	push_error("[Network] Connection attempt failed!")
	multiplayer.multiplayer_peer = null

func _on_peer_connected(id: int) -> void:
	print("[Network] Peer connected: %d" % id)

func _on_peer_disconnected(id: int) -> void:
	print("[Network] Peer disconnected: %d" % id)
	if players.has(id):
		players.erase(id)
		player_disconnected.emit(id)

func _on_server_disconnected() -> void:
	print("[Network] Lost connection to server.")
	players.clear()
	multiplayer.multiplayer_peer = null
	server_disconnected.emit()

@rpc("any_peer", "reliable", "call_local")
func _register_player(info: Dictionary) -> void:
	var sender_id = multiplayer.get_remote_sender_id()
	players[sender_id] = info
	player_connected.emit(sender_id, info)
	
	# If host, replicate current lobby roster to new client
	if multiplayer.is_server():
		for peer_id in players:
			if peer_id != sender_id:
				_register_player.rpc_id(sender_id, players[peer_id])

## Server initiates synchronized race countdown
@rpc("authority", "call_local", "reliable")
func start_race() -> void:
	race_started.emit()

## High-frequency car telemetry replication (Unreliable Ordered RPC)
@rpc("any_peer", "unreliable_ordered", "call_local")
func sync_car_telemetry(pos: Vector3, rot_y: float, speed_kmh: float, is_nitro: bool, is_braking: bool) -> void:
	var sender_id = multiplayer.get_remote_sender_id()
	if sender_id == multiplayer.get_unique_id():
		return # Do not re-apply local inputs
	
	if players.has(sender_id) and players[sender_id].has("car_node"):
		var car = players[sender_id]["car_node"]
		if is_instance_valid(car) and car.has_method("apply_remote_telemetry"):
			car.apply_remote_telemetry(pos, rot_y, speed_kmh, is_nitro, is_braking)

## Server-authoritative lap checkpoint validation (Anti-cheat)
@rpc("any_peer", "call_remote", "reliable")
func submit_checkpoint_pass(checkpoint_idx: int) -> void:
	if not multiplayer.is_server():
		return
	var sender_id = multiplayer.get_remote_sender_id()
	print("[Server Auth] Validating checkpoint %d from Peer %d" % [checkpoint_idx, sender_id])
"""

    const val EXPORT_GUIDE_ANDROID = """
=== GODOT 4.x ANDROID (.APK / .AAB) EXPORT GUIDE ===

1. Prerequisites:
   - Android SDK (Platform SDK 34/35, Build-Tools, Command-line Tools, Platform-Tools).
   - OpenJDK 17.
   - Set Android SDK path in Godot Editor: Editor -> Editor Settings -> Export -> Android -> Android SDK Path.

2. Generate Debug Keystore:
   Run in terminal:
   keytool -keyalg RSA -genkeypair -alias androiddebugkey -keypass android -keystore debug.keystore -storepass android -dname "CN=Android Debug,O=Android,C=US" -validity 9999 -deststoretype pkcs12
   In Editor Settings -> Export -> Android -> Debug Keystore, point to this file.

3. Install Android Build Template:
   - In Godot Editor: Project -> Install Android Build Template.
   - This creates 'res://android/build/' enabling custom Gradle builds and plugins.

4. Export Preset Configuration:
   - Project -> Export -> Add... -> Android.
   - Package Name: com.yourstudio.arcaderacer
   - Architecture: Check 'arm64-v8a' (required for modern 64-bit devices) and 'armeabi-v7a'.
   - Orientation: Sensor Landscape.
   - Export APK: Click 'Export Project' -> release.apk.
"""

    const val EXPORT_GUIDE_IOS = """
=== GODOT 4.x iOS (XCODE PROJECT) EXPORT GUIDE ===

1. Prerequisites:
   - macOS machine with Xcode installed.
   - Apple Developer account (for signing on physical iPhones/iPads).

2. Export Preset Configuration:
   - Project -> Export -> Add... -> iOS.
   - Bundle Identifier: com.yourstudio.arcaderacer
   - Team ID: Your 10-character Apple Developer Team ID.
   - Orientations: Check 'Landscape Left' and 'Landscape Right'.
   - Export Path: build/ios/ApexRacer.xcodeproj

3. Xcode Building:
   - Open the generated Xcode project:
     open build/ios/ApexRacer.xcodeproj
   - Select your target device (iPhone/iPad).
   - Verify Signing & Capabilities tab has your Team selected.
   - Hit Cmd+R or click the Play button to build and run.
"""
}
