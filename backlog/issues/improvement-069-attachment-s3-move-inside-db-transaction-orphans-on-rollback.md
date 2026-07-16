# improvement-069: S3 file move happens inside the advertisement-save DB transaction — orphaned S3 objects on rollback

**Type:** improvement — data-integrity/distributed-transaction gap. Found via direct code review,
verified against current source (2026-07-16).
**Module:** `marketplace-app` (`services/advertisement/AdvertisementSaveService.java`),
`attachment-spring-boot-starter` (`services/AttachmentService.commitTempUploadsQuiet()`).
**Priority:** medium — real orphan-file risk, but requires a mid-save failure *after* the S3 move
to actually manifest (not triggered by every save); no reports of it happening yet, filed as a
found structural gap, not an active incident.
**When:** independent, no blockers.

## Problem

`AdvertisementSaveService.save()` runs its entire flow inside one `TransactionTemplate` block:
```java
return tx.execute(status -> {
    ...
    Long savedId = advertisementPortFactory.get().save(dto, actorId);
    Long gallerySnapshotId = commitGallery.apply(new EntityRef(EntityType.ADVERTISEMENT, savedId));
    ...
    taxonPortFactory.ifAvailable(p -> p.replaceAssignments(EntityType.ADVERTISEMENT, savedId, catIds));
    ...
    auditPortFactory.ifAvailable(p -> p.captureCreation(savedId, after, actorId)); // or captureUpdate
    ...
    return savedId;
});
```
`commitGallery.apply(...)` (the `activeHandle.commit(entityRef)` callback wired in from
`AdvertisementFormOverlayModeHandler.save()`) eventually calls
`AttachmentService.commitTempUploadsQuiet()`, which does a **real, physical, non-transactional** S3
move (`storageService.move(t.tempUrl(), folder, t.filename())`) from `temp/` to
`advertisement/<id>/`.

Confirmed: this S3 move happens roughly in the *middle* of the DB transaction — category
reassignment and audit capture both run *after* it, still inside the same `tx.execute(...)` block.
If either of those (or the final commit itself) throws, the DB transaction rolls back — but the S3
files have already been physically moved and there's no compensating action to move them back or
delete them.

**Confirmed the resulting orphans are permanently unswept:** `AttachmentCleanupService` only
handles two things — stale `temp/`-prefixed uploads, and DB-row-driven deletions via
`attachmentRepository.deleteByUrls()`. A file sitting in `advertisement/<id>/` with no
corresponding `attachment` row (because the DB transaction rolled back after the S3 move already
happened) matches neither case and is never revisited by any existing cleanup path.

## Suggested fix

This is a real distributed-transaction problem with no fully "clean" single fix — options, cheapest
first:
1. **Minimum: log loudly on rollback.** Add a transaction synchronization
   (`TransactionSynchronizationManager.registerSynchronization`) that logs an ERROR with the moved
   URLs if the transaction rolls back after `commitGallery.apply()` ran — turns a silent orphan
   into a discoverable one via logs/alerting, without fixing the root cause.
2. **Better: reorder to move S3 files last, closest to the transaction's actual commit.** Doesn't
   eliminate the gap (a failure between S3 move and DB commit is still possible) but shrinks the
   window since everything else that can fail (`replaceAssignments`, audit capture) would already
   have succeeded by the time the move runs.
3. **Most complete: periodic reconciliation sweep.** A scheduled job (alongside
   `AttachmentCleanupService`'s existing scheduled work) that lists S3 objects under
   `advertisement/*/` older than some threshold and deletes any with no matching `attachment` row —
   the general fix for this whole class of distributed-transaction gap, not just this one call
   site.

## Related

- `attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentCleanupService.java`
  — confirmed scope (temp/ + DB-driven deletes only) that would need extending for option 3.
- `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/overlay/modes/AdvertisementFormOverlayModeHandler.java`
  — where the `commitGallery` callback passed into `AdvertisementSaveService.save()` originates.
