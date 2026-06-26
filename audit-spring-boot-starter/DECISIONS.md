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

**Decision:** Audit projections do not JOIN domain tables. Instead:
- Raw `actor_id` returned from the query.
- `AuditActorNameResolver` SPI performs a single bulk `SELECT id, name FROM user_information WHERE id = ANY(:ids)`.
- `AuditEntityExistenceChecker` SPI performs a single bulk `SELECT id FROM <table> WHERE id = ANY(:ids)` per entity type.
Both SPIs wired via `ObjectProvider` — absent → actor names stay `null`, existence defaults `false`.

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
- CSS classes renamed to domain-neutral vocabulary (`entity-history-*`, `activity-feed-*`)

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
- `AuditPort.getUserStateBefore/getUserStateAt` → `getSnapshotContent/getPreviousSnapshotContent(Long, EntityType)`
- DB column `user_id` → `actor_id`

**Consequences:** "Actor" applies to bots, workflows, or service accounts equally.

---

## ADR-006: AuditActivityFieldsHook SPI — expanded field display in activity feed
**Status:** Accepted

**Context:** Activity feed was showing only changed fields. Domain-specific field lists must not
be hardcoded in the starter.

**Decision:** `AuditActivityFieldsHook` SPI lets consumers supply a merged `List<ChangeEntry>`
(changed + unchanged fields) for their entity types. `AuditActivityRowRenderer.buildRow` calls it
for non-settings items; falls back to raw `changes` when no provider registered.

**Consequences:** Rejected: hardcoding field names in `AuditActivityRowRenderer` — introduces
domain coupling into the starter.

---

## ADR-007: Action badge — two CSS classes (base + modifier)
**Status:** Accepted

**Context:** `AuditActivityRowRenderer` previously added only the base class, making all activity
rows render with a hardcoded blue badge — no visual distinction between created/updated/deleted.

**Decision:** Every action badge carries two CSS classes: base (`entity-history-action` or
`activity-feed-action`) and modifier (`--created`, `--updated`, `--deleted`, `--restored`).
Modifier derived from `ActionType.name().toLowerCase()`.

**Consequences:** Any new UI component rendering action badges must follow this two-class pattern.
Single-class with hardcoded color is forbidden.

---

## ADR-008: Restore semantics — getSnapshotContent is the restore source
**Status:** Accepted

**Context:** `getPreviousSnapshotContent` returns the state *before* a recorded change, which
inverts the UX expectation "make the entity look like it did at this recorded moment".

**Decision:** `AuditPort.getSnapshotContent(snapshotId, entityType)` is the only correct method
for restore flows. `getPreviousSnapshotContent` is reserved for diff display only.

**Consequences:** Any consumer implementing a restore button via `AuditSnapshotBinder.onRestore`
must call `getSnapshotContent`.

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

**Consequences:**
- When adding a new `AuditableSnapshot`: annotate with `@JsonTypeName("stable_name")` + register
  in `JacksonConfig`. Short names must be stable — changing them requires a DB migration.
- Backward compatibility: existing DB rows with `Id.CLASS` format are incompatible. Dev: deploy
  with `--reset`. Production: data migration script required before first deployment.

---

## ADR-010: Audit decoupled from attachment via AuditActivityEnrichHook
**Status:** Accepted

**Context:** The audit starter called `AttachmentAuditHook` (an `attachment.spi` interface)
directly — starter-to-starter coupling. Marketplace is the correct orchestrator.

**Decision:** `AttachmentAuditHook` removed from audit-starter. New `AuditActivityEnrichHook`
SPI (`audit.spi`) replaces the direct attachment calls with domain-neutral method names
(`getAdditionalChanges`, `matchesCurrent`). Marketplace implements `ActivityEnrichHookImpl`.
Required hook injections now use plain required fields, not `ObjectProvider`.

**Consequences:** Audit starter must never import from `attachment.*` packages.

---

## ADR-011: SPI consolidation — 3 always-co-implemented pairs merged
**Status:** Accepted

**Context:** Three pairs of SPI interfaces were always co-implemented by the same class in
marketplace-app. The split served no isolation purpose, just added indirection.

**Decision:**
1. `AuditActivityFieldsHook` absorbs `AuditFieldLabelHook` — added `default String labelFor(String rawFieldKey)`.
2. `AuditDomainHook` absorbs `EntityNameHook` — added `String resolveDisplayName(EntityType, AuditableSnapshot)`.
3. `AuditActivityEnrichHook` absorbs `AuditActivityRenderHook` — added `EntityType entityType()`,
   `getMediaStateForSnapshot`, `getMediaStateAtVersion`.

