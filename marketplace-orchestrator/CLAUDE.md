## marketplace-orchestrator

Application/BFF composition layer between `marketplace-app` (the UI adapter) and the domain
starters. Owns cross-domain use-case orchestration that a domain starter must not perform itself
and that `marketplace-app` must not perform directly against multiple domain Ports.

Java package root: `org.ost.orchestrator`

---

## What it owns

- `shared.TaxonLookupService` / `shared.ActorLookupService` — shared read-only lookups
  (`TaxonPort`/`UserPort`), reused by every domain's own display-enrichment step. Return raw
  `TaxonDto`/`UserDto` data — domain-specific field mapping stays in the calling class.
- `shared.TaxonAssignmentWriteService` — shared `TaxonPort.replaceAssignments()` write, reused by
  every domain's save/delete path.
- `shared.AttachmentSnapshotReaderService` / `shared.AttachmentSoftDeleteService` — shared
  read-only snapshot lookup and soft-delete-cascade write against `AttachmentPort`.
- `advertisement.enrich.AdvertisementDisplayEnrichmentService` — assembles
  `AdvertisementInfoDto`'s display-only fields (category/city names, author name/email, media
  summary) from Taxon/User/Attachment.
- `advertisement.save.AdvertisementSaveService` — the atomic save/delete transaction for an
  advertisement: write + category/city assignment + attachment gallery commit + audit capture,
  all in one `TransactionTemplate`-bounded unit.
- `providerprofile.enrich.ProviderProfileDisplayEnrichmentService` — the ProviderProfile
  equivalent of the Advertisement enrichment service (category/city/actor only — no attachments).
- `user.delete.UserDeleteService` — cascades a user's own dependent data (advertisements, provider
  profile) before deleting the account itself.

**Autoconfiguration entry point:** `OrchestratorAutoConfiguration` (`@ComponentScan` over
`org.ost.orchestrator`, since this module is a mandatory, non-optional dependency of
`marketplace-app` rather than a pluggable starter — same `@AutoConfiguration` +
`AutoConfiguration.imports` mechanism every starter uses, chosen for consistency rather than
inventing a second wiring approach for one module).

---

## Key constraints

- **Never depends on a starter jar directly.** `pom.xml` depends only on `platform-commons` +
  `spring-boot-starter` + `lombok` — enforced by a Maven Enforcer `bannedDependencies` rule
  (mirrors every starter's own `enforce-no-starter-to-starter-deps` rule). Every domain access goes
  through `ComponentFactory<XPort>`, exactly the mechanism `marketplace-app` itself already uses —
  this preserves today's optional-module behavior unchanged (`taxon`/`provider-profile` stay
  `<scope>runtime</scope>` in `marketplace-app/pom.xml`; this module has no compile-time visibility
  into any starter's concrete classes to begin with, so it cannot make an optional starter
  mandatory).
- **≤2 domain `*Port` types per class, via `ComponentFactory` only.** Enforced by
  `ArchitectureRulesTest.orchestrator_classes_depend_on_at_most_two_domain_ports`. Counts only
  `ComponentFactory<XPort>`-wrapped fields — a direct, mandatory `*Port` field (e.g.
  `UserAccountPort` in `UserDeleteService`, injected because `user-spring-boot-starter` is a
  compile-scope, non-optional dependency of the final app) is a different shape from the optional
  cross-domain composition fan-out this rule guards against, and is not counted. When a use case
  genuinely needs more than 2 optional domain ports, extract the shared parts into a `shared.*`
  collaborator (as `TaxonLookupService`/`ActorLookupService`/etc. already do) rather than holding
  every `ComponentFactory<XPort>` directly in one class.
- **No persistence access.** Enforced by `ArchitectureRulesTest.orchestrator_has_no_persistence_access`
  — zero imports of `JdbcClient`/`*Repository`/`*CrudRepository` anywhere in this module. Mirrors
  the `*PortImpl`/`*HookImpl` pure-delegation discipline one layer down — this module composes
  results from domain Ports only, never touches SQL directly.
- **Not every cross-domain call moves here.** Query-time filter resolution (e.g.
  `AdvertisementService.resolveTaxonIdFilter()`, used to translate a category-id filter into an
  advertisement-id set before `SqlFilterBuilder` builds the `WHERE` clause) stays inside the owning
  starter — it's part of executing the query itself, not display composition. `TaxonService`/
  `UserPreferencesService`/`UserService` calling `AuditPort.capture*()` (cross-cutting event
  reporting) and `UserService.cleanup()` calling `AdvertisementPort`/`ProviderProfilePort.findOwnerIds()`
  (narrow, scheduled-job-scoped referential-integrity cooperation) also stay in their starters —
  neither is the "assemble a read-model from several domains" pattern this module exists for.
- **`*Hook` implementations never live here.** Hooks (`AuditDomainHookImpl`, `*ActivityFieldsHookImpl`,
  etc.) stay in `marketplace-app` by convention regardless of how many domain ports they touch — a
  `*Hook` that dispatches to exactly one of several ports per call, based on `EntityType`, is
  per-branch pure delegation, not the simultaneous-composition shape the ≤2-port rule targets.
- **UI presence-guards are not orchestration.** A UI class wrapping an optional port in
  `ifAvailable()`/`findIfAvailable()` purely to decide whether to render a feature (e.g. gating an
  attachment-gallery button or a history button) stays exactly where it is — moving that check into
  this module would add a service round-trip for what is today a zero-cost local `Optional` check.
