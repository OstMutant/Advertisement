# improvement-208: Feedback module follow-ups — role-check tests, atomic moderation+audit, typed exceptions, moderation-queue N+1

**Type:** improvement
**Module:** `feedback-spring-boot-starter`, `marketplace-orchestrator` (`org.ost.orchestrator.services.FeedbackAccessService`), `marketplace-app` (`ui.views.main.tabs.moderation.ModerationView`), `platform-commons` (`org.ost.platform.core`)
**Priority:** high
**When:** independent, no blockers — user-requested Top-of-Top placement, 2026-10-15 (sequenced ahead of all other Top tasks)

## Current state

Four independent findings in the feedback area, each verified directly against current code:

1. **No negative tests for moderation role checks.** `FeedbackAccessService` calls
   `authorizationService.requireIsPrivileged(actorId)` as the first line of exactly six methods:
   `approveFeedback`, `approveComment`, `confirmFeedbackViolation`, `confirmCommentViolation`,
   `rejectFeedback`, `rejectComment`. `flagFeedback`/`flagComment` are intentionally open to any
   logged-in user — not guarded, by design. `FeedbackAccessServiceTest`
   (`integration-tests`, `@SpringBootTest`) only has happy-path tests via `newModerator(...)`; it
   contains no `assertThrows`/`AccessDeniedException` anywhere. Deleting any of the six guards
   today would leave the suite green.

2. **Moderation write and audit capture are not atomic.** `AdvertisementSaveService` and
   `ProviderProfileSaveService` run the entity write and the audit capture inside one
   `TransactionTemplate` block, so a failed audit rolls the write back.
   `FeedbackAccessService` does the opposite: `feedbackPortFactory.get().approveFeedback(id)` (and
   the equivalent for all 8 moderation actions — flag/approve/confirm/reject × feedback/comment)
   followed by a separate `auditPortFactory.ifAvailable(p -> p.captureUpdate(...))`, with no
   transaction boundary. No `TransactionTemplate`/`@Transactional` exists anywhere in
   `FeedbackAccessService` today. If the audit call fails after the port write committed, the
   moderation change persists with no history row.

