package com.github.yeicor.radioalarmapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AlarmRepository
import com.github.yeicor.radioalarmapp.ui.StatusPill
import com.github.yeicor.radioalarmapp.ui.theme.Coral
import com.github.yeicor.radioalarmapp.ui.theme.Mint
import com.github.yeicor.radioalarmapp.ui.theme.Night
import com.github.yeicor.radioalarmapp.ui.theme.Sky
import com.github.yeicor.radioalarmapp.ui.theme.Sunrise
import com.github.yeicor.radioalarmapp.ui.theme.SunriseSoft
import com.github.yeicor.radioalarmapp.ui.theme.TextPrimary
import com.github.yeicor.radioalarmapp.ui.theme.TextSecondary
import com.github.yeicor.radioalarmapp.ui.theme.RadioAlarmAppTheme
import com.github.yeicor.radioalarmapp.ui.theme.Warning
import com.github.yeicor.radioalarmapp.util.TimeText
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RingActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_SNOOZE_COUNT = "snooze_count"
        private const val EXTRA_TEST = "test_alarm"

        fun intent(context: Context, alarm: Alarm, snoozeCount: Int, test: Boolean): Intent =
            Intent(context, RingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_ALARM_ID, alarm.id)
                putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
                putExtra(EXTRA_TEST, test)
            }
    }

    private var alarmId: Long = -1L
    private var snoozeCount: Int = 0
    private var test: Boolean = false
    private var currentAlarm: Alarm? = null

    private val endedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmActionReceiver.ACTION_ALARM_ENDED) {
                val id = intent.getLongExtra(AlarmActionReceiver.EXTRA_ALARM_ID, -1L)
                if (id == -1L || id == alarmId) finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }

        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        test = intent.getBooleanExtra(EXTRA_TEST, false)
        currentAlarm = AlarmRepository.get(this).getAlarm(alarmId)
        if (currentAlarm == null) {
            finishAndRemoveTask()
            return
        }

        renderAlarm()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (newId <= 0) return
        alarmId = newId
        snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        test = intent.getBooleanExtra(EXTRA_TEST, false)
        currentAlarm = AlarmRepository.get(this).getAlarm(alarmId)
        if (currentAlarm == null) {
            finishAndRemoveTask()
            return
        }
        renderAlarm()
    }

    private fun renderAlarm() {
        val alarm = currentAlarm ?: return
        setContent {
            RadioAlarmAppTheme {
                val playback by AlarmService.playback.collectAsStateWithLifecycle()
                AlarmRingScreen(
                    alarm = alarm,
                    snoozeCount = snoozeCount,
                    test = test,
                    playback = playback,
                    onSnooze = ::snooze,
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            endedReceiver,
            IntentFilter(AlarmActionReceiver.ACTION_ALARM_ENDED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(endedReceiver) }
        super.onStop()
    }

    private fun snooze() {
        sendAction(AlarmActionReceiver.ACTION_SNOOZE, snoozeCount)
        finishAndRemoveTask()
    }

    private fun dismiss() {
        sendAction(AlarmActionReceiver.ACTION_DISMISS, 0)
        finishAndRemoveTask()
    }

    private fun sendAction(action: String, count: Int) {
        val intent = Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmActionReceiver.EXTRA_SNOOZE_COUNT, count)
            putExtra(AlarmActionReceiver.EXTRA_TEST, test)
        }
        sendBroadcast(intent)
    }
}

@Composable
private fun AlarmRingScreen(
    alarm: Alarm,
    snoozeCount: Int,
    test: Boolean,
    playback: PlaybackSnapshot?,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    val pulse = rememberInfiniteTransition(label = "alarmPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
        label = "glowScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2A1610),
                        Color(0xFF150F18),
                        Night,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
                .size(330.dp)
                .scale(glow)
                .background(
                    Brush.radialGradient(
                        listOf(Coral.copy(alpha = 0.28f), Sunrise.copy(alpha = 0.08f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (test) Sky.copy(alpha = 0.15f) else Sunrise.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (test) Sky.copy(alpha = 0.35f) else Sunrise.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (test) Icons.Rounded.Alarm else Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (test) Sky else Sunrise,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (test) "TEST ALARM" else "WAKE UP",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (test) Sky else SunriseSoft,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(62.dp))
            Text(
                alarm.timeLabel(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.05f),
                color = TextPrimary,
            )
            Text(
                alarm.label.ifBlank { "Time to wake up" },
                style = MaterialTheme.typography.headlineMedium,
                color = SunriseSoft,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "${alarm.repeatLabel()} · ${if (alarm.streamUrl.isNotBlank()) alarm.streamHost() else "Local audio"}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(38.dp))
            AudioStatusCard(playback)

            if (!test && alarm.maxSnoozes > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (snoozeCount < alarm.maxSnoozes) {
                        "Snooze ${snoozeCount + 1} of ${alarm.maxSnoozes}"
                    } else {
                        "No snoozes left"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Sunrise,
                    contentColor = Color(0xFF211307),
                ),
            ) {
                Text("I'm awake", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (!test && alarm.maxSnoozes > snoozeCount) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Icon(Icons.Rounded.Snooze, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Snooze ${TimeText.minutesLabel(alarm.snoozeMinutes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Radio Alarm App keeps your alarm volume and ramps its own playback from 0% to 100%. Raise the phone's alarm volume if you can't hear it.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AudioStatusCard(playback: PlaybackSnapshot?) {
    val verified = playback?.verified == true
    val color = when {
        verified -> Mint
        playback == null -> Sky
        else -> Warning
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        verified -> Icons.Rounded.CheckCircle
                        playback?.tier == AudioTier.STREAM -> Icons.Rounded.Radio
                        playback?.tier == AudioTier.LOCAL -> Icons.Rounded.MusicNote
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        playback == null -> "Starting audio…"
                        verified -> "Audio output verified"
                        else -> playback.detail
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    playback?.let { "${it.sourceLabel} · ${it.volumePercent}%" } ?: "Preparing the most reliable source",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(
                text = when {
                    verified -> "VERIFIED"
                    playback?.tier == AudioTier.STREAM -> "RADIO"
                    playback?.tier == AudioTier.LOCAL -> "LOCAL"
                    playback?.tier == AudioTier.SYSTEM -> "PHONE"
                    else -> "BUILT-IN"
                },
                color = color,
            )
        }
    }
}
