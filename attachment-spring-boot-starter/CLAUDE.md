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
- `AttachmentPort`, `AttachmentMediaChangeHook`, `AttachmentAuditHook` live in `platform-commons`.
- UI components in marketplace-app MUST degrade gracefully via `ObjectProvider.ifAvailable()` when this starter is absent.
- `@EnableJdbcRepositories(basePackages = "org.ost.attachment")` declared in `AttachmentAutoConfiguration`.
- Storage (`StorageService` and its S3 implementation) lives in `org.ost.attachment.services` — not in marketplace-app.
