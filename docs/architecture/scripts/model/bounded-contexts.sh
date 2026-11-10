# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Bounded Contexts: relations between domains (orchestration, hooks, derived) read live from real source, reusing the domain tables.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, grep, find, sed.
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: Java sources of the orchestrator, the starters, marketplace-app and marketplace-rest-api, the BC_* tables from the domains fragment.
# Outputs: defines bounded_contexts_json.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

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
      ports_json="$(json_named_file_array "$( { find "$REPO_ROOT/platform-commons/src/main/java" -path '*/spi/*.java'; find "$REPO_ROOT/marketplace-orchestrator/src/main/java" -path '*/spi/*.java'; } | sort | while read -r ifile; do
        iface="$(basename "$ifile" .java)"
        owns=2
        spi_owns_iface "$mod" "$iface" && owns=0 || owns=$?
        if [ "$owns" = 2 ]; then
          grep -qlP "implements\s+.*\b${iface}\b" -r --include='*.java' "$REPO_ROOT/marketplace-app/src/main/java/org/ost/marketplace/spi" 2>/dev/null && owns=0
        fi
        [ "$owns" = 0 ] && printf '%s\t%s\n' "$iface" "${ifile#"$REPO_ROOT"/}"
      done)")"
    elif [ "$d" = "Orchestrator" ]; then
      entities_json="[]"; tables_json="[]"
      # *Service.java (services/, composition/lookup) and *HookImpl.java (spi/, Hook implementations
      # that only need domain-port access -- see .claude/rules/marketplace-orchestrator.md) are two
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
        owns=2
        spi_owns_iface "$mod" "$iface" && owns=0 || owns=$?
        if [ "$owns" = 2 ]; then
          grep -qlP "ComponentFactory<\s*${iface}\s*>|\b${iface}\s+\w+\s*;|implements\s+.*\b${iface}\b" -r --include='*.java' "$REPO_ROOT/$mod/src/main/java" 2>/dev/null && owns=0
        fi
        [ "$owns" = 0 ] && printf '%s\t%s\n' "$iface" "${ifile#"$REPO_ROOT"/}"
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
        owns=2
        spi_owns_iface "$mod" "$iface" && owns=0 || owns=$?
        if [ "$owns" = 2 ]; then
          grep -qlP "implements\s+.*\b${iface}\b" -r --include='*.java' "$REPO_ROOT/$mod/src/main/java" 2>/dev/null && owns=0
        fi
        [ "$owns" = 0 ] && printf '%s\t%s\n' "$iface" "${ifile#"$REPO_ROOT"/}"
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
    local pf p_iface p_evidence ui_orch_ev restapi_orch_ev
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
    # RestApi -> Orchestrator: same real signal as UI -> Orchestrator above, for
    # marketplace-rest-api's own source instead of marketplace-app's -- the external REST API is a
    # second real consumer of the orchestrator, never a domain-composition layer itself (it has no
    # ComponentFactory<XPort> usage of its own at all -- enforced by ArchitectureRulesTest's
    # marketplace_app_must_not_depend_on_platform_commons_spi_directly rule, scoped to also cover
    # org.ost.restapi.. -- so no RestApi -> starter direct-call loop exists here, unlike UI's, since
    # it would always find nothing by construction, not just by current coincidence).
    if [ -n "$bc_restapi_mod" ]; then
      restapi_orch_ev="$(grep -rl "import org\.ost\.orchestrator\." "$REPO_ROOT/$bc_restapi_mod/src/main/java" --include='*.java' 2>/dev/null | wc -l | tr -d ' ')" || true
      if [ "${restapi_orch_ev:-0}" -gt 0 ]; then
        add_rel "RestApi" "Orchestrator" "calls" "extracted" "$restapi_orch_ev $bc_restapi_mod classes import org.ost.orchestrator.*" "false"
      fi
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

  # A starter-side direct TaxonPort.replaceAssignments() call (bypassing marketplace-orchestrator's
  # own TaxonAssignmentWriteService) used to be detected here via a regex regression guard and drawn
  # as a "category assignment via" / Cross-Starter Exceptions relationship. Removed once a real,
  # build-enforced ArchUnit rule replaced it (marketplace-app/.../architecture/
  # ArchitectureRulesTest.java's starters_must_route_taxon_assignment_writes_through_orchestrator) --
  # a failing build is a stronger guarantee than a diagram category that could only ever report the
  # violation after the fact, at the next documentation regeneration.

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

