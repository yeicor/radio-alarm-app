package com.github.yeicor.radioalarmapp.audio

import android.media.AudioFormat
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Normalizes decoded audio towards a stable level. The gain is derived per buffer from its
 * RMS and peak, then capped and smoothed so quiet and loud intervals converge without
 * pumping or clipping. Only gain is applied, so pitch and intonation are untouched.
 */
@OptIn(UnstableApi::class)
class AutoBalanceProcessor(
    private val enabled: () -> Boolean,
    private val onGainChanged: (Float) -> Unit = {},
) : BaseAudioProcessor() {
    @Volatile
    var currentGain: Float = 1f
        private set

    private var encoding = AudioFormat.ENCODING_PCM_16BIT
    private var smoothedGain = 1f

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != AudioFormat.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != AudioFormat.ENCODING_PCM_FLOAT &&
            inputAudioFormat.encoding != AudioFormat.ENCODING_PCM_8BIT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val target = if (enabled()) {
            AudioSignal.balanceGain(
                AudioSignal.rms(inputBuffer, encoding),
                AudioSignal.peak(inputBuffer, encoding),
            )
        } else {
            1f
        }
        // Lower quickly when an interval gets loud, raise gently when it gets quiet.
        smoothedGain += (target - smoothedGain) * if (target < smoothedGain) 0.35f else 0.10f
        currentGain = smoothedGain
        onGainChanged(smoothedGain)

        val output = replaceOutputBuffer(size)
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val samples = inputBuffer.duplicate().asFloatBuffer()
                val out = output.asFloatBuffer()
                while (samples.hasRemaining()) {
                    out.put((samples.get() * smoothedGain).coerceIn(-1f, 1f))
                }
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                while (inputBuffer.hasRemaining()) {
                    val value = (inputBuffer.get().toInt() and 0xFF) - 128
                    val scaled = (value * smoothedGain).toInt().coerceIn(-128, 127)
                    output.put((scaled + 128).toByte())
                }
            }

            else -> {
                val samples = inputBuffer.duplicate().asShortBuffer()
                val out = output.asShortBuffer()
                while (samples.hasRemaining()) {
                    out.put((samples.get() * smoothedGain).toInt().coerceIn(-32_768, 32_767).toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.position(size)
        output.flip()
    }

    // Keep the gain across track/source changes so the new source starts at the level the
    // previous one ended with instead of jumping back to full volume.
    override fun onFlush() = Unit

    override fun onReset() {
        smoothedGain = 1f
        currentGain = 1f
        onGainChanged(1f)
    }
}
