@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Android\Android Studio1\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_USER_HOME=%~dp0android-user-home"

if not exist "%ANDROID_USER_HOME%" mkdir "%ANDROID_USER_HOME%"

"%~dp0android-cli\android.exe" --no-metrics --sdk="C:\Users\Marcus Hughes\AppData\Local\Android\Sdk" %*
