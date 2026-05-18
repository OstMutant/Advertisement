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

## 2026-05-18 — Decoupled from the advertisement domain (generic over EntityType)

**Decision:** The starter now operates on arbitrary entities, not just `ADVERTISEMENT`. Every public API — `AttachmentService`, `AttachmentSnapshotService`, `AttachmentRepository`, `AttachmentSnapshotRepository`, `AttachmentGallery`, `CardMediaLightbox`, the activity projection — takes `(EntityType entityType, Long entityId)` instead of a hard-coded advertisement id. The `attachment` and `attachment_snapshot` tables grew an `entity_type` column. Domain Spring events (`AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`, `AdvertisementMediaUpdatedEvent`) were replaced by SPI calls: `AttachmentPort` (domain → starter) and `MediaChangeConsumer` (starter → domain). S3 folder layout is canonical singular `entityType.name().toLowerCase() + "/" + entityId` (e.g. `advertisement/42`, `user/17`).

**Why:** The original starter compiled only against an advertisement-shaped world (event types, field names, S3 path constants). Adding photo galleries to USER, COMMENT, or any future entity required either renaming everything or branching by name. SPI symmetry with `audit-spring-boot-starter` (which already uses `AuditPort` + a current-user SPI) was the second driver — both starters now follow the same pattern: domain calls a port, starter notifies the domain via an `ObjectProvider`-injected SPI. (Same-day follow-up unified the two starters' user-provider SPIs into a single `core.spi.CurrentUserProvider` — see `platform-contracts/DECISIONS.md`.)

**Migration:** Hard cutover — no compatibility shims, no fallback `EntityType.ADVERTISEMENT` defaults. DB and MinIO were wiped because this is dev-only state. Marketplace-app wires `EntityType.ADVERTISEMENT` explicitly at every call site; `AdvertisementMediaChangeConsumer` (in marketplace-app) reacts to changes and updates the advertisement table's denormalized media columns.

**Deferred:**
- `EntityRef(EntityType, Long)` record — would collapse the repeated `(entityType, entityId)` pair argument; deferred as cosmetic.
- `EntityType.storageKey()` method — currently the S3 folder uses `name().toLowerCase()`; a typed method would let entities customize their storage segment if ever needed.
- `AttachmentGalleryExtension`/`AdvertisementHistoryExtension` naming — the latter still carries "Advertisement" in its name but is generic over `EntityType`; rename deferred until a second consumer exists.

**Rejected:** Keeping the event-based flow alongside the SPI — splits the contract surface and forces consumers to choose. The starter speaks SPI and only SPI.

---

## 2026-05-12 — Vaadin IFrame src patching via `Page.executeJs`

**Decision:** In `CardMediaLightbox`, iframe `src` is updated via `UI.getCurrent().getPage().executeJs(...)` in addition to `getElement().setAttribute(...)`.

**Why:** Vaadin's `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client after initial render — the property diff is not propagated. Direct DOM manipulation via JS is the only reliable way to blank or restore the YouTube embed URL. `setAttribute` is kept in sync so Vaadin's internal state stays consistent.

**Rejected:** Using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic `page.evaluate` in Playwright (both `iframe.src` and `iframe.getAttribute('src')` remained unchanged after the Vaadin call).
