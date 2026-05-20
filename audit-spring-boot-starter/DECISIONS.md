# Architecture & Technical Decisions — audit-spring-boot-starter

---

## 2026-05-13 — Module created: write-side audit extracted from marketplace-app

**Decision:** `audit-spring-boot-starter` owns the full audit subsystem — write side (`DefaultAuditPort`, `NoOpAuditPort`, `AuditDiffEngine`, `AuditFieldCache`, `AuditSnapshotMapper`, `AuditLogRepository`) and read side (`AuditReadRepository`, `AuditHistoryService`, `AuditQueryService`, `ActivityService`, `ActivityPanel`, `ProfileActivityPanel`). Auto-configured via a single `AuditAutoConfiguration`. Enabled by default (`audit.enabled=true`).

**Why:** Audit is infrastructure. Extracting it as a starter allows: (a) running the app without audit overhead, (b) reuse in future modules, (c) symmetry with `attachment-spring-boot-starter`. Write and read sides were initially separate modules (`audit-core-spring-boot-starter` + `audit-read-spring-boot-starter`) but merged on 2026-05-13 — fewer modules is simpler when there is no concrete scenario requiring the write side without the read side.

**Key patterns:**
- `@ConditionalOnAuditEnabled` gates `DefaultAuditPort` bean creation
- `NoOpAuditPort` is the unconditional fallback via `@ConditionalOnMissingBean` — wiring never fails
- `AuditableSnapshot` marker interface (in contracts) carries `entityType()` — eliminates stringly-typed entity-type strings
- `CurrentUserProvider` SPI (in contracts, `core.spi`) — the starter calls it without knowing about Spring Security or session context (unified with attachment-starter on 2026-05-18 — see `platform-contracts/DECISIONS.md`)

**Rejected:** `@ConditionalOnAuditEnabled` in `platform-contracts` — contracts must be Spring-free pure Java.

---

## 2026-05-13 — audit-core + audit-read merged into one module

**Decision:** `audit-core-spring-boot-starter` (write side) and `audit-read-spring-boot-starter` (read side + Vaadin UI) were merged into a single `audit-spring-boot-starter`. The `read/` subpackage was dropped; all classes live flat under `org.ost.audit.*` (mirroring `org.ost.attachment.*` in `attachment-spring-boot-starter`).

**Why:** The split was premature. The only motivation for keeping them separate was "write side without Vaadin for a future REST service", but no such service exists. Two modules with a mandatory one-way dependency add complexity without benefit. The merged module uses a single `AuditAutoConfiguration`.

**Rejected:** Keeping the split to preserve the option of a Vaadin-free write-side jar — deferred until a concrete REST use case materialises.

---

## 2026-05-13 — SQL coupling to domain tables removed via SPI batch pattern

**Decision:** `ActivityProjection` and `AdvertisementHistoryProjection` no longer JOIN `user_information` or use EXISTS subqueries against `advertisement`/`user_information`. Instead: (a) raw `actor_id` is returned from the query; (b) `AuditActorNameResolver` SPI (`platform-contracts`) performs a single bulk `SELECT id, name FROM user_information WHERE id = ANY(:ids)` after the query; (c) `AuditEntityExistenceChecker` SPI performs a single bulk `SELECT id FROM <table> WHERE id = ANY(:ids)` grouped by entity type. Both SPIs are wired via `ObjectProvider` — if absent, actor names stay `null` and entity existence defaults to `false`.

`AdvertisementHistoryDto` gained an `actorId` field so the service can do bulk resolution without a secondary per-row query. Implementations (`AuditActorNameResolverImpl`, `AuditEntityExistenceCheckerImpl`) live in `marketplace-app`.

**Why:** The starter previously coupled directly to `user_information` and `advertisement` tables, making it unusable in any context that does not have those tables. The SPI pattern mirrors `AuditUserProvider` — the starter knows nothing about the domain schema and calls back to the host application for any domain-specific data.

