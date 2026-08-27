#!/usr/bin/env bash
# Description: Generates architecture-model.json and architecture-map.html -- the live, browsable
#   architecture control plane -- from real repo state, no hand-maintained markdown.
# Uses: bash, node (invokes liquibase-schema-to-json.js always, and
#   .claude/nav/scripts/md-to-decisions-json.js only when --with-adr-details is passed, as
#   subprocesses), python3 (only when --with-sonar/--with-archunit are passed).
# Input: pom.xml, real Java source + Javadoc, Liquibase changelogs, every module's DECISIONS.md,
#   .claude/nav/adr-index.md, .claude/nav/flows.md, .claude/commands, .claude/skills, .claude/agents,
#   backlog/.
# Output: docs/architecture/data/architecture-model.json + docs/architecture/architecture-map.html +
#   docs/architecture/data/arch-embed-index.md.
#
# Generates architecture-model.json (Track A of the architecture control plane) from
# already-structured, non-code sources only -- no ArchUnit, no bytecode analysis. Node types:
# MODULE (from pom.xml reactor + per-module pom.xml dependencies), COMMAND/SKILL (from
# .claude/commands, .claude/skills, cross-checked against .claude/nav/flows.md), AGENT (from
# .claude/agents), and one BACKLOG summary node. Per-ADR/per-issue graph nodes are deliberately not built -- the issue/ADR count
# would blow past a "tens of nodes, not thousands" budget -- ADRs are folded into each module's
# own `intent[]` list instead, reusing adr-index.md rather than reparsing every DECISIONS.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OUTPUT="$REPO_ROOT/docs/architecture/data/architecture-model.json"
HTML_OUTPUT="$REPO_ROOT/docs/architecture/architecture-map.html"
ARCH_EMBED_INDEX="$REPO_ROOT/docs/architecture/data/arch-embed-index.md"
ADR_INDEX="$REPO_ROOT/.claude/nav/adr-index.md"
FLOWS="$REPO_ROOT/.claude/nav/flows.md"
ROOT_CLAUDE_MD="$REPO_ROOT/CLAUDE.md"

# ── Node.js runner: host `node` if available, else a disposable node:22-alpine container --------
# same "use it if the host has it, else containerize it" self-healing pattern this repo already
# uses for Java/Playwright/SonarScanner (build-and-test/playwright/sonar all run their runtime
# inside a container rather than requiring it on the host) -- a host with Docker Desktop + WSL but
# no host-level node install is a real, confirmed case, not hypothetical.
if command -v node >/dev/null 2>&1; then
  NODE_MODE=host
else
  NODE_MODE=docker
  NODE_IMAGE="node:22-alpine"
  docker image inspect "$NODE_IMAGE" >/dev/null 2>&1 || docker pull -q "$NODE_IMAGE" >/dev/null
fi
run_node() {
  if [ "$NODE_MODE" = host ]; then
    node "$@"
  else
    docker run --rm -i -v "$REPO_ROOT:$REPO_ROOT" -w "$REPO_ROOT" "$NODE_IMAGE" node "$@"
  fi
}

# Opt-in Sonar/ArchUnit/CI-metrics/ADR-details fetch -- all off by default so a plain run never
# triggers a SonarQube rescan, depends on build-and-test.sh --archunit-metrics having run
# recently, depends on a Dagu CI run having run recently, or bakes every module's full ADR text
# into the output. See docs/architecture/scripts/DECISIONS.md.
WITH_SONAR=""
WITH_ARCHUNIT=""
WITH_CI_METRICS=""
WITH_ADR_DETAILS=""
for arg in "$@"; do
  case "$arg" in
    --with-sonar) WITH_SONAR=1 ;;
    --with-archunit) WITH_ARCHUNIT=1 ;;
    --with-ci-metrics) WITH_CI_METRICS=1 ;;
    --with-adr-details) WITH_ADR_DETAILS=1 ;;
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
# double-clicked -- see .claude/nav/adr-index.md. Parsing lives in
# .claude/nav/scripts/md-to-decisions-json.js (Node) -- an earlier awk version hit two real bugs on real content (label+list with no blank
# line merging into one paragraph; multi-line list items losing their numbering) that
# JSON.stringify()'s correct-by-construction escaping and normal regex/string methods avoid.
FULL_DECISIONS_MODULES=(attachment-spring-boot-starter audit-spring-boot-starter integration-tests marketplace-app platform-commons query-lib taxon-spring-boot-starter scripts docs/architecture/scripts scripts/ci scripts/sonar playwright)

# ── Non-Maven tooling directories -- get a SCRIPT_GROUP node (same ADR-embedding/popup mechanism
# as MODULE nodes) so their files/decisions are visible on the Tooling & Pipelines screen, not
# invisible outside the interactive tool. "category" is the group heading each dir's card renders
# under. Not every SCRIPT_GROUP dir has its own DECISIONS.md -- .claude/nav/scripts's own history moved
# to docs/architecture/scripts/DECISIONS.md wholesale (see docs/architecture/scripts/DECISIONS.md
# ADR-021), so .claude/nav/scripts now gets a files-only node, no ADR/decisions section
# (decisions_json_for/adr_intent_for_module both degrade to empty for a module with no
# DECISIONS.md, not a special case here). docs/architecture/scripts stays a single-level flat card
# here -- scripts, playwright, and .claude are each the root of their own arbitrary-depth
# SCRIPT_GROUP tree instead (SCRIPT_TREE_ROOTS below, .claude/nav/scripts reached as one of its
# nested children), so they're deliberately not listed in SCRIPT_GROUP_DIRS/SCRIPT_GROUP_CATEGORY.
declare -A SCRIPT_GROUP_CATEGORY=(
  [docs/architecture]="Build architecture page"
  [docs/architecture/scripts]="Build architecture page"
  [docs/architecture/data]="Build architecture page"
)
SCRIPT_GROUP_DIRS=(docs/architecture docs/architecture/scripts docs/architecture/data)

# Top-level roots of the unified, arbitrary-depth drill-down tree (see emit_script_tree_node()
# below) -- each root carries its own display category, so the same recursive mechanism feeds both
# the "Scripts" card (scripts/, playwright/) and the "AI Tooling" card (.claude/) on the Tooling &
# Pipelines screen. playwright/ is a physically separate top-level directory, not nested under
# scripts/, but is treated as one child card of the root "Scripts" card
# (docs/architecture/scripts/DECISIONS.md).
declare -A SCRIPT_TREE_ROOT_CATEGORY=(
  [scripts]="Scripts"
  [playwright]="Scripts"
  [.claude]="AI Tooling"
)
SCRIPT_TREE_ROOTS=(scripts playwright .claude)

# Directories that never become their own card/tree node even though they sit inside a
# SCRIPT_TREE_ROOTS subtree -- generated/report output, never source. "wiki" is Dagu's own
# runtime-created documentation folder (see `dagu config`'s "Wiki directory"), written directly
# into whatever directory Dagu was started with `--dags` pointed at -- not part of the real source
# tree, confirmed by its absence outside a running ci-runner container.
SCRIPT_TREE_EXCLUDE_DIRS=(reports pw-report report logs node_modules wiki)

# Directories whose real subdirectories are never recursed into, even though they exist on disk --
# .claude/skills/<name>/ is never a folder-card of its own: each skill's own SKILL.md is already
# that unit's complete, self-contained description (see .claude/skills/infra-readme-standards's
# own ".claude/skills/" section), so drilling into one would show an empty card with no headers and
# no README. The .claude/skills card itself still renders normally (its own top-level README.md).
SCRIPT_TREE_LEAF_DIRS=(.claude/skills)

# Explicit "what matters first" ordering per directory -- entry points and generators before the
# CI gates that verify their output, dev-only tooling last. Falls back to alphabetical (find |
# sort) for any directory not listed here. Applies uniformly to SCRIPT_GROUP_DIRS's flat dirs and
# to every level of the SCRIPT_TREE_ROOTS tree (looked up by the node's own directory path).
declare -A SCRIPT_GROUP_FILE_ORDER=(
  [.claude/nav/scripts]="generate-adr-index.sh check-adr-index-freshness.sh check-hardcoded-counts.sh check-flows-completeness.sh md-to-decisions-json.js"
  [docs/architecture]="architecture-doc.sh architecture-doc.bat"
  [docs/architecture/scripts]="generate-architecture-model.sh liquibase-schema-to-json.js check-architecture-model-freshness.sh screenshot-architecture-map.sh Dockerfile"
  [docs/architecture/data]="architecture-model.json arch-embed-index.md runtime-notes.md"
  [scripts]="build-and-test.sh deploy-and-run.sh ci.sh run-all-tests.sh playwright.sh sonar.sh reset.sh run-local.bat build-and-test.bat deploy-and-run.bat ci.bat run-all-tests.bat playwright.bat sonar.bat claude.bat clean.bat collect-code.bat"
  [scripts/sonar]="run.sh run.bat docker-compose.sonar.yml sonar-project.properties"
  [scripts/build-and-test]="run.sh build.sh build-and-test.properties Dockerfile"
  [scripts/deploy-and-run]="run.sh reset.sh Dockerfile docker-compose.db.yml docker-compose.minio.yml docker-compose.app.yml"
  [scripts/run-all-tests]="run.sh"
  [scripts/ci]="run.sh Dockerfile docker-entrypoint.sh watch-run.py"
  [scripts/ci/dagu]="ci.yaml pipeline-metrics.py"
  [playwright/e2e]="_helpers.js 01-marketplace-empty-flow.spec.js 02-marketplace-authentication-flow.spec.js 03-marketplace-promotion-flow.spec.js 04-marketplace-advertisement-flow.spec.js 05-seed-filter-sort-pagination.spec.js 06-marketplace-delete-flow.spec.js 07-accessibility.spec.js"
  [playwright/e2e/_flows]="auth.flow.js signup.flow.js advertisement.flow.js advertisement-filter.flow.js filter.flow.js category.flow.js city.flow.js attachment.flow.js audit.flow.js timeline.flow.js entity-activity.flow.js delete.flow.js user-management.flow.js settings.flow.js language-switch.flow.js seed.flow.js"
)
decisions_json_for() {
  local module="$1"
  # Off by default (see --with-adr-details above) -- full ADR-body embedding accounts for
  # ~605KB/~72% of a with-flag architecture-model.json, baked in whether or not anyone opens the
  # popup. MODEL.allAdrs (built by all_adrs_json() from .claude/nav/adr-index.md) stays populated
  # either way -- it never carried full body text to begin with. openAdrPopupForAdr() still opens
  # the popup (id/title/status always available from MODEL.allAdrs) when a module's "decisions"
  # field is null, showing a source-file link instead of the full body.
  [ -n "$WITH_ADR_DETAILS" ] || { echo "null"; return; }
  [ -f "$REPO_ROOT/$module/DECISIONS.md" ] || { echo "null"; return; }
  local found=false
  for m in "${FULL_DECISIONS_MODULES[@]}"; do [ "$m" = "$module" ] && found=true; done
  $found || { echo "null"; return; }
  run_node "$REPO_ROOT/.claude/nav/scripts/md-to-decisions-json.js" --stdout "$module"
}

# A SCRIPT_GROUP dir's own README.md (if it has one), read raw via Node's JSON.stringify --
# json_escape() strips newlines, unsuitable for multi-paragraph content. Always embedded (unlike
# decisions_json_for(), which is opt-in behind --with-adr-details) -- a README is orders of
# magnitude smaller than a full ADR history.
readme_json_for() {
  local dir="$1"
  [ -f "$REPO_ROOT/$dir/README.md" ] || { echo "null"; return; }
  run_node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(JSON.stringify(d)))' < "$REPO_ROOT/$dir/README.md"
}

# A root-level markdown file (README.md/INFRASTRUCTURE.md), read raw the same way readme_json_for()
# reads a SCRIPT_GROUP dir's own README.md -- these two aren't SCRIPT_GROUP-scoped, so they need
# their own path (repo root, not a dir/README.md join).
root_md_json_for() {
  local file="$1"
  [ -f "$REPO_ROOT/$file" ] || { echo "null"; return; }
  run_node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(JSON.stringify(d)))' < "$REPO_ROOT/$file"
}

# ── Module list, in pom.xml reactor order ───────────────────────────────────────────────────
mapfile -t MODULES < <(sed -n '/<modules>/,/<\/modules>/p' "$REPO_ROOT/pom.xml" \
  | grep -o '<module>[^<]*</module>' | sed 's/<module>\(.*\)<\/module>/\1/')

# ── Domain <-> module mapping, derived live from each module's own pom.xml
# <properties><architecture.boundedContext> declaration -- self-describing, not a hardcoded list of
# module names here. A module with no such property (query-lib, integration-tests) is simply not a
# bounded context and gets no box on the diagram; this is how a brand-new module type (e.g.
# marketplace-orchestrator's "orchestrator" kind) becomes visible on this diagram automatically,
# from the same commit that adds the module, with no separate edit to this script required.
# See docs/architecture/scripts/DECISIONS.md ADR-019 "Open goals" (superseded by this ADR entry) and the
# new ADR recorded alongside this change.
declare -A BC_DOMAIN_MODULE=() BC_DOMAIN_LABEL=() BC_DOMAIN_KIND=()
BC_DOMAIN_ORDER=() BC_DOMAIN_ORDER_STARTERS=()
bc_shared_mod="" bc_ui_mod="" bc_orch_mod=""
for bc_mod in "${MODULES[@]}"; do
  bc_kind="$(sed -n '/<properties>/,/<\/properties>/p' "$REPO_ROOT/$bc_mod/pom.xml" 2>/dev/null \
    | grep -o '<architecture.boundedContext>[^<]*</architecture.boundedContext>' \
    | sed 's/<architecture.boundedContext>\(.*\)<\/architecture.boundedContext>/\1/')" || true
  [ -n "$bc_kind" ] || continue
  case "$bc_kind" in
    shared) bc_shared_mod="$bc_mod" ;;
    ui) bc_ui_mod="$bc_mod" ;;
    orchestrator) bc_orch_mod="$bc_mod" ;;
    starter)
      bc_word="${bc_mod%-spring-boot-starter}"
      bc_id="" bc_label_words=()
      IFS='-' read -ra bc_parts <<< "$bc_word"
      for bc_part in "${bc_parts[@]}"; do
        bc_id="$bc_id${bc_part^}"
        bc_label_words+=("${bc_part^}")
      done
      BC_DOMAIN_MODULE["$bc_id"]="$bc_mod"
      BC_DOMAIN_LABEL["$bc_id"]="$(IFS=' '; echo "${bc_label_words[*]}") Domain"
      BC_DOMAIN_KIND["$bc_id"]="starter"
      BC_DOMAIN_ORDER+=("$bc_id")
      BC_DOMAIN_ORDER_STARTERS+=("$bc_id")
      ;;
  esac
done
if [ -n "$bc_shared_mod" ]; then
  BC_DOMAIN_MODULE[Shared]="$bc_shared_mod"; BC_DOMAIN_LABEL[Shared]="Shared Kernel"
  BC_DOMAIN_KIND[Shared]="shared"; BC_DOMAIN_ORDER=(Shared "${BC_DOMAIN_ORDER[@]}")
fi
if [ -n "$bc_orch_mod" ]; then
  BC_DOMAIN_MODULE[Orchestrator]="$bc_orch_mod"; BC_DOMAIN_LABEL[Orchestrator]="Application/BFF Layer"
  BC_DOMAIN_KIND[Orchestrator]="orchestrator"; BC_DOMAIN_ORDER+=(Orchestrator)
fi
if [ -n "$bc_ui_mod" ]; then
  BC_DOMAIN_MODULE[UI]="$bc_ui_mod"; BC_DOMAIN_LABEL[UI]="UI/Application Layer"
  BC_DOMAIN_KIND[UI]="ui"; BC_DOMAIN_ORDER+=(UI)
