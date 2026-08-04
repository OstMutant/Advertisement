#!/usr/bin/env bash
# Generates architecture-model.json (Track A of the architecture control plane) from
# already-structured, non-code sources only -- no ArchUnit, no bytecode analysis. Node types:
# MODULE (from pom.xml reactor + per-module pom.xml dependencies), COMMAND/SKILL (from
# .claude/commands, .claude/skills, cross-checked against docs/ai/flows.md), and one BACKLOG
# summary node. Per-ADR/per-issue graph nodes are deliberately not built -- the issue/ADR count
# would blow past a "tens of nodes, not thousands" budget -- ADRs are folded into each module's
# own `intent[]` list instead, reusing adr-index.md rather than reparsing every DECISIONS.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="$REPO_ROOT/architecture-model.json"
HTML_OUTPUT="$REPO_ROOT/architecture-map.html"
ADR_INDEX="$REPO_ROOT/docs/ai/adr-index.md"
FLOWS="$REPO_ROOT/docs/ai/flows.md"
BOUNDED_CONTEXTS="$REPO_ROOT/docs/architecture/03-bounded-contexts.md"
ROOT_CLAUDE_MD="$REPO_ROOT/CLAUDE.md"

json_escape() {
  # Escapes backslash and double-quote, strips newlines/carriage-returns and other C0 control
  # bytes -- sufficient for the plain-text fields this script emits (file paths, short prose
  # lines); not a general-purpose JSON encoder. LC_ALL=C keeps tr from mangling multi-byte UTF-8
  # (em-dashes etc. in command descriptions) while still stripping 0x00-0x1F control bytes.
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | LC_ALL=C tr -d '\000-\037'
}

# Extracts every ```mermaid ... ``` fenced block in a markdown file, paired with its nearest
# preceding heading, as a comma-joined list of {"title":..,"source":..} JSON objects (no enclosing
# brackets -- the caller wraps them). Escaping done in-awk so multi-line diagram source never has
# to round-trip through a bash variable (fragile for content this size/shape).
extract_all_mermaids_json() {
  local file="$1"
  [ -f "$file" ] || return 0
  awk '
    BEGIN { first = 1 }
    function jesc(s) {
      gsub(/\\/, "\\\\", s); gsub(/"/, "\\\"", s); gsub(/\r/, "", s); gsub(/\n/, "\\n", s)
      return s
    }
    /^#+ / && !inblock { heading = $0; sub(/^#+ */, "", heading) }
    /^```mermaid/ { inblock = 1; buf = ""; next }
    /^```/ {
      if (inblock) {
        inblock = 0
        if (!first) printf(",\n")
        printf("    {\"title\": \"%s\", \"source\": \"%s\"}", jesc(heading), jesc(buf))
        first = 0
      }
      next
    }
    inblock { buf = buf $0 "\n" }
  ' "$file"
}

# ── Module list, in pom.xml reactor order ───────────────────────────────────────────────────
mapfile -t MODULES < <(sed -n '/<modules>/,/<\/modules>/p' "$REPO_ROOT/pom.xml" \
  | grep -o '<module>[^<]*</module>' | sed 's/<module>\(.*\)<\/module>/\1/')

