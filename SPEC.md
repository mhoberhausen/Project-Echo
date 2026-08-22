---
title: Local Voice Interpreter (Android)
description: An offline-first Android app that records speech, transcribes it locally, interprets it with an on-device LLM, and displays structured results.
published: true
date: 2026-08-06T00:00:00Z
tags: android, offline-ai, speech-recognition, local-llm, privacy
editor: markdown
dateCreated: 2026-08-06T00:00:00Z
status: inbox
---

# Local Voice Interpreter (Android)

## Current Summary

Local Voice Interpreter is an offline-first Android application idea focused on turning spoken conversations into structured knowledge entirely on-device. Milestone 1 is intentionally narrow: prove the microphone -> local transcription -> local LLM -> structured JSON -> UI pipeline using Kotlin, Jetpack Compose, `whisper.cpp`, and LiteRT-LM without any cloud dependency.

## Milestone 1 Implementation Status

Last verified on a Pixel 8 Pro running Android 17 on 2026-08-21.

### Completed

- [x] Kotlin, Jetpack Compose, MVVM, Coroutines, and StateFlow application foundation
- [x] Microphone permission and 16 kHz mono PCM capture through `AudioRecord`
- [x] Start/stop recording flow with a visible timer
- [x] Fully local `whisper.cpp` transcription with bundled `tiny.en` and `base.en` models
- [x] Deterministic filler-word cleanup with access to the original transcript
- [x] Fully local LiteRT-LM inference with bundled Gemma 3 1B int4
- [x] Structured response parsing for summary, intent, key points, and action items
- [x] Resilient parsing when Gemma adds metadata or omits an empty `action_items` array
- [x] Processed-result UI, including transcript disclosure and empty action-item handling
- [x] No `INTERNET` permission, cloud API, account, or external transmission path
- [x] Debug APK builds, installs, and launches on the target Pixel 8 Pro
- [x] JVM parser regression tests and Pixel tests for Gemma inference and result rendering

### Remaining

- [ ] Add live or near-live partial transcript updates; the current MVP transcribes after Stop
- [ ] Run and document the complete workflow in Airplane Mode
- [ ] Run and document a two-minute recording stability test
- [ ] Exercise all specified model, transcription, and malformed-JSON error paths
- [ ] Tighten the prompt to require summaries of at most three sentences and exact name preservation

## UI/UX Pivot: Mode-Based App Shell

The light MVP pipeline remains intact, but the next UI foundation expands the original
single-screen concept into a small app shell. This section supersedes the original
single-screen UI constraint for new work while keeping persistence and Live Mode
processing explicitly staged.

### Home

- [x] Present two prominent choices: **Manual Mode** and **Live Mode**.
- [x] Use a bold diagonal split inspired by the supplied reference without copying its palette.
- [x] Use **Choose capture mode** as the sole Home prompt and devote most of the available
  height to the Manual / Live selection surface.
- [x] Manual Mode opens the existing record -> stop -> transcribe -> Process workflow.
- [x] Live Mode initially opens an explanatory placeholder until silence segmentation and
  near-live transcript capture are implemented.
- [x] Android Back returns from either mode to Home instead of exiting the app.

### Manual Mode

- [x] Replace the idle **Start listening** button with a large circular play control.
- [x] Present the bundled Whisper choices as a simple **Fast** / **Accurate** toggle while
  keeping the underlying `tiny.en` / `base.en` mapping internal to the app.

### Navigation Drawer

- [x] Open from a hamburger button at the top left of primary screens.
- [x] Provide functional All / Pending / Processed session filters.
- [x] Show persisted sessions in a newest-first list with local date/time, duration, and status.
- [x] Keep Preferences anchored at the bottom and clear of system navigation insets.
- [x] Use an honest empty state when no saved sessions match the active filter.

### Session History

Each successful Manual transcription creates an app-private persisted session with:

- [x] Stable UUID identifier
- [x] Automatically generated title, with repository support for future renaming
- [x] Created and last-updated timestamps stored as UTC epoch milliseconds and displayed
  in the device's local timezone
- [x] Audio-derived duration
- [x] Transcription model, cleaned transcript, and original transcript
- [x] Processing status: Transcribed, Queued, Processing, Processed, or Failed
- [x] Persisted processed text and local-LLM topic tags
- [x] Full-content detail view from the navigation drawer
- [x] Share through the Android Sharesheet using `ACTION_SEND` and `text/plain`
- [x] Confirmed local deletion
- [ ] Add UI for renaming and transcript editing; repository operations already invalidate
  stale processed output and return an edited transcript to Pending