fi
# EntityType enum value -> the bounded-context domain it belongs to (platform-commons/core/model/EntityType.java).
declare -A BC_ENTITY_TYPE_DOMAIN=(
  [ADVERTISEMENT]=Advertisement [USER]=User [USER_SETTINGS]=User [TAXON]=Taxon [PROVIDER_PROFILE]=ProviderProfile
)
# What actually crosses each relationship, keyed by the relationship label -- checked directly
# against the real Port method signatures, not guessed (AuditPort.capture*() all take
# AuditableSnapshot; AttachmentPort.getMediaSummaries() returns AttachmentMediaSummaryDto;
# TaxonPort.replaceAssignments() takes a Set<Long> of taxon ids). "calls back via Hook
# implementations" has no single fixed payload -- see BC_HOOK_PAYLOAD below, which is looked up
# per real Hook interface instead; the text here is only the fallback for the rare case a
# hook-callback edge is found with no matching BC_HOOK_PAYLOAD entry (should not happen in
# practice -- every *Hook.java under platform-commons/marketplace-orchestrator's spi/ packages has
# one).
declare -A BC_LABEL_PAYLOAD=(
  ["decouples"]="Compile-time dependency only -- no runtime payload"
  ["audited via"]="AuditableSnapshot (AuditPort.captureCreation/Update/Deletion/Restore)"
  ["can have"]="AttachmentMediaSummaryDto (AttachmentPort.getMediaSummaries)"
  ["category assignment via"]="Set<Long> of taxon ids (TaxonPort.replaceAssignments)"
  ["calls"]="Whatever DTO that domain's own Port methods return -- varies per call, see SPI Map for the real per-method types"
  ["calls back via Hook implementations"]="Varies by which Hook interface backs this edge -- see BC_HOOK_PAYLOAD"
)

# Real per-method return/parameter types for every *Hook interface that can produce a "calls back
# via Hook implementations" edge -- checked directly against each interface's own method
# signatures (see the interfaces themselves under platform-commons/*/spi and
# marketplace-orchestrator/spi), not a single reused text across all 4 hook-callback edges (that
# was the bug: the old generic BC_LABEL_PAYLOAD entry above cited AuditActivityFieldsHook, an
# interface removed from the codebase entirely, and was wrong for 3 of the 4 real edges since they
# each carry a completely different payload). Keyed by simple interface name so the hook-callback
# loop below can look it up per real `hook_iface` it discovers.
declare -A BC_HOOK_PAYLOAD=(
  [AuditDomainHook]="Map<Long,String> (resolveNames), Set<Long> (findExisting), String (resolveDisplayName), Optional<AuditSnapshotContentDto<T>> (castIfKnown)"
  [AuditActivityEnrichHook]="List<AuditTimelineItemDto<T>> (merge), List<AuditActivityItemDto<T>> (enrichActivity), String media state (getMediaStateForSnapshot)"
  [CurrentActorHook]="Optional<Long> (getCurrentActorId)"
  [UiLabelHook]="String (translateActorDeletedSuffix)"
  [SessionActorHook]="Optional<Long> (getCurrentActorId)"
)

# ── Bounded Contexts category split -- the same 16 relationships render with the same arrow+label
# visual regardless of how different their real nature is (a genuine BFF call vs. a reverse Hook
# callback vs. a documented starter-to-starter exception vs. a derived, not-a-call fact), which was
# the actual source of user confusion ("чому стартери зі стартерами, аудіт з юай, а ми ж казали БФФ").
# One diagram tab per category (mirrors SPI_SUBSYSTEM_ORDER/SPI_SUBSYSTEM_LABEL's per-subsystem
# split above for SPI Map). BC_LABEL_CATEGORY maps every existing BC_LABEL_PAYLOAD key except
# "decouples" (never drawn -- see add_rel's Shared loop) to one of the 4 categories below.
declare -a BC_CATEGORY_ORDER=(orchestration hooks exceptions derived)
declare -A BC_CATEGORY_LABEL=(
  [orchestration]="Service Calls (BFF)"
  [hooks]="Hook Callbacks"
  [exceptions]="Cross-Starter Exceptions"
  [derived]="Derived Facts"
)
declare -A BC_CATEGORY_DESC=(
  [orchestration]="Forward-direction real Port calls -- Orchestrator composing each domain, UI calling Orchestrator, and the one documented UI exception (AccessEvaluator -> UserAuthorizationPort). The BFF pattern working as intended."
  [hooks]="Reverse-direction calls -- a starter or Orchestrator calls a *Hook interface it depends on; marketplace-app/marketplace-orchestrator supplies the real implementation. Dependency inversion, not orchestration -- does not violate the BFF principle."
  [exceptions]="A real starter-to-starter Port call that bypasses the orchestrator -- documented technical debt, not the intended pattern."
  [derived]="Not code calls at all -- classification/composition facts derived from data (which EntityTypes get audited, which domains can carry attachments)."
)
declare -A BC_LABEL_CATEGORY=(
  ["calls"]="orchestration"
  ["calls back via Hook implementations"]="hooks"
  ["category assignment via"]="exceptions"
  ["audited via"]="derived"
  ["can have"]="derived"
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
# Orchestrator calls Ports via ComponentFactory<XPort> rather than implementing them -- the
# generic "implements XPort" grep above finds nothing for it, so override with the real signal:
# which *Port types it actually injects.
if [ -n "$bc_orch_mod" ]; then
  MODULE_CONTRACT["$bc_orch_mod"]="$( (find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java' | sort | while read -r ifile; do
    iface="$(basename "$ifile" .java)"
    grep -qlP "ComponentFactory<\s*${iface}\s*>|\b${iface}\s+\w+\s*;" -r --include='*.java' "$REPO_ROOT/$bc_orch_mod/src/main/java" 2>/dev/null && echo "$iface"
    true
  done) || true)"$'\n'
fi
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
      /^\| ADR-/ && $2 == m { sub(/^\| /, "", $1); sub(/ *\|$/, "", $4); print $1 "\x1f" $4 }
    ' "$ADR_INDEX"
  fi
}

# Flat, deduplicated list of every ADR across every module's DECISIONS.md, for the System screen's
# ADRs card/list -- reads .claude/nav/adr-index.md (same file adr_intent_for_module reads above), but
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
# docs/architecture/scripts/DECISIONS.md ADR-003/005). Its diagramGroups entry is synthesized directly below,
# with an empty "source" (nothing reads it -- the special-cased renderer never parses Mermaid text
# for this group) purely so the Diagrams list page has a card to link from.
# All four diagrams (Module Dependencies/SPI Map/Database ERD/Bounded Contexts) render live --
# no markdown source is parsed for the Diagrams list anymore.
# ── SPI Map subsystem order/labels -- declared before diagram_groups_json below since the SPI Map
# group now has one diagram per subsystem (7 tabs instead of one 71-node canvas -- user reported
# the single flat graph was too cluttered to read). Reused again inside spi_map_json() further
# down instead of a second, separately-maintained local copy.
declare -a SPI_SUBSYSTEM_ORDER=(audit attachment user advertisement taxon providerprofile core)
declare -A SPI_SUBSYSTEM_LABEL=(
  [audit]="Audit Subsystem"
  [attachment]="Attachment Subsystem"
  [user]="User Subsystem"
  [advertisement]="Advertisement Subsystem"
  [taxon]="Taxon (Reference Data) Subsystem"
  [providerprofile]="Provider Profile Subsystem"
  [core]="Core / Platform"
)

