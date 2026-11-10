# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: The SPI Map: subsystem order and labels, the interfaces and implementations extracted from real Java source, and the diagram groups for the SPI Map and Bounded Contexts screens.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, grep, sed, find, python3.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: Java sources under platform-commons and every starter and the application modules.
# Outputs: defines SPI_SUBSYSTEM_* tables, spi_subsystem_label_for, spi_kind_for, spi_map_json, spi_map_diagrams_json, bounded_contexts_diagrams_json.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── SPI Map subsystem order/labels -- declared before diagram_groups_json below since the SPI Map
# group now has one diagram per subsystem (one tab per subsystem instead of one cluttered flat
# canvas). Reused again inside spi_map_json() further down instead of a second, separately-
# maintained local copy.
# Discovered live from platform-commons' own spi/ package tree -- a new domain's spi/ package
# (org/ost/platform/<x>/spi/*.java) gets its own tab automatically, no edit needed here. Previously
# a fully hardcoded list that silently dropped any subsystem not manually added to it (confirmed:
# both "contact" and "feedback" were missing before this fix, with real ContactPort/FeedbackPort
# data already present in spiMap.details/nodes but unreachable from any tab or the markdown export
# -- see docs/architecture/scripts/DECISIONS.md and this task's own verification notes).
mapfile -t SPI_SUBSYSTEM_DISCOVERED < <(
  for spi_f in "$REPO_ROOT"/platform-commons/src/main/java/org/ost/platform/*/spi/*.java; do
    [ -f "$spi_f" ] || continue
    sed -n 's/^package *org\.ost\.platform\.\([a-z]*\)\.spi;.*/\1/p' "$spi_f"
  done | sort -u
)
# Preferred display order for subsystems we curate a specific position/label for; anything
# discovered above but not listed here is appended at the end automatically, so a new subsystem
# still gets its own tab (with an auto-generated label, see spi_subsystem_label_for below) instead
# of silently disappearing.
declare -a SPI_SUBSYSTEM_PREFERRED_ORDER=(audit attachment user apikey advertisement taxon providerprofile core)
declare -a SPI_SUBSYSTEM_ORDER=()
for spi_s in "${SPI_SUBSYSTEM_PREFERRED_ORDER[@]}"; do
  for spi_d in "${SPI_SUBSYSTEM_DISCOVERED[@]}"; do [ "$spi_s" = "$spi_d" ] && SPI_SUBSYSTEM_ORDER+=("$spi_s"); done
done
for spi_d in "${SPI_SUBSYSTEM_DISCOVERED[@]}"; do
  spi_found=false
  for spi_s in "${SPI_SUBSYSTEM_ORDER[@]}"; do [ "$spi_s" = "$spi_d" ] && spi_found=true; done
  $spi_found || SPI_SUBSYSTEM_ORDER+=("$spi_d")
done
declare -A SPI_SUBSYSTEM_LABEL=(
  [audit]="Audit Subsystem"
  [attachment]="Attachment Subsystem"
  [user]="User Subsystem"
  [apikey]="API-Key Subsystem"
  [advertisement]="Advertisement Subsystem"
  [taxon]="Taxon (Reference Data) Subsystem"
  [providerprofile]="Provider Profile Subsystem"
  [core]="Core / Platform"
)
# Fallback for any subsystem discovered above with no explicit override: Title-Case the key + "
# Subsystem" (e.g. "feedback" -> "Feedback Subsystem"). Add a real SPI_SUBSYSTEM_LABEL entry
# whenever this generic fallback reads awkwardly (multi-word/abbreviated names, same as
# "providerprofile"/"apikey"/"taxon"/"core" already need above).
spi_subsystem_label_for() {
  local s="$1"
  if [ -n "${SPI_SUBSYSTEM_LABEL[$s]:-}" ]; then
    echo "${SPI_SUBSYSTEM_LABEL[$s]}"
  else
    echo "$(tr '[:lower:]' '[:upper:]' <<< "${s:0:1}")${s:1} Subsystem"
  fi
}

