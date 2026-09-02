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

Release baseline: 0.1.1 is awaiting Google Play review. It was built and launched on a Pixel 8
Pro running Android 17 on 2026-09-01.

### Completed

- [x] Kotlin, Jetpack Compose, MVVM, Coroutines, and StateFlow application foundation
- [x] Microphone permission and 16 kHz mono PCM capture through `AudioRecord`
- [x] Start/stop recording flow with a visible timer
- [x] Fully local `whisper.cpp` transcription with the bundled Accurate (`base.en`) model
- [x] Preserve Whisper segment start/end timestamps with each saved transcript
- [x] Run optional sherpa-onnx speaker diarization after Whisper; a model-agnostic acoustic
  engine boundary and pass-through fallback keep the stage replaceable
- [x] Deterministic filler-word cleanup with access to the original transcript
- [x] Fully local LiteRT-LM inference with bundled Gemma 3 1B int4
- [x] Structured response parsing for summary, intent, key points, and action items
- [x] Resilient parsing when Gemma adds metadata or omits an empty `action_items` array
- [x] Processed-result UI, including transcript disclosure and empty action-item handling
- [x] No account, cloud transcription, or cloud LLM path; Firebase crash diagnostics and
  anonymous usage insights are independent, explicit opt-ins with a content-free event boundary.
  Optional developer support is a configured external-browser link only: it has no subscription,
  donation processing, feature gate, quota, premium state, or support tracking.
  `INTERNET` and Android
  17 local-network access are limited to user-configured private-LAN device/AI endpoints
- [x] Persist an audio-input picker for the phone, with an honest Bluetooth setup placeholder.
  Huh? Puck live capture is not included in the 0.1.1 Play release.
- [x] Provide a nested AI Selection settings page with persistent enablement and drag ordering;
  bundled Gemma and unauthenticated OpenAI-compatible private-LAN endpoints execute in priority
  order with fallback, while third-party/cloud entries remain configuration-only
- [x] Debug APK builds, installs, and launches on the target Pixel 8 Pro
- [x] JVM parser/router/LAN fixture regression tests plus Pixel tests for bundled Gemma, real
  private-LAN inference, fallback behavior, and result rendering
- [x] Run instrumentation against an isolated `.testbed` application ID so test install,
  cleanup, databases, and preferences cannot alter normal app sessions or AI endpoints

### Remaining

- [ ] Add live partial transcript display. Listen Now and Keep an Ear Out already transcribe
  quiet-delimited chunks serially while recording continues, using the persisted 1,000 ms
  default quiet boundary. They fall back to a full post-capture pass if VAD finds no chunks,
  Whisper disagrees with VAD, a chunk fails, or the bounded backlog fills. Active captures
  are persisted before incremental work completes and recover through the durable local queue.
  Full-audio diarization remains post-capture.
- [ ] Exercise all specified model, transcription, and malformed-JSON error paths
- [ ] Tighten the prompt to require summaries of at most three sentences and exact name preservation

### Full test evaluation

- [ ] Complete the repeatable [full test evaluation](docs/test/test-validation.md) and
  record evidence or an explicit release exception for every case.

### Future scope, deliberately excluded from 0.1.1

- [ ] Reintroduce Huh? Puck live capture only after its release-ready foreground-service and
  device-association design is complete.
- [ ] Add authenticated Puck association and discovery after the firmware control/identity
  contract is frozen.
- [ ] Evaluate bundling Silero VAD behind the existing abstraction if real-world tuning shows the
  heuristic detector is insufficient.

## Huh? Rebrand and Experience

The user-facing product identity is now **Huh?**, guided by the promise **Never miss what
was said.** The Android application ID, Kotlin package names, database, model pipeline,
and existing installs intentionally remain unchanged. The supplied ear/question-mark logo
geometry is implemented as reusable, tintable vectors rather than as a fixed-background
bitmap.

### Identity and language

- [x] Rename the launcher label and visible app identity from Echo Keep to **Huh?**
- [x] Add adaptive, round, and monochrome/themed launcher icon resources
- [x] Add an Android 12+ launch splash using the Huh? mark
- [x] Establish neutral light/dark surfaces, an accessible teal interactive primary, a
  brighter logo accent, and independent semantic recording/error colors
