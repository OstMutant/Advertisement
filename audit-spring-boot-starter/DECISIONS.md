# Architecture & Technical Decisions — audit-spring-boot-starter

---

## Ongoing — Module structure and auto-configuration

**Decision:** `audit-spring-boot-starter` owns the full audit subsystem — write side (`DefaultAuditPort`, `AuditableSnapshot.diff()`, `AuditLogRepository`) and read side (`AuditReadService`, `AuditReadService`, `AuditReadService`, Vaadin UI components). Auto-configured via a single `AuditAutoConfiguration`. Active whenever the jar is on the classpath — jar presence is the toggle.

Write and read sides were initially separate modules (`audit-core` + `audit-read`) but merged — fewer modules is simpler when there is no concrete scenario requiring the write side without the read side.

**Key patterns:**
- `AuditableSnapshot` marker interface carries `entityType()` — eliminates stringly-typed entity-type strings
- `CurrentActorHook` SPI (`core.spi`) — the starter calls it without knowing about Spring Security

---


## 2026-05-13 — SQL coupling to domain tables removed via SPI batch pattern

**Decision:** Audit projections do not JOIN domain tables. Instead: (a) raw `actor_id` is returned from the query; (b) `AuditActorNameResolver` SPI performs a single bulk `SELECT id, name FROM user_information WHERE id = ANY(:ids)` after the query; (c) `AuditEntityExistenceChecker` SPI performs a single bulk `SELECT id FROM <table> WHERE id = ANY(:ids)` per entity type. Both SPIs are wired via `ObjectProvider` — if absent, actor names stay `null` and entity existence defaults to `false`. Implementations live in `marketplace-app`.

**Why:** The starter previously coupled directly to `user_information` and `advertisement` tables, making it unusable in any context that does not have those tables.

**Rejected:** Per-row secondary queries — single bulk SELECT with `ANY(:ids)` is one round-trip vs N.

---

## 2026-05-16 — Full decoupling from advertisement domain achieved

`audit-spring-boot-starter` contains zero knowledge of advertisement-specific entities, field names, or business logic. The module is reusable in any Spring Boot + Vaadin project without modification.

Key changes that completed decoupling: `AdvertisementHistoryProjection` → generic `EntityHistoryProjection`; `AdvertisementHistoryDto` → `AuditHistoryItemDto` with `SnapshotPayload`; `ActivityProjection` uses single generic query; display name resolution delegated to `EntityDisplayNameResolver` SPI; CSS classes renamed to domain-neutral vocabulary (`entity-history-*`, `activity-feed-*`).

---

## 2026-05-19 — Profile activity decoration via AuditSnapshotBinder + AuditActivityRowHook SPI

**Decision:** Profile activity panels are built through `AuditUiExtension.buildProfileActivityPanel(ProfileActivityParams)`. Consumers pass a list of `AuditActivityRowHook` — an SPI with `entityType()` + `decorate(AuditActivityItemDto): Component` — to attach per-row UI without the starter understanding the snapshot shape.

`AuditSnapshotBinder<T>` (Spring prototype bean, `Configurable<T, Parameters<T>>`) is the canonical generic implementation: deserializes `AuditActivityItemDto.snapshotData` into consumer-provided `Class<T>`, checks a consumer-provided `Predicate<T>` for "is current", optionally fires a `BiConsumer<Long, T>` for restore.

**Why:** The previous pattern parsed snapshot JSON inside the starter — coupling it to specific user/settings shapes. The SPI puts shape knowledge on the consumer side.

**Rejected:** Decorator wrapper around `AuditActivityPanel`; abstract `ActivityRowDecorator` requiring subclasses per shape.

---

## 2026-05-19 — Starter owns `auditObjectMapper`

**Decision:** `AuditAutoConfiguration` defines `@Bean("auditObjectMapper") ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` disabled, `@ConditionalOnMissingBean(name = "auditObjectMapper")` for override. All audit consumers qualify injection with `@Qualifier("auditObjectMapper")`.

**Why:** The starter previously consumed `userSettingsObjectMapper` — a marketplace-specific name. The starter must work in any Spring Boot context.

**Rejected:** `@Primary` on the starter's `ObjectMapper` — explicit `@Qualifier` over `@Primary` everywhere (durable project rule).

**2026-05-23 update:** `audit.enabled` property removed. Jar presence is the only toggle — no scenario exists where the jar is on the classpath but the subsystem should be disabled. `@ConditionalOnAuditEnabled` removed entirely (was a no-op marker with no `@ConditionalOnProperty`).

