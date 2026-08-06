#!/usr/bin/env bash
# Description: Generates architecture-model.json and architecture-map.html -- the live, browsable
#   architecture control plane -- from real repo state, no hand-maintained markdown.
# Uses: bash, node (invokes liquibase-schema-to-json.js and md-to-decisions-json.js as
#   subprocesses), python3 (only when --with-sonar/--with-archunit are passed).
# Input: pom.xml, real Java source + Javadoc, Liquibase changelogs, every module's DECISIONS.md,
#   docs/ai/adr-index.md, docs/ai/flows.md, .claude/commands, .claude/skills, backlog/.
# Output: docs/architecture/architecture-model.json + docs/architecture/architecture-map.html.
#
# Generates architecture-model.json (Track A of the architecture control plane) from
# already-structured, non-code sources only -- no ArchUnit, no bytecode analysis. Node types:
# MODULE (from pom.xml reactor + per-module pom.xml dependencies), COMMAND/SKILL (from
# .claude/commands, .claude/skills, cross-checked against docs/ai/flows.md), and one BACKLOG
# summary node. Per-ADR/per-issue graph nodes are deliberately not built -- the issue/ADR count
# would blow past a "tens of nodes, not thousands" budget -- ADRs are folded into each module's
# own `intent[]` list instead, reusing adr-index.md rather than reparsing every DECISIONS.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="$REPO_ROOT/docs/architecture/architecture-model.json"
HTML_OUTPUT="$REPO_ROOT/docs/architecture/architecture-map.html"
ADR_INDEX="$REPO_ROOT/docs/ai/adr-index.md"
FLOWS="$REPO_ROOT/docs/ai/flows.md"
ROOT_CLAUDE_MD="$REPO_ROOT/CLAUDE.md"

# Opt-in Sonar/ArchUnit fetch -- both off by default so a plain run never triggers a SonarQube
# rescan or depends on unit-tests.sh having run recently. See scripts/architecture/DECISIONS.md.
WITH_SONAR=""
WITH_ARCHUNIT=""
for arg in "$@"; do
  case "$arg" in
    --with-sonar) WITH_SONAR=1 ;;
    --with-archunit) WITH_ARCHUNIT=1 ;;
  esac
done

# The 6 real Liquibase changelogs holding an actual createTable -- single source of truth for the
# live Database ERD (see root CLAUDE.md's "Database Changes" guideline). Declared early so the
# module-table-ownership computation below can use it too, not just db_erd_json() further down.
DB_ERD_CHANGELOG_FILES=(
  "user-spring-boot-starter/src/main/resources/db/user-changelog/changes/01-user-schema.xml"
  "advertisement-spring-boot-starter/src/main/resources/db/advertisement-changelog/changes/01-advertisement-schema.xml"
  "attachment-spring-boot-starter/src/main/resources/db/attachment-changelog/changes/01-attachment-schema.xml"
  "audit-spring-boot-starter/src/main/resources/db/audit-changelog/changes/01-audit-schema.xml"
  "taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/001-taxon.xml"
  "provider-profile-spring-boot-starter/src/main/resources/db/provider-profile-changelog/changes/01-provider-profile-schema.xml"
)

json_escape() {
  # Escapes backslash and double-quote, strips newlines/carriage-returns and other C0 control
  # bytes -- sufficient for the plain-text fields this script emits (file paths, short prose
  # lines); not a general-purpose JSON encoder. LC_ALL=C keeps tr from mangling multi-byte UTF-8
  # (em-dashes etc. in command descriptions) while still stripping 0x00-0x1F control bytes.
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | LC_ALL=C tr -d '\000-\037'
}

# ── Full ADR content (Knowledge Pyramid L1 fact, "decisions" domain) for modules listed here --
# embedded directly into this module's node in architecture-model.json (inlined into
# architecture-map.html's own <script> block at generation time, same mechanism the rest of MODEL
# already uses) -- NOT a separate <module>/DECISIONS.json loaded at runtime via <script src>. That
# design was tried and reverted: it depends on browser-specific file:// security policy for
# cross-directory script loading, an unacceptable dependency for a tool meant to just work when
# double-clicked -- see scripts/architecture/DECISIONS.md ADR-008. Parsing lives in md-to-decisions-json.js
# (Node) -- an earlier awk version hit two real bugs on real content (label+list with no blank
# line merging into one paragraph; multi-line list items losing their numbering) that
# JSON.stringify()'s correct-by-construction escaping and normal regex/string methods avoid.
FULL_DECISIONS_MODULES=(attachment-spring-boot-starter audit-spring-boot-starter integration-tests marketplace-app platform-commons query-lib taxon-spring-boot-starter scripts scripts/architecture scripts/ci scripts/sonar playwright)

# ── Non-Maven tooling directories -- get a SCRIPT_GROUP node (same ADR-embedding/popup mechanism
# as MODULE nodes) so their files/decisions are visible on the Tooling & Pipelines screen, not
# invisible outside the interactive tool. "category" drives the page's AI Tooling vs Other Scripts
# split. Not every SCRIPT_GROUP dir has its own DECISIONS.md -- scripts/ai's own history moved to
# scripts/architecture/DECISIONS.md wholesale (see scripts/architecture/DECISIONS.md ADR-021), so
# scripts/ai now gets a files-only node, no ADR/decisions section (decisions_json_for/
# adr_intent_for_module both degrade to empty for a module with no DECISIONS.md, not a special
# case here).
declare -A SCRIPT_GROUP_CATEGORY=(
  [scripts/ai]="ai"
  [scripts/architecture]="ai"
  [scripts]="scripts"
  [scripts/ci]="scripts"
  [scripts/sonar]="scripts"
  [playwright]="scripts"
)
SCRIPT_GROUP_DIRS=(scripts/ai scripts/architecture scripts scripts/ci scripts/sonar playwright)

# Explicit "what matters first" ordering per directory -- entry points and generators before the
# CI gates that verify their output, dev-only tooling last. Falls back to alphabetical (find |
# sort) for any directory not listed here.
declare -A SCRIPT_GROUP_FILE_ORDER=(
  [scripts/ai]="generate-adr-index.sh check-adr-index-freshness.sh check-hardcoded-counts.sh check-flows-completeness.sh"
  [scripts/architecture]="generate-architecture-model.sh md-to-decisions-json.js liquibase-schema-to-json.js check-architecture-model-freshness.sh screenshot-architecture-map.sh"
)
decisions_json_for() {
  local module="$1"
  [ -f "$REPO_ROOT/$module/DECISIONS.md" ] || { echo "null"; return; }
  local found=false
  for m in "${FULL_DECISIONS_MODULES[@]}"; do [ "$m" = "$module" ] && found=true; done
  $found || { echo "null"; return; }
  node "$REPO_ROOT/scripts/architecture/md-to-decisions-json.js" --stdout "$module"
}

# ── Module list, in pom.xml reactor order ───────────────────────────────────────────────────
mapfile -t MODULES < <(sed -n '/<modules>/,/<\/modules>/p' "$REPO_ROOT/pom.xml" \
  | grep -o '<module>[^<]*</module>' | sed 's/<module>\(.*\)<\/module>/\1/')

# ── Domain <-> module mapping, derived live from the real module list above using this repo's own
# "<domain>-spring-boot-starter" naming convention -- module names ARE the domain names for every
# starter, no separate map to hand-maintain (see scripts/architecture/DECISIONS.md ADR-019 "Open goals").
# Shared (platform-commons) and UI (marketplace-app) are structural categories, not
# "*-spring-boot-starter" domain modules, so they're seeded explicitly; everything else (domain id,
# human label, ordering) comes straight out of $MODULES.
declare -A BC_DOMAIN_MODULE=([Shared]=platform-commons [UI]=marketplace-app)
declare -A BC_DOMAIN_LABEL=([Shared]="Shared Kernel" [UI]="UI/Application Layer")
BC_DOMAIN_ORDER=(Shared)
for bc_mod in "${MODULES[@]}"; do
  [[ "$bc_mod" == *-spring-boot-starter ]] || continue
  bc_word="${bc_mod%-spring-boot-starter}"
  bc_id="" bc_label_words=()
  IFS='-' read -ra bc_parts <<< "$bc_word"
  for bc_part in "${bc_parts[@]}"; do
    bc_id="$bc_id${bc_part^}"
    bc_label_words+=("${bc_part^}")
  done
  BC_DOMAIN_MODULE["$bc_id"]="$bc_mod"
  BC_DOMAIN_LABEL["$bc_id"]="$(IFS=' '; echo "${bc_label_words[*]}") Domain"
  BC_DOMAIN_ORDER+=("$bc_id")
done
BC_DOMAIN_ORDER+=(UI)
# The domain-starter subset (excludes the two structural categories) -- used wherever a
# relationship rule applies uniformly to "every real domain" (e.g. Shared's "decouples" edges,
# UI's "calls" edges), so that list isn't hand-typed a second time either.
BC_DOMAIN_ORDER_STARTERS=("${BC_DOMAIN_ORDER[@]:1:$((${#BC_DOMAIN_ORDER[@]}-2))}")
# EntityType enum value -> the bounded-context domain it belongs to (platform-commons/core/model/EntityType.java).
declare -A BC_ENTITY_TYPE_DOMAIN=(
  [ADVERTISEMENT]=Advertisement [USER]=User [USER_SETTINGS]=User [TAXON]=Taxon [PROVIDER_PROFILE]=ProviderProfile
)
# What actually crosses each relationship, keyed by the relationship label -- checked directly
# against the real Port method signatures, not guessed (AuditPort.capture*() all take
# AuditableSnapshot; AttachmentPort.getMediaSummaries() returns AttachmentMediaSummaryDto;
# TaxonPort.replaceAssignments() takes a Set<Long> of taxon ids; AuditActivityFieldsHook/
# AuditActivityEnrichHook return ChangeEntry/AuditTimelineItemDto).
declare -A BC_LABEL_PAYLOAD=(
  ["decouples"]="Compile-time dependency only -- no runtime payload"
  ["audited via"]="AuditableSnapshot (AuditPort.captureCreation/Update/Deletion/Restore)"
  ["can have"]="AttachmentMediaSummaryDto (AttachmentPort.getMediaSummaries)"
  ["category assignment via"]="Set<Long> of taxon ids (TaxonPort.replaceAssignments)"
  ["calls"]="Whatever DTO that domain's own Port methods return -- varies per call, see SPI Map for the real per-method types"
  ["calls back via Hook implementations"]="List<ChangeEntry>/field labels (AuditActivityFieldsHook) or merged List<AuditTimelineItemDto> (AuditActivityEnrichHook)"
)

# ── Domain grouping + Entities/Key Services/Contract lists per module -- live, same real signals
# bounded_contexts_json() uses further down (real @Table classes, real *Service classes, real SPI
# interface `implements` relationships), just keyed by module instead of by domain and reduced to
# bare names (no file link -- the Module screen shows these as plain labels, not a linked list).
declare -A MODULE_DOMAIN MODULE_KEYSERVICES MODULE_CONTRACT MODULE_ENTITY
for bc_d in "${BC_DOMAIN_ORDER[@]}"; do
  bc_m="${BC_DOMAIN_MODULE[$bc_d]}"
  MODULE_DOMAIN["$bc_m"]="${BC_DOMAIN_LABEL[$bc_d]}"
  [ "$bc_d" = "Shared" ] || [ "$bc_d" = "UI" ] && continue
  MODULE_ENTITY["$bc_m"]="$( (grep -rl '^@Table\|@Table(' "$REPO_ROOT/$bc_m/src/main/java" --include='*.java' 2>/dev/null || true) | sort | xargs -r -n1 basename | sed 's/\.java$//')"$'\n'
  MODULE_KEYSERVICES["$bc_m"]="$(find "$REPO_ROOT/$bc_m/src/main/java" -name '*Service.java' 2>/dev/null | sort | xargs -r -n1 basename | sed 's/\.java$//')"$'\n'
  MODULE_CONTRACT["$bc_m"]="$( (find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java' | sort | while read -r ifile; do
    iface="$(basename "$ifile" .java)"
    grep -qlP "implements\s+.*\b${iface}\b" -r --include='*.java' "$REPO_ROOT/$bc_m/src/main/java" 2>/dev/null && echo "$iface"
    true
  done) || true)"$'\n'
done
# Structural modules the domain map above doesn't cover -- fallback labels, confidence: manual
# either way (both paths are heuristic, not extracted).
MODULE_DOMAIN["marketplace-app"]="${MODULE_DOMAIN[marketplace-app]:-UI/Application Layer}"
MODULE_DOMAIN["query-lib"]="${MODULE_DOMAIN[query-lib]:-Shared Kernel}"
MODULE_DOMAIN["integration-tests"]="${MODULE_DOMAIN[integration-tests]:-Testing}"

# ── Table ownership per module, live from the real Liquibase changelogs (table name + module are
# both derivable straight from liquibase-schema-to-json.js's output -- no markdown parsing needed).
declare -A MODULE_TABLES
while IFS=$'\t' read -r tbl_module tbl_name; do
  [ -z "$tbl_name" ] && continue
  MODULE_TABLES["$tbl_module"]="${MODULE_TABLES[$tbl_module]:-}$tbl_name"$'\n'
done < <(
  files=()
  for f in "${DB_ERD_CHANGELOG_FILES[@]}"; do files+=("$REPO_ROOT/$f"); done
  node "$REPO_ROOT/scripts/architecture/liquibase-schema-to-json.js" "$REPO_ROOT" "${files[@]}" \
    | node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>JSON.parse(d).forEach(t=>console.log(t.module+"\t"+t.name)))'
)

# ── One-line module descriptions, reused from root CLAUDE.md's "Module Layout" ASCII tree ──────
# (already a clean, one-line-per-module, consistently-formatted description -- no need to write a
# second copy).
declare -A MODULE_DESCRIPTION
if [ -f "$ROOT_CLAUDE_MD" ]; then
  while IFS= read -r line; do
    line="${line%$'\r'}"
    if [[ "$line" =~ ^[├└]──\ ([a-z0-9-]+)[[:space:]]+—\ (.+)$ ]]; then
      MODULE_DESCRIPTION["${BASH_REMATCH[1]}"]="${BASH_REMATCH[2]}"
    fi
  done < "$ROOT_CLAUDE_MD"
fi

# ── ADR intent-links per module (id + title), reusing adr-index.md (never reparsing DECISIONS.md,
#    per §14) -- emitted as {"id":..,"title":..,"file":..} objects: the human layer shows the real
#    title and a real link to the ADR's own real DECISIONS.md -- never a copy of the ADR text
#    itself. DECISIONS.md is the one place that text lives; this tool always resolves to it, it
#    never restates it (doc-standards/SKILL.md's own "reference by ADR number, never restate the
#    reasoning inline" rule -- an earlier version of this feature embedded the full body text, and
#    a version after that added a line number, both corrected -- see scripts/architecture/DECISIONS.md
#    ADR-006). No line number: a raw .md opened via file:// has no heading anchors for a fragment
#    to jump to anyway (no markdown rendering happens), and a line number needs recalculating on
#    every edit to any ADR above it in the same file for a benefit that was never real navigation,
#    just a hint -- the ADR id itself, already the link's own visible text, is what a human
#    actually searches for (Ctrl+F) once the file is open, and it never goes stale. ──
adr_intent_for_module() {
  local module="$1"
  if [ -f "$ADR_INDEX" ]; then
    awk -F' \\| ' -v m="$module" '
      /^\| ADR-/ && $2 == m { sub(/^\| /, "", $1); sub(/ *\|$/, "", $4); print $1 "\x1f" $4 }
    ' "$ADR_INDEX"
  fi
}

# Flat, deduplicated list of every ADR across every module's DECISIONS.md, for the System screen's
# ADRs card/list -- reads docs/ai/adr-index.md (same file adr_intent_for_module reads above), but
# keeps only each ADR's home-module row, skipping the extra "Also affects" cross-reference rows
# that table carries (same id string repeated once per affected module) so an ADR is counted once.
all_adrs_json() {
  local items_json="" first_i=true id module status title home
  if [ -f "$ADR_INDEX" ]; then
    while IFS=$'\x1f' read -r id module status title; do
      [ -z "$id" ] && continue
      home="$(echo "$id" | sed -n 's/^ADR-[0-9]\+ (\(.*\))$/\1/p')"
      [ -z "$home" ] && home="$module"
      [ "$module" != "$home" ] && continue
      $first_i || items_json="$items_json,"$'\n'
      first_i=false
      items_json="$items_json    {\"id\": \"$(json_escape "$id")\", \"module\": \"$(json_escape "$home")\", \"status\": \"$(json_escape "$status")\", \"title\": \"$(json_escape "$title")\"}"
    done < <(awk -F' \\| ' '/^\| ADR-/ { sub(/^\| /, "", $1); sub(/ *\|$/, "", $4); print $1 "\x1f" $2 "\x1f" $3 "\x1f" $4 }' "$ADR_INDEX")
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
  [ -f "$REPO_ROOT/$m/DECISIONS.md" ] || POINTER_DECISIONS_MODULES+=("$m")
done
generate_pointer_decisions_md() {
  local module="$1" items home id title
  items="$(adr_intent_for_module "$module")"
  {
    echo "# $module — Decisions (generated index)"
    echo
    echo "This module has no \`DECISIONS.md\` of its own — decisions about it are recorded in"
    echo "other modules' files and cross-listed here via their own \`**Also affects:**\` tag."
    echo "Do not hand-edit this file — add \`**Also affects:** $module\` to the real ADR in its"
    echo "home file instead, then regenerate via \`bash scripts/architecture/generate-architecture-model.sh\`."
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
  } > "$REPO_ROOT/$module/DECISIONS.md"
}
for m in "${POINTER_DECISIONS_MODULES[@]}"; do generate_pointer_decisions_md "$m"; done

# ── Per-module dependency extraction from each module's own pom.xml ────────────────────────────
# State machine over one-line-per-tag <dependency>...</dependency> blocks (this repo's consistent
# pom.xml formatting, confirmed by direct inspection) -- not a general XML parser.
module_deps() {
  local module="$1"
  local pom="$REPO_ROOT/$module/pom.xml"
  [ -f "$pom" ] || return 0
  awk -v mods="${MODULES_JOINED}" '
    BEGIN { split(mods, modarr, ",") }
    /<dependency>/ { artifact=""; scope="compile"; optional="false"; next }
    /<artifactId>/ {
      gsub(/.*<artifactId>|<\/artifactId>.*/, "", $0)
      artifact = $0
      next
    }
    /<scope>/ {
      gsub(/.*<scope>|<\/scope>.*/, "", $0)
      scope = $0
      next
    }
    /<optional>true<\/optional>/ { optional = "true"; next }
    /<\/dependency>/ {
      is_module = 0
      for (i in modarr) if (modarr[i] == artifact) is_module = 1
      if (is_module) print artifact "|" scope "|" optional
    }
  ' "$pom"
}
MODULES_JOINED="$(IFS=,; echo "${MODULES[*]}")"

# ── Root pom.xml's own artifactId/version (not the Spring Boot parent's) -- the project's own
# <artifactId>/<version> lines are the two immediately after </parent> closes. Feeds the Module
# Dependencies diagram page's "Module Versions" line so it's mechanical, not hand-typed. ──────────
ROOT_ARTIFACT_ID="$(awk '/<\/parent>/{f=1;next} f && /<artifactId>/{gsub(/.*<artifactId>|<\/artifactId>.*/,""); print; exit}' "$REPO_ROOT/pom.xml")"
ROOT_VERSION="$(awk '/<\/parent>/{f=1;next} f && /<version>/{gsub(/.*<version>|<\/version>.*/,""); print; exit}' "$REPO_ROOT/pom.xml")"

# ── Reverse dependency lookup ("who depends on me") -- for the human drill-down view; the pom.xml
#    dependency direction alone only tells a reader "what do I depend on," not "what would break if
#    I changed" -- the latter is exactly the question the plan's own §4 "blast-radius" goal names. ──
reverse_deps_for_module() {
  local target="$1"
  for m in "${MODULES[@]}"; do
    [ "$m" = "$target" ] && continue
    if module_deps "$m" | awk -F'|' -v t="$target" '$1==t{found=1} END{exit !found}'; then
      echo "$m"
    fi
  done
}

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

# Builds a JSON array of {"name","file"} from newline-separated "name<TAB>file" pairs -- for lists
# that need to render as real links (client-side sourceLink()), not just plain labels.
json_named_file_array() {
  local items="$1" out="" first=true name file
  while IFS=$'\t' read -r name file; do
    [ -z "$name" ] && continue
    $first || out="$out, "
    first=false
    if [ -n "$file" ]; then
      out="$out{\"name\": \"$(json_escape "$name")\", \"file\": \"$(json_escape "$file")\"}"
    else
      out="$out{\"name\": \"$(json_escape "$name")\"}"
    fi
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
# scripts/architecture/DECISIONS.md ADR-003/005). Its diagramGroups entry is synthesized directly below,
# with an empty "source" (nothing reads it -- the special-cased renderer never parses Mermaid text
# for this group) purely so the Diagrams list page has a card to link from.
# All four diagrams (Module Dependencies/SPI Map/Database ERD/Bounded Contexts) render live --
# no markdown source is parsed for the Diagrams list anymore.
diagram_groups_json="  {\"key\": \"01-module-dependencies\", \"label\": \"Module Dependencies\", \"file\": \"pom.xml (live)\", \"diagrams\": [{\"title\": \"Dependency Graph\", \"source\": \"\"}]},"$'\n'"  {\"key\": \"02-spi-map\", \"label\": \"SPI Map\", \"file\": \"platform-commons/src (live)\", \"diagrams\": [{\"title\": \"SPI Dependency Graph\", \"source\": \"\"}]},"$'\n'"  {\"key\": \"04-database-erd\", \"label\": \"Database ERD\", \"file\": \"Liquibase changelogs (live)\", \"diagrams\": [{\"title\": \"Entity Relationship Diagram\", \"source\": \"\"}]},"$'\n'"  {\"key\": \"bounded-contexts\", \"label\": \"Bounded Contexts\", \"file\": \"real code (live)\", \"diagrams\": [{\"title\": \"Context Map\", \"source\": \"\"}]}"

# ── SPI Map: mechanically extracted from real Java source, same "live from real source, not a
# separately-maintained .md" pattern as Module Dependencies (01) -- every *.spi interface under
# platform-commons + every real `implements` of it across the starters/marketplace-app, via grep
# (text-pattern matching, not full semantic/bytecode analysis -- same bar as module_deps()).
# "Purpose" one-liners are the one genuinely-editorial part with no mechanical source, carried over
# from the retired docs/architecture/02-spi-map.md as a static lookup, same exception Module
# Dependencies' Key Observations already established.
declare -A SPI_SUBSYSTEM_LABEL=(
  [audit]="Audit Subsystem"
  [attachment]="Attachment Subsystem"
  [user]="User Subsystem"
  [advertisement]="Advertisement Subsystem"
  [taxon]="Taxon (Reference Data) Subsystem"
  [providerprofile]="Provider Profile Subsystem"
  [core]="Core / Platform"
)
# Subsystem-level editorial notes carried over verbatim -- explain a non-obvious absence (Attachment
# has no starter->marketplace media-change callback) or a design rationale (User's 4-port split) that
# the mechanical per-interface extraction has no way to produce on its own.
declare -A SPI_SUBSYSTEM_NOTE=(
  [attachment]="AttachmentMediaChangeHook does not exist -- there is no starter->marketplace media-change callback. Media summaries are computed at read time via AttachmentPort.getMediaSummaries() instead (see marketplace-app/DECISIONS.md ADR-035)."
  [user]="Split into 4 narrow ports (see platform-commons/DECISIONS.md ADR-026 for the rationale -- interface cohesion, not runtime-toggle behavior; all 4 are always implemented by user-spring-boot-starter)."
)
spi_kind_for() {
  case "$1" in
    *Port) echo "Port (marketplace -> starter)" ;;
    *Hook) echo "Hook (starter -> marketplace)" ;;
    *) echo "type contract" ;;
  esac
}

