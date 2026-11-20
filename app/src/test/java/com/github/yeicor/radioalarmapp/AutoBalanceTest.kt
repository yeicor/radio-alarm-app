package com.github.yeicor.radioalarmapp

import android.media.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import com.github.yeicor.radioalarmapp.audio.AudioSignal
import com.github.yeicor.radioalarmapp.audio.AutoBalanceProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBalanceTest {
    private fun pcm16(samples: ShortArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(buffer::putShort)
        buffer.flip()
        return buffer
    }

    private fun sine(sampleCount: Int, amplitude: Double, sampleRate: Int = 44_100): ShortArray =
        ShortArray(sampleCount) { index ->
            (sin(2.0 * PI * 440.0 * index / sampleRate) * amplitude).toInt().toShort()
        }

    private fun processor(): AutoBalanceProcessor =
        AutoBalanceProcessor({ true }).also {
            it.configure(AudioProcessor.AudioFormat(44_100, 1, AudioFormat.ENCODING_PCM_16BIT))
        }

    /**
     * Regression test for byte-swapped PCM: a duplicated buffer defaults to big-endian, so
     * reading it without restoring native order used to turn playback into white noise.
     */
    @Test
    fun pcm16SinePassesThroughWithOnlyGainApplied() {
        val processor = processor()
        val samples = sine(2_000, 8_000.0)

        processor.queueInput(pcm16(samples))
        val output = processor.getOutput()
        assertEquals(samples.size * 2, output.remaining())
        val out = output.order(ByteOrder.nativeOrder()).asShortBuffer()
        var maxError = 0.0
        for (sample in samples) {
            val expected = (sample * processor.currentGain).toInt()
            maxError = maxOf(maxError, abs(out.get().toInt() - expected).toDouble())
        }
        assertEquals(0.0, maxError, 2.0)
    }

    @Test
    fun audioSignalMeasuresNativeEndianPcm() {
        val samples = sine(1_000, 8_000.0)
        val buffer = pcm16(samples)
        val expectedRms = kotlin.math.sqrt(samples.sumOf { it.toDouble() * it } / samples.size) / 32_768.0
        assertEquals(expectedRms.toFloat(), AudioSignal.rms(buffer, AudioFormat.ENCODING_PCM_16BIT), 0.001f)
        assertEquals(8_000f / 32_768f, AudioSignal.peak(buffer, AudioFormat.ENCODING_PCM_16BIT), 0.001f)
    }

    @Test
    fun loudAudioIsAttenuatedAndStaysIntelligible() {
        val processor = processor()
        val samples = sine(2_000, 30_000.0)

        // Feed several buffers so the smoothed gain converges.
        repeat(20) { processor.queueInput(pcm16(samples)) }

        processor.queueInput(pcm16(samples))
        val gain = processor.currentGain
        assertTrue("loud audio should be attenuated, gain=$gain", gain < 1f)
        assertEquals(AudioSignal.BALANCE_MIN_GAIN, gain, 0.02f)

        val out = processor.getOutput().order(ByteOrder.nativeOrder()).asShortBuffer()
        for (index in samples.indices) {
            val expected = samples[index] * gain
            val actual = out.get()
            assertTrue(
                "sample $index differs: expected $expected, got $actual",
                abs(actual - expected) < 2f,
            )
        }
    }

    @Test
    fun quietAudioIsBoostedWithoutClipping() {
        val processor = processor()
        val samples = sine(2_000, 2_000.0)

        repeat(30) { processor.queueInput(pcm16(samples)) }

        processor.queueInput(pcm16(samples))
        val gain = processor.currentGain
        assertTrue("quiet audio should be boosted, gain=$gain", gain > 1f)

        val out = processor.getOutput().order(ByteOrder.nativeOrder()).asShortBuffer()
        var peak = 0
        for (index in samples.indices) {
            val actual = out.get().toInt()
            peak = maxOf(peak, abs(actual))
            val expected = (samples[index] * gain).toInt()
            assertTrue("sample $index differs from pure gain", abs(actual - expected) <= 2)
        }
        assertTrue("boosted audio must not clip, peak=$peak", peak < 32_767)
    }

    @Test
    fun pcmFloatIsGainedWithoutGarbage() {
        val processor = AutoBalanceProcessor({ true }).also {
            it.configure(AudioProcessor.AudioFormat(44_100, 1, AudioFormat.ENCODING_PCM_FLOAT))
        }
        val samples = FloatArray(1_000) { index ->
            (sin(2.0 * PI * 440.0 * index / 44_100.0) * 0.3).toFloat()
        }
        val input = ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()

        processor.queueInput(input)
        val out = processor.getOutput().order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertEquals(samples.size, out.remaining())
        for (index in samples.indices) {
            val expected = samples[index] * processor.currentGain
            assertTrue("sample $index differs", abs(out.get() - expected) < 0.001f)
        }
    }
}
