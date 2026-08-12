# marketplace-orchestrator — Decisions

---

## ADR-001: Extract a dedicated Application/BFF module instead of moving orchestration into marketplace-app
**Status:** Accepted

**Context:** Two domain starters (`advertisement-spring-boot-starter`,
`provider-profile-spring-boot-starter`) each grew a service that reaches into 2-3 sibling domains'
Ports (`TaxonPort`/`UserPort`/`AttachmentPort`) purely to assemble display data for its own Port's
return DTO — a starter orchestrating other domains, which the root `CLAUDE.md`'s module boundaries
never intended. The pattern had already spread once (copy-pasted from Advertisement to
ProviderProfile while building the Portfolio/Profile feature), and `marketplace-app` itself already
held equivalent cross-domain orchestration (`AdvertisementSaveService`, `UserDeleteService`) with no
architectural boundary distinguishing "UI adapter" concerns from "cross-domain use case" concerns.

A narrower fix (move the two enrichment services into `marketplace-app/services/*`, mirroring the
existing `AdvertisementAuditEnrichService` precedent) was considered and rejected: it would have
left `marketplace-app` permanently responsible for both UI rendering and cross-domain composition,
with no room to add a REST adapter later without re-extracting orchestration out of a
Vaadin-entangled monolith.

**Decision:** A new module, `marketplace-orchestrator`, sits between `marketplace-app` (kept as a
thin UI/application-shell adapter) and the domain starters. It owns application-level use-case
composition; domain starters keep only their own bounded-context logic; `marketplace-app` calls
orchestrator services instead of composing multiple domain Ports directly. Every real cross-domain
call site in the repository was inventoried before deciding what moves — see the full discovery
(Phase 0) and target-architecture (Phase 1) writeup preserved in
`backlog/completed/issues/improvement-136-marketplace-orchestrator-extraction.md` for the complete
evidence trail, including the classes that were deliberately *not* moved and why.

**Consequences:** Root `CLAUDE.md`'s "Architecture Guidelines" now describes three layers, not two.
Two new ArchUnit rules (`orchestrator_classes_depend_on_at_most_two_domain_ports`,
`orchestrator_has_no_persistence_access`) keep this module from becoming the next
`AdvertisementEnrichmentService`-shaped god-service one layer up. `AdvertisementPort`/
`ProviderProfilePort` dropped their `Locale` parameter (see `platform-commons/DECISIONS.md`) since
the only reason either port needed it was the enrichment logic that now lives here.

**Trigger to revisit:** If a future REST adapter is actually built, confirm the orchestrator's
existing application-facing services (e.g. `AdvertisementSaveService.save()`'s `commitGallery`
callback parameter) are genuinely adapter-agnostic before reusing them as-is — flagged as an open
question at extraction time, not verified against a real second caller yet.

---

## ADR-002: `AdvertisementSaveService`'s cascade-cleanup-on-delete folds into the same transaction, not a separate step
**Status:** Accepted

**Context:** `AdvertisementService.delete()` (the starter) used to clean up the advertisement's own
taxon assignments and attachments before soft-deleting the row, all inside one
`@Transactional` method that simply joined whatever outer transaction `AdvertisementSaveService.delete()`
(then in `marketplace-app`) had already started (Spring's default `REQUIRED` propagation, one shared
`DataSource`/transaction manager). Moving this cleanup into the orchestrator raised a question: does
folding it into `AdvertisementSaveService.delete()` fragment that transaction, or conflict with the
"orchestrator never touches persistence" rule?

**Decision:** Neither. The transaction boundary was always owned by `AdvertisementSaveService`
(via `TransactionTemplate`), not by the starter's own `@Transactional` — the starter's cleanup calls
were always just Port calls happening inside that same transaction. Moving them into
`AdvertisementSaveService.delete()` directly (via the shared `TaxonAssignmentWriteService`/
`AttachmentSoftDeleteService` collaborators) is not a new transaction-boundary concern, just
relocating which class issues the same Port calls. `AdvertisementService.delete()` (the starter)
shrank to a single `repository.softDelete(...)` call — no longer needs its own `@Transactional` at
all, since a single repository call always participates in whatever transaction its caller already
holds.

