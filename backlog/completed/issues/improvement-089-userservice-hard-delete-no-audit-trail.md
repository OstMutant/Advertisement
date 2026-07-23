# improvement-089: UserService.delete() — hard delete with no audit capture, unlike every other lifecycle mutation

**Type:** bug — audit-trail gap + consistency violation. Found via pattern-focused code review
(2026-07-19).
**Module:** `user-spring-boot-starter` (`services/UserService.java`), caller in `marketplace-app`
(`ui/.../users/UserView.java`)
**Priority:** medium-high — a user's entire existence can vanish without a Timeline trace; needs a
small design decision (soft-delete vs. capture-then-delete) before coding
**When:** independent, no blockers

## Problem

`UserService.delete(userId)` is `repository.deleteById(userId)` — a **hard** `DELETE`, called from
`UserView`'s delete action. Two inconsistencies with the rest of the system:

1. **No audit capture.** Every other lifecycle mutation writes to `audit_log`: `register()` →
   `captureCreation` (×2, user + settings), `save()`/`applyUserRestore()` → `captureUpdate`,
   `UserSettingsService.save()` → `captureUpdate`, advertisement/taxon deletes →
   `captureDeletion`. User deletion writes nothing — the Timeline never shows the user existed or
   who deleted them.
2. **Hard vs. soft.** `Advertisement`, `Taxon`, and `Attachment` all soft-delete
   (`deleted_at`/`deleted_by`); `user_information` is the only domain hard-deleted from the UI.

Mitigating factor (why this isn't data-loss today): `advertisement.created_by` has an
`ON DELETE RESTRICT` FK, so deleting a user who ever created an advertisement fails at the DB
level anyway — but a user with no ads deletes silently and trace-free, and existing `audit_log`
rows keep referencing an `actor_id` that no longer resolves to a name.

## Suggested fix

Decide first, then implement:

- **Option A — align with the platform (recommended):** soft-delete `user_information`
  (`deleted_at`/`deleted_by` columns via a new Liquibase changeset) + `captureDeletion` with a
  `UserSnapshotDto`, mirroring the advertisement/taxon shape. Login and actor-name resolution
  must then filter/annotate deleted users.
- **Option B — minimal:** keep the hard delete but wrap it with
  `auditPortFactory.ifAvailable(p -> p.captureDeletion(userId, toSnapshot(before), actingUserId))`
  (requires `delete()` to accept the acting user, which `UserView` already knows).

Either way `delete()` should take `actingUserId` — the current signature can't even attribute the
action.

## Related

- `user-spring-boot-starter/CLAUDE.md` — user domain constraints (no soft-delete today).
- `docs/architecture/07-risk-report.md` — documents the RESTRICT FK that partially masks this.
