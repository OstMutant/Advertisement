#!/usr/bin/env bash
# Resets the local database to a clean state with minimal seed data.
# Run before Playwright smoke tests when you want a fresh start.
#
# Usage: bash database/reset.sh
set -euo pipefail

trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc): database reset error ==="; exit $_rc' ERR

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-experiments}"
DB_USER="${DB_USER:-experiments_user}"
DB_PASSWORD="${DB_PASSWORD:-experiments_user_password}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" -p "$DB_PORT" \
    -U "$DB_USER" -d "$DB_NAME" \
    -f "$SCRIPT_DIR/reset.sql"

echo "Database reset complete — 3 seed users inserted (user1-user3@example.com, password: password)."
