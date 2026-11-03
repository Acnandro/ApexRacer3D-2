package com.example.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlin.math.*

class ArcadeSoundManager(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMuted = false

    // Engine synth state
    private var engineTrack: AudioTrack? = null
    private var isEngineRunning = false
    private var targetPitchHz = 110f
    private var isTireScreeching = false
    private var isNitroActive = false

    init {
        startEngineSynth()
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    fun updateEngineAudio(speedKmh: Float, throttle: Float, isDrifting: Boolean, isBoosting: Boolean = false) {
        if (isMuted) return
        
        // Dynamic engine RPM simulation: 80 Hz idle up to 550 Hz during nitro boost
        val baseHz = 85f
        val maxSpeedScale = if (isBoosting) 250f else 200f
        val speedContribution = (speedKmh / maxSpeedScale).coerceIn(0f, 1f) * 360f
        val throttleBoost = throttle * 45f
        val nitroBoostHz = if (isBoosting) 90f else 0f

        targetPitchHz = baseHz + speedContribution + throttleBoost + nitroBoostHz
        isTireScreeching = isDrifting && speedKmh > 20f
        
        if (isBoosting && !isNitroActive) {
            vibrate(80)
        }
        isNitroActive = isBoosting
    }

    private fun startEngineSynth() {
        scope.launch {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            try {
                engineTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                engineTrack?.play()
                isEngineRunning = true

                val buffer = ShortArray(bufferSize / 2)
                var phase = 0.0
                var noisePhase = 0.0
                var nitroPhase = 0.0
                val random = java.util.Random()

                while (isEngineRunning) {
                    if (isMuted) {
                        buffer.fill(0)
                    } else {
                        val freq = targetPitchHz
                        val phaseInc = 2.0 * PI * freq / sampleRate
                        val screech = isTireScreeching
                        val nitro = isNitroActive

                        for (i in buffer.indices) {
                            // Triangle + sine wave for roaring arcade boxer engine
                            val sine = sin(phase)
                            val triangle = (2.0 / PI) * asin(sin(phase))
                            var sample = (sine * 0.45 + triangle * 0.55) * 8000.0

                            // Sub-harmonic rumble
                            sample += sin(phase * 0.5) * 4000.0

                            // Drift tire screech noise modulation
                            if (screech) {
                                val whiteNoise = (random.nextDouble() * 2.0 - 1.0) * 8000.0
                                val screechTone = sin(noisePhase) * 5000.0
                                sample = sample * 0.45 + (whiteNoise + screechTone) * 0.55
                                noisePhase += 2.0 * PI * 1800.0 / sampleRate
                            }

                            // Nitro rocket flame hiss & turbo spool resonance
                            if (nitro) {
                                val nitroNoise = (random.nextDouble() * 2.0 - 1.0) * 11000.0
                                val turboWhine = sin(nitroPhase) * 7000.0
                                sample = sample * 0.5 + (nitroNoise * 0.6 + turboWhine * 0.4) * 0.7
                                nitroPhase += 2.0 * PI * 2800.0 / sampleRate
                            }

                            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            phase += phaseInc
                            if (phase > 2.0 * PI) phase -= 2.0 * PI
                        }
                    }
                    engineTrack?.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
                // Fallback gracefully
            }
        }
    }

    fun playCountdownBeep(isHighPitch: Boolean) {
        if (isMuted) return
        scope.launch {
            playTone(if (isHighPitch) 880f else 440f, 220)
            vibrate(if (isHighPitch) 120 else 50)
        }
    }

    fun playCheckpointChime() {
        if (isMuted) return
        scope.launch {
            playTone(660f, 90)
            delay(90)
            playTone(990f, 150)
            vibrate(70)
        }
    }

    fun playWrongWayBuzzer() {
        if (isMuted) return
        scope.launch {
            playTone(180f, 200)
            vibrate(180)
        }
    }

    fun vibrate(durationMs: Long) {
        try {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    private fun playTone(freqHz: Float, durationMs: Int) {
        try {
            val sampleRate = 22050
            val numSamples = (sampleRate * durationMs / 1000)
            val buffer = ShortArray(numSamples)
            var phase = 0.0
            val phaseInc = 2.0 * PI * freqHz / sampleRate

            for (i in buffer.indices) {
                val envelope = when {
                    i < numSamples * 0.1 -> i / (numSamples * 0.1)
                    i > numSamples * 0.8 -> (numSamples - i) / (numSamples * 0.2)
                    else -> 1.0
                }
                val sample = sin(phase) * 16000.0 * envelope
                buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                phase += phaseInc
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            scope.launch {
                delay(durationMs + 50L)
                track.release()
            }
        } catch (_: Exception) {}
    }

    fun release() {
        isEngineRunning = false
        try {
            engineTrack?.stop()
            engineTrack?.release()
        } catch (_: Exception) {}
        scope.cancel()
    }
}
