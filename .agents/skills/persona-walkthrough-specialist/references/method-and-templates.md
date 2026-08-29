# Walkthrough Method and Templates

Read this reference when a formal, framework-scored report or multi-persona comparison is
requested.

## Persona Profile

```text
PERSONA PROFILE
Name:
Age range and relevant identity/context:
Current situation:
Accessibility needs:

ARRIVAL CONTEXT
Trigger or exact search/query:
Arrival source:
Alternatives seen first:
Device and environment:

PSYCHOLOGY
Domain familiarity: Low / Medium / High
Urgency: Browsing / Weeks / Days / Urgent
Primary fears:
Trust triggers:
Decision style:
Reassurance preference: frequent / basics only / facts-first

GOAL
Success looks like:
Action threshold:
```

## Per-State Entry

```text
PERSONA — State [N]: [screen or transition]
"[Raw first-person monologue without UX jargon.]"

ANALYST — State [N]
Observed evidence:
Inferred emotional state:
Trust delta: ↑ / ↓ / → and why
LIFT factor most affected:
Cialdini active:
Cialdini potentially missing:
Fogg position: Motivation Low/Med/High | Ability Low/Med/High | Prompt Yes/No/N/A
Primary action reachable: Yes / No / N/A
Accessibility or technical notes: observed issues only
Confidence: High / Medium / Low
```

For a scroll-based page, replace `State` with `Fold`. For mobile, use named screens and
state transitions such as permission request, recording, processing, empty, success, or
error.

## Verdict

```text
VERDICT
Confidence: 1-10 — Would this persona trust the experience with their data or goal?
Clarity: 1-10 — Do they understand what happens and why?
Relevance: 1-10 — Does the journey serve their triggering need?
Likely action: Yes / No / Maybe — and why

Top strengths:
1.
2.
3.

Top weaknesses:
1.
2.
3.

Strongest abandonment moment:
Strongest engagement moment:
Unknowns requiring validation:
```

Scores summarize this simulation; they are not measurements or population estimates.

## Recommendations

```text
[Quick win | Major improvement | Strategic opportunity] — [Title]
State: [screen/transition]
Evidence: [what was observed]
Persona reaction: [what was inferred]
Framework: [LIFT / Cialdini / Fogg factor]
Change: [specific implementation]
Expected effect: [testable hypothesis, not a promised outcome]
Validation: [usability task, local prototype comparison, accessibility check, or experiment]
```

- **Quick win:** focused copy, hierarchy, placement, labeling, or affordance change.
- **Major improvement:** journey restructuring, missing state, or substantial component work.
- **Strategic opportunity:** broader product capability requiring planning and validation.

Effort tiers are contextual. Do not assign time estimates without inspecting the product.

## Multi-Persona Comparison

Compare two or three personas against the same evidence. Use a matrix with rows for key
screens or decisions and columns for each persona. Capture:

- Reaction and trust change
- Needed information or reassurance
- Ability and prompt differences
- Shared friction
- Direct conflicts and the audience currently favored

Do not erase contradictions by averaging scores. Treat conflicts as product-positioning or
progressive-disclosure decisions.

## Framework Quick Reference

### LIFT

The value proposition is moderated by relevance and clarity, strengthened where appropriate
by urgency, and inhibited by anxiety and distraction. Do not manufacture urgency or remove
necessary consent, privacy, or safety information as "distraction."

### Cialdini

- Reciprocity: value offered before asking
- Commitment: a small voluntary step supports a later action
- Social proof: relevant peers demonstrate credible use
- Authority: verifiable expertise or official status
- Liking: relatable people and tone
- Scarcity: genuine constraint, never fabricated pressure
- Unity: authentic shared identity

Not every principle belongs on every screen. Identify only principles relevant to the
persona's decision and the product's ethics.

### Fogg Behavior Model

Behavior occurs when motivation, ability, and a prompt converge. Diagnose whether the user
lacks reason, capability, or a timely cue before proposing a stronger prompt. For privacy or
permission decisions, comprehension and voluntary consent take priority over conversion.
