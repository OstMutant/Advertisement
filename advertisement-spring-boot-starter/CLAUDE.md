## advertisement-spring-boot-starter

Auto-configures the Advertisement domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.advertisement`

---

## What it owns

- `Advertisement` entity + `AdvertisementRepository` — CRUD and filter/sort queries
- `AdvertisementService` — create, update, delete, ownership checks; sanitizes HTML description via OWASP HTML Sanitizer; wires category assignments through `TaxonPort` via `ComponentFactory`
- `AdvertisementPortImpl` — implements `AdvertisementPort`; thin delegation to `AdvertisementService`
- `MediaChangeHookImpl` — implements `AttachmentMediaChangeHook`; notifies service when media changes

**Autoconfiguration entry point:** `AdvertisementAutoConfiguration`

---

## Schema

Liquibase changelog: `db/advertisement-changelog/advertisement-changelog-master.xml`  
Tables: `advertisement`

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here.
- `AdvertisementPort` lives in `platform-commons`.
- `@EnableJdbcRepositories(basePackages = "org.ost.advertisement")` declared in `AdvertisementAutoConfiguration`.
- `AdvertisementPortImpl` is pure delegation — no business logic inside the port.
- `AdvertisementService` depends on `ComponentFactory<TaxonPort>` — category assignment is optional (guard via `taxonPortFactory.ifAvailable(...)`).
- HTML description is sanitized using OWASP HTML Sanitizer (`Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)`). Never trust raw HTML from UI.
- Description visible-text length is enforced server-side via a Jsoup-based check in
  `AdvertisementService.sanitizeHtml()` (`Jsoup.parse(html).text().length()`), in addition to
  the raw-size `@Size` cap on `AdvertisementSaveDto.description` — see
  `marketplace-app/DECISIONS.md` ADR-024.
