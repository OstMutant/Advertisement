# improvement-202: `OptimisticLockingFailureException` couples every layer to Spring Data's exception vocabulary

**Type:** improvement — architecture/decoupling (deferred-findings bucket, carved out of
`improvement-201` Part 4 once sized for real work).
**Module:** platform-commons (`AdvertisementPort`/`ProviderProfilePort`/`TaxonPort` Javadoc
contracts) + advertisement/provider-profile/taxon/user-spring-boot-starter (repositories) +
marketplace-orchestrator (`AdvertisementSaveService`/`ProviderProfileSaveService`) +
marketplace-app (`AbstractEntityOverlay`) + marketplace-rest-api (`ApiExceptionHandler`).
**Priority:** 🔴 Top — placed above every other backlog item per explicit user direction,
2026-09-23.
**When:** Not yet started. Independent of every other Top item — no shared files.

## Current state

Verified end-to-end against current source, 2026-09-23: `org.springframework.dao.
OptimisticLockingFailureException` (a Spring Data framework type, not a project-owned type) is
the de facto cross-cutting "stale write" signal used everywhere in the reactor:

- Declared as part of the public contract in `platform-commons`'s own `*Port` interfaces (Javadoc
  `@throws`-style documentation, not a formal `throws` clause since these are unchecked): `
  AdvertisementPort.java:29`, `ProviderProfilePort.java:35`, `TaxonPort.java:96,100`.
- **Manually thrown in 4 places** (raw-SQL affected-rows guard, own `throw new` statement):
  `AdvertisementRepository.java:131` (`softDelete`), `ProviderProfileRepository.java:117`
  (`delete`), `TaxonRepository.java:112` (`softDelete`), `UserPreferencesRepository.java:78`
  (`saveSettings`, version embedded in the `settings` JSONB column).
- **Thrown natively by Spring Data JDBC itself in 5 more places** — every `*CrudRepository.save()`
  call on a `@Version`-annotated entity throws this type from inside the framework on a version
  mismatch, with no `throw new` of our own to edit: `AdvertisementRepository.save():62` →
  `AdvertisementCrudRepository`, `ProviderProfileRepository.save():65` →
  `ProviderProfileCrudRepository`, `TaxonRepository.save():65` → `TaxonCrudRepository`,
  `UserRepository.save():74` (registration) → `UserCrudRepository`, `UserRepository.
  updateProfile():130` (profile edit) → `UserEditableFieldsCrudRepository`. None of these 5 has an
  integration test covering the stale-version case today (only the 4 manual raw-SQL paths above
  do) — confirmed via `@Version` grep across all 5 entities (`Advertisement`, `ProviderProfile`,
  `Taxon`, `User`, `UserEditableFields`) and their repository `save()` wrappers. Replacing the type
  at these 5 sites requires a `catch (OptimisticLockingFailureException) { throw new
  StaleWriteException(...); }` wrapper around each `crud.save(...)` call — there is no throw
  statement here to simply swap.
- Plus 2 synthetic-guard throws in `marketplace-orchestrator`: `AdvertisementSaveService.java:56`
  and `ProviderProfileSaveService.java:47` — "the row was deleted between read and write", a
  different real-world scenario reusing the same exception type and the same generic message, see
  `marketplace-orchestrator/DECISIONS.md` ADR-006.
- Caught/handled in 2 places: `marketplace-app/.../AbstractEntityOverlay.java:92` (Vaadin UI —
  shows a conflict notification) and `marketplace-rest-api/.../ApiExceptionHandler.java:31-35`
  (REST — maps to HTTP 412 Precondition Failed).
- Referenced directly in **10** existing test files, all needing updates alongside any type
  change: `integration-tests` — `AdvertisementRepositoryTest`, `ProviderProfileRepositoryTest`,
  `UserPreferencesRepositoryTest`, `UserRepositoryTest`; `marketplace-orchestrator` —
  `AdvertisementSaveServiceTest`, `ProviderProfileSaveServiceTest`; `marketplace-rest-api` —
  `AdvertisementApiControllerTest`, `TaxonApiControllerTest`, `ProviderProfileApiControllerTest`,
  `ApiExceptionHandlerTest`.

