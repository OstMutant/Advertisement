# Taxonomy — Architecture

## Module placement

A new starter, **`taxon-spring-boot-starter`**, is introduced at the same level as `audit-spring-boot-starter` and `attachment-spring-boot-starter`.

```
advertisement-parent
├── platform-commons              ← SPI + DTOs for taxonomy
├── query-starter
├── audit-spring-boot-starter
├── attachment-spring-boot-starter
├── taxon-spring-boot-starter   ← NEW
└── marketplace-app
```

Java package root for the starter: `org.ost.taxon`.

The starter is optional at compile-time the same way audit and attachment are. Marketplace declares a runtime dependency on it; all UI integration uses `ObjectProvider.ifAvailable(...)`.

## Data model

Three tables, all owned by the starter, all managed via its own Liquibase changelog `db/taxon-changelog/`. The set of taxon types is fixed in code as an `enum` (see `TaxonType` in `platform-commons`), so there is no `taxon_type` table — types are stable infrastructure, not user-managed data.

```sql
taxon (
  id                BIGSERIAL PRIMARY KEY,
  type              VARCHAR(64) NOT NULL,            -- TaxonType enum name: 'CATEGORY', ...
  code              VARCHAR(64),                     -- stable lookup key for well-known entries ('GENERAL'); nullable for user-created ones
  deleted_at        TIMESTAMP,
  created_at        TIMESTAMP NOT NULL,
  updated_at        TIMESTAMP NOT NULL,
  created_by        BIGINT,
  updated_by        BIGINT,
  UNIQUE (type, code)                                -- only when code is non-null; partial unique index
)

taxon_translation (
  taxon_id          BIGINT NOT NULL REFERENCES taxon(id) ON DELETE CASCADE,
  locale            VARCHAR(8) NOT NULL,             -- 'uk', 'en'
  name              VARCHAR(255) NOT NULL,
  description       VARCHAR(2000) NOT NULL,
  PRIMARY KEY (taxon_id, locale)
)

taxon_assignment (
  entity_type       VARCHAR(64) NOT NULL,            -- matches platform EntityType
  entity_id         BIGINT NOT NULL,
  taxon_id          BIGINT NOT NULL REFERENCES taxon(id),
  assigned_at       TIMESTAMP NOT NULL,
  assigned_by       BIGINT,
  PRIMARY KEY (entity_type, entity_id, taxon_id)
)
```

### Indexes

- `taxon(type, deleted_at)` — for listing active entries by type from the management grid.
- Partial unique index on `taxon(type, code) WHERE code IS NOT NULL` — for stable lookup of well-known entries (e.g. the default category by code `GENERAL`).
- `taxon_translation(locale, name)` — for uniqueness checks and autocomplete-style lookups.
- `taxon_assignment(taxon_id)` — for usage-count queries from the management view.
- `taxon_assignment(entity_type, entity_id)` — for "what categories does this ad have?" (covered by PK prefix).

### Uniqueness rules

- `(type, locale, name)` uniqueness is enforced at the **application layer** (validated by `TaxonService` before insert/update). A database constraint would require joining `taxon → taxon_translation`, which is awkward at the schema level; app validation is sufficient and gives better error messages.
- `(type, code)` uniqueness is enforced by partial unique index at the DB level for non-null `code` values.

### Soft-delete semantics

- `taxon.deleted_at IS NOT NULL` ⇒ entry is hidden from selectors and from the default management list.
- A soft-deleted entry remains referenced by historical `taxon_assignment` rows. Past audit records can still resolve the name.
- The management view has a "show deleted" toggle and a "restore" action that nulls out `deleted_at`.

## SPI (in `platform-commons`)

All cross-module contracts live in `platform-commons/taxon/...`, following the existing semantic split. The set of taxon types is part of the SPI surface — it lives in `model/` as an enum (same package convention as `core.model.EntityType`):

```
platform-commons/taxon/
├── api/        (none for now — no marker annotations needed)
├── model/
│   └── TaxonType             (enum: CATEGORY, [TAG, STATUS...] added in future releases)
├── dto/
│   ├── TaxonDto         (id, type, code, name, description, deleted)
│   └── TaxonTranslationDto   (locale, name, description)
└── spi/
    ├── TaxonPort             (marketplace → starter, data ops)
    ├── TaxonUiPort           (marketplace → starter, UI components)
    └── TaxonAuditHook        (starter → marketplace, audit events)
```

