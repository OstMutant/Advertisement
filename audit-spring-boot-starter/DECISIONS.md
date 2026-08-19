# Architecture & Technical Decisions — audit-spring-boot-starter

---

## ADR-001: Module structure — single starter, write + read side combined
**Status:** Accepted

**Context:** Write (`DefaultAuditPort`, `AuditLogRepository`) and read (`AuditHistoryService`,
`AuditQueryService`, `ActivityService`) sides were initially separate modules (`audit-core` +
`audit-read`). No concrete scenario requires the write side without the read side.

**Decision:** Full audit subsystem in one `audit-spring-boot-starter`. Auto-configured via
`AuditAutoConfiguration`. Active whenever the jar is on the classpath — jar presence is the toggle.
All Vaadin UI lives in `marketplace-app`.

**Consequences:**
- `AuditableSnapshot` marker interface carries `entityType()` — eliminates stringly-typed strings.
- `CurrentActorHook` SPI (`core.spi`) — the starter calls it without knowing about Spring Security.

---

## ADR-002: SQL coupling to domain tables removed via SPI batch pattern
**Status:** Accepted

**Context:** The audit starter previously coupled directly to `user_information` and `advertisement`
tables, making it unusable in any context that does not have those tables.

**Decision:** Audit projections do not JOIN domain tables. Instead, raw `actor_id`/entity ids are
returned from the query and resolved via SPI, not SQL join: `AuditDomainHook.resolveNames()`/
`.findExisting()` (`platform-commons/.../audit/spi/AuditDomainHook.java`), implemented by
`AuditDomainHookImpl` in marketplace-app.

**Consequences:** Rejected: per-row secondary queries — single bulk SELECT is one round-trip vs N.

---

## ADR-003: Full decoupling from advertisement domain
**Status:** Accepted

**Context:** The starter contained advertisement-specific entities, field names, and business logic,
making it non-reusable in other Spring Boot + Vaadin projects.

**Decision:** Zero advertisement knowledge in the starter. Key renames:
- `AdvertisementHistoryProjection` → `EntityHistoryProjection`
- `AdvertisementHistoryDto` → `AuditHistoryItemDto` with `SnapshotPayload`
- Display name resolution delegated to `EntityDisplayNameResolver` SPI
- CSS classes renamed to domain-neutral vocabulary — current classes are `entity-activity-*` and
  `activity-feed-*`

**Consequences:** The module is reusable in any Spring Boot + Vaadin project without modification.

---

## ADR-004: Starter owns `auditObjectMapper` with @Qualifier; audit.enabled removed
**Status:** Accepted

**Context:** The starter previously consumed `userSettingsObjectMapper` — a marketplace-specific name.
`audit.enabled` property was a no-op marker with no `@ConditionalOnProperty`.

**Decision:** `AuditAutoConfiguration` defines `@Bean("auditObjectMapper") ObjectMapper` with
`FAIL_ON_UNKNOWN_PROPERTIES` disabled and `@ConditionalOnMissingBean(name = "auditObjectMapper")`.
All audit consumers qualify injection with `@Qualifier("auditObjectMapper")`. `audit.enabled`
property and `@ConditionalOnAuditEnabled` removed entirely.

**Consequences:** Rejected: `@Primary` on the starter's `ObjectMapper` (project rule — explicit
`@Qualifier` over `@Primary` everywhere).

---

## ADR-005: Actor-centric SPI vocabulary; user-domain types purged
**Status:** Accepted

**Context:** "User" is a marketplace-specific concept unusable in neutral audit contexts.

**Decision:** Audit subsystem speaks about actors and subjects, not users. Key renames:
- `AuditUserProvider` → `CurrentActorProvider` → `CurrentActorHook`
- `AuditPort.getUserStateBefore/getUserStateAt` → `getSnapshotContent(Long, EntityType)` — the
  only method of this shape (see ADR-008)
- DB column `user_id` → `actor_id`

**Consequences:** "Actor" applies to bots, workflows, or service accounts equally.

