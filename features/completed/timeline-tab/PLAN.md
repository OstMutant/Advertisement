# Timeline Tab — Plan

> Reconciled with the actual state of branch `feature/audit-ui-updating`.
> Key facts verified in code:
> - `I18nKey` lives in `org.ost.marketplace.services.i18n.I18nKey` (NOT `common`).
> - `AuditPort` already has `getTimeline(Long actorId, int limit)` + `AuditTimelineItemDto<>`
>   + `AuditTimelineListRenderer`/`AuditTimelineRowRenderer`. The new feature adds a
>   **filtered + paginated** read path; the old single-actor path stays until Step 5.
> - `AuditLogRepository` already has `findByActor(actorId, limit)` (correlated-subquery
>   projection). The new query must emit the same projection columns so `ProjectionMapper`
>   + `AuditReadService.toTimelineItem` are reused unchanged.
> - The inline Timeline tab ALREADY exists in overlays via `AuditTimelinePanel`
>   (`SettingsFormModeHandler.buildTimelineContent`, `UserViewOverlayModeHandler`) and
>   the `TIMELINE_TAB` i18n key is already present. Step 5 removes that inline usage.
> - `QueryBlock<F>` requires BOTH a `FilterProcessor<F>` and a `SortProcessor` (see
>   `UserQueryBlock`). "No sort" still needs a `SortProcessor` bean with a fixed DESC
>   default and no registered sort icons.

## Steps

<!-- Each step = one commit (but DO NOT commit until the user says so). Format:
     ### Step N — <title>
     **Status:** [ ] todo / [~] in progress / [x] done
     **Files:** ...
     **Notes:** <gotchas or deviations found during execution>
-->

### Step 1 — platform-commons: filter DTO + AuditPort methods + settings DTOs

**Status:** [x] done — `AuditTimelineFilterDto` is a **mutable** `@Data @FieldNameConstants @Builder(toBuilder)` bean (QueryBlock/FilterProcessor need getters+setters, not a record). `AuditPort.getTimelinePage`/`countTimeline` replace the old `getTimeline`. `UserSettingsDto`/`SettingsSnapshotDto` carry `timelinePageSize`.

**Files:**
- NEW `platform-commons/src/main/java/org/ost/platform/audit/dto/AuditTimelineFilterDto.java`
  - `record AuditTimelineFilterDto(Long actorId, Set<EntityType> entityTypes, Set<ActionType> actionTypes, Instant fromDate, Instant toDate)` + `static empty()`.
- MODIFY `platform-commons/src/main/java/org/ost/platform/audit/spi/AuditPort.java`
  - ADD `List<AuditTimelineItemDto<AuditableSnapshot>> getTimelinePage(@NonNull AuditTimelineFilterDto filter, int page, int size);`
  - ADD `int countTimeline(@NonNull AuditTimelineFilterDto filter);`
  - KEEP existing `getTimeline(Long actorId, int limit)` for now (removed in Step 5).
- MODIFY `platform-commons/src/main/java/org/ost/platform/user/dto/UserSettingsDto.java`
  - ADD `@Min(5) @Max(100) @Builder.Default int timelinePageSize = 20;`
  - ADD `.timelinePageSize(20)` to `defaultSettings()` (it enumerates every field explicitly).
- MODIFY `platform-commons/src/main/java/org/ost/platform/user/dto/SettingsSnapshotDto.java`
  - ADD `int timelinePageSize` to the record components.
  - UPDATE `from(UserSettingsDto)`, `diff(...)` (new `FieldChange` on `Fields.timelinePageSize`), `allFields()`.

**Notes:** `@Builder.Default` is required so audit-deserialized old JSONB rows without the field default to 20.

---

### Step 2 — audit-starter: filtered/paginated query → service → port

