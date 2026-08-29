# Design Contract and Finish-Gate Templates

Use these templates for formal pre-ship reviews. Adapt the headings to the scoped interface;
do not fill sections with generic observations merely to complete the template.

## Design Contract

```markdown
# [Screen or journey] Design Contract

**User + job:** [Who completes what]
**First-read object:** [What must be understood first]
**Primary action:** [One observable action]
**Frequency and risk:** [Frequent workflow and rare high-risk action]
**Density decision:** [Compact / balanced / spacious, and why]
**Hierarchy:** [Key signal, controls, and supporting information]
**Interaction model:** [Capture, editor, timeline, list/detail, form, etc.]
**Target adaptation:** [What stays, collapses, moves, or changes across targets]
**Reference lessons:** [Pattern → transferable lesson; omit if not researched]
**Forbidden defaults:** [Specific generic choices that conflict with this product]
**Finish evidence:** [Screenshots, states, themes, sizes, semantics, and tests]
```

## Finish Gate

```markdown
# UI Finish Gate — [Screen or journey]

## Decision: PASS | HOLD

## Scope and evidence reviewed
- [Build/version, device or viewport, theme, state, screenshot/test]

## Blocking evidence
- [Observed issue] → [why it breaks the design contract]

## Required before PASS
1. [Concrete change] — verify with [specific state, target, or test]

## Keep
- [Specific choice that already serves the product]

## Optional refinements
- [Non-blocking improvement]

## PASS criteria
- [First-read object and primary action are clear]
- [No prohibited default remains without a product reason]
- [Required states and target checks are verified]

## Unreviewed
- [Unavailable states or targets; omit when none]
```

If the decision is PASS, replace “Blocking evidence” and “Required before PASS” with a short
statement that no blocker was observed in scope. Do not invent blockers to populate sections.

## Finding Format

Each blocking finding should be independently actionable:

```text
[Priority] [Screen/state] — [Short finding]
Observed: [Visible or interactive evidence]
Product impact: [Specific user job, decision, or risk affected]
Required change: [Outcome to implement, without prescribing taste]
Verify: [Device/viewport, state, theme, accessibility check, or test]
```

Use severity only when it changes release disposition:

- **P0:** unsafe, destructive, privacy-breaking, or core flow cannot complete
- **P1:** primary workflow or first-read product understanding is materially blocked
- **P2:** meaningful finish problem that should be corrected but does not alone block the
  scoped release
- **P3:** optional refinement

## Reference Evidence

When comparable products materially help, record no more than a focused set:

| Reference | Pattern | Job served | Transferable lesson | What not to copy |
|---|---|---|---|---|

Prefer official product pages, platform guidelines, or directly observed implementations.
Reference popularity is not evidence that a pattern fits this product.
