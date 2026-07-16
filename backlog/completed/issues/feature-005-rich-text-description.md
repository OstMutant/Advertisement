# feature-005: Rich Text Description for Advertisements — ✅ DONE

**Type:** feature — replace plain-text description with a rich-text editor, condensed from the
original `rich-text-description/SPEC.md` (pre-issue-file convention).
**Module:** `marketplace-app` (create/edit form, view overlay, card), `advertisement-spring-boot-starter`
(sanitization).
**Status:** done — `QuillEditor` (not Vaadin's stock `RichTextEditor`, see note below), OWASP
sanitization, HTML rendering all shipped. The two bugs this spec's own "Known Problems" section
flagged were fixed later, as part of an unrelated bigger redesign (see below).

## Scope (as delivered)

- Create/edit form: plain `TextArea` → `QuillEditor` (`org.ost.marketplace.ui.views.components.fields.QuillEditor`,
  a Quill v2 web-component wrapper — the original spec proposed Vaadin's stock `RichTextEditor`;
  the actual implementation uses a custom Quill wrapper instead, giving direct access to
  `quill.getText()` for the character counter added later by
  [improvement-006](improvement-006-quill-description-counter-and-db-limit.md)).
- View overlay renders the stored HTML directly; advertisement card/grid strips tags to a
  plain-text excerpt (`Jsoup.parse(html).text()`) — no HTML ever reaches a grid cell.
- Storage: raw HTML in the existing `advertisement.description` column, no schema migration
  needed at the time (later widened to `VARCHAR(20000)` by improvement-006, see
  `marketplace-app/DECISIONS.md` ADR-031).
- XSS sanitization: OWASP Java HTML Sanitizer, applied at save time in `AdvertisementService`
  (service layer, not the UI layer — resolving this spec's own open question that way, "service
  layer — guarantees clean data regardless of caller").

## Known problems flagged here, fixed later by a different feature

Both bugs below were discovered while building this feature but were **not** fixed as part of it
— both were architectural snapshot/versioning issues, not description-editor issues, and were
resolved later by [feature-002](feature-002-advertisement-snapshot-redesign.md)
(advertisement-snapshot-redesign):

1. **`isCurrentState` badge missing after restore** — `AttachmentSnapshotRepository` compared
   `created_at` timestamps to correlate snapshots with audit versions; inside a single Postgres
   transaction `NOW()` returns the same value for every INSERT, so the boundary condition never
   matched. Fixed by replacing timestamp correlation with direct `attachmentSnapshotId`
   soft-references in `AdvertisementSnapshotDto`.
2. **Cross-module SQL coupling** — `AttachmentSnapshotRepository` (attachment-spring-boot-starter)
   queried the `audit_log` table (owned by audit-spring-boot-starter) directly, breaking module
   independence. Fixed by replacing those queries with direct `attachment_snapshot.id`-keyed
   lookups that never reference `audit_log`.

## Related

- [feature-002](feature-002-advertisement-snapshot-redesign.md) — fixed both "Known problems"
  above as part of its own, larger scope.
- [improvement-006](improvement-006-quill-description-counter-and-db-limit.md) — added the
  character counter and widened the DB column, both building on this feature's `QuillEditor`.
