# improvement-049: Taxon update() silently drops deletedBy; three AttachmentService/CleanupService paths leave orphaned state on partial failure

**Type:** bug fix. Found via targeted code review of `taxon-spring-boot-starter` and
`attachment-spring-boot-starter` — modules not previously audited in this session's earlier passes
(improvement-045/047/048 covered `marketplace-app`/`user-spring-boot-starter`/`advertisement`/
partially `taxon`). All four findings verified directly against current source, not inferred.
**Module:** `taxon-spring-boot-starter` (`TaxonService.update()`),
`attachment-spring-boot-starter` (`AttachmentService.upload()`, `.commitTempUploadsQuiet()`,
`AttachmentCleanupService.deleteAttachments()`).
**Priority:** medium — none are live-traffic incidents today (see reachability notes per item), but
all four are real, silent data-corruption/orphan-state risks, not test-coverage gaps; items 2/3/4
(Attachment) are the more concrete ones since their trigger paths are live in production.
**When:** independent, no blockers.

## Problem

### 1. `TaxonService.update()` forwards `deletedAt` but not `deletedBy` — DATA-LOSS
`/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/TaxonService.java:66-76`

```java
Taxon updated = Taxon.builder()
        .id(existing.getId())
        .type(existing.getType())
        .code(existing.getCode())
        .deletedAt(existing.getDeletedAt())   // forwarded
        .createdAt(existing.getCreatedAt())
        .createdBy(existing.getCreatedBy())
        .updatedAt(Instant.now())
        .updatedBy(actorId)
        .version(version)
        .build();                              // .deletedBy(existing.getDeletedBy()) — missing
```

`Taxon.deletedBy` is never forwarded, so it defaults to `null` in the builder. Since
`TaxonRepository.save()` goes through Spring Data JDBC's `CrudRepository.save()` (a full-row
`UPDATE`, not a partial patch), this silently **overwrites `deleted_by` to `NULL`** on every
`update()` call. For an active (never-deleted) taxon this is invisible (`null` → `null`). If
`update()` is ever called on an already soft-deleted taxon (`deletedAt` non-null), the row keeps
its `deleted_at` but permanently loses the record of *who* deleted it.

**Current reachability:** checked `TaxonManagementView.buildRowActions()` — the edit action is
only rendered `if (!isDeleted)`, so the UI doesn't expose a path to call `update()` on a deleted
taxon today. Same shape as the already-fixed `TaxonRepository.findByIds()` gap in improvement-045
(items 4/5): a real service-layer defect currently masked only by UI discipline, not a code-level
guarantee — nothing stops a future UI change, a direct service call, or a race (two admins:
one deletes while the other has an edit form open) from hitting it.

Also worth noting (not claimed as a bug, flagging for awareness): `TaxonRepository.restore()`
(`repository/TaxonRepository.java:113-117`) only clears `deleted_at`, leaving `deleted_by`
untouched after a restore — this could be intentional ("who last deleted this, even if since
restored" as a permanent audit trail), unlike `update()`'s gap which is clearly an oversight (the
code visibly tries to preserve soft-delete state by forwarding `deletedAt`, just forgets its
sibling field). Confirm intent before touching `restore()`.

### 2. `AttachmentService.commitTempUploadsQuiet()` — file moves happen outside the try/catch that's supposed to undo them — ORPHANED FILES
`/app/attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentService.java:166-192`

```java
List<Attachment> toSave = temps.stream()
        .map(t -> {
            String finalUrl = isVideo(t.contentType())
                    ? t.tempUrl()
                    : storageService.move(t.tempUrl(), folder, t.filename());  // side effect here
            return Attachment.builder()...build();
        })
        .toList();                                    // exception from .move() propagates from HERE
try {
    attachmentRepository.saveAll(toSave);
} catch (Exception e) {
    toSave.stream()...forEach(a -> storageService.delete(a.getUrl()));  // never reached if .move() failed
    throw e;
}
```

