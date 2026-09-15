# improvement-064: `S3StorageService.upload()` never closes the `InputStream` it's given

**Type:** improvement — resource hygiene. Found via direct code review, verified against current
source (2026-07-16).
**Module:** `attachment-spring-boot-starter` (`services/S3StorageService.java`,
`services/AttachmentService.java`).
**Priority:** medium — a defensive fix that's cheap and safe regardless of severity; real severity
is uncertain (see Problem) rather than confirmed-critical.
**When:** independent, no blockers.

## Problem

`S3StorageService.upload()`:
```java
s3Client.putObject(
        PutObjectRequest.builder()...build(),
        RequestBody.fromInputStream(inputStream, contentLength)
);
```
`RequestBody.fromInputStream(InputStream, long)` is documented AWS SDK v2 behavior to **not** close
the given stream (to support retries) — confirmed, not assumed. Neither `S3StorageService` nor its
two callers in `AttachmentService` that take an `InputStream` parameter (`upload()`, `uploadTemp()`
— `uploadDto()` is a thin wrapper delegating to `upload()`, not a separate call site) wrap this in
try-with-resources anywhere.

**Severity is genuinely uncertain, not confirmed-critical:** the `InputStream` passed in
ultimately comes from `AttachmentGallery.buildUploadHandler()`'s `UploadEvent.getInputStream()` —
Vaadin's newer `UploadHandler` API (Vaadin 24.4+), not the classic `Upload` + `Receiver`/
`MemoryBuffer` model this kind of "leaked file descriptor" concern usually targets. Whether Vaadin's
own `UploadHandler` request-cycle machinery already closes this stream after
`handleUploadRequest()` returns could not be confirmed from this codebase's source alone. Filed as
a defensive fix regardless: closing an already-closed `InputStream` is a safe no-op in the JDK, so
adding try-with-resources here cannot make things worse even if Vaadin already handles it, and
removes the uncertainty either way.

## Suggested fix

Wrap the `InputStream` parameter in try-with-resources at each `AttachmentService` call site that
receives one and forwards it to `storageService.upload(...)`, so the stream is guaranteed closed
once the upload call returns (success or failure) regardless of what Vaadin's own upload
infrastructure does with it.

## Related

- `attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/S3StorageService.java`
  — the AWS SDK call this issue is about.
- `attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentService.java`
  — the two call sites (`upload`, `uploadTemp`) needing try-with-resources.
- `marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/attachment/AttachmentGallery.java`
  — `buildUploadHandler()`, the actual source of the `InputStream` via `UploadEvent.getInputStream()`.