`TaxonType` is intentionally a closed enum in code rather than a database table. Rationale:
- Adding a new taxon type is never just data — it always requires UI integration (where the selector lives), audit translations, and seed entries. So the "create a new type" operation cannot be exposed safely to end users; it is a release-level change.
- Closed enums match the existing pattern (`Role`, `EntityType`, `ActionType`) and give compile-time safety at every call site.

### `TaxonPort` (marketplace → starter)

```java
public interface TaxonPort {

    /** Assigns an entry to an entity (idempotent). */
    void assign(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long entryId);

    /** Removes an assignment (no-op if absent). */
    void unassign(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long entryId);

    /** Replaces all assignments for an entity with the given set in one transaction. */
    void replaceAssignments(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Set<Long> entryIds);

    /** Active (non-deleted) entries currently assigned to an entity, localised to the given locale. */
    List<TaxonDto> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Locale locale);

    /** Batched variant: returns assignments for many entities in one call. Avoids N+1 when rendering card lists. */
    Map<Long, List<TaxonDto>> getForEntities(@NonNull EntityType entityType, @NonNull Set<Long> entityIds, @NonNull Locale locale);

    /** All active entries of a given taxon type, localised. */
    List<TaxonDto> getAllByType(@NonNull TaxonType type, @NonNull Locale locale);

    /** Resolves a specific entry by id (even if soft-deleted — used by audit rendering). */
    Optional<TaxonDto> findById(@NonNull Long entryId, @NonNull Locale locale);

    /** Resolves a well-known entry by its stable code (e.g. the default category). */
    Optional<TaxonDto> findByCode(@NonNull TaxonType type, @NonNull String code, @NonNull Locale locale);

    /**
     * Returns the set of entity ids (of the given entity type) that have AT LEAST ONE of the given taxons
     * assigned. Used by marketplace to build category filtering without exposing the {@code taxon_assignment}
     * table name to the marketplace SQL layer. Empty input set → empty result.
     */
    Set<Long> findEntityIdsWithAnyTaxon(@NonNull EntityType entityType, @NonNull Set<Long> taxonIds);
}
```

### `TaxonUiPort` (marketplace → starter)

```java
public interface TaxonUiPort {

    /** Multi-select chip combo for use in entity edit forms. */
    Component buildSelector(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull TaxonType type);

    /** Read-only chip strip for entity view mode. */
    Component buildChips(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull TaxonType type);

    /**
     * Read-only chip strip rendered from pre-loaded data. Used by card lists where the marketplace has
     * already batched taxons via {@link TaxonPort#getForEntities}, so the component must not re-query.
     */
    Component buildChipsFromData(@NonNull List<TaxonDto> taxons);

    /**
     * Multi-select filter field for the query block. Renders a chip combo populated with all active taxons
     * of the given type. The caller registers a listener (or binds the component) to react to selection
     * changes — the field exposes the chosen taxon ids via a standard {@code HasValue<?, Set<Long>>}.
     */
    Component buildFilterField(@NonNull TaxonType type);

    /** Tab content for the Reference Data management view — owned and rendered by the starter. */
    Component buildManagementContent();
}
```

`TaxonManagementView` itself (the `@Route("reference-data")` class) lives in the **marketplace-app** so it can apply marketplace-level security (`RoleChecker`, layout integration). The view delegates its body to `TaxonUiPort.buildManagementContent()`.

### `TaxonAuditHook` (starter → marketplace)

```java
public interface TaxonAuditHook {

    /** Called when an entry is assigned to or unassigned from an entity. */
    void onAssignmentChanged(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long entryId, @NonNull AssignmentChange change);

    enum AssignmentChange { ASSIGNED, UNASSIGNED }
}
```

Marketplace's `TaxonAuditHookImpl` (in `org.ost.marketplace.services.audit.taxon`) translates these calls into activity items, analogously to `AttachmentAuditHookImpl`. Per the hook implementation rule, the impl is **pure delegation** to a marketplace service that does the actual work.

## Audit integration

Two audit surfaces:

