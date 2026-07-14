# improvement-049: Taxon update() silently drops deletedBy; two AttachmentService paths leave orphaned state on partial failure

**Type:** bug fix. Found via targeted code review of `taxon-spring-boot-starter` and
`attachment-spring-boot-starter` — modules not previously audited in this session's earlier passes
(improvement-045/047/048 covered `marketplace-app`/`user-spring-boot-starter`/`advertisement`/
partially `taxon`). All three findings verified directly against current source, not inferred.
**Module:** `taxon-spring-boot-starter` (`TaxonService.update()`),
`attachment-spring-boot-starter` (`AttachmentService.upload()`, `.commitTempUploadsQuiet()`).
**Priority:** medium — none are live-traffic incidents today (see reachability notes per item), but
all three are real, silent data-corruption/orphan-state risks, not test-coverage gaps; items 2/3
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

## Suggested fix

1. **Item 1 (`TaxonService.update()`):** add `.deletedBy(existing.getDeletedBy())` to the builder
   — one-line fix, mirrors how `deletedAt` is already forwarded. Add a Testcontainers regression
   test in `integration-tests` (new `TaxonRepositoryTest` method or a small
   `TaxonServiceTest`-equivalent, following the improvement-045-item-6 precedent of testing through
   the real service/repository, not a mock): soft-delete a taxon, then call `update()` on it,
   assert `deletedBy` survives. First confirm current intent for `restore()`'s `deleted_by`
   handling (leave as history, or also clear it) before deciding if that needs its own fix.
2. **Item 2 (`commitTempUploadsQuiet()`):** move the `storageService.move()` calls inside the
   `try` block (or wrap the whole method body, including the `.stream()` construction, in one
   `try`/`catch` that can see and clean up whatever was moved so far) so a mid-loop failure can
   still trigger cleanup of the files that *did* move successfully before the failure. Needs a
   Playwright regression test per `.claude/rules.md` "Test Coverage After Bug Fixes" (upload a
   gallery where one file's move is forced to fail — may need a test-only fault injection point,
   or cover this at the `AttachmentService` unit-test level instead if Playwright can't easily
   force a mid-batch storage failure).
3. **Item 3 (`upload()`):** either make `upload()` `@Transactional` so the DB `save()` only commits
   once the whole method succeeds (matching its sibling methods in the same class), or reorder so
   `captureMediaChanges()`/`notifyMediaChanged()` run before the DB commit point if a full
   transaction isn't feasible here for some reason (verify why the other methods are
   `@Transactional` but this one isn't — check history/intent before assuming it's simply an
   oversight). If `@Transactional` doesn't cover the `storageService.upload()` call itself (file
   writes aren't part of a DB transaction anyway), the fix is really about making the **DB save**
   atomic with the **subsequent DB-touching steps** (`captureMediaChanges()`), not the file upload.

## Required verification

- Confirm whether `AttachmentCleanupService`'s scheduled orphan-cleanup job would actually catch
  the files orphaned by item 2's bug, or whether its criteria (age threshold? DB-reference check?)
  would miss them — not verified as part of this issue, changes the urgency assessment for item 2
  if the existing cleanup job already provides a safety net.
- After each fix, run the full `bash scripts/integration-tests.sh --sandbox` suite plus the
  relevant Playwright attachment/taxon specs (`04-marketplace-advertisement-flow` for gallery
  upload, `03-marketplace-promotion-flow` for taxon edit) to confirm no regression.

## Related

- [improvement-045](improvement-045-critical-test-coverage-gaps.md) items 4/5 — the same
  "protected by UI discipline, not code" shape already found and fixed once for
  `TaxonRepository.findByIds()`/`findByTypeAndCode()`.
- `marketplace-app/DECISIONS.md` ADR-029 — the `version`-forwarding discipline for
  `TaxonService.update()`/`AdvertisementService.buildEntity()`; this issue's item 1 is the same
  class of "forward every existing field explicitly" bug, just for `deletedBy` instead of
  `version`.
- `attachment-spring-boot-starter/CLAUDE.md` — owns `AttachmentService`, `AttachmentCleanupService`
  (relevant to the "Required verification" orphan-cleanup question above).
