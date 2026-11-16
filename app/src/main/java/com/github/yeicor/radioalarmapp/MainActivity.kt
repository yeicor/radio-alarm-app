package com.github.yeicor.radioalarmapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.yeicor.radioalarmapp.alarm.AlarmActionReceiver
import com.github.yeicor.radioalarmapp.alarm.NotificationHelper
import com.github.yeicor.radioalarmapp.alarm.SafetyFix
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AppSettings
import com.github.yeicor.radioalarmapp.data.withDefaults
import com.github.yeicor.radioalarmapp.ui.AlarmEditorScreen
import com.github.yeicor.radioalarmapp.ui.HomeScreen
import com.github.yeicor.radioalarmapp.ui.ReliabilityPermissions
import com.github.yeicor.radioalarmapp.ui.SettingsScreen
import com.github.yeicor.radioalarmapp.ui.RadioAlarmAppBackground
import com.github.yeicor.radioalarmapp.ui.theme.RadioAlarmAppTheme
import com.github.yeicor.radioalarmapp.util.Permissions
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_SAFETY = "com.github.yeicor.radioalarmapp.action.OPEN_SAFETY"
    }

    private val openSafety = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Recover any schedules lost to a force-stop or an OEM cleanup. This is deliberately
        // not done in Application.onCreate: that also runs for alarm broadcasts and could
        // re-schedule a one-time alarm that is firing at that exact moment.
        runCatching {
            com.github.yeicor.radioalarmapp.alarm.AlarmScheduler.scheduleAll(this)
            com.github.yeicor.radioalarmapp.alarm.SnoozeStore.rescheduleAll(this)
        }
        handleIntent(intent)
        setContent {
            RadioAlarmAppTheme {
                RadioAlarmAppBackground {
                    RadioAlarmAppRoot(openSafety)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_SAFETY) openSafety.value = true
    }
}

@Composable
private fun RadioAlarmAppRoot(openSafety: MutableStateFlow<Boolean>) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shouldOpenSafety by openSafety.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val liveRms by viewModel.liveRms.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var editorJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editorIsNew by rememberSaveable { mutableStateOf(false) }
    var editorIsDuplicate by rememberSaveable { mutableStateOf(false) }
    var permissionRefresh by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefresh++
                NotificationHelper.ensureChannels(context)
                viewModel.checkForUpdate(force = false)
            }
        }
        val dismissReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getStringExtra(AlarmActionReceiver.EXTRA_RESULT) == "dismissed") {
                    viewModel.checkForUpdate(force = true)
                }
            }
        }
        val filter = IntentFilter(AlarmActionReceiver.ACTION_ALARM_ENDED)
        ContextCompat.registerReceiver(context, dismissReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(dismissReceiver) }
        }
    }

    val permissions = remember(permissionRefresh, context) {
        ReliabilityPermissions(
            exactAlarms = Permissions.canScheduleExactAlarms(context),
            notifications = Permissions.notificationsEnabled(context),
        )
    }
    val nextAlarm = remember(alarms, permissionRefresh) { viewModel.nextAlarm() }
    val editorAlarm = remember(editorJson) { Alarm.fromJsonString(editorJson) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh++
        NotificationHelper.ensureChannels(context)
    }

    LaunchedEffect(shouldOpenSafety) {
        if (shouldOpenSafety) {
            editorJson = null
            showSettings = true
            openSafety.value = false
        }
    }

    BackHandler(enabled = editorJson != null || showSettings) {
        if (editorJson != null) {
            editorJson = null
        } else {
            showSettings = false
        }
    }

    fun openEditor(alarm: Alarm, isNew: Boolean, duplicate: Boolean) {
        editorJson = alarm.toJsonString()
        editorIsNew = isNew
        editorIsDuplicate = duplicate
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Permissions.openAppNotificationSettings(context)
        }
    }

    fun handleFix(fix: SafetyFix) {
        when (fix) {
            SafetyFix.EXACT_ALARMS -> Permissions.openExactAlarmSettings(context)
            SafetyFix.NOTIFICATIONS -> requestNotificationPermission()
            SafetyFix.EDIT_ALARM -> {
                val target = viewModel.nextAlarm()
                if (target != null) openEditor(target, isNew = false, duplicate = false) else showSettings = true
            }
            SafetyFix.CHARGE -> Toast.makeText(
                context,
                "Plug in your charger before sleeping.",
                Toast.LENGTH_LONG,
            ).show()
            SafetyFix.ALARM_VOLUME -> Permissions.openSoundSettings(context)
            SafetyFix.NONE -> Unit
        }
    }

    when {
        editorAlarm != null -> {
            AlarmEditorScreen(
                initial = editorAlarm,
                isNew = editorIsNew,
                isDuplicate = editorIsDuplicate,
                settings = settings,
                onBack = { editorJson = null },
                onSave = { alarm ->
                    viewModel.saveAlarm(alarm)
                    editorJson = null
                    if (!Permissions.notificationsEnabled(context)) requestNotificationPermission()
                },
                onDelete = { alarm ->
                    viewModel.deleteAlarm(alarm)
                    editorJson = null
                },
            )
        }

        showSettings -> {
            SettingsScreen(
                settings = settings,
                permissions = permissions,
                nextAlarm = nextAlarm,
                onBack = { showSettings = false },
                onUpdateSettings = viewModel::updateSettings,
                onFix = ::handleFix,
                onEditNextAlarm = {
                    nextAlarm?.let { openEditor(it, isNew = false, duplicate = false) }
                },
                onTestNextAlarm = { viewModel.testAlarm(nextAlarm) },
                onStopTest = viewModel::stopTest,
                playback = playback,
                liveRms = liveRms,
                updateInfo = updateInfo,
                onDismissUpdate = viewModel::dismissUpdate,
                onCheckUpdate = { viewModel.checkForUpdate(force = true) },
            )
        }

        else -> {
            HomeScreen(
                alarms = alarms,
                settings = settings,
                onAdd = {
                    openEditor(newAlarm(settings), isNew = true, duplicate = false)
                },
                onEdit = { openEditor(it, isNew = false, duplicate = false) },
                onDuplicate = { source ->
                    val copy = viewModel.alarms.value.firstOrNull { it.id == source.id }?.copy(
                        id = 0L,
                        label = if (source.label.isBlank()) "Alarm copy" else "${source.label} copy",
                        enabled = true,
                        createdAt = 0L,
                    )
                    if (copy != null) openEditor(copy, isNew = true, duplicate = true)
                },
                onDelete = viewModel::deleteAlarm,
                onToggle = viewModel::setEnabled,
                onTest = viewModel::testAlarm,
                onSettings = { showSettings = true },
                playback = playback,
                onStopTest = viewModel::stopTest,
                updateInfo = updateInfo,
                onDismissUpdate = viewModel::dismissUpdate,
            )
        }
    }
}

private fun newAlarm(settings: AppSettings): Alarm =
    Alarm(
        id = 0L,
        label = "",
        hour = 7,
        minute = 0,
        repeatDays = setOf(1, 2, 3, 4, 5),
        enabled = true,
        streamUrl = Alarm.DEFAULT_STREAM_URL,
    ).withDefaults(settings)
