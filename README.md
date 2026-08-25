# Project Echo

Project Echo is the monorepo for **Huh?**, an offline-first conversation capture system.
Audio capture, Whisper transcription, and optional Gemma interpretation remain local.

## Repository layout

- `mobile/android` — Kotlin and Jetpack Compose Android application.
- `firmware/xiao` — PlatformIO firmware for the Seeed Studio XIAO ESP32S3 Sense Huh? Puck.
- `.agents` — project-local Android, mobile-development, and UI-design Codex skills.

## Build

Android:

```powershell
cd mobile/android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

XIAO firmware:

```powershell
cd firmware/xiao
platformio run -e xiao_esp32s3_sense -e xiao_esp32s3_sense_sd_test
```

The Android application ID and Kotlin namespace are `com.mobileobie.echo`. The user-facing
application name remains **Huh?**.
