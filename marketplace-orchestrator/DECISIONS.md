# marketplace-orchestrator — Decisions

---

## ADR-007: Service-boundary authorization lives in `marketplace-orchestrator`, not per-starter or UI-only

**Status:** Accepted

**Context:** Mutating service/port methods across 4 domains (`AdvertisementPort`,
`ProviderProfilePort`, `UserAccountPort`, `TaxonPort`) trusted `actingUserId` with no ownership/role
check — authorization lived only in `marketplace-app`'s `AccessEvaluator` (UI button-gating). The
first non-UI caller (a future REST endpoint) would inherit zero authorization at the port. Two
shapes were considered; a first implementation draft actually started down the "checks inside each
starter" shape (a new `UserAuthorizationPort.canOperate(Long,Long)` SPI method +
`ComponentFactory<UserAuthorizationPort>` wiring into 4 starters) before being reversed the same day.

**Decision:** Place the checks in `marketplace-orchestrator` instead. Grep-verified every mutating
Port method is called only from this module today — never directly from `marketplace-app` (already
ArchUnit-enforced) and never from a sibling starter for a human-actor-authorized mutation — making
this module the real, already-enforced choke point every mutation passes through, without touching
`platform-commons` SPI signatures or any starter's own code. New `AuthorizationService` methods:
`canOperate`/`canEditAccount`/`canEditRole`/`isPrivileged`, both `UserDto`-taking (the actual rule
composition — single source of truth) and `Long`-id-taking (resolve via `ActorLookupService`, then
delegate) overloads, plus throwing `require*` variants raising a new `AccessDeniedException`. Wired
into `AdvertisementSaveService`, `ProviderProfileSaveService`, `UserProfileService`,
`UserDeleteService`, `TaxonCatalogService`. `AccessEvaluator` (`marketplace-app`) now delegates its
own rule composition to the same `UserDto`-taking overloads instead of independently
re-implementing `isAdmin || isModerator || isOwner`-shaped logic — **supersedes ADR-003's
"AccessEvaluator keeps a direct `UserAuthorizationPort` field" framing** (it still resolves the
current `UserDto` for free via session, avoiding the id-based overloads' extra lookup, but no
longer owns rule composition itself). Also closed a related bug found during implementation:
`UserProfileService.save()` is the one write path for both name and role, but only the UI disabled
the role field for unauthorized editors — server-side, nothing stopped a crafted request from
self-escalating role. Fixed by requiring the stricter `canEditRole` check whenever `dto.role()`
differs from the stored value.

