# Taxonomy — Architecture

## Module placement

A new starter, **`taxon-spring-boot-starter`**, is introduced at the same level as `audit-spring-boot-starter` and `attachment-spring-boot-starter`.

```
advertisement-parent
├── platform-commons              ← SPI + DTOs for taxonomy
├── query-lib
├── audit-spring-boot-starter
├── attachment-spring-boot-starter
├── taxon-spring-boot-starter   ← NEW (domain + data only, no Vaadin)
└── marketplace-app               ← all UI components for taxonomy
```

Java package root for the starter: `org.ost.taxon`.

The starter is optional at compile-time the same way audit and attachment are. Marketplace declares a runtime dependency on it; all UI integration uses `ObjectProvider.ifAvailable(...)`.

## Data model

Three tables, all owned by the starter, all managed via its own Liquibase changelog `db/taxon-changelog/`. The set of taxon types is fixed in code as an `enum` (see `TaxonType` in `platform-commons`), so there is no `taxon_type` table — types are stable infrastructure, not user-managed data.

```sql
taxon (
  id                BIGSERIAL PRIMARY KEY,
  type              VARCHAR(64) NOT NULL,            -- TaxonType enum name: 'CATEGORY', ...
  code              VARCHAR(64),                     -- stable lookup key for well-known entries; nullable for user-created ones
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
- Partial unique index on `taxon(type, code) WHERE code IS NOT NULL` — for stable lookup of well-known entries.
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

All cross-module contracts live in `platform-commons/taxon/...`, following the existing semantic split:

```
platform-commons/taxon/
├── model/
│   └── TaxonType             (enum: CATEGORY; new values added in future releases)
├── dto/
│   ├── TaxonDto              (id, type, code, name, description, deleted)
│   └── TaxonTranslationDto   (locale, name, description)
└── spi/
    ├── TaxonPort             (marketplace → starter, data ops)
    └── TaxonAuditHook        (starter → marketplace, audit events)
```

There is **no `TaxonUiPort`**. All Vaadin UI components live in marketplace-app and call `TaxonPort` directly via `ObjectProvider`. This keeps the starter free of any Vaadin dependency and avoids an unnecessary SPI layer for UI.

`TaxonType` is intentionally a closed enum in code rather than a database table. Rationale:
- Adding a new taxon type is never just data — it always requires UI integration, audit translations, and seed entries. So the "create a new type" operation cannot be exposed safely to end users; it is a release-level change.
- Closed enums match the existing pattern (`Role`, `EntityType`, `ActionType`) and give compile-time safety at every call site.

### `TaxonPort` (marketplace → starter)

```java
public interface TaxonPort {

    /** Assigns an entry to an entity (idempotent). */
    void assign(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long taxonId);

    /** Removes an assignment (no-op if absent). */
    void unassign(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long taxonId);

    /** Replaces all assignments for an entity with the given set in one transaction. */
    void replaceAssignments(@NonNull EntityType entityType, @NonNull Long entityId,
                            @NonNull Set<Long> taxonIds);

    /** Active (non-deleted) entries currently assigned to an entity, localised to the given locale. */
    List<TaxonDto> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId,
                                @NonNull Locale locale);

    /** Batched variant: returns assignments for many entities in one call. Avoids N+1 when rendering card lists. */
    Map<Long, List<TaxonDto>> getForEntities(@NonNull EntityType entityType,
                                             @NonNull Set<Long> entityIds,
                                             @NonNull Locale locale);

    /** All active entries of a given taxon type, localised. */
    List<TaxonDto> getAllByType(@NonNull TaxonType type, @NonNull Locale locale);

    /** Resolves a specific entry by id (even if soft-deleted — used by audit rendering). */
    Optional<TaxonDto> findById(@NonNull Long taxonId, @NonNull Locale locale);

    /** Resolves a well-known entry by its stable code. */
    Optional<TaxonDto> findByCode(@NonNull TaxonType type, @NonNull String code,
                                  @NonNull Locale locale);

    /**
     * Returns the set of entity ids that have AT LEAST ONE of the given taxons assigned.
     * Used by marketplace to filter advertisements without exposing taxon_assignment table names.
     * Empty input set → empty result.
     */
    Set<Long> findEntityIdsWithAnyTaxon(@NonNull EntityType entityType,
                                        @NonNull Set<Long> taxonIds);
}
```

### `TaxonAuditHook` (starter → marketplace)

```java
public interface TaxonAuditHook {