`storageService.move(...)` runs inside the `.stream().map()` that builds `toSave`, i.e. **before**
the `try` block starts. **Failure scenario:** uploading a gallery of 5 images, `move()` succeeds
for files 1-3 and throws on file 4 — files 1-3 are now physically in their final storage location,
but the exception propagates out of the `.stream()...toList()` call itself, so `toSave` is never
built and the `catch` block (whose whole job is to delete moved files on failure) never runs. Those
3 files are orphaned: not in the DB, not cleaned up by this method's own error path. (Whether
`AttachmentCleanupService`'s scheduled job eventually reaps them depends on its own criteria — not
verified as part of this issue, flagged as a follow-up question in "Required verification" below.)
This is a **live, reachable path** — multi-file gallery uploads (up to 10 items, per this
project's own Playwright coverage) are a normal user flow, not an edge case.

### 3. `AttachmentService.upload()` — non-transactional save commits before a later step can still fail — INCONSISTENT DB/STORAGE STATE
`/app/attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentService.java:74-95`

```java
public Attachment upload(...) {  // no @Transactional — unlike delete()/deleteSkipSnapshot()/restoreToUrls() in the same class
    String url = storageService.upload(...);
    try {
        Attachment saved = attachmentRepository.save(...);   // commits immediately, not transactional
        captureMediaChanges(entityType, entityId);            // audit capture — can throw (real, wired starter)
        notifyMediaChanged(entityType, entityId);              // hook — currently unimplemented, can't throw today
        return saved;
    } catch (Exception e) {
        storageService.delete(url);       // deletes the file even though the DB row committed already
        throw e;
    }
}
```

**Failure scenario:** `attachmentRepository.save()` commits (this method isn't `@Transactional`,
unlike its siblings in the same class). If `captureMediaChanges()` throws afterward (a real,
reachable path — `audit-spring-boot-starter` is a live, wired starter, not an unimplemented
optional SPI like the `notifyMediaChanged()` hook currently is), the `catch` block deletes the
just-uploaded file from storage — but the attachment row from `save()` is already committed and
stays in the DB, now pointing at a file that no longer exists. This is **worse than doing nothing**:
instead of a clean rollback, the system ends up with a DB record referencing a nonexistent file,
which would surface later as a broken image/video wherever that attachment is rendered.

### 4. `AttachmentCleanupService.deleteAttachments()` deletes from S3 before the DB, wrong way round — INCONSISTENT STATE ON CRASH
`/app/attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentCleanupService.java:52-76`

```java
private void deleteAttachments() {
    List<String> urls = attachmentRepository.findUrlsDeletedOlderThan(cleanupProperties.retentionDays());
    ...
    urls.forEach(url -> {
        try { storageService.delete(url); } catch (Exception e) { ... failedUrls.add(url); }
    });
    List<String> toDelete = failedUrls.isEmpty() ? urls : urls.stream().filter(u -> !failedUrls.contains(u)).toList();
    int deleted = attachmentRepository.deleteByUrls(toDelete);   // DB delete happens AFTER S3 delete
    ...
}
```

Same root shape as items 2/3 above, same fix direction: storage and DB are mutated in the wrong
order relative to each other. Here S3 objects are deleted first, DB rows second. **Failure
scenario:** if the process crashes or loses its DB connection between the `storageService.delete()`
loop finishing and `attachmentRepository.deleteByUrls()` executing (pod eviction, OOM kill, network
partition — this method is `@Transactional` at the Spring level, but that only protects the DB
statement itself, not the gap between the S3 calls and the DB call), the S3 objects are already
gone but their DB rows survive, now pointing at deleted files — the client hits a 404 on next view.
Already partially defensive for S3-delete *failures* (tracks `failedUrls`, retries them next run)
but that doesn't cover a crash between the two phases. Reversing the order (delete DB rows first,
inside the transaction; delete S3 objects after commit) makes the failure mode safe either way: if
the process dies after the DB commit but before S3 cleanup, the objects are merely orphaned
storage "trash" that a future cleanup pass can still sweep by prefix/age, not DB rows pointing at
nothing.

## Suggested fix

1. ✅ **Item 1 (`TaxonService.update()`) — done 2026-07-15.** Added
   `.deletedBy(existing.getDeletedBy())` to the builder, mirroring how `deletedAt` was already
   forwarded. `TaxonRepository.restore()`'s `deleted_by` handling confirmed intentional (permanent
   "who last deleted this" trail, unaffected) — not touched.
   `integration-tests/src/test/java/org/ost/integrationtests/taxon/TaxonServiceTest.java` (2
   tests: `update_onSoftDeletedTaxon_preservesDeletedBy`, `update_onActiveTaxon_deletedByStaysNull`).
   TDD-verified: confirmed the first test fails against the pre-fix code
   (`expected: 42L but was: null`) before re-applying the fix. `bash scripts/integration-tests.sh
   --sandbox TaxonServiceTest`: 2/2, `BUILD SUCCESS`.
