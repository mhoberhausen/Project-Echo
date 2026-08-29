# Persona Walkthrough — Huh? Core Journeys

This is a qualitative simulation that generates hypotheses. It is not user research,
statistical evidence, or proof of conversion or comprehension. Observations come from the
Pixel storybook and current implementation; reactions are inferred from the profiles below.

## Personas and assumptions

### Maya — reassurance-seeking capture user

- 39, caregiver, frequently reconstructing conversations in noisy environments
- Android phone, moderate domain familiarity, needs to act immediately
- Primary fears: missing a key detail, recording the wrong source, losing a capture, or
  accidentally sharing private context
- Trust triggers: plain state language, visible confirmation, local-processing explanation
- Decision style: quick when reassured; action threshold is knowing exactly what tapping
  will do and where the result will appear

### Theo — privacy-conscious facts-first user

- 31, technical professional, uses local AI tools and compares provider behavior
- Android phone, high domain familiarity, low patience for decorative or ambiguous controls
- Primary fears: hidden cloud transfer, silent fallback, unsupported device paths, stale AI
  output after editing
- Trust triggers: explicit provider order, clear local/network boundaries, reversible actions
- Decision style: fast once configuration and status are legible

## Pre-arrival relevance contracts

- **Maya:** Within five seconds, show that this app captures what was said, which mode starts
  a one-time recording, and that processing stays private.
- **Theo:** Within five seconds, distinguish intentional capture from persistent listening
  and expose device/provider control without implying cloud dependence.

## Five-second test — Home

### Maya

> “Listen Now sounds like what I need. The other half sounds like it keeps running, so I’m
> not touching that today. Good—it says this stays on the device.”

**Analyst:** What is this? Clear. Is it for me? Clear for missed-conversation capture. What
should I do? Clear at the mode level. Trust rises from the local-processing line. LIFT:
Relevance and Clarity increase. Fogg: motivation high, ability high, prompt visible.

### Theo

> “The two operating modes are obvious and the device icon tells me input is configurable.
> I’d still inspect AI settings before trusting the interpretation step.”

**Analyst:** All three questions are answered. Product-specific mode naming and the device
control prevent the Home screen from feeling generic. Authority is not claimed; privacy
language is appropriately factual.

## Journey 1 — Manual capture

### State: Listen Now idle

**Maya:**
> “I’m ready, but is the big logo the button? I think so. I wish it actually said Start.”

**Analyst:** Observed icon-only visible action; Start listening exists only in semantics.
Trust ↓ slightly. LIFT: Clarity. Fogg: motivation High, ability Medium, prompt ambiguous.

**Theo:**
> “Accurate is selected and local processing is explicit. The giant brand button is more
> decorative than informative, but I can infer it.”

**Analyst:** Same friction with lower abandonment risk. The visible action label is a shared
need rather than persona-specific reassurance.

### State: Recording and processing

**Maya:**
> “Red means it’s recording, the timer is moving, and Stop listening is obvious. Now it says
> it’s turning speech into words, so I know not to leave yet.”

**Analyst:** Trust ↑. State, elapsed time, and stop action are explicit. LIFT: Clarity strong;
Fogg ability and prompt High. The processing state correctly omits an unnecessary CTA.

**Theo:**
> “The state changes are direct and there’s no fake progress percentage. Good.”

**Analyst:** The restrained capture hierarchy should be preserved.

### State: Transcript ready

**Maya:**
> “It says Pending. Pending what? I can see there’s a transcript, so maybe it still isn’t
> safe to use.”

**Analyst:** Observed status conflicts with completed transcript. Trust ↓. LIFT: Clarity and
Anxiety. Fogg: ability Medium because the next optional step is mislabeled as unfinished work.

**Theo:**
> “This is really ‘not interpreted yet,’ not pending. The button tells me more than the
> status does.”

**Analyst:** Shared critical language issue. “Transcript ready” accurately names the object;
an “Unprocessed” history filter accurately groups optional inference states.

## Journey 2 — Review, rename, and share

### State: What I Heard drawer

**Maya:**
> “I found the recording, but Pending makes me wonder if I need to wait before opening it.”

