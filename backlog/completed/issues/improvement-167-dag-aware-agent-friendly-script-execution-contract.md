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

## Related

- `.claude/rules.md` "Error Reporting" — the existing, currently-sufficient diagnosis convention.
- `backlog/issues/improvement-036-actuator-structured-logging.md` — adjacent but distinct
  (application-runtime structured logging, not shell-script execution contracts).
- `scripts/ci/DECISIONS.md` (ADR-009, ADR-010) — real, verified facts about Dagu's actual
  run-identity/working-directory/dependency-gate behavior, used to ground this analysis.
