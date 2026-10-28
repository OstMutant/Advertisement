#!/usr/bin/env bash
# ── Header ────────────────────────────────────────────────
# Description: Gives one session ID to a chain of scripts that call each other. The outermost
#   script creates it; every script it calls inherits the same value through the environment.
#   Source this file, then call write_session_info after a script's logs are on the host.
# Usage: source scripts/utils/session-id.sh; write_session_info <log_dir> <script_name>
# Uses: bash, date.
# Env: SESSION_ID -- reused when already set by a calling script; otherwise set to the current UTC
#   time as YYYYMMDDHHMMSS and exported.
# Input: None -- the directory and script name are passed as arguments.
# Outputs: <log_dir>/session-info.txt with SESSION_ID, script and timestamp lines.
# Returns: 0 always; an unwritable directory is skipped silently.
# ────────────────────────────────────────────────────────

export SESSION_ID="${SESSION_ID:-$(date -u +%Y%m%d%H%M%S)}"

write_session_info() {
  local dir="$1" script="$2"
  mkdir -p "$dir" 2>/dev/null || return 0
  printf 'SESSION_ID=%s\nscript=%s\ntimestamp=%s\n' "$SESSION_ID" "$script" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    > "$dir/session-info.txt" 2>/dev/null || true
}
