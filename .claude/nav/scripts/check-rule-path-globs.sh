#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Checks every .claude/rules/*.md file's own "paths:" frontmatter glob against the
#   real repo file tree and reports any directory elsewhere in the tree sharing the same basename --
#   Claude Code's path-scoped rule matcher is not anchored to the repo root (confirmed directly,
#   see .claude/rules/README.md), so a bare "name/**" pattern also matches "name" appearing as a
#   path segment anywhere, not only at the top level.
# Usage: bash .claude/nav/scripts/check-rule-path-globs.sh
# Uses: bash, find, grep, sed.
# Env: None.
# Input: every .claude/rules/*.md file with a "paths:" frontmatter line; the current repo file
#   tree (excludes .git, target, node_modules, .m2, pw-report, and .claude/worktrees as build/tool
#   noise, not real source).
# Outputs: one report line per unintended match to stdout; a summary count at the end.
# Returns: 0 always -- informational, not a CI gate.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

# One tree walk for every rule file, not one walk per rule file -- the naive per-file loop
# (16 full-tree find calls) took long enough to time out a 120s foreground command.
ALL_DIRS="$(find . \( -path ./.git -o -path ./.claude/worktrees -o -name target -o -name node_modules -o -name pw-report -o -name .m2 \) -prune -o -type d -print 2>/dev/null | sed 's|^\./||')"

total_findings=0

for rule_file in .claude/rules/*.md; do
    [ -f "$rule_file" ] || continue
    grep -q '^paths:' "$rule_file" || continue   # README.md and any future non-scoped file
    glob=$(grep -m1 '^paths:' "$rule_file" | sed -E 's/^paths:\s*\["?([^"]*)"?\].*$/\1/')
    [ -z "$glob" ] && continue

    base="${glob%%/**}"
    [ -z "$base" ] && continue

    matches=$(echo "$ALL_DIRS" | grep -E "(^|/)${base}\$" || true)
    match_count=$(echo "$matches" | grep -c . || true)

    if [ "$match_count" -gt 1 ]; then
        echo "UNINTENDED MATCH — $rule_file (paths: \"$glob\"):"
        echo "$matches" | grep -v "^${base}\$" | sed 's/^/    /'
        total_findings=$((total_findings + 1))
    fi
done

echo ""
echo "Checked $(ls .claude/rules/*.md | wc -l | tr -d ' ') rule files, $total_findings with unintended path matches."
exit 0
