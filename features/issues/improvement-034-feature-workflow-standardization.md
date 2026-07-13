# improvement-034: Feature-workflow standardization — SPEC.md template + `/feature` skill

**Type:** improvement — process tooling. Migrated from `features/process-improvements.md` Part 2,
item 12 (SPEC.md template portion only — see Explicitly not migrated below for the rest of that
item).
**Module:** `.claude/` skills, `features/` conventions
**Priority:** low — convenience/consistency, no correctness or safety impact
**When:** independent, no blockers

## Problem

Recent feature specs (e.g. the entity-extensions SPEC, the leaf-widgets-plain-classes work behind
improvement-025) converged organically on the same shape — Goal / Problem / Files / Steps /
Acceptance criteria — but there is no template or skill scaffolding new features into that shape
from the start; each one is written from scratch and consistency depends on remembering the
pattern.

## Suggested fix

- A `SPEC.md` template capturing the shape that's already emerged in practice: Goal, Problem,
  Files (scope), Steps, Acceptance criteria.
- A `/feature <name>` skill that scaffolds a new `features/<name>/SPEC.md` from that template,
  so every new feature starts with measurable acceptance criteria rather than free-form prose.

## Explicitly not migrated from the source item

The source process-improvements.md item 12 also mentioned **OpenRewrite**, explicitly marked
"deferred" there: worth adopting only if mechanical mass-refactors become a regular occurrence
(official recipes exist for Spring Boot version upgrades; custom recipes possible for conversions
like improvement-025's leaf-widget refactor). Not given its own issue — it has no concrete trigger
yet, same reasoning the source document already applied; revisit if a third mechanical
mass-refactor of similar shape comes up.

Also explicitly rejected in the source item (not migrated, kept only as a historical note): PIT
mutation testing (premature without a unit layer — see improvement-027), Error Prone/NullAway
(duplicates Lombok `@NonNull` at doubtful gain), Checkstyle (SonarQube already covers style, see
improvement-032).

## Related

- `features/process-improvements.md` Part 2, item 12 — source item, now superseded by this issue
  (partially — see above for what was deliberately not migrated).
- `features/entity-extensions/SPEC.md` — one example of the SPEC shape this issue formalizes.