# ── Domain grouping + Key Services/Contract bullet lists, seeded from 03-bounded-contexts.md ────
# (confidence: manual, per plan §11 A1). Reads the same already-written prose the docs already
# carry -- not a new extraction risk, just surfacing existing content in the human layer instead
# of leaving the Module screen with only structural facts (user-requested enrichment, 2026-08-04).
declare -A MODULE_DOMAIN MODULE_KEYSERVICES MODULE_CONTRACT MODULE_ENTITY
if [ -f "$BOUNDED_CONTEXTS" ]; then
  domain=""; current_module=""; field=""
  while IFS= read -r line; do
    line="${line%$'\r'}"  # this repo's working tree uses CRLF line endings (core.autocrlf)
    if [[ "$line" =~ ^###\ (.+)$ ]]; then
      domain="${BASH_REMATCH[1]}"; current_module=""; field=""
    elif [[ "$line" =~ ^\*\*(Ownership|Location):\*\*\ \`[^\`]*\`\ \(([a-z0-9-]+)\)$ ]]; then
      MODULE_DOMAIN["${BASH_REMATCH[2]}"]="$domain"; current_module="${BASH_REMATCH[2]}"; field=""
    elif [[ "$line" =~ ^\*\*Location:\*\*\ \`([a-z0-9-]+)\`$ ]]; then
      MODULE_DOMAIN["${BASH_REMATCH[1]}"]="$domain"; current_module="${BASH_REMATCH[1]}"; field=""
    elif [[ "$line" =~ ^\*\*Entit(y|ies):\*\*$ ]]; then
      field="entity"
    elif [[ "$line" =~ ^\*\*Key\ Services:\*\*$ ]]; then
      field="keyservices"
    elif [[ "$line" =~ ^\*\*Contract:\*\*$ ]]; then
      field="contract"
    elif [[ "$line" =~ ^\*\*.+:\*\*.*$ || "$line" == "---" ]]; then
      field=""
    elif [ -n "$current_module" ] && [ -n "$field" ] && [[ "$line" =~ ^-\ (.+)$ ]]; then
      case "$field" in
        keyservices) MODULE_KEYSERVICES["$current_module"]="${MODULE_KEYSERVICES[$current_module]:-}${BASH_REMATCH[1]}"$'\n' ;;
        contract)    MODULE_CONTRACT["$current_module"]="${MODULE_CONTRACT[$current_module]:-}${BASH_REMATCH[1]}"$'\n' ;;
        entity)      MODULE_ENTITY["$current_module"]="${MODULE_ENTITY[$current_module]:-}${BASH_REMATCH[1]}"$'\n' ;;
      esac
    elif [ -z "$line" ]; then
      field=""
    fi
  done < "$BOUNDED_CONTEXTS"
fi
# Structural modules 03-bounded-contexts.md doesn't describe as a "Domain" -- fallback labels,
# confidence: manual either way (both paths are heuristic, not extracted).
MODULE_DOMAIN["marketplace-app"]="${MODULE_DOMAIN[marketplace-app]:-UI/Application Layer}"
MODULE_DOMAIN["query-lib"]="${MODULE_DOMAIN[query-lib]:-Shared Kernel}"
MODULE_DOMAIN["integration-tests"]="${MODULE_DOMAIN[integration-tests]:-Testing}"

# ── Table ownership per module, from 04-database-erd.md's "### table" / "**Module:**" pairs ────
declare -A MODULE_TABLES
if [ -f "$REPO_ROOT/docs/architecture/04-database-erd.md" ]; then
  pending_table=""
  while IFS= read -r line; do
    line="${line%$'\r'}"
    if [[ "$line" =~ ^###\ ([a-z_]+)$ ]]; then
      pending_table="${BASH_REMATCH[1]}"
    elif [ -n "$pending_table" ] && [[ "$line" =~ ^\*\*Module:\*\*\ \`([a-z0-9-]+)\` ]]; then
      MODULE_TABLES["${BASH_REMATCH[1]}"]="${MODULE_TABLES[${BASH_REMATCH[1]}]:-}$pending_table"$'\n'
      pending_table=""
    elif [ -n "$line" ] && [[ ! "$line" =~ ^\*\* ]]; then
      pending_table=""
    fi
  done < "$REPO_ROOT/docs/architecture/04-database-erd.md"
fi

# ── One-line module descriptions, reused from root CLAUDE.md's "Module Layout" ASCII tree ──────
# (already a clean, one-line-per-module, consistently-formatted description -- no need to write a
# second copy or parse prose out of 03-bounded-contexts.md's paragraphs).
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
#    per §14) -- emitted as {"id":..,"title":..} objects so the human layer can show real titles. ──
adr_intent_for_module() {
  local module="$1"
  if [ -f "$ADR_INDEX" ]; then
    awk -F' \\| ' -v m="$module" '
      /^\| ADR-/ && $2 == m { sub(/^\| /, "", $1); sub(/ *\|$/, "", $4); print $1 "\x1f" $4 }
    ' "$ADR_INDEX"
  fi
}

# Builds a JSON array of {"id":..,"title":..} from "id\x1ftitle" lines; empty input -> "[]".
json_adr_array() {
  local items="$1" out="" first=true
  while IFS=$'\x1f' read -r id title; do
    [ -z "$id" ] && continue
    $first || out="$out, "
    first=false
    out="$out{\"id\": \"$(json_escape "$id")\", \"title\": \"$(json_escape "$title")\"}"
  done <<< "$items"
  echo "[$out]"
}

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

# Every documented diagram, from every docs/architecture/*.md file that has one -- not just the
# two originally picked (user-flagged, 2026-08-04: "not all docs were loaded"). Each source file
# becomes one group, keyed by its own filename stem so the human layer can label groups
# meaningfully ("01 · Module Dependencies" etc.) without hand-maintaining a title map.
declare -A DIAGRAM_FILE_LABEL=(
  [01-module-dependencies]="Module Dependencies"
  [02-spi-map]="SPI Map"
  [03-bounded-contexts]="Bounded Contexts"
  [04-database-erd]="Database ERD"
  [05-sequence-diagrams]="Sequence Diagrams"
)
diagram_groups_json=""
first_group=true
for stem in 01-module-dependencies 02-spi-map 03-bounded-contexts 04-database-erd 05-sequence-diagrams; do
  f="$REPO_ROOT/docs/architecture/$stem.md"
  [ -f "$f" ] || continue
  diagrams_json="$(extract_all_mermaids_json "$f")"
  [ -z "$diagrams_json" ] && continue
  $first_group || diagram_groups_json="$diagram_groups_json,"$'\n'
  first_group=false
  diagram_groups_json="$diagram_groups_json  {\"key\": \"$stem\", \"label\": \"$(json_escape "${DIAGRAM_FILE_LABEL[$stem]}")\", \"file\": \"docs/architecture/$stem.md\", \"diagrams\": ["$'\n'"$diagrams_json"$'\n'"  ]}"
done

{
  echo "{"
  echo "  \"generated_by\": \"scripts/ai/generate-architecture-model.sh\","
  echo "  \"generated_note\": \"Track A only -- modules+deps from pom.xml, domain grouping/entities/services/contracts from docs/architecture/03-bounded-contexts.md (manual confidence), tables from 04-database-erd.md, every Mermaid diagram in docs/architecture/01-05 reused verbatim, lifecycle from DECISIONS.md/backlog, pipeline nodes from docs/ai/flows.md + .claude/commands + .claude/skills. No ArchUnit/bytecode data yet (planned for a future track).\","
  echo "  \"diagramGroups\": ["
  echo "$diagram_groups_json"
  echo "  ],"
  echo "  \"nodes\": ["

  first=true

  # MODULE nodes
  for m in "${MODULES[@]}"; do
    $first || echo "    ,"
    first=false
    domain="${MODULE_DOMAIN[$m]:-UNKNOWN}"
    domain_confidence="extracted"
    [ "$domain" = "UNKNOWN" ] && domain_confidence="inferred"
    [[ "$m" == "marketplace-app" || "$m" == "query-lib" || "$m" == "integration-tests" || -n "${MODULE_DOMAIN[$m]:-}" ]] && domain_confidence="manual"

    intent_json="$(json_adr_array "$(adr_intent_for_module "$m")")"
    description="${MODULE_DESCRIPTION[$m]:-}"

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

  # BACKLOG summary node
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
  .card .card-title { font-weight: 600; font-size: 14px; margin-bottom: 4px; }
  .card .card-desc { font-size: 12px; color: var(--muted); line-height: 1.4; }
  .badge { display: inline-block; font-size: 10px; padding: 2px 8px; border-radius: 10px; font-weight: 600; margin-top: 8px; }
  .badge.ACTIVE { color: var(--active); background: var(--active-bg); }
  .badge.TRANSITIONAL { color: var(--transitional); background: var(--transitional-bg); }
  .badge.DEPRECATED { color: var(--deprecated); background: var(--deprecated-bg); }
  .special-card { background: linear-gradient(135deg,#2b6cb0,#2c5282); color: #fff; }
  .special-card .card-desc { color: #cbd5e0; }
  #map { height: 360px; background: var(--card); border: 1px solid var(--line); border-radius: 8px; margin-bottom: 28px; }
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
  table.simple { width: 100%; border-collapse: collapse; font-size: 13px; }
  table.simple th { text-align: left; font-size: 11px; text-transform: uppercase; color: var(--muted); padding: 6px 8px; border-bottom: 2px solid var(--line); }
  table.simple td { padding: 8px; border-bottom: 1px solid #f0f2f4; vertical-align: top; }
  table.simple tr:last-child td { border-bottom: none; }
  code.path { font-size: 11px; color: var(--muted); }
  .info-list { list-style: none; margin: 0; padding: 0; }
  .info-list li { padding: 6px 0; border-bottom: 1px solid #f0f2f4; font-size: 13px; line-height: 1.5; }
  .info-list li:last-child { border-bottom: none; }
  .info-list code { background: #f1f3f5; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
  .table-chip { display: inline-block; background: #f1f3f5; color: var(--ink); font-size: 11px; padding: 3px 9px; border-radius: 6px; margin: 2px 4px 2px 0; font-family: monospace; }
  .diagram-wrap { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 20px; overflow: auto; max-height: 75vh; }
  #diagram-zoom-box { transform-origin: top left; width: fit-content; transition: transform .1s ease-out; }
  .diagram-note { font-size: 12px; color: var(--muted); margin-bottom: 12px; }
  .diagram-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
  .diagram-toolbar button { background: var(--card); border: 1px solid var(--line); border-radius: 6px; padding: 6px 12px; cursor: pointer; font-size: 13px; }
  .diagram-toolbar button:hover { background: var(--bg); }
  .zoom-controls { display: flex; align-items: center; gap: 6px; }
  .zoom-controls span#zoom-label { font-size: 12px; color: var(--muted); width: 42px; text-align: center; display: inline-block; }
</style>
</head>
<body>
<header>
  <h1>Architecture Control Plane</h1>
  <div class="subtitle">Generated from pom.xml, DECISIONS.md, backlog/, docs/ai/flows.md, .claude/commands, .claude/skills, root CLAUDE.md — regenerate via <code>bash scripts/ai/generate-architecture-model.sh</code>. Track A only: module-level granularity; Contract/Implementation/Method levels are placeholders until Track B's ArchUnit exporter lands.</div>
  <nav id="breadcrumb"></nav>
</header>
<main id="content"></main>
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

// ── Router: System | Module(id) | Pipelines | Backlog ──────────────────────────────────────────
let view = { screen: "system" };

function navigate(next) { view = next; render(); window.scrollTo(0,0); }

function renderBreadcrumb() {
  const bc = document.getElementById("breadcrumb");
  let html = '<a onclick="navigate({screen:\'system\'})">System</a>';
  if (view.screen === "module") {
    html += `<span class="sep">›</span><span class="current">${esc(displayName(view.id))}</span>`;
  } else if (view.screen === "pipelines") {
    html += `<span class="sep">›</span><span class="current">Tooling &amp; Pipelines</span>`;
  } else if (view.screen === "backlog") {
    html += `<span class="sep">›</span><span class="current">Backlog</span>`;
  } else if (view.screen === "diagrams") {
    html += `<span class="sep">›</span><a onclick="navigate({screen:'diagrams'})">Diagrams</a>`;
    if (view.groupKey && view.diagramIndex !== undefined) {
      const g = MODEL.diagramGroups.find(x => x.key === view.groupKey);
      html += `<span class="sep">›</span><span class="current">${esc(g.label)} — ${esc(g.diagrams[view.diagramIndex].title)}</span>`;
    }
  }
  bc.innerHTML = html;
}

// ── System screen: domain-grouped module cards + a compact dependency map + Pipelines/Backlog ──
function renderSystem() {
  let html = `<h2 class="screen-title">System</h2>
    <div class="screen-desc">${moduleNodes.length} modules across ${domainOrder.length} domains. Click a module to drill in, or use the map below to see dependencies at a glance (scroll/drag also works — the buttons are just a visible affordance for the same thing).</div>
    <div class="diagram-toolbar">
      <span></span>
      <span class="zoom-controls">
        <button onclick="zoomMap(-0.2)">−</button>
        <span id="map-zoom-label">100%</span>
        <button onclick="zoomMap(0.2)">+</button>
        <button onclick="zoomMap(0)">reset</button>
      </span>
    </div>
    <div id="map"></div>`;

  html += `<div class="card-grid" style="margin-bottom:28px">
    <div class="card special-card" onclick="navigate({screen:'pipelines'})">
      <div class="card-title">🛠 Tooling &amp; Pipelines</div>
      <div class="card-desc">${commandNodes.length} commands, ${skillNodes.length} skills</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'backlog'})">
      <div class="card-title">📋 Backlog</div>
      <div class="card-desc">${backlogNode ? backlogNode.open_issues : "?"} open, ${backlogNode ? backlogNode.completed_issues : "?"} completed</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'diagrams'})">
      <div class="card-title">📐 Diagrams</div>
      <div class="card-desc">${totalDiagramCount} diagrams across ${MODEL.diagramGroups.length} docs — dependency graph, SPI map, context map, ERD, sequence flows</div>
    </div>
  </div>`;

  domainOrder.forEach(domain => {
    const mods = moduleNodes.filter(n => n.domain === domain);
    html += `<div class="domain-group"><h3>${esc(domain)}</h3><div class="card-grid">`;
    mods.forEach(n => {
      html += `<div class="card" style="border-top:3px solid ${domainColor(domain)}" onclick="navigate({screen:'module',id:'${n.id}'})">
        <div class="card-title">${esc(n.id)}</div>
        <div class="card-desc">${esc(n.description || "")}</div>
        <span class="badge ${n.lifecycle}">${n.lifecycle}</span>
      </div>`;
    });
    html += `</div></div>`;
  });

  document.getElementById("content").innerHTML = html;
  renderMap();
}

let systemCy = null;

function renderMap() {
  const els = moduleNodes.map(n => ({
    data: { id: n.id, label: n.id.replace(/-spring-boot-starter$/, ""), domain: n.domain },
    style: { "background-color": domainColor(n.domain) }
  }));
  moduleNodes.forEach(n => {
    // Layout edges point dependency -> dependent (reverse of the real semantic direction) so
    // dagre's left-to-right ranking puts foundational/most-depended-on modules on the left and
    // consumers on the right, matching 01-module-dependencies.md's own `graph LR` convention --
    // dagre ranks by source-before-target, and "X depends on Y" should read Y (left) <- X (right).
    // The arrowhead is drawn on the *visual* source/target below, independent of layout ranking.
    ["DEPENDS_ON_COMPILE","DEPENDS_ON_RUNTIME"].forEach(et => {
      (n.edges[et] || []).forEach(target => {
        els.push({ data: { id: n.id+"->"+target+et, source: target, target: n.id, dashed: et==="DEPENDS_ON_RUNTIME" } });
      });
    });
  });
  const hasDagre = typeof cytoscapeDagre !== "undefined";
  systemCy = cytoscape({
    container: document.getElementById("map"),
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
  systemCy.on("tap", "node", e => navigate({ screen: "module", id: e.target.id() }));
  systemCy.on("zoom", () => {
    const label = document.getElementById("map-zoom-label");
    if (label) label.textContent = Math.round(systemCy.zoom() * 100) + "%";
  });
}

function zoomMap(delta) {
  if (!systemCy) return;
  if (delta === 0) { systemCy.fit(undefined, 20); return; }
  systemCy.zoom({ level: systemCy.zoom() + delta, renderedPosition: { x: systemCy.width()/2, y: systemCy.height()/2 } });
}

// ── Module screen: full readable detail, drill-down deps, ADRs, Track B placeholder slots ──────
function renderDepList(ids, label, emptyText) {
  if (!ids || ids.length === 0) return `<div class="empty-hint">${emptyText}</div>`;
  return `<ul class="dep-list">` + ids.map(id => {
    const n = byId[id];
    return `<li><a onclick="navigate({screen:'module',id:'${id}'})">${esc(id)}</a>${n ? `<span class="dep-tag">${esc(n.domain)}</span>` : ""}</li>`;
  }).join("") + `</ul>`;
}

function renderModule() {
  const n = byId[view.id];
  if (!n) { navigate({ screen: "system" }); return; }
  let html = `<div class="module-header">
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

  html += `<section class="block"><h3>Architectural decisions (${(n.intent||[]).length})</h3>`;
  if (!n.intent || n.intent.length === 0) {
    html += `<div class="empty-hint">No ADRs recorded for this module (it may record decisions in another module's DECISIONS.md — see root CLAUDE.md).</div>`;
  } else {
    html += n.intent.map(a => `<div class="adr-item"><span class="adr-id">${esc(a.id)}</span> — ${esc(a.title)}</div>`).join("");
  }
  html += `</section>`;

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
function renderPipelines() {
  let html = `<h2 class="screen-title">Tooling &amp; Pipelines</h2>
    <div class="screen-desc">Slash commands and skills available in this repo — sourced from .claude/commands and .claude/skills, cross-checked against docs/ai/flows.md.</div>`;

  html += `<section class="block"><h3>Commands (${commandNodes.length})</h3><table class="simple"><thead><tr><th>Command</th><th>Description</th><th>Source</th></tr></thead><tbody>`;
  commandNodes.forEach(n => {
    html += `<tr><td>/${esc(displayName(n.id))}</td><td>${esc(n.description)}</td><td><code class="path">${esc(n.evidence[0].file)}</code></td></tr>`;
  });
  html += `</tbody></table></section>`;

  html += `<section class="block"><h3>Skills (${skillNodes.length})</h3><table class="simple"><thead><tr><th>Skill</th><th>Description</th><th>Source</th></tr></thead><tbody>`;
  skillNodes.forEach(n => {
    html += `<tr><td>${esc(displayName(n.id))}</td><td>${esc(n.description)}</td><td><code class="path">${esc(n.evidence[0].file)}</code></td></tr>`;
  });
  html += `</tbody></table></section>`;

  document.getElementById("content").innerHTML = html;
}

