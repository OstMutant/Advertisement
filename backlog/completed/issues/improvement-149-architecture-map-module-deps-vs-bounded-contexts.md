# improvement-149: clarify (or fix) System › Diagrams › Module Dependencies vs Bounded Contexts

**Type:** investigation + architecture change — started as a diagram-clarity investigation, grew to
  include a real decision to relocate `*Hook` implementations out of `marketplace-app` (Point 4)
**Module:** `scripts/architecture/generate-architecture-model.sh`, `docs/architecture/architecture-map.html`,
  `marketplace-app/spi/*.java`, `marketplace-orchestrator`, `platform-commons/core/spi`
**Priority:** 🔴 top — explicit user request to rank at the top of the backlog
**When:** independent, no blockers

## Problem

While reviewing `improvement-147`'s true-BFF migration (marketplace-app repointed from direct
domain `*Port` access to going through `marketplace-orchestrator`), the user checked
`System › Diagrams › Module Dependencies — Dependency Graph` expecting to see the improvement
reflected there, and it looked completely unchanged. The explanation given in that conversation
(Module Dependencies reads real `pom.xml` `<dependency>` blocks — a Maven build-graph fact — while
Bounded Contexts reads real Java source for `ComponentFactory<XPort>`/`implements XPort` evidence
— a code-level API-usage fact — and this migration only changed the second, not the first) was
**not accepted as clear** ("ти якусь хуйню мені чешеш" — still doesn't understand it). This issue
exists to actually resolve that gap in understanding, or fix the diagram/its explanatory text if
the confusion turns out to be a real UX problem with the page rather than just an unclear verbal
explanation.

## Point 1 — explain why Module Dependencies looks the way it does, in a way that actually lands

Re-derive and re-explain from scratch, concretely, probably with a live walkthrough of the actual
generator code and the actual rendered diagram (not just prose) — the previous chat explanation
didn't work. Things to ground the explanation in:
- Read `scripts/architecture/generate-architecture-model.sh`'s real Module Dependencies section
  (the `<dependency>` block state machine around line 336, `moduleNodes`/`moduleDeps` construction)
  end to end, not from memory.
- Show the actual rendered graph (screenshot or direct HTML/JSON inspection) before and after
  `improvement-147`'s migration, side by side, so the "nothing changed" claim is either visibly
  confirmed or shown to be wrong.
- If the diagram is correct and the confusion is purely about *why* it's correct, figure out what
  concrete artifact (an annotated example, a different diagram, added text on the page) would
  actually make it click — a repeat of the same verbal explanation that already failed is not an
  acceptable outcome for round two.

## Point 2 — Bounded Contexts vs Module Dependencies: what's actually different

Both are Cytoscape-rendered graphs on the same `architecture-map.html` page. Nail down, concretely
and with real generator-code line references for each:
- What node set does each one draw (modules vs. domains — are these ever not 1:1?).
- What edge set does each one draw, and from what evidence (pom.xml dependency declarations vs.
  Java-source `ComponentFactory`/`implements`/import evidence).
- A concrete example change that would move each diagram and NOT the other (a real, testable
  litmus test — not just an abstract description), e.g.: adding a new `<dependency>` block to a
  pom.xml with no code using it yet vs. writing a new Java class that imports a `*Port` without any
  new Maven dependency.
- Whether having two diagrams that can diverge (as they just did) is actually useful information
  or is more confusing than helpful — this needs an honest answer, not an assumption that more
  diagrams are automatically better.

## Points 1 & 2 — resolved in conversation, 2026-08-08

**Point 1 answer:** `module_deps()` (`generate-architecture-model.sh:338-361`) is an `awk` state
machine that reads only `<dependency><artifactId>/<scope>` text out of each module's own `pom.xml`
— it never touches `.java` source at all. Confirmed directly: `marketplace-app/pom.xml`'s only
change in `improvement-147`'s commit was adding a `<properties><architecture.boundedContext>`
block (unrelated prior work) — zero `<dependency>` blocks touched — so `module_deps()`'s input was
byte-identical before/after, hence the rendered graph was byte-identical too. Real computed edges
pulled from the model JSON to confirm: `marketplace-app.edges.DEPENDS_ON_COMPILE` still lists all
6 starters + `marketplace-orchestrator`; `marketplace-orchestrator.edges.DEPENDS_ON_COMPILE` is
still just `[platform-commons]` — neither changed because Maven's dependency graph itself didn't
change (Spring still needs every starter JAR on the runtime classpath for its `@AutoConfiguration`
to register beans, regardless of which Java class references the Port type).

**Point 2 answer, with a real litmus test:**

| | Module Dependencies | Bounded Contexts |
|---|---|---|
| Evidence source | `pom.xml` `<dependency>` blocks (text parse) | `.java` source (grep for `ComponentFactory<XPort>`/`implements XPort`/`import`) |
| Edge meaning | "Maven puts this JAR on that module's classpath" | "This class actually calls that port/service in real code" |

Litmus test (both directions confirmed against the real generator code): (a) adding a new
`<dependency>` to a `pom.xml` for a JAR nothing in the code calls yet moves Module Dependencies
only; (b) writing a new Java class that imports an already-available `*Port` type (no new Maven
dependency needed, since `*Port` interfaces live in `platform-commons`, already a dependency
everywhere) moves Bounded Contexts only — exactly what happened here. Real example pulled from the
model JSON: `Orchestrator -> Advertisement "calls"`, evidence
`AdvertisementReadService.java injects ComponentFactory<AdvertisementPort>` — a Java-source fact
with no `pom.xml` equivalent.

ADR-015's original reasoning ("conceptual business edges aren't grep-able from pom.xml") is
outdated in its *mechanism* description (`bounded-contexts.md` no longer exists, both diagrams are
now live-generated — see ADR-019/025) but the *underlying reason to keep two diagrams* still holds:
they answer genuinely different questions (build graph vs. real-call graph) and conflating them
into one would lose one of the two facts. Not treated as "resolved, close the issue" yet — the
concrete UX-improvement plan below came out of this same conversation and is the issue's real
remaining scope.

## Point 3 — Bounded Contexts visually conflates `Shared -> X` with `UI -> X` (found 2026-08-08)

User reported still seeing marketplace-app depending on starters on the Bounded Contexts diagram
specifically, after `improvement-147`'s migration. Checked the real committed
`architecture-model.json` directly (not from memory): the only `UI ->` edges are `UI -> Orchestrator`
and `UI -> User` (via `AccessEvaluator`) — confirmed by listing every relationship in the file.

Root cause found in `buildBoundedContextsMermaidSource()` (`generate-architecture-model.sh:2547-2562`):
every relationship renders on one single `graph TB` Mermaid diagram, including 7 `Shared -> <every
starter> "decouples"` edges (dashed lines — `platform-commons` being a compile dependency of every
starter, unrelated to this migration, always true). With domain subgraphs, `Shared`, `UI`, and
`Orchestrator` all drawn together and only line-style (solid vs. dashed) distinguishing "calls"
from "decouples," it's easy to visually mistake a dashed `Shared -> starter` line for a `UI ->
starter` line at a glance — the data is correct, the diagram's visual legibility isn't. Needs its
own fix (separate legend, visually distinct `Shared` positioning, or splitting decouples-edges
into their own optional toggle) — not sized here, folded into this issue's remaining scope since
it's the same underlying "diagram doesn't communicate what it shows clearly enough" problem as
Points 1/2.

## Point 4 — `Audit -> UI` reverse edge: move `*Hook` implementations into `marketplace-orchestrator` via forwarder SPIs (found 2026-08-08)

Following Point 3, user asked why the diagram still shows a line touching `UI` for `User` and
`Audit` at all — investigated concretely (not from memory):

- **User: one real, forward-direction exception exists.** `AccessEvaluator`
  (`marketplace-app/services/security/AccessEvaluator.java`) holds a direct `UserAuthorizationPort`
  field — real, confirmed via grep, already documented in `marketplace-orchestrator/DECISIONS.md`
  ADR-003 as a deliberate residual exception (security-boundary checks called on nearly every
  render, not a domain read-model).
- **Audit: zero forward-direction exceptions.** Confirmed via grep: no file in `marketplace-app`
  imports `AuditPort`. What exists instead is 9 files implementing `*Hook` interfaces
  (`AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook`) — the *reverse* call
  direction (audit-starter calls into marketplace-app, not the other way around), rendered on the
  diagram as `Audit -> UI "calls back via Hook implementations"`.

User's decision: even the reverse-direction `*Hook` implementations should move out of
`marketplace-app` into `marketplace-orchestrator`, via a forwarder-SPI pattern — checked each of
the 7 `spi/*.java` files' actual dependencies first (not assumed):

| File | Dependency | Blocker |
|---|---|---|
| `AdvertisementActivityFieldsHookImpl` | `I18nService` | translates a field-name key to a locale label, nothing else |
| `TaxonActivityFieldsHookImpl` | `I18nService` | same shape |
| `UserActivityFieldsHookImpl` | `I18nService` | same shape |
| `UserSettingsActivityFieldsHookImpl` | `I18nService` | same shape |
| `AuditDomainHookImpl` | `EntityExistenceService` (orchestrator, already), `UserActorNameService` (marketplace-app, itself needs `I18nService` for a "deleted actor" label) | only the `I18nService` leaf blocks a full move |
| `CurrentActorHookImpl` | `AuthContextService` → `SecurityContextHolder.getContext()` | Spring Security session access — `marketplace-orchestrator/pom.xml` has zero Spring Security dependency today |
| `ActivityEnrichHookImpl` | `AdvertisementAuditEnrichService` (already documented to stay in marketplace-app, ADR-073 — needs `LocaleProvider`/`I18nService` for HTML-formatted diff output, a larger piece than a single label lookup) | out of scope for a forwarder-shaped fix, needs its own larger design |

**Decided approach — 2 new SPI interfaces in `platform-commons`, thin implementations stay in
`marketplace-app`, the 6 Hook classes move to `marketplace-orchestrator/services/`:**

1. **`UiLabelHook`** (new, `platform-commons/core/spi/`) — `String translate(I18nKey key)`. One
   implementation, `UiLabelHookImpl`, stays in `marketplace-app` as a thin wrapper around the
   existing `I18nService`.
2. **`SessionActorHook`** (new, `platform-commons/core/spi/`) — `Optional<Long> getCurrentActorId()`
   (same shape as the existing `CurrentActorHook`, deliberately — see below). One implementation,
   `SessionActorHookImpl`, stays in `marketplace-app` as a thin wrapper around `AuthContextService`.
3. **Move to `marketplace-orchestrator/services/`, rewritten to depend on the new SPIs instead of
   `I18nService`/`AuthContextService` directly:**
   - `AdvertisementActivityFieldsHookImpl`, `TaxonActivityFieldsHookImpl`,
     `UserActivityFieldsHookImpl`, `UserSettingsActivityFieldsHookImpl` — swap `I18nService` for
     `UiLabelHook`.
   - `AuditDomainHookImpl` — swap `UserActorNameService`'s `I18nService` dependency for
     `UiLabelHook` too (so `UserActorNameService` itself becomes movable, or its remaining
     `ActorLookupService`-delegation logic folds directly into the moved `AuditDomainHookImpl`).
   - `CurrentActorHookImpl` — moves, implements `CurrentActorHook` (unchanged, existing interface),
     but now depends on the new `SessionActorHook` instead of `AuthContextService` directly. Noted
     directly to the user as architecturally a like-for-like duplicate of `CurrentActorHook`'s own
     shape one layer down (the forwarder does exactly what the original interface already did) —
     raised, not treated as a blocker, since the user explicitly wants this file physically out of
     `marketplace-app` regardless.
4. **`ActivityEnrichHookImpl` stays in `marketplace-app`, out of scope here** — its dependency
   (`AdvertisementAuditEnrichService`) does real HTML-formatting work, not a single-value lookup;
   moving it needs its own design, not a mechanical forwarder swap.
5. **Consequences to work through when this is implemented (not yet fully scoped):**
   - `marketplace-orchestrator/CLAUDE.md`'s existing rule ("`*Hook` implementations never live
     here... regardless of how many domain ports they touch") needs correcting/superseding — it
     currently states the opposite of this decision.
   - `ArchitectureRulesTest` likely has a rule keying off `spi` package location for Hook
     implementations (`marketplace_must_not_import_starter_internals` or similar) — needs checking
     whether moving these breaks or needs updating existing ArchUnit rules.
   - Bounded Contexts diagram evidence-gathering (`generate-architecture-model.sh`'s Audit/Hook
     relationship code, currently greps `marketplace-app/src/main/java/org/ost/marketplace/spi`
     specifically for `implements .*Hook`) will need its search path updated to also/instead check
     `marketplace-orchestrator/services`, or the `Audit -> UI` edge label/evidence text will go
     stale once these move.

**Status: plan only, not implemented.** Explicitly requested as planning-only — no code touched.

## Point 5 — move starter `<dependency>` declarations from `marketplace-app/pom.xml` to `marketplace-orchestrator/pom.xml` (found 2026-08-08)

Follow-up to Point 4's Maven-scope finding: `marketplace-orchestrator` doesn't need a compile
dependency on any starter to function — `ComponentFactory<XPort>` resolves beans by type via
`platform-commons` interfaces + `ObjectProvider`, purely at runtime, no compile-time reference to a
starter's concrete class needed. The current `<dependency>` declarations on all 6 starters live in
`marketplace-app/pom.xml` only because that's the final assembled `@SpringBootApplication` and
*someone* has to pull each starter's JAR onto the runtime classpath so Spring finds its
`@AutoConfiguration`/beans — not because `marketplace-app`'s own code needs them (confirmed earlier
this session: zero `org.ost.<starter>.*` imports anywhere in `marketplace-app` main or test source).

**User-confirmed direction: move the `<dependency>` declarations to `marketplace-orchestrator/pom.xml`
instead, so `marketplace-app` depends only on `marketplace-orchestrator`** (plus `platform-commons`,
`query-lib`, Vaadin, etc. — never a starter directly). Verified this is Maven-mechanically sound,
not just conceptually:
- `marketplace-app --compile--> marketplace-orchestrator --compile--> <starter>` propagates as
  `compile` transitively (Maven scope table: `compile + compile = compile`) — the 4 currently-
  compile-scope starters (`audit`/`attachment`/`user`/`advertisement`) keep the exact same effective
  classpath presence.
- `marketplace-app --compile--> marketplace-orchestrator --runtime--> <starter>` propagates as
  `runtime` transitively (`compile + runtime = runtime`) — `taxon`/`provider-profile` keep their
  exact same optional/removable semantics, not accidentally promoted to mandatory.

**Payoff:** `Module Dependencies` would then show the same shape `Bounded Contexts` already shows —
`marketplace-app -> marketplace-orchestrator` as the only edge out of `marketplace-app`,
`marketplace-orchestrator -> <every starter>` for the rest — the two diagrams converging instead of
diverging, directly closing the confusion this whole issue started from.

**What has to change:**
1. Remove the `enforce-no-starter-deps` `bannedDependencies` Enforcer rule in
   `marketplace-orchestrator/pom.xml` (lines 66-88) — or narrow/replace it, since its original
   reasoning (`marketplace-orchestrator/DECISIONS.md` ADR-001, "orchestrator must depend only on
   platform-commons Port/DTO contracts, never a starter jar directly") is exactly what this point
   proposes reversing. Needs its own ADR entry recording the reversal and why (Enforcer rules that
   get quietly deleted without a recorded reason are exactly the kind of drift this repo's own ADR
   discipline exists to prevent).
2. Move all 6 `<dependency>` blocks (correct scope preserved per starter: `compile` for
   `audit`/`attachment`/`user`/`advertisement`, `runtime` for `taxon`/`provider-profile`) from
   `marketplace-app/pom.xml` to `marketplace-orchestrator/pom.xml`.
3. **Hard requirement, explicitly stated by the user: removing an optional starter module (`taxon`/
   `provider-profile`) from the build must still break nothing** — same bar `improvement-136`'s
   Phase 8 and `improvement-148` already established. This must be verified for real after this
   change lands, not assumed from the scope-propagation math alone:
   - Remove `taxon-spring-boot-starter` (or `provider-profile-spring-boot-starter`) from the root
     `pom.xml` `<modules>` (or its dependency), rebuild, boot the app, confirm it starts and a
     taxon-dependent feature degrades gracefully (same check `improvement-148` already tracks —
     this point should be folded into that issue's scope when both are picked up, not duplicated
     as a second removal test).
   - Also confirm the 4 non-optional starters (`audit`/`attachment`/`user`/`advertisement`) still
     resolve correctly when `marketplace-orchestrator` — not `marketplace-app` — is the one
     declaring them; a Maven reactor build (`mvn clean package`) and a real `deploy.sh` boot are
     both required, not just `mvn compile` (this session's own `AuditReadService`/`AuditQueryService`
     bean-collision bug was invisible to `mvn compile`/unit tests and only caught by an actual
     container boot — same risk class applies here).
4. Regenerate `architecture-model.json`/`architecture-map.html` afterward and confirm the Module
   Dependencies diagram now shows the converged shape described above.

**Status: plan only, not implemented.**

## Point 6 — SPI Map: add a third column of real caller classes, not just implementations (asked 2026-08-08)

`spi_map_json()` (`generate-architecture-model.sh:580-639`) currently builds a 2-column Cytoscape
graph per SPI interface: `platform-commons` (the interface itself, one group) →
`<module>` (each real implementation class, grouped by owning module) — the edge direction is
`interface -> implementation` ("implements"), found via
`grep -rlP "implements\s+.*\b${iface}\b" .../*-spring-boot-starter/src/main/java
marketplace-app/src/main/java` (line 616). This answers "who implements X" but not "who actually
calls X" — the caller side is completely absent from this diagram today, even though the same
underlying evidence-gathering mechanism already exists elsewhere in this same script (the Bounded
Contexts relationship code's `ComponentFactory<XPort>` grep, `generate-architecture-model.sh:1043-1056`
and `:1097-1103`, added in `improvement-147`).

**Proposed: a third node column, real caller classes, added as a new leftmost (or rightmost —
match whichever visual convention "the other two diagrams" already use) group, with a new
`caller -> interface "calls"` edge.**

Evidence-gathering differs by suffix, matching the existing `*Port`/`*Hook` direction convention
(`platform-commons/CLAUDE.md`'s SPI naming table):
- **For `*Port` interfaces** (marketplace/orchestrator → starter): grep every module (not just
  starters) for `ComponentFactory<\s*${iface}\s*>|\b${iface}\s+\w+\s*;` — same pattern already
  proven in the Bounded Contexts code, just needs running across all modules instead of scoped to
  one at a time.
- **For `*Hook` interfaces** (starter → marketplace, reverse direction): the "caller" is the
  starter itself — needs a different grep shape (e.g. `List<\s*${iface}\s*>` or
  `${iface}\s+\w+\s*;` inside each starter's own `src/main/java`, since the direction is inverted —
  a starter holds a collection of hook implementations and iterates them, rather than a single
  field). Confirm the actual autowiring shape (e.g. `audit-spring-boot-starter`'s
  `AuditReadService`/`AuditLogRepository` classes calling `List<AuditActivityFieldsHook>`) before
  writing the grep, don't assume the same single-field shape `*Port` callers use.

**Caveat, confirmed real — same class of problem as Point 3.** For `*Port` interfaces the caller
column would be marketplace-app/orchestrator classes (left) and the implementer column would be a
starter (right). For `*Hook` interfaces the roles are exactly reversed: the caller column is the
*starter itself* (left) and the implementer column is marketplace-app or, once Point 4 lands,
`marketplace-orchestrator` (right). A reader who gets used to "left = our code, right = starter"
from scanning the many `*Port` rows would hit a `*Hook` row and see the module positions flipped
with no visual signal explaining why — the exact same "diagram is technically correct but visually
misleading without a distinguishing cue" problem Point 3 already found for `Shared`/`UI` edges.

**User's call, 2026-08-08:** don't over-design this now — the arrow direction/edge label itself
("calls" vs "implements") may already be a sufficient distinguishing signal without adding a
separate color/icon/section split. Revisit once this is actually built and the real rendered
graph can be looked at directly, rather than deciding the fix sight-unseen.

**Not yet decided:** exact visual layout (3 Cytoscape compound groups in a row — Callers |
platform-commons | Implementations — vs. some other arrangement), and whether "caller" should
be per-real-class (matching the existing per-class implementation node granularity) or collapsed
to per-module (coarser, less noisy for interfaces with many callers like `TaxonPort`). Needs a
decision before sizing, not assumed here.

**Status: plan only, not implemented.**

## Suggested fix — Diagrams screen: reorder cards, add a per-card description, store it in the JSON

Decided 2026-08-08, plan only, not yet implemented:

1. **Reorder** `MODEL.diagramGroups` (currently hardcoded as one string, `generate-architecture-
   model.sh:512`, in order Module Dependencies → SPI Map → Database ERD → Bounded Contexts) to:
   **Bounded Contexts → SPI Map → Module Dependencies → Database ERD**.
2. **Add a `description` field** to each of the 4 `diagram_groups_json` entries (alongside the
   existing `label`/`file` fields) — a one-line, hand-written "what this diagram actually shows"
   summary per diagram, e.g. Bounded Contexts: "which domain calls which other domain in real
   code, and why"; Module Dependencies: "which module's JAR ends up on which other module's
   classpath, per real `pom.xml` declarations". Store this in the model JSON itself (matching how
   `file`/`label` already travel as data, not hand-typed HTML) rather than hardcoding descriptive
   text separately in the HTML template — the user's explicit preference, so the same source of
   truth that already answers "where does the data come from" (`file`) also answers "what does it
   show" (`description`) in one place.
3. **Render the new field** in `renderDiagrams()`'s card-grid loop (`generate-architecture-
   model.sh:2775-2784`), which currently shows only the group `label`/`file` badge and each card's
   bare `title` — add the `description` text visibly on the group header or per-card (each of the
   4 groups currently has exactly one diagram inside it, so group-level placement is sufficient;
   revisit only if a group ever gains a second diagram).
4. Regenerate `architecture-model.json`/`architecture-map.html`, confirm
   `check-architecture-model-freshness.sh` passes, visually verify the new card layout (screenshot
   or direct page load) before considering this done.

**Status: reverted, not implemented.** An implementation attempt (steps 1-2 above) was started in
this session after plan-mode approval, then explicitly reverted at the user's request (`git
checkout -- scripts/architecture/generate-architecture-model.sh`) before regeneration/commit —
the user wanted planning only at that point, not execution. The plan above remains valid for
whenever this is actually picked up.

## Implementation log — 2026-08-11

Points 3, 4, 5, 6, and the Suggested fix were all implemented in one autopilot run this session
(user approved the combined plan). Summary, not a substitute for the `git diff`/DECISIONS.md ADRs:

- **Point 4** — the 6 `*Hook` implementations moved out of `marketplace-app/spi`. Real
  compile-visibility problem found and resolved along the way: `I18nKey` (the enum the 4
  `*ActivityFieldsHookImpl` classes and `UserActorNameService` translated through) lives in
  `marketplace-app`, not visible from `marketplace-orchestrator` — moving the whole ~300-key enum
  was rejected as disproportionate. Fixed via two new forwarder SPIs, `UiLabelHook`/
  `SessionActorHook` in `platform-commons`, documented in `platform-commons/DECISIONS.md` ADR-029.
  See `marketplace-orchestrator/DECISIONS.md` ADR-004 for the full decision record.
- **Point 5** — starter `<dependency>` blocks moved from `marketplace-app/pom.xml` to
  `marketplace-orchestrator/pom.xml`; the `enforce-no-starter-deps` Maven Enforcer rule deleted
  (superseded, not narrowed — see ADR-004). **Hard-requirement verification actually performed,
  not assumed:** `taxon-spring-boot-starter`'s dependency was temporarily removed from
  `marketplace-orchestrator/pom.xml`, a real `bash scripts/deploy.sh --reset` was run, and the
  booted app returned HTTP 200 on `/health` and `/` with zero errors/exceptions in the container
  log — then the dependency was restored and redeployed. This also closes
  `improvement-148`'s scope (same removal proof, not duplicated).
- **Points 3, 6, Suggested fix** — all implemented in `scripts/architecture/generate-architecture-model.sh`:
  a `showDecouplesEdges` toggle (off by default) hiding the repeated `Shared -> X` compile-fact
  edges on Bounded Contexts; a third "real caller" node column added to the SPI Map diagram;
  `diagramGroups` reordered (Bounded Contexts first) with a per-group `description` field.
- **`/code-review` (high effort, 8 finder angles + verification)** ran on the full diff before
  the test cycle, per the standing autopilot process. Confirmed and fixed: the Bounded Contexts
  diagram's Orchestrator card silently omitted the 3 Hook interfaces it now implements (missing
  `implements` grep in that branch); `spi_map_json()`'s new caller-detection loop walked the same
  directory trees a second time per interface (~2x slower, measured) — combined into one
  `grep -rlP` pass + per-file classification; the combined-grep refactor introduced a JSON-comma
  bookkeeping bug (caller edges didn't share the `first_edge` guard) — fixed same session, caught
  by regenerating and validating the JSON before moving on; the "show decouples edges" checkbox
  reset the diagram's zoom via the shared `renderDiagrams()` — fixed with a dedicated
  `toggleDecouplesEdges()` handler that saves/restores zoom; a stale `ArchitectureRulesTest.java`
  javadoc claim about `marketplace-app` depending on every starter directly; a missing
  cross-reference in `marketplace-app/CLAUDE.md`'s I18n section to the new raw-string-key
  exception; a 6-line comment citing "improvement-149" by name in the generator script (fixed on
  sight, standing rule violation). Findings surfaced but accepted as already-deliberate,
  documented trade-offs, not changed: `SessionActorHook` being a structural duplicate of
  `CurrentActorHook` (already flagged and accepted before implementation began, see Point 4 above);
  the Enforcer-rule deletion narrowing defense-in-depth to ArchUnit-only (confirmed consistent with
  the existing governance model `marketplace-app` itself already used for the same class of
  concern, not a new weakness).
- Unit tests (119 total across query-lib/marketplace-orchestrator/marketplace-app, including the
  moved `UserActorNameServiceTest` and all 16 `ArchitectureRulesTest` cases) and integration tests
  (165 total) all passed after the fixes above.
- `architecture-model.json`/`architecture-map.html` regenerated; `check-architecture-model-freshness.sh`
  green.

**Mid-session correction (still same day):** after reviewing the result, the user pointed out
`marketplace-app/pom.xml` still depends on `platform-commons`/`query-lib` directly — which matches
what this issue's own Point 5 text recorded as the agreed scope at the time ("plus
`platform-commons`, `query-lib`, Vaadin, etc.") but not what the user actually wants going forward.
Filed as a separate, explicitly-scoped follow-up rather than reopening Point 5's already-verified
work: `backlog/issues/improvement-150-marketplace-app-zero-deps-except-orchestrator.md`, ranked
directly after this issue in `BACKLOG.md`.

**Further correction, same session:** the 6 `*Hook` implementations that Point 4 moved into
`marketplace-orchestrator` had all landed in the existing flat `org.ost.orchestrator.services`
package, mixed in with plain composition/lookup services. User asked for these split into their
own sibling `org.ost.orchestrator.spi` package (mirroring `marketplace-app`'s own `services`/`spi`
separation) — done: 6 files moved with package-declaration changes, `AuditDomainHookImpl` gained
explicit imports for `EntityExistenceService`/`UserActorNameService` (now a different package),
`UserActorNameService` itself stayed in `services/` (it implements no SPI interface itself). Every
generator-script grep path and `marketplace-orchestrator/CLAUDE.md` reference to
`org.ost.orchestrator.services` for Hook-implementation evidence was updated to
`org.ost.orchestrator.spi`. **Not yet recompiled/retested** — user explicitly asked to hold off
("не компіль в кінці — ще будем рефакторити"), more refactoring is coming in the same session
before the next full verification pass.

**Third correction, same session — coupling pushback on the message-key constants.** User rejected
the `private static final String FIELD_TITLE = "changes.field.title"`-style constants duplicated
across the 4 `*ActivityFieldsHookImpl` classes and `UserActorNameService`, calling it coupling
(each constant a hand-copied duplicate of an `I18nKey` enum entry with no compiler check keeping
them in sync — this was already flagged by `/code-review`'s Angle D/G findings and accepted as a
"deliberate, narrow exception" in ADR-029 at the time, but the user wants it actually fixed, not
just documented as accepted debt). Two follow-up design questions, both resolved by the same
insight (`marketplace-app` legally depends on `marketplace-orchestrator`, so a type defined in
`marketplace-orchestrator` is visible to `marketplace-app` — nothing needs to go in
`platform-commons` for this pair):

1. **Where do the 16 message keys live?** New `AuditLabelKey` enum in
   `marketplace-orchestrator/src/main/java/org/ost/orchestrator/spi/AuditLabelKey.java` — one
   canonical entry per key (same constant names `I18nKey` already used:
   `CHANGES_FIELD_TITLE`, ..., `AUDIT_ACTOR_DELETED_NAME`). `I18nKey.java` (marketplace-app) now
   references `AuditLabelKey.CHANGES_FIELD_TITLE.key()` instead of duplicating the literal string
   — a rename in `AuditLabelKey` is now a compile error in `I18nKey.java`, closing the original
   coupling complaint. The 4 Hook classes + `UserActorNameService` reference `AuditLabelKey.X`
   directly (same module), no local constants at all anymore.
2. **Does `UiLabelHook`/`SessionActorHook` themselves need to live in `platform-commons`?** User
   asked this directly, correctly spotting that the *Port/*Hook-must-live-in-platform-commons rule
   exists specifically for starter optionality ("marketplace compiles without a starter present") —
   which doesn't apply here, since no starter calls `UiLabelHook`/`SessionActorHook` at all (only
   `marketplace-orchestrator`'s own Hook classes do), and `marketplace-orchestrator` is a
   *mandatory*, never-optional dependency. Both interfaces moved from `platform-commons/core/spi`
   into `marketplace-orchestrator/src/main/java/org/ost/orchestrator/spi/`, alongside
   `AuditLabelKey`. `UiLabelHookImpl`/`SessionActorHookImpl` (marketplace-app) now implement
   `org.ost.orchestrator.spi.UiLabelHook`/`SessionActorHook` — legal (marketplace-app depends on
   marketplace-orchestrator), same Spring-DI-finds-bean-by-type mechanism as before.
   `UiLabelHook.translate()` is now fully typed (`AuditLabelKey key`, not `String messageKey`) —
   the only remaining `String` boundary is `UiLabelHookImpl.translate()`'s one-line call into
   `I18nService.get(key.key(), args)`.
   `ArchitectureRulesTest.hooks_live_only_in_platform_commons` updated with a named allow-list
   (`UiLabelHook`, `SessionActorHook`) documenting why this pair is the exception, rather than
   silently breaking or being deleted.

**Net effect: `platform-commons` gained zero new types from this whole Point 4 line of work** — the
two forwarder SPIs and the shared label-key enum all ended up living in `marketplace-orchestrator`
instead, once the actual dependency-direction constraint was worked through properly instead of
defaulting to "SPI-shaped things go in platform-commons."

**Known consequence, not yet swept:** `scripts/architecture/generate-architecture-model.sh`'s SPI
Map diagram only scans `platform-commons/*/spi/*.java` for interfaces to show — since
`UiLabelHook`/`SessionActorHook` no longer live there, they will silently stop appearing on that
diagram once regenerated, and `spi_kind_for()`'s special-cased branch for these two names becomes
dead code. Not fixed yet — deferred to the same later sweep as the recompile/retest, per the
"more refactoring coming" note above; flagged here so it isn't lost.

**Fourth correction, same session — `AuditLabelKey` itself pushed back on and removed.** User
correctly identified two more real problems with `AuditLabelKey`: (1) baking a resource-bundle-path
string convention (`"changes.field.title"`) into `marketplace-orchestrator`, which has no business
knowing how `marketplace-app` structures its message bundles; (2) it duplicated work the codebase
already did for free — each DTO's `@FieldNameConstants`-generated `Fields.*` constant (e.g.
`TaxonSnapshotDto.Fields.nameEn`) is already a compiler-checked identifier visible to both modules
via `platform-commons`, no parallel enum needed. Fixed: `UiLabelHook` split into two purpose-built
methods — `translate(EntityType entityType, String rawFieldKey, Object... args)` for field labels
(no key enum — the raw `Fields.*` constant plus `EntityType` for disambiguation) and
`translateActorDeletedSuffix(String actorName)` for the one non-field-label case. Every
`*ActivityFieldsHookImpl.labelFor()` collapsed to a one-line delegation — the whole
field-name-to-label switch, previously duplicated once per domain class in
`marketplace-orchestrator`, now lives in exactly one place: `marketplace-app`'s `UiLabelHookImpl`,
nested by `EntityType` then `rawFieldKey`. `AuditLabelKey` deleted entirely; `I18nKey.java`
reverted to its own plain literals, no import from `marketplace-orchestrator` at all — closing
that direction of coupling too, not just the original one. See `marketplace-orchestrator/DECISIONS.md`
ADR-004's third refinement and `platform-commons/DECISIONS.md` ADR-029's second refinement for the
full record.

**Known consequence, still not swept:** the SPI Map diagram gap noted above still applies — now
also true for `AuditLabelKey` never having existed there (nothing to sweep for that part). The
generator-script fix (scan `marketplace-orchestrator/spi` for interfaces, not just
`platform-commons/*/spi`) is still outstanding.

**Fifth correction, same session — the whole per-domain Hook pattern for field labels questioned
and removed.** User asked directly whether the four `*ActivityFieldsHookImpl` classes (Advertisement/
Taxon/User/UserSettings) were even still needed as separate implementations, or whether "services"
could do it — i.e., pointing at the same class of premature-abstraction concern as every correction
above, one layer further out. Verified before acting, not assumed: diffed
`AdvertisementActivityFieldsHookImpl` against `TaxonActivityFieldsHookImpl` — identical except the
`EntityType` constant; `expandFields()` had *always* been `item.expandedChanges()` in all four
(never domain-specific, before this session either); grepped the whole repo for
`AuditActivityFieldsHook` and found exactly one real caller — `marketplace-app`'s own
`AuditTimelineRowRenderer` — not the audit-starter the interface's own Javadoc described. User's
verdict once these facts were laid out: "ну так — це ж мусор" (yeah, that's garbage). Removed:

- `AuditActivityFieldsHook` interface — deleted from `platform-commons` entirely.
- All four `*ActivityFieldsHookImpl` classes — deleted from `marketplace-orchestrator/spi`.
- `UiLabelHook.translate(EntityType, String, ...)` — deleted; `UiLabelHook` is a single-method
  `@FunctionalInterface` again (`translateActorDeletedSuffix(String)`, the one case that still has
  a genuine cross-module need — its real caller, `UserActorNameService`, serves the audit-starter,
  which has no i18n awareness of its own).
- The field-name-to-label switch (previously duplicated once per domain class, then briefly
  centralized in `marketplace-app`'s `UiLabelHookImpl`) moved into a private `labelFor(EntityType,
  String)` method directly on `AuditTimelineRowRenderer` — its one real consumer, same module,
  no interface indirection needed at all anymore.
- `AuditTimelineRowRenderer` restructured: `List<AuditActivityFieldsHook> fieldsProviderList` and
  the `Map<EntityType, AuditActivityFieldsHook>` it built in `init()` are gone; a
  `Set<EntityType> LABELED_ENTITY_TYPES` constant (`ADVERTISEMENT`, `TAXON`, `USER`,
  `USER_SETTINGS`) replicates the exact same fallback behavior the old Spring multi-bean lookup
  provided (unmapped entity types fall back to `changeFormatter.buildChangesList()`, unchanged) —
  a structural simplification verified to preserve observable behavior, not a behavior change.

See `marketplace-orchestrator/DECISIONS.md` ADR-004's fourth refinement and
`platform-commons/DECISIONS.md` ADR-029's third refinement for the full record. Current-state docs
updated to match: root `CLAUDE.md`, `audit-spring-boot-starter/CLAUDE.md`,
`platform-commons/CLAUDE.md`, `marketplace-app/CLAUDE.md`, `marketplace-orchestrator/CLAUDE.md`.

**Known consequence, now bigger than the earlier SPI-Map-diagram note:**
`scripts/architecture/generate-architecture-model.sh`'s Bounded Contexts `"$dom" -> "Audit"
"audited via"` relationship edges sourced their evidence entirely from
`grep implements AuditActivityFieldsHook` — with that interface gone, this specific
evidence-gathering method now finds nothing, so those edges will silently vanish from the diagram
on next regeneration even though the underlying fact (these four domains genuinely are audited)
hasn't changed. This is a real signal loss, not just a cosmetic diagram change — still deferred to
the same later sweep as the recompile/retest, flagged here with its real severity so it isn't
mistaken for a minor cleanup item when that sweep happens.

**Compile check run (2026-08-11), full test suite still not run.** User asked specifically for
just a compile check (`./mvnw compile -pl platform-commons,marketplace-orchestrator,marketplace-app
-am`) after the `AuditActivityFieldsHook` removal — **BUILD SUCCESS**, all three modules compile
clean. No unit/integration/Playwright tests run yet — Definition of Done still pending.

**Sixth addition, same session — SPI Map diagram split into one tab per subsystem.** Separate from
the coupling-pushback thread above: user reported (unprompted, a fresh observation while looking at
the already-regenerated model) that `System › Diagrams › SPI Map` had become genuinely unreadable —
the new caller column (Point 6) pushed the single combined canvas to 71 nodes / 66 edges (`TaxonPort`
alone has 9 real callers). Presented options (caller-column toggle, per-module caller collapsing,
per-subsystem filter, split into separate diagrams, focus/search, color-coding); user picked the
biggest structural option — split into 7 separate diagrams (one per subsystem: audit, attachment,
user, advertisement, taxon, providerprofile, core), matching the subsystem grouping the "SPI
Interface Details" table already used. Implemented as a client-side filter
(`spiMapNodesForSubsystem()`) over the same unfiltered `MODEL.spiMap` data ADR-026 already produces
— no new bash computation, `SPI_SUBSYSTEM_ORDER` hoisted to one shared global array instead of two
separately-maintained copies. Real result: `user` (largest, 7 interfaces) 20 leaf nodes, `audit` 16,
`taxon` 11, `attachment`/`advertisement` 8-9, `providerprofile` 6, `core` 4 — down from one shared
62-leaf-node canvas. See `scripts/architecture/DECISIONS.md` ADR-027 for the full record, including
the three rejected alternatives and why. Regenerated and freshness-checked, green.

**Seventh addition, same session — Bounded Contexts: checkbox replaced with two diagrams, item
cap, tighter spacing, and the flagged `audited via` regression actually fixed.** User asked to
work on Bounded Contexts next; along the way flagged the `showDecouplesEdges` checkbox itself as
unclear ("та все незрозуміло"), and separately that the diagram was too stretched vertically,
"особливо пробіли між айтемами в блоках" ("especially the gaps between items in the boxes"),
wanting it to fit on one screen. Measured the real cause before proposing a fix (per
[[feedback_ground_proposals_in_real_data_offer_options]]): `Orchestrator`'s domain box alone lists
29 items (18 services + 11 ports) against every other domain's max of 11, plus Mermaid's default
50/50 node/rank spacing compounding across every box.

Implemented, mirroring ADR-027's SPI Map subsystem-tabs pattern:
- Checkbox removed; `bounded-contexts` group now has 2 diagrams — "Context Map" (default, real
  calls only, `Shared` domain excluded since it never appears in a real call edge) and "Shared
  Dependencies" (the `decouples` edges only, simple domain-name boxes, no item lists).
- `BC_MAX_ITEMS_PER_BOX = 8` caps each domain box's inline item list, with a "+N more" link to the
  existing "Domain Contents" section for the full list.
- Mermaid `flowchart.nodeSpacing`/`rankSpacing`/`padding` tightened (12/25/6, from ~50/50/8).
- The `audited via` edges the previous entry flagged as lost (evidence source
  `implements AuditActivityFieldsHook` no longer existing) are fixed for real, not just noted:
  new evidence source is `AuditTimelineRowRenderer.LABELED_ENTITY_TYPES`. Confirmed in the
  regenerated model: `Advertisement`/`Taxon` -> `Audit`, `User` -> `Audit` (with `USER`/
  `USER_SETTINGS` folded into one edge's evidence, matching the pre-existing `add_rel()` merge
  behavior).

See `scripts/architecture/DECISIONS.md` ADR-028 for the full record.

**Eighth addition, same session — Bounded Contexts moved from Mermaid to Cytoscape entirely; ADR-028's
"Shared Dependencies" second diagram removed again.** ADR-028's spacing fixes didn't land — user
reported (with real screenshots, which surfaced a genuine blocked-tool moment: raw `docker run` for
an ad-hoc screenshot container was correctly blocked by this repo's own safety hook, since it's not
a sanctioned path — `bash scripts/deploy.sh`/`playwright.sh` are, but stand up the full Vaadin app
for a static-file screenshot, out of proportion to the task) the diagram was still stretched, and
separately asked directly why dragging a Cytoscape diagram (Module Dependencies, SPI Map) shows
real native visual feedback while dragging Bounded Contexts (Mermaid + hand-rolled
`enableDragToPan()`) shows none — could Bounded Contexts just work the same way those do?

That question runs straight into `DECISIONS.md` ADR-016, which reverted an earlier Cytoscape
attempt at this exact diagram for a real, structural reason: domain items nested as compound-child
nodes created a cycle spanning compound boundaries, which dagre's compound-aware ranking can't
resolve. Checked directly (not assumed) whether that failure mode still applies: a 2-node cycle
still exists today (`Orchestrator <-> Audit`, shifted from the old `UI <-> Audit`), but it's now
between two *flat, non-nested* domains — the ordinary case dagre handles fine, never the specific
parent-child-crossing-boundary shape ADR-016 actually diagnosed.

Implemented: `renderContextMapGraph()`, reusing `renderModuleDependencyGraph()`'s own shape — flat
domain nodes (no item children on the canvas at all, entities/services/tables/ports stay in the
existing "Domain Contents" table below), same `domainColor()` coloring, same native Cytoscape
pan/zoom/click interaction as every other Cytoscape diagram in the tool. Separately, asked what
real value the "Shared Dependencies" diagram (introduced one message earlier, ADR-028) actually
provided — honest answer: very little, 7-8 identical dashed edges restating one already-documented
fact — removed again, back to one diagram, the fact stated once as plain text. Dead code from
ADR-028 cleaned up in the same pass: `wireBoundedContextsClicks()`, the now-unused Mermaid
`flowchart` spacing config and its CSS overrides (no live Mermaid flowchart-type diagram remains in
the tool at all). See `scripts/architecture/DECISIONS.md` ADR-029 for the full record.

**Ninth addition, same session — clickable arrows + Relationships table readability.** Once the
diagram itself worked, follow-up feedback on the "Relationships (N)" table below it: `Confidence`
always showed `"extracted"` for every single row (dead column, no information); `What crosses`
wasn't obviously directional; `Confidence`/`Evidence` weren't self-explanatory; `Label` values
(`"calls"`, `"audited via"`, etc.) read as cryptic shorthand. Also asked for the diagram's arrows
themselves to be clickable, leading to the methods involved. Implemented: `Confidence` column
dropped (constant value across every row, stated once in the section header instead); `Label` cells
get a hover tooltip with a plain-English sentence (`BC_LABEL_MEANING`); `Evidence` text now
linkifies the real file path inside it (`linkifyEvidence()`) wherever the evidence actually names
one, leaving prose-only evidence (e.g. "N classes import X") as plain text; diagram edges are
clickable (`diagramCy.on("tap", "edge", ...)`), scrolling to and flashing the matching Relationships
row via a shared, collision-safe `bcRelRowId()` id. Domain Contents was checked and found already
fully linked (every item already carries a real `.file`) — no gap there. See
`scripts/architecture/DECISIONS.md` ADR-029's latest refinement note for the full record.

**Tenth addition, same session — same tooltip treatment applied to SPI Map's "Direction" column.**
User asked to mirror the Bounded Contexts tooltip work on SPI Map. `d.kind` values ("Port
(marketplace -> starter)", "Hook (starter -> marketplace)", "type contract") already named the
call direction but not why it matters — added `SPI_KIND_MEANING`/`spiKindMeaning()` (prefix-matched
so the module-specific suffix `spi_kind_for()` appends for `UiLabelHook`/`SessionActorHook` still
resolves) as a `title` tooltip per row, plus header tooltips on Caller(s)/Direction/
Implementation(s). Deliberately did not add edge-click-to-scroll on SPI Map's diagram this round —
wasn't asked for, and its `renderCytoscapeFromGraph()` is shared with Module Dependencies, so that
would need its own scoped decision rather than riding along with a tooltip request. See
`scripts/architecture/DECISIONS.md` ADR-027's refinement note.

**Eleventh addition, same session — a genuine bug found by the user just asking "why does Audit
call UI".** Checked the evidence instead of re-explaining the diagram, and it didn't hold up: the
old code labeled *every* Hook implementation file in `marketplace-app/spi`/
`marketplace-orchestrator/spi` as an "Audit ->" edge regardless of who actually calls it. Verified
per-interface via grep: `UiLabelHook`/`SessionActorHook`'s real caller is
`marketplace-orchestrator`'s own classes (not any starter) — a real `Orchestrator -> UI` fact, not
`Audit -> UI`; `CurrentActorHook` has two real callers (`audit-spring-boot-starter` **and**
`attachment-spring-boot-starter`), not just audit. Rewrote the evidence-gathering as a proper
per-interface, per-real-caller loop. Real result after the fix: `Audit -> UI` now correctly carries
only `AuditActivityEnrichHook`; two previously-missing edges appeared — `Attachment -> Orchestrator`
and `Orchestrator -> UI`. See `scripts/architecture/DECISIONS.md` ADR-029's latest note for the
full record, including the one pre-existing, not-chased-further gap it surfaced
(`UserSettingsChangedHook`'s implementor lives outside the searched `spi/` package).

**Compile check, still not full retest.** This round (and the five before it) only touched the
bash generator script — no Java changes — so no new compile check was needed.
Unit/integration/Playwright still pending overall; more refactoring may still be coming in the same
session.

**Twelfth addition, same session — implemented: split Bounded Contexts into 4 diagrams by
relationship nature.** User asked "чого діаграма така — ми ж говорили що маєм через
бфф комунікувати а тут виглядає що стартери зі стартерами аудіт з юай". Traced the real, current
16 relationships (all non-`decouples` edges) and found they fall into 4 genuinely different kinds
that all render with the same arrow+label visual today, which is the actual source of confusion —
not a bug in the relationships themselves, a legibility gap in showing 4 different *kinds* of fact
on one canvas:

1. **Service Calls (BFF)** — label `"calls"`. `Orchestrator -> {Advertisement, Attachment, Audit,
   ProviderProfile, Taxon, User}`, `UI -> Orchestrator`, `UI -> User` (the one documented exception,
   `AccessEvaluator` — see `marketplace-orchestrator/CLAUDE.md`). This is the BFF pattern working
   as intended.
2. **Hook Callbacks** — label `"calls back via Hook implementations"`. `Audit -> UI`,
   `Audit -> Orchestrator`, `Attachment -> Orchestrator`, `Orchestrator -> UI`. Reverse-direction
   dependency inversion (a starter/Orchestrator asks a higher layer for one piece of context it
   can't otherwise reach) — a different mechanism than orchestration, not a BFF violation.
3. **Cross-Starter Exceptions** — label `"category assignment via"`. `ProviderProfile -> Taxon`
   only. A real, direct starter-to-starter `TaxonPort` call bypassing the orchestrator — documented
   technical debt (`provider-profile-spring-boot-starter/CLAUDE.md`: "there is no orchestrator save
   path for this domain yet"), not the intended pattern.
4. **Derived Facts** — labels `"audited via"` + `"can have"`. `Advertisement/Taxon/User -> Audit`,
   `Advertisement -> Attachment`. Not code calls at all — classification facts derived from data
   (which `EntityType`s get audit field-labels, which domains' Hook implementations declare which
   `entityType()`). Merged into one tab since both are "not a real call" facts of the same kind,
   rather than 5 tabs for 5 labels.

**Plan:**
- `scripts/architecture/generate-architecture-model.sh` (bash): add `BC_CATEGORY_ORDER`/
  `BC_CATEGORY_LABEL`/`BC_CATEGORY_DESC` (mirrors the existing `SPI_SUBSYSTEM_ORDER`/
  `SPI_SUBSYSTEM_LABEL` pattern used for SPI Map's 7-tab split) and `BC_LABEL_CATEGORY` (maps each
  of the 5 existing labels to one of the 4 categories above). Add `bounded_contexts_diagrams_json()`
  (mirrors `spi_map_diagrams_json()`) returning 4 diagram entries, each carrying `category` +
  `description`. `bounded_contexts_json()`'s relationship JSON gains a `"category"` field per edge.
  `diagram_groups_json`'s `bounded-contexts` entry gets `"diagrams": [$(bounded_contexts_diagrams_json)]`
  instead of the current single `{"title": "Context Map", ...}`.
- JS: `buildContextMapGraph(category)` filters relationships to `r.category === category` (no-arg
  stays "all", used only by the unfiltered Markdown export); `renderContextMapGraph(category)` and
  `renderBoundedContextsExtrasHtml(category)` take the active tab's category the same way
  `renderSpiMapGraph(subsystem)`/`renderSpiMapExtrasHtml(subsystem)` already do for SPI Map. Node
  set per tab = only domains actually touched by that category's edges (not all 8 every time) —
  real decluttering for 3 of the 4 tabs (Hook Callbacks: 4 nodes, Cross-Starter Exceptions: 2 nodes,
  Derived Facts: 5 nodes; Service Calls stays close to the full 8 since it's the main flow). Domain
  Contents/Overview/Legend stay unfiltered across all 4 tabs (same precedent as SPI Map's Overview/
  Legend/Call Flow Examples staying global while only the interface-details table filters).
  Relationships table filters to the active category. `exportBoundedContextsMarkdown()` stays a
  single unfiltered document (all 16 relationships, one file) with a `Category` column added.
- Regenerate `architecture-model.json`/`architecture-map.html`, verify JSON validity + freshness
  check, record the decision in `scripts/architecture/DECISIONS.md` (new ADR, refining ADR-029),
  regenerate the ADR index.

**Done.** Verified directly, not assumed: regenerated `architecture-model.json` and confirmed via a
Python check that each of the 16 non-`decouples` relationships now carries the expected
`"category"` (`orchestration`: 8, `hooks`: 4, `exceptions`: 1, `derived`: 4, matching the plan
exactly), that `diagramGroups`'s `bounded-contexts` entry now lists the 4 expected diagram titles
in order, and that the freshness check (`check-architecture-model-freshness.sh`) passes. Extracted
and `node --check`ed the generated inline `<script>` block to confirm no JS syntax error was
introduced by the `renderBoundedContextsExtrasHtml`/`buildContextMapGraph`/`renderContextMapGraph`
signature changes. Recorded as a "Refinement (same session)" note on ADR-029 in
`scripts/architecture/DECISIONS.md`; ADR index regenerated (`docs/ai/adr-index.md`, 221 entries,
same 4 pre-existing non-standard-format gaps as before, unrelated to this change).
Unit/integration/Playwright still pending overall — this round only touched the bash generator
script, no Java changes, so no new compile check was needed either.

**Thirteenth addition, same session — Domain Contents now filters per active category too.** User
asked to also filter "Domain Contents (8)" instead of showing all 8 domains unfiltered on every
tab (the original plan deliberately left it unfiltered, mirroring SPI Map's Overview/Legend/Call
Flow Examples staying global). `renderBoundedContextsExtrasHtml(activeCategory)` now computes
`involvedIds` from the active category's own filtered relationships (`r.from`/`r.to`) and filters
`MODEL.boundedContexts.domains` to that set — the same domain set the diagram canvas itself draws
for that tab, so Domain Contents and the diagram never disagree about what's "on screen." Verified:
regenerated `architecture-model.json`/`architecture-map.html`, freshness check passes, extracted
and `node --check`ed the inline script again — clean.

**Fourteenth addition, same session — the "What crosses (payload type)" column was wrong for 3 of
4 Hook Callback edges, fixed with per-Hook-interface payloads.** User asked to look closer at
`Audit -> UI`'s payload cell, which led to two rounds of investigation and a real fix (see
`scripts/architecture/DECISIONS.md`'s new ADR-030 for the full record):

1. Traced the real `Audit -> UI` call path end to end with actual class/method names
   (`AuditReadService` holds `List<AuditActivityEnrichHook>`, Spring DI wires in
   `ActivityEnrichHookImpl` from `marketplace-app`, zero compile-time import either direction) —
   confirmed this is a legitimate, documented dependency-inversion exception
   (`marketplace-orchestrator/CLAUDE.md`: `ActivityEnrichHookImpl` stays in marketplace-app because
   its collaborator does real HTML-diff formatting, not pure delegation), not a BFF violation.
2. Found the real bug while grounding that explanation: `BC_LABEL_PAYLOAD["calls back via Hook
   implementations"]` was one hardcoded text shared across all 4 hook-callback edges, and it cited
   `AuditActivityFieldsHook` — an interface deleted from the codebase entirely earlier this session.
   Verified: 0 matches for that name anywhere in `.java` source. The text was also simply wrong for
   3 of the 4 real edges (`Audit -> Orchestrator`/`Attachment -> Orchestrator`/`Orchestrator -> UI`
   each carry completely different real types).
3. Fix: added `BC_HOOK_PAYLOAD` (bash, keyed by real interface name — `AuditDomainHook`,
   `AuditActivityEnrichHook`, `CurrentActorHook`, `UiLabelHook`, `SessionActorHook` — each mapped to
   its own real method signatures, checked directly against the interface source), extended
   `add_rel()` with an optional 7th `payload_override` argument accumulated per-edge (parallel
   `rel_payload` array, deduped by substring so a caller-domain hit twice for the same hook doesn't
   duplicate its fragment), and passed `"$hook_iface: ${BC_HOOK_PAYLOAD[$hook_iface]}"` from the
   hook-callback loop. `rel_json` emission now uses the per-edge override when present, falling back
   to the old generic `BC_LABEL_PAYLOAD[label]` text for every other label (accurate as one fixed
   text per label there — only the Hook Callbacks label needed per-edge granularity). Also fixed a
   stale illustrative comment in `spi_map_json()` that cited the same deleted interface as an
   example, replacing it with the still-real `UiLabelHook`/`SessionActorHook` case.
4. **Hit and fixed a real bash gotcha while implementing:** an apostrophe inside a `${VAR:-default
   text}` fallback value, even though the whole expression sat inside outer double quotes, broke
   bash's parser (`unexpected EOF while looking for matching \`'\``) — confirmed by isolating the
   single line into a minimal repro script before touching the real fix. Removed the apostrophe
   from the fallback text instead of fighting the quoting.

**Verified, not assumed:** regenerated `architecture-model.json`, printed every "calls back via
Hook implementations" edge's `payload` field directly — all 4 now show distinct, real values
matching each edge's actual `*Hook` interface method signatures (e.g. `Attachment -> Orchestrator`
now correctly shows only `CurrentActorHook: Optional<Long> (getCurrentActorId)`, not the old
Audit-specific text). Freshness check passes; extracted and `node --check`ed the inline script —
clean; `bash -n` on the whole generator script also passes.

