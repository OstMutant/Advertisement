# attachment-spring-boot-starter

Auto-configured photo/attachment module with S3-compatible storage for the Advertisement Platform.

## What it provides

- Upload, delete, and restore attachments linked to any entity
- S3-compatible storage via AWS SDK (`S3StorageService`)
- Scheduled cleanup of orphaned attachments (`AttachmentCleanupService`)
- **SPI implementations:** `AttachmentPort`, `AttachmentAuditPort` (called by marketplace-app)

## Key classes

| Class | Role |
|---|---|
| `DefaultAttachmentPort` | Entry point — implements `AttachmentPort`, thin delegation to `AttachmentService` |
| `AttachmentService` | Business logic: upload, delete, restore from snapshot |
| `AttachmentSnapshotService` | Manages attachment snapshot records |
| `AttachmentRepository` | Persists and queries `attachment` table |
| `AttachmentSnapshotRepository` | Stores attachment snapshots for audit/restore |
| `S3StorageService` / `StorageService` | S3-compatible file upload/delete (`org.ost.attachment.services`) |
| `AttachmentCleanupService` | Scheduled orphan cleanup |
| `AttachmentAuditPortImpl` | Implements `AttachmentAuditPort`; thin delegation |

## Dependencies

- `platform-commons` — SPI interfaces (`AttachmentPort`, `AttachmentAuditPort`) and DTOs
  (`AttachmentMediaChangeHook` does not exist)
- Spring Boot, Spring JDBC, Liquibase, AWS SDK S3
