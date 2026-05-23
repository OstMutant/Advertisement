# Architecture & Technical Decisions — attachment-spring-boot-starter

---

## 2026-05-19 — Attachment descriptors migrated to Read/Write namespace pattern

**Decision:** `AttachmentDescriptor` and `AttachmentSnapshotDescriptor` are `final` namespace classes (private constructor) that implement the new `SqlEntityDescriptor` marker interface from `sql-engine`. Each descriptor splits its body into two symmetric inner classes:

- `Read` — `PROJECTION` (a `SqlEntityProjection<T>` with inline `mapRow`), `SELECT_*` SQL constants, read-side param factories. `AttachmentSnapshotDescriptor.Read` additionally hosts the `extractUrls(ResultSet)` row helper.
- `Write` — `SqlWriteCommand` constants and write-side param factories. `DELETE_BY_URLS` moved from "read" mix to `Write` (it is a delete command).

`AttachmentDescriptor` no longer extends `SqlEntityProjection<Attachment>` — the projection is owned via `Read.PROJECTION`. Callers obtain it explicitly (`AttachmentDescriptor.Read.PROJECTION`) instead of relying on `new AttachmentDescriptor()` doubling as projection + RowMapper.

**Why:** The previous "flat descriptor + tiny `Write` bucket for column names" arrangement intermixed UPDATE/DELETE/SELECT SQL and param factories with no visual separation. The new layout makes the SQL boundary side explicit at the call site (`AttachmentDescriptor.Read.PROJECTION` ↔ `AttachmentDescriptor.Write.SOFT_DELETE`) and gives a single grep target (`SqlEntityDescriptor`) for finding all dual-side descriptors in the project.

**Rejected:** Generic marker (`SqlEntityDescriptor<T>`) — does not fit `AttachmentSnapshotDescriptor` which has no full-row projection (queries return `String[]` and JSON text directly). Non-generic marker keeps both descriptors uniform.

---

## 2026-05-19 — AttachmentRepository migrated to CrudRepository + Custom split

**Decision:** Aligned attachment-starter with the project-wide repository policy (see `CLAUDE.md` → Repository pattern):
- `Attachment` entity gained `@Table("attachment")`, `@Id`, `@CreatedDate`.
- `AttachmentRepository` is now an interface extending `CrudRepository<Attachment, Long> + AttachmentRepositoryCustom`.
- `AttachmentRepositoryCustomImpl` keeps the bespoke soft-delete / restore / cleanup / media-stats queries against `AttachmentDescriptor`.
- Removed `INSERT` SqlWriteCommand, `insertParams`, `FIND_BY_ID_SQL`, `findByIdParams` from `AttachmentDescriptor` — superseded by `save()` / `findById()`.
- `AttachmentAutoConfiguration` declares `@EnableJdbcRepositories(basePackages = "org.ost.attachment.repository")` so the starter is self-contained; the marketplace `@SpringBootApplication` scan does not cover `org.ost.attachment.*`.

**Why:** Eliminates a duplicated INSERT/SELECT-by-id path that Spring Data JDBC already provides, and brings attachment-starter in line with the `User` / `Advertisement` repository structure so future contributors meet one pattern across the codebase.

**Rejected:** Migrating bespoke soft-delete / restore / cleanup / media-stats queries to derived repository methods — those queries depend on PostgreSQL-specific features (`ANY(:urls)`, `MAKE_INTERVAL`, `ROW_NUMBER()` etc.) and stay on `JdbcClient`.

---

## 2026-05-07 — Attachment logic extracted from marketplace-app

**Decision:** All attachment/photo domain logic (entity, repository, UI components `AttachmentGallery`, `CardMediaLightbox`) lives in this module, not in `marketplace-app`.

**Why:** Enables two independent deployments — the attachment module can be used without the advertisement app. The starter is auto-configured via Spring Boot's autoconfiguration mechanism.

**Rejected:** Keeping UI components in `marketplace-app` — would couple the UI to the app module and prevent reuse.

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

**Migration:** Hard cutover — no compatibility shims, no fallback `EntityType.ADVERTISEMENT` defaults. DB and MinIO were wiped because this is dev-only state. Marketplace-app wires `EntityType.ADVERTISEMENT` explicitly at every call site; `MediaChangeHookImpl` (in marketplace-app) reacts to changes and updates the advertisement table's denormalized media columns.

**Deferred:**
- `EntityRef(EntityType, Long)` record — would collapse the repeated `(entityType, entityId)` pair argument; deferred as cosmetic.
- `EntityType.storageKey()` method — currently the S3 folder uses `name().toLowerCase()`; a typed method would let entities customize their storage segment if ever needed.
- `AttachmentGalleryExtension`/`AdvertisementHistoryExtension` naming — the latter still carries "Advertisement" in its name but is generic over `EntityType`; rename deferred until a second consumer exists.

**Rejected:** Keeping the event-based flow alongside the SPI — splits the contract surface and forces consumers to choose. The starter speaks SPI and only SPI.

---

## 2026-05-19 — Storage SPI internalized

**Decision:** `StorageService` moved out of `platform-contracts` into the starter (package `org.ost.attachment.storage`). S3-specific config (`storage.s3.endpoint`, `region`, `access-key`, `secret-key`, `bucket`, `public-url`) stays under `storage.s3.*`.

**Why:** No module outside `attachment-spring-boot-starter` referenced `StorageService` — it lived in contracts as noise. `platform-contracts` is reserved for types crossed by ≥2 modules.

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

## 2026-05-12 — Vaadin IFrame src patching via `Page.executeJs`

**Decision:** In `CardMediaLightbox`, iframe `src` is updated via `UI.getCurrent().getPage().executeJs(...)` in addition to `getElement().setAttribute(...)`.

**Why:** Vaadin's `IFrame.setSrc()` / `setProperty("src", ...)` is silently ignored by the client after initial render — the property diff is not propagated. Direct DOM manipulation via JS is the only reliable way to blank or restore the YouTube embed URL. `setAttribute` is kept in sync so Vaadin's internal state stays consistent.

**Rejected:** Using only `setSrc()` or `setProperty()` — confirmed non-functional via diagnostic `page.evaluate` in Playwright (both `iframe.src` and `iframe.getAttribute('src')` remained unchanged after the Vaadin call).