---

## 2026-05-19 — Actor-centric SPI vocabulary; user-domain types purged

**Decision:** Audit subsystem speaks about actors and subjects, not users. Key renames: `AuditUserProvider` → `CurrentActorProvider`; `UserActivityExtension` → `ActivityFeedExtension`; `AdvertisementHistoryExtension` → `MediaHistoryExtension`; `UserSnapshotState` deleted; `AuditPort.getUserStateBefore/getUserStateAt` deleted in favor of generic `getSnapshotContent/getPreviousSnapshotContent(Long, EntityType)`; DB column `user_id` → `actor_id`.

**Why:** "User" is a marketplace-specific concept. "Actor" is neutral and applies to bots, workflows, or service accounts.

---

## 2026-05-16 — ActivityItemFieldsProvider SPI: expanded field display in activity feed

**Decision:** `ActivityItemFieldsProvider` SPI (`core.spi`) lets consumers supply a merged `List<ChangeEntry>` (changed + unchanged fields) for their entity types. `AuditActivityRowRenderer.buildRow` calls it for non-settings items; falls back to raw `changes` when no provider registered.

**Why:** Activity feed was showing only changed fields. Domain-specific field lists must not be hardcoded in the starter.

**Rejected:** Hardcoding field names in `AuditActivityRowRenderer` — introduces domain coupling into the starter.

---

## 2026-05-24 — Action badge uses type-specific CSS class in both history and activity

**Decision:** Every action badge (Created / Updated / Deleted / Restored) carries two CSS classes: a base class for shared layout (`entity-history-action` or `activity-feed-action`) and a modifier for color (`--created`, `--updated`, `--deleted`, `--restored`). The modifier is derived from `ActionType.name().toLowerCase()`. Color palette is identical in both `entity-history.css` and `activity-feed.css`: green / blue / red / yellow respectively.

**Rule:** Any new UI component that renders action badges must follow this two-class pattern. Single-class with hardcoded color is forbidden.

**Why:** Previously `AuditActivityRowRenderer` added only the base class, making all activity rows render with a hardcoded blue badge — no visual distinction between created/updated/deleted. `AuditHistoryPanel` already applied the modifier class; bringing `AuditActivityRowRenderer` to parity was the fix.

**Scope:** `AuditHistoryPanel` (ads history), `AuditActivityRowRenderer` (user activity, settings activity, all profile feeds).

---

## 2026-05-24 — Restore semantics: `getSnapshotContent` is the restore source

**Decision:** `AuditPort.getSnapshotContent(snapshotId, entityType)` is the only correct method for restore flows. `getPreviousSnapshotContent` returns the state *before* a recorded change; it must not be used to populate restore targets.

**Rule:** Any consumer implementing a restore button via `AuditSnapshotBinder.onRestore` must call `getSnapshotContent`. `getPreviousSnapshotContent` is only for displaying diffs or "what changed" UI.

**Why:** Restoring to the snapshot of a history entry means "make the entity look like it did at this recorded moment" — that is the snapshot data (`after`), not the state before the change.

---

## Deferred — Snapshot schema versioning (designed, not yet implemented)

**Decision (designed 2026-05-26, implementation deferred):** Add `@SchemaVersion(int value default 1)` annotation to `platform-commons/audit.api`. Apply to all `AuditableSnapshot` implementations. Add `default int schemaVersion() { return 1; }` to `AuditableSnapshot` so Jackson serializes `"schemaVersion": 1` into every stored snapshot. `SnapshotCodec.decode()` reads `schemaVersion` from the JSON tree, compares with `@SchemaVersion` on the target class, and logs a warning on mismatch.

**Why:** Field renames and type changes are silent data loss under the current model — `FAIL_ON_UNKNOWN_PROPERTIES = false` handles additions and removals but not renames or type changes. The version stamp makes staleness visible without adding migration infrastructure prematurely.

**Implementation plan:**
1. New `@SchemaVersion` annotation in `platform-commons/audit.api`
2. `default int schemaVersion() { return 1; }` on `AuditableSnapshot`
3. `SnapshotCodec.decode()`: parse via `readTree()`, extract `schemaVersion` (default 0 if absent = legacy), read annotation from `clazz`, log warning on mismatch, then decode normally
4. Add `@SchemaVersion(1)` to `UserSnapshotDto`, `AdvertisementSnapshotDto`, `SettingsSnapshotDto`

**Trigger:** implement before the first `AuditableSnapshot` field rename or type change.

