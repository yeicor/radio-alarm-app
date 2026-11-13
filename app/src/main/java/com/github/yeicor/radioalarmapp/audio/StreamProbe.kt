package com.github.yeicor.radioalarmapp.audio

import com.github.yeicor.radioalarmapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class StreamProbeResult(
    val ok: Boolean,
    val detail: String,
    val contentType: String? = null,
    val resolvedUrl: String? = null,
)

/**
 * Validates more than an HTTP status: for HLS it walks the master playlist, the media
 * playlist and fetches the first segment. The segment (or the first bytes of a progressive
 * stream) is decoded for a short window, so a stream that answers but is almost silent is
 * reported as a failure instead of a false success.
 */
object StreamProbe {
    private val USER_AGENT = "RadioAlarmApp/${BuildConfig.VERSION_NAME} (Android alarm)"
    private const val PLAYLIST_BYTES = 128 * 1024
    private const val AUDIO_BYTES = 256 * 1024
    private const val MAX_ANALYSIS_MS = 3_000

    suspend fun probe(url: String, timeoutMs: Int = 8_000): StreamProbeResult = withContext(Dispatchers.IO) {
        runCatching { probeBlocking(url, timeoutMs) }
            .getOrElse { StreamProbeResult(false, it.message ?: "Could not reach the stream") }
    }

    private fun probeBlocking(rawUrl: String, timeoutMs: Int): StreamProbeResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return StreamProbeResult(false, "No stream URL configured")
        val scheme = runCatching { URI(trimmed).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            return StreamProbeResult(false, "Use an http:// or https:// stream URL")
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        val master = fetch(trimmed, deadline, maxBytes = PLAYLIST_BYTES)
            ?: return StreamProbeResult(false, "The stream did not respond")
        if (master.status !in 200..299) {
            return StreamProbeResult(false, "The stream returned HTTP ${master.status}", master.contentType)
        }
        val type = master.contentType.orEmpty().lowercase()
        val bodyText = master.sample.toString(Charsets.UTF_8)
        val looksHls = type.contains("mpegurl") || trimmed.substringBefore('?').endsWith(".m3u8") ||
            bodyText.trimStart().startsWith("#EXTM3U")

        if (looksHls) {
            return probeHls(master.url, bodyText, master.contentType, deadline)
        }

        if (master.sample.isEmpty()) return StreamProbeResult(false, "The stream returned no audio data", master.contentType)
        if (bodyText.trimStart().startsWith("<")) {
            return StreamProbeResult(false, "The URL returned a web page, not audio", master.contentType)
        }
        if (type.contains("text/html") || type.contains("application/json")) {
            return StreamProbeResult(false, "The URL returned ${type.substringBefore(';')}, not audio", master.contentType)
        }
        if (!looksLikeAudio(master.sample, type)) {
            return StreamProbeResult(
                false,
                "Endpoint reachable but no audio frames were found",
                master.contentType,
                master.url,
            )
        }

        val analysis = analyze(master.sample, deadline)
        return when {
            analysis == null -> StreamProbeResult(
                true,
                "Audio stream reachable (${prettyType(type)}) · silence check unavailable",
                master.contentType,
                master.url,
            )

            analysis.analyzed && !analysis.audible -> StreamProbeResult(
                false,
                "The stream is reachable but its audio is silent",
                master.contentType,
                master.url,
            )

            else -> StreamProbeResult(
                true,
                "Audio stream reachable (${prettyType(type)}) · audio is not silent",
                master.contentType,
                master.url,
            )
        }
    }

