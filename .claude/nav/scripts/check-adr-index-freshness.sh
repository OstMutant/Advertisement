#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Read-only freshness check -- fails if .claude/nav/adr-index.md doesn't match what
#   generate-adr-index.sh would produce right now. Regenerates into a throwaway temp path and
#   diffs; the committed file is never opened for writing, so an interrupted run cannot corrupt it.
# Usage: bash .claude/nav/scripts/check-adr-index-freshness.sh
# Uses: bash, .claude/nav/scripts/generate-adr-index.sh (reused verbatim, no separate parsing logic).
# Env: None.
# Input: every DECISIONS.md file in the repo (via generate-adr-index.sh), the committed
#   .claude/nav/adr-index.md.
# Outputs: an ERROR line + fix instructions if stale; writes only a self-cleaning temp file, never
#   the committed file.
# Returns: 0 = up to date, 1 = stale.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMMITTED="$REPO_ROOT/.claude/nav/adr-index.md"
GENERATED="$(mktemp)"
trap 'rm -f "$GENERATED"' EXIT

bash "$REPO_ROOT/.claude/nav/scripts/generate-adr-index.sh" "$GENERATED" > /dev/null

if ! diff -q "$COMMITTED" "$GENERATED" > /dev/null 2>&1; then
  echo "ERROR: .claude/nav/adr-index.md is stale (out of sync with the current DECISIONS.md files)."
  echo "Run: bash .claude/nav/scripts/generate-adr-index.sh, review the diff, and commit the result."
  exit 1
fi

echo ".claude/nav/adr-index.md is up to date."
exit 0
