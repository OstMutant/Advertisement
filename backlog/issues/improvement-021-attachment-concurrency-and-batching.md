# improvement-021: Attachment concurrency races + missing batch inserts

**Type:** improvement — data integrity under concurrency, found by external code audit (round 6)
**Module:** attachment-spring-boot-starter
**Priority:** low — requires concurrent editing of the same gallery; trigger-based
**When:** Deferred — per-item triggers below; item A joins any attachment schema touch

## Items

### A — snapshot version race in `AttachmentSnapshotService.capture()`
Version is computed as `COALESCE((SELECT MAX(version) ...), 0) + 1` — a classic
read-then-insert race: two concurrent captures for the same entity can both read the same
MAX and insert duplicate versions.
**Fix:** `UNIQUE(entity_type, entity_id, version)` constraint (Liquibase changeset in this
starter) + catch `DuplicateKeyException` and retry once. Cheap and definitive.

### B — ~~media summary last-write-wins in `AdvertisementService.onMediaChanged()`~~ — resolved as a byproduct of improvement-042 (2026-07-14)
Was: two concurrent uploads to the same advertisement both read media state and both write the
denormalized summary — the later write may reflect the earlier state. `onMediaChanged()`,
`AdvertisementRepository.updateMedia()`, and the denormalized `media_url`/`media_content_type`/
`media_count` columns it raced over no longer exist — `AdvertisementService.enrichWithMediaSummary()`
now computes the media summary fresh at read time via `AttachmentPort.getMediaSummaries()`, on
every list/detail fetch. There is no stored summary left to race on a write, so this item is
moot, not fixed by a concurrency control — see `marketplace-app/DECISIONS.md` ADR-035.

### C — `saveAll()` issues row-by-row inserts
`AttachmentService.commitTempUploadsQuiet()` uses `CrudRepository.saveAll()`, which in
Spring Data JDBC executes individual INSERTs. For a 10-item gallery commit that is 10 round
trips.
**Fix:** multi-row `INSERT ... VALUES (...), (...)` via `JdbcClient` (repo pattern already
supports it), or enable `reWriteBatchedInserts=true` on the PostgreSQL datasource.

## Trigger

Concurrent gallery editing appearing in practice (post-launch, moderator + owner editing the
same ad) or gallery sizes growing past ~10 items routinely. Item A's unique constraint is
cheap enough to bundle with any attachment-starter schema touch earlier.
