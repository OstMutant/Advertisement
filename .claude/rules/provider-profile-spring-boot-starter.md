---
paths: ["provider-profile-spring-boot-starter/**"]
---

## provider-profile-spring-boot-starter

Auto-configures the Provider Profile domain. Active whenever the jar is on the classpath.

Java package root: `org.ost.provider`

---

## What it owns

- `ProviderProfile` entity + `ProviderProfileRepository` — CRUD and filter/sort queries
- `ProviderProfileService` — create/update, delete, sanitizes `about` via the shared
  `html-sanitizer-lib` module's `HtmlSanitizer.sanitize()`, enforces the `kind == SUPPORT`
  requires-privileged-actor rule, resolves query-time category
  filters via `TaxonPort.findEntityIdsWithAnyTaxon()`. Does not write category assignments (see
  Key constraints) and does not enrich display fields — `getFiltered()`/`findById()`/
  `findByActorId()` return raw (unenriched) `ProviderProfileDto`s; category/city/actor display
  enrichment happens afterward via `marketplace-orchestrator`'s `ProviderProfileDisplayEnrichmentService`.
- `ProviderProfilePortImpl` — implements `ProviderProfilePort`; thin delegation to
  `ProviderProfileService`

**Autoconfiguration entry point:** `ProviderProfileAutoConfiguration`

---

## Schema

Liquibase changelog: `db/provider-profile-changelog/provider-profile-changelog-master.xml`
Tables: `provider_profile`

---

## Key constraints

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
- `ProviderProfileService.save(dto, targetUserId, actingUserId, actingUserIsPrivileged)` takes two
  distinct identity parameters: `targetUserId` is whose profile this is (the row's `actor_id` on
  create), `actingUserId` is who is performing the save (audit purposes only) — they differ
  whenever an admin/moderator edits another user's profile via the account overlay's Users-grid
  entry path. `actingUserIsPrivileged` gates `kind == SUPPORT` — the one authorization-shaped rule
  this starter enforces server-side, a deliberate exception to the "authorization lives only in
  marketplace-app" convention. See `.claude/nav/adr-index.md`.
- `findOwnerIds()` blocks user purge while a profile exists (mirrors `AdvertisementPort`'s
  `created_by` protection) — no `clearActorReferences()`, since there are no nullable
  actor-reference columns on this table to null.
- `marketplace-orchestrator`'s `ProviderProfileSaveService` — not this starter's own service —
  writes category assignments via `TaxonAssignmentWriteService` and captures audit via `AuditPort`,
  mirroring the Advertisement domain's own `AdvertisementSaveService` save path exactly. This
  starter's `ProviderProfileService` only resolves query-time category filters (read-only) via
  `TaxonPort`.