spi_map_diagrams_json() {
  local out="" first=true s
  for s in "${SPI_SUBSYSTEM_ORDER[@]}"; do
    $first || out="$out,"
    first=false
    out="$out{\"title\": \"$(json_escape "$(spi_subsystem_label_for "$s")")\", \"source\": \"\", \"subsystem\": \"$(json_escape "$s")\"}"
  done
  echo "$out"
}

# One tab per relationship category (BC_CATEGORY_ORDER/LABEL/DESC above) instead of one combined
# canvas -- same per-subsystem split pattern as spi_map_diagrams_json() above.
bounded_contexts_diagrams_json() {
  local out="" first=true c
  for c in "${BC_CATEGORY_ORDER[@]}"; do
    $first || out="$out,"
    first=false
    out="$out{\"title\": \"$(json_escape "${BC_CATEGORY_LABEL[$c]}")\", \"source\": \"\", \"category\": \"$(json_escape "$c")\", \"description\": \"$(json_escape "${BC_CATEGORY_DESC[$c]}")\"}"
  done
  echo "$out"
}

# Order and description are both data here, same source of truth as file/label.
diagram_groups_json="  {\"key\": \"bounded-contexts\", \"label\": \"Bounded Contexts\", \"file\": \"real code (live)\", \"description\": \"Which domain actually calls which other domain in real code, and why -- entities/services/tables/ports per domain, relationships from real Hook/Port usage signals. Split one tab per relationship nature (BFF service calls, reverse Hook callbacks, derived facts) -- the single combined graph mixed fundamentally different kinds of relationship in one arrow+label visual.\", \"diagrams\": [$(bounded_contexts_diagrams_json)]},"$'\n'"  {\"key\": \"02-spi-map\", \"label\": \"SPI Map\", \"file\": \"platform-commons/src (live)\", \"description\": \"Every cross-module Port/Hook interface in platform-commons, who really calls it, and who really implements it -- three real-code facts, not a build-graph fact. Split one tab per subsystem -- the single combined graph got too dense to read.\", \"diagrams\": [$(spi_map_diagrams_json)]},"$'\n'"  {\"key\": \"01-module-dependencies\", \"label\": \"Module Dependencies\", \"file\": \"pom.xml (live)\", \"description\": \"Which module's JAR ends up on which other module's classpath, per real pom.xml <dependency> declarations -- a Maven build-graph fact, not a real-code-call fact (a module can depend on a JAR nothing in its code calls yet).\", \"diagrams\": [{\"title\": \"Dependency Graph\", \"source\": \"\"}]},"$'\n'"  {\"key\": \"04-database-erd\", \"label\": \"Database ERD\", \"file\": \"Liquibase changelogs (live)\", \"description\": \"Every table and column this app persists, with the business-meaning remarks pulled live from each Liquibase changelog.\", \"diagrams\": [{\"title\": \"Entity Relationship Diagram\", \"source\": \"\"}]}"

