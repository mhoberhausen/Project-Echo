# UI Finish Gate — Huh? Core Journeys (Final)

## Decision: PASS

PASS applies to the normalization scope defined by the baseline gate. It is not a claim that
every app state or device integration is release-ready.

## Scope and evidence reviewed

- Baseline Pixel 8 Pro storybook for the complete current journey model.
- API 34 Pixel 3a emulator renders after implementation:
  - Listen Now idle in light and dark themes
  - Unconfigured audio-device picker
  - AI Selection with bundled Gemma
- Compose semantics for Start listening and provider reorder.
- Focused instrumentation coverage for idle capture and transcript-ready session language.
- Local unit tests, lint, debug assembly, and instrumentation APK assembly.

## Baseline resolution

1. **Visible capture action — resolved.** “Start listening” now appears beneath the circular
   control while the control retains its accessibility description. Light and dark renders
   preserve the spacious capture hierarchy.
2. **Transcript status — resolved.** A successfully transcribed session is now “Transcript
   ready,” and history groups these sessions under “Unprocessed.” The implementation test
   verifies the language in both drawer and detail contexts.
3. **Unavailable device emphasis — resolved.** Bluetooth and an unconfigured Puck retain
   honest roadmap/setup copy but use disabled emphasis as a complete row.
4. **Provider reorder affordance — resolved.** The unsupported glyph is replaced with a
   Material menu/drag affordance and “Drag to reorder [provider]” semantics.
5. **Session-title hierarchy — resolved in implementation.** The title receives full width;
   Rename moves to a separate trailing row. Long-title and status presence are covered by the
   focused session test.

## Required before PASS

No blocker remains within this normalization scope.

## Keep

- Product-specific diagonal Home mode chooser
- Distinct recording and local-transcription states
- Separate expandable transcript and inferred-summary content
- Explicit selective sharing without session metadata
- Honest Bluetooth, Puck, LAN, and third-party limitations

## Optional refinements

- Evaluate whether the reorder affordance should become a dedicated drag-handle icon if the
  project later adds Material Icons Extended. The current supported icon and semantics are
  clear enough to pass.
- Add a formal large-font screenshot fixture for long session titles rather than relying on
  semantic UI coverage.
- Consider “Needs processing” versus “Unprocessed” in real-user terminology testing; the
  current term accurately describes the filter and avoids implying transcription is pending.

## PASS criteria

- The first-read capture state and primary action are visible and semantically named.
- Transcript readiness is distinct from optional inference progress.
- Unsupported choices no longer look enabled.
- No fallback glyph remains in provider priority cards.
- Conversation identity no longer shares its line with Rename.
- Affected light/dark renders, tests, lint, and build checks pass.

## Unreviewed

- Pixel 8 Pro rendering after these changes because the physical phone was disconnected
- Large font-scale and TalkBack traversal on the session detail screen
- Permission denial and recovery, transcription failure, and inference-provider failure
- Configured physical Puck behavior

## Test note

A broader pre-existing `HuhAppSessionTest` run also exercised an external-device settings
test whose “Trusted-LAN POC” node was not displayed at the smaller emulator viewport. The
focused tests for this change pass. The external-device failure was not caused by this diff
and should be handled separately by making that test scroll to the target before asserting.