---

## 2026-05-26 — Code review findings (open backlog)

Full codebase review identified the following issues. Items marked ✅ are done.

### HIGH
- ✅ `AuditActivityRowRenderer`: `addHistorySpan` / `addActivitySpan` — identical except CSS prefix → merged into `addSpan(Div, String, boolean, String cssBase)`
- ✅ `AuditActivityRowRenderer`: `buildAdvertisementActivityFieldsList` / `buildAdvHistoryFieldsList` — extracted via `AuditActivityRenderHook` SPI; renamed to generic `buildHistoryFieldsList(h, EntityRef)`. No `EntityType.ADVERTISEMENT` hardcode remains in the starter.
- ✅ `AuditReadService` + `AuditReadService`: `resolveActorNames()` — extracted into `AuditDomainHelper.withResolvedActorNames(items, idGetter, nameApplier)`.

### MEDIUM
- ✅ `AdvertisementFormOverlayModeHandler` / `UserFormOverlayModeHandler`: extracted `AbstractFormOverlayModeHandler<D extends EditDto>` base class with `hasChanges()` and `wireSaveGuard()`.
- ✅ `AdvertisementViewOverlayModeHandler` / `UserViewOverlayModeHandler`: extracted `AbstractViewOverlayModeHandler` base class in `marketplace-app` with `final activate()` template method and 5 abstract hooks (`tabsCssClass`, `buildPrimaryTab`, `buildPrimaryContent`, `buildSecondaryTab`, `buildHeaderActions`). Lazy-loading and tab-switching logic centralised in `assembleTabbedContent`. `SecondaryTabDef` record carries tab + CSS class + `Supplier<Component>` loader; `null` = no secondary tab.
- ~~`AuditAutoConfiguration` / `AttachmentAutoConfiguration`: identical `SchedulingConfigurer` registration for cleanup~~ — **won't fix**: each starter owns its own lifecycle; extracting shared config would couple independent modules or require a boilerplate-only starter. Duplication is intentional isolation.
- ~~`AuditAutoConfiguration` / `AttachmentAutoConfiguration`: both create `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` disabled~~ — **won't fix**: same reasoning; each starter owns its own serialization config so it can diverge independently.

### MEDIUM-LOW
- ✅ `AuditMessages.fieldLabel()` hardcoded marketplace field names (`title`, `description`, `name`, `email`, `role`). Removed `fieldLabel()` + 5 `CHANGES_FIELD_*` constants from `AuditMessages`. `AuditActivityFieldsHook` implementations in marketplace now return `GenericChange(labelKey.key(), from, to)` entries instead of raw `FieldChange`. `buildActivityChangesDiv` updated to detect unchanged `GenericChange` entries (`before == null`). New `AdvertisementActivityFieldsHookImpl` added for `EntityType.ADVERTISEMENT`.
- ✅ `AuditSnapshotBinder` coupling removed. `AuditUiPort.snapshotRowHook(SnapshotRowHookParams<T>)` added; `AuditUiPortImpl` creates the binder internally. See `marketplace-app/DECISIONS.md` 2026-05-21 (superseded).

---

## 2026-05-29 — Jackson polymorphic type info: `Id.CLASS` → `Id.NAME` + subtype registration

**Decision:** `AuditableSnapshot` switched from `@JsonTypeInfo(use = Id.CLASS)` to `@JsonTypeInfo(use = Id.NAME, property = "@type")`. Each marketplace snapshot DTO carries `@JsonTypeName` with a stable short name:

| Class | `@type` value |
|---|---|
| `AdvertisementSnapshotDto` | `"advertisement"` |
| `UserSnapshotDto` | `"user"` |
| `SettingsSnapshotDto` | `"user_settings"` |

Subtype registration is done in `marketplace-app/JacksonConfig` via `@PostConstruct registerAuditSnapshotSubtypes()`, which calls `auditObjectMapper.registerSubtypes(...)`. The starter itself has no knowledge of the concrete snapshot classes — registration stays in marketplace where the implementations live.

**Why:** `Id.CLASS` embeds the fully-qualified class name (e.g. `org.ost.marketplace.dto.audit.AdvertisementSnapshotDto`) into the stored JSON. Any package rename or class move silently breaks deserialization of all existing snapshot rows. `Id.NAME` with short stable names decouples the stored type discriminator from the class location.

**Backward compatibility:** Existing DB rows with `Id.CLASS` format are incompatible with the new deserializer. In the dev environment this is handled by deploying with `--reset` to wipe and re-seed the database. A production migration would require a data migration script; that is deferred until the first production deployment.