# ── SPI Map: mechanically extracted from real Java source, same "live from real source, not a
# separately-maintained .md" pattern as Module Dependencies (01) -- every *.spi interface under
# platform-commons, plus every real caller/implementor of it across the starters/marketplace-app.
# Caller/implementor edges: real bytecode-derived data from ArchitectureMetricsExport.java's
# spiEdges when available, falling back to grep (text-pattern matching, not full semantic/bytecode
# analysis -- same bar as module_deps()) otherwise -- see javadoc_purpose_for()'s neighboring
# archunit_file handling above.
# "Purpose" one-liners ARE mechanically extracted too, just from a different real source than the
# edges: javadoc_purpose_for() reads each interface's own Javadoc block live, every run -- not a
# static lookup, and not something ArchUnit could supply even in principle (bytecode carries no
# Javadoc text). This makes every *.spi interface's own Javadoc a mechanically-required input, per
# module-doc-standards/SKILL.md's "SPI interface Javadoc" section.
# Subsystem-level editorial notes (SPI_SUBSYSTEM_NOTE below) remain the one genuinely-editorial,
# no-mechanical-source part of this diagram -- explain a non-obvious absence (Attachment has no
# starter->marketplace media-change callback) or a design rationale (User's 4-port split) that no
# per-interface extraction, mechanical or not, has any way to produce on its own.
declare -A SPI_SUBSYSTEM_NOTE=(
  [attachment]="AttachmentMediaChangeHook does not exist -- there is no starter->marketplace media-change callback. Media summaries are computed at read time via AttachmentPort.getMediaSummaries() instead (see marketplace-app/DECISIONS.md ADR-035)."
  [user]="Split into 4 narrow ports (see platform-commons/DECISIONS.md ADR-026 for the rationale -- interface cohesion, not runtime-toggle behavior; all 4 are always implemented by user-spring-boot-starter)."
)
spi_kind_for() {
  case "$1" in
    UiLabelHook|SessionActorHook) echo "Hook (marketplace-orchestrator -> marketplace-app)" ;;
    *Port) echo "Port (marketplace -> starter)" ;;
    *Hook) echo "Hook (starter -> marketplace)" ;;
    *) echo "type contract" ;;
  esac
}

