# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: ADR links per module read from the generated ADR index, and the thin pointer-only DECISIONS.md files for Maven modules that have no decision file of their own.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, sed, awk, grep.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: The ADR index and every module's DECISIONS.md.
# Outputs: defines adr_intent_for_module, all_adrs_json, json_adr_array, generate_pointer_decisions_md and the POINTER_DECISIONS_MODULES list; generate_pointer_decisions_md rewrites the pointer-only DECISIONS.md files in place.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── ADR intent-links per module (id + title), reusing adr-index.md (never reparsing DECISIONS.md,
#    per §14) -- emitted as {"id":..,"title":..,"file":..} objects: the human layer shows the real
#    title and a real link to the ADR's own real DECISIONS.md -- never a copy of the ADR text
#    itself. DECISIONS.md is the one place that text lives; this tool always resolves to it, it
#    never restates it (doc-standards/SKILL.md's own "reference by ADR number, never restate the
#    reasoning inline" rule -- an earlier version of this feature embedded the full body text, and
#    a version after that added a line number, both corrected -- see docs/architecture/scripts/DECISIONS.md
#    ADR-006). No line number: a raw .md opened via file:// has no heading anchors for a fragment
#    to jump to anyway (no markdown rendering happens), and a line number needs recalculating on
#    every edit to any ADR above it in the same file for a benefit that was never real navigation,
#    just a hint -- the ADR id itself, already the link's own visible text, is what a human
#    actually searches for (Ctrl+F) once the file is open, and it never goes stale. ──
adr_intent_for_module() {
  local module="$1"
  if [ -f "$ADR_INDEX" ]; then
    awk -F' \\| ' -v m="$module" '
      /^\| ADR-/ && $2 == m { sub(/^\| /, "", $1); sub(/ *\|$/, "", $6); print $1 "\x1f" $6 }
    ' "$ADR_INDEX"
  fi
}

# Flat, deduplicated list of every ADR across every module's DECISIONS.md, for the System screen's
# ADRs card/list -- reads .claude/nav/adr-index.md (same file adr_intent_for_module reads above), but
# keeps only each ADR's home-module row, skipping the extra "Also affects" cross-reference rows
# that table carries (same id string repeated once per affected module) so an ADR is counted once.
all_adrs_json() {
  local items_json="" first_i=true id module status applied title home
  if [ -f "$ADR_INDEX" ]; then
    while IFS=$'\x1f' read -r id module status applied title; do
      [ -z "$id" ] && continue
      home="$(echo "$id" | sed -n 's/^ADR-[0-9]\+ (\(.*\))$/\1/p')"
      [ -z "$home" ] && home="$module"
      [ "$module" != "$home" ] && continue
      $first_i || items_json="$items_json,"$'\n'
      first_i=false
      items_json="$items_json    {\"id\": \"$(json_escape "$id")\", \"module\": \"$(json_escape "$home")\", \"status\": \"$(json_escape "$status")\", \"applied\": ${applied:-null}, \"title\": \"$(json_escape "$title")\"}"
    done < <(awk -F' \\| ' '/^\| ADR-/ { sub(/^\| /, "", $1); sub(/ *\|$/, "", $6); print $1 "\x1f" $2 "\x1f" $3 "\x1f" $5 "\x1f" $6 }' "$ADR_INDEX")
  fi
  echo "[$items_json"$'\n'"  ]"
}

# Builds a JSON array of {"id":..,"title":..,"file":..} from "id\x1ftitle" lines; empty input ->
# "[]". "file" is the ADR's real home DECISIONS.md path -- extracted from the "(home-module)"
# parenthetical adr-index.md always carries on the id (e.g. "ADR-071 (marketplace-app)"), not from
# the queried $module, since a "**Also affects:**" cross-listed entry lives in a DIFFERENT file
# than the module it's being listed under (see generate-adr-index.sh).
json_adr_array() {
  local module="$1" items="$2" out="" first=true home
  while IFS=$'\x1f' read -r id title; do
    [ -z "$id" ] && continue
    home="$(echo "$id" | sed -n 's/^ADR-[0-9]\+ (\(.*\))$/\1/p')"
    [ -z "$home" ] && home="$module"
    $first || out="$out, "
    first=false
    out="$out{\"id\": \"$(json_escape "$id")\", \"title\": \"$(json_escape "$title")\", \"file\": \"$(json_escape "$home/DECISIONS.md")\"}"
  done <<< "$items"
  echo "[$out]"
}

