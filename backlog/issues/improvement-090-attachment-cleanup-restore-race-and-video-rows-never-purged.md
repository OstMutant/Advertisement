# improvement-090: AttachmentCleanupService — restore/cleanup TOCTOU can hard-delete a restored attachment; video rows are never purged

**Type:** bug ×2 — data-loss race + unbounded row accumulation, both in the same
`deleteAttachments()` path. Found via pattern-focused code review (2026-07-19).
**Module:** `attachment-spring-boot-starter` (`services/AttachmentCleanupService.java`,
`repository/AttachmentRepository.java`)
**Priority:** medium-high — item 1 is real data loss with a plausible trigger (restore from an old
snapshot); item 2 is harmless-per-day but unbounded; both are one-line SQL fixes in the same PR
**When:** independent, no blockers

## Item 1 — TOCTOU: cleanup can hard-delete an attachment restored between its two steps

`deleteAttachments()` runs:
1. `findUrlsDeletedOlderThan(retentionDays)` — collects urls of rows soft-deleted long ago;
2. `deleteByUrls(urls)` — `DELETE FROM attachment WHERE url = ANY(:urls)` — **no
   `deleted_at IS NOT NULL` guard**;
3. S3 deletes for those urls.

If a user restores an advertisement's gallery from an old snapshot between steps 1 and 2
(`AttachmentService.restoreToUrls()` sets `deleted_at = NULL` on exactly the long-deleted urls —
restoring old snapshots is *the* flow that resurrects urls old enough to be on the cleanup list),
step 2 hard-deletes the now-active row and step 3 deletes its S3 object. The method is
deliberately non-`@Transactional` (see its javadoc / improvement-049 item 4 — correct for the
crash-window reasoning), which widens the window: nothing serializes cleanup against a concurrent
restore.

**Fix:** make the DELETE re-check state:
`DELETE FROM attachment WHERE url = ANY(:urls) AND deleted_at IS NOT NULL`. S3 deletion should
then iterate only the urls the DELETE actually removed (return them via `RETURNING url`), so a
concurrently-restored attachment keeps both its row and its object.

## Item 2 — soft-deleted video rows accumulate forever

`findUrlsDeletedOlderThan()` filters `content_type NOT IN ('video/youtube', 'video/embed')` — the
right guard for the **S3** step (external video urls have no S3 object to delete), but the same
filtered list also feeds the **DB** purge, so soft-deleted video rows are never hard-deleted.
The S3-safety filter accidentally became a DB-retention policy.

**Fix:** purge video rows in the same pass — e.g. a second, videos-only DELETE (
`WHERE deleted_at < ... AND content_type IN ('video/youtube','video/embed') AND deleted_at IS NOT
NULL`) with no S3 step, or restructure `deleteAttachments()` to select `(url, content_type)` and
skip only the S3 call for videos.

## Suggested verification

Integration test in `integration-tests` (attachment package): (a) seed a long-deleted row, restore
it, run cleanup, assert the row and its "S3" url survive; (b) seed a long-deleted video row, run
cleanup, assert the row is gone.

## Related

- `backlog/completed/issues/improvement-049-taxon-attachment-incomplete-rollback-bugs.md` item 4 —
  established the deliberate non-transactional ordering this issue must preserve.
- `backlog/issues/improvement-021-attachment-concurrency-and-batching.md` — the broader
  concurrent-gallery-editing tracker; this race is cleanup-vs-user, not user-vs-user, so it is
  filed separately with its own trigger-free priority.
