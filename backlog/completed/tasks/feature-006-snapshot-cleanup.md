# feature-006: Snapshot DTO Cleanup — Remove Redundant `visible`/`restorable` Fields — ✅ DONE

**Type:** cleanup — dead-code removal, condensed from the original `snapshot-cleanup/SPEC.md`
(pre-issue-file convention).
**Module:** `platform-commons` (`AuditableSnapshot`), `marketplace-app` (`AuditActivityPanel`,
`AuditActivityRowRenderer`).
**Status:** done, in two steps.

## Problem

`AuditableSnapshot`'s two default methods (`isVisible()`, `isRestorable()`) were serialized into
every snapshot JSON (`{"visible": true, "restorable": true, ...}`) despite carrying no real
information for almost every implementation — `isRestorable()` was fully dead code (nothing ever
read it as the deciding factor; `AuditActivityRowRenderer`'s real guard was `actionType`), and
`isVisible()`'s only meaningful override (`CategoryChangeSnapshotDto` → `false`) existed solely to
filter that one DTO out of the timeline.

## Resolution

**Step 1:** `isRestorable()` removed entirely from `AuditableSnapshot` and its one override
(`CategoryChangeSnapshotDto`); `AuditActivityPanel`'s `restorableCount` logic and
`AuditActivityRowRenderer`'s `isRestorable()` check in `canShowAction` both removed.
`isVisible()` kept but annotated `@JsonIgnore` (Java-only, no longer serialized).

**Step 2 (after [feature-002](feature-002-advertisement-snapshot-redesign.md) deleted
`CategoryChangeSnapshotDto` — the only class that ever overrode `isVisible()` to `false`):**
`isVisible()` itself became dead code too, since every remaining `AuditableSnapshot`
implementation used the default `true`, making `AuditActivityPanel`'s
`.filter(i -> i.snapshotData().isVisible())` a no-op. Removed entirely from the interface, along
with that filter call.

No schema migration needed — existing `audit_log` rows with `visible`/`restorable` fields in their
stored JSON are silently ignored by Jackson on read.

## Related

- [feature-002](feature-002-advertisement-snapshot-redesign.md) — deleted `CategoryChangeSnapshotDto`,
  which triggered this cleanup's second step.