1. **CRUD on a taxon itself** (create, edit translation, soft-delete, restore) — handled by the standard audit subsystem because taxa are auditable entities. Snapshots use the `AuditableSnapshot` interface.
2. **Assignment / unassignment to an advertisement** — flows through `TaxonAuditHook`, which marketplace translates into an entry in the *advertisement's* activity feed. The audit record carries the entry id; rendering resolves the localised name at display time, so renaming a category after the fact does not falsify history (the id is stable, the human-readable name reflects current state).

## No default category

An advertisement may carry zero categories — this is a valid, intentional state, not an edge case. Chips and detail views simply render an empty strip when the set is empty. The starter ships no seeded "General" entry and exposes no `findByCode` / `default-category-code` machinery. Marketplace performs no auto-assignment in `AdvertisementService.save()`.

Why no default: SPEC declares categories as **metadata, not navigation** (out of scope: "public-facing taxonomy browsing"). A default category would imply that every ad belongs *somewhere* in a navigable taxonomy — which we explicitly do not promise. Keeping zero-category as a valid state also keeps the starter's responsibility narrow: it never invents domain data on the caller's behalf.

## UI

### Marketplace-side

- New tab in `MainView` labelled "Reference Data" / "Довідники", visible only when `RoleChecker.isAtLeastModerator(currentUser)`.
- `TaxonManagementView` (route, secured) — host class in marketplace; delegates content to `TaxonUiPort.buildManagementContent()`.
- `AdvertisementFormOverlayModeHandler` — adds the selector after existing fields via `taxonUiPort.ifAvailable(p -> p.buildSelector(ADVERTISEMENT, id, TaxonType.CATEGORY))`.
- `AdvertisementViewOverlayModeHandler` — adds the chips strip via `buildChips(...)`.
- `AdvertisementQueryBlock` — adds a category filter field via `taxonUiPort.ifAvailable(p -> p.buildFilterField(TaxonType.CATEGORY))`. Selected taxon ids are pushed into `AdvertisementFilterDto.categoryIds` (new `Set<Long>` field).
- `AdvertisementCardView` (the card-list view) — before rendering, calls `taxonPort.ifAvailable(p -> p.getForEntities(ADVERTISEMENT, visibleAdIds, locale))` once per page and passes the per-ad list down to `AdvertisementCardMetaPanel`, which renders chips via `taxonUiPort.buildChipsFromData(taxons)`. This avoids the N+1 query that a per-card `getForEntity` call would produce.

#### Category filtering at the SQL layer

`AdvertisementRepository` MUST NOT join `taxon_assignment` directly — that would couple marketplace SQL to a starter-owned table name and break compile-time decoupling when the starter is removed.

Pattern instead:
1. `AdvertisementService.list(filter, ...)` checks `filter.getCategoryIds()`. If non-empty AND `TaxonPort` is present, it calls `taxonPort.findEntityIdsWithAnyTaxon(ADVERTISEMENT, categoryIds)` and replaces the filter with the resolved id set: `filter = filter.withAdvertisementIds(resolvedIds)` (the `advertisementIds` clause already exists in the repository as a generic id-restriction filter).
2. If the resolved id set is empty → short-circuit: return empty page (no SQL call).
3. If the starter is absent: `filter.getCategoryIds()` is ignored (the UI never exposed the filter field, so the value should always be empty; defensive `null`/`empty` checks at the boundary).

The repository remains unaware of taxonomy — it only sees a standard "filter by these advertisement ids" clause. Decoupling intact.

### Starter-side

- `TaxonManagementContent` (Vaadin component, `prototype` scope, `Configurable` pattern):
  - Top: `TaxonType` selector (combo populated from enum values — now just `CATEGORY`; new values appear automatically as the enum grows).
  - Grid: id, name (default locale), translation count, assignment count, deleted flag.
  - Buttons: Add / Edit / Soft-delete / Restore / Show deleted toggle.
- `TaxonEditor` (modal dialog):
  - One Vaadin tab per supported locale (`uk`, `en`).
  - Each tab: required `name` (TextField) and `description` (TextArea).
  - Save validates all locales filled; aborts otherwise with field-level errors.
- `TaxonSelector` (multi-select combo of chips, populated from `getAllByType`).
- `TaxonChipsDisplay` (read-only horizontal strip).

All Vaadin components in the starter follow the existing `Configurable<T, P>` pattern.

## i18n inside the starter