**Status:** [x] done — `AuditLogRepository.findTimeline`/`countTimeline` use a `SqlFilterBuilder<AuditTimelineFilterDto> FILTER` (needs `import static AuditTimelineFilterDto.Fields.*`). `AuditReadService` + `DefaultAuditPort` delegate. Old `findByActor` path removed (no references remain).

**Files:**
- MODIFY `audit-spring-boot-starter/src/main/java/org/ost/audit/repository/AuditLogRepository.java`
  - ADD `private static final SqlFilterBuilder<AuditTimelineFilterDto> FILTER = ...` (actorId, entityTypes, actionTypes, fromDate, toDate — per DESIGN.md).
  - ADD `List<AuditLogProjection> findTimeline(AuditTimelineFilterDto filter, int page, int size)` — same window/correlated projection columns as `findByActor` (so `ProjectionMapper` is reused), WHERE built by `FILTER`, `ORDER BY created_at DESC`, `LIMIT/OFFSET` from page+size.
  - ADD `int countTimeline(AuditTimelineFilterDto filter)` — `SELECT COUNT(*)` with the same `FILTER` WHERE clause.
- MODIFY `audit-spring-boot-starter/src/main/java/org/ost/audit/services/AuditReadService.java`
  - ADD `getTimelinePage(filter, page, size)` — map rows via existing `toTimelineItem`, then run `activityEnrichHooks` `merge` (same as `getTimeline`).
  - ADD `countTimeline(filter)` — delegate to repository.
- MODIFY `audit-spring-boot-starter/src/main/java/org/ost/audit/services/DefaultAuditPort.java`
  - ADD `getTimelinePage` / `countTimeline` — pure delegation to `auditReadService`.

**Notes:** Reuse `AuditLogProjection` + `ProjectionMapper` unchanged. `query-lib` `SqlFilterBuilder`/`inSet`/`equalsTo`/`after`/`before` are already available on the starter classpath.

---

### Step 3 — Settings: timelinePageSize field in UI + DTO

**Status:** [x] done — `SettingsEditDto.timelinePageSize` + `timelinePageSizeField` wired through `activate/save/discard/restore/buildBinder`. `SETTINGS_TIMELINE_PAGE_SIZE_LABEL` + messages added.

**Files:**
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/ui/dto/SettingsEditDto.java`
  - ADD `private Integer timelinePageSize;`
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/settings/SettingsFormModeHandler.java`
  - ADD `IntegerField timelinePageSizeField` (mirror ads/users field setup in `buildBinder`).
  - WIRE into `activate()` (value-change listener + add to `fieldsCard`), `save()`, `discardChanges()`, `loadRestored()`, `handleRestoreFromActivity()`, and the `SettingsEditDto` builders in `activate()`.
- MODIFY `marketplace-app/src/main/java/org/ost/marketplace/services/i18n/I18nKey.java`
  - ADD `SETTINGS_TIMELINE_PAGE_SIZE_LABEL`.
- MODIFY `marketplace-app/src/main/resources/i18n/messages_en.properties` + `messages_uk.properties`
  - ADD the label key.

**Notes:** Path correction vs old plan — `I18nKey` is under `services/i18n/`, not `common/`.

---

### Step 4 — marketplace-app: TimelineView + QueryBlock + QueryConfig + MainView tab

**Status:** [x] done — NEW `ui/views/main/tabs/timeline/TimelineView.java` + `query/{TimelineQueryBlock,TimelineQueryConfig,TimelineFilterMeta}.java` + `ui/mappers/TimelineFilterMapper.java`. `AuditTimelineListRenderer.buildRows` made `public` (called cross-package). `MainView` adds the Timeline tab for everyone (USER sees own feed via forced `actorId`; ADMIN/MOD get an actor `ComboBox<UserDto>`). Reuses existing `queryMultiSelectComboFieldFactory`/`queryDateTimeFieldFactory`/`auditActivityListRendererFactory` beans — no new factory beans needed.

