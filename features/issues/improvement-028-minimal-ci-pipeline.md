# improvement-028: No CI pipeline at all — tests and builds run only when someone remembers

**Type:** improvement — process/tooling. Migrated from `features/process-improvements.md` Part 1,
item 4 (verified 2026-07-04, re-verified during migration on 2026-07-13 — still no `.github/`
directory in the repo).
**Module:** repo-wide (CI config, no application code)
**Priority:** medium — the "tests after fixes" project rule is currently enforced by discipline
alone; nothing stops a broken build or a skipped e2e run from landing on `main`
**When:** independent — becomes materially more valuable once improvement-027 (unit/Testcontainers
layer) and improvement-030 (ArchUnit) exist, since a CI pipeline with nothing fast to run gains
little; can still add the skeleton (build + full e2e) now

## Problem

No CI configuration exists anywhere in the repo (no `.github/workflows/`, no `Jenkinsfile`, no
`.gitlab-ci.yml`). Every quality gate — compile, `mvn test`, Playwright e2e, SonarQube — currently
runs only when a person remembers to run it manually, following the documented Monitor+tee
patterns in `scripts/CLAUDE.md`. A broken build or a regression can be committed and pushed with
no automatic signal.

## Suggested fix

GitHub Actions (or equivalent), staged by cost:
- **Every push:** `mvn test` (once improvement-027 exists) + full multi-module `mvn compile`/
  `package`.
- **On PR:** the above, plus one smoke Playwright scenario (fastest single spec, not the full
  suite).
- **Nightly (scheduled):** full e2e (`scripts/playwright.sh e2e --full`).
- **Optional:** a SonarQube scan step — the server and `scripts/sonar.sh` already exist but are
  currently outside any automated loop (see improvement-032 for making its gate blocking).

## Dependencies / sequencing

- Best done *after* improvement-027 (unit/Testcontainers layer) exists, so "every push" has
  something fast and meaningful to run — a CI pipeline that only runs `mvn compile` is a weak
  starting point.
- Complements improvement-030 (ArchUnit) — once ArchUnit tests exist, they run as part of the same
  `mvn test` step for free.

## Related

- `features/process-improvements.md` Part 1, item 4 — source item, now superseded by this issue.
- `features/completed/issues/improvement-027-unit-testcontainers-test-layer.md` — the test layer this
  pipeline should actually exercise.
- `features/issues/improvement-030-archunit-test-module.md` — architecture rules that become
  build-breaking tests once both land.
- `features/issues/improvement-033-quality-gate-skill-and-definition-of-done.md` — the manual/local
  equivalent of what this issue automates in CI.
