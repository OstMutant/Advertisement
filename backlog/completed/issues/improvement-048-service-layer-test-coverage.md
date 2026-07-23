# improvement-048: Cover `marketplace-app`'s non-UI service layer with unit tests, mirroring `src/main`'s package layout under `src/test`

**Type:** improvement — testing infrastructure/process. Follow-up to
[improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) item 1
(`AccessEvaluatorTest`), which proved the pattern: `marketplace-app` already has a real,
UI-free service layer (`org.ost.marketplace.services.*` — verified zero `com.vaadin.*` imports
across all 10 files in that package) that orchestrates domain-starter Ports/Hooks, and it can be
unit-tested directly in `marketplace-app`'s own `src/test/java` with no Spring context and no
Testcontainers, the same way `AccessEvaluatorTest` was just added.
**Module:** `marketplace-app` only — these classes belong to `marketplace-app`, not
`integration-tests` (see "Why not `integration-tests`" below) and not `platform-commons` (see
"Why not `platform-commons`" below).
**Priority:** medium — no known live bugs in this layer, but it's real orchestration logic
(transaction boundaries, audit snapshot diffing, media/category enrichment) with the same
"looks right in review, wrong at runtime" risk profile as the items already covered in
improvement-045.
**When:** independent, no blockers.

## Context: the layer already exists, this issue only adds test coverage to it

Not a proposal for new architecture. Verified directly:
`org.ost.marketplace.services.{advertisement,auth,i18n,security}` — 10 `.java` files, all plain
Spring `@Service`/`@Component` beans, **zero `com.vaadin.*` imports**. This is already a clean,
UI-independent layer sitting between `ui/` and the domain-starter Ports — it's just never been
named as one or had its own test tree. This issue does two things: adds the missing tests, and
gives them one consistent home so the layer's test coverage is easy to find as a set (see
"Suggested fix" placement below) — not a code reorganization of the production classes themselves,
which already live in the right place.

## Why not `integration-tests`

`integration-tests` exists specifically so domain *starters* never carry test code themselves — it
depends on starters (`compile` scope) precisely because it's never shipped and nothing depends on
it back (see `integration-tests/CLAUDE.md` ADR-001). Putting a `marketplace-app` class's test there
would require `integration-tests` to depend on `marketplace-app`, inverting that direction —
`marketplace-app` already depends on everything else; nothing should depend on it. Same reasoning
already applied when placing `AccessEvaluatorTest` in `marketplace-app/src/test/java` instead of
`integration-tests`.

## Why not `platform-commons`

`platform-commons/CLAUDE.md` governance is explicit: "NOT ALLOWED: Business logic of any kind...
Spring `@Service`/`@Component` beans... Rule: if only one module needs it — it lives in that
module, not here." `AdvertisementSaveService`/`AdvertisementEnrichService` are real orchestration
logic consumed only by `marketplace-app`; `AuthContextService` depends on Spring Security's
`SecurityContextHolder`, a dependency `platform-commons` deliberately has none of. Moving any of
this to `platform-commons` would violate an existing, explicit rule — not a judgment call.

## Candidates found, and what's worth testing

Read all 10 files in `org.ost.marketplace.services.*` before scoping this list — excluded the
trivial ones (`I18nServiceImpl` is a one-line delegation to `MessageSource`; `InstantFormatter`/
`LocaleProvider` are bare interfaces with no logic in this package) per the same "skip low-stakes
getters/trivial mappers" judgment already applied in improvement-045's own scoping.

### 1. `AdvertisementSaveService.save()`
`/app/marketplace-app/src/main/java/org/ost/marketplace/services/advertisement/AdvertisementSaveService.java:36-77`

