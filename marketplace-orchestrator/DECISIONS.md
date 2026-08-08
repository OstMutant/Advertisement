# marketplace-orchestrator — Decisions

---

## ADR-001: Extract a dedicated Application/BFF module instead of moving orchestration into marketplace-app
**Status:** Accepted

**Context:** Two domain starters (`advertisement-spring-boot-starter`,
`provider-profile-spring-boot-starter`) each grew a service that reaches into 2-3 sibling domains'
Ports (`TaxonPort`/`UserPort`/`AttachmentPort`) purely to assemble display data for its own Port's
return DTO — a starter orchestrating other domains, which the root `CLAUDE.md`'s module boundaries
never intended. The pattern had already spread once (copy-pasted from Advertisement to
ProviderProfile while building the Portfolio/Profile feature), and `marketplace-app` itself already
held equivalent cross-domain orchestration (`AdvertisementSaveService`, `UserDeleteService`) with no
architectural boundary distinguishing "UI adapter" concerns from "cross-domain use case" concerns.

A narrower fix (move the two enrichment services into `marketplace-app/services/*`, mirroring the
existing `AdvertisementAuditEnrichService` precedent) was considered and rejected: it would have
left `marketplace-app` permanently responsible for both UI rendering and cross-domain composition,
with no room to add a REST adapter later without re-extracting orchestration out of a
Vaadin-entangled monolith.

**Decision:** A new module, `marketplace-orchestrator`, sits between `marketplace-app` (kept as a
thin UI/application-shell adapter) and the domain starters. It owns application-level use-case
composition; domain starters keep only their own bounded-context logic; `marketplace-app` calls
orchestrator services instead of composing multiple domain Ports directly. Every real cross-domain
call site in the repository was inventoried before deciding what moves — see the full discovery
(Phase 0) and target-architecture (Phase 1) writeup preserved in
`backlog/completed/issues/improvement-136-marketplace-orchestrator-extraction.md` for the complete
evidence trail, including the classes that were deliberately *not* moved and why.

**Consequences:** Root `CLAUDE.md`'s "Architecture Guidelines" now describes three layers, not two.
Two new ArchUnit rules (`orchestrator_classes_depend_on_at_most_two_domain_ports`,
`orchestrator_has_no_persistence_access`) keep this module from becoming the next
`AdvertisementEnrichmentService`-shaped god-service one layer up. `AdvertisementPort`/
`ProviderProfilePort` dropped their `Locale` parameter (see `platform-commons/DECISIONS.md`) since
the only reason either port needed it was the enrichment logic that now lives here.

**Trigger to revisit:** If a future REST adapter is actually built, confirm the orchestrator's
existing application-facing services (e.g. `AdvertisementSaveService.save()`'s `commitGallery`
callback parameter) are genuinely adapter-agnostic before reusing them as-is — flagged as an open
question at extraction time, not verified against a real second caller yet.

---

## ADR-002: `AdvertisementSaveService`'s cascade-cleanup-on-delete folds into the same transaction, not a separate step
**Status:** Accepted

**Context:** `AdvertisementService.delete()` (the starter) used to clean up the advertisement's own
taxon assignments and attachments before soft-deleting the row, all inside one
`@Transactional` method that simply joined whatever outer transaction `AdvertisementSaveService.delete()`
(then in `marketplace-app`) had already started (Spring's default `REQUIRED` propagation, one shared
`DataSource`/transaction manager). Moving this cleanup into the orchestrator raised a question: does
folding it into `AdvertisementSaveService.delete()` fragment that transaction, or conflict with the
"orchestrator never touches persistence" rule?

**Decision:** Neither. The transaction boundary was always owned by `AdvertisementSaveService`
(via `TransactionTemplate`), not by the starter's own `@Transactional` — the starter's cleanup calls
were always just Port calls happening inside that same transaction. Moving them into
`AdvertisementSaveService.delete()` directly (via the shared `TaxonAssignmentWriteService`/
`AttachmentSoftDeleteService` collaborators) is not a new transaction-boundary concern, just
relocating which class issues the same Port calls. `AdvertisementService.delete()` (the starter)
shrank to a single `repository.softDelete(...)` call — no longer needs its own `@Transactional` at
all, since a single repository call always participates in whatever transaction its caller already
holds.

**Consequences:** `AdvertisementService` (starter) no longer depends on `AttachmentPort` at all —
its only remaining cross-domain dependency is `TaxonPort`, used solely for query-time category-filter
resolution (a different, deliberately-not-moved concern — see `marketplace-orchestrator/CLAUDE.md`).

---

## ADR-003: `marketplace-app` becomes a true BFF client — zero direct domain `*Port` access, one named exception
**Status:** Accepted

