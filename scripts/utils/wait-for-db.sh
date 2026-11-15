#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Waits until a Postgres container accepts connections, and stops with the container's
#   last log lines as soon as the container is no longer running or the time limit passes, so a
#   database that died at start (for example a volume written by another PostgreSQL major version)
#   ends the calling script instead of leaving it waiting forever.
# Usage: source scripts/utils/wait-for-db.sh; wait_for_db_ready <container> <db_user> <db_name> [timeout_seconds]
#   timeout_seconds defaults to 120.
# Uses: bash, docker.
# Env: None.
# Input: the named container's state and logs.
# Outputs: on failure, to stderr: the reason, the last 30 log lines of the container and, when the
#   container is not running, the hint to run with --reset; nothing on success.
# Returns: 0 as soon as pg_isready succeeds, 1 when the container is not running or the time limit
#   passes (when sourced under `set -e`, the calling script stops there).
# ────────────────────────────────────────────────────────────────────────────

#######################################
# Waits for a Postgres container to accept connections.
# Arguments: container name, database user, database name, optional time limit in seconds (default 120).
# Outputs: failure reason, container log tail and the --reset hint to stderr.
# Returns: 0 when ready, 1 when the container is not running or the time limit passes.
#######################################
wait_for_db_ready() {
  local container="$1" db_user="$2" db_name="$3" timeout="${4:-120}"
  local end=$((SECONDS + timeout))
  until docker exec "$container" pg_isready -U "$db_user" -d "$db_name" -q 2>/dev/null; do
    if [ "$(docker container inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" != "true" ]; then
      echo "DB container $container is not running; its last log lines:" >&2
      docker logs --tail 30 "$container" >&2 || true
      echo "A volume written by another PostgreSQL major version is unusable: run with --reset." >&2
      return 1
    fi
    if [ "$SECONDS" -ge "$end" ]; then
      echo "DB container $container did not accept connections within ${timeout}s; its last log lines:" >&2
      docker logs --tail 30 "$container" >&2 || true
      return 1
    fi
    sleep 1
  done
}
