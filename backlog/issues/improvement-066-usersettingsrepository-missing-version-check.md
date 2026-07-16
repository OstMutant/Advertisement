# improvement-066: `UserSettingsRepository.save()` has no optimistic-locking version check — settings from two tabs silently clobber each other

**Type:** improvement — data-integrity bug. Found via direct code review, verified against current
source (2026-07-16).
**Module:** `user-spring-boot-starter` (`repository/UserSettingsRepository.java`).
**Priority:** medium-high — a real, silent data-loss bug (last-write-wins across concurrent tabs
with no warning), consistent with the "optimistic locking everywhere" principle already applied to
every other mutable entity in this codebase.
**When:** independent, no blockers.

## Problem

`UserSettingsRepository.save()`:
```java
jdbcClient.sql("UPDATE user_information SET settings = :settings::jsonb WHERE id = :userId")
```
updates the `settings` JSONB column with **no version check at all**. `ADR-029` established
`version`-column optimistic locking for `advertisement`, `user_information` (name/role, via
`UserProfileCrudRepository`), and `taxon` — but the settings JSONB column goes through this
separate, narrower repository/path that was never brought into that scheme.

Concretely: a user opens Settings in two browser tabs, changes "Ads per page" in the first tab and
saves, then changes "Timeline per page" in the second tab (still holding the settings state from
before the first tab's save) and saves — the second save's `UPDATE` overwrites the **entire** JSONB
blob with its own stale copy, silently discarding the first tab's "Ads per page" change. No error,
no conflict notification — the exact failure mode `ADR-029` was written to prevent everywhere else.

## Suggested fix

Add `AND version = :version` to the `UPDATE` statement (mirroring the pattern already used for
`advertisement`/`taxon`/`user_information` name-role updates), check the affected-row count, and
throw `OptimisticLockingFailureException` on a mismatch (0 rows updated) — the same exception type
`ADR-029` already standardized on so the UI's existing conflict-handling path (dedicated
notification, no auto-reload) picks it up without new UI code. Requires adding a `version` column
tracked alongside `settings` if `user_information.version` (the name/role one) isn't safe to share
across two independently-editable parts of the same row — needs a decision on whether settings
gets its own version column or reuses the row's existing one (reusing it would mean a settings save
and a profile-name save in two tabs would also conflict with each other, which may or may not be
desired).

## Related

- `marketplace-app/DECISIONS.md` ADR-029 — the optimistic-locking scheme this issue extends to a
  path it missed.
- `user-spring-boot-starter/CLAUDE.md` — documents the existing `UserProfileUpdate`/
  `UserProfileCrudRepository` narrow-entity pattern `ADR-029` already established for a different
  part of the same `user_information` row.
