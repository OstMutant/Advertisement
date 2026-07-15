#!/usr/bin/env bash
# Truncates all application data. Run manually when you need a clean DB without restarting the app.
#
# Usage: bash scripts/reset-db.sh
set -euo pipefail

trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc): database reset error ==="; exit $_rc' ERR

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-experiments}"
DB_USER="${DB_USER:-experiments_user}"
DB_PASSWORD="${DB_PASSWORD:-experiments_user_password}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

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
