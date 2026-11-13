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

    fun isAudible(rms: Float): Boolean = rms >= SILENCE_RMS
}