**Rejected:** Per-row secondary queries (`getActorIdForSnapshot`) — single bulk SELECT with `ANY(:ids)` is one round-trip vs N.

---

## 2026-05-16 — Full decoupling from advertisement domain DONE

**Target state achieved:** `audit-spring-boot-starter` contains zero knowledge of advertisement-specific entities, field names, or business logic. The module is reusable in any Spring Boot + Vaadin project without modification.

**What was changed:**
- `AdvertisementHistoryProjection` → deleted; replaced with generic `EntityHistoryProjection` (PARTITION BY entity_type + entity_id, returns raw `snapshot_data` JSON).
- `AdvertisementHistoryDto` → renamed to `EntityHistoryDto`; `title`/`description`/`prevTitle`/`prevDescription` replaced by `SnapshotPayload snapshotData` / `SnapshotPayload prevSnapshotData`.
- `AuditHistoryService.getAdvertisementHistory()` → renamed to `getEntityHistory(EntityType, Long, Long, boolean)`.
- `AdvertisementHistoryPanel` → renamed to `EntityHistoryPanel`; `currentTitle`/`currentDesc` removed; current-state comparison done via `jsonEquals(SnapshotPayload, SnapshotPayload)` using `ObjectMapper.readTree`.
- `ActivityProjection` — UNION SQL replaced with single generic query; `display_name` / typed snapshot fields removed; `snapshot_data::text` added; display name resolution delegated to `EntityDisplayNameResolver` SPI (new interface in `platform-contracts`).
- `ActivityItemDto` — `snapshotTitle`, `snapshotDescription`, `snapshotEmail`, `snapshotRole` removed; `SnapshotPayload snapshotData` added.
- `SnapshotContent` — `title`/`description` replaced by `SnapshotPayload snapshotData`.
- `AuditLogRepository.getSnapshotContent()` — now generic: takes `(Long snapshotId, EntityType entityType)`, returns raw JSON in `SnapshotPayload`.
- `AuditLogRepository.findLastSnapshotId()` — now generic: takes `(EntityType, Long)`.
- `AuditPort` — two new methods: `appendNoteToLastSnapshot(EntityType, Long, String)` and `getSnapshotContent(Long, EntityType)`.
- `AuditUiExtension` — `buildAdvertisementHistoryPanel(AdvertisementHistoryParams)` → `buildEntityHistoryPanel(EntityHistoryParams)`; `currentTitle`/`currentDesc` removed from params.
- `ActivityRowRenderer` — advertisement-specific field rendering removed; `buildRow()` delegates to `activityPanel.buildChangesList()` for all entity types.
- `AdvertisementDisplayNameResolver` (`marketplace-app`) implements `EntityDisplayNameResolver` for ADVERTISEMENT / USER / USER_SETTINGS.
- `AdvertisementService` — `getSnapshotContent(Long, EntityType.ADVERTISEMENT)` now returns `SnapshotPayload`; `ObjectMapper` used to parse title/description.

---

## 2026-05-19 — Profile activity decoration through `SnapshotBinder<T>` + `ActivityRowBinding` SPI

**Decision:** `ProfileActivityPanel` no longer parses snapshot payloads. Per-row decorations (current-state badges, restore buttons) are produced by `ActivityRowBinding` instances supplied by the host application through `AuditUiExtension.buildProfileActivityPanel(ProfileActivityParams)`. The canonical generic implementation `SnapshotBinder<T>` (Spring prototype bean, `Configurable<T, Parameters<T>>` per the CLAUDE.md UI Component Patterns) lives in this module so consumers do not have to reinvent the deserialize-and-decorate dance.

`SnapshotBinder<T>` accepts: `entityType`, `Class<T> snapshotClass`, `Predicate<T> isCurrent`, optional `BiConsumer<Long, T> onRestore` (null → no restore button), `currentLabel`, `restoreLabel`. It deserializes `ActivityItemDto.snapshotData` (via `userSettingsObjectMapper`), tests `isCurrent`, and emits either a "current state" badge, a "restore" button, or nothing. The `Builder<T>` mirrors the `OverlayFormBinder<T>.Builder` pattern.

