## provider-profile-spring-boot-starter

Auto-configures the Provider Profile domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.provider`

---

## What it owns

- `ProviderProfile` entity + `ProviderProfileRepository` — CRUD and filter/sort queries
- `ProviderProfileService` — create/update, delete, sanitizes `about` via OWASP HTML Sanitizer,
  enforces the `kind == SUPPORT` requires-privileged-actor rule, wires category assignments
  directly via `TaxonPort.replaceAssignments()`
- `ProviderProfileEnrichmentService` — category/city/actor enrichment on read, via
  `ComponentFactory<TaxonPort>`/`ComponentFactory<UserPort>`
- `ProviderProfilePortImpl` — implements `ProviderProfilePort`; thin delegation to
  `ProviderProfileService`

**Autoconfiguration entry point:** `ProviderProfileAutoConfiguration`

---

## Schema

Liquibase changelog: `db/provider-profile-changelog/provider-profile-changelog-master.xml`
Tables: `provider_profile`

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here. This module ships backend-only as of its first batch
  (improvement-124 Batch 124-B) — no UI surfaces it yet.
- `ProviderProfilePort` lives in `platform-commons`.
- `@EnableJdbcRepositories(basePackages = "org.ost.provider.repository")` declared in
  `ProviderProfileAutoConfiguration`.
- `ProviderProfilePortImpl` is pure delegation — no business logic inside the port.
- `provider_profile.kind` is `NOT NULL` — the row is created **lazily**, only on first "become a
  provider" save, never eagerly at registration (unlike `advertisement`, there is no "every actor
  gets one" concept). `provider_profile.actor_id` has a unique index — at most one profile per
  actor.
- `provider_profile.city_taxon_id` is a **plain column**, not a `taxon_assignment` row — a
  provider has exactly one city, so a scalar column is the simpler, correct shape. Only
  `categoryIds` (many-to-many) goes through `TaxonPort`.
- `delete()` is a **real `DELETE`**, not a soft-delete — no `deleted_at`/`deleted_by` columns, no
  "restore" concept for provider status.
- `ProviderProfileService.save()` enforces `kind == SUPPORT` requires an `actingUserIsPrivileged`
  boolean the caller (marketplace-app) computes and passes in — the one authorization-shaped rule
  this starter enforces server-side, a deliberate exception to the "authorization lives only in
  marketplace-app" convention. See `platform-commons/DECISIONS.md` ADR-027.
- `findOwnerIds()` blocks user purge while a profile exists (mirrors `AdvertisementPort`'s
  `created_by` protection) — no `clearActorReferences()`, since there are no nullable
  actor-reference columns on this table to null.
- This starter's own service — not a marketplace-app orchestration service — writes category
  assignments directly via `TaxonPort.replaceAssignments()`, unlike `AdvertisementService` which
  pushes that write to marketplace-app's `AdvertisementSaveService`. Deliberate: this batch has no
  marketplace-app "SaveService" yet (planned for improvement-124 Batch 124-C, alongside the actual
  `AuditPort.record()` audit-write call).
- No `audit.spi` implementations of its own yet — audit-side wiring (the
  `ProviderProfileActivityFieldsHookImpl` triad other domains have) is deferred to Batch 124-C,
  since no code writes `PROVIDER_PROFILE` audit rows yet in this batch.