    /** Called when an entry is assigned to or unassigned from an entity. */
    void onAssignmentChanged(@NonNull EntityType entityType, @NonNull Long entityId,
                             @NonNull Long taxonId, @NonNull AssignmentChange change);

    enum AssignmentChange { ASSIGNED, UNASSIGNED }
}
```

Marketplace's `TaxonAuditHookImpl` (in `org.ost.marketplace.spi`) translates these calls into activity items, analogously to `AttachmentAuditHookImpl`. Per the hook implementation rule, the impl is **pure delegation** to a marketplace service that does the actual work.

## Audit integration

Two audit surfaces:

1. **CRUD on a taxon itself** (create, edit translation, soft-delete, restore) — handled by the standard audit subsystem because taxa are auditable entities. Snapshots use the `AuditableSnapshot` interface.
2. **Assignment / unassignment to an advertisement** — flows through `TaxonAuditHook`, which marketplace translates into an entry in the *advertisement's* activity feed. The audit record carries the entry id; rendering resolves the localised name at display time, so renaming a category after the fact does not falsify history.

## No default category

An advertisement may carry zero categories — this is a valid, intentional state. Chips and detail views simply render an empty strip. The starter ships no seeded entries and performs no auto-assignment in `AdvertisementService.save()`.

## UI

### Marketplace-side (all UI lives here)

All Vaadin components for taxonomy are written in `marketplace-app`. No UI exists in the starter. Marketplace calls `TaxonPort` directly via `ObjectProvider<TaxonPort>`.

Integration surfaces:

| Surface | Where | Behaviour when starter absent |
|---|---|---|
| Reference Data tab in `MainView` | `MainView` | Tab not added |
| `TaxonManagementView` (route + content) | `ui/views/main/tabs/referencedata/` | Route renders empty-state message |
| Category selector in advertisement form | `AdvertisementFormOverlayModeHandler` | Field not added |
| Category chips in advertisement detail view | `AdvertisementViewOverlayModeHandler` | Strip not added |
| Category chips on advertisement cards | `AdvertisementCardView` | Strip not added; card height stable |
| Category filter in `AdvertisementQueryBlock` | `AdvertisementQueryBlock` | Filter field not added |
| SQL-layer filtering in `AdvertisementService` | `AdvertisementService` | `categoryIds` ignored |

#### Category filtering at the SQL layer

`AdvertisementRepository` MUST NOT join `taxon_assignment` directly — that would couple marketplace SQL to a starter-owned table name.

Pattern:
1. `AdvertisementService.list(filter, ...)` checks `filter.getCategoryIds()`. If non-empty AND `TaxonPort` is present, calls `taxonPort.findEntityIdsWithAnyTaxon(ADVERTISEMENT, categoryIds)` and converts to an `advertisementIds` filter clause (pre-existing clause in the repository).
2. If the resolved id set is empty → short-circuit: return empty page (no SQL call).
3. If the starter is absent: `filter.getCategoryIds()` is ignored.

#### Chips on cards (avoiding N+1)

`AdvertisementCardView` (the card-list view) calls `taxonPort.getForEntities(ADVERTISEMENT, visibleAdIds, locale)` once per page load and passes the per-ad `List<TaxonDto>` down to each card. Individual cards render chips from the pre-loaded data — no per-card DB call.

### Reference Data management view

The management UI for categories (create / edit / soft-delete / restore) lives entirely in `marketplace-app` at `ui/views/main/tabs/referencedata/`.

#### Extensibility: one view, multiple taxon types

Currently only `CATEGORY` exists. When a second `TaxonType` is added (e.g. `TAG`), the same management component is reused — the Reference Data tab gains an inner tab per type, each passing its `TaxonType` value to the same component. No structural redesign needed.

#### List layout — accordion

The entry list uses an accordion pattern: each row shows name + usage count in collapsed state; clicking the row expands an inline preview (localised name and description for all locales). Action icons (edit pencil, delete trash) appear on row hover, consistent with the advertisement card pattern.

```
┌─────────────────────────────────────────────────────────────┐
│  Categories                                  [ + Add new ]  │
│                                                             │
│  ▶  Electronics        12 ads               (hover: ✏ 🗑)  │
│  ▼  Auto                8 ads               (hover: ✏ 🗑)  │
│  │   EN: Auto — vehicles for sale and rent                  │
│  │   UK: Авто — транспортні засоби                         │
│  ▶  Real Estate         3 ads               (hover: ✏ 🗑)  │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │
│  ○  Jobs (deleted)      0 ads               (hover: ↩)     │
└─────────────────────────────────────────────────────────────┘
```

Deleted entries appear below a visual separator; their hover action is Restore only.

#### Edit / create overlay

Clicking the edit icon or "Add new" opens an overlay following the same pattern as `UserOverlay` and advertisement overlays. The overlay has two tabs: **Edit** and **Activity**.

**Edit tab:**
```
┌─────────────────────────────────────────────────────────────┐
│  [ Edit ]  [ Activity ]                                     │
│  ─────────────────────────────────────────────────────────  │
│  ┌─ EN ─┐ ┌─ UK ─┐                                         │
│                                                             │
│  Name:        [________________________________]            │
│  Description: [________________________________]            │
│                                                             │
│  [ Save ]   [ Restore ]                                     │
└─────────────────────────────────────────────────────────────┘
```

- Locale tabs (EN / UK): each tab has required `name` (TextField) and `description` (TextArea). A per-tab error badge appears if the tab contains validation errors, so the user knows which locale is incomplete without clicking through all tabs.
- **Save** — persists all locale translations in one call.
- **Restore** — reverts to the previous snapshot, same mechanism as used for users and advertisements.
- "Add new" opens the same overlay with empty fields and no Activity tab content.

**Activity tab:**
- Shows the full audit timeline for this taxon entry: create, edits (field-level diffs), soft-delete, restore events.
- Same `AuditTimelineListRenderer` used by other entity overlays.

## Security

- `TaxonManagementView` — secured to `ADMIN` and `MODERATOR` roles, consistent with how other admin routes are secured.
- `TaxonPort` itself is unguarded — authorisation is enforced at the UI / route boundary, consistent with the existing project decision not to put `@PreAuthorize` on Vaadin-facing services.

## Graceful degradation and decoupling

Hard rules:
- Marketplace files MUST import only from `org.ost.platform.taxon.*`. No `org.ost.taxon.*` imports anywhere.
- `AdvertisementRepository` MUST NOT name `taxon`, `taxon_assignment`, or `taxon_translation` in SQL.
- No marketplace bean carries a non-optional dependency on `TaxonPort`. Every injection uses `ObjectProvider<TaxonPort>`.

Decoupling verification (step 7 in PLAN.md):
1. Comment out `taxon-spring-boot-starter` in `marketplace-app/pom.xml`.
2. `mvn -pl marketplace-app -am clean package -DskipTests` — must succeed.
3. Deploy and smoke-check: login, browse ads, open and edit an ad — all must work.
4. Visible differences only: no Reference Data tab, no category filter, no chips.

## Cross-references

- `taxon-spring-boot-starter/DECISIONS.md` — module-specific decisions (storage shape, table layout, seed strategy).
- `platform-commons/DECISIONS.md` — gains an entry for the new SPI surface.
- `marketplace-app/DECISIONS.md` — gains an entry if `categoryIds → advertisementIds` collapse or card batching exposes anything non-obvious.
