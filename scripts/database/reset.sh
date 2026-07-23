#!/usr/bin/env bash
# Truncates all application data. Run manually when you need a clean DB without restarting the app.
#
# Usage: bash scripts/reset-db.sh
set -euo pipefail

trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc): database reset error ==="; exit $_rc' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load shared credential defaults from the repo-root .env into ENV_*-prefixed vars -- not
# exported/sourced directly, so an already-exported override is never clobbered (same pattern as
# scripts/deploy.sh, see improvement-044).
if [ -f "$ROOT/.env" ]; then
  while IFS='=' read -r _env_key _env_value; do
    [[ "$_env_key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    printf -v "ENV_$_env_key" '%s' "$_env_value"
  done < <(grep -v '^\s*#' "$ROOT/.env" | grep -v '^\s*$')
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-${ENV_DB_PORT:-5432}}"
DB_NAME="${DB_NAME:-${ENV_DB_NAME:-experiments}}"
DB_USER="${DB_USER:-${ENV_DB_USER:-experiments_user}}"
DB_PASSWORD="${DB_PASSWORD:-${ENV_DB_PASSWORD:-experiments_user_password}}"

_db_container=$(docker ps -a --filter "publish=5432" --format "{{.Names}}" | head -1)
if [ -n "$_db_container" ]; then
  if [ "$(docker inspect -f '{{.State.Status}}' "$_db_container")" != "running" ]; then
    echo "Starting $_db_container..."
    docker start "$_db_container"
    until docker exec "$_db_container" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do sleep 1; done
  fi
else
  echo "No DB container found — starting via docker compose..."
  source "$ROOT/scripts/ensure-docker-plugins.sh"
  ensure_docker_compose
  docker compose --project-directory "$ROOT" -f "$ROOT/scripts/infra/docker-compose.db.yml" up -d
  until docker exec "$(docker ps --filter "publish=5432" --format "{{.Names}}" | head -1)" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do sleep 1; done
fi

PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" -p "$DB_PORT" \
    -U "$DB_USER" -d "$DB_NAME" \
    -f "$SCRIPT_DIR/reset-clean.sql"

echo "Database reset complete — all data truncated."
