#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Measures one batch of the /optimize command and checks its hard rules without a model
#   call. For the files of the batch it counts the lines added and removed against a base (staged
#   and unstaged changes together; an untracked file counts all its lines as added), and over the
#   whole change against that base it fails when anything under marketplace-orchestrator/ changed,
#   when an added line in a pom.xml contains <dependency>, or when an added line in a .java file uses
#   reflection. The counts are physical lines, comments and blank lines included.
# Usage: bash .claude/commands/optimize/check-batch.sh [--base <ref>] <path>... [--help]
#   <path>...   the files (or folders) of one batch
#   --base      the ref the change is measured against (default HEAD)
#   --help      print the usage text and exit
# Uses: bash, git, awk, grep, wc.
# Env: None.
# Input: the paths, the git repository of the current directory and its working tree.
# Outputs: one line per measured file "<added> <removed> <net> <path>", a line
#   "TOTAL added=<n> removed=<n> net=<n> files=<n>", "VIOLATION ..." lines for broken rules and
#   "WARNING ..." lines for a changed file that is neither in the batch nor under backlog/.
# Returns: 0 when no rule is broken (warnings do not change it); 1 when a rule is broken; 2 on a
#   usage error.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REFLECTION='java\.lang\.reflect|setAccessible\(|getDeclaredField|getDeclaredMethod|Class\.forName'

usage() {
  awk '/^# Usage:/{f=1} /^# Uses:/{f=0} f' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

BASE="HEAD"
PATHS=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    --help) usage; exit 0 ;;
    --base)
      [ "$#" -ge 2 ] || { echo "error: --base needs a ref" >&2; exit 2; }
      BASE="$2"
      shift 2
      ;;
    -*) echo "error: unknown option '$1'" >&2; usage >&2; exit 2 ;;
    *) PATHS+=("$1"); shift ;;
  esac
done
[ "${#PATHS[@]}" -gt 0 ] || { echo "error: give at least one path" >&2; usage >&2; exit 2; }

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
git rev-parse --verify -q "$BASE" >/dev/null || { echo "error: unknown ref '$BASE'" >&2; exit 2; }

TOTAL_ADDED=0
TOTAL_REMOVED=0
FILES=0
declare -A MEASURED=()

for path in "${PATHS[@]}"; do
  while IFS=$'\t' read -r added removed file; do
    [ -n "$file" ] || continue
    [ -z "${MEASURED[$file]:-}" ] || continue
    [ "$added" != "-" ] || { added=0; removed=0; }
    MEASURED[$file]=1
    printf '%s %s %s %s\n' "$added" "$removed" "$((added - removed))" "$file"
    TOTAL_ADDED=$((TOTAL_ADDED + added))
    TOTAL_REMOVED=$((TOTAL_REMOVED + removed))
    FILES=$((FILES + 1))
  done < <(git diff --numstat --no-renames "$BASE" -- "$path")
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    [ -z "${MEASURED[$file]:-}" ] || continue
    added="$(wc -l < "$file")"
    MEASURED[$file]=1
    printf '%s 0 %s %s\n' "$added" "$added" "$file"
    TOTAL_ADDED=$((TOTAL_ADDED + added))
    FILES=$((FILES + 1))
  done < <(git ls-files --others --exclude-standard -- "$path")
done
echo "TOTAL added=$TOTAL_ADDED removed=$TOTAL_REMOVED net=$((TOTAL_ADDED - TOTAL_REMOVED)) files=$FILES"

VIOLATIONS=0
CHANGED="$( { git diff --name-only --no-renames "$BASE"; git ls-files --others --exclude-standard; } | sort -u)"

while IFS= read -r file; do
  [ -n "$file" ] || continue
  case "$file" in
    marketplace-orchestrator/*)
      echo "VIOLATION $file is under marketplace-orchestrator/, which /optimize never touches"
      VIOLATIONS=$((VIOLATIONS + 1))
      ;;
  esac
  in_batch=false
  for path in "${PATHS[@]}"; do
    if [ "$file" = "$path" ] || [[ "$file" == "${path%/}/"* ]]; then
      in_batch=true
    fi
  done
  if ! $in_batch && [[ "$file" != backlog/* ]]; then
    echo "WARNING $file changed but is not in the batch"
  fi
done <<< "$CHANGED"

added_lines() {
  { git diff -U0 --no-renames "$BASE" -- "$1" | grep '^+' | grep -v '^+++' || true; }
  while IFS= read -r file; do
    [ -n "$file" ] && sed 's/^/+/' "$file"
  done < <(git ls-files --others --exclude-standard -- "$1")
}

if added_lines '*pom.xml' | grep '<dependency>' > /dev/null; then
  echo "VIOLATION an added line in a pom.xml contains <dependency>"
  VIOLATIONS=$((VIOLATIONS + 1))
fi
if added_lines '*.java' | grep -E "$REFLECTION" > /dev/null; then
  echo "VIOLATION an added line in a .java file uses reflection"
  VIOLATIONS=$((VIOLATIONS + 1))
fi

[ "$VIOLATIONS" -eq 0 ]
