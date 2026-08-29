---
name: persona-walkthrough-specialist
description: Simulate persona-led cognitive walkthroughs of mobile apps or websites, pairing authentic first-person reactions with structured LIFT, Cialdini, and Fogg analysis. Use for journey reviews, conversion or activation critique, multi-persona comparisons, and experience hypotheses; do not treat the simulation as user research or statistical evidence.
---

# Persona Walkthrough Specialist

Experience an interface from a defined persona's psychological perspective, then translate
their reactions into concrete UX hypotheses. Preserve two distinct voices: the persona's
plain-language inner monologue and the analyst's framework-grounded assessment.

## Establish the Persona

Build a psychologically coherent profile before the walkthrough. Use information supplied
by the user and available product context. A useful profile includes:

- Name, age range, cultural context, current situation, and relevant accessibility needs
- Arrival context: triggering event, source, prior alternatives, device, and environment
- Domain familiarity, urgency, primary fears, and trust triggers
- Decision style and reassurance preference
- Goal, success condition, and threshold for taking the primary action

Ask only for missing details that would materially change the walkthrough. Otherwise state
reasonable assumptions and proceed. Treat attachment tendency as an optional hypothesis,
not a diagnosis. Avoid unsupported cultural stereotypes; use cultural context only when the
user provides evidence or asks for a cross-cultural comparison.

## Inspect the Real Experience

Use available screenshots, app/device inspection, browser inspection, or journey artifacts.
For mobile apps, evaluate one meaningful screen or state transition at a time rather than
forcing web-style page folds. For scrolling pages, capture the initial viewport and then
representative scroll positions. Reuse screenshots where journeys overlap.

Run these phases:

1. **Pre-arrival:** Set the persona's expectation, concern, and relevance contract.
2. **Five-second test:** On the first stable screen, ask: What is this? Is it for me? What
   should I do next? Mark an unclear answer as a critical hypothesis.
3. **Progressive journey:** At every meaningful screen, state change, or scroll position,
   record the persona monologue and a separate analyst assessment.
4. **Verdict:** Summarize confidence, clarity, relevance, likely action, strengths,
   weaknesses, and the strongest engagement and abandonment moments.
5. **Recommendations:** Tie each action to the exact screen, persona reaction, observed
   evidence, and framework factor. Prioritize by likely impact and implementation effort.

Track whether the primary action is visible and usable at each relevant state. Note repeated
friction when it persists, but do not mechanically demand a CTA on screens whose purpose is
orientation, consent, progress, or review.

## Keep the Two Voices Separate

The persona speaks in first-person, colloquial language without UX terminology. Maintain
their familiarity, fears, patience, and decision style consistently; confidence changes only
after an observable trigger.

The analyst identifies evidence and maps it to:

- LIFT: value proposition, relevance, clarity, urgency, anxiety, distraction
- Cialdini: reciprocity, commitment, social proof, authority, liking, scarcity, unity
- Fogg: motivation, ability, and prompt

Never blend framework labels into the persona monologue. Read
[references/method-and-templates.md](references/method-and-templates.md) when producing a
formal report, multi-persona comparison, or framework-scored walkthrough.

## Evidence and Boundaries

Start every deliverable with a brief statement that persona simulation is qualitative and
generates hypotheses to validate with real users, analytics, accessibility testing, or
experiments. Distinguish:

- **Observed:** directly visible behavior, copy, layout, state, or interaction
- **Inferred:** a plausible persona reaction grounded in the supplied profile
- **Unknown:** performance, comprehension, conversion, or accessibility outcomes not tested

Do not invent benchmarks, audience behavior percentages, conversion effects, support-call
patterns, or framework certainty. Phrase expected effects as hypotheses. Be opinionated
about observed friction while remaining honest about confidence.

For Project Echo, respect the offline-first privacy model and clearly label unfinished
Bluetooth, Puck, diarization, or other placeholder behavior. Do not recommend cloud services,
analytics, tracking, or collection of sensitive audio/transcript data. Favor local usability
tests and privacy-preserving validation.

## Deliverable Quality

A useful report includes:

- Persona profile and assumptions
- Pre-arrival relevance contract
- Five-second-test result
- Persona monologue plus analyst assessment for every meaningful state
- Emotional and trust arc across the journey
- Verdict with explicit limitations
- Prioritized recommendations traceable to evidence
- Conflicts and trade-offs when comparing personas

Keep recommendations specific enough to implement and test. Prefer a smaller number of
well-supported findings over a long checklist of generic UX advice.
