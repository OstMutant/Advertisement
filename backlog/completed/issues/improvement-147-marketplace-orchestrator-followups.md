# improvement-147: marketplace-orchestrator package layout, Bounded Contexts diagram, true-BFF migration

**Type:** improvement — tracked continuation of `improvement-136`'s extraction.
**Module:** `marketplace-orchestrator`, `marketplace-app`, `scripts/architecture`
**Priority:** 🔴 top — moved to the very top of the backlog 2026-08-07 per explicit user request.
**When:** Independent, no blockers — all three sections below are self-contained.

**Note:** this issue originally also tracked whether `TaxonAssignmentWriteService`/
`AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService` are justified as shared
collaborators given each has exactly one caller today. That question depends entirely on
`improvement-124` Batch 124-C (`ProviderProfileSaveService`, not yet built) landing as the real
second consumer — moved there in full (see that issue's Batch 124-C section) since it can't be
answered independently of that batch's own plan, and leaving it split across two files just meant
neither one could ever fully close.

## Also in scope: flatten `marketplace-orchestrator`'s package layout into one `services` folder

Today the module's 9 service classes are scattered across 5 domain-scoped sub-packages:

| Current path | New path |
|---|---|
| `shared/ActorLookupService.java` | `services/ActorLookupService.java` |
| `shared/AttachmentSnapshotReaderService.java` | `services/AttachmentSnapshotReaderService.java` |
| `shared/AttachmentSoftDeleteService.java` | `services/AttachmentSoftDeleteService.java` |
| `shared/TaxonAssignmentWriteService.java` | `services/TaxonAssignmentWriteService.java` |
| `shared/TaxonLookupService.java` | `services/TaxonLookupService.java` |
| `advertisement/enrich/AdvertisementDisplayEnrichmentService.java` | `services/AdvertisementDisplayEnrichmentService.java` |
| `advertisement/save/AdvertisementSaveService.java` | `services/AdvertisementSaveService.java` |
| `providerprofile/enrich/ProviderProfileDisplayEnrichmentService.java` | `services/ProviderProfileDisplayEnrichmentService.java` |
| `user/delete/UserDeleteService.java` | `services/UserDeleteService.java` |

Explicit user request: consolidate all of them into a single flat `org.ost.orchestrator.services`
package, not scattered per-domain sub-packages. `config/OrchestratorAutoConfiguration.java` is not
a service and stays in `config`.

When this is picked up, it also requires:
- Updating package declarations in all 9 files plus their test classes
  (`AdvertisementSaveServiceTest.java`, `UserDeleteServiceTest.java`).
- Updating every `marketplace-app` import of these classes (`AdvertisementsView`,
  `AdvertisementFormOverlayModeHandler`, `AdvertisementCardView`, `UserView`,
  `OgMetaRequestListener`, `SitemapController`, `AdvertisementAuditEnrichService`).
- Rewriting `marketplace-orchestrator/CLAUDE.md`'s "What it owns" section to describe the flat
  layout instead of the current per-domain sub-package list.
- Updating `backlog/issues/improvement-124-provider-profile.md`'s Batch 124-C section, which
  currently names the future `ProviderProfileSaveService`'s package as
  `org.ost.orchestrator.providerprofile.save` — that must become `org.ost.orchestrator.services`
  to match the flattened layout, so a new service built for Batch 124-C lands in the right place
  from the start.

## Also in scope: Bounded Contexts diagram has no `marketplace-orchestrator` node

`scripts/architecture/generate-architecture-model.sh` derives every Bounded Contexts domain from
two sources only (lines 124-140): the two hardcoded structural entries
`declare -A BC_DOMAIN_MODULE=([Shared]=platform-commons [UI]=marketplace-app)`, plus every
`pom.xml` module whose name matches `*-spring-boot-starter` (line 128:
`[[ "$bc_mod" == *-spring-boot-starter ]] || continue`). `marketplace-orchestrator` matches neither
— it isn't a starter and isn't one of the two hardcoded structural ids — so it never enters
`BC_DOMAIN_ORDER`/`BC_DOMAIN_MODULE` and gets no box on the diagram at all.

Compounding this, the `"UI calls X"` relationship (lines 1031-1033) is drawn unconditionally for
every domain in `BC_DOMAIN_ORDER_STARTERS`:
```bash
for d in "${BC_DOMAIN_ORDER_STARTERS[@]}"; do
  add_rel "UI" "$d" "calls" "extracted" "marketplace-app injects a ComponentFactory/ObjectProvider for ${BC_DOMAIN_MODULE[$d]}'s port(s)" "false"
done
```
This draws a direct `UI → <starter>` edge for every starter regardless of whether `UI` (marketplace-app)
actually holds a direct `ComponentFactory<XPort>` for that starter's port or only reaches it
transitively through the orchestrator — it doesn't inspect real orchestrator source at all.

Suggested fix (do together with the true-BFF migration below, not before it, since the correct
edges depend on which files still hold direct Port access once that lands):
1. Add `Orchestrator` as a third structural domain id (alongside `Shared`/`UI`), mapped to
   `marketplace-orchestrator`, with its `ports`/`services` populated the same way starter domains
   are (real `*Service.java` files under the module, real `implements *Port` grep for evidence).
2. Replace the unconditional `UI → <starter>` loop with real evidence: grep marketplace-app source
   for actual `ComponentFactory<XPort>`/direct `*Port` fields per starter (only draw `UI → <starter>`
   where that's still true after the BFF migration) and grep `marketplace-orchestrator` source the
   same way for `Orchestrator → <starter>` edges, plus a `UI → Orchestrator` edge wherever
   marketplace-app actually injects an orchestrator service.

## Also in scope: make `marketplace-app` a true BFF client — zero direct `*Port` access

`improvement-136`'s own user-authored spec
(`completed/issues/improvement-136-marketplace-orchestrator-extraction.md`, verbatim block "Goal"
through "Final rule") contains an internal contradiction: its target diagram (line ~132-146) shows
`Vaadin UI → marketplace-orchestrator → domain starters` with **no** direct UI-to-starter arrow at
all — a strict BFF. But `NON-NEGOTIABLE ARCHITECTURAL RULES` #4 (also user-authored, same verbatim
block) only bans `marketplace-app` from composing **multiple** domain Ports for one use case — it
implicitly allows direct single-Port access, which is what actually got built. This gap wasn't
flagged during `improvement-136`'s Phase 0/1 planning and should have been.

Confirmed by direct grep + an `Explore` agent mapping every real method call: 25 classes in
`marketplace-app/src/main/java` hold a direct `ComponentFactory<XPort>` (or, in 3 cases —
`UserFormOverlayModeHandler`'s `UserPort`/`UserAccountPort`, `SettingsFormModeHandler`'s
`UserPreferencesPort` — a direct mandatory `*Port` field, since `user-spring-boot-starter` is
compile-scope, never optional). Below is the concrete migration target for every one of them —
6 orchestrator-level services (2 new, 1 extended, 3 already existing and reused as-is), plus 4
open questions that need an explicit decision before this is implemented, not an assumption.

### New/extended orchestrator services (`org.ost.orchestrator.services`, per the flattened layout above)

| Service | Wraps | Methods | Status |
|---|---|---|---|
| `AdvertisementReadService` | `ComponentFactory<AdvertisementPort>` | `findById`, `getFiltered`, `count` | **new** |
| `TaxonCatalogService` | `ComponentFactory<TaxonPort>` | `getAllByType`, `listAllByType`, `getUsageCounts`, `create`, `update`, `findById(id, locale)`, `getTranslations` | **new** — distinct from existing `TaxonLookupService` (kept as-is: narrower, entity-assignment lookups only — `getForEntities`/`getForEntity`/`findByIds`/`findById`) |
| `AttachmentMediaService` | `ComponentFactory<AttachmentPort>` + `ComponentFactory<AttachmentAuditPort>` (2 ports — within Rule 13) | Full gallery lifecycle (`getByEntityId`, `getByEntityAndUrls`, `getUrlsBySnapshotId`, `commitTempUploads`, `captureSnapshot`, `discardTempUploads`, `upload`, `uploadTemp`, `delete`, `addVideo`, `addVideoTemp`, `restoreToUrls`) + `getMediaStateForSnapshot`, `getChangesBySnapshotId`. Reuses existing `AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService` internally rather than re-wrapping `getLatestSnapshotId`/`softDeleteAll` a second time. | **new** |
| `AuditReadService` | `ComponentFactory<AuditPort>` | `getLastSnapshot`, `getEntityActivity`, `getSnapshotContent`, `getTimelinePage`, `countTimeline` | **new** |
| `ActorLookupService` | `ComponentFactory<UserPort>` | existing `findByIds`/`findById` + new `findActorNames`, `findDeletedIds` | **extended** (already exists in `orchestrator.services`) |
| `UserProfileService` | `UserPort` + `UserAccountPort` + `UserPreferencesPort` (mandatory direct fields, matching `UserDeleteService`'s existing `UserAccountPort accountPort` precedent — not counted against Rule 13) | `findById`, `save(userDto, userId)`, `loadSettings`, `saveSettings` | **new** |
| `EntityExistenceService` | `ComponentFactory<AdvertisementPort>` + `ComponentFactory<UserPort>` + `ComponentFactory<TaxonPort>` + `ComponentFactory<ProviderProfilePort>` (4 ports — **named exception** to the ≤2-port rule, see Question B) | `findExisting(EntityType, Set<Long>)` — pure per-`EntityType` dispatch, no cross-port logic | **new** |

### Per-file repoint target

| File | Repoints to | Notes |
|---|---|---|
| `config/seo/OgMetaRequestListener.java` | `AdvertisementReadService.findById` | not Vaadin — a request listener; still an "adapter" per the target diagram (REST API (future) also goes through the orchestrator) |
| `rest/SitemapController.java` | `AdvertisementReadService.getFiltered` | same as above |
| `ui/views/main/tabs/advertisements/AdvertisementsView.java` | `AdvertisementReadService.getFiltered`/`count`/`findById` (deep-link) | |
| `ui/views/main/tabs/advertisements/overlay/modes/AdvertisementFormOverlayModeHandler.java` | `AdvertisementReadService.findById` + `TaxonCatalogService.getAllByType` + `AuditReadService.getSnapshotContent` (drops direct `AdvertisementPort`/`TaxonPort`/`AuditPort`; `AttachmentPort` usage already indirect via gallery component) | biggest single consumer — 3 of the 4 ports it currently touches move |
| `ui/views/main/tabs/advertisements/overlay/modes/AdvertisementViewOverlayModeHandler.java` | existing `TaxonLookupService.getForEntity` (no new method needed — already built for exactly this) | `AttachmentPort` usage here is presence-guard only, see Open Question A |
| `ui/views/main/tabs/advertisements/query/AdvertisementQueryBlock.java` | `TaxonCatalogService.getAllByType` | |
| `ui/views/main/tabs/referencedata/TaxonManagementView.java` | `TaxonCatalogService.listAllByType`/`getUsageCounts` | |
| `ui/views/main/tabs/referencedata/CityManagementView.java` | `TaxonCatalogService.listAllByType`/`getUsageCounts` | |
| `ui/views/main/tabs/referencedata/overlay/modes/TaxonFormOverlayModeHandler.java` | `TaxonCatalogService.create`/`update`/`findById`/`getTranslations` + `AuditReadService.getSnapshotContent` | |
| `ui/views/main/tabs/referencedata/overlay/modes/CityFormOverlayModeHandler.java` | same as above | identical shape to `TaxonFormOverlayModeHandler` |
| `ui/views/main/tabs/referencedata/overlay/TaxonOverlay.java` | `TaxonCatalogService.findById` | |
| `ui/views/main/tabs/referencedata/overlay/CityOverlay.java` | `TaxonCatalogService.findById` | |
| `ui/views/main/tabs/referencedata/overlay/modes/TaxonViewOverlayModeHandler.java` | `TaxonCatalogService.getTranslations` | |
| `ui/views/main/tabs/referencedata/overlay/modes/CityViewOverlayModeHandler.java` | `TaxonCatalogService.getTranslations` | |
| `ui/views/components/attachment/AttachmentGallery.java` | `AttachmentMediaService` (all 13 methods) | |
| `ui/views/components/attachment/AttachmentGalleryService.java` | `AttachmentMediaService.getByEntityId` | |
| `ui/views/components/audit/AuditActivityPanel.java` | `AuditReadService.getLastSnapshot`/`getEntityActivity` | |
| `ui/views/main/tabs/timeline/TimelineView.java` | `AuditReadService.getTimelinePage`/`countTimeline` | |
| `ui/views/main/tabs/users/overlay/modes/UserFormOverlayModeHandler.java` | `UserProfileService.findById`/`save` + `AuditReadService.getSnapshotContent` | |
| `services/advertisement/AdvertisementAuditEnrichService.java` | `AttachmentMediaService.getMediaStateForSnapshot`/`getChangesBySnapshotId` | class itself stays in marketplace-app (ADR-073 — needs `LocaleProvider`/`I18nService`); only its `ComponentFactory<AttachmentAuditPort>` field moves |
| `services/user/UserActorNameService.java` | `ActorLookupService.findActorNames`/`findDeletedIds` | investigate whether this class still earns its keep as a marketplace-app-side wrapper once it's pure delegation, or whether its callers can call `ActorLookupService` directly |
| `ui/views/main/header/settings/SettingsFormModeHandler.java` | `UserProfileService.loadSettings`/`saveSettings` | `AuditPort` field is presence-guard only, see Open Question A |
| `ui/views/main/MainView.java` | Open Question A (presence-guard only, no real data call) | |
| `ui/views/main/tabs/advertisements/AdvertisementCardView.java` | Open Question A (presence-guard only, no real data call) | |
| `spi/AuditDomainHookImpl.java` | new `EntityExistenceService.findExisting` (4-port exception, see Question B) | `findExisting()` becomes one delegating line; `resolveNames()`/`castIfKnown()`/`resolveDisplayName()` unaffected |

### Open questions — need an explicit decision, not an assumption

**A. Presence-only `ifAvailable()` gates** (`MainView`/`TaxonPort`, `AdvertisementCardView`/`AttachmentPort`,
`SettingsFormModeHandler`/`AuditPort`, `AdvertisementViewOverlayModeHandler`'s `AttachmentPort` field)
— these never fetch real data, only check "is this optional starter present" to decide whether to
render a tab/button. `marketplace-orchestrator/CLAUDE.md` already carves this out as *not*
orchestration ("moving that check into this module would add a service round-trip for what is
today a zero-cost local `Optional` check"). Keep this carve-out as-is (still a direct `*Port`
reference, technically not "zero"), or route even presence checks through the orchestrator for
literal purity?

**Decided: route through the orchestrator too.** For literal BFF purity — no direct `*Port`
reference of any kind survives in marketplace-app, including presence-only checks. This means
`marketplace-orchestrator/CLAUDE.md`'s "UI presence-guards are not orchestration" carve-out is
superseded for these 4 call sites and needs to be corrected/removed as part of this migration, not
just left as an accepted exception.

**B. `AuditDomainHookImpl`** — a `*Hook` implementation: the *starter* calls *into* marketplace-app
here, the reverse of every other row in this table. `marketplace-orchestrator/CLAUDE.md` already
says hooks stay in marketplace-app regardless of port count. Does "zero direct Port access" apply
to this reverse-direction case at all, or is it out of scope by definition (not a UI→backend call)?

**Decided: route through the orchestrator too, with a granted exception to the ≤2-port rule for
the new router class.** `findExisting(EntityType, Set<Long>)`'s 4-way switch has no cross-port
logic to extract into a `shared.*` collaborator — each branch is one direct, unrelated
`port.findExistingIds()` call, so splitting it into 4 single-port classes plus a coordinator would
be pure ceremony with no cohesion benefit. Case-by-case exceptions to the ≤2-port rule are allowed
when justified and the result stays concise (evaluated on merit each time, not a blanket rule
change) — this dispatcher qualifies: it is pure per-`EntityType` routing, categorically different
from the "assemble one read-model from N ports" shape the rule exists to prevent.

Concretely:
- New orchestrator class `org.ost.orchestrator.services.EntityExistenceService` (flattened layout,
  per the services-folder section above) with `findExisting(EntityType, Set<Long>)`, holding all 4
  `ComponentFactory<XPort>` fields directly (`AdvertisementPort`/`UserPort`/`TaxonPort`/
  `ProviderProfilePort`).
- `ArchitectureRulesTest.orchestrator_classes_depend_on_at_most_two_domain_ports` needs an explicit,
  named allowlist entry for this one class (not a loosened threshold) — the test should still fail
  a *new* class that exceeds 2 ports without going through the same justification.
- `marketplace-orchestrator/CLAUDE.md`'s ≤2-port rule gains a sentence documenting this as the one
  granted exception and why, so a future reader doesn't read the allowlist as an oversight.
- `AuditDomainHookImpl.findExisting()` becomes a single delegating line — `return
  entityExistenceService.findExisting(entityType, entityIds);` — matching the `*HookImpl` "pure
  delegation only" rule more closely than it does today (today's inline switch is itself a mild
  violation of that rule).
- `resolveNames()` still delegates to `UserActorNameService` — that class already moves to call
  `ActorLookupService` per its own repoint row above, so it stops holding a direct `UserPort`
  itself once that lands.
- `castIfKnown()`/`resolveDisplayName()` touch no port at all — unchanged.

**C was withdrawn — not a real decision point.** It was originally listed here alongside B out of
over-caution, not because a genuine second shape existed to weigh against it. The field move (drop
`ComponentFactory<AttachmentAuditPort>`, call the new `AttachmentMediaService` instead) is a
mechanical consequence of the same repoint every other row in the table gets, with no alternative
design on the table — unlike B, where the ≤2-port exception was a real fork. Noting the resulting
shape for the record only: once the port field is gone, the class holds `TaxonLookupService` +
`AttachmentMediaService` (both orchestrator) plus `LocaleProvider`/`I18nService` (marketplace-app
UI-formatting infra, out of scope for the orchestrator by design) — zero `*Port` types, nothing
further to restructure.

**D. Sequencing** — this is a large, mechanical but wide-reaching change (23 files beyond the 2
already-exempted-by-design ones). Land it as one batch, or split into smaller PRs per Port group
(Taxon/City first since it's the largest single cluster, then Advertisement, then
Attachment/Audit/User)?

**Decided: one big batch.** Not split into smaller PRs per Port group.

## Related

- `completed/issues/improvement-136-marketplace-orchestrator-extraction.md` — where these 3 classes
  were built and first questioned, and where the BFF-vs-Rule-4 contradiction originates.
- `improvement-124` (F-04, Batch 124-C) — where the real second caller (`ProviderProfileSaveService`)
  is now planned; its own issue file section is the source of truth for that work, not this one.
- `marketplace-orchestrator/DECISIONS.md` ADR-001, ADR-003.
- `scripts/architecture/generate-architecture-model.sh` lines 124-140 (domain discovery), 1031-1033
  (`UI calls` edge) — Bounded Contexts diagram gap.

## Implementation notes

All three sections landed in one batch (per Question D). One class was renamed mid-implementation
from the plan's original `AuditReadService` to `AuditQueryService`: the planned name collided with
`audit-spring-boot-starter`'s own pre-existing internal `org.ost.audit.services.AuditReadService`,
producing an identical default Spring bean name and a `ConflictingBeanDefinitionException` at boot
— invisible to every unit/integration test (none of them boot the full `@SpringBootApplication`
context with every starter's autoconfiguration present at once), only caught by an actual
`deploy.sh` container boot. See `marketplace-orchestrator/DECISIONS.md` ADR-003 for the full
writeup. A grep sweep confirmed no other new service name collides with an existing class anywhere
else in the repo.

`/code-review --fix` (8 finder angles + 1-vote verification on every surviving candidate) found:
one real, fixed issue (`AdvertisementFormOverlayModeHandler.save()` gated its write call on a
sibling read-service's `isAvailable()` instead of its own save-service's — functionally harmless
since both wrap the same Spring bean registry, but a genuine clarity/maintainability defect; fixed
by adding `AdvertisementSaveService.isAvailable()` and repointing); several REFUTED candidates
(chip-row empty-list rendering, `UserActorNameService` atomicity — both absorbed by pre-existing
guards/mandatory-dependency facts); one PLAUSIBLE-but-pre-existing finding (`TaxonManagementView`/
`CityManagementView`'s "Add" button already rendered unconditionally *before* this migration too,
not a regression this diff introduced — proposed for the deferred-findings bucket
(`improvement-133`), not fixed here, pending approval); and a cluster of efficiency findings (
`isAvailable()` + separate call now does 2-3 independent `ComponentFactory` lookups where one
`.ifAvailable(lambda)` block used to do one) — real but cheap (in-memory `ObjectProvider` lookups,
not I/O), left as-is rather than redesigning several services' APIs for a proportionately small
constant-factor cost.

## Operational notes
- token_cost_review: ~1,425,538 (8 finder-angle agents ~1,054,069 + 4 verifier agents ~371,469, all `/code-review` purpose)
- token_cost_research: n/a (the implementation agent combined research and building; no standalone research-purpose agent was launched)
- token_cost_verification: n/a (test execution was direct script runs via Bash/Monitor, not Agent-tool calls; the code-review verifiers are already counted under token_cost_review)
- context_loading_task_type: Cross-module feature
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: pre-scoped multi-step architectural migration batch, already agreed in conversation
- flows_chosen: /autopilot
- flows_matched: yes
