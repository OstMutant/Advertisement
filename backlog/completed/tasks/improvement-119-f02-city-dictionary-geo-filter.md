# improvement-119: F-02 — city dictionary + geo filter

**Type:** feature — product roadmap Phase 1, item #2 (private/roadmap.md), ships right after F-01.
**Module:** `platform-commons` (`TaxonType`, `AdvertisementFilterDto`/`InfoDto`),
`advertisement-spring-boot-starter` (`AdvertisementService` — no schema change),
`marketplace-app` (query block, form/view overlay, card, new `City*` admin classes alongside the
existing `Taxon*` ones).
**Priority:** high — next unblocked Phase 1 item; a shared link must land on a city-filterable
catalog or visitors bounce (per `private/roadmap.md`).
**When:** independent, no blockers — F-01 shipped 2026-07-24; improvement-007/improvement-004
(execution-plan's bundled prerequisites) already resolved 2026-07-11.

## Problem

Local services are geo-first ("плиточник у Луцьку" is the actual query) — without a city facet
the catalog is unusable past one city and every listing is unfilterable noise. Full spec:
`private/features/F-02-city-dictionary-filter.md` (gitignored product doc; this issue is the
trackable counterpart).

## Suggested fix

Reuses the existing generic taxon dictionary **and its existing assignment mechanism** via a new
`TaxonType.CITY` — no new tables, no new columns, no new FK anywhere, no new admin abstraction.
Verified against current code, not assumed. Two earlier drafts of this issue proposed a
`city_taxon_id` column + FK on `advertisement`, and a Liquibase seed changeset for city rows —
**both explicitly rejected**, see the callouts below for why.

1. **No schema change to `advertisement` at all.** A city is just another `taxon_assignment` row
   (`entity_type='ADVERTISEMENT', taxon_id=<city id>`) — the exact same mechanism categories
   already use, via the existing `TaxonPort.replaceAssignments()`/`getForEntity(s)`. Confirmed
   `taxon_assignment` has no FK to `advertisement` at all (generic `entity_type`/`entity_id`, PK
   across both + `taxon_id`) — `advertisement-spring-boot-starter`'s schema stays exactly as
   taxon-agnostic as it is today (`ComponentFactory<TaxonPort>` stays a genuinely optional
   dependency, no hard FK forcing the taxon table to always exist).
   **Rejected: `city_taxon_id BIGINT REFERENCES taxon(id)` on `advertisement`.** An earlier draft
   proposed this, copying the private spec's wording literally without checking it against this
   project's actual decoupling pattern — a direct FK from `advertisement`'s table to `taxon`'s
   table would be a hard SQL-level coupling between two starters that today have zero schema
   dependency on each other.
2. **`platform-commons`:** add `CITY` to `TaxonType` enum (release-level change per
   `taxon-spring-boot-starter/CLAUDE.md`'s existing checklist — UI, audit translations, seed
   entries via the admin UI, see point 5). Add `cityTaxonId`/`cityName` to
   `AdvertisementInfoDto`/`AdvertisementFilterDto`, following `categoryIds`'s exact shape —
   resolved the same way (`AdvertisementService.resolveCategoryFilter()`'s
   `TaxonPort.findEntityIdsWithAnyTaxon()` pattern already generalizes directly to city filtering,
   same code path, just called with the selected city's id instead of a category set — no new SQL
   binding needed).
3. **`AdvertisementService`:** `enrichWithCategories()`'s bulk `taxonPort.getForEntities(...)` call
   **is not type-filtered** — confirmed `TaxonAssignmentService.getForEntity(s)` takes only
   `(entityType, entityId[s])`, no `TaxonType` parameter, so it returns categories and city
   assignments mixed together in one list per entity. `enrichWithCity()` (new method, same shape
   as `enrichWithCategories()`) must partition the existing result by `TaxonDto.getType()` rather
   than issue a second port call — verify during implementation whether `enrichWithCategories()`
   should be refactored to share one `getForEntities()` call with `enrichWithCity()` (one bulk
   lookup, split client-side by type) instead of two separate port calls per list render.
4. **`marketplace-app` — new, separate `City*` classes by analogy, not a generalized/parameterized
   `Taxon*`.** `TaxonManagementView`/`TaxonOverlay`/`TaxonFormOverlayModeHandler`/
   `TaxonViewOverlayModeHandler` get mirrored into an analogous set —
   `CityManagementView`/`CityOverlay`/`CityFormOverlayModeHandler`/`CityViewOverlayModeHandler`
   (same package, `ui/views/main/tabs/referencedata/` and its `overlay/` subpackage), hardcoded to
   `TaxonType.CITY`, not a shared class parameterized by `TaxonType`. Reason: `TaxonOverlay` is a
   `@UIScope` singleton — if categories and cities render as two simultaneous tabs, they need two
   distinct bean instances, not one shared parameterized instance fought over by both tabs.
   `ReferenceDataView` gains a second tab ("Cities") alongside the existing "Categories" tab.
   Matches this project's existing preference for duplication over premature abstraction when the
   only real commonality between two screens is structural shape, not shared behavior.
   **Rejected: generalizing `TaxonManagementView` to take a `TaxonType` parameter.** An earlier
   draft proposed this; reverted in favor of the copy-by-analogy approach above.
5. **Initial city list (~30 cities):** entered by hand through the new `CityManagementView` admin
   UI after deploy — **not** a Liquibase seed changeset.
   **Rejected: a new Liquibase `<insert>`-based seed changeset for city rows.** An earlier draft
   proposed this despite its own research finding that zero such changesets exist anywhere in this
   project today — categories aren't seeded that way either, they're created exclusively through
   the running admin UI (`overlay.openForCreate()` → `TaxonPort.create()`). Follow that existing
   convention instead of inventing a new one.
6. **Advertisement card/form/view visualization — mirror the existing category rendering exactly,
   no new visual pattern:**
   - View overlay: one city chip, reusing the existing per-chip category rendering shape (`Span` +
     `advertisement-category-chip`-style CSS, `role="listitem"`) with a new
     `advertisement-city-chip` class.
   - Card: a small, separate city badge placed below the categories line (categories render as
     one joined comma-line via `createCategoriesLine()`; city is a single value, so this is a new
     sibling element positioned underneath it, not an addition to that method).
   - Form: a single-select `ComboBox<TaxonDto>` (categories use `MultiSelectComboBox` — no
     existing single-select precedent in this codebase, this is genuinely new UI), fed via
     `taxonPort.getAllByType(TaxonType.CITY, locale)`, same data-loading call shape as categories.
   - `AdvertisementQueryBlock`: the same single-select `ComboBox<TaxonDto>`, wired through a new
     `AdvertisementFilterMeta.CITY_TAXON_ID`.
7. **i18n:** typed `I18nKey` entries for the city filter label, form field, and chip — no dynamic
   keys (existing project rule).

## Explicitly out of scope

- PostGIS / real geo-coordinates — taxon-based city granularity is sufficient until "masters near
  me" is an actual requested feature (per spec).
- Nova Poshta/КАТОТТГ full city list — hand-entered ~30 cities via the admin UI is correct for
  launch.
- "Remember last selected city" (`UserSettingsDto`) — the private spec mentions this, but no
  existing filter field currently persists across sessions this way (page size does, via
  `SettingsPaginationBinding`, a different mechanism) — deferred to a follow-up once the base
  filter ships, not bundled into this issue's initial scope.

## Related

- `private/features/F-02-city-dictionary-filter.md` — full product spec (gitignored).
- `private/execution-plan.md` — bundles this with `improvement-007`/`improvement-004`, both
  already resolved 2026-07-11 (`completed/BACKLOG-ARCHIVE.md`).
- `taxon-spring-boot-starter/CLAUDE.md` — `TaxonType` extension checklist; `DECISIONS.md` ADR-005
  (existing `activeOnly` filtering-intent convention relevant to a new `getAllByType(CITY, ...)`
  caller).
- `marketplace-app/DECISIONS.md` ADR-019 (category-name resolution at read time — same pattern
  this issue's `enrichWithCity()` follows).
- Should ship together with **F-03** (`private/features/F-03-listing-types.md`) per the spec —
  worth filing as a paired issue once F-02's scope is confirmed.
