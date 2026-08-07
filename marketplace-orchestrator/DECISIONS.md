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
