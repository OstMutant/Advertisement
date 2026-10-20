# improvement-218: CI monitor shows real step durations

**Type:** improvement — tooling accuracy
**Module:** `scripts/activity-monitor/run.sh`, `scripts/ci/dagu-rest-run-monitor.py` (the Dagu sidecar that feeds it)
**Priority:** low — Top, requested so it is not forgotten; small
**When:** independent

## Current state

`scripts/activity-monitor/run.sh` shows a step's duration as its completion time minus a baseline, where the baseline is the previous step's completion time (or the wrapper start). For the CI pipeline the unit, integration, end-to-end, Sonar and ArchUnit stages run in parallel, so they are seen finishing at the same moment: all but the last show about `0s`, and the last one in the list ("Pipeline metrics") shows the whole wait for the end-to-end stage (24 minutes in the run of 2026-10-19). `dagu-rest-run-monitor.py` carries a `durationSeconds` field that the parser ignores, and says the real duration is computed by `run.sh` from its own timestamps.

Dagu itself records real start and finish times per step. Read on 2026-10-20 for the last three runs: build 100–119 s, unit 261–299 s, integration 377–445 s, end-to-end 1441–1551 s, Sonar 802–922 s, ArchUnit 246–271 s, lint ~20 s, shellcheck 16 s, docs ~58 s; only `pipeline_metrics` is genuinely 0 s (it only aggregates). All three runs were green, with 66 end-to-end tests passing and the Sonar gate passed.

## Why change

The `0s` labels and the 24-minute "Pipeline metrics" misled a reading of the run as suspicious when it was healthy, and the same baseline logic feeds the per-step history used to flag stalls.

## Expected benefit

The monitor shows each stage's real duration, so a green run can be read at a glance and the stall baseline is based on true durations.

## Approach

1. In the Dagu sidecar, pass each step's own start and finish timestamps from the Dagu REST data into the block it emits (the field already exists and is ignored), and make `run.sh` use them for a step's duration when present, falling back to the current wall-clock logic for scripts that do not provide them.
2. Keep the wall-clock logic for every other profile unchanged (they run steps one after another, where it is correct).
3. Add a test for the duration calculation in `scripts/activity-monitor/run.test.sh`, including two parallel steps finishing together.
4. Check that the per-step history file used for stall detection records the new values.

## Related

- [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md) — its final verification reads the real per-stage numbers of the CI run
- `scripts/activity-monitor/README.md` and `scripts/ci/README.md` — the documentation of the monitor and the pipeline

## Operational notes
- token_cost_review: n/a
- token_cost_research: 68752
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