spi_map_json() {
  local nodes="" edges="" details="" first_node=true first_edge=true first_detail=true
  local -A group_seen=() caller_node_seen=()
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

    # Real, bytecode-derived edges (ArchitectureMetricsExport.java's spiEdges)
    # when available; falls back to the old grep-regex tree-walk otherwise (e.g. --archunit-metrics
    # was never run this session). Checked per-interface, not once for the whole file, so a genuine
    # zero-callers interface (valid data) is never confused with "no archunit data at all" (real
    # fallback trigger) -- distinguished by python3's exit code, not by empty output alone.
    local impls_json="" first_impl=true
    local callers_json="" first_caller=true
    local candidate_file impl module caller module_c
    local archunit_file="" edge_rows="" archunit_ok=1
    [ -f "$ARCHUNIT_METRICS_FILE" ] && archunit_file="$ARCHUNIT_METRICS_FILE"
    [ -z "$archunit_file" ] && [ -f "$ARCHUNIT_METRICS_FILE_FALLBACK" ] && archunit_file="$ARCHUNIT_METRICS_FILE_FALLBACK"
    if [ -n "$archunit_file" ]; then
      edge_rows="$(python3 -c "
import json, sys
data = json.load(open(sys.argv[1]))
edges = data.get('spiEdges', {}).get(sys.argv[2])
if edges is None:
    sys.exit(1)
used = set()
for caller in edges.get('callers', []):
    for c in caller.get('calls', []):
        used.add(c['to'])
print('methodCount\t' + str(edges.get('methodCount', 0)))
print('usedCount\t' + str(len(used)))
print('allMethods\t' + json.dumps([{'name': n, 'used': n in used} for n in edges.get('allMethods', [])]))
for impl in edges.get('implementations', []):
    print('\t'.join(['impl', impl['class'], impl['module'], impl['file']]))
for caller in edges.get('callers', []):
    print('\t'.join(['caller', caller['class'], caller['module'], caller['file'], json.dumps(caller.get('calls', []))]))
" "$archunit_file" "$iface" 2>/dev/null)"
      archunit_ok=$?
    fi
    local method_count="" used_count="" all_methods_json=""
    if [ -n "$archunit_file" ] && [ "$archunit_ok" -eq 0 ]; then
      while IFS=$'\t' read -r row_kind class_name mod file_path calls_json; do
        [ -z "$row_kind" ] && continue
        if [ "$row_kind" = "methodCount" ]; then
          method_count="$class_name"
        elif [ "$row_kind" = "usedCount" ]; then
          used_count="$class_name"
        elif [ "$row_kind" = "allMethods" ]; then
          # class_name holds the already-built JSON array ({"name":..,"used":bool}[]) --
          # python3 built it directly, nothing left to merge or re-parse here.
          all_methods_json="$class_name"
        elif [ "$row_kind" = "impl" ]; then
          impl="$class_name"; module="$mod"
          if [ -z "${group_seen[$module]:-}" ]; then
            group_seen[$module]=1
            nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$module")\", \"label\": \"$(json_escape "$module")\", \"isGroup\": true}"
          fi
          nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$impl")\", \"label\": \"$(json_escape "$impl")\", \"parent\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "$file_path")\"}"
          $first_edge || edges="$edges,"$'\n'
          first_edge=false
          edges="$edges    {\"source\": \"$(json_escape "$iface")\", \"target\": \"$(json_escape "$impl")\", \"label\": \"implemented by\"}"
          $first_impl || impls_json="$impls_json, "
          first_impl=false
          impls_json="$impls_json{\"class\": \"$(json_escape "$impl")\", \"module\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "$file_path")\", \"purpose\": \"$(json_escape "$(javadoc_purpose_for "$REPO_ROOT/$file_path")")\"}"
        elif [ "$row_kind" = "caller" ]; then
          caller="$class_name"; module_c="$mod"
          if [ -z "${group_seen[$module_c]:-}" ]; then
            group_seen[$module_c]=1
            nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$module_c")\", \"label\": \"$(json_escape "$module_c")\", \"isGroup\": true}"
          fi
          if [ -z "${caller_node_seen[$caller]:-}" ]; then
            caller_node_seen[$caller]=1
            nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "call_$caller")\", \"label\": \"$(json_escape "$caller")\", \"parent\": \"$(json_escape "$module_c")\", \"file\": \"$(json_escape "$file_path")\"}"
          fi
          $first_edge || edges="$edges,"$'\n'
          first_edge=false
          edges="$edges    {\"source\": \"$(json_escape "call_$caller")\", \"target\": \"$(json_escape "$iface")\", \"label\": \"calls\"}"
          $first_caller || callers_json="$callers_json, "
          first_caller=false
          callers_json="$callers_json{\"class\": \"$(json_escape "$caller")\", \"module\": \"$(json_escape "$module_c")\", \"file\": \"$(json_escape "$file_path")\", \"purpose\": \"$(json_escape "$(javadoc_purpose_for "$REPO_ROOT/$file_path")")\", \"calls\": ${calls_json:-[]}}"
        fi
      done <<< "$edge_rows"
    else
      local IMPL_PATTERN="implements\s+.*\b${iface}\b"
      # For *Port this is marketplace-app/marketplace-orchestrator/a starter calling another
      # starter's port; for *Hook this is whichever module actually injects it -- checked
      # per-interface via CALLER_PATTERN below, never assumed from the *Hook suffix's usual "starter
      # calls back" direction alone. Both injection shapes appear in real code (a single mandatory
      # field, or a List<Iface> collection for hooks with several registered beans), so both are
      # matched.
      local CALLER_PATTERN="ComponentFactory<\s*${iface}\s*>|List<\s*${iface}\s*>|\b${iface}\s+\w+\s*;"
      while IFS= read -r candidate_file; do
        [ -z "$candidate_file" ] && continue
        if grep -qP "$IMPL_PATTERN" "$candidate_file" 2>/dev/null; then
          impl="$(basename "$candidate_file" .java)"
          module="${candidate_file#"$REPO_ROOT"/}"
          module="${module%%/*}"
          if [ -z "${group_seen[$module]:-}" ]; then
            group_seen[$module]=1
            nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$module")\", \"label\": \"$(json_escape "$module")\", \"isGroup\": true}"
          fi
          nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$impl")\", \"label\": \"$(json_escape "$impl")\", \"parent\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "${candidate_file#"$REPO_ROOT"/}")\"}"
          $first_edge || edges="$edges,"$'\n'
          first_edge=false
          edges="$edges    {\"source\": \"$(json_escape "$iface")\", \"target\": \"$(json_escape "$impl")\", \"label\": \"implemented by\"}"
          $first_impl || impls_json="$impls_json, "
          first_impl=false
          impls_json="$impls_json{\"class\": \"$(json_escape "$impl")\", \"module\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "${candidate_file#"$REPO_ROOT"/}")\", \"purpose\": \"$(json_escape "$(javadoc_purpose_for "$candidate_file")")\"}"
        fi
        if grep -qP "$CALLER_PATTERN" "$candidate_file" 2>/dev/null; then
          caller="$(basename "$candidate_file" .java)"
          module_c="${candidate_file#"$REPO_ROOT"/}"
          module_c="${module_c%%/*}"
          if [ -z "${group_seen[$module_c]:-}" ]; then
            group_seen[$module_c]=1
            nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "$module_c")\", \"label\": \"$(json_escape "$module_c")\", \"isGroup\": true}"
          fi
          if [ -z "${caller_node_seen[$caller]:-}" ]; then
            caller_node_seen[$caller]=1
            nodes="$nodes,"$'\n'"    {\"id\": \"$(json_escape "call_$caller")\", \"label\": \"$(json_escape "$caller")\", \"parent\": \"$(json_escape "$module_c")\", \"file\": \"$(json_escape "${candidate_file#"$REPO_ROOT"/}")\"}"
          fi
          $first_edge || edges="$edges,"$'\n'
          first_edge=false
          edges="$edges    {\"source\": \"$(json_escape "call_$caller")\", \"target\": \"$(json_escape "$iface")\", \"label\": \"calls\"}"
          $first_caller || callers_json="$callers_json, "
          first_caller=false
          callers_json="$callers_json{\"class\": \"$(json_escape "$caller")\", \"module\": \"$(json_escape "$module_c")\", \"file\": \"$(json_escape "${candidate_file#"$REPO_ROOT"/}")\", \"purpose\": \"$(json_escape "$(javadoc_purpose_for "$candidate_file")")\"}"
        fi
      done < <(grep -rlP "${IMPL_PATTERN}|${CALLER_PATTERN}" \
          "$REPO_ROOT"/*-spring-boot-starter/src/main/java "$REPO_ROOT"/marketplace-app/src/main/java \
          "$REPO_ROOT"/marketplace-orchestrator/src/main/java 2>/dev/null | sort -u)
    fi

    $first_detail || details="$details,"$'\n'
    first_detail=false
    details="$details    {\"interface\": \"$(json_escape "$iface")\", \"file\": \"$(json_escape "${iface_file#"$REPO_ROOT"/}")\", \"package\": \"$(json_escape "$pkg")\", \"subsystem\": \"$(json_escape "$subsystem")\", \"kind\": \"$(json_escape "$kind")\", \"purpose\": \"$(json_escape "$(javadoc_purpose_for "$iface_file")")\", \"methodCount\": ${method_count:-null}, \"usedCount\": ${used_count:-null}, \"allMethods\": ${all_methods_json:-null}, \"implementations\": [$impls_json], \"callers\": [$callers_json]}"
  done
  local labels_json="" notes_json="" first_s=true
  for s in "${SPI_SUBSYSTEM_ORDER[@]}"; do
    $first_s || labels_json="$labels_json, "
    $first_s || notes_json="$notes_json, "
    first_s=false
    labels_json="$labels_json\"$s\": \"$(json_escape "$(spi_subsystem_label_for "$s")")\""
    notes_json="$notes_json\"$s\": \"$(json_escape "${SPI_SUBSYSTEM_NOTE[$s]:-}")\""
  done
  echo "{"
  echo "  \"nodes\": [${nodes}"$'\n'"  ],"
  echo "  \"edges\": [${edges}"$'\n'"  ],"
  echo "  \"details\": [${details}"$'\n'"  ],"
  echo "  \"subsystemOrder\": $(json_str_array "$(printf '%s\n' "${SPI_SUBSYSTEM_ORDER[@]}")"),"
  echo "  \"subsystemLabels\": {$labels_json},"
  echo "  \"subsystemNotes\": {$notes_json}"
  echo "}"
}