**Rejected alternatives:**
1. Checks inside each of the 4 starters — reversed once grep confirmed the orchestrator is already
   the sole real caller, making the starter-level version no stronger in practice while touching
   far more files (new SPI method, 4 starters' own `ComponentFactory` wiring).
2. UI-only authorization plus an ArchUnit-enforced convention (e.g. `@PreAuthorize` required on
   every `*Controller`) — cheaper, but leaves a residual gap for non-REST callers (a `@Scheduled`
   job, an event listener) an ArchUnit rule can't reach; rejected as the primary mechanism for a
   project heading toward public endpoints.

**Consequences:** No `AdvertisementPort`/`ProviderProfilePort`/`UserAccountPort`/`TaxonPort`
signature changes — `ProviderProfileSaveService.save()` dropped its untrusted
`actorIsPrivileged` parameter, computing it internally instead. A hypothetical future direct-Port
caller bypassing the orchestrator (a new module, a scheduled job) would still have zero
authorization — same residual gap option 2 already accepted; not closed now. New
`AccessDeniedException` is caught at every `marketplace-app` save/delete call site, showing one
shared, localized notification (`I18nKey.COMMON_NOTIFICATION_ACCESS_DENIED`) instead of the
exception's raw internal message — the first genuinely domain-agnostic notification string in this
app's `I18nKey` enum, a deliberate exception to the per-view key-naming convention.

**Trigger to revisit:** If a non-orchestrator-mediated mutation caller is ever added, the residual
authorization gap above becomes real and needs its own fix.

---

## ADR-006: Stale-id-during-concurrent-delete guard in `AdvertisementSaveService`/`ProviderProfileSaveService`

**Status:** Accepted

**Context:** Both `SaveService`s already read a `before` snapshot ahead of calling the port's
`save()`, purely to build the audit diff. When an edit's target row was deleted between read and
write, `before` came back `null` for a non-new `dto` — previously this only logged a warning
("...updated but no 'before' snapshot was available (concurrent delete?) - skipping audit
capture") and proceeded to call `save()` anyway, which hit a pre-existing bug in both starters'
`buildEntity()`: a stale, non-null `id` combined with a `null` `before` silently produces
insert-shaped defaults, and Spring Data JDBC then attempts an update-path against a non-existent
row instead of a clear error.

**Decision:** Right after computing `before` in each `SaveService.save()`, before calling the
port's own `save()`: `if (!isNew && before == null) throw new OptimisticLockingFailureException(...)`.
This exception type was already the proven, correct shape — both `AdvertisementRepository`/
`ProviderProfileRepository` already throw it for version conflicts, and `marketplace-app`'s
`AbstractEntityOverlay.handleSave()` already has a dedicated catch block showing
`saveConfig().conflict()`, so this reaches correct UI handling for free. The guard makes
`captureAudit()`'s old `before == null` branch unreachable, so it collapses to a plain
`isNew ? captureCreation : captureUpdate` dispatch — the dead `log.warn(...)` branch is removed.

**Rejected alternative:** a guard inside each starter's own `buildEntity()` (the originally-planned
location) — moved one layer up once `AdvertisementSaveService`/`ProviderProfileSaveService` were
confirmed to already compute the exact state needed, making the fix smaller and requiring no
`buildEntity()` change in either starter.

---

## ADR-001: Extract a dedicated Application/BFF module instead of moving orchestration into marketplace-app
**Status:** Accepted — the "never depends on a starter jar" consequence is superseded by ADR-004;
the module-extraction decision itself stands

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
risk either. → superseded by ADR-007 (`AccessEvaluator` now delegates rule composition to
`AuthorizationService` instead of holding `UserAuthorizationPort` directly).

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

**Context:** `System › Diagrams › Module Dependencies` didn't visually converge with `Bounded
Contexts` after ADR-003's true-BFF migration (`marketplace-app` repointed to call only
`marketplace-orchestrator`, never a domain `*Port` directly), for two reasons: (1)
`AuditDomainHookImpl` and `CurrentActorHookImpl` still lived in `marketplace-app/spi`, so the
*reverse*-direction `Audit -> UI`/`starter -> UI` edges never converged the way the
forward-direction edges did; (2) `marketplace-app/pom.xml` still declared every starter's
`<dependency>` directly, even though a repo-wide grep found zero `org.ost.<starter>.*` imports
anywhere in `marketplace-app` source — the only reason those declarations existed was that
*someone* has to put each starter's JAR on the final `@SpringBootApplication`'s runtime classpath
so Spring finds its `@AutoConfiguration`.

**Decision, part 1 — Hook relocation:** `AuditDomainHookImpl` and `CurrentActorHookImpl` needed
only domain-port access (`EntityExistenceService`, already here) plus small UI-shell dependencies
(translation, current-actor-id) — solved via forwarder SPIs `UiLabelHook`/`SessionActorHook`
(see the "Forwarder SPI pattern" section in `marketplace-orchestrator/CLAUDE.md`). Both Hooks now
live in `org.ost.orchestrator.spi`, a sibling package to the existing flat `services/` package —
mirroring `marketplace-app`'s own `services`/`spi` separation, so SPI implementations aren't mixed
in with plain composition services in one folder. This **narrows, not reverses**, this module's
original "no `*Hook` implementations here" rule (see ADR-001) — the boundary that actually matters
(no simultaneous multi-port composition fan-out) is unaffected; a `*Hook` that dispatches to one
port per call based on `EntityType` was never the shape the ≤2-port rule targets.
`UserActorNameService` (the actor-name-resolution collaborator `AuditDomainHookImpl` delegates to)
stays in `services/`, since it doesn't itself implement an SPI interface. `ActivityEnrichHookImpl`
moved here too, along with its collaborator — see ADR-005.

Both `UiLabelHook`/`SessionActorHook` and their message-key vocabulary live in
`org.ost.orchestrator.spi` directly (not `platform-commons`): since `marketplace-app` already
legally depends on `marketplace-orchestrator`, any type this module defines is visible to
`marketplace-app` too, and neither forwarder SPI is ever called by a starter. `UiLabelHook` takes
the raw, compiler-checked `Fields.*` DTO field-name constant (e.g.
`TaxonSnapshotDto.Fields.nameEn`, already visible to both modules via `platform-commons`) plus its
owning `EntityType` directly — `translate(EntityType entityType, String rawFieldKey, Object...
args)` — rather than a separate key-mapping enum, since the field constant is already the
compiler-checked identifier needed and a parallel enum would duplicate it while also baking an
i18n resource-bundle-path convention into `marketplace-orchestrator`, which has no business
knowing how `marketplace-app` organizes its message bundles.
`ArchitectureRulesTest.hooks_live_only_in_platform_commons` carries a named allow-list for these
two interfaces.

`AuditActivityFieldsHook` — a fourth Hook interface that once existed for per-domain audit
field-label mapping, with four per-domain `*ActivityFieldsHookImpl` implementations in this
module — was removed entirely, `platform-commons` interface included: every implementation had
converged to an identical one-line delegation with zero domain-specific logic left
(`expandFields()` always just returned `item.expandedChanges()`), and its only real caller anywhere
in the repo was already `marketplace-app`'s own `AuditTimelineRowRenderer`, not the audit-starter
its own Javadoc claimed. The field-name-to-label switch statement, previously duplicated once per
domain class, now lives in exactly one place — a private method on `AuditTimelineRowRenderer`
itself. `AuditTimelineRowRenderer` no longer collects `List<AuditActivityFieldsHook>`/builds an
`EntityType`-keyed map for this purpose; a `Set<EntityType> LABELED_ENTITY_TYPES` constant
replicates the same fallback behavior the old Spring multi-bean lookup provided (falls back to
`changeFormatter.buildChangesList()` for entity types with no label mapping) — a structural
simplification, not an observable behavior change.
`docs/architecture/scripts/generate-architecture-model.sh`'s Bounded Contexts relationship-building
(`"$dom" -> "Audit" "audited via"` edges) now derives its evidence from
`AuditTimelineRowRenderer.LABELED_ENTITY_TYPES` instead of `grep implements
AuditActivityFieldsHook`.

**Decision, part 2 — `pom.xml` dependency relocation, superseding ADR-001:** ADR-001's original
"never depends on a starter jar directly" rule (enforced via a Maven Enforcer `bannedDependencies`
rule) is **reversed**. All 6 starter `<dependency>` declarations moved from
`marketplace-app/pom.xml` to `marketplace-orchestrator/pom.xml` (`compile` scope preserved for
`audit`/`attachment`/`user`/`advertisement`, `runtime` scope preserved for `taxon`/
`provider-profile` — Maven's scope propagation table means `marketplace-app --compile-->
marketplace-orchestrator --compile--> <starter>` still resolves to `compile` transitively, and the
`--runtime-->` chain still resolves to `runtime`, so the two optional starters' removability is
unchanged). The Enforcer rule and its `enforce-no-starter-deps` execution block were deleted
outright, not narrowed — ADR-001's reasoning ("orchestrator must depend only on platform-commons
Port/DTO contracts") described a Maven-level restriction that no longer holds; the code-level
restriction it was really protecting (never import a starter's concrete class) is enforced
independently by `ArchitectureRulesTest.marketplace_must_not_import_starter_internals`, which
checks real import statements regardless of what `pom.xml` declares, and continues to apply
unchanged to this module.

**Consequences:** `marketplace-app/pom.xml` now depends only on `platform-commons` +
`marketplace-orchestrator` (plus Vaadin/Spring/tooling deps unrelated to domain access) — `Module
Dependencies` now shows the same shape `Bounded Contexts` already showed post-ADR-003. Verified for
real: a full reactor `mvn clean package`, and a `deploy.sh` boot with both `taxon-spring-boot-starter`
and `provider-profile-spring-boot-starter` each temporarily removed from the root `pom.xml`,
confirming the app still starts and the dependent UI degrades gracefully in both cases (the
provider-profile case required an actual code fix — `UserService`'s mandatory
`ComponentFactory<ProviderProfilePort>` field had no fallback producer once that starter left the
classpath; fixed by adding the missing `@Bean` to `UserAutoConfiguration`, see
`platform-commons/DECISIONS.md`). The further tightening this ADR originally anticipated (removing
`platform-commons`/`query-lib` from `marketplace-app/pom.xml` entirely) was later scoped down:
`query-lib` was removed, but a full `platform-commons` removal was deliberately dropped in favor of
a narrower "zero direct `*Port`/`*Hook` (SPI) usage" goal — `platform-commons` DTOs are still
genuinely used for UI binding and remain a direct dependency.

---

## ADR-005: `ActivityEnrichHookImpl` and `AdvertisementAuditEnrichService` move here too, behind the forwarder-SPI pattern
**Status:** Accepted

**Context:** ADR-004 kept `ActivityEnrichHookImpl` in `marketplace-app/spi`, reasoning its
collaborator (`AdvertisementAuditEnrichService`) did real HTML-diff formatting rather than a
mechanical lookup, unlike `AuditDomainHookImpl`/`CurrentActorHookImpl`. Re-examining that service
found the claim didn't hold: every one of its UI-shell dependencies — `LocaleProvider`
(`Locale`-aware taxon name resolution), `I18nService` (`AdKind` label text, soft-deleted-name
strikethrough markup, the no-media placeholder text) — is a single-value lookup or a presentation
decision, exactly the shape the forwarder-SPI pattern already covers elsewhere; the HTML-diff/
change-merging logic itself never depends on any of them directly.

**Decision:** One new forwarder SPI, `org.ost.orchestrator.spi.CurrentLocaleHook`
(`getCurrentLocale(): Locale`), joins `UiLabelHook`/`SessionActorHook`. `UiLabelHook` gains three
more methods beyond `translateActorDeletedSuffix`: `labelFor(AdKind): String`,
`markDeleted(String): String` (wraps a soft-deleted taxon's name in strikethrough markup), and
`noMediaPlaceholder(): String` (backed by a real `I18nKey.AUDIT_CHANGES_NO_MEDIA` translation entry
— `audit.changes.no.media`, `—` in both `messages_en.properties`/`messages_uk.properties` — rather
than a bare literal, since it is user-facing text). A dedicated per-concern interface for each was
considered and rejected: every method wraps the same `I18nService`, every method is implemented by
the same class, and no caller ever needs one without also being able to reach the others, so
splitting them would buy nothing but extra files. None of the three forwarder SPIs is
`@FunctionalInterface` in practice — every implementor is a `@Component` class, never a lambda — so
`UiLabelHook` carrying multiple methods costs nothing a single-method shape would have preserved.

`AdvertisementAuditEnrichService` moves to `org.ost.orchestrator.services`, its constructor now
taking `CurrentLocaleHook`/`UiLabelHook` instead of `LocaleProvider`/`I18nService`, and calling
`uiLabelHook` for all four lookups above — it carries no string/markup literals of its own.
`ActivityEnrichHookImpl` moves to `org.ost.orchestrator.spi` alongside
`AuditDomainHookImpl`/`CurrentActorHookImpl`, unchanged otherwise. `marketplace-app` gains one thin
`*Impl` class in its own `spi/` package — `CurrentLocaleHookImpl` (wraps `LocaleProvider`) —
matching `UiLabelHookImpl`/`SessionActorHookImpl`'s existing shape; `UiLabelHookImpl` itself gains
the four delegations.

**Consequences:** `marketplace-app`'s `services/advertisement/` package is now empty and removed —
`AdvertisementSaveService` (ADR-001) and `AdvertisementAuditEnrichService` (this ADR) were its only
two members, and both now live in `marketplace-orchestrator`. `AuditActivityEnrichHook` now has
zero implementations left in `marketplace-app` — every `*Hook` interface's real implementation
lives in this module, with only thin UI-shell forwarders remaining on the `marketplace-app` side.
The moved unit test (`AdvertisementAuditEnrichServiceTest`) required no logic changes, only mock
types swapped from `LocaleProvider`/`I18nService` to `CurrentLocaleHook`/`UiLabelHook`.