// ── Backlog screen ───────────────────────────────────────────────────────────────────────────
function renderBacklog() {
  const html = `<h2 class="screen-title">Backlog</h2>
    <div class="screen-desc">Aggregate counts only in Track A — see backlog/BACKLOG.md for the ranked, per-issue view.</div>
    <div class="card-grid">
      <div class="card"><div class="card-title">${backlogNode.open_issues}</div><div class="card-desc">open issues (backlog/issues/)</div></div>
      <div class="card"><div class="card-title">${backlogNode.completed_issues}</div><div class="card-desc">completed issues (backlog/completed/issues/)</div></div>
    </div>`;
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
const GRAPH_TYPE_KEYS = ["01-module-dependencies", "02-spi-map", "03-bounded-contexts"];

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

function renderCytoscapeDiagram(source) {
  const parsed = parseMermaidGraph(source);
  const els = [
    ...parsed.nodes.map(n => ({
      data: { id: n.id, label: n.label, parent: n.parent || undefined },
      classes: n.isGroup ? "group-node" : ""
    })),
    ...parsed.edges.map((e, i) => ({
      data: { id: "e" + i, source: e.source, target: e.target, label: e.label, dashed: e.dashed }
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
      ? { name: "dagre", rankDir: "TB", nodeSep: 24, rankSep: 60, padding: 20 }
      : { name: "breadthfirst", directed: true, padding: 20 }
  });
  diagramCy.on("zoom", () => {
    const label = document.getElementById("zoom-label");
    if (label) label.textContent = Math.round(diagramCy.zoom() * 100) + "%";
  });
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

// ── Diagrams screen: every diagram from docs/architecture/01-05, grouped by source file. ───────
function renderDiagrams() {
  let html = `<h2 class="screen-title">Diagrams</h2>
    <div class="screen-desc">${totalDiagramCount} diagrams reused verbatim from docs/architecture/01-05 — nothing re-derived, these files stay the authoring source.</div>`;

  if (!view.groupKey) {
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
  const isGraphType = GRAPH_TYPE_KEYS.includes(g.key);
  zoomLevel = 1;
  diagramCy = null;
  html += `<div class="diagram-toolbar">
      <button onclick="navigate({screen:'diagrams'})">← all diagrams</button>
      <span class="zoom-controls">
        <button onclick="zoomDiagram(-0.15)">−</button>
        <span id="zoom-label">100%</span>
        <button onclick="zoomDiagram(0.15)">+</button>
        <button onclick="zoomDiagram(0)">reset</button>
      </span>
    </div>`;

  if (isGraphType) {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered as an interactive graph (parsed from the Mermaid source) — drag nodes, edges follow, same as the System map.</div>
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>`;
    document.getElementById("content").innerHTML = html;
    renderCytoscapeDiagram(d.source);
  } else {
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}, rendered live via Mermaid.js. This diagram type (${g.key === "04-database-erd" ? "entity-relationship" : "sequence/temporal"}) isn't rendered as a draggable node graph like the System map — an ERD needs a table/column renderer and a sequence diagram shows message order along lifelines, neither of which is "drag this box anywhere" the way a dependency graph is. Drag to pan, or use the zoom controls above.</div>
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
  else renderSystem();
}

render();
</script>
</body>
</html>
HTML_TAIL

echo "Wrote $HTML_OUTPUT"
exit 0
