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
  `AttachmentPort` SPI interfaces `AdvertisementService` wires through `ComponentFactory<T>`.
  `AdvertisementService` has no `ComponentFactory<AuditPort>` field — audit writes for
  advertisement saves are orchestrated by marketplace-app's own `AdvertisementSaveService`, not
  this starter
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- **No Maven dependency on any sibling starter** (`audit-`/`attachment-`/`taxon-spring-boot-starter`)
  — zero Java source in this module imports from any of them
  (`org.ost.audit.*`/`org.ost.attachment.*`). Maven Enforcer's `bannedDependencies` rule makes a
  real starter→starter dependency a build failure, not just a code-review catch. All optional-port
  wiring inside this starter (category assignment, author enrichment, media-summary enrichment)
  goes entirely through
  `platform-commons`' SPI types via `ComponentFactory<T>` — genuine runtime decoupling with zero
  build-time coupling to any other starter. Audit writes for advertisement saves are not wired
  here at all — that orchestration lives in marketplace-app's `AdvertisementSaveService`, which
  calls `AuditPort` directly.
- Spring Boot, Spring JDBC, Liquibase, OWASP HTML Sanitizer, Jsoup