Real orchestration: wraps a `TransactionTemplate`, builds the before/after `AdvertisementSnapshotDto`
pair for the audit diff, decides `captureCreation` vs `captureUpdate` based on `isNew`, and has a
non-obvious `attachmentSnapshotId` fallback (`gallerySnapshotId != null ? gallerySnapshotId :
before.attachmentSnapshotId()`) that preserves the previous media snapshot reference when the
gallery itself wasn't touched during this save. **Failure scenario:** if the fallback logic
regresses, an edit that only changes the title could silently lose its media-snapshot linkage,
making the audit timeline's media diff show "media removed" when nothing about the media changed.

### 2. `AdvertisementEnrichService.mergeMediaChanges()` / `.enrichActivityItems()` / `resolveCategories()`
`/app/marketplace-app/src/main/java/org/ost/marketplace/services/advertisement/AdvertisementEnrichService.java:35-126`

Merges attachment-hook media changes into audit timeline/activity entries, and resolves raw
category IDs in a `ChangeEntry.FieldChange` into human-readable names via a pattern-matched
`switch` on `ChangeEntry.FieldChange` filtered by field name (`AdvertisementSnapshotDto.Fields
.categoryIds`). **Failure scenario:** a regression in `resolveCategories()`'s field-name match
silently stops resolving category names — the audit diff would show raw numeric IDs instead of
names with no error, easy to miss since it only affects the "changed" case, not every row.

### 3. `AuthContextService.getCurrentUser()`
`/app/marketplace-app/src/main/java/org/ost/marketplace/services/auth/AuthContextService.java:15-30`

Reads `SecurityContextHolder`, narrows `Authentication.getPrincipal()` to `AuthenticatedPrincipal`,
and has a defensive `catch (Exception)` that logs and returns `Optional.empty()`. This is the
single source `AccessEvaluator` (and everything downstream of it) relies on for "who is the current
user" — worth testing directly via `SecurityContextHolder.setContext(...)` (standard Spring
Security test pattern, no mocking of the holder itself needed) rather than only indirectly through
`AccessEvaluatorTest`'s mocked `AuthContextService`.

### Explicitly not included here (already tracked elsewhere)
`AuthService` (login/register rate limiting) is
[improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) item 3 — do
not duplicate tracking.

## Suggested fix — one consistent home for this layer's tests

Mirror `src/main`'s package layout under `src/test`, exactly as `AccessEvaluatorTest` already does
(`services/security/AccessEvaluator.java` → `services/security/AccessEvaluatorTest.java`). This is
already the idiomatic Java/Maven convention and needs no new tooling — applying it consistently
means every test for this layer lives under one coherent subtree,
`marketplace-app/src/test/java/org/ost/marketplace/services/**`, directly parallel to the
`services/**` package that *is* the layer being tested. No separate module, no relocation of the
production classes.

1. `services/advertisement/AdvertisementSaveServiceTest.java` — mock
   `ComponentFactory<AdvertisementPort>`/`ComponentFactory<AttachmentPort>`/
   `ComponentFactory<TaxonPort>`/`ComponentFactory<AuditPort>` and a real (non-mocked)
   `TransactionTemplate` backed by a no-op `PlatformTransactionManager` test double (or mock
   `TransactionTemplate.execute()` to just invoke the callback directly — simpler, and sufficient
   since this class doesn't test transaction rollback behavior itself). Cover: create path
   (`captureCreation` called, not `captureUpdate`), update path (`captureUpdate` called with
   correct before/after), the `attachmentSnapshotId` fallback in both directions (gallery touched
   vs. not touched), missing-optional-port graceful degradation (taxon/audit ports absent).
2. `services/advertisement/AdvertisementEnrichServiceTest.java` — mock
   `ComponentFactory<AttachmentAuditHook>`/`ComponentFactory<TaxonPort>`. Cover:
   `mergeMediaChanges()` merges when present, no-ops when hook absent or snapshot id null;
   `enrichActivityItems()`'s category-name resolution (present, missing → falls back to raw id
   string per `idsToNames()`), and the media-changes-on-activity-item path.