# ── Thin, generated DECISIONS.md for Maven modules that have no real file of their own -- a pure
# pointer index (no ADR prose), listing whichever ADRs cross-reference this module via another
# module's own "**Also affects:**" tag (see generate-adr-index.sh). Generated, never hand-edited --
# adding a new cross-reference means tagging the real ADR in its home file, not this file.
POINTER_DECISIONS_MODULES=()
for m in "${MODULES[@]}"; do
  f="$REPO_ROOT/$m/DECISIONS.md"
  # Regenerate when there's no file at all, OR the existing file is itself a previously-generated
  # pointer (first-line marker below) -- a real, hand-authored DECISIONS.md never carries this
  # marker, so it's never touched. Without this content check, a pointer file that already exists
  # on disk from an earlier run would be skipped forever (mere -f existence looks identical to a
  # real DECISIONS.md), leaving it to silently drift out of sync with real "Also affects" tags.
  if [ ! -f "$f" ] || head -1 "$f" | grep -q '(generated index)$'; then
    POINTER_DECISIONS_MODULES+=("$m")
  fi
done

# ── FULL_DECISIONS_MODULES: every Maven module with real content (i.e. not in
# POINTER_DECISIONS_MODULES, computed just above) plus the fixed non-Maven tooling dirs. ──────────
for m in "${MODULES[@]}"; do
  is_pointer=false
  for p in "${POINTER_DECISIONS_MODULES[@]}"; do [ "$p" = "$m" ] && is_pointer=true; done
  $is_pointer || FULL_DECISIONS_MODULES+=("$m")
done
FULL_DECISIONS_MODULES+=("${FULL_DECISIONS_NON_MODULE_DIRS[@]}")

generate_pointer_decisions_md() {
  local module="$1" items home id title content
  items="$(adr_intent_for_module "$module")"
  content="$(
    echo "# $module — Decisions (generated index)"
    echo
    echo "This module has no \`DECISIONS.md\` of its own — decisions about it are recorded in"
    echo "other modules' files and cross-listed here via their own \`**Also affects:**\` tag."
    echo "Do not hand-edit this file — add \`**Also affects:** $module\` to the real ADR in its"
    echo "home file instead, then regenerate via \`bash docs/architecture/scripts/generate-architecture-model.sh\`."
    echo
    if [ -z "$items" ]; then
      echo "No ADRs currently cross-reference this module."
    else
      while IFS=$'\x1f' read -r id title; do
        [ -z "$id" ] && continue
        home="$(echo "$id" | sed -n 's/^ADR-[0-9]\+ (\(.*\))$/\1/p')"
        [ -z "$home" ] && home="$module"
        echo "- [$id](../$home/DECISIONS.md) — $title"
      done <<< "$items"
    fi
  )"
  # A write-permission failure here is an environment quirk (observed on a Windows/WSL checkout
  # where one module directory silently rejects writes even from bash, with no NTFS attribute or
  # ACL visibly explaining it) -- not worth hard-failing the entire model generation over one
  # low-stakes, fully-regenerable pointer file. Warn and move on instead of letting `set -e` abort.
  if ! printf '%s\n' "$content" > "$REPO_ROOT/$module/DECISIONS.md" 2>/dev/null; then
    echo "WARNING: could not write $module/DECISIONS.md (write-permission issue in this" \
         "environment) -- leaving the existing file as-is, skipping this module's pointer refresh." >&2
  fi
}
for m in "${POINTER_DECISIONS_MODULES[@]}"; do generate_pointer_decisions_md "$m"; done

