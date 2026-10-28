#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Invariant tests for the activity-monitor engine (run.sh) and its shared "agentic"
#   profile. Plain bash + fake `curl`/`jq` shell functions -- no test framework dependency,
#   proportionate to a small tool, and no real jq install required (confirmed jq isn't installed in
#   this sandbox and isn't a dependency of any other script in this repo -- see profiles/agentic.sh's
#   own header). Covers: redact, bound, step-state/tree rendering, the agentic profile's mechanical
#   marker parsing, container-vs-script-log pointer selection, idle/mechanical cost control,
#   model-path call count, visible-on-failure, and real spawn/exit-code passthrough end-to-end (not
#   just unit-level function calls).
# Usage: bash scripts/activity-monitor/run.test.sh
# Uses: bash 4+, fake `curl`/`jq` shell functions (real jq not required to run this suite).
# Env: none required (ANTHROPIC_API_KEY is set to a dummy value for the model-path tests).
# Input: none.
# Outputs: PASS/FAIL lines to stdout; a final "passed: N, failed: N" summary.
# Returns: 0 if every assertion passed, 1 otherwise.
# ────────────────────────────────────────────────────────────────────────────
set -uo pipefail

PASS=0
FAIL=0

assert() {
  local desc="$1" condition="$2"
  if eval "$condition"; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    echo "FAIL: $desc"
  fi
}

# --- fake curl: records call count + full args, returns a canned response -------------------
FAKE_CURL_LOG="$(mktemp)"
FAKE_CURL_LAST_ARGS="$(mktemp)"
FAKE_CURL_MODE="ok"   # ok | fail
curl() {
  echo "called" >> "$FAKE_CURL_LOG"
  printf '%s\n' "$@" > "$FAKE_CURL_LAST_ARGS"
  case "$FAKE_CURL_MODE" in
    ok)   echo '{"content":[{"text":"synthetic status line"}]}' ;;
    fail) echo '' ;;
  esac
}
export -f curl
call_count() { wc -l < "$FAKE_CURL_LOG" | tr -d ' '; }
reset_curl_log() { : > "$FAKE_CURL_LOG"; : > "$FAKE_CURL_LAST_ARGS"; }

