package com.github.yeicor.radioalarmapp.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import kotlin.math.max

/**
 * Decodes a short window of an audio source and reports whether it actually contains
 * sound. This is what turns "the URL answered" into "the alarm will be audible".
 *
 * Analysis is deliberately bounded: at most a few seconds are decoded, so checking a
 * folder with thousands of songs only costs a fraction of a second.
 */
object AudioSampleAnalyzer {
    private const val MAX_ANALYSIS_MS = 3_000
    private const val MAX_DECODED_US = 2_500_000L

    data class Result(
        val readable: Boolean,
        val analyzed: Boolean,
        val audible: Boolean,
        val rms: Float,
        val detail: String,
    )

    fun analyze(context: Context, uri: Uri, timeoutMs: Int = MAX_ANALYSIS_MS): Result {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            decode(extractor, timeoutMs)
        } catch (error: Exception) {
            Result(false, false, false, 0f, error.message ?: "Could not read audio")
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun analyze(bytes: ByteArray, timeoutMs: Int = MAX_ANALYSIS_MS): Result {
        if (bytes.size < 512) {
            return Result(false, false, false, 0f, "Not enough audio data")
        }
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(ByteArraySource(bytes))
            decode(extractor, timeoutMs)
        } catch (error: Exception) {
            Result(true, false, false, 0f, error.message ?: "Could not decode audio")
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun decode(extractor: MediaExtractor, timeoutMs: Int): Result {
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: return Result(true, false, false, 0f, "No audio track found")

        extractor.selectTrack(trackIndex)
        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: return Result(true, false, false, 0f, "Unknown audio format")

        var codec: MediaCodec? = null
        return try {
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var channelCount = max(1, inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2))
            var sampleRate = max(1, inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100))
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceIn(500, MAX_ANALYSIS_MS)
            var inputDone = false
            var outputDone = false
            var decodedFrames = 0L
            var maxRms = 0f
            var sawOutput = false

            while (!outputDone && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        encoding = outputFormat.intOr(
                            MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT,
                        )
                        channelCount = max(1, outputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, channelCount))
                        sampleRate = max(1, outputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate))
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            maxRms = max(maxRms, AudioSignal.rms(output, encoding))
                            sawOutput = true
                            val bytesPerSample = when (encoding) {
                                AudioFormat.ENCODING_PCM_8BIT -> 1
                                AudioFormat.ENCODING_PCM_FLOAT -> 4
                                else -> 2
                            }
                            decodedFrames += info.size / (channelCount * bytesPerSample)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        if (decodedFrames * 1_000_000L / sampleRate >= MAX_DECODED_US) {
                            outputDone = true
                        }
                    }
                }
            }

            if (!sawOutput) {
                Result(true, false, false, 0f, "Audio could not be decoded")
            } else if (AudioSignal.isAudible(maxRms)) {
                Result(true, true, true, maxRms, "Audio is not silent")
            } else {
                Result(true, true, false, maxRms, "Audio is silent")
            }
        } catch (error: Exception) {
            Result(true, false, false, 0f, error.message ?: "Audio could not be decoded")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    private class ByteArraySource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val start = position.toInt()
            val length = minOf(size, data.size - start)
            System.arraycopy(data, start, buffer, offset, length)
            return length
        }

        override fun getSize(): Long = data.size.toLong()

        override fun close() = Unit
    }
}
