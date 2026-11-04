#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Lists the research topics under backlog/research/ with the time of their latest
#   change, the number of progress lines and sources, and whether the human plan was filled in.
# Usage: bash backlog/research/tools/scripts/list-topics.sh [--help]
#   --help    print the usage text and exit
# Uses: bash, find, sort, grep, topic-common.sh.
# Env: None.
# Input: the topic folders under backlog/research/ (the tools folder is skipped).
# Outputs: one line per topic: name, latest change, progress lines, sources, plan state; or the
#   line "no topics" when there is none.
# Returns: 0 always; 2 on a usage error.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# shellcheck source=topic-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/topic-common.sh"

if [ "${1:-}" = "--help" ]; then
  print_usage "${BASH_SOURCE[0]}"
  exit 0
fi
[ "$#" -eq 0 ] || die 2 "usage: list-topics.sh"

found=false
for dir in "$RESEARCH_DIR"/*/; do
  [ -d "$dir" ] || continue
  name="$(basename "$dir")"
  [ "$name" != "tools" ] || continue
  found=true
  changed="$(find "$dir" -type f -printf '%TY-%Tm-%Td %TH:%TM\n' | sort | tail -1)"
  steps="$(grep -c '^- ' "$dir/progress.md" 2>/dev/null || true)"
  sources="$(grep -c '^| http' "$dir/sources.md" 2>/dev/null || true)"
  if [ "$(grep -c '^[^#[:space:]]' "$dir/plan-human.md" 2>/dev/null || true)" -gt 1 ]; then
    plan="plan written"
  else
    plan="no plan yet"
  fi
  printf '%s  changed %s  steps %s  sources %s  %s\n' "$name" "$changed" "$steps" "$sources" "$plan"
done
$found || echo "no topics"