spi_map_diagrams_json() {
  local out="" first=true s
  for s in "${SPI_SUBSYSTEM_ORDER[@]}"; do
    $first || out="$out,"
    first=false
    out="$out{\"title\": \"$(json_escape "${SPI_SUBSYSTEM_LABEL[$s]}")\", \"source\": \"\", \"subsystem\": \"$(json_escape "$s")\"}"
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
diagram_groups_json="  {\"key\": \"bounded-contexts\", \"label\": \"Bounded Contexts\", \"file\": \"real code (live)\", \"description\": \"Which domain actually calls which other domain in real code, and why -- entities/services/tables/ports per domain, relationships from real Hook/Port usage signals. Split one tab per relationship nature (BFF service calls, reverse Hook callbacks, cross-starter exceptions, derived facts) -- the single combined graph mixed 4 fundamentally different kinds of relationship in one arrow+label visual.\", \"diagrams\": [$(bounded_contexts_diagrams_json)]},"$'\n'"  {\"key\": \"02-spi-map\", \"label\": \"SPI Map\", \"file\": \"platform-commons/src (live)\", \"description\": \"Every cross-module Port/Hook interface in platform-commons, who really calls it, and who really implements it -- three real-code facts, not a build-graph fact. Split one tab per subsystem -- the single combined graph got too dense to read.\", \"diagrams\": [$(spi_map_diagrams_json)]},"$'\n'"  {\"key\": \"01-module-dependencies\", \"label\": \"Module Dependencies\", \"file\": \"pom.xml (live)\", \"description\": \"Which module's JAR ends up on which other module's classpath, per real pom.xml <dependency> declarations -- a Maven build-graph fact, not a real-code-call fact (a module can depend on a JAR nothing in its code calls yet).\", \"diagrams\": [{\"title\": \"Dependency Graph\", \"source\": \"\"}]},"$'\n'"  {\"key\": \"04-database-erd\", \"label\": \"Database ERD\", \"file\": \"Liquibase changelogs (live)\", \"description\": \"Every table and column this app persists, with the business-meaning remarks pulled live from each Liquibase changelog.\", \"diagrams\": [{\"title\": \"Entity Relationship Diagram\", \"source\": \"\"}]}"

# ── SPI Map: mechanically extracted from real Java source, same "live from real source, not a
# separately-maintained .md" pattern as Module Dependencies (01) -- every *.spi interface under
# platform-commons + every real `implements` of it across the starters/marketplace-app, via grep
# (text-pattern matching, not full semantic/bytecode analysis -- same bar as module_deps()).
# "Purpose" one-liners are the one genuinely-editorial part with no mechanical source, carried over
# from the retired docs/architecture/02-spi-map.md as a static lookup, same exception Module
# Dependencies' Key Observations already established.
# Subsystem-level editorial notes carried over verbatim -- explain a non-obvious absence (Attachment
# has no starter->marketplace media-change callback) or a design rationale (User's 4-port split) that
# the mechanical per-interface extraction has no way to produce on its own.
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

    # Real, bytecode-derived edges (ArchitectureMetricsExport.java's spiEdges -- improvement-156)
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
          impls_json="$impls_json{\"class\": \"$(json_escape "$impl")\", \"module\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "$file_path")\"}"
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
          callers_json="$callers_json{\"class\": \"$(json_escape "$caller")\", \"module\": \"$(json_escape "$module_c")\", \"file\": \"$(json_escape "$file_path")\", \"calls\": ${calls_json:-[]}}"
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
          impls_json="$impls_json{\"class\": \"$(json_escape "$impl")\", \"module\": \"$(json_escape "$module")\", \"file\": \"$(json_escape "${candidate_file#"$REPO_ROOT"/}")\"}"
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
          callers_json="$callers_json{\"class\": \"$(json_escape "$caller")\", \"module\": \"$(json_escape "$module_c")\", \"file\": \"$(json_escape "${candidate_file#"$REPO_ROOT"/}")\"}"
        fi
      done < <(grep -rlP "${IMPL_PATTERN}|${CALLER_PATTERN}" \
          "$REPO_ROOT"/*-spring-boot-starter/src/main/java "$REPO_ROOT"/marketplace-app/src/main/java \
          "$REPO_ROOT"/marketplace-orchestrator/src/main/java 2>/dev/null | sort -u)
    fi

    $first_detail || details="$details,"$'\n'
    first_detail=false
    details="$details    {\"interface\": \"$(json_escape "$iface")\", \"file\": \"$(json_escape "${iface_file#"$REPO_ROOT"/}")\", \"package\": \"$(json_escape "$pkg")\", \"subsystem\": \"$(json_escape "$subsystem")\", \"kind\": \"$(json_escape "$kind")\", \"purpose\": \"$(json_escape "$(spi_javadoc_purpose_for "$iface_file")")\", \"methodCount\": ${method_count:-null}, \"usedCount\": ${used_count:-null}, \"allMethods\": ${all_methods_json:-null}, \"implementations\": [$impls_json], \"callers\": [$callers_json]}"
  done
  local labels_json="" notes_json="" first_s=true
  for s in "${SPI_SUBSYSTEM_ORDER[@]}"; do
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
  echo "  \"subsystemOrder\": $(json_str_array "$(printf '%s\n' "${SPI_SUBSYSTEM_ORDER[@]}")"),"
  echo "  \"subsystemLabels\": {$labels_json},"
  echo "  \"subsystemNotes\": {$notes_json}"
  echo "}"
}

# ── Database ERD: live from the real Liquibase changelogs (table/column/type/constraints/FKs/
# indexes/remarks -- single source of truth, see root CLAUDE.md's "Database Changes" guideline and
# the sibling SPI Javadoc convention above). What's NOT mechanically derivable: cross-table
# relationships with no real SQL-level FK (this codebase deliberately decouples actor-reference
# columns -- advertisement.created_by, audit_log.actor_id, provider_profile.city_taxon_id, etc. --
# see marketplace-app/DECISIONS.md ADR-034/ADR-035). Those stay a hand-preserved list, carried over
# verbatim from the retired 04-database-erd.md's ER diagram. Real FKs (taxon_translation/taxon_assignment -> taxon,
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
  tables_json="$(run_node "$REPO_ROOT/docs/architecture/scripts/liquibase-schema-to-json.js" "$REPO_ROOT" "${files[@]}")"
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
# run" instead).
ARCHUNIT_METRICS_FILE="$REPO_ROOT/marketplace-app/target/architecture-metrics.json"
ARCHUNIT_METRICS_FILE_FALLBACK="$REPO_ROOT/scripts/build-and-test/reports/architecture-metrics.json"

archunit_metrics_json() {
  if [ -f "$ARCHUNIT_METRICS_FILE" ]; then
    cat "$ARCHUNIT_METRICS_FILE"
  elif [ -f "$ARCHUNIT_METRICS_FILE_FALLBACK" ]; then
    cat "$ARCHUNIT_METRICS_FILE_FALLBACK"
  else
    echo "null"
  fi
}

# ── CI pipeline run metrics: each step's status/duration from the last `bash scripts/ci.sh` run,
# written by the `pipeline_metrics` step in scripts/ci/dagu/ci.yaml (queries Dagu's own API for
# its own just-finished run, `docker cp`s the result onto the host). Read here if present; null if
# no Dagu-backed CI run has ever finished -- same passive, no-auto-trigger shape as
# archunit_metrics_json() above, and this generator never talks to Dagu directly either way.
CI_PIPELINE_METRICS_FILE="$REPO_ROOT/scripts/ci/reports/pipeline-metrics.json"

ci_pipeline_metrics_json() {
  if [ -f "$CI_PIPELINE_METRICS_FILE" ]; then
    cat "$CI_PIPELINE_METRICS_FILE"
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
  done < <(grep -rl '@RequiredArgsConstructor' "$REPO_ROOT" --include='*.java' 2>/dev/null | grep -v '/target/' | grep '/src/main/java/' | sort)

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

# ── Self-documenting tooling: extracts each file's own "Description:"/"Usage:"/"Uses:"/"Input:"/
# "Output:" header comment fields (see .claude/skills/doc-standards/SKILL.md "Script header
# convention") -- single source of truth right next to the code, feeds each SCRIPT_GROUP node's own
# Tooling & Pipelines card instead of hand-written prose that can silently drift from what the
# files actually do. Takes the same file list already computed for that node (never re-globs
# separately, so "files shown" and "headers parsed" can't drift from each other) -- a file whose
# comment style this can't parse (#, // only) or that simply has no header yet yields no entry, not
# an error, so a new file with the convention shows up with no generator edit needed.
script_headers_json() {
  local dir="$1" files="$2"
  python3 -c "
import json, os, re, sys

repo_root = sys.argv[1]
dir_rel = sys.argv[2]
files = [f for f in sys.argv[3].splitlines() if f]

def extract(path):
    with open(path) as fh:
        lines = fh.readlines()
    text = []
    for l in lines:
        l = l.rstrip('\n')
        if l.strip().lower() == '@echo off':
            continue  # .bat's structural equivalent of a shebang -- always first, never the header
        if l.startswith('#'):
            text.append(l[1:].lstrip())
        elif l.startswith('//'):
            text.append(l[2:].lstrip())
        elif re.match(r'^REM(\s|$)', l, re.I):
            text.append(l[3:].lstrip())
        elif l.startswith('/*'):
            # JS-style block-comment open line, e.g. /* -- Header -- ... . Strip the leading /*
            # only -- a trailing */ (a one-line block comment) is stripped the same way the
            # continuation-line branch below strips it off the closing delimiter line.
            rest = l[2:].lstrip()
            if rest.rstrip().endswith('*/'):
                rest = rest.rstrip()[:-2].rstrip()
            text.append(rest)
        elif re.match(r'^\s*\*\s?', l):
            # JS-style block-comment continuation line, e.g. a Description: line or the closing
            # dash-delimiter line ending in */ -- strip the leading * prefix, then a trailing */
            # if this is that closing line.
            rest = re.sub(r'^\s*\*\s?', '', l)
            if rest.rstrip().endswith('*/'):
                rest = rest.rstrip()[:-2].rstrip()
            text.append(rest)
        else:
            break
    fields = {'Description': '', 'Usage': '', 'Uses': '', 'Env': '', 'Input': '', 'Outputs': '', 'Returns': ''}
    current = None
    for l in text:
        m = re.match(r'^(Description|Usage|Uses|Env|Input|Outputs|Returns):\s*(.*)', l)
        if m:
            current = m.group(1)
            fields[current] = m.group(2)
        elif current and (l.strip() == '' or re.match(r'^[─-]+$', l.strip())):
            break  # a blank line or the closing #-delimiter line ends the whole header block --
                   # don't keep scanning for a stray field-name match further down in unrelated
                   # prose (a real bug this once hit), and don't swallow the delimiter itself into
                   # the last field's value as a continuation line (another real bug this once hit)
        elif current:
            fields[current] += '\n' + l.strip()
    return fields

out = []
for f in files:
    fields = extract(os.path.join(repo_root, dir_rel, f))
    if not fields['Description']:
        continue
    out.append({
        'file': dir_rel + '/' + f,
        'description': fields['Description'],
        'usage': fields['Usage'],
        'uses': fields['Uses'],
        'env': fields['Env'],
        'input': fields['Input'],
        'outputs': fields['Outputs'],
        'returns': fields['Returns'],
    })
print(json.dumps(out))
" "$REPO_ROOT" "$dir" "$files"
}

# Emits one SCRIPT_GROUP-shaped JSON object for directory "$1" (relative to REPO_ROOT), recursing
# into every real child subdirectory (skipping SCRIPT_TREE_EXCLUDE_DIRS and hidden dirs) as a
# "children" array of the same shape -- an arbitrary-depth drill-down tree, not a fixed number of
# levels. "$2" is the display category the top-level root itself carries (nested children carry no
# category -- only a top-level SCRIPT_GROUP node needs one, to be found by PIPELINE_GROUPS'
# category filter; a nested node is only ever reached by walking a parent's "children" array, never
# looked up by category). Classification of "is this a folder-card or a file" is purely "is it a
# real subdirectory" -- never a naming convention or whether a same-named file also exists (a real
# counterexample: architecture-doc.sh has no architecture-doc/ subfolder at all).
emit_script_tree_node() {
  local d="$1" category="${2:-}"
  local desc=""
  [ -f "$REPO_ROOT/.claude/rules/$d.md" ] && desc="$(sed -n '5{s/^#* *//;p}' "$REPO_ROOT/.claude/rules/$d.md")"

  local files_list=""
  if [ -n "${SCRIPT_GROUP_FILE_ORDER[$d]:-}" ]; then
    local f
    for f in ${SCRIPT_GROUP_FILE_ORDER[$d]}; do
      [ -f "$REPO_ROOT/$d/$f" ] && files_list="$files_list$f"$'\n'
    done
  else
    # *.md included so a dir with only markdown content (e.g. .claude/commands) isn't an empty
    # card -- README.md excluded since it renders separately as its own readme block below, never
    # duplicated as a plain chip.
    files_list="$(find "$REPO_ROOT/$d" -maxdepth 1 \( -name '*.sh' -o -name '*.js' -o -name '*.bat' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' -o -name '*.md' -o -name 'Dockerfile' \) -printf '%f\n' 2>/dev/null | grep -v '^README\.md$' | sort)"
  fi
  local files_json decisions_field readme_field headers_field evidence_file
  files_json="$(json_str_array "$files_list")"
  decisions_field="$(decisions_json_for "$d")"
  readme_field="$(readme_json_for "$d")"
  headers_field="$(script_headers_json "$d" "$files_list")"
  evidence_file="$d/DECISIONS.md"
  [ -f "$REPO_ROOT/$d/DECISIONS.md" ] || evidence_file="$d"

  # Immediate child directories only (one level -- recursion handles deeper levels), real
  # subdirectories, alphabetical, minus the excluded generated/report dirs and anything hidden.
  # Skipped entirely for a SCRIPT_TREE_LEAF_DIRS entry -- real subdirectories exist on disk but are
  # deliberately never surfaced as folder-cards (see SCRIPT_TREE_LEAF_DIRS's own comment).
  local child_dirs=() child is_leaf=false ld
  for ld in "${SCRIPT_TREE_LEAF_DIRS[@]}"; do [ "$d" = "$ld" ] && is_leaf=true; done
  if ! $is_leaf; then
    while IFS= read -r child; do
      [ -n "$child" ] && child_dirs+=("$child")
    done < <(find "$REPO_ROOT/$d" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort)
  fi

  local children_json="" first_child=true excluded
  for child in "${child_dirs[@]}"; do
    excluded=false
    for ex in "${SCRIPT_TREE_EXCLUDE_DIRS[@]}"; do [ "$child" = "$ex" ] && excluded=true; done
    [[ "$child" == .* ]] && excluded=true
    $excluded && continue
    $first_child || children_json="$children_json,"
    first_child=false
    children_json="$children_json$(emit_script_tree_node "$d/$child" "")"
  done

  echo "    {"
  echo "      \"id\": \"$(json_escape "$d")\","
  echo "      \"type\": \"SCRIPT_GROUP\","
  if [ -n "$category" ]; then echo "      \"category\": \"$(json_escape "$category")\","; fi
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
  echo "      \"children\": [$children_json],"
  echo "      \"edges\": {}"
  echo "    }"
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
    elif [ "$d" = "Orchestrator" ]; then
      entities_json="[]"; tables_json="[]"
      # *Service.java (services/, composition/lookup) and *HookImpl.java (spi/, Hook implementations
      # that only need domain-port access -- see marketplace-orchestrator/CLAUDE.md) are two
      # sibling packages under this one module; both are real "what this module owns" content.
      services_json="$(json_named_file_array "$(find "$REPO_ROOT/$mod/src/main/java" \( -name '*Service.java' -o -name '*HookImpl.java' \) 2>/dev/null | sort | while read -r f; do
        printf '%s\t%s\n' "$(basename "$f" .java)" "${f#"$REPO_ROOT"/}"
      done)")"
      # Orchestrator calls *Port interfaces via ComponentFactory<XPort> (never implements them) but
      # since the Hook relocation it does genuinely `implements` several *Hook interfaces too (the
      # six *HookImpl classes above) -- both real signals belong in this one "what Orchestrator
      # depends on/fulfills" ports list.
      ports_json="$(json_named_file_array "$(find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java' | sort | while read -r ifile; do
        iface="$(basename "$ifile" .java)"
        grep -qlP "ComponentFactory<\s*${iface}\s*>|\b${iface}\s+\w+\s*;|implements\s+.*\b${iface}\b" -r --include='*.java' "$REPO_ROOT/$mod/src/main/java" 2>/dev/null && printf '%s\t%s\n' "$iface" "${ifile#"$REPO_ROOT"/}"
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
  # rel_payload accumulates a per-edge payload override ($7, optional) -- used only by the
  # hook-callback loop below, which knows the real *Hook interface(s) behind each specific edge
  # and passes "InterfaceName: real types" per call. Falls back to BC_LABEL_PAYLOAD[label] (a
  # single generic text) at rel_json emission time for every other label, where one fixed payload
  # per label is actually accurate (all "audited via" edges really do carry an AuditableSnapshot,
  # etc.) -- only "calls back via Hook implementations" needs per-edge granularity, since that one
  # label covers several unrelated interfaces with genuinely different payload types.
  local -a rel_key=() rel_from=() rel_to=() rel_label=() rel_evidence=() rel_dashed=() rel_payload=()
  add_rel() {
    local key="$1|$2|$3" i found=""
    for i in "${!rel_key[@]}"; do
      if [ "${rel_key[$i]}" = "$key" ]; then found=$i; break; fi
    done
    if [ -n "$found" ]; then
      rel_evidence[$found]="${rel_evidence[$found]}; $5"
      if [ -n "${7:-}" ]; then
        case "${rel_payload[$found]}" in
          *"$7"*) ;; # this interface's payload fragment already recorded for this edge
          "") rel_payload[$found]="$7" ;;
          *) rel_payload[$found]="${rel_payload[$found]}; $7" ;;
        esac
      fi
    else
      rel_key+=("$key"); rel_from+=("$1"); rel_to+=("$2"); rel_label+=("$3"); rel_evidence+=("$5"); rel_dashed+=("$6")
      rel_payload+=("${7:-}")
    fi
  }

  # Shared decouples every domain -- real pom.xml compile-scope dependency (moduleNodes edges),
  # same data Module Dependencies (01) already computes.
  for d in "${BC_DOMAIN_ORDER_STARTERS[@]}"; do
    add_rel "Shared" "$d" "decouples" "extracted" "${BC_DOMAIN_MODULE[$d]}/pom.xml depends on platform-commons" "true"
  done
  if [ -n "$bc_orch_mod" ]; then
    add_rel "Shared" "Orchestrator" "decouples" "extracted" "$bc_orch_mod/pom.xml depends on platform-commons" "true"
    # Orchestrator -> starter: real evidence per starter -- which *Port interfaces
    # marketplace-orchestrator's own source actually injects via ComponentFactory<XPort>, matched
    # against which starter really implements each one.
    local pf p_iface p_evidence ui_orch_ev
    for pf in "$REPO_ROOT/platform-commons/src/main/java"/org/ost/platform/*/spi/*Port.java; do
      [ -f "$pf" ] || continue
      p_iface="$(basename "$pf" .java)"
      p_evidence="$(grep -rlP "ComponentFactory<\s*${p_iface}\s*>|\b${p_iface}\s+\w+\s*;" "$REPO_ROOT/$bc_orch_mod/src/main/java" --include='*.java' 2>/dev/null | sort | head -1)" || true
      [ -z "$p_evidence" ] && continue
      for d in "${BC_DOMAIN_ORDER_STARTERS[@]}"; do
        grep -qlP "implements\s+.*\b${p_iface}\b" -r --include='*.java' "$REPO_ROOT/${BC_DOMAIN_MODULE[$d]}/src/main/java" 2>/dev/null || continue
        add_rel "Orchestrator" "$d" "calls" "extracted" "$(sed "s|$REPO_ROOT/||" <<< "$p_evidence") injects ComponentFactory<$p_iface>" "false"
      done
    done
    # UI -> Orchestrator: real evidence -- marketplace-app importing any org.ost.orchestrator.*
    # class at all (services today; still correct once the flat org.ost.orchestrator.services
    # package from the pending package-flatten lands too, since this matches the whole subtree).
    ui_orch_ev="$(grep -rl "import org\.ost\.orchestrator\." "$REPO_ROOT/marketplace-app/src/main/java" --include='*.java' 2>/dev/null | wc -l | tr -d ' ')" || true
    if [ "${ui_orch_ev:-0}" -gt 0 ]; then
      add_rel "UI" "Orchestrator" "calls" "extracted" "$ui_orch_ev marketplace-app classes import org.ost.orchestrator.*" "false"
    fi
  fi

  # "audited via" -- real signal: AuditTimelineRowRenderer.LABELED_ENTITY_TYPES, the single
  # constant listing which EntityTypes have field-label support in the audit timeline UI (replaces
  # the old "implements AuditActivityFieldsHook" grep -- that interface and its four per-domain
  # implementations were removed entirely once every one of them converged to an identical
  # one-line delegation with zero domain-specific logic, see platform-commons/DECISIONS.md ADR-029's
  # third refinement).
  local audit_timeline_renderer="$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/AuditTimelineRowRenderer.java"
  local etype dom
  for etype in $(sed -n '/LABELED_ENTITY_TYPES/,/;/p' "$audit_timeline_renderer" 2>/dev/null | grep -oP 'EntityType\.\K\w+'); do
    dom="${BC_ENTITY_TYPE_DOMAIN[$etype]:-}"
    [ -z "$dom" ] && continue
    add_rel "$dom" "Audit" "audited via" "extracted" "AuditTimelineRowRenderer.LABELED_ENTITY_TYPES includes EntityType.$etype" "false"
  done

  # "can have" -- real signal: which *.java class implements AuditActivityEnrichHook (unaffected by
  # the removal above -- ActivityEnrichHookImpl still does real HTML-diff formatting, not a
  # mechanical delegation), and what EntityType its entityType() method declares.
  local hf
  while IFS= read -r hf; do
    [ -z "$hf" ] && continue
    etype="$(grep -A2 "entityType()" "$hf" | grep -oP 'EntityType\.\K\w+' | head -1)"
    [ -z "$etype" ] && continue
    dom="${BC_ENTITY_TYPE_DOMAIN[$etype]:-}"
    [ -z "$dom" ] && continue
    add_rel "$dom" "Attachment" "can have" "extracted" "$(basename "$hf" .java).entityType() = $etype" "false"
  done < <(grep -rl "implements AuditActivityEnrichHook" \
      "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/spi" \
      "$REPO_ROOT/marketplace-orchestrator/src/main/java/org/ost/orchestrator/spi" --include="*.java" 2>/dev/null)

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

  # UI -> starter: real evidence per starter, same shape as the Orchestrator -> starter loop above --
  # since the true-BFF migration, marketplace-app holds almost no direct *Port reference anymore
  # (everything routes through marketplace-orchestrator instead), so this must not stay unconditional.
  local ui_pf ui_p_iface ui_p_evidence
  for ui_pf in "$REPO_ROOT/platform-commons/src/main/java"/org/ost/platform/*/spi/*Port.java; do
    [ -f "$ui_pf" ] || continue
    ui_p_iface="$(basename "$ui_pf" .java)"
    ui_p_evidence="$(grep -rlP "ComponentFactory<\s*${ui_p_iface}\s*>|\b${ui_p_iface}\s+\w+\s*;" "$REPO_ROOT/marketplace-app/src/main/java" --include='*.java' 2>/dev/null | head -1)" || true
    [ -z "$ui_p_evidence" ] && continue
    for d in "${BC_DOMAIN_ORDER_STARTERS[@]}"; do
      grep -qlP "implements\s+.*\b${ui_p_iface}\b" -r --include='*.java' "$REPO_ROOT/${BC_DOMAIN_MODULE[$d]}/src/main/java" 2>/dev/null || continue
      add_rel "UI" "$d" "calls" "extracted" "$(sed "s|$REPO_ROOT/||" <<< "$ui_p_evidence") injects $ui_p_iface directly" "false"
    done
  done

  # *Hook implementations calling back into either UI or Orchestrator -- one edge per real
  # (caller-domain, implementor-domain) pair, not a blanket "Audit -> X" for every Hook
  # implementation found in those two folders. That blanket version was wrong: verified directly
  # (not assumed) that UiLabelHook/SessionActorHook's real caller is marketplace-orchestrator's own
  # Hook classes, not any starter -- a real "Orchestrator -> UI" fact, not "Audit -> UI" -- and that
  # CurrentActorHook has two real callers (audit-starter AND attachment-starter), not just audit.
  # Found by the user asking "why does Audit call UI" and the evidence not actually supporting it.
  local hif hook_iface impl_file impl_domain impl_name caller_file caller_dom cd
  while IFS= read -r hif; do
    [ -z "$hif" ] && continue
    hook_iface="$(basename "$hif" .java)"
    impl_file="$(grep -rlP "implements\s+.*\b${hook_iface}\b" \
        "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/spi" \
        "$REPO_ROOT/marketplace-orchestrator/src/main/java/org/ost/orchestrator/spi" --include='*.java' 2>/dev/null | sort | head -1)"
    [ -z "$impl_file" ] && continue
    impl_domain="UI"
    case "$impl_file" in "$REPO_ROOT/marketplace-orchestrator/"*) impl_domain="Orchestrator" ;; esac
    impl_name="$(basename "$impl_file" .java)"
    while IFS= read -r caller_file; do
      [ -z "$caller_file" ] && continue
      caller_dom=""
      for cd in "${BC_DOMAIN_ORDER_STARTERS[@]}"; do
        case "$caller_file" in "$REPO_ROOT/${BC_DOMAIN_MODULE[$cd]}/"*) caller_dom="$cd"; break ;; esac
      done
      if [ -z "$caller_dom" ]; then
        case "$caller_file" in "$REPO_ROOT/marketplace-orchestrator/"*) caller_dom="Orchestrator" ;; esac
      fi
      [ -z "$caller_dom" ] || [ "$caller_dom" = "$impl_domain" ] && continue
      add_rel "$caller_dom" "$impl_domain" "calls back via Hook implementations" "extracted" \
        "$impl_name implements $hook_iface, called from $(sed "s|$REPO_ROOT/||" <<< "$caller_file")" "false" \
        "$hook_iface: ${BC_HOOK_PAYLOAD[$hook_iface]:-real payload not catalogued -- see that interface method signatures directly}"
    done < <(grep -rlP "List<\s*${hook_iface}\s*>|\b${hook_iface}\s+\w+\s*;|ComponentFactory<\s*${hook_iface}\s*>" \
        "$REPO_ROOT"/*-spring-boot-starter/src/main/java "$REPO_ROOT/marketplace-orchestrator/src/main/java" --include='*.java' 2>/dev/null | sort -u)
  done < <(find "$REPO_ROOT/platform-commons/src/main/java" "$REPO_ROOT/marketplace-orchestrator/src/main/java" \
      -path '*/spi/*Hook.java' 2>/dev/null | sort)

  local rel_json="" first_r=true i rel_payload_value
  for i in "${!rel_key[@]}"; do
    $first_r || rel_json="$rel_json,"$'\n'
    first_r=false
    rel_payload_value="${rel_payload[$i]:-${BC_LABEL_PAYLOAD[${rel_label[$i]}]:-}}"
    rel_json="$rel_json    {\"from\": \"${rel_from[$i]}\", \"to\": \"${rel_to[$i]}\", \"label\": \"$(json_escape "${rel_label[$i]}")\", \"category\": \"$(json_escape "${BC_LABEL_CATEGORY[${rel_label[$i]}]:-}")\", \"confidence\": \"extracted\", \"evidence\": \"$(json_escape "${rel_evidence[$i]}")\", \"payload\": \"$(json_escape "$rel_payload_value")\", \"dashed\": ${rel_dashed[$i]}}"
  done

  echo "{\"domains\": [$domains_json"$'\n'"  ], \"relationships\": [$rel_json"$'\n'"  ]}"
}

# ── Marked excerpts embedded directly inside a module's own path-scoped rules file
# (`.claude/rules/<module>.md`, `<!-- #arch-embed:KEY --> ... <!-- /#arch-embed -->` convention) --
# lets a paragraph live once, in the doc a human reader already reads for that topic, and be pulled
# onto this generated page verbatim instead of a hand-copied second version here that can drift out
# of sync with it. Read raw via Node's JSON.stringify (not json_escape(), which strips newlines and
# mishandles CRLF).
ARCH_EMBED_KEYS=(
  ".claude/rules/platform-commons.md:spi-glossary"
  ".claude/rules/platform-commons.md:port-glossary"
  ".claude/rules/platform-commons.md:hook-glossary"
  ".claude/rules/platform-commons.md:why-port-hook-glossary"
  ".claude/rules/platform-commons.md:spi-implementation-rules"
)
arch_embed_raw() {
  local file="$1" key="$2"
  # Depth-tracked, not a flat on/off flag: a nested `<!-- #arch-embed:OTHER -->` (any key) inside
  # our target block increments depth instead of ending capture at its own closing tag -- a flat
  # flag would truncate the outer block's remaining content at the *inner* block's
  # `<!-- /#arch-embed -->`. Nested marker lines themselves are skipped (never printed); the real
  # text between/around them is still captured. Multiple separate (non-nested) blocks sharing the
  # same key still concatenate, same as before -- depth returns to 0 after each one, ready to
  # re-enter on the next match.
  awk -v key="$key" '
    depth == 0 && $0 ~ ("<!-- #arch-embed:" key " -->") { depth = 1; next }
    depth > 0 && $0 ~ /<!-- #arch-embed:[^ ]+ -->/ { depth++; next }
    depth > 0 && $0 ~ "<!-- /#arch-embed -->" { depth--; next }
    depth > 0 { print }
  ' "$REPO_ROOT/$file"
}
arch_embeds_json() {
  local out="" first=true entry file key raw
  for entry in "${ARCH_EMBED_KEYS[@]}"; do
    file="${entry%%:*}"
    key="${entry##*:}"
    $first || out="$out,"
    first=false
    raw="$(arch_embed_raw "$file" "$key")"
    out="$out\"$key\": $(printf '%s' "$raw" | run_node -e 'let d="";process.stdin.on("data",c=>d+=c);process.stdin.on("end",()=>process.stdout.write(JSON.stringify(d)))')"
  done
  echo "{$out}"
}

# ── Arch-embed marker index: every #arch-embed:KEY marker across every CLAUDE.md in the repo,
# with a one-line description derived from its own content -- discovery index for the convention
# itself, same role .claude/nav/adr-index.md plays for ADRs. Regenerated as part of this script's own
# run (not a separately-triggered script, unlike generate-adr-index.sh) so it can never go stale
# relative to the markers that actually exist. Repo-wide scan, not scoped to platform-commons --
# only one module uses the convention today, but nothing here assumes that stays true.
arch_embed_index_md() {
  echo "# Arch-embed marker index (generated)"
  echo
  echo "Generated by \`docs/architecture/scripts/generate-architecture-model.sh\` from every"
  echo "\`<!-- #arch-embed:KEY --> ... <!-- /#arch-embed -->\` marker found in any \`CLAUDE.md\` or"
  echo "\`.claude/rules/*.md\` file in this repo -- do not hand-edit, rerun the generator after any"
  echo "marker changes instead."
  echo "Description is derived from the marker's own leading \`**bold**\` phrase(s), not"
  echo "hand-authored -- a marker wrapping more than one bold-led paragraph (e.g."
  echo "\`spi-implementation-rules\`) gets all of them joined with \`; \`."
  echo
  echo "| Key | Source | Description |"
  echo "|---|---|---|"
  local file rel key line raw desc
  while IFS= read -r file; do
    rel="${file#"$REPO_ROOT"/}"
    while IFS= read -r key; do
      [ -z "$key" ] && continue
      line="$(grep -n -m1 "<!-- #arch-embed:${key} -->" "$file" | cut -d: -f1)"
      raw="$(arch_embed_raw "$rel" "$key")"
      desc="$(printf '%s' "$raw" | run_node -e '
        let d="";process.stdin.on("data",c=>d+=c);
        process.stdin.on("end",()=>{
          const paras = d.split(/\n\s*\n/).map(p=>p.trim()).filter(Boolean);
          const heads = paras.map(p => { const m = p.match(/^\*\*(.+?)\*\*/); return m ? m[1] : null; }).filter(Boolean);
          const out = heads.length ? heads.join("; ") : d.trim().replace(/\s+/g," ").slice(0,120);
          process.stdout.write(out);
        })')"
      echo "| \`$key\` | \`$rel:$line\` | $desc |"
    done < <(grep -oP '(?<=<!-- #arch-embed:)[a-z0-9-]+(?= -->)' "$file" | sort -u)
  done < <(find "$REPO_ROOT" \( -name "CLAUDE.md" -o -path "$REPO_ROOT/.claude/rules/*.md" \) -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/.git/*" | sort)
}

[ -n "$WITH_SONAR" ] && ensure_sonar_fresh

sonar_json="null"
[ -n "$WITH_SONAR" ] && sonar_json="$(sonar_metrics_json)"
archunit_json="null"
[ -n "$WITH_ARCHUNIT" ] && archunit_json="$(archunit_metrics_json)"
ci_metrics_json="null"
[ -n "$WITH_CI_METRICS" ] && ci_metrics_json="$(ci_pipeline_metrics_json)"

{
  echo "{"
  echo "  \"generated_by\": \"docs/architecture/scripts/generate-architecture-model.sh\","
  echo "  \"generated_note\": \"Track A, plus real SonarQube/ArchUnit metrics -- modules+deps from pom.xml, domain grouping/entities/services/contracts derived live from real Java source and the module list, tables live from the real Liquibase changelogs. Module Dependencies (01)/SPI Map (02)/Database ERD (04)/Bounded Contexts have no .md counterpart -- rendered live on this tool's own Diagrams page instead, lifecycle from DECISIONS.md/backlog, pipeline nodes from .claude/nav/flows.md + .claude/commands + .claude/skills + .claude/agents.\","
  echo "  \"rootArtifactId\": \"$(json_escape "$ROOT_ARTIFACT_ID")\","
  echo "  \"rootVersion\": \"$(json_escape "$ROOT_VERSION")\","
  echo "  \"diagramGroups\": ["
  echo "$diagram_groups_json"
  echo "  ],"
  echo "  \"archEmbeds\": $(arch_embeds_json),"
  echo "  \"backlogPriorityOrder\": $(backlog_priority_order_json),"
  echo "  \"spiMap\": $(spi_map_json),"
  echo "  \"dbErd\": $(db_erd_json),"
  echo "  \"boundedContexts\": $(bounded_contexts_json),"
  echo "  \"sonarMetrics\": $sonar_json,"
  echo "  \"archUnitMetrics\": $archunit_json,"
  echo "  \"ciPipelineMetrics\": $ci_metrics_json,"
  echo "  \"couplingChecks\": $(coupling_checks_json),"
  echo "  \"largestJavaFiles\": $(largest_java_files_json),"
  echo "  \"constructorInjection\": $(constructor_injection_json),"
  echo "  \"godPackages\": $(god_packages_json),"
  echo "  \"allAdrs\": $(all_adrs_json),"
  echo "  \"rootReadme\": $(root_md_json_for "README.md"),"
  echo "  \"rootInfrastructure\": $(root_md_json_for "INFRASTRUCTURE.md"),"
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
    if [ -n "${SCRIPT_GROUP_FILE_ORDER[$d]:-}" ]; then
      files_list=""
      for f in ${SCRIPT_GROUP_FILE_ORDER[$d]}; do
        [ -f "$REPO_ROOT/$d/$f" ] && files_list="$files_list$f"$'\n'
      done
    else
      # *.md included so a dir with only markdown content isn't an empty card -- README.md
      # excluded since it renders separately as its own readme block below, never duplicated.
      files_list="$(find "$REPO_ROOT/$d" -maxdepth 1 \( -name '*.sh' -o -name '*.js' -o -name '*.bat' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' -o -name '*.md' -o -name 'Dockerfile' \) -printf '%f\n' 2>/dev/null | grep -v '^README\.md$' | sort)"
    fi
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
  .card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; margin-bottom: 12px; }
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
  .row-flash { animation: row-flash-anim 1.5s ease-out; }
  @keyframes row-flash-anim { 0% { background-color: #fefcbf; } 100% { background-color: transparent; } }
  table.simple.spi-table td { text-align: left; vertical-align: middle; }
  .spi-calls { margin-top: 4px; font-size: 11px; color: var(--muted); line-height: 1.7; }
  .spi-calls code { background: none; padding: 0; font-size: 11px; }
  .spi-methodcount { margin-top: 4px; font-size: 11px; color: var(--muted); font-weight: 600; }
  .spi-method-list { margin-top: 2px; font-size: 11px; line-height: 1.6; }
  .spi-method-unused { color: var(--muted); font-style: italic; }
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
  .md-code { background: #1e293b; color: #e2e8f0; padding: 10px 14px; border-radius: 6px; font-size: 12px; line-height: 1.5; overflow-x: auto; margin: 8px 0; }
  .info-list { list-style: none; margin: 0; padding: 0; }
  .info-list li { padding: 6px 0; border-bottom: 1px solid #f0f2f4; font-size: 13px; line-height: 1.5; }
  .info-list li:last-child { border-bottom: none; }
  .info-list code { background: #f1f3f5; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
  .header-entry { padding: 14px 0; border-bottom: 1px solid #e5e8eb; }
  .header-entry:last-child { border-bottom: none; }
  .header-entry-file { font-family: monospace; font-size: 13px; font-weight: 600; margin-bottom: 6px; }
  .header-entry-field { font-size: 13px; line-height: 1.5; margin: 3px 0; white-space: pre-wrap; }
  .header-entry-field strong { color: var(--muted); font-weight: 600; }
  .table-chip { display: inline-block; background: #f1f3f5; color: var(--ink); font-size: 11px; padding: 3px 9px; border-radius: 6px; margin: 2px 4px 2px 0; font-family: monospace; }
  .diagram-wrap { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 20px; overflow: auto; max-height: 75vh; }
  /* Cytoscape-rendered diagrams (Module Dependencies, SPI Map, bounded-contexts graph) manage
     their own internal pan/zoom transform -- the canvas element's DOM size never actually changes
     with zoom, so this container never really overflows and native scrollbars never engage. Drag
     empty canvas space to pan instead (see the diagram-note text above each one). */
  #diagram-cy-wrap { overflow: hidden; max-height: none; }
  #diagram-zoom-box { transform-origin: top left; width: fit-content; margin: 0 auto; transition: transform .1s ease-out; }
  .diagram-note { font-size: 12px; color: var(--muted); margin-bottom: 12px; }
  .rel-row-flash { animation: rel-row-flash-anim 1.5s ease-out; }
  @keyframes rel-row-flash-anim { 0% { background: #fff3bf; } 100% { background: transparent; } }
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
  a.module-link { color: var(--accent); cursor: pointer; text-decoration: none; }
  a.module-link:hover { text-decoration: underline; }
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
  <div class="subtitle">Generated from pom.xml, DECISIONS.md, backlog/, .claude/nav/flows.md, .claude/commands, .claude/skills, .claude/agents, root CLAUDE.md — regenerate via <code>bash docs/architecture/scripts/generate-architecture-model.sh</code>. Track A only: module-level granularity; Contract/Implementation/Method levels are placeholders until Track B's ArchUnit exporter lands.</div>
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
const agentNodes = MODEL.nodes.filter(n => n.type === "AGENT");
const scriptGroupNodes = MODEL.nodes.filter(n => n.type === "SCRIPT_GROUP");
const backlogNode = MODEL.nodes.find(n => n.type === "BACKLOG_SUMMARY");

// ── Tooling & Pipelines' own card->detail groups -- one card per tool, same drill-down shape as
// Diagrams' groupKey (list view with no view.groupId, detail view once one is picked). "category"
// matches a top-level SCRIPT_GROUP node's own category field (SCRIPT_GROUP_CATEGORY for
// build-architecture-page; each TREE_ROOTS entry's own SCRIPT_TREE_ROOT_CATEGORY for
// ai-tooling/scripts-tree). ai-tooling and scripts-tree both open a real arbitrary-depth
// drill-down (view.path walks n.children) instead of a flat category filter -- see TREE_ROOTS and
// renderScriptTree() below.
const PIPELINE_GROUPS = {
  "ai-tooling": { icon: "🤖", label: "AI Tooling", category: "AI Tooling", desc: `${commandNodes.length} commands, ${skillNodes.length} skills, ${agentNodes.length} agents, and the rest of .claude/ (nav, rules, script headers)` },
  "build-architecture-page": { icon: "🗺️", label: "Build architecture page", category: "Build architecture page", desc: "docs/architecture/scripts — this page's own generator" },
  "scripts-tree": { icon: "📜", label: "Scripts", category: "Scripts", desc: "Every developer script for building, deploying, testing, and running CI lives here: local deploy/infra setup, Docker/Maven builds, SonarQube analysis, and the isolated Dagu-based CI runner. Playwright's end-to-end test suite sits alongside as its own folder. Drill into any folder for its own README and per-file headers." }
};
const PIPELINE_GROUP_ORDER = ["ai-tooling", "build-architecture-page", "scripts-tree"];
// Which top-level SCRIPT_GROUP tree root(s) each tree-shaped pipeline group drills into -- the
// first root's own children render directly at the group's root view (no intermediate card to
// click through); any further roots (only scripts-tree has one: "playwright") are inserted as one
// more sibling folder-card at that same level, same shape scriptTreeNodeAt()/renderScriptTree()
// already used to hardcode for "scripts"+"playwright" specifically.
const TREE_ROOTS = { "scripts-tree": ["scripts", "playwright"], "ai-tooling": [".claude"] };
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
  if (v.screen === "pipelines") {
    if (v.groupId) return PIPELINE_GROUPS[v.groupId] ? PIPELINE_GROUPS[v.groupId].label : v.groupId;
    return "Tooling & Pipelines";
  }
  if (v.screen === "backlog") return "Backlog";
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
  // Drilling deeper within the unified Scripts tree (path segments, same "pipelines"/groupId) is
  // a path change, not a new navigation hop -- crumbStack must not grow on every folder-card
  // click, or the breadcrumb repeats "Scripts" once per depth level. Scoped tightly to
  // screen === "pipelines" with a real groupId (never matches the diagrams screen, which
  // identifies its own views via groupKey/diagramIndex instead -- both undefined on unrelated
  // diagram views would otherwise false-match here).
  const samePlace = next.screen === "pipelines" && view.screen === "pipelines" && next.groupId && next.groupId === view.groupId;
  if (next.screen === "system") {
    crumbStack = [];
  } else if (view.screen !== "system" && !samePlace) {
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
// place") stays correct automatically instead of hand-coding where it should land. A
// tree-drilling view (the unified Scripts tree) one or more folders deep goes up exactly one
// folder level first -- crumbStack itself never grew for those levels (see navigate()), so
// falling straight through to the crumbStack-based jump below would skip past every intermediate
// folder straight out of the whole tree in one click.
function navigateBack() {
  if (view.screen === "pipelines" && view.groupId && view.path && view.path.length) {
    navigate({ screen: "pipelines", groupId: view.groupId, path: view.path.slice(0, -1) });
    return;
  }
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
    // A tree-drilling view (the unified Scripts tree) whose own path is non-empty is "inside" its
    // own root, not at it -- crumbStack itself never grows for a path change within the same tree
    // (see navigate()), so the breadcrumb must show the real depth itself: "Scripts" (always
    // clickable back to path: []) plus one clickable segment per path entry, the last one plain
    // (current, matching every other screen's own last-segment convention).
    if (view.screen === "pipelines" && view.groupId && view.path && view.path.length) {
      html += `<span class="sep">›</span><a onclick="navigate({screen:'pipelines',groupId:'${view.groupId}',path:[]})">${esc(crumbLabelFor(view))}</a>`;
      view.path.forEach((seg, i) => {
        const segPath = JSON.stringify(view.path.slice(0, i + 1));
        if (i === view.path.length - 1) {
          html += `<span class="sep">›</span><span class="current">${esc(seg)}</span>`;
        } else {
          html += `<span class="sep">›</span><a onclick='navigate({screen:"pipelines",groupId:"${view.groupId}",path:${segPath}})'>${esc(seg)}</a>`;
        }
      });
    } else {
      html += `<span class="sep">›</span><span class="current">${esc(crumbLabelFor(view))}</span>`;
    }
  }
  bc.innerHTML = html;
}

// ── System screen: just the 3 entry-point cards. Module browsing lives under Diagrams ›
// Module Dependencies (one shared graph-building function, not a second copy — see
// renderModuleDependencyGraph() and docs/architecture/scripts/DECISIONS.md ADR-003). ──────────────────────────
function renderSystem() {
  let html = `<h2 class="screen-title">System</h2>
    <div class="screen-desc">${moduleNodes.length} modules across ${domainOrder.length} domains. Pick where to start.</div>`;

  html += `<div class="card-grid">
    <div class="card special-card" onclick="navigate({screen:'pipelines'})">
      <div class="card-title">🛠 Tooling &amp; Pipelines</div>
      <div class="card-desc">${commandNodes.length} commands, ${skillNodes.length} skills, ${agentNodes.length} agents, ${scriptGroupNodes.length} script groups</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'backlog'})">
      <div class="card-title">📋 Backlog</div>
      <div class="card-desc">${backlogNode ? backlogNode.open_issues : "?"} open, ${backlogNode ? backlogNode.completed_issues : "?"} completed</div>
    </div>
    <div class="card special-card" onclick="navigate({screen:'diagrams'})">
      <div class="card-title">📐 Diagrams</div>
      <div class="card-desc">${totalDiagramCount} diagrams — dependency graph (click a module to drill in), SPI map, context map, ERD, sequence flows</div>
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

  // Root README.md, rendered inline at the bottom of this same page -- not a card/screen of its
  // own, the same way renderScriptGroupSection() renders a SCRIPT_GROUP dir's own README directly
  // in place rather than behind a click-through.
  html += `<section class="block">${mdBlockToHtml(MODEL.rootReadme || "", "")}<div class="empty-hint">Source: ${sourceLink("README.md")}</div></section>`;

  document.getElementById("content").innerHTML = html;
  mermaid.run({ querySelector: ".mermaid" });
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
// 01-module-dependencies.md "Dependency Table", see docs/architecture/scripts/DECISIONS.md ADR-005). Returns
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

// baseDir: repo-relative directory the source markdown file itself lives in (e.g. ".claude/nav"
// for .claude/nav/README.md, "" for a repo-root file) -- resolves a same-directory link target
// (e.g. "adr-index.md") into a real, clickable sourceLink(). A link target already containing "/"
// (e.g. "docs/architecture/bounded-contexts.md") is treated as already repo-root-relative and used
// as-is, regardless of baseDir -- matches how these READMEs actually write cross-directory links.
function mdInlineToHtml(s, baseDir) {
  let html = esc(s).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/`([^`]+)`/g, "<code>$1</code>");
  return html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (m, label, url) => {
    if (/^https?:\/\//.test(url)) return `<a href="${url}" target="_blank">${label}</a>`;
    if (url.startsWith("#")) return m;
    const relPath = url.includes("/") ? url : (baseDir ? `${baseDir}/${url}` : url);
    return `<a href="../../${esc(relPath)}" target="_blank">${label}</a>`;
  });
}

// Paragraph/list/table/heading/fenced-code markdown -> HTML for ADR body text (Context/Decision/
// Consequences, amendments, tables) and README.md content (headings, fenced "how to run" bash
// blocks). Not a general markdown parser -- same "only what's needed" scope as
// parseMermaidGraph(). Reuses mdInlineToHtml() for **bold**/`code` within each non-code block.
function mdBlockToHtml(text, baseDir) {
  if (!text) return "";
  // Blocks break on a blank line OR on a line-type transition (e.g. "**Decision:**" directly
  // followed by "1. ..." with no blank line between -- a real pattern in these ADRs). A plain
  // (non-marker) line while inside a list is a soft-wrapped continuation of the *previous* list
  // item, not a new paragraph -- real ADR list items wrap across multiple source lines.
  const blocks = [];
  let current = null;
  const lines = text.split("\n");
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i];
    const line = raw.trim();
    if (line.startsWith("```")) {
      // Fenced code block -- consumed verbatim (no inline markdown, no wrapping into the
      // surrounding paragraph/list logic) until the matching closing fence or end of text. The
      // language tag (```mermaid) decides how it renders -- see the "code" block-type mapping below.
      const lang = line.slice(3).trim();
      const codeLines = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith("```")) { codeLines.push(lines[i]); i++; }
      blocks.push({ type: "code", lang, text: codeLines.join("\n") });
      current = null;
      continue;
    }
    if (line === "") { current = null; continue; }
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    if (heading) {
      blocks.push({ type: "h", level: heading[1].length, text: heading[2] });
      current = null;
      continue;
    }
    if (/^[-*] /.test(line)) {
      if (!current || current.type !== "ul") { current = { type: "ul", items: [] }; blocks.push(current); }
      current.items.push(line.replace(/^[-*] /, ""));
      continue;
    }
    if (/^\d+\. /.test(line)) {
      if (!current || current.type !== "ol") { current = { type: "ol", items: [] }; blocks.push(current); }
      current.items.push(line.replace(/^\d+\. /, ""));
      continue;
    }
    if (line.startsWith("|")) {
      if (!current || current.type !== "table") { current = { type: "table", lines: [] }; blocks.push(current); }
      current.lines.push(line);
      continue;
    }
    if (current && (current.type === "ul" || current.type === "ol") && current.items.length) {
      current.items[current.items.length - 1] += " " + line;
      continue;
    }
    if (!current || current.type !== "p") { current = { type: "p", lines: [] }; blocks.push(current); }
    current.lines.push(line);
  }
  return blocks.map(b => {
    if (b.type === "code" && b.lang === "mermaid") return `<div class="diagram-wrap" style="padding:8px"><pre class="mermaid">${esc(b.text)}</pre></div>`;
    if (b.type === "code") return `<pre class="md-code">${esc(b.text)}</pre>`;
    // +3: a README's top-level "#" title would collide with the surrounding page's own <h2>/<h3>
    // screen chrome -- start one level down (## -> <h4>) same as this tool's other embedded-markdown
    // headings.
    if (b.type === "h") { const lvl = Math.min(b.level + 3, 6); return `<h${lvl}>${mdInlineToHtml(b.text, baseDir)}</h${lvl}>`; }
    if (b.type === "ul") return "<ul>" + b.items.map(l => `<li>${mdInlineToHtml(l, baseDir)}</li>`).join("") + "</ul>";
    if (b.type === "ol") return "<ol>" + b.items.map(l => `<li>${mdInlineToHtml(l, baseDir)}</li>`).join("") + "</ol>";
    if (b.type === "table" && b.lines.length > 1 && /^\|[\s:|-]+\|$/.test(b.lines[1])) {
      const rows = b.lines.filter((_, i) => i !== 1).map(l => l.split("|").slice(1, -1).map(c => c.trim()));
      const [head, ...body] = rows;
      return `<table class="simple"><thead><tr>${head.map(c => `<th>${mdInlineToHtml(c, baseDir)}</th>`).join("")}</tr></thead><tbody>` +
        body.map(r => `<tr>${r.map(c => `<td>${mdInlineToHtml(c, baseDir)}</td>`).join("")}</tr>`).join("") + `</tbody></table>`;
    }
    return `<p>${mdInlineToHtml(b.lines.join(" "), baseDir)}</p>`;
  }).join("");
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
// see docs/architecture/scripts/DECISIONS.md ADR-005). Shared by every "Export as Markdown" button in this tool.
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

// Full snapshot of one module's page. ADRs are listed as title + a real file reference, not
// a copy of the ADR text -- same "resolve to the one source, never restate it" rule the on-page
// list follows.
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
    html += `<div class="empty-hint">No data -- regenerate with <code>--with-archunit</code> (requires <code>bash scripts/build-and-test.sh --archunit-metrics</code> to have run at least once).</div>`;
  } else {
    html += `<div class="empty-hint">Source: ArchUnit (from the last <code>bash scripts/build-and-test.sh --archunit-metrics</code> run).</div>
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
// root) -- opens the actual file, "resolve to source, never restate it" rule.
function sourceLink(relPath) {
  return `<a href="../../${esc(relPath)}" target="_blank"><code class="path">${esc(relPath)}</code></a>`;
}

function renderScriptGroupSection(n, skipHeading) {
  // Only chip files that DON'T already get their own full block in the "Script headers" list
  // below -- a file with a real parsed header would otherwise appear twice on the same card (a
  // bare chip up here, the real thing right after), the exact kind of duplicate this generator's
  // "one fact, one canonical home" discipline exists to avoid.
  const headeredFiles = new Set((n.headers || []).map(h => h.file.split("/").pop()));
  const chipFiles = (n.files || []).filter(f => !headeredFiles.has(f));
  let html = "";
  // The chip-row is a last-resort file listing -- it only ever renders when nothing else on this
  // card already covers those files. A node with its own README.md is assumed to already name and
  // link its own files in context (the README IS the canonical place for that, per
  // infra-readme-standards) -- showing a second, context-free chip-row above/beside it would
  // duplicate the exact same links the README already gives real explanation around. Only a node
  // with NO README at all (e.g. .claude/commands, whose *.md files carry no infra-doc-standards
  // header either) falls back to the chip-row, so its files are still visible/clickable somewhere.
  if (!skipHeading && (n.description || (!n.readme && chipFiles.length))) {
    html += `<section class="block"><h3>${esc(n.id)}</h3>`;
    if (n.description) html += `<div class="screen-desc">${esc(n.description)}</div>`;
    if (!n.readme && chipFiles.length) {
      html += `<div class="table-chip-row">` + chipFiles.map(f => sourceLink(`${n.id}/${f}`)).join(" ") + `</div>`;
    }
    html += `</section>`;
  } else if (skipHeading && !n.readme && chipFiles.length) {
    html += `<div class="table-chip-row">` + chipFiles.map(f => sourceLink(`${n.id}/${f}`)).join(" ") + `</div>`;
  }
  // Last Dagu-backed CI run's per-step status/duration (scripts/ci only) -- opt-in
  // (--with-ci-metrics), passively read from whatever scripts/ci/reports/pipeline-metrics.json
  // already contains; no live query against Dagu itself, same reasoning as Sonar/ArchUnit above.
  // Renders nothing at all (not even the heading) until real data exists -- an empty placeholder
  // box is dead-weight noise on a screen someone is browsing for real content.
  if (n.id === "scripts/ci" && MODEL.ciPipelineMetrics) {
    const ci = MODEL.ciPipelineMetrics;
    html += `<section class="block"><h3>Last CI run</h3>
      <div class="empty-hint">Source: Dagu run <code>${esc(ci.dagRunId || "unknown")}</code>, finished ${esc(ci.generatedAt || "unknown")}.</div>
        <table class="simple"><thead><tr><th>Step</th><th>Status</th><th>Duration</th></tr></thead><tbody>` +
        (ci.steps || []).map(s => `<tr><td>${esc(s.name || "?")}</td><td>${esc(s.status || "?")}</td><td>${s.durationSeconds != null ? esc(s.durationSeconds.toFixed(0) + "s") : "-"}</td></tr>`).join("") +
        `</tbody></table></section>`;
  }
  // Each file's own structured Description/Usage/Uses/Env/Input/Outputs/Returns header (see
  // .claude/skills/infra-doc-standards/SKILL.md) -- read live from the file itself, self-contained
  // ("what does this do, how do I run it, what do I get"), not a restatement of README's own job
  // (the flow *between* files, rendered right after via n.readme below). A vertical list (one
  // block per file, divided by a horizontal rule) reads far better than a wide table once a file
  // has multi-line field values -- a table cell wraps awkwardly, a block doesn't. Order matches
  // n.files (SCRIPT_GROUP_FILE_ORDER when the dir declares one, entry points listed first).
  if (n.headers && n.headers.length) {
    html += `<section class="block"><h3>Script headers</h3>`;
    n.headers.forEach(h => {
      html += `<div class="header-entry"><div class="header-entry-file">${sourceLink(h.file)}</div>`;
      [["Description", h.description], ["Usage", h.usage], ["Uses", h.uses], ["Env", h.env],
       ["Input", h.input], ["Outputs", h.outputs], ["Returns", h.returns]].forEach(([label, value]) => {
        if (value) html += `<div class="header-entry-field"><strong>${esc(label)}:</strong> ${esc(value)}</div>`;
      });
      html += `</div>`;
    });
    html += `</section>`;
  }
  // The dir's own README.md (if any) -- the flow *between* files, not each file's own behavior
  // again (that's n.headers above) -- rendered as its own block right after.
  if (n.readme) {
    html += `<section class="block">${mdBlockToHtml(n.readme, n.id)}<div class="empty-hint">Source: ${sourceLink(`${n.id}/README.md`)}</div></section>`;
  }
  return html;
}

// Same list-then-drill-in shape as renderDiagrams() (no view.groupId -> card grid of tools;
// view.groupId set -> that one tool's content only).
function renderPipelines() {
  if (!view.groupId) {
    let html = backButtonHtml();
    html += `<h2 class="screen-title">Tooling &amp; Pipelines</h2>
      <div class="screen-desc">Slash commands, skills, custom subagents, and scripts available in this repo — sourced from .claude/commands, .claude/skills, .claude/agents, and scripts/**, cross-checked against .claude/nav/flows.md. Click a card to see its content.</div>`;

    html += `<div class="card-grid">`;
    PIPELINE_GROUP_ORDER.forEach(id => {
      const g = PIPELINE_GROUPS[id];
      html += `<div class="card" onclick="navigate({screen:'pipelines',groupId:'${id}'})">
        <div class="card-title">${g.icon} ${esc(g.label)}</div>
        <div class="card-desc">${esc(g.desc)}</div>
      </div>`;
    });
    html += `</div>`;

    // Root INFRASTRUCTURE.md, rendered inline at the bottom of this same page -- not its own
    // card/drill-down, the same way README.md renders inline at the bottom of the System page.
    html += `<section class="block">${mdBlockToHtml(MODEL.rootInfrastructure || "", "")}<div class="empty-hint">Source: ${sourceLink("INFRASTRUCTURE.md")}</div></section>`;

    document.getElementById("content").innerHTML = html;
    mermaid.run({ querySelector: ".mermaid" });
    return;
  }

  const g = PIPELINE_GROUPS[view.groupId];
  if (!g) { navigate({ screen: "pipelines" }); return; }

  // ai-tooling and scripts-tree are both tree-shaped (TREE_ROOTS), sharing one drill-down
  // renderer -- see renderScriptTree()'s own header comment for why groupId is passed through.
  if (view.groupId === "scripts-tree" || view.groupId === "ai-tooling") { renderScriptTree(g, view.groupId); return; }

  let html = backButtonHtml();
  html += `<h2 class="screen-title">${g.icon} ${esc(g.label)}</h2>`;

  // build-architecture-page has two real subdirectories worth their own folder-card (scripts/,
  // data/) -- reuses view.path, the exact same mechanism renderScriptTree() already drives
  // navigateBack()/renderBreadcrumb() with (both already groupId-agnostic), instead of inventing a
  // second, parallel "current subview" field that those two functions wouldn't know about. The
  // only PIPELINE_GROUPS entry reaching this point -- ai-tooling/scripts-tree both return early
  // via the tree dispatch above, so no further groupId branch is needed here.
  if (!view.path || !view.path.length) {
    html += `<div class="card-grid">
      <div class="card" onclick='navigate({screen:"pipelines",groupId:"build-architecture-page",path:["scripts"]})'>
        <div class="card-title">📁 scripts</div>
      </div>
      <div class="card" onclick='navigate({screen:"pipelines",groupId:"build-architecture-page",path:["data"]})'>
        <div class="card-title">📁 data</div>
      </div>
    </div>`;
    // docs/architecture's own entry-point files (architecture-doc.sh/.bat) + its own README --
    // a real SCRIPT_GROUP node (readme_json_for() already picks up its README.md the same way
    // it does for every other SCRIPT_GROUP dir), not a separate one-off MODEL field -- one fact,
    // one mechanism, same as scripts/data above. Cards always come first on this page.
    const archNode = scriptGroupNodes.find(x => x.id === "docs/architecture");
    if (archNode) html += renderScriptGroupSection(archNode, true);

    // A fact about this page's own rendering tech stack -- not per-file, so it doesn't belong in
    // any single file's own header (renderScriptGroupSection() already shows every file's real
    // Description/Usage/Uses/Input/Output, mechanically, no second copy of that table here).
    html += `<div class="empty-hint"><strong>Rendering:</strong> Cytoscape.js + cytoscape-dagre for the draggable graphs (Module Dependencies, SPI Map); Mermaid.js for the Database ERD and Sequence Diagrams.</div>`;
  } else {
    const targetId = view.path[0] === "scripts" ? "docs/architecture/scripts" : "docs/architecture/data";
    const n = scriptGroupNodes.find(x => x.id === targetId);
    // skipHeading=true -- same reasoning as renderScriptTree()'s own leaf-level call: the
    // breadcrumb/page title/folder-card just clicked already say the id, and a dir with no
    // parsed file headers (docs/architecture/data) would otherwise show an empty heading+chip-row
    // directly above a README that already fully describes the same files -- two places for one
    // fact, exactly what "one fact, one canonical home" exists to prevent.
    if (n) html += renderScriptGroupSection(n, true);
  }

  document.getElementById("content").innerHTML = html;
  // A README's own ```mermaid fenced block (rendered as <pre class="mermaid"> by mdBlockToHtml())
  // needs this same call every other Mermaid diagram on this tool already uses -- the fence's raw
  // text sits inert in the DOM as plain text until mermaid.run() finds and replaces it with SVG.
  mermaid.run({ querySelector: ".mermaid" });
}

// Walks MODEL's top-level SCRIPT_GROUP nodes down through view.path, for the tree belonging to
// "roots" (TREE_ROOTS[groupId] -- e.g. ["scripts","playwright"] or [".claude"]). The primary root
// (roots[0]) is implicit -- never a path segment of its own, so that card's root view has no
// redundant click-through -- so path[0] resolves against any further root id first (e.g.
// "playwright") and falls back to treating path[0] as the primary root's own child basename
// otherwise; each further segment is the next child directory's own basename (e.g.
// ["ci","dagu"] resolves to scripts/ci/dagu). Returns null if the path doesn't resolve (e.g. a
// stale bookmark after a rename).
function scriptTreeNodeAt(pathSegs, roots) {
  if (!pathSegs.length) return null;
  let node = scriptGroupNodes.find(n => n.id === pathSegs[0]);
  if (!node) {
    const primaryRoot = scriptGroupNodes.find(n => n.id === roots[0]);
    node = primaryRoot ? (primaryRoot.children || []).find(c => c.id.split("/").pop() === pathSegs[0]) : null;
  }
  for (let i = 1; node && i < pathSegs.length; i++) {
    node = (node.children || []).find(c => c.id.split("/").pop() === pathSegs[i]);
  }
  return node || null;
}

// A tree-shaped pipeline card's own arbitrary-depth drill-down -- folder-cards (one per
// node.children, clickable to extend view.path one segment deeper) rendered first, then the flat
// file list for the current level via the existing renderScriptGroupSection() (reused as-is, same
// shape as any flat SCRIPT_GROUP dir). No view.path (or an empty one) shows the primary root's own
// children/files directly (no intermediate click-through card), with any further TREE_ROOTS
// entries (only scripts-tree has one: "playwright") inserted as sibling folder-cards at that same
// level. Navigation back up is the page-level breadcrumb (crumbStack, which records entry into
// this card as one hop) plus the "Back" button already rendered above -- no separate in-card
// breadcrumb of its own. groupId picks which TREE_ROOTS entry drives this render and which
// navigate() calls the folder-cards below use, so scripts-tree and ai-tooling share this one
// function instead of two near-duplicate copies.
function renderScriptTree(g, groupId) {
  const roots = TREE_ROOTS[groupId];
  const path = view.path || [];
  let html = backButtonHtml();
  // Depth suffix (matches the breadcrumb's own " › " join) so the title itself shows current
  // depth at a glance, not just the breadcrumb above it.
  const titleSuffix = path.length ? ` — ${path.join(" › ")}` : "";
  html += `<h2 class="screen-title">${g.icon} ${esc(g.label)}${esc(titleSuffix)}</h2>`;
  // Root-only -- the whole-tree description doesn't apply any more specifically once drilled into
  // a subfolder, and repeating identical text at every depth is exactly the kind of duplication
  // "One fact, one canonical home" already governs elsewhere on this page.
  if (!path.length) html += `<div class="screen-desc">${esc(g.desc)}</div>`;

  let node = null;
  let children;
  if (!path.length) {
    // Root view: no separate primary-root click-through -- show the primary root's own children
    // and files directly, with any further TREE_ROOTS entries inserted as sibling folder-cards
    // (even though structurally separate top-level nodes, not literally the primary root's own
    // children).
    node = scriptGroupNodes.find(n => n.id === roots[0]);
    const primaryChildren = node ? (node.children || []).map(c => ({ label: c.id.split("/").pop(), path: [c.id.split("/").pop()] })) : [];
    const siblingCards = roots.slice(1)
      .filter(r => scriptGroupNodes.find(n => n.id === r))
      .map(r => ({ label: r, path: [r] }));
    children = primaryChildren.concat(siblingCards);
  } else {
    node = scriptTreeNodeAt(path, roots);
    if (!node) { navigate({ screen: "pipelines", groupId, path: [] }); return; }
    children = (node.children || []).map(c => ({ label: c.id.split("/").pop(), path: path.concat([c.id.split("/").pop()]) }));
  }

  // Folder-cards first: every child directory of the current level, clickable to extend the path
  // one segment deeper. Cards are folders only -- a file never navigates, it only ever renders as
  // a plain row inside renderScriptGroupSection() below.
  if (children.length) {
    html += `<div class="card-grid">`;
    children.forEach(c => {
      const p = JSON.stringify(c.path);
      html += `<div class="card" onclick='navigate({screen:"pipelines",groupId:"${groupId}",path:${p}})'>
        <div class="card-title">📁 ${esc(c.label)}</div>
      </div>`;
    });
    html += `</div>`;
  }

  // Flat file list for the current level, below the folder-cards -- same renderer every flat
  // SCRIPT_GROUP dir already uses, so file headers/README/chip-list all just work unchanged, minus
  // the redundant heading+description+chip-row block (skipHeading=true -- see
  // renderScriptGroupSection()'s own comment for why). No node at the root (path-less) view -- the
  // folder-cards above are the whole list there.
  if (node) html += renderScriptGroupSection(node, true);

  document.getElementById("content").innerHTML = html;
  mermaid.run({ querySelector: ".mermaid" });
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
const GRAPH_TYPE_KEYS = ["01-module-dependencies", "02-spi-map", "bounded-contexts"];

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
  // SPI Map's "calls"/"implemented by" edges have a matching table row below (spiRowId()) --
  // jump to it on click. Any other diagram's edges (Module Dependencies, Bounded Contexts) just
  // find no matching id and no-op, so this is safe to wire in generically here.
  diagramCy.on("tap", "edge", e => {
    const d = e.target.data();
    const rowId = d.label === "calls" ? spiRowId("call", d.target, d.source.replace(/^call_/, ""))
      : d.label === "implemented by" ? spiRowId("impl", d.source, d.target) : null;
    const row = rowId && document.getElementById(rowId);
    if (!row) return;
    row.scrollIntoView({ behavior: "smooth", block: "center" });
    row.classList.remove("row-flash");
    void row.offsetWidth;
    row.classList.add("row-flash");
  });
}

function renderCytoscapeDiagram(source, rankDir) {
  const parsed = parseMermaidGraph(source);
  renderCytoscapeFromGraph(parsed.nodes, parsed.edges, rankDir);
}

// Live from real Java source (platform-commons/*/spi interfaces + real `implements` across
// starters/marketplace-app) -- same "live, not a separately-maintained .md" pattern as Module
// Dependencies. Left-to-right layout (interfaces on the left, implementations on the right) reads
// better for this 2-level interface->impl shape than top-to-bottom.
// One diagram per subsystem instead of one 70+-node canvas -- the combined graph (every SPI
// interface's callers + implementations on one page) was too dense to read. An interface belongs
// to a subsystem per MODEL.spiMap.details; edges/nodes pulled in are whatever actually touches
// that subsystem's own interfaces, same node/edge shape renderCytoscapeFromGraph already expects.
function spiMapNodesForSubsystem(subsystem) {
  const ifaceIds = new Set(MODEL.spiMap.details.filter(d => d.subsystem === subsystem).map(d => d.interface));
  const edges = MODEL.spiMap.edges.filter(e => ifaceIds.has(e.source) || ifaceIds.has(e.target));
  const nodeIds = new Set(ifaceIds);
  edges.forEach(e => { nodeIds.add(e.source); nodeIds.add(e.target); });
  const leafNodes = MODEL.spiMap.nodes.filter(n => !n.isGroup && nodeIds.has(n.id));
  const usedParents = new Set(leafNodes.map(n => n.parent));
  const groupNodes = MODEL.spiMap.nodes.filter(n => n.isGroup && usedParents.has(n.id));
  return { nodes: [...groupNodes, ...leafNodes], edges };
}

function renderSpiMapGraph(subsystem) {
  const filtered = spiMapNodesForSubsystem(subsystem);
  renderCytoscapeFromGraph(filtered.nodes, filtered.edges, "LR");
}

// Real link, readable class name as the visible text -- same "open the actual source, short
// label" pattern exportModuleMarkdown() already uses for ADRs, not a raw path dump.
function spiFileLink(file, label) {
  return `<a href="../../${esc(file)}" target="_blank">${esc(label)}</a>`;
}

// One pair of tables per subsystem (package prefix shown once per heading, same as the retired
// .md did) -- plus each subsystem's own editorial note (SPI_SUBSYSTEM_NOTE) when present. With
// the SPI Map diagram now split per subsystem, a specific subsystem restricts this to just its
// own tables, matching whichever diagram tab is on screen; omitted only by the (now unused)
// caller with no subsystem context. Split into two tables (Calls / Implemented By), one row per
// real edge rather than one row per interface with all edges stacked in a cell -- each row's
// `id` matches the diagram edge it represents, so clicking that edge (renderCytoscapeFromGraph's
// edge-tap handler) can scroll straight to it (improvement-157).
function spiRowId(kind, interfaceName, className) {
  return `spi-${kind}-${esc(interfaceName)}__${esc(className)}`;
}

// Same "real link, not plain text" treatment Code Quality's module column already uses --
// reused here since these tables repeat the same Interface/Purpose text once per caller/
// implementation otherwise (grouped away below via rowspan instead).
function moduleLink(moduleId) {
  return `<a class="module-link" onclick="navigate({screen:'module', id:'${esc(moduleId)}'})">${esc(moduleId)}</a>`;
}

// "(N/M methods used)" plus the full method list, unused ones dimmed and marked -- a real
// dead-code-in-the-contract signal, not just a count. N = distinct interface methods actually
// called by any real caller (ArchitectureMetricsExport.java's spiEdges), M = total methods
// declared on the interface. Everything null when no ArchUnit data was available for this
// interface (old regex fallback path) -- rendered as nothing rather than a misleading "0/0".
function spiMethodCountLine(d) {
  if (d.methodCount == null || d.usedCount == null) return "";
  const list = (d.allMethods || []).map(m => m.used
    ? `<div>${esc(m.name)}</div>`
    : `<div class="spi-method-unused">${esc(m.name)} (unused)</div>`
  ).join("");
  return `<div class="spi-methodcount">(${d.usedCount}/${d.methodCount} methods used)</div><div class="spi-method-list">${list}</div>`;
}

// Real (callerMethod, interfaceMethod) call-site pairs, one per line -- undefined/empty for the
// old regex fallback path (no method-level data at all) or a caller found via DI wiring only with
// no actual method call (doesn't happen with real ArchUnit data, but stays defensive).
function renderSpiCalls(calls) {
  if (!calls || !calls.length) return "";
  return `<div class="spi-calls">${calls.map(c => `<code>${esc(c.from)}() &rarr; #${esc(c.to)}()</code>`).join("<br>")}</div>`;
}

function renderSpiCallsRows(rows) {
  return rows.map(d => {
    const span = d.callers && d.callers.length ? d.callers.length : 1;
    const ifaceCell = `<td rowspan="${span}">${spiFileLink(d.file, d.interface)}${spiMethodCountLine(d)}</td>`;
    const purposeCell = `<td rowspan="${span}">${esc(d.purpose)}</td>`;
    if (!d.callers || !d.callers.length) {
      return `<tr><td><span class="empty-hint">no caller found</span></td>${ifaceCell}${purposeCell}</tr>`;
    }
    return d.callers.map((c, i) => {
      const callerCell = `<td>${spiFileLink(c.file, c.class)} (${moduleLink(c.module)})${renderSpiCalls(c.calls)}</td>`;
      const row = i === 0 ? `${callerCell}${ifaceCell}${purposeCell}` : callerCell;
      return `<tr id="${spiRowId("call", d.interface, c.class)}">${row}</tr>`;
    }).join("");
  }).join("");
}

function renderSpiImplementedByRows(rows) {
  return rows.map(d => {
    const span = d.implementations.length || 1;
    const ifaceCell = `<td rowspan="${span}">${spiFileLink(d.file, d.interface)}${spiMethodCountLine(d)}</td>`;
    const purposeCell = `<td rowspan="${span}">${esc(d.purpose)}</td>`;
    if (!d.implementations.length) {
      return `<tr>${ifaceCell}<td><span class="empty-hint">no implementation found</span></td>${purposeCell}</tr>`;
    }
    return d.implementations.map((impl, i) => {
      const implCell = `<td>${spiFileLink(impl.file, impl.class)} (${moduleLink(impl.module)})</td>`;
      const row = i === 0 ? `${ifaceCell}${implCell}${purposeCell}` : implCell;
      return `<tr id="${spiRowId("impl", d.interface, impl.class)}">${row}</tr>`;
    }).join("");
  }).join("");
}

function renderSpiSubsystemTables(subsystem) {
  const order = subsystem ? [subsystem] : MODEL.spiMap.subsystemOrder;
  return order.map(s => {
    const rows = MODEL.spiMap.details.filter(d => d.subsystem === s);
    if (!rows.length) return "";
    const note = MODEL.spiMap.subsystemNotes[s];
    const callCount = rows.reduce((n, d) => n + (d.callers ? d.callers.length : 0), 0);
    const implCount = rows.reduce((n, d) => n + d.implementations.length, 0);
    return `
      <div class="domain-group">
        <h3>${esc(MODEL.spiMap.subsystemLabels[s])} <code class="path">${esc(rows[0].package)}</code></h3>
        <h4>Calls (${callCount})</h4>
        <table class="simple spi-table"><thead><tr>
          <th title="Real caller class -- who actually calls the interface's method, from bytecode analysis, not who's merely allowed to" style="cursor:help">Caller</th>
          <th>Interface</th>
          <th>Purpose</th>
        </tr></thead><tbody>${renderSpiCallsRows(rows)}</tbody></table>
        <h4>Implemented By (${implCount})</h4>
        <table class="simple spi-table"><thead><tr>
          <th>Interface</th>
          <th title="Real class that implements this interface" style="cursor:help">Implementation</th>
          <th>Purpose</th>
        </tr></thead><tbody>${renderSpiImplementedByRows(rows)}</tbody></table>
        ${note ? `<div class="empty-hint" style="margin-top:8px">${mdInlineToHtml(note)}</div>` : ""}
      </div>`;
  }).join("");
}

function renderSpiMapExtrasHtml(subsystem) {
  const rows = MODEL.spiMap.details.filter(d => !subsystem || d.subsystem === subsystem);
  const callCount = rows.reduce((n, d) => n + (d.callers ? d.callers.length : 0), 0);
  const implCount = rows.reduce((n, d) => n + d.implementations.length, 0);
  return `
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a node to open its real <code class="path">.java</code> file. Click an edge to jump to its row below. Drag a node to reposition it, drag empty canvas space to pan.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">Dashed gray box</td><td>A module boundary (<code class="path">platform-commons</code>, a starter, <code class="path">marketplace-app</code>, or <code class="path">marketplace-orchestrator</code>)</td></tr>
        <tr><td class="scope-label">Blue rounded box</td><td>A Java interface, an implementation class, or a real caller class</td></tr>
        <tr><td class="scope-label">──calls──▶</td><td>A real caller class injects/calls the interface — arrow points from the caller to the interface</td></tr>
        <tr><td class="scope-label">──implemented by──▶</td><td>Arrow points from the interface to its implementation class</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>SPI Interface Details (${callCount} calls, ${implCount} implementations)</h3>${renderSpiSubsystemTables(subsystem)}</section>`;
}

// Parses one "**Heading:** location sentence. Example: example sentence." paragraph from the
// platform-commons/CLAUDE.md#spi-implementation-rules arch-embed into the pieces
// renderImplementationRulesHtml()/exportSpiMapMarkdown() both render as a Location/Example pair
// -- generic over the paragraph's own content, so a third implementation-kind paragraph added to
// that CLAUDE.md section later needs no code change here. Returns raw markdown (backticks intact,
// not HTML-escaped) -- each caller does its own target-format conversion.
function parseImplementationRuleParagraphs() {
  const raw = (MODEL.archEmbeds && MODEL.archEmbeds["spi-implementation-rules"]) || "";
  const stripPeriod = t => t.trim().replace(/\.$/, "");
  return raw.split(/\n\s*\n/).map(p => p.trim()).filter(Boolean).map(p => {
    const m = p.match(/^\*\*(.+?):\*\*\s*([\s\S]*)$/);
    if (!m) return null;
    const heading = m[1];
    const rest = m[2];
    const exIdx = rest.indexOf("Example:");
    const location = stripPeriod(exIdx >= 0 ? rest.slice(0, exIdx) : rest);
    const example = exIdx >= 0 ? stripPeriod(rest.slice(exIdx + "Example:".length)) : "";
    return { heading, location, example };
  }).filter(Boolean);
}

// Glossary content (SPI/Port/Hook) -- subsystem-independent, so rendered once at the bottom of
// the top-level Diagrams listing screen (next to Implementation Rules), not once per SPI Map
// subsystem tab -- renderSpiMapExtrasHtml() used to repeat the SPI paragraph on every tab before
// this. All three paragraphs are live-read platform-commons/CLAUDE.md arch-embeds (MODEL.archEmbeds).
// Parses one "**Heading** body text" glossary paragraph -- same bold-lead-in shape as
// parseImplementationRuleParagraphs() but without the required ":" before the closing "**"
// (a glossary heading is a short phrase/question, not a label).
function parseGlossaryEntry(raw) {
  const p = (raw || "").trim().replace(/\s+/g, " ");
  const m = p.match(/^\*\*(.+?)\*\*\s*(.*)$/);
  return m ? { heading: m[1], body: m[2].trim() } : null;
}

// Same adr-item visual style as renderImplementationRulesHtml() below -- one <strong> heading per
// glossary term, not a plain markdown paragraph.
function renderDiagramsOverviewHtml() {
  const toPathCode = t => esc(t).replace(/`([^`]+)`/g, '<code class="path">$1</code>');
  const items = ["spi-glossary", "port-glossary", "hook-glossary", "why-port-hook-glossary"]
    .map(k => MODEL.archEmbeds && MODEL.archEmbeds[k] ? parseGlossaryEntry(MODEL.archEmbeds[k]) : null)
    .filter(Boolean)
    .map(g => `<div class="adr-item"><strong>${esc(g.heading)}</strong>
        <div class="empty-hint">${toPathCode(g.body)}</div>
      </div>`).join("");
  return `
    <section class="block"><h3>Overview</h3>
      ${items}
      <div class="empty-hint">All cross-module extension points (Ports and Hooks) live in <code class="path">platform-commons</code> to decouple starters from marketplace-app — see ${sourceLink("platform-commons/CLAUDE.md")}'s "SPI Interface Naming" table for the authoritative direction/role definition of each suffix.</div>
    </section>`;
}

// Static, subsystem-independent content -- rendered once at the bottom of the top-level Diagrams
// listing screen instead of once per SPI Map subsystem tab (renderSpiMapExtrasHtml used to repeat
// this identical block on every tab). Text itself is read live from the
// platform-commons/CLAUDE.md#spi-implementation-rules arch-embed (MODEL.archEmbeds), not
// hand-copied here, so the two can't drift apart the way the old hardcoded version already had
// once (a stale CurrentActorHookImpl package path, fixed earlier this session).
function renderImplementationRulesHtml() {
  const toPathCode = t => esc(t).replace(/`([^`]+)`/g, '<code class="path">$1</code>');
  const items = parseImplementationRuleParagraphs()
    .map(it => `<div class="adr-item"><strong>${esc(it.heading.replace(/`/g, ""))}</strong>
        <ul class="info-list">
          <li>Location: ${toPathCode(it.location)}</li>
          ${it.example ? `<li>Example: ${toPathCode(it.example)}</li>` : ""}
        </ul>
      </div>`).join("");
  return `
    <section class="block"><h3>Implementation Rules</h3>
      ${items}
      <div class="empty-hint">All implementations follow these patterns — the core "pure delegation" rule is stated once, canonically, in ${sourceLink("platform-commons/CLAUDE.md")}'s "Hook and Port Implementation Rules" section, not restated here.</div>
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
  MODEL.spiMap.edges.forEach(e => { out += `    ${e.source} -->|${e.label || "implemented by"}| ${e.target}\n`; });
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
    md += `| Caller(s) | Interface | Direction | Implementation(s) | Purpose |\n|---|---|---|---|---|\n`;
    rows.forEach(d => {
      const callers = d.callers && d.callers.length ? d.callers.map(i => `${i.class} (${i.module})`).join("; ") : "no caller found";
      const impls = d.implementations.length ? d.implementations.map(i => `${i.class} (${i.module})`).join("; ") : "no implementation found";
      md += `| ${callers} | ${d.interface} | ${d.kind} | ${impls} | ${d.purpose} |\n`;
    });
    md += "\n";
    if (MODEL.spiMap.subsystemNotes[s]) md += `${MODEL.spiMap.subsystemNotes[s]}\n\n`;
  });
  md += `## Implementation Rules\n\nAll implementations follow these patterns (core "pure delegation" rule stated once, canonically, in platform-commons/CLAUDE.md's "Hook and Port Implementation Rules" section):\n\n`;
  parseImplementationRuleParagraphs().forEach(it => {
    md += `### ${it.heading}\n- Location: ${it.location}\n${it.example ? `- Example: ${it.example}\n` : ""}\n`;
  });
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

