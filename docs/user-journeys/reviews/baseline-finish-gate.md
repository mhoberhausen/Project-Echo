# UI Finish Gate — Huh? Core Journeys (Baseline)

## Decision: HOLD

## Product lens

Huh? serves people who need to capture speech quickly, trust that it remains private, and
later distinguish raw transcript from optional interpretation. The first-read object is the
current capture or saved conversation; the primary action is contextual: begin listening,
stop listening, review a transcript, or process it. The frequent path must be immediate and
calm. Destructive actions, unsupported inputs, provider routing, and privacy boundaries are
less frequent but high-risk and must remain explicit.

## Design contract

**User + job:** Capture or revisit speech without losing track of what the app is doing.

**First-read object:** Capture state on Listen Now; conversation identity and readiness on
What I Heard.

**Primary action:** Start/stop capture, then review or make sense of the saved transcript.

**Density decision:** Spacious during capture; balanced and scan-oriented for history and
configuration.

**Hierarchy:** State → primary action → model/privacy context → secondary management.

**Interaction model:** Mode choice, stateful capture control, list/detail session history,
and explicit configuration forms.

**Target adaptation:** Preserve the primary action and unbroken title hierarchy on the
Pixel-width target, with accessible labels and at least 48dp controls.

**Forbidden defaults:** Icon-only primary actions without visible meaning; workflow jargon;
enabled-looking placeholders; fallback glyphs; equal-weight management actions competing
with the conversation title.

**Finish evidence:** Storybook screenshots captured from the Pixel 8 Pro, Compose semantics,
focused instrumentation tests, lint, build, and affected-state emulator screenshots.

## Scope and evidence reviewed

- Current light-theme Pixel storybook covering Home, Listen Now states, active listening,
  session history/detail, device selection, AI selection, settings, and Puck setup.
- Current Compose implementation for the affected screens and their existing tests.
- Dark theme, large font scale, permission denial, and full error recovery were not present
  in the storybook and remain unverified at this baseline stage.

## Blocking evidence

1. **P1 — Listen Now idle: primary action relies on the logo alone.**
   - Observed: “Ready when you are” appears above a large circular Huh? mark, but no visible
     “Start listening” label accompanies it.
   - Product impact: a new or low-confidence user must infer that the brand mark is a button.
   - Required: add a visible action label without weakening the calm capture hierarchy.
   - Verify: idle screenshot and accessible Start listening semantics.

2. **P1 — Saved transcript: “Pending” describes a completed transcript.**
   - Observed: the drawer and session detail show Pending while transcript content is ready
     and the next action is optional inference.
   - Product impact: users can reasonably interpret this as transcription still running.
   - Required: name the state “Transcript ready” and make the history filter describe
     unprocessed sessions rather than an unspecified pending operation.
   - Verify: drawer and detail states for transcribed, processing, processed, and failed.

3. **P2 — Audio picker: unavailable inputs retain active-looking labels.**
   - Observed: disabled radio controls are paired with full-emphasis Bluetooth and Puck text.
   - Product impact: availability is communicated mainly by small supporting copy.
   - Required: apply disabled emphasis to unavailable choices while preserving the honest
     setup explanation.
   - Verify: unconfigured and configured Puck states.

4. **P1 — AI Selection: reorder handle renders as “?”.**
   - Observed: both provider cards show a question-mark glyph where drag guidance says a
     reorder affordance should appear.
   - Product impact: the control contradicts its instruction and looks broken.
   - Required: use a supported Material icon with a reorder content description.
   - Verify: provider list screenshot and semantics.

5. **P2 — Session detail: default title competes with Rename.**
   - Observed: a long generated title wraps into the narrow space left beside Rename.
   - Product impact: the first-read conversation identity is visually compressed by a
     secondary management action.
   - Required: give the title full width and place Rename on its own aligned action row.
   - Verify: default and 120-character titles at target width and increased font scale.

## Keep

- The diagonal Home choice makes the Manual / Live split product-specific.
- Capture, recording, and processing states have distinct marks and restrained information.
- Transcript and inferred summary are separate, collapsed sections.
- Share explicitly excludes session metadata.
- Unsupported Bluetooth and trusted-LAN limitations are described honestly.

## Required before PASS

1. Resolve the five findings above in Compose and affected tests.
2. Verify the changed states through build, lint, focused tests, and rendered screenshots.
3. Run the persona walkthrough before the final gate to test whether the normalized language
   and hierarchy address both reassurance-seeking and facts-first users.

