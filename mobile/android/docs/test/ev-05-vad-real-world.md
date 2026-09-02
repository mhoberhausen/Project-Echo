# EV-05 — Real-world VAD quality

**Status:** Not run

## Goal

Measure false captures and missed speech in representative everyday sound conditions before
changing VAD thresholds or considering Silero.

## Method

Run separate, labelled trials with normal speech, television, music, HVAC, traffic, keyboard noise,
and a mixed conversation. Use the same timing settings for the baseline; log whether a session was
started, finalized, discarded, and transcribed.

## Success

Normal speech reliably captures; non-speech conditions do not create meaningful false sessions at a
rate that makes Active Listening impractical. Results establish a baseline for tuning.

## Failure

Repeated false captures, missed ordinary speech, or unstable behavior that cannot be explained by a
single condition.

## Iteration record

Record condition, duration, settings, expected/actual capture count, and one proposed threshold or
algorithm change. Rerun the baseline after every change; do not stack tweaks.
