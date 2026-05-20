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

## 2026-05-19 — Storage SPI internalized; `attachment.enabled` is the subsystem flag

**Decision:** `StorageService` and the subsystem conditional moved out of `platform-contracts` into the starter (package `org.ost.attachment.storage`). The conditional is renamed `@ConditionalOnAttachmentEnabled` and reads `attachment.enabled` (default `true`, `matchIfMissing=true`). The previous prop name `storage.s3.enabled` was reused for the `NoOpStorageService` fallback — that `@ConditionalOnProperty` is now also keyed on `attachment.enabled` (`havingValue = "false"`). S3-specific config (`storage.s3.endpoint`, `region`, `access-key`, `secret-key`, `bucket`, `public-url`) stays under `storage.s3.*` — it is S3-implementation config, not a subsystem switch.

**Why:** No module outside `attachment-spring-boot-starter` referenced `StorageService` or `@ConditionalOnStorageEnabled` — they lived in contracts as noise. `storage.s3.enabled` was de-facto the attachment subsystem master switch (only S3 and NoOp implementations exist), so naming it `attachment.enabled` matches reality and mirrors `audit.enabled` / `@ConditionalOnAuditEnabled`. The previous earlier-on-this-day entry below ("Every starter-owned bean gated by `@ConditionalOnStorageEnabled`") now applies via the renamed annotation.

**Rejected:** Keeping a separate `attachment.enabled` AND `storage.s3.enabled` toggle — premature flexibility. If a second storage backend (local FS, GCS) is ever added, introduce `attachment.storage.type` then; today there is exactly one production backend.

**Migration:** Hard cutover. Repo had no `storage.s3.enabled=false` configuration files; the prop was only mentioned in docs. Anyone on an external config setting `storage.s3.enabled=false` must rename to `attachment.enabled=false`.

---

## 2026-05-19 — Every starter-owned bean gated by `@ConditionalOnAttachmentEnabled`

**Decision:** `AttachmentCleanupJob`, `AttachmentSnapshotService`, `AttachmentRepository`, `AttachmentSnapshotRepository` are now annotated with `@ConditionalOnAttachmentEnabled`. Previously only the higher-level beans (`AttachmentService`, `DefaultAttachmentPort`, `AttachmentGalleryExtensionImpl`, `MediaHistoryExtensionImpl`, `attachmentLiquibase`, `S3Client`, `s3StorageService`) carried the conditional; the lower-level component-scanned classes were instantiated unconditionally.

**Why:** With `attachment.enabled=false` the starter must leave no residue. The critical violation was `AttachmentCleanupJob`: it carries `@Scheduled(cron = "0 0 2 * * *")` and was active regardless of the flag — at 02:00 it would walk a disabled storage. The repositories and snapshot service had no scheduled effect, but their presence in the application context broke the symmetry promise: "subsystem off" should mean "no subsystem beans". The audit starter already followed this rule (`AuditCleanupJob` carries `@ConditionalOnAuditEnabled`); attachment now matches.

**Rejected:** Leaving the repositories/service unconditional with the argument "they have no side effects" — symmetry with the audit starter is a usability contract, not a micro-optimization. A future maintainer reading "subsystem disabled" expects an empty subsystem.

---

## 2026-05-19 — Starter owns `attachmentObjectMapper`; Liquibase gated by subsystem flag

**Decision:** `AttachmentAutoConfiguration` defines `@Bean("attachmentObjectMapper") ObjectMapper` (with `FAIL_ON_UNKNOWN_PROPERTIES` disabled), `@ConditionalOnMissingBean(name = "attachmentObjectMapper")` for override. `AttachmentSnapshotService.objectMapper` is annotated `@Qualifier("attachmentObjectMapper")` so it does not collide with `userSettingsObjectMapper` / `auditObjectMapper` in a context that has all three. The `attachmentLiquibase` bean is now `@ConditionalOnAttachmentEnabled` so `attachment.enabled=false` leaves no schema apply.

**Why:** The starter previously consumed the host application's `userSettingsObjectMapper` — a marketplace-specific name — which broke contexts with multiple `ObjectMapper` beans (the audit starter introduced a second one, surfacing `NoUniqueBeanDefinitionException`). Owning a named mapper and explicit qualifier on every injection site eliminates the ambiguity without using `@Primary`. Gating Liquibase by `@ConditionalOnAttachmentEnabled` matches the audit pattern: disabling the subsystem leaves the database untouched.

**Rejected:** `@Primary` on either mapper (user preference recorded as durable feedback: always qualify, never `@Primary`). Pulling `JavaTimeModule` in — `jackson-datatype-jsr310` is not on the starter's classpath and the attachment JSON shapes do not require it.

---

## 2026-05-19 — Actor-centric public API; user-domain naming purged

**Decision:** Every public method that previously named a `userId` parameter (e.g. `AttachmentPort.softDeleteAll`, `restoreToSnapshot`, gallery upload callbacks) was renamed to `actorId`. The starter no longer references "user" in any contract method, log message, or DTO field.

**Why:** Symmetry with the audit-starter's actor-centric rename and the contract-level shift from `CurrentUserProvider` → `CurrentActorProvider`. "User" implied a marketplace-specific principal; "actor" is neutral and applies to bots, workflows, or service accounts that may upload or remove attachments in non-marketplace deployments.

**Migration:** Hard cutover — no aliases or wrapper methods. Marketplace-app call sites updated in the same commit. SPI `MediaChangeConsumer` already carried only `(entityType, entityId)` and required no change.

**Rejected:** Keeping `userId` aliases for backwards compatibility — there are no external consumers; aliases would persist the user-domain vocabulary forever.

---

## 2026-05-12 — Vaadin IFrame src patching via `Page.executeJs`

**Decision:** In `CardMediaLightbox`, iframe `src` is updated via `UI.getCurrent().getPage().executeJs(...)` in addition to `getElement().setAttribute(...)`.

**Why:** Vaadin's `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client after initial render — the property diff is not propagated. Direct DOM manipulation via JS is the only reliable way to blank or restore the YouTube embed URL. `setAttribute` is kept in sync so Vaadin's internal state stays consistent.

**Rejected:** Using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic `page.evaluate` in Playwright (both `iframe.src` and `iframe.getAttribute('src')` remained unchanged after the Vaadin call).