**Consequences:** Interface count reduced from 13 to 10. 4 files deleted.
Rule: `AuditActivityFieldsHook.expandFields()` must never call `I18nService`. `labelFor()` is
the correct place for i18n (invoked from the UI thread in the renderer).

---

## ADR-012: Repository — findRows + findForActivity replace domain-coupled UNION
**Status:** Accepted

**Context:** `findActivityForProfile(Long userId)` contained `entity_type IN ('USER', 'USER_SETTINGS')`
hardcoded — domain knowledge in the persistence layer.

**Decision:** Two clean, domain-free methods:
- `findRows(EntityType, Long entityId, Long filterActorId, int limit)` — entity-scoped with window functions.
- `findForActivity(List<EntityRef> subjects, Long actorId, int limit, int offset)` — unified activity query.

Marketplace passes `[EntityRef(USER, id), EntityRef(USER_SETTINGS, id)]` — the starter has zero
knowledge of which entity types constitute a "profile".

**Consequences:** `AuditLogRepository` must not contain any `EntityType` name literals or UNION
queries combining domain-specific filters.

---

## ADR-013: findByActor — correlated subqueries replace window-function scan
**Status:** Accepted

**Context:** Old `findRowsByActor` ran window functions (`ROW_NUMBER`, `LAG`) over ALL rows of
ALL entities for the actor — unbounded as the actor's entity count grows.

**Decision:** `findByActor(Long actorId, int limit)` — CTE fetches actor's top N rows directly
(indexed on `actor_id`, bounded by `LIMIT :limit`), then computes version/prev via correlated
subqueries on only those N rows — 3 indexed point-lookups per row.

**Consequences:** Rejected: single UNION SQL — dynamic SQL assembly in the repository couples
it to the "activity" concept and splits SQL across Java strings.

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

## ADR-015: Generic AuditLogProjection — one repository DTO for history and activity
**Status:** Accepted

**Context:** `AuditLogRepository` previously depended on `AuditHistoryItemDto`, `AuditActivityItemDto`,
and `EntityNameHook` — service-layer concerns bleeding into the persistence layer.

**Decision:** Single `AuditLogProjection` record returned by the repository. Window functions
(`ROW_NUMBER()`, `LAG()`) computed at SQL level. Services transform to their specific DTOs.

**Consequences:**
- `AuditLogRepository` must not import any service-layer DTO or hook.
- `AuditLogProjection` fields map 1:1 to SQL columns — no deserialization in the mapper.
- `ROW_NUMBER()` and `LAG()` stay in SQL — Java-side computation breaks future pagination.

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

## ADR-017: GenericChange i18n key convention — audit.changes.* namespace
**Status:** Accepted

**Context:** `ChangeEntry.GenericChange.labelI18nKey` stores a raw i18n key string in the DB.
Namespace ownership must be clear to avoid collision.

**Decision:** Keys produced by the attachment starter follow `audit.changes.*` namespace
(e.g. `audit.changes.media`). Translations live in `marketplace-app/i18n/messages*.properties`.

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

**Context:** When category-change rows (`CategoryChangeSnapshotDto`) appear between advertisement
rows, SQL `LAG` returns the immediately preceding row regardless of type.
`AdvertisementSnapshotDto.diff(CategoryChangeSnapshotDto)` receives an incompatible type —
the diff method returns only new values (no "old" side), breaking the activity diff UI.

**Decision:** `AuditReadService.getEntityActivity` passes `findRows` result through
`withSameTypePrevSnapshot` before building `AuditActivityItemDto` items. This method walks
oldest-first and substitutes `prevSnapshot` with the nearest previous row of the same Java class
(tracked in a `Map<Class<?>, AuditableSnapshot>`).

**Consequences:** Do NOT add type-filtering correlated subqueries to `AuditLogRepository` —
SQL must stay domain-free. In-memory correction is the right fix.

---

## ADR-022: restorableCount replaces items.size() for restore button visibility
**Status:** Accepted

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

## Deferred backlog

→ [improvement-002-snapshot-schema-versioning](../features/issues/improvement-002-snapshot-schema-versioning.md)

→ [improvement-003-deferred-performance](../features/issues/improvement-003-deferred-performance.md)