3. **16 `IllegalStateException` in `FeedbackService`** — far more than any other starter (0-1
   each): 11 are `orElseThrow(...)` not-found lookups, 2 are "... after save" re-read invariants,
   and 3 are user-facing business rules ("Cannot flag your own feedback entry", "Cannot flag your
   own comment", "Edit window has closed"). No typed not-found/business-rule exception exists in
   the repo for this starter. Precedents: `TooManyAttemptsException` (`platform-commons`,
   `org.ost.platform.core`) and `AccessDeniedException` (`marketplace-orchestrator`, already used
   next to these same calls in `FeedbackCommentWriteService`).
   `ApiExceptionHandler.handleIllegalState` maps `IllegalStateException` to a 500 ("genuine
   unexpected-state bug"), with a test asserting it stays distinct from `TooManyAttemptsException`.

4. **Moderation queue has an N+1 and a silent cap.** `FeedbackAccessService.findModerationQueue`
   rebuilds the whole queue on every call: 4 queries (`hiddenFeedback`, `hiddenComments`,
   `confirmedFeedback`, `confirmedComments`), each capped by `MAX_QUEUE_FETCH = 1000`, merged,
   filtered, sorted and paged in memory. For every comment, `toQueueItem` calls
   `findFeedbackById(c.feedbackId())` solely to read `entityId` — `FeedbackPort` has no bulk
   lookup. `ModerationView.refresh()` calls this on every page change, tab select, moderation
   action, sort and filter — roughly 4 + 1 + N queries each time. `totalCount` is computed after
   the cap, so it silently undercounts beyond 1000 per category. Nothing in the backlog currently
   tracks this.

## Why change

(1) and (2) are correctness/data-integrity risks: an authorization regression or a transient audit
failure would currently go undetected and could leave moderation state and its audit history out
of sync, with no test anywhere that would catch it. (3) makes the feedback starter inconsistent
with the rest of the codebase's error-handling convention and forces callers into string-matching
to distinguish real business rules from a generic 500. (4) is both a performance and a correctness
problem — the undercount in `totalCount` means moderators cannot trust the number shown once a
category exceeds 1000 items.

## Expected benefit

- A role-check regression (guard accidentally removed/weakened) fails CI immediately instead of
  shipping silently.
- A transient audit failure no longer leaves a moderation action without its corresponding history
  row — write and audit succeed or fail together.
- Feedback business-rule violations become distinguishable from genuine bugs by type, letting the
  UI show a precise, localized message instead of relying on exception message text.
- Moderation queue reads go from 4 + 1 + N queries to a small constant number, and the displayed
  count stops silently lying once a category exceeds the fetch cap.

## Approach

Four independent phases. **Process for every phase:** present the technical plan → wait for
explicit approval → implement **that phase only** → run its targeted test(s) → run the full
`bash scripts/build-and-test.sh --unit --integration --sandbox` gate → report back with the
actually-changed files, the actual commands run, and the actual test results (not just
"BUILD SUCCESS") → wait for an explicit instruction to commit or to move to the next phase. No
automatic continuation from one phase into the next within the same approval.

### Phase 1 — negative tests for the moderation role checks

**Done (2026-10-15).** Added `guardedModerationMethod_actorNotPrivileged_throwsAccessDeniedException_stateUnchanged`
(`@ParameterizedTest` over the 6 guarded methods) and `flagFeedback_byPlainUser_succeeds` to
`FeedbackAccessServiceTest.java` (+62 lines, that file only). Targeted run: 15/15 green. Mutation
check: temporarily removed the `approveFeedback` guard — exactly the `APPROVE_FEEDBACK`
parameterized case failed, all 14 others stayed green; guard restored and confirmed via `git diff`
(zero net change to `FeedbackAccessService.java`); re-run green (15/15). Not yet committed — full
`--unit --integration --sandbox` gate not yet run (per this task's own process: one phase per
approval cycle).

- One `@ParameterizedTest` over the six guarded methods (repo convention: no `if`/`for` in a test
  body). Actor has `Role.USER` (the `UserTestFixtures` overload taking a `Role`). Assert
  `AccessDeniedException` **and** that the moderation state is unchanged — re-read it through the
  port, don't just trust the exception.
- One positive test: a plain `USER` can `flagFeedback` on another user's feedback.
- Name tests `method_condition_expectedResult`.
- Prove the tests bite: remove one guard **at a time**, confirm exactly that case fails, then
  **restore it unconditionally** — even if the test doesn't fail as expected, the working tree must
  end the check in its original state, never left dirty.

### Phase 2 — make moderation write + audit atomic

**Done (2026-10-15).** Added `TransactionTemplate tx` field to `FeedbackAccessService`, wrapped
all 8 moderation methods' bodies in `tx.executeWithoutResult(...)` (guards stay before the
transaction opens). Confirmed `FeedbackPortImpl`'s write methods and `DefaultAuditPort.capture*`
are plain `@Transactional` (default REQUIRED propagation) so they join the outer transaction.
Added `@MockitoBean AuditPort` + 2 rollback tests to `FeedbackAccessServiceTest.java` (the test's
`@SpringBootTest` context had no `AuditPort` bean at all before this — the audit branch was a
silent no-op in every prior test). Both new tests were run **first against the unfixed code** and
failed as expected (`approveFeedback` test: status read back as `NEW` instead of `HIDDEN`;
`rejectFeedback` test: row found deleted instead of still present) — proving the bug was real.
After the fix: targeted run 17/17 green. One cleanup needed after the agent's first pass: a
duplicate `import TransactionTemplate` line (harmless but sloppy) was found via `git diff` review
and removed; re-ran the targeted test afterward to confirm still 17/17 green. Not yet committed —
full `--unit --integration --sandbox` gate not yet run.

- Inject `TransactionTemplate` the same way `AdvertisementSaveService` does, and wrap the port call
  plus the audit capture of each of the 8 moderation methods in `tx.executeWithoutResult`. Keep
  `requireIsPrivileged` **before** the transaction opens (fail fast, no transaction for a denied
  actor).
- Confirm by reading the actual annotations (don't assume) that `FeedbackPortImpl`'s write methods
  and `DefaultAuditPort.capture*` use default (`REQUIRED`) propagation and therefore join the outer
  transaction.
- Read how `reject*` (permanent removal) is audited today before wrapping it, and mirror
  `AdvertisementSaveService`'s delete path (snapshot, delete, and `captureDeletion` inside the same
  block).
- **Rollback test must prove the real thing, not just that `TransactionTemplate` was called:**
  perform an actual moderation operation with an `AuditPort` double that throws, let it propagate,
  then **re-read the moderation state from the database** (not from an in-memory return value) and
  assert it is unchanged — i.e. a genuine rollback, not merely "no audit row written". Cover the
  `reject*` physical-delete path with the same discipline (the row must still exist afterward).
- Heads-up: adding a constructor dependency to `FeedbackAccessService` has already broken
  `FeedbackAccessServiceTest`'s `@SpringBootTest` context once before (a missing bean class) —
  expect to update that test's context configuration.

### Phase 3 — typed exceptions in `FeedbackService`

**Done (2026-10-15).** Scoped to the 3 business-rule throws only (confirmed via `/review`-style
investigation that the 11 not-found cases already degrade gracefully today via `ModerationView`'s
existing generic catch — typing them would have been typing without a behavior change, so skipped
per YAGNI). Added `SelfActionNotAllowedException`/`EditWindowClosedException` to `platform-commons`
(`org.ost.platform.core`, mirroring `TooManyAttemptsException`'s shape). Changed exactly the 3
throw-sites in `FeedbackService.java`; the other 13 `IllegalStateException` throws untouched.
Renamed + retyped the 5 matching assertions in `FeedbackServiceTest`. Added 2 i18n keys (enum +
both locale files). Investigated how these exceptions reach the UI today: "cannot flag your own
X" is already unreachable through the rendered UI (report button is disabled/hidden for one's own
content in both panels) so no dedicated UI message was added for it — confirmed via real code read,
not assumed; "edit window closed" **is** genuinely reachable (client-side `isEditable()` check at
render time vs. server check at submit time can diverge across a long-open form) and got real
try/catch + a dedicated i18n message at all 3 affected call sites. Also discovered and fixed, as
part of this work, that 5 UI call sites across `FeedbackPanel`/`FeedbackReplyPanel` had **no
exception handling at all** before this change (would crash into Vaadin's default unhandled-
exception behavior) — now all 5 have at least a generic catch-all with a localized error message.
No REST mappings added (feedback isn't exposed via REST yet, per plan). No Playwright test added —
48h edit-window boundary isn't practically triggerable through real browser interaction; covered at
the integration-test level instead. Full `--unit --integration --sandbox` gate: 730/730, including
`ArchitectureRulesTest` 20/20.

**Follow-up (`/review` + DRY cleanup, 2026-10-15).** `/review` on this diff raised 2 medium-
confidence findings (neither auto-reported): (1) the 5 UI call sites' try/catch shape was
duplicated — fixed by extracting `FeedbackErrorHandlingUtil.runCatchingFeedbackErrors(...)`
(mirrors `ProviderProfileDeleteUtil`'s shape, lives next to its 2 consumers in
`ui/views/components/`), reducing all 5 call sites to one-line wrapper calls and removing the
now-dead `EditWindowClosedException`/`FEEDBACK_NOTIFICATION_*` imports from both panel files. (2) a
proposal to add a shared marker type across `TooManyAttemptsException`/`SelfActionNotAllowedException`/
`EditWindowClosedException` for generic "any business-rule exception" handling — **not done**:
verified the review's own premise was factually wrong (all three already extend `RuntimeException`
directly, not `IllegalStateException` as claimed), and confirmed zero current call sites would
actually use such a marker (every one already falls back to `catch(Exception)`, which already
covers this) — adding it now would be typing with no behavior change, same YAGNI reasoning as
skipping the not-found types above. Full `--unit --integration --sandbox` gate re-run after the
DRY fix: 730/730. Not yet committed.

Design first, present for approval before implementing:

- Decide names and placement in `platform-commons` (`org.ost.platform.core`, next to
  `TooManyAttemptsException`): one generic not-found type (entity kind + id) plus one business-rule
  type, or per-case types — grep first to confirm nothing equivalent already exists.
- **Keep the 2 "after save" invariants as `IllegalStateException`** — that is genuinely unexpected
  state, exactly what the existing 500 mapping is for.
- Trace every caller (`FeedbackPanel`, `FeedbackReplyPanel`, `ModerationView`,
  `FeedbackCommentWriteService`, `FeedbackAccessService`) to see how these exceptions reach the
  user today. **Acceptance criterion: the UI must distinguish the new exception types without
  parsing the exception message text** — each business rule ("edit window closed", "cannot flag
  your own feedback/comment") needs its own dedicated i18n key reachable from its own typed
  exception, not a shared generic error path.
- Do **not** add speculative `ApiExceptionHandler` REST mappings (feedback isn't exposed via
  `marketplace-rest-api` today) — record in the relevant `DECISIONS.md` which mapping (404 / 409 or
  422) to use once it is exposed.
- Update `FeedbackServiceTest` assertions that currently expect `IllegalStateException` for the 3
  business-rule cases. Keep the existing message constants.

### Phase 4 — moderation queue N+1 and silent cap

**Superseded (2026-10-15) — scope expanded from a patch to a real fix, by explicit user decision
after weighing the trade-off.** The originally-planned patch (bulk `findFeedbackByIds` + a visible
`truncated` flag) would have kept the underlying in-memory architecture and its `MAX_QUEUE_FETCH =
1000` cap. Investigation found that architecture is the one place in the codebase that does NOT
follow this repo's standard SQL-level filter/sort/page pattern (`SqlFilterBuilder`/`OrderByBuilder`
+ real `LIMIT`/`OFFSET`, as every other catalog view — e.g. `AdvertisementRepository.findByFilter`
— already does); moderation queue instead fetches up to 1000 rows per of 4 categories and does
filter/sort/page in Java. No ADR records this as a deliberate choice — only a code comment
("queue size is bounded by real reports, not catalog scale"), which the very existence of the cap
already contradicts. Decision: move moderation queue onto the same SQL-level pattern as everything
else, eliminating the cap (and therefore the need for any `truncated` flag) and the N+1 in one
pass, rather than patching around a design that isn't actually used anywhere else in this app.

**Why this doesn't violate starter independence (resolved concern, verified against real code):**
the one apparent blocker — `authorName` filter/sort needs data from `user_information`, which
`feedback-spring-boot-starter`'s own `pom.xml` enforcer rule explicitly bans depending on
(`user-spring-boot-starter` is in its `bannedDependencies`) — already has a live precedent solving
the exact same shape of problem: `AdvertisementService` (`advertisement-spring-boot-starter`) holds
a `ComponentFactory<TaxonPort>` field and calls `TaxonPort.resolveCategoryAndCityFilter(...)` to
resolve a category/city filter into an entity-id `Set<Long>` *before* building its own SQL —
depending on the **Port interface** (lives in `platform-commons`, always a legal dependency) while
its Maven enforcer rule bans only the **sibling starter module** (the implementation). The same
mechanism applies here: `FeedbackService` gets a `ComponentFactory<UserPort>` field, and
`UserFilterDto` already has a `name` field with existing SQL-level `ILIKE` support in
`UserPort.getFiltered(...)` — no new method needs to be added to `UserPort` at all.

**Design (filter resolution vs. display enrichment — the same split `AdvertisementDisplayEnrichmentService`
already uses elsewhere, applied here as an inline step rather than a new class since it's one
field):**
- **Filter resolution** (authorName *search text* → `Set<Long>` of matching user ids, used in the
  `WHERE` clause) happens *inside* `feedback-spring-boot-starter`'s `FeedbackService`, mirroring
  `AdvertisementService.resolveTaxonFilter` exactly: `Optional<Set<Long>>` where empty-`Optional`
  means "no filter requested" (unrestricted) and a present-but-possibly-empty `Set` means "filter
  requested, these are the matches" (empty set → short-circuit to an empty page without hitting
  feedback's own SQL at all, same as `AdvertisementService.getFiltered` already does for taxon
  filters).