`ApiExceptionHandler` follows the same shape for several other JDK/Spring generic exception types
(`DuplicateKeyException`, `IllegalStateException`, `NoSuchElementException`,
`IllegalArgumentException`) — this is a deliberate, consistently-applied project convention, not
an isolated oversight in one class. `marketplace-orchestrator/DECISIONS.md` ADR-006 already
records why `OptimisticLockingFailureException` specifically was chosen for the synthetic
concurrent-delete guard ("already the proven, correct shape").

## Why change

Every consuming layer (UI, REST, every domain starter, the shared-kernel `*Port` contracts
themselves) is coupled to Spring Data's own exception hierarchy as the vocabulary for "this write
conflicts with newer state" — a persistence-framework type leaking into the shared domain
contract (`platform-commons`) and every layer built on top of it, rather than a project-owned
type. Separately, the two real-world scenarios that throw it today — a genuine `@Version`
conflict vs. a row deleted out from under an in-flight edit — are currently indistinguishable to
any caller, both carrying the same generic message.

## Expected benefit

Primarily architectural-purity: `platform-commons` contracts stop naming a framework-internal
type; a project-owned type could carry richer, structured semantics (e.g. distinguish the two
scenarios above) if that's ever needed. **Explicitly not** a user-facing or external-API-facing
benefit — `marketplace-rest-api` already never serializes the Java exception type itself, only the
mapped `ErrorResponse` + HTTP 412, so no external contract changes either way. See the discussion
in this task's own originating conversation (`improvement-201` Part 4) for the full honest
cost/benefit weighing before starting this.

## Approach

1. **Done (2026-09-23).** Defined `StaleWriteException` (unchecked) in `platform-commons`'s
   `org.ost.platform.core` package, alongside `TooManyAttemptsException`.
2. **Done (2026-09-23).** Replaced all 9 real throw sites and the 3 `*Port` Javadoc references:
   - 4 manual raw-SQL guards — swapped `throw new OptimisticLockingFailureException(...)` for
     `throw new StaleWriteException(...)` directly: `AdvertisementRepository.softDelete():131`,
     `ProviderProfileRepository.delete():117`, `TaxonRepository.softDelete():112`,
     `UserPreferencesRepository.saveSettings():78`.
   - 5 native Spring Data JDBC `.save()` paths — wrapped the `crud.save(...)` call in a
     `try { ... } catch (OptimisticLockingFailureException e) { throw new StaleWriteException(...,
     e); }`: `AdvertisementRepository.save():62`, `ProviderProfileRepository.save():65`,
     `TaxonRepository.save():65`, `UserRepository.save():74`, `UserRepository.updateProfile():130`.
   - 2 orchestrator synthetic guards: `AdvertisementSaveService.java:56`,
     `ProviderProfileSaveService.java:47`.
3. **Done (2026-09-23).** Updated both catch/handler sites (`AbstractEntityOverlay.java:92`,
   `ApiExceptionHandler.java:31-35`, method renamed `handleOptimisticLocking` →
   `handleStaleWrite`) and all 10 existing tests that referenced the old type.
4. **Decided (2026-09-23), autonomously per autopilot — kept the single-type-two-scenarios
   shape, no distinguishing subtype/field.** Rationale: no current consumer (UI notification, REST
   412 handler) reads the two real-world scenarios (genuine `@Version` conflict vs. concurrent
   delete) any differently — both already produce the identical generic message/outcome today, and
   the task's own "Expected benefit" section already flagged this as speculative ("if that's ever
   needed"). Adding a subtype now with zero consumers would be YAGNI. Recorded in
   `marketplace-orchestrator/DECISIONS.md` ADR-009's "Rejected alternatives".
