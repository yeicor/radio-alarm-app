package com.github.yeicor.radioalarmapp.ui

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.yeicor.radioalarmapp.BuildConfig
import com.github.yeicor.radioalarmapp.alarm.PlaybackSnapshot
import com.github.yeicor.radioalarmapp.alarm.SafetyFix
import com.github.yeicor.radioalarmapp.audio.AudioSignal
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
import com.github.yeicor.radioalarmapp.update.UpdateChannel
import com.github.yeicor.radioalarmapp.update.UpdateInfo
import com.github.yeicor.radioalarmapp.util.TimeText
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    permissions: ReliabilityPermissions,
    nextAlarm: Alarm?,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onFix: (SafetyFix) -> Unit,
    onEditNextAlarm: () -> Unit,
    onTestNextAlarm: () -> Unit,
    onStopTest: () -> Unit,
    playback: PlaybackSnapshot?,
    liveRms: Float,
    updateInfo: UpdateInfo?,
    onDismissUpdate: (UpdateInfo) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current

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
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                HelpButton(
                    title = "Reliability & Settings",
                    text = "The alarm retries the radio stream with automatic multi-tier fallback to local audio, " +
                        "the phone alarm sound, and a built-in sound if silence or network issues occur.\n\n" +
                        "Software volume slider lets you lower the volume below the phone's minimum.",
                )
            }
        }

        // 1. Update available banner (if an update is ready)
        if (updateInfo != null && updateInfo.isNewer) {
            item {
                UpdateBanner(
                    updateInfo = updateInfo,
                    onUpdate = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    onDismiss = { onDismissUpdate(updateInfo) },
                )
            }
        }

        // 2. Sound, Volume & Alarm Testing Card
        item {
            GlassCard(accent = if (playback != null) Mint else Sunrise) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(
                            if (playback != null) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.VolumeUp,
                            if (playback != null) Mint else Sunrise,
                            44,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Sound & Volume",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                            )
                            Text(
                                if (playback != null) "Testing alarm in progress…" else "Global volume & audibility detector",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    SettingsSlider(
                        title = "Alarm volume",
                        value = settings.softwareVolumePercent,
                        valueText = "${settings.softwareVolumePercent}%",
                        range = 0f..100f,
                        steps = 99,
                        onChange = { onUpdateSettings(settings.copy(softwareVolumePercent = it.roundToInt())) },
                    )
                    Text(
                        "Note: Phone alarm volume also influences final sound level. Use this to lower volume below the phone's hardware minimum.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    SettingsSlider(
                        title = "Silence detector threshold",
                        value = settings.silenceThresholdPercent,
                        valueText = "${settings.silenceThresholdPercent}%",
                        range = 5f..150f,
                        steps = 28,
                        onChange = { onUpdateSettings(settings.copy(silenceThresholdPercent = it.roundToInt())) },
                    )
                    Text(
                        "Radio audio below this threshold is treated as silent and triggers fallbacks. Lower values are more sensitive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )

                    // Visual indicator while testing
                    if (playback != null) {
                        Spacer(Modifier.height(4.dp))
                        AudibilityIndicator(
                            liveRms = liveRms,
                            volumePercent = settings.softwareVolumePercent,
                            thresholdPercent = settings.silenceThresholdPercent,
                            sourceLabel = playback.sourceLabel,
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playback != null) {
                            Button(
                                onClick = onStopTest,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop alarm")
                            }
                        } else {
                            Button(
                                onClick = onTestNextAlarm,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Sunrise, contentColor = Color(0xFF211307)),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (nextAlarm != null) "Test alarm" else "Test audio")
                            }
                        }
                        if (nextAlarm != null) {
                            TextButton(onClick = onEditNextAlarm) {
                                Text("Edit alarm", color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // 3. Update Channel Settings Card
        item {
            GlassCard(accent = Sky) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.SystemUpdate, Sky, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Updates",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                            )
                            Text(
                                "Checks on app launch and when an alarm is dismissed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "Release Channel",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.updateChannel == UpdateChannel.STABLE,
                            onClick = { onUpdateSettings(settings.copy(updateChannel = UpdateChannel.STABLE)) },
                            label = { Text("Stable") },
                            leadingIcon = if (settings.updateChannel == UpdateChannel.STABLE) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Sunrise.copy(alpha = 0.2f),
                                selectedLabelColor = SunriseSoft,
                            ),
                        )
                        FilterChip(
                            selected = settings.updateChannel == UpdateChannel.NIGHTLY,
                            onClick = { onUpdateSettings(settings.copy(updateChannel = UpdateChannel.NIGHTLY)) },
                            label = { Text("Nightly (main)") },
                            leadingIcon = if (settings.updateChannel == UpdateChannel.NIGHTLY) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Sunrise.copy(alpha = 0.2f),
                                selectedLabelColor = SunriseSoft,
                            ),
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (settings.updateChannel == UpdateChannel.STABLE) {
                            "Official GitHub releases with tested release APKs."
                        } else {
                            "Continuous integration builds from every commit to the main branch."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check for updates now")
                    }
                }
            }
        }

        // 4. Reliability permissions & settings
        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("System permissions", Modifier.weight(1f))
                        HelpButton(
                            title = "System permissions",
                            text = "Exact alarms let Android wake the phone at the right minute. " +
                                "Notification permission is needed for ringing controls.",
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    PermissionRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notifications",
                        detail = if (permissions.notifications) "Allowed" else "Blocked",
                        ok = permissions.notifications,
                        actionLabel = "Allow",
                        onFix = { onFix(SafetyFix.NOTIFICATIONS) },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    PermissionRow(
                        icon = Icons.Rounded.Shield,
                        title = "Exact alarms",
                        detail = if (permissions.exactAlarms) "Allowed" else "Blocked",
                        ok = permissions.exactAlarms,
                        actionLabel = "Allow",
                        onFix = { onFix(SafetyFix.EXACT_ALARMS) },
                    )
                }
            }
        }

        // 6. Audio Fallback Configuration
        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Fallback audio", Modifier.weight(1f))
                        HelpButton(
                            title = "Fallback audio",
                            text = "When the radio stream cannot be reached, the app falls back to local music, " +
                                "the phone alarm ringtone, or a built-in sound.",
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    FallbackChoiceRow(
                        icon = Icons.Rounded.FolderOpen,
                        title = "Music folder",
                        detail = settings.fallbackFolderUri?.let { LocalAudio.displayName(context, it) } ?: "Not set",
                        action = if (settings.fallbackFolderUri.isNullOrBlank()) "Choose" else "Change",
                        onAction = { folderPicker.launch(null) },
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    FallbackChoiceRow(
                        icon = Icons.Rounded.MusicNote,
                        title = "Phone alarm sound",
                        detail = settings.systemAlarmSoundUri?.let { LocalAudio.displayName(context, it) } ?: "Phone default",
                        action = "Change",
                        onAction = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
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
                }
            }
        }

        // 7. Defaults for New Alarms
        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Defaults for new alarms", Modifier.weight(1f))
                        HelpButton(
                            title = "Defaults",
                            text = "New alarms start with these values. Existing alarms keep their own settings.",
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SettingsSlider(
                        title = "Volume ramp-up",
                        value = settings.defaultRampSeconds,
                        valueText = if (settings.defaultRampSeconds == 0) "Instant" else "${settings.defaultRampSeconds}s",
                        range = 0f..180f,
                        steps = 35,
                        onChange = { onUpdateSettings(settings.copy(defaultRampSeconds = it.roundToInt())) },
                    )
                    SettingsSlider(
                        title = "Snooze duration",
                        value = settings.defaultSnoozeMinutes,
                        valueText = TimeText.minutesLabel(settings.defaultSnoozeMinutes),
                        range = 1f..30f,
                        steps = 28,
                        onChange = { onUpdateSettings(settings.copy(defaultSnoozeMinutes = it.roundToInt())) },
                    )
                    SettingsSlider(
                        title = "Snoozes allowed",
                        value = settings.defaultMaxSnoozes,
                        valueText = if (settings.defaultMaxSnoozes == 0) "Off" else "${settings.defaultMaxSnoozes}",
                        range = 0f..5f,
                        steps = 4,
                        onChange = { onUpdateSettings(settings.copy(defaultMaxSnoozes = it.roundToInt())) },
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
private fun UpdateBanner(
    updateInfo: UpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = Sunrise.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Sunrise.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = Sunrise,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Update available: ${updateInfo.versionLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        if (updateInfo.channel == UpdateChannel.STABLE) "Official stable release" else "Nightly CI build (direct via nightly.link)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUpdate,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sunrise, contentColor = Color(0xFF211307)),
                ) {
                    Text("Download & install")
                }
                TextButton(onClick = onDismiss) {
                    Text("Later", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun AudibilityIndicator(
    liveRms: Float,
    volumePercent: Int,
    thresholdPercent: Int,
    sourceLabel: String,
) {
    val volMultiplier = (volumePercent / 100f).coerceIn(0.01f, 1f)
    val effectiveThreshold = AudioSignal.SILENCE_RMS * (thresholdPercent / 100f).coerceAtLeast(0.01f)
    val effectiveRms = liveRms * volMultiplier
    val ratio = if (effectiveThreshold > 0f) effectiveRms / effectiveThreshold else 0f
    val isAudible = ratio >= 1.0f

    Surface(
        color = if (isAudible) Mint.copy(alpha = 0.1f) else Warning.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, if (isAudible) Mint.copy(alpha = 0.25f) else Warning.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (isAudible) {
                        "✓ ${String.format(Locale.US, "%.1f", ratio)}× above threshold"
                    } else {
                        "⚠ Too quiet (${String.format(Locale.US, "%.1f", ratio)}× threshold)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isAudible) Mint else Warning,
                )
            }

            Spacer(Modifier.height(8.dp))

            // VU meter bar with threshold tick at 50%
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                val fillFraction = (ratio / 2.0f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fillFraction)
                        .background(if (isAudible) Mint else Warning),
                )
                // Threshold tick line at 50% (which represents 1.0× ratio)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(Alignment.Center)
                        .background(Color.White.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String,
    onFix: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (ok) Mint else Warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        if (!ok) {
            TextButton(onClick = onFix) { Text(actionLabel, color = Sunrise) }
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

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
