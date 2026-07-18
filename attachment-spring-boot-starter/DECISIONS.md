# Architecture & Technical Decisions — attachment-spring-boot-starter

---

## ADR-001: Attachment domain logic extracted from marketplace-app
**Status:** Accepted

**Context:** Attachment/photo domain logic (entity, repository, services) lived in marketplace-app,
preventing independent deployment or reuse without the advertisement app.

**Decision:** All attachment domain logic lives in `attachment-spring-boot-starter`.
Auto-configured via Spring Boot's autoconfiguration mechanism.

**Consequences:** UI components (`AttachmentGallery`, `CardMediaLightbox`, `AttachmentLightbox`,
`AttachmentThumbnail`, `CardLightboxStrip`, `CardLightboxViewer`) moved to `marketplace-app`
as part of UI monolith consolidation (2026-06-13). The starter owns only domain logic and
JdbcClient persistence — no Vaadin UI.

---

## ADR-002: S3 storage merged into this module; storage-s3-starter deleted
**Status:** Accepted

**Context:** `storage-s3-spring-boot-starter` was a separate module with a mandatory one-way
dependency on attachment. No realistic scenario exists where storage runs without the attachment
module or vice versa.

**Decision:** `S3StorageService` merged into `attachment-spring-boot-starter`, implementing the
`StorageService` interface. `storage-s3-spring-boot-starter` deleted. (Corrected 2026-07-16 —
originally also claimed a `NoOpStorageService` was merged in; no such class exists anywhere in the
repo, only `S3StorageService`/`StorageService` — likely a planned-but-never-written no-op fallback,
not something that actually shipped.)

