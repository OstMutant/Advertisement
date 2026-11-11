# improvement-021: Attachment concurrency races + missing batch inserts

**Type:** improvement — data integrity under concurrency, found by external code audit (round 6)
**Module:** attachment-spring-boot-starter
**Priority:** ⚪ low — requires concurrent editing of the same gallery; trigger-based
**When:** Deferred — per-item triggers below; item A joins any attachment schema touch

## Items

### A — snapshot version race when a snapshot is inserted (`AttachmentSnapshotRepository.insert`)
Version is computed as `COALESCE((SELECT MAX(version) ...), 0) + 1` — a classic
read-then-insert race: two concurrent captures for the same entity can both read the same
MAX and insert duplicate versions.
**Fix:** `UNIQUE(entity_type, entity_id, version)` constraint (Liquibase changeset in this
starter) + catch `DuplicateKeyException` and retry once. Cheap and definitive.

### C — `saveAll()` issues row-by-row inserts
`AttachmentService.commitTempUploadsQuiet()` uses `CrudRepository.saveAll()`, which in
Spring Data JDBC executes individual INSERTs. For a 10-item gallery commit that is 10 round
trips.
**Fix:** multi-row `INSERT ... VALUES (...), (...)` via `JdbcClient` (repo pattern already
supports it), or enable `reWriteBatchedInserts=true` on the PostgreSQL datasource.

### D — no DB-level uniqueness guard on `attachment.url` (added 2026-07-19, external-review follow-up)
Every url is unique by construction (`S3StorageService.upload()` names objects
`UUID.randomUUID() + extension`), but nothing in the schema enforces it — the invariant lives
only in application code, while several paths rely on it (`findByEntityAndUrls`, `deleteByUrls`,
the orphan sweep — all keyed by url). A plain `UNIQUE(url)` index is a cheap belt-and-suspenders
guard that turns any future url-generation bug into a loud `DuplicateKeyException` instead of
silent duplicate rows.
**Scope note:** the external suggestion was `UNIQUE(entity_type, entity_id, url)` — assessed and
narrowed 2026-07-19. A composite index does not protect against the real concurrency threat: a
double-commit of the same gallery produces *different* UUIDs per temp upload, so no uniqueness
constraint can catch it — that needs commit idempotency (item A's territory). Plain `UNIQUE(url)`
is what actually hardens an invariant the code depends on. Bundle with item A's changeset.

## Checked against the code (2026-11-11)

All four items still hold; none was done and none was started.

- A: the version is still `COALESCE((SELECT MAX(version) ...), 0) + 1` inside one `INSERT`, now in `AttachmentSnapshotRepository.insert` (the item named the service, which no longer holds the SQL). The schema has only a non-unique index on `(entity_type, entity_id)`; the remarks of the `version` column say it is not a real optimistic-lock check.
- C: `AttachmentService.commitTempUploadsQuiet` still calls `attachmentRepository.saveAll`, which is `CrudRepository.saveAll`: one `INSERT` per element.
- D: `attachment.url` is `NOT NULL` and has no unique constraint; the only indexes are `idx_attachment_entity` and `idx_attachment_deleted_at`.
- E: `TaxonAssignmentService.replaceAssignments` still loops `unassign` over the removed taxons and `assign` over the added ones inside one `@Transactional` method, so a save costs the number of added plus removed taxons in statements; no upper limit on the categories of one entity was found in the code.
- The schema file was touched since this item was written (the unused GIN index on `changes_summary` was dropped, remarks were corrected) without taking A or D along, so "bundle with any schema touch" does not happen by itself.
- Before the unique constraints of A and D: check the development and any real database for duplicate urls (`SELECT url, COUNT(*) FROM attachment GROUP BY url HAVING COUNT(*) > 1`) and duplicate versions, or the changeset stops.

Decision 2026-11-11: not done now; A and D stay a pair for the next schema touch, C waits for galleries above ten items, E goes to the taxon module as its own task if it is picked up.

## Trigger

Concurrent gallery editing appearing in practice (post-launch, moderator + owner editing the
same ad) or gallery sizes growing past ~10 items routinely. Items A + D share one Liquibase
changeset — cheap enough to bundle with any attachment-starter schema touch earlier.

Related size note (2026-07-19): `attachment_snapshot.changes_summary` JSONB is tiny today because
galleries are capped at `AttachmentUploadButton.MAX_FILES = 10`. If that cap is ever raised
substantially, re-check JSONB payload size on the Timeline/Activity SELECT path as part of the
same change.

### E — `replaceAssignments()` issues per-row assignment writes (added 2026-07-19, edge-case review)
Not attachment-specific, but the same N+1-write shape as item C, found in the same save path:
`TaxonAssignmentService.replaceAssignments()` (taxon-spring-boot-starter) loops
`for (toRemove) unassign()` + `for (toAdd) assign()`, each a single-row DELETE/INSERT. An
advertisement save with 10 categories issues up to 20 statements. Bounded by category count so
not catastrophic, but it sits in the hot advertisement-save path.
**Fix:** batch — one `DELETE FROM taxon_assignment WHERE entity_type=:t AND entity_id=:e AND
taxon_id = ANY(:removeIds)` and one multi-row `INSERT ... SELECT unnest(:addIds)` (repository
already uses array binds elsewhere, improvement-054). Cross-module note: this fix lives in
`taxon-spring-boot-starter`, listed here only because it's the same batching decision as item C —
do them together if both are picked up, or split into a taxon issue if the attachment items are
deferred and this one isn't.
