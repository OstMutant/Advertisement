# improvement-033: `/quality-gate` skill + Definition of Done in rules.md

**Type:** improvement — process tooling. Migrated from `features/process-improvements.md` Part 2,
item 10.
**Module:** `.claude/` skills, `.claude/rules.md`
**Priority:** low-medium — a convenience/discipline layer on top of gates that mostly don't exist
yet
**When:** **blocked** — depends on improvement-027 (unit/Testcontainers layer), improvement-030
(ArchUnit), and improvement-032 (SonarQube blocking) all existing first; do this last of the four,
once its three prerequisites are real

## Problem

Even once the individual gates below exist, there is no single command that runs them all in one
pass with one summary, and no explicit, written Definition of Done that a feature must satisfy
before being considered complete — the current discipline (full e2e + DECISIONS.md update + issue
moved to completed) is enforced by the rules already in `.claude/rules.md`, but is not backed by a
single fast local command that chains the faster gates before the slow e2e run.

## Suggested fix

A single skill/command chaining gates fastest to slowest, with one summary report:
1. compile + ArchUnit (~30s) — requires improvement-030.
2. unit/Testcontainers tests (~1-2 min) — requires improvement-027.
3. Sonar quality gate (blocking) — requires improvement-032.
4. smoke e2e (~2-3 min).

Record in `.claude/rules.md` as the explicit Definition of Done: a feature is not complete until
`/quality-gate` is green, the relevant full e2e scenario passes, `DECISIONS.md` is updated (if
architectural), and the issue file is moved to `features/completed/issues/` — codifying practices
this project already follows informally into one written checklist.

## Related

- `features/process-improvements.md` Part 2, item 10 — source item, now superseded by this issue.
- `features/completed/issues/improvement-027-unit-testcontainers-test-layer.md`,
  `improvement-030-archunit-test-module.md`,
  `improvement-032-sonarqube-quality-gate-blocking.md` — the three hard prerequisites.
- `.claude/rules.md` — where the Definition of Done gets recorded once this lands.
