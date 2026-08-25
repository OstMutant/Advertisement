---
paths: ["provider-profile-spring-boot-starter/**"]
---

## provider-profile-spring-boot-starter

Auto-configures the Provider Profile domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.provider`

---

## What it owns

- `ProviderProfile` entity + `ProviderProfileRepository` — CRUD and filter/sort queries
- `ProviderProfileService` — create/update, delete, sanitizes `about` via OWASP HTML Sanitizer,
  enforces the `kind == SUPPORT` requires-privileged-actor rule, wires category assignments
  directly via `TaxonPort.replaceAssignments()`, resolves query-time category filters via
  `TaxonPort.findEntityIdsWithAnyTaxon()`. Does not enrich display fields — `getFiltered()`/
  `findById()`/`findByActorId()` return raw (unenriched) `ProviderProfileDto`s; category/city/actor
  display enrichment happens afterward via `marketplace-orchestrator`'s
  `ProviderProfileDisplayEnrichmentService`.
- `ProviderProfilePortImpl` — implements `ProviderProfilePort`; thin delegation to
  `ProviderProfileService`

**Autoconfiguration entry point:** `ProviderProfileAutoConfiguration`

---

## Schema

Liquibase changelog: `db/provider-profile-changelog/provider-profile-changelog-master.xml`
Tables: `provider_profile`

---

## Key constraints

- This module ships backend-only — no UI surfaces it yet.
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
  marketplace-app" convention. See `.claude/nav/adr-index.md`.
- `findOwnerIds()` blocks user purge while a profile exists (mirrors `AdvertisementPort`'s
  `created_by` protection) — no `clearActorReferences()`, since there are no nullable
  actor-reference columns on this table to null.
- This starter's own service — not `marketplace-orchestrator` — writes category assignments
  directly via `TaxonPort.replaceAssignments()`, unlike the Advertisement domain, whose save path
  (`marketplace-orchestrator`'s `AdvertisementSaveService`) makes that same write instead.
  Deliberate: there is no orchestrator save path for this domain yet (planned for a future batch,
  alongside the actual `AuditPort.record()` audit-write call) — building one now, ahead of any real
  UI or audit wiring, would be new feature work, not the mechanical extraction the decision
  recorded for `marketplace-orchestrator` scoped — see `.claude/nav/adr-index.md` for that decision's
  "what does NOT move" table.
- No `audit.spi` implementations of its own yet — audit-side wiring (the
  `ProviderProfileActivityFieldsHookImpl` triad other domains have) is deferred to a future batch,
  since no code writes `PROVIDER_PROFILE` audit rows yet.