Sessions are stored in an app-private SQLite database behind a `SessionRepository`
interface. This keeps structured history local, updateable, and independent from the
Compose UI while avoiding an additional annotation-processor toolchain for the POC.

### Processing Pipeline Boundary

- [x] Save the transcript before offering local LLM processing.
- [x] Allow a Pending or Failed saved transcript to start/retry processing from its
  session detail view; disable the action while Queued or Processing.
- [x] Keep transcription history when Gemma processing fails.
- [x] Persist explicit Queued, Processing, Processed, and Failed transitions.
- [x] Mark a session Processed only after its processed text and tags are committed.
- [ ] Move queued work to a durable background processor; the current Process action
  executes the queue immediately in the foreground.
- [ ] Add retry/cancel controls and a dedicated processing-queue view.

Session persistence, naming, filtering, and restoration belong to the conversation-history
milestone and are not part of the first UI-shell implementation.

### Preferences

- [x] Light and dark appearance selection (in-memory for the current app session)
- [x] Whisper transcription model selection (`tiny.en` or `base.en`)
- [x] Minimum silence duration UI for a future Live Mode transcript segment
- [x] Clearly label settings that are previews and are not yet applied
- [ ] Persist preferences locally in a later slice; initial UI state may be in-memory

### Android 17 System UI

- [x] Keep the top app toolbar below the status/notification bar.
- [x] Start the navigation drawer surface below the status bar so system icons do not
  appear over drawer content.
- [x] Keep drawer actions above the three-button navigation area.
- [x] Apply an adaptive navigation-bar contrast scrim.
- [x] Use dark system navigation icons in light mode and light icons in dark mode.
- [x] Let the hamburger icon inherit the toolbar content color so it remains visible in
  both themes.
- [x] Manually verified the light and dark system-bar behavior on the Pixel 8 Pro.

### UX Assumptions To Validate

- Pending means a captured transcript that has not completed Gemma processing; Processed
  means a structured Gemma result exists.
- New sessions receive an automatic date/time-based name until explicit renaming is added.
- Minimum silence duration defaults to 1.5 seconds and should later be tunable within a
  practical range after on-device testing.

## Original Idea

Source material was provided inline in chat as `spec.md`. The repository's current local `spec.md` contains a different spec, so this intake preserves the user-provided source below. A few arrows and checkmarks are normalized to ASCII so the page remains markdown-safe and stable in this repository.

~~~~text
# spec.md

# Project: Local Voice Interpreter (Android)

## Vision

Build an offline-first Android application that transforms spoken conversations into structured knowledge using entirely on-device AI.

The application should never require an internet connection for its core functionality. All transcription and language model inference will execute locally on the device.

The long-term vision is to evolve into a personal AI memory system, but this specification only covers Milestone 1.

---

# Milestone 1

Deliver the smallest possible application that proves the following pipeline:

Microphone
-> Local Speech Recognition
-> Local LLM
-> Structured Interpretation
-> Display Results

Nothing more.

---

# Goals

The application must:

- Record microphone input
- Display live or near-live transcription
- Run a lightweight local LLM
- Interpret the completed transcript
- Display structured results
- Operate entirely offline
- Never transmit audio or transcripts externally

---

# Non Goals

This milestone intentionally excludes:

- User accounts
- Cloud APIs
- Background recording
- Wake words
- Speaker identification
- Knowledge graph
- Conversation history
- Search
- Calendar integration
- Reminder system
- Home Assistant integration
- Continuous listening
- Audio summarization while recording

---

# Technology Stack

## Language

Kotlin

## UI

Jetpack Compose

## Architecture

MVVM

## Concurrency

Kotlin Coroutines

StateFlow

## Audio

Android AudioRecord

16kHz Mono PCM

---

# Speech Recognition

Engine:

whisper.cpp

Initial model:

tiny.en

Future upgrade:

base.en

Responsibilities:

- Capture audio
- Perform local transcription
- Provide partial transcript updates
- Produce final transcript

---

# Language Model

Engine:

LiteRT-LM

Initial Model:

Gemma 3 1B (instruction tuned)

Responsibilities:

- Read transcript
- Produce structured JSON
- Never hallucinate
- Never invent dates
- Never invent people

---

# User Interface

Single screen.

States:

## Idle

Large button:

Start Listening

Status:

Ready

---

## Recording

Button:

Stop Listening

Visible timer

Transcript updates

Status:

Listening...

---

## Processing

Status:

Interpreting locally...

Disable controls

---

## Complete

Sections:

Transcript

Summary

Intent

Key Points

Action Items

Buttons:

Record Again

Clear

---

# Interpretation Schema

The LLM returns only JSON.

```json
{
  "summary": "",
  "intent": "",
  "key_points": [],
  "action_items": [
    {
      "text": "",
      "due_date": null
    }
  ]
}
```

Allowed intents:

- note
- idea
- reminder
- task
- conversation
- question
- unknown

---

# Prompt

```
You are an offline AI assistant.

Interpret the transcript.

Return ONLY valid JSON.

Never fabricate information.

Schema:

{
  "summary": "",
  "intent": "",
  "key_points": [],
  "action_items": [
    {
      "text": "",
      "due_date": null
    }
  ]
}

Rules:

- Summary <= 3 sentences.
- Preserve names exactly.
- Preserve dates exactly.
- Empty arrays are acceptable.
- due_date must be null unless explicitly stated.

Transcript:

{{TRANSCRIPT}}
```

---

# Processing Pipeline

```
Start Recording

v

AudioRecord

v

PCM Buffer

v

Whisper

v

Partial Transcript

v

Stop Recording

v

Final Transcript

v

Gemma

v

JSON

v

UI
```

---

# Project Structure

```
app/

audio/
    AudioRecorder.kt
    AudioSession.kt

transcription/
    Transcriber.kt
    WhisperTranscriber.kt

llm/
    TranscriptInterpreter.kt
    GemmaInterpreter.kt

model/
    RecordingState.kt
    InterpretationResult.kt

ui/
    RecorderScreen.kt
    RecorderViewModel.kt
```

---

# Interfaces

```kotlin
interface Transcriber {

    suspend fun start(
        onPartialTranscript: (String) -> Unit
    )

    suspend fun stop(): String

}
```

```kotlin
interface TranscriptInterpreter {

    suspend fun interpret(
        transcript: String
    ): InterpretationResult

}
```

---

# Models

## RecordingState

```kotlin
sealed interface RecordingState {

    object Idle

    object Recording

    object Processing

    object Complete

}
```

---

## InterpretationResult

```kotlin
data class InterpretationResult(

    val summary: String,

    val intent: String,

    val keyPoints: List<String>,

    val actionItems: List<ActionItem>

)
```

---

# Model Management

For Milestone 1:

Whisper model is packaged with the application.

Gemma model is stored locally on the device and manually copied during development.

Future milestone:

Model download manager.

---

# Privacy

The application shall:

- Never upload audio.
- Never upload transcripts.
- Never require login.
- Function in Airplane Mode.
- Request only microphone permission.

---

# Error Handling

Display friendly errors for:

- Missing microphone permission
- Missing Whisper model
- Missing Gemma model
- Model load failure
- Transcription failure
- JSON parse failure

Errors should never crash the application.

---

# Success Criteria

Milestone 1 is complete when:

[x] App installs on a Pixel 8 Pro

[x] User can start recording

[x] User can stop recording

[x] Transcript is displayed

[x] Local LLM produces structured output

[ ] Works completely offline (offline architecture is complete; Airplane Mode validation remains)

[x] No cloud services required

[ ] No crashes during a 2-minute recording (stability run remains)

---

# Future Milestones (Out of Scope)

Milestone 2

- [x] Conversation/session history foundation
- [x] App-private SQLite persistence
- [x] UTC timestamps with local-time display
- [ ] Rename and transcript-edit UI
- [ ] Durable background processing queue

Milestone 3

- Semantic search
- Embeddings
- Vector storage

Milestone 4

- Automatic task detection
- Reminder engine

Milestone 5

- Knowledge graph

Milestone 6

- Background listening

Milestone 7

- Wear OS support

Milestone 8

- AR glasses integration

---

# Guiding Principles

1. Offline First

Everything should work without internet access.

2. Simplicity

Prefer the simplest implementation that proves the concept.

3. Modularity

Every AI component should be replaceable.

4. Privacy

User data belongs only to the user.

5. Performance

Optimize for responsiveness before adding features.

6. Foundation Before Features

Every milestone should leave the project in a stable, extensible state before expanding functionality.
~~~~

## Assumptions