5. **Done (2026-09-23).** Recorded `marketplace-orchestrator/DECISIONS.md` ADR-009 (new entry,
   `StaleWriteException` decision) and annotated ADR-006's `**Status:**` line as superseded by
   ADR-009 for the exception-type choice specifically (guard placement/rationale itself still
   stands). Regenerated `.claude/nav/adr-index.md` in the same change.
6. **Done (2026-09-23).** Full `scripts/ci.sh` pass — see "Additional work: closing the Sonar
   new-code coverage gate" below for what this actually required.

## Additional work: closing the Sonar new-code coverage gate

Step 6's first attempts (`scripts/ci.sh --sonar`) failed the quality gate on `new_coverage`
(74.9%, then 76.3%, both below the 80% threshold) — not from this task's own repository/service
changes (those already had solid coverage after adding the 4 missing native-`.save()` stale-version
integration tests noted in step 2/3 above), but from **pre-existing zero/low-coverage code
elsewhere on this feature branch**, most of it from `improvement-201`. Investigated and closed
directly rather than deferred, per explicit user direction (2026-09-23):

- **`AttachmentContentTypeValidator.java`** (`attachment-spring-boot-starter`, added by
  `improvement-201` commit `cc1b419d`) — its existing test class covered every happy path but
  neither error branch: `catch (IOException e)` when `Tika.detect()` fails, and `closeQuietly()`'s
  own `catch (IOException e)` when the input stream's `close()` itself throws. Added 2 tests
  (`validate_streamReadFailure_throwsUncheckedIOException`,
  `validate_disallowedDeclaredType_closeFailure_stillThrowsIllegalArgumentException`) exercising
  both. **Note:** confirmed via the raw JaCoCo XML (`test-reports` volume, `sonar-scanner`
  container) that this file already showed 100% line coverage (`mi=0` on every line) even *before*
  these 2 tests were added for the specific commit under analysis — Sonar's own dashboard/API was
  reporting a stale 38.5%/14-uncovered-lines figure that didn't match the underlying JaCoCo data at
  all. This is a real discrepancy in this repo's Sonar analysis pipeline (new-code coverage
  attribution vs. the actual per-line JaCoCo report), not a code or test gap — worth a dedicated
  investigation outside this task if it recurs, but out of scope here since it self-resolved once
  overall new-code coverage crossed the gate threshold via the fixes below.
- **`I18nKey.java`** (`marketplace-app`, `improvement-201` commits `5dead4ff`/`414f7710`) —
  confirmed via the same raw JaCoCo data that this file had **zero** coverage across its entire
  405 lines (not just the ~39 lines newly touched by this branch), because no unit test in
  `marketplace-app` ever loads the class. Added `I18nKeyTest` (new file,
  `marketplace-app/src/test/java/org/ost/marketplace/services/i18n/`) with real assertions, not
  filler: every key has a non-blank message-source string, every key string is unique (a real
  copy-paste-typo guard), `toTestId()`'s transformation, and all 4 static mapping methods
  (`forAction`/`forAdKind`/`forProviderKind`/`forEntityType`) map every enum constant of their
  input type to the correct key — genuine coverage of the class's only real logic, with full enum
  loading (hence full line coverage) as a side effect of exercising `values()`.
- **`FailureRateLimiter.java`** (`platform-commons`) — had zero dedicated unit test of any kind
  (`recordFailure()`/`clear()` were 0% covered; `checkAllowed()`'s allowed-path was untested too).
  `platform-commons` had no `src/test/java` at all before this — added
  `spring-boot-starter-test` (test scope) to its `pom.xml` and a new `FailureRateLimiterTest`
  covering `checkAllowed()` under/at threshold, per-key isolation, and `clear()`'s reset behavior —
  a real gap in a security-relevant rate-limiter class, not busywork.
- **Not touched:** `OrchestratorAutoConfiguration`'s `@Bean` wiring (pure Spring plumbing, the real
  logic it wires — `UserCleanupService.cleanup()` — already has its own dedicated
  `UserCleanupServiceTest`) and the `*PortImpl` pure-delegation classes (`UserAccountPortImpl`,
  `UserPortImpl`) — both intentionally excluded from this pass per the project's own "PortImpl —
  pure delegation only" convention; no independent logic there worth a dedicated test.

