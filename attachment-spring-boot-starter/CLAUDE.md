## attachment-spring-boot-starter

Auto-configures attachment storage, gallery UI, and snapshot history when the jar is on the classpath. Java package root: `org.ost.attachment`.

---

### Module surface

| Layer | Classes |
|---|---|
| Domain | `Attachment` entity, `AttachmentMediaChange` |
| Repositories | `AttachmentRepository` (`CrudRepository` + custom `JdbcClient` impl), `AttachmentSnapshotRepository` |
| Services | `AttachmentService`, `AttachmentSnapshotService`, `AttachmentCleanupService`, `S3StorageService`, `StorageService` interface |
| UI | `AttachmentGallery`, `AttachmentUploadButton`, `AttachmentThumbnail`, `AttachmentLightbox`, `CardMediaLightbox`, `CardLightboxDialog`, `CardLightboxStrip`, `CardLightboxViewer` |
| Ports/Hooks impl | `DefaultAttachmentPort`, `AttachmentGalleryPortImpl`, `AttachmentAuditHookImpl` |

`AttachmentAutoConfiguration` owns infrastructure-only beans: Liquibase changelog, named `ObjectMapper`, S3 client (`AttachmentS3Config`), cleanup scheduler. Ports/Hooks impls are `@Component`/`@SpringComponent` discovered by ComponentScan. `@EnableJdbcRepositories(basePackages = "org.ost.attachment.repository")` is mandatory.

---

### SPI map (all interfaces live in `platform-commons/attachment.spi`)

| Direction | Interface | Implemented in |
|---|---|---|
| marketplace → starter | `AttachmentPort` | starter (`DefaultAttachmentPort`) |
| marketplace → starter | `AttachmentGalleryPort` | starter (`AttachmentGalleryPortImpl`) |
| starter → marketplace | `AttachmentMediaChangeHook` | marketplace |
| starter → marketplace | `AttachmentAuditHook` | starter (`AttachmentAuditHookImpl`, delegates into the marketplace audit subsystem via `AuditPort`) |

→ Suffix semantics in @platform-commons/CLAUDE.md

---

### Rules

**Generic over `EntityType`.** Every public method takes `(EntityType entityType, Long entityId)` — never a bare `advertisementId`. The `attachment` and `attachment_snapshot` tables carry an `entity_type` column. Never hardcode `EntityType.ADVERTISEMENT` inside the starter; marketplace passes it at every call site.

**S3 folder layout:** canonical singular path is `<entityType.name().toLowerCase()>/<entityId>` (e.g. `advertisement/42`, `user/17`). The construction lives in `S3StorageService` — do not duplicate the string concat elsewhere.

**ObjectMapper:** the starter ships `@Bean("attachmentObjectMapper") ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES = false`, `@ConditionalOnMissingBean(name = "attachmentObjectMapper")`. Every internal injection site qualifies with `@Qualifier("attachmentObjectMapper")`. Never `@Primary`. Same convention applies to every starter — see `platform-commons/DECISIONS.md`.

**i18n:** all translation keys live in `AttachmentI18n implements TranslationKey`. Consumers call `I18nService.get(AttachmentI18n.*)`. Never raw `MessageSource`, never `msg(key, fallback)`.

**Actor vocabulary:** `actorId`, not `userId`, in every public method, log line, and DTO. The starter must remain usable in non-marketplace contexts where the principal is a bot, workflow, or service account.

**Read/Write descriptor split:** `AttachmentDescriptor` and `AttachmentSnapshotDescriptor` are namespace classes (private constructor) that implement `SqlEntityDescriptor` from `query-starter`. Each descriptor exposes `Read.PROJECTION` + `Read.SELECT_*` constants and `Write.*` `SqlWriteCommand` constants. Call sites are explicit: `AttachmentDescriptor.Read.PROJECTION` ↔ `AttachmentDescriptor.Write.SOFT_DELETE`. The descriptor itself is NOT a `RowMapper` — fetch the projection.

**Repository pattern:** `AttachmentRepository extends CrudRepository<Attachment, Long> + AttachmentRepositoryCustom`. `*CustomImpl` keeps PostgreSQL-specific queries (`ANY(:urls)`, `MAKE_INTERVAL`, `ROW_NUMBER()`) on `JdbcClient`. Never reintroduce hand-rolled INSERT / `findById` — `CrudRepository.save` / `.findById` already provide them.

**Vaadin `IFrame` src patching:** `IFrame.setSrc()` / `setProperty("src", …)` are silently ignored after initial render. Use `UI.getCurrent().getPage().executeJs(…)` plus `getElement().setAttribute("src", …)` together (the `setAttribute` keeps Vaadin's internal state in sync, the JS actually mutates the DOM). All video iframes must carry `sandbox="allow-scripts allow-same-origin allow-presentation allow-forms"` — minimum required for YouTube embed playback.

---

### Where to look first

- Attachment query SQL → `AttachmentDescriptor.Read` / `Write` namespace constants.
- New entity wants a gallery → call `AttachmentGalleryPort.build(EntityType, Long, …)` in the consumer; no starter change.
- Upload/delete not reflected in audit history → `AttachmentAuditHookImpl` delegates to `AuditPort`; the audit subsystem owns the snapshot.
- Storage not available in tests → swap the `StorageService` bean for an in-memory or no-op implementation (`@ConditionalOnMissingBean` allows override).
