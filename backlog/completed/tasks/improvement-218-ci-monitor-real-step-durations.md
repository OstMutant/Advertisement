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

## Plan (done 2026-10-28)

1. `scripts/ci/dagu-rest-run-monitor.py`: for a succeeded or failed step, compute `stepDurationSeconds` = Dagu node `finishedAt` − `startedAt` (parsed ISO timestamps; omitted when either is missing) and add it to the emitted success/error block. `durationSeconds` (run-wide elapsed) stays as is.
2. `scripts/activity-monitor/profiles/agentic.sh`: extract the numeric `stepDurationSeconds` field and pass it to `mark_step` as a 5th argument.
3. `scripts/activity-monitor/run.sh`: new `STEP_DURATION` map set by `mark_step` when given; `render_step_line` shows `STEP_DURATION[id]` when present, else the existing completion-minus-baseline; `record_step_history` writes `STEP_DURATION[id]` when present, else the baseline difference (parallel steps no longer break the history chain).
4. `scripts/activity-monitor/run.test.sh`: two parallel steps completing in the same second with different real durations render their own durations; a step without a real duration keeps the wall-clock value; history records the real value.
5. Documentation: update the headers of the three touched files; `scripts/activity-monitor/README.md` only if it describes the marker fields.
6. Verification: run `run.test.sh`; check the Dagu node field names against a live run's REST output (no run exists in Dagu right now, so a short `bash scripts/ci.sh --unit-only`-style run is needed).

## Detailed implementation spec (for a Haiku executor; every edit is exact)

Rules for the executor: touch only the files named below; comments are one line or none; no task numbers in comments; English only; after the edits run `bash scripts/activity-monitor/run.test.sh` and `bash -n` on both `.sh` files, `python3 -m py_compile scripts/ci/dagu-rest-run-monitor.py`, and report the output verbatim. Do not commit.

### Edit 1 — `scripts/ci/dagu-rest-run-monitor.py`

a) Add `import re` and `from datetime import datetime` to the import block (keep alphabetical: `json, os, re, sys, time, urllib.error, urllib.request`, then `from datetime import datetime` after them).

b) Directly above `def emit_agentic_success_block`, replace the 4-line comment that says durationSeconds "is carried for shape-completeness ... computed by activity-monitor/run.sh itself from its own wall-clock timestamps" with one line: `# stepDurationSeconds (optional) is the step's own real duration from Dagu's startedAt/finishedAt; run.sh uses it instead of its wall-clock estimate.` Keep the first two lines of that comment unchanged.

c) Add this function before the comment block of `emit_agentic_success_block`:

```python
def step_duration_seconds(node):
    """Real duration of a Dagu node from its startedAt/finishedAt, or None when either is unset."""
    try:
        started, finished = (
            datetime.fromisoformat(re.sub(r"(\.\d{6})\d+", r"\1", node[key]).replace("Z", "+00:00"))
            for key in ("startedAt", "finishedAt")
        )
    except (KeyError, ValueError, TypeError):
        return None
    return max(0, int((finished - started).total_seconds()))
```

d) Change the two emit functions to take an optional `step_seconds=None` second/third parameter and append the field:
- `emit_agentic_success_block(step, step_seconds=None)`: build `extra = "" if step_seconds is None else f',"stepDurationSeconds":{step_seconds}'` and put `{extra}` right after `"durationSeconds":{elapsed}` inside the printed JSON.
- `emit_agentic_error_block(step, description, step_seconds=None)`: same `extra`, same position.
- `emit_agentic_skip_block` unchanged.

e) In the polling loop, pass the duration: `emit_agentic_success_block(name, step_duration_seconds(node))` and `emit_agentic_error_block(name, f"...", step_duration_seconds(node))` (keep the existing description string exactly).

f) Header `# Outputs:` paragraph: add one sentence that success/error blocks carry `stepDurationSeconds` when Dagu reports both timestamps.

### Edit 2 — `scripts/activity-monitor/profiles/agentic.sh`

a) Add next to `extract_agentic_field`:

```bash
# Extracts one numeric field's value from this repo's own fixed-shape agentic JSON; empty when absent.
extract_agentic_number() {
  local json="$1" field="$2"
  printf '%s' "$json" | sed -n "s/.*\"${field}\":\([0-9][0-9]*\).*/\1/p"
}
```

b) In the `AGENTIC_SUCCESS_BLOCK` case: after `step=...` line add `local seconds; seconds="$(extract_agentic_number "$json" "stepDurationSeconds")"` and change the call to `mark_step "$step" "ok" "" "" "$seconds"`.
c) In the `AGENTIC_ERROR_BLOCK` case: declare `seconds` with the other locals, extract it the same way, and change the call to `mark_step "$step" "error" "$description" "$pointer" "$seconds"`.
d) `AGENTIC_SKIP_BLOCK` unchanged. Header `# Outputs:` gets one sentence: an optional `stepDurationSeconds` field is passed on to `mark_step`.

### Edit 3 — `scripts/activity-monitor/run.sh`

