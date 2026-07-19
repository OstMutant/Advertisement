# improvement-106: Timeline fails OPEN for a non-admin when actorIds resolves empty — leaks all actors' activity

**Type:** bug — security (broken access control, fail-open). Found via edge-case review
(2026-07-19).
**Module:** `marketplace-app` (`ui/.../timeline/TimelineView.java`), invariant also belongs in
`query-lib` (`SqlFilterBuilder`/`SqlCondition`)
**Priority:** 🔴 critical — a non-privileged user can see the entire cross-entity audit timeline
of every actor; the unsafe branch is reachable during Vaadin's pre-auth view wiring
**When:** Batch C (session security) — see `backlog/BACKLOG.md` "Execution batches"

## Problem

`TimelineView.refresh()` narrows the timeline to the current actor for non-privileged users:

```java
AuditTimelineFilterDto filter = access.canView()
    ? baseFilter
    : baseFilter.toBuilder()
        .actorIds(currentUserId != null ? Set.of(currentUserId) : Set.of())
        .build();
```

When `currentUserId == null`, the fallback is an **empty set**, and empty means "no restriction",
not "nothing":

1. `AuditLogRepository.FILTER` binds `actorIds` via `anyOf(m, v.getActorIds())`.
2. `SqlCondition.anyOf(m, emptySet)` returns `null` (`CollectionUtils.isEmpty(values)` guard).
3. `SqlFilterBuilder.build()` drops null conditions (`.filter(Objects::nonNull)`).
4. The `al.actor_id` predicate disappears from the `WHERE` clause entirely.
5. The non-admin gets the **unfiltered** cross-entity timeline of all actors.

This is fail-open: the defensive `: Set.of()` branch was written deliberately (the author
anticipated `null`), but its effect is the opposite of safe. And `currentUserId == null` is
reachable — Vaadin instantiates view beans on the first request **before** authentication
resolves (the exact reason this project bans class-level `@PreAuthorize`, see
`marketplace-app/CLAUDE.md`), so an early-wiring refresh with a null actor id opens the whole feed.

## Suggested fix

Fail closed. When a non-privileged user has no resolvable actor id, the query must match nothing:

- In `TimelineView`: if `!access.canView() && currentUserId == null`, render an empty feed / skip
  the query outright (don't build a "see everything" filter). If non-null, keep `Set.of(id)`.
- Harden the invariant one layer down so this class of bug can't recur: an empty in-set bound to a
  security-narrowing filter must not silently become "no filter". Either (a) document that
  `anyOf`/`inSet` return-`null`-on-empty means "no restriction" and forbid using them for
  access-narrowing predicates, or (b) add a `mustMatchSomething` variant that emits a
  never-true predicate (`al.actor_id = ANY('{}'::bigint[])` → always false) on empty input.
  Record the choice in `query-lib/DECISIONS.md`.

## Verification

Add a Playwright timeline test: a plain USER sees only their own timeline rows (assert an
admin-authored row is absent), and add a repository/unit test that an empty-`actorIds`
security-narrowing filter yields zero rows, not all rows.

## Related

- `marketplace-app/CLAUDE.md` — the pre-auth view-wiring hazard that makes the null branch real.
- `backlog/issues/improvement-088-authservice-login-session-fixation.md` — same Batch C
  (pre-launch session/security).