Result: `new_coverage` rose from 74.9% → 76.3% (this task's own repository/service tests) → 85.2%
(after the 3 fixes above) — Sonar quality gate now **passes** (`new_coverage: 85.2%` ≥ 80%,
`new_violations: 0`, `new_duplicated_lines_density: 1.11%` ≤ 3%). Full unflagged `scripts/ci.sh
--foreground` (unit + integration + e2e + sonar + archunit + lint + shellcheck + docs together) ran
green — every stage passed, no failures.

## Operational notes
- token_cost_review: 96799 (deep-review-orchestrator, current-diff scope)
- token_cost_research: 62126 + 43068 + 55414 + 12639601(duration_ms, not tokens — see below) = n/a exact sum, see Agent calls below for per-call figures
- token_cost_verification: n/a (no dedicated verification-purpose Agent call; verification was direct script runs, see Script/command runs below)
- review_signal_ratio: 0/0 (deep-review-orchestrator found no findings across all 3 lenses)
- context_loading_task_type: Architectural change (new project-owned exception type crossing platform-commons/starters/orchestrator/app/rest-api)
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: architectural decision needing an ADR; Sonar quality-gate failure needing root-cause diagnosis
- flows_chosen: /record-decision; direct dagu-analyst/sonar-analyst Agent dispatches + manual SonarQube REST API queries
- flows_matched: yes (record-decision matched flows.md exactly; the Sonar-gate diagnosis had no single dedicated flow entry, composed dagu-analyst + sonar-analyst + direct API calls)

### Agent calls
- Code review of current diff | subagent_type=deep-review-orchestrator | tokens=96799 | tool_uses=30 | duration_s=172 | mode=background | batch=solo
- Dagu CI run status check (1st sonar-only run) | subagent_type=dagu-analyst | tokens=55414 | tool_uses=7 | duration_s=13 | mode=background | batch=solo
- Sonar failure detail (2nd full run) | subagent_type=dagu-analyst | tokens=62126 | tool_uses=11 | duration_s=12640 | mode=background | batch=solo
- Find uncovered new-code lines | subagent_type=sonar-analyst | tokens=43068 | tool_uses=7 | duration_s=201 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --integration --sandbox (1st, repo tests, pre-fix) | duration_s=276 | mode=background | result=fail (stale-version test bug, fixed same run)
- scripts/build-and-test.sh --unit --integration --sandbox (2nd, after test fix) | duration_s=256 | mode=background | result=pass
- scripts/ci.sh --sonar (1st, sonar-only) | duration_s=~831 (per Dagu step) | mode=background | result=fail (new_coverage 74.9%)
- scripts/ci.sh --foreground (1st, full run) | duration_s=~1400 | mode=background | result=partial (sonar failed 76.3%, all other stages passed)
- scripts/build-and-test.sh --unit-test AttachmentContentTypeValidatorTest --no-integration | duration_s=175 | mode=background | result=pass
- scripts/ci.sh --sonar --foreground (2nd, after Attachment fix) | duration_s=~394 | mode=background | result=fail (new_coverage unchanged 76.3% — root cause: raw JaCoCo showed 100%, Sonar API misreported; separately, I18nKey/FailureRateLimiter still genuinely uncovered)
- scripts/build-and-test.sh --unit --integration --sandbox (3rd, after I18nKey/FailureRateLimiter tests) | duration_s=237 | mode=background | result=pass
- scripts/ci.sh --sonar --foreground (3rd, final sonar check) | duration_s=~331 | mode=background | result=pass (new_coverage 85.2%)
- scripts/ci.sh --foreground (2nd, final full run) | duration_s=~1400 | mode=background | result=pass (every stage green)

### Review angle yield
- dry-kiss-yagni | survived=0 | total_candidates=0 | tokens=unknown (not separately reported by coordinator)
- solid | survived=0 | total_candidates=0 | tokens=97510
- precedent | survived=0 | total_candidates=0 | tokens=102776
