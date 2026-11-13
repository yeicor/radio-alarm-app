# Radio Alarm App

[![CI](https://github.com/yeicor/radio-alarm-app/actions/workflows/ci.yml/badge.svg)](https://github.com/yeicor/radio-alarm-app/actions/workflows/ci.yml)

> **Note:** This project is vibe-coded with very little human supervision. Treat it as a
> working experiment: review the code and test it on your own device before relying on it as
> your only alarm.

Radio Alarm App is a small Android alarm clock built around one rule: **the alarm must always
make noise**. It plays a radio stream, verifies that audio is actually reaching the phone's
output, ramps its own player volume, and falls back through local audio, the phone's alarm
sound and a bundled sound.

- Package: `com.github.yeicor.radioalarmapp`
- Version: 0.0.2 · Min Android 8.0 (API 26) · targetSdk 36 · compileSdk 37.0
- Repository: <https://github.com/yeicor/radio-alarm-app>

## Features

- **Radio alarms** – HLS (`.m3u8`), MP3, AAC and Ogg streams. A SomaFM example is pre-filled
  when creating an alarm.
- **Real audio verification** – player state, the alarm stream's active playback configuration
  and the actual alarm volume are checked continuously. A source is only marked verified when
  audio is genuinely playing.
- **Retries and fallback** – 3 stream attempts in total, then local files/folder, then the
  phone's alarm sound, then a bundled sound that works offline.
- **Volume ramp** – configurable 0–180 s ramp of the app's own player volume from 0% to 100%.
  The phone's alarm-stream volume is never changed; a muted stream is unmuted so the alarm can
  be heard.
- **Alarm-volume warnings** – muted/zero is critical and below 30% is a warning, shown
  immediately after saving or enabling an alarm and by the automatic checks.
- **Snooze** – 1–30 minutes and 0–5 snoozes per alarm, persisted across reboots.
- **Widget** – a small card with the next alarm, an alarm icon and three icon buttons:
  `−10 min`, `skip` and `+10 min`; after a skip the middle button becomes `undo`. The app shows
  the effective time plus the original time when the widget changed it.
- **Exact alarms** – `AlarmManager.setAlarmClock`, direct-boot-aware, restored after reboot,
  time change, timezone change and app update.
- **Full-screen wake-up** – lock-screen activity with snooze/dismiss, also available from the
  notification.
- **Automatic checks** – a low-power WorkManager job runs about ten times a day, verifies the
  next alarm and sends a notification when something needs attention.
- **Duplicate & adjust** – duplicate an alarm from its overflow menu and change only what you
  need.
- **Device-protected storage** – alarms and snoozes survive a reboot before first unlock.

## Permissions

The app asks for only two things:

1. **Exact alarms** – *Allow alarms & reminders.* Without it Android may delay the alarm.
2. **Notifications** – needed for the ringing screen, snooze controls and safety alerts.

Do Not Disturb is never read or changed, and the app does not ask for notification-listener or
battery-optimization access. Everything else is optional and lives in the alarm editor (local
backup audio and phone alarm sound).

## Install

Download `radio-alarm-app-<version>-release.apk` from the
[Releases](https://github.com/yeicor/radio-alarm-app/releases) page.

```sh
adb install -r radio-alarm-app-<version>-release.apk
```

Or copy the APK to the phone and open it (allow installation from unknown sources for your
file manager).

## Creating an alarm

1. Tap **New alarm**.
2. Pick the time and repeat days (Weekdays / Every day / Once, or individual days).
3. Optionally change the name.
4. Enter a stream URL and press **Test**. The test walks HLS master/media playlists and fetches
   an audio segment, so it catches broken CDNs rather than just checking an HTTP status.
5. Optionally add **local backup audio** – individual files and/or a folder.
6. Adjust the volume ramp, snooze and vibration.
7. Save. Saving or enabling an alarm runs an immediate check, including the alarm-volume
   warning.

Use the overflow menu to **Duplicate & adjust**, **Test now** or **Delete**. Deleting or
disabling the ringing alarm stops playback immediately.

Tap the **?** next to a section for a short explanation instead of a wall of text.

## Home-screen widget

Add the widget from your launcher's widget list. It shows an alarm icon, the next alarm in
short form (for example `07:30 · Tomorrow`, plus `+10m` when shifted) and three buttons:

| Button | Action |
| --- | --- |
| `−` | Move the next occurrence 10 minutes earlier |
| `>>` | Skip the next occurrence: repeating alarms go to their next repeat day, one-time alarms move one day later |
| `+` | Move the next occurrence 10 minutes later |
| `<<` | Undo the skip, shown instead of `>>` until the skipped time has passed |

Overrides are one-shot: they are consumed when the alarm fires or when it is edited,
re-enabled or deleted. Tapping the text opens the app; with no enabled alarm the widget shows
"No alarm set" and hides the buttons.

## What happens when an alarm fires

1. The exact alarm fires and the foreground alarm service starts with a wake lock.
2. The ringing notification opens the full-screen alarm over the lock screen.
3. The app unmutes the alarm stream if needed, then plays the radio while its player volume
   ramps from 0% to 100% of the configured alarm volume. Playback is verified; failures are
   retried twice.
4. If the radio still fails, local files/folder audio is tried in order.
5. Then the phone's alarm sound (if enabled for that alarm), using the platform `Ringtone` API
   and the same ramp.
6. Finally the bundled sound. If even that fails, the app keeps retrying rather than giving
   up.
7. Snooze or "I'm awake" stops playback. The alarm-stream volume is left as it was.

## Automatic checks

A WorkManager job runs about ten times a day while the battery is not low. For the next alarm
it checks:

- exact-alarm and notification permission;
- alarm volume (muted/zero critical, below 30% warning);
- battery level and charging state;
- radio reachability, with the available fallbacks;
- readability of the configured local backups.

Results appear in the app and, when something is wrong, as a high-priority notification. The
same check runs when you save or enable an alarm.

## Building and CI

Prerequisites: JDK 17+, Android SDK platform/build-tools 37, and the SDK path in
`local.properties`:

```properties
sdk.dir=/path/to/AndroidSdk
```

Build, test and lint:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To run the
instrumented widget test you need a device or emulator:

```sh
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions runs unit tests, lint and the debug build on every push and pull request, plus
an instrumented test on a local emulator. Creating a GitHub Release builds and attaches both
APKs; the production APK is signed with a keystore supplied only through GitHub secrets
(`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`).

Dependency updates are automated with [Renovate](https://docs.renovatebot.com/) using
`renovate.json5`. AndroidX/Compose, Kotlin, the Android Gradle plugin, GitHub Actions and the
Gradle wrapper are grouped into one pull request each.

## Project layout

```
app/src/main/java/com/github/yeicor/radioalarmapp/
├── MainActivity.kt              navigation, permissions, alarm testing
├── MainViewModel.kt             repository/state glue, scheduling after edits
├── RadioAlarmApp.kt             startup, channels, periodic checks
├── alarm/
│   ├── AlarmService.kt          playback, retry chain, verification, player-volume ramp
│   ├── AlarmScheduler.kt        exact alarms and snoozes
│   ├── AlarmReceiver.kt         fires alarms and reschedules repeats
│   ├── AlarmActionReceiver.kt   snooze/dismiss
│   ├── BootReceiver.kt          restore after boot/time/app update
│   ├── SafetyChecker.kt         reliability and alarm-volume checks, report cache
│   ├── SafetyCheckWorker.kt     low-power periodic check
│   ├── NotificationHelper.kt    channels and notifications
│   └── RingActivity.kt          full-screen lock-screen alarm UI
├── audio/
│   ├── StreamProbe.kt           HLS/progressive stream validation
│   └── LocalAudio.kt            local, system and embedded source resolution
├── data/                        alarm model, repository, device-protected prefs,
│                                one-shot widget overrides
├── ui/                          Compose home, editor and reliability screens
├── widget/                      compact next-alarm widget and button receiver
└── util/                        permissions and time formatting
```

## Known limitations

- Radio availability is outside the app's control. If a station is down and no local backup is
  configured, the app falls back to the phone alarm sound and the bundled sound.
- Some OEMs (Xiaomi, Huawei, Samsung, OnePlus, etc.) aggressively kill background services.
  Allow the app in any "autostart" or "protected apps" list if your phone has one.
- The app cannot play louder than the phone's configured alarm volume. If that volume is muted
  or zero, every source is silent; the automatic check catches this.
- `SET_ALARM_CLOCK` alarms are generally not delayed by Doze, but no app can guarantee
  behavior against every vendor power-saving implementation.

## Troubleshooting

- **No sound at all** – check that alarms & reminders and notifications are allowed, the phone
  is not in a vendor "ultra power saving" mode, and the alarm stream is neither muted nor at
  zero. The ringing screen shows the current source, ramp percentage and verification state.
- **Alarm volume warning** – raise the phone's alarm volume in sound settings (the **Raise
  alarm volume** button opens it). The app deliberately leaves that level alone.
- **Radio never starts at wake-up** – run **Test** in the alarm editor and check the result.
  Some streams require a user agent or block data-centre IPs; use a local backup as well.
- **No safety notifications** – allow notifications for the app and check that the phone is
  not blocking background work.
- **Widget buttons do not respond** – remove and re-add the widget so the launcher refreshes
  its pending intents, then check that an alarm is enabled.
- **Alarm does not repeat** – one-time alarms are disabled after firing by design. Set repeat
  days (e.g. Weekdays) for a recurring alarm.
