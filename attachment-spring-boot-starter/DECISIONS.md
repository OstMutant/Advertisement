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
JdbcClient persistence — no Vaadin UI. Internal package is `org.ost.attachment.services`
(plural), matching `audit-spring-boot-starter`'s convention. The starter carries no i18n
infrastructure of its own — attachment i18n keys live in the single consolidated
`org.ost.marketplace.services.i18n.I18nKey` enum, per `marketplace-app/CLAUDE.md`'s "single
consolidated enum" rule. `DefaultAttachmentPort` is discovered via `@Component` ComponentScan,
not an explicit `@Bean` in `AttachmentAutoConfiguration`. `AttachmentGalleryPort` does not
exist — all UI logic lives in marketplace-app; do not re-introduce it.

---

## ADR-002: S3 storage merged into this module; storage-s3-starter deleted
**Status:** Accepted

**Context:** `storage-s3-spring-boot-starter` was a separate module with a mandatory one-way
dependency on attachment. No realistic scenario exists where storage runs without the attachment
module or vice versa.

**Decision:** `S3StorageService` merged into `attachment-spring-boot-starter`, implementing the
`StorageService` interface. `storage-s3-spring-boot-starter` deleted. (Corrected 2026-07-16 —
originally also claimed a `NoOpStorageService` was merged in; no such class exists anywhere in the
repo, only `S3StorageService`/`StorageService` — likely a planned-but-never-written no-op fallback,
not something that actually shipped.)

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
- Some related performance optimizations remain deferred, tracked in the backlog until their
  triggers fire.
- Rejected: keeping the event-based flow alongside the SPI — the starter speaks SPI and only SPI.

---

## ADR-004: StorageService internalized; attachment.enabled property removed
**Status:** Accepted

**Context:** `StorageService` lived in `platform-commons` but had no cross-module consumer.
`@ConditionalOnAttachmentEnabled` and the `attachment.enabled` property added unnecessary
configuration overhead.

**Decision:** `StorageService` moved to `org.ost.attachment.services` (corrected 2026-07-27 — an
earlier `org.ost.attachment.storage` package this ADR originally named does not exist; verified
directly against the actual source tree). The `attachment.enabled`
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

## ADR-008: IFrame sandbox attribute on all video embeds
**Status:** Accepted

**Context:** Without `sandbox`, the embedded iframe has unrestricted browser capabilities.

**Decision:** All `IFrame` components for video embedding carry a `sandbox` attribute, set via
`LightboxUtil.applyEmbedIframeAttributes(iframe, isYoutube)` /
`LightboxUtil.embedSandbox(isYoutube)` (`org.ost.marketplace.ui.views.utils.LightboxUtil`), which
centralizes the flag string: `allow-scripts allow-same-origin allow-presentation`. YouTube gets
`allow-same-origin` in addition, since its player needs Cache Storage to bootstrap; non-YouTube
embeds omit it.

**Consequences:** Minimum flags required for YouTube and generic embed playback, centralized in
one utility rather than set inline at each call site.

---

## ADR-009: Vaadin IFrame src patching via Page.executeJs
**Status:** Accepted

**Context:** `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client
after initial render — the property diff is not propagated to the DOM.

**Decision:** In `CardLightboxViewer`, iframe `src` is updated via
`UI.getCurrent().getPage().executeJs(...)` in addition to `getElement().setAttribute(...)`.

**Consequences:** `setAttribute` is kept in sync so Vaadin's internal state stays consistent.
Rejected: using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic
`page.evaluate` in Playwright.

---

## ADR-014: `AttachmentVideoUtil` extracted from `AttachmentService`; video/embed classification consolidated onto `AttachmentMediaContentType`

**Status:** Accepted

**Context:** A repo-wide SOLID/DRY review found `AttachmentService` mixed five concerns (gallery queries, upload/video-ingestion,
commit/restore orchestration, snapshot delegation, media-summary DTO shaping) in one class, and
"is this a video/embed" was checked three independent, non-identical ways: `AttachmentService`'s
own `CT_YOUTUBE`/`CT_EMBED` constants + private `isVideo()`, the platform-commons
`AttachmentMediaContentType.isEmbedded()` authority, and `AttachmentSnapshotService.filename()`'s
inline `YoutubeUtil.extractId(url) != null` check.

**Decision:** Extracted `AttachmentVideoUtil` — `public final class`,
`@NoArgsConstructor(access = PRIVATE)`, all-static methods, package `org.ost.attachment.util`
(mirrors `platform-commons`' `attachment.util` package housing `YoutubeUtil`) — holding
`VideoDescriptor`, `resolveVideoDescriptor(url)`, `resolveDisplayUrl(url, contentType)`,
`embedFilename(url)`, `validateEmbedUrl(url)`, and the `ALLOWED_EMBED_HOSTS` allowlist, called
directly by `AttachmentService` with no injection (zero dependencies, zero mutable state, same
shape as `YoutubeUtil`, which it delegates to — not a Spring bean, since no real call site ever
needs DI substitution). `AttachmentService` keeps only upload/commit/delete orchestration and
calls the utility statically for video-URL resolution. Removed `AttachmentService`'s local
`CT_YOUTUBE`/`CT_EMBED` constants and private `isVideo()` entirely — every remaining call site
(`commitTempUploadsQuiet`, `discardTempUploads`, `AttachmentVideoUtil.resolveVideoDescriptor`/
`resolveDisplayUrl`) now uses `AttachmentMediaContentType.YOUTUBE.getValue()`/`.EMBED.getValue()`
for the string literals and `AttachmentMediaContentType.isEmbedded(contentType)` for the boolean
check — one shared authority instead of three.

`AttachmentSnapshotService.filename()`'s `YoutubeUtil.extractId(url) != null` check was
deliberately left untouched, not retargeted onto a content-type lookup: it classifies by URL
pattern, not stored content type, which is exactly why it still resolves a real YouTube filename
for historical snapshot URLs whose underlying `attachment` row has since been purged past
retention by `AttachmentCleanupService` (a content-type lookup requires the row to still exist, a
URL-pattern check does not). It already calls the same canonical `YoutubeUtil.extractId` used by
`AttachmentVideoUtil`, so there was no duplicated constant or predicate to remove — only a
different, legitimately narrower classification for a different purpose (historical-display
fallback naming, not upload/storage routing): for a live YouTube row, the URL-pattern branch and
the (unused) DB-filename branch always compute the identical string; for a live EMBED row, the
method already falls through correctly to the DB-resolved filename on the very next branch.

**Consequences:**
- No public method signature on `AttachmentService`/`AttachmentPort` changed — this is an internal
  reorganization, no UI-visible behavior change, no Playwright coverage needed.
