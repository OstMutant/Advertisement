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
- `ActivityRowRenderer`: `buildAdvertisementActivityFieldsList` / `buildAdvHistoryFieldsList` — ~80 lines of near-identical logic (split changes, expand text fields, render spans, render media section). Only differ in CSS prefix and media lookup method.
- `ActivityService` + `AuditHistoryService`: `resolveActorNames()` — identical bulk-resolution logic in both services.

### MEDIUM
- `AdvertisementFormOverlayModeHandler` / `UserFormOverlayModeHandler`: same `activate()` skeleton (titleField, saveButton, buildBinder). Candidate for a base class with template methods.
- `AdvertisementViewOverlayModeHandler` / `UserViewOverlayModeHandler`: same tab-switching + lazy history-loading pattern.
- `AuditAutoConfiguration` / `AttachmentAutoConfiguration`: identical `SchedulingConfigurer` registration for cleanup — duplicate every time a new starter adds cleanup.
- `AuditAutoConfiguration` / `AttachmentAutoConfiguration`: both create `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` disabled — same boilerplate in two places.

### MEDIUM-LOW
- `AuditMessages.fieldLabel()` hardcodes marketplace field names — marketplace must modify the starter to add new audited fields. Should be driven by `ActivityFieldsHook`.
- `SnapshotBinder` lives in `audit-starter` UI layer; `UserViewOverlayModeHandler` imports it directly. If its API changes, marketplace compilation breaks.

---

## Deferred backlog

- EntityType: migrate from enum to string registry/descriptor when second consumer project appears
- SnapshotCodec: centralize ObjectMapper.readValue calls; eliminates JSON parsing inside projections
- ActivityProjection: JSON deserialization per row — negligible at 20 rows; revisit with cursor pagination
- jsonEquals readTree: expensive for large history lists — add parsed snapshot cache in EntityHistoryPanel.configure()
- EntityDisplayNameResolver.supports(): replace linear scan with map lookup when resolvers > 5
- LIMIT 20/100: replace with cursor pagination when needed
