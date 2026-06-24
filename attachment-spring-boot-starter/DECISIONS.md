# Architecture & Technical Decisions — attachment-spring-boot-starter

---

## ~~2026-05-19 — Attachment descriptors migrated to Read/Write namespace~~ *(removed 2026-05-21)*

`AttachmentDescriptor`, `AttachmentSnapshotDescriptor`, and `SqlEntityDescriptor` were introduced then removed. The Read/Write namespace descriptor pattern was superseded by the standard project-wide repository pattern (`@Repository` + `JdbcClient` + inline SQL text blocks). No descriptor or projection pattern remains in the codebase.

---

## 2026-05-07 — Attachment domain logic extracted from marketplace-app

**Decision:** All attachment/photo domain logic (entity, repository, services) lives in this module, not in `marketplace-app`.

**Why:** Enables two independent deployments — the attachment module can be used without the advertisement app. The starter is auto-configured via Spring Boot's autoconfiguration mechanism.

**2026-06-13 update:** UI components (`AttachmentGallery`, `CardMediaLightbox`, `AttachmentLightbox`, `AttachmentThumbnail`, `CardLightboxStrip`, `CardLightboxViewer`) were moved to `marketplace-app` as part of the UI monolith consolidation. The starter now owns only domain logic and JdbcClient persistence — no Vaadin UI.

---

## 2026-05-13 — S3 storage implementation merged into this module

**Decision:** `S3StorageService` and `NoOpStorageService` (formerly in `storage-s3-spring-boot-starter`) were merged into `attachment-spring-boot-starter`. Their beans are now registered in `AttachmentAutoConfiguration`. The `storage-s3-spring-boot-starter` module was deleted.

**Why:** Storage only exists to serve attachments. There is no realistic scenario where storage runs without the attachment module or vice versa. Two modules with a mandatory one-way dependency added complexity with no benefit.

**Rejected:** Keeping `storage-s3-spring-boot-starter` as a separate module — the only theoretical benefit was "S3 storage without attachment logic", which has no concrete use case in this project.

---

## 2026-05-13 — IFrame sandbox attribute on all video embeds

**Decision:** All `IFrame` components for video embedding (`AttachmentGallery`, `CardMediaLightbox` — now in `marketplace-app`) carry `sandbox="allow-scripts allow-same-origin allow-presentation allow-forms"`.

**Why:** Without `sandbox`, the embedded iframe has unrestricted browser capabilities. The chosen flags are the minimum required for YouTube and generic embed playback.

---

## 2026-05-18 — Decoupled from the advertisement domain (generic over EntityType)

**Decision:** The starter now operates on arbitrary entities, not just `ADVERTISEMENT`. Every public API — `AttachmentService`, `AttachmentSnapshotService`, `AttachmentRepository`, `AttachmentSnapshotRepository`, `AttachmentGallery`, `CardMediaLightbox`, the activity projection — takes `(EntityType entityType, Long entityId)` instead of a hard-coded advertisement id. The `attachment` and `attachment_snapshot` tables grew an `entity_type` column. Domain Spring events (`AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`, `AdvertisementMediaUpdatedEvent`) were replaced by SPI calls: `AttachmentPort` (domain → starter) and `MediaChangeConsumer` (starter → domain). S3 folder layout is canonical singular `entityType.name().toLowerCase() + "/" + entityId` (e.g. `advertisement/42`, `user/17`).