- [x] Rename **Manual Mode** to **Listen Now** and **Live Mode** to **Keep an Ear Out**
- [x] Rename history to **What I Heard**, transcript surfaces to **What Was Said**, and
  Gemma results to **What I Got From It**
- [x] Rename the local Gemma action to **Make sense of this**
- [x] Add concise **Processed on this device** privacy messaging
- [x] Update Android Sharesheet text to use the new transcript and interpretation headings

### Experience

- [x] Integrate the Huh? mark into Home, the navigation drawer, Listen Now, and the
  unavailable Keep an Ear Out screen
- [x] Replace the idle play glyph with the branded listening control while retaining an
  explicit accessible **Start listening** label
- [x] Add a subtle recording-only ripple and keep idle rendering static
- [x] Use distinct, human-readable states for recording, Whisper transcription, and Gemma
  interpretation
- [x] Use Accurate local transcription consistently across recording, persistence, processing,
  sharing, and deletion behavior
- [x] Replace the Keep an Ear Out placeholder with explicit Off, Starting, Waiting,
  Listening, Paused, and Error states
- [x] Implement the foreground-service Active Listening foundation, local VAD,
  conversation segmentation, durable audio handoff, and serial Whisper queue
- [ ] Add a longer in-app About/privacy explanation. The published policy is
  [mobileobie.com/apps/huh/privacy](https://mobileobie.com/apps/huh/privacy/).
- [x] Use the monochrome Huh? notification icon for Active Listening and local transcription.

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
- [x] Keep an Ear Out opens the Active Listening control surface and reflects the
  foreground service state.
- [x] Android Back returns from either mode to Home instead of exiting the app.

### Manual Mode

- [x] Replace the idle **Start listening** button with a large circular play control.
- [x] Use Accurate (`base.en`) local transcription without a model-selection control in the
  user interface.

### Navigation Drawer

- [x] Open from a hamburger button on Home only.
- [x] Use a standard back arrow on Listen Now, Keep an Ear Out, Settings, saved-session
  detail, and future child screens; toolbar Back and Android Back both return Home.
- [x] Keep capture selection on Home instead of duplicating a **Listen** action in the drawer.
- [x] Provide functional All / Unprocessed / Processed session filters.
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
- [x] Timestamped Whisper segments, including nullable speaker IDs reserved for the local
  diarization pipeline
- [x] Processing status: Transcribed, Queued, Processing, Processed, or Failed
- [x] Persisted processed text and local-LLM topic tags
- [x] Session detail view with independently expandable, collapsed-by-default Transcript and
  Inferred summary sections
- [x] Share either transcript-only or inferred-summary-only through the Android Sharesheet;
  session title, date, duration, and status are excluded
- [x] Confirmed local deletion
- [x] Edit a transcript before Gemma processing or from a processed saved session; saving
  clears timestamp segments and stale inferred output, then returns the session to Transcript ready
- [x] Rename saved sessions in-place without invalidating transcript or inferred content

Sessions are stored in an app-private SQLite database behind a `SessionRepository`
interface. This keeps structured history local, updateable, and independent from the
Compose UI while avoiding an additional annotation-processor toolchain for the POC.

### Processing Pipeline Boundary

- [x] Run diarization after Whisper transcription and before transcript persistence and
  cleanup; align sherpa-onnx speaker turns to Whisper segments by greatest timestamp overlap
- [x] Persist speaker IDs and include human-readable speaker labels in the transcript passed
  to the user-triggered Gemma interpretation step
- [x] Render each diarized speaker turn on its own line and allow generated speaker labels
  to be replaced with distinct user-provided names; renaming invalidates stale inference
- [x] Save the transcript before offering local LLM processing.
- [x] Allow a Transcript ready or Failed saved transcript to start/retry processing from its
  session detail view; disable the action while Queued or Processing.
- [x] Keep transcription history when Gemma processing fails.
- [x] Persist explicit Queued, Processing, Processed, and Failed transitions.
- [x] Mark a session Processed only after its processed text and tags are committed.
- [ ] Add retry/cancel controls and a dedicated processing-queue view.

Session persistence, naming, filtering, and restoration belong to the conversation-history
milestone and are not part of the first UI-shell implementation.

### Preferences

- [x] Light and dark appearance selection (in-memory for the current app session)
- [x] Accurate (`base.en`) Whisper transcription
- [x] Advanced Settings child page for Active Listening timing and eligibility, with a
  one-tap **Reset to defaults** action
- [x] Explain Active Listening terminology such as VAD (Voice Activity Detection) before
  using its acronym in the Advanced Settings controls
- [x] Configurable 5–60 second continuous no-speech ending threshold, defaulting to 15
  seconds and persisted locally for the next Active Listening activation
- [x] Default legacy Active Listening model preferences to Accurate (`base.en`)
- [x] Persist advanced defaults of 400 ms sustained speech to start, two seconds of pre-roll,
  and three seconds of cumulative VAD-positive speech before transcription
- [x] Persist optional transcript cleanup, defaulting to discarding results shorter than
  three cleaned words; this remains separate from user-controlled Gemma interpretation
- [ ] Persist light/dark appearance locally; it remains in-memory for the current app session

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

- Transcript ready means a captured transcript that has not completed optional inference; Processed
  means a structured Gemma result exists.
- New sessions receive an automatic date/time-based name until explicit renaming is added.
- Conversation-ending silence defaults to 15 seconds and is tunable from 5–60 seconds;
  Pixel testing should determine whether that range and default feel natural.

## Keep an Ear Out — Active Listening

Keep an Ear Out is Huh?'s explicitly enabled, continuous local conversation-capture mode.
Its responsibility ends when a normal saved session reaches a ready transcript. Gemma is
not referenced by the Active Listening service or transcription queue and is only invoked
after the user selects **Make sense of this** from a ready session.

### Implemented foundation

- [x] Android microphone foreground service with `START_NOT_STICKY`; no boot receiver or
  automatic restart after reboot
- [x] Required microphone foreground-service and notification permissions, requested from
  the visible UI before activation
- [x] Persistent Huh?-branded Waiting, Listening, Paused, and Error notifications
- [x] Notification and in-app Pause, Resume/Try Again, and Turn Off controls
- [x] Service-owned 16 kHz mono PCM microphone stream that continues when the activity is
  backgrounded
- [x] Replaceable `VoiceActivityDetector` abstraction with a provisional fully local,
  multi-feature adaptive heuristic detector
- [x] Explicit `WAITING -> LISTENING -> POSSIBLE_END -> FINALIZING -> WAITING` state
  machine with detailed transition logging
- [x] Central defaults of 400 ms sustained speech to start, 15 seconds of continuous
  VAD-observed silence to end, two seconds of pre-roll, and three seconds of cumulative
  detected speech to qualify for transcription
- [x] Two-second in-memory rolling buffer that is only written after a conversation is
  detected
- [x] Minimum-speech discard behavior and meaningful-capture finalization on Pause or Turn Off
- [x] App-private PCM persistence before queue submission, so capture returns to Waiting
  without waiting for Whisper
- [x] Durable serial WorkManager chain for Whisper transcription, with a visible local-
  transcription notification, automatic continuation after backgrounding/process death,
  persisted audio-path recovery, and retry from failed-session detail
- [x] Delete app-private conversation audio only after cleaned and original transcripts are
  committed successfully; failed/interrupted work retains audio for retry
- [x] Determine transcription eligibility from cumulative VAD-positive speech rather than
  total audio-file duration; silence never makes a capture eligible for Whisper
- [x] Apply the configurable minimum-word threshold after local Whisper cleanup but before
  committing a ready session; undersized sessions and their temporary audio are removed
- [x] Persist local timing instrumentation for total audio, cumulative speech, distinct
  speech segments, longest internal silence followed by resumed speech, and the configured
  conversation-ending threshold
> **Historical Puck prototype:** the completed trusted-LAN Puck prototype is retained in this
> history only. Its TCP streaming/reconnect and connected-device foreground-service path are not
> shipped in 0.1.1; Puck work is tracked under Future scope above.
- [x] Distinct Transcribing, Transcription failed, transcript-ready, and Gemma processing
  states in history; Gemma is never invoked automatically
- [x] Active Listening screen, Home status copy, queued-transcription indicator, privacy
  explanation, and session source label
- [x] Temporary **Conversation in progress** row in pending session history while Active
  Listening is writing audio; discarded captures disappear without creating history
- [x] Apply changed timing preferences to an active waiting service after a short debounce,
  or after the current conversation finishes, and reload all timing/VAD state on Resume
- [x] VAD speech hangover, guarded adaptive-noise updates, and periodic local diagnostic
  logging to make real-device misses observable without retaining additional audio
- [x] Unit coverage for detector timing and transitions, pause/off behavior, rolling-buffer
  ordering, heuristic VAD features, and serial queue concurrency
- [x] No cloud dependency or remote endpoint; Android's `INTERNET` permission is used only
  for a direct socket to the user-configured device on the local network

### Device validation and tuning

The current post-release validation checklist above is the source of truth for outstanding
device validation and VAD tuning work.

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
- Request microphone and notification permission only when the user enables visible phone Active
  Listening. Android 17 local-network permission is requested only for a user-enabled private-LAN
  AI provider.
- Keep optional crash diagnostics and anonymous usage insights off until separately enabled; they
  never include conversation content or identifying local settings.
- Publish and maintain the user-facing policy at
  [mobileobie.com/apps/huh/privacy](https://mobileobie.com/apps/huh/privacy/).

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
- [x] Rename and transcript-edit UI
- [x] Durable WorkManager background queue for local transcription

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

### v5 - Huh? Rebrand

Rebranded all user-facing Android identity to Huh? without changing the application ID or
local data model. Added the ear/question-mark mark as reusable Compose and Android vector
art, adaptive/round/themed launcher icons, an Android 12+ splash, a teal-and-neutral light/
dark token system, human-friendly capture/history/result language, local-processing privacy
copy, and a subtle recording-only ripple. Kept Keep an Ear Out clearly marked as coming
later so the presentation does not overstate the app's current capabilities. Built and
installed the rebrand on the Pixel 8 Pro, passed the focused Compose UI suite, and visually
verified the launcher identity, light Home and Listen Now screens, drawer/system insets,
dark Settings, and dark Keep an Ear Out placeholder on 2026-08-22.

## Decisions

- Bundle both Whisper models and Gemma directly in the APK for the personal-use MVP.
- Target `arm64-v8a` and the Pixel 8 Pro for Milestone 1.
- Keep inference on-device and omit the Android `INTERNET` permission.
- Treat omitted `action_items` as an empty list while continuing to require summary,
  intent, and key points.
- Defer model downloads, search, and reminders to later iterations.
- Store session history in app-private SQLite behind a repository interface; keep UTC as
  the storage representation and convert to the device timezone only for display.
- Use explicit Android edge-to-edge handling and theme-aware system icon appearance rather
  than relying on platform defaults.
- Keep unfinished Live Mode and chat-history functionality visible only as clearly labeled
  placeholders; do not imply capture or persistence is active.
- Use **Huh?** for user-facing identity and **Project Echo** as the repository codename.
  Kotlin code and the Android application use the `com.mobileobie.echo` package, with
  `Huh*` class/composable names. Retain the `echo_keep.db` filename for the existing local
  database schema; changing the application ID creates a separate Android installation.
- Use the supplied logo geometry as tintable vector art; reserve bright cyan for branding
  and use a darker teal for accessible light-theme controls.

## Next Actions

- Validate the complete flow in Airplane Mode.
- Run a two-minute recording stability test on the Pixel 8 Pro.
- Decide whether near-live partial transcription remains in Milestone 1 or moves to the first post-MVP iteration.
- Tighten the Gemma prompt and complete error-path testing.
- Persist appearance, transcription-model, and silence-duration preferences locally.
