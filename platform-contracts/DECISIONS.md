# Architecture & Technical Decisions — platform-contracts

---

## Ongoing — Shared kernel: contracts, not implementations

**Decision:** `platform-contracts` contains only pure Java: DTOs, domain events, SPI interfaces, annotations, and conditional markers. No Spring Boot autoconfiguration, no Spring beans, no framework annotations beyond `@interface`.

**Why:** Every module depends on `platform-contracts`. If it pulled in Spring Boot or any framework, every module would inherit that transitive dependency — including `sql-engine` which is intentionally framework-free. Keeping contracts pure Java preserves the dependency hierarchy.

**Rejected:** Placing `@ConditionalOnAuditEnabled` or `@ConditionalOnStorageEnabled` in Spring config classes inside this module — confirmed that `@ConditionalOnProperty` requires Spring context, which contracts must not have.

---

## Ongoing — SPI pattern for cross-module extension

**Decision:** Cross-module extension points are defined as SPI interfaces in `platform-contracts` (e.g. `AdvertisementGalleryExtension`, `AdvertisementHistoryExtension`, `UserActivityExtension`, `StorageService`). Each starter provides its own implementation; `marketplace-app` calls them via `ObjectProvider.ifAvailable()`.

**Why:** Modules must not know about each other — only about contracts. The SPI pattern lets `attachment-spring-boot-starter` extend audit history without `audit-spring-boot-starter` importing attachment types.

---

## Ongoing — Domain events as records

**Decision:** Domain events (`AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`, `AdvertisementMediaUpdatedEvent`) are Java records published via Spring's `ApplicationEventPublisher`. Listeners live in the modules that react to them (e.g. `AttachmentEventListener` in `attachment-spring-boot-starter`). Events live in `attachment.event` since only the attachment module reacts to them.

**Why:** Records are immutable by default, require no boilerplate, and make event payloads explicit. `ApplicationEventPublisher` decouples the publisher from the listener without requiring a message broker for in-process events.

**Note:** `AdvertisementCreatedEvent` and `AdvertisementUpdatedEvent` were removed — unused in all modules.

---

## 2026-05-16 — Package restructure: core / audit / attachment

**Decision:** Replaced flat scattered packages (`events/`, `events/dto/`, `events/spi/`, `events/model/`, `audit/`, `config/`, `dto/`, `entities/`, `i18n/`, `ui/rules/`, `spi/storage/`) with three semantic groups:

```
core.config    — CleanupProperties, UserSettings
core.i18n      — I18nService, InstantFormatter, LocaleProvider, TranslationKey
core.model     — ActionType, ChangeEntry, EntityType, Role
core.spi       — AuditActorNameResolver, AuditEntityExistenceChecker,
                 UserActivityExtension, AdvertisementHistoryExtension
core.ui        — Configurable, ComponentBuilder, Initialization, Provider

audit.api      — AuditPort, AuditableSnapshot, AuditedField, @ConditionalOnAuditEnabled
audit.dto      — ActivityItemDto, AdvertisementHistoryDto, SnapshotContent, UserSnapshotState
audit.spi      — AuditUserProvider, AuditUiExtension

attachment.event   — AdvertisementDeletedEvent, AdvertisementRestoredEvent,
                     AdvertisementMediaUpdatedEvent
attachment.spi     — AdvertisementGalleryExtension, AttachmentCurrentUserProvider,
                     AttachmentEntityDisplayNameResolver
attachment.storage — StorageService, @ConditionalOnStorageEnabled
```

`PaginationDefaults` moved to `marketplace-app` (only used there).

**Why:** The old structure mixed audit, attachment, and shared types in `events.*` with no clear ownership. The new structure makes it immediately obvious which package belongs to which subsystem and where SPIs live.
