package com.example.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.NotificationTone
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

object SoundGenerator {

    fun playTone(tone: NotificationTone) {
        when (tone) {
            NotificationTone.PEACEFUL_CHIME -> playPeacefulChime()
            NotificationTone.RESONANT_GONG -> playResonantGong()
            NotificationTone.STANDARD_BEEP -> playStandardBeep()
            else -> {} // system default or silent is handled by the OS notification system
        }
    }

    private fun playPeacefulChime() {
        Thread {
            try {
                val sampleRate = 44100
                val duration = 2.0 // seconds
                val numSamples = (duration * sampleRate).toInt()
                val samples = FloatArray(numSamples)
                
                // Pure chord: E5 (659.25), A4 (440.0), C#5 (554.37)
                val f1 = 440.0
                val f2 = 554.37
                val f3 = 659.25
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    
                    // Simple bell curve sound envelope
                    // Fast attack, smooth long decay
                    val envelope = if (t < 0.02) {
                        t / 0.02
                    } else {
                        exp(-2.5 * (t - 0.02))
                    }
                    
                    val s1 = sin(2 * Math.PI * f1 * t) * 1.0
                    val s2 = sin(2 * Math.PI * f2 * t) * 0.7
                    val s3 = sin(2 * Math.PI * f3 * t) * 0.5
                    val s4 = sin(2 * Math.PI * (f1 * 2) * t) * 0.3 // Harmonic
                    
                    samples[i] = ((s1 + s2 + s3 + s4) / 2.5 * envelope).toFloat()
                }
                
                writeAndPlayPcm(samples, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playResonantGong() {
        Thread {
            try {
                val sampleRate = 44100
                val duration = 2.5 // seconds
                val numSamples = (duration * sampleRate).toInt()
                val samples = FloatArray(numSamples)
                
                // Deep tibetan gong: low fundamental A2 (110.0 Hz) + slightly dissonant/beating harmonics
                val f1 = 110.0
                val f2 = 110.5  // creates a subtle beating vibraphone effect
                val f3 = 220.3
                val f4 = 330.0
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    
                    val envelope = if (t < 0.05) {
                        t / 0.05
                    } else {
                        exp(-1.5 * (t - 0.05))
                    }
                    
                    val s1 = sin(2 * Math.PI * f1 * t) * 1.2
                    val s2 = sin(2 * Math.PI * f2 * t) * 1.0
                    val s3 = sin(2 * Math.PI * f3 * t) * 0.5
                    val s4 = sin(2 * Math.PI * f4 * t) * 0.2
                    
                    samples[i] = ((s1 + s2 + s3 + s4) / 2.9 * envelope).toFloat()
                }
                
                writeAndPlayPcm(samples, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playStandardBeep() {
        Thread {
            try {
                val sampleRate = 44100
                val duration = 0.5 // seconds
                val numSamples = (duration * sampleRate).toInt()
                val samples = FloatArray(numSamples)
                
                // Classic high electronic alert: 1500 Hz
                val frequency = 1500.0
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    
                    // Instant attack, abrupt ending with quick decay
                    val envelope = if (t > 0.4) {
                        exp(-15.0 * (t - 0.4))
                    } else {
                        1.0
                    }
                    
                    // Standard sharp square-ish wave for a beep
                    val sine = sin(2 * Math.PI * frequency * t)
                    // add some third harmonic to make it a bit square-like
                    val wave = sine + 0.3 * sin(2 * Math.PI * (frequency * 3) * t)
                    
                    samples[i] = (wave / 1.3 * envelope).toFloat()
                }
                
                writeAndPlayPcm(samples, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun writeAndPlayPcm(samples: FloatArray, sampleRate: Int) {
        val numSamples = samples.size
        // Convert to 16-bit PCM bytes
        val pcmData = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val sample = (samples[i] * 32767).toInt().coerceIn(-32768, 32767)
            pcmData[2 * i] = (sample and 0xff).toByte()
            pcmData[2 * i + 1] = (sample shr 8 and 0xff).toByte()
        }
        
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
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
            .setBufferSizeInBytes(pcmData.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        
        audioTrack.write(pcmData, 0, pcmData.size)
        audioTrack.play()
        
        // Block thread to allow buffer to finish playing
        val durationMs = (numSamples.toDouble() / sampleRate * 1000).toLong()
        Thread.sleep(durationMs)
        try {
            audioTrack.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}