# --- fake jq: handles narrate_with_model's two exact call shapes only (payload build via
# `jq -n --arg ...`, response parsing via `jq -r '.content[0].text // empty'`) -- not a general
# JSON tool. `command -v jq` finds this exported function even with no real jq binary present,
# satisfying run.sh's own availability guard the same way a real install would.
# ------------------------------------------------------------------------------------------------
jq() {
  if [[ "${1:-}" == "-n" ]]; then
    local text="" args=("$@") i
    for ((i = 0; i < ${#args[@]}; i++)); do
      if [[ "${args[$i]}" == "--arg" && "${args[$((i+1))]}" == "text" ]]; then
        text="${args[$((i+2))]}"
      fi
    done
    printf '{"messages":[{"role":"user","content":"%s"}]}' "$text"
  elif [[ "${1:-}" == "-r" ]]; then
    local input
    input="$(cat)"
    if [[ "$input" == *'"text":"'* ]]; then
      printf '%s' "$input" | sed -n 's/.*"text":"\([^"]*\)".*/\1/p'
    fi
  fi
}
export -f jq

# --- load the engine under test -----------------------------------------------------------------
SELF_DIR_TEST="$(cd "$(dirname "$0")" && pwd)"
export ANTHROPIC_API_KEY="test-key-not-real"
# shellcheck source=/dev/null
source "$SELF_DIR_TEST/run.sh"
set +e   # run.sh sets -uo pipefail; restore normal control flow here

# Unit-level tests below call process_batch/try_mechanical_format directly, without going through
# main() -- flush_tree() (called by process_batch) needs WORK_DIR set, same as a real run would
# have it set by the time any batch is processed.
WORK_DIR="$(mktemp -d)"

# 1. redact — secret cannot reach the model --------------------------------------------------
secret_line="Authorization: Bearer sk-ant-supersecrettoken123"
redacted="$(printf '%s' "$secret_line" | redact)"
assert "redact removes the bearer token" '[[ "$redacted" != *"supersecrettoken123"* ]]'
assert "redact leaves a marker in its place" '[[ "$redacted" == *"[REDACTED]"* ]]'

# 2. bound — payload never exceeds the stated limit --------------------------------------------
big_chunk="$(for i in $(seq 1 500); do echo "line $i of noisy unrecognized output"; done)"
MAX_LINES=50
MAX_BYTES=2000
bounded="$(bound_chunk "$big_chunk")"
byte_len="$(printf '%s' "$bounded" | wc -c)"
line_len="$(printf '%s\n' "$bounded" | wc -l)"
assert "bound_chunk respects MAX_BYTES" '(( byte_len <= 2000 ))'
assert "bound_chunk respects MAX_LINES" '(( line_len <= 50 ))'
MAX_LINES=120
MAX_BYTES=12000

# 3. step-state / render_tree (a script name outside SCRIPT_STEP_SEQUENCE, so this exercises the
# plain fallback rendering path, not sequence/timing logic -- that's tests 12-14 below) -----------
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=()
CURRENT_SCRIPT_NAME="unmapped-test-script.sh"
WRAPPER_START_TIME="$(date +%s)"
mark_step "build" "ok" "" ""
mark_step "deploy" "running" "" ""
tree_out="$(render_tree)"
assert "render_tree shows ok step with checkmark" '[[ "$tree_out" == *"✅ build"* ]]'
assert "render_tree shows running step with hourglass" '[[ "$tree_out" == *"⏳ deploy"* ]]'
mark_step "build" "error" "boom" "/tmp/somelog"
tree_out="$(render_tree)"
assert "mark_step updates an existing step in place, not duplicated" '(( ${#STEP_ORDER[@]} == 2 ))'
assert "render_tree shows the updated error state" '[[ "$tree_out" == *"❌ build"*"— boom"* ]]'
assert "render_tree shows the pointer line" '[[ "$tree_out" == *"details: /tmp/somelog"* ]]'

# 4. agentic profile — mechanical marker parsing, no model call, keyed by raw step id -----------
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=()
CURRENT_SCRIPT_NAME="deploy-and-run.sh"
RAW_LOG="/tmp/fake-raw.log"
load_profile "agentic"
reset_curl_log
try_mechanical_format 'AGENTIC_SUCCESS_BLOCK: {"status":"success","currentStep":"infra","durationSeconds":3}'
assert "agentic profile recognizes a success marker" '[[ "${STEP_STATUS[infra]:-}" == "ok" ]]'
assert "agentic profile makes no model call for a recognized marker" '(( $(call_count) == 0 ))'

# 5. container-vs-script-log pointer selection (STEP_IS_CONTAINER now holds the real container
# name, not a boolean) ------------------------------------------------------------------------
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=()
try_mechanical_format 'AGENTIC_ERROR_BLOCK: {"status":"error","errorCategory":"transient","isRetryable":true,"currentStep":"start-container","description":"port already in use","durationSeconds":3}'
assert "container-associated step points at docker logs, not the raw script log" \
  '[[ "${STEP_POINTER[start-container]}" == docker\ logs* ]]'
try_mechanical_format 'AGENTIC_ERROR_BLOCK: {"status":"error","errorCategory":"transient","isRetryable":true,"currentStep":"infra","description":"db unreachable","durationSeconds":1}'
assert "non-container step points at the raw script log" \
  '[[ "${STEP_POINTER[infra]}" == "/tmp/fake-raw.log" ]]'

# 6. generic profile — idle batch makes no API call -----------------------------------------------
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); LAST_ACTIVITY=""
CURRENT_SCRIPT_NAME="unmapped-test-script.sh"
unset -f try_mechanical_format
reset_curl_log
process_batch ""
assert "idle batch makes no API call" '(( $(call_count) == 0 ))'

# 7. generic profile — unrecognized content calls the model exactly once ---------------------------
reset_curl_log
FAKE_CURL_MODE=ok
process_batch "some totally unrecognized diagnostic spew"
assert "unrecognized batch (no profile) calls the model exactly once" '(( $(call_count) == 1 ))'
assert "narrated line lands in LAST_ACTIVITY" '[[ "$LAST_ACTIVITY" == "synthetic status line" ]]'

# 8. model failure — visible, not silent -----------------------------------------------------------
reset_curl_log
FAKE_CURL_MODE=fail
LAST_ACTIVITY=""
process_batch "some totally unrecognized diagnostic spew"
assert "model failure sets a visible LAST_ACTIVITY marker" '[[ "$LAST_ACTIVITY" == *"narrator error"* ]]'
FAKE_CURL_MODE=ok

# 9. end-to-end — a real secret in the log never reaches the model payload ------------------------
reset_curl_log
FAKE_CURL_MODE=ok
process_batch "Authorization: Bearer sk-ant-supersecrettoken123
some unrecognized diagnostic line, so this batch falls through to the model"
assert "secret is absent from the actual curl payload" \
  '! grep -q "supersecrettoken123" "$FAKE_CURL_LAST_ARGS"'
assert "redaction marker is present in the actual curl payload instead" \
  'grep -q "REDACTED" "$FAKE_CURL_LAST_ARGS"'

# 10. real spawn + exit-code passthrough, end to end (not a unit-level function call) ---------------
FAKE_SCRIPT="$(mktemp)"
cat > "$FAKE_SCRIPT" <<'EOF'
#!/bin/bash
echo "doing work"
sleep 1
exit 0
EOF
chmod +x "$FAKE_SCRIPT"
rm -rf "/tmp/activity-monitor/$(basename "$FAKE_SCRIPT")"
BATCH_SECONDS=1 bash "$SELF_DIR_TEST/run.sh" --profile generic -- "$FAKE_SCRIPT" > /dev/null 2>&1
real_exit=$?
assert "wrapped command's real exit code (0) passes through" '(( real_exit == 0 ))'

FAKE_SCRIPT_FAIL="$(mktemp)"
cat > "$FAKE_SCRIPT_FAIL" <<'EOF'
#!/bin/bash
echo "about to fail"
exit 7
EOF
chmod +x "$FAKE_SCRIPT_FAIL"
rm -rf "/tmp/activity-monitor/$(basename "$FAKE_SCRIPT_FAIL")"
BATCH_SECONDS=1 bash "$SELF_DIR_TEST/run.sh" --profile generic -- "$FAKE_SCRIPT_FAIL" > /dev/null 2>&1
real_exit=$?
assert "wrapped command's real non-zero exit code (7) passes through unchanged" '(( real_exit == 7 ))'
assert "exit_code file matches the real exit code" \
  '[[ "$(cat "/tmp/activity-monitor/$(basename "$FAKE_SCRIPT_FAIL")/exit_code")" == "7" ]]'
rm -f "$FAKE_SCRIPT" "$FAKE_SCRIPT_FAIL"
rm -rf "/tmp/activity-monitor/$(basename "$FAKE_SCRIPT")" "/tmp/activity-monitor/$(basename "$FAKE_SCRIPT_FAIL")"

# 11. interpreter-prefixed command (`bash <script>`) resolves the script name from the real
# script, not from "bash" -- this project's own scripts are invoked this way everywhere (e.g.
# `bash scripts/deploy-and-run.sh`), including cases where the target file has no +x bit set.
FAKE_SCRIPT_NOEXEC="$(mktemp --suffix=-noexec-target.sh)"
cat > "$FAKE_SCRIPT_NOEXEC" <<'EOF'
#!/bin/bash
echo "ran without its own +x bit"
exit 0
EOF
chmod -x "$FAKE_SCRIPT_NOEXEC"
run_name="$(basename "$FAKE_SCRIPT_NOEXEC")"
rm -rf "/tmp/activity-monitor/$run_name"
BATCH_SECONDS=1 bash "$SELF_DIR_TEST/run.sh" --profile generic -- bash "$FAKE_SCRIPT_NOEXEC" > /dev/null 2>&1
real_exit=$?
assert "bash-prefixed non-executable target still runs and exits 0" '(( real_exit == 0 ))'
assert "work dir is named after the real script, not 'bash'" '[[ -d "/tmp/activity-monitor/$run_name" ]]'
rm -f "$FAKE_SCRIPT_NOEXEC"
rm -rf "/tmp/activity-monitor/$run_name" "/tmp/activity-monitor/bash"

# 12. sequence-based rendering — completed step shows a real duration, current step shows a live
# elapsed counter, future steps stay pending with no timer ------------------------------------
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=()
CURRENT_SCRIPT_NAME="deploy-and-run.sh"
WORK_DIR="$(mktemp -d)"
WRAPPER_START_TIME=$(( $(date +%s) - 10 ))
mark_step "infra" "ok" "" ""
STEP_COMPLETED_AT[infra]=$(( WRAPPER_START_TIME + 4 ))
LAST_ACTIVITY_AT="$(date +%s)"
tree_out="$(render_tree)"
assert "completed step shows a real duration, not 0s" '[[ "$tree_out" == *"✅ Infra (DB/SeaweedFS) (4s)"* ]]'
assert "next undeclared step shows as running with a live elapsed counter" '[[ "$tree_out" == *"⏳ Build (running"* ]]'
assert "a step further out in the sequence stays pending, no timer" '[[ "$tree_out" == *"⬜ Start container"* && "$tree_out" != *"⬜ Start container (running"* ]]'
rm -rf "$WORK_DIR"

# 13. adaptive stall threshold — no history falls back to the constant; history present raises
# the effective threshold instead of using the flat default -----------------------------------
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=()
CURRENT_SCRIPT_NAME="deploy-and-run.sh"
WORK_DIR="$(mktemp -d)"
no_history_threshold="$(get_stall_threshold "infra")"
assert "no history yet falls back to the flat/global threshold" '(( no_history_threshold == STALL_THRESHOLD_SECONDS ))'
printf 'infra\t500\n' > "$WORK_DIR/history.tsv"
adaptive_threshold="$(get_stall_threshold "infra")"
assert "a past 500s run raises the effective threshold to 2x that (1000), well past the flat default" '(( adaptive_threshold == 1000 ))'
rm -rf "$WORK_DIR"

# 14. container-state warning — running container is silent, a stopped/missing one warns --------
assert "a container that genuinely doesn't exist warns, doesn't silently pass" \
  '[[ "$(container_state_warning "definitely-not-a-real-container-name-xyz")" == *"⚠️"* ]]'

# 15. playwright live tally — track_playwright_total/track_playwright_test_line parse Playwright's
# own list-reporter lines, and the tally shows up in a rendered running step ------------------
PW_TOTAL=0; PW_PASSED=0; PW_FAILED=0; PW_LAST_SPEC=""
track_playwright_total "Running 63 tests using 4 workers"
assert "track_playwright_total parses the real test count" '(( PW_TOTAL == 63 ))'
track_playwright_test_line "  ✓  1 05-marketplace-advertisement-flow.spec.js:12:3 › userEn creates advertisement (2.1s)"
assert "a ✓ result line increments PW_PASSED" '(( PW_PASSED == 1 ))'
assert "a ✓ result line records the finished spec's basename" \
  '[[ "$PW_LAST_SPEC" == "05-marketplace-advertisement-flow.spec.js" ]]'
track_playwright_test_line "  ✘  2 06-seed-filter-sort-pagination.spec.js:40:3 › some flaky check (1.4s)"
assert "a ✘ result line increments PW_FAILED, leaves PW_PASSED unchanged" \
  '(( PW_FAILED == 1 && PW_PASSED == 1 ))'

STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=()
CURRENT_SCRIPT_NAME="playwright.sh"
WORK_DIR="$(mktemp -d)"
WRAPPER_START_TIME=$(( $(date +%s) - 5 ))
mark_step "playwright-run" "running" "" ""
tree_out="$(render_tree)"
assert "a running playwright-run step shows the live pass/fail tally in its description" \
  '[[ "$tree_out" == *"1/63 passed, 1 failed"* ]]'
assert "the tally names the most recently finished spec" \
  '[[ "$tree_out" == *"last: 06-seed-filter-sort-pagination.spec.js"* ]]'
rm -rf "$WORK_DIR"

# 16. playwright tally dispatch is gated to playwright.sh only -- the same lines through another
# script's own batch must not touch PW_* state ---------------------------------------------------
PW_TOTAL=0; PW_PASSED=0; PW_FAILED=0; PW_LAST_SPEC=""
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=()
CURRENT_SCRIPT_NAME="deploy-and-run.sh"
RAW_LOG="/tmp/fake-raw.log"
load_profile "agentic"
try_mechanical_format "Running 63 tests using 4 workers"
try_mechanical_format "  ✓  1 some.spec.js:1:1 › whatever (1s)"
assert "the live tally is never touched for a script other than playwright.sh" \
  '(( PW_TOTAL == 0 && PW_PASSED == 0 ))'

# 17. real per-step duration from the producer -- parser reads stepDurationSeconds when present,
# leaves it unset when absent, and the parallel-group render and the fallback both show the right value
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); STEP_DURATION=()
CURRENT_SCRIPT_NAME="ci.sh"
try_mechanical_format 'AGENTIC_SUCCESS_BLOCK: {"status":"success","currentStep":"unit","durationSeconds":900,"stepDurationSeconds":261}'
assert "a success block's stepDurationSeconds is recorded as the step's real duration" '(( STEP_DURATION[unit] == 261 ))'
try_mechanical_format 'AGENTIC_SUCCESS_BLOCK: {"status":"success","currentStep":"integration","durationSeconds":900}'
assert "a success block without stepDurationSeconds leaves the real duration unset" \
  '[[ -z "${STEP_DURATION[integration]:-}" ]]'

STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); STEP_DURATION=()
CURRENT_SCRIPT_NAME="ci.sh"
WORK_DIR="$(mktemp -d)"
WRAPPER_START_TIME=$(( $(date +%s) - 1500 ))
mark_step "unit" "ok" "" "" 261
mark_step "e2e" "ok" "" "" 1441
STEP_COMPLETED_AT[unit]=$(( WRAPPER_START_TIME + 1450 ))
STEP_COMPLETED_AT[e2e]=$(( WRAPPER_START_TIME + 1450 ))
tree_out="$(render_tree)"
assert "two parallel steps finishing together each show their own real duration (4m21s)" '[[ "$tree_out" == *"(4m21s)"* ]]'
assert "two parallel steps finishing together each show their own real duration (24m1s)" '[[ "$tree_out" == *"(24m1s)"* ]]'
assert "parallel steps with real durations never show a misleading (0s)" '[[ "$tree_out" != *"(0s)"* ]]'
rm -rf "$WORK_DIR"

STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); STEP_DURATION=()
CURRENT_SCRIPT_NAME="deploy-and-run.sh"
WORK_DIR="$(mktemp -d)"
WRAPPER_START_TIME=$(( $(date +%s) - 10 ))
mark_step "infra" "ok" "" ""
STEP_COMPLETED_AT[infra]=$(( WRAPPER_START_TIME + 4 ))
tree_out="$(render_tree)"
assert "a step without a real duration keeps the wall-clock value (4s)" '[[ "$tree_out" == *"✅ Infra (DB/SeaweedFS) (4s)"* ]]'
rm -rf "$WORK_DIR"

STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); STEP_DURATION=()
CURRENT_SCRIPT_NAME="ci.sh"
WORK_DIR="$(mktemp -d)"
WRAPPER_START_TIME=$(( $(date +%s) - 1500 ))
mark_step "unit" "ok" "" "" 261
mark_step "e2e" "ok" "" "" 1441
STEP_COMPLETED_AT[unit]=$(( WRAPPER_START_TIME + 1450 ))
STEP_COMPLETED_AT[e2e]=$(( WRAPPER_START_TIME + 1450 ))
SCRIPT_STEP_SEQUENCE[ci.sh]="unit e2e"
record_step_history
unset 'SCRIPT_STEP_SEQUENCE[ci.sh]'
history_unit_line="$(printf 'unit\t261')"
history_e2e_line="$(printf 'e2e\t1441')"
assert "history records the real unit duration, not the parallel-skewed baseline" \
  'grep -qxF "$history_unit_line" "$WORK_DIR/history.tsv"'
