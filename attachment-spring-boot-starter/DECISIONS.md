# Architecture & Technical Decisions — attachment-spring-boot-starter

---

## 2026-05-07 — Attachment logic extracted from marketplace-app

**Decision:** All attachment/photo domain logic (entity, repository, UI components `AttachmentGallery`, `CardMediaLightbox`) lives in this module, not in `marketplace-app`.

**Why:** Enables two independent deployments — the attachment module can be used without the advertisement app. The starter is auto-configured via Spring Boot's autoconfiguration mechanism.

**Rejected:** Keeping UI components in `marketplace-app` — would couple the UI to the app module and prevent reuse.

---

## 2026-05-13 — MediaContentType enum centralizes video content type constants

**Decision:** `"video/youtube"` and `"video/embed"` are defined once in `MediaContentType` enum (`org.ost.attachment.entity`). Both `AttachmentGallery` and `CardMediaLightbox` reference it instead of using raw strings or private constants.

**Why:** The two classes independently duplicated the same string literals. Centralizing eliminates the risk of inconsistency and makes the `isVideo()` check a single-source-of-truth call.

**Rejected:** Placing the enum in `platform-contracts` — the content types are an internal attachment-module concept, not a cross-module contract.

---

## 2026-05-13 — S3 storage implementation merged into this module

**Decision:** `S3StorageService` and `NoOpStorageService` (formerly in `storage-s3-spring-boot-starter`) were merged into `attachment-spring-boot-starter`. Their beans are now registered in `AttachmentAutoConfiguration`. The `storage-s3-spring-boot-starter` module was deleted.

**Why:** Storage only exists to serve attachments. There is no realistic scenario where storage runs without the attachment module or vice versa. Two modules with a mandatory one-way dependency added complexity with no benefit.

**Rejected:** Keeping `storage-s3-spring-boot-starter` as a separate module — the only theoretical benefit was "S3 storage without attachment logic", which has no concrete use case in this project.

---

## 2026-05-13 — IFrame sandbox attribute on all video embeds

**Decision:** All `IFrame` components for video embedding in `AttachmentGallery` and `CardMediaLightbox` carry `sandbox="allow-scripts allow-same-origin allow-presentation allow-forms"`.

**Why:** Without `sandbox`, the embedded iframe has unrestricted browser capabilities. The chosen flags are the minimum required for YouTube and generic embed playback.

---

## 2026-05-12 — Vaadin IFrame src patching via `Page.executeJs`

**Decision:** In `CardMediaLightbox`, iframe `src` is updated via `UI.getCurrent().getPage().executeJs(...)` in addition to `getElement().setAttribute(...)`.

**Why:** Vaadin's `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client after initial render — the property diff is not propagated. Direct DOM manipulation via JS is the only reliable way to blank or restore the YouTube embed URL. `setAttribute` is kept in sync so Vaadin's internal state stays consistent.

**Rejected:** Using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic `page.evaluate` in Playwright (both `iframe.src` and `iframe.getAttribute('src')` remained unchanged after the Vaadin call).
