# Huh? full test evaluation

This is the repeatable, evidence-led evaluation suite for a release candidate. It follows an
iterative-improvement loop: run one bounded experiment, record the evidence, make one scoped
change only when the result identifies a concrete issue, then rerun the same experiment. It is
not a pass/fail substitute for user research.

## Rules

- Test on a Pixel 8 Pro running the release candidate unless a case says otherwise.
- Keep audio, transcripts, screenshots, logs, and recordings local. Redact any test speech before
  attaching evidence to a public issue.
- Do not change more than one relevant variable between iterations.
- A **pass** requires recorded evidence; an unrun case is **Not run**, not a pass.

## Status

| ID | Evaluation | Status | Evidence |
|---|---|---|---|
| EV-01 | Offline and Airplane Mode | Not run | — |
| EV-02 | Two-minute recording stability | Not run | — |
| EV-03 | Background and screen-off Active Listening | Partially observed | Review-video capture only; complete formal run remains |
| EV-04 | Notification and permission lifecycle | Partially observed | Foreground notification observed; full matrix remains |
| EV-05 | Real-world VAD quality | Not run | — |
| EV-06 | Long-run resource behavior | Not run | — |
| EV-07 | Route, storage, contention, and recovery | Not run | — |

## Completion rule

The full evaluation passes only when EV-01 through EV-07 are marked **Pass** or a deliberate
release exception is documented with owner, reason, and follow-up version.

## Test cases

- [EV-01 Offline and Airplane Mode](ev-01-offline-airplane-mode.md)
- [EV-02 Two-minute recording stability](ev-02-two-minute-recording-stability.md)
- [EV-03 Background and screen-off Active Listening](ev-03-background-screen-off.md)
- [EV-04 Notification and permission lifecycle](ev-04-notification-permission-lifecycle.md)
- [EV-05 Real-world VAD quality](ev-05-vad-real-world.md)
- [EV-06 Long-run resource behavior](ev-06-resource-behavior.md)
- [EV-07 Route, storage, contention, and recovery](ev-07-route-storage-recovery.md)