// Live from MODEL.boundedContexts (real @Table entities/*Service classes/tables/SPI ports per
// domain, real relationships detected from AuditTimelineRowRenderer.LABELED_ENTITY_TYPES,
// AuditActivityEnrichHook entityType() declarations, and other concrete signals -- see
// bounded_contexts_json() for exactly what backs each fact).
//
// Rendered via Cytoscape (renderModuleDependencyGraph()'s own engine), not Mermaid -- ADR-016
// documented a real, structural reason the original Cytoscape+dagre attempt was dropped for this
// diagram: nesting each domain's entities/services/tables/ports as *compound-child* nodes created
// cycles spanning compound boundaries (a domain's own child linking back to a sibling domain),
// which dagre's compound-aware ranking cannot resolve -- it collapsed the whole graph onto one
// column. This version sidesteps that failure mode at the root instead of retrying it: domains are
// flat, non-compound nodes (matching Module Dependencies' own shape) with no item children on the
// canvas at all -- entities/services/tables/ports live only in the "Domain Contents" table below
// and each domain's own module page (one click away), never as canvas nodes. A same-shape 2-node
// cycle still exists in the real relationships today (Orchestrator <-> Audit), but a cycle between
// two flat siblings is the ordinary case dagre handles fine -- it was never the failure mode ADR-016
// diagnosed. Gets the same native pan/zoom/click/drag interaction every other Cytoscape diagram
// already has for free, instead of the hand-rolled scroll-drag Mermaid fallback needed elsewhere.
// category: one of BC_CATEGORY_ORDER's keys ("orchestration"/"hooks"/"exceptions"/"derived"), or
// omitted/falsy for the full, unfiltered graph (used only by the Markdown export -- every live tab
// always passes its own category). Node set is restricted to domains actually touched by the
// filtered edges, not all 8 every time -- that's the real decluttering this split exists for.
function buildContextMapGraph(category) {
  const domains = MODEL.boundedContexts.domains.filter(d => d.id !== "Shared");
  const moduleOf = {};
  MODEL.boundedContexts.domains.forEach(d => { moduleOf[d.id] = d.module; });
  const edges = MODEL.boundedContexts.relationships
    .filter(r => r.label !== "decouples" && (!category || r.category === category))
    .map(r => ({
      source: moduleOf[r.from] || r.from, target: moduleOf[r.to] || r.to,
      label: r.label, dashed: r.dashed,
      // Carried through purely so the diagram's edge-tap handler can find this edge's real
      // Relationships-table row -- rowId must match bcRelRowId()'s own id exactly, since the
      // Cytoscape node ids above are module ids, not the domain ids the table is keyed by.
      rowId: bcRelRowId(r.from, r.to, r.label)
    }));
  const involved = new Set(edges.flatMap(e => [e.source, e.target]));
  const nodes = domains
    .filter(d => involved.has(d.module))
    .map(d => {
      const mn = moduleNodes.find(n => n.id === d.module);
      return { id: d.module, label: d.label, domain: mn ? mn.domain : null };
    });
  return { nodes, edges: edges.filter(e => nodes.some(n => n.id === e.source) && nodes.some(n => n.id === e.target)) };
}

