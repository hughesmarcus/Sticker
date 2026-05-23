# Sticker

Sticker is an Android XR MVP for placing timed todo notes around a house.

The current build targets Galaxy XR safely without the mobile Google Play Services ARCore runtime. UI is written with Jetpack Compose, todo data is stored with Room, app state is managed with MVVM, dependencies are wired with Hilt, and room placement uses Jetpack XR SceneCore plus ARCore for Jetpack XR anchors.

## What Works

- Runs as a Galaxy XR-compatible Compose Android panel.
- Lets you type, save, and place todo stickers in room space.
- Lets you attach no timer, 5m, 15m, 30m, or 60m timers to a sticker.
- Creates a persisted room anchor and spatial note when you tap `Spawn Note`.
- Renders placed stickers as separate SceneCore spatial panels attached to those anchors.
- Shows due timers and completed timers in a sticky-note style list.
- Lets you mark stickers done or delete them.
- Saves todo text, completion state, timer data, and persisted anchor UUIDs in Room.
- Avoids the mobile ARCore runtime and Google Play Services for AR.

## Run

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Connect a Galaxy XR device or Android XR emulator.
4. Run the `app` configuration.
5. Grant scene understanding permission when prompted.

If Android Studio asks for SDK packages, install Android SDK Platform 36 and the matching build tools.

## Android CLI

This project includes a Windows wrapper for Google's Android CLI:

```cmd
tools\android.cmd info
tools\android.cmd describe --project_dir="C:\Users\Marcus Hughes\AndroidStudioProjects\Sticker"
```

See `docs\android-cli.md` for setup notes and common commands.

## Next Milestones

- Add richer placement controls for wall/table/floor preference.
- Add a room map or room zone labels for filtering placed notes.
- Add room zones, due dates, and recurring chores.
