# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Pipeline nodes for commands, skills and agents, and the backlog: task titles, short descriptions, priorities, the ranked priority order and the issue list.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, awk, sed, find, grep.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: .claude/commands, .claude/skills, .claude/agents, backlog/tasks and backlog/BACKLOG.md.
# Outputs: defines command_first_line, emit_pipeline_md_node, json_str_array, json_named_file_array, issue_*_for, backlog_priority_order_json, issue_list_json.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Pipeline nodes: .claude/commands/*.md + .claude/skills/*/SKILL.md ──────────────────────────
command_first_line() {
  # Prefer a YAML frontmatter "description:" field (skill files); fall back to the first
  # non-empty line (command files, which start with a plain prose summary, no frontmatter).
  local file="$1" desc
  if head -1 "$file" 2>/dev/null | grep -q '^---'; then
    desc="$(awk '/^description:/{sub(/^description: *>?/,""); getline nxt; if ($0=="") print nxt; else print; exit}' "$file")"
    [ -n "$desc" ] && { echo "$desc" | sed 's/^ *//'; return; }
  fi
  head -1 "$file" 2>/dev/null | sed 's/^#* *//'
}

# Emits one COMMAND/SKILL/AGENT-shaped JSON node -- the identical field sequence all three of
# .claude/commands/*.md, .claude/skills/*/SKILL.md, and .claude/agents/*.md map their own single
# source file onto (id/type/provenance/lifecycle/disposition/confidence/evidence/description/
# edges never differ between them, only how "$1"/"$2"/"$3" get computed per source, which stays in
# each loop below since the name-derivation and find-glob genuinely differ per type). "$1" full
# node id (e.g. "command:sync-docs"), "$2" node type ("COMMAND"/"SKILL"/"AGENT"), "$3" repo-relative
# source file path, "$4" already-extracted one-line description.
emit_pipeline_md_node() {
  local id="$1" type="$2" rel="$3" desc="$4"
  echo "    ,"
  echo "    {"
  echo "      \"id\": \"$(json_escape "$id")\","
  echo "      \"type\": \"$type\","
  echo "      \"provenance\": \"OBSERVED\","
  echo "      \"lifecycle\": \"ACTIVE\","
  echo "      \"disposition\": \"KEEP\","
  echo "      \"confidence\": \"extracted\","
  echo "      \"evidence\": [{\"file\": \"$(json_escape "$rel")\", \"line\": 1}],"
  echo "      \"description\": \"$(json_escape "$desc")\","
  echo "      \"edges\": {}"
  echo "    }"
}

# Builds a JSON string array from newline-separated input; empty input -> "[]", never [""].
json_str_array() {
  local items="$1" out="" first=true
  while IFS= read -r item; do
    [ -z "$item" ] && continue
    $first || out="$out, "
    first=false
    out="$out\"$(json_escape "$item")\""
  done <<< "$items"
  echo "[$out]"
}

# Builds a JSON array of {"name","file","description"} from newline-separated
# "name<TAB>file<TAB>description" pairs -- for lists that need to render as real links (client-side
# fileLink()), not just plain labels. "file"/"description" are both optional per row (omitted from
# the object when empty) -- existing 2-column "name<TAB>file" callers are unaffected.
json_named_file_array() {
  local items="$1" out="" first=true name file description
  while IFS=$'\t' read -r name file description; do
    [ -z "$name" ] && continue
    $first || out="$out, "
    first=false
    out="$out{\"name\": \"$(json_escape "$name")\""
    [ -n "$file" ] && out="$out, \"file\": \"$(json_escape "$file")\""
    [ -n "$description" ] && out="$out, \"description\": \"$(json_escape "$description")\""
    out="$out}"
  done <<< "$items"
  echo "[$out]"
}

issue_title_for() {
  awk '/^# /{sub(/^# /,""); print; exit}' "$1"
}

# First real paragraph of body text -- skips the H1 title, "**Label:**" metadata lines, "---"
# rules, and blank lines; stops at the first "##" heading or blank line after real text started.
issue_short_desc_for() {
  awk '
    BEGIN { started = 0; buf = "" }
    /^# / { next }
    /^\*\*/ { next }
    /^---[[:space:]]*$/ { next }
    /^##/ { if (started && buf != "") { print buf; exit } next }
    /^[[:space:]]*$/ { if (started && buf != "") { print buf; exit } next }
    { if (buf == "") buf = $0; else buf = buf " " $0; started = 1 }
    END { if (buf != "") print buf }
  ' "$1"
}

