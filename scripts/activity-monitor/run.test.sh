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
assert "completed step shows a real duration, not 0s" '[[ "$tree_out" == *"✅ Infra (DB/MinIO) (4s)"* ]]'
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

echo ""
echo "passed: $PASS, failed: $FAIL"
rm -f "$FAKE_CURL_LOG" "$FAKE_CURL_LAST_ARGS"
rm -rf "$WORK_DIR"
[[ $FAIL -eq 0 ]]
