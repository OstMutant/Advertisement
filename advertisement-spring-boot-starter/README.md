# advertisement-spring-boot-starter

Auto-configured Advertisement domain for the Advertisement Platform.

## What it provides

- Advertisement CRUD with ownership checks and dynamic filter/sort
- HTML description sanitization (OWASP HTML Sanitizer) with a defense-in-depth length check
- Category assignment via `TaxonPort` — optional, degrades gracefully if the taxon starter is absent
- Author name/email and media summary enriched at read time via `UserPort.findByIds()` /
  `AttachmentPort.getMediaSummaries()` bulk lookups — optional, degrade gracefully if those
  starters are absent
- **SPI implementation:** `AdvertisementPort` (called by marketplace-app)

## Key classes

| Class | Role |
|---|---|
| `AdvertisementPortImpl` | Entry point — implements `AdvertisementPort`, thin delegation to `AdvertisementService` |
| `AdvertisementService` | Create, update, delete, ownership validation, HTML sanitization; wires category, author, and media enrichment through `ComponentFactory<TaxonPort>`/`ComponentFactory<UserPort>`/`ComponentFactory<AttachmentPort>` |
| `AdvertisementRepository` | Persists and queries `advertisement`; supports dynamic filter/sort |

## Dependencies

- `platform-commons` — `AdvertisementPort` SPI and DTOs
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- `taxon-spring-boot-starter` — optional, via `ComponentFactory<TaxonPort>` for category assignment
- Spring Boot, Spring JDBC, Liquibase, OWASP HTML Sanitizer, Jsoup
