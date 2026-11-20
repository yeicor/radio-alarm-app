package com.github.yeicor.radioalarmapp.audio

import android.media.AudioFormat
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * Shared PCM loudness helper used by both the stream probe and the live player watchdog.
 * A source only counts as audible when decoded samples rise above [SILENCE_RMS]
 * (about -50 dBFS), so a stream that returns packets but is effectively silent is
 * treated as a failure instead of a success.
 */
object AudioSignal {
    const val SILENCE_RMS = 0.003f

    /** Reasonable loudness for alarm audio; quieter sources are boosted, louder ones cut. */
    const val BALANCE_TARGET_RMS = 0.08f

    /** Never boost more than this (about +12 dB), so quiet audio stays natural. */
    const val BALANCE_MAX_GAIN = 4f

    /** Never cut more than this (about -12 dB), so a loud source is not silenced. */
    const val BALANCE_MIN_GAIN = 0.25f

    private const val MAX_SAMPLES = 16_384

    fun rms(buffer: ByteBuffer, encoding: Int): Float {
        val copy = buffer.duplicate()
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val samples = copy.asFloatBuffer()
                var sum = 0.0
                var count = 0
                while (samples.hasRemaining() && count < MAX_SAMPLES) {
                    val value = samples.get().toDouble()
                    sum += value * value
                    count++
                }
                if (count == 0) 0f else sqrt(sum / count).toFloat()
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                var sum = 0.0
                var count = 0
                while (copy.hasRemaining() && count < MAX_SAMPLES) {
                    val value = (copy.get().toInt() and 0xFF) - 128
                    sum += value.toDouble() * value
                    count++
                }
                if (count == 0) 0f else (sqrt(sum / count) / 128.0).toFloat()
            }

            else -> {
                val samples = copy.asShortBuffer()
                var sum = 0.0
                var count = 0
                while (samples.hasRemaining() && count < MAX_SAMPLES) {
                    val value = samples.get().toDouble()
                    sum += value * value
                    count++
                }
                if (count == 0) 0f else (sqrt(sum / count) / 32_768.0).toFloat()
            }
        }
    }

    fun isAudible(rms: Float, threshold: Float = SILENCE_RMS): Boolean = rms >= threshold

    fun peak(buffer: ByteBuffer, encoding: Int): Float {
        val copy = buffer.duplicate()
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val samples = copy.asFloatBuffer()
                var peak = 0f
                var count = 0
                while (samples.hasRemaining() && count < MAX_SAMPLES) {
                    peak = maxOf(peak, kotlin.math.abs(samples.get()))
                    count++
                }
                peak
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                var peak = 0
                var count = 0
                while (copy.hasRemaining() && count < MAX_SAMPLES) {
                    val value = (copy.get().toInt() and 0xFF) - 128
                    peak = maxOf(peak, kotlin.math.abs(value))
                    count++
                }
                peak / 128f
            }

            else -> {
                val samples = copy.asShortBuffer()
                var peak = 0
                var count = 0
                while (samples.hasRemaining() && count < MAX_SAMPLES) {
                    peak = maxOf(peak, kotlin.math.abs(samples.get().toInt()))
                    count++
                }
                peak / 32_768f
            }
        }
    }

    /**
     * Gain that moves a source towards [BALANCE_TARGET_RMS]. Quiet audio is boosted only as
     * far as its peaks allow; loud audio is cut. Both directions are capped so the result
     * stays natural and the alarm is never silenced.
     */
    fun balanceGain(rms: Float, peak: Float = 0f): Float {
        if (rms <= 0.0001f) return 1f
        val desired = BALANCE_TARGET_RMS / rms
        val gain = if (desired > 1f) {
            val peakGain = if (peak > 0.0001f) 0.95f / peak else BALANCE_MAX_GAIN
            minOf(desired, peakGain)
        } else {
            desired
        }
        return gain.coerceIn(BALANCE_MIN_GAIN, BALANCE_MAX_GAIN)
    }
}
