# improvement-032: SonarQube quality gate is informational only — make it blocking

**Type:** improvement — quality tooling. Migrated from `backlog/process-improvements.md` Part 2,
item 9.
**Module:** `scripts/sonar.sh`, `scripts/sonar/` config
**Priority:** low-medium — zero new infrastructure needed, this is a config flip on a tool that
already exists and already runs (`bash scripts/sonar.sh`, per `scripts/CLAUDE.md`); the gap is
purely that nobody's results block anything
**When:** independent, no blockers — do any time; higher value once improvement-028 (CI) exists so
the gate actually runs on every push rather than only when manually invoked

## Problem

SonarQube server and `scripts/sonar.sh` already exist and produce real analysis results
(`http://localhost:9099/dashboard?id=advertisement` per `scripts/CLAUDE.md`), but nothing fails
when new issues are introduced — the scan is informational, checked (if at all) by someone
manually opening the dashboard.

## Suggested fix

Enable fail-on-quality-gate in the `sonar-scanner-cli` invocation inside `scripts/sonar.sh` (or
the underlying scanner config), so a quality-gate failure exits non-zero and can block a commit/PR
once wired into CI (improvement-028).

## Related

- `backlog/process-improvements.md` Part 2, item 9 — source item, now superseded by this issue.
- `scripts/CLAUDE.md` — existing SonarQube setup and run instructions this issue makes blocking.
- `backlog/issues/improvement-028-minimal-ci-pipeline.md` — where this gate actually gets
  enforced on every push, not just on manual local runs.
- `backlog/issues/improvement-033-quality-gate-skill-and-definition-of-done.md` — the local
  `/quality-gate` command this becomes one step of.
