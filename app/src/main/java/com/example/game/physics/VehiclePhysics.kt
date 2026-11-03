package com.example.game.physics

import kotlin.math.*

/**
 * Real-time Vehicle Physics Engine mimicking Godot 4's VehicleBody3D and VehicleWheel3D.
 * Enhanced with Nitro Boost mechanics and twin exhaust flame simulation.
 */
data class VehicleTuning(
    var maxEngineForce: Float = 480f,
    var reverseEngineForce: Float = 240f,
    var maxBrakeForce: Float = 28f,
    var maxSpeedKmh: Float = 190f,
    var nitroBoostForce: Float = 460f,
    var nitroTopSpeedKmh: Float = 245f,
    var nitroBurnRate: Float = 35f,
    var nitroRechargeRate: Float = 14f,
    var maxNitroFuel: Float = 100f,
    var maxSteerAngleDeg: Float = 28f,
    var steerSpeed: Float = 6.0f,
    var highSpeedSteerDampening: Float = 0.42f,
    var handbrakeForce: Float = 55f,
    var normalRearFriction: Float = 1.85f,
    var driftRearFriction: Float = 0.48f,
    var suspensionStiffness: Float = 45f
)

data class VehicleState(
    var posX: Float = 0f,
    var posZ: Float = 0f,
    var velocityX: Float = 0f,
    var velocityZ: Float = 0f,
    var headingRad: Float = -PI.toFloat() / 2f, // Facing forward (negative Z)
    var steerAngleRad: Float = 0f,
    var speedKmh: Float = 0f,
    var isDrifting: bool = false,
    var isBraking: bool = false,
    var isReversing: bool = false,
    var isOffroad: bool = false,
    var isBoosting: bool = false,
    var nitroFuel: Float = 100f, // 0 to 100%
    var suspensionPitch: Float = 0f, // radians
    var suspensionRoll: Float = 0f,  // radians
    var angularVelocity: Float = 0f
)

typealias bool = Boolean

data class NitroFlameParticle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var life: Float,
    val maxLife: Float = 0.22f,
    var size: Float = 0.35f
)