Starter owns its own translation key enum: `TaxonMessages implements TranslationKey`. Used for UI strings (button labels, validation messages, tab titles). Per-locale message files live in the starter's `src/main/resources`.

This is consistent with the open goal in `audit-spring-boot-starter/DECISIONS.md` (per-module i18n). Since taxonomy is a new module, we ship it with module-local i18n from day one — no need to retrofit later.

## Security

- `TaxonManagementView` — `@RolesAllowed({"ADMIN", "MODERATOR"})` (or equivalent Spring Security expression, matching the pattern already used by `MainView` route).
- `TaxonPort` itself is unguarded — service-layer methods are intentionally callable by any authenticated component. Authorisation is enforced at the UI / route boundary, consistent with the existing project decision not to put `@PreAuthorize` on Vaadin-facing services.

## Graceful degradation and decoupling

The starter is **a removable feature**, not a required dependency. Every integration point in marketplace is wrapped in `ObjectProvider<TaxonPort>` / `ObjectProvider<TaxonUiPort>` and resolved with `ifAvailable(...)`. Marketplace source code references only `platform-commons` types (`TaxonPort`, `TaxonUiPort`, `TaxonDto`, `TaxonType`); it never imports anything from `org.ost.taxon.*`.

### Integration surfaces and their fallback when the starter is removed

| Surface | Bean used | Behaviour when absent |
|---|---|---|
| Reference Data tab in `MainView` | `TaxonUiPort` | Tab not added to the layout. |
| `TaxonManagementView` route | `TaxonUiPort` | Route page renders an empty-state message ("This feature is not enabled in the current build"). |
| Category selector in advertisement form | `TaxonUiPort` | Field not added. |
| Category chips in advertisement detail / view overlay | `TaxonUiPort` | Strip not added. |
| Category chips on advertisement cards | `TaxonPort` + `TaxonUiPort` | Strip not added. Card height does not jump (the chip area is `null`-safe). |
| Category filter in `AdvertisementQueryBlock` | `TaxonUiPort` | Filter field not added. `AdvertisementFilterDto.categoryIds` stays empty. |
| Category filter at SQL layer in `AdvertisementService` | `TaxonPort` | `categoryIds` ignored even if somehow populated — the service short-circuits without calling the port. |
| Audit-history entries for assignment changes | `TaxonAuditHook` (in marketplace, implements interface from `platform-commons`) | The hook is a marketplace bean that depends on `TaxonPort` indirectly via the audit service. With the starter absent, the hook is never called (no source of events). |

### Decoupling verification

The decoupling claim is testable. The verification recipe is part of step 8 in `PLAN.md`:

1. Comment out `taxon-spring-boot-starter` from `marketplace-app/pom.xml`.
2. Run `mvn -pl marketplace-app -am clean package -DskipTests`. **Must succeed** — no compilation errors.
3. Build the Docker image and start the app (`bash scripts/deploy.sh`).
4. Smoke-check: log in, open advertisements list, open an existing ad, edit and save it. **All flows must work.** Visible differences: no Reference Data tab, no category filter, no chips.
5. Restore the pom entry, rebuild, confirm everything reappears.

### Hard rules to preserve decoupling

- Marketplace files MUST import only from `org.ost.platform.taxon.*` (i.e. `platform-commons`). No `org.ost.taxon.*` imports anywhere. Enforced by review / grep.
- `AdvertisementRepository` (and any other repository in marketplace) MUST NOT name `taxon`, `taxon_assignment`, or `taxon_translation` in SQL. The taxonomy filter is resolved to a generic id set BEFORE the SQL fires.
- `AdvertisementFilterDto.categoryIds` is a transport field; it is converted to `advertisementIds` (a pre-existing filter clause) in the service layer.
- No marketplace bean carries a non-optional dependency on `TaxonPort` or `TaxonUiPort`. Every constructor uses `ObjectProvider<...>`.

## Cross-references

- `taxon-spring-boot-starter/DECISIONS.md` (created in step 1) — records module-specific decisions: storage shape, table layout, seed strategy.
- `platform-commons/DECISIONS.md` — gains an entry for the new SPI surface if anything notable comes up.
- `marketplace-app/DECISIONS.md` — gains an entry only if integration in `AdvertisementService` / `AdvertisementCardView` exposes anything non-obvious (e.g. how `categoryIds` collapses into `advertisementIds` before the SQL fires).
