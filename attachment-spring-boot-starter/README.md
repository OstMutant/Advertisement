# attachment-spring-boot-starter

Auto-configured photo/attachment module with S3-compatible storage for the Advertisement Platform.

## What it provides

- Upload, delete, and restore attachments linked to advertisements
- S3-compatible storage via AWS SDK (`S3StorageService`)
- Scheduled cleanup of orphaned attachments (`AttachmentCleanupJob`)
- **SPI implementations:** `AttachmentPort` (called by marketplace-app for attachment operations)

## Key classes

| Class | Role |
|---|---|
| `AttachmentPortImpl` | Entry point — implements `AttachmentPort`, delegates to `AttachmentService` |
| `AttachmentService` | Business logic: upload, delete, restore from snapshot |
| `AttachmentRepository` | Persists and queries `attachment` table |
| `PhotoSnapshotRepository` | Stores attachment snapshots for audit/restore |
| `S3StorageService` | S3-compatible file upload/delete |
| `AttachmentCleanupJob` | Scheduled orphan cleanup |

## Dependencies

- `platform-commons` — SPI interfaces (`AttachmentPort`, `AttachmentMediaChangeHook`, `AttachmentAuditHook`) and DTOs
- Spring Boot, Spring JDBC, Liquibase, AWS SDK S3
