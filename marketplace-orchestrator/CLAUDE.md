## marketplace-orchestrator

Application/BFF composition layer between `marketplace-app` (the UI adapter) and the domain
starters. Owns cross-domain use-case orchestration that a domain starter must not perform itself
and that `marketplace-app` must not perform directly against multiple domain Ports.

Java package root: `org.ost.orchestrator`

---

## What it owns

Two sibling packages, mirroring `marketplace-app`'s own `services`/`spi` split: composition/
lookup services live in one flat `org.ost.orchestrator.services` (no per-domain sub-packages);
`*Hook` SPI implementations live in `org.ost.orchestrator.spi`, listed separately below.

- `TaxonLookupService` / `ActorLookupService` — shared read-only lookups (`TaxonPort`/`UserPort`),
  reused by every domain's own display-enrichment step. Return raw `TaxonDto`/`UserDto` data —
  domain-specific field mapping stays in the calling class.
- `TaxonAssignmentWriteService` — shared `TaxonPort.replaceAssignments()` write, reused by every
  domain's save/delete path.
- `AttachmentSnapshotReaderService` / `AttachmentSoftDeleteService` — shared read-only snapshot
  lookup and soft-delete-cascade write against `AttachmentPort`.
- `AdvertisementDisplayEnrichmentService` — assembles `AdvertisementInfoDto`'s display-only fields
  (category/city names, author name/email, media summary) from Taxon/User/Attachment.
- `AdvertisementSaveService` — the atomic save/delete transaction for an advertisement: write +
  category/city assignment + attachment gallery commit + audit capture, all in one
  `TransactionTemplate`-bounded unit.
- `AdvertisementAuditEnrichService` — resolves raw category/city taxon ids and `AdKind` into
  display labels for audit timeline/activity diffs, and merges in attachment-domain media
  changes. The 2 i18n-touching lookups (locale, `AdKind` label) go through the
  `CurrentLocaleHook`/`UiLabelHook` forwarder pair (see "Hook implementations" below) instead
  of a direct `LocaleProvider`/`I18nService` dependency.
- `ProviderProfileDisplayEnrichmentService` — the ProviderProfile equivalent of the Advertisement
  enrichment service (category/city/actor only — no attachments).
- `UserDeleteService` — cascades a user's own dependent data (advertisements, provider profile)
  before deleting the account itself.
- `AdvertisementReadService` — wraps `ComponentFactory<AdvertisementPort>`'s query methods
  (`findById`/`getFiltered`/`count`) so marketplace-app never holds a direct `AdvertisementPort`.
- `TaxonCatalogService` — wraps `ComponentFactory<TaxonPort>`'s catalog-management methods
  (`getAllByType`/`listAllByType`/`getUsageCounts`/`create`/`update`/`findById`/`getTranslations`)
  — distinct from `TaxonLookupService`, which stays narrowly scoped to entity-assignment lookups.
- `AttachmentMediaService` — wraps `ComponentFactory<AttachmentPort>` +
  `ComponentFactory<AttachmentAuditPort>` for the full gallery lifecycle (upload/commit/delete/
  restore) plus audit-diff media state; reuses `AttachmentSnapshotReaderService` internally instead
  of re-wrapping its call. `AttachmentSoftDeleteService`'s `softDeleteAll` is used directly by
  `AdvertisementSaveService`'s delete cascade, not through this class.
- `AuditQueryService` — wraps `ComponentFactory<AuditPort>`'s read-side methods (`getLastSnapshot`/
  `getEntityActivity`/`getSnapshotContent`/`getTimelinePage`/`countTimeline`).
