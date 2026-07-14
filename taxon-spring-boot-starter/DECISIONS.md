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
**Status:** Accepted (done 2026-06-26)

**Context:** `TaxonAssignmentService.replaceAssignments()` processes a diff between old and new
assignment sets. The diff may add N items and remove M items in a single call.

**Decision:** `TaxonAuditHook.onAssignmentChanged()` is called once per individual add/remove
operation inside the diff loop — not once per batch. This produces one `audit_log` row per taxon
assignment change, giving fine-grained history.

**Consequences:** A batch of 5 adds + 3 removes produces 8 audit_log entries. This is intentional
for full audit traceability. If performance becomes a concern, consider a batch hook variant.