    private fun probeHls(
        masterUrl: String,
        masterBody: String,
        contentType: String?,
        deadline: Long,
    ): StreamProbeResult {
        var playlistUrl = masterUrl
        var body = masterBody
        repeat(3) {
            val variant = firstUriAfter(body, "#EXT-X-STREAM-INF")
            if (variant != null) {
                playlistUrl = resolve(playlistUrl, variant)
                val variantBody = fetch(playlistUrl, deadline, maxBytes = AUDIO_BYTES)
                    ?: return StreamProbeResult(false, "HLS playlist did not respond", contentType)
                if (variantBody.status !in 200..299) {
                    return StreamProbeResult(false, "HLS playlist returned HTTP ${variantBody.status}", contentType)
                }
                body = variantBody.sample.toString(Charsets.UTF_8)
                return@repeat
            }

            val segment = firstUriAfter(body, "#EXTINF")
                ?: firstMediaUri(body)
                ?: return StreamProbeResult(false, "HLS playlist contains no playable segments", contentType, playlistUrl)
            val segmentUrl = resolve(playlistUrl, segment)
            val segmentResponse = fetch(segmentUrl, deadline, maxBytes = AUDIO_BYTES)
                ?: return StreamProbeResult(false, "HLS audio segment did not respond", contentType, playlistUrl)
            if (segmentResponse.status !in 200..299) {
                return StreamProbeResult(false, "HLS audio segment returned HTTP ${segmentResponse.status}", contentType, playlistUrl)
            }
            if (segmentResponse.sample.isEmpty()) {
                return StreamProbeResult(false, "HLS audio segment was empty", contentType, playlistUrl)
            }

            val encrypted = body.lineSequence().any {
                val line = it.trim()
                line.startsWith("#EXT-X-KEY:") && !line.contains("METHOD=NONE")
            }
            if (encrypted) {
                return StreamProbeResult(
                    true,
                    "HLS stream reachable · playlist and audio segment OK · encrypted (silence not verifiable)",
                    contentType,
                    playlistUrl,
                )
            }

            val initBytes = mapUri(body)?.let { map ->
                fetch(resolve(playlistUrl, map), deadline, maxBytes = AUDIO_BYTES)
                    ?.takeIf { it.status in 200..299 }
                    ?.sample
            }
            val audioBytes = if (initBytes == null || initBytes.isEmpty()) {
                segmentResponse.sample
            } else {
                initBytes + segmentResponse.sample
            }
            val analysis = analyze(audioBytes, deadline)
            return when {
                analysis == null -> StreamProbeResult(
                    true,
                    "HLS stream reachable · playlist and audio segment OK · silence check unavailable",
                    contentType,
                    playlistUrl,
                )

                analysis.analyzed && !analysis.audible -> StreamProbeResult(
                    false,
                    "The HLS stream is reachable but its audio is silent",
                    contentType,
                    playlistUrl,
                )

                else -> StreamProbeResult(
                    true,
                    "HLS stream reachable · audio is not silent",
                    contentType,
                    playlistUrl,
                )
            }
        }
        return StreamProbeResult(false, "HLS playlist could not be resolved", contentType, playlistUrl)
    }

    private fun analyze(bytes: ByteArray, deadline: Long): AudioSampleAnalyzer.Result? {
        val remaining = (deadline - System.currentTimeMillis()).coerceAtMost(MAX_ANALYSIS_MS.toLong())
        if (remaining < 500L) return null
        return runCatching { AudioSampleAnalyzer.analyze(bytes, remaining.toInt()) }.getOrNull()
    }

    private fun mapUri(body: String): String? = body.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("#EXT-X-MAP:") }
        ?.let { line -> Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1) }

    private fun firstUriAfter(body: String, tag: String): String? {
        val lines = body.lineSequence().map { it.trim() }.toList()
        val index = lines.indexOfFirst { it.startsWith(tag) }
        if (index < 0) return null
        for (i in index + 1 until lines.size) {
            val line = lines[i]
            if (line.isNotBlank() && !line.startsWith("#")) return line
        }
        return null
    }

    private fun firstMediaUri(body: String): String? =
        body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }

    private fun resolve(base: String, relative: String): String = runCatching {
        URI(base).resolve(relative.trim()).toString()
    }.getOrDefault(relative.trim())

    private fun fetch(url: String, deadline: Long, maxBytes: Int): Response? {
        var current = url
        repeat(6) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = remaining.coerceAtMost(8_000L).toInt()
                readTimeout = remaining.coerceAtMost(8_000L).toInt()
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Icy-MetaData", "1")
                if (maxBytes > 0) setRequestProperty("Range", "bytes=0-${maxBytes - 1}")
            }
            val status = runCatching { connection.responseCode }.getOrElse {
                connection.disconnect()
                return null
            }
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) return null
                current = resolve(current, location)
                return@repeat
            }
            val stream: InputStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val sample = runCatching {
                stream.use { input ->
                    val buffer = ByteArray(maxBytes.coerceIn(4 * 1024, 512 * 1024))
                    var total = 0
                    while (total < buffer.size) {
                        val read = input.read(buffer, total, buffer.size - total)
                        if (read <= 0) break
                        total += read
                    }
                    buffer.copyOf(total)
                }
            }.getOrElse { ByteArray(0) }
            val contentType = connection.contentType
            connection.disconnect()
            return Response(current, status, sample, contentType)
        }
        return null
    }

    private fun looksLikeAudio(bytes: ByteArray, type: String): Boolean {
        if (type.startsWith("audio/") || type.contains("ogg") || type.contains("mpegurl")) return true
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        return when {
            b0 == 0x49 && b1 == 0x44 && b2 == 0x33 -> true // ID3
            b0 == 0xFF && (b1 and 0xE0) == 0xE0 -> true // MPEG/ADTS sync
            b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53 -> true // OggS
            b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43 -> true // fLaC
            b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 -> true // RIFF
            bytes.size >= 8 && b0 == 0x66 && b1 == 0x74 && b2 == 0x79 && b3 == 0x70 -> true // ftyp
            b0 == 0x47 -> true // MPEG-TS
            else -> false
        }
    }

    private fun prettyType(type: String): String = when {
        type.contains("mpeg") -> "MP3"
        type.contains("aac") || type.contains("mp4a") -> "AAC"
        type.contains("ogg") -> "Ogg"
        type.isBlank() -> "audio"
        else -> type.substringBefore(';')
    }

    private data class Response(
        val url: String,
        val status: Int,
        val sample: ByteArray,
        val contentType: String?,
    )
}
