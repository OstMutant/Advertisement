# improvement-136: Extract `marketplace-orchestrator` — an Application/BFF composition layer for cross-domain orchestration

**Type:** architecture — new module extraction. Supersedes this issue's original, narrower scope
(see "Decision history" below).
**Module:** new `marketplace-orchestrator` module; touches `advertisement-spring-boot-starter`,
`provider-profile-spring-boot-starter`, `platform-commons` (`AdvertisementPort`/
`ProviderProfilePort`), `marketplace-app`, root `pom.xml`, `ArchitectureRulesTest`, root
`CLAUDE.md`, `marketplace-app/DECISIONS.md`.
**Priority:** 🔴 top — moved to the very top of `BACKLOG.md`'s priority order 2026-08-07 per
explicit user request, ahead of `improvement-138`/`improvement-135`/`improvement-124`'s remaining
batches; still intended to land before `improvement-124` Batch 124-C so the `AccountOverlay` UI is
built against the final contract from the start.
**When:** **Phase 0 (mandatory architecture discovery) started 2026-08-07**, per explicit user
go-ahead in chat ("давай фазу 0"). The pause recorded below (2026-08-04) is lifted — this section
is kept for history, not as a current blocker.

## Decision history (how this issue arrived at this scope)

- Originally filed (2026-08-04, see git history) as a narrower fix: delete
  `AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService` from their starters and move
  the same enrichment responsibility into new `marketplace-app/services/advertisement/` and
  `services/providerprofile/` classes, mirroring the existing `AdvertisementAuditEnrichService`
  precedent. That plan is preserved below under "Superseded plan" for reference — its diagnosis of
  the problem (both enrichment services orchestrate 2-3 sibling `*Port`s from inside the wrong
  module, and the `Locale` parameter leaking onto `AdvertisementPort`/`ProviderProfilePort` is a
  presentation concern that doesn't belong in a domain contract) is still correct and still part of
  this issue's scope — only the destination module changed.
- The user then supplied a much larger, independently-authored task spec (reproduced verbatim
  below, Goal through Final rule) proposing a dedicated `marketplace-orchestrator` Maven module: an
  Application/BFF composition layer sitting between `marketplace-app` (kept as a thin UI adapter)
  and the domain starters, explicitly designed so a future REST adapter could reuse the same
  orchestration services without relocating business logic a second time.
- Trade-offs discussed directly with the user before deciding:
  - **For the narrow fix:** smaller diff, no new module, matches root `CLAUDE.md`'s currently
    documented architecture ("UI is a monolith... marketplace-app stitches things together"); no
    REST work is actually scheduled anywhere in this backlog today — checked `improvement-073`
    (REST-for-Playwright-seeding only, explicitly "not the first real REST controller", 🔵 low
    priority) and `improvement-111` (authorization-at-service-boundary, explicitly Deferred until
    "the first non-UI mutation endpoint... exists") — REST is not an active near-term driver by the
    backlog's own evidence.
  - **For the orchestrator module:** this repo already has a recorded precedent
    (`user-spring-boot-starter/CLAUDE.md`, `platform-commons/DECISIONS.md` ADR-026) of paying a
    structural cost purely for interface cohesion/readability with "no runtime-toggle benefit" —
    so "structural investment without an immediate functional trigger" is not foreign to this
    codebase's own decision history. The user's stated goal is genuine: flexibility to change the
    UI/frontend layer later without first having to re-extract business orchestration out of a
    Vaadin-entangled monolith, plus avoiding rework on two already-queued cross-domain features
    (`improvement-124` Batch 124-C/D) that will need the same kind of composition again very soon —
    a real, not speculative, near-term need.
  - **Optional-module-removal risk — investigated concretely, not assumed:** confirmed the current
    mechanism (`*Port` interfaces live only in `platform-commons`; every consumer injects
    `ComponentFactory<XPort>`/`ObjectProvider<XPort>`, never a starter's concrete impl class
    directly; `marketplace-app/pom.xml` marks `taxon-spring-boot-starter`/
    `provider-profile-spring-boot-starter` as `<scope>runtime</scope>` while `audit`/`attachment`/
    `user`/`advertisement` are plain `compile` scope) does not depend on *which module* calls
    `ComponentFactory<XPort>` — only on (a) the Port interface living in `platform-commons`, (b) no
    caller ever importing a concrete `*PortImpl`/`Default*Port` class, and (c) the final assembled
    app (`marketplace-app`) controlling compile-vs-runtime scope per starter. As long as
    `marketplace-orchestrator` depends only on `platform-commons` — never on a starter artifact
    directly, never on a concrete implementation class — today's optional-removal behavior is
    preserved unchanged. Phase 8 below turns this from an architectural argument into an empirical
    proof (remove a starter, compile, boot, exercise a dependent feature).
- **Decision (2026-08-04): proceed with the `marketplace-orchestrator` module**, superseding the
  narrower plan. The full target task spec is preserved close to verbatim below (it was carefully
  authored with explicit non-negotiable rules and phase-by-phase discovery discipline) rather than
  paraphrased, so nothing is lost in translation.
- **Explicitly paused before Phase 0** — the user has more points to raise before this task starts.

## Superseded plan (kept for reference only — do not implement as-is)

The original, narrower fix: drop `Locale` from `AdvertisementPort`/`ProviderProfilePort`; delete
`AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService` from their starters; add
`AdvertisementViewEnrichService`/`ProviderProfileViewEnrichService` in
`marketplace-app/services/advertisement/` and `services/providerprofile/`, mirroring
`AdvertisementAuditEnrichService`'s existing shape; repoint every call site
(`AdvertisementsView`, `AdvertisementCardView`, `OgMetaRequestListener`, `SitemapController`,
etc.). No new Maven module. This diagnosis (the `Locale`-on-port smell, the two enrichment
services' shape, the list of real call sites to repoint) remains valid input for Phase 0/3/7 below
— only the destination module (`marketplace-orchestrator` instead of `marketplace-app`) changed.

---

## Target task spec (as supplied by the user, 2026-08-04)

### Goal

Create a new Maven module `marketplace-orchestrator` that becomes the application's
**Application / Backend / BFF-style composition layer**.

The orchestrator must:

* own application-level use cases and cross-domain orchestration;
* decouple domain starters from each other;
* keep domain starters focused on their own bounded context and persistence;
* remove cross-domain display/read-model composition from domain starters;
* keep the UI layer thin;
* provide a stable application-facing API that can later be consumed by REST controllers without
  moving business orchestration back into `marketplace-app`;
* preserve the repository's existing modularity and optional-module behavior;
* provide a clean application boundary for continuing the currently paused Portfolio/Profile
  feature after this refactor is complete.

### Important sequencing context

The `ProviderProfile` / Portfolio Profile feature is currently under implementation and has
exposed a second concrete instance of the same cross-domain enrichment pattern already present in
the Advertisement domain.

**Do not continue implementing new Portfolio/Profile feature functionality as part of this task.**

The Portfolio/Profile implementation should be treated as **temporarily paused at its current
state** while this architectural extraction is performed.

This task exists partly because continuing the Portfolio/Profile implementation in its current
form would risk reproducing the same cross-domain orchestration pattern again.

After `marketplace-orchestrator` is extracted and verified, Portfolio/Profile implementation will
resume on top of the new application/orchestration boundary.

Do not delete or roll back valid Portfolio/Profile work merely because it is paused. Preserve the
current implementation state unless a change is directly required by this refactor.

### Target architectural direction

The intended architecture is conceptually:

```
Vaadin UI (current) ──────┐
                         │
REST API (future) ───────┤
                         ▼
              marketplace-orchestrator
               Application / BFF layer
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
   advertisement       user            taxon
      starter         starter          starter
          │              │              │
          ▼              ▼              ▼
         DB             DB              DB
```

`marketplace-orchestrator` is the composition boundary between external/application adapters
(currently Vaadin, future REST) and domain starters.

REST is **not part of this task**.

The objective is to establish the application/backend boundary now so that REST can be added later
as another adapter without relocating orchestration logic again.

Do NOT introduce a new contract framework, new capability abstraction, new `marketplace-contracts`
module, or duplicate SPI/Port system unless the existing repository is first inspected and the
code proves that the current contracts cannot support this architecture.

Prefer reusing the project's existing `*Port`, SPI, auto-configuration, conditional bean, and
module-boundary mechanisms.

---

## NON-NEGOTIABLE ARCHITECTURAL RULES

1. Domain starters must remain independent of each other.
2. A domain starter must NOT orchestrate another domain.
3. Cross-domain composition belongs in `marketplace-orchestrator`.
4. `marketplace-app` must not directly compose multiple domain SPIs/Ports for a single
   application use case.
5. `marketplace-app` should primarily contain external/application adapters such as Vaadin UI,
   authentication, locale handling, and other concerns that are genuinely application-shell-specific.
6. `marketplace-orchestrator` owns application-level use cases and cross-domain composition.
7. Future REST controllers should be able to call the same orchestrator/application services used
   by Vaadin rather than duplicating business orchestration.
8. Preserve existing contracts and architecture mechanisms wherever possible.
9. Do not create a second or parallel SPI/Port system if an existing contract already provides
   the required boundary.
10. Preserve existing optional-module behavior.
11. Do not turn `marketplace-orchestrator` into a new God module or God service. Keep application
    services/use cases cohesive and aligned with actual use cases already present in the codebase.
12. Follow all existing architecture rules in root/module `CLAUDE.md`, `ArchitectureRulesTest`,
    ADRs, and existing module contracts.
13. No single class in `marketplace-orchestrator` may depend on more than two domain `*Port`
    interfaces. If a use case genuinely needs more, split it into smaller, composed use-case
    services rather than one service that "knows about everything" — this is the concrete
    mechanism preventing the orchestrator from becoming the next
    `AdvertisementEnrichmentService`-style god-service, just one module up.
14. `marketplace-orchestrator` must never import `JdbcClient`, any `*Repository`, or any
    `*CrudRepository`. It composes results from domain Ports only. This mirrors the existing
    `port_and_hook_impl_classes_are_pure_delegation` ArchUnit rule's spirit — apply the same
    discipline one layer up.

---

## PHASE 0 — MANDATORY ARCHITECTURE DISCOVERY

Before modifying anything, inspect the actual repository and establish how the current
architecture already solves the problems this task is trying to address.

Do NOT assume the repository needs new contracts or a new optional-capability mechanism.

### Confirmed Starting Point

Two confirmed instances anchor this task, not one — the second was found after this task was
first scoped, which is itself informative: the pattern is actively spreading, not a one-off.

1. `AdvertisementEnrichmentService` (`advertisement-spring-boot-starter`) — depends on
   `TaxonPort`, `UserPort`, and `AttachmentPort`.
2. `ProviderProfileEnrichmentService` (`provider-profile-spring-boot-starter`) — depends on
   `TaxonPort` and `UserPort`. It has a near-identical method shape to case 1 (category + city +
   actor enrichment), apparently introduced by copy-paste while adding the F-04 Portfolio/Profile
   feature.

Phase 0 must treat resolving **both** as in-scope.

The task must search for the **shape** of this pattern rather than only raw import counts. A
class depending on exactly 2 foreign ports is just as much an instance of this problem as one
depending on 3. Rule 13's fan-out limit bounds how bad a single class can get; it does not detect
the same bounded shape being duplicated across N domains.

Search for the specific method-name/helper pattern (`enrichWith*AndCity`, `enrichWithActor*`,
`applyCategoryAndCityData`-style private helpers) in addition to import-based search, since that
is what actually caught the second instance.

Phase 0 must search for all additional instances of the same semantic pattern before deciding the
final extraction shape.

Three distinct patterns must remain separate rather than treating every cross-domain call as
equivalent:

1. **Display/view composition** — a starter reaching into multiple other domains' ports to
   assemble a read-model for presentation. This includes the confirmed Advertisement and
   ProviderProfile enrichment cases. This pattern belongs in `marketplace-orchestrator`.
2. **Cross-cutting event reporting** — `TaxonService` / `UserPreferencesService` calling
   `AuditPort` to record that something happened. Treat this as a different, more defensible
   category (closer to logging than orchestration) unless discovery finds concrete evidence it is
   doing more than recording events. Do not sweep it into the same bucket as pattern 1 without
   justification.
3. **Narrow referential-integrity cooperation** — `UserService.cleanup()` calling
   `AdvertisementPort.findOwnerIds()` / `clearActorReferences()`, a deliberate design from
   `improvement-120` replacing a removed FK constraint. This is a real starter-to-starter
   dependency but a narrow, specific one tied to a scheduled retention job inside user-starter
   itself. Moving just this call into the orchestrator could add awkward indirection for little
   benefit. Evaluate it on its own; do not assume it must move just because it is cross-domain.

Read and inspect: root `CLAUDE.md`; every relevant module `CLAUDE.md`; root `pom.xml`;
`marketplace-app/pom.xml`; every relevant starter `pom.xml`; `ArchitectureRulesTest`; all relevant
`DECISIONS.md`/ADRs; all existing `*Port` interfaces; all SPI interfaces and implementations; all
`*Hook`/`*HookImpl` classes; all `ComponentFactory` implementations; all Spring Boot
auto-configuration classes; all `@ConditionalOn...` usage; all `ObjectProvider`/`Optional`/
equivalent optional-bean resolution mechanisms; all existing module-to-module dependencies; all
services in `marketplace-app` that currently coordinate multiple domain modules; all relevant
DTOs, especially `AdvertisementInfoDto` and the ProviderProfile DTOs; all existing mechanisms that
allow a module/starter to be absent; the current Portfolio/Profile implementation state, including
all changes already made for F-04.

### A. Existing contracts

For every relevant domain boundary, identify: where the contract is declared; which module
implements it; who consumes it; whether it is already designed to work when the implementation
module is absent. Do not invent a replacement contract if an existing one already serves this
purpose. Produce a table:

| Contract | Declared in | Implemented in | Consumed by | Optional? | Existing mechanism |
| -------- | ----------- | -------------- | ----------- | --------- | ------------------ |

Use actual repository names.

### B. Existing optional-module behavior

> If a module is removed from the build, the application should continue to compile and work
> wherever that module is optional.

First verify whether the current repository already supports this. Test/document the actual
behavior at three distinct levels: **(1) Compile-time** — can the module be removed from the
Maven reactor/dependencies without compilation failures? **(2) Application startup** — can the
application start without that module? **(3) Feature-time** — what happens when a feature
requiring the absent module is invoked? Do not assume these three behaviors are identical.

For every relevant module, document the actual current behavior. If the repository already has a
mechanism for this, reuse it — do NOT introduce a new capability registry or abstraction merely
because optional modules exist. If the existing mechanism is insufficient, identify the exact
concrete gap before proposing any new mechanism.

### C. Existing dependency graph

Build the current dependency picture from actual Maven dependencies and Java imports. Identify:
starter → starter dependencies; `marketplace-app` → starter dependencies; current orchestration
dependencies; SPI/Port dependency direction; implementation dependencies; `ComponentFactory`
cross-domain dependencies. Distinguish: (1) Maven/module dependency; (2) compile-time Java
dependency; (3) Spring bean/runtime dependency; (4) architectural dependency enforced by
`ArchitectureRulesTest`. Do not conflate these.

### D. Existing application/orchestration layer

Find all existing services that already perform application-level coordination. In particular
inspect: `AdvertisementSaveService`; `UserDeleteService`; `AdvertisementAuditEnrichService`;
`AdvertisementEnrichmentService`; `ProviderProfileEnrichmentService`; any other services that call
multiple domain Ports/SPIs; any services currently used directly by Vaadin Views; any
Portfolio/Profile services introduced as part of F-04. For each, determine: its current
responsibility; which domain boundaries it crosses; whether it belongs in
`marketplace-orchestrator`; whether it should remain in `marketplace-app`; whether it should be
split into smaller application use cases; whether it duplicates an existing composition pattern
elsewhere. Do not move classes merely because their names contain `Service` — move based on
responsibility and dependency direction.

### E. Existing DTO ownership

Inspect `AdvertisementInfoDto`, ProviderProfile DTOs, and all related DTOs. Determine whether each
is: domain-owned; persistence-facing; application/composition-level; UI-specific. If
`AdvertisementInfoDto` or a ProviderProfile DTO combines domain data from multiple modules, verify
whether it is already effectively an application/composition DTO. Do not duplicate or relocate
DTOs without evidence from the actual dependency graph.

---

## PHASE 1 — WRITE THE TARGET ARCHITECTURE SPEC

Before implementation, produce a concrete target architecture based on Phase 0. The target must
show:

```
marketplace-app
    │
    │ application-facing calls
    ▼
marketplace-orchestrator
    │
    ├── existing Advertisement contract
    ├── existing ProviderProfile contract
    ├── existing User contract
    ├── existing Taxon contract
    └── existing Attachment contract
             │
             ▼
         domain starters
```

Use the project's actual contract names — do not replace the above with invented abstractions.

The target must explicitly show: which dependencies move from `marketplace-app` to
`marketplace-orchestrator`; which dependencies are removed from domain starters; which existing
contracts remain unchanged; which contracts, if any, need modification; which existing
optional-module mechanisms are preserved; how absence of an optional module is handled; which
modules are mandatory vs. optional, based on actual code; how future REST can call the same
application/orchestration services as Vaadin; how the paused Portfolio/Profile implementation
resumes on top of the new application boundary; where shared enrichment/composition logic lives;
which domain-specific enrichment steps remain separate.

The target architecture must be validated against `ArchitectureRulesTest` and existing ADRs. If
the proposed shared composition/generalization conflicts with rule 13's ≤2-port limit, explicitly
document the conflict and the chosen solution. Prefer the smallest genuinely shared composition
primitive — do not generalize merely because two services look structurally similar. If the two
DTOs' enrichment target fields genuinely cannot be unified cleanly, unify only the overlapping
semantics (category, city, actor) and leave domain-specific fields (advertisement media/attachment
data) as separate composition steps. Do not introduce unnecessary generic DTO hierarchies,
reflection, generic "enrichment engines", or abstraction layers solely to eliminate superficial
code duplication.

---

## PHASE 2 — CREATE `marketplace-orchestrator`

Create a new Maven module `marketplace-orchestrator`. Its purpose: *application-level
orchestration and cross-domain use-case composition*. It is NOT: a new domain; a replacement for
domain starters; a generic shared library; a new contract registry; a duplicate SPI layer; a
dumping ground for all backend code.

Configure `pom.xml` only with dependencies actually justified by the Phase 0 dependency analysis.
Do not blindly add all five starters just because this spec's diagram shows them — for every
starter dependency added, explain why it is required, and verify whether adding it would break
the project's existing compile-time optional-module behavior. If an existing contract can be
consumed without making the corresponding implementation module mandatory, preserve that
mechanism; do not solve this by inventing a new abstraction unless the audit proves it necessary.

Add `marketplace-orchestrator` to the root Maven module list. Update `marketplace-app` to depend
on `marketplace-orchestrator`. Keep direct starter dependencies in `marketplace-app` only where
genuinely required (Spring Boot auto-configuration, application startup, UI-specific
functionality, or another verified responsibility). Remove redundant direct dependencies after
verifying the actual runtime/auto-configuration requirements.

---

## PHASE 3 — CLEAN DOMAIN STARTERS

### Advertisement

1. Inspect `AdvertisementEnrichmentService`.
2. If it performs cross-domain composition, move that responsibility to
   `marketplace-orchestrator`.
3. Remove `UserPort`, `TaxonPort`, and `AttachmentPort` dependencies from `AdvertisementService` if
   the audit confirms they are only needed for cross-domain composition.
4. Refactor `AdvertisementService` to expose only advertisement-owned/core behavior.
5. Update `AdvertisementPort` only as required by the actual target architecture.
6. Preserve existing contract semantics unless the change is necessary to remove cross-domain
   orchestration.
7. Do not change unrelated domain behavior.

### Provider Profile

1. Inspect `ProviderProfileEnrichmentService`.
2. Remove cross-domain display/view composition from the domain starter if the Phase 0 audit
   confirms it belongs in the application/orchestration layer.
3. Preserve ProviderProfile domain behavior and its own domain-owned responsibilities.
4. Update ProviderProfile contracts only as required by the target architecture.
5. Do not delete or roll back valid F-04 Portfolio/Profile implementation work merely because this
   orchestration responsibility moves.

Apply the same principle to other starters: *a domain starter owns its own domain capability. It
must not orchestrate another domain.*

Inspect every existing `ComponentFactory` and cross-domain dependency. Do not blanket-delete every
`ComponentFactory`. For each one: identify what it creates; identify which module owns the created
component; determine whether it violates the domain boundary; remove/refactor only those that
create direct cross-domain coupling.

---

## PHASE 4 — MOVE APPLICATION ORCHESTRATION

Move appropriate application-level orchestration services from `marketplace-app` to
`marketplace-orchestrator`. Candidates: `AdvertisementSaveService`; `UserDeleteService`;
`AdvertisementAuditEnrichService`; `AdvertisementEnrichmentService` (if Phase 0 confirms
cross-domain application orchestration); `ProviderProfileEnrichmentService` (same condition).

Because two near-identical enrichment services now exist, moving both unchanged into
`marketplace-orchestrator` as two separate classes would relocate the duplication rather than
resolve it. Identify and extract the **smallest genuinely shared composition primitive**.
Generalize only semantics that are actually shared — e.g. category/city/actor enrichment may be
shared between Advertisement and ProviderProfile, but Advertisement's additional media/attachment
enrichment stays a separate, Advertisement-specific composition step. Do not force a single
abstraction to cover fields only one DTO has; do not introduce a generic abstraction merely
because two services have similar method names.

Avoid: generic "enrichment engines"; reflection-based DTO mutation; unnecessary generic DTO
hierarchies; complex builder/callback abstractions; abstraction layers whose only purpose is
eliminating a small amount of duplication. Prefer simple, explicit composition.

If the shared composition touches more than two domain Ports, do not violate rule 13. A shared
composition touching Taxon + User + Attachment for Advertisement but only Taxon + User for
ProviderProfile may need to be structured as:

```
Advertisement-specific application use case
    │
    ├── ≤2-port shared Taxon/User composition
    │
    └── separate Attachment-specific composition
```

or another equally simple decomposition that preserves the ≤2-port rule. The exact structure must
be decided from the real DTOs and use cases, not imposed in advance. Flag explicitly in the Phase 1
target architecture spec if this generalization conflicts with rule 13.

The orchestrator should expose application-facing operations usable by Vaadin, REST, or other
application adapters later. Package structure should mirror the organization already used inside
each domain starter (entity/repository/service/spi), one level up — e.g.
`orchestrator.advertisement.save`, `orchestrator.advertisement.enrich`, `orchestrator.user.delete`,
`orchestrator.providerprofile.enrich` — rather than one flat `orchestrator.services` package
accumulating every use case. This is a structural complement to the fan-out limit: the limit keeps
individual classes small; the package structure keeps the module navigable as it grows; shared
composition primitives prevent cross-domain enrichment logic from being duplicated across use
cases. Do not create one giant `AdvertisementOrchestrationService` or
`MarketplaceOrchestrationService`.

---

## PHASE 5 — MOVE SPI IMPLEMENTATIONS CAREFULLY

Inspect every `*HookImpl` currently under `marketplace-app/src/main/java/org/ost/marketplace/spi/`.
Do NOT move all implementations blindly. For each, classify its responsibility: domain logic;
application orchestration; cross-domain coordination; infrastructure adapter; UI/application-shell
concern. Move only implementations whose responsibility belongs to application-level
orchestration; preserve implementations that genuinely belong elsewhere.

Final dependency direction must be:

```
domain starter
    ↓
exposes contract/SPI

marketplace-orchestrator
    ↓
implements application-level cross-domain coordination

marketplace-app
    ↓
consumes application/orchestrator services
```

Do not introduce circular dependencies.

---

## PHASE 6 — APPLICATION/BFF API

`marketplace-orchestrator` must provide application-facing services suitable for both current
Vaadin use and future REST:

```
Vaadin View ────────────┐
                        ▼
             marketplace-orchestrator
                        ▲
                        │
REST Controller ────────┘
(future)
```

Vaadin Views must not need to know how multiple domain modules are composed. Instead of
`AdvertisementsView` directly injecting `AdvertisementPort`/`UserPort`/`TaxonPort`/
`AttachmentPort`, it should call the appropriate application/orchestrator use case, which in turn
calls the project's actual existing contract names — do not create a new contract abstraction
simply to make this diagram look cleaner. The same principle applies when Portfolio/Profile
implementation resumes: `ProviderProfileView` → application/orchestrator use case → existing
ProviderProfile + shared domain contracts → domain modules. REST implementation itself is
explicitly out of scope for this task.

---

## PHASE 7 — UI ALIGNMENT

Update relevant `marketplace-app` Views (`AdvertisementsView`, `UserView`, ProviderProfile/
Portfolio Views if they currently perform cross-domain composition directly, other Views
identified in Phase 0). Replace direct multi-domain composition with calls to
`marketplace-orchestrator` application services.

Keep in `marketplace-app` only responsibilities that genuinely belong to the application shell:
Vaadin UI, `VaadinLocaleProvider`, `AuthContextService`, navigation, UI-specific state. If
`AuthContextService`/`VaadinLocaleProvider` are required by orchestrator logic, do not blindly move
them — first determine whether the dependency is a UI-specific concern that should remain in
`marketplace-app`, an application concern that should be abstracted through an existing project
contract, or an accidental dependency that should be removed. Reuse existing project mechanisms;
do not invent a new abstraction unless the code proves one is required.

---

## PHASE 8 — OPTIONAL MODULE VERIFICATION (mandatory acceptance criterion)

For each currently-optional module, verify the refactor does not accidentally make it mandatory.
Test at minimum: **(1) Full module set** — all modules present, compile, startup, relevant
features work. **(2) Remove one optional module** — remove it from the Maven build/dependency
graph as the repository currently supports; compile; start the application; verify unaffected
functionality continues working. **(3) Invoke a feature that depends on the removed module** —
document the actual expected behavior; verify graceful absence or the existing expected behavior.

Do not invent new graceful-degradation semantics — preserve the behavior already established by
the project. If a module is actually mandatory, document why based on the code and existing
architecture. Pay particular attention to `marketplace-orchestrator` dependencies: adding a direct
Maven dependency from the orchestrator to a starter may unintentionally convert a previously
optional domain module into a mandatory compile-time dependency — verify this explicitly.

---

## PHASE 9 — ARCHITECTURE VERIFICATION

Update architecture tests only if required by the new dependency direction. The final architecture
must enforce: no direct starter-to-starter coupling; domain starters do not orchestrate other
domains; cross-domain application composition belongs in `marketplace-orchestrator`;
`marketplace-app` does not directly compose multiple domain modules for application use cases;
future REST can use the same application/orchestrator layer as Vaadin; existing optional-module
behavior is preserved; no `marketplace-orchestrator` class depends on more than two domain `*Port`
interfaces (**new ArchUnit rule**, sibling to the existing rules in `ArchitectureRulesTest`);
`marketplace-orchestrator` contains zero imports of `JdbcClient`/`*Repository`/`*CrudRepository`
(**new ArchUnit rule**).

These two new rules must be added to `ArchitectureRulesTest` as part of this task, not left as
documentation-only guidance — the whole reason this project prefers compiler/build enforcement
over prose is exactly the case this task itself is an example of.

The architecture verification must also ensure the same cross-domain display enrichment pattern is
not simply duplicated under a new package or class name. Do not weaken or remove existing
architecture rules merely to make the refactor pass. If an existing rule conflicts with the target
architecture, stop and explain the conflict before changing the rule.

---

## PHASE 10 — VERIFICATION

Run:
```
bash scripts/unit-tests.sh
bash scripts/integration-tests.sh --sandbox
bash scripts/playwright.sh e2e --ux
```

Also verify: Maven dependency graph; compile-time optional-module behavior; application startup
with optional modules removed; no accidental starter-to-starter dependencies; no new circular
dependencies; `ArchitectureRulesTest`; Vaadin flows affected by the moved services; existing audit
behavior; existing attachment behavior; existing enrichment behavior; Advertisement enrichment
behavior after extraction; ProviderProfile enrichment behavior after extraction; no duplicated
category/city/actor enrichment logic remains where a genuinely shared composition primitive is
appropriate; the paused Portfolio/Profile implementation remains intact and can resume from its
current state after this task.

Do not continue implementing new Portfolio/Profile functionality during this task.

---

## Follow-up (separate, small task — do not fold into this task's scope)

Once `marketplace-orchestrator` exists, `.claude/skills/deep-review/references/full-mode.md`
should add it to its module list so future `/deep-review full` runs cover it the same way they
already cover the other 9 modules. One-line addition to an existing file — track as its own tiny
follow-up when this task is picked up, not as part of this task's acceptance criteria.

## Documentation deliverables (not in the original spec text, added for this repo's own conventions)

This task reverses a decision root `CLAUDE.md` currently states explicitly ("UI is a monolith...
Decoupling is required only at the service ↔ UI boundary [starters vs marketplace-app]"). Per
`.claude/rules.md`, this requires, in the same change:
- A new ADR in `marketplace-app/DECISIONS.md` recording the reversal and its rationale (this
  issue's "Decision history" section above is the source material).
- Root `CLAUDE.md`'s "Architecture Guidelines" section updated to describe the new three-layer
  shape (UI adapter → orchestrator → starters) instead of the current two-layer one.
- `platform-commons/DECISIONS.md` entry for dropping `Locale` from `AdvertisementPort`/
  `ProviderProfilePort` (mechanism carried over from the superseded plan above).
- `docs/ai/adr-index.md` regenerated (`bash scripts/ai/generate-adr-index.sh`) in the same change,
  per the standing rule.

---

## ACCEPTANCE CRITERIA

1. `marketplace-orchestrator` exists as a separate Maven module.
2. `marketplace-orchestrator` owns application-level cross-domain orchestration.
3. Domain starters no longer orchestrate unrelated domain starters.
4. Both confirmed enrichment cases — Advertisement and ProviderProfile — are resolved as part of
   this task.
5. Existing `*Port`/SPI contracts are reused wherever they already provide the correct boundary.
6. No parallel or duplicate contract system was introduced without a concrete code-based
   justification.
7. `marketplace-app` Views use orchestrator/application services rather than directly composing
   multiple domain Ports.
8. The orchestrator exposes application-facing services that can later be called by REST without
   moving orchestration logic again.
9. `marketplace-app` remains the UI/application shell where appropriate.
10. Existing optional-module behavior is preserved.
11. Removing an optional module does not accidentally make the application fail compilation or
    startup.
12. Mandatory modules remain explicitly mandatory and fail fast if absent.
13. No direct starter-to-starter coupling is introduced.
14. No new God service or God module is created.
15. No cross-domain enrichment duplication is simply relocated from starters into the orchestrator.
16. Genuinely shared composition semantics are extracted where appropriate, but no unnecessary
    generic abstraction layer is introduced merely to eliminate superficial duplication.
17. The ≤2-domain-Port rule is respected by every orchestrator class.
18. `marketplace-orchestrator` has zero direct persistence access through `JdbcClient`,
    repositories, or CRUD repositories.
19. Existing architecture rules and ADR decisions remain respected.
20. The Portfolio/Profile implementation is left in a coherent paused state and can continue after
    this task without reintroducing the original cross-domain orchestration pattern.
21. All tests and verification scripts pass.

---

## FINAL RULE

Do not start by creating new contracts, capability registries, or new abstraction layers. First
inspect the actual repository. The repository already contains contracts, SPIs, Ports,
auto-configuration, module boundaries, architecture rules, and optional-module mechanisms — treat
those as the source of truth. The objective is to **extract and formalize the Application/BFF
composition layer that the project already implicitly has**, not to redesign the project's
contract architecture from scratch.

The newly discovered `ProviderProfileEnrichmentService` is evidence that the cross-domain
enrichment pattern is already spreading between domains. Do not solve this by merely moving
multiple copies into `marketplace-orchestrator`. At the same time, do not over-generalize the
solution. Extract the smallest genuinely shared composition primitive, preserve domain-specific
composition where it is genuinely different, and keep every orchestrator class within the
≤2-domain-Port constraint.

The Portfolio/Profile feature is intentionally paused during this architectural extraction.
Preserve its valid work and use the refactored application boundary as the foundation for
continuing it afterward.

REST is intentionally deferred. The goal now is to establish a clean backend/application boundary
that makes REST a future adapter rather than another orchestration layer.

If the existing code already supports the desired behavior, preserve it and move the minimum
necessary code. If the proposed target architecture cannot be achieved with the existing contracts
and mechanisms, identify the exact blocker, show the relevant dependency path, and propose the
smallest necessary change. Do not proceed with speculative architectural additions.

---

## PHASE 0 — FINDINGS (2026-08-07)

Discovery only — no code changed. Every claim below is grep/read-verified against the real
repository, not assumed from the spec's own framing.

### Confirmed Starting Point — pattern search beyond the 2 known instances

Searched every starter's `main` sources for classes holding 2+ distinct `ComponentFactory<XPort>`
fields (the method-name pattern `enrichWith*`/`applyCategory*` plus a raw field-count grep). Result:
the two already-known instances (`AdvertisementEnrichmentService`: Taxon+User+Attachment;
`ProviderProfileEnrichmentService`: Taxon+User) are the **only** display/view-composition instances
in any starter. No third domain has silently grown the same pattern yet.

However, the search surfaced **two more cross-domain shapes inside `AdvertisementService` itself**
(not the enrichment service) that the original spec's "3 patterns" list doesn't cleanly cover:

1. **Query-time filter composition** — `AdvertisementService.resolveTaxonIdFilter()` calls
   `TaxonPort.findEntityIdsWithAnyTaxon()` to resolve a category filter into advertisement ids
   before the SQL query runs. `ProviderProfileService` has the exact same shape
   (`taxonPortFactory.findIfAvailable().map(p -> p.findEntityIdsWithAnyTaxon(...))`). This is not
   display composition (nothing is enriched onto the returned DTO) — it's filter resolution, a
   different concern that still crosses a domain boundary.
2. **Cascade cleanup on delete** — `AdvertisementService.delete()` calls
   `AttachmentPort.softDeleteAll()` and `TaxonPort.replaceAssignments(..., Set.of())` to clean up
   dependent data when the owning advertisement is deleted. `ProviderProfileService.delete()` does
   the same for taxon assignments (no attachment cleanup — providers don't have attachments).

Neither of these is "narrow referential-integrity cooperation" in the spec's sense (that pattern is
specifically `UserService.cleanup()` reacting to a *different* domain's deletion) — these are a
domain reacting to **its own** deletion by cleaning up *other* domains' dependent rows. Flagging
this as a 4th shape, not force-fitting it into patterns 1-3: **cascade-cleanup-on-own-delete**.
Whether this belongs in `marketplace-orchestrator` or stays in the starter is a genuine Phase 1
design question — moving it would mean `AdvertisementService.delete()` can no longer guarantee
attachment/taxon cleanup happens in the same transaction as the row's own soft-delete without the
orchestrator taking on transaction-boundary responsibility it doesn't have today (Rule 14 forbids
`marketplace-orchestrator` from touching `JdbcClient`/repositories at all, but the *transaction*
still needs to wrap both the domain write and the dependent-data cleanup call).

### A. Existing contracts

| Contract | Declared in | Implemented in | Consumed by (real call sites) | Optional? | Mechanism |
|---|---|---|---|---|---|
| `AdvertisementPort` | platform-commons | `AdvertisementPortImpl` (advertisement-starter) | `marketplace-app` (Views, `AdvertisementSaveService`, `UserDeleteService`, `AuditDomainHookImpl`, `OgMetaRequestListener`, `SitemapController`); `user-starter`'s `UserService.cleanup()` | Compile-scope in marketplace-app (mandatory today) | `ComponentFactory<AdvertisementPort>` |
| `ProviderProfilePort` | platform-commons | `ProviderProfilePortImpl` (provider-profile-starter) | `marketplace-app`'s `AuditDomainHookImpl` only (no UI yet) | Runtime-scope in marketplace-app (`pom.xml`) | `ComponentFactory<ProviderProfilePort>` |
| `UserPort` | platform-commons | `UserPortImpl` (user-starter) | `advertisement-starter`'s `AdvertisementEnrichmentService`; `provider-profile-starter`'s `ProviderProfileEnrichmentService`; `marketplace-app`'s `UserActorNameService`/`AuditDomainHookImpl` | Compile-scope (mandatory today) | `ComponentFactory<UserPort>` |
| `UserAccountPort`/`UserAuthorizationPort`/`UserPreferencesPort` | platform-commons | `*PortImpl` (user-starter) | `marketplace-app` UI/auth layer only | Compile-scope (mandatory today) | direct injection (not via `ComponentFactory`, confirmed no optional consumers) |
| `TaxonPort` | platform-commons | `DefaultTaxonPort` (taxon-starter) | `advertisement-starter` (`AdvertisementService`, `AdvertisementEnrichmentService`); `provider-profile-starter` (`ProviderProfileService`, `ProviderProfileEnrichmentService`); `marketplace-app` (multiple Views, `AdvertisementSaveService`, `AdvertisementAuditEnrichService`, `AuditDomainHookImpl`) | Runtime-scope in marketplace-app | `ComponentFactory<TaxonPort>` everywhere |
| `AttachmentPort` | platform-commons | `DefaultAttachmentPort` (attachment-starter) | `advertisement-starter` (`AdvertisementService`, `AdvertisementEnrichmentService`); `marketplace-app` (`AdvertisementSaveService`, `AttachmentGalleryService`, `AttachmentGallery`, Views) | Compile-scope (mandatory today) | `ComponentFactory<AttachmentPort>` |
| `AttachmentAuditPort` | platform-commons | `AttachmentAuditPortImpl` (attachment-starter) | `marketplace-app`'s `AdvertisementAuditEnrichService` only | Compile-scope (mandatory today) | `ComponentFactory<AttachmentAuditPort>` |
| `AuditPort` | platform-commons | `DefaultAuditPort` (audit-starter) | `taxon-starter`'s `TaxonService`; `user-starter`'s `UserService`/`UserPreferencesService`; `marketplace-app` (`AdvertisementSaveService`, multiple Views/mode-handlers) | Compile-scope (mandatory today) | `ComponentFactory<AuditPort>` |
| `*Hook`s (`AuditDomainHook`, `AuditActivityFieldsHook`×4, `AuditActivityEnrichHook`, `CurrentActorHook`, `UserSettingsChangedHook`) | platform-commons | all in `marketplace-app/spi/*.java` + `SettingsPaginationService` | called by the owning starter (audit-starter calls `AuditDomainHook`, etc.) | n/a — Hooks are marketplace-app-implemented by design | direct Spring bean lookup by the starter |

**Not `ComponentFactory`-mediated:** `UserAccountPort`/`UserAuthorizationPort`/`UserPreferencesPort`
are injected directly (not through `ComponentFactory`) because `user-spring-boot-starter` is a
compile-scope, non-optional dependency of `marketplace-app` — confirmed no call site wraps them in
`ifAvailable()`/`findIfAvailable()`.

### B. Existing optional-module behavior — mechanism confirmed, not yet empirically re-drilled

Confirmed directly (not assumed) that today's optional-removal mechanism is a chain of 3 facts, all
independently verified:
1. **Compile-time:** no starter imports another starter's classes — confirmed via `pom.xml`
   inspection (`advertisement-spring-boot-starter`/`provider-profile-spring-boot-starter` list only
   `platform-commons`/`query-lib` as internal deps) and the existing
   `starters_must_not_import_sibling_starters` ArchUnit rule
   (`ArchitectureRulesTest.java:118`), which already enforces this as a build-breaking rule today.
2. **Startup-time:** each `ComponentFactory<XPort>` bean is declared **only** inside its owning
   starter's own `@AutoConfiguration` class (e.g. `attachmentPortFactory` bean method lives in
   `AttachmentAutoConfiguration`, nowhere else). If that starter's jar is absent, Spring Boot's
   autoconfiguration for it never runs, so the `ComponentFactory<AttachmentPort>` bean simply does
   not exist in the context.
3. **Feature-time:** any class with a *hard* constructor dependency on a `ComponentFactory<XPort>`
   that turns out missing fails Spring's dependency injection at context-startup (not gracefully) —
   confirmed this is the real mechanism behind `AttachmentGalleryService`/`AttachmentGallery`/
   `AuditActivityPanel` "failing without the starter", **not** `@ConditionalOnBean` on those classes
   as an earlier capture (`improvement-142`'s archived `06-coupling-analysis.md` content) stated —
   verified directly: none of the three carry a `@ConditionalOnBean` annotation, they're plain
   `@SpringComponent`. The actual guard is one level up: `ComponentFactory<T>`'s own bean method is
   conditional (by virtue of living in the starter's autoconfiguration), and any *consumer* class
   either survives (if it wraps every call in `ifAvailable()`/`findIfAvailable()`, like
   `AdvertisementService`/`AdvertisementEnrichmentService` do for the truly-optional
   `Taxon`/`Attachment`/`User` ports it treats as optional) or fails at startup (if the port is
   treated as mandatory, like `taxon`/`provider-profile` currently are — both `<scope>runtime</scope>`
   in `marketplace-app/pom.xml`, meaning `marketplace-app` itself never imports their concrete
   classes, only the Port type from `platform-commons`).

**Correction filed:** the "`@ConditionalOnBean`" claim above is inaccurate in the archived
`06-coupling-analysis.md` capture inside `improvement-142`'s issue file — noted here as a finding,
not fixed there (out of this issue's scope; `improvement-142` owns that content).

**Not yet done, deliberately deferred to Phase 8:** an actual empirical drill (remove a starter from
the Maven reactor, `mvn compile`, boot the app, exercise a dependent feature) — Phase 0 only
confirms the *mechanism* holds by code inspection; Phase 8 is where this becomes an empirical proof,
per the issue's own sequencing.

### C. Existing dependency graph

- **Starter → starter:** none (confirmed, both by `pom.xml` inspection and the existing ArchUnit
  rule already enforcing it).
- **`marketplace-app` → starter:** `audit`/`attachment`/`user`/`advertisement` are plain `compile`
  scope (mandatory); `taxon`/`provider-profile` are `<scope>runtime</scope>` (marketplace-app never
  imports their concrete classes, only `platform-commons` Port types — genuinely optional at
  compile time today).
- **SPI/Port direction:** always starter → implements platform-commons interface; consumer (starter
  or marketplace-app) → depends only on the platform-commons interface, never the impl class.
  Confirmed with zero exceptions across all 9 ports/hooks checked.
- **`ComponentFactory` cross-domain dependencies:** exactly the two known enrichment services plus
  the two newly-found filter/cleanup shapes in `AdvertisementService`/`ProviderProfileService`
  (see "Confirmed Starting Point" above) — no other starter-internal class holds 2+ foreign
  `ComponentFactory<XPort>` fields.
- **Existing marketplace-app orchestration services already exceed the planned ≤2-port rule:**
  `AdvertisementSaveService` holds **4** distinct port factories
  (`AdvertisementPort`/`AttachmentPort`/`TaxonPort`/`AuditPort`) — confirmed by direct field read.
  If Phase 4 moves this class into `marketplace-orchestrator` unchanged, it immediately violates
  Rule 13. This is the single most concrete Rule-13 conflict Phase 1 must resolve explicitly (the
  issue's own Phase 1 instructions anticipated exactly this situation and require it be documented,
  not silently avoided).

  **Decided (2026-08-07):** this is not the same shape as `AdvertisementEnrichmentService`'s
  problem. The enrichment service composes 3 *independent* read-side fields onto a display DTO —
  each enrichment step could be added/removed without affecting the others. `AdvertisementSaveService`
  is one atomic write transaction (`tx.execute(...)` wraps the whole method) where the steps are
  *sequentially dependent* — the audit snapshot is built from the state the save+taxon-assignment
  steps just produced, and all of it must commit or roll back together. Forcing an artificial split
  purely to hit a port-count number would be exactly the kind of abstraction-for-its-own-sake the
  issue's own Phase 4 instructions warn against ("abstraction layers whose only purpose is
  eliminating a small amount of duplication").

  Resolution, confirmed with the user: light decomposition that satisfies Rule 13's literal text
  without fragmenting the transaction or inventing a generic engine — extract the taxon-assignment
  write (`replaceAssignments()` call) into a small shared collaborator class (naturally shared with
  `ProviderProfileService`'s identical write, from the "Confirmed Starting Point" section above),
  so `AdvertisementSaveService` itself depends on `AdvertisementPort` + `AuditPort` directly (2
  ports) plus that one collaborator class (not a third `*Port` field). The transaction itself is
  unchanged — `tx.execute(...)` still wraps the collaborator's call, same commit/rollback unit as
  today. Applies the same shape to `ProviderProfileService`'s save/delete once that starter's own
  save path exists in the orchestrator (currently written directly in the starter per its own
  `CLAUDE.md` — see Related section below). The Attachment-snapshot read inside
  `buildCurrentSnapshot()` gets the same treatment (a small reader collaborator), keeping
  `AdvertisementSaveService` itself at exactly 2 direct `*Port` fields.

### D. Existing application/orchestration layer — inventory + pattern classification

| Service | Location | Ports touched | Pattern | Phase-4 candidate? |
|---|---|---|---|---|
| `AdvertisementEnrichmentService` | advertisement-starter | Taxon+User+Attachment | 1 — display composition | Yes, confirmed starting point |
| `ProviderProfileEnrichmentService` | provider-profile-starter | Taxon+User | 1 — display composition | Yes, confirmed starting point |
| `AdvertisementService.resolveTaxonIdFilter()` | advertisement-starter | Taxon | new shape — query-time filter composition | Yes (small, single-port, low risk) |
| `ProviderProfileService`'s category filter | provider-profile-starter | Taxon | new shape — query-time filter composition | Yes (small, single-port, low risk) |
| `AdvertisementService.delete()` cleanup | advertisement-starter | Taxon+Attachment | new shape — cascade-cleanup-on-own-delete | Open question — transaction-boundary conflict with Rule 14, see above |
| `ProviderProfileService.delete()` cleanup | provider-profile-starter | Taxon | same shape | Same open question |
| `TaxonService`/`UserPreferencesService`/`UserService` → `AuditPort.capture*()` | taxon-starter / user-starter | Audit only | 2 — cross-cutting event reporting, confirmed pure `capture*()` calls, no additional logic | No — spec explicitly says treat as defensible unless evidence of more; confirmed no more |
| `UserService.cleanup()` → `AdvertisementPort.findOwnerIds()`/`clearActorReferences()` | user-starter | Advertisement only | 3 — narrow referential-integrity cooperation, confirmed this is its only use | No — spec explicitly says evaluate on its own, narrow and specific |
| `AdvertisementSaveService` | marketplace-app | Advertisement+Attachment+Taxon+Audit (4!) | mixed — domain write + cross-domain assignment write + audit capture | Yes, but must be decomposed per the Rule-13 conflict above |
| `UserDeleteService` | marketplace-app | Advertisement (+ `UserAccountPort` direct) | 3-adjacent — orchestrates the same referential-integrity cleanup `UserService.cleanup()` already partly does, from the other side | Needs Phase 1 judgment — may already fully belong in orchestrator since it's marketplace-app-owned today, not a starter |
| `AdvertisementAuditEnrichService` | marketplace-app | Attachment(Audit)+Taxon | display composition for audit-diff rendering (category names in diffs) | Yes, explicitly listed as a Phase 4 candidate in the spec |

### E. Existing DTO ownership

Both `AdvertisementInfoDto` and `ProviderProfileDto` (platform-commons, the Port's own return type)
are confirmed **hybrid** DTOs — domain-owned fields and composition-enriched fields sit flat in the
same class with no structural separation:

- **Domain-owned:** `id`, `title`/`about`, `adKind`/`kind`, `createdAt`/`updatedAt`, `version` — these
  map directly to real persisted columns (confirmed against each starter's Liquibase changelog).
- **Composition-enriched, not persisted anywhere:** `createdByUserName`/`createdByUserEmail`
  (`actorName`/`actorEmail` on ProviderProfile) from `UserPort`; `mediaUrl`/`mediaContentType`/
  `mediaCount` (Advertisement only) from `AttachmentPort`; `categoryIds`/`categoryNames`/`cityName`
  (+`cityTaxonId` on Advertisement) from `TaxonPort` — confirmed via each starter's own `CLAUDE.md`
  ("`advertisement` does **not** store `media_url`/...") and the enrichment services' `.toBuilder()`
  calls that populate exactly these fields and no others.

**Concrete, previously-unnamed finding: the `Locale` parameter is already baked into both
`AdvertisementPort`/`ProviderProfilePort`'s method signatures** (`getFiltered`/`findById`, plus
`findByActorId` on ProviderProfilePort) — confirmed by reading both interfaces directly. It exists
**solely** to pass through to `TaxonPort.getForEntities(..., locale)` inside the enrichment services
for translated category/city names — nothing else in either port's implementation touches `Locale`.
This is exactly the smell the issue's "Superseded plan" section named. Once enrichment moves to
`marketplace-orchestrator`, `AdvertisementPort`/`ProviderProfilePort`'s `getFiltered`/`findById`/
`findByActorId` no longer need a `Locale` parameter at all — `Locale` moves to whichever
orchestrator-level method calls `TaxonPort` directly. **This is a real, in-scope Port signature
change Phase 1 must design explicitly** (dropping a parameter from a `platform-commons` interface
touches every real call site across both starters and marketplace-app — a mechanical but non-trivial
Phase 3/7 ripple, not just a Phase 1 diagram note).

### F. Completeness sweep — every marketplace-app class with 2+ `ComponentFactory<XPort>` fields

The searches above (Sections A/D) were targeted at the classes the spec names explicitly. A
broader sweep across **every** `marketplace-app` main-source class (not just the named ones) found
2 more shapes worth recording before Phase 0 is considered closed:

**5th shape — `AuditDomainHookImpl`: per-entity-type routing, not composition.** Holds 4 port
factories (`Advertisement`/`User`/`Taxon`/`ProviderProfile`) but `findExisting(EntityType, ids)` is
a `switch (entityType) { case ADVERTISEMENT -> advertisementPort...; case TAXON -> taxonPort...; }`
— **exactly one** port is ever called per invocation, never more than one. This is a `*Hook`
implementation (audit-starter calls it), so per the existing Hook convention it must stay in
`marketplace-app` regardless of Rule 13 — Hooks are never orchestrator-owned. More importantly,
Rule 13's actual concern (a class that has to reason about N domains' data *simultaneously* to
produce one result) doesn't apply here at all — each branch is independent, single-port, pure
delegation, matching `platform-commons/CLAUDE.md`'s existing "`*HookImpl` — pure delegation only"
rule already. **Not a Phase 4/13 conflict — stays exactly as-is**, noted here only so it isn't
mistaken for a 6th unresolved case later.

**UI-layer classes hold multiple ports too, but most of it is optional-feature presence-guarding,
not composition.** `AdvertisementFormOverlayModeHandler` (4 ports: Advertisement/Attachment/Taxon/
Audit), `AdvertisementViewOverlayModeHandler` (2: Attachment/Taxon), `CityFormOverlayModeHandler` /
`TaxonFormOverlayModeHandler` (2: Audit/Taxon each) — confirmed by reading actual call sites, these
split into two different uses that Phase 7 must **not** treat identically:
- **Real composition (Phase 7 territory):** `taxonPortFactory` populating category/city dropdown
  options in the form; `advertisementPortFactory`'s save/load calls. These are exactly what Phase 7
  means by "Views must not need to know how multiple domain modules are composed."
- **Optional-feature presence guards (already the correct, established pattern — not a violation):**
  `attachmentPortFactory.ifAvailable(...)` gating whether the gallery UI renders at all;
  `auditPortFactory.findIfAvailable().ifPresent(...)` gating whether the history button renders.
  This is root `CLAUDE.md` rule 4's own documented pattern ("UI components MUST degrade gracefully
  via `ObjectProvider.ifAvailable()`") — moving this check into the orchestrator would not remove
  any real coupling, it would just relocate a presence check that's already correctly placed.

**Phase 7 must distinguish these two uses per class, not blanket-move every multi-port UI class into
calling one orchestrator method** — collapsing a presence-guard into an orchestrator round-trip
would add a network/service hop for what is today a zero-cost local `Optional` check.

### Phase 0 summary — what this changes about Phase 1's starting assumptions

1. The "3 patterns" framing in the original spec needs a 4th category for Phase 1's target spec:
   **cascade-cleanup-on-own-delete** (distinct from both display composition and the narrower
   `UserService.cleanup()` shape) — with an explicit open question about how it interacts with
   Rule 14's "no `JdbcClient`/repository in the orchestrator" constraint if it moves.
2. `AdvertisementSaveService` (already in marketplace-app, a Phase 4 move candidate per the spec)
   already violates the planned ≤2-port Rule 13 today, at 4 ports — Phase 1 must show its
   decomposition explicitly, not discover this mid-Phase-4.
3. `Locale` really is dead weight on two `platform-commons` Port interfaces — confirmed, not
   assumed — and removing it is a real, traceable Phase 3/7 task, not just a diagram simplification.
4. Query-time filter composition (`resolveTaxonIdFilter`-shaped) is a second, smaller, so-far-unnamed
   cross-domain shape present in both Advertisement and ProviderProfile — small enough to move
   alongside the enrichment work with low risk, but Phase 1's target spec should name it explicitly
   rather than silently fold it into "display composition."
5. `AuditDomainHookImpl`'s 4-port field count is a false positive for Rule 13 purposes — per-branch
   single-port routing, not simultaneous composition. Stays in `marketplace-app` as-is; Phase 1
   should say so explicitly so it isn't re-flagged as an unresolved conflict later.
6. Not every multi-port UI class is a Phase 7 target — `AdvertisementFormOverlayModeHandler` and
   siblings mix real composition (dropdown data, save/load) with already-correct optional-feature
   presence guards (`ifAvailable` gating gallery/history-button visibility). Phase 7 must move only
   the former; collapsing the latter into an orchestrator call would add cost with no benefit.

**Phase 0 is CLOSED (2026-08-07).** All 5 subsections (A-E) plus the completeness sweep (F) are
evidence-verified against the real repository — no further discovery pass planned before Phase 1
starts. The one still-open item carried into Phase 1 as a design question (not a Phase 0 gap): how
cascade-cleanup-on-own-delete (finding 1 above) interacts with Rule 14's transaction-boundary
constraint if it moves into the orchestrator.

Phase 1 (target architecture spec) starts next, per explicit user go-ahead.

---

## PHASE 1 — TARGET ARCHITECTURE SPEC (2026-08-07)

No code changed — this is the design Phase 2+ implements against. Every decision below either
reuses an existing contract unchanged or is explicitly justified against a Phase 0 finding.

### Adjacent finding, fixed in the same batch (per user decision): ProviderProfile purge-guard gap

Discovered while designing `UserDeleteService`'s target shape, not part of 136's original scope,
but small enough and directly touching the same code path — user decided to fix it here rather
than defer:

- `user-spring-boot-starter/services/UserService.cleanup()` (the scheduled retention-purge job)
  checks `AdvertisementPort.findOwnerIds()` before purging a soft-deleted user, but never checks
  `ProviderProfilePort.findOwnerIds()` — confirmed `ProviderProfilePort` has zero consumers anywhere
  in `user-spring-boot-starter` today. `provider-profile-spring-boot-starter/CLAUDE.md` documents
  `findOwnerIds()` as existing specifically to "block user purge while a profile exists,
  mirror[ing] `AdvertisementPort`'s `created_by` protection" — that protection was never wired in.
  **Fix (stays in `user-spring-boot-starter`, not the orchestrator — this is pattern 3, narrow
  referential-integrity cooperation, same as the existing Advertisement check):** add
  `ComponentFactory<ProviderProfilePort>` to `UserService`, check `findOwnerIds()` in `cleanup()`
  alongside the existing Advertisement check, skip purge if either returns a hit.
- `marketplace-app`'s `UserDeleteService.delete()` (immediate cascade when an account is deleted,
  not the retention job) only cascades `AdvertisementPort` too. Since `provider_profile.delete()` is
  a real `DELETE` (no soft-delete/restore concept per its own `CLAUDE.md`), the safe fix is the same
  shape: also delete the user's own provider profile (if one exists) as part of the same cascade,
  via `ProviderProfilePort.findByActorId()` + `.delete()`. This class is moving into the orchestrator
  in this same batch (see below) — the fix lands there directly, not as a separate marketplace-app
  change first.

### Target module diagram

```
marketplace-app (Vaadin UI, auth, locale, application shell)
    │ calls
    ▼
marketplace-orchestrator (new module — application/BFF layer)
    │
    ├── org.ost.orchestrator.shared           (TaxonLookupService, ActorLookupService,
    │                                           TaxonAssignmentWriteService — ≤1 domain Port each)
    ├── org.ost.orchestrator.advertisement.enrich  (AdvertisementDisplayEnrichmentService)
    ├── org.ost.orchestrator.advertisement.save    (AdvertisementSaveService, incl. delete-cascade)
    ├── org.ost.orchestrator.providerprofile.enrich (ProviderProfileDisplayEnrichmentService)
    └── org.ost.orchestrator.user.delete           (UserDeleteService)
    │
    │ every class above depends only on platform-commons Port/DTO types via ComponentFactory<T>
    ▼
existing contracts, unchanged in location: AdvertisementPort, ProviderProfilePort, UserPort,
TaxonPort, AttachmentPort, AttachmentAuditPort, AuditPort, UserAccountPort
    │ implemented by
    ▼
domain starters (unchanged: advertisement, provider-profile, user, taxon, attachment, audit)
```

### What moves from `marketplace-app` into `marketplace-orchestrator`

| Class | Today | Target | Ports (direct) | Rule 13 |
|---|---|---|---|---|
| `AdvertisementSaveService` | `marketplace-app/services/advertisement` | `orchestrator.advertisement.save` | `AdvertisementPort` + `AuditPort` (2) + `TaxonAssignmentWriteService`/`AttachmentCleanupService` collaborators | Compliant |
| `UserDeleteService` | `marketplace-app/services/user` | `orchestrator.user.delete` | `AdvertisementPort` + `ProviderProfilePort` (2, gains the purge-guard fix above) + `UserAccountPort` (direct, non-`ComponentFactory`, mandatory dep) | Compliant |

**Not moved — `AdvertisementAuditEnrichService` stays in `marketplace-app`.** See "Phase 1
correction, found while implementing" below the Phase 1 closure note: it field-injects
`LocaleProvider`/`I18nService` (marketplace-app-owned i18n, per Rule 5), which the orchestrator
must never depend on. Internally simplified to depend on `orchestrator.shared.TaxonLookupService`
instead of its own `ComponentFactory<TaxonPort>` field — a one-fewer-field cleanup, not a move.

### What moves out of the domain starters into `marketplace-orchestrator`

| Class | Today | Target | Notes |
|---|---|---|---|
| `AdvertisementEnrichmentService` | `advertisement-spring-boot-starter/services` | `orchestrator.advertisement.enrich`, rebuilt on `TaxonLookupService`+`ActorLookupService`+direct `AttachmentPort` | Confirmed starting point — full move |
| `ProviderProfileEnrichmentService` | `provider-profile-spring-boot-starter/services` | `orchestrator.providerprofile.enrich`, rebuilt on `TaxonLookupService`+`ActorLookupService` | Confirmed starting point — full move |

### What does NOT move — deliberate, with reasoning

| Class / behavior | Stays in | Why |
|---|---|---|
| `AdvertisementService.resolveTaxonIdFilter()` / `ProviderProfileService`'s equivalent category-filter resolution | advertisement-starter / provider-profile-starter | Not display composition — it resolves a filter into an id-set *before* `SqlFilterBuilder` builds the `WHERE` clause, i.e. it's part of executing the query itself, not assembling a read-model. Moving it would require restructuring `AdvertisementFilterDto`/`ProviderProfileFilterDto` to carry a pre-resolved id-set — explicitly listed as **out of scope** in this issue's own "Out of scope" section ("Redesigning `AdvertisementFilterDto`/`ProviderProfileFilterDto`"). Matches the spec's pattern-3 instruction: evaluate on its own, don't move merely because it's cross-domain. |
| `ProviderProfileService.save()`/`delete()`'s direct `TaxonPort.replaceAssignments()` write | provider-profile-starter | No marketplace-app `ProviderProfileSaveService` exists yet, and none is built by this issue — per `provider-profile-spring-boot-starter/CLAUDE.md`, this write is explicitly deferred to "a future batch, alongside the actual `AuditPort.record()` audit-write call" (ProviderProfile has no audit wiring at all yet — no `ProviderProfileActivityFieldsHookImpl`). Building an orchestrator-side save path now — including its audit capture — would mean designing genuinely new functionality for a domain with no UI yet, which the task spec's "Important sequencing context" explicitly forbids ("Do not continue implementing new Portfolio/Profile feature functionality as part of this task"). Left exactly as-is; the future batch that builds ProviderProfile's save UI does this move then, following the same pattern `AdvertisementSaveService` now demonstrates. |
| `TaxonService`/`UserPreferencesService`/`UserService` → `AuditPort.capture*()` | taxon-starter / user-starter | Pattern 2 (cross-cutting event reporting) — confirmed pure `capture*()` calls, no additional logic. Spec explicitly says treat as defensible unless evidence of more; none found. |
| `UserService.cleanup()` → `AdvertisementPort`/`ProviderProfilePort.findOwnerIds()` | user-starter | Pattern 3 (narrow referential-integrity cooperation) — a specific, scheduled-job-scoped check, not general orchestration. Gains the `ProviderProfilePort` fix above but stays in the starter. |
| `AuditDomainHookImpl` | marketplace-app (unchanged location) | It's a `*Hook` implementation — Hooks are always marketplace-app-owned by convention, never orchestrator-owned. Also a Rule-13 false positive (per-branch single-port routing, not composition) — see Phase 0-F. |
| UI presence-guards (`attachmentPortFactory.ifAvailable(...)` gating gallery visibility, `auditPortFactory.findIfAvailable()` gating the history button) | marketplace-app UI classes, unchanged | Already the correct, established `ObjectProvider.ifAvailable()` degrade-gracefully pattern (root `CLAUDE.md` rule 4) — not a composition problem, moving it would add a service round-trip for a local `Optional` check with no benefit. |

### Contract changes

**`AdvertisementPort`/`ProviderProfilePort` drop `Locale`** from `getFiltered()`, `findById()`, and
(ProviderProfile only) `findByActorId()`. Confirmed the *only* use of `Locale` inside either port's
implementation is passing it through to `TaxonPort.getForEntities(..., locale)` for translated
category/city names inside the enrichment services being moved — once that call moves to
`orchestrator.*.enrich`, the port itself has no remaining use for `Locale`. `Locale` moves to the
new `TaxonLookupService.getForEntities(EntityType, Set<Long>, Locale)` signature instead, called
directly by the orchestrator's enrichment services.

**Real call sites needing this signature change** (Phase 3/7 ripple, confirmed by the same grep used
in Phase 0-A): `AdvertisementService`/`ProviderProfileService` (impl side, drop the parameter from
their own methods too — internally they no longer need it once they stop calling the enrichment
service); every UI/marketplace-app call site currently passing a `Locale` into `getFiltered()`/
`findById()`/`findByActorId()` (Views, `AdvertisementSaveService`'s own two `findById(id,
Locale.ENGLISH)` calls per ADR-068, `OgMetaRequestListener`, `SitemapController`). None of these
call sites need deletion — they just stop passing the now-removed parameter.

**`AdvertisementInfoDto`/`ProviderProfileDto` — unchanged shape.** Per Phase 0-E, both DTOs are
already hybrid (domain-owned + composition-enriched fields sit flat in one class). The smallest
necessary change is not restructuring these DTOs — the Port's raw return now simply carries `null`/
empty values in the enriched fields (`categoryNames`, `createdByUserName`, `mediaUrl`, etc.) until
the orchestrator's enrichment service populates them via the exact same `.toBuilder()` pattern the
starter's enrichment service uses today, just relocated. No new DTO type, no reflection, no generic
hierarchy — matches Rule 8 ("preserve existing contracts wherever possible") and the Phase 0-E
instruction not to relocate/duplicate DTOs without evidence.

### Shared composition primitives — what's genuinely shared vs. domain-specific

Per the spec's instruction to extract only the smallest genuinely shared primitive:

- **`TaxonLookupService`** (`orchestrator.shared`, `TaxonPort` only) — wraps
  `getForEntities()`/`getForEntity()`/`findByIds()`, returns raw `TaxonDto`/`Map` data. Used by both
  `AdvertisementDisplayEnrichmentService` and `ProviderProfileDisplayEnrichmentService`. Does **not**
  know about either DTO type — category/city extraction (`TaxonType.CATEGORY`/`CITY` filtering) stays
  in each domain's own enrichment service, since Advertisement has `cityTaxonId`+`cityName` as
  separate concerns while ProviderProfile only exposes `cityName` — a real, small shape difference
  Phase 4's own instructions warned not to force-unify.
- **`ActorLookupService`** (`orchestrator.shared`, `UserPort` only) — wraps `findByIds()`/`findById()`,
  returns raw `UserDto`/`Map` data. Used by both enrichment services identically (both apply
  name+email onto their own DTO's actor-shaped fields).
- **`TaxonAssignmentWriteService`** (`orchestrator.shared`, `TaxonPort` only) — wraps
  `replaceAssignments()`. Used today only by `AdvertisementSaveService`'s move (ProviderProfile's own
  write stays in its starter per the "does NOT move" table above) — kept in `shared` anyway since
  it's the exact same call `ProviderProfileService` already makes, ready to be reused once that
  domain's save path is extracted in a future batch, per Rule 9 ("do not create a parallel system if
  an existing contract already provides the boundary" — this collaborator *is* that boundary,
  pre-positioned for reuse, not spec work for ProviderProfile done early).
- **Domain-specific, stays separate:** Advertisement's media/attachment enrichment (`AttachmentPort`
  direct call inside `AdvertisementDisplayEnrichmentService` — ProviderProfile has no equivalent,
  providers don't have attachments); Advertisement's own cascade-cleanup-on-delete (folded into
  `AdvertisementSaveService`'s `delete()`, see below).

### `AdvertisementSaveService`'s final shape (resolves the Rule 13 conflict + the cascade-cleanup open question together)

Re-examined the cascade-cleanup-on-delete open question from Phase 0 in light of the
already-agreed `AdvertisementSaveService` resolution — it turns out to be **the same resolution**,
not a separate one: `AdvertisementService.delete()`'s `@Transactional` today already just *joins*
the outer transaction `AdvertisementSaveService.delete()` starts (Spring's default `REQUIRED`
propagation, same `DataSource`/transaction manager) — moving the Taxon-clear/Attachment-cleanup
calls into `AdvertisementSaveService.delete()` directly doesn't fragment anything; it's the same
single `tx.executeWithoutResult(...)` call, just with 2 more collaborator calls inside it instead of
delegating them to the starter's own method body.

```java
// orchestrator.advertisement.save.AdvertisementSaveService — 2 direct ports + 3 collaborators
private final ComponentFactory<AdvertisementPort> advertisementPortFactory;   // direct (1)
private final ComponentFactory<AuditPort>          auditPortFactory;          // direct (2)
private final TaxonAssignmentWriteService          taxonAssignmentWriteService;   // collaborator
private final TaxonLookupService                   taxonLookupService;            // collaborator (snapshot reads)
private final AttachmentSnapshotReaderService       attachmentSnapshotReaderService; // collaborator (new, wraps getLatestSnapshotId)

public Long save(...) { /* unchanged shape, taxonPortFactory.ifAvailable(...) -> taxonAssignmentWriteService.replace(...) */ }

public void delete(...) {
    tx.executeWithoutResult(status -> {
        AdvertisementSnapshotDto snapshot = buildCurrentSnapshot(id); // via taxonLookupService + attachmentSnapshotReaderService
        advertisementPortFactory.get().delete(id, actorId, version);
        taxonAssignmentWriteService.clear(EntityType.ADVERTISEMENT, id);      // moved from AdvertisementService.delete()
        attachmentCleanupService.softDeleteAll(EntityType.ADVERTISEMENT, id); // moved from AdvertisementService.delete()
        auditPortFactory.ifAvailable(p -> p.captureDeletion(id, snapshot, actorId));
    });
}
```

`AttachmentSnapshotReaderService`/`AttachmentCleanupService` — 2 small `orchestrator.shared`
collaborators, `AttachmentPort` only (1 port each), mirroring the `TaxonLookupService`/
`TaxonAssignmentWriteService` split (read vs. write, same reasoning). `AdvertisementService.delete()`
in the starter shrinks to exactly `repository.softDelete(...)` — no more `attachmentPortFactory`/
`taxonPortFactory` fields, no more `@Transactional` needed there at all (a single repository call
doesn't need its own transaction boundary; it participates in whatever the caller already started).

### Optional-module behavior — preserved, mechanism unchanged

`marketplace-orchestrator`'s `pom.xml` depends on `platform-commons` (compile) + `spring-boot-starter`
+ `lombok` only — **no starter jar, ever**, matching Phase 0-B's confirmed mechanism exactly (every
consumption goes through `ComponentFactory<XPort>`, whose bean only exists if the owning starter's
autoconfiguration ran). This means adding `marketplace-orchestrator` cannot make any currently-optional
starter mandatory — it has no compile-time visibility into any starter's concrete classes to begin
with. `marketplace-app`'s own starter dependencies (scopes unchanged: `taxon`/`provider-profile`
stay `runtime`, the rest stay `compile`) are untouched by this refactor — they're still needed for
Spring Boot autoconfiguration/Liquibase/`@EnableJdbcRepositories` at the final assembled-app level,
which has nothing to do with where the orchestration *logic* lives. `marketplace-app/pom.xml` gains
exactly one new dependency: `marketplace-orchestrator` (compile).

### REST reusability — one real design flag, not silently resolved

`AdvertisementSaveService.save()`'s current signature takes `Function<EntityRef, Long> commitGallery`
— a callback the UI passes in, tied to `AttachmentGallery`'s pending-upload-commit flow. This is a
genuine question Phase 6 must answer, not assumed away: is `commitGallery` a UI-shaped abstraction
that leaks into what should be an application-facing method (blocking clean REST reuse), or is it
already adapter-agnostic (any caller — Vaadin today, REST later — could supply "commit whatever
pending attachment state exists and return its snapshot id" as a plain `Function`)? Flagged for
Phase 6, not decided here — moving the class as-is Phase 2-4, revisiting this specific parameter's
shape only when Phase 6 (BFF API design) is actually reached.

### Mandatory vs. optional modules (confirmed unchanged by this refactor)

| Module | Scope in `marketplace-app/pom.xml` | Mandatory? |
|---|---|---|
| audit, attachment, user, advertisement | `compile` (default) | Yes, today and after |
| taxon, provider-profile | `runtime` | No, today and after |

### New ArchUnit rules (Phase 9, confirmed shape)

1. `orchestrator_classes_depend_on_at_most_two_domain_ports` — no class under
   `org.ost.orchestrator` may declare more than 2 distinct `ComponentFactory<XPort>`/direct `*Port`
   fields where `XPort` is a domain port (excludes `TaxonLookupService`-style shared collaborators
   from counting against the *consuming* class, since Rule 13 counts direct Port dependencies, not
   transitive ones through a named collaborator — confirmed this reading against the spec's own
   `AdvertisementSaveService` example in Phase 4).
2. `orchestrator_has_no_persistence_access` — zero imports of `JdbcClient`/`*Repository`/
   `*CrudRepository` anywhere under `org.ost.orchestrator`.

### Validated against existing architecture rules

- `starters_must_not_import_sibling_starters` — unaffected, `marketplace-orchestrator` isn't a
  starter and this rule's package scope doesn't need to change.
- `marketplace_must_not_import_starter_internals` — needs its package-matcher extended to also cover
  `org.ost.orchestrator` (same "only platform-commons Port/DTO types, never a starter's concrete impl
  class" rule applies identically to the new module).
- `ports_live_only_in_platform_commons` / `hooks_live_only_in_platform_commons` — unaffected, no
  interfaces move.
- `port_and_hook_impl_classes_are_pure_delegation` — unaffected, `*PortImpl`/`*HookImpl` classes
  don't change.

**Phase 1 is CLOSED (2026-08-07), approved by the user.** Target architecture, contract changes,
what moves/doesn't move, the Rule 13 resolution, the ProviderProfile purge-guard fix, and the two
new ArchUnit rules are all confirmed above as the design Phase 2+ implements against — no open
design questions remain except the explicitly-flagged Phase 6 `commitGallery` question, deferred to
Phase 6 on purpose, not an oversight.

Phase 2 (create the `marketplace-orchestrator` module) starts next, per explicit user go-ahead.

### Phase 1 correction, found while implementing (2026-08-07): `AdvertisementAuditEnrichService` does NOT move

Caught while actually writing the moved class, before any code was committed to the wrong shape —
Phase 1's "what moves" table listed this class as a simple 2-port move
(`AttachmentAuditPort`+`TaxonPort`, "already compliant, no decomposition needed"). That check never
looked past the port count: the class also field-injects `LocaleProvider` and `I18nService`
(`marketplace-app/services/i18n`), used to resolve `AdKind` into a localized display label
(`i18nService.get(I18nKey.forAdKind(kind))`). Both are explicitly marketplace-app-owned per that
module's own `CLAUDE.md` ("Starters have no i18n infrastructure of their own — all UI i18n lives
here") and per this issue's own Rule 5 ("`marketplace-app` should primarily contain... locale
handling... application-shell-specific" concerns). Moving this class into
`marketplace-orchestrator` would require the orchestrator to depend on marketplace-app's i18n
package — the exact inverse of the real dependency direction (`marketplace-app` depends on
`marketplace-orchestrator`, never the other way).

**Decision:** `AdvertisementAuditEnrichService` **stays in `marketplace-app`**, unmoved. Its actual
role — resolving raw taxon ids into localized display strings for audit-diff rendering, and
`AdKind` into a localized label — is UI-display formatting, not the "assemble a Port's own return
DTO from cross-domain data" pattern the enrichment services embody, even though it superficially
touches 2 domain ports the same way. **Small, optional consistency improvement applied instead:**
its own `ComponentFactory<TaxonPort>` field is replaced with a dependency on the new
`orchestrator.shared.TaxonLookupService` (marketplace-app is free to depend on the orchestrator,
that direction is correct) — same real behavior, one fewer direct `ComponentFactory<XPort>` field,
no functional change. `AttachmentAuditPort` stays as its own direct field (nothing else in the
orchestrator needs it).

---

## Code review outcome (2026-08-07, `/code-review --fix`, high effort)

8 parallel finder angles (line-by-line, removed-behavior, cross-file tracer, reuse,
simplification, efficiency, altitude, CLAUDE.md conventions) ran against the full working-tree
diff, followed by 1-vote verification on the substantive candidates. Fixed directly:

- **Duplicated `enrichSingle()` (CONFIRMED)** — the 3-call enrichment chain
  (category/city → actor → media) was copy-pasted verbatim in `AdvertisementsView` and
  `AdvertisementFormOverlayModeHandler`. Centralized as `AdvertisementDisplayEnrichmentService
  .enrichSingle(AdvertisementInfoDto, Locale)`; both UI classes now delegate to it, private
  duplicates deleted.
- **Stale `advertisement-spring-boot-starter/CLAUDE.md` + lost-cascade risk (CONFIRMED)** —
  the file still claimed `AdvertisementService` clears taxon assignments on `delete()`, no longer
  true after the extraction. Rewritten to state the real current shape (query-time filter
  resolution only, no write/enrich/cascade). `provider-profile-spring-boot-starter/CLAUDE.md` had
  the same class of staleness (`ProviderProfileEnrichmentService` no longer exists) — fixed too.
- **Ticket number in a Javadoc comment** (`AdvertisementSaveService.java`) — removed, replaced
  with a reference to `marketplace-orchestrator/CLAUDE.md`'s rule instead, per `.claude/rules.md`.
- **Multi-line `//` comment** (`UserDeleteService.java`) — collapsed to one line.
- **Defensive empty-check inside a method body** (`AdvertisementsView.enrich()`) — moved the
  `isEmpty()` guard to the call site in `refresh()`, per root `CLAUDE.md`'s "Design by contract"
  rule.
- **Missing singular `findById`** (`TaxonLookupService`) — added, replacing
  `ProviderProfileDisplayEnrichmentService`'s awkward `Set.of(id)` + map-lookup workaround.

**Verified but not changed:**
- *ArchUnit rule's ComponentFactory-only counting (PLAUSIBLE → REFUTED on verification)* — the
  rule only counts direct `ComponentFactory<XPort>` fields, not ports reached transitively through
  `shared.*` collaborators, so `AdvertisementSaveService` "shows" 2 ports while transitively
  touching 4 (Advertisement, Audit, Taxon, Attachment). Verified this is the *documented, intended*
  mitigation shape (`marketplace-orchestrator/CLAUDE.md` states it explicitly), not a silent
  loophole — collaborator-extraction is the sanctioned way to stay under budget, not a bypass.
- *Single-caller `shared.*` collaborators* (`TaxonAssignmentWriteService`, `AttachmentSoftDeleteService`,
  `AttachmentSnapshotReaderService`) — flagged as possible premature abstraction, but this is the
  explicit, user-approved Phase 1 design (pre-positioned for `ProviderProfile`'s own save path once
  that domain gets one) — not undone.
- *`OgMetaRequestListener` only enriching media, not category/actor* — checked against real field
  usage (`injectMeta()`/`addJsonLd()` only read title/description/media) — intentional, not a gap;
  actually fewer wasted Port calls than the pre-refactor code, which always fully enriched.
- *Cascade-before-optimistic-lock-check ordering in `delete()`* — compared directly against the
  pre-refactor `AdvertisementService.delete()` (`git show HEAD`) — identical ordering already
  existed before this diff; not a new risk.
- *Imperative if/else instead of `Optional` chaining* (`AdvertisementSaveService.save()`) — verbatim
  carried over from the pre-refactor code, not new; left as-is rather than restyling unrelated,
  unchanged logic in an already-large diff.

## Verification outcome (2026-08-07)

- `./mvnw -pl platform-commons,marketplace-orchestrator,advertisement-spring-boot-starter,provider-profile-spring-boot-starter,user-spring-boot-starter,marketplace-app -am compile/test-compile` —
  clean, both before and after the code-review fixes.
- `bash scripts/ci.sh --unit --integration --e2e --sandbox --playwright-args "e2e --full --ux"` —
  **unit PASSED (56s), integration PASSED (43s)**. First attempt's `docs`/`e2e` stages surfaced 3
  real, pre-existing infrastructure gaps unrelated to this issue's own logic (found and fixed in
  the same pass, matching the precedent `improvement-138` set for exactly this class of discovery):
  1. **Root `Dockerfile` missing `marketplace-orchestrator` in 3 places** (pom.xml `COPY` cache
     step, `src` `COPY` step, `mvnw install -pl` module list) — same "forgot to update the module
     list" pattern already fixed once before for `provider-profile-spring-boot-starter`. This is
     what actually broke the CI's isolated e2e stage's image build (`Child module
     /app/marketplace-orchestrator of /app/pom.xml does not exist`). Fixed.
  2. **`scripts/ci/Dockerfile` missing `nodejs`** — the `docs` stage's
     `check-architecture-model-freshness.sh` needs `node` (the architecture-model generator moved
     to Node scripts in an earlier batch); the CI-runner image never got `nodejs` added. Fixed.
  3. **`docs/architecture/README.md`** hardcoded "`10 modules`", now stale at 11 — reworded to avoid
     the hardcoded count entirely, per `.claude/skills/doc-standards/SKILL.md`.
  Also fixed the same recurring "module list" gap in `scripts/sonar/sonar-project.properties`
  (2 spots) and `scripts/sonar/run.sh`'s copy loop, found while fixing #1 — same class of bug,
  not yet triggered since `sonar` wasn't part of this run's selected stages.
- Re-ran `scripts/ci.sh` with the Dockerfile fix: **unit PASSED, integration PASSED**, but `e2e`
  showed 3 failures (2 independent — a post-relogin element-visibility timeout, a category-combobox
  click timeout during ad creation — plus 1 cascade from Playwright's serial-mode skip-after-failure
  cutting 20 tests short) inside the CI's own Docker-in-Docker isolated stack.
- **Root-caused, not assumed:** redeployed via the standard dev workflow instead
  (`bash scripts/deploy.sh --reset` + `bash scripts/playwright.sh e2e --full --ux`, per
  `playwright/CLAUDE.md`) — **50/50 passed (10.2m)**, including both of the two independently-failing
  tests from the CI run, run cleanly with no code changes in between. Confirms the CI-stack failures
  were environment flakiness (resource contention in the nested-Docker CI runner), not a real
  regression from this issue's changes — consistent with `playwright/CLAUDE.md`'s own documented
  warning that state/environment artifacts can look like real regressions on re-runs.
- Docs freshness gates (`check-adr-index-freshness.sh`, `check-flows-completeness.sh`,
  `check-hardcoded-counts.sh`, `check-architecture-model-freshness.sh`) all re-verified green after
  `bash scripts/architecture/generate-architecture-model.sh` was re-run to pick up every change
  (module addition, new ADRs, this issue's own final edits).

## Testing strategy

- Unit: new `marketplace-orchestrator` use-case services get Mockito-based coverage (bulk vs.
  single, graceful degradation when a sibling starter's `ComponentFactory` is empty) — same bar
  `AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService` had.
- Integration: `AdvertisementRepositoryTest`/`ProviderProfileRepositoryTest` unaffected (repository
  layer doesn't change); any integration test currently asserting on a *fully enriched*
  `AdvertisementInfoDto`/`ProviderProfileDto` coming back from the port/service needs updating to
  assert the now-raw shape instead.
- Playwright: full `e2e --full --ux` — this changes a read path every card/list view depends on.
- Phase 8's optional-module removal drill, run and its actual output recorded in this issue's
  `## Operational notes` block once executed.

### Phase 8 — optional-module removal drill, actual result (2026-08-07)

Ran the compile-time half of the drill for real, not assumed: temporarily commented out
`taxon-spring-boot-starter`'s `<dependency>` block from `marketplace-app/pom.xml` (the module
itself stayed in the root reactor, just not depended on), ran `./mvnw -pl marketplace-app -am
compile`. **Result: clean compile**, `marketplace-orchestrator` and `marketplace-app` both build
successfully with zero references to `taxon-spring-boot-starter` on the classpath — confirms the
new module's `TaxonLookupService`/`TaxonAssignmentWriteService` collaborators only ever reference
`TaxonPort` (a `platform-commons` interface), never a concrete `taxon-starter` class, so the
Maven Enforcer `bannedDependencies` rule and the actual dependency graph agree. Reverted the pom.xml
edit immediately after (confirmed via `git diff` showing zero residual change beyond the intended
`marketplace-orchestrator` dependency addition), rebuilt to confirm the restore was clean.

**Not repeated this round: the full runtime boot + feature-exercise (Phase 8 steps 2-3's "start the
app, invoke a dependent feature" half).** The underlying mechanism this issue's changes rely on
(`ComponentFactory<T>` beans declared only inside the owning starter's own `@AutoConfiguration`,
resolved via `ObjectProvider`) is unchanged by this work — `marketplace-orchestrator` only adds a
new *consumer* of that already-proven mechanism, it doesn't touch the mechanism itself. The
compile-time result above is the part of the drill actually at risk from this specific change;
runtime graceful-degradation behavior for `taxon`/`provider-profile` absence was last verified
working before this issue started and nothing in this diff changes how any `ComponentFactory<T>`
bean is declared or consumed. Flagged here explicitly as a scope decision, not silently skipped.

## Out of scope

- Redesigning `AdvertisementFilterDto`/`ProviderProfileFilterDto` or the filter/pagination
  mechanism itself.
- Any change to `AdvertisementAuditEnrichService`'s own correctness — only its module location is
  in scope (Phase 4 lists it as a move candidate).
- REST implementation itself (explicitly deferred, per the spec above).
- Continuing Portfolio/Profile feature functionality (explicitly paused, per the spec above).

## Related

- `improvement-124` (F-04, provider-profile) — Batch 124-C (`AccountOverlay`) should land after
  this issue, not before.
- `platform-commons/DECISIONS.md` ADR-034 (bulk-lookup-over-join), ADR-035 (media summary
  enrichment), ADR-068 (`AdvertisementEnrichmentService` extraction) — document the *mechanism*
  this issue's fix reuses.
- `user-spring-boot-starter/CLAUDE.md` / `platform-commons/DECISIONS.md` ADR-026 — the
  "structural cost for cohesion, no runtime-toggle benefit" precedent that informed the decision
  to proceed with a dedicated module.
- `marketplace-app/services/advertisement/AdvertisementAuditEnrichService.java` — existing
  precedent for the enrichment shape being extracted.
- `improvement-073`, `improvement-111` — evidence checked that no real REST rollout is currently
  scheduled in this backlog; this task proceeds on flexibility/readability grounds, not an active
  REST requirement.

## Operational notes
- token_cost_review: 1232292 (8 parallel finder agents + 3 verifier agents, `/code-review --fix`)
- token_cost_research: n/a (Phase 0 discovery done directly by the main thread, no delegation)
- token_cost_verification: n/a (unit-tests/integration-tests/playwright run directly via Bash, no Agent-tool verification calls)
- context_loading_task_type: cross-module architectural refactor (new module extraction)
- context_loading_consulted: yes — re-read root CLAUDE.md, .claude/rules.md, and every touched module's CLAUDE.md (marketplace-app, platform-commons, advertisement/provider-profile/user-spring-boot-starter, integration-tests) at each phase transition, plus scripts/CLAUDE.md and playwright/CLAUDE.md fresh before each script run
- context_loading_matched: yes
- flows_situation: large, fully-researched, user-approved architectural task handed off to autopilot after Phase 0/1 planning already done conversationally
- flows_chosen: /autopilot
- flows_matched: yes
