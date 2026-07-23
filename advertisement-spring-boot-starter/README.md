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

- `platform-commons` — `AdvertisementPort` SPI and DTOs, plus the `TaxonPort`/`UserPort`/
  `AttachmentPort`/`AuditPort` SPI interfaces `AdvertisementService` wires through
  `ComponentFactory<T>`
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- **No Maven dependency on any sibling starter** (`audit-`/`attachment-`/`taxon-spring-boot-starter`)
  — confirmed 2026-07-16, this `pom.xml` used to declare `audit-`/`attachment-spring-boot-starter`
  as `<optional>true</optional>` dependencies, but zero Java source in this module ever imported
  from either (`org.ost.audit.*`/`org.ost.attachment.*`); removed as vestigial cruft alongside
  improvement-031 (Maven Enforcer's `bannedDependencies` rule now makes a real starter→starter
  dependency a build failure, not just a code-review catch). All optional-port wiring (category
  assignment, author enrichment, media-summary enrichment, audit writes) goes entirely through
  `platform-commons`' SPI types via `ComponentFactory<T>` — genuine runtime decoupling with zero
  build-time coupling to any other starter.
- Spring Boot, Spring JDBC, Liquibase, OWASP HTML Sanitizer, Jsoup
