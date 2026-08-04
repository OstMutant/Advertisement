# improvement-136: Extract `marketplace-orchestrator` — an Application/BFF composition layer for cross-domain orchestration

**Type:** architecture — new module extraction. Supersedes this issue's original, narrower scope
(see "Decision history" below).
**Module:** new `marketplace-orchestrator` module; touches `advertisement-spring-boot-starter`,
`provider-profile-spring-boot-starter`, `platform-commons` (`AdvertisementPort`/
`ProviderProfilePort`), `marketplace-app`, root `pom.xml`, `ArchitectureRulesTest`, root
`CLAUDE.md`, `marketplace-app/DECISIONS.md`.
**Priority:** 🔴 top — unchanged from the original ranking; still ordered ahead of
`improvement-135`/`improvement-124`'s remaining batches, still intended to land before
`improvement-124` Batch 124-C so the `AccountOverlay` UI is built against the final contract from
the start.
**When:** **NOT started.** Explicitly paused by the user (2026-08-04) before Phase 0 discovery —
the user has additional points to discuss first in a future session. This file documents the
*decided target solution* so nothing is lost between sessions; it is not a green light to begin
implementation. Do not start Phase 0 or touch any code until the user explicitly says to proceed.

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
