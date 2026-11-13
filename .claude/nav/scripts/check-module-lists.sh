#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────
# Description: Backstop for the root README's hand-drawn "Module Layout" section -- fails if its
#   diagram does not name every module the root pom.xml lists (integration-tests may be in the text
#   only) or its "Per-module documentation" table has no row for one, so a new module cannot be
#   added to the reactor and silently left out of what the repository's front page shows.
# Usage: bash .claude/nav/scripts/check-module-lists.sh
# Uses: bash, awk, grep, sed.
# Env: None.
# Input: pom.xml (the <module> list), README.md (the section from "## Module Layout" up to the next
#   "---" rule, "## " heading or the end of the file; its mermaid block is the diagram, its
#   "| <module> |" lines are the documentation table).
# Outputs: one OK line on success; on failure one ERROR line followed by a "- <module>" line per
#   module the diagram does not name, and the same for the table.
# Returns: 0 = every module is in the diagram and the table, 1 = at least one is missing (or the
#   section is absent).
# ─────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

SECTION="$(awk '/^## Module Layout$/{on=1; next} on && /^(---|## )/{exit} on' "$REPO_ROOT/README.md")"
if [ -z "$SECTION" ]; then
  echo "ERROR: README.md has no \"## Module Layout\" section (or it is empty)."
  exit 1
fi
DIAGRAM="$(printf '%s\n' "$SECTION" | awk '/^```mermaid/{on=1; next} /^```/{on=0} on')"

missing=""
missing_rows=""
while IFS= read -r module; do
  # integration-tests is test-only and named in the paragraph only; every other module is drawn.
  scope="$DIAGRAM"
  [ "$module" = "integration-tests" ] && scope="$SECTION"
  if ! printf '%s\n' "$scope" | grep -Eq "(^|[^A-Za-z0-9-])${module}([^A-Za-z0-9-]|\$)"; then
    missing="${missing}- ${module}\n"
  fi
  if ! printf '%s\n' "$SECTION" | grep -Eq "^\| ${module} \|"; then
    missing_rows="${missing_rows}- ${module}\n"
  fi
done < <(sed -n 's#.*<module>\(.*\)</module>.*#\1#p' "$REPO_ROOT/pom.xml")

if [ -n "$missing" ]; then
  echo "ERROR: README.md's \"Module Layout\" does not name these modules from pom.xml (every module must be in the diagram, integration-tests in the text):"
  printf '%b' "$missing"
  echo "Add them to the diagram."
fi
if [ -n "$missing_rows" ]; then
  echo "ERROR: README.md's \"Per-module documentation\" table has no row for these modules from pom.xml:"
  printf '%b' "$missing_rows"
  echo "Add a row for each."
fi
if [ -n "$missing" ] || [ -n "$missing_rows" ]; then
  exit 1
fi

echo "README.md's \"Module Layout\" names every module of pom.xml, in the diagram and in the documentation table."
exit 0
