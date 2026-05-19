# Architecture & Technical Decisions — platform-contracts

---

## Ongoing — Shared kernel: contracts, not implementations

**Decision:** `platform-contracts` contains only pure Java: DTOs, domain events, SPI interfaces, annotations, and conditional markers. No Spring Boot autoconfiguration, no Spring beans, no framework annotations beyond `@interface`.

**Why:** Every module depends on `platform-contracts`. If it pulled in Spring Boot or any framework, every module would inherit that transitive dependency — including `sql-engine` which is intentionally framework-free. Keeping contracts pure Java preserves the dependency hierarchy.

**Rejected:** Placing `@ConditionalOnAuditEnabled` or `@ConditionalOnStorageEnabled` in Spring config classes inside this module — confirmed that `@ConditionalOnProperty` requires Spring context, which contracts must not have.

---

## Ongoing — SPI pattern for cross-module extension

**Decision:** Cross-module extension points are defined as SPI interfaces in `platform-contracts` (e.g. `AttachmentGalleryExtension`, `AdvertisementHistoryExtension`, `UserActivityExtension`, `StorageService`, `AttachmentPort`, `MediaChangeConsumer`). Each starter provides its own implementation; `marketplace-app` calls them via `ObjectProvider.ifAvailable()`.

**Why:** Modules must not know about each other — only about contracts. The SPI pattern lets `attachment-spring-boot-starter` extend audit history without `audit-spring-boot-starter` importing attachment types.

---

## 2026-05-18 — Attachment lifecycle: SPI ports replace ApplicationEvents

**Decision:** Dropped the three advertisement-scoped events (`AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`, `AdvertisementMediaUpdatedEvent`) along with the `attachment.event` package. Cross-module attachment lifecycle is now carried by SPIs:

- **`AttachmentPort`** (domain → starter): `softDeleteAll(EntityType, Long, Long)`, `restoreToSnapshot(EntityType, Long, int, Long)`, `getMediaSummary(EntityType, Long)`. Symmetric with `AuditPort`. `NoOpAttachmentPort` activates when storage is disabled.
- **`MediaChangeConsumer`** (starter → domain): `onMediaChanged(EntityType, Long)`. The starter calls it via `ObjectProvider.ifAvailable()`; consumers query current state via `AttachmentPort.getMediaSummary(...)` and update their own denormalized columns.
- **`MediaSummary(displayUrl, contentType, count)`** — display-ready record returned by `getMediaSummary`; the starter resolves YouTube → thumbnail URL, generic embed → `null`, image/uploaded-video → original URL.

**Why:** Events were a one-way pipe with no return value, forcing the starter to denormalize derived fields (URLs, counts) into the event payload and the domain to listen and translate. The SPI surface is symmetric with `AuditPort`, gives the caller a direct return path, and keeps derived fields inside the starter where the truth lives. The `MediaChangeConsumer` SPI intentionally carries only `(entityType, entityId)` so the contract surface does not leak attachment internals.

**Rejected:** Keeping events alongside the SPI — splits the contract surface and makes "which path does the starter use" ambiguous.

---

## 2026-05-19 — User-domain knowledge purged from contracts; actor-centric naming

**Decision:** All user-domain types removed from `platform-contracts`; SPIs renamed so the audit/attachment surface speaks about "actors" (whoever performs the action) and "subjects" (whoever is observed), not "users".

- `Role` enum → moved to `marketplace-app` (`org.ost.marketplace.entities`). It was never used by either starter — only by domain code.
- `UserSettings` → moved to `marketplace-app`. Contracts no longer ship a settings record.
- `UserSnapshotState` → deleted from `audit.dto`. The starter does not parse user payloads; consumers (`marketplace-app`) read raw `SnapshotContent` via `AuditPort.getSnapshotContent` / `getPreviousSnapshotContent` and deserialize their own `UserSnapshot`.
- `AuditPort.getUserStateBefore(Long)` / `getUserStateAt(Long)` → deleted. Replaced by the generic pair `getSnapshotContent(Long, EntityType)` / `getPreviousSnapshotContent(Long, EntityType)`.
- `AuditUserProvider` / `CurrentUserProvider` → renamed to `CurrentActorProvider` (`getCurrentActorId(): Optional<Long>`). "Actor" is the audit-side concept; "user" leaked the marketplace domain.
- `UserActivityExtension` → renamed to `ActivityFeedExtension`. `AdvertisementHistoryExtension` → renamed to `MediaHistoryExtension`.
- `ActorSnapshot(displayName)` record added in `audit.dto` — display-ready actor info returned by SPI callbacks (replacing scattered `Long actorId` + secondary name resolution).
- Attachment public API: every `userId` parameter renamed to `actorId` (no domain semantics).

**Why:** "User" is a marketplace concept. Starters that talk about `userId` cannot be reused in a CRM, ticketing, or content system where the acting principal is an agent, robot, or workflow. The actor/subject vocabulary is generic; consumers map it onto their own domain.

**Rejected:** Keeping `UserSnapshotState` as a "typed convenience" — locks the contract surface to a specific subject shape (name/email/role) and forces every consumer through the same fields.

---

## 2026-05-19 — Activity decoration via SPI: `ActivityRowBinding` + `AuditUiExtension.buildProfileActivityPanel`