**Consequences:** `AdvertisementService` (starter) no longer depends on `AttachmentPort` at all —
its only remaining cross-domain dependency is `TaxonPort`, used solely for query-time category-filter
resolution (a different, deliberately-not-moved concern — see `marketplace-orchestrator/CLAUDE.md`).

---

## ADR-003: `marketplace-app` becomes a true BFF client — zero direct domain `*Port` access, one named exception
**Status:** Accepted

**Context:** ADR-001 built this module as a composition layer, but its own guiding spec (preserved
verbatim in `backlog/completed/issues/improvement-136-marketplace-orchestrator-extraction.md`)
contained an internal contradiction never caught during that extraction: the target diagram showed
`Vaadin UI → marketplace-orchestrator → domain starters` with no direct UI-to-starter arrow at all,
but the accompanying rule only banned `marketplace-app` from composing *multiple* domain Ports for
one use case — implicitly allowing direct single-Port access, which is what actually got built. 25
classes in `marketplace-app` ended up holding a direct `ComponentFactory<XPort>` (or, for the
non-optional `user-spring-boot-starter`, a mandatory direct `*Port` field).

**Decision:** Route every remaining direct domain-Port reference in `marketplace-app` through the
orchestrator instead, including the 4 presence-only `ifAvailable()` gates that were previously
carved out as "not orchestration" (that carve-out is now removed — see
`marketplace-orchestrator/CLAUDE.md`'s history). Six services in the flattened
`org.ost.orchestrator.services` package absorb this: `AdvertisementReadService`, `TaxonCatalogService`,
`AttachmentMediaService`, `AuditQueryService` (new), `ActorLookupService` (extended), and
`UserProfileService` (new, mandatory direct `UserPort`/`UserAccountPort`/`UserPreferencesPort`
fields, matching `UserDeleteService`'s existing precedent since `user-spring-boot-starter` is never
optional). A seventh, `EntityExistenceService`, is a deliberate, named exception to the module's
own ≤2-port rule — it holds all 4 of `AdvertisementPort`/`UserPort`/`TaxonPort`/`ProviderProfilePort`
directly for `AuditDomainHookImpl`'s per-`EntityType` existence-check routing, allow-listed by name
in `ArchitectureRulesTest` rather than by loosening the threshold, since its `findExisting()` has no
cross-port composition logic to extract into a shared collaborator — splitting it into four
single-port classes plus a coordinator would be ceremony with no cohesion benefit.

One deliberate residual exception remains: `AccessEvaluator` keeps a direct `UserAuthorizationPort`
field. Role/ownership checks (`isAdmin`/`isModerator`/`isOwner`) are the security boundary itself,
not a domain read-model this module composes — the same category as a `*Hook`, called on nearly
every render across the whole UI, where an orchestrator round-trip would cost real latency for no
architectural benefit. `user-spring-boot-starter` is non-optional, so this carries no decoupling
risk either.

Alongside this, the module's 9 pre-existing service classes (scattered across 5 domain-scoped
sub-packages: `shared/`, `advertisement/enrich/`, `advertisement/save/`, `providerprofile/enrich/`,
`user/delete/`) moved into one flat `org.ost.orchestrator.services` package, so every service —
old and new — lives in one place instead of forcing a reader to know which sub-package a given
service happens to sit in.

**Consequences:** `scripts/architecture/generate-architecture-model.sh`'s Bounded Contexts diagram
now derives `UI → <starter>` edges from real evidence (grepping `marketplace-app` for actual
`ComponentFactory<XPort>`/direct `*Port` fields) instead of drawing one unconditionally for every
starter — after this migration, the only surviving edge is `UI → User` (via `AccessEvaluator`).
`Orchestrator → <starter>` and `UI → Orchestrator` edges are populated the same evidence-based way.
Every marketplace-app class that used to gate a UI section on `ComponentFactory<XPort>.ifAvailable()`
now gates on an orchestrator service's own `isAvailable()` method instead — behaviorally identical,
since the underlying `ComponentFactory` resolves against the same Spring bean registry regardless of
which class injects it.

