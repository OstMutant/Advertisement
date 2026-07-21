# taxon-spring-boot-starter — Decisions

---

## ADR-001: Filter resolves through `Set<Long>`, not SQL JOIN
**Status:** Accepted

**Context:** Advertisement filtering by category requires joining `taxon_assignment` to
`advertisement`. A direct JOIN would couple `AdvertisementRepository` SQL to `taxon_assignment`
table — a starter-owned table that the marketplace module must not reference directly.

**Decision:** `TaxonPort.findEntityIdsWithAnyTaxon(entityType, taxonIds)` returns a `Set<Long>`
of matching entity ids. `AdvertisementRepository.findByFilter()`/`.countByFilter()` accept this
set as the `allowedIds` parameter (corrected 2026-07-13 — written as `advertisementIds` originally;
verified the actual parameter name in `AdvertisementRepository.java`) — no SQL JOIN between
marketplace tables and starter tables.

**Consequences:** Any new filtering that involves taxon data must follow the same pattern —
resolve to a `Set<Long>` of entity ids first, then hand off to the caller's existing id-restriction clause.
`AdvertisementRepository` SQL remains valid even if the taxon starter is absent from the classpath.

**Trigger to revisit:** If a single category routinely resolves to >10k advertisement ids in
production, consider a starter-owned JOIN helper or a pre-computed materialised mapping (note,
2026-07-13: no `WARN` log for this threshold currently exists in `AdvertisementService` — this
trigger is not yet instrumented; add the log if/when this is actually revisited, don't assume it's
already firing).

---

## ADR-002: DefaultTaxonPort is a coordination layer, not pure delegation
**Status:** Accepted (done 2026-06-26)

**Context:** `TaxonPort` methods return `TaxonDto` with translated names. The translation resolution
requires: (1) loading taxon entries, (2) loading translations in the requested locale, (3) falling
back to `TaxonProperties.defaultLocale` when no translation exists for the requested locale.
This three-step process cannot live in a single `*Service` call without exposing internals.

**Decision:** `DefaultTaxonPort` is permitted to contain coordination logic — locale fallback chain,
DTO assembly from service results — while keeping all persistence logic in `TaxonService` and
`TaxonAssignmentService`. This is explicitly documented as an exception to the "pure delegation"
rule for `*PortImpl` classes (see `platform-commons/CLAUDE.md`).

**Consequences:** `DefaultTaxonPort` is annotated `@Service` (not `@Component`) because it holds
business coordination logic. Logic that belongs only in persistence (SQL, sorting, filtering) stays
in the repository and services.

---

## ADR-003: TaxonType enum is a closed set in platform-commons
**Status:** Accepted (done 2026-06-26)

**Context:** Taxon types (`CATEGORY`, future: `TAG`) drive which taxon management tabs appear in
the UI, which audit translation keys are used, and which seed data is loaded. If a new type were
added without updating these three concerns, the app would behave incorrectly at runtime.

**Decision:** `TaxonType` enum lives in `platform-commons/taxon.model`. Currently contains only
`CATEGORY`. Adding a new type is a release-level change requiring: (1) new UI tab in
`ReferenceDataView`, (2) new i18n keys in `I18nKey`, (3) Liquibase seed entry, (4) audit
translation coverage.

**Consequences:** `TaxonType` must never be added to as a "quick hack". Each addition must be
tracked via an ADR entry here.

---

## ADR-004: TaxonAuditHook fires per assignment change, not per batch
**Status:** Superseded 2026-07-17 (improvement-058) — `TaxonAuditHook` removed entirely

**Context:** `TaxonAssignmentService.replaceAssignments()` processes a diff between old and new
assignment sets. The diff may add N items and remove M items in a single call.

**Original decision (no longer in effect):** `TaxonAuditHook.onAssignmentChanged()` was called once
per individual add/remove operation inside the diff loop — not once per batch. This produced one
`audit_log` row per taxon assignment change, giving fine-grained history.

**Why superseded:** `TaxonAuditHook` never gained an implementation, and both of its call sites
(both routed through an advertisement save or delete) already sit inside a flow that produces its
own audit snapshot covering the same net information. The per-change firing granularity this ADR
designed for was never actually consumed by anything. Removed rather than implemented — see
`marketplace-app/DECISIONS.md` ADR-019 and ADR-043, `platform-commons/DECISIONS.md` ADR-017's note.
`TaxonAssignmentService.replaceAssignments()` still computes the same add/remove diff internally
(needed for the `INSERT`/`DELETE` statements themselves) — only the per-item hook call was removed.

---

## ADR-005: `TaxonRepository.findByIds()` now returns soft-deleted rows too
**Status:** Accepted (done 2026-07-21)

**Context:** improvement-045 originally added a `deleted_at IS NULL` filter to `findByIds()` to
match every other query in the class. This had a real, unintended side effect: `DefaultTaxonPort`'s
only caller of this method — `indexById()`, feeding both `getForEntity()` (advertisement view
overlay's category chips) and the port-level `findByIds()` (used by `AdvertisementEnrichService`
to resolve category names in audit diffs) — could never see a soft-deleted category at all. In
practice this meant: (1) a category soft-deleted while still assigned to an advertisement silently
vanished from that advertisement's view overlay instead of showing struck-through, and (2) audit
diffs referencing that category rendered a bare numeric id instead of its name, since the name
could never be resolved (verified directly: the SQL filter, not a resolution failure elsewhere, was
the root cause — improvement-008/101).

**Decision:** Removed `AND deleted_at IS NULL` from `TaxonRepository.findByIds()`'s SQL. The
existing `activeOnly` boolean already threaded through `DefaultTaxonPort.resolveDtos()`/
`buildDtoIndex()` now does real work instead of being permanently moot: `getForEntity()` passes
`false` (deleted categories now surface, tagged via `TaxonDto.deleted`, so the UI can render them
struck-through); every other caller (`getForEntities()`, `getAllByType()`, the port-level
`findByIds()`) is unaffected in net behavior — `getForEntities()`/`getForEntity()` bulk-listing
callers still pass/effectively resolve to active-only where that was already the case, and the
port-level `findByIds()` already passed `activeOnly=false`, so it now correctly resolves
soft-deleted taxon names instead of silently omitting them from the result map.

**Consequences:** Any *new* caller of `TaxonRepository.findByIds()` (directly or via
`DefaultTaxonPort.indexById()`) must explicitly decide and pass its own `activeOnly` filtering
intent — the method itself is now a plain by-id lookup with no built-in soft-delete exclusion.
`TaxonRepositoryTest.findByIds_excludesSoftDeletedRows` (improvement-045) was rewritten to
`findByIds_includesSoftDeletedRows`, asserting the new contract.