- **Display enrichement** (`authorId` → human-readable `authorName`, for rendering) stays in
  `marketplace-orchestrator`'s `FeedbackAccessService`, exactly where it already lives today via
  `resolveAuthorNames`/`UserActorNameService` — *not* moved into the starter, per the existing rule
  that query-time filter resolution belongs in the owning starter but display composition belongs
  in the orchestrator. The one real improvement: this now resolves names for only the current
  **page's** `authorId`s (e.g. 20-50), not for up to 4000 pre-fetched rows as today.

**Concrete plan, by module:**

1. **`platform-commons`** — relocate 3 types currently misplaced in `marketplace-orchestrator.dto`
   (an SPI-shaped concept that belongs with its owning domain, not the BFF layer) into their
   correct packages, no field/behavior change in this step:
   - `ModerationQueueFilterDto` → `org.ost.platform.feedback.dto.ModerationQueueFilterDto`
   - `ModerationQueueItemDto` → `org.ost.platform.feedback.dto.ModerationQueueItemDto` (add
     `@Builder(toBuilder = true)` — needed so the orchestrator can rebuild each page item with its
     resolved `authorName` without losing immutability)
   - `ModerationQueueItemType` → `org.ost.platform.feedback.model.ModerationQueueItemType`
   - Update every consumer's import (8 files found via grep: `FeedbackAccessServiceTest`,
     `ModerationQueueFilterMapper`, `ModerationView`, `ModerationQueueFilterMeta`,
     `ModerationQueryBlock`, `ModerationQueryConfig`, `ModerationQueueSortMeta`,
     `FeedbackAccessService` — mechanical import-path changes only for all but the last two).
   - `FeedbackPort`: add `List<ModerationQueueItemDto> findModerationQueue(@NonNull
     ModerationQueueFilterDto filter, @NonNull Sort sort, int page, int size);` and `int
     countModerationQueue(@NonNull ModerationQueueFilterDto filter);` — mirrors
     `AdvertisementPort.getFiltered`/`count`'s exact two-method shape. The 4 existing methods
     (`findHiddenFeedback`/`findHiddenComments`/`findConfirmedViolationFeedback`/
     `findConfirmedViolationComments`) stay — they have their own direct callers/tests unrelated to
     the queue (confirmed: `FeedbackAccessServiceTest.moderationQueue_flagApproveConfirmReject_fullLifecycle`
     asserts against them directly).

