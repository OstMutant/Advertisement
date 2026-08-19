# improvement-133: deferred oversized review findings (running collection)

**Type:** improvement — meta/process issue, ongoing collection bucket, not a single fix.
**Module:** cross-cutting — whichever module each entry below actually touches.
**Priority:** 🔵 larger tech-debt — no live bug, entries need a design decision before sizing.
**When:** ongoing; triage entries opportunistically or during a backlog grooming pass, not on a fixed schedule.

## Purpose

A running catch-all for `/code-review`/`/deep-review` (or any other review) findings that are
real and worth fixing, but whose solution is too large to fit in the batch/PR that surfaced them
(a new abstraction, an architectural change, a cross-module refactor). Per `.claude/rules.md`
"Out-of-scope-but-valid findings", such findings are proposed for approval and then appended here
as a new entry — never dropped silently, and never spun into a brand-new issue file per finding.

Each entry below is independent; when one is picked up, evaluate it fresh (the surrounding code
may have moved on since it was logged), size it properly, and either fold it into its own
issue/batch or resolve it directly. Remove the entry once resolved — same lifecycle as any other
finding, just deferred.

## Entries

### 1. query-lib: compile-time enforcement of the `Fields.*` convention (from Batch H review, 2026-07-30)

`query-lib/CLAUDE.md` documents that `SqlBoundFilter.of()`'s `filterProperty` argument must always
be a typed `Fields.*` constant, never a raw string literal — but nothing enforces this at compile
time today; it is convention only, and a new call site can silently reintroduce a raw string (as
`TaxonRepository.java` did before Batch H's item 32 fix). Surfaced by the Altitude review angle
during Batch H's retroactive 8-agent review. Needs a design decision (marker interface? annotation
processor? ArchUnit rule alongside the existing `ArchitectureRulesTest`?) before it can be sized.

### 2. query-lib: `inSet`/`anyOf` null-vs-empty cardinality semantics asymmetry (from Batch H review, 2026-07-30)

`SqlCondition`'s scalar factories (`like`/`equalsTo`/`after`/`before`) return `null` only when the
value is absent; `inSet`/`anyOf` return `null` for an absent *or* empty collection — a real
semantic difference that today is only documented in a Javadoc paragraph (fixed as part of Batch H
item 25), not encoded in the type system. Surfaced by the same Altitude pass. Possible directions:
a `SqlOperator`-level cardinality marker, or splitting the factory method shapes — needs a design
decision, not a mechanical fix.

### 3. `integration-tests/run.sh` silently drops all but the last test-class scenario argument (found during Batch I, 2026-07-30)

`run.sh`'s arg-parsing loop (`for arg in "$@"; do ... else SCENARIO="$arg"; fi; done`) overwrites
`SCENARIO` on every non-flag argument instead of accumulating them, so
`bash scripts/integration-tests.sh --sandbox ClassA ClassB ClassC` silently runs only `ClassC` —
`ClassA`/`ClassB` are dropped with no warning or error. Confirmed directly during Batch I: a run
intended to cover 5 attachment-domain test classes only ran the last one named; had to be redone
with a single comma-joined `-Dtest=...`-style argument instead (which the script does forward
correctly as one opaque `SCENARIO` string). Fix is small and mechanical (join extra positional args
with a comma instead of overwriting) but touches a shared script used by every batch's test cycle,
so it's deferred here for a deliberate pass rather than a rushed inline fix.

### 4. Unify entity→DTO simple-mapping convention across all modules (decided during Batch C, 2026-07-30)

