# improvement-032: SonarQube quality gate is informational only — make it blocking — ✅ DONE (2026-07-16)

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

## Resolution (2026-07-16)

`scripts/sonar/run.sh` now passes `-Dsonar.qualitygate.wait=true` by default, so `sonar-scanner`
polls the computed quality gate status after upload and the script exits non-zero if it's `ERROR`.
`--no-gate` (forwarded through `scripts/sonar.sh`) restores the old informational-only behavior for
a quick manual scan. `scripts/ci/entrypoint.sh`'s `sonar` stage takes the default — the point of
wiring this into `scripts/ci.sh` was for the gate to actually fail a CI run.

Turning this on surfaced a real, pre-existing bug that would have silently defeated the fix even
with the wait flag added: the scanner invocation was piped through `tee "$LOG"`, and the script's
own `EXIT_CODE=$?` was reading `tee`'s exit status (always 0), never the scanner's. Fixed by reading
`${PIPESTATUS[0]}` instead, bracketed with `set +e`/`set -e` (not a trailing `|| true`, which would
itself have overwritten `PIPESTATUS` with `true`'s own exit code before it could be read) so the
active `set -e` doesn't abort the script before the HTML report gets generated on a gate failure.

Verified directly: with the default flag, a real quality-gate failure (35 existing issues in this
codebase) correctly exits non-zero (`3`) with a clear `QUALITY GATE FAILED` message, while still
generating the HTML report; with `--no-gate`, the same run reports `EXECUTION SUCCESS` and exits 0
regardless. As of this fix, `scripts/ci.sh`'s default extensive run will report its `sonar` stage as
`FAILED` until those 35 existing issues are addressed or the gate itself is reconfigured — an
intended, immediate consequence of turning this on, not a bug. See `scripts/sonar/DECISIONS.md`
(2026-07-16 entry) for the full technical writeup.

## Related

- `backlog/process-improvements.md` Part 2, item 9 — source item, now superseded by this issue.
- `scripts/CLAUDE.md` — SonarQube setup and run instructions, updated for the new blocking default.
- `scripts/sonar/DECISIONS.md` — full technical writeup of the fix (PIPESTATUS/set -e bug).
- `backlog/completed/issues/improvement-059-local-isolated-parameterized-ci-runner.md` — the local
  CI runner whose `--sonar` stage now actually fails on a bad quality gate, not just reports one.
- `backlog/issues/improvement-028-minimal-ci-pipeline.md` — where this gate would also gate a
  GitHub Actions push/PR, not just local runs.
- `backlog/issues/improvement-033-quality-gate-skill-and-definition-of-done.md` — the local
  `/quality-gate` command this becomes one step of.