- `UserProfileService` — mandatory direct `UserPort`/`UserAccountPort`/`UserPreferencesPort` fields
  (matching `UserDeleteService`'s existing `UserAccountPort` precedent) for profile/settings
  read+write (`findById`/`save`/`loadSettings`/`saveSettings`).
- `EntityExistenceService` — a named, documented exception to the ≤2-port rule (see below): holds
  `AdvertisementPort`/`UserPort`/`TaxonPort`/`ProviderProfilePort` directly for pure
  per-`EntityType` existence-check routing (`findExisting`).
- `UserActorNameService` — actor-name-resolution collaborator `AuditDomainHookImpl` (in `spi/`,
  below) delegates to; stays in `services/` since it doesn't itself implement an SPI interface.

`org.ost.orchestrator.spi` — the `*Hook` implementations (see "Hook implementations" below):
`AuditDomainHookImpl`, `CurrentActorHookImpl`, `ActivityEnrichHookImpl` (`AuditActivityFieldsHook`
and its four per-domain implementations were removed entirely — see `platform-commons/DECISIONS.md`
ADR-029's second refinement: every implementation had converged to a one-line delegation with zero
domain-specific logic, and the interface's only real caller was already `marketplace-app`'s own
`AuditTimelineRowRenderer`, so the whole per-domain Hook pattern collapsed into one field-name-to-
label mapping directly in that class). Also owns the forwarder SPIs these implementations call
through to reach a UI-shell resource — `UiLabelHook` (`translateActorDeletedSuffix(String)`, the
one remaining audit-starter translation case, since its real caller has no i18n awareness of its
own; also carries `labelFor(AdKind)`, `markDeleted(String)` (wraps a name in strikethrough markup
for a soft-deleted taxon), and `noMediaPlaceholder()` (the "no attachment" placeholder text) for
`AdvertisementAuditEnrichService` — all four wrap the same `I18nService`/produce presentation
output only marketplace-app should own, so they share one interface rather than four),
`SessionActorHook` (current actor ID), and `CurrentLocaleHook` (current request locale, used by
`AdvertisementAuditEnrichService`). None of these forwarder SPIs live in `platform-commons`: all
are called only by this module's own Hook implementations or services, never a starter, and this
module is a mandatory, never-optional dependency of `marketplace-app` — the
*Port/*Hook-must-live-in-platform-commons rule exists specifically for starter optionality, which
doesn't apply here. `marketplace-app`'s `UiLabelHookImpl`/`SessionActorHookImpl`/
`CurrentLocaleHookImpl` (in its own `spi/` package) implement these three interfaces, a legal
import since `marketplace-app` already depends on `marketplace-orchestrator`.

**Autoconfiguration entry point:** `OrchestratorAutoConfiguration` (`@ComponentScan` over
`org.ost.orchestrator`, since this module is a mandatory, non-optional dependency of
`marketplace-app` rather than a pluggable starter — same `@AutoConfiguration` +
`AutoConfiguration.imports` mechanism every starter uses, chosen for consistency rather than
inventing a second wiring approach for one module).

---

## Key constraints

- **`pom.xml` declares all 6 starter `<dependency>` blocks** (`audit`/`attachment`/`user`/
  `advertisement` at `compile` scope, `taxon`/`provider-profile` at `runtime` scope, preserving
  their existing optional-module semantics unchanged) — this is the one module in the app that
  pulls every starter JAR onto the runtime classpath, since `marketplace-app` no longer declares
  them itself (`marketplace-app/pom.xml` depends only on `platform-commons` +
  `marketplace-orchestrator`, so `Module Dependencies`/`Bounded Contexts` show the same converged
  shape — see `platform-commons/DECISIONS.md` for the ADR reversing the earlier
  "orchestrator must depend only on platform-commons" rule). This is a Maven-classpath-assembly
  concern only, not a license to touch starter internals — **no class in this module imports a
  starter's concrete class, ever.** Every domain access still goes through `ComponentFactory<XPort>`
  against a `platform-commons` interface, exactly the mechanism `marketplace-app` itself used to
  use directly — enforced at the code level (not the Maven level) by
  `ArchitectureRulesTest.marketplace_must_not_import_starter_internals`, which bans importing any
  starter's `util`/`services`/`repository` package regardless of what's on the classpath.
- **≤2 domain `*Port` types per class, via `ComponentFactory` only.** Enforced by
  `ArchitectureRulesTest.orchestrator_classes_depend_on_at_most_two_domain_ports`. Counts only
  `ComponentFactory<XPort>`-wrapped fields — a direct, mandatory `*Port` field (e.g.
  `UserAccountPort` in `UserDeleteService`, injected because `user-spring-boot-starter` is a
  compile-scope, non-optional dependency of the final app) is a different shape from the optional
  cross-domain composition fan-out this rule guards against, and is not counted. When a use case
  genuinely needs more than 2 optional domain ports, extract the shared parts into a `services.*`
  collaborator (as `TaxonLookupService`/`ActorLookupService`/etc. already do) rather than holding
  every `ComponentFactory<XPort>` directly in one class. **One granted exception:**
  `EntityExistenceService` holds all 4 of `AdvertisementPort`/`UserPort`/`TaxonPort`/
  `ProviderProfilePort` directly, allow-listed by name in the ArchUnit test — its `findExisting()`
  is pure per-`EntityType` dispatch with no cross-port composition to extract, so splitting it into
  four single-port classes plus a coordinator would be ceremony with no cohesion benefit. This is a
  case-by-case exception evaluated on its own merit, not a loosened threshold — a new class that
  exceeds 2 ports still fails the test unless it earns the same explicit allowlist entry.
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
- **Hook implementations that only need domain-port access live here, in their own `spi/`
  package parallel to `services/`; ones that need UI-shell resources (translations, locale, the
  HTTP session) go through a forwarder SPI instead of pulling `marketplace-app` in as a
  dependency.** `AuditDomainHookImpl`, `CurrentActorHookImpl`, and `ActivityEnrichHookImpl` live in
  `org.ost.orchestrator.spi` — a `*Hook` that dispatches to exactly one of several ports per call,
  based on `EntityType`, is per-branch pure delegation, not the simultaneous-composition shape the
  ≤2-port rule targets, so this module is still the natural home even though the package is
  separate from `services/`. Three forwarder SPIs, also in `org.ost.orchestrator.spi` (not
  platform-commons — see above), make this possible without an illegal
  `marketplace-orchestrator -> marketplace-app` dependency (the compile direction only ever runs
  the other way): `UiLabelHook` (`translateActorDeletedSuffix`, plus `labelFor(AdKind)`,
  `markDeleted(String)`, and `noMediaPlaceholder()` — all four wrap the same `I18nService` or
  produce presentation output only marketplace-app should own, so one interface covers all four
  rather than a separate single-method Hook per lookup), `SessionActorHook` (read the current
  actor ID from the HTTP session), and `CurrentLocaleHook` (current request locale) — each
  implemented by a thin `*Impl` that stays in `marketplace-app`
  (`UiLabelHookImpl`/`SessionActorHookImpl`/`CurrentLocaleHookImpl`), wrapping
  `I18nService`/`AuthContextService`/`LocaleProvider`. `ActivityEnrichHookImpl`'s real collaborator,
  `AdvertisementAuditEnrichService`, does real category/city/`AdKind` diff-label resolution, not a
  single-value lookup — but only the UI-shell-touching calls (locale, `AdKind`/deleted-taxon/
  no-media label text) go through `CurrentLocaleHook`/`UiLabelHook`, so it splits the same way the
  narrower Hooks do: the pure-data majority (change-merging, field resolution) stays a
  `marketplace-orchestrator` service (see `marketplace-orchestrator/DECISIONS.md` ADR-005,
  reversing ADR-004's original call that this one couldn't follow the same pattern).
  `AuditActivityFieldsHook` — the fourth original Hook interface, previously implemented by four
  per-domain classes here — was removed entirely: every implementation had converged to a one-line
  delegation with zero domain-specific logic left, and its only real caller was already
  `marketplace-app`'s own `AuditTimelineRowRenderer`, so the whole field-name-to-label mapping now
  lives directly in that class instead of crossing the module boundary at all.
- **UI presence-guards also route through the orchestrator.** For literal BFF purity — zero direct
  `*Port` reference of any kind in marketplace-app — even a presence-only check (e.g. "is the
  attachment starter on the classpath, to decide whether to render a gallery button") goes through
  an orchestrator service's own `isAvailable()` method rather than a local `ComponentFactory<XPort>`
  field. `AdvertisementReadService`/`TaxonCatalogService`/`AttachmentMediaService`/`AuditQueryService`
  each expose `isAvailable()` for exactly this.
- **One remaining direct `*Port` reference in marketplace-app, by design:** `AccessEvaluator`
  (`services/security/`) keeps a direct `UserAuthorizationPort` field. Authorization/ownership
  checks (`isAdmin`/`isModerator`/`isOwner`) are the security boundary itself, not a domain
  read-model this module composes — the same category as a `*Hook`, called on nearly every render
  across the whole UI, where adding an orchestrator round-trip would cost real latency for no
  architectural benefit. `user-spring-boot-starter` is non-optional, so this carries no
  decoupling risk either. Every other `User*Port` usage in marketplace-app (search/filter,
  registration, locale, settings, pagination) routes through `UserProfileService`/
  `ActorLookupService` — `AccessEvaluator` is the one deliberate, named exception.
