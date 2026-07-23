# improvement-091: AttachmentRepository.loadMediaStats — "main attachment" pick has no `id` tiebreaker on tied `created_at`

**Type:** bug — nondeterministic display, same defect class as improvement-050 item 4 /
improvement-087. Found via pattern-focused code review (2026-07-19).
**Module:** `attachment-spring-boot-starter` (`repository/AttachmentRepository.java`)
**Priority:** medium — cosmetic-level impact (which photo is "main") but trivially fixable and
the ambiguity is real: batch inserts share timestamps
**When:** independent, no blockers — natural companion PR to improvement-087 (same one-line fix
shape)

## Problem

Both variants pick the "main attachment" as earliest-created, non-deleted:

- single: `ORDER BY created_at ASC LIMIT 1`
- bulk: `ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at ASC)`

Neither has an `id` tiebreaker. `AttachmentService.commitTempUploadsQuiet()` persists a whole
gallery via one `saveAll(toSave)` — sibling rows routinely get equal or near-equal `created_at`
values. On a tie:

- the chosen main photo can flip between renders (Postgres gives no ordering guarantee among
  ties), and
- the single and bulk variants can disagree for the same entity — the card list (bulk) and the
  detail view (single) may show different "main" thumbnails.

`attachment-spring-boot-starter/CLAUDE.md` documents the invariant as "the earliest-created,
non-deleted attachment" — under ties that spec is currently unimplementable as stated.

## Suggested fix

Add `, id ASC` to both order clauses (`ORDER BY created_at ASC, id ASC`), making insertion order
the deterministic tiebreak. Update the CLAUDE.md sentence to "earliest-created (id as tiebreak)".

## Related

- `backlog/completed/issues/improvement-087-audit-prev-snapshot-and-last-snapshot-missing-id-tiebreaker.md` —
  same class; fixed together in one PR (Batch A, 2026-07-20).
- `marketplace-app/DECISIONS.md` ADR-035 — why these queries are the sole source of media
  summaries (no denormalized fallback exists to mask the flicker).