**Found but deliberately not fixed this round (flagged, not silently dropped):** while chasing the
payload bug, found `spi_call_flow_examples_json()` (hand-typed narrative call traces, "carried
over verbatim from the retired 02-spi-map.md") has drifted stale in all 3 entries after this
session's Hook-relocation/orchestrator-extraction work — e.g. "Enrich Audit Activity" still cites
`AuditActivityFieldsHook.fields()`/`AdvertisementActivityFieldsHookImpl` (both deleted), "Create
Advertisement with Audit" cites `org.ost.marketplace.spi.AuditDomainHookImpl` (moved to
`marketplace-orchestrator/spi`, and its real methods don't include `.on(CREATED, ...)` either), and
"Upload Media to Advertisement" cites `AdvertisementService.enrichWithMediaSummary()` (no longer
exists — enrichment now lives in `marketplace-orchestrator`'s `AdvertisementDisplayEnrichmentService`).
Rewriting all 3 accurately needs its own scoped pass (verify each full call chain from scratch),
which is bigger than "fix the payload column" — proposing this as a follow-up rather than expanding
scope unasked. **Logged as entry 10 in `improvement-133-deferred-oversized-review-findings.md`**
(the standing deferred-findings bucket — user confirmed that's the right home, not `improvement-150`,
which is unrelated in scope).

**Fifteenth addition, same session — `ChangeEntry.mapField()` added, `AuditTimelineRowRenderer.
applyLabel()` refactored to use it (real code change, not just the diagram generator).** User
spotted that `applyLabel()` (`marketplace-app/.../audit/AuditTimelineRowRenderer.java`) hand-rolled
its own `switch` over `ChangeEntry.FieldChange`/`MediaChange` to relabel a field name, duplicating
the exact `instanceof FieldChange(...) / else passthrough` idiom `ChangeEntry.replaceIfField()`
already existed for — and `replaceIfField`'s own Javadoc claimed to be "the single instanceof-check
... in the codebase," which `applyLabel()` quietly contradicted. Confirmed `replaceIfField` couldn't
be reused directly (it targets one known `fieldName` and rewrites values; `applyLabel` needs to
rewrite whichever field name is present, unconditionally) — added a sibling default method instead:
`ChangeEntry.mapField(UnaryOperator<String> fieldFn)`, same file, same "pure derivation over the
record's own fields" precedent `replaceIfField` and `platform-commons/DECISIONS.md` ADR-021
(`AuditTimelineItemDto.expandedChanges()`) already established. `applyLabel()` now reads
`entry.mapField(field -> labelFor(entityType, field))` — one line instead of a 5-line switch. Both
new/edited Javadocs shortened to one line per user request (repo's "one line or none" comment
convention). Verified: `./mvnw -pl platform-commons,marketplace-app -am compile` — clean, exit 0.

## Related

- `backlog/issues/improvement-150-marketplace-app-zero-deps-except-orchestrator.md` — direct
  follow-up filed mid-session, tightening Point 5's result further (zero deps on platform-commons/
  query-lib too, not just the 6 starters).
- `backlog/completed/issues/improvement-147-marketplace-orchestrator-followups.md` — the migration
  whose effect on these two diagrams triggered this investigation.
- `backlog/issues/improvement-148-reverify-optional-module-removal-after-bff-migration.md` — Point
  5's optional-starter-removal verification should fold into this issue's scope when picked up
  (same check, don't duplicate a second removal test).
- `scripts/architecture/DECISIONS.md` ADR-015 — already recorded a decision that Bounded Contexts
  stays a separate, hand-distinct diagram from Module Dependencies/SPI Map; read this first before
  re-deriving Point 2 from scratch, since the original reasoning may already answer it (or may
  itself need updating if it doesn't hold up under this issue's own litmus-test approach).
- `scripts/architecture/DECISIONS.md` ADR-003/ADR-016/ADR-018/ADR-019/ADR-025 — related generator
  design history for both diagrams' evolution.

## Operational notes
- token_cost_review: n/a (no `/code-review` ran this session; verification was direct manual grep/read/compile, not Agent-tool-based)
- token_cost_research: n/a (no Agent-tool calls made — all investigation done directly via Bash/Read/Grep in the main thread)
- token_cost_verification: n/a (test execution was direct script runs via Bash/Monitor, not Agent-tool calls)
- review_signal_ratio: n/a (no `/code-review` ran this session)
- context_loading_task_type: Architectural change (new SPI relocation, `*Hook` implementations moved between modules) for the bulk of the session; the final rounds (payload fix, `ChangeEntry.mapField()`) were closer to Local refactor, single class/package
- context_loading_consulted: yes
- context_loading_matched: yes — `platform-commons/DECISIONS.md`, `marketplace-orchestrator/CLAUDE.md`/`DECISIONS.md`, and `docs/architecture/architecture-map.html` were all read in full before each design change, matching the "Architectural change" row's guidance
- flows_situation: iterative diagram-legibility + Hook-relocation + doc-accuracy fixes, driven turn-by-turn by direct user instruction rather than a single up-front plan
- flows_chosen: n/a — no `/autopilot` or other documented flows.md command was invoked this session; every round was a direct propose-then-approve exchange
- flows_matched: n/a

### Script/command runs
- bash scripts/architecture/generate-architecture-model.sh | tail -30 -> Valid JSON, 33 nodes | duration_s=n/a (not individually timed; run repeatedly across ~15 rounds) | mode=background/foreground mixed | result=pass (every run)
- bash scripts/architecture/check-architecture-model-freshness.sh | duration_s=n/a | mode=foreground | result=pass (every run)
- bash scripts/ai/generate-adr-index.sh | duration_s=n/a | mode=foreground | result=pass (221 entries, same 4 pre-existing non-standard-format gaps throughout)
- ./mvnw -pl platform-commons,marketplace-app -am compile | duration_s=n/a | mode=background | result=pass (exit 0)
- bash scripts/deploy.sh --reset | duration_s=~110 | mode=foreground | result=pass (app started, health check 200)
- bash scripts/run-all-tests.sh --integration "--sandbox" --playwright "e2e --full --ux" | duration_s=n/a (auto-backgrounded by harness; unit-tests reactor alone reported "Total time: 14:00 min", integration-tests reactor "Total time: 01:55 min", playwright "50 passed (11.3m)") | mode=background | result=pass — unit-tests 72/72, integration-tests 165/165, Playwright 50/50, ALL PASSED
