#!/usr/bin/env bash
# Description: CI gate -- fails if the committed architecture-model.json/architecture-map.html
#   are stale (out of sync with what the generator would produce from current repo state).
# Uses: bash, calls generate-architecture-model.sh as a subprocess.
# Input: the current committed docs/architecture/data/architecture-model.json + architecture-map.html,
#   plus everything generate-architecture-model.sh itself reads.
# Output: exit 0 ("up to date") or exit 1 with an ERROR line naming which file is stale --
#   no file is written, the committed files are always restored afterward.
#
# Fails if architecture-model.json / architecture-map.html don't match what
# generate-architecture-model.sh would produce right now. Read-only: never modifies the tracked
# files, restores them after comparing. Reuses the generator script verbatim -- no separate
# parsing logic to keep in sync. Same pattern as check-adr-index-freshness.sh.
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
trap 'mv "$BACKUP_JSON" "$JSON"; mv "$BACKUP_HTML" "$HTML"' EXIT

cp "$JSON" "$BACKUP_JSON"
cp "$HTML" "$BACKUP_HTML"
bash "$REPO_ROOT/docs/architecture/scripts/generate-architecture-model.sh" > /dev/null

# sonarMetrics.analysisDate is a live SonarQube scan timestamp -- it legitimately differs between
# two runs even when nothing "architectural" changed (e.g. someone reran a Sonar scan for an
# unrelated reason between two freshness checks). Normalize it out of both copies before diffing,
# same way the ncloc/complexity/etc. values themselves are still compared normally (those only
# change when code actually changes).
normalize() { sed -E 's/"analysisDate": ?"[^"]*"/"analysisDate": "NORMALIZED"/' "$1"; }

stale=0
if ! diff -q <(normalize "$BACKUP_JSON") <(normalize "$JSON") > /dev/null 2>&1; then
  echo "ERROR: architecture-model.json is stale (out of sync with pom.xml/DECISIONS.md/backlog/docs/ai/flows.md/.claude/commands/.claude/skills)."
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
