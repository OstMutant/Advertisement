# goal-001: Activity field visibility filtering by role

**Type:** open-goal
**Module:** marketplace-app (audit integration)
**Priority:** low — acceptable edge case, no user complaints
**When:** Deferred — no trigger; revisit on user feedback

## Problem

Settings activity rows show all changed fields (`adsPageSize`, `usersPageSize`, `timelinePageSize`).
For USER role, only `adsPageSize` is configurable — the other two fields appear in their activity log
but have no meaning in their UI context.

## Options

**Option A — filter by current viewer role (recommended)**
Show only fields the viewer's current role can configure.
Downside: a demoted MODERATOR → USER would see a truncated view of their own historical changes.
Accepted as a rare, low-impact edge case.

**Option B — store role at snapshot time**
Add `role` field to `SettingsSnapshotDto` + Liquibase migration.
Accurate but complex — deferred until Option A proves insufficient.

## Implementation trigger

When a user or moderator explicitly reports confusion about extra fields in their activity log.
