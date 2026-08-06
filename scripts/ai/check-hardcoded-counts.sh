#!/usr/bin/env bash
# Backstop behind the doc-standards skill's checklist -- fails if a doc's hard-coded module count no longer matches pom.xml.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REAL_COUNT="$(grep -c '<module>' "$REPO_ROOT/pom.xml")"

SEARCH_PATHS=(
  "$REPO_ROOT/docs"
  "$REPO_ROOT/CLAUDE.md"
  "$REPO_ROOT/README.md"
)
while IFS= read -r -d '' f; do SEARCH_PATHS+=("$f"); done < <(find "$REPO_ROOT" -maxdepth 2 \( -name "CLAUDE.md" -o -name "README.md" \) -not -path "$REPO_ROOT/CLAUDE.md" -not -path "$REPO_ROOT/README.md" -print0)

mismatch=""

# architecture-model.json / architecture-map.html carry full ADR body text (scripts/architecture/DECISIONS.md
# ADR-008) -- historical ADR prose can legitimately mention an unrelated past "N modules" count
# (e.g. a starter-list length, a table/module split) that has nothing to do with the reactor's
# total module count; excluded here, same as ADR-006 covered before this was briefly reverted.
while IFS=: read -r file lineno match; do
  num="${match%% *}"
  if [ "$num" -ne "$REAL_COUNT" ]; then
    mismatch="${mismatch}- ${file#"$REPO_ROOT"/}:$lineno -- claims \"$match\", pom.xml currently has $REAL_COUNT modules\n"
  fi
done < <(grep -rnoP '(?<!≥)[0-9]+ modules?\b' "${SEARCH_PATHS[@]}" 2>/dev/null \
  | grep -v -e '^'"$REPO_ROOT"'/docs/architecture/architecture-model.json:' -e '^'"$REPO_ROOT"'/docs/architecture/architecture-map.html:' \
  || true)

if [ -n "$mismatch" ]; then
  echo "ERROR: stale hard-coded module count(s) found (pom.xml currently has $REAL_COUNT modules):"
  printf '%b' "$mismatch"
  echo "Fix the count, or reword to avoid a hard-coded number entirely (see .claude/skills/doc-standards/SKILL.md)."
  exit 1
fi

echo "No stale hard-coded module counts found (pom.xml has $REAL_COUNT modules)."
exit 0
