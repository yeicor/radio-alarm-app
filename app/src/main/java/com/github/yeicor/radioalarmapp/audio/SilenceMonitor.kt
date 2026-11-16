package com.github.yeicor.radioalarmapp.audio

import android.content.Context
import android.media.AudioFormat
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer

/**
 * Taps the decoded PCM stream and remembers when sound was last present. No recording
 * permission is needed because the app only inspects its own playback.
 */
@OptIn(UnstableApi::class)
class SilenceMonitor : TeeAudioProcessor.AudioBufferSink {
    @Volatile
    var lastAudibleAtMs: Long = 0L
        private set

    @Volatile
    var currentRms: Float = 0f
        private set

    @Volatile
    var volumeFactor: Float = 1f

    @Volatile
    var threshold: Float = AudioSignal.SILENCE_RMS

    private var encoding = AudioFormat.ENCODING_PCM_16BIT

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val r = AudioSignal.rms(buffer, encoding)
        currentRms = if (currentRms == 0f) r else (currentRms * 0.7f + r * 0.3f)
        val effectiveRms = r * volumeFactor
        if (AudioSignal.isAudible(effectiveRms, threshold)) {
            lastAudibleAtMs = SystemClock.elapsedRealtime()
        }
    }

    fun reset() {
        lastAudibleAtMs = 0L
        currentRms = 0f
    }
}

/**
 * Supplies a DefaultAudioSink with a [TeeAudioProcessor] so live playback can prove that
 * decoded samples are not silent. Audio offload is disabled because offloaded playback
 * bypasses the processor.
 */
@OptIn(UnstableApi::class)
class SilenceRenderersFactory(
    context: Context,
    private val monitor: SilenceMonitor,
) : DefaultRenderersFactory(context) {
    @Suppress("DEPRECATION")
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(monitor)))
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
        .build()
}
