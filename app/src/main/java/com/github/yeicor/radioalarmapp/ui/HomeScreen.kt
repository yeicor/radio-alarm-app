package com.github.yeicor.radioalarmapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.yeicor.radioalarmapp.alarm.CheckSeverity
import com.github.yeicor.radioalarmapp.alarm.SafetyFix
import com.github.yeicor.radioalarmapp.alarm.SafetyIssue
import com.github.yeicor.radioalarmapp.alarm.SafetyReport
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AlarmOverride
import com.github.yeicor.radioalarmapp.data.AlarmOverrides
import com.github.yeicor.radioalarmapp.data.AppSettings
import com.github.yeicor.radioalarmapp.data.OverrideKind
import com.github.yeicor.radioalarmapp.ui.theme.Coral
import com.github.yeicor.radioalarmapp.ui.theme.Danger
import com.github.yeicor.radioalarmapp.ui.theme.Mint
import com.github.yeicor.radioalarmapp.ui.theme.NightCard
import com.github.yeicor.radioalarmapp.ui.theme.Sky
import com.github.yeicor.radioalarmapp.ui.theme.Sunrise
import com.github.yeicor.radioalarmapp.ui.theme.SunriseSoft
import com.github.yeicor.radioalarmapp.ui.theme.TextPrimary
import com.github.yeicor.radioalarmapp.ui.theme.TextSecondary
import com.github.yeicor.radioalarmapp.ui.theme.Warning
import com.github.yeicor.radioalarmapp.util.TimeText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    alarms: List<Alarm>,
    settings: AppSettings,
    report: SafetyReport?,
    permissions: ReliabilityPermissions,
    checking: Boolean,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onDuplicate: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onTest: (Alarm) -> Unit,
    onSettings: () -> Unit,
    onRunCheck: () -> Unit,
    onFix: (SafetyFix) -> Unit,
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var deleteTarget by remember { mutableStateOf<Alarm?>(null) }
    val context = LocalContext.current
    LaunchedEffect(alarms) {
        while (true) {
            now = ZonedDateTime.now()
            delay(20_000)
        }
    }
    // The widget writes overrides straight to storage. Refresh as soon as the app is
    // visible again so a shift/skip is reflected immediately, not on the next poll.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = ZonedDateTime.now()
    }

    val next = remember(alarms, now, context) {
        AlarmOverrides.next(context, alarms, now)
    }
    val nextOverride = remember(next, context) {
        next?.first?.let { AlarmOverrides.get(context, it.id) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HomeHeader(onSettings) }

            if (next != null) {
                item {
                    NextAlarmCard(
                        alarm = next.first,
                        target = next.second,
                        override = nextOverride,
                        now = now,
                        report = report,
                        onClick = { onEdit(next.first) },
                        onRunCheck = onRunCheck,
                        checking = checking,
                    )
                }
            } else {
                item { EmptyNextCard(onAdd = onAdd) }
            }

            if (alarms.isNotEmpty() && (permissions.setupMissing > 0 || (report?.warningCount ?: 0) > 0)) {
                item {
                    ReliabilityCard(
                        permissions = permissions,
                        report = report,
                        onSettings = onSettings,
                        onRunCheck = onRunCheck,
                        checking = checking,
                    )
                }
            }

            if (!report?.isReady.orFalse() && !report?.issues.isNullOrEmpty()) {
                item {
                    SafetySummaryCard(
                        report = report,
                        onFix = onFix,
                        onRunCheck = onRunCheck,
                        checking = checking,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Your alarms",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (alarms.isNotEmpty()) {
                        Text(
                            "${alarms.count { it.enabled }} active",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            if (alarms.isEmpty()) {
                item {
                    FirstAlarmCard(onAdd = onAdd)
                }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        settings = settings,
                        now = now,
                        onEdit = { onEdit(alarm) },
                        onDuplicate = { onDuplicate(alarm) },
                        onDelete = { deleteTarget = alarm },
                        onToggle = { enabled -> onToggle(alarm, enabled) },
                        onTest = { onTest(alarm) },
                    )
                }
            }

            item {
                Text(
                    "Radio Alarm App verifies real audio output, not just that the stream URL responded. " +
                        "If the radio fails it tries local audio, your phone alarm sound, and finally a built-in sound.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New alarm") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
            containerColor = Sunrise,
            contentColor = Color(0xFF211307),
        )
    }

    deleteTarget?.let { alarm ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Danger) },
            title = { Text("Delete this alarm?") },
            text = {
                Text(
                    "${alarm.timeLabel()} · ${alarm.label.ifBlank { alarm.repeatLabel() }} will be removed, " +
                        "including any pending snooze.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(alarm)
                    deleteTarget = null
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    Brush.linearGradient(listOf(Sunrise, Coral)),
                    shape = RoundedCornerShape(15.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Color(0xFF1A0E06),
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Radio Alarm App",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Radio alarms that refuse to fail",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
    }
}

@Composable
private fun NextAlarmCard(
    alarm: Alarm,
    target: ZonedDateTime,
    override: AlarmOverride?,
    now: ZonedDateTime,
    report: SafetyReport?,
    onClick: () -> Unit,
    onRunCheck: () -> Unit,
    checking: Boolean,
) {
    GradientCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NEXT ALARM",
                    style = MaterialTheme.typography.labelMedium,
                    color = SunriseSoft,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                val ready = report?.isReady == true && report.nextAlarmId == alarm.id
                if (ready) {
                    StatusPill("Ready", Mint, Icons.Rounded.CheckCircle)
                } else {
                    StatusPill(
                        if (report == null) "Not checked" else "${report.warningCount} to review",
                        if (report == null) TextSecondary else Warning,
                        Icons.Rounded.Shield,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    target.format(Alarm.TIME_FORMAT),
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    TimeText.relativeDay(target, now),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
            Text(
                alarm.label.ifBlank { "Wake up" },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (override != null) {
                Spacer(Modifier.height(8.dp))
                StatusPill(overrideLabel(override, now.zone), Coral, Icons.Rounded.Alarm)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    if (alarm.streamUrl.isNotBlank()) alarm.streamHost().ifBlank { "Web radio" } else "Local audio",
                    Sky,
                    if (alarm.streamUrl.isNotBlank()) Icons.Rounded.Radio else Icons.Rounded.MusicNote,
                )
                Text(
                    "${TimeText.until(target, now)} · ${alarm.repeatLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.09f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = if (report?.isReady == true) Mint else Sunrise,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        report == null -> "Run a sleep check before bed"
                        report.isReady -> "Last check passed"
                        report.criticalCount > 0 -> "${report.criticalCount} critical issue${if (report.criticalCount == 1) "" else "s"}"
                        else -> "${report.warningCount} warning${if (report.warningCount == 1) "" else "s"}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (report?.isReady == true) Mint else TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRunCheck, enabled = !checking) {
                    Text(if (checking) "Checking…" else "Check now", color = SunriseSoft)
                }
            }
        }
    }
}

@Composable
private fun EmptyNextCard(onAdd: () -> Unit) {
    GradientCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            StatusPill("NO UPCOMING ALARM", Sky, Icons.Rounded.Alarm)
            Spacer(Modifier.height(16.dp))
            Text(
                "Nothing scheduled.\nSet an alarm now.",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick a radio stream and Radio Alarm App will keep multiple sound backups ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ReliabilityCard(
    permissions: ReliabilityPermissions,
    report: SafetyReport?,
    onSettings: () -> Unit,
    onRunCheck: () -> Unit,
    checking: Boolean,
) {
    val critical = permissions.criticalMissing
    val title = if (critical > 0) {
        "Finish reliability setup"
    } else {
        "Review your sleep check"
    }
    GlassCard(accent = if (critical > 0) Coral else Warning) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    if (critical > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.WarningAmber,
                    if (critical > 0) Coral else Warning,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(
                        when {
                            critical > 0 -> "$critical critical permission${if (critical == 1) "" else "s"} may stop the alarm from waking you."
                            permissions.setupMissing > 0 -> "Improve DND bypass, lock-screen wake-up and bedtime checks."
                            else -> report?.issues?.firstOrNull()?.title ?: "A recent check found something to review."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSettings) { Text("Open setup", color = Sunrise) }
                TextButton(onClick = onRunCheck, enabled = !checking) {
                    Text(if (checking) "Checking…" else "Run check", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SafetySummaryCard(
    report: SafetyReport,
    onFix: (SafetyFix) -> Unit,
    onRunCheck: () -> Unit,
    checking: Boolean,
) {
    val top = report.issues.filter { it.severity >= CheckSeverity.WARNING }.take(2)
    if (top.isEmpty()) return
    GlassCard(accent = if (report.criticalCount > 0) Danger else Warning) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Next alarm check",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRunCheck, enabled = !checking) {
                    Text(if (checking) "Checking…" else "Refresh", color = Sunrise)
                }
            }
            top.forEach { issue ->
                IssueRow(issue = issue, onFix = onFix)
            }
        }
    }
}

@Composable
fun IssueRow(issue: SafetyIssue, onFix: (SafetyFix) -> Unit) {
    val color = when (issue.severity) {
        CheckSeverity.CRITICAL -> Danger
        CheckSeverity.WARNING -> Warning
        else -> Sky
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .background(color.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    issue.id == "stream" -> Icons.Rounded.CloudOff
                    issue.id == "battery" -> Icons.Rounded.BatteryAlert
                    issue.severity == CheckSeverity.CRITICAL -> Icons.Rounded.Error
                    else -> Icons.Rounded.WarningAmber
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(issue.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(issue.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (issue.fix != SafetyFix.NONE) {
                TextButton(
                    onClick = { onFix(issue.fix) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                ) {
                    Text(issue.fixLabel.ifBlank { "Fix" }, color = Sunrise, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    settings: AppSettings,
    now: ZonedDateTime,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val next = remember(alarm, now, context) { AlarmOverrides.effectiveOccurrence(context, alarm, now) }
    val override = remember(alarm, now, context) { AlarmOverrides.get(context, alarm.id) }
    val timeLabel = if (alarm.enabled && next != null) next.format(Alarm.TIME_FORMAT) else alarm.timeLabel()
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                        color = if (alarm.enabled) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    if (alarm.isOneTime) {
                        StatusPill("Once", Sky)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alarm.label.ifBlank { "Alarm" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (alarm.enabled) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (alarm.enabled && next != null) {
                        Text(
                            "  ·  ${TimeText.until(next, now)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sunrise.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        alarm.repeatLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Text("·", color = TextSecondary.copy(alpha = 0.5f))
                    Text(
                        if (alarm.streamUrl.isNotBlank()) "Radio" else "Local audio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    if (alarm.localFallbackUris.isNotEmpty() || settings.fallbackFolderUri != null) {
                        Text("·", color = TextSecondary.copy(alpha = 0.5f))
                        Text("Backups", style = MaterialTheme.typography.bodyMedium, color = Mint.copy(alpha = 0.9f))
                    }
                }
                if (override != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        overrideLabel(override, now.zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Coral,
                    )
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF211307),
                    checkedTrackColor = Sunrise,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = NightCard,
                    uncheckedBorderColor = TextSecondary.copy(alpha = 0.3f),
                ),
                modifier = Modifier.scale(0.9f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Alarm options", tint = TextSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuOpen = false; onEdit() },
                        leadingIcon = { Icon(Icons.Rounded.Alarm, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate & adjust") },
                        onClick = { menuOpen = false; onDuplicate() },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Test now") },
                        onClick = { menuOpen = false; onTest() },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Danger) },
                        onClick = { menuOpen = false; onDelete() },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Danger) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstAlarmCard(onAdd: () -> Unit) {
    GlassCard {
        Column(Modifier.padding(22.dp)) {
            IconBubble(Icons.Rounded.Alarm, Sunrise, 52)
            Spacer(Modifier.height(14.dp))
            Text("Set your first alarm", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Start with the SomaFM example or paste any HLS, MP3 or AAC radio stream. " +
                    "Add local music as a backup if you want.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Create an alarm", color = Sunrise)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Sunrise, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun Boolean?.orFalse(): Boolean = this == true

/** Human-readable description of a one-shot widget change, shown next to the effective time. */
private fun overrideLabel(override: AlarmOverride, zone: ZoneId): String {
    val originalMs = if (override.kind == OverrideKind.SKIP) override.skippedAt else override.naturalAt
    val original = Instant.ofEpochMilli(originalMs).atZone(zone).format(Alarm.TIME_FORMAT)
    return when (override.kind) {
        OverrideKind.SHIFT -> {
            val minutes = ((override.effectiveAt - override.naturalAt) / 60_000L).toInt()
            "Widget ${if (minutes > 0) "+" else ""}$minutes min · was $original"
        }
        OverrideKind.SKIP -> "Widget skipped $original"
    }
}

private val shortTime = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
