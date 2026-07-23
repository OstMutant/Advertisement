# improvement-093: AttachmentService.captureMediaChanges() silently skips the snapshot when no actor is present

**Type:** improvement — fail-fast violation / inconsistent contract, potential silent audit gap.
Found via pattern-focused code review (2026-07-19).
**Module:** `attachment-spring-boot-starter` (`services/AttachmentService.java`)
**Priority:** low-medium — no observed gap today (all current callers run inside an authenticated
Vaadin request), but the failure mode is silence, which is exactly what makes it dangerous later
**When:** independent, no blockers

## Problem

The same class resolves the current actor two different ways:

```java
// delete(), restoreToUrls(): fail fast — no actor is a bug
Long actorId = currentActorHook.getCurrentActorId().orElseThrow();

// captureMediaChanges() (called from upload/addVideo/commitTempUploads): silent skip
currentActorHook.getCurrentActorId().ifPresent(actorId ->
        attachmentSnapshotService.capture(entityType, entityId, actorId));
```

If a future caller reaches `upload()`/`addVideo()`/`commitTempUploads()` without an authenticated
actor (scheduled job, seeding endpoint — cf. improvement-073, REST integration), the media change
**persists but its snapshot is silently never captured** — the audit trail develops undetectable
holes. The project's own rules ("Design by contract — fail fast, no silent NPE/skip") say this
should throw, like its siblings in the same file do.

## Suggested fix

Make `captureMediaChanges()` use `orElseThrow()` too, matching `delete()`. If a legitimate
actor-less flow ever appears, it should pass an explicit system-actor id (the way
`restoreToUrls(..., actorId)` already accepts one), not silently skip snapshotting.

## Related

- `.claude/rules.md` / root `CLAUDE.md` "Design by contract — no defensive empty checks".
- `backlog/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md` — the most likely
  future source of actor-less calls.
