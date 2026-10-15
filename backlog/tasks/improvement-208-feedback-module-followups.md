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

Present both options below and the trade-off before implementing — final choice to be confirmed:

- **(A)** Return `entityId` on `FeedbackCommentDto` via a `JOIN feedback` in the comment queries —
  zero extra queries, but changes a `platform-commons` DTO (an SPI contract): every constructor,
  mapper and test using it must be updated.
- **(B)** Add a bulk `findFeedbackByIds(Set<Long>)` to `FeedbackPort` using `= ANY(:ids)` (this
  repo's existing convention for id lists) — one extra query regardless of N, no SPI contract
  change. *Recommended starting point given the smaller blast radius*, final choice still to be
  confirmed in the phase's own plan.
  - If (B): verify empty-`Set`, duplicate-id, and `ANY(:ids)` behavior in the actual repository
    implementation before relying on it — no new test dependency introduced just for this.
- **Make truncation visible to the moderator, not just to a developer reading the code:** add an
  explicit `truncated` signal on the moderation queue page result that reaches `ModerationView`'s
  status bar — a comment next to `MAX_QUEUE_FETCH` stating the cap is deliberate is not sufficient
  on its own, because it doesn't tell the user looking at the screen that the count is incomplete.
  - Off-by-one: fetching exactly `MAX_QUEUE_FETCH` per category can't distinguish "exactly at the
    cap" from "more exist beyond it" — fetch `MAX_QUEUE_FETCH + 1` per category and use the extra
    row purely as the truncation signal (never render it).
  - Note explicitly that this still doesn't make `totalCount` numerically accurate past the cap —
    only that it stops silently lying by flagging incompleteness. Moving filtering/sorting/paging
    into the database to fix `totalCount` itself is a larger change — out of scope here, flag as a
    possible future task if/when the queue is observed to actually exceed the cap in practice.
- Test: comments across several feedback entries appear in the queue with `entityId` populated,
  and paging/`totalCount`/`truncated` are correct at and around the cap boundary.

## Related

- Pattern precedent for atomic write+audit: `AdvertisementSaveService`, `ProviderProfileSaveService`
  (`marketplace-orchestrator`).
- Pattern precedent for typed exceptions: `TooManyAttemptsException` (`platform-commons`),
  `AccessDeniedException` (`marketplace-orchestrator`, already used in `FeedbackCommentWriteService`
  next to the same `orElseThrow` calls).
- No existing ADR overlaps this scope (checked `.claude/nav/adr-index.md`) — the one feedback-related
  entry found concerns `EntityRef` reuse/self-containment, not these four findings.
