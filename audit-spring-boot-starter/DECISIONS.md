# Architecture & Technical Decisions — audit-spring-boot-starter

---

## Ongoing — Module structure and auto-configuration

**Decision:** `audit-spring-boot-starter` owns the full audit subsystem — write side (`DefaultAuditPort`, `AuditDiffEngine`, `AuditLogRepository`) and read side (`AuditHistoryService`, `AuditQueryService`, `ActivityService`, Vaadin UI components). Auto-configured via a single `AuditAutoConfiguration`. Active whenever the jar is on the classpath — jar presence is the toggle.

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

Key changes that completed decoupling: `AdvertisementHistoryProjection` → generic `EntityHistoryProjection`; `AdvertisementHistoryDto` → `EntityHistoryDto` with `SnapshotPayload`; `ActivityProjection` uses single generic query; display name resolution delegated to `EntityDisplayNameResolver` SPI; CSS classes renamed to domain-neutral vocabulary (`entity-history-*`, `activity-feed-*`).

---

## 2026-05-19 — Profile activity decoration via SnapshotBinder + ActivityRowBinding SPI

**Decision:** Profile activity panels are built through `AuditUiExtension.buildProfileActivityPanel(ProfileActivityParams)`. Consumers pass a list of `ActivityRowBinding` — an SPI with `entityType()` + `decorate(ActivityItemDto): Component` — to attach per-row UI without the starter understanding the snapshot shape.

`SnapshotBinder<T>` (Spring prototype bean, `Configurable<T, Parameters<T>>`) is the canonical generic implementation: deserializes `ActivityItemDto.snapshotData` into consumer-provided `Class<T>`, checks a consumer-provided `Predicate<T>` for "is current", optionally fires a `BiConsumer<Long, T>` for restore.

**Why:** The previous pattern parsed snapshot JSON inside the starter — coupling it to specific user/settings shapes. The SPI puts shape knowledge on the consumer side.

**Rejected:** Decorator wrapper around `ProfileActivityPanel`; abstract `ActivityRowDecorator` requiring subclasses per shape.

---

## 2026-05-19 — Starter owns `auditObjectMapper`

**Decision:** `AuditAutoConfiguration` defines `@Bean("auditObjectMapper") ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` disabled, `@ConditionalOnMissingBean(name = "auditObjectMapper")` for override. All audit consumers qualify injection with `@Qualifier("auditObjectMapper")`.

**Why:** The starter previously consumed `userSettingsObjectMapper` — a marketplace-specific name. The starter must work in any Spring Boot context.

**Rejected:** `@Primary` on the starter's `ObjectMapper` — explicit `@Qualifier` over `@Primary` everywhere (durable project rule).

**2026-05-23 update:** `audit.enabled` property removed. Jar presence is the only toggle — no scenario exists where the jar is on the classpath but the subsystem should be disabled. `@ConditionalOnAuditEnabled` is now a plain marker annotation with no `@ConditionalOnProperty`.

---

## 2026-05-19 — Actor-centric SPI vocabulary; user-domain types purged

**Decision:** Audit subsystem speaks about actors and subjects, not users. Key renames: `AuditUserProvider` → `CurrentActorProvider`; `UserActivityExtension` → `ActivityFeedExtension`; `AdvertisementHistoryExtension` → `MediaHistoryExtension`; `UserSnapshotState` deleted; `AuditPort.getUserStateBefore/getUserStateAt` deleted in favor of generic `getSnapshotContent/getPreviousSnapshotContent(Long, EntityType)`; DB column `user_id` → `actor_id`.

**Why:** "User" is a marketplace-specific concept. "Actor" is neutral and applies to bots, workflows, or service accounts.

---

## 2026-05-16 — ActivityItemFieldsProvider SPI: expanded field display in activity feed

**Decision:** `ActivityItemFieldsProvider` SPI (`core.spi`) lets consumers supply a merged `List<ChangeEntry>` (changed + unchanged fields) for their entity types. `ActivityRowRenderer.buildRow` calls it for non-settings items; falls back to raw `changes` when no provider registered.

**Why:** Activity feed was showing only changed fields. Domain-specific field lists must not be hardcoded in the starter.

**Rejected:** Hardcoding field names in `ActivityRowRenderer` — introduces domain coupling into the starter.

---

## 2026-05-24 — Action badge uses type-specific CSS class in both history and activity

**Decision:** Every action badge (Created / Updated / Deleted / Restored) carries two CSS classes: a base class for shared layout (`entity-history-action` or `activity-feed-action`) and a modifier for color (`--created`, `--updated`, `--deleted`, `--restored`). The modifier is derived from `ActionType.name().toLowerCase()`. Color palette is identical in both `entity-history.css` and `activity-feed.css`: green / blue / red / yellow respectively.

**Rule:** Any new UI component that renders action badges must follow this two-class pattern. Single-class with hardcoded color is forbidden.

**Why:** Previously `ActivityRowRenderer` added only the base class, making all activity rows render with a hardcoded blue badge — no visual distinction between created/updated/deleted. `EntityHistoryPanel` already applied the modifier class; bringing `ActivityRowRenderer` to parity was the fix.

