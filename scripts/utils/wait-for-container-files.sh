#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Confirms a container's own output actually landed on the host disk (checked as
#   plain host filesystem paths, after the caller's own `docker cp` already ran -- not via `docker
#   exec`, which only works against a running container and fails outright on a one-shot build
#   container that has already exited by this point) before the caller tears the container down --
#   instead of either always removing it (trusting an unverified copy) or always keeping it (paying
#   the resource cost even on success). Source this file, call the function you need.
# Usage: source scripts/utils/wait-for-container-files.sh; then call
#   wait_for_container_files_or_keep.
# Uses: bash, docker (only for the final `docker rm -f` -- the file check itself is a plain host
#   filesystem test, works the same whether the container is still running or already exited).
# Env: None.
# Input: None.
# Outputs: on confirmed success, none beyond removing the container. On timeout, an
#   "ERROR: ... left running for inspection" line to stderr naming the container and the missing
#   file(s).
# Returns: 0 always when sourced (see the function's own Returns below).
# ────────────────────────────────────────────────────────────────────────────

#######################################
# Polls the host filesystem once a second for a set of expected file paths (the caller's own
# `docker cp` must already have run before calling this), removing the named container once all
# are present (non-empty), or leaving it untouched, for inspection, if the timeout elapses first.
# Arguments: $1 = container name (only used for the final `docker rm -f` and the ERROR message),
#   $2 = timeout in seconds, $3.. = one or more host filesystem paths to check for.
# Outputs: on timeout, an "ERROR: ... left running for inspection" line to stderr naming the
#   container and the missing file(s).
# Returns: 0 if every file was confirmed present within the timeout (container removed); 1 if the
#   timeout elapsed with at least one file still missing (container left running).
#######################################
wait_for_container_files_or_keep() {
  local container="$1"
  local timeout="$2"
  shift 2
  local files=("$@")
  local elapsed=0
  local missing=()

  while [ "$elapsed" -lt "$timeout" ]; do
    missing=()
    local f
    for f in "${files[@]}"; do
      [ -s "$f" ] || missing+=("$f")
    done
    if [ "${#missing[@]}" -eq 0 ]; then
      docker rm -f "$container" >/dev/null 2>&1 || true
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  echo "ERROR: expected file(s) not found on host after ${timeout}s -- container '$container' left running for inspection: ${missing[*]}" >&2
  return 1
}
