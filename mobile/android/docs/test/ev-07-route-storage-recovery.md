# EV-07 — Route, storage, contention, and recovery

**Status:** Not run

## Goal

Verify phone capture fails safely and recovers across audio-route changes, competing microphone
users, low storage, process interruption, and reboot behavior.

## Method

Test one condition per run: connect/disconnect a Bluetooth headset, start another microphone app,
approach low-storage conditions safely, interrupt/reopen Huh?, and reboot after stopping capture.
For each, attempt a short Listen Now and Keep an Ear Out capture where appropriate.

## Success

Huh? gives a clear state or error, does not retain audio invisibly, preserves recoverable sessions,
and never auto-starts microphone capture after reboot.

## Failure

Crash, corrupt session, invisible capture, unrecoverable app state, or microphone capture beginning
without a fresh user action.

## Iteration record

Record the exact condition, prior capture state, expected result, observed result, and a local log
excerpt if behavior differs.