**Rule:** When adding a new `AuditableSnapshot` implementation: (1) annotate with `@JsonTypeName("stable_short_name")`, (2) register the class in `JacksonConfig.registerAuditSnapshotSubtypes()`. Short names must be stable — changing them requires a DB migration.

---

## 2026-05-29 — Audit starter decoupled from attachment; ObjectProvider removed for required hooks

**Decision:** Two related cleanups applied to the audit starter:

1. **`AttachmentAuditHook` removed from audit-starter.** `AuditReadService`, `AuditReadService`, and `AuditHistoryPanel` no longer import `attachment.spi`. A new `AuditActivityEnrichHook` SPI (`audit.spi`) replaces the direct attachment calls — method names are domain-neutral (`getAdditionalChanges`, `matchesCurrent`). Marketplace implements `ActivityEnrichHookImpl`, which delegates to `AttachmentAuditHook` via `ObjectProvider`.

2. **`ObjectProvider` removed for all required hook injections.** Hooks implemented by marketplace (`CurrentActorHook`, `AuditDomainHook`, `AuditActivityEnrichHook`) are now injected as plain required fields. `ObjectProvider` is kept only for cross-starter optional deps (`AttachmentAuditHook` in marketplace) and prototype bean factories (`rendererProvider`, `Builder.provider`).

**Why:** The audit starter called an `attachment.spi` hook directly — starter-to-starter coupling through the SPI layer. Marketplace is the correct orchestrator; it knows about both subsystems. `ObjectProvider` for required hooks implied optionality that was architecturally false.

---

## 2026-05-30 — Repository unified: findRows + findRowsByActor replace domain-coupled UNION query

**Decision:** `findActivityForProfile(Long userId)` removed. Replaced by two clean, domain-free methods:
- `findRows(EntityType, Long entityId, Long filterActorId)` — entity-scoped; window functions over all rows for this entity, optional actor filter.
- `findRowsByActor(Long actorId)` — actor-scoped; window functions over all rows where actor_id matches.

`AuditReadService.getForSubject(List<EntityRef> subjects, Long actorId)` merges streams from multiple `findRows` calls (one per subject entity) + `findRowsByActor`, deduplicates by id, sorts by `createdAt DESC`, limits to 20 in Java.

`ProfileActivityParams` and `AuditActivityPanel.Parameters` now carry `List<EntityRef> subjects` + `Long actorId` instead of `subjectType`+`subjectId`. Marketplace passes `[EntityRef(USER, id), EntityRef(USER_SETTINGS, id)]` as subjects — the starter has zero knowledge of which entity types constitute a "profile".

**Why:** The old UNION had `entity_type IN ('USER', 'USER_SETTINGS')` hardcoded — domain knowledge in the persistence layer. Now the repository is domain-free; marketplace decides which entities to include.

**Rule:** `AuditLogRepository` must not contain any `EntityType` name literals or UNION queries combining domain-specific filters. One method per query axis (by entity, by actor).

---

## 2026-05-30 — changes_summary removed; diff computed dynamically on read

**Decision:** `changes_summary JSONB` column removed from `audit_log`. Diff is now computed at read time by ``AuditableSnapshot.diff()`` from `snapshot_data` (current row) and `prev_snapshot_data` (LAG window function). `AuditLogProjection` no longer carries `changesSummaryJson`. `AuditJsonSerializationService.fromJsonList` / `toChangesJson` removed.

**Why:** Pre-computing diffs at write time meant stale diffs when diff logic changed. Dynamic computation gives always-fresh results with negligible cost (max 20–100 rows). Write side (`DefaultAuditPort`, `AuditLogRepository.save`) simplified — no `AuditableSnapshot.diff()` dependency. `AuditableSnapshot.diff()` is now a read-side concern only (injected by `AuditReadService` and `AuditReadService`).

**Rule:** Never store pre-computed diffs. Always derive changes from snapshot pairs at read time.

---

## 2026-05-30 — Generic AuditLogProjection: one repository DTO for history and activity

**Decision:** `AuditLogRepository` returns a single generic `AuditLogProjection` record instead of `AuditHistoryItemDto` / `AuditActivityItemDto`. Window functions (`ROW_NUMBER()`, `LAG()`) are computed at SQL level and included in the record. Services transform `AuditLogProjection` into their specific DTOs.

