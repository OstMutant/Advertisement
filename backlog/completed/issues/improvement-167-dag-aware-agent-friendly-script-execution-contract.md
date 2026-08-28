# improvement-167: DAG-aware agent-friendly script execution contract — pilot for scripts/deploy-and-run.sh

**Type:** improvement — tooling/infra, design pending
**Module:** `scripts/deploy-and-run.sh` (delegates to `scripts/deploy-and-run/run.sh`),
  `scripts/ci/dagu/ci.yaml`, `scripts/ci/watch-run.py`, candidate new `scripts/utils/`
**Priority:** high (Top)
**When:** independent, no blockers — scope/go decision deferred, see "Open decision" below

## Problem

A large mission prompt proposed building a structured, machine-readable execution contract
between Dagu, project scripts, monitoring utilities, and AI agents: a run-identity hierarchy
(`DAG_RUN_ID`/`SCRIPT_ID`/`EXECUTION_ID`), concurrency-safe per-execution state directories, a
4-category structured error taxonomy (`transient`/`validation`/`business`/`environment`) emitted
as an `AGENTIC_ERROR_BLOCK` JSON marker, a progress/event stream, and programmatic dependency
gates — piloted on `scripts/deploy-and-run.sh` only.

Investigated against the real project state before deciding whether to build any of it:

- Dagu already auto-injects `$DAG_NAME`/`$DAG_RUN_ID` into every step (confirmed in
  `scripts/ci/DECISIONS.md` ADR-010 — `scripts/ci/dagu/pipeline-metrics.py` already consumes
  this via Dagu's own REST API). The mission's own "do not assume `DAGU_RUN_ID` exists" caution
  is already resolved: it exists and is already used.
- Each Dagu step already gets an isolated `DAG_RUN_WORK_DIR`; `continue_on: {failure: true,
  skipped: true}` is already the project's existing dependency-gate mechanism at the DAG level.
- `scripts/deploy-and-run/run.sh` is a **linear `set -e` bash script** with an existing
  `trap ... ERR` that already prints `=== FAILED (exit N) ===` plus a tail of the relevant
  `docker logs` on failure. A later stage cannot run after an earlier one fails — bash already
  guarantees this; no separate gate mechanism is needed for a single sequential script.
- It is invoked from exactly one place in `scripts/ci/dagu/ci.yaml` (the `e2e` step) — not
  self-invoked in parallel by Dagu today. The project's real parallelism is across *different*
  steps (`unit`/`integration`/`e2e`/`sonar`/`archunit_metrics`), not concurrent instances of this
  one script.
- The script currently writes **no state files at all**, so the "parallel run overwrites another
  run's state" risk the mission spends most of its design on does not exist yet — it would only be
  introduced by implementing the mission's own state-directory layer.
- No existing convention for `AGENTIC_ERROR_BLOCK`/`agent-logger`/`errorCategory`/`isRetryable`
  exists anywhere in the repo (grep-verified). `improvement-036` (Spring Boot actuator/structured
  JSON logging) is an adjacent but distinct concern — application-runtime logging, not
  shell-script execution contracts.
- Today's actual AI-agent failure-diagnosis path is `.claude/rules.md`'s "Error Reporting" rule:
  read the raw script/log output directly and report the specific error lines — already
  established practice throughout this project's `DECISIONS.md` history ("confirmed directly"
  style), not a demonstrated pain point.

## Suggested fix

**Open decision — not yet made.** Filed to capture the analysis and keep the idea visible; the
scope has not been chosen yet. Candidates identified so far, weakest to largest:

1. **Minimal (leaning recommendation from initial analysis):** a single `AGENTIC_ERROR_BLOCK`
   JSON summary emitted only on failure, at the end of `scripts/deploy-and-run/run.sh`'s existing
   `trap ... ERR` handler (`category`/`isRetryable`/`currentStep`/`description`) — no run-identity
   hierarchy, no state directory, no event stream, since the concurrency problem those solve
   doesn't exist for this script today.
2. **Full mission scope:** everything described above (run-identity resolution, concurrency-safe
   execution-scoped state directories, full error taxonomy, progress events, dependency-gate
   module, `watch-run.py` integration, validated across 11 concurrency/failure scenarios). Real
   cost, uncertain payoff given the findings above — would need a concrete demonstrated need
   (e.g. `deploy-and-run.sh` actually starting to run concurrently from multiple call sites) before
   this is justified, per this project's own "don't design for hypothetical future requirements"
   principle.
3. **Do nothing:** current `set -e` + `trap ERR` + direct-log-reading already covers the project's
   actual, demonstrated needs.

## Refined candidate — shared utility library (2026-08-26)

Checked against the real private certification document (`/app/private/AICertificationAndAudit/AICertification.txt`, "Structured Error Responses" section) before drafting this: the real 4-category
taxonomy is `transient` / `validation` / `business` / `permission` — **not** `environment` as
this issue's own Problem section states above; that fourth category name does not appear anywhere
in the certification source and should be treated as unverified until corrected or re-sourced.
Each category carries `errorCategory` + `isRetryable` + `description`; the same document also
shows the identical envelope shape (a `status` field) used on both the success and the failure
response, not only on failure.

