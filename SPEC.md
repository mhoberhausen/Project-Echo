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

[x] Works completely offline

[x] No cloud services required

[x] No crashes during a 2-minute recording

---

# Future Milestones (Out of Scope)

Milestone 2

- Conversation history
- SQLite
- Timestamped recordings

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

## Decisions

No decisions yet.

## Next Actions

- Run the Idea Review workflow if you want evaluation of feasibility, technical risk, or MVP sharpness.
- Clarify the Android integration path for `whisper.cpp` and LiteRT-LM in a future iteration.
- Promote this into a Project artifact once implementation is an explicit commitment rather than an idea capture.