**Context:** ADR-001 built this module as a composition layer, but its own guiding spec (preserved
verbatim in `backlog/completed/issues/improvement-136-marketplace-orchestrator-extraction.md`)
contained an internal contradiction never caught during that extraction: the target diagram showed
`Vaadin UI → marketplace-orchestrator → domain starters` with no direct UI-to-starter arrow at all,
but the accompanying rule only banned `marketplace-app` from composing *multiple* domain Ports for
one use case — implicitly allowing direct single-Port access, which is what actually got built. 25
classes in `marketplace-app` ended up holding a direct `ComponentFactory<XPort>` (or, for the
non-optional `user-spring-boot-starter`, a mandatory direct `*Port` field).

**Decision:** Route every remaining direct domain-Port reference in `marketplace-app` through the
orchestrator instead, including the 4 presence-only `ifAvailable()` gates that were previously
carved out as "not orchestration" (that carve-out is now removed — see
`marketplace-orchestrator/CLAUDE.md`'s history). Six services in the flattened
`org.ost.orchestrator.services` package absorb this: `AdvertisementReadService`, `TaxonCatalogService`,
`AttachmentMediaService`, `AuditQueryService` (new), `ActorLookupService` (extended), and
`UserProfileService` (new, mandatory direct `UserPort`/`UserAccountPort`/`UserPreferencesPort`
fields, matching `UserDeleteService`'s existing precedent since `user-spring-boot-starter` is never
optional). A seventh, `EntityExistenceService`, is a deliberate, named exception to the module's
own ≤2-port rule — it holds all 4 of `AdvertisementPort`/`UserPort`/`TaxonPort`/`ProviderProfilePort`
directly for `AuditDomainHookImpl`'s per-`EntityType` existence-check routing, allow-listed by name
in `ArchitectureRulesTest` rather than by loosening the threshold, since its `findExisting()` has no
cross-port composition logic to extract into a shared collaborator — splitting it into four
single-port classes plus a coordinator would be ceremony with no cohesion benefit.

One deliberate residual exception remains: `AccessEvaluator` keeps a direct `UserAuthorizationPort`
field. Role/ownership checks (`isAdmin`/`isModerator`/`isOwner`) are the security boundary itself,
not a domain read-model this module composes — the same category as a `*Hook`, called on nearly
every render across the whole UI, where an orchestrator round-trip would cost real latency for no
architectural benefit. `user-spring-boot-starter` is non-optional, so this carries no decoupling
risk either.

Alongside this, the module's 9 pre-existing service classes (scattered across 5 domain-scoped
sub-packages: `shared/`, `advertisement/enrich/`, `advertisement/save/`, `providerprofile/enrich/`,
`user/delete/`) moved into one flat `org.ost.orchestrator.services` package, so every service —
old and new — lives in one place instead of forcing a reader to know which sub-package a given
service happens to sit in.

**Consequences:** `scripts/architecture/generate-architecture-model.sh`'s Bounded Contexts diagram
now derives `UI → <starter>` edges from real evidence (grepping `marketplace-app` for actual
`ComponentFactory<XPort>`/direct `*Port` fields) instead of drawing one unconditionally for every
starter — after this migration, the only surviving edge is `UI → User` (via `AccessEvaluator`).
`Orchestrator → <starter>` and `UI → Orchestrator` edges are populated the same evidence-based way.
Every marketplace-app class that used to gate a UI section on `ComponentFactory<XPort>.ifAvailable()`
now gates on an orchestrator service's own `isAvailable()` method instead — behaviorally identical,
since the underlying `ComponentFactory` resolves against the same Spring bean registry regardless of
which class injects it.

**Bug found and fixed during verification:** the new service was first named `AuditReadService`,
which collided with `audit-spring-boot-starter`'s own internal `org.ost.audit.services.AuditReadService`
— identical simple class name means an identical default Spring bean name
(`auditReadService`), so the app failed to boot at all
(`ConflictingBeanDefinitionException`) despite every unit/integration test passing cleanly (none of
them boot the full `@SpringBootApplication` context with every starter's autoconfiguration on the
classpath at once). Caught only by an actual `deploy.sh` + container boot, not by any Maven-level
test — renamed to `AuditQueryService` and re-verified boot succeeds. Confirmed via a full grep sweep
that no other new service name collides with an existing class elsewhere in the repo.

**Trigger to revisit:** None currently open — Open Questions A/B/C from
`backlog/completed/issues/improvement-147-marketplace-orchestrator-followups.md` are all resolved
(A: route presence-guards through the orchestrator; B: the `EntityExistenceService` exception; C:
withdrawn, not a real design fork). The module's original single-caller-collaborator question
(`TaxonAssignmentWriteService`/`AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService`) moved
to `backlog/issues/improvement-124-provider-profile.md`'s Batch 124-C, unrelated to this ADR.
