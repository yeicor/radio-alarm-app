package com.github.yeicor.radioalarmapp.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.yeicor.radioalarmapp.BuildConfig
import com.github.yeicor.radioalarmapp.audio.LocalAudio
import com.github.yeicor.radioalarmapp.audio.ResolvedAudio
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AlarmRepository
import com.github.yeicor.radioalarmapp.data.RadioAlarmPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AudioTier { STREAM, LOCAL, SYSTEM, EMBEDDED }

data class PlaybackSnapshot(
    val tier: AudioTier,
    val sourceLabel: String,
    val detail: String,
    val verified: Boolean,
    val volumePercent: Int,
    val streamAttempt: Int = 0,
)

@androidx.annotation.OptIn(UnstableApi::class)
class AlarmService : Service() {
    companion object {
        const val ACTION_START = "com.github.yeicor.radioalarmapp.action.START_ALARM_SERVICE"
        const val ACTION_STOP = "com.github.yeicor.radioalarmapp.action.STOP_ALARM_SERVICE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
        const val EXTRA_TEST = "test_alarm"

        private const val KEY_ACTIVE_ID = "active_alarm_id"
        private const val KEY_ACTIVE_COUNT = "active_snooze_count"
        private const val KEY_ACTIVE_TEST = "active_alarm_test"
        private const val KEY_ACTIVE_STARTED = "active_alarm_started"

        private const val TAG = "RadioAlarmApp"

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Stops playback immediately when the user deletes or disables the alarm that is
         * currently ringing. Without this the service would keep playing a deleted alarm.
         */
        fun stopIfActive(context: Context, alarmId: Long) {
            if (!isRunning) return
            val active = RadioAlarmPrefs.get(context).getLong(KEY_ACTIVE_ID, -1L)
            if (active == alarmId) {
                runCatching { context.stopService(Intent(context, AlarmService::class.java)) }
            }
        }

        private val _playback = MutableStateFlow<PlaybackSnapshot?>(null)
        val playback: StateFlow<PlaybackSnapshot?> = _playback.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var repository: AlarmRepository
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    private var alarm: Alarm? = null
    private var snoozeCount = 0
    private var isTest = false
    private var foregroundStarted = false
    private var activeId: Long = -1L

    /**
     * Radio Alarm App never changes the alarm stream volume: the user's configured level is the
     * loudness ceiling. The gradual wake-up is produced by ramping the player's own volume
     * from 0 to 100% of that ceiling.
     */
    private var currentVolume = 0f
    private var rampJob: Job? = null
    private var watchdogJob: Job? = null
    private var switching = false
    private var sourceStartedAt = 0L
    private var lastVerified = false
    private var lastStatus = ""
    private var consecutiveFailures = 0

    private var streamAttempt = 0
    private var localSources: List<ResolvedAudio> = emptyList()
    private var localIndex = 0
    private var systemSources: List<ResolvedAudio> = emptyList()
    private var systemIndex = 0
    private lateinit var embeddedSource: ResolvedAudio
    private var currentTier: AudioTier? = null
    private var audioPlaybackActive = false
    private var systemRingtone: Ringtone? = null
    private var systemLoopJob: Job? = null

    private val audioCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            audioPlaybackActive = configs.any { config ->
                config.audioAttributes.usage == AudioAttributes.USAGE_ALARM
            }
        }
    }

    /**
     * The playback callback only fires when the active configuration changes. Polling the
     * authoritative list on every watchdog tick makes verification robust against a missed
     * callback (for example right after switching sources or when the UI process is busy).
     */
    private fun isAlarmPlaybackActive(): Boolean {
        val polled = runCatching {
            audioManager.getActivePlaybackConfigurations().any { config ->
                config.audioAttributes.usage == AudioAttributes.USAGE_ALARM
            }
        }.getOrNull()
        if (polled != null) audioPlaybackActive = polled
        return audioPlaybackActive
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        repository = AlarmRepository.get(this)
        audioManager = getSystemService(AudioManager::class.java)
        embeddedSource = LocalAudio.embeddedSource(this)
        createPlayer()
        registerAudioCallback()
        acquireWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val requestedId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        val id = if (requestedId > 0) requestedId else activeFromPrefs()
        if (id <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val found = repository.getAlarm(id)
        if (found == null) {
            Log.w(TAG, "Alarm $id no longer exists")
            stopSelf()
            return START_NOT_STICKY
        }
        if (requestedId > 0) {
            snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0
            isTest = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
            persistActive(id)
        } else {
            snoozeCount = activeCountFromPrefs()
            isTest = activeTestFromPrefs()
        }

        if (activeId == id && foregroundStarted && currentTier != null) {
            return START_STICKY
        }
        val previousId = activeId
        activeId = id
        alarm = found
        if (previousId != id) {
            // A second alarm can fire while another one is still ringing. Restart the retry
            // counters so the new alarm always gets its full chain, and take over the
            // foreground notification instead of leaving the old alarm's one behind.
            streamAttempt = 0
            lastStatus = ""
            lastVerified = false
        }
        beginAlarm(found, previousId)
        return START_STICKY
    }

    private fun beginAlarm(alarm: Alarm, previousId: Long) {
        // Never touch the configured alarm level. Only clear a muted state, which would make
        // every source inaudible even though the user's chosen volume is non-zero.
        runCatching { AudioManagerCompat.unmuteAlarmStream(audioManager) }
            .onFailure { Log.e(TAG, "Could not unmute alarm stream", it) }
        startVibration(alarm)
        startRemotePlayback()

        val initialStatus = if (alarm.streamUrl.isNotBlank()) "Starting radio…" else "Starting backup audio…"
        val notification = NotificationHelper.buildAlarmNotification(
            this, alarm, snoozeCount, isTest, initialStatus, fullScreen = true,
        )
        if (previousId > 0 && previousId != alarm.id) {
            NotificationHelper.cancelAlarmNotification(this, previousId)
        }
        // Safe to call again while already in the foreground: Android replaces the
        // foreground notification with the new alarm's one.
        startForeground(NotificationHelper.alarmNotificationId(alarm.id), notification)
        foregroundStarted = true
        startRamp(alarm)
        if (alarm.streamUrl.isNotBlank()) {
            attemptStream(alarm)
        } else {
            beginFallbackChain()
        }
    }

    private fun createPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("RadioAlarmApp/${BuildConfig.VERSION_NAME} (Android alarm)")
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSource = DefaultDataSource.Factory(this, http)
        val mediaSource = DefaultMediaSourceFactory(dataSource)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSource)
            .build()
            .apply {
                val attrs = Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_ALARM)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(attrs, false)
                setWakeMode(C.WAKE_MODE_NETWORK)
                volume = 1f
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
                        handleSourceFailure("Playback error: ${error.errorCodeName}")
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            handleSourceFailure("Audio source ended")
                        }
                    }
                })
            }
    }

    private fun startRemotePlayback() {
        AudioManagerCompat.requestAlarmAudioFocus(audioManager)
    }

    private fun attemptStream(alarm: Alarm) {
        if (!scope.isActive) return
        switching = false
        streamAttempt++
        currentTier = AudioTier.STREAM
        lastVerified = false
        consecutiveFailures = 0
        publish(AudioTier.STREAM, alarm.streamHost().ifBlank { "Web radio" }, "Connecting to radio…", false, streamAttempt)
        playUri(
            alarm.streamUrl,
            repeat = false,
            label = alarm.streamHost().ifBlank { "Web radio" },
        )
        startWatchdog(lambdaLabel = { "radio stream" })
    }

    private fun beginFallbackChain() {
        val current = alarm ?: return
        localSources = LocalAudio.resolve(this, current, repository.settings.value)
        systemSources = if (current.useSystemFallback) {
            LocalAudio.systemSources(this, repository.settings.value)
        } else {
            emptyList()
        }
        localIndex = 0
        systemIndex = 0
        if (localSources.isNotEmpty()) {
            playLocal()
        } else {
            playSystemOrEmbedded()
        }
    }

    private fun playLocal() {
        val current = alarm ?: return
        if (localIndex >= localSources.size) {
            playSystemOrEmbedded()
            return
        }
        val source = localSources[localIndex]
        currentTier = AudioTier.LOCAL
        lastVerified = false
        consecutiveFailures = 0
        publish(AudioTier.LOCAL, source.label, "Playing local backup…", false)
        updateNotification("Radio unavailable • playing local backup")
        playUri(source.uri.toString(), repeat = true, label = source.label)
        startWatchdog(lambdaLabel = { source.label })
    }

    private fun playSystemOrEmbedded() {
        if (systemIndex < systemSources.size) {
            val source = systemSources[systemIndex]
            currentTier = AudioTier.SYSTEM
            lastVerified = false
            consecutiveFailures = 0
            publish(AudioTier.SYSTEM, source.label, "Playing phone alarm sound…", false)
            updateNotification("Playing phone alarm sound")
            playSystemRingtone(source)
            startWatchdog(lambdaLabel = { source.label })
        } else {
            playEmbedded()
        }
    }

    /**
     * System alarm sounds are content:// ringtone URIs. Android 17 no longer lets media
     * players open those as files ("ringtone playback is available through
     * android.media.Ringtone"), so this tier uses the platform Ringtone API instead of
     * ExoPlayer. Volume is still the alarm stream, so the ramp applies unchanged.
     */
    private fun playSystemRingtone(source: ResolvedAudio) {
        stopCurrentSource()
        runCatching {
            val ringtone = RingtoneManager.getRingtone(this, source.uri)
                ?: error("No ringtone for ${source.uri}")
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
                // Keep the system fallback on the same player-volume ramp as the radio and
                // local files without touching the alarm stream volume.
                ringtone.setVolume(currentVolume)
            }
            systemRingtone = ringtone
            ringtone.play()
            sourceStartedAt = System.currentTimeMillis()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                systemLoopJob = scope.launch {
                    while (isActive && currentTier == AudioTier.SYSTEM) {
                        delay(1_000)
                        val active = systemRingtone
                        if (active != null && !active.isPlaying) {
                            runCatching { active.play() }
                        }
                    }
                }
            }
        }.onFailure {
            Log.e(TAG, "Could not play ${source.label}", it)
            handleSourceFailure("Could not open ${source.label}")
        }
    }

    private fun playEmbedded() {
        val source = embeddedSource
        currentTier = AudioTier.EMBEDDED
        lastVerified = false
        consecutiveFailures = 0
        publish(AudioTier.EMBEDDED, source.label, "Playing built-in alarm sound…", false)
        updateNotification("Playing built-in Radio Alarm App alarm")
        playUri(source.uri.toString(), repeat = true, label = source.label)
        startWatchdog(lambdaLabel = { source.label })
    }

    private fun playUri(uri: String, repeat: Boolean, label: String) {
        runCatching {
            stopCurrentSource()
            player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
            sourceStartedAt = System.currentTimeMillis()
        }.onFailure {
            Log.e(TAG, "Could not start $label", it)
            handleSourceFailure("Could not open $label")
        }
    }

    private fun stopCurrentSource() {
        systemLoopJob?.cancel()
        systemLoopJob = null
        runCatching { systemRingtone?.stop() }
        systemRingtone = null
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
    }

    private fun startWatchdog(lambdaLabel: () -> String) {
        watchdogJob?.cancel()
        lastVerified = false
        lastStatus = ""
        watchdogJob = scope.launch {
            delay(1_000)
            while (isActive) {
                val sourcePlaying = if (currentTier == AudioTier.SYSTEM) {
                    systemRingtone?.isPlaying == true
                } else {
                    player.playWhenReady && player.playbackState == Player.STATE_READY && player.isPlaying
                }
                // The ramp owns the player volume; re-apply the current value in case a
                // media session or audio policy changed it behind our back.
                enforcePlayerVolume()
                val streamVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val streamMuted = runCatching { audioManager.isStreamMute(AudioManager.STREAM_ALARM) }
                    .getOrDefault(false)
                val volumeOk = streamVolume > 0 && !streamMuted && currentVolume > 0.01f
                val playbackActive = isAlarmPlaybackActive()
                val verifiedNow = sourcePlaying && playbackActive && volumeOk
                if (!verifiedNow && consecutiveFailures % 3 == 0) {
                    Log.d(
                        TAG,
                        "verify: tier=$currentTier sourcePlaying=$sourcePlaying " +
                            "playWhenReady=${player.playWhenReady} state=${player.playbackState} " +
                            "isPlaying=${player.isPlaying} ringtone=${systemRingtone?.isPlaying} " +
                            "active=$playbackActive volume=${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}",
                    )
                }
                if (verifiedNow && !lastVerified) {
                    lastVerified = true
                    consecutiveFailures = 0
                    publish(
                        currentTier ?: AudioTier.STREAM,
                        lambdaLabel(),
                        "Audio output verified",
                        true,
                    )
                    updateNotification("Audio verified • ${lambdaLabel()}")
                }
                if (!verifiedNow) {
                    consecutiveFailures++
                    val elapsed = System.currentTimeMillis() - sourceStartedAt
                    if (elapsed > 14_000 && consecutiveFailures >= 3) {
                        handleSourceFailure("No verified audio output")
                        return@launch
                    }
                } else {
                    consecutiveFailures = 0
                }
                if (player.playbackState == Player.STATE_ENDED) {
                    handleSourceFailure("Audio source ended")
                    return@launch
                }
                delay(1_000)
            }
        }
    }

    private fun handleSourceFailure(reason: String) {
        if (switching) return
        switching = true
        watchdogJob?.cancel()
        lastVerified = false
        Log.w(TAG, "Source failure: $reason (tier=$currentTier attempt=$streamAttempt)")
        scope.launch {
            stopCurrentSource()
            when (currentTier) {
                AudioTier.STREAM -> {
                    if (streamAttempt < 3) {
                        publish(
                            AudioTier.STREAM,
                            alarm?.streamHost() ?: "Web radio",
                            "Radio failed. Retry $streamAttempt of 2…",
                            false,
                            streamAttempt,
                        )
                        updateNotification("Radio unavailable • retrying")
                        delay(1_500)
                        alarm?.let { attemptStream(it) }
                        switching = false
                    } else {
                        updateNotification("Radio unavailable • switching to backup")
                        beginFallbackChain()
                        switching = false
                    }
                }
                AudioTier.LOCAL -> {
                    localIndex++
                    if (localIndex < localSources.size) {
                        delay(800)
                        playLocal()
                    } else {
                        playSystemOrEmbedded()
                    }
                    switching = false
                }
                AudioTier.SYSTEM -> {
                    systemIndex++
                    if (systemIndex < systemSources.size) {
                        delay(800)
                        playSystemOrEmbedded()
                    } else {
                        playEmbedded()
                    }
                    switching = false
                }
                AudioTier.EMBEDDED, null -> {
                    publish(AudioTier.EMBEDDED, embeddedSource.label, "Restarting built-in alarm…", false)
                    delay(2_000)
                    playEmbedded()
                    switching = false
                }
            }
        }
    }

    private fun startRamp(alarm: Alarm) {
        rampJob?.cancel()
        currentVolume = 0f
        applyPlayerVolume()
        Log.i(
            TAG,
            "Starting player ramp 0→100% over ${alarm.rampSeconds}s; " +
                "alarm stream ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}/" +
                "${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)} left unchanged",
        )
        if (alarm.rampSeconds <= 0) {
            currentVolume = 1f
            applyPlayerVolume()
            return
        }
        rampJob = scope.launch {
            val started = System.currentTimeMillis()
            val duration = alarm.rampSeconds * 1000L
            var lastLoggedStep = -1
            while (isActive) {
                val fraction = ((System.currentTimeMillis() - started).toFloat() / duration)
                    .coerceIn(0f, 1f)
                currentVolume = fraction
                applyPlayerVolume()
                val step = (currentVolume * 100).roundToInt() / 5
                if (step != lastLoggedStep) {
                    lastLoggedStep = step
                    Log.d(
                        TAG,
                        "Player-volume ramp=${(currentVolume * 100).roundToInt()}% " +
                            "(alarm stream ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}/" +
                            "${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)} unchanged)",
                    )
                }
                if (fraction >= 1f) break
                delay(500)
            }
        }
    }

    private fun enforcePlayerVolume() {
        if (player.volume != currentVolume) {
            runCatching { player.volume = currentVolume }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            systemRingtone?.let { runCatching { it.setVolume(currentVolume) } }
        }
    }

    private fun applyPlayerVolume() {
        enforcePlayerVolume()
        publishVolume()
    }

    /**
     * Keeps the UI's volume reading in sync with the ramp. Verification snapshots are only
     * published on state changes, so the displayed percentage would otherwise stay at the
     * (low) value from the first verification for the whole ramp.
     */
    private fun publishVolume() {
        val percent = (currentVolume * 100).roundToInt().coerceIn(0, 100)
        _playback.value = _playback.value?.copy(volumePercent = percent)
    }

    private fun startVibration(alarm: Alarm) {
        if (!alarm.vibrate) return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val pattern = longArrayOf(0, 500, 350, 500, 900)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun registerAudioCallback() {
        runCatching {
            audioManager.registerAudioPlaybackCallback(
                audioCallback,
                Handler(Looper.getMainLooper()),
            )
        }
    }

    private fun acquireWakeLock() {
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioAlarmApp:Alarm")?.apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun updateNotification(status: String) {
        val current = alarm ?: return
        if (status == lastStatus) return
        lastStatus = status
        NotificationHelper.showAlarmNotification(
            this, current, snoozeCount, isTest, status, fullScreen = true,
        )
    }

    private fun publish(
        tier: AudioTier,
        label: String,
        detail: String,
        verified: Boolean,
        attempt: Int = streamAttempt,
    ) {
        _playback.value = PlaybackSnapshot(
            tier = tier,
            sourceLabel = label,
            detail = detail,
            verified = verified,
            volumePercent = (currentVolume * 100).roundToInt().coerceIn(0, 100),
            streamAttempt = attempt,
        )
        if (currentTier == AudioTier.STREAM && !lastVerified) {
            updateNotification(detail)
        }
    }

    private fun persistActive(id: Long) {
        RadioAlarmPrefs.get(this).edit()
            .putLong(KEY_ACTIVE_ID, id)
            .putInt(KEY_ACTIVE_COUNT, snoozeCount)
            .putBoolean(KEY_ACTIVE_TEST, isTest)
            .putLong(KEY_ACTIVE_STARTED, System.currentTimeMillis())
            .commit()
    }

    private fun activeFromPrefs(): Long =
        RadioAlarmPrefs.get(this).getLong(KEY_ACTIVE_ID, -1L)

    private fun activeCountFromPrefs(): Int =
        RadioAlarmPrefs.get(this).getInt(KEY_ACTIVE_COUNT, 0)

    private fun activeTestFromPrefs(): Boolean =
        RadioAlarmPrefs.get(this).getBoolean(KEY_ACTIVE_TEST, false)

    private fun stopAlarm() {
        stopSelf()
    }

    override fun onDestroy() {
        rampJob?.cancel()
        watchdogJob?.cancel()
        systemLoopJob?.cancel()
        runCatching { systemRingtone?.stop() }
        systemRingtone = null
        runCatching { vibrator?.cancel() }
        runCatching { player.stop(); player.release() }
        runCatching { audioManager.unregisterAudioPlaybackCallback(audioCallback) }
        releaseWakeLock()
        _playback.value = null
        if (foregroundStarted) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            alarm?.let { NotificationHelper.cancelAlarmNotification(this, it.id) }
        }
        RadioAlarmPrefs.get(this).edit()
            .remove(KEY_ACTIVE_ID)
            .remove(KEY_ACTIVE_COUNT)
            .remove(KEY_ACTIVE_TEST)
            .remove(KEY_ACTIVE_STARTED)
            .commit()
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }
}

private object AudioManagerCompat {
    fun requestAlarmAudioFocus(manager: AudioManager) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                )
            }
        }
    }

    fun unmuteAlarmStream(manager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (runCatching { manager.isStreamMute(AudioManager.STREAM_ALARM) }.getOrDefault(false)) {
                runCatching { manager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0) }
            }
        }
    }
}
