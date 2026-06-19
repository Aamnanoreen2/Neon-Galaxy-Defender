package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sin
import kotlin.random.Random

object SyntheticAudioEngine {

    const val SAMPLE_RATE = 22050 // Low sample rate for micro memory usage & low-end safety
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)

    @Volatile
    var isSoundEnabled: Boolean = true

    @Volatile
    var systemVolume: Float = 0.5f // 0.0f to 1.0f

    // Background ambient tracker loop
    private var musicTrack: AudioTrack? = null
    @Volatile
    private var isMusicPlaying: Boolean = false

    fun playShoot() {
        if (!isSoundEnabled || systemVolume <= 0f) return
        executor.execute {
            try {
                val duration = 0.08f // 80 milliseconds
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = FloatArray(numSamples)
                
                var phase = 0f
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    // Sweep frequency from 1200Hz down to 100Hz
                    val currentFreq = 1200f - (1100f * progress)
                    phase += (2f * Math.PI.toFloat() * currentFreq / SAMPLE_RATE)
                    val envelope = 1f - progress // linear volume decay
                    samples[i] = sin(phase) * envelope * systemVolume
                }
                
                playBuffer(samples)
            } catch (e: Exception) {
                // Secure sandbox
            }
        }
    }

    fun playExplosion() {
        if (!isSoundEnabled || systemVolume <= 0f) return
        executor.execute {
            try {
                val duration = 0.25f // 250 milliseconds
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = FloatArray(numSamples)
                
                var phase = 0f
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    // Low resonant rumble sweeping down
                    val currentFreq = 300f - (250f * progress)
                    phase += (2f * Math.PI.toFloat() * currentFreq / SAMPLE_RATE)
                    
                    // Mix white noise and low rumble for explosive debris texture
                    val whiteNoise = Random.nextFloat() * 2f - 1f
                    val sineRumble = sin(phase)
                    val mixed = (whiteNoise * 0.4f + sineRumble * 0.6f)
                    
                    // Soft exponential envelope decay
                    val envelope = (1f - progress) * (1f - progress)
                    
                    samples[i] = mixed * envelope * systemVolume * 0.8f
                }
                
                playBuffer(samples)
            } catch (e: Exception) {
                // Secure cleanup
            }
        }
    }

    fun playWarning() {
        if (!isSoundEnabled || systemVolume <= 0f) return
        executor.execute {
            try {
                val duration = 0.15f // 150 ms
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = FloatArray(numSamples)
                
                var phase = 0f
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    // Alternating sharp frequency square-like tone
                    val currentFreq = 650f
                    phase += (2f * Math.PI.toFloat() * currentFreq / SAMPLE_RATE)
                    
                    // Simple soft clip square wave
                    val value = if (sin(phase) > 0f) 0.6f else -0.6f
                    val envelope = 1f - progress
                    samples[i] = value * envelope * systemVolume
                }
                
                playBuffer(samples)
            } catch (e: Exception) {
                // Sandbox safety
            }
        }
    }

    private fun playBuffer(samples: FloatArray) {
        val bufferSize = samples.size * 2 // short is 2 bytes
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STATIC
            )
        }

        // Convert float samplings down to 16-bit PCM shorts
        val pcm = ShortArray(samples.size)
        for (i in samples.indices) {
            pcm[i] = (samples[i].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }

        track.write(pcm, 0, pcm.size)
        track.play()
        
        // Let Static AudioTrack auto-release after playing
        executor.execute {
            try {
                Thread.sleep((samples.size.toFloat() / SAMPLE_RATE * 1000f).toLong() + 100)
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Safe disposal
            }
        }
    }

    /**
     * Loops background ambient sci-fi theme on a dedicated stream thread
     */
    fun startBackgroundMusic() {
        if (isMusicPlaying) return
        isMusicPlaying = true
        
        executor.execute {
            try {
                val loopDuration = 1.6f // 1.6 seconds per loop iteration
                val numSamples = (SAMPLE_RATE * loopDuration).toInt()
                val samples = FloatArray(numSamples)
                
                // Synthesize a sci-fi cybernetic arpeggiated bass sequence
                // Core frequencies (A1, C2, E2, G2 notes)
                val notes = floatArrayOf(55.0f, 65.41f, 82.41f, 98.0f) 
                val subStepSamples = numSamples / 8 // 8 sixteenth notes
                
                for (step in 0 until 8) {
                    val rootFreq = notes[step % notes.size]
                    val stepStart = step * subStepSamples
                    val stepEnd = stepStart + subStepSamples
                    
                    var phase = 0f
                    var fmPhase = 0f
                    for (i in stepStart until stepEnd) {
                        val progress = (i - stepStart).toFloat() / subStepSamples
                        
                        // Modulate with FM synthesise for gritty industrial tone
                        val fmFreq = rootFreq * 2f
                        fmPhase += (2f * Math.PI.toFloat() * fmFreq / SAMPLE_RATE)
                        val fmModulator = sin(fmPhase) * 1.5f
                        
                        // Carrier oscillator
                        phase += (2f * Math.PI.toFloat() * (rootFreq + fmModulator * 30f) / SAMPLE_RATE)
                        val sineVal = sin(phase)
                        
                        // Quick gating envelope pluck
                        val attack = 0.05f
                        val release = 0.95f
                        val envelope = if (progress < attack) {
                            progress / attack
                        } else {
                            1f - ((progress - attack) / release)
                        }
                        
                        samples[i] = sineVal * envelope * 0.4f
                    }
                }

                val bufferSize = samples.size * 2
                musicTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STATIC
                    )
                }

                val pcm = ShortArray(samples.size)
                for (i in samples.indices) {
                    pcm[i] = (samples[i].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
                }

                musicTrack?.write(pcm, 0, pcm.size)
                // Configure Static Loop mode!
                musicTrack?.setLoopPoints(0, pcm.size, -1) // Unchecked repeat loop points
                
                musicTrack?.play()
                
                // Reroute volume changes gracefully while playing
                refreshMusicVolume()
            } catch (e: Exception) {
                // Recoverable stream
                isMusicPlaying = false
            }
        }
    }

    fun stopBackgroundMusic() {
        isMusicPlaying = false
        try {
            musicTrack?.stop()
            musicTrack?.release()
        } catch (e: Exception) {
            // Unlocked state
        }
        musicTrack = null
    }

    fun refreshMusicVolume() {
        try {
            val volumeCoeff = if (isSoundEnabled) systemVolume * 0.25f else 0f
            musicTrack?.setVolume(volumeCoeff)
        } catch (e: Exception) {
            // Track state sync
        }
    }
}
