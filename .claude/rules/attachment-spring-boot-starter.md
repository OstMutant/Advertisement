---
paths: ["attachment-spring-boot-starter/**"]
---

## attachment-spring-boot-starter

Auto-configures the photo/attachment module with S3-compatible storage. Active whenever the jar is on the classpath.

Java package root: `org.ost.attachment`

---

## What it owns

See `attachment-spring-boot-starter/README.md`'s "Key classes" table for the class list and
one-line roles — not restated here.

**Autoconfiguration entry point:** `AttachmentAutoConfiguration`

---

## Schema

Liquibase changelog: `db/attachment-changelog/changes/01-attachment-schema.xml`  
Tables: `attachment`, `attachment_snapshot`

---

## Key constraints

- UI components (`AttachmentGallery`, `CardMediaLightbox`) live in `marketplace-app`.
- `AttachmentPort`, `AttachmentAuditPort` live in `platform-commons` (`AttachmentMediaChangeHook`
  does not exist; `AttachmentAuditPort`'s call direction — see `docs/ai/adr-index.md`).
- UI components in marketplace-app MUST degrade gracefully via `ObjectProvider.ifAvailable()` when this starter is absent.
- `@EnableJdbcRepositories(basePackages = "org.ost.attachment.repository")` declared in `AttachmentAutoConfiguration`.
- Storage (`StorageService` and its S3 implementation) lives in `org.ost.attachment.services` — not in marketplace-app.
- `AttachmentPort.getMediaSummaries(EntityType, Set<Long>)` (bulk) computes the "main attachment"
  for each entity: the earliest-created (id as tiebreak on tied `created_at`), non-deleted
  attachment for the entity, via `AttachmentRepository.loadMediaStats(EntityType, Set<Long>)`
  (Postgres `ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at ASC, id ASC)`). Exists
  so consumers (e.g. `AdvertisementService.enrichWithMediaSummary()`) never cache media data on
  their own entity's row — one bulk query per list render instead of a stored, sync-triggered
  column. See `docs/ai/adr-index.md`. There is no single-entity
  `AttachmentPort.getMediaSummary(EntityRef)` port method — every real caller uses the bulk
  variant; `AttachmentRepository.loadMediaStats(EntityType, Long)`, the single-entity repository
  method the removed port method used to delegate to, is kept because it has its own direct
  repository-level test coverage.
