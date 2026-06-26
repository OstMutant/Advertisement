# Architecture & Technical Decisions — attachment-spring-boot-starter

---

## ADR-001: Attachment domain logic extracted from marketplace-app
**Status:** Accepted

**Context:** Attachment/photo domain logic (entity, repository, services) lived in marketplace-app,
preventing independent deployment or reuse without the advertisement app.

**Decision:** All attachment domain logic lives in `attachment-spring-boot-starter`.
Auto-configured via Spring Boot's autoconfiguration mechanism.

**Consequences:** UI components (`AttachmentGallery`, `CardMediaLightbox`, `AttachmentLightbox`,
`AttachmentThumbnail`, `CardLightboxStrip`, `CardLightboxViewer`) moved to `marketplace-app`
as part of UI monolith consolidation (2026-06-13). The starter owns only domain logic and
JdbcClient persistence — no Vaadin UI.

---

## ADR-002: S3 storage merged into this module; storage-s3-starter deleted
**Status:** Accepted

**Context:** `storage-s3-spring-boot-starter` was a separate module with a mandatory one-way
dependency on attachment. No realistic scenario exists where storage runs without the attachment
module or vice versa.

**Decision:** `S3StorageService` and `NoOpStorageService` merged into `attachment-spring-boot-starter`.
`storage-s3-spring-boot-starter` deleted.

**Consequences:** Rejected: keeping the separate module — theoretical benefit ("S3 without attachment
logic") has no concrete use case.

---

## ADR-003: Decoupled from advertisement domain — generic over EntityType
**Status:** Accepted

**Context:** The original starter compiled only against an advertisement-shaped world (event types,
field names, S3 path constants). Adding photo galleries to USER or any future entity required
either renaming everything or branching by name.

**Decision:** Every public API takes `(EntityType entityType, Long entityId)` instead of a
hard-coded advertisement id. The `attachment` and `attachment_snapshot` tables grew an `entity_type`
column. Domain Spring events replaced by SPI calls. S3 folder layout:
`entityType.name().toLowerCase() + "/" + entityId` (e.g. `advertisement/42`, `user/17`).

**Consequences:**
- ✅ `EntityRef(EntityType, Long)` record implemented in `platform-commons/core.model`.
- → [improvement-003-deferred-performance](../features/issues/improvement-003-deferred-performance.md) (items G, H)
- Rejected: keeping the event-based flow alongside the SPI — the starter speaks SPI and only SPI.

---

## ADR-004: StorageService internalized; attachment.enabled property removed
**Status:** Accepted

**Context:** `StorageService` lived in `platform-commons` but had no cross-module consumer.
`@ConditionalOnAttachmentEnabled` and the `attachment.enabled` property added unnecessary
configuration overhead.

**Decision:** `StorageService` moved to `org.ost.attachment.storage`. The `attachment.enabled`
property and `@ConditionalOnAttachmentEnabled` annotation removed. Jar presence is the only
toggle. UI components degrade via `ObjectProvider.ifAvailable()`.

**Consequences:** S3-specific config stays under `storage.s3.*`. Rejected: conditional flag —
no scenario exists where the jar is present but the subsystem should be disabled.

---

## ADR-005: Starter owns `attachmentObjectMapper` with @Qualifier
**Status:** Accepted

**Context:** The starter previously consumed `userSettingsObjectMapper` — a marketplace-specific
name — which broke contexts with multiple `ObjectMapper` beans.

**Decision:** `AttachmentAutoConfiguration` defines `@Bean("attachmentObjectMapper") ObjectMapper`
with `FAIL_ON_UNKNOWN_PROPERTIES` disabled and `@ConditionalOnMissingBean(name = "attachmentObjectMapper")`.
All injection sites annotated `@Qualifier("attachmentObjectMapper")`.

**Consequences:** Rejected: `@Primary` on either mapper (project rule — always qualify, never `@Primary`).

---

## ADR-006: Actor-centric public API; user-domain naming purged
**Status:** Accepted

**Context:** Methods named `userId` implied a marketplace-specific principal. "Actor" is neutral
and applies to bots, workflows, or service accounts.

**Decision:** Every `userId` parameter renamed to `actorId` across all public methods and contracts.

**Consequences:** Hard cutover — no aliases. Marketplace call sites updated in the same commit.

---

## ADR-007: Symmetry with audit-starter — package rename, i18n enum, port via @Component
**Status:** Accepted

**Context:** Reducing cognitive overhead when reading across starters requires identical conventions.
`AutoConfiguration` should be lean: only beans requiring `@ConditionalOnMissingBean` or
infrastructure setup.

**Decision:** Three structural changes:
1. Package rename: `org.ost.attachment.service` → `org.ost.attachment.services` (plural).
2. i18n enum rename: `AttachmentMessages` → `AttachmentI18n`.
3. Port registration via `@Component` — `DefaultAttachmentPort` discovered by ComponentScan,
   not an explicit `@Bean` in `AttachmentAutoConfiguration`.

**Consequences:** `AttachmentGalleryPort` and `AttachmentGalleryPortImpl` removed (2026-06-15) —
all UI logic moved to marketplace-app; the port was unnecessary indirection.
Do not re-introduce `AttachmentGalleryPort`.

---

## ADR-008: IFrame sandbox attribute on all video embeds
**Status:** Accepted

**Context:** Without `sandbox`, the embedded iframe has unrestricted browser capabilities.

**Decision:** All `IFrame` components for video embedding carry:
`sandbox="allow-scripts allow-same-origin allow-presentation allow-forms"`.

**Consequences:** Minimum flags required for YouTube and generic embed playback.

---

## ADR-009: Vaadin IFrame src patching via Page.executeJs
**Status:** Accepted

**Context:** `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client
after initial render — the property diff is not propagated to the DOM.

**Decision:** In `CardMediaLightbox`, iframe `src` is updated via
`UI.getCurrent().getPage().executeJs(...)` in addition to `getElement().setAttribute(...)`.

**Consequences:** `setAttribute` is kept in sync so Vaadin's internal state stays consistent.
Rejected: using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic
`page.evaluate` in Playwright.

---

## ADR-010: Open — marketplace-app attachment UI imports starter internals directly
**Status:** Accepted (tracking open work)

→ [improvement-001-attachment-ui-boundary-violation](../features/issues/improvement-001-attachment-ui-boundary-violation.md)
