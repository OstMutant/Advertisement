# improvement-014: Media diffs list every filename — render a counts summary instead

**Type:** improvement — UX, found during screenshot review of full e2e run; re-scoped 2026-07-19
after improvement-068 resolved the original UUID-readability half of this issue.
**Module:** marketplace-app (rendering) + platform-commons (if DTO change needed)
**Priority:** low — diffs are readable since improvement-068; only max-content galleries still
produce an overly long diff row
**When:** Batch F (UI dedup & polish, PR 2) — see `backlog/BACKLOG.md` "Execution batches" (2026-07-19; formerly Deferred "any diff-rendering touch")

## Problem

Originally this issue covered raw UUID storage names in media diff rows (`5229ab40-....png →
26d0d168-....png`). That half is fixed: improvement-068 (commit `03138f84`) resolves real original
filenames via `AttachmentSnapshotService`'s url→filename map, so diffs now read
`sofa-front.jpg → sofa-front-v2.jpg`.

What remains is a volume problem, not a readability one: media changes render the full
comma-joined before/after filename lists (`AttachmentSnapshotService` joins all current names with
`", "`). With max-content galleries (10 items), a single media edit's diff row on the
Timeline/Activity feeds becomes a long wall of filenames.

`AuditChangeFormatter.format()` already treats `MediaChange` as a dedicated case
(`AUDIT_CHANGES_MEDIA` / `AUDIT_CHANGES_MEDIA_CHANGED` keys), so only the *values* need
summarizing — the rendering slot already exists.

## Suggested fix

Render a localized counts summary — "2 added, 1 removed, 1 replaced" (via `I18nKey`) — computed
from the before/after lists, instead of printing every filename. Keep the exact filenames
available in a tooltip if admins need them.

This is a pure formatting change inside the media-change rendering path; no DTO or snapshot
format changes.

**Rejected/deferred alternatives from the original issue:**
- Ordinal labels ("photo #1 → photo #4") — strictly worse than the real filenames that are now
  available; dropped.
- Before/after thumbnails in the diff row — best UX but most work; belongs with the
  thumbnail-pipeline refactor (improvement-017 step 2), not this formatting fix.