**Bug found and fixed during verification:** the new service was first named `AuditReadService`,
which collided with `audit-spring-boot-starter`'s own internal `org.ost.audit.services.AuditReadService`
— identical simple class name means an identical default Spring bean name
(`auditReadService`), so the app failed to boot at all
(`ConflictingBeanDefinitionException`) despite every unit/integration test passing cleanly (none of
them boot the full `@SpringBootApplication` context with every starter's autoconfiguration on the
classpath at once). Caught only by an actual `deploy.sh` + container boot, not by any Maven-level
test — renamed to `AuditQueryService` and re-verified boot succeeds. Confirmed via a full grep sweep
that no other new service name collides with an existing class elsewhere in the repo.

**Trigger to revisit:** None currently open — Open Questions A/B/C from
`backlog/completed/issues/improvement-147-marketplace-orchestrator-followups.md` are all resolved
(A: route presence-guards through the orchestrator; B: the `EntityExistenceService` exception; C:
withdrawn, not a real design fork). The module's original single-caller-collaborator question
(`TaxonAssignmentWriteService`/`AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService`) moved
to `backlog/issues/improvement-124-provider-profile.md`'s Batch 124-C, unrelated to this ADR.

---

## ADR-004: `*Hook` implementations that only need domain-port access move here; `pom.xml` gains all 6 starter dependencies directly, superseding ADR-001's "never depends on a starter jar"

**Status:** Accepted

**Context:** Two related findings surfaced while investigating why `System › Diagrams ›
Module Dependencies` didn't visually converge with `Bounded Contexts` after ADR-003's true-BFF
migration (`marketplace-app` repointed to call only `marketplace-orchestrator`, never a domain
`*Port` directly): (1) `AuditDomainHookImpl`, `AdvertisementActivityFieldsHookImpl`,
`TaxonActivityFieldsHookImpl`, `UserActivityFieldsHookImpl`, `UserSettingsActivityFieldsHookImpl`,
and `CurrentActorHookImpl` still lived in `marketplace-app/spi`, so the *reverse*-direction
`Audit -> UI`/`starter -> UI` edges never converged the way the forward-direction edges did; (2)
`marketplace-app/pom.xml` still declared every starter's `<dependency>` directly, even though a
repo-wide grep found zero `org.ost.<starter>.*` imports anywhere in `marketplace-app` source — the
only reason those declarations existed was that *someone* has to put each starter's JAR on the
final `@SpringBootApplication`'s runtime classpath so Spring finds its `@AutoConfiguration`.

**Decision, part 1 — Hook relocation:** `AuditDomainHookImpl` and the four `*ActivityFieldsHookImpl`
classes needed only domain-port access (`EntityExistenceService`, already here) plus two small
UI-shell dependencies (translation, current-actor-id) — solved via two new forwarder SPIs, see
`platform-commons/DECISIONS.md` ADR-029. `CurrentActorHookImpl` needed only the new
`SessionActorHook` forwarder. All six now live in `org.ost.orchestrator.services`, alongside this
module's existing services. This **narrows, not reverses**, this module's original "no `*Hook`
implementations here" rule (see ADR-001's now-superseded reasoning below) — the boundary that
actually matters (no simultaneous multi-port composition fan-out) is unaffected; a `*Hook` that
dispatches to one port per call based on `EntityType` was never the shape the ≤2-port rule targets.
`ActivityEnrichHookImpl` stays in `marketplace-app/spi` — its dependency does real HTML-diff
formatting, not a mechanical forwarder-shaped lookup.

**Superseded by ADR-005:** the call above was revisited — `AdvertisementAuditEnrichService`'s only
two UI-shell touchpoints turned out to be single-value lookups (current locale, an `AdKind` label),
not the HTML-diff formatting itself. Both moved here behind forwarder SPIs; see ADR-005.

