## attachment-spring-boot-starter

Auto-configures the photo/attachment module with S3-compatible storage. Active whenever the jar is on the classpath.

Java package root: `org.ost.attachment`

---

## What it owns

- `Attachment` entity + `AttachmentRepository` + `AttachmentSnapshotRepository`
- `AttachmentService` — business logic for upload, delete, restore from snapshot
- `AttachmentSnapshotService` — manages attachment snapshot records
- `DefaultAttachmentPort` — implements `AttachmentPort`; thin delegation to `AttachmentService`
- `AttachmentAuditHookImpl` — implements `AttachmentAuditHook`; thin delegation
- `AttachmentCleanupService` — scheduled service for orphan cleanup (uses `CleanupProperties`)
- `S3StorageService` / `StorageService` — S3-compatible storage via AWS SDK

**Autoconfiguration entry point:** `AttachmentAutoConfiguration`

---

## Schema

Liquibase changelog: `db/attachment-changelog/changes/01-attachment-schema.xml`  
Tables: `attachment`, `attachment_snapshot`

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here. UI components (`AttachmentGallery`, `CardMediaLightbox`) live in `marketplace-app`.
- `AttachmentPort`, `AttachmentAuditHook` live in `platform-commons` (`AttachmentMediaChangeHook`
  was removed entirely, improvement-102 — zero implementations, see
  `marketplace-app/DECISIONS.md` ADR-035).
- UI components in marketplace-app MUST degrade gracefully via `ObjectProvider.ifAvailable()` when this starter is absent.
- `@EnableJdbcRepositories(basePackages = "org.ost.attachment.repository")` declared in `AttachmentAutoConfiguration`.
- Storage (`StorageService` and its S3 implementation) lives in `org.ost.attachment.services` — not in marketplace-app.
- `AttachmentPort.getMediaSummaries(EntityType, Set<Long>)` (bulk) computes the "main attachment"
  for each entity: the earliest-created (id as tiebreak on tied `created_at` — improvement-091),
  non-deleted attachment for the entity, via `AttachmentRepository.loadMediaStats(EntityType,
  Set<Long>)` (Postgres `ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at ASC, id
  ASC)`). Exists so consumers (e.g. `AdvertisementService.enrichWithMediaSummary()`) never cache
  media data on their own entity's row — one bulk query per list render instead of a stored,
  sync-triggered column. See `marketplace-app/DECISIONS.md` ADR-035. (A single-entity
  `AttachmentPort.getMediaSummary(EntityRef)` port method existed alongside this but was removed
  as dead API surface — improvement-115 — since every real caller already used the bulk variant;
  `AttachmentRepository.loadMediaStats(EntityType, Long)`, the single-entity repository method it
  delegated to, is kept because it has its own direct repository-level test coverage.)
