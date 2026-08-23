# Huh?

Huh? is an offline-first Android conversation-memory MVP. It records 16 kHz mono PCM,
transcribes speech through a replaceable `whisper.cpp` boundary, removes only
high-confidence filler words, and displays both the concise and original transcript.

## Current status

- Compose single-screen recording flow
- Runtime microphone permission
- In-memory `AudioRecord` capture using the voice-recognition audio source
- Recording timer and explicit idle/recording/processing/result/error states
- Deterministic filler removal with unit coverage
- No network permission and no Android/cloud speech-recognition fallback
- Pinned `whisper.cpp` v1.9.1 native engine and JNI bridge
- Bundled `tiny.en` and `base.en` models with Fast/Accurate selection
- LiteRT-LM 0.16.1 integration for bundled Gemma 3 1B int4 transcript interpretation

The complete recording-to-transcript pipeline runs locally. The application does not
request internet permission and never sends audio or transcript data off the device.
The Process action also runs locally: Gemma returns a summary, intent, key points, and
action items that are strictly validated before display.

## Build

The project uses Gradle 9.3.1, Android Gradle Plugin 9.1.1, AGP's built-in Kotlin,
the Kotlin 2.2.10 Compose compiler plugin, the June 2026 Compose BOM, and Android API 37. It is configured to run
Gradle with Android Studio's bundled JDK 25 while emitting Java 17-compatible app
bytecode. Install the Android 17/API 37 SDK platform before syncing. The app keeps
`minSdk 26` and does not depend on an OS speech service.

## Native transcription

The build pins NDK 28.2.13676358, CMake 3.31.6, and upstream `whisper.cpp` v1.9.1.
Only `arm64-v8a` is built because this MVP targets the Pixel 8 Pro. The JNI bridge
converts captured signed 16-bit PCM to normalized floats, runs English inference on a
background coroutine, and returns all generated text segments to Kotlin.

`ggml-tiny.en.bin` and `ggml-base.en.bin` live in `app/src/main/assets/models/`.
The selected model is copied to private app storage on first use. Model binaries are
ignored by Git; on a fresh checkout, download both with:

```powershell
app\src\main\cpp\whisper.cpp\models\download-ggml-model.cmd tiny.en app\src\main\assets\models
app\src\main\cpp\whisper.cpp\models\download-ggml-model.cmd base.en app\src\main\assets\models
```

## Transcript cleanup policy

The cleaner removes standalone `um`, `uh`, `erm`, `er`, and `hmm`, collapses immediate
word repetitions, and normalizes spacing. It preserves ambiguous words such as `like`,
`so`, and `well`, because deleting them blindly can change meaning. The original
Whisper output is always retained and available in the UI.

## On-device message processing

The POC uses the generic `gemma3-1b-it-int4.litertlm` artifact through LiteRT-LM's CPU
backend. Put the 584,417,280-byte model in `app/src/main/assets/models/` after accepting
the Gemma license at https://huggingface.co/litert-community/Gemma3-1B-IT. The binary is
ignored by Git and bundled directly into the APK. On first Process use it is copied to
private app storage because LiteRT-LM requires a filesystem model path.