2. ✅ **Item 2 (`commitTempUploadsQuiet()`) — done 2026-07-15.** Replaced the `.stream().map()`
   (which ran `storageService.move()` outside the `try` block) with a plain `for` loop that builds
   `toSave` incrementally *inside* the `try`, so a mid-loop `move()` failure leaves `toSave`
   containing exactly the files that succeeded before it — the existing `catch` block's cleanup now
   actually sees them. Covered with a plain Mockito unit test (no Spring, no DB — matches
   `UserServiceTest`'s shape) rather than Playwright: `AttachmentService`'s storage dependency is
   directly mockable, no test-only fault-injection hook needed.
   `integration-tests/src/test/java/org/ost/integrationtests/attachment/AttachmentServiceTest.java`
   (2 tests: mid-batch `move()` failure cleans up the already-moved files and never reaches
   `saveAll()`; all-succeed case saves once with no cleanup calls). TDD-verified against the
   pre-fix code. `bash scripts/integration-tests.sh --sandbox AttachmentServiceTest`: 2/2,
   `BUILD SUCCESS`.
3. ✅ **Item 3 (`upload()`) — done 2026-07-15.** Added `@Transactional`, matching
   `delete()`/`deleteSkipSnapshot()`/`addVideo()` in the same class — an exception anywhere in the
   method (including from `captureMediaChanges()`) now rolls back the `attachmentRepository.save()`
   too, so there is never a committed row with no corresponding file.
   `integration-tests/src/test/java/org/ost/integrationtests/attachment/AttachmentServiceTransactionTest.java`
   — a real `@SpringBootTest` (not a Mockito unit test: proving a genuine DB transaction rollback
   requires Spring's actual `@Transactional` proxy and a real Postgres, which a plain
   `new AttachmentService(...)` construction can't provide) with its own `TestConfig`
   (`@ImportAutoConfiguration` allow-list, see ADR-009) plus `@MockitoBean` overrides for
   `S3Client`/`StorageService` (no real S3/MinIO dependency) and `AttachmentSnapshotService`
   (forced to throw, to trigger the rollback path) — a plain `@Bean` override lost the
   `@ConditionalOnMissingBean` ordering race against `AttachmentS3Config`'s real beans, confirmed
   directly, hence `@MockitoBean`'s dedicated override mechanism instead (see the class's own
   javadoc). TDD-verified against the pre-fix code (DB row survived the forced audit-capture
   failure). `bash scripts/integration-tests.sh --sandbox AttachmentServiceTransactionTest`: 2/2,
   `BUILD SUCCESS`.
4. ✅ **Item 4 (`AttachmentCleanupService.deleteAttachments()`) — done 2026-07-15.** Swapped the
   order — DB rows deleted first, S3 objects after. Turned out to need more than reordering two
   statements: `cleanup()`'s `@Transactional` deferred the DB commit until the whole method
   returned (i.e. until *after* the S3 loop too), which would have silently reintroduced the same
   crash-window bug in the opposite direction. Removed `@Transactional` from `cleanup()` entirely
   — `deleteByUrls()` is a single SQL statement, already atomic on its own, and now auto-commits
   immediately instead of waiting on the method boundary; see the class's own javadoc for the full
   reasoning. `failedUrls` bookkeeping now only affects logging (no longer excludes URLs from the
   DB delete, since the DB delete already happened first for all of them).
   `integration-tests/src/test/java/org/ost/integrationtests/attachment/AttachmentCleanupServiceTest.java`
   (2 tests: `InOrder`-verified DB-delete-before-S3-delete ordering; a storage failure doesn't
   affect the already-completed DB delete). TDD-verified: both tests failed against the pre-fix
   code. `bash scripts/integration-tests.sh --sandbox AttachmentCleanupServiceTest`: 2/2,
   `BUILD SUCCESS`.

**All 4 items done — improvement-049 complete (2026-07-15).** Full `integration-tests` suite
verified twice consecutively at 49/49 green after all four fixes together.

## Required verification

- Confirm whether `AttachmentCleanupService`'s scheduled orphan-cleanup job would actually catch
  the files orphaned by item 2's bug, or whether its criteria (age threshold? DB-reference check?)
  would miss them — not verified as part of this issue, out of scope for the fix itself (item 2's
  own fix closes the orphaning at the source, independent of whether the safety net would have
  caught it).
- Playwright regression: not run as part of this issue — all four fixes were verified at the
  integration-tests level (real Postgres, real transactions where relevant); a full Playwright
  pass covering attachment/taxon flows is still worth running before considering this fully closed
  end-to-end, tracked as a follow-up rather than blocking.

## Related

- [improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) items 4/5 — the same
  "protected by UI discipline, not code" shape already found and fixed once for
  `TaxonRepository.findByIds()`/`findByTypeAndCode()`.
- `marketplace-app/DECISIONS.md` ADR-029 — the `version`-forwarding discipline for
  `TaxonService.update()`/`AdvertisementService.buildEntity()`; this issue's item 1 is the same
  class of "forward every existing field explicitly" bug, just for `deletedBy` instead of
  `version`.
- `attachment-spring-boot-starter/CLAUDE.md` — owns `AttachmentService`, `AttachmentCleanupService`
  (relevant to the "Required verification" orphan-cleanup question above).
