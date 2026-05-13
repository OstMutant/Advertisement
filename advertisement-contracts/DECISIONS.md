# Architecture & Technical Decisions — advertisement-contracts

---

## Ongoing — Shared kernel: contracts, not implementations

**Decision:** `advertisement-contracts` contains only pure Java: DTOs, domain events, SPI interfaces, annotations, and conditional markers. No Spring Boot autoconfiguration, no Spring beans, no framework annotations beyond `@interface`.

**Why:** Every module depends on `advertisement-contracts`. If it pulled in Spring Boot or any framework, every module would inherit that transitive dependency — including `sql-engine` which is intentionally framework-free. Keeping contracts pure Java preserves the dependency hierarchy.

**Rejected:** Placing `@ConditionalOnAuditEnabled` or `@ConditionalOnStorageEnabled` in Spring config classes inside this module — confirmed that `@ConditionalOnProperty` requires Spring context, which contracts must not have.

---

## Ongoing — SPI pattern for cross-module extension

**Decision:** Cross-module extension points are defined as SPI interfaces in `advertisement-contracts` (e.g. `AdvertisementGalleryExtension`, `AdvertisementHistoryExtension`, `UserActivityExtension`, `StorageService`). Each starter provides its own implementation; `advertisement-app` calls them via `ObjectProvider.ifAvailable()`.

**Why:** Modules must not know about each other — only about contracts. The SPI pattern lets `attachment-spring-boot-starter` extend audit history without `audit-spring-boot-starter` importing attachment types.

---

## Ongoing — Domain events as records

**Decision:** Domain events (`AdvertisementCreatedEvent`, `AdvertisementUpdatedEvent`, etc.) are Java records published via Spring's `ApplicationEventPublisher`. Listeners live in the modules that react to them (e.g. `AttachmentEventListener` in `attachment-spring-boot-starter`).

**Why:** Records are immutable by default, require no boilerplate, and make event payloads explicit. `ApplicationEventPublisher` decouples the publisher from the listener without requiring a message broker for in-process events.