# "**Priority:** <emoji> <level> -- <reason>" -> just "<emoji> <level>", a compact badge (the
# reason is part of the file's own prose, not duplicated here).
issue_priority_for() {
  awk '/^\*\*Priority:\*\*/ { sub(/^\*\*Priority:\*\* */, ""); sub(/ (—|--).*/, ""); print; exit }' "$1"
}

# The real execution-order ranking already lives in backlog/BACKLOG.md's own "At a glance" table
# ("Issues (in execution order)" column) -- a curated sequence, not re-derivable from each issue's
# own free-form "**Priority:**" prose (which only groups into coarse tiers, losing the fine-grained
# order BACKLOG.md already settled, e.g. 138 before 136 before 135 despite all three being "Top").
# Flattens every row's comma/arrow-separated issue numbers into one ordered list; issues outside
# this table (not yet triaged into the compact view) get no rank and sort after everything listed.
backlog_priority_order_json() {
  json_str_array "$(awk '
    /^\| Priority \| Tier \| Issues/ { intable=1; next }
    intable && /^\|---/ { next }
    intable && /^\|/ {
      n = split($0, cols, "|")
      issues = cols[4]
      gsub(/^[ \t]+|[ \t]+$/, "", issues)
      gsub(/→/, ",", issues)
      m = split(issues, nums, ",")
      for (i = 1; i <= m; i++) {
        num = nums[i]
        gsub(/^[ \t]+|[ \t]+$/, "", num)
        if (num != "") print num
      }
      next
    }
    intable && !/^\|/ { exit }
  ' "$REPO_ROOT/backlog/BACKLOG.md")"
}

# Builds a JSON array of {"title":..,"description":..,"file":..} for every *.md in $1 (one level,
# sorted) -- description truncated to ~240 chars ("…" suffix) so 150+ issues stay a small addition
# to the model, not a second copy of every issue's full body.
issue_list_json() {
  local dir="$1" rel_prefix="$2" out="" first=true title desc priority file rel
  [ -d "$dir" ] || { echo "[]"; return; }
  while IFS= read -r -d '' file; do
    rel="${file#"$REPO_ROOT"/}"
    title="$(issue_title_for "$file")"
    [ -z "$title" ] && title="$(basename "$file" .md)"
    desc="$(issue_short_desc_for "$file")"
    [ "${#desc}" -gt 240 ] && desc="${desc:0:240}…"
    priority="$(issue_priority_for "$file")"
    $first || out="$out, "
    first=false
    out="$out{\"title\": \"$(json_escape "$title")\", \"description\": \"$(json_escape "$desc")\", \"priority\": \"$(json_escape "$priority")\", \"file\": \"$(json_escape "$rel")\"}"
  done < <(find "$dir" -maxdepth 1 -name "*.md" -print0 | sort -z)
  echo "[$out]"
}

# Every documented diagram, from every docs/architecture/*.md file that has one -- not just the
# two originally picked (user-flagged, 2026-08-04: "not all docs were loaded"). Each source file
# becomes one group, keyed by its own filename stem so the human layer can label groups
# meaningfully ("01 · Module Dependencies" etc.) without hand-maintaining a title map.
#
# "01-module-dependencies" is deliberately NOT in this list -- it has no markdown diagram to
# extract anymore. Its own diagram + dependency table + module-versions line render live on the
# Diagrams page straight from MODULES/module_deps()/ROOT_ARTIFACT_ID (see renderModuleDependencyGraph()
# and its accompanying table/notes in the HTML template below) -- one source (pom.xml), not a
# second, separately-extracted copy (user-requested unification, 2026-08-04; see
# docs/architecture/scripts/DECISIONS.md ADR-003/005). Its diagramGroups entry is synthesized directly below,
# with an empty "source" (nothing reads it -- the special-cased renderer never parses Mermaid text
# for this group) purely so the Diagrams list page has a card to link from.
# All four diagrams (Module Dependencies/SPI Map/Database ERD/Bounded Contexts) render live --
# no markdown source is parsed for the Diagrams list anymore.
