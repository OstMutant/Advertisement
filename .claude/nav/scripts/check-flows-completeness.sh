#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Fails if any .claude/commands/*.md or .claude/skills/*/SKILL.md file has no
#   corresponding mention in .claude/nav/flows.md's "Project commands & skills" table -- the only
#   half of that file checkable from inside this repo (the "Built-in Claude Code skills" table
#   isn't, see flows.md's own "Staying correct" section).
# Usage: bash .claude/nav/scripts/check-flows-completeness.sh
# Uses: bash, awk, find, grep.
# Env: None.
# Input: every .claude/commands/*.md and .claude/skills/*/SKILL.md file, .claude/nav/flows.md's
#   "Project commands & skills" section.
# Outputs: an ERROR line listing each missing command/skill, if any.
# Returns: 0 = every command/skill has a row, 1 = at least one is missing.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FLOWS="$REPO_ROOT/.claude/nav/flows.md"

# Scope the search to the "Project" section only -- stop at the "Built-in" header.
PROJECT_SECTION="$(awk '/^## Project commands/{f=1} /^## Built-in Claude Code skills/{f=0} f' "$FLOWS")"

missing=""

while IFS= read -r -d '' file; do
  name="$(basename "$file" .md)"
  if ! grep -qi -- "$name" <<< "$PROJECT_SECTION"; then
    missing="$missing- .claude/commands/$name.md (command: /$name)\n"
  fi
done < <(find "$REPO_ROOT/.claude/commands" -name "*.md" -print0 | sort -z)

while IFS= read -r -d '' file; do
  name="$(basename "$(dirname "$file")")"
  if ! grep -qi -- "$name" <<< "$PROJECT_SECTION"; then
    missing="$missing- .claude/skills/$name/SKILL.md (skill: $name)\n"
  fi
done < <(find "$REPO_ROOT/.claude/skills" -name "SKILL.md" -print0 2>/dev/null | sort -z)

if [ -n "$missing" ]; then
  echo "ERROR: .claude/nav/flows.md is missing a row for:"
  printf '%b' "$missing"
  echo "Add a row to the 'Project commands & skills' table in .claude/nav/flows.md."
  exit 1
fi

echo ".claude/nav/flows.md covers every project-local command/skill."
exit 0
