# improvement-104: UserService.expandActivityFields() is generic snapshot logic living in the user domain

**Type:** improvement — simplification (feature envy / wrong home). Found via simplification
review (2026-07-19).
**Module:** `user-spring-boot-starter` (`services/UserService.java`), `platform-commons`
(audit DTOs), callers in `marketplace-app`
**Priority:** low — no bug; a misplaced generic method invites domain services to accumulate
rendering logic
**When:** Batch N (audit-rendering simplification) — see `backlog/BACKLOG.md` "Execution
batches"; after Batch F (084/101 reshape the same DTO layer)

## Problem

```java
// UserService — a user-domain service
public List<ChangeEntry> expandActivityFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
    return item.snapshotData() != null
            ? item.snapshotData().expandWithChanges(item.changes())
            : item.changes();
}
```

Nothing here is user-specific — it's a generic "full state + highlighted changes" transformation
over the platform-commons audit DTOs, parked in `UserService` (presumably because a user view
needed it first). Consequences: any non-user caller must reach through `UserPort` for a
rendering concern, and the user domain owns presentation logic it has no business owning.

## Suggested fix

Move the null-safe expansion to where its inputs live — a default/static method on
`AuditTimelineItemDto` itself (e.g. `item.expandedChanges()`), since the DTO already carries both
`snapshotData()` and `changes()` and `expandWithChanges()` is already on the snapshot contract.
Delete the `UserService` method and (if present) its `UserPort` exposure; update callers.
Check for a sibling copy handling `AuditActivityItemDto` the same way — if one exists, give both
DTOs the same method in the same PR.

## Related

- `platform-commons/CLAUDE.md` — "*.dto — data carriers with no behavior" needs a conscious
  exception note here (pure derivation over own fields, no dependencies — same spirit as
  `diff()` already living on snapshot DTOs), or place the helper in `marketplace-app`'s audit
  rendering layer instead if that rule should stay absolute. Decide in the PR, record the choice.
