# improvement-028: No CI pipeline at all — tests and builds run only when someone remembers

**Type:** improvement — process/tooling. Migrated from `backlog/process-improvements.md` Part 1,
item 4 (verified 2026-07-04, re-verified during migration on 2026-07-13 — still no `.github/`
directory in the repo).
**Module:** repo-wide (CI config, no application code)
**Priority:** low — [improvement-059](../completed/issues/improvement-059-local-isolated-parameterized-ci-runner.md)
(the local, isolated, parameterized runner this issue was deferred behind) is now done, so the
pipeline logic itself is proven; what's left is genuinely GitHub-specific and unrelated to that:
repo is public so execution is free, but still needs `gh` CLI set up + explicit push authorization,
and a from-scratch GitHub-hosted-runner environment vs. this project's current sandbox-tuned
scripts.
**When:** unblocked (improvement-059 done 2026-07-16) — the same compile/unit/integration/e2e
pipeline already exists as `scripts/ci.sh`; migrating it to GitHub Actions steps is now the
remaining, much smaller step (see that issue's "Related" for how the two map onto each other:
local `ci-*` env-var overrides ↔ GitHub `services:`, `ci-m2-cache` named volume ↔ `actions/cache`).
Still also benefits from improvement-030 (ArchUnit, not yet done) existing first, per the original
sequencing note below.

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

- `backlog/process-improvements.md` Part 1, item 4 — source item, now superseded by this issue.
- [improvement-059](../completed/issues/improvement-059-local-isolated-parameterized-ci-runner.md)
  — the local runner this issue builds on, done 2026-07-16; the same pipeline logic, proven locally
  first, now to be adapted here by swapping the outer orchestration layer (`ci-*` env-var overrides
  → GitHub `services:`, `ci-m2-cache` named volume → `actions/cache`) without rewriting the actual
  build/test steps.
- `backlog/completed/issues/improvement-027-unit-testcontainers-test-layer.md` — the test layer this
  pipeline should actually exercise.
- `backlog/issues/improvement-030-archunit-test-module.md` — architecture rules that become
  build-breaking tests once both land.
- `backlog/issues/improvement-033-quality-gate-skill-and-definition-of-done.md` — the manual/local
  equivalent of what this issue automates in CI.
