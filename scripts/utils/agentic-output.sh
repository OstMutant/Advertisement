#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Emits a single-line JSON status marker for a script's own execution result --
#   AGENTIC_SUCCESS_BLOCK on a clean finish, AGENTIC_ERROR_BLOCK on failure -- so an AI agent
#   reading raw script output can parse machine-readable status/category/duration instead of
#   scraping free-text log lines. Category/isRetryable taxonomy (transient/validation/business/
#   permission) is not invented -- source this file, call the function you need.
# Usage: source scripts/utils/agentic-output.sh; set `SECONDS=0` as the caller's own first
#   executable line (bash's own elapsed-seconds counter -- both emit functions read it directly,
#   no duration argument to pass); then call emit_agentic_success_block / emit_agentic_error_block.
# Uses: bash.
# Env: None.
# Input: None.
# Outputs: one JSON line to stdout per call.
# Returns: 0 always.
# ────────────────────────────────────────────────────────────────────────────

#######################################
# Emits AGENTIC_SUCCESS_BLOCK: a single-line JSON marker confirming the calling script's own run
# finished without error.
# Globals: SECONDS (bash's own elapsed-seconds counter -- read, not modified; the caller must set
#   SECONDS=0 as its own first executable line for this to reflect its real total runtime).
# Arguments:
#   $1 - current_step: name of the last step that completed (free text, no literal double quotes).
# Outputs: one JSON line to stdout, prefixed "AGENTIC_SUCCESS_BLOCK: ".
# Returns: 0 always.
#######################################
emit_agentic_success_block() {
  local current_step="$1"
  echo "AGENTIC_SUCCESS_BLOCK: {\"status\":\"success\",\"currentStep\":\"$current_step\",\"durationSeconds\":$SECONDS}"
}

#######################################
# Emits AGENTIC_ERROR_BLOCK: a single-line JSON marker categorising a script failure so an AI
# agent can decide whether to retry.
# Globals: SECONDS (bash's own elapsed-seconds counter -- read, not modified; the caller must set
#   SECONDS=0 as its own first executable line for this to reflect its real total runtime).
# Arguments:
#   $1 - category: one of transient|validation|business|permission.
#   $2 - is_retryable: "true" or "false" (emitted as an unquoted JSON boolean).
#   $3 - current_step: name of the step that was running when the failure occurred.
#   $4 - description: human-readable explanation (free text, no literal double quotes).
# Outputs: one JSON line to stdout, prefixed "AGENTIC_ERROR_BLOCK: ".
# Returns: 0 always -- the caller's own trap/exit still carries the real exit code.
#######################################
emit_agentic_error_block() {
  local category="$1" is_retryable="$2" current_step="$3" description="$4"
  echo "AGENTIC_ERROR_BLOCK: {\"status\":\"error\",\"errorCategory\":\"$category\",\"isRetryable\":$is_retryable,\"currentStep\":\"$current_step\",\"description\":\"$description\",\"durationSeconds\":$SECONDS}"
}
