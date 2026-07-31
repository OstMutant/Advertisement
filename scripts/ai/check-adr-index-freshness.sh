#!/usr/bin/env bash
# Fails if docs/ai/adr-index.md doesn't match what generate-adr-index.sh would produce right now.
# Read-only: never modifies the tracked file, restores it after comparing. Reuses the generator
# script verbatim -- no separate parsing logic to keep in sync.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMMITTED="$REPO_ROOT/docs/ai/adr-index.md"
BACKUP="$(mktemp)"
trap 'mv "$BACKUP" "$COMMITTED"' EXIT

cp "$COMMITTED" "$BACKUP"
bash "$REPO_ROOT/scripts/ai/generate-adr-index.sh" > /dev/null

if ! diff -q "$BACKUP" "$COMMITTED" > /dev/null 2>&1; then
  echo "ERROR: docs/ai/adr-index.md is stale (out of sync with the current DECISIONS.md files)."
  echo "Run: bash scripts/ai/generate-adr-index.sh, review the diff, and commit the result."
  exit 1
fi

echo "docs/ai/adr-index.md is up to date."
exit 0
