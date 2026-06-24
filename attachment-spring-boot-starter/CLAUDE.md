## attachment-spring-boot-starter

Auto-configures the photo/attachment module with S3-compatible storage. Active whenever the jar is on the classpath.

Java package root: `org.ost.attachment`

---

## What it owns

- `Attachment` entity + `AttachmentRepository` + `PhotoSnapshotRepository`
- `AttachmentService` — business logic for upload, delete, restore from snapshot
- `AttachmentPortImpl` — implements `AttachmentPort`; thin delegation to `AttachmentService`
- `AttachmentMediaChangeHookImpl`, `AttachmentAuditHookImpl` — implement starter-side hooks
- `AttachmentCleanupJob` — scheduled job for orphan cleanup (uses `CleanupProperties`)
- `S3StorageService` — S3-compatible storage via AWS SDK

**Autoconfiguration entry point:** `AttachmentAutoConfiguration`

---

## Schema

Liquibase changelog: `db/changelog/attachment-changelog.xml`  
Tables: `attachment`, `photo_snapshot`

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here. UI components (`AttachmentGallery`, `CardMediaLightbox`) live in `marketplace-app`.
- `AttachmentPort`, `AttachmentMediaChangeHook`, `AttachmentAuditHook` live in `platform-commons`.
- UI components in marketplace-app MUST degrade gracefully via `ObjectProvider.ifAvailable()` when this starter is absent.
- `@EnableJdbcRepositories(basePackages = "org.ost.attachment")` declared in `AttachmentAutoConfiguration`.
- Storage (`StorageService` and its S3 implementation) lives in `org.ost.attachment.storage` — not in marketplace-app.
