#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Regenerates .claude/nav/adr-index.md first (a generated file this script then reads),
#   then generates architecture-model.json and architecture-map.html -- the live, browsable
#   architecture control plane -- from real repo state, no hand-maintained markdown. Regenerating
#   the ADR index here keeps a single "regenerate the architecture docs" run from leaving it stale.
#   Generates architecture-model.json from repo files and Java source (no bytecode analysis; Sonar,
#   ArchUnit and CI data only with the flags below) -- module/command/skill/agent graph nodes from
#   pom.xml/config sources, with ADRs folded into each module's intent[] list.
# Usage: bash docs/architecture/scripts/generate-architecture-model.sh [--with-adr-details] [--with-sonar] [--no-sonar-rescan] [--with-archunit] [--with-ci-metrics]
#   (no flags)         regenerate architecture-model.json and architecture-map.html from repo state, including JaCoCo coverage and Surefire results from the last local build-and-test.sh run
#   --with-adr-details embed full ADR text in the architecture-map.html (slower, larger output)
#   --with-sonar       include SonarQube quality metrics; unless --no-sonar-rescan is given, it runs
#                      scripts/sonar.sh --no-gate (a full build, test and scan, which starts the server)
#                      when SonarQube is unreachable, has no analysis or is older than a changed .java file;
#                      with no sonar.token in scripts/sonar/sonar-project.properties it never scans
#   --no-sonar-rescan  use the stored Sonar analysis as is (the CI sonar stage already scanned); only meaningful with --with-sonar
#   --with-archunit    include the ArchUnit module-coupling export that build-and-test.sh --archunit-metrics wrote (never runs it)
#   --with-ci-metrics  include the per-step status and duration of the last CI run (scripts/ci/reports/pipeline-metrics.json)
# Uses: bash, node (liquibase-schema-to-json.js, .claude/nav/scripts/generate-adr-index.sh, md-to-decisions-json.js),
#   python3 (JaCoCo/Surefire parsing always; Sonar/ArchUnit only with --with-sonar/--with-archunit),
#   curl and scripts/sonar.sh (only with --with-sonar), docker (a Node container when no local node).
# Env: None.
# Input: scripts/build-and-test/reports/jacoco/*.xml, scripts/build-and-test/reports/surefire/*/
#   (also the same two folders inside scripts/build-and-test/reports/advertisement-build-only-unit,
#   scripts/build-and-test/reports/advertisement-build-only-integration and
#   scripts/build-and-test/reports/advertisement-build-only-ci-sonar, the CI stage folders),
#   scripts/logs/** and scripts/ci/reports/ (run logs and CI metrics), marketplace-app/target/architecture-metrics.json
#   (only with --with-archunit), SonarQube's server data (only with --with-sonar), pom.xml, Java source + Javadoc, Liquibase changelogs, every module's DECISIONS.md,
#   .claude/nav/adr-index.md, .claude/commands, .claude/skills, .claude/agents,
#   backlog/, root CLAUDE.md, root README.md, INFRASTRUCTURE.md, docs/best-practices.md, .claude/rules/*.md (including html/markdown embedded sections marked
#   with <!-- #arch-embed:KEY --> ... <!-- /#arch-embed -->), and the browser libraries the page
#   loads from docs/architecture/vendor/, which must stay next to architecture-map.html (cytoscape 3.28.1, dagre 0.8.5, cytoscape-dagre 2.5.0, minisearch 7.2.0, all MIT, each with its
#   own LICENSE file; to update one, replace its file there), and the page CSS and JavaScript from
#   docs/architecture/page/ (style.css plus the *.js files, joined in the order page/order.txt names them; eslint.config.js there only configures the lint run).
#   The model-building code comes from docs/architecture/scripts/model/*.sh, sourced in the order the generator names them.
# Outputs: .claude/nav/adr-index.md (regenerated in place), docs/architecture/data/architecture-model.json,
#   docs/architecture/architecture-map.html, docs/architecture/data/arch-embed-index.md. JSON/HTML
#   built to temp siblings and moved into place only on full success (atomic -- interrupted runs
#   never leave partial/empty files).
# Returns: 0 on success, non-zero if any generation step fails, among them a Database ERD column named
#   like a reference (entity_id, author_id, actor_id, created_by, *_actor_id, *_taxon_id) that has no foreign
#   key and no "References <table>(<column>), no FK" marker in its remarks=.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# Fixed collation so host and container runs sort identically
unset LC_ALL
export LC_COLLATE=C

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

