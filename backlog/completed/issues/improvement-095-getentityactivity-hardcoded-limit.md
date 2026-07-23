# improvement-095: AuditReadService.getEntityActivity() — hardcoded row limit `100`

**Type:** improvement — magic number, minor. Found via pattern-focused code review (2026-07-19).
**Module:** `audit-spring-boot-starter` (`services/AuditReadService.java`)
**Priority:** low — invisible until an entity accumulates >100 audit rows, then the Activity tab
silently truncates its history with no indicator
**When:** Batch H (audit read-side rewrite, with improvement-019) — see `backlog/BACKLOG.md` "Execution batches" (2026-07-19)

## Problem

`AuditReadService.getEntityActivity()` calls `repository.findRows(entityType, entityId, ..., 100)`
— a bare literal. Two issues:

1. **Magic number:** the only cap on an entity's visible history is an unnamed constant buried in
   a call site; nothing in config or the port contract documents it.
2. **Silent truncation:** an advertisement edited >100 times shows only the latest 100 activity
   rows with no "more…" affordance or log — older history looks like it never existed, even
   though the rows are still in `audit_log`.

## Suggested fix

Minimum: extract `private static final int ENTITY_ACTIVITY_MAX_ROWS = 100;` with a comment stating
the truncation behavior — makes the policy visible and greppable.

Better (if the Activity tab ever needs full history): thread a limit/paging parameter through
`AuditPort.getEntityActivity()` the way `getTimelinePage()` already does, and let the UI decide.
Do not build paging speculatively — YAGNI; the named constant alone closes the discoverability
problem.

## Related

- `backlog/issues/improvement-019-findtimeline-correlated-subqueries.md` — same file, natural
  companion change.