a) After line `declare -A STEP_COMPLETED_AT=() ...` add:
`declare -A STEP_DURATION=()     # step id -> real duration in seconds supplied by the producer; unset when only wall-clock is known`

b) `mark_step`: first line becomes `local id="$1" status="$2" reason="${3:-}" pointer="${4:-}" duration="${5:-}"`; after `STEP_POINTER["$id"]="$pointer"` add:

```bash
  if [[ -n "$duration" ]]; then
    STEP_DURATION["$id"]="$duration"
  fi
```

c) Add above `render_step_line` (and below `format_duration`):

```bash
# A completed step's duration: the producer's real value when given, else completion time minus the baseline.
step_duration_seconds() {
  local id="$1" baseline="$2"
  echo "${STEP_DURATION[$id]:-$(( STEP_COMPLETED_AT[$id] - baseline ))}"
}
```

d) `render_step_line`: replace `line+=" ($(format_duration $(( STEP_COMPLETED_AT[$id] - baseline ))))"` with `line+=" ($(format_duration "$(step_duration_seconds "$id" "$baseline")"))"`.

e) `record_step_history`: replace the `echo -e` line with `echo -e "${id}\t$(step_duration_seconds "$id" "$baseline")" >> "$WORK_DIR/history.tsv"`. The `baseline=` update line below it stays.

f) Header/comment: the `STEP_COMPLETED_AT` comment block near render_step_line stays; `# $3 = previous step's completion time` comment gets `(fallback when no real duration is known)` appended.

### Edit 4 — `scripts/activity-monitor/run.test.sh`

Append before the final summary (after the last adaptive-threshold assertions), following the file's existing style (reset the arrays first: `STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); STEP_DURATION=()`):

1. Parser test: feed `try_mechanical_format` with `AGENTIC_SUCCESS_BLOCK: {"status":"success","currentStep":"unit","durationSeconds":900,"stepDurationSeconds":261}` for `CURRENT_SCRIPT_NAME="ci.sh"`; assert `STEP_DURATION[unit]` equals `261`.
2. Parser test: a block without the field leaves `STEP_DURATION[integration]` unset (`[[ -z "${STEP_DURATION[integration]:-}" ]]`).
3. Parallel render test: `WRAPPER_START_TIME=$(( $(date +%s) - 1500 ))`; mark `unit` ok with `261`, `e2e` ok with `1441`, both with `STEP_COMPLETED_AT` equal to `WRAPPER_START_TIME + 1450`; assert the tree contains `(4m21s)` and `(24m1s)` and no `(0s)` for those two.
4. Fallback render test: a step marked ok with no duration and `STEP_COMPLETED_AT = WRAPPER_START_TIME + 4` still renders `(4s)`.
5. History test: with `SCRIPT_STEP_SEQUENCE[ci.sh]="unit e2e"` set locally and `WORK_DIR` a fresh temp dir, `record_step_history` writes lines `unit<TAB>261` and `e2e<TAB>1441` to `history.tsv`.
6. Python helper test is out of scope here (checked in Edit 5).

### Edit 5 — verification of the Python helper (no repo file change)

Run `python3 -I -c` importing nothing from the script (it executes on import): instead copy the function body into a one-off check in the scratchpad directory and assert: `{"startedAt":"2026-10-20T10:00:00Z","finishedAt":"2026-10-20T10:04:21.123456789Z"}` gives `261`; a node with `finishedAt` missing or `"-"` gives `None`. Report the output.

### Edit 6 — docs

`scripts/activity-monitor/README.md` and `scripts/ci/README.md`: grep each for `durationSeconds` / "duration"; only if a sentence describes how a step's duration is computed, update it to say the CI profile uses Dagu's own start/finish times and every other profile uses the wall-clock difference. No new sections.

### After Haiku (done by the main thread)

Review `git diff`; run the Dagu field-name check against a live run (needs a separate approval to start `bash scripts/ci.sh`); tick Operational notes and the plan items in this file.

## Related

- [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md) — its final verification reads the real per-stage numbers of the CI run
- `scripts/activity-monitor/README.md` and `scripts/ci/README.md` — the documentation of the monitor and the pipeline

## Operational notes
- token_cost_review: n/a
- token_cost_research: 68752
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: Feature, single module
- context_loading_consulted: yes
- context_loading_matched: no
- flows_situation: verify a tooling change against a live Dagu run
- flows_chosen: activity-monitor.sh wrapping ci.sh --foreground
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 1
- haiku_decomposition_rework_needed: 1 / 1
- adrs_referenced: none

### Agent calls
- implement the exact edit spec | subagent_type=general-purpose | tokens=166723 | tool_uses=33 | duration_s=204 | mode=background | batch=solo

### Script/command runs
- scripts/activity-monitor/run.test.sh | duration_s=n/a | mode=foreground | result=pass (47 assertions)
- scripts/activity-monitor.sh -- bash scripts/ci.sh --foreground --unit --integration --no-docs --no-archunit-metrics --no-lint --no-shellcheck | duration_s=638 | mode=background | result=pass