**Convention adopted:** when a DTO is built by pure field copy with no join/enrichment (no locale,
no related-entity lookup, no external service call), the mapping method is `entity.toDto()` — an
instance method on the entity itself, not a static method on a `*Service` class and not inlined
per-repository. Decided while fixing improvement-132 Batch C's `UserService.toDto(User)`: that
method was originally kept as a `public static` method on `UserService` (mirroring
`AttachmentService.toDto(Attachment)`'s existing shape), but a `/code-review` finding caught that
this created a real `services`↔`security` import cycle once `UserPrincipal` needed to call it too
(`UserService` already imports and constructs `UserPrincipal` in `refreshSecurityContext()`).
Moving the mapping onto `User.toDto()` broke the cycle at the source — both callers already depend
on the entity, so no new dependency direction is introduced regardless of how many packages need
the mapping. `AttachmentService.toDto(Attachment)` (the precedent that was originally mirrored) was
corrected in the same conversation to `Attachment.toDto()`, since it was a pure field-copy mapping
with the identical shape, once the inconsistency was pointed out — not deferred, already applied.

**Not covered by this convention** (deliberately excluded, not an inconsistency to fix):
`AdvertisementInfoDto`/`TaxonDto`, whose construction needs joined/locale-dependent data
(category names, city name, translations) — those correctly stay as repository `RowMapper`s or
service/port-level builder methods, since `entity.toDto()` can't reach data outside the entity's
own row.

**Still open:** only `User`/`Attachment` have been audited and fixed so far — this entry tracks
the remaining work of auditing every other entity in every starter (`taxon`, `audit`,
`advertisement`) for a simple, no-enrichment DTO mapping currently living somewhere other than
`entity.toDto()` (a repository `RowMapper` inlining a pure field copy would also count, not just
a `*Service` static method), and migrating any found onto this convention for full consistency.

**Why not Lombok or MapStruct instead of a hand-written method:** Lombok has no annotation for
mapping between two different classes (`@Value`/`@Builder` only generate accessors/constructors
for the class they're on) — that's MapStruct's job, not Lombok's. MapStruct itself is already a
project dependency (root `pom.xml` manages `mapstruct.version`) and already used in `marketplace-
app` (`ui/mappers/UserMapper`, `AdvertisementMapper`, etc., for UI-form↔DTO mapping) — but checked
directly: **no domain starter** (`user-`, `attachment-spring-boot-starter`, etc.)
currently depends on it. Introducing it into a starter for one or two simple mapping methods would
be a new dependency addition, not a small fix — out of proportion here. Revisit only if a future
starter accumulates enough of these simple mappings that a `@Mapper` interface would clearly pay
for the added dependency.

### 5. `UserFormOverlayModeHandler` repeats the same field-copy block, same shape improvement-132 Batch F just fixed for advertisements (found during Batch F review, 2026-07-30)

`marketplace-app/.../users/overlay/modes/UserFormOverlayModeHandler.java` inline-duplicates the
same two-field (`name`, `role`) copy lambda at `loadRestored()` and `discardChanges()` — the exact
shape `improvement-132` Batch F's item 14 just extracted into `copyEditFields()` for
`AdvertisementFormOverlayModeHandler`. Surfaced by Batch F's own `/code-review` reuse-angle finder
while reviewing the item 14 fix; left untouched since it's a different class outside Batch F's
approved scope (items 13/14/15/17, all in `AdvertisementFormOverlayModeHandler`/buttons/fields/
`AccessEvaluator`). Fix is mechanical and small — extract a `copyEditFields(src, tgt)` method
mirroring the same precedent (`TaxonFormOverlayModeHandler.copyLocaleFields()`) — but is deferred
here rather than expanded into Batch F, since touching a second, unrelated form handler mid-batch
would have widened the reviewed diff beyond what was approved.

### 6. `integration-tests`: `@EnableJdbcAuditing` still copy-pasted alongside `@RepositoryTestAutoConfig` in all 5 locations (found during Batch K review, 2026-07-31)

Batch K (item 31) centralized the 8-class `@ImportAutoConfiguration` array into one composed
annotation, `org.ost.integrationtests.support.RepositoryTestAutoConfig`, applied at
`RepositoryTestSupport` and 4 `*RepositoryTest`/`*TransactionTest` local `TestConfig` classes. The
adjacent `@EnableJdbcAuditing` line is a fixed constant with no per-site variation, byte-identical
in all 5 locations, and was left untouched — folding it into the same composed annotation (or a
new one) would fully collapse the pair to one annotation everywhere. Left out of Batch K's scope
(item 31 named only the `@ImportAutoConfiguration` array); a future touch to how JDBC auditing is
enabled would otherwise still require editing all 5 files.

### 7. `integration-tests`: `auditorAware()`/`currentActorHook()`/`@MockitoBean S3Client+StorageService` bean triads duplicated across 3-4 `TestConfig` classes (found during Batch K review, 2026-07-31)

Beyond the `@ImportAutoConfiguration` array Batch K deduped, the same 5 `TestConfig` classes still
duplicate other boilerplate wholesale: `AttachmentRepositoryTest` and
`AttachmentSnapshotRepositoryTest` both declare byte-identical `auditorAware() -> Optional::empty`
and `currentActorHook() -> Optional::empty` beans, and 3 of the 4 attachment/audit `TestConfig`s
duplicate `@MockitoBean S3Client`/`@MockitoBean StorageService` declarations. Not addressed in
Batch K (out of the approved item-31 scope) — needs a design decision (a second shared
`@TestConfiguration` bean bag alongside `RepositoryTestSupport`? a builder-style test fixture?)
before it can be sized as a mechanical fix.

### 8. `TaxonFormOverlayModeHandler`/`CityFormOverlayModeHandler` pure duplication (from improvement-132 Batch E, 2026-07-29)

Every method, field, and control-flow path in `TaxonFormOverlayModeHandler.java` and
`CityFormOverlayModeHandler.java` matches line-for-line — a pure mechanical rename of each other.
`marketplace-app/DECISIONS.md` ADR-065's stated reason for not sharing a generic base (`TaxonOverlay`
is a `@UIScope` singleton needing distinct bean instances per simultaneous tab) applies to the
`*Overlay` classes, not to these two `@Scope("prototype")` handlers, which by construction get a
fresh instance per lookup regardless of parameterization — so ADR-065's exception doesn't actually
cover this case. Real risk already realized per ADR-065/066's own "fixed in one copy, not the
sibling" bug history. Needs a design decision (shared prototype-scoped base class vs. extending
ADR-065's stated exception to cover handlers too) before it can be sized as a mechanical fix — not
picked up as part of improvement-132's Batch E since every other batch there was a same-file
mechanical edit and this one isn't.

### 9. `scripts/architecture/DECISIONS.md`: ADR-003 and ADR-024 are UI/layout churn that shouldn't have been separate ADRs (found 2026-08-07)

`scripts/architecture/DECISIONS.md` (1556 lines / 21 ADRs) documents `architecture-map.html`'s own
generator (`generate-architecture-model.sh`, 2955 lines) — already the direct motivation for
`improvement-145`'s on-demand ADR extraction. Auditing all 21 entries against
`.claude/commands/decision.md`'s worthiness gate (tightened in the same conversation to explicitly
state that a tool being *about* architecture doesn't exempt its own UI/layout changes from the
gate) surfaces two clear demotion candidates:

- **ADR-003** ("System page holds only the 3 entry-point cards") — a pure screen-layout count
  that's already silently wrong (the System screen has grown to 6 cards since, across ADR-023's
  "ADRs + Notes cards" and a later conversation's now-removed Docker card), never updated when it
  changed. A textbook "UI/layout adjustment" the gate says shouldn't be a standalone ADR.
- **ADR-024** ("SonarQube/ArchUnit metrics consolidated onto a dedicated Code Quality screen,
  removed from Module pages") — a screen reorganization with no mechanism decision inside it
  (contrast ADR-022/023, kept: both are borderline-UI-titled but each contains a real reusable
  mechanism — a self-documenting header convention, a live-ADR-list data-source choice — not just
  "a section moved").

Not fixed now: demoting/merging existing ADR entries means editing an append-only historical
record (`doc-standards` `SKILL.md`'s explicit carve-out: `DECISIONS.md` — "write what happened,
accurately; optimizing an ADR for brevity over completeness is the wrong trade"), a different and
more sensitive kind of edit than appending a dated Amendment note (already done for ADR-022 in the
same conversation this was found in). Needs its own explicit go-ahead and a decided approach
(delete outright vs. fold into a neighboring ADR's Consequences vs. mark `Status: Superseded`)
before touching it.

### 10. `spi_call_flow_examples_json()`'s 3 hand-typed narrative call traces have drifted stale after this session's Hook-relocation/orchestrator-extraction work (found during improvement-149, 2026-08-11)

`scripts/architecture/generate-architecture-model.sh`'s `spi_call_flow_examples_json()` — 3
hand-typed narrative call traces "carried over verbatim from the retired 02-spi-map.md", the only
part of the SPI Map screen with no mechanical/live source — have gone stale in all 3 entries after
this same session's Hook-relocation and marketplace-orchestrator-extraction work:

- **"Create Advertisement with Audit"** cites `org.ost.marketplace.spi.AuditDomainHookImpl.on(CREATED,
  ...)`. Wrong on two counts: `AuditDomainHookImpl` moved to `org.ost.orchestrator.spi` this
  session (no longer in `marketplace-app`), and its real methods are `resolveNames`/
  `findExisting`/`resolveDisplayName`/`castIfKnown` — there is no `.on(CREATED, ...)` method at
  all. The step before it (`AdvertisementService.save()` calling `DefaultAuditPort.captureCreation()`
  directly) is also wrong — that composition now happens in `marketplace-orchestrator`'s
  `AdvertisementSaveService`, not inside the starter's own `AdvertisementService`.
- **"Upload Media to Advertisement"** cites `AdvertisementService.enrichWithMediaSummary()` — no
  longer exists; the real enrichment step is `marketplace-orchestrator`'s
  `AdvertisementDisplayEnrichmentService`.
- **"Enrich Audit Activity"** cites `AuditActivityFieldsHook.fields()` and
  `org.ost.marketplace.spi.AdvertisementActivityFieldsHookImpl` — both deleted from the codebase
  entirely this same session (the whole `AuditActivityFieldsHook` interface and its four per-domain
  implementations were removed, see `platform-commons/DECISIONS.md` ADR-029's third refinement).

Not fixed inline when found (during the Bounded Contexts payload-column fix, see
`scripts/architecture/DECISIONS.md` ADR-029's latest refinement) — rewriting all 3 accurately
needs its own scoped pass, re-verifying each full call chain end to end from scratch (class names,
method names, and the real current owning module for each step), which is bigger than the payload
fix that surfaced it. No design decision needed here, just careful re-tracing — a mechanical fix
once picked up.

### 11. `TaxonManagementView`/`CityManagementView` render the "Add" button unconditionally even when `taxon-spring-boot-starter` is absent (found during improvement-147 review, 2026-08-08)

`refresh()` in both views constructs and adds the "Add category"/"Add city" button unconditionally,
outside any `taxonCatalogService.isAvailable()` guard — while the list-rendering/count calls
correctly degrade to an empty list when the optional `taxon-spring-boot-starter` module
(`<scope>runtime</scope>` in `marketplace-app/pom.xml`) is absent. Clicking "Add" in that state opens
a create overlay whose save path calls `TaxonCatalogService.create()`/`.update()` — these call
`taxonPortFactory.get()` (hard unwrap, not `.findIfAvailable()`), so saving throws instead of
degrading gracefully, unlike `softDelete()`/`restore()` on the same service, which do use
`.ifAvailable()`. Confirmed via `git show HEAD` that the button was already unconditional *before*
improvement-147's migration too — not a regression that migration introduced, just an existing gap
it didn't happen to touch. Root `CLAUDE.md`'s "MUST degrade gracefully via `ObjectProvider
.ifAvailable()`" rule is written only for `attachment-spring-boot-starter`/`StorageService`, not
generically for every optional starter, so this isn't a direct rule violation either — an analogous
gap, not a documented one. Needs a decision: guard the button behind `isAvailable()` (simplest), or
make `TaxonCatalogService.create()`/`.update()` themselves degrade gracefully (larger, changes what
"create with no taxon starter" means to callers) — not sized here.
