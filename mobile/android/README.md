# Huh? Android

Huh? is an offline-first Android conversation-memory app targeting a Pixel 8 Pro on Android
17. It records or receives 16 kHz mono PCM, transcribes locally with `whisper.cpp`, optionally
assigns speakers with sherpa-onnx, removes only high-confidence filler words, stores sessions
in app-private SQLite, and optionally interprets a transcript with bundled Gemma 3 1B.

## Implemented experience

- **Listen Now:** explicit phone recording with Fast (`tiny.en`) or Accurate (`base.en`)
  Whisper models.
- **Keep an Ear Out:** foreground-service capture with VAD, configurable timing, durable
  WorkManager transcription, and visible status/notification controls.
- **Huh? Puck:** Android-controlled capture, resumable SD-file transfer, direct Whisper
  queueing, and persisted device/stream diagnostics over a user-selected trusted LAN.
- **Transcripts:** original and cleaned text, Whisper segment timestamps, optional speaker
  labels, one line per speaker turn, editing, and custom speaker names.
- **Sessions:** app-private history, status filtering, processing retry, sharing, and deletion.
- **Interpretation:** on-device Gemma summary, intent, key points, action items, and tags.
- **Configuration:** audio-device picker plus persistent AI-provider ordering and enablement.

Bluetooth capture and LAN/third-party LLM execution are not implemented. Their UI entries
are configuration/setup boundaries and say so. Only an enabled bundled Gemma provider is
eligible for interpretation.

## Architecture

```text
Compose UI (HuhApp / RecorderScreen)
        |
RecorderViewModel ---- SessionRepository (SQLite)
        |                       ^
phone AudioRecord               |
        |                 WorkManager queue
        v                       |
WhisperTranscriber -> SpeakerDiarizer -> TranscriptCleaner
        |
SelectedTranscriptInterpreter -> bundled Gemma

ActiveListeningService
  |- phone StreamingAudioCapture -> VAD / ConversationDetector
  `- TcpExternalPcmSource -> finalized Puck PCM (bypasses phone VAD)
```

Important boundaries:

- `Transcriber`, `SpeakerDiarizer`, `AcousticDiarizationEngine`, and `TranscriptInterpreter`
  keep native/model implementations replaceable.
- `TranscriptionProcessor` owns the durable audio-to-session pipeline; `TranscriptionQueue`
  serializes WorkManager requests, while `SerializedTranscriber` also protects Whisper from
  concurrent manual/background use.
- `SessionRepository` is the persistence boundary. Manual transcript edits intentionally
  clear timestamps/speaker segments; transcript or speaker edits invalidate stale inference.
- `SelectionSettings`, `ActiveListeningSettings`, and `ExternalDeviceSettings` hold small
  local preferences. Recordings and transcript content remain in app-private storage.

## Privacy and permissions

The app declares:

- `RECORD_AUDIO` for phone capture.
- foreground-service and notification permissions for visible active listening and local
  transcription work.
- `ACCESS_LOCAL_NETWORK` on Android 17 and `INTERNET` for direct TCP communication with the
  user-configured Puck.

There is no analytics SDK, account system, cloud transcription, or implemented remote-LLM
connector. `android:usesCleartextTraffic="false"` remains set; Puck communication uses the
documented raw HUH1 socket protocol. The trusted-LAN Puck POC is unauthenticated and should
not be used on an untrusted network.

## Toolchain

- Gradle 9.3.1 and Android Gradle Plugin 9.1.1
- Kotlin Compose plugin 2.2.10 and Compose BOM 2026.08.00
- compile/target SDK 37, minimum SDK 26
- Java 17 app bytecode using Android Studio's bundled JDK
- NDK 28.2.13676358, CMake 3.31.6, arm64-v8a only
- LiteRT-LM 0.16.1

Run Gradle from this directory:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

With an authorized device connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Model setup

Model binaries are ignored by Git and belong in `app/src/main/assets/models/`.

Whisper:

```powershell
app\src\main\cpp\whisper.cpp\models\download-ggml-model.cmd tiny.en app\src\main\assets\models
app\src\main\cpp\whisper.cpp\models\download-ggml-model.cmd base.en app\src\main\assets\models
```

Gemma: obtain `gemma3-1b-it-int4.litertlm` from the LiteRT community release after accepting
its license. The expected artifact size is documented in `app/src/main/assets/models/README.md`.

Sherpa-onnx diarization:

```powershell
.\scripts\setup-sherpa-onnx.ps1
```

That script installs the pinned arm64 JNI library and segmentation/embedding models. If the
optional artifacts are absent, `SpeakerDiarizerProvider` uses an explicit pass-through
fallback and transcripts remain timestamped without speaker IDs.

## Tests

- JVM tests cover VAD, conversation timing, rolling buffers, cleanup, timestamps, speaker
  alignment/naming, protocol framing, sequence gaps, reconnect timing, external transfer,
  structured Gemma parsing, provider routing, session metadata, and transcription processing.
- Instrumentation tests cover SQLite persistence, notification behavior, Compose journeys,
  the real bundled Gemma model, settings persistence, and native sherpa-onnx startup.
- Native Whisper is compiled as part of the debug build. Firmware/protocol fixtures live in
  `firmware/xiao` and are run separately with PlatformIO/Python.

## Related documentation

- `SPEC.md` — product scope, completed behavior, and known boundaries.
- `docs/HUH_AUDIO_PROTOCOL_V1.md` — canonical Android/Puck wire contract.
- `docs/EXTERNAL_DEVICE_SETUP.md` — trusted-LAN Puck setup and troubleshooting.
- `app/src/main/assets/models/README.md` — model filenames and provenance.
- `third_party/SHERPA_ONNX_NOTICES.md` — diarization dependency notices.