```java
public record AuditLogProjection(
    Long       id,
    EntityType entityType,
    Long       entityId,
    ActionType actionType,
    String     snapshotDataJson,
    String     changesSummaryJson,
    Long       actorId,
    Instant    createdAt,
    int        version,
    Long       prevId,
    String     prevSnapshotDataJson
) {}
```

- `AuditReadService` maps `AuditLogProjection` → `AuditHistoryItemDto` (uses `version`, `prevId`, `prevSnapshotDataJson`)
- `AuditReadService` maps `AuditLogProjection` → `AuditActivityItemDto` (uses `version` for `AuditActivityEnrichHook.getAdditionalChanges`)
- Repository has one `RowMapper<AuditLogProjection>`, no dependency on service-layer DTOs or hooks

**Why:** The repository previously depended on `AuditHistoryItemDto`, `AuditActivityItemDto`, and `EntityNameHook` — service-layer concerns bleeding into the persistence layer. With `AuditLogProjection`, the repository is a pure SQL → data layer. `ROW_NUMBER()` and `LAG()` stay in SQL because Java-side computation breaks with future pagination (you can only paginate correctly if the window is computed over the full dataset in SQL).

**Pagination:** future cursor/offset pagination is added at the repository query level only — services and UI panels are unaffected.

**Rule:** `AuditLogRepository` must not import any service-layer DTO or hook. `AuditLogProjection` fields map 1:1 to SQL columns/expressions — no deserialization in the mapper.

---

## 2026-06-02 — DEFERRED: AuditHistoryRowActionsHook for restore button decoupling

**Status: Not implemented.** `AuditHistoryPanel` still builds the restore button and current-state badge directly; the hook SPI does not exist yet.

**Proposed design (when triggered):** Replace the direct `onRestoreRequested` callback in `AuditHistoryPanel.Parameters` with an `AuditHistoryRowActionsHook` SPI in `platform-commons/audit.spi`. The panel calls `hook.buildRowActions(item, isCurrentState, onRestore)` via `ObjectProvider` — no-op when absent. Marketplace provides `AuditHistoryRowActionsHookImpl`.

**Why it should eventually be done:** The restore button is a marketplace business decision. The starter's panel should only lay out history rows; what actions appear per row is the caller's concern.

**Trigger:** implement when a second consumer of the audit starter appears, or when the current direct coupling causes a real maintenance problem. Do not implement speculatively.

---

## 2026-06-02 — Audit-starter owns its own i18n; display labels removed from AuditUiPort params

**Decision:** All display label strings (empty-state text, "current state" badge, "restore" button text) were previously passed from marketplace into `AuditUiPort` params (`labelEmpty`, `labelCurrentState`, `labelRestore`, `emptyLabel`, `currentLabel`, `restoreLabel`). These parameters have been removed. The audit-starter resolves all its own labels via `AuditI18n` enum + `I18nService`.

`AuditMessages` renamed to `AuditI18n` (implements `TranslationKey`). All keys now carry the `audit.` namespace prefix (e.g. `audit.activity.empty`, `audit.history.restore`). The enum is internal to the starter — marketplace never references it.

**Why:** Passing display strings across a module boundary couples the caller to the starter's internal rendering decisions. The starter is responsible for how it looks; the caller is responsible for what it shows.

**Rule:** `AuditUiPort` params must carry only data (entity IDs, subjects, callbacks, predicates) — never display strings or i18n keys.

---

## 2026-06-02 — GenericChange i18n key convention: audit.changes.*

**Decision:** `ChangeEntry.GenericChange.labelI18nKey` stores a raw i18n key string in the DB. The audit-starter resolves it at render time via `AuditChangeFormatter`. Keys produced by the attachment starter follow the `audit.changes.*` namespace (e.g. `audit.changes.media`). The translation itself lives in `marketplace-app/i18n/messages*.properties`.

**Why:** The starter resolves labels via a shared `MessageSource`. Namespacing keys under `audit.changes.*` makes ownership clear and avoids collision with marketplace-owned keys.

**Deferred — per-module i18n:** Each starter should eventually own its own `messages*.properties` (e.g. `i18n/audit-messages_en.properties`), loaded via a composite `MessageSource`. This eliminates audit- and attachment-specific keys from the marketplace properties files. Trigger: when a second consumer of either starter appears, or the marketplace properties file becomes unmanageable.

---

## 2026-06-03 — AuditFieldLabelHook: field-key translation moved out of expandFields() *(superseded — see below)*

