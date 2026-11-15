# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: The module list in pom.xml reactor order, the Liquibase changelog files to read, and the domain-to-module mapping with the bounded-context tables derived from each module's own pom.xml.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, sed, grep, find.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: Root pom.xml, each module's pom.xml and db/*/changes/ changelogs.
# Outputs: defines MODULES, DB_ERD_CHANGELOG_FILES and the BC_* domain, entity, label, hook and category tables.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Module list, in pom.xml reactor order ───────────────────────────────────────────────────
mapfile -t MODULES < <(sed -n '/<modules>/,/<\/modules>/p' "$REPO_ROOT/pom.xml" \
  | grep -o '<module>[^<]*</module>' | sed 's/<module>\(.*\)<\/module>/\1/')

# ── DB_ERD_CHANGELOG_FILES: discover every changelog under each module's own db/*/changes/ that
# holds a real createTable, instead of a hand-typed path per module -- see the comment near this
# array's declaration further up for why. ──────────────────────────────────────────────────────
DB_ERD_CHANGELOG_FILES=()
for erd_mod in "${MODULES[@]}"; do
  while IFS= read -r erd_file; do
    [ -z "$erd_file" ] && continue
    grep -q '<createTable\|<setColumnRemarks' "$erd_file" && DB_ERD_CHANGELOG_FILES+=("${erd_file#"$REPO_ROOT/"}")
  done < <(find "$REPO_ROOT/$erd_mod/src/main/resources/db" -mindepth 3 -maxdepth 3 -name "*.xml" 2>/dev/null | sort)
done

# ── Domain <-> module mapping, derived live from each module's own pom.xml
# <properties><architecture.boundedContext> declaration -- self-describing, not a hardcoded list of
# module names here. A module with no such property (query-lib, html-sanitizer-lib, integration-tests) is simply not a
# bounded context and gets no box on the diagram; this is how a brand-new module type (e.g.
# marketplace-orchestrator's "orchestrator" kind) becomes visible on this diagram automatically,
# from the same commit that adds the module, with no separate edit to this script required.
# See docs/architecture/scripts/DECISIONS.md ADR-019 "Open goals" (superseded by this ADR entry) and the
# new ADR recorded alongside this change.
declare -A BC_DOMAIN_MODULE=() BC_DOMAIN_LABEL=() BC_DOMAIN_KIND=()
BC_DOMAIN_ORDER=() BC_DOMAIN_ORDER_STARTERS=()
bc_shared_mod="" bc_ui_mod="" bc_orch_mod="" bc_restapi_mod="" bc_mcp_mod=""
for bc_mod in "${MODULES[@]}"; do
  bc_kind="$(sed -n '/<properties>/,/<\/properties>/p' "$REPO_ROOT/$bc_mod/pom.xml" 2>/dev/null \
    | grep -o '<architecture.boundedContext>[^<]*</architecture.boundedContext>' \
    | sed 's/<architecture.boundedContext>\(.*\)<\/architecture.boundedContext>/\1/')" || true
  [ -n "$bc_kind" ] || continue
  case "$bc_kind" in
    shared) bc_shared_mod="$bc_mod" ;;
    ui) bc_ui_mod="$bc_mod" ;;
    orchestrator) bc_orch_mod="$bc_mod" ;;
    rest-api) bc_restapi_mod="$bc_mod" ;;
    mcp) bc_mcp_mod="$bc_mod" ;;
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
if [ -n "$bc_restapi_mod" ]; then
  BC_DOMAIN_MODULE[RestApi]="$bc_restapi_mod"; BC_DOMAIN_LABEL[RestApi]="External REST API"
  BC_DOMAIN_KIND[RestApi]="rest-api"; BC_DOMAIN_ORDER+=(RestApi)
fi
if [ -n "$bc_mcp_mod" ]; then
  BC_DOMAIN_MODULE[Mcp]="$bc_mcp_mod"; BC_DOMAIN_LABEL[Mcp]="MCP Server Adapter"
  BC_DOMAIN_KIND[Mcp]="mcp"; BC_DOMAIN_ORDER+=(Mcp)