# The ADR index is a generated file this script then reads -- regenerate it first so one
# "regenerate the architecture docs" run refreshes it too and never leaves it stale behind the model/map.
bash "$REPO_ROOT/.claude/nav/scripts/generate-adr-index.sh"

OUTPUT="$REPO_ROOT/docs/architecture/data/architecture-model.json"
HTML_OUTPUT="$REPO_ROOT/docs/architecture/architecture-map.html"
ARCH_EMBED_INDEX="$REPO_ROOT/docs/architecture/data/arch-embed-index.md"

# Atomic write: build the .json/.html into temp siblings and mv into place only on full success,
# so an interrupted run never leaves a partial or empty committed file.
OUTPUT_FINAL="$OUTPUT"
HTML_OUTPUT_FINAL="$HTML_OUTPUT"
OUTPUT="$(mktemp "${OUTPUT}.XXXXXX.tmp")"
HTML_OUTPUT="$(mktemp "${HTML_OUTPUT}.XXXXXX.tmp")"
trap 'rm -f "$OUTPUT" "$HTML_OUTPUT"' EXIT
ADR_INDEX="$REPO_ROOT/.claude/nav/adr-index.md"
ROOT_CLAUDE_MD="$REPO_ROOT/CLAUDE.md"

MODEL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/model" && pwd)"
# shellcheck source=/dev/null
source "$MODEL_DIR/helpers.sh"
source "$MODEL_DIR/tooling-dirs.sh"
source "$MODEL_DIR/domains.sh"
source "$MODEL_DIR/code-signals.sh"
source "$MODEL_DIR/adrs.sh"
source "$MODEL_DIR/dependencies.sh"
source "$MODEL_DIR/pipelines-backlog.sh"
source "$MODEL_DIR/spi-map.sh"
source "$MODEL_DIR/db-erd.sh"
source "$MODEL_DIR/metrics.sh"
source "$MODEL_DIR/code-checks.sh"
source "$MODEL_DIR/script-headers.sh"
source "$MODEL_DIR/bounded-contexts.sh"
source "$MODEL_DIR/arch-embeds.sh"

[ -n "$WITH_SONAR" ] && [ -z "$NO_SONAR_RESCAN" ] && ensure_sonar_fresh

sonar_json="null"
[ -n "$WITH_SONAR" ] && sonar_json="$(sonar_metrics_json)"
archunit_json="null"
[ -n "$WITH_ARCHUNIT" ] && archunit_json="$(archunit_metrics_json)"
ci_metrics_json="null"
[ -n "$WITH_CI_METRICS" ] && ci_metrics_json="$(ci_pipeline_metrics_json)"
test_reports_json_out="$(test_reports_json)"
run_logs_json_out="$(run_logs_json)"
services_list_json="$(services_json)"
db_erd_out="$(db_erd_json)"