**Consequences:** Rejected: keeping the separate module — theoretical benefit ("S3 without attachment
logic") has no concrete use case.

---

## ADR-003: Decoupled from advertisement domain — generic over EntityType
**Status:** Accepted

**Context:** The original starter compiled only against an advertisement-shaped world (event types,
field names, S3 path constants). Adding photo galleries to USER or any future entity required
either renaming everything or branching by name.

**Decision:** Every public API takes `(EntityType entityType, Long entityId)` instead of a
hard-coded advertisement id. The `attachment` and `attachment_snapshot` tables grew an `entity_type`
column. Domain Spring events replaced by SPI calls. S3 folder layout:
`entityType.name().toLowerCase() + "/" + entityId` (e.g. `advertisement/42`, `user/17`).

**Consequences:**
- ✅ `EntityRef(EntityType, Long)` record implemented in `platform-commons/core.model`.
- → [improvement-003-deferred-performance](../backlog/issues/improvement-003-deferred-performance.md) (items G, H)
- Rejected: keeping the event-based flow alongside the SPI — the starter speaks SPI and only SPI.

---

## ADR-004: StorageService internalized; attachment.enabled property removed
**Status:** Accepted

**Context:** `StorageService` lived in `platform-commons` but had no cross-module consumer.
`@ConditionalOnAttachmentEnabled` and the `attachment.enabled` property added unnecessary
configuration overhead.

**Decision:** `StorageService` moved to `org.ost.attachment.storage`. The `attachment.enabled`
property and `@ConditionalOnAttachmentEnabled` annotation removed. Jar presence is the only
toggle. UI components degrade via `ObjectProvider.ifAvailable()`.

**Consequences:** S3-specific config stays under `storage.s3.*`. Rejected: conditional flag —
no scenario exists where the jar is present but the subsystem should be disabled.

---

## ADR-005: Starter owns `attachmentObjectMapper` with @Qualifier
**Status:** Accepted

**Context:** The starter previously consumed `userSettingsObjectMapper` — a marketplace-specific
name — which broke contexts with multiple `ObjectMapper` beans.

**Decision:** `AttachmentAutoConfiguration` defines `@Bean("attachmentObjectMapper") ObjectMapper`
with `FAIL_ON_UNKNOWN_PROPERTIES` disabled and `@ConditionalOnMissingBean(name = "attachmentObjectMapper")`.
All injection sites annotated `@Qualifier("attachmentObjectMapper")`.

**Consequences:** Rejected: `@Primary` on either mapper (project rule — always qualify, never `@Primary`).

---

## ADR-006: Actor-centric public API; user-domain naming purged
**Status:** Accepted

**Context:** Methods named `userId` implied a marketplace-specific principal. "Actor" is neutral
and applies to bots, workflows, or service accounts.

**Decision:** Every `userId` parameter renamed to `actorId` across all public methods and contracts.

**Consequences:** Hard cutover — no aliases. Marketplace call sites updated in the same commit.

---

## ADR-007: Symmetry with audit-starter — package rename, i18n enum, port via @Component
**Status:** Accepted

**Context:** Reducing cognitive overhead when reading across starters requires identical conventions.
`AutoConfiguration` should be lean: only beans requiring `@ConditionalOnMissingBean` or
infrastructure setup.

**Decision:** Three structural changes:
1. Package rename: `org.ost.attachment.service` → `org.ost.attachment.services` (plural).
2. i18n: `AttachmentMessages` removed, not renamed (corrected 2026-07-16 — no `AttachmentI18n`
   class exists anywhere in the repo). Attachment i18n keys now live in the single consolidated
   `org.ost.marketplace.services.i18n.I18nKey` enum, per `marketplace-app/CLAUDE.md`'s "single
   consolidated enum" rule — starters carry no i18n infrastructure of their own.
3. Port registration via `@Component` — `DefaultAttachmentPort` discovered by ComponentScan,
   not an explicit `@Bean` in `AttachmentAutoConfiguration`.

**Consequences:** `AttachmentGalleryPort` and `AttachmentGalleryPortImpl` removed (2026-06-15) —
all UI logic moved to marketplace-app; the port was unnecessary indirection.
Do not re-introduce `AttachmentGalleryPort`.

---

## ADR-008: IFrame sandbox attribute on all video embeds
**Status:** Accepted

**Context:** Without `sandbox`, the embedded iframe has unrestricted browser capabilities.

**Decision:** All `IFrame` components for video embedding carry:
`sandbox="allow-scripts allow-same-origin allow-presentation"` (corrected 2026-07-13 — written as
including `allow-forms` originally; verified in `AttachmentLightbox.java:83` and
`CardLightboxViewer.java:44` (line numbers re-verified 2026-07-16, drifted from the original
citation after later refactors — substance unchanged, neither carries `allow-forms`).

**Consequences:** Minimum flags required for YouTube and generic embed playback.

---

## ADR-009: Vaadin IFrame src patching via Page.executeJs
**Status:** Accepted

**Context:** `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client
after initial render — the property diff is not propagated to the DOM.

**Decision:** In `CardLightboxViewer` (corrected 2026-07-13 — written as `CardMediaLightbox`
originally, which today is a dialog/navigation orchestrator with no `IFrame`/`executeJs` code at
all; the actual iframe-patching class is `CardLightboxViewer.java`, line numbers re-verified
2026-07-16 — the paired `setAttribute("src", ...)` / `getPage().executeJs(...)` calls now span
lines 77-98, drifted from the original "62-85" citation after later refactors, substance
unchanged), iframe `src` is updated via `UI.getCurrent().getPage().executeJs(...)` in addition to
`getElement().setAttribute(...)`.

**Consequences:** `setAttribute` is kept in sync so Vaadin's internal state stays consistent.
Rejected: using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic
`page.evaluate` in Playwright.

---

## ADR-010: marketplace-app attachment UI imports starter internals directly
**Status:** Resolved (2026-06-26) — was tracked as open work, now closed

**Context (historical):** Six UI components in marketplace-app directly imported
`attachment-spring-boot-starter` internals (`Attachment` entity, `AttachmentService`,
`AttachmentSnapshotService`, `MediaContentTypeUtil`) instead of going through `AttachmentPort` /
platform-commons DTOs.

**Resolution (verified 2026-07-13):** all six violations fixed —
`org.ost.attachment.services|repository|entities` imports in marketplace-app: zero matches.
`MediaContentTypeUtil` merged into `AttachmentMediaContentType` (platform-commons). See
`backlog/completed/issues/improvement-001-attachment-ui-boundary-violation.md` for the full
resolution record; that issue file's own Status line already says RESOLVED — this ADR's Status
line was the one place still describing it as open work.

---

## ADR-011: `AttachmentService` closes upload `InputStream`s explicitly; `AttachmentCleanupService` gains a DB-cross-checked orphan sweep for entity-folder S3 files

**Status:** Accepted

**Context:** Two independent findings, fixed together since both touch the upload/cleanup path.

1. [improvement-064](../backlog/completed/issues/improvement-064-s3storageservice-inputstream-not-closed.md)
   — `RequestBody.fromInputStream(InputStream, long)` (AWS SDK v2) documents that it does not
   close the given stream, to support retries. Neither `S3StorageService.upload()` nor its two
   `AttachmentService` call sites (`upload()`, `uploadTemp()`) ever closed it.
2. [improvement-069](../backlog/completed/issues/improvement-069-attachment-s3-move-inside-db-transaction-orphans-on-rollback.md)
   — `AttachmentService.commitTempUploadsQuiet()`'s `storageService.move()` physically relocates
   S3 files as a non-transactional side effect, called from inside
   `AdvertisementSaveService.save()`'s DB transaction (see `marketplace-app/DECISIONS.md`
   ADR-047). If a later step in that same transaction fails, the DB rolls back but the already
   -moved S3 files do not — an orphan with no `attachment` row at all, active or soft-deleted.
   Neither of `AttachmentCleanupService`'s two existing cleanup passes could ever catch this
   shape: `deleteStaleTempUploads()` only looks under `temp/`, and `deleteAttachments()` only acts
   on urls a DB row already names. This was an explicitly flagged open question in
   [improvement-049](../backlog/completed/issues/improvement-049-taxon-attachment-incomplete-rollback-bugs.md)'s
   "Required verification" section ("confirm whether the scheduled job would actually catch"),
   never previously answered.

**Decision:**
1. `AttachmentService` gets a private `closeQuietly(InputStream)` helper (catches and logs
   `IOException`, never throws) called right after `storageService.upload(...)` returns in both
   `upload()` and `uploadTemp()`. Deliberately not try-with-resources: wrapping the call in one
   would make a `close()` failure *after* a successful S3 upload throw from the enclosing
   try-with-resources statement, which would then incorrectly look like the upload itself failed
   (and, in `upload()`, would trigger the surrounding catch block's `storageService.delete(url)`
   cleanup on a file that actually made it to S3 successfully). Logging and swallowing the close
   failure keeps "did the upload succeed" and "did we manage to close an unrelated stream"
   independent, as they should be.
2. `AttachmentRepository` gains `findExistingUrls(Collection<String>)` — `SELECT url FROM
   attachment WHERE url = ANY(:urls)` (array bind, matching the `= ANY()` convention already
   established for every bulk lookup in this codebase, not `IN`). `AttachmentCleanupService.cleanup()`
   gains a third pass, `sweepOrphanedEntityFiles()`, run after the two existing ones: for every
   `EntityType`, list S3 objects under `<type>/` older than 1 day (`storageService.listByPrefix`,
   already used for the `temp/` sweep), cross-check against `findExistingUrls`, delete whichever
   have no matching row at all. No new scheduling infrastructure — `cleanup()` already runs on
   `CleanupProperties.cronExpression()` (`AttachmentAutoConfiguration`'s `SchedulingConfigurer`
   bean), this is a third step inside the method that's already wired to fire nightly.

**Consequences:**
- Closes improvement-049's open "Required verification" question definitively: no, the existing
  scheduled job did not catch this orphan shape before this ADR: now it does.
- The 1-day age cutoff means a file orphaned by a rollback isn't swept instantly — it's caught by
  the next nightly run once it's old enough, same latency profile as the existing `temp/` sweep.
  Combined with `marketplace-app/DECISIONS.md` ADR-047's reorder + rollback log, an operator sees
  the orphan in logs immediately even though the sweep itself waits.
- Iterates all four `EntityType` values generically (matches ADR-003's "generic over EntityType"
  principle) even though only `ADVERTISEMENT` has a live caller of `commitTempUploadsQuiet()`
  today — a future entity type adopting the attachment gallery gets orphan-sweep coverage for
  free, no code change needed here.
- New tests: `AttachmentServiceTest` (`upload_closesInputStreamAfterS3UploadSucceeds`,
  `uploadTemp_closesInputStreamAfterS3UploadSucceeds`) and `AttachmentCleanupServiceTest`
  (`cleanup_orphanedEntityFileWithNoDbRow_getsDeleted`,
  `cleanup_entityFileWithMatchingDbRow_isNotDeleted`), same plain-Mockito, no-Spring-context shape
  as their existing siblings in the same files. `AttachmentRepositoryTest` (real Postgres)
  re-verified green after adding `findExistingUrls`. Full `integration-tests` (`AttachmentServiceTest`,
  `AttachmentCleanupServiceTest`, `AttachmentRepositoryTest`) and a full Playwright e2e pass
  (35/35 non-skipped) both green.

---

## ADR-012: `AttachmentSnapshotRepository.extractUrls()` reads the `attachment_urls` array via `Array.getResultSet()` — no cast at all, not even to `Object[]`

**Status:** Accepted

**Context:** [improvement-070](../backlog/completed/issues/improvement-070-attachmentsnapshotrepository-unsafe-array-cast-silent-swallow.md)
— `extractUrls()` did `(String[]) arr.getArray()`, an unchecked cast resting on PostgreSQL JDBC
driver convention rather than a `java.sql.Array` contract guarantee, wrapped in `catch (Exception
_) { return List.of(); }` — any failure, including a `ClassCastException` from the cast itself,
silently became an empty result with no log trace.

**Decision:** Standing project preference against casts (not just unsafe ones). The initially
proposed fix — cast to `Object[]` instead of `String[]`, then `Stream.of(raw).map(String::valueOf)`
— was rejected on exactly that basis even though it's the safe/conventional version of this fix
(array covariance guarantees any reference-type array is assignable to `Object[]`). Replaced
instead with `java.sql.Array.getResultSet()` — part of the `java.sql.Array` interface itself, not
driver-specific behavior: it returns a two-column `ResultSet` (array index, element value) that's
read via `arrRs.getString(2)`, so the driver does the type conversion, not our code. Zero casts of
any kind. `catch (Exception _)` narrowed to `catch (SQLException e)` (the only checked exception
`getArray()`/`getResultSet()`/`getString()` can throw) with a `log.warn(...)`.

**Consequences:**
- No other place in this codebase reads a Postgres array column back via `getArray()` (writing
  arrays via `= ANY(:array)` bind params is common; this was the only read side), so there was no
  existing convention to stay consistent with — free to pick the cast-free option.
- New `AttachmentSnapshotRepositoryTest` (real Postgres, first test coverage this repository has
  ever had) round-trips multiple urls through `insert()` → `getPrevUrls()`/`getUrlsById()`,
  proving the new extraction against a real `text[]` column rather than mocking
  `java.sql.Array`/`ResultSet` — 3/3 green, plus the 4 other attachment-domain integration test
  classes re-verified green (21/21 total) to rule out any regression in adjacent tests that
  exercise the same starter.

---

## ADR-013: `AttachmentSnapshotService` resolves real filenames from `attachment.filename` by url, instead of deriving a display name from the S3 object key

**Status:** Accepted

**Context:** [improvement-068](../backlog/completed/issues/improvement-068-attachment-audit-shows-uuid-not-original-filename.md)
— `AttachmentSnapshotService.filename(url)` took the last path segment of the stored URL as the
displayed media name in Activity/Timeline diffs. Since `S3StorageService.upload()` always names
uploaded objects `UUID + extension` (never the original filename), every non-video media change
showed something like `550e8400-e29b....png → 6789bcde-f123....png`, meaningless to the user who
actually uploaded `sofa-front.jpg`. A dedicated research pass (prompted by "this same fix should
apply to Activity and views too") confirmed the bug is fully isolated to this one method — gallery
/lightbox/card UI components already display the real `attachment.filename` column end-to-end via
`AttachmentItemDto`, and Activity/Timeline rendering only ever displays whatever string this
method already produced at snapshot-capture time, re-deriving nothing themselves. Fixing this one
method was therefore sufficient, no changes needed elsewhere.

**Decision:** Picked option 2 from the issue (repository lookup) over option 1 (extend the
snapshot schema/payload) — no schema change, and the `attachment` row reliably still exists at
every point `filename()` is called (capture always runs right after the row's own
insert/soft-delete in the same or an immediately following transaction). New private
`resolveFilenames(EntityType, Long, List<String> urls)` bulk-resolves via
`AttachmentRepository.findByEntityAndUrls()` into a `Map<url, filename>` — **keyed by url, not by
filename**, so two attachments legitimately sharing the same original filename (e.g. uploaded from
different devices) can never collide in the lookup; each url still resolves to its own correct
name independently, even if both display the same string. `filename(url, urlToFilename)` checks
this map after the existing YouTube-id branch, before falling back to the old last-path-segment
behavior (only reachable now if the attachment row is gone entirely, e.g. purged past retention).
`buildDiff()` (capture-time, called from `captureAndGetId()`) and `getMediaStateForSnapshot()`
(render-time) both resolve once per call via one bulk lookup covering every url they need, not one
lookup per url.

**Consequences:**
- New snapshots capture the real filename permanently into `changes_summary` at write time — no
  later re-resolution needed, and no dependency on the `attachment` row still existing once
  captured. Snapshots captured before this fix keep showing their already-baked-in UUID-derived
  names (no data migration; out of scope, same precedent as every other in-place fix in this
  codebase).
- If an attachment is later hard-deleted (past the 90-day retention purge in
  `AttachmentCleanupService`), a *new* snapshot capture for the same entity would fall back to the
  old UUID-derived name for that url, since no row remains to resolve against — an acknowledged,
  narrow edge case matching the issue's own framing, not fixed here.
- New `AttachmentSnapshotServiceTest` (`integration-tests`, plain Mockito, no Spring context) — 4
  tests: real filename resolved on first capture, fallback to url segment when no attachment row
  matches, `getMediaStateForSnapshot()` resolves correctly, and two attachments sharing an
  identical original filename resolve independently without collision. Full attachment-domain
  integration sweep (25/25) and a full Playwright e2e pass (35/35 non-skipped) both green.
- **Bug found during improvement-075's Playwright pass (2026-07-18):** `resolveFilenames()`'s
  `Collectors.toMap(Attachment::getUrl, Attachment::getFilename)` had no merge function, so it
  threw `IllegalStateException: Duplicate key <url>` whenever `findByEntityAndUrls()` returned two
  rows sharing the same url — not the "same filename, different url" case this ADR already
  guarded against, but the inverse: **the same url appearing on two rows**, e.g. a YouTube
  attachment soft-deleted and then re-added with the same video url. `findByEntityAndUrls()` does
  not filter `deleted_at`, so both rows come back. Only ever exercised by a heavier scenario (spec
  04's 10-item gallery replace) than this ADR's own original test coverage exercised. Fixed with a
  `(a, b) -> a` merge function — either name is acceptable since both rows resolve to the same
  underlying url/attachment identity for display purposes.
