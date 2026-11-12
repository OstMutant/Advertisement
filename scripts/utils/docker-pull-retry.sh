#!/usr/bin/env bash
# ── Header ────────────────────────────────────────────────
# Description: Retries a Docker pull command up to 3 times, 5 s apart, so a single transient
#   registry or layer-extraction failure does not abort the calling script.
# Usage: source scripts/utils/docker-pull-retry.sh; docker_pull_retry docker pull <image>
#   or docker_pull_retry docker compose -f <file> pull -q
# Uses: bash, docker.
# Env: None.
# Input: None.
# Outputs: the command's own output, plus a stderr line per failed attempt.
# Returns: 0 as soon as one attempt succeeds, 1 after the third failure (when sourced under
#   `set -e`, the calling script stops there).
# ────────────────────────────────────────────────────────

#######################################
# Runs the given Docker pull command, retrying up to 3 times with a 5 s pause between attempts.
# Arguments: the pull command and its arguments, e.g. docker pull <image>.
# Outputs: the command's own output, plus a stderr line per failed attempt.
# Returns: 0 on the first successful attempt, 1 after the third failure.
#######################################
docker_pull_retry() {
  local attempt
  for attempt in 1 2 3; do
    "$@" && return 0
    echo "Pull failed (attempt $attempt/3)." >&2
    [ "$attempt" -lt 3 ] && sleep 5
  done
  return 1
}