- A Pixel 8 Pro can run microphone capture, `whisper.cpp` `tiny.en`, and a local Gemma 3 1B model on-device with acceptable responsiveness for Milestone 1.
- `whisper.cpp` can provide partial transcript updates reliably enough to satisfy the "live or near-live" transcript requirement.
- LiteRT-LM and the chosen Gemma model can be integrated into an Android Kotlin application without requiring network services for inference.
- The proposed MVVM, Compose, and interface boundaries are sufficient to keep the transcription and interpretation components replaceable.
- A single-screen UI is enough to validate the end-to-end value of the offline interpretation pipeline.
- Prompting plus JSON parsing and schema enforcement can constrain the local LLM to the required structured output format for this milestone.
- Packaging Whisper with the app and manually copying Gemma during development is an acceptable model-management approach for an initial proof of concept.
- Requesting only microphone permission is enough for the intended recording and on-device processing flow.

## Open Questions

- What Android integration approach will be used for `whisper.cpp` and LiteRT-LM: JNI bindings, packaged native libraries, or another bridge?
- What transcript latency is acceptable for "near-live" partial updates and for final interpretation on the target device?
- Where exactly will the Gemma model live on-device during development, and how will the app detect and validate its presence?
- What transcript length and prompt size can the local Gemma model handle reliably before latency or memory becomes problematic?
- How should the app recover if the LLM returns invalid JSON or structurally incomplete JSON?
- How should partial transcript updates be reconciled with the final transcript if the text changes after recording stops?
- What APK size, storage, memory, thermal, and battery constraints will result from bundling Whisper and running both models locally?
- What instrumentation or manual test procedure will prove the "no crashes during a 2-minute recording" success criterion?

## Reviews

No reviews yet.

## Iterations

### v0 - Original Capture

Initial version of the idea, structured through the Idea Intake workflow from a user-provided Milestone 1 spec.

### v1 - Light MVP Implementation

Implemented and device-tested the record -> local Whisper -> local Gemma -> structured
result -> Compose UI pipeline. Added dual Whisper models, deterministic filler cleanup,
resilient Gemma JSON parsing, and local regression/device coverage. Remaining
Milestone 1 validation and near-live transcription are tracked above.

### v2 - Mode-Based UI Foundation

Added the diagonal Manual/Live mode chooser, shared app toolbar, navigation drawer,
chat-filter and empty-history shells, Live Mode placeholder, and in-memory Preferences.
Corrected Android 17 edge-to-edge handling for the status bar, drawer surface, bottom
navigation area, adaptive system icon contrast, and light/dark hamburger visibility.
Built, deployed, and manually verified the resulting UI on the Pixel 8 Pro.

### v3 - Capture Screen Refinements

Reweighted Home around the Manual / Live selection surface, simplified its heading, and
made Android Back return there from either mode. Replaced Manual Mode's idle action with
a large circular play control and simplified model selection to Fast / Accurate. Built,
deployed, and verified both navigation paths and the updated layouts on the Pixel 8 Pro.

### v4 - Persistent Session History

Added app-private SQLite session storage, UTC metadata, audio-derived duration, generated
titles, explicit LLM processing states, persisted processed text and tags, and a repository
boundary for future background processing. Connected newest-first history and functional
status filters to the drawer, added a full session detail view, Android Sharesheet export,
confirmed deletion, and direct Process/retry actions for saved transcripts. Unit, lint,
build, and on-device SQLite/UI tests pass; a real recording, history/detail navigation,
saved-session Gemma processing, sharing, and deletion were verified on the Pixel 8 Pro.

## Decisions

- Bundle both Whisper models and Gemma directly in the APK for the personal-use MVP.
- Target `arm64-v8a` and the Pixel 8 Pro for Milestone 1.
- Keep inference on-device and omit the Android `INTERNET` permission.
- Treat omitted `action_items` as an empty list while continuing to require summary,
  intent, and key points.
- Defer model downloads, search, reminders, rename/edit UI, and durable background queue
  execution to later iterations.
- Store session history in app-private SQLite behind a repository interface; keep UTC as
  the storage representation and convert to the device timezone only for display.
- Use explicit Android edge-to-edge handling and theme-aware system icon appearance rather
  than relying on platform defaults.
- Keep unfinished Live Mode and chat-history functionality visible only as clearly labeled
  placeholders; do not imply capture or persistence is active.

## Next Actions

- Validate the complete flow in Airplane Mode.
- Run a two-minute recording stability test on the Pixel 8 Pro.
- Decide whether near-live partial transcription remains in Milestone 1 or moves to the first post-MVP iteration.
- Tighten the Gemma prompt and complete error-path testing.
- Add local chat-session persistence, naming, timestamps, and real drawer filtering.
- Persist appearance, transcription-model, and silence-duration preferences locally.