`ProfileActivityPanel.Parameters` now carries a `List<ActivityRowBinding> bindings`. `decorateRow` looks up the first binding whose `entityType()` matches the row and appends `binding.decorate(item)` if non-null.

**Why:** Previously the panel hardcoded "if entityType == USER, parse `UserSnapshotState`, compare to current `User`" — a domain leak into the starter. The SPI puts shape knowledge on the consumer side; the starter only iterates bindings.

**Rejected:** Decorator pattern (extra panel wrapper) and abstract `ActivityRowDecorator<T>` (forces inheritance per shape). Builder+Parameters mirrors the established `Configurable<T, P>` pattern.

---

## 2026-05-19 — UI components symmetrically gated by `@ConditionalOnAuditEnabled`

**Decision:** `ActivityRowRenderer` and `ActivityPanel` now carry `@ConditionalOnAuditEnabled` to match the other audit UI beans (`EntityHistoryPanel`, `ProfileActivityPanel`, `SnapshotBinder`, `AuditUiExtensionImpl`). Previously these two were the only `@SpringComponent` UI beans in `org.ost.audit.ui` without the conditional, so they would be instantiated even with `audit.enabled=false`.

**Why:** No functional effect (Vaadin prototypes were only resolved through `AuditUiExtension`, which is itself gated), but the inconsistency was a maintenance hazard — a future caller wiring `ActivityPanel` directly would silently bypass the disable flag. Uniform gating eliminates that risk.

**Rejected:** Promoting the conditional to a package-level `@ComponentScan` filter — `@ConditionalOnAuditEnabled` on each `@SpringComponent` is more discoverable and matches the existing per-bean style.

---

## 2026-05-19 — Starter owns `auditObjectMapper`; Liquibase gated by `audit.enabled`

**Decision:** `AuditAutoConfiguration` now defines `@Bean("auditObjectMapper") ObjectMapper` (with `FAIL_ON_UNKNOWN_PROPERTIES` disabled), `@ConditionalOnMissingBean(name = "auditObjectMapper")` for override. All audit-side consumers — `ActivityProjection`, `EntityHistoryProjection`, `AuditReadRepository`, `ActivityRepository`, `AuditSnapshotMapper`, `ActivityRowRenderer`, `EntityHistoryPanel`, `SnapshotBinder` — qualify their `ObjectMapper` injection with `@Qualifier("auditObjectMapper")` (lombok.copyableAnnotations propagates the qualifier from field to generated constructor parameter). The `auditLiquibase` bean is now `@ConditionalOnAuditEnabled` so `audit.enabled=false` produces no schema apply attempt.

**Why:** Previously the starter relied on the host's `userSettingsObjectMapper` bean — a marketplace-specific name — and silently consumed it. The starter must work in any Spring Boot context, including ones with zero `ObjectMapper` beans or several. Naming the bean prevents collisions, qualifying every injection site keeps wiring explicit, and gating Liquibase mirrors the existing `attachmentLiquibase` pattern: disabling the subsystem leaves no residue.

**Rejected:** `@Primary` on the starter's `ObjectMapper` (user feedback: explicit `@Qualifier` over `@Primary` everywhere — auto-memory entry). Adding `JavaTimeModule` to the starter mapper — `jackson-datatype-jsr310` is not in the starter's pom and the audit JSON shapes do not need it.

---

## 2026-05-19 — CSS classes renamed to domain-neutral vocabulary

**Decision:** Stylesheet `adv-history.css` → `entity-history.css`; CSS class prefix `adv-history-*` → `entity-history-*` and `user-activity-*` → `activity-feed-*` across every Java component, marketplace theme stylesheet, Playwright selector, README excerpt, and helper. `@CssImport("./entity-history.css")` in `ActivityRowRenderer`; `@import url("./activity-feed.css")` in marketplace `styles.css`.