class VehiclePhysics(
    var tuning: VehicleTuning = VehicleTuning()
) {
    var state = VehicleState()
    private val tireSmokeParticles = mutableListOf<SmokeParticle>()
    private val nitroFlameParticles = mutableListOf<NitroFlameParticle>()
    private val random = java.util.Random()

    fun update(
        deltaSec: Float,
        steerInput: Float,     // -1.0 (left) to +1.0 (right)
        accelInput: Float,     // 0.0 to 1.0
        brakeInput: Float,     // 0.0 to 1.0
        handbrakeInput: Boolean,
        nitroInput: Boolean = false,
        trackWidth: Float = 24f,
        trackCenterOffset: Float = 0f
    ) {
        val dt = deltaSec.coerceIn(0.001f, 0.05f)

        // 1. Current speed in m/s and km/h
        val forwardDirX = cos(state.headingRad)
        val forwardDirZ = sin(state.headingRad)
        val rightDirX = -sin(state.headingRad)
        val rightDirZ = cos(state.headingRad)

        val forwardSpeed = state.velocityX * forwardDirX + state.velocityZ * forwardDirZ
        val lateralSpeed = state.velocityX * rightDirX + state.velocityZ * rightDirZ
        state.speedKmh = sqrt(state.velocityX * state.velocityX + state.velocityZ * state.velocityZ) * 3.6f

        // 2. Nitro Boost logic
        val canBoost = nitroInput && state.nitroFuel > 1.0f && (accelInput > 0.05f || forwardSpeed > 2.0f)
        if (canBoost) {
            state.isBoosting = true
            state.nitroFuel = (state.nitroFuel - tuning.nitroBurnRate * dt).coerceAtLeast(0f)
        } else {
            state.isBoosting = false
            // Auto recharge when not in use
            state.nitroFuel = (state.nitroFuel + tuning.nitroRechargeRate * dt).coerceAtMost(tuning.maxNitroFuel)
        }

        // 3. Dynamic high-speed steering dampening
        val activeMaxSpeed = if (state.isBoosting) tuning.nitroTopSpeedKmh else tuning.maxSpeedKmh
        val speedRatio = (state.speedKmh / activeMaxSpeed).coerceIn(0f, 1f)
        val currentMaxSteer = Math.toRadians(tuning.maxSteerAngleDeg.toDouble()).toFloat() *
                (1f - speedRatio * (1f - tuning.highSpeedSteerDampening))

        val targetSteer = steerInput * currentMaxSteer
        state.steerAngleRad += (targetSteer - state.steerAngleRad) * (tuning.steerSpeed * dt).coerceAtMost(1f)

        // 4. Off-road detection (based on distance from track centerline)
        state.isOffroad = abs(trackCenterOffset) > trackWidth / 2f
        val surfaceFriction = if (state.isOffroad) 0.55f else 1.0f
        val rollingDrag = if (state.isOffroad) 4.5f else 0.85f

        // 5. Handbrake drift mechanics
        val isHandbraking = handbrakeInput && state.speedKmh > 12f
        state.isDrifting = isHandbraking || (abs(lateralSpeed) > 4.5f && state.speedKmh > 40f)
        val rearGrip = if (isHandbraking) tuning.driftRearFriction else tuning.normalRearFriction * surfaceFriction

        // 6. Throttle & Service Brake & Reverse & Nitro Boost Force
        var appliedDriveForce = 0f
        state.isBraking = false
        state.isReversing = false

        val speedFalloff = (1f - (state.speedKmh / activeMaxSpeed)).coerceIn(0f, 1f)
        if (accelInput > 0f || state.isBoosting) {
            val normalForce = accelInput * (tuning.maxEngineForce / 15f)
            val boostForce = if (state.isBoosting) (tuning.nitroBoostForce / 12f) else 0f
            appliedDriveForce = (normalForce + boostForce) * speedFalloff * surfaceFriction
        } else if (brakeInput > 0f) {
            if (forwardSpeed > 1.0f) {
                // Moving forward: Apply service brakes
                state.isBraking = true
                val brakeDecel = brakeInput * (tuning.maxBrakeForce / 2.5f) * surfaceFriction
                appliedDriveForce = -brakeDecel.coerceAtMost(forwardSpeed * 8f)
            } else {
                // Stopped or rolling back: Reverse gear
                state.isReversing = true
                appliedDriveForce = -brakeInput * (tuning.reverseEngineForce / 20f) * surfaceFriction
            }
        }

        if (isHandbraking) {
            appliedDriveForce -= (tuning.handbrakeForce / 6f) * (forwardSpeed.sign)
            state.isBraking = true
        }

        // Natural resistance / drag
        appliedDriveForce -= forwardSpeed * rollingDrag

        // 7. Angular velocity and vehicle yaw
        val wheelBase = 2.6f
        val steeringTurnRate = (forwardSpeed / wheelBase) * tan(state.steerAngleRad)
        val driftYawBoost = if (state.isDrifting) -lateralSpeed * 0.12f else 0f
        state.angularVelocity = steeringTurnRate + driftYawBoost
        state.headingRad += state.angularVelocity * dt

        // 8. Update forward and lateral velocities
        val newForwardSpeed = forwardSpeed + appliedDriveForce * dt
        val lateralGripDecel = -lateralSpeed * (rearGrip * 9.8f)
        val newLateralSpeed = (lateralSpeed + lateralGripDecel * dt) * (if (state.isDrifting) 0.985f else 0.82f)

        // Reconstitute world velocity vector
        val updatedFwdX = cos(state.headingRad)
        val updatedFwdZ = sin(state.headingRad)
        val updatedRightX = -sin(state.headingRad)
        val updatedRightZ = cos(state.headingRad)

        state.velocityX = newForwardSpeed * updatedFwdX + newLateralSpeed * updatedRightX
        state.velocityZ = newForwardSpeed * updatedFwdZ + newLateralSpeed * updatedRightZ

        // 9. Update world position
        state.posX += state.velocityX * dt
        state.posZ += state.velocityZ * dt

        // 10. Suspension pitch & roll
        val targetPitch = (appliedDriveForce * 0.003f).coerceIn(-0.08f, 0.08f)
        val targetRoll = (-state.angularVelocity * forwardSpeed * 0.02f).coerceIn(-0.12f, 0.12f)
        state.suspensionPitch += (targetPitch - state.suspensionPitch) * (tuning.suspensionStiffness * 0.2f * dt).coerceAtMost(1f)
        state.suspensionRoll += (targetRoll - state.suspensionRoll) * (tuning.suspensionStiffness * 0.2f * dt).coerceAtMost(1f)

        // 11. Drift tire smoke particles
        if (state.isDrifting && state.speedKmh > 20f) {
            val rearLeftX = state.posX - updatedFwdX * 1.2f - updatedRightX * 0.8f
            val rearLeftZ = state.posZ - updatedFwdZ * 1.2f - updatedRightZ * 0.8f
            val rearRightX = state.posX - updatedFwdX * 1.2f + updatedRightX * 0.8f
            val rearRightZ = state.posZ - updatedFwdZ * 1.2f + updatedRightZ * 0.8f

            tireSmokeParticles.add(SmokeParticle(rearLeftX, rearLeftZ, 0.45f))
            tireSmokeParticles.add(SmokeParticle(rearRightX, rearRightZ, 0.45f))
        }

        tireSmokeParticles.removeAll {
            it.life -= dt
            it.size += dt * 0.8f
            it.life <= 0f
        }

        // 12. Nitro exhaust flame particles (twin exhausts)
        if (state.isBoosting) {
            val tailOffsetX = state.posX - updatedFwdX * 2.15f
            val tailOffsetZ = state.posZ - updatedFwdZ * 2.15f
            val pipeSpread = 0.42f

            // Left exhaust pipe
            val pipeLX = tailOffsetX - updatedRightX * pipeSpread
            val pipeLZ = tailOffsetZ - updatedRightZ * pipeSpread

            // Right exhaust pipe
            val pipeRX = tailOffsetX + updatedRightX * pipeSpread
            val pipeRZ = tailOffsetZ + updatedRightZ * pipeSpread

            // High velocity shooting directly behind car
            val exhaustSpeed = 18f + (random.nextFloat() * 6f)
            val jitterX = (random.nextFloat() - 0.5f) * 1.2f
            val jitterZ = (random.nextFloat() - 0.5f) * 1.2f

            nitroFlameParticles.add(
                NitroFlameParticle(
                    x = pipeLX, y = 0.42f, z = pipeLZ,
                    vx = -updatedFwdX * exhaustSpeed + jitterX,
                    vy = (random.nextFloat() - 0.2f) * 0.6f,
                    vz = -updatedFwdZ * exhaustSpeed + jitterZ,
                    life = 0.18f
                )
            )
            nitroFlameParticles.add(
                NitroFlameParticle(
                    x = pipeRX, y = 0.42f, z = pipeRZ,
                    vx = -updatedFwdX * exhaustSpeed + jitterX,
                    vy = (random.nextFloat() - 0.2f) * 0.6f,
                    vz = -updatedFwdZ * exhaustSpeed + jitterZ,
                    life = 0.18f
                )
            )
        }

        // Update flame particles
        nitroFlameParticles.removeAll {
            it.life -= dt
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.z += it.vz * dt
            it.size += dt * 1.1f
            it.life <= 0f
        }
    }

    fun getSmokeParticles(): List<SmokeParticle> = tireSmokeParticles
    fun getNitroParticles(): List<NitroFlameParticle> = nitroFlameParticles

    fun reset(posX: Float = 0f, posZ: Float = 0f, headingRad: Float = -PI.toFloat() / 2f) {
        state.posX = posX
        state.posZ = posZ
        state.velocityX = 0f
        state.velocityZ = 0f
        state.headingRad = headingRad
        state.steerAngleRad = 0f
        state.speedKmh = 0f
        state.isDrifting = false
        state.isBraking = false
        state.isReversing = false
        state.isBoosting = false
        state.nitroFuel = tuning.maxNitroFuel
        state.angularVelocity = 0f
        state.suspensionPitch = 0f
        state.suspensionRoll = 0f
        tireSmokeParticles.clear()
        nitroFlameParticles.clear()
    }
}

data class SmokeParticle(
    val x: Float,
    val z: Float,
    var life: Float,
    var size: Float = 0.3f
)
