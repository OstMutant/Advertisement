# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Per-module signals read from real source: ArchUnit coupling metrics, module domain grouping, entities, key services, contracts, owned tables and the one-line module descriptions taken from the root project instructions.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, grep, find, awk, python3, run_node.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: Java sources and Javadoc, Liquibase changelogs, root CLAUDE.md, the ArchUnit metrics JSON.
# Outputs: defines ARCHUNIT_METRICS_FILE(_FALLBACK), spi_owns_iface, javadoc_purpose_for and the MODULE_DOMAIN / MODULE_KEYSERVICES / MODULE_CONTRACT / MODULE_ENTITY / MODULE_TABLES / MODULE_DESCRIPTION tables.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── ArchUnit: real Efferent/Afferent Coupling, Instability, Abstractness per module, computed by
# ArchitectureMetricsExport (marketplace-app/src/test/java/org/ost/marketplace/architecture).
# Its class name doesn't match Surefire's default *Test/Test*/*Tests/*TestCase include patterns,
# so it never runs as part of a normal `mvn test` -- must be explicitly forced
# (-Dtest=ArchitectureMetricsExport), and needs the full reactor already installed (it scans every
# module's classes on one combined classpath). Two possible sources, checked in order: a direct
# host-side forced run writes straight to marketplace-app/target/; `bash scripts/build-and-test.sh
# --archunit-metrics` runs it inside a throwaway container instead and moves the result to
# scripts/build-and-test/reports/architecture-metrics.json. Read here if present; null if neither
# has run yet -- optional data, no auto-trigger (several minutes even on a warm build, a much
# bigger cost than SonarQube's own staleness check, so this stays passively "as fresh as the last
# run" instead). Declared here (ahead of the Entities/Key Services/Contract block below, which
# needs it at top-level script scope, not deferred inside a later-called function) -- same ordering
# constraint javadoc_purpose_for() already hit earlier this session.
ARCHUNIT_METRICS_FILE="$REPO_ROOT/marketplace-app/target/architecture-metrics.json"
ARCHUNIT_METRICS_FILE_FALLBACK="$REPO_ROOT/scripts/build-and-test/reports/architecture-metrics.json"

# Whether $1 (a module id) really owns (implements or calls) $2 (an SPI interface simple name),
# per the real bytecode-derived spiEdges data -- replaces this generator's own
# older implements-regex guessing (found producing the same class of false
# positive/negative spi_map_json() already had before its own ArchUnit-based fix). Exit code is a
# 3-way signal, not a boolean: 0 = real data confirms ownership, 1 = real data confirms no
# ownership, 2 = no ArchUnit data available at all this session -- callers fall back to their own
# existing regex only on 2, never silently lose coverage when --archunit-metrics hasn't run.
spi_owns_iface() {
  local mod="$1" iface="$2" archunit_file=""
  [ -f "$ARCHUNIT_METRICS_FILE" ] && archunit_file="$ARCHUNIT_METRICS_FILE"
  [ -z "$archunit_file" ] && [ -f "$ARCHUNIT_METRICS_FILE_FALLBACK" ] && archunit_file="$ARCHUNIT_METRICS_FILE_FALLBACK"
  [ -z "$archunit_file" ] && return 2
  python3 -c "
import json, sys
data = json.load(open(sys.argv[1]))
edges = data.get('spiEdges', {}).get(sys.argv[3])
if edges is None:
    sys.exit(2)
owns = any(m['module'] == sys.argv[2] for m in edges.get('implementations', [])) or any(c['module'] == sys.argv[2] for c in edges.get('callers', []))
sys.exit(0 if owns else 1)
" "$archunit_file" "$mod" "$iface"
}

