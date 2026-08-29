# Project Echo / Huh?

Project Echo is the monorepo for **Huh?**, an offline-first Android conversation capture
system with an optional Seeed Studio XIAO ESP32S3 Sense recorder (“Huh? Puck”). Speech
recognition, speaker diarization, transcript cleanup, storage, and the bundled Gemma
interpretation path run locally.

## Repository layout

- `mobile/android` — Kotlin, Jetpack Compose, JNI, Whisper, sherpa-onnx, Gemma, and tests.
- `firmware/xiao` — PlatformIO firmware for SD-backed Puck capture and trusted-LAN transfer.
- `3d-model/huh-puck-v1` — printable enclosure sources, exports, and assembly notes.
- `.agents` — project-local Android, mobile-development, and UI-design guidance.

## Product status

The Android app supports manual phone recording, foreground active listening, saved session
history, transcript/result editing, Whisper timestamps, optional sherpa-onnx diarization,
speaker naming, and direct transfer from a configured Puck. The AI Selection screen persists
provider order and enablement, but only bundled on-device Gemma executes inference today.
Bluetooth capture and LAN/third-party LLM connectors are clearly labeled setup boundaries,
not working integrations.

Audio and transcripts are never sent to a cloud service by the implemented code. Android's
`INTERNET` permission exists solely for a raw TCP connection to a user-selected Puck on the
local network. See [`mobile/android/README.md`](mobile/android/README.md) for architecture,
model setup, permissions, and verification commands.

## Quick verification

Android (use Android Studio's bundled JDK):

```powershell
cd mobile/android
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

Puck firmware and host receiver:

```powershell
cd firmware/xiao
platformio run -e xiao_esp32s3_sense -e xiao_esp32s3_sense_sd_test
python -m unittest discover -s test -p "test_python_receiver.py"
```

Model binaries, generated build output, and `firmware/xiao/wifi.local.ini` are intentionally
not committed.