assert "history records the real e2e duration" \
  'grep -qxF "$history_e2e_line" "$WORK_DIR/history.tsv"'
rm -rf "$WORK_DIR"

# 18. a finished member of a still-running parallel group keeps its real status icon
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); STEP_DURATION=()
CURRENT_SCRIPT_NAME="ci.sh"
WORK_DIR="$(mktemp -d)"
WRAPPER_START_TIME=$(( $(date +%s) - 100 ))
mark_step "unit" "ok" "" "" 61
tree_out="$(render_tree)"
assert "a finished parallel member shows its done icon and duration while siblings still run" \
  '[[ "$tree_out" == *"✅ Unit tests (1m1s)"* ]]'
rm -rf "$WORK_DIR"

# 19. a skipped step carries its own real duration instead of the gap since the previous completion
STEP_ORDER=(); STEP_STATUS=(); STEP_REASON=(); STEP_POINTER=(); STEP_COMPLETED_AT=(); STEP_DURATION=()
CURRENT_SCRIPT_NAME="ci.sh"
WORK_DIR="$(mktemp -d)"
WRAPPER_START_TIME=$(( $(date +%s) - 500 ))
try_mechanical_format 'AGENTIC_SKIP_BLOCK: {"status":"skipped","currentStep":"e2e","durationSeconds":400,"stepDurationSeconds":0}'
STEP_COMPLETED_AT[e2e]=$(( WRAPPER_START_TIME + 434 ))
tree_out="$(render_tree)"
assert "a skipped step shows its own real duration (0s), not the wall-clock gap" \
  '[[ "$tree_out" == *"⏭️ E2E (Playwright) (0s)"* ]]'
rm -rf "$WORK_DIR"

echo ""
echo "passed: $PASS, failed: $FAIL"
rm -f "$FAKE_CURL_LOG" "$FAKE_CURL_LAST_ARGS"
rm -rf "$WORK_DIR"
[[ $FAIL -eq 0 ]]
