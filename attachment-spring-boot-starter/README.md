# attachment-spring-boot-starter

Auto-configures the photo/attachment domain — upload, deletion, restore, and S3-compatible storage
for files and embedded videos linked to any entity, plus the filename-diff snapshot history behind
both restore and the audit timeline.

## What it provides

- Upload and temp-upload of files (S3-backed) and embedded videos (YouTube, allow-listed Vimeo
  embeds), commit/discard of a temp upload batch, soft-delete, and restore to an arbitrary prior
  url set.
- A cron-configurable retention sweep (`CleanupProperties`) so abandoned uploads and
  deleted-but-unpurged storage never accumulate unbounded.
- A bulk "main attachment" media summary per entity, computed on demand rather than cached on the
  entity's own row.
- **SPI implementations:** `AttachmentPort` (called by `marketplace-orchestrator`'s
  `AttachmentMediaService`/`AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService`/
  `AdvertisementDisplayEnrichmentService`), `AttachmentAuditPort` (called by
  `AttachmentMediaService`).

## Data flow

Every command/query enters through `DefaultAttachmentPort`/`AttachmentAuditPortImpl`, the sole
implementations of `AttachmentPort`/`AttachmentAuditPort` — both delegate every call, unchanged,
to `AttachmentService`/`AttachmentSnapshotService`, the module's two business-logic classes.

- **Upload:** `AttachmentPort.upload`/`uploadTemp` → `AttachmentService` validates the declared and
  magic-byte-detected content type via `AttachmentContentTypeValidator` (rejecting anything outside
  `AttachmentAllowedContentTypes.VALUES`), then calls `StorageService` (`S3StorageService`) to write
  the object, then persists an `Attachment` row via
  `AttachmentRepository`; a temp upload skips the row and is later promoted by
  `commitTempUploads`, which moves each non-embedded temp object into its entity's folder (an
  embedded-video temp entry keeps its original url unmoved) and saves the rows in one batch.
  `addVideo`/`addVideoTemp` resolve the url through `AttachmentVideoUtil` instead of touching
  `StorageService` at all. `upload`, `addVideo`, and `commitTempUploads` each end by calling
  `AttachmentSnapshotService.capture`, which diffs the entity's new active url set against
  `AttachmentSnapshotRepository`'s last-stored one and persists the result as a new
  `attachment_snapshot` row; a plain delete/restore does not — its own row-level history is
  already the `deleted_at`/`deleted_by_actor_id` columns.
- **Delete/restore:** `AttachmentPort.delete`/`restoreToUrls` resolve the current actor from
  `CurrentActorHook` before calling `AttachmentService`; `softDeleteAll` instead takes an
  already-resolved actor id from its caller. Each updates `attachment` rows via
  `AttachmentRepository` (soft-delete sets `deleted_at`/`deleted_by_actor_id`; restore un-deletes
  the target urls and re-deletes the rest) — the S3 object itself is never touched here, only the
  row.
- **Read:** `AttachmentPort.getByEntityId`/`getByEntityAndUrls`/`getMediaSummaries` →
  `AttachmentService` → `AttachmentRepository`, mapping each `Attachment` row (or, for summaries,
  `AttachmentRepository.loadMediaStats`'s per-entity "earliest active row" query) to its DTO.
  `AttachmentAuditPort.getChangesBySnapshotId`/`getMediaStateForSnapshot` →
  `AttachmentSnapshotService` → `AttachmentSnapshotRepository`, turning a stored snapshot's JSON
  diff back into a change entry/display string for the audit timeline.
- **Cleanup:** `AttachmentAutoConfiguration`'s `SchedulingConfigurer` bean triggers
  `AttachmentCleanupService.cleanup()` on the configured cron; it deletes stale `temp/` objects
  directly via `StorageService`, then deletes retention-expired `attachment` rows before removing
  their matching S3 objects, then retention-expired `attachment_snapshot` rows, then sweeps any
  per-entity-folder S3 object with no matching row at all.

## Dependencies

- `platform-commons` — `AttachmentPort`/`AttachmentAuditPort` (`attachment.spi`), `AttachmentItemDto`/
  `AttachmentMediaSummaryDto`/`TempAttachmentDto` (`attachment.dto`), `AttachmentMediaContentType`/
  `AttachmentAllowedContentTypes`/`YoutubeUtil` (`attachment.model`/`attachment.util`), plus the
  cross-domain `EntityType`/`EntityRef`/`ChangeEntry` (`core.model`), `CleanupProperties`
  (`core.config`), `CurrentActorHook` (`core.spi`), and `ComponentFactory`.
- Spring Boot (`spring-boot-starter`, `spring-boot-starter-data-jdbc`, `spring-boot-liquibase`) —
  the autoconfiguration/JDBC-repository/migration machinery `AttachmentAutoConfiguration` builds on.
- AWS SDK S3 — the only storage backend implementation shipped (`S3StorageService`); `StorageService`
  itself stays storage-agnostic so a future backend can be swapped in without touching
  `AttachmentService`.
- Apache Tika (`tika-core`) — magic-byte content-type detection backing
  `AttachmentContentTypeValidator`.
- No Maven dependency on any sibling starter (enforced by this module's own `maven-enforcer-plugin`
  `enforce-no-starter-to-starter-deps` rule) — the UI components that render attachments live in
  `marketplace-app` instead, reached only through `AttachmentPort`.