{
  echo "{"
  echo "  \"generated_by\": \"docs/architecture/scripts/generate-architecture-model.sh\","
  echo "  \"generated_note\": \"Track A, plus real SonarQube/ArchUnit metrics -- modules+deps from pom.xml, domain grouping/entities/services/contracts derived live from real Java source and the module list, tables live from the real Liquibase changelogs. Module Dependencies (01)/SPI Map (02)/Database ERD (04)/Bounded Contexts have no .md counterpart -- rendered live on this tool's own Diagrams page instead, lifecycle from DECISIONS.md/backlog, pipeline nodes from .claude/commands + .claude/skills + .claude/agents.\","
  echo "  \"rootArtifactId\": \"$(json_escape "$ROOT_ARTIFACT_ID")\","
  echo "  \"rootVersion\": \"$(json_escape "$ROOT_VERSION")\","
  echo "  \"diagramGroups\": ["
  echo "$diagram_groups_json"
  echo "  ],"
  echo "  \"archEmbeds\": $(arch_embeds_json),"
  echo "  \"backlogPriorityOrder\": $(backlog_priority_order_json),"
  echo "  \"spiMap\": $(spi_map_json),"
  echo "  \"dbErd\": $db_erd_out,"
  echo "  \"boundedContexts\": $(bounded_contexts_json),"
  echo "  \"sonarMetrics\": $sonar_json,"
  echo "  \"archUnitMetrics\": $archunit_json,"
  echo "  \"ciPipelineMetrics\": $ci_metrics_json,"
  echo "  \"testReports\": $test_reports_json_out,"
  echo "  \"runLogs\": $run_logs_json_out,"
  echo "  \"couplingChecks\": $(coupling_checks_json),"
  echo "  \"largestJavaFiles\": $(largest_java_files_json),"
  echo "  \"constructorInjection\": $(constructor_injection_json),"
  echo "  \"godPackages\": $(god_packages_json),"
  echo "  \"allAdrs\": $(all_adrs_json),"
  echo "  \"rootReadme\": $(root_md_json_for "README.md"),"
  echo "  \"rootInfrastructure\": $(root_md_json_for "INFRASTRUCTURE.md"),"
  echo "  \"services\": $services_list_json,"
  echo "  \"rootBestPractices\": $(root_md_json_for "docs/best-practices.md"),"
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
    echo "      \"entities\": $(json_named_file_array "${MODULE_ENTITY[$m]:-}"),"
    echo "      \"keyServices\": $(json_named_file_array "${MODULE_KEYSERVICES[$m]:-}"),"
    echo "      \"contracts\": $(json_named_file_array "${MODULE_CONTRACT[$m]:-}"),"
    echo "      \"tables\": $(json_str_array "${MODULE_TABLES[$m]:-}"),"
    echo "      \"readme\": $(readme_json_for "$m"),"
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
    emit_pipeline_md_node "command:$name" "COMMAND" "$rel" "$(command_first_line "$file")"
  done < <(find "$REPO_ROOT/.claude/commands" -name "*.md" -print0 | sort -z)

  # SKILL nodes
  while IFS= read -r -d '' file; do
    name="$(basename "$(dirname "$file")")"
    rel="${file#"$REPO_ROOT"/}"
    emit_pipeline_md_node "skill:$name" "SKILL" "$rel" "$(command_first_line "$file")"
  done < <(find "$REPO_ROOT/.claude/skills" -name "SKILL.md" -print0 2>/dev/null | sort -z)

  # AGENT nodes
  while IFS= read -r -d '' file; do
    name="$(basename "$file" .md)"
    [ "$name" = "README" ] && continue
    rel="${file#"$REPO_ROOT"/}"
    emit_pipeline_md_node "agent:$name" "AGENT" "$rel" "$(command_first_line "$file")"
  done < <(find "$REPO_ROOT/.claude/agents" -maxdepth 1 -name "*.md" -print0 2>/dev/null | sort -z)

  # SCRIPT_GROUP nodes -- non-Maven tooling directories, most (not all -- see .claude/nav/scripts above)
  # with their own DECISIONS.md
  for d in "${SCRIPT_GROUP_DIRS[@]}"; do
    desc=""
    [ -f "$REPO_ROOT/.claude/rules/$d.md" ] && desc="$(sed -n '5{s/^#* *//;p}' "$REPO_ROOT/.claude/rules/$d.md")"
    files_list="$(script_group_files_list "$d")"
    files_json="$(json_str_array "$files_list")"
    decisions_field="$(decisions_json_for "$d")"
    readme_field="$(readme_json_for "$d")"
    headers_field="$(script_headers_json "$d" "$files_list")"
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
    echo "      \"decisions\": $decisions_field,"
    echo "      \"readme\": $readme_field,"
    echo "      \"headers\": $headers_field,"
    echo "      \"edges\": {}"
    echo "    }"
  done

  # SCRIPT_GROUP tree roots -- scripts/, playwright/, and .claude/, each an arbitrary-depth
  # drill-down rooted at that directory (docs/architecture/scripts/DECISIONS.md: the unified
  # "Scripts" card; .claude/ feeds the "AI Tooling" card the same way). Each top-level root still
  # emits as a normal top-level SCRIPT_GROUP node (its own SCRIPT_TREE_ROOT_CATEGORY entry, so
  # PIPELINE_GROUPS' tree cards can filter for it) -- only the *nested* levels additionally carry a
  # "children" array of the same SCRIPT_GROUP shape, since a plain file listing at any depth already
  # reuses files/headers/readme exactly like a flat SCRIPT_GROUP dir does.
  for d in "${SCRIPT_TREE_ROOTS[@]}"; do
    echo "    ,"
    emit_script_tree_node "$d" "${SCRIPT_TREE_ROOT_CATEGORY[$d]}"
  done

  # BACKLOG summary node -- title + short (truncated) description + real file link, per issue,
  # for both open and completed lists. Not the full issue body (that would bloat the model the
  # same way embedding full ADR prose would, for content this tool doesn't otherwise need) -- click
  # shows the short description, a real link opens the actual file for the rest.
  open_count=$(find "$REPO_ROOT/backlog/tasks" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l | tr -d ' ')
  completed_count=$(find "$REPO_ROOT/backlog/completed/tasks" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l | tr -d ' ')
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
  echo "      \"openIssues\": $(issue_list_json "$REPO_ROOT/backlog/tasks" "backlog/tasks"),"
  echo "      \"completedIssues\": $(issue_list_json "$REPO_ROOT/backlog/completed/tasks" "backlog/completed/tasks"),"
  echo "      \"edges\": {}"
  echo "    }"

  echo "  ]"
  echo "}"
} > "$OUTPUT"