fi
if [ -n "$bc_ui_mod" ]; then
  BC_DOMAIN_MODULE[UI]="$bc_ui_mod"; BC_DOMAIN_LABEL[UI]="UI/Application Layer"
  BC_DOMAIN_KIND[UI]="ui"; BC_DOMAIN_ORDER+=(UI)
fi
# EntityType enum value -> the bounded-context domain it belongs to (platform-commons/core/model/EntityType.java).
declare -A BC_ENTITY_TYPE_DOMAIN=(
  [ADVERTISEMENT]=Advertisement [USER]=User [USER_SETTINGS]=User [TAXON]=Taxon [PROVIDER_PROFILE]=ProviderProfile
  [FEEDBACK]=Feedback [FEEDBACK_COMMENT]=Feedback
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
  [AuditDomainHook]="Map<Long,String> (resolveNames), Set<Long> (findExisting), Optional<AuditSnapshotContentDto<T>> (castIfKnown)"
  [AuditActivityEnrichHook]="List<AuditTimelineItemDto<T>> (merge), List<AuditActivityItemDto<T>> (enrichActivity), String media state (getMediaStateForSnapshot)"
  [CurrentActorHook]="Optional<Long> (getCurrentActorId)"
  [UiLabelHook]="String (translateActorDeletedSuffix)"
  [SessionActorHook]="Optional<Long> (getCurrentActorId)"
  [UserSettingsChangedHook]="UserSettingsDto (onSettingsChanged, after a settings save)"
)

# ── Bounded Contexts category split -- the same relationships render with the same arrow+label
# visual regardless of how different their real nature is (a genuine BFF call vs. a reverse Hook
# callback vs. a derived, not-a-call fact), which was the actual source of user confusion ("чому
# стартери зі стартерами, аудіт з юай, а ми ж казали БФФ"). One diagram tab per category (mirrors
# SPI_SUBSYSTEM_ORDER/SPI_SUBSYSTEM_LABEL's per-subsystem split above for SPI Map).
# BC_LABEL_CATEGORY maps every existing BC_LABEL_PAYLOAD key except "decouples" (never drawn -- see
# add_rel's Shared loop) to one of the 3 categories below. A 4th category, "exceptions" (Cross-
# Starter Exceptions -- a starter-to-starter Port call bypassing the orchestrator, e.g.
# TaxonPort.replaceAssignments() called from provider-profile/advertisement instead of
# marketplace-orchestrator's TaxonAssignmentWriteService), existed here until it was superseded by a
# real, build-enforced ArchUnit rule
# (marketplace-app/.../architecture/ArchitectureRulesTest.java's
# starters_must_route_taxon_assignment_writes_through_orchestrator) -- the diagram category always
# rendered zero relationships once that specific bypass was fixed (see this file's own git history)
# and now can never render any again, so removed rather than kept as permanently-empty documentation
# for something a failing build already guarantees.
declare -a BC_CATEGORY_ORDER=(orchestration hooks derived)
declare -A BC_CATEGORY_LABEL=(
  [orchestration]="Service Calls (BFF)"
  [hooks]="Hook Callbacks"
  [derived]="Derived Facts"
)
declare -A BC_CATEGORY_DESC=(
  [orchestration]="Forward-direction real Port calls -- Orchestrator composing each domain, UI and the REST API calling Orchestrator. The BFF pattern working as intended."
  [hooks]="Reverse-direction calls -- a starter or Orchestrator calls a *Hook interface it depends on; marketplace-app/marketplace-orchestrator supplies the real implementation. Dependency inversion, not orchestration -- does not violate the BFF principle."
  [derived]="Not code calls at all -- classification/composition facts derived from data (which EntityTypes get audited, which domains can carry attachments)."
)
declare -A BC_LABEL_CATEGORY=(
  ["calls"]="orchestration"
  ["calls back via Hook implementations"]="hooks"
  ["audited via"]="derived"
  ["can have"]="derived"
)

