# improvement-068: Attachment audit/timeline diffs show S3 UUID keys instead of original filenames

**Type:** improvement — UX bug. Found via direct code review, verified against current source
(2026-07-16).
**Module:** `attachment-spring-boot-starter` (`services/AttachmentSnapshotService.java`).
**Priority:** medium — a real, user-visible audit-trail readability bug (not data loss), affects
every media change in every advertisement's activity/timeline.
**When:** independent, no blockers.

## Problem

`AttachmentSnapshotService.filename(url)`:
```java
private static String filename(String url) {
    if (url == null || url.isBlank()) return "";
    String ytId = YoutubeUtil.extractId(url);
    if (ytId != null) return YoutubeUtil.filename(ytId);
    int i = url.lastIndexOf('/');
    return i >= 0 ? url.substring(i + 1) : url;
}
```
extracts the last path segment of the stored URL as the "filename" shown in audit diffs
(`buildDiff()`, `getMediaStateForSnapshot()`). Since `S3StorageService.upload()` names every
uploaded object `UUID.randomUUID() + extension` (never the original filename), every non-YouTube
media change in Timeline/Activity shows something like `550e8400-e29b....png →
6789bcde-f123....png` — meaningless to the user, who uploaded e.g. `sofa-front.jpg` and
`sofa-front-v2.jpg`.

The `attachment` table already has a `filename` column holding the real original name (confirmed:
`AttachmentService.toDto()` reads `a.getFilename()`) — but `attachment_snapshot` only stores the
`attachment_urls` array, and nothing in the diff-building path joins back to `attachment.filename`
by URL.

## Suggested fix

Two options, either resolves the UX bug:
1. **Store original filenames in the snapshot itself** — extend `attachment_snapshot` (or its
   JSON `changes_summary` payload) to carry the original filename alongside each URL at capture
   time, so the diff never needs a later join. Simpler, but a schema/payload shape change.
2. **Resolve original names via `AttachmentRepository` when building the diff** — `filename(url)`
   (or its caller) looks up the matching `attachment` row by URL and uses its `filename` column,
   falling back to the current UUID-extraction behavior only if no matching row exists (e.g. after
   a hard delete). No schema change, but adds a repository lookup to diff-building.

Pick based on whether `attachment` rows for since-deleted attachments still exist at diff-render
time (if they don't, option 2 can't resolve historical names either, and option 1 becomes
necessary).

## Related

- `attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/S3StorageService.java`
  — confirms uploaded objects are always named `UUID + extension`, never the original filename.
- `attachment-spring-boot-starter/src/main/java/org/ost/attachment/repository/AttachmentRepository.java`
  — where the real `filename` column this issue proposes joining against already lives.
