# Timeline Tab — Spec

## Problem statement

The current Timeline tabs inside UserOverlay and SettingsOverlay show only events where that user was the actor — low value for admins and moderators who need a cross-system view. There is no way to see all recent activity across all entities in one place.

## Goals

- Add a top-level **Timeline** navigation tab in `MainView` (alongside Listings and Users)
- Show a paginated audit feed across all entity types (ADVERTISEMENT, USER, USER_SETTINGS)
- Filters: entity type, action type, date range, actor (ADMIN/MOD only)
- Pagination driven by user settings (`timelinePageSize`)
- After this tab is live — remove inline Timeline tabs from UserOverlay and SettingsOverlay

## Visibility rules

- **USER** — sees only own events (`actorId = currentUserId`, non-overridable)
- **MODERATOR / ADMIN** — full feed; actor filter available (ComboBox<UserDto> resolved to actorId before calling AuditPort)

## Non-goals

- Sort order (always newest first)
- Restore from Timeline (restore stays in entity overlays)

## Deferred items

- Inline Timeline tabs removal from UserOverlay and SettingsOverlay — done only after this tab is live and covered by Playwright tests