**Scope:** `EntityHistoryPanel` (ads history), `ActivityRowRenderer` (user activity, settings activity, all profile feeds).

---

## 2026-05-24 — Restore semantics: `getSnapshotContent` is the restore source

**Decision:** `AuditPort.getSnapshotContent(snapshotId, entityType)` is the only correct method for restore flows. `getPreviousSnapshotContent` returns the state *before* a recorded change; it must not be used to populate restore targets.

**Rule:** Any consumer implementing a restore button via `SnapshotBinder.onRestore` must call `getSnapshotContent`. `getPreviousSnapshotContent` is only for displaying diffs or "what changed" UI.

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
- ✅ `ActivityRowRenderer`: `addHistorySpan` / `addActivitySpan` — identical except CSS prefix → merged into `addSpan(Div, String, boolean, String cssBase)`
- ✅ `ActivityRowRenderer`: `buildAdvertisementActivityFieldsList` / `buildAdvHistoryFieldsList` — extracted via `ActivityRenderHook` SPI; renamed to generic `buildHistoryFieldsList(h, EntityRef)`. No `EntityType.ADVERTISEMENT` hardcode remains in the starter.
- ✅ `ActivityService` + `AuditHistoryService`: `resolveActorNames()` — extracted into `AuditDomainHelper.withResolvedActorNames(items, idGetter, nameApplier)`.

### MEDIUM
- ✅ `AdvertisementFormOverlayModeHandler` / `UserFormOverlayModeHandler`: extracted `AbstractFormOverlayModeHandler<D extends EditDto>` base class with `hasChanges()` and `wireSaveGuard()`.
- ✅ `AdvertisementViewOverlayModeHandler` / `UserViewOverlayModeHandler`: extracted `AbstractViewOverlayModeHandler` base class in `marketplace-app` with `final activate()` template method and 5 abstract hooks (`tabsCssClass`, `buildPrimaryTab`, `buildPrimaryContent`, `buildSecondaryTab`, `buildHeaderActions`). Lazy-loading and tab-switching logic centralised in `assembleTabbedContent`. `SecondaryTabDef` record carries tab + CSS class + `Supplier<Component>` loader; `null` = no secondary tab.
- `AuditAutoConfiguration` / `AttachmentAutoConfiguration`: identical `SchedulingConfigurer` registration for cleanup — duplicate every time a new starter adds cleanup.
- `AuditAutoConfiguration` / `AttachmentAutoConfiguration`: both create `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` disabled — same boilerplate in two places.

### MEDIUM-LOW
- ✅ `AuditMessages.fieldLabel()` hardcoded marketplace field names (`title`, `description`, `name`, `email`, `role`). Removed `fieldLabel()` + 5 `CHANGES_FIELD_*` constants from `AuditMessages`. `ActivityFieldsHook` implementations in marketplace now return `GenericChange(labelKey.key(), from, to)` entries instead of raw `FieldChange`. `buildActivityChangesDiv` updated to detect unchanged `GenericChange` entries (`before == null`). New `AdvertisementActivityFieldsHookImpl` added for `EntityType.ADVERTISEMENT`.
- `SnapshotBinder` lives in `audit-starter` UI layer; `UserViewOverlayModeHandler` imports it directly. If its API changes, marketplace compilation breaks.

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

1. **`AttachmentAuditHook` removed from audit-starter.** `ActivityService`, `AuditHistoryService`, and `EntityHistoryPanel` no longer import `attachment.spi`. A new `ActivityEnrichHook` SPI (`audit.spi`) replaces the direct attachment calls — method names are domain-neutral (`getAdditionalChanges`, `matchesCurrent`). Marketplace implements `ActivityEnrichHookImpl`, which delegates to `AttachmentAuditHook` via `ObjectProvider`.

2. **`ObjectProvider` removed for all required hook injections.** Hooks implemented by marketplace (`CurrentActorHook`, `AuditDomainHook`, `ActivityEnrichHook`) are now injected as plain required fields. `ObjectProvider` is kept only for cross-starter optional deps (`AttachmentAuditHook` in marketplace) and prototype bean factories (`rendererProvider`, `Builder.provider`).

**Why:** The audit starter called an `attachment.spi` hook directly — starter-to-starter coupling through the SPI layer. Marketplace is the correct orchestrator; it knows about both subsystems. `ObjectProvider` for required hooks implied optionality that was architecturally false.

---

## Deferred backlog

- EntityType: migrate from enum to string registry/descriptor when second consumer project appears
- SnapshotCodec: centralize ObjectMapper.readValue calls; eliminates JSON parsing inside projections
- ActivityProjection: JSON deserialization per row — negligible at 20 rows; revisit with cursor pagination
- jsonEquals readTree: expensive for large history lists — add parsed snapshot cache in EntityHistoryPanel.configure()
- EntityDisplayNameResolver.supports(): replace linear scan with map lookup when resolvers > 5
- LIMIT 20/100: replace with cursor pagination when needed