if command -v python3 >/dev/null 2>&1; then
  python3 -c "import json,sys; json.load(open('$OUTPUT'))" \
    && echo "Valid JSON: $OUTPUT_FINAL" \
    || { echo "ERROR: generated JSON is invalid" >&2; exit 1; }
fi

node_count=$(grep -c '"type":' "$OUTPUT" || true)
echo "Wrote $node_count nodes to $OUTPUT_FINAL"

# ── A2: architecture-map.html -- a real drill-down pyramid (System -> Module -> Tooling & Pipelines), not a flat graph with a
# JSON-dump side panel. Static Cytoscape.js explorer, model JSON inlined so the file works
PAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../page" && pwd)"
# Page script fragments, in the order page/order.txt names them; every fragment must be listed and every listed file must exist
mapfile -t PAGE_JS_FILES < "$PAGE_DIR/order.txt"
for page_file in "${PAGE_JS_FILES[@]}"; do
  [ -f "$PAGE_DIR/$page_file.js" ] || { echo "ERROR: page/order.txt names $page_file.js, which does not exist" >&2; exit 1; }
done
for page_path in "$PAGE_DIR"/*.js; do
  page_file="$(basename "$page_path" .js)"
  [ "$page_file" = eslint.config ] && continue
  printf '%s\n' "${PAGE_JS_FILES[@]}" | grep -qxF "$page_file" \
    || { echo "ERROR: $page_file.js is not listed in page/order.txt" >&2; exit 1; }
done
# Syntax check of the joined page script, so a broken fragment stops the run before the page is written
for page_file in "${PAGE_JS_FILES[@]}"; do cat "$PAGE_DIR/$page_file.js"; done | run_node --check - \
  || { echo "ERROR: the page script has a syntax error" >&2; exit 1; }
# standalone via file:// (no local server, no separate fetch() that CORS would block).
cat > "$HTML_OUTPUT" <<'HTML_HEAD'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Architecture Control Plane</title>
<script src="vendor/cytoscape/cytoscape.min.js"></script>
<script src="vendor/dagre/dagre.min.js"></script>
<script src="vendor/cytoscape-dagre/cytoscape-dagre.js"></script>
<script src="vendor/minisearch/minisearch.umd.js"></script>
<style>
HTML_HEAD
cat "$PAGE_DIR/style.css" >> "$HTML_OUTPUT"
cat >> "$HTML_OUTPUT" <<'HTML_HEAD_REST'
</style>
</head>
<body>
<header>
  <h1>Architecture Control Plane</h1>
  <div class="subtitle">Generated from pom.xml, DECISIONS.md, backlog/, .claude/commands, .claude/skills, .claude/agents, root CLAUDE.md — regenerate via <code>bash docs/architecture/scripts/generate-architecture-model.sh</code>.</div>
  <div class="header-row">
    <nav id="breadcrumb"></nav>
    <div id="search-wrap">
      <input type="search" id="search-box" placeholder="Search modules, ADRs, tasks, tables, docs" autocomplete="off">
      <div id="search-results" hidden></div>
    </div>
  </div>
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
HTML_HEAD_REST

cat "$OUTPUT" >> "$HTML_OUTPUT"

cat >> "$HTML_OUTPUT" <<'HTML_TAIL_OPEN'
;
HTML_TAIL_OPEN
for page_file in "${PAGE_JS_FILES[@]}"; do
  cat "$PAGE_DIR/$page_file.js" >> "$HTML_OUTPUT"
done
cat >> "$HTML_OUTPUT" <<'HTML_TAIL_CLOSE'
</script>
</body>
</html>
HTML_TAIL_CLOSE

# Move the temp siblings into place. $OUTPUT/$HTML_OUTPUT stay pointed at the (now-gone) temp
# paths so the EXIT trap's `rm -f` is a harmless no-op and never touches the real files.
mv -f "$OUTPUT" "$OUTPUT_FINAL"
mv -f "$HTML_OUTPUT" "$HTML_OUTPUT_FINAL"
chmod 644 "$OUTPUT_FINAL" "$HTML_OUTPUT_FINAL"  # mktemp makes 0600; committed files must stay world-readable for CI's sync

echo "Wrote $HTML_OUTPUT_FINAL"

arch_embed_index_md > "$ARCH_EMBED_INDEX"
echo "Wrote $ARCH_EMBED_INDEX"

exit 0
