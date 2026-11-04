#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Appends one progress line to a topic's progress.md in the form
#   "- 14:02 [search] 7 sources (+2 new), choice unchanged: <text>"; a date heading is added
#   first whenever the date differs from the last heading in the file.
# Usage: bash backlog/research/tools/scripts/log-progress.sh <topic> <stage> <sources> <new> <changed|unchanged> <text...> [--help]
#   <topic>      an existing topic
#   <stage>      lower-case letters and hyphens, for example search, read, compare, draft
#   <sources>    number of sources used so far
#   <new>        number of sources added by this step
#   <changed|unchanged>   whether this step changed the leading choice
#   <text...>    what was done, one short sentence
# Uses: bash, date, grep, topic-common.sh.
# Env: None.
# Input: the arguments above.
# Outputs: appends to backlog/research/<topic>/progress.md and prints the appended line.
# Returns: 0 on success; 1 when the topic does not exist; 2 on a usage error.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# shellcheck source=topic-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/topic-common.sh"

if [ "${1:-}" = "--help" ]; then
  print_usage "${BASH_SOURCE[0]}"
  exit 0
fi
[ "$#" -ge 6 ] || die 2 "usage: log-progress.sh <topic> <stage> <sources> <new> <changed|unchanged> <text...>"
TOPIC="$1" STAGE="$2" SOURCES="$3" NEW="$4" CHANGED="$5"
shift 5
TEXT="$*"
require_existing_topic "$TOPIC"
[[ "$STAGE" =~ ^[a-z][a-z-]*$ ]] || die 2 "stage '$STAGE' must be lower-case letters and hyphens"
[[ "$SOURCES" =~ ^[0-9]+$ ]] || die 2 "sources '$SOURCES' must be a number"
[[ "$NEW" =~ ^[0-9]+$ ]] || die 2 "new '$NEW' must be a number"
case "$CHANGED" in
  changed) CHOICE="choice CHANGED" ;;
  unchanged) CHOICE="choice unchanged" ;;
  *) die 2 "fifth argument must be 'changed' or 'unchanged'" ;;
esac

FILE="$RESEARCH_DIR/$TOPIC/progress.md"
TODAY="$(date +%F)"
LAST_HEADING="$(grep '^## ' "$FILE" | tail -1 | sed 's/^## //' || true)"
if [ "$LAST_HEADING" != "$TODAY" ]; then
  printf '\n## %s\n\n' "$TODAY" >> "$FILE"
fi
LINE="- $(date +%H:%M) [$STAGE] $SOURCES sources (+$NEW new), $CHOICE: $TEXT"
echo "$LINE" >> "$FILE"
echo "$LINE"