**Decision:** Profile activity panels (per-subject feeds) are now built through `AuditUiExtension.buildProfileActivityPanel(ProfileActivityParams)`. Consumers pass in a list of `ActivityRowBinding` — an SPI with `entityType()` + `decorate(ActivityItemDto): Component` — to attach per-row UI (e.g. "current state" badges, "restore" buttons) without the starter understanding the snapshot shape.

`SnapshotBinder<T>` (in `audit-spring-boot-starter`) is the canonical generic implementation: it deserializes `ActivityItemDto.snapshotData` into the consumer-provided `Class<T>`, checks a consumer-provided `Predicate<T>` for "is current", and optionally fires a consumer-provided `BiConsumer<Long, T>` for restore. Marketplace consumers (`SettingsOverlay`, `UserViewOverlayModeHandler`) build one `SnapshotBinder<UserSnapshot>` and/or `SnapshotBinder<UserSettings>` per panel.

**Why:** The previous pattern parsed snapshot JSON inside the starter to decide rendering — coupling the starter to specific user/settings shapes. With `ActivityRowBinding`, the starter only knows: "for this row's entityType, ask the binding what (if any) decoration to attach." The shape of the snapshot stays in the consumer. Mirrors `OverlayFormBinder<T>` already established in marketplace-app.

**Rejected:**
- Decorator wrapper around `ProfileActivityPanel` — adds an extra layer with no payoff; the SPI list is enough.
- Abstract `ActivityRowDecorator` requiring subclasses per shape — duplicates the Builder+Parameters pattern already used everywhere else (`Configurable<T, P>` per CLAUDE.md).

---

## 2026-05-18 — Audit/attachment contract symmetry cleanup

**Decision:** Reorganised cross-module SPIs so audit and attachment contracts now mirror each other exactly:

- **Audit-only SPIs** (`AuditActorNameResolver`, `AuditEntityExistenceChecker`, `ActivityItemFieldsProvider`, `UserActivityExtension`, `AdvertisementHistoryExtension`) moved from `core.spi/` to `audit.spi/`. They are not cross-cutting — only the audit subsystem consumes them.
- **`AuditPort`** moved from `audit.api/` to `audit.spi/` — symmetric with `attachment.spi.AttachmentPort`. `audit.api/` now holds only annotations and snapshot markers (`AuditableSnapshot`, `AuditedField`, `@ConditionalOnAuditEnabled`).
- **`CurrentUserProvider`** (new, in `core.spi/`) replaces both `audit.spi.AuditUserProvider` and `attachment.spi.AttachmentCurrentUserProvider` — both had the identical signature `Optional<Long> getCurrentUserId()`. "Current user" is an auth concern, not subsystem-specific; both starters now inject the same SPI via `ObjectProvider<CurrentUserProvider>`. Marketplace-app collapses two implementations (`AuditUserProviderImpl` + `AttachmentUserProviderConfig`) into one `CurrentUserProviderImpl` in `services/auth/`.
- **`AttachmentEntityDisplayNameResolver`** deleted — was dead code with no starter consumer; `EntityDisplayNameResolver` (`core.spi/`, with `supports(EntityType) + resolveDisplayName(EntityType, SnapshotPayload)`) is the single canonical form.

**Why:** Incremental growth left asymmetric layouts (two ports under different package families, two identical user-provider SPIs, an audit-only SPI living in `core.spi/`). A reader skimming `core.spi/` could not tell which interfaces were cross-cutting vs audit-only. The cleaned-up layout makes ownership obvious: `core.spi/` = used by ≥2 subsystems, `audit.spi/` = audit only, `attachment.spi/` = attachment only.

**Rejected:** Renaming both user providers to `*CurrentUserProvider` and keeping them as separate interfaces — keeps duplicated implementation in marketplace-app while fixing only the cosmetic asymmetry.

---

## 2026-05-16 — Package restructure: core / audit / attachment

**Decision:** Replaced flat scattered packages (`events/`, `events/dto/`, `events/spi/`, `events/model/`, `audit/`, `config/`, `dto/`, `entities/`, `i18n/`, `ui/rules/`, `spi/storage/`) with three semantic groups (final layout after the 2026-05-18 symmetry cleanup):

```
core.config    — CleanupProperties, UserSettings
core.i18n      — I18nService, InstantFormatter, LocaleProvider, TranslationKey
core.model     — ActionType, ChangeEntry, EntityType, Role
core.spi       — CurrentUserProvider, EntityDisplayNameResolver
core.ui        — Configurable, ComponentBuilder, Initialization, Provider

audit.api      — AuditableSnapshot, AuditedField, @ConditionalOnAuditEnabled
audit.dto      — ActivityItemDto, EntityHistoryDto, SnapshotContent,
                 SnapshotPayload, UserSnapshotState
audit.spi      — AuditPort, AuditActorNameResolver, AuditEntityExistenceChecker,
                 ActivityItemFieldsProvider, UserActivityExtension,
                 AdvertisementHistoryExtension, AuditUiExtension

attachment.spi     — AttachmentPort, AttachmentGalleryExtension,
                     MediaChangeConsumer, MediaSummary
attachment.storage — StorageService, @ConditionalOnStorageEnabled
```

The `attachment.event` package was removed on 2026-05-18 — see the SPI-replaces-events entry above.

`PaginationDefaults` moved to `marketplace-app` (only used there).

**Why:** The old structure mixed audit, attachment, and shared types in `events.*` with no clear ownership. The new structure makes it immediately obvious which package belongs to which subsystem and where SPIs live.