function renderContextMapGraph(category) {
  const g = buildContextMapGraph(category);
  const els = [
    ...g.nodes.map(n => ({
      data: { id: n.id, label: n.label, domain: n.domain },
      style: { "background-color": domainColor(n.domain) }
    })),
    ...g.edges.map((e, i) => ({
      data: { id: "e" + i, source: e.source, target: e.target, label: e.label || "", dashed: e.dashed, rowId: e.rowId }
    }))
  ];
  const hasDagre = typeof cytoscapeDagre !== "undefined";
  diagramCy = cytoscape({
    container: document.getElementById("diagram-cy"),
    elements: els,
    style: [
      { selector: "node", style: {
          "label": "data(label)", "font-size": 11, "color": "#fff", "text-valign": "center", "text-halign": "center",
          "border-width": 0, "shape": "round-rectangle", "width": "label", "height": "label",
          "padding": "12px", "text-wrap": "wrap", "text-max-width": 130, "font-weight": 600
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
      ? { name: "dagre", rankDir: "TB", nodeSep: 30, rankSep: 80, padding: 20 }
      : { name: "breadthfirst", directed: true, padding: 20 }
  });
  diagramCy.on("zoom", () => {
    const label = document.getElementById("zoom-label");
    if (label) label.textContent = Math.round(diagramCy.zoom() * 100) + "%";
  });
  diagramCy.on("tap", "node", e => navigate({ screen: "module", id: e.target.id() }));
  diagramCy.on("tap", "edge", e => jumpToRelationshipRow(e.target.data("rowId")));
  diagramCy.nodes().style("cursor", "pointer");
  diagramCy.edges().style("cursor", "pointer");
}

// Scrolls to and briefly flashes the Relationships-table row a diagram edge maps to -- the
// evidence (which real method/class the arrow represents) lives in that row, not in a second copy
// on the canvas itself.
function jumpToRelationshipRow(rowId) {
  const row = document.getElementById(rowId);
  if (!row) return;
  row.scrollIntoView({ behavior: "smooth", block: "center" });
  row.classList.add("rel-row-flash");
  setTimeout(() => row.classList.remove("rel-row-flash"), 1500);
}

// Kept only for the Markdown export (a static text document has none of the live interaction
// concerns above) -- flat nodes/edges matching the live Cytoscape graph's own shape, not the old
// compound-subgraph-with-items version.
function buildContextMapMermaidSource() {
  const g = buildContextMapGraph();
  let out = "graph TB\n";
  g.nodes.forEach(n => { out += `    ${n.id}["${esc(n.label)}"]\n`; });
  g.edges.forEach(e => {
    const arrow = e.dashed ? "-.->" : "-->";
    out += `    ${e.source} ${arrow}|${e.label.replace(/\|/g, "/")}| ${e.target}\n`;
  });
  return out;
}

// Stable, collision-safe id for a relationship's <tr> -- shared between the row itself and the
// diagram's edge-tap handler (renderContextMapGraph()) so clicking an arrow can scroll straight to
// its evidence instead of leaving the reader to hunt for the matching row by eye.
function bcRelRowId(from, to, label) {
  return "rel-" + [from, to, label].join("|").replace(/[^a-zA-Z0-9]+/g, "-");
}

// Readable name as the link text, real file underneath -- same "short label, real link" pattern
// spiFileLink() already uses for SPI Map. Items with no file (Shared's plain count summaries)
// render as plain text, not a broken link.
function bcItemLink(item) {
  return item.file ? `<a href="../../${esc(item.file)}" target="_blank">${esc(item.name)}</a>` : esc(item.name);
}

// Plain-English meaning of each relationship label -- shown as a tooltip on the Label cell, since
// "calls"/"audited via"/"can have" read as cryptic shorthand without it. Keys match
// BC_LABEL_PAYLOAD in the bash generator exactly (both describe the same fixed label set).
const BC_LABEL_MEANING = {
  "calls": "The source domain directly invokes a method on the target domain's own Port interface (a real method call, not just a Maven dependency).",
  "audited via": "The source domain's entities get captured as audit-log snapshots through the Audit domain.",
  "can have": "The source domain's entities may carry attachment/media data, resolved through the Attachment domain at read time.",
  "category assignment via": "The source domain writes its taxon/category assignments through the target domain's Port.",
  "calls back via Hook implementations": "Reverse direction: the source domain (a starter) calls a Hook interface; the target domain supplies the real implementation."
};

// Category display label for the Markdown export's Category column -- keys match
// BC_CATEGORY_LABEL in the bash generator exactly (both describe the same fixed category set).
const BC_CATEGORY_LABEL_JS = {
  "orchestration": "Service Calls (BFF)",
  "hooks": "Hook Callbacks",
  "exceptions": "Cross-Starter Exceptions",
  "derived": "Derived Facts"
};

// Finds a real "<module>/src/main/java/.../ClassName.java[:line]" path inside a free-text evidence
// string and wraps just that substring in a real link to the file -- evidence text is a mix of
// prose and real paths (e.g. "X.java:82:      someCall()" or "N classes import org.ost.orchestrator.*"),
// so this only linkifies the part that's actually a path, leaving the rest as plain text.
function linkifyEvidence(text) {
  return esc(text).replace(/([\w.-]+\/src\/main\/java\/[\w./]+\.java)(:\d+)?/g,
    (m, path, line) => `<a href="../../${path}" target="_blank">${path}${line || ""}</a>`);
}

// activeCategory: the current tab's category key -- Domain Contents/Overview/Legend stay
// unfiltered (same domains/text every tab, same precedent as SPI Map's Overview/Legend/Call Flow
// Examples staying global while only the interface-details table filters per subsystem); only the
// Relationships table filters to the active tab's edges.
function renderBoundedContextsExtrasHtml(activeCategory) {
  const category = (label, items) => items.length
    ? `<div><span class="scope-label">${label}</span> ${items.map(bcItemLink).join(", ")}</div>` : "";
  const relationships = MODEL.boundedContexts.relationships
    .filter(r => r.label !== "decouples" && r.category === activeCategory);
  // Same domain set the diagram itself draws for this tab (buildContextMapGraph's own "involved"
  // set) -- Domain Contents lists only the domains this category's edges actually touch, not all 8
  // every tab, so the two sections never disagree about what's "on screen" for this category.
  const involvedIds = new Set(relationships.flatMap(r => [r.from, r.to]));
  const domains = MODEL.boundedContexts.domains.filter(d => d.id !== "Shared" && involvedIds.has(d.id));
  const domainRows = domains.map(d => {
    const body = [
      category("Entities", d.entities), category("Services", d.services),
      category("Tables", d.tables), category("Ports", d.ports)
    ].filter(Boolean).join("");
    return `<div class="adr-item"><strong>${esc(d.label)}</strong> <span class="scope-label">${esc(d.module)}</span>
      ${body || `<div class="empty-hint">(no directly-owned entities/services/tables)</div>`}
    </div>`;
  }).join("");
  const relRows = relationships.map(r => {
    const rowId = bcRelRowId(r.from, r.to, r.label);
    const meaning = BC_LABEL_MEANING[r.label] || "";
    return `<tr id="${rowId}"><td>${esc(r.from)} → ${esc(r.to)}</td><td title="${esc(meaning)}" style="cursor:help">${esc(r.label)}</td><td>${esc(r.payload)}</td><td>${linkifyEvidence(r.evidence)}</td></tr>`;
  }).join("");
  return `
    <section class="block"><h3>Overview</h3>
      <div class="empty-hint">Domain grouping (entities/services/tables/ports per box) is extracted live from real <code class="path">@Table</code> classes, <code class="path">*Service</code> classes, Liquibase tables, and SPI interface <code class="path">implements</code> relationships. Every relationship below is backed by a real, named code signal, all "extracted" (grep/AST match, never guessed or hand-typed) — see the Evidence column, or hover a Label cell for what it actually means. Not shown on the diagram: <code class="path">platform-commons</code> (the "Shared" module) is a compile-time dependency of every domain and Orchestrator — a real fact, but the same one repeated 7 times, so it's stated once here as text instead of drawn as 7 identical edges.</div>
    </section>
    <section class="block"><h3>Legend</h3>
      <div class="empty-hint">Click a domain box to open its real module page. Click an arrow to jump to its row in the Relationships table below. Drag empty canvas space to pan. This tab shows only the domains and relationships belonging to the current category — see the diagram-note above for what that category actually means.</div>
      <table class="simple"><tbody>
        <tr><td class="scope-label" style="width:150px">Box</td><td>A bounded context / domain — click to open its real module page. Its entities/services/tables/ports are listed in Domain Contents below, not on the canvas</td></tr>
        <tr><td class="scope-label">──▶ (solid line)</td><td>A real business relationship backed by a concrete code signal — click the arrow to jump to its evidence in the Relationships table below</td></tr>
      </tbody></table>
    </section>
    <section class="block"><h3>Domain Contents (${domains.length})</h3>${domainRows}</section>
    <section class="block"><h3>Relationships (${relationships.length}) — all "extracted" (real code signal, not hand-typed)</h3>
      <div class="empty-hint">Hover a Label cell for what it means. Evidence links open the real file where the relationship was found.</div>
      <table class="simple"><thead><tr><th>Relationship</th><th>Label</th><th>What crosses (payload type)</th><th>Evidence</th></tr></thead><tbody>${relRows}</tbody></table>
    </section>`;
}

function exportBoundedContextsMarkdown() {
  let md = `# Bounded Contexts — Context Map\n\n`;
  md += `Generated from architecture/architecture-map.html on ${new Date().toISOString().slice(0,10)}. Live version: docs/architecture/architecture-map.html › Diagrams › Bounded Contexts. The live version splits relationships across 4 tabs by category (Service Calls / Hook Callbacks / Cross-Starter Exceptions / Derived Facts); this export keeps all of them in one document, with a Category column.\n\n`;
  md += `## Context Map\n\n\`\`\`mermaid\n${buildContextMapMermaidSource()}\`\`\`\n\n`;
  md += `platform-commons ("Shared") is a compile-time dependency of every domain and Orchestrator -- not drawn above (same fact repeated 7 times), stated once here instead.\n\n`;
  md += `## Domain Contents\n\n`;
  MODEL.boundedContexts.domains.forEach(d => {
    md += `### ${d.label} — \`${d.module}\`\n\n`;
    const cat = (label, items) => { if (items.length) md += `**${label}:** ${items.map(i => i.name).join(", ")}\n\n`; };
    cat("Entities", d.entities); cat("Services", d.services); cat("Tables", d.tables); cat("Ports", d.ports);
  });
  md += `## Relationships\n\nAll rows below are "extracted" -- backed by a real grep/AST code signal, never guessed.\n\n| Relationship | Category | Label | What crosses | Evidence |\n|---|---|---|---|---|\n`;
  MODEL.boundedContexts.relationships.filter(r => r.label !== "decouples").forEach(r => {
    md += `| ${r.from} -> ${r.to} | ${BC_CATEGORY_LABEL_JS[r.category] || r.category} | ${r.label} | ${r.payload} | ${r.evidence} |\n`;
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
      html += `<div class="domain-group"><h3>${esc(g.label)} <code class="path">(${esc(g.file)})</code> ${graphType ? '<span class="badge ACTIVE">draggable</span>' : ""}</h3>
        ${g.description ? `<div class="empty-hint">${esc(g.description)}</div>` : ""}
        <div class="card-grid">`;
      g.diagrams.forEach((d, i) => {
        html += `<div class="card" onclick="navigate({screen:'diagrams',groupKey:'${g.key}',diagramIndex:${i}})">
          <div class="card-title">${esc(d.title || g.label)}</div>
        </div>`;
      });
      html += `</div></div>`;
    });
    html += renderDiagramsOverviewHtml();
    html += renderImplementationRulesHtml();
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
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. Rendered from real Java source (this subsystem's platform-commons/*/spi interfaces + their real callers/\`implements\` across starters/marketplace-app/marketplace-orchestrator), click a node to open its real .java file — drag a node to reposition it, drag empty canvas space to pan the view.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>
      ${renderSpiMapExtrasHtml(d.subsystem)}`;
    document.getElementById("content").innerHTML = html;
    renderSpiMapGraph(d.subsystem);
  } else if (g.key === "bounded-contexts") {
    // Rendered via Cytoscape (renderContextMapGraph(), same engine/interaction as Module
    // Dependencies and SPI Map -- see that function's own comment for why the original 2026
    // Cytoscape attempt was dropped (DECISIONS.md ADR-016) and why this flat-node version doesn't
    // hit the same failure mode). Domain contents and relationships are generated live from
    // MODEL.boundedContexts (real code signals -- see the Overview section below) -- no markdown
    // source at all, same "live, not a second copy" pattern as 01/02/04 (see DECISIONS.md
    // ADR-019/ADR-020).
    html += `<div class="diagram-note">${esc(g.label)} — ${esc(d.title || "")}. ${esc(d.description || "")} Click a domain to open its real module page, click an arrow to jump to its evidence row below. Drag to pan, or use the zoom controls below.</div>
      ${zoomControlsHtml}
      <div class="diagram-wrap" id="diagram-cy-wrap" style="padding:0"><div id="diagram-cy" style="width:100%;height:70vh"></div></div>
      ${renderBoundedContextsExtrasHtml(d.category)}`;
    document.getElementById("content").innerHTML = html;
    renderContextMapGraph(d.category);
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
  else if (view.screen === "adrs") renderAdrs();
  else if (view.screen === "codequality") renderCodeQuality();
  else renderSystem();
}

// ── ADRs screen: flat, deduplicated list of every ADR across every module's DECISIONS.md, read
// live from .claude/nav/adr-index.md (MODEL.allAdrs) -- no separate hand-maintained summary.
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
    <div class="screen-desc">${adrs.length} architectural decisions across every module's own DECISIONS.md — see ${sourceLink(".claude/nav/adr-index.md")} for the generated index this screen reads, and the Overview section at the bottom of this screen for what an ADR is and how it's used. Clicking an ADR opens its full text inline only when the model was generated with <code>--with-adr-details</code> (opt-in, off by default); otherwise it opens the real DECISIONS.md file directly.</div>`;
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
    // (docs/architecture/scripts, scripts/ci, etc.) stay plain text, no dead/wrong-feeling navigation.
    const grpNode = byId[grp.module];
    const grpHeading = grpNode && grpNode.type === "MODULE"
      ? `<a class="module-link" onclick="navigate({screen:'module', id:'${esc(grp.module)}'})">${esc(grp.module)}</a>`
      : esc(grp.module);
    html += `<section class="block"><h3>${grpHeading} (${grp.items.length}) ${sourceLink(grp.module + "/DECISIONS.md")}</h3>
      <table class="simple"><thead><tr><th>ADR</th><th>Status</th><th>Title</th></tr></thead><tbody>`;
    grp.items.forEach(a => {
      html += `<tr><td><a onclick="openAdrPopupForAdr('${esc(a.id)}', '${esc(a.module)}', '${esc(a.title)}', '${esc(a.status)}')">${esc(a.id)}</a></td><td>${esc(a.status)}</td><td>${esc(a.title)}</td></tr>`;
    });
    html += `</tbody></table></section>`;
  });

  html += `<section class="block"><h3>Overview</h3>` + GLOSSARY.map(g => `<div class="glossary-item"><strong>${esc(g.term)}</strong>${g.body}</div>`).join("") + `</section>`;

  document.getElementById("content").innerHTML = html;
}

// Always opens the popup -- title/status come from MODEL.allAdrs (always populated, passed in
// directly from the calling row so there's no second lookup) -- the body is the full embedded
// text only if that module's full ADR content was embedded (see .claude/nav/adr-index.md and
// FULL_DECISIONS_MODULES above, gated behind --with-adr-details); otherwise a real link to the
// source file plus a generic pointer to where that flag is documented, not a copy of the exact
// command here (kept deliberately decoupled from one script's exact CLI shape). Takes the ADR id +
// home module directly since the ADRs screen's list is flat across every module, not scoped to one
// node.
function openAdrPopupForAdr(id, module, title, status) {
  const bareId = id.split(" (")[0];
  const homeNode = byId[module];
  const found = homeNode && homeNode.decisions && homeNode.decisions.adrs.find(x => x.id === bareId);
  document.getElementById("adr-popup-title").textContent = `${id} — ${title}`;
  document.getElementById("adr-popup-status").textContent = status;
  if (found) {
    document.getElementById("adr-popup-body").innerHTML = mdBlockToHtml(found.body, module);
  } else {
    document.getElementById("adr-popup-body").innerHTML =
      `<div class="empty-hint">Full text not included in this build. Read it directly: ${sourceLink(module + "/DECISIONS.md")}</div>
       <div class="empty-hint">For how to include full ADR text inline here, see <a onclick="document.getElementById('adr-popup').close(); navigate({screen:'pipelines'})">Tooling &amp; Pipelines</a>.</div>`;
  }
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
    ${sourceLink(".claude/nav/adr-index.md")} is a generated, searchable flat index of every ADR
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

render();
</script>
</body>
</html>
HTML_TAIL

echo "Wrote $HTML_OUTPUT"

arch_embed_index_md > "$ARCH_EMBED_INDEX"
echo "Wrote $ARCH_EMBED_INDEX"

exit 0