**Decision, part 2 — `pom.xml` dependency relocation, superseding ADR-001:** ADR-001's original
"never depends on a starter jar directly" rule (enforced via a Maven Enforcer `bannedDependencies`
rule) is **reversed**. All 6 starter `<dependency>` declarations moved from `marketplace-app/pom.xml`
to `marketplace-orchestrator/pom.xml` (`compile` scope preserved for `audit`/`attachment`/`user`/
`advertisement`, `runtime` scope preserved for `taxon`/`provider-profile` — Maven's scope
propagation table means `marketplace-app --compile--> marketplace-orchestrator --compile-->
<starter>` still resolves to `compile` transitively, and the `--runtime-->` chain still resolves to
`runtime`, so the two optional starters' removability is unchanged). The Enforcer rule and its
`enforce-no-starter-deps` execution block were deleted outright, not narrowed — ADR-001's
reasoning ("orchestrator must depend only on platform-commons Port/DTO contracts") described a
Maven-level restriction that no longer holds; the code-level restriction it was really protecting
(never import a starter's concrete class) is enforced independently by
`ArchitectureRulesTest.marketplace_must_not_import_starter_internals`, which checks real import
statements regardless of what `pom.xml` declares, and continues to apply unchanged to this module.

**Consequences:** `marketplace-app/pom.xml` now depends only on `platform-commons` +
`marketplace-orchestrator` (plus Vaadin/Spring/tooling deps unrelated to domain access) — `Module
Dependencies` now shows the same shape `Bounded Contexts` already showed post-ADR-003, closing the
original divergence this investigation started from. Verified for real, not assumed from the
scope-propagation math alone: a full reactor `mvn clean package`, and a `deploy.sh` boot with
`taxon-spring-boot-starter` temporarily removed from the root `pom.xml`, confirming the app still
starts and taxon-dependent UI degrades gracefully — see
`backlog/issues/improvement-149-architecture-map-module-deps-vs-bounded-contexts.md`'s Operational
notes for the concrete run once that issue closes. This also closes
`backlog/completed/issues/improvement-148-reverify-optional-module-removal-after-bff-migration.md`'s
scope — the same removal proof, not duplicated as a second test.

**Correction (verified 2026-08-12):** the taxon-removal proof above was real but incomplete — it
did not cover `provider-profile-spring-boot-starter`. Repeating the same removal test for that
starter found a real boot failure (`UserService`'s mandatory `ComponentFactory<ProviderProfilePort>`
field had no fallback producer once that starter left the classpath), fixed by adding the missing
`@Bean` to `UserAutoConfiguration` — see `platform-commons/DECISIONS.md` ADR-006's amendment. Both
starters are now confirmed removable; "this also closes improvement-148's scope" above should be
read as "closes the taxon half of it" — the provider-profile half needed an actual code fix, not
just a re-run of the same proof.

**Refinement (same session):** the six moved `*HookImpl` classes were first landed directly inside
`org.ost.orchestrator.services` (the existing flat services package), then split out into their
own sibling `org.ost.orchestrator.spi` package — mirroring `marketplace-app`'s own `services`/`spi`
separation, on user request, so SPI implementations aren't mixed in with plain composition
services in one folder. `UserActorNameService` (the actor-name-resolution collaborator
`AuditDomainHookImpl` delegates to) stays in `services/`, since it doesn't itself implement an SPI
interface.

**Second refinement (same session) — `UiLabelHook`/`SessionActorHook` also moved here, out of
`platform-commons`, plus a typed `AuditLabelKey` enum.** ADR-029's original placement (both
forwarder SPIs in `platform-commons/core/spi`) was reconsidered after two direct questions: why the
message-key constants these forwarders needed were raw, uncoupled `String` duplicates instead of a
typed shared constant, and whether `UiLabelHook`/`SessionActorHook` needed `platform-commons`
visibility at all given neither is ever called by a starter. Both resolved the same way: since
`marketplace-app` already legally depends on `marketplace-orchestrator`, any type this module
defines is visible to `marketplace-app` too — nothing needs `platform-commons` here. Landed:
`org.ost.orchestrator.spi.AuditLabelKey` (one enum entry per message key, matching `I18nKey`'s
existing constant names; `I18nKey.java` now references `AuditLabelKey.X.key()` instead of
duplicating the literal) and `UiLabelHook`/`SessionActorHook` themselves relocated into
`org.ost.orchestrator.spi` alongside it, `UiLabelHook.translate()` now typed on `AuditLabelKey`
instead of a raw `String`. `ArchitectureRulesTest.hooks_live_only_in_platform_commons` gained a
named allow-list for these two interfaces rather than silently breaking. See
`platform-commons/DECISIONS.md` ADR-029's own refinement note for the mirrored record.

**Third refinement (same session) — `AuditLabelKey` removed again; `UiLabelHook` takes the raw
DTO field-name constant directly.** User pushed back a second time on the same coupling concern,
this time correctly identifying that `AuditLabelKey`'s own `.key()` payload baked an i18n
resource-bundle-path convention (`"changes.field.title"`) into `marketplace-orchestrator`, which
has no business knowing how `marketplace-app` organizes its message bundles — and separately, that
each `@FieldNameConstants`-generated `Fields.*` constant (e.g. `TaxonSnapshotDto.Fields.nameEn`,
already visible to both modules via `platform-commons`) is already exactly the compiler-checked
identifier needed, making a parallel enum redundant. `UiLabelHook` now has two purpose-built
methods instead: `translate(EntityType entityType, String rawFieldKey, Object... args)` for field
labels (the raw `Fields.*` constant plus its owning `EntityType` for disambiguation — no key
enum), and `translateActorDeletedSuffix(String actorName)` for the one non-field-label case
(narrower than a generic key lookup, since it's exactly one fixed message with exactly one
argument shape). Every `*ActivityFieldsHookImpl`'s `labelFor()` collapsed to a one-line delegation
(`uiLabelHook.translate(EntityType.X, rawFieldKey)`) — the entire field-name-to-label switch
statement, previously duplicated once per domain class, now lives in exactly one place,
`marketplace-app`'s `UiLabelHookImpl`, nested by `EntityType` then `rawFieldKey`. `AuditLabelKey`
deleted; `I18nKey.java` reverted to its own plain string literals (no import from
`marketplace-orchestrator` at all anymore, closing that direction of coupling too).

**Fourth refinement (same session) — `AuditActivityFieldsHook` and its four implementations
removed entirely; not just simplified.** Asked directly whether the four `*ActivityFieldsHookImpl`
classes were even earning their keep once `labelFor()` collapsed to identical one-line delegation
in all of them. Verified before acting: `expandFields()` had always returned
`item.expandedChanges()` in every one of the four (never actually domain-specific, in this session
or before it), and grepping the whole repo found exactly one real caller of
`AuditActivityFieldsHook` anywhere — `marketplace-app`'s own `AuditTimelineRowRenderer` (not the
audit-starter the interface's own Javadoc claimed). With zero domain-specific behavior left and a
single caller already positioned to own the logic directly, deleted: the interface
(`platform-commons`), all four implementations (this module), and `UiLabelHook.translate(EntityType,
String, ...)`. `UiLabelHook` is a single-method `@FunctionalInterface` again
(`translateActorDeletedSuffix`). The field-name-to-label switch moved unchanged into a private
method on `AuditTimelineRowRenderer`; `AuditTimelineRowRenderer` no longer collects
`List<AuditActivityFieldsHook>`/builds an `EntityType`-keyed map for this purpose — a
`Set<EntityType> LABELED_ENTITY_TYPES` constant replicates the same fallback behavior the old
Spring multi-bean lookup provided (falls back to `changeFormatter.buildChangesList()` for entity
types with no label mapping), so this is a structural simplification, not an observable behavior
change. See `platform-commons/DECISIONS.md` ADR-029's third refinement for the mirrored record.

**Known consequence, not yet swept:** `scripts/architecture/generate-architecture-model.sh`'s
Bounded Contexts relationship-building (`"$dom" -> "Audit" "audited via"` edges) sourced its
evidence entirely from `grep implements AuditActivityFieldsHook` — with that interface gone, this
specific evidence-gathering method now finds nothing, so those edges will silently disappear from
the diagram on next regeneration even though the underlying fact (Advertisement/Taxon/User/
UserSettings genuinely are audited) hasn't changed. This is a bigger gap than the earlier
"SPI Map loses two nodes" note — an actual relationship signal is now undetectable by the
generator's current evidence-gathering approach, not just a cosmetic diagram change. Not fixed —
deferred to the same later sweep, flagged here so the size of the gap isn't lost.

**Trigger to revisit:** None currently open — `improvement-150` (filed 2026-08-11) tracks a further
tightening (removing `platform-commons`/`query-lib` from `marketplace-app/pom.xml` entirely), not
yet designed.

---

## ADR-005: `ActivityEnrichHookImpl` and `AdvertisementAuditEnrichService` move here too, behind the forwarder-SPI pattern
**Status:** Accepted

**Context:** ADR-004 kept `ActivityEnrichHookImpl` in `marketplace-app/spi`, reasoning its
collaborator (`AdvertisementAuditEnrichService`) did real HTML-diff formatting rather than a
mechanical lookup, unlike `AuditDomainHookImpl`/`CurrentActorHookImpl`. Re-examining that service
while scoping `improvement-150` found the claim didn't hold: its only two UI-shell dependencies —
`LocaleProvider` (for `Locale`-aware taxon name resolution) and `I18nService` (for `AdKind` label
text) — are each a single-value lookup, exactly the shape the forwarder-SPI pattern already covers
elsewhere. The HTML-diff/change-merging logic itself never touches either dependency directly.

**Decision:** One new forwarder SPI, `org.ost.orchestrator.spi.CurrentLocaleHook`
(`getCurrentLocale(): Locale`), joins `UiLabelHook`/`SessionActorHook`. `UiLabelHook` itself gains
a second method, `labelFor(AdKind): String` — a dedicated `AdKindLabelHook` was drafted first but
folded back in immediately: both methods wrap the same `I18nService`, both are implemented by the
same class, and no caller ever needs `labelFor` without also being able to reach
`translateActorDeletedSuffix`, so a second single-method interface bought nothing but an extra
file. `AdvertisementAuditEnrichService` moves to `org.ost.orchestrator.services`, its constructor
now taking `CurrentLocaleHook`/`UiLabelHook` instead of `LocaleProvider`/`I18nService`.
`ActivityEnrichHookImpl` moves to `org.ost.orchestrator.spi` alongside
`AuditDomainHookImpl`/`CurrentActorHookImpl`, unchanged otherwise. `marketplace-app` gains one
thin `*Impl` class in its own `spi/` package — `CurrentLocaleHookImpl` (wraps `LocaleProvider`) —
matching `UiLabelHookImpl`/`SessionActorHookImpl`'s existing shape; `UiLabelHookImpl` itself gains
the `labelFor` delegation.

**Consequences:** `marketplace-app`'s `services/advertisement/` package is now empty and removed
— `AdvertisementSaveService` (ADR-001) and `AdvertisementAuditEnrichService` (this ADR) were its
only two members, and both now live in `marketplace-orchestrator`. `AuditActivityEnrichHook` now
has zero implementations left in `marketplace-app` — every `*Hook` interface's real implementation
lives in this module, with only thin UI-shell forwarders remaining on the `marketplace-app` side.
None of the three forwarder SPIs is `@FunctionalInterface` in practice usage — every implementor is
a `@Component` class, never a lambda — so `UiLabelHook` carrying multiple methods costs nothing a
single-method shape would have preserved. The moved unit test
(`AdvertisementAuditEnrichServiceTest`) required no logic changes, only mock types swapped from
`LocaleProvider`/`I18nService` to `CurrentLocaleHook`/`UiLabelHook`.

**Follow-up correction (same batch):** the moved service still carried two more UI-shell decisions
after the move — `nameOrStrikethrough()` built raw `<s>...</s>` HTML markup for a soft-deleted
taxon's name, and a `NO_MEDIA_ENTRY` constant hardcoded the non-localized placeholder text `"—"`
for "no attachment yet". Both are presentation decisions, not data composition, and both slipped
through because the file moved verbatim from `marketplace-app` (where they were legitimate) without
re-auditing its content for UI-shaped logic. Fixed the same way: `UiLabelHook` gained
`markDeleted(String): String` and `noMediaPlaceholder(): String`; the latter is backed by a real
`I18nKey.AUDIT_CHANGES_NO_MEDIA` translation entry (`audit.changes.no.media`, `—` in both
`messages_en.properties`/`messages_uk.properties`) rather than a bare literal, since it is
user-facing text. `AdvertisementAuditEnrichService` now calls both through `uiLabelHook` and
carries no string/markup literals of its own.