3. `services/auth/AuthContextServiceTest.java` — set `SecurityContextHolder` directly per test
   (with a `@AfterEach` clearing it, to avoid leaking state across tests same-JVM). Cover:
   authenticated with `AuthenticatedPrincipal` principal → `UserDto` returned; unauthenticated →
   empty; non-`AuthenticatedPrincipal` principal type → empty (not an exception); an
   `Authentication` that throws on access → caught, empty, not propagated.

## Required verification

`mvn -pl marketplace-app -am test -Dtest=AdvertisementSaveServiceTest,AdvertisementEnrichServiceTest,AuthContextServiceTest`
(or the equivalent Monitor+tee pattern per `.claude/rules.md`) — all green, no Docker required for
any of these three classes.

## Related

- [improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) item 1 —
  `AccessEvaluatorTest`, the precedent this issue's placement/pattern follows.
- `integration-tests/CLAUDE.md` ADR-001 — why `integration-tests` never hosts `marketplace-app`
  tests.
- `platform-commons/CLAUDE.md` "Governance" — why this layer's classes never move there.

## Resolution (2026-07-17)

Added all three planned classes under `marketplace-app/src/test/java/org/ost/marketplace/services/`,
mirroring `src/main`'s package layout exactly as `AccessEvaluatorTest` already does. Re-read the
current source of all three target classes before writing tests, since `AdvertisementEnrichService`
had changed shape since this issue was filed (ADR-043's `ChangeEntry.replaceIfField()` /
`prevSnapshotData` refactor postdates this issue's original scoping) — tests target the code as it
exists today, not the issue's original description.

- `services/advertisement/AdvertisementSaveServiceTest` (5 tests) — create vs update path
  (`captureCreation` vs `captureUpdate`), the `attachmentSnapshotId` fallback in both directions
  (gallery touched → uses the fresh snapshot id; not touched → falls back to the previous one),
  and graceful completion when `taxonPortFactory`/`auditPortFactory` are unstubbed (simulating the
  optional-starter-absent shape).
- `services/advertisement/AdvertisementEnrichServiceTest` (9 tests) — `mergeMediaChanges()` (Timeline)
  and `enrichActivityItems()` (Activity): media changes merge ahead of resolved category changes
  when the attachment hook is available, no-op gracefully when it's absent, category-name
  resolution falls back to the raw id string when `TaxonPort` is absent, non-`ADVERTISEMENT`
  timeline entries pass through untouched, and `enrichActivityItems()` only pulls media changes
  when the snapshot's `attachmentSnapshotId` actually differs from the previous one (not on every
  row). Plus `getMediaStateForSnapshot()`'s null-id short-circuit and hook-present/absent paths.
- `services/auth/AuthContextServiceTest` (5 tests) — authenticated with `AuthenticatedPrincipal` →
  `UserDto`; no `Authentication` in context → empty; `isAuthenticated() == false` → empty; a
  non-`AuthenticatedPrincipal` principal type → empty (not an exception); `Authentication` that
  throws on `isAuthenticated()` → caught, empty, not propagated. `SecurityContextHolder` set/cleared
  directly per test (no mocking of the holder itself), per the plan's stated approach.

`ComponentFactory<T>` (a plain, non-final class wrapping `ObjectProvider<T>`) is mocked directly in
both advertisement-service tests rather than mocking the underlying `ObjectProvider` — a small
`stubAvailable(factory, component)` helper (duplicated locally in each of the two files, ~6 lines,
not worth a shared test-support class) stubs `findIfAvailable()`/`ifAvailable()` together; when left
unstubbed, Mockito's default-empty-values behavior already returns `Optional.empty()` for
`findIfAvailable()` and no-ops `ifAvailable()`, which is exactly the "optional starter absent" shape
production code sees via `ObjectProvider` — no extra stubbing needed for the absent-port test cases.

Verified via `bash scripts/unit-tests.sh marketplace-app` — BUILD SUCCESS, all 3 new classes green
(19 new tests total), plus `ArchitectureRulesTest` (8/8) confirming no ArchUnit rule violations from
the new test code.
