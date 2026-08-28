#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Read-only freshness check -- fails if .claude/nav/adr-index.md doesn't match what
#   generate-adr-index.sh would produce right now. Never modifies the tracked file: backs it up,
#   regenerates, diffs, always restores the original afterward regardless of outcome.
# Usage: bash .claude/nav/scripts/check-adr-index-freshness.sh
# Uses: bash, .claude/nav/scripts/generate-adr-index.sh (reused verbatim, no separate parsing logic).
# Env: None.
# Input: every DECISIONS.md file in the repo (via generate-adr-index.sh), the committed
#   .claude/nav/adr-index.md.
# Outputs: an ERROR line + fix instructions if stale; the committed file is always restored to its
#   original content before exit.
# Returns: 0 = up to date, 1 = stale.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMMITTED="$REPO_ROOT/.claude/nav/adr-index.md"
BACKUP="$(mktemp)"
trap 'mv "$BACKUP" "$COMMITTED"' EXIT

cp "$COMMITTED" "$BACKUP"
bash "$REPO_ROOT/.claude/nav/scripts/generate-adr-index.sh" > /dev/null

if ! diff -q "$BACKUP" "$COMMITTED" > /dev/null 2>&1; then
  echo "ERROR: .claude/nav/adr-index.md is stale (out of sync with the current DECISIONS.md files)."
  echo "Run: bash .claude/nav/scripts/generate-adr-index.sh, review the diff, and commit the result."
  exit 1
fi

echo ".claude/nav/adr-index.md is up to date."
exit 0