**Why:** The original starter compiled only against an advertisement-shaped world (event types, field names, S3 path constants). Adding photo galleries to USER, COMMENT, or any future entity required either renaming everything or branching by name. SPI symmetry with `audit-spring-boot-starter` (which already uses `AuditPort` + a current-user SPI) was the second driver — both starters now follow the same pattern: domain calls a port, starter notifies the domain via an `ObjectProvider`-injected SPI. (Same-day follow-up unified the two starters' user-provider SPIs into a single `core.spi.CurrentUserProvider` — see `platform-commons/DECISIONS.md`.)

**Migration:** Hard cutover — no compatibility shims, no fallback `EntityType.ADVERTISEMENT` defaults. DB and MinIO were wiped because this is dev-only state. Marketplace-app wires `EntityType.ADVERTISEMENT` explicitly at every call site; `MediaChangeHookImpl` (in marketplace-app) reacts to changes and updates the advertisement table's denormalized media columns.

**Deferred:**
- ✅ `EntityRef(EntityType, Long)` record — implemented in `platform-commons/core.model`; used throughout all starters and marketplace-app.
- `EntityType.storageKey()` method — currently the S3 folder uses `name().toLowerCase()`; a typed method would let entities customize their storage segment if ever needed.
- `AttachmentGalleryExtension`/`AdvertisementHistoryExtension` naming — the latter still carries "Advertisement" in its name but is generic over `EntityType`; rename deferred until a second consumer exists.

**Rejected:** Keeping the event-based flow alongside the SPI — splits the contract surface and forces consumers to choose. The starter speaks SPI and only SPI.

---

## 2026-05-19 — Storage SPI internalized

**Decision:** `StorageService` moved out of `platform-commons` into the starter (package `org.ost.attachment.storage`). S3-specific config (`storage.s3.endpoint`, `region`, `access-key`, `secret-key`, `bucket`, `public-url`) stays under `storage.s3.*`.

**Why:** No module outside `attachment-spring-boot-starter` referenced `StorageService` — it lived in contracts as noise. `platform-commons` is reserved for types crossed by ≥2 modules.

**Superseded (2026-05-23):** `@ConditionalOnAttachmentEnabled` and the `attachment.enabled` property were removed. The starter is always active when present in the classpath — jar presence is the toggle. `@AutoConfiguration` already requires `DataSource` on classpath (`@ConditionalOnClass`). UI components degrade via `ObjectProvider.ifAvailable()` without needing a flag.

---

## 2026-05-19 — Starter owns `attachmentObjectMapper`; Liquibase gated by subsystem flag

**Decision:** `AttachmentAutoConfiguration` defines `@Bean("attachmentObjectMapper") ObjectMapper` (with `FAIL_ON_UNKNOWN_PROPERTIES` disabled), `@ConditionalOnMissingBean(name = "attachmentObjectMapper")` for override. `AttachmentSnapshotService.objectMapper` is annotated `@Qualifier("attachmentObjectMapper")` so it does not collide with `userSettingsObjectMapper` / `auditObjectMapper` in a context that has all three.

**Why:** The starter previously consumed the host application's `userSettingsObjectMapper` — a marketplace-specific name — which broke contexts with multiple `ObjectMapper` beans (the audit starter introduced a second one, surfacing `NoUniqueBeanDefinitionException`). Owning a named mapper and explicit qualifier on every injection site eliminates the ambiguity without using `@Primary`.

**Rejected:** `@Primary` on either mapper (user preference recorded as durable feedback: always qualify, never `@Primary`). Pulling `JavaTimeModule` in — `jackson-datatype-jsr310` is not on the starter's classpath and the attachment JSON shapes do not require it.

---

## 2026-05-19 — Actor-centric public API; user-domain naming purged

**Decision:** Every public method that previously named a `userId` parameter (e.g. `AttachmentPort.softDeleteAll`, `restoreToSnapshot`, gallery upload callbacks) was renamed to `actorId`. The starter no longer references "user" in any contract method, log message, or DTO field.

**Why:** Symmetry with the audit-starter's actor-centric rename and the contract-level shift from `CurrentUserProvider` → `CurrentActorProvider`. "User" implied a marketplace-specific principal; "actor" is neutral and applies to bots, workflows, or service accounts that may upload or remove attachments in non-marketplace deployments.

**Migration:** Hard cutover — no aliases or wrapper methods. Marketplace-app call sites updated in the same commit. SPI `MediaChangeConsumer` already carried only `(entityType, entityId)` and required no change.

**Rejected:** Keeping `userId` aliases for backwards compatibility — there are no external consumers; aliases would persist the user-domain vocabulary forever.

---

## 2026-06-02 — Symmetry with audit-starter: package rename, i18n enum, port registration via @Component

**Decision:** Three structural changes made to align attachment-starter with audit-starter conventions:

1. **Package rename:** `org.ost.attachment.service` → `org.ost.attachment.services` (plural). Matches `org.ost.audit.services`.

2. **i18n enum rename:** `AttachmentMessages` → `AttachmentI18n`. Matches `AuditI18n` naming. All UI keys live in this enum; callers use `I18nService.get(AttachmentI18n.*)`.

3. **Port registration via `@Component`:** `DefaultAttachmentPort` is now a `@Component` class discovered by ComponentScan, not an explicit `@Bean` method in `AttachmentAutoConfiguration`. Minimizes `AutoConfiguration` to infrastructure-only concerns (Liquibase, ObjectMapper, cleanup scheduler).

**Why:** Reducing cognitive overhead when reading across starters — identical conventions allow pattern recognition. `AutoConfiguration` should be lean: only beans that require `@ConditionalOnMissingBean` or infrastructure setup belong there.

**Note:** `AttachmentGalleryPort` and `AttachmentGalleryPortImpl` were removed (2026-06-15) — all UI logic moved to marketplace-app; the port was unnecessary indirection.

---

## 2026-06-15 — Open: marketplace-app attachment UI imports starter internals directly

Six UI components in marketplace-app (`AttachmentGallery`, `AttachmentLightbox`, `AttachmentThumbnail`, `CardLightboxStrip`, `CardLightboxViewer`, `CardMediaLightbox`) directly import:
- `org.ost.attachment.entities.Attachment` — entity
- `org.ost.attachment.services.AttachmentService` / `AttachmentSnapshotService` — services
- `org.ost.attachment.util.MediaContentTypeUtil` — util

**Partially resolved:** `YoutubeUtil` moved to `platform-commons/attachment.util` (done). `MediaContentTypeUtil` still lives in the starter.

**Remaining fix:**
- Move `MediaContentTypeUtil` to `platform-commons` (`attachment.util`)
- Replace direct `Attachment` entity usage at UI call sites with DTOs (`AttachmentMediaSummaryDto`) from `platform.attachment.dto`
- Replace direct `AttachmentService` injection with calls through `AttachmentPort`

**Note:** `AttachmentGalleryPort` was removed (2026-06-15) — do not re-introduce it. Route through `AttachmentPort` instead.

---

## 2026-05-12 — Vaadin IFrame src patching via `Page.executeJs`

**Decision:** In `CardMediaLightbox`, iframe `src` is updated via `UI.getCurrent().getPage().executeJs(...)` in addition to `getElement().setAttribute(...)`.

**Why:** Vaadin's `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client after initial render — the property diff is not propagated. Direct DOM manipulation via JS is the only reliable way to blank or restore the YouTube embed URL. `setAttribute` is kept in sync so Vaadin's internal state stays consistent.

**Rejected:** Using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic `page.evaluate` in Playwright (both `iframe.src` and `iframe.getAttribute('src')` remained unchanged after the Vaadin call).