# Live from the real Javadoc immediately above the interface declaration -- single source of
# truth lives next to the code, not duplicated in this generator. Walks backward from the
# "interface X" line past blank/annotation lines (handles @FunctionalInterface) to find a
# preceding "*/", then further back to the matching "/**"; strips "*"/"{@code}"/"{@link}" markup.
# Every *.spi interface is expected to carry one -- see platform-commons/CLAUDE.md.
spi_javadoc_purpose_for() {
  local file="$1"
  awk '
    /^[[:space:]]*(public[[:space:]]+)?interface[[:space:]]+/ && !found { target=NR; found=1 }
    { lines[NR] = $0 }
    END {
      if (!found) { exit }
      i = target - 1
      while (i > 0 && (lines[i] ~ /^[[:space:]]*$/ || lines[i] ~ /^[[:space:]]*@/)) i--
      if (lines[i] !~ /\*\/[[:space:]]*$/) exit
      end = i
      while (i > 0 && lines[i] !~ /^[[:space:]]*\/\*\*/) i--
      start = i
      if (lines[start] !~ /^[[:space:]]*\/\*\*/) exit
      out = ""
      for (j = start; j <= end; j++) {
        line = lines[j]
        gsub(/^[[:space:]]*\/\*\*[[:space:]]*/, "", line)
        gsub(/[[:space:]]*\*\/[[:space:]]*$/, "", line)
        gsub(/^[[:space:]]*\*[[:space:]]?/, "", line)
        if (line != "") {
          out = (out == "" ? line : out " " line)
        }
      }
      gsub(/\{@code /, "", out); gsub(/\{@link /, "", out); gsub(/\}/, "", out)
      print out
    }
  ' "$file"
}

spi_map_json() {
  local nodes="" edges="" details="" first_node=true first_edge=true first_detail=true
  local -A group_seen=()
  local iface_file iface impl_file impl module kind pkg subsystem
  for iface_file in $(find "$REPO_ROOT/platform-commons/src/main/java" -path "*/spi/*.java" | sort); do
    iface="$(basename "$iface_file" .java)"
    kind="$(spi_kind_for "$iface")"
    pkg="$(grep -m1 "^package " "$iface_file" | sed 's/^package *//;s/;.*//')"
    subsystem="$(echo "$pkg" | sed -n 's/^org\.ost\.platform\.\([a-z]*\)\.spi$/\1/p')"
    if [ -z "${group_seen[platform-commons]:-}" ]; then
      group_seen[platform-commons]=1
      $first_node || nodes="$nodes,"$'\n'
      first_node=false
      nodes="$nodes    {\"id\": \"platform-commons\", \"label\": \"platform-commons\", \"isGroup\": true}"
    fi
    $first_node || nodes="$nodes,"$'\n'
    first_node=false
    nodes="$nodes    {\"id\": \"$(json_escape "$iface")\", \"label\": \"$(json_escape "$iface")\", \"parent\": \"platform-commons\", \"file\": \"$(json_escape "${iface_file#"$REPO_ROOT"/}")\"}"

    local impls_json="" first_impl=true
    while IFS= read -r impl_file; do
      [ -z "$impl_file" ] && continue
      impl="$(basename "$impl_file" .java)"
      module="${impl_file#"$REPO_ROOT"/}"
      module="${module%%/*}"
      if [ -z "${group_seen[$module]:-}" ]; then
        group_seen[$module]=1
        nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$module")\", \"label\": \"$(json_escape "$module")\", \"isGroup\": true}"
      fi
      nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$impl")\", \"label\": \"$(json_escape "$impl")\", \"parent\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "${impl_file#"$REPO_ROOT"/}")\"}"
      $first_edge || edges="$edges,"$'\n'
      first_edge=false
      edges="$edges    {\"source\": \"$(json_escape "$iface")\", \"target\": \"$(json_escape "$impl")\"}"
      $first_impl || impls_json="$impls_json, "
      first_impl=false
      impls_json="$impls_json{\"class\": \"$(json_escape "$impl")\", \"module\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "${impl_file#"$REPO_ROOT"/}")\"}"
    done < <(grep -rlP "implements\s+.*\b${iface}\b" "$REPO_ROOT"/*-spring-boot-starter/src/main/java "$REPO_ROOT"/marketplace-app/src/main/java 2>/dev/null | sort -u)

    $first_detail || details="$details,"$'\n'
    first_detail=false
    details="$details    {\"interface\": \"$(json_escape "$iface")\", \"file\": \"$(json_escape "${iface_file#"$REPO_ROOT"/}")\", \"package\": \"$(json_escape "$pkg")\", \"subsystem\": \"$(json_escape "$subsystem")\", \"kind\": \"$(json_escape "$kind")\", \"purpose\": \"$(json_escape "$(spi_javadoc_purpose_for "$iface_file")")\", \"implementations\": [$impls_json]}"
  done
  local subsystem_order=(audit attachment user advertisement taxon providerprofile core)
  local labels_json="" notes_json="" first_s=true
  for s in "${subsystem_order[@]}"; do
    $first_s || labels_json="$labels_json, "
    $first_s || notes_json="$notes_json, "
    first_s=false
    labels_json="$labels_json\"$s\": \"$(json_escape "${SPI_SUBSYSTEM_LABEL[$s]}")\""
    notes_json="$notes_json\"$s\": \"$(json_escape "${SPI_SUBSYSTEM_NOTE[$s]:-}")\""
  done
  echo "{"
  echo "  \"nodes\": [${nodes}"$'\n'"  ],"
  echo "  \"edges\": [${edges}"$'\n'"  ],"
  echo "  \"details\": [${details}"$'\n'"  ],"
  echo "  \"subsystemOrder\": $(json_str_array "$(printf '%s\n' "${subsystem_order[@]}")"),"
  echo "  \"subsystemLabels\": {$labels_json},"
  echo "  \"subsystemNotes\": {$notes_json}"
  echo "}"
}

# Three narrative call traces, carried over verbatim from the retired 02-spi-map.md -- genuinely
# editorial content (a specific example path through the system) with no mechanical source.
spi_call_flow_examples_json() {
  cat <<'EOF'
[
  {"title": "Create Advertisement with Audit", "steps": ["marketplace-app (UI) calls AdvertisementPort.save()", "org.ost.advertisement.spi.AdvertisementPortImpl", "org.ost.advertisement.services.AdvertisementService.save()", "org.ost.audit.services.DefaultAuditPort.captureCreation()", "org.ost.marketplace.spi.AuditDomainHookImpl.on(CREATED, ...)", "marketplace-app (custom domain handlers)"]},
  {"title": "Upload Media to Advertisement", "steps": ["marketplace-app (UI) calls AttachmentPort.upload()", "org.ost.attachment.spi.DefaultAttachmentPort", "org.ost.attachment.services.AttachmentService.save()", "Media summaries are never stored on the advertisement row -- computed at read time: AdvertisementService.enrichWithMediaSummary() -> AttachmentPort.getMediaSummaries() (bulk lookup, one query per list render)"]},
  {"title": "Enrich Audit Activity", "steps": ["marketplace-app (viewing activity feed) calls AuditPort.getEntityActivity()", "org.ost.audit.services.DefaultAuditPort", "calls AuditActivityFieldsHook.fields() for each activity item", "org.ost.marketplace.spi.AdvertisementActivityFieldsHookImpl", "returns field labels: \"Title\", \"Description\", etc."]}
]
EOF
}

# ── Database ERD: live from the real Liquibase changelogs (table/column/type/constraints/FKs/
# indexes/remarks -- single source of truth, see root CLAUDE.md's "Database Changes" guideline and
# the sibling SPI Javadoc convention above). What's NOT mechanically derivable: cross-table
# relationships with no real SQL-level FK (this codebase deliberately decouples actor-reference
# columns -- advertisement.created_by, audit_log.actor_id, provider_profile.city_taxon_id, etc. --
# see marketplace-app/DECISIONS.md ADR-034/ADR-035). Those stay a hand-preserved list, carried over
# verbatim from the retired 04-database-erd.md's ER diagram -- same exception class as
# spi_call_flow_examples_json() above. Real FKs (taxon_translation/taxon_assignment -> taxon,
# user_information's self-referential deleted_by) are NOT duplicated here -- they come live from
# liquibase-schema-to-json.js.
db_erd_conceptual_relationships_json() {
  cat <<'EOF'
[
  {"from": "USER_INFORMATION", "to": "ADVERTISEMENT", "label": "creates (created_by, no FK)"},
  {"from": "USER_INFORMATION", "to": "ADVERTISEMENT", "label": "modifies (updated_by, no FK)"},
  {"from": "USER_INFORMATION", "to": "ADVERTISEMENT", "label": "deletes (deleted_by, no FK)"},
  {"from": "USER_INFORMATION", "to": "USER_PREFERENCES", "label": "has (actor_id, no FK)"},
  {"from": "USER_INFORMATION", "to": "PROVIDER_PROFILE", "label": "has (actor_id, no FK)"},
  {"from": "ADVERTISEMENT", "to": "ATTACHMENT", "label": "owns (entity_type/entity_id, generic, no FK)"},
  {"from": "ATTACHMENT", "to": "ATTACHMENT_SNAPSHOT", "label": "has_snapshots (entity_type/entity_id, no FK)"},
  {"from": "AUDIT_LOG", "to": "USER_INFORMATION", "label": "actor_is (actor_id, no FK)"},
  {"from": "PROVIDER_PROFILE", "to": "TAXON", "label": "city_taxon_id (no FK)"}
]
EOF
}
db_erd_json() {
  local files=()
  local f
  for f in "${DB_ERD_CHANGELOG_FILES[@]}"; do files+=("$REPO_ROOT/$f"); done
  local tables_json relationships_json
  tables_json="$(node "$REPO_ROOT/scripts/architecture/liquibase-schema-to-json.js" "$REPO_ROOT" "${files[@]}")"
  relationships_json="$(db_erd_conceptual_relationships_json)"
  echo "{\"tables\": $tables_json, \"conceptualRelationships\": $relationships_json}"
}

# ── SonarQube: real code-quality metrics (ncloc/complexity/cognitive_complexity/code_smells,
# project-level duplication) from a running SonarQube server, via its stable
# /api/measures/component_tree endpoint (one call returns every real package-level DIR component's
# metrics; grouped and summed here into a per-module rollup, since SonarQube has no aggregate
# component for e.g. "advertisement-spring-boot-starter/src/main/java" itself -- only real leaf
# package directories are indexed). Gracefully degrades to null if the server isn't reachable even
# after ensure_sonar_fresh()'s attempt -- optional data, never blocks generation.
SONAR_URL="http://localhost:9099"
SONAR_PROPS="$REPO_ROOT/scripts/sonar/sonar-project.properties"

ensure_sonar_fresh() {
  [ -f "$SONAR_PROPS" ] || return 0
  local token
  token="$(grep '^sonar.token=' "$SONAR_PROPS" 2>/dev/null | cut -d= -f2 | tr -d '\r')"
  [ -z "$token" ] && return 0
  if ! curl -s --max-time 3 -o /dev/null "$SONAR_URL/api/system/status"; then
    echo "SonarQube not reachable -- running bash scripts/sonar.sh --no-gate to start/scan it..." >&2
    bash "$REPO_ROOT/scripts/sonar.sh" --no-gate >&2 || true
    return 0
  fi
  local analysis_date newest_java stamp_file
  analysis_date="$(curl -s -u "$token:" --max-time 5 "$SONAR_URL/api/project_analyses/search?project=advertisement&ps=1" 2>/dev/null | grep -oP '"date":"\K[^"]+' | head -1)"
  if [ -z "$analysis_date" ]; then
    echo "No SonarQube analysis found -- running bash scripts/sonar.sh --no-gate..." >&2
    bash "$REPO_ROOT/scripts/sonar.sh" --no-gate >&2 || true
    return 0
  fi
  stamp_file="$(mktemp)"
  touch -d "$analysis_date" "$stamp_file" 2>/dev/null
  newest_java="$(find "$REPO_ROOT" -name '*.java' -not -path '*/target/*' -newer "$stamp_file" 2>/dev/null | head -1)"
  rm -f "$stamp_file"
  if [ -n "$newest_java" ]; then
    echo "SonarQube data stale ($newest_java changed since last scan) -- rescanning via bash scripts/sonar.sh --no-gate..." >&2
    bash "$REPO_ROOT/scripts/sonar.sh" --no-gate >&2 || true
  fi
}

sonar_metrics_json() {
  local token
  [ -f "$SONAR_PROPS" ] && token="$(grep '^sonar.token=' "$SONAR_PROPS" 2>/dev/null | cut -d= -f2 | tr -d '\r')"
  if [ -z "${token:-}" ] || ! curl -s --max-time 3 -o /dev/null "$SONAR_URL/api/system/status"; then
    echo "null"
    return 0
  fi
  local analysis_date project_json tree_json file_counts mod count
  analysis_date="$(curl -s -u "$token:" --max-time 5 "$SONAR_URL/api/project_analyses/search?project=advertisement&ps=1" 2>/dev/null | grep -oP '"date":"\K[^"]+' | head -1)"
  project_json="$(curl -s -u "$token:" --max-time 10 "$SONAR_URL/api/measures/component?metricKeys=ncloc,complexity,cognitive_complexity,code_smells,duplicated_lines_density&component=advertisement" 2>/dev/null)"
  tree_json="$(curl -s -u "$token:" --max-time 15 "$SONAR_URL/api/measures/component_tree?component=advertisement&metricKeys=ncloc,complexity,cognitive_complexity,code_smells&qualifiers=DIR&ps=500" 2>/dev/null)"
  file_counts=""
  for mod in "${MODULES[@]}"; do
    [ -d "$REPO_ROOT/$mod/src/main/java" ] || continue
    count="$(find "$REPO_ROOT/$mod/src/main/java" -name '*.java' 2>/dev/null | wc -l | tr -d ' ')"
    file_counts="$file_counts$mod=$count,"
  done
  python3 -c "
import json, sys

analysis_date = sys.argv[1]
project = json.loads(sys.argv[2]) if sys.argv[2] else {}
tree = json.loads(sys.argv[3]) if sys.argv[3] else {}
modules = [m for m in sys.argv[4].split(',') if m]
file_counts = dict(p.split('=') for p in sys.argv[5].split(',') if p)

def get_measure(measures, key):
    for m in measures:
        if m['metric'] == key:
            return m['value']
    return None

proj_measures = project.get('component', {}).get('measures', [])
proj = {k: get_measure(proj_measures, k) for k in ['ncloc','complexity','cognitive_complexity','code_smells','duplicated_lines_density']}

per_module = {m: {'ncloc': 0, 'complexity': 0, 'cognitiveComplexity': 0, 'codeSmells': 0, 'javaFileCount': int(file_counts.get(m, 0))} for m in modules}
for c in tree.get('components', []):
    path = c.get('path', '')
    for m in modules:
        if path.startswith(m + '/'):
            for meas in c.get('measures', []):
                key = meas['metric']
                val = int(meas['value']) if meas['value'].isdigit() else 0
                if key == 'ncloc': per_module[m]['ncloc'] += val
                elif key == 'complexity': per_module[m]['complexity'] += val
                elif key == 'cognitive_complexity': per_module[m]['cognitiveComplexity'] += val
                elif key == 'code_smells': per_module[m]['codeSmells'] += val
            break

out = {
  'analysisDate': analysis_date,
  'project': {
    'ncloc': int(proj['ncloc']) if proj['ncloc'] else None,
    'complexity': int(proj['complexity']) if proj['complexity'] else None,
    'cognitiveComplexity': int(proj['cognitive_complexity']) if proj['cognitive_complexity'] else None,
    'codeSmells': int(proj['code_smells']) if proj['code_smells'] else None,
    'duplicatedLinesDensity': float(proj['duplicated_lines_density']) if proj['duplicated_lines_density'] else None,
  },
  'modules': per_module,
}
print(json.dumps(out))
" "$analysis_date" "$project_json" "$tree_json" "$(IFS=,; echo "${MODULES[*]}")" "$file_counts"
}

# ── ArchUnit: real Efferent/Afferent Coupling, Instability, Abstractness per module, computed by
# ArchitectureMetricsExport (marketplace-app/src/test/java/org/ost/marketplace/architecture) and
# written to a fixed JSON path every time `bash scripts/unit-tests.sh` runs. Read here if present;
# null if the test hasn't run yet -- optional data, no auto-trigger (running the full unit-test
# suite from inside this generator would be a much bigger cost than SonarQube's own staleness
# check, so this stays passively "as fresh as the last test run" instead).
ARCHUNIT_METRICS_FILE="$REPO_ROOT/marketplace-app/target/architecture-metrics.json"

archunit_metrics_json() {
  if [ -f "$ARCHUNIT_METRICS_FILE" ]; then
    cat "$ARCHUNIT_METRICS_FILE"
  else
    echo "null"
  fi
}

# ── Live coupling checks: re-runs real grep commands, each producing a PASS/FAIL with the real
# evidence, not hand-typed "checkmark" text.
coupling_checks_json() {
  local checks_json="" first_c=true name pass evidence

  name="No Vaadin imports in starters"
  evidence="$(grep -rl 'import com\.vaadin\.' "$REPO_ROOT"/*-spring-boot-starter/src/main/java --include='*.java' 2>/dev/null | sed "s|$REPO_ROOT/||" | paste -sd, -)"
  [ -z "$evidence" ] && pass=true || pass=false
  [ -z "$evidence" ] && evidence="grep for com.vaadin imports across every *-spring-boot-starter -- no matches"
  first_c=false
  checks_json="$checks_json    {\"name\": \"$(json_escape "$name")\", \"pass\": $pass, \"evidence\": \"$(json_escape "$evidence")\"}"

  name="No direct starter-to-starter internal imports"
  local viol="" mod other
  for mod in "${MODULES[@]}"; do
    [[ "$mod" == *-spring-boot-starter ]] || continue
    for other in "${MODULES[@]}"; do
      [[ "$other" == *-spring-boot-starter ]] || continue
      [ "$mod" = "$other" ] && continue
      # Real package root from the real directory structure, not a string-stripped guess -- module
      # names don't always match their package 1:1 (provider-profile-spring-boot-starter's real
      # root is org.ost.provider, not org.ost.provider-profile, which a naive suffix-strip would
      # produce and which can never match a real import).
      local other_pkg
      other_pkg="org.ost.$(find "$REPO_ROOT/$other/src/main/java/org/ost" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | head -1)"
      local hit
      hit="$(grep -rl "import ${other_pkg}\." "$REPO_ROOT/$mod/src/main/java" --include='*.java' 2>/dev/null | sed "s|$REPO_ROOT/||")"
      [ -n "$hit" ] && viol="$viol$hit,"
    done
  done
  [ -z "$viol" ] && pass=true || pass=false
  [ -z "$viol" ] && evidence="checked every starter pair for cross-package imports -- no matches" || evidence="$viol"
  $first_c || checks_json="$checks_json,"$'\n'
  checks_json="$checks_json    {\"name\": \"$(json_escape "$name")\", \"pass\": $pass, \"evidence\": \"$(json_escape "$evidence")\"}"

  name="No UI to Repository direct imports"
  evidence="$(grep -rl 'import org\.ost\.[a-z]*\.repository\.' "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/ui" --include='*.java' 2>/dev/null | sed "s|$REPO_ROOT/||" | paste -sd, -)"
  [ -z "$evidence" ] && pass=true || pass=false
  [ -z "$evidence" ] && evidence="grep for org.ost.*.repository imports under marketplace-app ui package -- no matches"
  $first_c || checks_json="$checks_json,"$'\n'
  checks_json="$checks_json    {\"name\": \"$(json_escape "$name")\", \"pass\": $pass, \"evidence\": \"$(json_escape "$evidence")\"}"

  echo "[$checks_json"$'\n'"  ]"
}

# ── Largest Java files by line count across the whole repo -- the same `find ... | sort -rn |
# head` command 07-risk-report.md (now deleted) had written as text, re-run every generation
# instead of showing a dated static snapshot.
largest_java_files_json() {
  local items_json="" first_i=true line file lines mod
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    lines="$(echo "$line" | awk '{print $1}')"
    file="$(echo "$line" | awk '{$1=""; print substr($0,2)}')"
    file="${file#"$REPO_ROOT"/}"
    mod="${file%%/*}"
    $first_i || items_json="$items_json,"$'\n'
    first_i=false
    items_json="$items_json    {\"file\": \"$(json_escape "$(basename "$file")")\", \"path\": \"$(json_escape "$file")\", \"lines\": $lines, \"module\": \"$(json_escape "$mod")\"}"
  done < <(find "$REPO_ROOT" -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' -exec wc -l {} \; 2>/dev/null | sort -rn | head -10)
  echo "[$items_json"$'\n'"  ]"
}

# ── Constructor Injection Complexity: every class with @RequiredArgsConstructor and 4+
# constructor-injected (private final) fields -- the full list, not the 4 hand-picked examples
# 07-risk-report.md (now deleted) used to show.
constructor_injection_json() {
  local items_json="" first_i=true f class_name field_count mod

  while IFS= read -r f; do
    [ -z "$f" ] && continue
    field_count="$(grep -cP '^\s*private final \S' "$f" 2>/dev/null)"
    [ "${field_count:-0}" -lt 4 ] && continue
    class_name="$(basename "$f" .java)"
    mod="${f#"$REPO_ROOT"/}"; mod="${mod%%/*}"
    $first_i || items_json="$items_json,"$'\n'
    first_i=false
    items_json="$items_json    {\"class\": \"$(json_escape "$class_name")\", \"module\": \"$(json_escape "$mod")\", \"fieldCount\": $field_count, \"file\": \"$(json_escape "${f#"$REPO_ROOT"/}")\"}"
  done < <(grep -rl '@RequiredArgsConstructor' "$REPO_ROOT" --include='*.java' 2>/dev/null | grep -v '/target/' | grep '/src/main/java/')

  echo "[$items_json"$'\n'"  ]"
}

# ── Package God-Package Analysis: every package (recursive) with more than 20 .java files --
# the full list, not 4 hand-picked examples.
god_packages_json() {
  local items_json="" first_i=true pkg count depth

  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    # Only 2-3 segments past org/ost (org.ost.X or org.ost.X.Y) -- deeper segments would flood the
    # list with small, already-well-organized leaf packages instead of the handful of genuinely
    # broad ones worth flagging.
    depth="$(echo "${pkg#"$REPO_ROOT"/}" | sed 's|.*/org/ost/||' | tr '/' '\n' | wc -l)"
    [ "$depth" -gt 2 ] && continue
    count="$(find "$pkg" -name '*.java' 2>/dev/null | wc -l | tr -d ' ')"
    [ "$count" -le 20 ] && continue
    $first_i || items_json="$items_json,"$'\n'
    first_i=false
    items_json="$items_json    {\"package\": \"$(json_escape "${pkg#"$REPO_ROOT"/}")\", \"fileCount\": $count}"
  done < <(find "$REPO_ROOT" -path '*/src/main/java/*' -not -path '*/target/*' -type d 2>/dev/null | sort)

  echo "[$items_json"$'\n'"  ]"
}

# ── Self-documenting tooling: walks scripts/architecture/ dynamically (no hardcoded file list) and
# extracts each script's own "Description:"/"Uses:"/"Input:"/"Output:" header comment fields --
# single source of truth right next to the code, feeds the System screen's "How this page is
# built" section instead of hand-written prose that can silently drift from what the scripts
# actually do. A new script dropped into the folder with the same header convention shows up here
# with no generator edit needed.
architecture_tooling_self_docs_json() {
  python3 -c "
import json, os, re, sys

repo_root = sys.argv[1]
folder = os.path.join(repo_root, 'scripts', 'architecture')
files = sorted(f for f in os.listdir(folder) if f.endswith(('.sh', '.js')))

def extract(path):
    with open(path) as fh:
        lines = fh.readlines()[:20]
    text = []
    for l in lines:
        l = l.rstrip('\n')
        if l.startswith('#'):
            text.append(l[1:].lstrip())
        elif l.startswith('//'):
            text.append(l[2:].lstrip())
        else:
            break
    fields = {'Description': '', 'Uses': '', 'Input': '', 'Output': ''}
    current = None
    for l in text:
        m = re.match(r'^(Description|Uses|Input|Output):\s*(.*)', l)
        if m:
            current = m.group(1)
            fields[current] = m.group(2)
        elif current and l.strip() == '':
            current = None
        elif current:
            fields[current] += ' ' + l.strip()
    return fields

out = []
for f in files:
    fields = extract(os.path.join(folder, f))
    out.append({
        'file': 'scripts/architecture/' + f,
        'description': fields['Description'],
        'uses': fields['Uses'],
        'input': fields['Input'],
        'output': fields['Output'],
    })
print(json.dumps(out))
" "$REPO_ROOT"
}

# ── Bounded Contexts: live from real source wherever a real signal exists, reusing the existing
# "confidence" field convention (see MODULE_DOMAIN's own extracted/inferred/manual tags above)
# rather than inventing a new marker. "extracted" = a real grep/file match backs this fact.
# BC_DOMAIN_MODULE/BC_DOMAIN_ORDER/BC_DOMAIN_LABEL/BC_ENTITY_TYPE_DOMAIN/BC_LABEL_PAYLOAD are
# declared near the top of the file (before DB_ERD_CHANGELOG_FILES's module-detail computation),
# since both that computation and this function need them.
bounded_contexts_json() {
  local domains_json="" first_d=true d mod
  for d in "${BC_DOMAIN_ORDER[@]}"; do
    mod="${BC_DOMAIN_MODULE[$d]}"
    local entities_json services_json tables_json ports_json changelog
    changelog="$(printf '%s\n' "${DB_ERD_CHANGELOG_FILES[@]}" | grep "^$mod/" | head -1)"
    if [ "$d" = "Shared" ]; then
      entities_json="[]"; services_json="[]"; tables_json="[]"
      local spi_count dto_count model_count
      spi_count="$(find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java' | wc -l | tr -d ' ')"
      dto_count="$(find "$REPO_ROOT/platform-commons/src/main/java" -path '*/dto/*.java' | wc -l | tr -d ' ')"
      model_count="$(find "$REPO_ROOT/platform-commons/src/main/java" -path '*/model/*.java' | wc -l | tr -d ' ')"
      ports_json="$(json_named_file_array "$(printf '%s\t\n' "${spi_count} SPI interfaces (Ports & Hooks)" "${dto_count} cross-domain DTOs" "${model_count} core model classes")")"
    elif [ "$d" = "UI" ]; then
      entities_json="[]"; services_json="[]"; tables_json="[]"
      ports_json="$(json_named_file_array "$(grep -rl 'implements .*Hook' "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/spi" --include='*.java' 2>/dev/null | sort | while read -r f; do
        printf '%s\t%s\n' "$(basename "$f" .java)" "${f#"$REPO_ROOT"/}"
      done)")"
    else
      entities_json="$(json_named_file_array "$(grep -rl '^@Table\|@Table(' "$REPO_ROOT/$mod/src/main/java" --include='*.java' 2>/dev/null | sort | while read -r f; do
        printf '%s\t%s\n' "$(basename "$f" .java)" "${f#"$REPO_ROOT"/}"
      done)")"
      services_json="$(json_named_file_array "$(find "$REPO_ROOT/$mod/src/main/java" -name '*Service.java' 2>/dev/null | sort | while read -r f; do
        printf '%s\t%s\n' "$(basename "$f" .java)" "${f#"$REPO_ROOT"/}"
      done)")"
      tables_json="$(json_named_file_array "$(printf '%s\n' "${MODULE_TABLES[$mod]:-}" | while read -r t; do
        [ -z "$t" ] && continue
        printf '%s\t%s\n' "$t" "$changelog"
      done)")"
      ports_json="$(json_named_file_array "$(find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java' | sort | while read -r ifile; do
        iface="$(basename "$ifile" .java)"
        grep -qlP "implements\s+.*\b${iface}\b" -r --include='*.java' "$REPO_ROOT/$mod/src/main/java" 2>/dev/null && printf '%s\t%s\n' "$iface" "${ifile#"$REPO_ROOT"/}"
      done)")"
    fi
    $first_d || domains_json="$domains_json,"$'\n'
    first_d=false
    domains_json="$domains_json    {\"id\": \"$d\", \"label\": \"$(json_escape "${BC_DOMAIN_LABEL[$d]}")\", \"module\": \"$(json_escape "$mod")\", \"confidence\": \"extracted\", \"entities\": $entities_json, \"services\": $services_json, \"tables\": $tables_json, \"ports\": $ports_json}"
  done

  # Relationships are collected into parallel indexed arrays first, keyed by "from|to|label" to
  # merge multiple real signals for the same conceptual edge (e.g. two different EntityTypes both
  # routing "User -> Audit") into one edge with combined evidence, instead of drawing duplicate
  # parallel lines for what a reader would see as a single relationship.
  local -a rel_key=() rel_from=() rel_to=() rel_label=() rel_evidence=() rel_dashed=()
  add_rel() {
    local key="$1|$2|$3" i found=""
    for i in "${!rel_key[@]}"; do
      if [ "${rel_key[$i]}" = "$key" ]; then found=$i; break; fi
    done
    if [ -n "$found" ]; then
      rel_evidence[$found]="${rel_evidence[$found]}; $5"
    else
      rel_key+=("$key"); rel_from+=("$1"); rel_to+=("$2"); rel_label+=("$3"); rel_evidence+=("$5"); rel_dashed+=("$6")
    fi
  }

  # Shared decouples every domain -- real pom.xml compile-scope dependency (moduleNodes edges),
  # same data Module Dependencies (01) already computes.
  for d in "${BC_DOMAIN_ORDER_STARTERS[@]}"; do
    add_rel "Shared" "$d" "decouples" "extracted" "${BC_DOMAIN_MODULE[$d]}/pom.xml depends on platform-commons" "true"
  done

  # "audited via" / "can have" -- real signal: which marketplace-app/spi/*.java class implements
  # AuditActivityFieldsHook (-> audited via Audit) or AuditActivityEnrichHook (-> can have Attachment
  # enrichment), and what EntityType its entityType() method declares.
  local hf etype dom
  while IFS= read -r hf; do
    [ -z "$hf" ] && continue
    etype="$(grep -A2 "entityType()" "$hf" | grep -oP 'EntityType\.\K\w+' | head -1)"
    [ -z "$etype" ] && continue
    dom="${BC_ENTITY_TYPE_DOMAIN[$etype]:-}"
    [ -z "$dom" ] && continue
    if grep -q "implements AuditActivityFieldsHook" "$hf"; then
      add_rel "$dom" "Audit" "audited via" "extracted" "$(basename "$hf" .java).entityType() = $etype" "false"
    fi
    if grep -q "implements AuditActivityEnrichHook" "$hf"; then
      add_rel "$dom" "Attachment" "can have" "extracted" "$(basename "$hf" .java).entityType() = $etype" "false"
    fi
  done < <(grep -rl "implements AuditActivityFieldsHook\|implements AuditActivityEnrichHook" "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/spi" --include="*.java" 2>/dev/null)

  # "category assignment via" -- real replaceAssignments() call sites. Provider's own service calls
  # it directly; Advertisement's real call site is marketplace-app's AdvertisementSaveService
  # instead (that starter never writes its own category assignments -- see that starter's own
  # CLAUDE.md) -- found while building this, not part of the original hand-typed diagram at all.
  local pt_evidence
  pt_evidence="$(grep -rn "TaxonPort.*replaceAssignments\|\.replaceAssignments(" "$REPO_ROOT/provider-profile-spring-boot-starter/src/main/java" --include="*.java" 2>/dev/null | head -1 | sed "s|$REPO_ROOT/||")"
  [ -n "$pt_evidence" ] && add_rel "ProviderProfile" "Taxon" "category assignment via" "extracted" "$pt_evidence" "false"
  local at_evidence
  at_evidence="$(grep -rn "TaxonPort.*replaceAssignments\|\.replaceAssignments(" "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/services/advertisement" --include="*.java" 2>/dev/null | head -1 | sed "s|$REPO_ROOT/||")"
  [ -n "$at_evidence" ] && add_rel "Advertisement" "Taxon" "category assignment via" "extracted" "$at_evidence" "false"

  # UI calls every port that has a real implementation somewhere (marketplace-app is the one
  # exposing the UI over every domain's own port).
  for d in "${BC_DOMAIN_ORDER_STARTERS[@]}"; do
    add_rel "UI" "$d" "calls" "extracted" "marketplace-app injects a ComponentFactory/ObjectProvider for ${BC_DOMAIN_MODULE[$d]}'s port(s)" "false"
  done

  # Audit calls back via every *Hook implementation actually found in marketplace-app/spi -- listed
  # by name (not just one representative), since each is independent real evidence.
  local hook_impls
  hook_impls="$(grep -rl 'implements .*Hook' "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/spi" --include='*.java' 2>/dev/null | xargs -r -I{} basename {} .java | sort | paste -sd, -)"
  [ -n "$hook_impls" ] && add_rel "Audit" "UI" "calls back via Hook implementations" "extracted" "marketplace-app/spi: $hook_impls" "false"

  local rel_json="" first_r=true i
  for i in "${!rel_key[@]}"; do
    $first_r || rel_json="$rel_json,"$'\n'
    first_r=false
    rel_json="$rel_json    {\"from\": \"${rel_from[$i]}\", \"to\": \"${rel_to[$i]}\", \"label\": \"$(json_escape "${rel_label[$i]}")\", \"confidence\": \"extracted\", \"evidence\": \"$(json_escape "${rel_evidence[$i]}")\", \"payload\": \"$(json_escape "${BC_LABEL_PAYLOAD[${rel_label[$i]}]:-}")\", \"dashed\": ${rel_dashed[$i]}}"
  done

  echo "{\"domains\": [$domains_json"$'\n'"  ], \"relationships\": [$rel_json"$'\n'"  ]}"
}

# ── Docker: real files, mechanically extracted facts only (build stages, compose service names)
# -- no restated prose. The full deployment workflow explanation stays in scripts/CLAUDE.md's own
# "Deployment" section, linked to, never copied.
DOCKER_FILES=(
  "Dockerfile|dockerfile|Main app image (multi-stage build)"
  "Dockerfile.ai|dockerfile|Claude Code dev sandbox environment -- not part of the app build"
  "scripts/build-env/Dockerfile|dockerfile|Local build-env container"
  "scripts/ci/Dockerfile|dockerfile|Isolated CI runner image"
  "scripts/infra/docker-compose.app.yml|compose|App container (dev infra)"
  "scripts/infra/docker-compose.db.yml|compose|PostgreSQL (dev infra)"
  "scripts/infra/docker-compose.minio.yml|compose|MinIO S3-compatible storage (dev infra)"
  "scripts/sonar/docker-compose.sonar.yml|compose|SonarQube server"
  "scripts/build-env/docker-compose.yml|compose|Build-env container wrapper"
)
docker_files_json() {
  local out="" first=true entry file kind label items_json
  for entry in "${DOCKER_FILES[@]}"; do
    IFS='|' read -r file kind label <<< "$entry"
    [ -f "$REPO_ROOT/$file" ] || continue
    if [ "$kind" = "dockerfile" ]; then
      items_json="$(json_str_array "$(awk '/^FROM/{ $1=""; sub(/^ /,""); print }' "$REPO_ROOT/$file")")"
    else
      items_json="$(json_str_array "$(awk '
        /^services:/ { insvc=1; next }
        /^[a-zA-Z]/ { insvc=0 }
        insvc && /^  [a-zA-Z0-9_-]+:[[:space:]]*$/ { s=$0; gsub(/^  /,"",s); gsub(/:.*/,"",s); print s }
      ' "$REPO_ROOT/$file")")"
    fi
    $first || out="$out,"$'\n'
    first=false
    out="$out  {\"file\": \"$(json_escape "$file")\", \"kind\": \"$kind\", \"label\": \"$(json_escape "$label")\", \"items\": $items_json}"
  done
  echo "[$out]"
}

[ -n "$WITH_SONAR" ] && ensure_sonar_fresh

sonar_json="null"
[ -n "$WITH_SONAR" ] && sonar_json="$(sonar_metrics_json)"
archunit_json="null"
[ -n "$WITH_ARCHUNIT" ] && archunit_json="$(archunit_metrics_json)"

{
  echo "{"
  echo "  \"generated_by\": \"scripts/architecture/generate-architecture-model.sh\","
  echo "  \"generated_note\": \"Track A, plus real SonarQube/ArchUnit metrics -- modules+deps from pom.xml, domain grouping/entities/services/contracts derived live from real Java source and the module list, tables live from the real Liquibase changelogs. Module Dependencies (01)/SPI Map (02)/Database ERD (04)/Bounded Contexts have no .md counterpart -- rendered live on this tool's own Diagrams page instead, lifecycle from DECISIONS.md/backlog, pipeline nodes from docs/ai/flows.md + .claude/commands + .claude/skills.\","
  echo "  \"rootArtifactId\": \"$(json_escape "$ROOT_ARTIFACT_ID")\","
  echo "  \"rootVersion\": \"$(json_escape "$ROOT_VERSION")\","
  echo "  \"diagramGroups\": ["
  echo "$diagram_groups_json"
  echo "  ],"
  echo "  \"dockerFiles\": $(docker_files_json),"
  echo "  \"backlogPriorityOrder\": $(backlog_priority_order_json),"
  echo "  \"spiMap\": $(spi_map_json),"
  echo "  \"spiCallFlowExamples\": $(spi_call_flow_examples_json),"
  echo "  \"dbErd\": $(db_erd_json),"
  echo "  \"boundedContexts\": $(bounded_contexts_json),"
  echo "  \"sonarMetrics\": $sonar_json,"
  echo "  \"archUnitMetrics\": $archunit_json,"
  echo "  \"couplingChecks\": $(coupling_checks_json),"
  echo "  \"largestJavaFiles\": $(largest_java_files_json),"
  echo "  \"constructorInjection\": $(constructor_injection_json),"
  echo "  \"godPackages\": $(god_packages_json),"
  echo "  \"architectureToolingSelfDocs\": $(architecture_tooling_self_docs_json),"
  echo "  \"allAdrs\": $(all_adrs_json),"
  echo "  \"nodes\": ["

  first=true

  # MODULE nodes
  for m in "${MODULES[@]}"; do
    $first || echo "    ,"
    first=false
    domain="${MODULE_DOMAIN[$m]:-UNKNOWN}"
    domain_confidence="extracted"
    [ "$domain" = "UNKNOWN" ] && domain_confidence="inferred"
    # "extracted" for every real "*-spring-boot-starter" module -- its domain id/label is
    # mechanically derived from the module name itself (see the BC_DOMAIN_MODULE derivation loop
    # above), not hand-typed. The three structural modules (Shared/UI's hardcoded seed entries,
    # plus query-lib/integration-tests' pure fallback labels) stay "manual" -- there's no naming
    # convention that derives "Shared Kernel"/"Testing"/"UI/Application Layer" from a module name.
    [[ "$m" == *-spring-boot-starter ]] || domain_confidence="manual"

    intent_json="$(json_adr_array "$m" "$(adr_intent_for_module "$m")")"
    description="${MODULE_DESCRIPTION[$m]:-}"
    decisions_field="$(decisions_json_for "$m")"

    echo "    {"
    echo "      \"id\": \"$(json_escape "$m")\","
    echo "      \"type\": \"MODULE\","
    echo "      \"description\": \"$(json_escape "$description")\","
    echo "      \"domain\": \"$(json_escape "$domain")\","
    echo "      \"provenance\": \"OBSERVED\","
    echo "      \"lifecycle\": \"ACTIVE\","
    echo "      \"disposition\": \"KEEP\","
    echo "      \"confidence\": \"extracted\","
    echo "      \"domain_confidence\": \"$domain_confidence\","
    echo "      \"evidence\": [{\"file\": \"$m/pom.xml\", \"line\": 1}],"
    echo "      \"intent\": $intent_json,"
    echo "      \"decisions\": $decisions_field,"
    echo "      \"entities\": $(json_str_array "${MODULE_ENTITY[$m]:-}"),"
    echo "      \"keyServices\": $(json_str_array "${MODULE_KEYSERVICES[$m]:-}"),"
    echo "      \"contracts\": $(json_str_array "${MODULE_CONTRACT[$m]:-}"),"
    echo "      \"tables\": $(json_str_array "${MODULE_TABLES[$m]:-}"),"
    deps="$(module_deps "$m")"
    compile_list=$(echo "$deps" | awk -F'|' '$2=="compile" && $3=="false" {print $1}')
    runtime_list=$(echo "$deps" | awk -F'|' '$2=="runtime" {print $1}')
    optional_list=$(echo "$deps" | awk -F'|' '$3=="true" {print $1}')
    dependents_list="$(reverse_deps_for_module "$m")"
    echo "      \"edges\": {\"DEPENDS_ON_COMPILE\": $(json_str_array "$compile_list"), \"DEPENDS_ON_RUNTIME\": $(json_str_array "$runtime_list"), \"DEPENDS_ON_OPTIONAL\": $(json_str_array "$optional_list"), \"DEPENDED_ON_BY\": $(json_str_array "$dependents_list")}"
    echo "    }"
  done

  # COMMAND nodes
  while IFS= read -r -d '' file; do
    name="$(basename "$file" .md)"
    rel="${file#"$REPO_ROOT"/}"
    desc="$(command_first_line "$file")"
    echo "    ,"
    echo "    {"
    echo "      \"id\": \"command:$name\","
    echo "      \"type\": \"COMMAND\","
    echo "      \"provenance\": \"OBSERVED\","
    echo "      \"lifecycle\": \"ACTIVE\","
    echo "      \"disposition\": \"KEEP\","
    echo "      \"confidence\": \"extracted\","
    echo "      \"evidence\": [{\"file\": \"$(json_escape "$rel")\", \"line\": 1}],"
    echo "      \"description\": \"$(json_escape "$desc")\","
    echo "      \"edges\": {}"
    echo "    }"
  done < <(find "$REPO_ROOT/.claude/commands" -name "*.md" -print0 | sort -z)

  # SKILL nodes
  while IFS= read -r -d '' file; do
    name="$(basename "$(dirname "$file")")"
    rel="${file#"$REPO_ROOT"/}"
    desc="$(command_first_line "$file")"
    echo "    ,"
    echo "    {"
    echo "      \"id\": \"skill:$name\","
    echo "      \"type\": \"SKILL\","
    echo "      \"provenance\": \"OBSERVED\","
    echo "      \"lifecycle\": \"ACTIVE\","
    echo "      \"disposition\": \"KEEP\","
    echo "      \"confidence\": \"extracted\","
    echo "      \"evidence\": [{\"file\": \"$(json_escape "$rel")\", \"line\": 1}],"
    echo "      \"description\": \"$(json_escape "$desc")\","
    echo "      \"edges\": {}"
    echo "    }"
  done < <(find "$REPO_ROOT/.claude/skills" -name "SKILL.md" -print0 2>/dev/null | sort -z)

  # SCRIPT_GROUP nodes -- non-Maven tooling directories, most (not all -- see scripts/ai above)
  # with their own DECISIONS.md
  for d in "${SCRIPT_GROUP_DIRS[@]}"; do
    desc=""
    [ -f "$REPO_ROOT/$d/CLAUDE.md" ] && desc="$(head -1 "$REPO_ROOT/$d/CLAUDE.md" | sed 's/^#* *//')"
    if [ -n "${SCRIPT_GROUP_FILE_ORDER[$d]:-}" ]; then
      files_list=""
      for f in ${SCRIPT_GROUP_FILE_ORDER[$d]}; do
        [ -f "$REPO_ROOT/$d/$f" ] && files_list="$files_list$f"$'\n'
      done
    else
      files_list="$(find "$REPO_ROOT/$d" -maxdepth 1 \( -name '*.sh' -o -name '*.js' \) -printf '%f\n' 2>/dev/null | sort)"
    fi
    files_json="$(json_str_array "$files_list")"
    intent_json="$(json_adr_array "$d" "$(adr_intent_for_module "$d")")"
    decisions_field="$(decisions_json_for "$d")"
    evidence_file="$d/DECISIONS.md"
    [ -f "$REPO_ROOT/$d/DECISIONS.md" ] || evidence_file="$d"
    echo "    ,"
    echo "    {"
    echo "      \"id\": \"$(json_escape "$d")\","
    echo "      \"type\": \"SCRIPT_GROUP\","
    echo "      \"category\": \"${SCRIPT_GROUP_CATEGORY[$d]}\","
    echo "      \"provenance\": \"OBSERVED\","
    echo "      \"lifecycle\": \"ACTIVE\","
    echo "      \"disposition\": \"KEEP\","
    echo "      \"confidence\": \"extracted\","
    echo "      \"evidence\": [{\"file\": \"$(json_escape "$evidence_file")\", \"line\": 1}],"
    echo "      \"description\": \"$(json_escape "$desc")\","
    echo "      \"files\": $files_json,"
    echo "      \"intent\": $intent_json,"
    echo "      \"decisions\": $decisions_field,"
    echo "      \"edges\": {}"
    echo "    }"
  done

  # BACKLOG summary node -- title + short (truncated) description + real file link, per issue,
  # for both open and completed lists. Not the full issue body (that would bloat the model the
  # same way embedding full ADR prose would, for content this tool doesn't otherwise need) -- click
  # shows the short description, a real link opens the actual file for the rest.
  open_count=$(find "$REPO_ROOT/backlog/issues" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l | tr -d ' ')
  completed_count=$(find "$REPO_ROOT/backlog/completed/issues" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l | tr -d ' ')
  echo "    ,"
  echo "    {"
  echo "      \"id\": \"backlog\","
  echo "      \"type\": \"BACKLOG_SUMMARY\","
  echo "      \"provenance\": \"OBSERVED\","
  echo "      \"lifecycle\": \"TRANSITIONAL\","
  echo "      \"disposition\": \"KEEP\","
  echo "      \"confidence\": \"extracted\","
  echo "      \"evidence\": [{\"file\": \"backlog/BACKLOG.md\", \"line\": 1}],"
  echo "      \"open_issues\": $open_count,"
  echo "      \"completed_issues\": $completed_count,"
  echo "      \"openIssues\": $(issue_list_json "$REPO_ROOT/backlog/issues" "backlog/issues"),"
  echo "      \"completedIssues\": $(issue_list_json "$REPO_ROOT/backlog/completed/issues" "backlog/completed/issues"),"
  echo "      \"edges\": {}"
  echo "    }"

  echo "  ]"
  echo "}"
} > "$OUTPUT"

if command -v python3 >/dev/null 2>&1; then
  python3 -c "import json,sys; json.load(open('$OUTPUT'))" \
    && echo "Valid JSON: $OUTPUT" \
    || { echo "ERROR: generated JSON is invalid" >&2; exit 1; }
fi

node_count=$(grep -c '"type":' "$OUTPUT" || true)
echo "Wrote $node_count nodes to $OUTPUT"

# ── A2: architecture-map.html -- a real drill-down pyramid (System -> Module -> [Contract/
# Implementation/Method placeholders for Track B] -> Tooling & Pipelines), not a flat graph with a
# JSON-dump side panel. Static Cytoscape.js explorer, model JSON inlined so the file works
# standalone via file:// (no local server, no separate fetch() that CORS would block).
cat > "$HTML_OUTPUT" <<'HTML_HEAD'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Architecture Control Plane</title>
<script src="https://unpkg.com/cytoscape@3.28.1/dist/cytoscape.min.js"></script>
<script src="https://unpkg.com/dagre@0.8.5/dist/dagre.min.js"></script>
<script src="https://unpkg.com/cytoscape-dagre@2.5.0/cytoscape-dagre.js"></script>
<script src="https://unpkg.com/mermaid@10/dist/mermaid.min.js"></script>
<style>
  :root {
    --ink: #1a202c; --muted: #718096; --line: #e2e8f0; --bg: #f7f8fa; --card: #ffffff;
    --accent: #2b6cb0; --active: #2f855a; --active-bg: #e6fffa; --transitional: #c05621;
    --transitional-bg: #fffaf0; --deprecated: #718096; --deprecated-bg: #f1f3f5;
    --critical: #c0392b; --critical-bg: #fdecea;
  }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--ink); background: var(--bg); }
  header { background: var(--card); border-bottom: 1px solid var(--line); padding: 14px 24px; position: sticky; top: 0; z-index: 5; }
  header h1 { font-size: 17px; margin: 0 0 6px; }
  header .subtitle { font-size: 12px; color: var(--muted); margin-bottom: 8px; }
  #breadcrumb { font-size: 13px; }
  #breadcrumb a { color: var(--accent); text-decoration: none; cursor: pointer; }
  #breadcrumb a:hover { text-decoration: underline; }
  #breadcrumb .sep { color: var(--muted); margin: 0 6px; }
  #breadcrumb .current { color: var(--ink); font-weight: 600; }
  main { max-width: 1100px; margin: 0 auto; padding: 24px; }
  h2.screen-title { font-size: 22px; margin: 0 0 4px; }
  .screen-desc { color: var(--muted); font-size: 13px; margin-bottom: 20px; }
  .domain-group { margin-bottom: 28px; }
  .domain-group h3 { font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); margin: 0 0 10px; border-bottom: 1px solid var(--line); padding-bottom: 6px; }
  .card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 14px; cursor: pointer; transition: box-shadow .15s, transform .15s; }
  .card:hover { box-shadow: 0 4px 14px rgba(0,0,0,0.08); transform: translateY(-1px); }
  .card.card-active { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
  .card .card-title { font-weight: 600; font-size: 14px; margin-bottom: 4px; }
  .card .card-desc { font-size: 12px; color: var(--muted); line-height: 1.4; }
  .badge { display: inline-block; font-size: 10px; padding: 2px 8px; border-radius: 10px; font-weight: 600; margin-top: 8px; }
  .badge.ACTIVE { color: var(--active); background: var(--active-bg); }
  .badge.TRANSITIONAL { color: var(--transitional); background: var(--transitional-bg); }
  .badge.DEPRECATED { color: var(--deprecated); background: var(--deprecated-bg); }
  .metric-good { color: var(--active); background: var(--active-bg); padding: 2px 8px; border-radius: 4px; font-weight: 500; }
  .metric-watch { color: var(--transitional); background: var(--transitional-bg); padding: 2px 8px; border-radius: 4px; font-weight: 500; }
  .metric-critical { color: var(--critical); background: var(--critical-bg); padding: 2px 8px; border-radius: 4px; font-weight: 500; }
  .special-card { background: linear-gradient(135deg,#2b6cb0,#2c5282); color: #fff; }
  .special-card .card-desc { color: #cbd5e0; }
  .module-header { display: flex; align-items: baseline; gap: 10px; margin-bottom: 4px; flex-wrap: wrap; }
  .module-header h2 { margin: 0; }
  .domain-tag { font-size: 12px; color: var(--accent); background: #ebf4ff; padding: 3px 10px; border-radius: 10px; }
  section.block { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 16px 18px; margin-bottom: 16px; }
  section.block h3 { font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); margin: 0 0 12px; }
  .dep-list { list-style: none; margin: 0; padding: 0; }
  .dep-list li { padding: 6px 0; border-bottom: 1px solid #f0f2f4; font-size: 13px; display: flex; justify-content: space-between; align-items: center; }
  .dep-list li:last-child { border-bottom: none; }
  .dep-list a { color: var(--accent); text-decoration: none; cursor: pointer; }
  .dep-list a:hover { text-decoration: underline; }
  .dep-tag { font-size: 10px; color: var(--muted); background: var(--bg); padding: 2px 7px; border-radius: 8px; }
  .empty-hint { font-size: 12px; color: var(--muted); font-style: italic; }
  .placeholder-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px dashed var(--line); opacity: 0.6; }
  .placeholder-row:last-child { border-bottom: none; }
  .placeholder-row .name { font-size: 13px; font-weight: 600; }
  .placeholder-row .tag { font-size: 10px; background: var(--bg); color: var(--muted); padding: 2px 8px; border-radius: 8px; }
  .adr-item { padding: 8px 0; border-bottom: 1px solid #f0f2f4; font-size: 13px; }
  .adr-item:last-child { border-bottom: none; }
  .adr-item .adr-id { color: var(--accent); font-weight: 600; font-size: 12px; }
  table.simple { width: 100%; border-collapse: collapse; font-size: 13px; table-layout: fixed; }
  table.simple th { text-align: left; font-size: 11px; text-transform: uppercase; color: var(--muted); padding: 6px 8px; border-bottom: 2px solid var(--line); }
  table.simple td { padding: 8px; border-bottom: 1px solid #f0f2f4; vertical-align: top; overflow-wrap: anywhere; word-break: break-word; }
  table.simple tr:last-child td { border-bottom: none; }
  table.simple a { color: var(--accent); text-decoration: none; cursor: pointer; }
  table.simple a:hover { text-decoration: underline; }
  table.simple a.module-badge { display: inline-block; color: #fff; font-weight: 600; padding: 3px 10px; border-radius: 10px; font-size: 12px; text-decoration: none; cursor: pointer; margin: 2px 2px 2px 0; white-space: nowrap; }
  table.simple a.module-badge:hover { opacity: 0.85; text-decoration: none; }
  .scope-label { font-size: 12px; color: var(--muted); }
  code.path { font-size: 11px; color: var(--muted); }
  .info-list { list-style: none; margin: 0; padding: 0; }
  .info-list li { padding: 6px 0; border-bottom: 1px solid #f0f2f4; font-size: 13px; line-height: 1.5; }
  .info-list li:last-child { border-bottom: none; }
  .info-list code { background: #f1f3f5; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
  .table-chip { display: inline-block; background: #f1f3f5; color: var(--ink); font-size: 11px; padding: 3px 9px; border-radius: 6px; margin: 2px 4px 2px 0; font-family: monospace; }
  .diagram-wrap { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 20px; overflow: auto; max-height: 75vh; }
  /* Cytoscape-rendered diagrams (Module Dependencies, SPI Map, bounded-contexts graph) manage
     their own internal pan/zoom transform -- the canvas element's DOM size never actually changes
     with zoom, so this container never really overflows and native scrollbars never engage. Drag
     empty canvas space to pan instead (see the diagram-note text above each one). */
  #diagram-cy-wrap { overflow: hidden; max-height: none; }
  #diagram-zoom-box { transform-origin: top left; width: fit-content; transition: transform .1s ease-out; }
  .diagram-note { font-size: 12px; color: var(--muted); margin-bottom: 12px; }
  .diagram-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
  .diagram-toolbar button { background: var(--card); border: 1px solid var(--line); border-radius: 6px; padding: 6px 12px; cursor: pointer; font-size: 13px; }
  .diagram-toolbar button:hover { background: var(--bg); }
  .zoom-controls { display: flex; align-items: center; gap: 6px; }
  .zoom-controls span#zoom-label { font-size: 12px; color: var(--muted); width: 42px; text-align: center; display: inline-block; }
  .adr-item a { color: var(--ink); text-decoration: none; cursor: pointer; }
  .adr-item a:hover .adr-id { text-decoration: underline; }
  .adr-item .adr-file { color: var(--muted); font-size: 12px; }
  .adr-item .adr-status { color: var(--muted); font-size: 11px; margin-left: 6px; }
  h3 .section-link { font-size: 12px; font-weight: 500; color: var(--accent); text-decoration: none; margin-left: 8px; cursor: pointer; }
  h3 .section-link:hover { text-decoration: underline; }
  h3 a.module-link { color: var(--accent); cursor: pointer; text-decoration: none; }
  h3 a.module-link:hover { text-decoration: underline; }
  .group-heading { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); margin: 24px 0 10px; }
  .group-heading:first-of-type { margin-top: 0; }
  .table-chip-row { margin-bottom: 12px; }
  .table-chip-row a { display: inline-block; margin: 2px 6px 2px 0; }
  .table-chip-row code.path { background: #f1f3f5; padding: 3px 8px; border-radius: 6px; }
  .adr-subheading { font-size: 12px; font-weight: 600; color: var(--muted); margin: 4px 0 6px; text-transform: uppercase; letter-spacing: .03em; }
  .adr-subheading .section-link { text-transform: none; letter-spacing: normal; font-weight: 500; }
  .glossary-item { margin-bottom: 20px; }
  .glossary-item:last-child { margin-bottom: 0; }
  .glossary-item > strong { display: block; font-size: 13px; margin-bottom: 6px; }
  #adr-popup { border: none; border-radius: 10px; padding: 0; max-width: 720px; width: 90vw; max-height: 80vh; overflow: hidden; box-shadow: 0 12px 40px rgba(0,0,0,.25); }
  #adr-popup::backdrop { background: rgba(20,24,28,.5); }
  #adr-popup .adr-popup-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; padding: 16px 20px; border-bottom: 1px solid var(--line); position: sticky; top: 0; background: var(--card); }
  #adr-popup .adr-popup-header h3 { margin: 0; font-size: 15px; }
  #adr-popup .adr-popup-header .adr-status { display: block; margin-top: 4px; }
  #adr-popup .adr-popup-close { border: none; background: none; font-size: 20px; cursor: pointer; color: var(--muted); line-height: 1; padding: 2px 6px; }
  #adr-popup .adr-popup-close:hover { color: var(--ink); }
  #adr-popup .adr-popup-body { padding: 16px 20px; overflow: auto; max-height: calc(80vh - 60px); font-size: 13px; line-height: 1.6; }
  #adr-popup .adr-popup-body p { margin: 0 0 10px; }
  #adr-popup .adr-popup-body p:last-child { margin-bottom: 0; }
  #adr-popup .adr-popup-body ul, #adr-popup .adr-popup-body ol { margin: 0 0 10px 20px; padding: 0; }
  #adr-popup .adr-popup-body code { background: #f1f3f5; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
  #adr-popup .adr-popup-body table.simple { margin: 0 0 10px; }
</style>
</head>
<body>
<header>
  <h1>Architecture Control Plane</h1>
  <div class="subtitle">Generated from pom.xml, DECISIONS.md, backlog/, docs/ai/flows.md, .claude/commands, .claude/skills, root CLAUDE.md — regenerate via <code>bash scripts/architecture/generate-architecture-model.sh</code>. Track A only: module-level granularity; Contract/Implementation/Method levels are placeholders until Track B's ArchUnit exporter lands.</div>
  <nav id="breadcrumb"></nav>
</header>
<main id="content"></main>
<dialog id="adr-popup">
  <div class="adr-popup-header">
    <div><h3 id="adr-popup-title"></h3><span class="adr-status" id="adr-popup-status"></span></div>
    <button class="adr-popup-close" onclick="document.getElementById('adr-popup').close()">&times;</button>
  </div>
  <div class="adr-popup-body" id="adr-popup-body"></div>
</dialog>
<script>
const MODEL =
HTML_HEAD

cat "$OUTPUT" >> "$HTML_OUTPUT"

cat >> "$HTML_OUTPUT" <<'HTML_TAIL'
;

// useMaxWidth:false on every diagram type -- otherwise Mermaid shrinks the SVG to fit the
// container width by default, which makes wide diagrams (the SPI map especially) render
// illegibly tiny no matter how the CSS zoom control scales the result afterward. Render at
// natural size instead; the diagram-wrap container scrolls, and zoom is a real magnifier on top.
mermaid.initialize({
  startOnLoad: false, theme: "neutral",
  flowchart: { useMaxWidth: false }, er: { useMaxWidth: false }, sequence: { useMaxWidth: false }
});
if (typeof cytoscapeDagre !== "undefined") cytoscape.use(cytoscapeDagre);

// ── Data indices ────────────────────────────────────────────────────────────────────────────
const byId = {};
MODEL.nodes.forEach(n => byId[n.id] = n);
const moduleNodes = MODEL.nodes.filter(n => n.type === "MODULE");
const commandNodes = MODEL.nodes.filter(n => n.type === "COMMAND");
const skillNodes = MODEL.nodes.filter(n => n.type === "SKILL");
const scriptGroupNodes = MODEL.nodes.filter(n => n.type === "SCRIPT_GROUP");
const backlogNode = MODEL.nodes.find(n => n.type === "BACKLOG_SUMMARY");
const totalDiagramCount = MODEL.diagramGroups.reduce((sum, g) => sum + g.diagrams.length, 0);
let zoomLevel = 1;

const domainOrder = [];
moduleNodes.forEach(n => { if (!domainOrder.includes(n.domain)) domainOrder.push(n.domain); });
const domainPalette = ["#2b6cb0","#2f855a","#c05621","#805ad5","#b83280","#2c7a7b","#975a16","#4a5568"];
function domainColor(domain) {
  const i = domainOrder.indexOf(domain);
  return domainPalette[i % domainPalette.length];
}

function esc(s) { return (s || "").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;"); }
function displayName(id) { return id.replace(/^command:|^skill:/, ""); }

// ── Router: System | Module(id) | Pipelines | Backlog | Diagrams(groupKey?, diagramIndex?) ──────
// One navigate() call is the single place that maintains the breadcrumb trail -- every screen
// drills through it, so every screen gets correct back-navigation for free, with no per-screen
// breadcrumb-building code to keep in sync (same "growing stack, only append, never rewrite"
// shape as this project's real BreadcrumbStep pattern in marketplace-app -- see root CLAUDE.md
// "Breadcrumb Pattern"). Add a new screen by teaching crumbLabelFor() its label; navigate()/
// renderBreadcrumb() need no changes.
let view = { screen: "system" };
let crumbStack = []; // steps between the implicit "System" root and the current view; never
                      // rewritten in place -- only pushed (drilling in) or truncated (going back)

function crumbLabelFor(v) {
  if (v.screen === "module") return displayName(v.id);
  if (v.screen === "pipelines") return "Tooling & Pipelines";
  if (v.screen === "backlog") return "Backlog";
  if (v.screen === "docker") return "Docker";
  if (v.screen === "adrs") return "ADRs";
  if (v.screen === "codequality") return "Code Quality";
  if (v.screen === "diagrams") {
    if (v.groupKey && v.diagramIndex !== undefined) {
      const g = MODEL.diagramGroups.find(x => x.key === v.groupKey);
      return `${g.label} — ${g.diagrams[v.diagramIndex].title}`;
    }
    return "Diagrams";
  }
  return "";
}

function navigate(next) {
  if (next.screen === "system") {
    crumbStack = [];
  } else if (view.screen !== "system") {
    crumbStack.push(view);
  }
  view = next;
  render();
  window.scrollTo(0, 0);
}

function navigateToCrumb(index) {
  view = crumbStack[index];
  crumbStack = crumbStack.slice(0, index);
  render();
  window.scrollTo(0, 0);
}

// Generic "one step back" -- same stack, so any button meaning "go back" (not "drill to a named
// place") stays correct automatically instead of hand-coding where it should land.
function navigateBack() {
  if (crumbStack.length === 0) { navigate({ screen: "system" }); return; }
  navigateToCrumb(crumbStack.length - 1);
}

// Same toolbar row the Module/diagram-detail screens already use -- every screen reached by
// clicking a System-level card gets the same "← back" affordance, not just the two that happened
// to need it first.
function backButtonHtml() {
  return `<div class="diagram-toolbar" style="justify-content:flex-start;gap:6px">
      <button onclick="navigateBack()">← back</button>
    </div>`;
}

function renderBreadcrumb() {
  const bc = document.getElementById("breadcrumb");
  let html = `<a onclick="navigate({screen:'system'})">System</a>`;
  crumbStack.forEach((v, i) => {
    html += `<span class="sep">›</span><a onclick="navigateToCrumb(${i})">${esc(crumbLabelFor(v))}</a>`;
  });
  if (view.screen !== "system") {
    html += `<span class="sep">›</span><span class="current">${esc(crumbLabelFor(view))}</span>`;
  }
  bc.innerHTML = html;
}

// ── System screen: just the 3 entry-point cards. Module browsing lives under Diagrams ›
// Module Dependencies (one shared graph-building function, not a second copy — see
// renderModuleDependencyGraph() and scripts/architecture/DECISIONS.md ADR-003). ──────────────────────────
function renderSystem() {
  let html = `<h2 class="screen-title">System</h2>
    <div class="screen-desc">${moduleNodes.length} modules across ${domainOrder.length} domains. Pick where to start.</div>`;

  html += `<div class="card-grid">
    <div class="card special-card" onclick="navigate({screen:'pipelines'})">
      <div class="card-title">🛠 Tooling &amp; Pipelines</div>
      <div class="card-desc">${commandNodes.length} commands, ${skillNodes.length} skills, ${scriptGroupNodes.length} script groups</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'backlog'})">
      <div class="card-title">📋 Backlog</div>
      <div class="card-desc">${backlogNode ? backlogNode.open_issues : "?"} open, ${backlogNode ? backlogNode.completed_issues : "?"} completed</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'diagrams'})">
      <div class="card-title">📐 Diagrams</div>
      <div class="card-desc">${totalDiagramCount} diagrams — dependency graph (click a module to drill in), SPI map, context map, ERD, sequence flows</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'docker'})">
      <div class="card-title">🐳 Docker</div>
      <div class="card-desc">${MODEL.dockerFiles.length} files — Dockerfiles + docker-compose stacks, what each builds/runs</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'adrs'})">
      <div class="card-title">📜 ADRs</div>
      <div class="card-desc">${(MODEL.allAdrs || []).length} architectural decisions across every module's DECISIONS.md</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'codequality'})">
      <div class="card-title">✅ Code Quality</div>
      <div class="card-desc">SonarQube + ArchUnit metrics per module, one table per source</div>
    </div>
  </div>`;

  html += `<section class="block"><h3>How this page is built</h3>
    <div class="empty-hint">
      <strong>Rendering:</strong> Cytoscape.js + cytoscape-dagre for the draggable graphs (Module Dependencies, SPI Map); Mermaid.js for the Database ERD and Sequence Diagrams.
    </div>
    <table class="simple"><thead><tr><th>Script</th><th>Description</th><th>Uses</th><th>Input</th><th>Output</th></tr></thead><tbody>`;
  (MODEL.architectureToolingSelfDocs || []).forEach(s => {
    html += `<tr><td>${sourceLink(s.file)}</td><td>${esc(s.description)}</td><td>${esc(s.uses)}</td><td>${esc(s.input)}</td><td>${esc(s.output)}</td></tr>`;
  });
  html += `</tbody></table>
    <div class="empty-hint">Read live from each script's own header comment (<code class="path">scripts/architecture/</code>) — not hand-written here, so it can't silently drift from what the scripts actually do. A small amount of genuinely non-mechanical content (a few call-flow examples, database relationships with no real foreign key) is hand-preserved as static data in the generator itself, not re-derived every run.</div>
  </section>`;

  document.getElementById("content").innerHTML = html;
}

// Domain-colored, click-to-navigate module dependency graph -- the one and only place this is
// built (previously duplicated: a rich version on the old System page, a generic Mermaid-text
// re-parse under Diagrams). Renders into #diagram-cy and assigns to the shared `diagramCy`
// variable so Diagrams' existing zoom toolbar (zoomDiagram()/#zoom-label) works unmodified.
function renderModuleDependencyGraph() {
  const els = moduleNodes.map(n => ({
    data: { id: n.id, label: n.id.replace(/-spring-boot-starter$/, ""), domain: n.domain },
    style: { "background-color": domainColor(n.domain) }
  }));
  moduleNodes.forEach(n => {
    // Layout edges point dependency -> dependent (reverse of the real semantic direction) so
    // dagre's left-to-right ranking puts foundational/most-depended-on modules on the left and
    // consumers on the right -- dagre ranks by source-before-target, and "X depends on Y" should
    // read Y (left) <- X (right).
    // The arrowhead is drawn on the *visual* source/target below, independent of layout ranking.
    ["DEPENDS_ON_COMPILE","DEPENDS_ON_RUNTIME"].forEach(et => {
      (n.edges[et] || []).forEach(target => {
        els.push({ data: { id: n.id+"->"+target+et, source: target, target: n.id, dashed: et==="DEPENDS_ON_RUNTIME" } });
      });
    });
  });
  const hasDagre = typeof cytoscapeDagre !== "undefined";
  diagramCy = cytoscape({
    container: document.getElementById("diagram-cy"),
    elements: els,
    style: [
      { selector: "node", style: {
          "label": "data(label)", "font-size": 10, "color": "#fff", "text-valign": "center", "text-halign": "center",
          "width": 92, "height": 34, "shape": "round-rectangle", "text-wrap": "wrap", "text-max-width": 84
        } },
      { selector: "edge", style: {
          "width": 1.5, "line-color": "#cbd5e0", "target-arrow-color": "#cbd5e0",
          "source-arrow-shape": "triangle", "source-arrow-color": "#cbd5e0", "target-arrow-shape": "none",
          "curve-style": "bezier"
        } },
      { selector: "edge[?dashed]", style: { "line-style": "dashed" } }
    ],
    layout: hasDagre
      ? { name: "dagre", rankDir: "LR", nodeSep: 18, rankSep: 70, edgeSep: 12, padding: 20 }
      : { name: "breadthfirst", directed: true, padding: 20, spacingFactor: 1.1 }
  });
  diagramCy.on("tap", "node", e => navigate({ screen: "module", id: e.target.id() }));
  diagramCy.on("zoom", () => {
    const label = document.getElementById("zoom-label");
    if (label) label.textContent = Math.round(diagramCy.zoom() * 100) + "%";
  });
}

// Scope-grouped dependency list for one module -- built from the same edges the graph/module
// pages use, not a second hand-typed table (this replaces the old docs/architecture/
// 01-module-dependencies.md "Dependency Table", see scripts/architecture/DECISIONS.md ADR-005). Returns
// [{label, deps}] groups; moduleDepsScopeText()/moduleDepsScopeHtml() below render this the same
// shape two ways (plain text for the markdown export, linked HTML for the on-page table) instead
// of each re-deriving compile/optional/runtime grouping independently.
function moduleDepsScopeGroups(n) {
  const compile = n.edges.DEPENDS_ON_COMPILE || [];
  const runtime = n.edges.DEPENDS_ON_RUNTIME || [];
  const optional = n.edges.DEPENDS_ON_OPTIONAL || [];
  const compileOnly = compile.filter(d => !optional.includes(d));
  const groups = [];
  if (compileOnly.length) groups.push({ label: "compile", deps: compileOnly });
  if (optional.length) groups.push({ label: "optional", deps: optional });
  if (runtime.length) groups.push({ label: "runtime", deps: runtime });
  return groups;
}

// e.g. "platform-commons, query-lib (compile); audit-spring-boot-starter (optional)" -- plain
// text, for the markdown export (exportModuleDependenciesMarkdown()).
function moduleDepsScopeText(n) {
  const groups = moduleDepsScopeGroups(n);
  return groups.length ? groups.map(g => `${g.deps.join(", ")} (${g.label})`).join("; ") : "(none — foundation)";
}

// Same grouping as moduleDepsScopeText(), but each dependency name links to its own module page --
// for the on-page Dependency Table only (HTML can't survive a markdown export, hence the separate
// plain-text variant above).
function moduleDepsScopeHtml(n) {
  const groups = moduleDepsScopeGroups(n);
  if (!groups.length) return "(none — foundation)";
  return groups.map(g => {
    const badges = g.deps.map(d => moduleBadgeHtml(d)).join(" ");
    return `${badges} <span class="scope-label">(${g.label})</span>`;
  }).join(" ");
}

// Same rounded, domain-colored pill the graph nodes use (domainColor()) -- for module names shown
// in the Dependency Table, so a module reads as "the same thing" whether it's a graph node or a
// table cell (user-requested visual consistency, 2026-08-05).
function moduleBadgeHtml(id) {
  const n = byId[id];
  const color = n ? domainColor(n.domain) : "#718096";
  return `<a class="module-badge" onclick="navigate({screen:'module',id:'${id}'})" style="background:${color}">${esc(id)}</a>`;
}

// Fixed editorial commentary about the module graph as a whole -- not mechanically derivable from
// pom.xml (these are judgment calls / interpretation, not facts), so unlike the graph/table above
// they are plain static text here rather than generated. Carried over verbatim from the retired
// docs/architecture/01-module-dependencies.md "Key Observations" section (see ADR-005) -- update
// in place here if they go stale, there is no other copy.
const MODULE_DEPENDENCY_KEY_OBSERVATIONS = [
  "**Shared Kernel:** platform-commons is the foundation — no module depends on any other module except via platform-commons SPI contracts.",
  "**Starter Independence:** Each starter (audit, attachment, user, advertisement, taxon, provider-profile) is self-contained and can be deployed independently.",
  "**Optional Dependencies:** advertisement-spring-boot-starter declares audit and attachment as <optional>true</optional> — it can run without them.",
  "**Query Library:** query-lib is a pure utility library (no Spring Boot autoconfiguration) that provides SQL filtering and sorting helpers.",
  "**No Circular Dependencies:** All edges are acyclic — the dependency graph forms a DAG.",
  "**Marketplace App Dependency:** The main application depends on all starters, composing the full feature set.",
  "**Test-Only Reactor Member:** integration-tests is the sole module allowed to depend on more than one domain starter at once — a real, compile-scope Maven dependency, not SPI-mediated. This is safe only because the module is never shipped, deployed, or depended upon by anything else (a leaf with zero inbound edges) — see integration-tests/CLAUDE.md for the full rationale and why this doesn't violate \"starters must not depend on each other.\""
];

function mdInlineToHtml(s) {
  return esc(s).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/`([^`]+)`/g, "<code>$1</code>");
}

// Real link to an ADR's own heading line in its real DECISIONS.md -- relative to this file's own
// location (docs/architecture/architecture-map.html, two levels above the repo root), so it
// resolves correctly regardless of where the repo is cloned. Opens the actual source, not a copy
// of it -- DECISIONS.md is the one place ADR text lives (see scripts/architecture/DECISIONS.md ADR-006 for
// the earlier, corrected attempt that embedded the full body text here instead).
function adrFileLink(a) {
  return `../../${a.file}`;
}

// Paragraph/list/table markdown -> HTML for ADR body text (Context/Decision/Consequences,
// amendments, tables -- real ADR content has all of these). Not a general markdown parser --
// same "only what's needed" scope as parseMermaidGraph(). Reuses mdInlineToHtml() for **bold**/
// `code` within each block.
function mdBlockToHtml(text) {
  if (!text) return "";
  // Blocks break on a blank line OR on a line-type transition (e.g. "**Decision:**" directly
  // followed by "1. ..." with no blank line between -- a real pattern in these ADRs). A plain
  // (non-marker) line while inside a list is a soft-wrapped continuation of the *previous* list
  // item, not a new paragraph -- real ADR list items wrap across multiple source lines.
  const blocks = [];
  let current = null;
  text.split("\n").forEach(raw => {
    const line = raw.trim();
    if (line === "") { current = null; return; }
    if (/^[-*] /.test(line)) {
      if (!current || current.type !== "ul") { current = { type: "ul", items: [] }; blocks.push(current); }
      current.items.push(line.replace(/^[-*] /, ""));
      return;
    }
    if (/^\d+\. /.test(line)) {
      if (!current || current.type !== "ol") { current = { type: "ol", items: [] }; blocks.push(current); }
      current.items.push(line.replace(/^\d+\. /, ""));
      return;
    }
    if (line.startsWith("|")) {
      if (!current || current.type !== "table") { current = { type: "table", lines: [] }; blocks.push(current); }
      current.lines.push(line);
      return;
    }
    if (current && (current.type === "ul" || current.type === "ol") && current.items.length) {
      current.items[current.items.length - 1] += " " + line;
      return;
    }
    if (!current || current.type !== "p") { current = { type: "p", lines: [] }; blocks.push(current); }
    current.lines.push(line);
  });
  return blocks.map(b => {
    if (b.type === "ul") return "<ul>" + b.items.map(l => `<li>${mdInlineToHtml(l)}</li>`).join("") + "</ul>";
    if (b.type === "ol") return "<ol>" + b.items.map(l => `<li>${mdInlineToHtml(l)}</li>`).join("") + "</ol>";
    if (b.type === "table" && b.lines.length > 1 && /^\|[\s:|-]+\|$/.test(b.lines[1])) {
      const rows = b.lines.filter((_, i) => i !== 1).map(l => l.split("|").slice(1, -1).map(c => c.trim()));
      const [head, ...body] = rows;
      return `<table class="simple"><thead><tr>${head.map(c => `<th>${mdInlineToHtml(c)}</th>`).join("")}</tr></thead><tbody>` +
        body.map(r => `<tr>${r.map(c => `<td>${mdInlineToHtml(c)}</td>`).join("")}</tr>`).join("") + `</tbody></table>`;
    }
    return `<p>${mdInlineToHtml(b.lines.join(" "))}</p>`;
  }).join("");
}

// Renders a module's Architectural decisions list uniformly from n.intent -- every module shows
// the same kind of clickable item, whether the ADR is homed in this module's own DECISIONS.md or
// cross-listed there via another module's "**Also affects:**" (see generate-adr-index.sh). Each
// item's "file" field already carries its real home module (json_adr_array()).
function renderAdrList(n) {
  return n.intent.map((a, i) => `
    <div class="adr-item">
      <a onclick="openAdrPopupForIntent('${esc(n.id)}', ${i})"><span class="adr-id">${esc(a.id)}</span> — ${esc(a.title)}</a>
      <span class="adr-file">${esc(a.file)}</span>
    </div>
  `).join("");
}

// Looks up the ADR's real home module (from its "file" field) and opens the popup only if that
// home module's full ADR content was embedded (see scripts/architecture/DECISIONS.md ADR-008's
// FULL_DECISIONS_MODULES) -- falls back to a real link to the source file otherwise (e.g. a
// non-Maven-module DECISIONS.md, which has no MODULE node to embed content into).
function openAdrPopupForIntent(moduleId, index) {
  const a = byId[moduleId].intent[index];
  const homeModule = a.file.replace(/\/DECISIONS\.md$/, "");
  const bareId = a.id.split(" (")[0];
  const homeNode = byId[homeModule];
  const found = homeNode && homeNode.decisions && homeNode.decisions.adrs.find(x => x.id === bareId);
  if (!found) { window.open(adrFileLink(a), "_blank"); return; }
  document.getElementById("adr-popup-title").textContent = `${found.id} — ${found.title}`;
  document.getElementById("adr-popup-status").textContent = found.status;
  document.getElementById("adr-popup-body").innerHTML = mdBlockToHtml(found.body);
  document.getElementById("adr-popup").showModal();
}

function renderModuleDependencyExtrasHtml() {
  const rows = moduleNodes.map(n =>
    `<tr><td>${moduleBadgeHtml(n.id)}</td><td>${moduleDepsScopeHtml(n)}</td></tr>`
  ).join("");
  const observations = MODULE_DEPENDENCY_KEY_OBSERVATIONS.map((o, i) =>
    `<li>${mdInlineToHtml(o).replace(/&lt;optional&gt;true&lt;\/optional&gt;/, "<code>&lt;optional&gt;true&lt;/optional&gt;</code>")}</li>`
  ).join("");
  // Domain colors are assigned by first-appearance order (domainOrder/domainColor() above), not a
  // fixed mapping -- a static legend would go stale the moment a new domain is added. Built live
  // from the exact same function the graph itself uses to color each node, so it can't drift.
  const domainLegendRows = domainOrder.map(d =>
    `<tr><td><span style="display:inline-block;width:12px;height:12px;border-radius:3px;background:${domainColor(d)};margin-right:6px;vertical-align:middle"></span>${esc(d)}</td></tr>`
  ).join("");
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">Every module dependency is declared in that module's own <code class="path">pom.xml</code> <code class="path">&lt;dependencies&gt;</code> block — this graph is rendered live from those real files, not a separately-maintained diagram. If a dependency changes, edit <code class="path">pom.xml</code>; the graph updates on the next regenerate, nothing else to keep in sync.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a node to open its module page. Drag a node to reposition it, drag empty canvas space to pan.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">──▶ (solid line)</td><td>Compile-scope dependency — arrow points toward the module being depended on</td></tr>
        <tr><td class="scope-label">┄┄▶ (dashed line)</td><td>Runtime-scope dependency</td></tr>
      </tbody></table>
      <div class="empty-hint" style="margin-top:8px">Node color = domain (same color used consistently across this diagram):</div>
      <table class="simple"><tbody>${domainLegendRows}</tbody></table>
    </section>
    <section class="block"><h3>Dependency Table</h3>
      <table class="simple"><thead><tr><th>Module</th><th>Depends on</th></tr></thead><tbody>${rows}</tbody></table>
    </section>
    ${renderArchitectureChecksHtml()}
    <section class="block"><h3>Key Observations</h3><ol class="info-list">${observations}</ol></section>
    <section class="block"><h3>Module Versions</h3>
      <div class="empty-hint">All modules are siblings with the same version: <code>${esc(MODEL.rootVersion)}</code>. Parent POM artifact: <code>${esc(MODEL.rootArtifactId)}</code>.</div>
    </section>
    ${renderLargestJavaFilesHtml()}
    ${renderConstructorInjectionHtml()}
    ${renderGodPackagesHtml()}`;
}

// Real grep-based checks (cyclic-import safety net beyond the module-level DAG already shown
// above), re-run every generation -- each PASS/FAIL carries the real evidence, not hand-typed text.
function renderArchitectureChecksHtml() {
  const checks = MODEL.couplingChecks || [];
  const rows = checks.map(c =>
    `<tr><td>${c.pass ? "✓ PASS" : "✗ FAIL"}</td><td>${esc(c.name)}</td><td>${esc(c.evidence)}</td></tr>`
  ).join("");
  return `<section class="block"><h3>Architecture Checks (${checks.length})</h3>
    <table class="simple"><thead><tr><th>Result</th><th>Check</th><th>Evidence</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

function renderLargestJavaFilesHtml() {
  const files = MODEL.largestJavaFiles || [];
  if (!files.length) return "";
  const rows = files.map(f =>
    `<tr><td>${esc(f.file)}</td><td>${f.lines}</td><td>${moduleBadgeHtml(f.module) || esc(f.module)}</td></tr>`
  ).join("");
  return `<section class="block"><h3>Largest Java Files</h3>
    <div class="empty-hint">Top 10 by line count, across the whole repo -- recomputed every generation.</div>
    <table class="simple"><thead><tr><th>File</th><th>Lines</th><th>Module</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

function renderConstructorInjectionHtml() {
  const items = MODEL.constructorInjection || [];
  if (!items.length) return "";
  const rows = items.map(i =>
    `<tr><td>${esc(i.class)}</td><td>${moduleBadgeHtml(i.module) || esc(i.module)}</td><td>${i.fieldCount}</td></tr>`
  ).join("");
  return `<section class="block"><h3>Constructor Injection (${items.length})</h3>
    <div class="empty-hint">Every class with <code class="path">@RequiredArgsConstructor</code> and 4+ injected fields.</div>
    <table class="simple"><thead><tr><th>Class</th><th>Module</th><th>Field count</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

function renderGodPackagesHtml() {
  const items = MODEL.godPackages || [];
  if (!items.length) return "";
  const rows = items.map(p =>
    `<tr><td><code class="path">${esc(p.package)}</code></td><td>${p.fileCount}</td></tr>`
  ).join("");
  return `<section class="block"><h3>Largest Packages (${items.length})</h3>
    <div class="empty-hint">Packages (recursive) with more than 20 .java files.</div>
    <table class="simple"><thead><tr><th>Package</th><th>File count</th></tr></thead><tbody>${rows}</tbody></table>
  </section>`;
}

// Same node/edge data renderModuleDependencyGraph() draws with Cytoscape, as plain Mermaid text --
// for the markdown export only (Cytoscape has no "serialize back to Mermaid" of its own). Mirrors
// the retired docs/architecture/01-module-dependencies.md diagram's own `graph LR` shape.
function buildModuleDependencyMermaid() {
  let out = "graph LR\n";
  moduleNodes.forEach(n => { out += `    ${n.id.replace(/-/g,"_")}["${n.id}"]\n`; });
  out += "\n";
  moduleNodes.forEach(n => {
    ["DEPENDS_ON_COMPILE","DEPENDS_ON_RUNTIME","DEPENDS_ON_OPTIONAL"].forEach(et => {
      (n.edges[et] || []).forEach(dep => {
        out += `    ${n.id.replace(/-/g,"_")} --> ${dep.replace(/-/g,"_")}\n`;
      });
    });
  });
  return out;
}

// Client-side only -- triggers a browser download of generated text. Nothing is written back to
// the repo; this is for pasting into a wiki/issue/chat, not a tracked file, so there is no
// staleness risk to guard against (contrast the retired approach of committing a generated block
// into docs/architecture/01-module-dependencies.md, which needed its own CI freshness gate --
// see scripts/architecture/DECISIONS.md ADR-005). Shared by every "Export as Markdown" button in this tool.
function downloadMarkdown(filename, content) {
  const blob = new Blob([content], { type: "text/markdown" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

function exportModuleDependenciesMarkdown() {
  let md = `# Module Dependencies\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › Module Dependencies.\n\n`;
  md += `## Dependency Graph\n\n\`\`\`mermaid\n${buildModuleDependencyMermaid()}\`\`\`\n`;
  md += `\n## Dependency Table\n\n| Module | Depends on |\n|---|---|\n`;
  moduleNodes.forEach(n => { md += `| ${n.id} | ${moduleDepsScopeText(n)} |\n`; });
  md += `\n## Key Observations\n\n`;
  MODULE_DEPENDENCY_KEY_OBSERVATIONS.forEach((o, i) => { md += `${i + 1}. ${o}\n`; });
  md += `\n## Module Versions\n\nAll modules are siblings with the same version: \`${MODEL.rootVersion}\`. Parent POM artifact: \`${MODEL.rootArtifactId}\`.\n`;
  downloadMarkdown("module-dependencies.md", md);
}

// Full snapshot of one module's page. ADRs are listed as title + a real file:line reference, not
// a copy of the ADR text -- same "resolve to the one source, never restate it" rule the on-page
// list follows (see adrFileLink() above).
function exportModuleMarkdown(id) {
  const n = byId[id];
  if (!n) return;
  let md = `# ${n.id}\n\n${n.description || ""}\n\n`;
  if (n.tables && n.tables.length) md += `## Tables owned\n\n${n.tables.map(t => `- ${t}`).join("\n")}\n\n`;
  if (n.entities && n.entities.length) md += `## Entities\n\n${n.entities.map(e => `- ${e}`).join("\n")}\n\n`;
  if (n.keyServices && n.keyServices.length) md += `## Key services\n\n${n.keyServices.map(s => `- ${s}`).join("\n")}\n\n`;
  if (n.contracts && n.contracts.length) md += `## Contracts (Port/Hook)\n\n${n.contracts.map(c => `- ${c}`).join("\n")}\n\n`;
  md += `## Depends on (compile)\n\n${(n.edges.DEPENDS_ON_COMPILE||[]).map(d=>`- ${d}`).join("\n") || "None."}\n\n`;
  if ((n.edges.DEPENDS_ON_RUNTIME||[]).length) md += `## Depends on (runtime)\n\n${n.edges.DEPENDS_ON_RUNTIME.map(d=>`- ${d}`).join("\n")}\n\n`;
  if ((n.edges.DEPENDS_ON_OPTIONAL||[]).length) md += `## Depends on (optional)\n\n${n.edges.DEPENDS_ON_OPTIONAL.map(d=>`- ${d}`).join("\n")}\n\n`;
  md += `## Depended on by\n\n${(n.edges.DEPENDED_ON_BY||[]).map(d=>`- ${d}`).join("\n") || "Nothing (leaf module)."}\n\n`;
  md += `## Architectural decisions\n\n`;
  (n.intent || []).forEach(a => { md += `- **${a.id}: ${a.title}** — see \`${a.file}\`\n`; });
  md += `\n---\nEvidence: ${n.evidence[0].file}\n`;
  downloadMarkdown(`${n.id}.md`, md);
}

// ── Module screen: full readable detail, drill-down deps, ADRs, Track B placeholder slots ──────
function renderDepList(ids, label, emptyText) {
  if (!ids || ids.length === 0) return `<div class="empty-hint">${emptyText}</div>`;
  return `<ul class="dep-list">` + ids.map(id => {
    const n = byId[id];
    return `<li><a onclick="navigate({screen:'module',id:'${id}'})">${esc(id)}</a>${n ? `<span class="dep-tag">${esc(n.domain)}</span>` : ""}</li>`;
  }).join("") + `</ul>`;
}

// Real code-quality/coupling numbers for one module -- SonarQube (ncloc/complexity/code smells/
// duplication/file count, from a running Sonar server) and ArchUnit (Efferent/Afferent Coupling,
// Instability, Abstractness, from the real class-dependency graph via
// ArchitectureMetricsExport.java) now live on their own System-level "Code Quality" screen
// (renderCodeQuality() below), one table per source so it's always clear which numbers came from
// where -- not repeated per module page anymore.
// Green/yellow/red thresholds -- only for *ratios*, never raw counts (Ce/Ca, code smells,
// complexity totals have no universal per-module threshold; only per-file/per-LOC/derived ratios
// do). value/thresholds -> the metric-good/metric-watch/metric-critical class.
function metricClass(value, green, yellow) {
  if (value < green) return "metric-good";
  if (value < yellow) return "metric-watch";
  return "metric-critical";
}
function coloredMetric(value, decimals, green, yellow) {
  return `<span class="${metricClass(value, green, yellow)}">${esc(value.toFixed(decimals))}</span>`;
}

const CODE_QUALITY_GLOSSARY = [
  { field: "Java files", desc: "Number of .java files under this module's src/main/java. The denominator for the two per-file ratios below — on its own it's just a size indicator, not a quality signal." },
  { field: "Lines of code", desc: "Non-comment lines of code (NCLOC), as counted by SonarQube. A raw size measure, not a complexity measure — a large module can still be simple if it's mostly straightforward, repetitive code (e.g. DTOs, generated mappers)." },
  { field: "Complexity", desc: "Cyclomatic complexity — the number of independent execution paths through the code, counted from branching statements (if/for/while/case/&&/||). Higher means more paths a test suite has to cover to reach full branch coverage, and more ways the code can behave differently at runtime. This is the module's raw total, not yet normalized by size — a big module naturally has a bigger raw total even if each individual method is simple." },
  { field: "Complexity/file", desc: "Complexity divided by Java file count — normalizes away module size so modules of very different sizes can be compared fairly. A high value means the average file is doing a lot of branching, which usually also means it's doing more than one job and is a candidate for splitting. Colored: green < 10, yellow 10-20, red > 20." },
  { field: "Cognitive complexity", desc: "SonarQube's readability metric — unlike cyclomatic complexity, it specifically penalizes nested/tangled control flow (an if inside a loop inside a try inside another if) more heavily than the same number of branches laid out flat, because nesting is what actually makes code hard for a human to hold in their head while reading it." },
  { field: "Cognitive/file", desc: "Cognitive complexity divided by Java file count. A high value is a stronger signal than high raw Complexity/file that a file is genuinely hard to read (not just branch-heavy) — worth a closer look even before it hits a hard SonarQube rule threshold. Colored: green < 10, yellow 10-25, red > 25." },
  { field: "Code smells", desc: "Count of maintainability issues SonarQube's rule engine actually flagged in this module (naming, dead code, duplicated logic, overly long methods, etc.) — a concrete, itemized list (visible in the SonarQube dashboard itself), not a derived estimate like the complexity numbers above." },
  { field: "Code smells/1k LOC", desc: "Code smells normalized per 1000 lines of code, so a small module with 2 smells isn't unfairly compared against a large module with 20 — what matters is smell density, not raw count. Colored: green < 5, yellow 5-15, red > 15." },
  { field: "Efferent coupling (Ce)", desc: "How many classes outside this module the classes inside it depend on — this module's \"outgoing\" dependencies. High Ce means this module is exposed to a lot of external change: if any of those other classes change their API, this module is one of the things that might break." },
  { field: "Afferent coupling (Ca)", desc: "How many classes outside this module depend on classes inside it — this module's \"incoming\" dependencies, i.e. its real-world blast radius. High Ca means many other parts of the codebase would be affected if this module's public API changed, so changes here need extra care." },
  { field: "Instability (I)", desc: "I = Ce/(Ce+Ca), from Robert Martin's stability metrics — 0 means maximally stable (only depended upon, never depends on anything itself — safe to keep unchanged, risky to modify), 1 means maximally unstable (only depends on others, nothing depends on it — safe to change freely, since nothing breaks downstream)." },
  { field: "Abstractness (A)", desc: "The ratio of abstract classes/interfaces to total classes in the module. A module that's mostly concrete implementation classes has low A; a module that's mostly interfaces/abstract contracts (like an SPI layer) has high A." },
  { field: "Distance from Main Sequence", desc: "|A+I-1| — the classic \"is this module positioned sensibly\" check from Martin's stability/abstractness model. 0 is the ideal balance (stable modules should be abstract, unstable modules should be concrete). Far from 0 in the low-A/low-I direction is the \"Zone of Pain\" (concrete AND stable — hard to change safely, because nothing about it is abstracted away, yet lots of things depend on it). Far from 0 in the high-A/high-I direction is the \"Zone of Uselessness\" (abstract AND unstable — an interface layer nothing actually relies on, which usually means it's dead weight). Colored: green < 0.3, yellow 0.3-0.6, red > 0.6." },
];

function renderCodeQuality() {
  const sonar = MODEL.sonarMetrics;
  const arch = MODEL.archUnitMetrics;
  let html = backButtonHtml();
  html += `<h2 class="screen-title">Code Quality</h2>
    <div class="screen-desc">Real SonarQube + ArchUnit metrics per module, one table per source. Both opt-in (<code>--with-sonar</code>/<code>--with-archunit</code> on <code>generate-architecture-model.sh</code>), off by default -- regenerate with those flags to populate this screen. Raw counts are shown plain; derived ratios are colored (see Overview at the bottom for thresholds).</div>`;

  html += `<section class="block"><h3>SonarQube</h3>`;
  if (!sonar) {
    html += `<div class="empty-hint">No data -- regenerate with <code>--with-sonar</code> (requires a reachable SonarQube server).</div>`;
  } else {
    html += `<div class="empty-hint">Source: SonarQube (analysis: ${esc(sonar.analysisDate || "unknown")}).</div>
      <table class="simple"><thead><tr><th>Module</th><th>Java files</th><th>Lines of code</th><th>Complexity</th><th>Complexity/file</th><th>Cognitive complexity</th><th>Cognitive/file</th><th>Code smells</th><th>Code smells/1k LOC</th></tr></thead><tbody>`;
    moduleNodes.forEach(n => {
      const m = sonar.modules && sonar.modules[n.id];
      if (!m) return;
      const perFile = m.javaFileCount > 0 ? m.complexity / m.javaFileCount : 0;
      const cogPerFile = m.javaFileCount > 0 ? m.cognitiveComplexity / m.javaFileCount : 0;
      const smellsPer1k = m.ncloc > 0 ? m.codeSmells / (m.ncloc / 1000) : 0;
      html += `<tr><td><a class="module-link" onclick="navigate({screen:'module', id:'${esc(n.id)}'})">${esc(n.id)}</a></td><td>${m.javaFileCount}</td><td>${m.ncloc}</td><td>${m.complexity}</td><td>${coloredMetric(perFile, 1, 10, 20)}</td><td>${m.cognitiveComplexity}</td><td>${coloredMetric(cogPerFile, 1, 10, 25)}</td><td>${m.codeSmells}</td><td>${coloredMetric(smellsPer1k, 1, 5, 15)}</td></tr>`;
    });
    html += `</tbody></table>`;
  }
  html += `</section>`;

  html += `<section class="block"><h3>ArchUnit</h3>`;
  if (!arch) {
    html += `<div class="empty-hint">No data -- regenerate with <code>--with-archunit</code> (requires <code>bash scripts/unit-tests.sh</code> to have run at least once).</div>`;
  } else {
    html += `<div class="empty-hint">Source: ArchUnit (from the last <code>bash scripts/unit-tests.sh</code> run).</div>
      <table class="simple"><thead><tr><th>Module</th><th>Efferent coupling</th><th>Afferent coupling</th><th>Instability</th><th>Abstractness</th><th>Distance from Main Sequence</th></tr></thead><tbody>`;
    moduleNodes.forEach(n => {
      const m = arch.modules && arch.modules[n.id];
      if (!m) return;
      const distance = Math.abs(m.abstractness + m.instability - 1);
      html += `<tr><td><a class="module-link" onclick="navigate({screen:'module', id:'${esc(n.id)}'})">${esc(n.id)}</a></td><td>${m.efferentCoupling}</td><td>${m.afferentCoupling}</td><td>${esc(m.instability.toFixed(2))}</td><td>${esc(m.abstractness.toFixed(2))}</td><td>${coloredMetric(distance, 2, 0.3, 0.6)}</td></tr>`;
    });
    html += `</tbody></table>`;
  }
  html += `</section>`;

  html += `<section class="block"><h3>Overview</h3>
    <table class="simple"><thead><tr><th>Field</th><th>What it means</th></tr></thead><tbody>` +
    CODE_QUALITY_GLOSSARY.map(g => `<tr><td class="scope-label">${esc(g.field)}</td><td>${esc(g.desc)}</td></tr>`).join("") +
    `</tbody></table></section>`;

  document.getElementById("content").innerHTML = html;
}

function renderModule() {
  const n = byId[view.id];
  if (!n) { navigate({ screen: "system" }); return; }
  let html = `<div class="diagram-toolbar" style="justify-content:flex-start;gap:6px">
      <button onclick="navigateBack()">← back</button>
      <button onclick="exportModuleMarkdown('${n.id}')">⭳ Export as Markdown</button>
    </div>
    <div class="module-header">
      <h2>${esc(n.id)}</h2>
      <span class="badge ${n.lifecycle}">${n.lifecycle}</span>
      <span class="domain-tag">${esc(n.domain)}</span>
    </div>
    <div class="screen-desc">${esc(n.description || "no one-line description available")}</div>`;

  if (n.tables && n.tables.length) {
    html += `<section class="block"><h3>Tables owned</h3>` + n.tables.map(t => `<span class="table-chip">${esc(t)}</span>`).join("") + `</section>`;
  }
  if (n.entities && n.entities.length) {
    html += `<section class="block"><h3>Entities</h3><ul class="info-list">` + n.entities.map(e => `<li>${e.replace(/`([^`]+)`/g, (m,c)=>`<code>${esc(c)}</code>`)}</li>`).join("") + `</ul></section>`;
  }
  if (n.keyServices && n.keyServices.length) {
    html += `<section class="block"><h3>Key services</h3><ul class="info-list">` + n.keyServices.map(s => `<li>${s.replace(/`([^`]+)`/g, (m,c)=>`<code>${esc(c)}</code>`)}</li>`).join("") + `</ul></section>`;
  }
  if (n.contracts && n.contracts.length) {
    html += `<section class="block"><h3>Contracts (Port/Hook)</h3><ul class="info-list">` + n.contracts.map(c => `<li>${c.replace(/`([^`]+)`/g, (m,g)=>`<code>${esc(g)}</code>`)}</li>`).join("") + `</ul></section>`;
  }

  html += `<section class="block"><h3>Depends on (compile)</h3>${renderDepList(n.edges.DEPENDS_ON_COMPILE, "compile", "No compile-time module dependencies.")}</section>`;
  if ((n.edges.DEPENDS_ON_RUNTIME||[]).length) html += `<section class="block"><h3>Depends on (runtime)</h3>${renderDepList(n.edges.DEPENDS_ON_RUNTIME, "runtime", "")}</section>`;
  if ((n.edges.DEPENDS_ON_OPTIONAL||[]).length) html += `<section class="block"><h3>Depends on (optional)</h3>${renderDepList(n.edges.DEPENDS_ON_OPTIONAL, "optional", "")}</section>`;
  html += `<section class="block"><h3>Depended on by</h3>${renderDepList(n.edges.DEPENDED_ON_BY, "", "Nothing in this repo depends on it directly (leaf module).")}</section>`;

  html += `<section class="block"><h3>Deeper levels (Track B — not built yet)</h3>
    <div class="placeholder-row"><span class="name">Contract method signatures &amp; types</span><span class="tag">needs ArchUnit exporter — names only above</span></div>
    <div class="placeholder-row"><span class="name">Implementation classes</span><span class="tag">needs ArchUnit exporter</span></div>
    <div class="placeholder-row"><span class="name">Methods</span><span class="tag">needs ArchUnit exporter</span></div>
    <div class="placeholder-row"><span class="name">Test coverage (DIRECT/INDIRECT/E2E)</span><span class="tag">needs ArchUnit exporter</span></div>
  </section>`;

  html += `<div class="empty-hint">Evidence: <code class="path">${esc(n.evidence[0].file)}</code></div>`;

  document.getElementById("content").innerHTML = html;
}

// ── Tooling & Pipelines screen ───────────────────────────────────────────────────────────────
// Real link to a source file, relative to this file's own location (two levels above the repo
// root) -- opens the actual file, same "resolve to source, never restate it" rule as adrFileLink().
function sourceLink(relPath) {
  return `<a href="../../${esc(relPath)}" target="_blank"><code class="path">${esc(relPath)}</code></a>`;
}

function renderScriptGroupSection(n) {
  let html = `<section class="block"><h3>${esc(n.id)}</h3>`;
  if (n.description) html += `<div class="screen-desc">${esc(n.description)}</div>`;
  if (n.files && n.files.length) {
    html += `<div class="table-chip-row">` + n.files.map(f => sourceLink(`${n.id}/${f}`)).join(" ") + `</div>`;
  }
  if (n.intent && n.intent.length) {
    const ownFileEntry = n.intent.find(a => a.file === `${n.id}/DECISIONS.md`) || n.intent[0];
    html += `<div class="adr-subheading">Architectural decisions (${n.intent.length}) <a class="section-link" href="${adrFileLink(ownFileEntry)}" target="_blank">view full file</a></div>`;
    html += renderAdrList(n);
  }
  html += `</section>`;
  return html;
}

function renderPipelines() {
  let html = backButtonHtml();
  html += `<h2 class="screen-title">Tooling &amp; Pipelines</h2>
    <div class="screen-desc">Slash commands, skills, and scripts available in this repo — sourced from .claude/commands, .claude/skills, and scripts/**, cross-checked against docs/ai/flows.md.</div>`;

  html += `<h3 class="group-heading">AI Tooling</h3>`;
  html += `<section class="block"><h3>Commands (${commandNodes.length})</h3><table class="simple"><thead><tr><th>Command</th><th>Description</th><th>Source</th></tr></thead><tbody>`;
  commandNodes.forEach(n => {
    html += `<tr><td>/${esc(displayName(n.id))}</td><td>${esc(n.description)}</td><td>${sourceLink(n.evidence[0].file)}</td></tr>`;
  });
  html += `</tbody></table></section>`;

  html += `<section class="block"><h3>Skills (${skillNodes.length})</h3><table class="simple"><thead><tr><th>Skill</th><th>Description</th><th>Source</th></tr></thead><tbody>`;
  skillNodes.forEach(n => {
    html += `<tr><td>${esc(displayName(n.id))}</td><td>${esc(n.description)}</td><td>${sourceLink(n.evidence[0].file)}</td></tr>`;
  });
  html += `</tbody></table></section>`;

  scriptGroupNodes.filter(n => n.category === "ai").forEach(n => { html += renderScriptGroupSection(n); });

  html += `<h3 class="group-heading">Other Scripts</h3>`;
  scriptGroupNodes.filter(n => n.category === "scripts").forEach(n => { html += renderScriptGroupSection(n); });

  document.getElementById("content").innerHTML = html;
}

// ── Backlog screen ───────────────────────────────────────────────────────────────────────────
// Priority text is free-form prose ("medium-high (UX)", "🔵 low now, rising with data volume"),
// not a clean enum -- ranked by keyword match, compound levels (medium-high, low-medium) checked
// before their plain counterparts so "medium-high" doesn't misrank as plain "medium".
function priorityRank(p) {
  const s = (p || "").toLowerCase();
  if (/critical/.test(s)) return 0;
  if (/\btop\b/.test(s)) return 1;
  if (/highest/.test(s)) return 2;
  if (/medium-high|high\/medium|high for/.test(s)) return 3;
  if (/\bhigh\b/.test(s)) return 4;
  if (/\bmedium\b/.test(s)) return 5;
  if (/low-medium/.test(s)) return 6;
  if (/\blow\b|cheap/.test(s)) return 7;
  if (/lowest/.test(s)) return 8;
  return 9;
}

// backlog/BACKLOG.md's "At a glance" table already has the real, curated execution order (e.g.
// 138 before 136 before 135, even though all three are "Top" tier) -- re-deriving order from each
// issue's own free-form Priority prose only recovers coarse tiers, losing that fine-grained
// sequence. Issues listed in the table sort by their real position there; anything not yet
// triaged into that compact view falls back to its own Priority-text tier, after every listed one.
function backlogOrderRank(file) {
  const m = file.match(/improvement-(\d+)/);
  if (!m) return -1;
  return MODEL.backlogPriorityOrder.indexOf(m[1]);
}
function issueSortKey(it) {
  const rank = backlogOrderRank(it.file);
  return rank >= 0 ? [0, rank] : [1, priorityRank(it.priority)];
}

// Same click-to-popup pattern as ADRs: title in the list, click shows the short description,
// the popup body itself carries a real link to open the full issue file. Indices passed to
// openIssuePopup() stay the *original* array positions, since that's what it looks up by.
function renderIssueList(kind, items) {
  if (!items || !items.length) return `<div class="empty-hint">None.</div>`;
  const order = items.map((_, i) => i).sort((a, b) => {
    const ka = issueSortKey(items[a]), kb = issueSortKey(items[b]);
    return ka[0] - kb[0] || ka[1] - kb[1];
  });
  return order.map(i => {
    const it = items[i];
    return `
    <div class="adr-item">
      <a onclick="openIssuePopup('${kind}', ${i})">${esc(it.title)}</a>
      ${it.priority ? `<span class="adr-status">${esc(it.priority)}</span>` : ""}
      <span class="adr-file">${esc(it.file)}</span>
    </div>
  `;
  }).join("");
}

function openIssuePopup(kind, index) {
  const item = backlogNode[kind === "open" ? "openIssues" : "completedIssues"][index];
  if (!item) return;
  document.getElementById("adr-popup-title").textContent = item.title;
  document.getElementById("adr-popup-status").textContent = item.priority || "";
  document.getElementById("adr-popup-body").innerHTML =
    `<p>${mdInlineToHtml(item.description)}</p><p>${sourceLink(item.file)}</p>`;
  document.getElementById("adr-popup").showModal();
}

let backlogFilter = "open";
function setBacklogFilter(f) { backlogFilter = f; renderBacklog(); }

function renderBacklog() {
  const items = backlogFilter === "open" ? backlogNode.openIssues : backlogNode.completedIssues;
  const label = backlogFilter === "open" ? "Open issues" : "Completed issues";
  let html = backButtonHtml();
  html += `<h2 class="screen-title">Backlog</h2>
    <div class="screen-desc">Per-issue titles in Track A — see ${sourceLink("backlog/BACKLOG.md")} for the ranked, priority view.</div>
    <div class="card-grid">
      <div class="card ${backlogFilter === "open" ? "card-active" : ""}" onclick="setBacklogFilter('open')"><div class="card-title">${backlogNode.open_issues}</div><div class="card-desc">open issues (backlog/issues/)</div></div>
      <div class="card ${backlogFilter === "completed" ? "card-active" : ""}" onclick="setBacklogFilter('completed')"><div class="card-title">${backlogNode.completed_issues}</div><div class="card-desc">completed issues (backlog/completed/issues/)</div></div>
    </div>`;
  html += `<section class="block"><h3>${label} (${items.length})</h3>${renderIssueList(backlogFilter, items)}</section>`;
  document.getElementById("content").innerHTML = html;
}

// Diagram groups whose Mermaid source is a `graph`/`flowchart` (nodes + edges, optionally
// grouped in `subgraph` blocks) -- these get parsed and re-rendered as real Cytoscape graphs, so
// nodes are draggable and edges follow, exactly like the System map (user-requested, 2026-08-04:
// "System" felt right, the others didn't -- because they're still static Mermaid SVGs). ERD
// (04) and sequence diagrams (05) are NOT graph-shaped in the same sense (ERD needs a
// table/column renderer Cytoscape doesn't give for free; sequence diagrams are temporal/lifeline
// diagrams, not spatial node graphs -- dragging boxes around doesn't map onto what they show) --
// those stay Mermaid, with pan+zoom, and the UI says why rather than silently behaving differently.
const GRAPH_TYPE_KEYS = ["01-module-dependencies", "02-spi-map"];

// Minimal parser for this repo's own Mermaid dialect -- only what docs/architecture/01-03 actually
// use: `graph LR|TD|TB`, `ID["Label<br/>text"]` node declarations, `subgraph ID["Label"] ... end`
// blocks (become Cytoscape compound parents), and edges `A --> B`, `A -->|label| B`,
// `A -.->|label| B` (dashed). Not a general Mermaid grammar -- would need extending if a diagram
// ever uses a shape/edge syntax this repo's diagrams don't currently use.
function parseMermaidGraph(source) {
  const nodes = new Map();
  const edges = [];
  const stack = [];
  const clean = s => s.replace(/<br\s*\/?>/gi, " ").trim();
  const ensure = id => {
    if (!nodes.has(id)) nodes.set(id, { id, label: id, parent: stack[stack.length - 1] || null });
    return nodes.get(id);
  };
  source.split("\n").map(l => l.trim()).filter(Boolean).forEach(line => {
    let m;
    if (/^graph\s/.test(line)) return;
    if ((m = /^subgraph\s+(\w+)\["([^"]*)"\]/.exec(line))) {
      nodes.set(m[1], { id: m[1], label: clean(m[2]), parent: stack[stack.length - 1] || null, isGroup: true });
      stack.push(m[1]);
      return;
    }
    if (line === "end") { stack.pop(); return; }
    if ((m = /^(\w+)\["([^"]*)"\]\s*$/.exec(line))) {
      ensure(m[1]).label = clean(m[2]);
      return;
    }
    if ((m = /^(\w+)\s*(-->|-\.->)\s*(?:\|([^|]*)\|\s*)?(\w+)/.exec(line))) {
      const [, src, arrow, elabel, tgt] = m;
      ensure(src); ensure(tgt);
      edges.push({ source: src, target: tgt, label: elabel ? clean(elabel) : "", dashed: arrow.indexOf(".") !== -1 });
    }
  });
  return { nodes: [...nodes.values()], edges };
}

let diagramCy = null;

// Shared by every compound-node (group/parent) Cytoscape render -- Module Dependencies has its
// own simpler flat renderer (renderModuleDependencyGraph()); this one is for graphs with subgroups
// (Mermaid-parsed diagrams, and SPI Map's own live-generated node/edge data -- same shape).
function renderCytoscapeFromGraph(nodes, edges, rankDir) {
  const parentOf = {};
  nodes.forEach(n => { parentOf[n.id] = n.parent || null; });
  // An edge directly between a compound parent and its own descendant forms a 2-cycle dagre's
  // compound-aware ranking can't resolve -- it collapses the whole graph onto one rank. Nesting
  // already shows the containment relationship visually, so these edges are redundant to draw.
  const layoutEdges = edges.filter(e => parentOf[e.target] !== e.source && parentOf[e.source] !== e.target);
  const els = [
    ...nodes.map(n => ({
      data: { id: n.id, label: n.label, parent: n.parent || undefined, file: n.file || undefined },
      classes: n.isGroup ? "group-node" : ""
    })),
    ...layoutEdges.map((e, i) => ({
      data: { id: "e" + i, source: e.source, target: e.target, label: e.label || "", dashed: e.dashed }
    }))
  ];
  const hasDagre = typeof cytoscapeDagre !== "undefined";
  diagramCy = cytoscape({
    container: document.getElementById("diagram-cy"),
    elements: els,
    style: [
      { selector: "node", style: {
          "label": "data(label)", "font-size": 10, "color": "#1a202c", "text-valign": "center", "text-halign": "center",
          "background-color": "#ebf4ff", "border-width": 1.5, "border-color": "#2b6cb0",
          "shape": "round-rectangle", "width": "label", "height": "label", "padding": "10px",
          "text-wrap": "wrap", "text-max-width": 110
        } },
      { selector: "node.group-node", style: {
          "background-color": "#f7f8fa", "background-opacity": 0.6, "border-color": "#a0aec0",
          "border-style": "dashed", "text-valign": "top", "font-weight": "bold", "font-size": 11,
          "padding": "16px"
        } },
      { selector: "edge", style: {
          "width": 1.5, "line-color": "#a0aec0", "target-arrow-color": "#a0aec0",
          "target-arrow-shape": "triangle", "curve-style": "bezier",
          "font-size": 9, "label": "data(label)", "color": "#4a5568",
          "text-background-color": "#f7f8fa", "text-background-opacity": 0.9, "text-background-padding": 2
        } },
      { selector: "edge[?dashed]", style: { "line-style": "dashed" } }
    ],
    layout: hasDagre
      ? { name: "dagre", rankDir: rankDir || "TB", nodeSep: 18, rankSep: 70, padding: 20 }
      : { name: "breadthfirst", directed: true, padding: 20 }
  });
  diagramCy.on("zoom", () => {
    const label = document.getElementById("zoom-label");
    if (label) label.textContent = Math.round(diagramCy.zoom() * 100) + "%";
  });
  // Any node carrying a real "file" (SPI Map's interfaces/impl classes) opens it directly --
  // group/parent nodes and Mermaid-parsed diagrams with no file association just have no data to
  // open, so nothing happens on click there, same as before.
  diagramCy.on("tap", "node[file]", e => window.open("../../" + e.target.data("file"), "_blank"));
  diagramCy.nodes("[file]").style("cursor", "pointer");
}

function renderCytoscapeDiagram(source, rankDir) {
  const parsed = parseMermaidGraph(source);
  renderCytoscapeFromGraph(parsed.nodes, parsed.edges, rankDir);
}

// Live from real Java source (platform-commons/*/spi interfaces + real `implements` across
// starters/marketplace-app) -- same "live, not a separately-maintained .md" pattern as Module
// Dependencies. Left-to-right layout (interfaces on the left, implementations on the right) reads
// better for this 2-level interface->impl shape than top-to-bottom.
function renderSpiMapGraph() {
  renderCytoscapeFromGraph(MODEL.spiMap.nodes, MODEL.spiMap.edges, "LR");
}

// Real link, readable class name as the visible text -- same "open the actual source, short
// label" pattern adrFileLink()/exportModuleMarkdown() already use for ADRs, not a raw path dump.
function spiFileLink(file, label) {
  return `<a href="../../${esc(file)}" target="_blank">${esc(label)}</a>`;
}

// One table per subsystem (package prefix shown once per heading, same as the retired .md did) --
// not one flat table -- plus each subsystem's own editorial note (SPI_SUBSYSTEM_NOTE) when present.
function renderSpiSubsystemTables() {
  return MODEL.spiMap.subsystemOrder.map(s => {
    const rows = MODEL.spiMap.details.filter(d => d.subsystem === s);
    if (!rows.length) return "";
    const note = MODEL.spiMap.subsystemNotes[s];
    const trs = rows.map(d => {
      const impls = d.implementations.length
        ? d.implementations.map(i => `${spiFileLink(i.file, i.class)} <span class="scope-label">${esc(i.module)}</span>`).join("<br>")
        : `<span class="empty-hint">no implementation found</span>`;
      return `<tr><td>${spiFileLink(d.file, d.interface)}</td><td class="scope-label">${esc(d.kind)}</td><td>${impls}</td><td>${esc(d.purpose)}</td></tr>`;
    }).join("");
    return `
      <div class="domain-group">
        <h3>${esc(MODEL.spiMap.subsystemLabels[s])} <code class="path">${esc(rows[0].package)}</code></h3>
        <table class="simple"><thead><tr><th>Interface</th><th>Direction</th><th>Implementation(s)</th><th>Purpose</th></tr></thead><tbody>${trs}</tbody></table>
        ${note ? `<div class="empty-hint" style="margin-top:8px">${mdInlineToHtml(note)}</div>` : ""}
      </div>`;
  }).join("");
}

function renderSpiMapExtrasHtml() {
  const flows = MODEL.spiCallFlowExamples.map(f =>
    `<div class="adr-item"><strong>${esc(f.title)}</strong><ol class="info-list">${f.steps.map(s => `<li>${mdInlineToHtml(s)}</li>`).join("")}</ol></div>`
  ).join("");
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">All cross-module extension points (Ports and Hooks) live in <code class="path">platform-commons</code> to decouple starters from marketplace-app. Suffixes encode call direction: <code class="path">*Port</code> = marketplace &rarr; starter (marketplace calls the starter); <code class="path">*Hook</code> = starter &rarr; marketplace (starter calls back to marketplace). See ${sourceLink("platform-commons/CLAUDE.md")}'s "SPI Interface Naming" table for the authoritative direction/role definition of each suffix.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a node to open its real <code class="path">.java</code> file. Drag a node to reposition it, drag empty canvas space to pan.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">Dashed gray box</td><td>A module boundary (<code class="path">platform-commons</code>, a starter, or <code class="path">marketplace-app</code>)</td></tr>
        <tr><td class="scope-label">Blue rounded box</td><td>A Java interface or implementation class</td></tr>
        <tr><td class="scope-label">──▶</td><td>"Implements" — arrow points from the interface to its implementation class</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>SPI Interface Details (${MODEL.spiMap.details.length})</h3>${renderSpiSubsystemTables()}</section>
    <section class="block"><h3>Call Flow Examples</h3>${flows}</section>
    <section class="block"><h3>Implementation Rules</h3>
      <div class="empty-hint">All implementations follow these patterns (core "pure delegation" rule stated once, canonically, in ${sourceLink("platform-commons/CLAUDE.md")}'s "Hook and Port Implementation Rules" section — not restated here):</div>
      <div class="adr-item"><strong>Port Implementation (*PortImpl, Default*Port)</strong>
        <ul class="info-list">
          <li>Location: same module as the port interface</li>
          <li>Example: <code class="path">org.ost.audit.services.DefaultAuditPort</code> delegates all methods to <code class="path">AuditLogRepository</code> and <code class="path">AuditReadService</code></li>
        </ul>
      </div>
      <div class="adr-item"><strong>Hook Implementation (*HookImpl)</strong>
        <ul class="info-list">
          <li>Location: service module that implements the hook</li>
          <li>Example: <code class="path">org.ost.marketplace.spi.CurrentActorHookImpl</code> calls <code class="path">AuthContextService.getCurrentActorId()</code></li>
        </ul>
      </div>
    </section>`;
}

function buildSpiMapMermaid() {
  let out = "graph LR\n";
  const groups = {};
  MODEL.spiMap.nodes.forEach(n => {
    if (n.isGroup) return;
    (groups[n.parent] = groups[n.parent] || []).push(n);
  });
  Object.entries(groups).forEach(([g, ns]) => {
    out += `    subgraph ${g.replace(/-/g,"_")}["${g}"]\n`;
    ns.forEach(n => { out += `        ${n.id}["${n.label}"]\n`; });
    out += `    end\n`;
  });
  MODEL.spiMap.edges.forEach(e => { out += `    ${e.source} -->|implemented by| ${e.target}\n`; });
  return out;
}

function exportSpiMapMarkdown() {
  let md = `# SPI Map\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › SPI Map.\n\n`;
  md += `## Overview\n\nAll cross-module extension points (Ports and Hooks) live in \`platform-commons\` to decouple starters from marketplace-app. Suffixes encode call direction: \`*Port\` = marketplace -> starter; \`*Hook\` = starter -> marketplace. See platform-commons/CLAUDE.md's "SPI Interface Naming" table for the authoritative direction/role definition of each suffix.\n\n`;
  md += `## SPI Dependency Graph\n\n\`\`\`mermaid\n${buildSpiMapMermaid()}\`\`\`\n`;
  md += `\n## SPI Interface Details\n\n`;
  MODEL.spiMap.subsystemOrder.forEach(s => {
    const rows = MODEL.spiMap.details.filter(d => d.subsystem === s);
    if (!rows.length) return;
    md += `### ${MODEL.spiMap.subsystemLabels[s]} — \`${rows[0].package}\`\n\n`;
    md += `| Interface | Direction | Implementation(s) | Purpose |\n|---|---|---|---|\n`;
    rows.forEach(d => {
      const impls = d.implementations.length ? d.implementations.map(i => `${i.class} (${i.module})`).join("; ") : "no implementation found";
      md += `| ${d.interface} | ${d.kind} | ${impls} | ${d.purpose} |\n`;
    });
    md += "\n";
    if (MODEL.spiMap.subsystemNotes[s]) md += `${MODEL.spiMap.subsystemNotes[s]}\n\n`;
  });
  md += `## Call Flow Examples\n\n`;
  MODEL.spiCallFlowExamples.forEach(f => {
    md += `### ${f.title}\n\n${f.steps.map(s => `1. ${s}`).join("\n")}\n\n`;
  });
  md += `## Implementation Rules\n\nAll implementations follow these patterns (core "pure delegation" rule stated once, canonically, in platform-commons/CLAUDE.md's "Hook and Port Implementation Rules" section):\n\n`;
  md += `### Port Implementation (*PortImpl, Default*Port)\n- Location: same module as the port interface\n- Example: org.ost.audit.services.DefaultAuditPort delegates all methods to AuditLogRepository and AuditReadService\n\n`;
  md += `### Hook Implementation (*HookImpl)\n- Location: service module that implements the hook\n- Example: org.ost.marketplace.spi.CurrentActorHookImpl calls AuthContextService.getCurrentActorId()\n`;
  downloadMarkdown("spi-map.md", md);
}

// ── Database ERD: live from MODEL.dbErd (real Liquibase changelog data) -- same "live, not a
// separately-maintained .md" pattern as Module Dependencies/SPI Map. Mermaid's own erDiagram
// renderer is used (not Cytoscape -- parseMermaidGraph() only understands "graph TB/LR" syntax,
// not erDiagram's column-block/relationship notation), fed a mermaid source string built here
// instead of a hand-copied one, so the diagram picture and the table-schema detail below it are
// always the same data, never two documents that can drift apart.
function simplifyDbType(type) {
  if (!type) return "unknown";
  const t = type.toUpperCase();
  if (t.startsWith("BIGSERIAL") || t.startsWith("BIGINT")) return "bigint";
  if (t.startsWith("SERIAL") || t.startsWith("INT")) return "int";
  if (t.startsWith("VARCHAR") || t.startsWith("TEXT")) return t.includes("[]") ? "text_array" : "varchar";
  if (t.startsWith("TIMESTAMP")) return "timestamp";
  if (t.startsWith("JSONB") || t.startsWith("JSON")) return "jsonb";
  if (t.startsWith("BOOLEAN")) return "boolean";
  return t.toLowerCase().replace(/[^a-z0-9_]/g, "_") || "unknown";
}

function buildDbErdMermaidSource() {
  let out = "erDiagram\n";
  MODEL.dbErd.tables.forEach(t => {
    out += `    ${t.name.toUpperCase()} {\n`;
    t.columns.forEach(c => {
      const isCompositePk = t.compositePrimaryKey && t.compositePrimaryKey.includes(c.name);
      const keys = [];
      if (c.primaryKey || isCompositePk) keys.push("PK");
      if (c.foreignKeyTable) keys.push("FK");
      if (c.unique && !c.primaryKey) keys.push("UK");
      const keyStr = keys.join(",");
      const comment = c.remarks ? ` "${c.remarks.replace(/"/g, "'").slice(0, 60)}"` : "";
      out += `        ${simplifyDbType(c.type)} ${c.name}${keyStr ? " " + keyStr : ""}${comment}\n`;
    });
    out += `    }\n`;
  });
  // Real FKs first (solid line -- "identifying" relationship in Mermaid's erDiagram notation).
  MODEL.dbErd.tables.forEach(t => {
    t.foreignKeys.forEach(fk => {
      out += `    ${fk.refTable.toUpperCase()} ||--o{ ${t.name.toUpperCase()} : "${fk.column}"\n`;
    });
    t.columns.forEach(c => {
      if (c.foreignKeyTable) out += `    ${c.foreignKeyTable.toUpperCase()} ||--o{ ${t.name.toUpperCase()} : "${c.name}"\n`;
    });
  });
  // Hand-preserved conceptual relationships -- no real FK, dotted line ("non-identifying").
  MODEL.dbErd.conceptualRelationships.forEach(r => {
    out += `    ${r.from} ||..o{ ${r.to} : "${r.label.replace(/"/g, "'")}"\n`;
  });
  return out;
}

// Bounded Contexts' subgraph ids -> the real module each domain maps to. Mermaid's flowchart
// renderer gives each subgraph's SVG cluster group an id exactly equal to the subgraph id itself
// (e.g. "User", "Audit" -- no prefix/uuid, unlike erDiagram's entity ids), so no name-matching
// regex is needed here, just a direct lookup against MODEL.boundedContexts.domains -- the same
// live data the diagram itself is built from, not a second hand-typed copy of the mapping.
function wireBoundedContextsClicks() {
  document.querySelectorAll(".cluster").forEach(g => {
    const domain = MODEL.boundedContexts.domains.find(d => d.id === g.id);
    const mod = domain && domain.module;
    if (!mod || !byId[mod]) return;
    g.style.cursor = "pointer";
    g.addEventListener("click", () => navigate({ screen: "module", id: mod }));
  });
}

// Live from MODEL.boundedContexts (real @Table entities/*Service classes/tables/SPI ports per
// domain, real relationships detected from AuditActivityFieldsHook/AuditActivityEnrichHook
// entityType() declarations and other concrete signals -- see bounded_contexts_json() for exactly
// what backs each fact). Mirrors buildDbErdMermaidSource()'s shape: generate Mermaid source live,
// don't read a hand-typed file.
function buildBoundedContextsMermaidSource() {
  let out = "graph TB\n";
  MODEL.boundedContexts.domains.forEach(d => {
    out += `    subgraph ${d.id}["${esc(d.label)}<br/>(${esc(d.module)})"]\n`;
    let i = 0;
    [...d.entities, ...d.services, ...d.tables, ...d.ports].forEach(item => {
      out += `        ${d.id}_n${i++}["${esc(item.name).replace(/"/g, "'")}"]\n`;
    });
    out += `    end\n`;
  });
  MODEL.boundedContexts.relationships.forEach(r => {
    const arrow = r.dashed ? "-.->" : "-->";
    out += `    ${r.from} ${arrow}|${r.label.replace(/\|/g, "/")}| ${r.to}\n`;
  });
  return out;
}

// Readable name as the link text, real file underneath -- same "short label, real link" pattern
// spiFileLink() already uses for SPI Map. Items with no file (Shared's plain count summaries)
// render as plain text, not a broken link.
function bcItemLink(item) {
  return item.file ? `<a href="../../${esc(item.file)}" target="_blank">${esc(item.name)}</a>` : esc(item.name);
}

function renderBoundedContextsExtrasHtml() {
  const category = (label, items) => items.length
    ? `<div><span class="scope-label">${label}</span> ${items.map(bcItemLink).join(", ")}</div>` : "";
  const domainRows = MODEL.boundedContexts.domains.map(d => {
    const body = [
      category("Entities", d.entities), category("Services", d.services),
      category("Tables", d.tables), category("Ports", d.ports)
    ].filter(Boolean).join("");
    return `<div class="adr-item"><strong>${esc(d.label)}</strong> <span class="scope-label">${esc(d.module)}</span>
      ${body || `<div class="empty-hint">(no directly-owned entities/services/tables)</div>`}
    </div>`;
  }).join("");
  const relRows = MODEL.boundedContexts.relationships.map(r =>
    `<tr><td>${esc(r.from)} → ${esc(r.to)}</td><td>${esc(r.label)}</td><td>${esc(r.payload)}</td><td class="scope-label">${esc(r.confidence)}</td><td>${esc(r.evidence)}</td></tr>`
  ).join("");
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">Domain grouping (entities/services/tables/ports per box) is extracted live from real <code class="path">@Table</code> classes, <code class="path">*Service</code> classes, Liquibase tables, and SPI interface <code class="path">implements</code> relationships. Every relationship below is backed by a real, named code signal (mostly <code class="path">AuditActivityFieldsHook</code>/<code class="path">AuditActivityEnrichHook</code> <code class="path">entityType()</code> declarations) — see the Evidence column.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a domain box to open its real module page. Drag a node to reposition it, drag empty canvas space to pan.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">Box (subgraph)</td><td>A bounded context / domain — its real entities, services, tables, and SPI ports are listed inside</td></tr>
        <tr><td class="scope-label">──▶ (solid line)</td><td>A real business relationship backed by a concrete code signal (see the Relationships table below)</td></tr>
        <tr><td class="scope-label">┄┄▶ (dashed line)</td><td>"decouples" — a compile-time module dependency only (Shared → every domain), not a runtime call</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>Domain Contents (${MODEL.boundedContexts.domains.length})</h3>${domainRows}</section>
    <section class="block"><h3>Relationships (${MODEL.boundedContexts.relationships.length}) — all "extracted" (real code signal, not hand-typed)</h3>
      <table class="simple"><thead><tr><th>Relationship</th><th>Label</th><th>What crosses</th><th>Confidence</th><th>Evidence</th></tr></thead><tbody>${relRows}</tbody></table>
    </section>`;
}

function exportBoundedContextsMarkdown() {
  let md = `# Bounded Contexts — Context Map\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › Bounded Contexts.\n\n`;
  md += `## Context Map\n\n\`\`\`mermaid\n${buildBoundedContextsMermaidSource()}\`\`\`\n\n`;
  md += `## Domain Contents\n\n`;
  MODEL.boundedContexts.domains.forEach(d => {
    md += `### ${d.label} — \`${d.module}\`\n\n`;
    const cat = (label, items) => { if (items.length) md += `**${label}:** ${items.map(i => i.name).join(", ")}\n\n`; };
    cat("Entities", d.entities); cat("Services", d.services); cat("Tables", d.tables); cat("Ports", d.ports);
  });
  md += `## Relationships\n\n| Relationship | Label | What crosses | Confidence | Evidence |\n|---|---|---|---|---|\n`;
  MODEL.boundedContexts.relationships.forEach(r => {
    md += `| ${r.from} -> ${r.to} | ${r.label} | ${r.payload} | ${r.confidence} | ${r.evidence} |\n`;
  });
  downloadMarkdown("bounded-contexts.md", md);
}

// Mermaid's erDiagram renderer gives each entity's SVG group an id like
// "entity-USERINFORMATION-<uuid>" -- strip the prefix/uuid, match against the real table names
// (also uppercased/underscore-stripped) to find which table was clicked, then jump to its schema
// section below (id="db-table-<real_name>", set in renderDbErdTableSchemas()). Called after
// mermaid.run()'s promise resolves -- the SVG doesn't exist synchronously before that.
function wireDbErdEntityClicks() {
  document.querySelectorAll('[id^="entity-"]').forEach(g => {
    const m = /^entity-([A-Z0-9]+)-/.exec(g.id);
    if (!m) return;
    const table = MODEL.dbErd.tables.find(t => t.name.toUpperCase().replace(/_/g, "") === m[1]);
    if (!table) return;
    g.style.cursor = "pointer";
    g.addEventListener("click", () => {
      const el = document.getElementById("db-table-" + table.name);
      if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  });
}

function renderDbErdTableSchemas() {
  return MODEL.dbErd.tables.map(t => {
    const rows = t.columns.map(c => {
      const isCompositePk = t.compositePrimaryKey && t.compositePrimaryKey.includes(c.name);
      const constraints = [];
      if (c.primaryKey || isCompositePk) constraints.push("PK");
      if (c.foreignKeyTable) constraints.push(`FK → ${c.foreignKeyTable}(${c.foreignKeyColumn})`);
      if (c.unique) constraints.push("UNIQUE");
      if (!c.nullable) constraints.push("NOT NULL");
      return `<tr><td><code class="path">${esc(c.name)}</code></td><td>${esc(c.type)}</td><td class="scope-label">${esc(constraints.join(", "))}</td><td>${esc(c.remarks)}</td></tr>`;
    }).join("");
    const fkItems = t.foreignKeys.map(fk =>
      `<li><code class="path">${esc(fk.column)}</code> → <code class="path">${esc(fk.refTable)}(${esc(fk.refColumn)})</code> ON DELETE ${esc(fk.onDelete)}</li>`
    ).join("");
    const idxItems = t.indexes.map(i => {
      if (i.check) return `<li>CHECK <code class="path">${esc(i.name)}</code>: <code class="path">${esc(i.check)}</code></li>`;
      const cols = (i.columns || []).join(", ");
      return `<li><code class="path">${esc(i.name)}</code> (${esc(cols)})${i.unique ? " UNIQUE" : ""}${i.using ? ` USING ${esc(i.using)}` : ""}${i.where ? ` ${esc(i.where)}` : ""}</li>`;
    }).join("");
    return `
      <div class="domain-group" id="db-table-${esc(t.name)}">
        <h3>${esc(t.name)} <span class="scope-label">${esc(t.module)}</span></h3>
        <div class="empty-hint">${sourceLink(t.file)}${t.remarks ? " — " + mdInlineToHtml(t.remarks) : ""}</div>
        <table class="simple"><thead><tr><th>Column</th><th>Type</th><th>Constraints</th><th>Notes</th></tr></thead><tbody>${rows}</tbody></table>
        ${fkItems ? `<div class="adr-item"><strong>Foreign Keys</strong><ul class="info-list">${fkItems}</ul></div>` : ""}
        ${idxItems ? `<div class="adr-item"><strong>Indexes</strong><ul class="info-list">${idxItems}</ul></div>` : ""}
      </div>`;
  }).join("");
}

function renderDbErdExtrasHtml() {
  const relItems = MODEL.dbErd.conceptualRelationships.map(r =>
    `<li>${esc(r.from)} → ${esc(r.to)}: ${esc(r.label)}</li>`
  ).join("");
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">All tables are created via Liquibase migrations. Each starter owns its own changelog under its own <code class="path">db/*-changelog/</code> directory — no shared migrations between modules. Column/table descriptions live directly in each changelog's own <code class="path">remarks=</code> attribute (single source of truth, see root CLAUDE.md's "Database Changes" guideline) — not duplicated in a separate markdown file.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a table in the diagram above to jump to its schema below.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:110px">PK</td><td>Primary key</td></tr>
        <tr><td class="scope-label">FK</td><td>Foreign key — real, enforced constraint in the database</td></tr>
        <tr><td class="scope-label">UK</td><td>Unique constraint</td></tr>
        <tr><td class="scope-label">──── (solid line)</td><td>Real foreign key between the two tables</td></tr>
        <tr><td class="scope-label">┄┄┄┄ (dotted line)</td><td>Relationship this codebase deliberately leaves unconstrained at the SQL level — no real FK, enforced at the application layer instead</td></tr>
        <tr><td class="scope-label">‖ at the line's end</td><td>"Exactly one" — the table at that end has exactly one matching row (crow's-foot notation)</td></tr>
        <tr><td class="scope-label">○&lt; at the line's end</td><td>"Zero or many" — the table at that end has zero or more matching rows (crow's-foot notation)</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>Tables (${MODEL.dbErd.tables.length})</h3>${renderDbErdTableSchemas()}</section>
    <section class="block"><h3>Relationships not backed by a real foreign key</h3>
      <div class="empty-hint">This codebase deliberately leaves these columns unconstrained at the SQL level — purge-safety and referential integrity are enforced at the application layer instead (see each module's own CLAUDE.md "Key constraints"). Dotted lines in the diagram above.</div>
      <ul class="info-list">${relItems}</ul>
    </section>`;
}

function exportDbErdMarkdown() {
  let md = `# Database ERD\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › Database ERD.\n\n`;
  md += `## Overview\n\nAll tables are created via Liquibase migrations. Each starter owns its own changelog under its own \`db/*-changelog/\` directory -- no shared migrations between modules.\n\n`;
  md += `## Entity Relationship Diagram\n\n\`\`\`mermaid\n${buildDbErdMermaidSource()}\`\`\`\n\n`;
  md += `## Table Schemas\n\n`;
  MODEL.dbErd.tables.forEach(t => {
    md += `### ${t.name}\n\n**Module:** \`${t.module}\`  \n**Changelog:** \`${t.file}\`\n\n`;
    if (t.remarks) md += `${t.remarks}\n\n`;
    md += `| Column | Type | Constraints | Notes |\n|---|---|---|---|\n`;
    t.columns.forEach(c => {
      const isCompositePk = t.compositePrimaryKey && t.compositePrimaryKey.includes(c.name);
      const constraints = [];
      if (c.primaryKey || isCompositePk) constraints.push("PK");
      if (c.foreignKeyTable) constraints.push(`FK -> ${c.foreignKeyTable}(${c.foreignKeyColumn})`);
      if (c.unique) constraints.push("UNIQUE");
      if (!c.nullable) constraints.push("NOT NULL");
      md += `| ${c.name} | ${c.type} | ${constraints.join(", ")} | ${c.remarks} |\n`;
    });
    md += "\n";
    if (t.foreignKeys.length) {
      md += `**Foreign Keys:**\n\n`;
      t.foreignKeys.forEach(fk => { md += `- ${fk.column} -> ${fk.refTable}(${fk.refColumn}) ON DELETE ${fk.onDelete}\n`; });
      md += "\n";
    }
    if (t.indexes.length) {
      md += `**Indexes:**\n\n`;
      t.indexes.forEach(i => {
        if (i.check) { md += `- CHECK ${i.name}: ${i.check}\n`; return; }
        md += `- ${i.name} (${(i.columns||[]).join(", ")})${i.unique ? " UNIQUE" : ""}${i.using ? ` USING ${i.using}` : ""}${i.where ? ` ${i.where}` : ""}\n`;
      });
      md += "\n";
    }
  });
  md += `## Relationships not backed by a real foreign key\n\n`;
  MODEL.dbErd.conceptualRelationships.forEach(r => { md += `- ${r.from} -> ${r.to}: ${r.label}\n`; });
  downloadMarkdown("database-erd.md", md);
}

function zoomDiagram(delta) {
  if (diagramCy) {
    if (delta === 0) { diagramCy.fit(undefined, 20); return; }
    diagramCy.zoom({ level: diagramCy.zoom() + delta, renderedPosition: { x: diagramCy.width()/2, y: diagramCy.height()/2 } });
    return;
  }
  // Mermaid (ERD/sequence) fallback: CSS scale transform + drag-to-pan, no native zoom/drag API.
  zoomLevel = delta === 0 ? 1 : Math.min(3, Math.max(0.3, zoomLevel + delta));
  const box = document.getElementById("diagram-zoom-box");
  if (box) box.style.transform = `scale(${zoomLevel})`;
  const label = document.getElementById("zoom-label");
  if (label) label.textContent = Math.round(zoomLevel * 100) + "%";
}

// Click-and-drag panning for a scrollable container -- used only for the Mermaid (ERD/sequence)
// fallback path; Cytoscape-rendered diagrams already pan/drag natively.
function enableDragToPan(el) {
  if (!el) return;
  let dragging = false, startX = 0, startY = 0, startLeft = 0, startTop = 0;
  el.style.cursor = "grab";
  el.addEventListener("mousedown", e => {
    dragging = true; el.style.cursor = "grabbing";
    startX = e.clientX; startY = e.clientY; startLeft = el.scrollLeft; startTop = el.scrollTop;
    e.preventDefault();
  });
  window.addEventListener("mousemove", e => {
    if (!dragging) return;
    el.scrollLeft = startLeft - (e.clientX - startX);
    el.scrollTop = startTop - (e.clientY - startY);
  });
  window.addEventListener("mouseup", () => { dragging = false; el.style.cursor = "grab"; });
}

// ── Diagrams screen: all diagrams render live -- no markdown counterpart for any of them. ───────
function renderDiagrams() {
  if (!view.groupKey) {
    let html = backButtonHtml();
    html += `<h2 class="screen-title">Diagrams</h2>
      <div class="screen-desc">${totalDiagramCount} diagrams, all rendered live from pom.xml/real Java source/real Liquibase changelogs -- no separately-maintained markdown source for any of them.</div>`;
    MODEL.diagramGroups.forEach(g => {
      const graphType = GRAPH_TYPE_KEYS.includes(g.key);
      html += `<div class="domain-group"><h3>${esc(g.label)} <code class="path">(${esc(g.file)})</code> ${graphType ? '<span class="badge ACTIVE">draggable</span>' : ""}</h3><div class="card-grid">`;
      g.diagrams.forEach((d, i) => {
        html += `<div class="card" onclick="navigate({screen:'diagrams',groupKey:'${g.key}',diagramIndex:${i}})">
          <div class="card-title">${esc(d.title || g.label)}</div>
        </div>`;
      });
      html += `</div></div>`;
    });
    document.getElementById("content").innerHTML = html;
    return;
  }

  const g = MODEL.diagramGroups.find(x => x.key === view.groupKey);
  const d = g.diagrams[view.diagramIndex];
  zoomLevel = 1;
  diagramCy = null;
  // Page-level nav (back/export) — belongs to "which screen am I on," sits above the title (same
  // placement as the Module screen's own back/export row). Zoom controls belong to the diagram
  // itself, not the page, so they render separately, right above the diagram box (see
  // zoomControlsHtml below) instead of sharing this row.
  let html = `<div class="diagram-toolbar" style="justify-content:flex-start;gap:6px">
      <button onclick="navigateBack()">← back</button>
      ${g.key === "01-module-dependencies" ? '<button onclick="exportModuleDependenciesMarkdown()">⭳ Export as Markdown</button>' : ""}
      ${g.key === "02-spi-map" ? '<button onclick="exportSpiMapMarkdown()">⭳ Export as Markdown</button>' : ""}
      ${g.key === "04-database-erd" ? '<button onclick="exportDbErdMarkdown()">⭳ Export as Markdown</button>' : ""}
      ${g.key === "bounded-contexts" ? '<button onclick="exportBoundedContextsMarkdown()">⭳ Export as Markdown</button>' : ""}
    </div>
    <h2 class="screen-title">Diagrams</h2>`;
  const zoomControlsHtml = `<div style="display:flex;justify-content:flex-end;margin-bottom:6px">
      <span class="zoom-controls">
        <button onclick="zoomDiagram(-0.15)">−</button>
        <span id="zoom-label">100%</span>
        <button onclick="zoomDiagram(0.15)">+</button>
        <button onclick="zoomDiagram(0)">reset</button>
      </span>
    </div>`;

  if (g.key === "01-module-dependencies") {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered from the same module data the whole model is built from (domain-colored, click a node to open its module page) — drag a node to reposition it, drag empty canvas space to pan the view.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>
      ${renderModuleDependencyExtrasHtml()}`;
    document.getElementById("content").innerHTML = html;
    renderModuleDependencyGraph();
  } else if (g.key === "02-spi-map") {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered from real Java source (platform-commons/*/spi interfaces + their real \`implements\` across starters/marketplace-app), click a node to open its real .java file — drag a node to reposition it, drag empty canvas space to pan the view.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>
      ${renderSpiMapExtrasHtml()}`;
    document.getElementById("content").innerHTML = html;
    renderSpiMapGraph();
  } else if (g.key === "bounded-contexts") {
    // Rendered via Mermaid's own native engine (mermaid.run(), same mechanism as the Database ERD),
    // not the removed Cytoscape+dagre compound-node pipeline -- that
    // pipeline collapsed this graph onto one column (real cross-domain cycles via the UI/Audit hub,
    // a documented dagre compound-layout limitation, see DECISIONS.md ADR-016). Domain contents and
    // relationships are generated live from MODEL.boundedContexts (real code signals -- see the
    // Overview section below) -- no markdown source at all, same "live, not a second copy" pattern
    // as 01/02/04 (see DECISIONS.md ADR-019/ADR-020).
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered live from real code — entities/services/tables/SPI ports per domain, relationships from real Hook entityType() declarations and other concrete signals (see the Overview and Relationships sections below). Click a domain to open its real module page. Drag to pan, or use the zoom controls below.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-scroll"><div id="diagram-zoom-box"><pre class="mermaid">${esc(buildBoundedContextsMermaidSource())}</pre></div></div>
      ${renderBoundedContextsExtrasHtml()}`;
    document.getElementById("content").innerHTML = html;
    enableDragToPan(document.getElementById("diagram-scroll"));
    mermaid.run({ querySelector: ".mermaid" }).then(() => wireBoundedContextsClicks());
  } else if (g.key === "04-database-erd") {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered live from the real Liquibase changelogs (table/column/type/constraints/FKs/indexes/remarks) — an ERD needs a table/column renderer, not a draggable node graph. Solid lines are real foreign keys; dotted lines are relationships this codebase deliberately leaves unconstrained at the SQL level (enforced at the application layer instead — see the notes below). Drag to pan, or use the zoom controls below.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-scroll"><div id="diagram-zoom-box"><pre class="mermaid">${esc(buildDbErdMermaidSource())}</pre></div></div>
      ${renderDbErdExtrasHtml()}`;
    document.getElementById("content").innerHTML = html;
    enableDragToPan(document.getElementById("diagram-scroll"));
    mermaid.run({ querySelector: ".mermaid" }).then(() => wireDbErdEntityClicks());
  } else {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}, rendered live via Mermaid.js. This diagram type (sequence/temporal) isn't rendered as a draggable node graph — a sequence diagram shows message order along lifelines, which isn't "drag this box anywhere" the way a dependency graph is. Drag to pan, or use the zoom controls below.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-scroll"><div id="diagram-zoom-box"><pre class="mermaid">${esc(d.source)}</pre></div></div>`;
    document.getElementById("content").innerHTML = html;
    mermaid.run({ querySelector: ".mermaid" });
    enableDragToPan(document.getElementById("diagram-scroll"));
  }
}

function render() {
  renderBreadcrumb();
  if (view.screen === "module") renderModule();
  else if (view.screen === "pipelines") renderPipelines();
  else if (view.screen === "backlog") renderBacklog();
  else if (view.screen === "diagrams") renderDiagrams();
  else if (view.screen === "docker") renderDocker();
  else if (view.screen === "adrs") renderAdrs();
  else if (view.screen === "codequality") renderCodeQuality();
  else renderSystem();
}

// ── ADRs screen: flat, deduplicated list of every ADR across every module's DECISIONS.md, read
// live from docs/ai/adr-index.md (MODEL.allAdrs) -- no separate hand-maintained summary.
let adrStatusFilter = "All";
function setAdrStatusFilter(f) { adrStatusFilter = f; renderAdrs(); }

function renderAdrs() {
  const adrs = MODEL.allAdrs || [];
  // Status text in this repo is often a long free-form annotation, not a bare word (e.g.
  // "Accepted (done 2026-06-26)") -- bucket by just the first word so the filter cards stay a
  // handful, not dozens of near-duplicate ones; the full original text is still shown per-row in
  // the table below.
  const bucketOf = a => (a.status.match(/^[A-Za-z]+/) || [a.status])[0];
  const counts = {};
  adrs.forEach(a => { const b = bucketOf(a); counts[b] = (counts[b] || 0) + 1; });
  const filtered = adrStatusFilter === "All" ? adrs : adrs.filter(a => bucketOf(a) === adrStatusFilter);

  let html = backButtonHtml();
  html += `<h2 class="screen-title">ADRs</h2>
    <div class="screen-desc">${adrs.length} architectural decisions across every module's own DECISIONS.md — see ${sourceLink("docs/ai/adr-index.md")} for the generated index this screen reads, and the Overview section at the bottom of this screen for what an ADR is and how it's used.</div>`;
  html += `<div class="card-grid">
    <div class="card ${adrStatusFilter === "All" ? "card-active" : ""}" onclick="setAdrStatusFilter('All')"><div class="card-title">${adrs.length}</div><div class="card-desc">All</div></div>`;
  html += Object.entries(counts).map(([status, count]) =>
    `<div class="card ${adrStatusFilter === status ? "card-active" : ""}" onclick="setAdrStatusFilter('${esc(status)}')"><div class="card-title">${count}</div><div class="card-desc">${esc(status)}</div></div>`
  ).join("");
  html += `</div>`;

  // Grouped by module (first-appearance order, same order MODEL.allAdrs already carries) instead
  // of one flat table -- the module is now the group heading (with its own DECISIONS.md link
  // right there), so a per-row "Module" column would just repeat what the heading already says.
  const groups = [];
  const groupIndex = {};
  filtered.forEach(a => {
    if (!(a.module in groupIndex)) { groupIndex[a.module] = groups.length; groups.push({ module: a.module, items: [] }); }
    groups[groupIndex[a.module]].items.push(a);
  });
  groups.forEach(grp => {
    // Only a real MODULE node has a Module-detail page to link to -- SCRIPT_GROUP entries
    // (scripts/architecture, scripts/ci, etc.) stay plain text, no dead/wrong-feeling navigation.
    const grpNode = byId[grp.module];
    const grpHeading = grpNode && grpNode.type === "MODULE"
      ? `<a class="module-link" onclick="navigate({screen:'module', id:'${esc(grp.module)}'})">${esc(grp.module)}</a>`
      : esc(grp.module);
    html += `<section class="block"><h3>${grpHeading} (${grp.items.length}) ${sourceLink(grp.module + "/DECISIONS.md")}</h3>
      <table class="simple"><thead><tr><th>ADR</th><th>Status</th><th>Title</th></tr></thead><tbody>`;
    grp.items.forEach(a => {
      html += `<tr><td><a onclick="openAdrPopupForAdr('${esc(a.id)}', '${esc(a.module)}')">${esc(a.id)}</a></td><td>${esc(a.status)}</td><td>${esc(a.title)}</td></tr>`;
    });
    html += `</tbody></table></section>`;
  });

  html += `<section class="block"><h3>Overview</h3>` + GLOSSARY.map(g => `<div class="glossary-item"><strong>${esc(g.term)}</strong>${g.body}</div>`).join("") + `</section>`;

  document.getElementById("content").innerHTML = html;
}

// Same popup as openAdrPopupForIntent() (Module screen's Architectural decisions list) -- this
// one takes the ADR id + home module directly instead of a node's intent[] index, since the ADRs
// screen's list is flat across every module, not scoped to one node.
function openAdrPopupForAdr(id, module) {
  const bareId = id.split(" (")[0];
  const homeNode = byId[module];
  const found = homeNode && homeNode.decisions && homeNode.decisions.adrs.find(x => x.id === bareId);
  if (!found) { window.open(`../../${module}/DECISIONS.md`, "_blank"); return; }
  document.getElementById("adr-popup-title").textContent = `${found.id} — ${found.title}`;
  document.getElementById("adr-popup-status").textContent = found.status;
  document.getElementById("adr-popup-body").innerHTML = mdBlockToHtml(found.body);
  document.getElementById("adr-popup").showModal();
}

// ── System screen's "Overview" block: a small glossary of general terms/concepts used across
// this tool and this repo's own conventions -- hand-maintained prose, same "genuinely
// non-mechanical content" exception this tool already uses elsewhere (SPI Map's Call Flow
// Examples, Bounded Contexts' narrative sections) -- a term's meaning isn't something to extract
// from a file, it's explained once, here, at the bottom of the System screen (not a separate
// "Notes" card/screen -- dropped that shape after landing it, kept the content).
const GLOSSARY = [
  { term: "ADR (Architectural Decision Record)", body: `
    <p><strong>What it is:</strong> a single, dated, numbered record of one architectural or
    technical decision — what was decided, the context that led to it, and why. Each module owns
    its own <code class="path">DECISIONS.md</code> file holding its own sequential
    <code>ADR-NNN</code>s.</p>
    <p><strong>How it's used here:</strong> filed via <code>/decision &lt;module&gt; —
    &lt;title&gt;</code>. Once written, an ADR is never edited to remove content or deleted — if a
    later decision reverses or replaces it, the old ADR gets <code>Status: Superseded</code> (or an
    amendment note) pointing at the new one, and a new ADR is added instead.
    ${sourceLink("docs/ai/adr-index.md")} is a generated, searchable flat index of every ADR
    (id/module/status/title) — check it before filing a new one, to avoid re-deciding something
    already settled.</p>
    <p><strong>Boundaries — what an ADR is not:</strong> not a running changelog of "what
    changed" (that's git history); not where code comments explain WHY (those stay a single
    inline line, in the code itself); not a substitute for a backlog issue (which tracks work not
    yet done — an ADR records a decision already made); not renumbered or reorganized once
    written — its number is permanent and load-bearing (other code/docs reference it by number)
    even if its owning file later moves or is renamed.</p>
  ` }
];

// ── Docker screen: real files + mechanically-extracted facts (build stages / compose service
// names) only -- the actual deployment workflow explanation stays in scripts/CLAUDE.md's
// "Deployment" section, linked to below, never restated here.
function renderDocker() {
  let html = backButtonHtml();
  html += `<h2 class="screen-title">Docker</h2>
    <div class="screen-desc">What builds/runs in this repo, straight from the real files — see ${sourceLink("scripts/CLAUDE.md")} for the actual deploy workflow (deploy.sh/deploy-dev.sh flags, when to use which).</div>`;

  const dockerfiles = MODEL.dockerFiles.filter(f => f.kind === "dockerfile");
  const composeFiles = MODEL.dockerFiles.filter(f => f.kind === "compose");

  html += `<section class="block"><h3>Dockerfiles (${dockerfiles.length})</h3><table class="simple"><thead><tr><th>File</th><th>Purpose</th><th>Build stages (FROM)</th></tr></thead><tbody>`;
  dockerfiles.forEach(f => {
    html += `<tr><td>${sourceLink(f.file)}</td><td>${esc(f.label)}</td><td>${f.items.map(s => `<code class="path">${esc(s)}</code>`).join("<br>")}</td></tr>`;
  });
  html += `</tbody></table></section>`;

  html += `<section class="block"><h3>docker-compose stacks (${composeFiles.length})</h3><table class="simple"><thead><tr><th>File</th><th>Purpose</th><th>Services</th></tr></thead><tbody>`;
  composeFiles.forEach(f => {
    html += `<tr><td>${sourceLink(f.file)}</td><td>${esc(f.label)}</td><td>${f.items.map(s => `<code class="path">${esc(s)}</code>`).join(" ")}</td></tr>`;
  });
  html += `</tbody></table></section>`;

  document.getElementById("content").innerHTML = html;
}

render();
</script>
</body>
</html>
HTML_TAIL

echo "Wrote $HTML_OUTPUT"
exit 0
