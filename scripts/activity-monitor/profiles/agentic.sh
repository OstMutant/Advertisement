#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Shared profile for every script already on scripts/utils/agentic-output.sh's
#   structured contract (AGENTIC_SUCCESS_BLOCK/AGENTIC_ERROR_BLOCK JSON) -- one profile file, not
#   one per script, since they all emit the exact same shape; only per-script step *labels* differ
#   (run.sh's own STEP_LABELS/STEP_IS_CONTAINER maps carry that difference, not this file).
#   Deliberately NOT the "one unrecognized line sends the whole batch to the model" rule the
#   original ci-profile design used -- for scripts on this contract, the structured markers ARE the
#   signal; free-text lines between them are exactly the noise this project exists to filter, so
#   they're dropped silently instead of triggering a model call. Also recognises
#   AGENTIC_SKIP_BLOCK, a third marker only scripts/ci/dagu-rest-run-monitor.py emits today (a Dagu
#   step whose own precondition wasn't met, e.g. `--unit`-only run leaving `integration`/`e2e`/
#   `sonar` genuinely un-run) -- not part of scripts/utils/agentic-output.sh's own two-block bash
#   contract since no other script needs a third state, but the same JSON shape either way.
# Usage: sourced by run.sh once a script's SCRIPT_PROFILE_MAP entry resolves to "agentic".
#   Defines try_mechanical_format(), called by run.sh's process_batch() once per batch.
# Uses: sed only -- deliberately not jq. AGENTIC_SUCCESS_BLOCK/ERROR_BLOCK/SKIP_BLOCK's JSON shape
#   is fixed and produced entirely by this repo's own code (flat, known field names, no nesting) --
#   confirmed directly that jq isn't installed in this sandbox and isn't a dependency of any other
#   script in this repo either, so a full JSON parser would be a new, unjustified dependency for a
#   shape this narrow; targeted sed field-extraction is safe here specifically because the producer
#   is our own code, not arbitrary external JSON.
# Env: none of its own -- reads run.sh's CURRENT_SCRIPT_NAME/STEP_LABELS/STEP_IS_CONTAINER/RAW_LOG.
# Input: a redacted text batch (possibly multiple lines) from run.sh.
# Outputs: calls run.sh's mark_step() for every recognised AGENTIC_SUCCESS_BLOCK/ERROR_BLOCK/
#   SKIP_BLOCK line; silently ignores every other line in the batch.
# Returns: 0 always -- this profile never falls back to the model, by design (see Description).
# ────────────────────────────────────────────────────────────────────────────

# Extracts one string field's value from this repo's own fixed-shape agentic JSON. Not a general
# JSON parser -- relies on the producer (emit_agentic_success_block/error_block) always emitting
# unnested, unescaped string values.
extract_agentic_field() {
  local json="$1" field="$2"
  printf '%s' "$json" | sed -n "s/.*\"${field}\":\"\([^\"]*\)\".*/\1/p"
}

try_mechanical_format() {
  local chunk="$1"
  local line

  while IFS= read -r line; do
    [[ -z "$line" ]] && continue

    case "$line" in
      AGENTIC_SUCCESS_BLOCK:*)
        local json="${line#AGENTIC_SUCCESS_BLOCK: }"
        local step
        step="$(extract_agentic_field "$json" "currentStep")"
        [[ -z "$step" ]] && continue
        mark_step "$step" "ok" "" ""
        ;;
      AGENTIC_ERROR_BLOCK:*)
        local json="${line#AGENTIC_ERROR_BLOCK: }"
        local step description pointer container
        step="$(extract_agentic_field "$json" "currentStep")"
        description="$(extract_agentic_field "$json" "description")"
        [[ -z "$step" ]] && continue
        container="${STEP_IS_CONTAINER["$CURRENT_SCRIPT_NAME:$step"]:-}"
        if [[ -n "$container" ]]; then
          pointer="docker logs $container"
        else
          pointer="$RAW_LOG"
        fi
        mark_step "$step" "error" "$description" "$pointer"
        ;;
      AGENTIC_SKIP_BLOCK:*)
        local json="${line#AGENTIC_SKIP_BLOCK: }"
        local step
        step="$(extract_agentic_field "$json" "currentStep")"
        [[ -z "$step" ]] && continue
        mark_step "$step" "skipped" "" ""
        ;;
      *)
        continue
        ;;
    esac
  done <<< "$chunk"

  return 0
}