**Decision:** `AuditActivityFieldsHook.expandFields()` now returns `FieldChange` entries with raw field keys (e.g. `"adsPageSize"`, `"title"`) instead of translated labels. A new `AuditFieldLabelHook` SPI (`platform-commons/audit.spi`) carries `supports(EntityType)` + `labelFor(String rawFieldKey)`. `AuditActivityRowRenderer.buildActivityChangesDiv()` resolves the matching label hook and translates each `fc.field()` before building spans.

Marketplace implementations (`ActivityFieldsHookImpl`, `AdvertisementActivityFieldsHookImpl`) implement both `AuditActivityFieldsHook` and `AuditFieldLabelHook`. `I18nService` is only called inside `labelFor()`, which is always invoked from the Vaadin UI thread (inside the renderer).

**Why:** `expandFields()` was calling `I18nService.get()` which internally accesses `VaadinLocaleProvider` — a `@Scope("vaadin-ui")` scoped-proxy bean. Even though the call chain runs in the Vaadin UI thread, the scoped proxy resolution failed under certain request lifecycle conditions, throwing `ScopeNotActiveException` and leaving `.activity-feed-list` unpopulated. The fix separates data expansion (scope-independent) from label translation (UI-context-guaranteed).

**Rule:** `AuditActivityFieldsHook.expandFields()` must never call `I18nService`. Label resolution is the responsibility of `AuditFieldLabelHook.labelFor()`, which is called exclusively from `AuditActivityRowRenderer` in Vaadin UI context.

---

## 2026-06-03 — SPI consolidation: 3 always-co-implemented pairs merged

**Decision:** Three pairs of SPI interfaces that were always co-implemented by the same class in marketplace-app were merged into single interfaces:

1. **`AuditActivityFieldsHook` absorbs `AuditFieldLabelHook`** — added `default String labelFor(String rawFieldKey) { return rawFieldKey; }` to `AuditActivityFieldsHook`. `AuditFieldLabelHook` deleted. Both `ActivityFieldsHookImpl` and `AdvertisementActivityFieldsHookImpl` dropped the extra `implements` clause (they already had `labelFor()`). `AuditActivityRowRenderer` now passes the single `AuditActivityFieldsHook` as the label resolver — `buildActivityChangesDiv` takes `AuditActivityFieldsHook` instead of `AuditFieldLabelHook`.

2. **`AuditDomainHook` absorbs `EntityNameHook`** — added `String resolveDisplayName(EntityType, AuditableSnapshot)` to `AuditDomainHook`. `EntityNameHook` and `EntityNameHookImpl` deleted. `AuditDomainHookImpl` gained the method (delegating to the same service calls). `AuditActivityPanel.buildDisplayContext()` now calls `auditDomainHook.resolveDisplayName()` directly instead of iterating `List<EntityNameHook>`.

3. **`AuditActivityEnrichHook` absorbs `AuditActivityRenderHook`** — added `EntityType entityType()`, `default String getMediaStateForSnapshot(EntityRef, Long)`, `default String getMediaStateAtVersion(EntityRef, int)` to `AuditActivityEnrichHook`. `AuditActivityRenderHook` and `AdvertisementActivityRenderHookImpl` deleted. `ActivityEnrichHookImpl` now implements all 6 methods. `AuditActivityRowRenderer` and `AuditHistoryRowRenderer` switched from a list of `AuditActivityRenderHook` to a list of `AuditActivityEnrichHook`. `AuditReadService` switched from single `AuditActivityEnrichHook` to `List<AuditActivityEnrichHook>` — iterates for `merge`, routes by `entityType()` for `getAdditionalChanges`.

**Why:** Each pair was always co-implemented in one class — the split served no isolation purpose, just added indirection. Merging reduces the interface count from 13 to 10, eliminates 4 files, and makes the natural coupling explicit at the interface level.

**Rule:** `AuditActivityFieldsHook.expandFields()` must still never call `I18nService`. `labelFor()` is the correct place for i18n (invoked from the UI thread in the renderer).

---

## Deferred backlog

- EntityType: migrate from enum to string registry/descriptor when second consumer project appears
- SnapshotCodec: centralize ObjectMapper.readValue calls; eliminates JSON parsing inside projections
- ActivityProjection: JSON deserialization per row — negligible at 20 rows; revisit with cursor pagination
- jsonEquals readTree: expensive for large history lists — add parsed snapshot cache in AuditHistoryPanel.configure()
- EntityDisplayNameResolver.supports(): replace linear scan with map lookup when resolvers > 5
- LIMIT 20/100: replace with cursor pagination when needed