**Why:** The audit subsystem renders histories and feeds for arbitrary entities; "adv-" hardcoded "advertisement" in selector vocabulary, and "user-activity" baked the marketplace's user concept into a generic feed. New names describe the surface (entity history list, activity feed row) rather than a specific consumer.

**Rejected:** Leaving the old CSS file as a forwarder shim — no production version uses it yet, so a flat rename keeps the starter clean.

---

## 2026-05-19 — Actor-centric SPI vocabulary; user-domain types purged

**Decision:** Audit subsystem speaks about actors and subjects, not users. Renames and removals:

- `AuditUserProvider` (later `CurrentUserProvider`) → `CurrentActorProvider` (`getCurrentActorId()`).
- `UserActivityExtension` → `ActivityFeedExtension`.
- `AdvertisementHistoryExtension` → `MediaHistoryExtension`.
- `UserSnapshotState` deleted (was the only typed snapshot in the contract surface).
- `AuditPort.getUserStateBefore(Long)` / `getUserStateAt(Long)` deleted in favor of the already-generic `getPreviousSnapshotContent(Long, EntityType)` / `getSnapshotContent(Long, EntityType)`.
- DB columns renamed: `user_id` → `actor_id` in audit log + related projections; field descriptors updated.
- `ActorSnapshot(displayName)` record introduced in `audit.dto` to carry display-ready actor metadata.

**Why:** "User" is a marketplace-specific concept and was the last domain leak in the contract surface. Renaming to "actor" makes the starter usable in any system whose acting principal is not a human user (workflow, bot, service account). Consumers in `marketplace-app` (e.g. `UserService.restoreToSnapshot`) now load `SnapshotContent` and deserialize their own `UserSnapshot` via `ObjectMapper` — domain JSON parsing stays on the domain side.

**Rejected:** Keeping `UserSnapshotState` "for convenience" — confirmed unused after consumers were migrated to `SnapshotContent` + per-domain snapshot record.

---

## 2026-05-16 — ActivityItemFieldsProvider SPI: expanded field display in activity feed

**Decision:** Added `ActivityItemFieldsProvider` SPI (contracts `core.spi`). `ActivityRowRenderer.buildRow` calls it for non-settings items: if a provider supports the entity type AND `snapshotData` is non-empty, the provider returns a merged `List<ChangeEntry>` (changed fields with diff + unchanged fields as `FieldChange(field, null, currentValue)`). Falls back to raw `changes` when no provider registered. `UserActivityFieldsProvider` in `marketplace-app` implements this for `EntityType.USER`.

**Why:** Activity feed was showing only changed fields. Unchanged fields (e.g. email, role when only name changed) were invisible. Domain-specific field lists must not be hardcoded in audit-starter — the SPI keeps the starter generic and reusable.

**Rejected:** Hardcoding `"name"`, `"email"`, `"role"` in `ActivityRowRenderer` or adding `email` to `UserSnapshotState` — both introduce domain coupling into the starter.

---

## Deferred backlog

- SnapshotPayload: add schemaVersion + metadata when snapshot versioning is needed
- EntityType: migrate from enum to string registry/descriptor when second consumer project appears
- SnapshotCodec: centralize ObjectMapper.readValue calls; eliminates leakage across layers
- ActivityProjection: JSON deserialization per row — negligible at 20 rows; revisit with cursor pagination
- jsonEquals readTree: expensive for large history lists — add parsed snapshot cache in EntityHistoryPanel.configure()
- EntityDisplayNameResolver.supports(): replace linear scan with map lookup when resolvers > 5
- LIMIT 20/100: replace with cursor pagination when needed
- mediaMatchCurrent: remaining domain coupling, accepted — goes through AdvertisementHistoryExtension SPI
- EntityHistoryPanel: thin via presenter/view-model layer in future UI refactoring
- Snapshot schema migration strategy: needed before first AdvertisementSnapshot field rename
- SnapshotPayload.isEmpty(): consider treating "{}" as empty semantic snapshot