# Live from the real Javadoc immediately above the class/interface/record/enum declaration --
# single source of truth lives next to the code, not duplicated in this generator. Walks backward
# from that declaration line past blank/annotation lines (handles @FunctionalInterface) to find a
# preceding "*/", then further back to the matching "/**"; strips "*"/"{@code}"/"{@link}" markup.
# Every *.spi interface is expected to carry one -- see .claude/rules/platform-commons.md. Also reused for
# entity/service/contract classes on the Module screen -- not every
# one of those is guaranteed a Javadoc comment, so an empty result there is expected, not an error.
# Defined here (ahead of the Entities/Key Services/Contract block below, which calls it at
# top-level script scope, not deferred inside a later-called function) -- bash has no function
# hoisting, so a definition after its first call site fails with "command not found" (confirmed
# directly).
javadoc_purpose_for() {
  local file="$1"
  awk '
    /^[[:space:]]*(public[[:space:]]+)?(final[[:space:]]+)?(abstract[[:space:]]+)?(static[[:space:]]+)?(class|interface|record|enum)[[:space:]]+/ && !found { target=NR; found=1 }
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

# ── Domain grouping + Entities/Key Services/Contract lists per module -- live, same real signals
# bounded_contexts_json() uses further down (real @Table classes, real *Service classes, real SPI
# interface `implements` relationships), just keyed by module instead of by domain. Each line is
# "name<TAB>file<TAB>description" (json_named_file_array()'s 3-column shape) -- file/description
# feed the Module screen's real file links + Javadoc one-liners.
declare -A MODULE_DOMAIN MODULE_KEYSERVICES MODULE_CONTRACT MODULE_ENTITY
for bc_d in "${BC_DOMAIN_ORDER[@]}"; do
  bc_m="${BC_DOMAIN_MODULE[$bc_d]}"
  MODULE_DOMAIN["$bc_m"]="${BC_DOMAIN_LABEL[$bc_d]}"
  [ "$bc_d" = "Shared" ] || [ "$bc_d" = "UI" ] && continue
  MODULE_ENTITY["$bc_m"]="$( (grep -rl '^@Table\|@Table(' "$REPO_ROOT/$bc_m/src/main/java" --include='*.java' 2>/dev/null || true) | sort | while read -r ef; do
    printf '%s\t%s\t%s\n' "$(basename "$ef" .java)" "${ef#"$REPO_ROOT"/}" "$(javadoc_purpose_for "$ef")"
  done)"$'\n'
  MODULE_KEYSERVICES["$bc_m"]="$(find "$REPO_ROOT/$bc_m/src/main/java" -name '*Service.java' 2>/dev/null | sort | while read -r sf; do
    printf '%s\t%s\t%s\n' "$(basename "$sf" .java)" "${sf#"$REPO_ROOT"/}" "$(javadoc_purpose_for "$sf")"
  done)"$'\n'
  MODULE_CONTRACT["$bc_m"]="$( (find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java' | sort | while read -r ifile; do
    iface="$(basename "$ifile" .java)"
    owns=2
    spi_owns_iface "$bc_m" "$iface" && owns=0 || owns=$?
    if [ "$owns" = 2 ]; then
      grep -qlP "implements\s+.*\b${iface}\b" -r --include='*.java' "$REPO_ROOT/$bc_m/src/main/java" 2>/dev/null && owns=0
    fi
    [ "$owns" = 0 ] && printf '%s\t%s\t%s\n' "$iface" "${ifile#"$REPO_ROOT"/}" "$(javadoc_purpose_for "$ifile")"
    true
  done) || true)"$'\n'
done
# Orchestrator calls Ports via ComponentFactory<XPort> rather than implementing them -- the
# generic "implements XPort" grep above finds nothing for it, so override with the real signal:
# which *Port types it actually injects.
if [ -n "$bc_orch_mod" ]; then
  MODULE_CONTRACT["$bc_orch_mod"]="$( (find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java' | sort | while read -r ifile; do
    iface="$(basename "$ifile" .java)"
    owns=2
    spi_owns_iface "$bc_orch_mod" "$iface" && owns=0 || owns=$?
    if [ "$owns" = 2 ]; then
      grep -qlP "ComponentFactory<\s*${iface}\s*>|\b${iface}\s+\w+\s*;" -r --include='*.java' "$REPO_ROOT/$bc_orch_mod/src/main/java" 2>/dev/null && owns=0
    fi
    [ "$owns" = 0 ] && printf '%s\t%s\t%s\n' "$iface" "${ifile#"$REPO_ROOT"/}" "$(javadoc_purpose_for "$ifile")"
    true
  done) || true)"$'\n'
fi
# Structural modules the domain map above doesn't cover -- fallback labels, confidence: manual
# either way (both paths are heuristic, not extracted).
MODULE_DOMAIN["marketplace-app"]="${MODULE_DOMAIN[marketplace-app]:-UI/Application Layer}"
MODULE_DOMAIN["query-lib"]="${MODULE_DOMAIN[query-lib]:-Shared Kernel}"
MODULE_DOMAIN["html-sanitizer-lib"]="${MODULE_DOMAIN[html-sanitizer-lib]:-Shared Kernel}"
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
  run_node "$REPO_ROOT/docs/architecture/scripts/liquibase-schema-to-json.js" "$REPO_ROOT" "${files[@]}" \
    | run_node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>JSON.parse(d).forEach(t=>console.log(t.module+"\t"+t.name)))'
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

