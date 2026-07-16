# improvement-042: `advertisement` table stores denormalized media columns — real schema-level coupling to the attachment domain

**Type:** improvement — architectural, cross-module coupling. Found by direct user pushback on an
earlier wrong call in this same review pass (see Correction below) — initially dismissed as "not
a problem" because the *sync mechanism* (a hook) is clean; that was answering the wrong question.
The mechanism being clean does not mean the coupling doesn't exist.
**Module:** `advertisement-spring-boot-starter`, `platform-commons` (`AttachmentPort`)
**Priority:** medium — not a runtime-fragile cross-starter SQL reference like improvement-041 (no
hardcoded foreign table/column names anywhere), but a genuine data-model coupling: three columns
on `advertisement`'s own table describe concepts (`media_url`, `media_content_type`, `media_count`)
that belong to the attachment domain's vocabulary, not advertisement's.
**When:** independent, no blockers — same well-precedented fix shape as improvement-007/041, just
for `AttachmentPort`

## Correction (read before anything else)

An earlier pass on this same review incorrectly concluded these columns were "fine as-is" because
they're synced via `AttachmentMediaChangeHook` → `MediaChangeHookImpl` (a clean, pure-delegation
hook implementation, correctly following the `*HookImpl` rule) rather than via a raw SQL `JOIN`
like the `user_information` case (improvement-041). That defense was answering "is the sync
mechanism clean?" (yes) instead of "does the coupling exist?" (also yes, independently of the
mechanism). A clean sync mechanism means the coupling is *managed*, not that it's *absent*. Three
columns whose names are literally `media_url`/`media_content_type`/`media_count` on
`advertisement`'s own table are attachment-domain vocabulary living in a different starter's
schema — exactly the coupling `backlog/entity-extensions/SPEC.md`'s own Problem section already
named ("`advertisement` table has `media_url`/`media_content_type` — data that belongs to the
attachment module but lives in the advertisement table").

## Problem

`advertisement` table (`01-advertisement-schema.xml`) has `media_url VARCHAR(1024)`,
`media_content_type VARCHAR(127)`, `media_count INT` — a denormalized cache of attachment data,
written by `AdvertisementRepository.updateMedia(Long entityId, AttachmentMediaSummaryDto summary)`,
triggered by `MediaChangeHookImpl.onMediaChanged()` whenever the attachment starter fires
`AttachmentMediaChangeHook`. `AttachmentPort` currently only exposes a single-entity
`getMediaSummary(EntityRef entity)` — no bulk variant — which is precisely why this denormalized
cache exists on the row in the first place: without it, rendering a list of 50 advertisement cards
would require 50 individual `AttachmentPort` calls (or a join) instead of one query.

## Why JSONB (`entity-extensions`) does not actually fix this

Wrapping the same three values into a generic `media JSONB` column instead of three typed columns
does not remove the coupling — it is the same attachment-domain data, on the same
`advertisement` row, just re-encoded. It also has a real cost with no offsetting benefit: checked
whether these columns are used for sorting (`AdvertisementSortMeta.java` — only `TITLE`,
`CREATED_AT`, `UPDATED_AT` are exposed as sortable; the `media_url`/`media_content_type`/
`media_count` entries in `AdvertisementRepository`'s `ORDER BY` alias map are dead capability,
never reachable from the UI) — so there is no sort feature to preserve either way, but a JSONB
column would still lose type safety for zero gain. Genericizing the representation is not the same
as removing the coupling; the fix has to remove the *data* from `advertisement`'s row entirely.

## Suggested fix — same pattern as improvement-007 (Taxon) and improvement-041 (User), now for Attachment

1. **Add a bulk lookup to `AttachmentPort`**: `getMediaSummaries(@NonNull EntityType entityType,
   @NonNull Set<Long> entityIds) -> Map<Long, AttachmentMediaSummaryDto>`, mirroring
   `TaxonPort.getForEntities()`/`findByIds()`. Implementation in `attachment-spring-boot-starter`
   stays pure delegation to a service-level bulk query (one SQL `IN` query against `attachment`,
   not N single-entity calls).
2. **Add enrichment to `AdvertisementService`**: a `ComponentFactory<AttachmentPort>
   attachmentPortFactory` field (mirrors the existing `taxonPortFactory` and the
   `userPortFactory` proposed in improvement-041) and an `enrichWithMediaSummary(ads)` step,
   exactly parallel to `enrichWithCategories()` — merges `mediaUrl`/`mediaContentType`/`mediaCount`
   into `AdvertisementInfoDto` at read time, from the bulk `AttachmentPort` call, not from a stored
   column.
3. **Remove the three columns from `advertisement`** (`01-advertisement-schema.xml`, direct edit —
   DB not yet in production, requires `deploy.sh --reset`), remove
   `AdvertisementRepository.updateMedia()`, and remove the now-pointless write path entirely:
   `MediaChangeHookImpl.onMediaChanged()` → `AdvertisementService.onMediaChanged()` → nothing left
   to update, since nothing is cached on the row anymore. **This simplifies the write side, not
   just the schema** — the whole hook-triggered update flow can be deleted once there is no
   denormalized column left to keep in sync.
4. Remove the dead `media_url`/`media_content_type`/`media_count` entries from the `ORDER BY` alias
   map in `AdvertisementRepository.findByFilter()` (confirmed unreachable from the UI, see above).

## Tradeoff introduced (accept it explicitly, don't hide it)

This adds one bulk `AttachmentPort` query per advertisement list render, the same cost class the
Taxon and (proposed) User enrichments already accept — not a new kind of cost, but worth recording
in `marketplace-app/DECISIONS.md` alongside the fix as a deliberate choice: real decoupling in
exchange for one more bulk query per page load, same tradeoff already made twice elsewhere in this
codebase.

## Related

- `backlog/issues/improvement-041-advertisement-user-sql-join-and-column-naming.md` — the sibling
  fix for the User-JOIN case; both land in `AdvertisementService` as the same shape of enrichment
  step, consider doing them in the same PR since both touch `enrichWithCategories()`'s neighborhood.
- `backlog/completed/issues/improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md` — the
  original bulk-lookup pattern this issue is the third application of.
- `backlog/entity-extensions/SPEC.md` (deleted 2026-07-13, superseded by this issue and
  improvement-041) — correctly identified this exact coupling as its motivating problem; this
  issue's fix achieves the same decoupling goal without the JSONB genericization's cost
  (type-safety loss) or its speculative framing (future hypothetical starters) — it solves the
  concrete problem that already existed today, in the concrete domain it exists in, without a
  general-purpose mechanism nothing else currently needs. Once both concrete motivating cases had
  a targeted fix, the generic SPEC no longer had a reason to exist as a separate document.
- `backlog/completed/issues/improvement-001-attachment-ui-boundary-violation.md` — a different,
  already-resolved coupling issue (UI-layer illegal imports, not schema-level denormalization); not
  overlapping with this one.
