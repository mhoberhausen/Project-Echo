# EV-04 — Notification and permission lifecycle

**Status:** Partially observed

## Goal

Prove foreground controls and runtime-permission failures are visible, safe, and recoverable.

## Method

1. With permissions granted, test notification Pause, Resume, and Turn Off one at a time.
2. Deny microphone permission when asked; retry and grant it.
3. Deny notification permission; verify Active Listening explains why it cannot start visibly.
4. Revoke microphone permission in Android Settings during or between captures, then reopen Huh?.

## Success

Every control produces the named state, no microphone capture continues invisibly, failure copy is
understandable, and the user can recover after granting permission again.

## Failure

Invisible capture, a stuck foreground notification, crash, ambiguous error, or an unrecoverable
state after permission is restored.

## Iteration record

Record Android version, permission state, action, observed notification/state, and result.
