#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Appends one row to a topic's sources.md: the link, what was taken from it, whether
#   it confirms or contradicts the leading choice, the date, the type of source, the level and
#   the confidence. The same link may be added again (a repeated use shows up in show-status.sh).
# Usage: bash backlog/research/tools/scripts/add-source.sh <topic> <link> <taken> <verdict> <type> <level> <confidence> [--help]
#   <topic>        an existing topic
#   <link>         the address of the source, starting with http
#   <taken>        what was taken from it, one short sentence
#   <verdict>      confirms, contradicts or neutral
#   <type>         official-doc, blog, paper, forum or other
#   <level>        0 direct source; 1 or 2 reached by following references when sources conflict
#   <confidence>   high, medium or low
# Uses: bash, date, topic-common.sh.
# Env: None.
# Input: the arguments above.
# Outputs: appends a table row to backlog/research/<topic>/sources.md and prints it.
# Returns: 0 on success; 1 when the topic does not exist; 2 on a usage error.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# shellcheck source=topic-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/topic-common.sh"

if [ "${1:-}" = "--help" ]; then
  print_usage "${BASH_SOURCE[0]}"
  exit 0
fi
[ "$#" -eq 7 ] || die 2 "usage: add-source.sh <topic> <link> <taken> <verdict> <type> <level> <confidence>"
TOPIC="$1" LINK="$2" TAKEN="$3" VERDICT="$4" TYPE="$5" LEVEL="$6" CONFIDENCE="$7"
require_existing_topic "$TOPIC"
[[ "$LINK" =~ ^https?:// ]] || die 2 "link '$LINK' must start with http:// or https://"
case "$VERDICT" in confirms | contradicts | neutral) ;; *) die 2 "verdict must be confirms, contradicts or neutral" ;; esac
case "$TYPE" in official-doc | blog | paper | forum | other) ;; *) die 2 "type must be official-doc, blog, paper, forum or other" ;; esac
case "$LEVEL" in 0 | 1 | 2) ;; *) die 2 "level must be 0, 1 or 2" ;; esac
case "$CONFIDENCE" in high | medium | low) ;; *) die 2 "confidence must be high, medium or low" ;; esac

TAKEN="${TAKEN//|/\\|}"
ROW="| $LINK | $TAKEN | $VERDICT | $(date +%F) | $TYPE | $LEVEL | $CONFIDENCE |"
echo "$ROW" >> "$RESEARCH_DIR/$TOPIC/sources.md"
echo "$ROW"
