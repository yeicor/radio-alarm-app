package com.github.yeicor.radioalarmapp.ui

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.yeicor.radioalarmapp.BuildConfig
import com.github.yeicor.radioalarmapp.alarm.SafetyFix
import com.github.yeicor.radioalarmapp.alarm.SafetyReport
import com.github.yeicor.radioalarmapp.audio.LocalAudio
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AppSettings
import com.github.yeicor.radioalarmapp.ui.theme.Danger
import com.github.yeicor.radioalarmapp.ui.theme.Mint
import com.github.yeicor.radioalarmapp.ui.theme.Sky
import com.github.yeicor.radioalarmapp.ui.theme.Sunrise
import com.github.yeicor.radioalarmapp.ui.theme.SunriseSoft
import com.github.yeicor.radioalarmapp.ui.theme.TextPrimary
import com.github.yeicor.radioalarmapp.ui.theme.TextSecondary
import com.github.yeicor.radioalarmapp.ui.theme.Warning
import com.github.yeicor.radioalarmapp.util.TimeText
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    permissions: ReliabilityPermissions,
    report: SafetyReport?,
    nextAlarm: Alarm?,
    checking: Boolean,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onRunCheck: () -> Unit,
    onFix: (SafetyFix) -> Unit,
    onEditNextAlarm: () -> Unit,
    onTestNextAlarm: () -> Unit,
) {
    val context = LocalContext.current
    var showAllIssues by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            onUpdateSettings(settings.copy(fallbackFolderUri = uri.toString()))
        }
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onUpdateSettings(settings.copy(systemAlarmSoundUri = uri?.toString()))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Reliability",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                HelpButton(
                    title = "Reliability checks",
                    text = "A low-power check runs about ten times a day. It verifies the alarm volume, " +
                        "battery, permissions, radio reachability and local backups, and sends a notification " +
                        "when something could stop the next alarm.\n\n" +
                        "The alarm itself retries the radio twice, then uses local audio, the phone alarm " +
                        "sound and finally a built-in sound. The app never changes your Do Not Disturb settings.",
                )
            }
        }

        item {
            SafetyHero(
                report = report,
                nextAlarm = nextAlarm,
                checking = checking,
                onRunCheck = onRunCheck,
                onEditNextAlarm = onEditNextAlarm,
                onTestNextAlarm = onTestNextAlarm,
            )
        }

        if (!report?.issues.isNullOrEmpty()) {
            item {
                GlassCard(accent = if (report.criticalCount > 0) Danger else Warning) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Findings",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRunCheck, enabled = !checking) {
                                Text(if (checking) "Checking…" else "Recheck", color = Sunrise)
                            }
                        }
                        val visible = if (showAllIssues) report.issues else report.issues.take(3)
                        visible.forEach { issue -> IssueRow(issue, onFix) }
                        if (report.issues.size > 3) {
                            TextButton(
                                onClick = { showAllIssues = !showAllIssues },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) {
                                Text(if (showAllIssues) "Show less" else "Show all ${report.issues.size}", color = Sunrise)
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Permissions", Modifier.weight(1f))
                        HelpButton(
                            title = "Why two permissions?",
                            text = "Exact alarms let Android wake the phone at the right minute. " +
                                "Notifications show the ringing screen, snooze controls and safety alerts. " +
                                "Radio Alarm App asks for nothing else.",
                        )
                    }
                    StatusRow(
                        icon = Icons.Rounded.Alarm,
                        title = "Exact alarms",
                        detail = if (permissions.exactAlarms) "Allowed" else "Blocked",
                        ok = permissions.exactAlarms,
                        actionLabel = "Allow",
                        onAction = { onFix(SafetyFix.EXACT_ALARMS) },
                        warningIsCritical = true,
                    )
                    StatusRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notifications",
                        detail = if (permissions.notifications) "On" else "Off",
                        ok = permissions.notifications,
                        actionLabel = "Turn on",
                        onAction = { onFix(SafetyFix.NOTIFICATIONS) },
                        warningIsCritical = true,
                    )
                }
            }
        }

        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Backup audio", Modifier.weight(1f))
                        HelpButton(
                            title = "Fallback order",
                            text = "Web radio (two retries) → local files or folder → phone alarm sound → " +
                                "built-in sound. The built-in sound is always available, even offline.",
                        )
                    }
                    FallbackChoiceRow(
                        icon = Icons.Rounded.FolderOpen,
                        title = "Default local folder",
                        detail = settings.fallbackFolderUri?.let { LocalAudio.displayName(context, it) } ?: "Not set",
                        action = if (settings.fallbackFolderUri == null) "Choose" else "Change",
                        onAction = { folderPicker.launch(null) },
                    )
                    if (settings.fallbackFolderUri != null) {
                        TextButton(
                            onClick = { onUpdateSettings(settings.copy(fallbackFolderUri = null)) },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) { Text("Clear", color = TextSecondary) }
                    }
                    DividerLine()
                    FallbackChoiceRow(
                        icon = Icons.Rounded.MusicNote,
                        title = "Phone alarm sound",
                        detail = if (settings.systemAlarmSoundUri != null) "Custom sound" else "Phone default",
                        action = "Choose",
                        onAction = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Radio Alarm App backup sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    settings.systemAlarmSoundUri?.let(Uri::parse),
                                )
                            }
                            ringtonePicker.launch(intent)
                        },
                    )
                    if (settings.systemAlarmSoundUri != null) {
                        TextButton(
                            onClick = { onUpdateSettings(settings.copy(systemAlarmSoundUri = null)) },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) { Text("Use phone default", color = TextSecondary) }
                    }
                    DividerLine()
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Shield, Mint, 38)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Built-in final fallback",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill("Always ready", Mint, Icons.Rounded.CheckCircle)
                    }
                }
            }
        }

        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Defaults for new alarms", Modifier.weight(1f))
                        HelpButton(
                            title = "Defaults",
                            text = "New alarms start with these values. Existing alarms keep their own " +
                                "settings; edit an alarm to change them individually.",
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SettingsSlider(
                        title = "Volume ramp-up",
                        value = settings.defaultRampSeconds,
                        valueText = if (settings.defaultRampSeconds == 0) "Instant" else "${settings.defaultRampSeconds}s",
                        range = 0f..180f,
                        steps = 35,
                        onChange = {
                            onUpdateSettings(settings.copy(defaultRampSeconds = it.roundToInt()))
                        },
                    )
                    SettingsSlider(
                        title = "Snooze duration",
                        value = settings.defaultSnoozeMinutes,
                        valueText = TimeText.minutesLabel(settings.defaultSnoozeMinutes),
                        range = 1f..30f,
                        steps = 28,
                        onChange = {
                            onUpdateSettings(settings.copy(defaultSnoozeMinutes = it.roundToInt()))
                        },
                    )
                    SettingsSlider(
                        title = "Snoozes allowed",
                        value = settings.defaultMaxSnoozes,
                        valueText = if (settings.defaultMaxSnoozes == 0) "Off" else "${settings.defaultMaxSnoozes}",
                        range = 0f..5f,
                        steps = 4,
                        onChange = {
                            onUpdateSettings(settings.copy(defaultMaxSnoozes = it.roundToInt()))
                        },
                    )
                }
            }
        }

        item {
            Text(
                "Radio Alarm App ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SafetyHero(
    report: SafetyReport?,
    nextAlarm: Alarm?,
    checking: Boolean,
    onRunCheck: () -> Unit,
    onEditNextAlarm: () -> Unit,
    onTestNextAlarm: () -> Unit,
) {
    val hasWarning = report != null && report.warningCount > 0
    GlassCard(accent = when {
        report == null -> Sky
        report.criticalCount > 0 -> Danger
        hasWarning -> Warning
        else -> Mint
    }) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    when {
                        report == null -> Icons.Rounded.Shield
                        report.criticalCount > 0 -> Icons.Rounded.BatteryAlert
                        hasWarning -> Icons.Rounded.Warning
                        else -> Icons.Rounded.CheckCircle
                    },
                    when {
                        report == null -> Sky
                        report.criticalCount > 0 -> Danger
                        hasWarning -> Warning
                        else -> Mint
                    },
                    48,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            nextAlarm == null -> "No upcoming alarm"
                            report == null -> "Not checked yet"
                            report.criticalCount > 0 -> "Needs attention"
                            hasWarning -> "Has warnings"
                            else -> "Ready"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        if (nextAlarm != null) {
                            "${nextAlarm.timeLabel()} · ${nextAlarm.label.ifBlank { "Alarm" }}"
                        } else {
                            "Create an alarm to enable checks."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (report != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    buildString {
                        append("Checked ${relativeTime(report.checkedAt)}")
                        report.batteryPercent?.let { append(" · Battery $it%") }
                        report.streamReachable?.let { append(if (it) " · Radio OK" else " · Radio down") }
                        if (report.localFallbackCount > 0) append(" · local backup")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRunCheck,
                    enabled = !checking,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sunrise, contentColor = Color(0xFF211307)),
                ) {
                    Icon(
                        if (checking) Icons.Rounded.Refresh else Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(if (checking) "Checking…" else "Check now")
                }
                if (nextAlarm != null) {
                    OutlinedButton(
                        onClick = onTestNextAlarm,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test")
                    }
                    TextButton(onClick = onEditNextAlarm) {
                        Text("Edit", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FallbackChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onAction) { Text(action, color = Sunrise) }
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Int,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Surface(color = Sunrise.copy(alpha = 0.12f), shape = CircleShape) {
                Text(
                    valueText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = SunriseSoft,
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Sunrise,
                activeTrackColor = Sunrise,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

private fun relativeTime(at: Long): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> {
            val date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            val now = ZonedDateTime.now()
            "${TimeText.relativeDay(date, now)} at ${date.format(Alarm.TIME_FORMAT)}"
        }
    }
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