---

## ADR-006: AuditActivityFieldsHook SPI — expanded field display in activity feed
**Status:** Superseded — see ADR-011's note on `AuditActivityFieldsHook`'s later removal

**Context:** Activity feed was showing only changed fields. Domain-specific field lists must not
be hardcoded in the starter.

**Decision:** `AuditActivityFieldsHook` SPI lets consumers supply a merged `List<ChangeEntry>`
(changed + unchanged fields) for their entity types. Called from
`AuditTimelineRowRenderer.buildActivityFieldsList()` for non-settings items; falls back to raw
`changes` when no provider registered.

**Consequences:** Rejected: hardcoding field names in `AuditActivityRowRenderer` — introduces
domain coupling into the starter. Superseded once every implementation of this Hook converged to a
one-line delegation with zero domain-specific logic — `AuditActivityFieldsHook` was removed
entirely and its only real caller, `AuditTimelineRowRenderer`, now does the field-name-to-label
mapping directly instead of crossing the module boundary (see `platform-commons/DECISIONS.md`
ADR-029's second refinement).

---

## ADR-007: Action badge — two CSS classes (base + modifier)
**Status:** Accepted

**Context:** `AuditActivityRowRenderer` previously added only the base class, making all activity
rows render with a hardcoded blue badge — no visual distinction between created/updated/deleted.

**Decision:** Every action badge carries two CSS classes: base (`entity-activity-action` or
`activity-feed-action` — corrected 2026-07-13, was written as `entity-history-action` here,
matching the same stale rename as ADR-003) and modifier (`--created`, `--updated`, `--deleted`,
`--restored`). Modifier derived from `ActionType.name().toLowerCase()`.

**Consequences:** Any new UI component rendering action badges must follow this two-class pattern.
Single-class with hardcoded color is forbidden.

---

## ADR-008: Restore semantics — getSnapshotContent is the restore source
**Status:** Accepted

**Context:** A `getPreviousSnapshotContent` method once returned the state *before* a recorded
change, which inverts the UX expectation "make the entity look like it did at this recorded
moment". It has since been removed entirely — diff display works directly from snapshot pairs via
`AuditableSnapshot.diff()` (see ADR-014).

**Decision:** `AuditPort.getSnapshotContent(snapshotId, entityType)` is the only correct method
for restore flows.

**Consequences:** Any consumer implementing a restore button (via `OverlayFormBinder`) must call
`getSnapshotContent`.

---

## ADR-009: Jackson Id.NAME + subtype registration in marketplace JacksonConfig
**Status:** Accepted

**Context:** `Id.CLASS` embeds fully-qualified class names into stored JSON — any package rename
silently breaks deserialization of all existing snapshot rows.

**Decision:** `AuditableSnapshot` uses `@JsonTypeInfo(use = Id.NAME, property = "@type")`.
Each snapshot DTO carries `@JsonTypeName` with a stable short name. Subtype registration done
in `marketplace-app/JacksonConfig` via `@PostConstruct registerAuditSnapshotSubtypes()`.

| Class | `@type` value |
|---|---|
| `AdvertisementSnapshotDto` | `"advertisement"` |
| `UserSnapshotDto` | `"user"` |
| `SettingsSnapshotDto` | `"user_settings"` |
| `TaxonSnapshotDto` | `"taxon_state"` (added when taxon-spring-boot-starter landed; registered in `JacksonConfig`, table row added 2026-07-13) |

**Consequences:**
- When adding a new `AuditableSnapshot`: annotate with `@JsonTypeName("stable_name")` + register
  in `JacksonConfig`. Short names must be stable — changing them requires a DB migration.
- Backward compatibility: existing DB rows with `Id.CLASS` format are incompatible. Dev: deploy
  with `--reset`. Production: data migration script required before first deployment.

---

## ADR-010: Audit decoupled from attachment via AuditActivityEnrichHook
**Status:** Accepted — see `platform-commons/DECISIONS.md` ADR-011 for the SPI-side counterpart
of the same decision (corrected together, 2026-07-16)

**Context:** The audit starter called `AttachmentAuditHook` (an `attachment.spi` interface)
directly — starter-to-starter coupling. Marketplace is the correct orchestrator.

**Decision:** `AttachmentAuditHook` removed from audit-starter. New `AuditActivityEnrichHook`
SPI (`audit.spi`) replaces the direct attachment calls with domain-neutral methods — current
signature (corrected 2026-07-16, originally listed `getAdditionalChanges`/`matchesCurrent`, which
do not exist anywhere in the codebase): `entityType()`, `merge(...)`, `enrichActivity(...)`,
`getMediaStateForSnapshot(EntityRef, Long)`. Marketplace implements `ActivityEnrichHookImpl`.
`AuditReadService` injects the hook list as a plain required field
(`List<AuditActivityEnrichHook>`), not `ObjectProvider`.

**Consequences:** Audit starter must never import from `attachment.*` packages.

---

## ADR-011: SPI consolidation — always-co-implemented pairs merged
**Status:** Accepted

**Context:** Several pairs of SPI interfaces were always co-implemented by the same class in
marketplace-app. The split served no isolation purpose, just added indirection.

**Decision:**
1. `AuditDomainHook` absorbs `EntityNameHook` — added `String resolveDisplayName(AuditableSnapshot)`.
2. `AuditActivityEnrichHook` absorbs `AuditActivityRenderHook` — added `EntityType entityType()`
   and `getMediaStateForSnapshot`.

**Consequences:** Interface count reduced; several files deleted. A third planned consolidation
(`AuditActivityFieldsHook` absorbing `AuditFieldLabelHook`) does not apply anymore —
`AuditActivityFieldsHook` was removed entirely once every implementation converged to a one-line
delegation with zero domain-specific logic; its label-resolution job now lives directly in
`AuditTimelineRowRenderer` (marketplace-app), not behind an SPI.

---

## ADR-014: changes_summary removed — diff computed dynamically on read
**Status:** Accepted

**Context:** Pre-computing diffs at write time produced stale diffs when diff logic changed.

**Decision:** `changes_summary JSONB` column removed from `audit_log`. Diff computed at read time
by `AuditableSnapshot.diff()` from `snapshot_data` (current) and `prev_snapshot_data` (LAG
window function).

**Consequences:** Write side simplified — no `AuditableSnapshot.diff()` dependency.
Rule: never store pre-computed diffs. Always derive changes from snapshot pairs at read time.

---

## ADR-015: Generic AuditLogProjection — domain-free findRows/findTimeline repository design
**Status:** Accepted

**Context:** `AuditLogRepository` previously depended on `AuditHistoryItemDto`, `AuditActivityItemDto`,
and `EntityNameHook` — service-layer concerns bleeding into the persistence layer. Earlier query
designs also hardcoded domain knowledge (e.g. `entity_type IN ('USER', 'USER_SETTINGS')`) and used
unbounded window-function scans over all of an actor's rows.

**Decision:** Single `AuditLogProjection` record returned by the repository, with fields mapping
1:1 to SQL columns — no deserialization in the mapper. Two domain-free query methods:
- `findRows(EntityType, Long entityId, Long filterActorId, int limit)` — entity-scoped, using
  `ROW_NUMBER()`/`LAG()` window functions.
- `findTimeline(...)` (+ `countTimeline`) — the unified cross-entity feed. Marketplace passes
  explicit `EntityRef` lists (e.g. `[EntityRef(USER, id), EntityRef(USER_SETTINGS, id)]`) rather
  than the repository knowing which entity types constitute a "profile". Bounded to an actor's
  top-N rows (indexed on `actor_id`, `LIMIT`), computing `version`/`prev_id`/`prev_snapshot_data`
  via correlated subqueries on only those N rows rather than a window-function scan over the full
  table.

**Consequences:**
- `AuditLogRepository` must not import any service-layer DTO or hook, and must not contain any
  `EntityType` name literals or UNION queries combining domain-specific filters.
- `ROW_NUMBER()`/`LAG()`/correlated subqueries stay in SQL — Java-side computation breaks future
  pagination.
- Rejected: single UNION SQL — dynamic SQL assembly in the repository couples it to the "activity"
  concept and splits SQL across Java strings.

---

## ADR-016: i18n consolidated into marketplace-app; no starter properties files
**Status:** Accepted

**Context:** Display label strings were previously passed from marketplace into `AuditUiPort`
params — coupling i18n to the SPI boundary.

**Decision:** All audit i18n keys live in `org.ost.marketplace.services.i18n.I18nKey` under
the `audit.*` namespace prefix. The starter ships no properties files and no i18n enum.

**Consequences:** Audit display strings resolved only inside marketplace-app UI components via
`I18nService.get(I18nKey.*)`. Never pass label strings across the module boundary.

---

## ADR-017: `audit.changes.*` field-label namespace convention
**Status:** Accepted

**Context:** Field-level change labels shown in the Activity/Timeline UI need a clear i18n
namespace to avoid collision between entity domains (e.g. Advertisement's `title` vs. User's
`name`). `ChangeEntry` (`platform-commons/.../core/model/ChangeEntry.java`) is a sealed interface
with two variants, `FieldChange` and `MediaChange` — neither carries an i18n key; no label is
ever persisted in `audit_log`. Labels are resolved at **render time**: `AuditTimelineRowRenderer`
(marketplace-app) maps a raw field key (e.g. `"nameEn"`, `"categoryIds"`) to a human label via the
single consolidated `I18nKey` enum.

**Decision:** Field-label i18n keys in `I18nKey` follow the `audit.changes.*` namespace convention
(e.g. `audit.changes.media`) for cross-entity consistency. Translations live in
`marketplace-app/i18n/messages*.properties`.

**Consequences:** `I18nKey` is a single consolidated enum in `org.ost.marketplace.services.i18n.I18nKey`.

---

## ADR-018: AuditHistoryRowActionsHook — cancelled
**Status:** Deprecated

**Context (historical):** Hook was planned to decouple the restore button from `AuditHistoryPanel`.

**Decision:** Cancelled 2026-06-15. All Vaadin UI lives in marketplace-app. There is no second
starter consumer that would require this SPI. The restore button callback (`onRestoreRequested`)
in `AuditHistoryPanel.Parameters` is the correct and sufficient pattern.

**Consequences:** Do not re-introduce `AuditHistoryRowActionsHook`.

---

## ADR-019: AuditReadService direct injection in marketplace UI panels is correct
**Status:** Accepted

**Context:** `AuditActivityPanel`, `AuditTimelinePanel` in marketplace-app inject `AuditReadService`
directly from `org.ost.audit.services.*`. This looked like a boundary violation.

**Decision:** This is correct design. All Vaadin UI lives in marketplace-app. `AuditUiPort` was
removed as unnecessary indirection (2026-06-15). Marketplace UI calling audit starter services
directly IS the legitimate service ↔ UI boundary — not a violation.

**Consequences:** Do not re-introduce `AuditUiPort`.

---

## ADR-020: id tiebreaker in SQL window functions for deterministic versioning
**Status:** Accepted

**Context:** Rows inserted in the same transaction share the same `created_at` millisecond.
Without `id` as a tiebreaker, `ROW_NUMBER()` is non-deterministic.

**Decision:** All window functions in `AuditLogRepository.findRows` use `ORDER BY created_at, id`:
`ROW_NUMBER()`, `LAG(id)`, `LAG(snapshot_data::text)`. Final `ORDER BY` also uses
`created_at DESC, id DESC`.

**Consequences:** `id` is a monotonically increasing sequence — stable tiebreaker within the
same timestamp.

---

## ADR-021: withSameTypePrevSnapshot — in-memory same-type diff post-processing
**Status:** Accepted

**Context:** When a mixed-type row (a different `AuditableSnapshot` subtype) appears between two
same-entity rows, SQL `LAG` returns the immediately preceding row regardless of type, and a diff
across incompatible snapshot types returns only new values (no "old" side), breaking the activity
diff UI. The original motivating type (`CategoryChangeSnapshotDto`) was later removed by
snapshot-cleanup, so today the method is a pass-through in the common case — it stays as the guard
for any future mixed-type entity history (`AuditReadService.java:37,94`).

**Decision:** `AuditReadService.getEntityActivity` passes `findRows` result through
`withSameTypePrevSnapshot` before building `AuditActivityItemDto` items. This method walks
oldest-first and substitutes `prevSnapshot` with the nearest previous row of the same Java class
(tracked in a `Map<Class<?>, AuditableSnapshot>`).

**Consequences:** Do NOT add type-filtering correlated subqueries to `AuditLogRepository` —
SQL must stay domain-free. In-memory correction is the right fix.

---

## ADR-022: restorableCount replaces items.size() for restore button visibility
**Status:** Superseded (2026-07-03, snapshot-cleanup — `isRestorable()` and
`CategoryChangeSnapshotDto` were removed; every row is restorable now, so plain
`items.size()` is correct again and `restorableCount` no longer exists in code)

**Context:** Category-change rows (`CategoryChangeSnapshotDto.isRestorable() == false`) inflate
the total item count. When `historySize > 1`, the CREATED row shows a "Restore" button. With
categories, a freshly created advertisement has 3 rows (1 CREATED + 2 category UPDATED) →
`historySize = 3 > 1` → Restore button appears on the CREATED row, which is wrong.

**Decision:** `AuditActivityPanel.configure` computes
`restorableCount = items.stream().filter(i -> i.snapshotData().isRestorable()).count()`
and passes it as `historySize` to `AuditActivityRowRenderer.RenderConfig`.

**Consequences:** Counting only restorable items gives the semantically correct value.

---

## ADR-023: Top-level Timeline tab replaces inline timeline tabs
**Status:** Accepted (done 2026-06-23)

**Context:** Per-overlay timeline queried by `actor_id` only. A top-level tab with proper filters
gives full audit context without navigating into individual overlays.

**Decision:** Replaced Timeline tabs in Users overlay and Settings overlay with a dedicated
top-level **Timeline** navigation tab (alongside Listings and Users), with filter, sort,
and pagination. Backend query via `AuditPort.getTimelinePage` / `countTimeline`.

**Consequences:**
- USER: sees only own activity (actor filter forced by `AccessEvaluator`).
- MODERATOR/ADMIN: full feed, filterable by actor/entity type/action type/date.

---

## ADR-024: captureRestore() — dedicated method for restore audit events
**Status:** Accepted (done 2026-06-26)

**Context:** `AuditPort.captureUpdate()` was reused for restore events. This stored
`action_type = 'UPDATED'` for both genuine field edits and soft-delete restores, making
them indistinguishable in the audit log without inspecting snapshot data.

**Decision:** `AuditPort.captureRestore(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot,
@NonNull Long actorId)` added to the port interface. `DefaultAuditPort.captureRestore()` writes
`ActionType.RESTORED` to `audit_log`. `ActionType.RESTORED` added to `core.model.ActionType` enum.
All restore-capable services (currently `TaxonService.restore()`) use `captureRestore`.

**Consequences:**
- `AuditActivityRowRenderer` CSS modifier `--restored` must be handled in all badge renderers.
- New restore rows appear in audit activity panels with distinct visual treatment.
- `isRestorable()` logic in `AuditActivityPanel` is unaffected — restorability is determined
  by the snapshot type, not the action type.

---

## Deferred backlog

Deferred performance optimizations (SnapshotCodec JSON parsing centralization, per-row activity
JSON deserialization, a snapshot equality-check cache) — each gated on its own trigger (cursor
pagination landing, or result/page sizes growing significantly); tracked in the backlog until a
trigger fires.
