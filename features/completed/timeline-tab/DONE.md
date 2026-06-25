# Timeline Tab — Completed

**Branch:** `feature/audit-ui-updating`  
**Completed:** 2026-06-24

---

## What was built

A dedicated top-level **Timeline** navigation tab (alongside Listings and Users) with full filter, sort, and pagination — replacing the limited inline timeline tabs that existed inside UserOverlay and SettingsOverlay.

## Scope delivered

### platform-commons
- `AuditTimelineFilterDto` — new filter DTO (`actorId`, `entityTypes`, `actionTypes`, `fromDate`, `toDate`)
- `AuditPort` — added `getTimelinePage(filter, page, size)` and `countTimeline(filter)`
- `UserSettingsDto` / `SettingsSnapshotDto` — added `timelinePageSize` (default 20, range 5–100)

### audit-spring-boot-starter
- `AuditLogRepository` — `findTimeline` (filtered + paginated via `SqlFilterBuilder`) + `countTimeline`
- `AuditReadService` / `DefaultAuditPort` — delegating implementations

### marketplace-app
- `TimelineView` (`@UIScope`) — main tab component; USER sees own feed only (forced `actorId`), ADMIN/MOD get full feed with actor picker (Dialog + User grid)
- `TimelineQueryBlock` — filter panel: entity types, action types, date range (multi-select combo + date fields)
- `TimelineQueryConfig` — `FilterProcessor`, `SortProcessor` (fixed DESC), `QueryStatusBar` beans
- `SettingsPaginationBinding` registered with `timelinePageSize`
- `SettingsFormModeHandler` — `timelinePageSizeField` added to settings UI; hidden from USER role
- Actor picker: replaced `ComboBox<UserDto>` with Dialog + User grid (lazy load on open)
- Inline `AuditTimelinePanel` tabs removed from `UserOverlay` and `SettingsOverlay`
- `AuditTimelinePanel` deleted

### Playwright tests
- `07-timeline.spec.js` — filter by entity type, action type, date range; actor filter (ADMIN/MOD only); USER isolation; pagination
- Flakiness fixed: Vaadin multi-select-combo-box overlay must not use `keyboard.type()` — click item directly by text

## Key design decisions
- USER role: `actorId` forced server-side by `AccessEvaluator` — not controllable via UI
- Actor picker is a Dialog+Grid (lazy), not an inline ComboBox — avoids loading all users on tab open
- `TimelineView.setVisible()` overridden to call `refreshFeed()` — tab switching via `setVisible` doesn't trigger `@PostConstruct`
- All 36 Playwright e2e tests pass
