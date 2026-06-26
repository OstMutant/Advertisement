# improvement-001: Attachment UI boundary violation

**Type:** improvement — architectural
**Module:** attachment-spring-boot-starter + marketplace-app
**Priority:** high — violates module independence rule

## Problem

Six UI components in `marketplace-app` directly import `attachment-spring-boot-starter` internals
instead of going through `AttachmentPort` / platform-commons DTOs:

| Component | Illegal import |
|-----------|---------------|
| `AttachmentGallery` | `org.ost.attachment.entities.Attachment` (entity) |
| `AttachmentLightbox` | `org.ost.attachment.services.AttachmentService` |
| `AttachmentThumbnail` | `org.ost.attachment.services.AttachmentSnapshotService` |
| `CardLightboxStrip` | `org.ost.attachment.util.MediaContentTypeUtil` |
| `CardLightboxViewer` | `org.ost.attachment.util.MediaContentTypeUtil` |
| `CardMediaLightbox` | `org.ost.attachment.util.MediaContentTypeUtil` |

## Required fixes

1. Move `MediaContentTypeUtil` → `platform-commons/attachment.util`
2. Replace direct `Attachment` entity usage at UI call sites with `AttachmentMediaSummaryDto`
3. Replace direct `AttachmentService` / `AttachmentSnapshotService` injection with `AttachmentPort`

## Constraints

- Do NOT re-introduce `AttachmentGalleryPort` — it was removed 2026-06-15.
  Route through the existing `AttachmentPort` instead.
- `YoutubeUtil` is already in `platform-commons/attachment.util` — use as reference.
