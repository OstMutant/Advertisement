#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: CI gate -- fails if the committed docs/architecture/data/architecture-model.json or
#   docs/architecture/architecture-map.html are stale (out of sync with what
#   generate-architecture-model.sh would produce from current repo state). Backs both files up,
#   regenerates in place, diffs, then restores the originals -- restore is guarded so an
#   interrupted run can never overwrite an intact committed file with a truncated backup.
# Usage: bash docs/architecture/scripts/check-architecture-model-freshness.sh
# Uses: bash, sed, diff; calls generate-architecture-model.sh as a subprocess.
# Env: None.
# Input: the committed architecture-model.json + architecture-map.html, plus everything
#   generate-architecture-model.sh itself reads.
# Outputs: an ERROR line naming which file is stale, if any; the committed files are backed up
#   before and restored after, so a successful run leaves them byte-identical to how it found them.
# Returns: 0 = up to date, 1 = stale (or the files don't exist yet).
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
JSON="$REPO_ROOT/docs/architecture/data/architecture-model.json"
HTML="$REPO_ROOT/docs/architecture/architecture-map.html"

if [ ! -f "$JSON" ] || [ ! -f "$HTML" ]; then
  echo "ERROR: architecture-model.json / architecture-map.html don't exist yet."
  echo "Run: bash docs/architecture/scripts/generate-architecture-model.sh and commit the result."
  exit 1
fi

BACKUP_JSON="$(mktemp)"
BACKUP_HTML="$(mktemp)"
cp "$JSON" "$BACKUP_JSON"
cp "$HTML" "$BACKUP_HTML"

# Arm the restore only after both backups are known-good, and guard each mv so a truncated or
# empty backup can never clobber an intact committed file -- an interrupted freshness check must
# never corrupt the tracked files.
restore_committed() {
  if [ -s "$BACKUP_JSON" ]; then mv -f "$BACKUP_JSON" "$JSON"; else rm -f "$BACKUP_JSON"; fi
  if [ -s "$BACKUP_HTML" ]; then mv -f "$BACKUP_HTML" "$HTML"; else rm -f "$BACKUP_HTML"; fi
}
trap restore_committed EXIT

bash "$REPO_ROOT/docs/architecture/scripts/generate-architecture-model.sh" > /dev/null

# sonarMetrics.analysisDate is a live SonarQube scan timestamp -- it legitimately differs between
# two runs even when nothing "architectural" changed (e.g. someone reran a Sonar scan for an
# unrelated reason between two freshness checks). Normalize it out of both copies before diffing,
# same way the ncloc/complexity/etc. values themselves are still compared normally (those only
# change when code actually changes).
normalize() { sed -E 's/"analysisDate": ?"[^"]*"/"analysisDate": "NORMALIZED"/' "$1"; }

stale=0
if ! diff -q <(normalize "$BACKUP_JSON") <(normalize "$JSON") > /dev/null 2>&1; then
  echo "ERROR: architecture-model.json is stale (out of sync with pom.xml/DECISIONS.md/backlog/.claude/nav/flows.md/.claude/commands/.claude/skills)."
  stale=1
fi
if ! diff -q <(normalize "$BACKUP_HTML") <(normalize "$HTML") > /dev/null 2>&1; then
  echo "ERROR: architecture-map.html is stale (out of sync with architecture-model.json)."
  stale=1
fi

if [ "$stale" -ne 0 ]; then
  echo "Run: bash docs/architecture/scripts/generate-architecture-model.sh, review the diff, and commit the result."
  exit 1
fi

echo "architecture-model.json / architecture-map.html are up to date."
exit 0
