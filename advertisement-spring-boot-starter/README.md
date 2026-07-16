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
- `audit-spring-boot-starter` — optional Maven dependency (`<optional>true</optional>`), for audit
  write integration
- `attachment-spring-boot-starter` — optional Maven dependency (`<optional>true</optional>`), via
  `ComponentFactory<AttachmentPort>` for media-summary enrichment
- `taxon-spring-boot-starter` — **not a Maven dependency at all** (corrected 2026-07-16 — this
  `pom.xml` has no dependency on it, optional or otherwise). Category assignment goes entirely
  through `platform-commons`' `TaxonPort` SPI via `ComponentFactory<TaxonPort>` — a real runtime
  decoupling with no build-time coupling to the taxon starter whatsoever.
- Spring Boot, Spring JDBC, Liquibase, OWASP HTML Sanitizer, Jsoup