2. **`feedback-spring-boot-starter`**:
   - `FeedbackRepository`: one `UNION ALL` SQL (feedback-shaped rows + comment-shaped rows,
     comment's `entity_id` resolved via a real `JOIN feedback` *in this query* — this is the actual
     N+1 fix, not a bulk-fetch patch) wrapped as a CTE, with `SqlFilterBuilder`-built `WHERE`
     (status, type, author-id-set, reason `ILIKE`, date range), `OrderByBuilder`-built `ORDER BY`
     for the `createdAt`/`type` sort options, and real `LIMIT`/`OFFSET` — plus a parallel
     `COUNT(*)` query over the same CTE without the limit/offset. A small row-mapper record (type
     discriminator, id, entityId, contentText, rating, moderationReason, authorId, createdAt,
     moderationStatus) is the query's own return shape; `FeedbackService` maps it into
     `ModerationQueueItemDto` with `authorName = null` (filled in later, by the orchestrator).
   - **`authorName` is not a sort option at all — matching the established, repo-wide convention.**
     Verified against real code: `AdvertisementInfoDto.createdByUserName` and
     `ProviderProfileDto.actorName` are both cross-domain display fields resolved for rendering,
     and in both cases the matching `*SortMeta` (`AdvertisementSortMeta`, `ProviderProfileSortMeta`)
     simply never exposes them as a sort option — only fields that live in the owning table are
     sortable anywhere in this app. `ModerationQueueSortMeta.AUTHOR_NAME` is the one place this
     convention was broken; removing it (not working around it with a fetch-all-then-sort fallback)
     is the actually-symmetric fix. Only `createdAt`/`type` remain as sort options, both fully
     SQL-level. The `authorName` *filter* (`ModerationQueueFilterMeta.AUTHOR_NAME`) is unaffected —
     filtering by a resolved id-set has a real precedent (`TaxonPort`) and stays.
     - `ModerationQueueSortMeta.java`: delete the `AUTHOR_NAME` constant.
     - `ModerationQueryBlock.java`: change the "Author row" to the same filter-only shape already
       used by the "Reason row" a few lines below it (`filterRow(label, field, filterMeta)`, no
       sort meta/sort label argument) — delete the `MODERATION_SORT_AUTHOR`-driven sort column.
     - `I18nKey.java` + both locale properties files: delete `MODERATION_SORT_AUTHOR`
       (`moderation.sort.author`) — confirmed via grep its only 2 usages are the two files above,
       safe to delete outright, not just stop referencing.
   - `FeedbackService`: `ComponentFactory<UserPort> userPortFactory` field (first cross-domain
     optional port this starter has ever needed — record this as its own ADR entry, see below);
     `findModerationQueue(filter, sort, page, size)`/`countModerationQueue(filter)` methods
     implementing the author-name-*filter* resolution only (no sort-dependent branch needed, per
     above).
   - `FeedbackPortImpl`: pure delegation for the 2 new methods, same as every other method in this
     class.
   - No `pom.xml` change needed — `UserPort` lives in `platform-commons`, already a dependency of
     every starter; only the *module* `user-spring-boot-starter` is banned, not its Port interface.

