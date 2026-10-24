#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Adds a bullet to a section of a topic's notes.md, below the last line of that
#   section; a section that does not exist yet is added at the end of the file.
# Usage: bash backlog/research/tools/scripts/add-note.sh <topic> <section> <text...> [--help]
#   <topic>     an existing topic
#   <section>   the heading text without the '## ', for example Findings, Variants and comparison,
#               Differences from the project
#   <text...>   the note
# Uses: bash, awk, mktemp, topic-common.sh.
# Env: None.
# Input: the arguments above.
# Outputs: rewrites backlog/research/<topic>/notes.md with the bullet added and prints the bullet.
# Returns: 0 on success; 1 when the topic does not exist; 2 on a usage error.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# shellcheck source=topic-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/topic-common.sh"

if [ "${1:-}" = "--help" ]; then
  print_usage "${BASH_SOURCE[0]}"
  exit 0
fi
[ "$#" -ge 3 ] || die 2 "usage: add-note.sh <topic> <section> <text...>"
TOPIC="$1" SECTION="$2"
shift 2
TEXT="$*"
require_existing_topic "$TOPIC"

FILE="$RESEARCH_DIR/$TOPIC/notes.md"
BULLET="- $TEXT"
if grep -qx "## $SECTION" "$FILE"; then
  TMP="$(mktemp)"
  awk -v section="## $SECTION" -v bullet="$BULLET" '
    function emit() { if (!body) print ""; print bullet; while (blanks > 0) { print ""; blanks-- } }
    $0 == section { inside = 1; body = 0; blanks = 0; print; next }
    inside && /^## / { emit(); done = 1; inside = 0; print; next }
    inside && $0 == "" { blanks++; next }
    inside { while (blanks > 0) { print ""; blanks-- } body++; print; next }
    { print }
    END { if (inside && !done) emit() }
  ' "$FILE" > "$TMP"
  mv "$TMP" "$FILE"
else
  printf '\n## %s\n\n%s\n' "$SECTION" "$BULLET" >> "$FILE"
fi
echo "$BULLET"
