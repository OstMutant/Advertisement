#!/usr/bin/env bash
# Fails if architecture-model.json / architecture-map.html don't match what
# generate-architecture-model.sh would produce right now. Read-only: never modifies the tracked
# files, restores them after comparing. Reuses the generator script verbatim -- no separate
# parsing logic to keep in sync. Same pattern as check-adr-index-freshness.sh.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JSON="$REPO_ROOT/docs/architecture-model.json"
HTML="$REPO_ROOT/docs/architecture-map.html"

if [ ! -f "$JSON" ] || [ ! -f "$HTML" ]; then
  echo "ERROR: architecture-model.json / architecture-map.html don't exist yet."
  echo "Run: bash scripts/ai/generate-architecture-model.sh and commit the result."
  exit 1
fi

BACKUP_JSON="$(mktemp)"
BACKUP_HTML="$(mktemp)"
trap 'mv "$BACKUP_JSON" "$JSON"; mv "$BACKUP_HTML" "$HTML"' EXIT

cp "$JSON" "$BACKUP_JSON"
cp "$HTML" "$BACKUP_HTML"
bash "$REPO_ROOT/scripts/ai/generate-architecture-model.sh" > /dev/null

stale=0
if ! diff -q "$BACKUP_JSON" "$JSON" > /dev/null 2>&1; then
  echo "ERROR: architecture-model.json is stale (out of sync with pom.xml/DECISIONS.md/backlog/docs/ai/flows.md/.claude/commands/.claude/skills)."
  stale=1
fi
if ! diff -q "$BACKUP_HTML" "$HTML" > /dev/null 2>&1; then
  echo "ERROR: architecture-map.html is stale (out of sync with architecture-model.json)."
  stale=1
fi

if [ "$stale" -ne 0 ]; then
  echo "Run: bash scripts/ai/generate-architecture-model.sh, review the diff, and commit the result."
  exit 1
fi

echo "architecture-model.json / architecture-map.html are up to date."
exit 0
