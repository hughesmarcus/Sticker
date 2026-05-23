# Android CLI

Sticker can use Google's Android CLI through a project-local wrapper:

```cmd
tools\android.cmd info
tools\android.cmd describe --project_dir="C:\Users\Marcus Hughes\AndroidStudioProjects\Sticker"
tools\android.cmd docs search "Jetpack XR anchors"
tools\android.cmd docs fetch kb://android/develop/xr/jetpack-xr-sdk/arcore/anchors
tools\android.cmd run --apks=app\build\outputs\apk\debug\app-debug.apk
```

The wrapper sets:

- `JAVA_HOME` to Android Studio's bundled JBR at `C:\Program Files\Android\Android Studio1\jbr`
- `ANDROID_USER_HOME` to `tools\android-user-home` so Android CLI can keep local metadata out of your Windows profile
- `--no-metrics` so the CLI does not try to write analytics spool files under `%USERPROFILE%\.android`
- `--sdk` to `C:\Users\Marcus Hughes\AppData\Local\Android\Sdk`
- Android CLI executable to `tools\android-cli\android.exe`

Google's Windows docs recommend `winget install --id Google.AndroidCLI` or the `curl.exe` + `cmd` installer. The project also keeps a local direct-download executable so this repo can call Android CLI even when the PATH alias is not available in the current shell.

Run this after updating Android CLI globally:

```cmd
tools\android.cmd update
```

Then refresh the local executable if needed:

```cmd
curl.exe -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/android.exe -o tools\android-cli\android.exe
```

## Android Skills

Android skills from https://github.com/android/skills are installed for Codex with:

```cmd
tools\android.cmd skills add --all --agent=codex
```

Verify installed skills with:

```cmd
tools\android.cmd skills list --long
```
