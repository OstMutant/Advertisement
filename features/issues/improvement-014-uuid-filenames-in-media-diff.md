# improvement-014: Media diffs show raw UUID file names instead of human-readable change summary

**Type:** improvement — UX, found during screenshot review of full e2e run
**Module:** marketplace-app (rendering) + platform-commons (if DTO change needed)
**Priority:** low-medium — every media edit produces an unreadable diff row
**When:** Deferred — batch with any diff-rendering touch

## Problem

`ChangeEntry.MediaChange` before/after values are rendered as raw storage file names, e.g. on
the Timeline (`timeline-adminen-edit-ad` screenshot):

```
media: 5229ab40-df73-4174-8513-0e84d4567257.png → 26d0d168-419c-41d8-b620-cbd06efb64c9.png
media: — → 5229ab40-df73-4174-8513-0e84d4567257.png
```

UUID file names carry zero meaning for a user. With max-content galleries (10 items) the diff
row becomes a wall of UUIDs. `AuditChangeFormatter.format()` already treats `MediaChange` as a
dedicated case (`AuditChangeFormatter.java` — `AUDIT_CHANGES_MEDIA` / `AUDIT_CHANGES_MEDIA_CHANGED`
keys), so only the *values* need humanizing, the rendering slot already exists.

## Suggested fix

Options, in order of effort:

1. **Counts summary (cheapest):** render "2 added, 1 removed, 1 replaced" (localized via
   `I18nKey`) computed from the before/after lists instead of printing the file names.
   Keep exact names available in a tooltip if needed for admins.
2. **Ordinal labels:** "photo #1 → photo #4" using gallery positions instead of UUIDs.
3. **Thumbnails (best UX, most work):** render small before/after thumbnails in the diff row —
   the enrich hook (`AuditActivityEnrichHook.getMediaStateForSnapshot`) already resolves media
   state for snapshots, so URLs are obtainable.

Option 1 is a pure formatting change inside the media-change rendering path and needs no DTO
or snapshot format changes.
