# feature-008: Timeline Tab — Cross-Entity Audit Feed — ✅ DONE (2026-06-24)

**Type:** feature — new top-level UI tab, condensed from the original `timeline-tab/SPEC.md` +
`DESIGN.md` + `PLAN.md` + `DONE.md` (pre-issue-file convention).
**Module:** `platform-commons` (audit/user DTOs), `audit-spring-boot-starter`, `marketplace-app`.
**Status:** done, all 36 Playwright e2e tests passing at the time.

## Problem

The Timeline tabs inline inside `UserOverlay` and `SettingsOverlay` showed only events where that
user was the actor — no cross-system view for admins/moderators who need to see all recent
activity across every entity type in one place.

## What shipped

A dedicated top-level **Timeline** navigation tab (alongside Listings and Users), with full
filter/sort/pagination, replacing the two inline overlay tabs entirely (`AuditTimelinePanel`
deleted).

- **platform-commons:** new `AuditTimelineFilterDto` (`actorId`, `entityTypes`, `actionTypes`,
  `fromDate`, `toDate`); `AuditPort.getTimelinePage(filter, page, size)` /
  `.countTimeline(filter)`; `timelinePageSize` (default 20, range 5-100) added to
  `UserSettingsDto`/`SettingsSnapshotDto`.
- **audit-spring-boot-starter:** `AuditLogRepository.findTimeline()`/`countTimeline()` via
  `SqlFilterBuilder` (bound on `actor_id`/`entity_type`/`action_type`/`created_at` range);
  `AuditReadService`/`DefaultAuditPort` delegating implementations.
- **marketplace-app:** `TimelineView` (`@SpringComponent @UIScope`) as the main tab component —
  `USER` role sees only its own feed (`actorId` forced server-side by `AccessEvaluator`, not
  UI-controllable); ADMIN/MOD get the full feed plus an actor filter. `TimelineQueryBlock`
  (entity-type/action-type multi-select, date range) + `TimelineQueryConfig`
  (`FilterProcessor`/`SortProcessor` fixed DESC/`QueryStatusBar` beans). `SettingsPaginationBinding`
  registered with the new `timelinePageSize` extractor; `SettingsFormModeHandler` gained a
  `timelinePageSizeField`, hidden from the `USER` role.

## Key design decisions

- **Actor picker: Dialog + User grid, lazy-loaded on open** — not an inline `ComboBox<UserDto>`
  (the original DESIGN.md draft), to avoid loading every user on tab open. This is the direct
  origin of today's `UserPickerField` component (`ui/query/elements/fields/UserPickerField.java`)
  — see [improvement-056](improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md)
  for a pagination-correctness bug found in that component later.
- `TimelineView.setVisible()` overridden to call `refreshFeed()` — tab switching via `setVisible()`
  doesn't trigger `@PostConstruct`, so a plain `init()`-only refresh would show stale data on
  re-entering the tab.
- Sort order is fixed newest-first (non-goal: user-controlled sort). Restore-from-Timeline was
  explicitly out of scope — restore stays in entity overlays.
- Playwright flakiness fix discovered during this feature: Vaadin's multi-select-combo-box overlay
  must not be driven via `keyboard.type()` — click the item directly by text instead.

## Related

- [improvement-056](improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md) — a
  bug found in 2026-07 in the `UserPickerField` component this feature introduced.
