#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Truncates all application data via the DB container's own psql (docker exec).
#   Called by scripts/deploy-and-run/run.sh (--reset-only-db) and playwright/run.sh instead of each
#   keeping its own inline copy of this truncate logic; also runnable standalone. Neither caller's
#   own orchestration (deploy timing, stop/restart around the truncate) lives here -- this script
#   only does the truncate itself. Auto-starts the DB container (via docker start, or docker
#   compose if none exists at all) if it isn't already running.
# Usage: bash scripts/deploy-and-run/reset.sh [--container <name>]
#   --container <name>   DB container to exec into (default: $DB_CONTAINER env var, or the first
#                         container publishing port 5432 if neither is set)
# Uses: bash, docker, docker compose (only if no DB container exists at all, via
#   scripts/utils/ensure-docker-plugins.sh's ensure_docker_compose).
# Env: DB_CONTAINER (no default -- see Usage above), DB_NAME (experiments), DB_USER
#   (experiments_user) -- all may be exported directly by a caller, falling back to the repo-root
#   .env, then a hardcoded default.
# Input: repo source, .env (DB_NAME/DB_USER fallback defaults), scripts/deploy-and-run/reset-clean.sql.
# Outputs: all application tables truncated (RESTART IDENTITY CASCADE) in the target DB container.
# Returns: 0 on success, non-zero on error (container never became ready, psql failure).
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc): database reset error ==="; exit $_rc' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load shared credential defaults from the repo-root .env into ENV_*-prefixed vars -- not
# exported/sourced directly, so an already-exported override is never clobbered (same pattern as
# scripts/deploy-and-run/run.sh).
if [ -f "$ROOT/.env" ]; then
  while IFS='=' read -r _env_key _env_value; do
    [[ "$_env_key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    printf -v "ENV_$_env_key" '%s' "${_env_value%$'\r'}"
  done < <(grep -v '^\s*#' "$ROOT/.env" | grep -v '^\s*$')
fi

DB_NAME="${DB_NAME:-${ENV_DB_NAME:-experiments}}"
DB_USER="${DB_USER:-${ENV_DB_USER:-experiments_user}}"

CONTAINER="${DB_CONTAINER:-}"
NEXT=""
for arg in "$@"; do
  case "$NEXT" in
    container) CONTAINER="$arg"; NEXT=""; continue ;;
  esac
  case "$arg" in
    --container) NEXT=container ;;
  esac
done

[ -z "$CONTAINER" ] && CONTAINER=$(docker ps -a --filter "publish=5432" --format "{{.Names}}" | head -1)

if [ -n "$CONTAINER" ] && docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  if [ "$(docker inspect -f '{{.State.Status}}' "$CONTAINER")" != "running" ]; then
    echo "Starting $CONTAINER..."
    docker start "$CONTAINER"
    until docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do sleep 1; done
  fi
else
  echo "No DB container found — starting via docker compose..."
  source "$ROOT/scripts/utils/ensure-docker-plugins.sh"
  ensure_docker_compose
  docker compose --project-directory "$ROOT" -f "$ROOT/scripts/deploy-and-run/docker-compose.db.yml" up -d
  CONTAINER=$(docker ps --filter "publish=5432" --format "{{.Names}}" | head -1)
  until docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do sleep 1; done
fi

docker cp "$SCRIPT_DIR/reset-clean.sql" "$CONTAINER:/tmp/reset-clean.sql"
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -f /tmp/reset-clean.sql -q

echo "Database reset complete — all data truncated."
