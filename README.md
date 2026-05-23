# Sticker

Sticker is an Android XR todo app for placing editable sticky notes around your house. Notes can be spawned in front of you, dragged into place, resized, recolored, prioritized, edited, given a clock-based alarm, snoozed, and restored after closing the app when XR anchor persistence is available.

The app is built for Galaxy XR-style Android XR devices with Jetpack XR APIs. It does not use the mobile Google Play Services AR runtime or `com.google.ar:core`.

## Features

- Compose-only UI for the main panel and spatial note panels.
- Spawn a note without typing first; blank notes become `New note`.
- Drag notes around the room with SceneCore movable spatial panels.
- Resize both placed notes and the main control panel.
- Minimize the main panel into a small `Open` button.
- Edit note text, alarm time, color, and priority from the main panel or the spatial note.
- Use clock-based alarms such as `09:30` or `6:45 PM`.
- Play an alarm tone only when a note becomes due.
- Snooze due alarms for 5 or 15 minutes.
- Show pin quality so you can tell whether a note is room-pinned, session-pinned, or restored from fallback placement.
- Save notes, completion state, alarms, alarm trigger state, note color, priority, note size, anchor ids, and fallback poses in Room.
- Persist room placement with ARCore for Jetpack XR local anchors when tracking/runtime support is available.
- Fall back to activity-space placement and retry anchor promotion when persistent room anchoring is not ready.

## Architecture

Sticker follows the Android architecture recommendations:

- `ui/` contains Compose screens, Activity glue, and ViewModels.
- `xr/` isolates Jetpack XR, SceneCore, anchors, and spatial entities.
- `data/` contains the Room-backed repository.
- `domain/` contains app models such as todo notes and spatial poses.
- Hilt provides the database and repository dependencies.
- ViewModels expose immutable UI state and receive UI events.
- People-facing text lives in `app/src/main/res/values/strings.xml`.

## Requirements

- Android Studio with Android SDK Platform 36 installed.
- JDK 17 or newer.
- A Galaxy XR / Android XR device or compatible Android XR emulator.
- Scene understanding permission granted at runtime.

## Build And Test

From the project root:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is created at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Run

Using Android Studio:

1. Open the project.
2. Let Gradle sync.
3. Select a connected Android XR device.
4. Run the `app` configuration.
5. Grant scene understanding permission when prompted.

Using the bundled Android CLI wrapper:

```powershell
.\tools\android.cmd info
.\tools\android.cmd describe --project_dir="$PWD"
.\tools\android.cmd run --device="<device-serial>" --apks="app\build\outputs\apk\debug\app-debug.apk"
```

## Notes On Persistence

Room keeps the todo data. Jetpack XR local anchor persistence keeps room placement when available. Because XR tracking can be unavailable at startup or in unsupported spaces, each note also stores a fallback activity-space pose so the app can show the note again and try to save a stronger room anchor later.

## Local-Only Files

Local agent instructions, private docs, SDK paths, build outputs, env files, signing keys, and service credentials are ignored by Git.