3. **`marketplace-orchestrator`** — `FeedbackAccessService`:
   - Remove `MAX_QUEUE_FETCH`, `buildFullModerationQueue`, `matchesFilter`, `resolveComparator`,
     both `toQueueItem` overloads — all of this logic moves into the starter's SQL + Java (step 2).
   - `findModerationQueue(filter, sort, page, size)` becomes: call
     `feedbackPortFactory.findIfAvailable().map(p -> p.findModerationQueue(...))`, collect the
     page's distinct `authorId`s, resolve names via the existing `resolveAuthorNames` (now only
     ever called with a page-sized set, not up to 4000 ids), rebuild each item via
     `.toBuilder().authorName(...).build()`, then call `countModerationQueue(filter)` for the real
     total. `ModerationQueuePage` record is unchanged (`items`, `totalCount`) — no `truncated`
     field needed since there is no longer a cap to signal.

4. **`marketplace-app`** — mechanical import-path updates only in the 6 files listed in step 1; no
   behavior change (`ModerationView`'s own rendering/pagination wiring is unaffected since
   `ModerationQueuePage`'s shape doesn't change).

5. **Tests:**
   - `FeedbackRepositoryTest`/`FeedbackServiceTest` (level1) — new tests for the unified SQL query:
     type/status/author-id/reason/date filters each isolated, `createdAt`/`type` sort via SQL, and
     the real N+1 fix (a comment attached to a *different* feedback entry than another comment in
     the same page must show the correct, distinct `entityId` for each — this is the functional
     proof the JOIN works, not a query-count assertion, since no query-counting utility exists in
     this repo and one should not be introduced just for this per the project's own "no new test
     dependency" guidance).
   - `FeedbackAccessServiceTest` (level2) — update existing `moderationQueue_*` tests for the new
     call shape; confirm `authorName` *filter* still works end-to-end through the real `UserPort`
     (this test's `@SpringBootTest` context already includes `UserAutoConfiguration`).
   - No large-row-count truncation test needed anymore (nothing to truncate).

6. **Record as its own ADR** (`/record-decision`, `feedback-spring-boot-starter` and/or
   `marketplace-orchestrator`): (a) moderation queue moving from in-memory merge/filter/sort/page
   to SQL-level, matching the repo-wide pattern; (b) `feedback-spring-boot-starter` gaining its
   first-ever optional cross-domain `ComponentFactory<UserPort>` dependency, and why it doesn't
   violate starter independence (Port-interface-via-`ComponentFactory` vs. Maven-module-ban are
   different things — same mechanism `advertisement-spring-boot-starter` already uses for
   `TaxonPort`).

**Progress (2026-10-15).** Checkpoints A (DTO relocation to `platform-commons`, `AUTHOR_NAME` sort
removed), B (SQL implementation: `FeedbackPort`/`FeedbackRepository`/`FeedbackService`/
`FeedbackPortImpl`), and C (`FeedbackAccessService` simplified to a thin SQL-backed delegate) are
done and verified. All 17 `FeedbackAccessServiceTest` tests pass unchanged against the new SQL
implementation (confirmed genuine, not a false positive: `moderationQueue_authorAndReasonAndDateFilters`'s
author-name-filter assertion exercises the real `UserPort`-backed resolution end to end against
Postgres). **Real regression found and fixed after Checkpoint B**, missed by that checkpoint's own
verification (which only ran `FeedbackAccessServiceTest`, already had `UserAutoConfiguration` in its
context): `FeedbackServiceTest` and `FeedbackRepositoryTest` (level1) don't include
`UserAutoConfiguration`, and `RepositoryTestSupport` has no empty `ComponentFactory<UserPort>`
fallback (by its own documented convention — a newly-needed optional port gets its fallback added
locally by the consuming test, not in the shared class) — so `FeedbackService`'s new
`ComponentFactory<UserPort>` constructor dependency broke both tests' Spring context entirely
(`UnsatisfiedDependencyException`). Fixed initially by adding a local `@TestConfiguration` with an empty `ComponentFactory<UserPort>`
bean to both test files — **later superseded** (see below) by a cleaner fix once a second,
systemic problem surfaced.

**Checkpoint D done**: 7 new tests added directly to `FeedbackRepositoryTest`, exercising
`findModerationQueuePage`/`countModerationQueue` at the SQL level — proving the actual N+1 fix (two
comments on two different feedback entries resolve distinct, correct `entityId`s via the real SQL
`JOIN`, not just "looks equivalent" at a higher layer), default-status filtering, reason `ILIKE`,
date range, `authorIds` filtering, `type` sort ordering, and count-independent-of-page-limit.
`FeedbackRepositoryTest` 15 → 22 tests, all green.

**Second regression found and fixed during the full-gate pass**: running the full `--unit
--integration --sandbox` gate (not just targeted test classes) failed twice, deterministically, at
the same ~4m14s mark, same test class (`ProviderProfileRepositoryTest`, unrelated to feedback) with
`FATAL: sorry, too many clients already` (Postgres connection exhaustion) — not random flakiness.
Root cause: the local-`TestConfiguration`-per-test fix above created 2 *textually identical but
distinct* nested classes (`FeedbackRepositoryTest.TestConfig`, `FeedbackServiceTest.TestConfig`),
and Spring Test's context cache keys on the literal `Class` objects in `@SpringBootTest(classes=
...)` — so 2 separate Spring contexts (and 2 separate HikariCP pools) were created instead of one,
tipping the full suite's cumulative connection count over Postgres's `max_connections` ceiling.
Fixed properly per this repo's own documented convention ("extract to a shared file once two or
more consumers need the same helper"): moved the empty `ComponentFactory<UserPort>` fallback bean
into `RepositoryTestSupport` itself (mirroring the existing Audit/Attachment/Advertisement/
ProviderProfile fallbacks there), removed both local `TestConfig` classes entirely. This both fixed
the regression (both tests now share one cached context again) and is objectively cleaner code.

**ADR recorded**: `marketplace-orchestrator/DECISIONS.md` ADR-011 ("Moderation queue implemented
with SQL-level filter, sort, and pagination"), `.claude/nav/adr-index.md` regenerated.

**`/review` run on the full diff before commit** — 3 `CONFIRMED` findings, all fixed:
1. `ModerationQueueFilterDto`'s Javadoc pointed to the now-deleted `FeedbackAccessService.matchesFilter()` — repointed to `FeedbackRepository.buildModerationQueueWhere()`.
2. `FeedbackRepository`'s SQL sort fields never mapped `authorName` — `OrderByBuilder` silently fell back to `createdAt DESC` instead of erroring, a real regression from the old Java `Comparator`-based sort. Fixed by removing the one remaining test (`sortedByAuthor`) that still exercised this already-removed-everywhere-else option, rather than resurrecting it — consistent with ADR-011's own decision that `authorName` isn't a supported sort.
3. `FeedbackService.resolveAuthorIdsFilter` capped author-name-filter resolution at a hardcoded `MAX_AUTHOR_NAME_MATCHES=1000` single page — the exact "silent cap" defect class this whole phase was created to eliminate, just at a different layer. Fixed: now pages through *all* matches (`resolveAllMatchingAuthorIds`, `AUTHOR_SEARCH_PAGE_SIZE=500` per page, loops until a short page is returned) — no cap at all.

**Final state — all verified, fully committed-ready:**
- `FeedbackRepositoryTest` 22/22, `FeedbackServiceTest` 34/34, `FeedbackAccessServiceTest` 17/17 (one
  stale assertion removed, not a count change), `FeedbackCommentWriteServiceTest` 7/7.
- Full `--unit --integration --sandbox` gate re-run after all 3 review fixes: **737/737**, 0 failures.
- Nothing outstanding. A separate follow-up task (`improvement-209`) was offered by the reviewing
  agent for these same 3 findings but declined — fixed directly in this phase instead of deferred.

## Related

- Pattern precedent for atomic write+audit: `AdvertisementSaveService`, `ProviderProfileSaveService`
  (`marketplace-orchestrator`).
- Pattern precedent for typed exceptions: `TooManyAttemptsException` (`platform-commons`),
  `AccessDeniedException` (`marketplace-orchestrator`, already used in `FeedbackCommentWriteService`
  next to the same `orElseThrow` calls).
- No existing ADR overlaps this scope (checked `.claude/nav/adr-index.md`) — the one feedback-related
  entry found concerns `EntityRef` reuse/self-containment, not these four findings.
