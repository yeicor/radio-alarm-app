package com.github.yeicor.radioalarmapp.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.yeicor.radioalarmapp.audio.LocalAudio
import com.github.yeicor.radioalarmapp.audio.StreamProbe
import com.github.yeicor.radioalarmapp.audio.StreamProbeResult
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AppSettings
import com.github.yeicor.radioalarmapp.ui.theme.Coral
import com.github.yeicor.radioalarmapp.ui.theme.Danger
import com.github.yeicor.radioalarmapp.ui.theme.Mint
import com.github.yeicor.radioalarmapp.ui.theme.Sky
import com.github.yeicor.radioalarmapp.ui.theme.Sunrise
import com.github.yeicor.radioalarmapp.ui.theme.SunriseSoft
import com.github.yeicor.radioalarmapp.ui.theme.TextPrimary
import com.github.yeicor.radioalarmapp.ui.theme.TextSecondary
import com.github.yeicor.radioalarmapp.ui.theme.Warning
import com.github.yeicor.radioalarmapp.util.TimeText
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

private val DAY_LABELS = listOf(
    1 to "Mon",
    2 to "Tue",
    3 to "Wed",
    4 to "Thu",
    5 to "Fri",
    6 to "Sat",
    7 to "Sun",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorScreen(
    initial: Alarm,
    isNew: Boolean,
    isDuplicate: Boolean,
    settings: AppSettings,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var label by remember { mutableStateOf(initial.label) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var repeatDays by remember { mutableStateOf(initial.repeatDays) }
    var streamUrl by remember { mutableStateOf(initial.streamUrl) }
    var localUris by remember { mutableStateOf(initial.localFallbackUris) }
    var rampSeconds by remember { mutableStateOf(initial.rampSeconds) }
    var snoozeMinutes by remember { mutableStateOf(initial.snoozeMinutes) }
    var maxSnoozes by remember { mutableStateOf(initial.maxSnoozes) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var useSystemFallback by remember { mutableStateOf(initial.useSystemFallback) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var testingStream by remember { mutableStateOf(false) }
    var streamTest by remember { mutableStateOf<StreamProbeResult?>(null) }

    val urlError = when {
        streamUrl.isBlank() -> null
        !streamUrl.trim().startsWith("http://") && !streamUrl.trim().startsWith("https://") ->
            "Use an http:// or https:// stream URL"
        else -> null
    }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> persistReadPermission(context, uri) }
            localUris = (localUris + uris.map { it.toString() }).distinct()
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            localUris = (localUris + uri.toString()).distinct()
        }
    }

    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                EditorHeader(
                    title = when {
                        isDuplicate -> "Duplicate alarm"
                        isNew -> "New alarm"
                        else -> "Edit alarm"
                    },
                    onBack = onBack,
                    onDelete = if (!isNew) ({ showDeleteDialog = true }) else null,
                )
            }

            item {
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            LocalTime.of(hour, minute).format(Alarm.TIME_FORMAT),
                            style = MaterialTheme.typography.displayLarge,
                            color = TextPrimary,
                        )
                        Text(
                            if (repeatDays.isEmpty()) "One-time alarm" else repeatDays.sorted().joinToString(" ") {
                                DAY_LABELS.first { d -> d.first == it }.second
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = SunriseSoft,
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Column(Modifier.padding(18.dp)) {
                        SectionLabel("Repeat")
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DAY_LABELS.forEach { (value, labelText) ->
                                FilterChip(
                                    selected = value in repeatDays,
                                    onClick = {
                                        repeatDays = if (value in repeatDays) repeatDays - value else repeatDays + value
                                    },
                                    label = { Text(labelText) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Sunrise.copy(alpha = 0.22f),
                                        selectedLabelColor = SunriseSoft,
                                        labelColor = TextSecondary,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = value in repeatDays,
                                        borderColor = Color.White.copy(alpha = 0.12f),
                                        selectedBorderColor = Sunrise.copy(alpha = 0.5f),
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { repeatDays = setOf(1, 2, 3, 4, 5) },
                                label = { Text("Weekdays") },
                            )
                            AssistChip(
                                onClick = { repeatDays = setOf(1, 2, 3, 4, 5, 6, 7) },
                                label = { Text("Every day") },
                            )
                            AssistChip(
                                onClick = { repeatDays = emptySet() },
                                label = { Text("Once") },
                            )
                        }
                    }
                }
            }

            item {
                GlassCard {
                    Column(Modifier.padding(18.dp)) {
                        SectionLabel("Name")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = label,
                            onValueChange = { if (it.length <= 60) label = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Morning radio") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors(),
                        )
                    }
                }
            }

            item {
                GlassCard(accent = Sky.copy(alpha = 0.4f)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.Radio, Sky)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Web radio",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            HelpButton(
                                title = "Web radio",
                                text = "HLS (.m3u8), MP3, AAC and Ogg streams work. Use Test to check the " +
                                    "stream before saving; it downloads a real audio segment, not just the playlist.",
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = streamUrl,
                            onValueChange = {
                                streamUrl = it
                                streamTest = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Stream URL") },
                            placeholder = { Text("https://…/playlist.m3u8") },
                            minLines = 2,
                            maxLines = 4,
                            isError = urlError != null,
                            supportingText = urlError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(
                                onClick = { streamUrl = Alarm.DEFAULT_STREAM_URL; streamTest = null },
                                label = { Text("Use SomaFM example") },
                                leadingIcon = { Icon(Icons.Rounded.Radio, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = {
                                    if (urlError != null) return@OutlinedButton
                                    testingStream = true
                                    streamTest = null
                                    scope.launch {
                                        streamTest = StreamProbe.probe(streamUrl.trim())
                                        testingStream = false
                                    }
                                },
                                enabled = streamUrl.isNotBlank() && !testingStream && urlError == null,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                if (testingStream) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(if (testingStream) "Testing" else "Test")
                            }
                        }
                        streamTest?.let { result ->
                            if (result.detail.isNotBlank()) {
                                StreamTestResult(result)
                            }
                        }
                    }
                }
            }

            item {
                GlassCard {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.LibraryMusic, Mint)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Local backup audio",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            HelpButton(
                                title = "Local backup audio",
                                text = "Files and folders are used automatically when the radio cannot play. " +
                                    "The phone alarm sound and the built-in sound follow as extra fallbacks.",
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val defaultFolderUri = settings.fallbackFolderUri
                        if (localUris.isEmpty() && defaultFolderUri != null) {
                            val folderName = remember(defaultFolderUri) {
                                LocalAudio.displayName(context, defaultFolderUri)
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Mint.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, Mint.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Using your default backup folder: $folderName",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Mint,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        localUris.forEach { raw ->
                            val name = remember(raw) { LocalAudio.displayName(context, raw) }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.04f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (raw.contains("tree")) Icons.Rounded.FolderOpen else Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Mint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { localUris = localUris - raw }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TextSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { filesLauncher.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream")) },
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Files")
                            }
                            OutlinedButton(
                                onClick = { folderLauncher.launch(null) },
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Folder")
                            }
                            if (localUris.isNotEmpty()) {
                                TextButton(onClick = { localUris = emptyList() }) {
                                    Text("Clear", color = TextSecondary)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(10.dp))
                        SettingSwitch(
                            icon = Icons.Rounded.MusicNote,
                            title = "Phone alarm sound fallback",
                            checked = useSystemFallback,
                            onCheckedChange = { useSystemFallback = it },
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.AutoMirrored.Rounded.VolumeUp, Sunrise)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Wake-up behavior",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            HelpButton(
                                title = "Volume and snooze",
                                text = "The app ramps its own player volume from 0% to 100% and never changes " +
                                    "the phone's alarm volume. Snooze restarts the same verified playback.",
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        ValueSlider(
                            title = "Volume ramp-up",
                            value = rampSeconds,
                            valueText = if (rampSeconds == 0) "Instant" else "${rampSeconds}s",
                            range = 0f..180f,
                            steps = 35,
                            onChange = { rampSeconds = it.roundToInt() },
                        )
                        ValueSlider(
                            title = "Snooze duration",
                            value = snoozeMinutes,
                            valueText = TimeText.minutesLabel(snoozeMinutes),
                            range = 1f..30f,
                            steps = 28,
                            onChange = { snoozeMinutes = it.roundToInt() },
                        )
                        ValueSlider(
                            title = "Snoozes allowed",
                            value = maxSnoozes,
                            valueText = if (maxSnoozes == 0) "Off" else "$maxSnoozes",
                            range = 0f..5f,
                            steps = 4,
                            onChange = { maxSnoozes = it.roundToInt() },
                        )
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(8.dp))
                        SettingSwitch(
                            icon = Icons.Rounded.Vibration,
                            title = "Vibrate while ringing",
                            checked = vibrate,
                            onCheckedChange = { vibrate = it },
                        )
                    }
                }
            }

        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color(0xF20A101D),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        ) {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            label = label.trim(),
                            hour = hour,
                            minute = minute,
                            repeatDays = repeatDays,
                            streamUrl = streamUrl.trim(),
                            localFallbackUris = localUris,
                            rampSeconds = rampSeconds,
                            snoozeMinutes = snoozeMinutes,
                            maxSnoozes = maxSnoozes,
                            vibrate = vibrate,
                            useSystemFallback = useSystemFallback,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (urlError == null) Sunrise else Sunrise.copy(alpha = 0.5f),
                    contentColor = Color(0xFF211307),
                ),
                enabled = urlError == null,
            ) {
                Text(if (isNew) "Create alarm" else "Save changes", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Alarm time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) { Text("Set", color = Sunrise) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Danger) },
            title = { Text("Delete alarm?") },
            text = { Text("This removes the alarm and cancels any pending snooze.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(initial)
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EditorHeader(title: String, onBack: () -> Unit, onDelete: (() -> Unit)?) {
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
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        HelpButton(
            title = "Alarm help",
            text = "Radio Alarm App verifies that audio is really playing and retries the stream twice. " +
                "Then it tries local audio, the phone alarm sound and finally a built-in sound. " +
                "The volume ramp only changes the app's player; your alarm volume stays as configured.",
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete alarm", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun StreamTestResult(result: StreamProbeResult) {
    val color = if (result.ok) Mint else Warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (result.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(result.detail, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun SettingSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (!description.isNullOrBlank()) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF211307),
                checkedTrackColor = Sunrise,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
            ),
        )
    }
}

@Composable
private fun ValueSlider(
    title: String,
    value: Int,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Surface(
                color = Sunrise.copy(alpha = 0.12f),
                shape = CircleShape,
            ) {
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

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Sunrise.copy(alpha = 0.7f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    focusedLabelColor = SunriseSoft,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Sunrise,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
)

private fun persistReadPermission(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