**Approach:** one shared function, in a new `scripts/utils/agentic-output.sh` (same shared-library
pattern as the existing `scripts/utils/ensure-docker-plugins.sh` — sourced by multiple
script-groups, not owned by any one of them), emitting the JSON envelope on both the success exit
path and the `trap ERR` failure path of a script — not only on failure as candidate 1 originally
scoped it.

**Target scripts, split by current state:**
- Already have `trap ... ERR` today (wiring in the shared function is the whole change):
  `scripts/deploy-and-run/run.sh`, `scripts/deploy-and-run/reset.sh`, `scripts/sonar/run.sh`
- No `trap ERR` yet (would need one added before the shared function has anywhere to hook into):
  `scripts/build-and-test/run.sh` (has `set -e`/`set +e`, no trap), `scripts/run-all-tests/run.sh`,
  `scripts/ci/run.sh`, `playwright/run.sh` (none of the three have `set -e` or a trap today)

All 7 top-level `scripts/*.sh` files (`build-and-test.sh`, `deploy-and-run.sh`, `run-all-tests.sh`,
`ci.sh`, `sonar.sh`, `playwright.sh`, `reset.sh`) are thin delegators with no logic of their own —
the envelope must be wired into each target's own `run.sh`/`reset.sh`, never into the delegator.

**Implemented (2026-08-26):** `scripts/utils/agentic-output.sh` created
(`emit_agentic_success_block`/`emit_agentic_error_block`), wired into the three scripts that
already had `trap ... ERR` — `scripts/deploy-and-run/run.sh`, `scripts/deploy-and-run/reset.sh`,
`scripts/sonar/run.sh`. Every real exit path in these three files now emits the matching JSON
marker, not only the generic `trap ERR` catch-all — confirmed `trap ERR` does not fire on an
explicit `exit N` (bash behavior, verified directly), so each script's own explicit-`exit` branches
(Liquibase-mismatch-after-retry and startup-timeout in `run.sh`; token-generation-failure and
quality-gate-failed in `sonar/run.sh`) got their own explicit `emit_agentic_error_block` call with
a category fitted to that specific failure, not the generic trap's default. **Extended to the remaining 4 scripts (2026-08-26):** rather than mechanically forcing `trap ERR`
onto every script, the mechanism was matched to each script's real control-flow shape:
- `scripts/build-and-test/run.sh` — already `set -e`-shaped like the first 3 scripts; got a
  `trap ERR` plus explicit emits at its own precondition-check and final build/test-result exit
  points.
- `scripts/ci/run.sh`, `playwright/run.sh` — no `set -e`, single linear command with `$?` captured
  into an `EXIT_CODE` variable and one final `exit`; no `trap` added (nothing for it to catch) —
  emit calls placed directly at each existing explicit `exit` point instead.
- `scripts/run-all-tests/run.sh` — deliberately runs its build-and-test and deploy+playwright
  branches to completion independently, even if one fails, then combines both exit codes into one
  `RESULT_LINE` — adding `set -e`/`trap ERR` here would break that intentional behavior. One emit
  call added at the script's own existing combined-result point instead, no trap.

Also added a `durationSeconds` field to both `emit_agentic_success_block`/`emit_agentic_error_block`
(reads bash's own `$SECONDS`, caller sets `SECONDS=0` as its first executable line) — this is the
one metric field with a real, already-existing consumer (`.claude/rules.md`'s mandatory
`## Operational notes` block already requires a `duration_s` value per script run), not a
speculative addition — checked against the real certification document's own "Tool Result
Trimming" guidance (`/app/private/AICertificationAndAudit/AICertification.txt`, context-management
section) before adding it: trim to only the fields actually consumed downstream, don't pile on
unused metrics "just in case".

All 8 touched files (`scripts/utils/agentic-output.sh` + 7 callers) pass `bash -n`; the emit
functions were smoke-tested directly (`durationSeconds` increments correctly). `deploy-and-run.sh`
was run end-to-end once live (success path only) and printed the expected
`AGENTIC_SUCCESS_BLOCK`. No error path has been exercised live in any of the 7 scripts yet — every
error-path emit call has only been verified by syntax check + reading, not by actually triggering
that failure.

## Related

- `.claude/rules.md` "Error Reporting" — the existing, currently-sufficient diagnosis convention.
- `backlog/issues/improvement-036-actuator-structured-logging.md` — adjacent but distinct
  (application-runtime structured logging, not shell-script execution contracts).
- `scripts/ci/DECISIONS.md` (ADR-009, ADR-010) — real, verified facts about Dagu's actual
  run-identity/working-directory/dependency-gate behavior, used to ground this analysis.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: Feature, single module (closest row — new scripts/utils/ shared library, scripts/DECISIONS.md read in full via the record-decision skill's own step, not filtered through adr-index.md first)
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: A real architectural decision was just made; about to write/edit a script's own header
- flows_chosen: /record-decision; infra-doc-standards skill
- flows_matched: yes

### Script/command runs
- bash scripts/deploy-and-run.sh | duration_s=n/a (durationSeconds field not yet implemented at time of this run) | mode=background | result=pass
- bash scripts/build-and-test.sh --unit --integration --sandbox | duration_s=172 | mode=background | result=pass