**Files:**
- NEW `.../ui/views/main/tabs/timeline/TimelineView.java`
  - `@SpringComponent @UIScope`; layout `QueryStatusBar` + `TimelineQueryBlock` + actor `ComboBox<UserDto>` (ADMIN/MOD only, via `AccessEvaluator.canView()`) + `PaginationBar` + rows from `AuditTimelineListRenderer`.
  - For `USER`: force `actorId = currentUserId` (no actor combo). For ADMIN/MOD: resolve combo selection → `actorId`.
  - Build `AuditTimelineFilterDto`, call `auditPort.getTimelinePage(...)` + `countTimeline(...)`.
  - Register `SettingsPaginationBinding` with `UserSettingsDto::getTimelinePageSize`; `unregister()` in `@PreDestroy`.
- NEW `.../ui/views/main/tabs/timeline/TimelineQueryBlock.java`
  - `extends QueryBlock<AuditTimelineFilterDto>`; fields: entity-type multi-select, action-type multi-select, date from/to (reuse `QueryMultiSelectComboField`, `QueryDateTimeField`).
- NEW `.../ui/views/main/tabs/timeline/TimelineQueryConfig.java`
  - `FilterProcessor<AuditTimelineFilterDto>` bean, a `SortProcessor` bean (fixed DESC, nothing registered), `QueryStatusBar<AuditTimelineFilterDto>` bean — mirror `UserQueryConfig`.
  - Requires a `FilterMapper<AuditTimelineFilterDto>` + `ValidationService<AuditTimelineFilterDto>` (add `TimelineFilterMeta` / mapper alongside, following `UserFilterMeta`/`UserFilterMapper`).
- MODIFY `.../ui/views/main/MainView.java`
  - INJECT `TimelineView`; add a `Tab` (visible to everyone — USER sees own feed) wired into `tabsToPages` + visibility listener.
- MODIFY `.../services/i18n/I18nKey.java`
  - ADD `MAIN_TAB_TIMELINE` + filter label keys (entity types, action types, actor, date from/to). Reuse existing `TIMELINE_TAB` where it already fits.
- MODIFY `messages_en.properties` + `messages_uk.properties` — new keys.

**Notes:** `QueryBlock` needs both processors. Actor resolution stays in marketplace-app via `UserPort` (no cross-module violation). Reuse `AuditTimelineListRenderer`/`AuditTimelineRowRenderer` for rows.

---

### Step 5 — Playwright tests + remove inline Timeline tabs from overlays

**Status:** [~] removal done, tests + build pending — inline Timeline removed from `SettingsFormModeHandler` + `UserViewOverlayModeHandler`; `AuditTimelinePanel` deleted; old `getTimeline`/`findByActor` gone (grep confirms no code refs). STILL TODO: docker build verification, Playwright specs (`--ux`), `DECISIONS.md` entries.

**Files (removal):**
- MODIFY `SettingsFormModeHandler.java` — drop `timelineContent`/`timelineTab`/`buildTimelineContent` + `AuditTimelinePanel` injection.
- MODIFY `.../tabs/users/overlay/modes/UserViewOverlayModeHandler.java` — drop its inline Timeline tab/usage.
- After removal, delete now-dead code: `AuditTimelinePanel`, `AuditPort.getTimeline(actorId, limit)`, `AuditReadService.getTimeline`, `AuditLogRepository.findByActor` — only if no other references remain (grep first).

**Files (tests):**
- NEW Playwright spec(s) under `playwright/e2e/` covering: Timeline tab visible; USER sees only own events; ADMIN/MOD full feed + actor filter; entity/action/date filters; pagination driven by `timelinePageSize`.

**Notes:** Remove inline usage ONLY after the new tab's tests pass. Per repo rules: run Playwright with `--ux` via `scripts/playwright.sh`. Record a `DECISIONS.md` entry (marketplace-app + platform-commons + audit-starter) and mark the timeline goal done.