**Analyst:** The history is otherwise scan-friendly, with title, date, duration, and state.
Status terminology is the main inhibitor.

**Theo:**
> “The default title is timestamp-heavy but useful until renamed. Filtering by processed
> state makes sense if the unprocessed label is precise.”

**Analyst:** Preserve compact list/detail behavior; clarify the filter rather than adding
more cards or metadata.

### State: Session detail

**Maya:**
> “I like that the words are tucked away until I ask. But the title is squeezed next to
> Rename, so it looks busier than the rest of the screen.”

**Analyst:** Observed title wrap and secondary-action competition. LIFT: Distraction. Give
the first-read object full width.

**Theo:**
> “Transcript and inferred summary being separate is exactly right. Sharing only the chosen
> content and excluding metadata is a strong privacy decision.”

**Analyst:** Cialdini is not relevant here; clarity and ability are. Keep the explicit share
choice and local provenance.

## Journey 3 — Devices and AI

### State: Audio device picker

**Maya:**
> “Bluetooth says it’s coming, but the label still looks like a normal choice. I’d rather it
> look unavailable right away.”

**Analyst:** Observed disabled radio with full-emphasis copy. Trust →, ability Medium. Use
disabled emphasis while retaining the explanatory text.

**Theo:**
> “The limitations are honest. Puck setup being separate is sensible. Don’t hide the option;
> just make its availability state visually consistent.”

**Analyst:** This is an emphasis correction, not a reason to remove roadmap visibility.

### State: AI Selection

**Maya:**
> “Why are there question marks next to these? Is something wrong with both options?”

**Analyst:** Observed glyph fallback directly reduces trust. LIFT: Anxiety and Clarity.

**Theo:**
> “The priority and fallback model is good, but the broken reorder symbol makes the page feel
> unfinished. The LAN versus on-device labels are otherwise specific enough.”

**Analyst:** Use a supported icon with reorder semantics. Preserve the endpoint, connector
type, enabled state, and explicit provider order.

## Verdict

| Measure | Maya | Theo |
|---|---:|---:|
| Confidence | 7/10 | 8/10 |
| Clarity | 7/10 | 8/10 |
| Relevance | 9/10 | 8/10 |
| Likely action | Yes, after confirming Start | Yes |

Scores summarize this simulation only.

### Shared strengths

1. Manual and active modes are distinct from the first screen.
2. Recording and processing states communicate progress without invented precision.
3. Local provenance, selective sharing, and provider order support privacy trust.

### Shared weaknesses

1. “Pending” breaks the transcript-ready mental model.
2. The idle capture prompt is inferred rather than visible.
3. Broken or enabled-looking configuration affordances undermine otherwise honest copy.

**Strongest abandonment moment:** Maya encountering “Pending” after expecting a usable
transcript; it suggests the core job may not be complete.

**Strongest engagement moment:** Recording transitions to explicit local transcription.

## Prioritized recommendations

1. **Quick win — Name completed transcription accurately.** Change Transcribed display to
   “Transcript ready” and Pending filter to “Unprocessed.” Expected effect: users distinguish
   successful transcription from optional interpretation. Validate with five-second
   comprehension tasks and state screenshots.
2. **Quick win — Label the capture prompt visibly.** Add “Start listening” adjacent to the
   circular control while retaining its accessibility description. Expected effect: reduce
   hesitation for first-time users. Validate with idle-state screenshot and TalkBack node.
3. **Quick win — Replace the reorder fallback glyph.** Use a supported Material icon and
   content description. Expected effect: restore confidence in provider prioritization.
4. **Quick win — Visually disable unavailable inputs.** Reduce emphasis for unsupported
   choices while retaining honest setup copy. Expected effect: faster availability scanning.
5. **Major improvement — Protect the session title hierarchy.** Give the title full width and
   place Rename below it. Expected effect: long titles remain the first-read object at target
   width and larger font scales.

## Unknowns requiring validation

- Permission denial and recovery comprehension
- Dark-theme hierarchy and contrast after changes
- Large-font and TalkBack order on session detail and provider cards
- Whether real users understand “Make sense of this” without onboarding

