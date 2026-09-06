# marketplace-orchestrator

The application/BFF composition layer between the two UI-shell adapters (`marketplace-app`'s
Vaadin UI, `marketplace-rest-api`'s external REST API) and the domain starters. Owns cross-domain
use-case orchestration a single starter must not perform itself, and that neither adapter is
allowed to perform directly against multiple domain `*Port`s.

## What it provides

- Composition/lookup services (`org.ost.orchestrator.services`) — one flat package, no per-domain
  sub-packages — for every cross-domain use case: advertisement/provider-profile save+audit+
  attachment-gallery transactions, taxon catalog management, user profile/settings, authorization
  rule composition, sitemap generation, API-key management, and shared taxon/actor/attachment
  lookups reused across domains.
- `*Hook` SPI implementations (`org.ost.orchestrator.spi`) that only need domain-port access —
  ones that also need a UI-shell resource (translations, current locale, session state) go through
  a small forwarder SPI declared here and implemented by `marketplace-app` instead, keeping this
  module free of any Vaadin/UI-shell dependency.
- **No SPI/Port implementation of its own** — this module composes results from the domain
  starters' own `*Port`s, it never implements one.

## Key classes

| Class | Role |
|---|---|
| `AdvertisementSaveService`/`ProviderProfileSaveService` | The atomic save/delete transaction per domain — write + taxon assignment + audit capture (+ attachment gallery, advertisement only), one `TransactionTemplate`-bounded unit each. |
| `AuthorizationService` | The single source of truth for ownership/role rules (`canOperate`/`isPrivileged`/`require*`) — both `AccessEvaluator` (`marketplace-app`) and this module's own save/delete services call through it, so a REST caller and a Vaadin user get identical authorization behavior. |
| `TaxonLookupService`/`ActorLookupService`/`TaxonAssignmentWriteService` | Shared read/write collaborators every domain's save path reuses, instead of each domain re-implementing the same `TaxonPort`/`UserPort` calls. |
| `EntityExistenceService` | The one class allowed to hold more than two domain `*Port`s directly (all four) — pure per-`EntityType` existence-check dispatch with no cross-port composition to extract. |
| `ApiKeyManagementService` | Wraps `ComponentFactory<ApiKeyPort>` so `marketplace-rest-api` never holds a `*Port` directly — same shape as every other `*ReadService`/`*CatalogService` here. |

## Dependencies

- All six domain starters (`audit`/`attachment`/`user`/`advertisement` at compile scope,
  `taxon`/`provider-profile` at runtime scope) — the one module that pulls every starter jar onto
  the app's runtime classpath, since `marketplace-app` no longer declares them itself. Every access
  still goes through `ComponentFactory<XPort>` against a `platform-commons` interface, never a
  starter's concrete class — this is a Maven-classpath-assembly concern only, enforced at the code
  level by ArchUnit (`marketplace_must_not_import_starter_internals`).
- No direct `JdbcClient`/`*Repository` access of any kind — this module composes domain Ports only,
  enforced by `orchestrator_has_no_persistence_access`.
- No Vaadin dependency — the forwarder-SPI pattern above exists specifically to keep this module a
  legal dependency of both `marketplace-app` and `marketplace-rest-api` without pulling either
  adapter's own concerns in.
