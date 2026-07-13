# advertisement-spring-boot-starter

Auto-configured Advertisement domain for the Advertisement Platform.

## What it provides

- Advertisement CRUD with ownership checks and dynamic filter/sort
- HTML description sanitization (OWASP HTML Sanitizer) with a defense-in-depth length check
- Category assignment via `TaxonPort` — optional, degrades gracefully if the taxon starter is absent
- **SPI implementations:** `AdvertisementPort` (called by marketplace-app), `AttachmentMediaChangeHook`
  (called back by the attachment starter when media changes)

## Key classes

| Class | Role |
|---|---|
| `AdvertisementPortImpl` | Entry point — implements `AdvertisementPort`, thin delegation to `AdvertisementService` |
| `AdvertisementService` | Create, update, delete, ownership validation, HTML sanitization; wires category assignments through `ComponentFactory<TaxonPort>` |
| `AdvertisementRepository` | Persists and queries `advertisement`; supports dynamic filter/sort |
| `MediaChangeHookImpl` | Implements `AttachmentMediaChangeHook`; notifies `AdvertisementService` when media changes for an advertisement |

## Dependencies

- `platform-commons` — `AdvertisementPort` SPI and DTOs
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- `taxon-spring-boot-starter` — optional, via `ComponentFactory<TaxonPort>` for category assignment
- Spring Boot, Spring JDBC, Liquibase, OWASP HTML Sanitizer, Jsoup
