# EV-06 — Long-run resource behavior

**Status:** Not run

## Goal

Establish evidence for battery, thermals, memory/storage pressure, and Whisper backlog behavior
during extended Active Listening.

## Method

1. Start with recorded battery percentage, free storage, and normal thermal state.
2. Run Active Listening for a planned interval with several short conversations.
3. Note battery, thermal warnings, free storage, queued transcription count, and final session
   outcomes at regular intervals and at the end.

## Success

The app remains responsive, captures recoverably, drains storage only as expected, and eventually
clears its local transcription backlog without an unexplained thermal or memory failure.

## Failure

Runaway backlog, sustained overheating, app/process termination, excessive unexpected storage use,
or lost sessions.

## Iteration record

Record interval, environmental conditions, battery/storage measurements, queue observations, and
terminal outcomes. Use measurements rather than guessing at an optimization.
