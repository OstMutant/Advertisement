#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Shows how a planning run on a topic is going without reading the agent's own
#   transcript: when each file of the topic last changed, the tail of progress.md, the number of
#   sources, and the starting loop signals. Signals: the same link in sources.md 3 or more times,
#   the same progress text 3 or more times, no new source in the last 5 steps. The status line is
#   'progressing' without signals, 'warning' with one kind, 'looping' when a repeat signal and
#   'no new sources' fire together, and 'empty' before the first step.
# Usage: bash backlog/research/tools/scripts/show-status.sh <topic> [lines] [--help]
#   <topic>   an existing topic
#   [lines]   how many progress lines to show (default 10)
#   --help    print the usage text and exit
# Uses: bash, awk, grep, sed, sort, uniq, stat, date, topic-common.sh.
# Env: None.
# Input: the topic folder.
# Outputs: file times, the progress tail, counts, the signals found and a final line
#   'STATUS: progressing|warning|looping|empty'.
# Returns: 0 always when the topic exists; 1 when it does not; 2 on a usage error.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# shellcheck source=topic-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/topic-common.sh"

if [ "${1:-}" = "--help" ]; then
  print_usage "${BASH_SOURCE[0]}"
  exit 0
fi
[ "$#" -ge 1 ] && [ "$#" -le 2 ] || die 2 "usage: show-status.sh <topic> [lines]"
TOPIC="$1"
SHOWN="${2:-10}"
[[ "$SHOWN" =~ ^[0-9]+$ ]] || die 2 "lines '$SHOWN' must be a number"
require_existing_topic "$TOPIC"
DIR="$RESEARCH_DIR/$TOPIC"
PROGRESS="$DIR/progress.md"
SOURCES="$DIR/sources.md"

echo "== topic: $TOPIC"
echo "-- files (last change)"
for name in brief progress sources notes plan-human plan-machine; do
  printf '  %-13s %s\n' "$name.md" "$(date -d "@$(stat -c %Y "$DIR/$name.md")" '+%F %H:%M')"
done

STEPS="$(grep -c '^- ' "$PROGRESS" || true)"
LINKS="$(grep -c '^| http' "$SOURCES" || true)"
NOW="$(date +%s)"
IDLE=$(( (NOW - $(stat -c %Y "$PROGRESS")) / 60 ))
echo "-- progress: $STEPS steps, $LINKS sources, progress.md idle for $IDLE min"
grep '^- ' "$PROGRESS" | tail -n "$SHOWN" | sed 's/^/  /' || true

if [ "$STEPS" -eq 0 ]; then
  echo "STATUS: empty"
  exit 0
fi

REPEAT=0
NO_NEW=0
echo "-- signals"
REPEATED_LINKS="$(awk -F'|' '/^\| http/ { gsub(/ /, "", $2); count[$2]++ } END { for (l in count) if (count[l] >= 3) print count[l] "x " l }' "$SOURCES")"
if [ -n "$REPEATED_LINKS" ]; then
  REPEAT=1
  echo "$REPEATED_LINKS" | sed 's/^/  SIGNAL repeated-link: /'
fi
REPEATED_STEPS="$(grep '^- ' "$PROGRESS" | sed 's/^.*choice [A-Za-z]*: //' | sort | uniq -c | awk '$1 >= 3 { c = $1; $1 = ""; print c "x" $0 }')"
if [ -n "$REPEATED_STEPS" ]; then
  REPEAT=1
  echo "$REPEATED_STEPS" | sed 's/^/  SIGNAL repeated-step: /'
fi
if [ "$STEPS" -ge 5 ] && [ "$(grep '^- ' "$PROGRESS" | tail -n 5 | grep -c '(+0 new)')" -eq 5 ]; then
  NO_NEW=1
  echo "  SIGNAL no-new-sources: the last 5 steps added no source"
fi

if [ "$REPEAT" -eq 1 ] && [ "$NO_NEW" -eq 1 ]; then
  echo "STATUS: looping"
elif [ "$REPEAT" -eq 1 ] || [ "$NO_NEW" -eq 1 ]; then
  echo "STATUS: warning"
else
  echo "STATUS: progressing"
fi
