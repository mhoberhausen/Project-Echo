# Huh? Android

Huh? is an offline-first Android conversation-memory app targeting a Pixel 8 Pro on Android
17. It records 16 kHz mono PCM, transcribes locally with `whisper.cpp`, optionally
assigns speakers with sherpa-onnx, removes only high-confidence filler words, stores sessions
in app-private SQLite, and optionally interprets a transcript with bundled Gemma 3 1B or a
user-configured OpenAI-compatible server on the private LAN.

## Implemented experience

- **Listen Now:** explicit phone recording with the bundled Accurate (`base.en`) Whisper model.
- **Keep an Ear Out:** foreground-service capture with VAD, configurable timing, durable
  WorkManager transcription, and visible status/notification controls.
- **Huh? Puck:** live Puck capture is not included in the 0.1.1 Play release.
- **Transcripts:** original and cleaned text, Whisper segment timestamps, optional speaker
  labels, one line per speaker turn, editing, and custom speaker names.
- **Sessions:** app-private history, status filtering, processing retry, sharing, and deletion.
- **Interpretation:** prioritized on-device Gemma or private-LAN OpenAI-compatible inference,
  with automatic instruction-model discovery and Gemma fallback.
- **Configuration:** audio-device picker plus persistent AI-provider ordering and enablement.

Bluetooth capture and third-party/cloud LLM execution are not implemented. LAN inference
supports unauthenticated OpenAI-compatible `/v1/models` and `/v1/chat/completions` endpoints
that resolve only to private/local addresses.

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
SelectedTranscriptInterpreter -> private-LAN OpenAI API -> bundled Gemma fallback

ActiveListeningService
  `- phone StreamingAudioCapture -> VAD / ConversationDetector
```

Important boundaries:

- `Transcriber`, `SpeakerDiarizer`, `AcousticDiarizationEngine`, and `TranscriptInterpreter`
  keep native/model implementations replaceable.
- `TranscriptionProcessor` owns the durable audio-to-session pipeline; `TranscriptionQueue`
  serializes WorkManager requests, while `SerializedTranscriber` also protects Whisper from
  concurrent manual/background use.
- `SessionRepository` is the persistence boundary. Manual transcript edits intentionally
  clear timestamps/speaker segments; transcript or speaker edits invalidate stale inference.
- `SelectionSettings` and `ActiveListeningSettings` hold small
  local preferences. Recordings and transcript content remain in app-private storage.

## Privacy and permissions

The app declares:

- `RECORD_AUDIO` for phone capture.
- foreground-service and notification permissions for visible active listening and local
  transcription work.
- `ACCESS_LOCAL_NETWORK` on Android 17 and `INTERNET` for direct communication with the
  private-LAN AI endpoint, plus optional Firebase reporting after
  the user explicitly enables it.

There is no account system, cloud transcription, or cloud-LLM connector. Firebase crash
diagnostics and anonymous product insights are optional, independently controlled, and off by
default. They never receive audio, transcripts, timestamps, speaker/session names, inferred
content, prompts, AI responses, local endpoint addresses, credentials, or settings values.

## Help and optional developer support

Huh? has no subscription, premium tier, feature gate, quota, or donor-specific behavior. Settings
includes **Help & support**, which opens the public [Huh? support page](https://mobileobie.com/apps/huh/support/)
in the Android browser. The page can provide app help and an optional way to support the developer;
the app does not process payments, record support activity, or send support data.

## Optional Firebase diagnostics

To enable the opt-in **Help improve Huh?** controls in a build, register
`com.mobileobie.echo` in the owner-managed Firebase project and place its downloaded
`google-services.json` at `app/google-services.json` (it is intentionally ignored by Git).
Without that file, the app builds normally and the telemetry implementation remains a no-op.
The app disables Firebase Analytics and Crashlytics by default in its manifest; a user must
enable each switch separately in Settings. Do not add Analytics breadcrumbs, custom user IDs,
custom keys/logs containing user data, or advertising integrations.
Cleartext traffic is enabled for user-selected LAN endpoints, while application validation
rejects LAN AI hosts that resolve outside private/local address ranges. LAN AI connectors are
unauthenticated and should not be used on an untrusted network.

## Toolchain

- Gradle 9.3.1 and Android Gradle Plugin 9.1.1
- Kotlin Compose plugin 2.2.10 and Compose BOM 2026.08.00
- compile/target SDK 37, minimum SDK 26
- Java 17 app bytecode using Android Studio's bundled JDK
- NDK 28.2.13676358, CMake 3.31.6, arm64-v8a only
- LiteRT-LM 0.16.1

## Releases

Version name and version code are defined in `app/build.gradle`. Release notes are recorded in
[`CHANGELOG.md`](CHANGELOG.md); use the matching section when creating a Git tag and its Google
Play release. Publish signed Android App Bundles (`.aab`) through Play App Signing. Keep upload
keystores, passwords, and local signing-property files outside this repository and out of source
control.

Run Gradle from this directory:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testInstrumentationUnitTest lintDebug assembleDebug assembleInstrumentationAndroidTest
```

With an authorized device connected, instrumentation targets the isolated
`com.mobileobie.echo.testbed` build and cannot clear the normal app's sessions or settings:

```powershell
.\gradlew.bat connectedInstrumentationAndroidTest
```

## Model setup

Model binaries are ignored by Git and belong in `app/src/main/assets/models/`.

Whisper:

```powershell
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
  structured response parsing, real local HTTP fixtures, provider routing/fallback, session
  metadata, and transcription processing.
- Instrumentation tests cover SQLite persistence, notification behavior, Compose journeys,
  the real bundled Gemma model, an opt-in real LAN endpoint test, settings persistence, and
  native sherpa-onnx startup.
- Native Whisper is compiled as part of the debug build. Firmware/protocol fixtures live in
  `firmware/xiao` and are run separately with PlatformIO/Python.

## Related documentation

- `SPEC.md` — product scope, completed behavior, and known boundaries.
- `docs/HUH_AUDIO_PROTOCOL_V1.md` — canonical Android/Puck wire contract.
- `docs/EXTERNAL_DEVICE_SETUP.md` — trusted-LAN Puck setup and troubleshooting.
- `app/src/main/assets/models/README.md` — model filenames and provenance.
- `third_party/SHERPA_ONNX_NOTICES.md` — diarization dependency notices.
