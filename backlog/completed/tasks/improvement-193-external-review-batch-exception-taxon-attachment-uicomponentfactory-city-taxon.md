# improvement-193: External review batch — ApiExceptionHandler / TaxonService / AttachmentService / UiComponentFactory / City-Taxon dedup

**Type:** improvement — bug-risk fix + type-safety + SOLID/DRY cleanup batch (5 independent items)
**Module:** marketplace-rest-api (`api/error/ApiExceptionHandler.java`), taxon-spring-boot-starter
  (`services/TaxonService.java`), attachment-spring-boot-starter (`services/AttachmentService.java`),
  marketplace-app (`ui/core/UiComponentFactory.java`,
  `ui/views/main/tabs/referencedata/overlay/modes/{City,Taxon}FormOverlayModeHandler.java`)
**Priority:** medium-high
**When:** independent, no blockers — item 6 builds on `improvement-072`/ADR-058's already-decided
  bound, not a re-opening of it

## Current state

An external SOLID/DRY/best-practices review submitted 7 findings; each verified against current
`main` before filing anything. 2 of 7 not carried into this batch: finding 1 (README "no UI yet"
for Provider Profile) is a **false positive** — no such claim exists anywhere in the current
`README.md` Module Layout table. Finding 2 (no CI pipeline) is already tracked as `improvement-028`,
deliberately deprioritized to ⚪ low with its own recorded reasoning (`scripts/ci.sh` already
provides the full pipeline locally; what's left is GitHub-Actions-specific migration, not new
coverage) — this batch doesn't relitigate that call. The remaining 5, renumbered 3-7 to match the
source review's own numbering:

3. `ApiExceptionHandler.handleIllegalState()` (`.java:42-46`) maps `IllegalStateException` → 429
   generically, correct only because the login rate-limiter is currently the sole source of that
   exception type in `org.ost.restapi.api` — any future unrelated `IllegalStateException` in that
   package would get the same wrong status code.
4. `TaxonService.update()` (`.java:73`) manually sets `.updatedAt(Instant.now())` even though
   `Taxon.java:30` already declares `@LastModifiedDate` for the same field.
5. `AttachmentService` has 15 public methods (confirmed by direct count) spanning upload /
   temp-session / video / lifecycle concerns. The source review's claim that `AttachmentPort`
   "already" reflects a clean 3-way grouping is overstated — it doesn't, on inspection. This
   project's own lighter, already-established "Service Class Section Headers" convention
   (`CLAUDE.md`) hasn't even been applied to this file yet — only one `// ── internals ──` marker
   exists today.
6. `UiComponentFactory<T>.build(P params)` still has an unchecked cast — **not for the reason the
   source review assumed**. `improvement-072`/`marketplace-app/DECISIONS.md` ADR-058 already
   bounded the class to `T extends Configurable<T, ?>` (2026-07-24), migrating the 10
   non-`Configurable` consumers to plain `ComponentFactory<T>` first — confirmed via direct read of
   the current file. The gap that's actually still open is narrower: `P` (the method's own type
   parameter) is not linked to the class's `Configurable<T, ?>` bound's second argument, so
   `build()` still cannot avoid casting to `Configurable<T, P>`. ~24 files currently declare
   `UiComponentFactory<Xxx>` (single type argument) and would need a second argument
   (`UiComponentFactory<Xxx, Xxx.Parameters>`) to close this the rest of the way.
7. `CityFormOverlayModeHandler` (283 lines) and `TaxonFormOverlayModeHandler` (282 lines) are
   near-exact structural duplicates — identical method names in the same order, identical
   `private record LocaleField`. `improvement-080` (completed) collapsed
   `TaxonFormOverlayModeHandler`'s own internal 4-field EN/UK repetition but never touched the
   cross-file City↔Taxon duplication — confirmed still present, unchanged, today.

## Why change

Items 3/4 are real, if narrow, correctness/consistency risks — worth closing before they cause a
confusing production symptom (wrong HTTP status; a redundant field write masking a real auditing
question). Items 5/6/7 are real structural debt with concrete evidence (method counts, line-by-line
duplication, an already-partially-fixed type-safety gap) rather than subjective taste.

## Expected benefit

- Item 3: a future unrelated `IllegalStateException` in `org.ost.restapi.api` gets its own correct
  status instead of silently inheriting 429.
- Item 4: one less place manually duplicating what Spring Data JDBC auditing already guarantees.
- Item 5: `AttachmentService` becomes scannable by concern instead of one 15-method flat list.
- Item 6: closes the specific unchecked-cast gap `improvement-072` deliberately left open at the
  time, now that the harder question (the `T`-bound) is already settled.
- Item 7: one canonical locale-form implementation instead of two ~283-line near-copies drifting
  independently on every future taxon/city edit.

## Approach

**Execution order (ascending risk/complexity):** 3 → 4 → 5 → 6 → 7. Items 3/4 are small,
self-contained fixes; item 5 is a comment-only reorganization (no behavior change) with a
class-split escalation only if needed; item 6 is a mechanical but wide migration (~24 files); item
7 is the most delicate (binder-validation wiring `improvement-080` already flagged as risky) and
goes last, verified with a dedicated Playwright pass. `/autopilot` step 3 (`/review`) runs once
after all 5 land, not per-item — the items are independent enough that per-item review would be
five small passes for no real benefit over one pass across the whole diff. `/autopilot` step 4's
two-pass verification (Sonar first, then the full `scripts/ci.sh` run) runs once at the end too.

**Item 3 — done (2026-09-16).** `TooManyAttemptsException extends RuntimeException` added in
`platform-commons` (`org.ost.platform.core`) rather than either single module, since it's thrown
by two different rate limiters in two different modules. **Autonomous scope decision (found while
implementing, not part of the original approved plan):** the "single source" premise was checked
against real code and turned out to be inaccurate in two ways — (a) there are actually **two**
rate limiters, `AuthService.login()` (marketplace-app) and `UserService.register()`
(user-spring-boot-starter), not one; (b) only the registration one is ever reachable through the
REST API (`UserApiController.register()`) — login never is, since the REST API authenticates via
API keys, not username/password. Updated both to throw `TooManyAttemptsException` for consistency,
plus their UI catch sites (`LoginDialog.java`, `SignUpDialog.java`). `ApiExceptionHandler` now maps
`TooManyAttemptsException` → 429 and the remaining generic `IllegalStateException` → 500 (a real
fallback case, not hypothetical: `PATCH /api/me/settings` → `UserPreferencesRepository`'s "No
user_preferences row" `IllegalStateException` was confirmed, by tracing the actual call chain, to
reach `ApiExceptionHandler` today and get the wrong 429 — now correctly 500). Checked
`ProviderProfileSaveService`'s own unrelated `IllegalStateException` catch (a different meaning,
already translated to `AccessDeniedException`/403 before reaching the REST layer) — confirmed it
doesn't interact with this change.

**Item 4 — done, found a different real bug than either the source review or this task's first two
attempts assumed (2026-09-16).** Went through three rounds before landing on the actual root cause
— worth recording honestly rather than smoothing over, since each round was a real, falsifiable
claim checked against the database, not a guess:
1. Removed the manual `.updatedAt(Instant.now())`, assuming auditing alone would populate it (the
   source review's premise). **Failed** — `updated.getUpdatedAt()` was `null`.
2. Concluded auditing doesn't refresh `updatedAt` on update for this Lombok `@Value` entity, reverted
   to a manual set with a comment saying so. **Also wrong** — a follow-up assertion comparing the
   manually-set value against what was actually in the database after save() failed too, on a
   values *mismatch* (not a null), which shouldn't happen if the manual value were simply being
   persisted as-is.
3. That mismatch was the real clue: isolated the question with a direct `taxonRepository.save()`
   call on an existing entity, no `TaxonService` involved at all, and printed both timestamps.
   **Auditing does refresh `updatedAt` correctly on update** — the actual bug was that
   `TaxonService.update()` calls `taxonRepository.save(updated)` and discards its return value,
   then returns the stale pre-save `updated` object instead of what `save()` actually persisted
   (which carries the auditing-refreshed `updatedAt`, and technically the incremented `@Version`
   too). Every caller of `TaxonService.update()` was receiving a `Taxon` whose `updatedAt` reflected
   the moment the builder ran, not the moment it was actually written to the database.

Final fix: `Taxon saved = taxonRepository.save(updated); ... return saved;` — no manual
`updatedAt` set at all, letting auditing do its job, and the method now returns what was actually
persisted. Test (`TaxonServiceTest.update_returnsAuditingRefreshedUpdatedAt_notTheStalePreSaveValue`)
verifies this against the real database, not just the in-memory return value. Net result: this
closes a real, independent correctness bug (stale returned `updatedAt`/`version` from every
`TaxonService.update()` call) that the original review's finding never named but that surfaced
directly from taking its "confirm, don't assume" instruction seriously.

**Item 5 — done (2026-09-16).** Physically regrouped `AttachmentService`'s 15 public methods (not
just labeled the existing scattered order) into 5 blocks with `// ── ... ──` headers: Query
(`getByEntityId`/`getMediaSummaries`/`getByEntityAndUrls`), Permanent attachments (`upload`/
`delete`), Video (`addVideoTemp`/`addVideo`), Temp upload session (`uploadTemp`/`commitTempUploads`/
`commitTempUploadsQuiet`/`discardTempUploads`/`captureSnapshot`), Lifecycle/restore
(`restoreToUrls`/`softDeleteAll`), plus the existing `internals` block unchanged. Pure reordering,
no logic changed. **Judgment call:** the grouped class reads as scannable now — did not escalate to
the source review's 3-class-split suggestion.

**Item 6 — done (2026-09-16).** `UiComponentFactory<T extends Configurable<T, P>, P>` — `build()`
now `return get().configure(params);`, zero casts, zero `@SuppressWarnings`. All 54 declaration
sites across 23 files updated mechanically (a script, not 54 manual edits — verified each
substitution against each class's real `implements Configurable<T, P>` signature first, not
guessed): 18 simple `Xxx` → `Xxx, Xxx.Parameters` classes, plus the generic
`OverlayFormBinder<X>` → `OverlayFormBinder<X>, OverlayFormBinder.Parameters<X>` case (6 sites) —
`OverlayFormBinder<T>` has its own generic `Parameters<T>`, not a plain `.Parameters`, confirmed by
reading its `implements Configurable<OverlayFormBinder<T>, OverlayFormBinder.Parameters<T>>`
declaration directly rather than assuming the simple-class pattern applied uniformly. No
`improvement-072`-shaped compile surprise — confirmed by an actual `--unit` build, not just "should
compile."

**`/autopilot` step 3 self-review (2026-09-16), partial due to a session-wide rate limit.** Only
`precedent-reviewer` finished before the `deep-review-orchestrator` dispatch and its
`dry-kiss-yagni-reviewer`/`solid-reviewer` siblings hit `HTTP 429` (session limit, reset ~22:20
UTC) — `precedent-reviewer`'s own 2 findings were still independently verified (a fresh verifier
subagent, not self-concluded) before being applied, per the standing "never self-conclude" rule.
Both CONFIRMED and fixed:
1. Item 6's `UiComponentFactory<T, P>` change silently diverged from `marketplace-app/DECISIONS.md`
   ADR-058 (which recorded the single-parameter `<T extends Configurable<T, ?>>` bound as final,
   with its own "the `Configurable<T,P>`-vs-caller's-`P` cast is unavoidable" claim now
   incorrect) — no annotation/supersession existed. Fixed via `/record-decision`: ADR-082 records
   the two-parameter decision, ADR-058's `Status:` now points to it (item 1 only; items 2-3
   unaffected). `.claude/nav/adr-index.md` regenerated in the same change.
2. `.claude/rules/marketplace-app.md`'s own documented `UiComponentFactory<T>` pattern was stale
   (still showed the single-parameter form). Fixed to show `UiComponentFactory<T extends
   Configurable<T, P>, P>` with a `P`-matching-`Parameters`-type example.

`dry-kiss-yagni-reviewer`/`solid-reviewer` lenses never ran in the first attempt — genuinely not
covered, not silently skipped. **Re-run once the rate limit cleared (2026-09-16): all three lenses
clean, zero findings.** `dry-kiss-yagni-reviewer` considered and deliberately did not flag one
minor stylistic tradeoff in `LocaleTranslationForm` (`Field<T>` re-declaring accessor fields already
in `Accessors<T>`); `solid-reviewer` confirmed the `UiComponentFactory<T,P>` change removes a
DIP/type-safety wart rather than introducing one; `precedent-reviewer` confirmed ADR-082/ADR-058's
supersession text and the regenerated index are internally consistent, and grepped all of
`marketplace-app` to confirm no leftover single-parameter `UiComponentFactory<X>` usage remains
anywhere.

**Real process gap the review caught, not a code finding:** two new files
(`LocaleTranslationForm.java`, `TooManyAttemptsException.java`) were untracked, not staged, despite
root `CLAUDE.md`'s "`git add` runs automatically after every file change" — the auto-add apparently
doesn't reliably cover brand-new files, only modifications to already-tracked ones (a pattern
worth watching for in future tasks). `git add`ed both directly once found; step 3 is now genuinely
complete with zero open findings.

**`/autopilot` step 4a (Sonar-only) found one real flaky test, unrelated to Sonar itself
(2026-09-16).** `bash scripts/ci.sh --sonar` failed — not a SonarQube finding (the scan itself
passed cleanly, quality gate green) but because that stage also runs the full unit+integration
suite as a prerequisite, and `TaxonServiceTest.update_returnsAuditingRefreshedUpdatedAt_notTheStalePreSaveValue`
(added earlier today for Item 4) failed on a **1-microsecond** mismatch between two independent
reads of the same persisted timestamp (`...524Z` expected vs `...525Z` actual) — real driver/DB
round-trip jitter at the microsecond boundary, not a logic bug. Root-caused via the `dagu-analyst`
agent pulling the real step log rather than guessing from "sonar failed." Fixed by asserting
`isCloseTo(..., within(1, MILLIS))` instead of exact equality after truncation — truncation alone
wasn't tight enough against genuine sub-microsecond rounding. Re-ran the single test class directly
(`integration-tests/run.sh --sandbox TaxonServiceTest`) to confirm the fix before re-triggering the
full pipeline: `BUILD SUCCESS`.

**Item 7 — done (2026-09-16).** New `LocaleTranslationForm<T>` (composition, not inheritance) in
the same package as both handlers, holding the EN/UK `Field` list and providing
`wireValueChangeListeners`/`bindValidation`/`copyLocaleFields`/`restoreFromSnapshot`/
`buildFieldsCard` — everything that was byte-for-byte identical between
`TaxonFormOverlayModeHandler` and `CityFormOverlayModeHandler` except DTO type, i18n key set, and
`TaxonType`. Each handler now holds one `LocaleTranslationForm<XxxEditDto>` field, builds it with
its own resolved i18n strings + method-reference accessors in `activate()`, and delegates
`save()`'s TaxonType-specific call, `buildDto()`, history-button/audit wiring, and `Parameters`
shape unchanged, in the handler itself — only the genuinely shared mechanics moved. Preserved one
existing quirk exactly rather than "fixing" it out of scope: both EN and UK fields reuse the same
test-id (the original code already called `.toTestId()` once and used the result for both), and
City's locale-label/content `Div`s still use the `"taxon-locale-*"` CSS class names (not
`"city-locale-*"`) — both pre-existing, unrelated to this dedup.

**Three real compile-time bugs found and fixed via an actual `--unit` build, not caught by review
or assumed correct — same discipline as `improvement-192`'s hook hardening:**
1. `record Field(...)`/`record Accessors(...)` nested directly inside generic `LocaleTranslationForm<T>`
   referenced the outer class's `T` — nested records are implicitly `static`, so they cannot see an
   enclosing instance's type parameter. Fixed by giving each record its own `<T>`, the same shape
   `OverlayFormBinder.Parameters<T>` already uses elsewhere in this codebase.
2. `bindValidation(OverlayFormBinder<T> binder)` failed with "type argument T is not within bounds"
   — `OverlayFormBinder<T extends EditDto>` requires the bound, but `LocaleTranslationForm<T>` had
   none. Fixed by adding the same `T extends EditDto` bound.
3. `new LocaleTranslationForm.Accessors(...)` (no diamond) is a raw-type constructor call, not
   inference — made explicit (`Accessors<TaxonEditDto>`/`Accessors<CityEditDto>`) in both handlers
   rather than relying on inference across a multi-argument outer constructor call.

Full unit suite green after all three fixes: `BUILD SUCCESS`, 71/71 `marketplace-app` tests
(including `ArchitectureRulesTest` — confirms `TooManyAttemptsException`'s `platform-commons`
placement and the `UiComponentFactory<T, P>` change cross no ArchUnit boundary), 92/92
`marketplace-rest-api` tests, 116/116 `marketplace-orchestrator` tests.

**Full `bash scripts/ci.sh` verification run (2026-09-16) — 3 real, independently root-caused
findings, all resolved or filed:**

1. **Host-level OOM mid-run (environmental, not this task's bug).** The host ran low on memory
   (~319Mi free) and killed the host-side `scripts/ci.sh --foreground` watcher process partway
   through — build/unit/integration/archunit had already succeeded by then. Confirmed via
   `dagu-analyst` that the Dagu run itself (inside the persistent `ci-runner` container) survived
   independently of the killed host process. Reattached a lightweight, detached poller
   (`python3 -u scripts/ci/dagu-rest-run-monitor.py`, stdlib-only, safe on memory) directly against
   the existing run id instead of re-triggering `scripts/ci.sh` (which would have started a
   duplicate run) — recovered full visibility with no wasted work.

2. **Sonar quality gate — real, in-scope finding, fixed.** New-code coverage 79.8% vs. required
   ≥80% (0.2pp short); 0 new violations, duplication fine. Root-caused via SonarQube's own REST API
   (`component_tree` new_coverage/new_uncovered_lines, filtered to nonzero) — the AttachmentService
   reorder (item 5) had physically relocated 3 methods (`getByEntityAndUrls`, `discardTempUploads`,
   `captureSnapshot`) that turned out to have **zero real test coverage** anywhere in the repo
   (confirmed via grep — no test file referenced any of the three), a real, pre-existing gap the
   reorder's git-blame-based "new code" accounting surfaced. Added 3 unit tests to the existing
   Mockito-based `AttachmentServiceTest` (11/11 passing). Re-ran `bash scripts/ci.sh --sonar`:
   quality gate now **OK**, new_coverage **87.7%** (confirmed via
   `/api/qualitygates/project_status`) — closed the gap with margin to spare, no unrelated files
   needed touching.

3. **E2E — 4 Playwright failures (63 run, 31 passed, 4 failed, 28 skipped serial-cascade).**
   Root-caused via `dagu-analyst` pulling the real step log + `ci-marketplace-app` logs (all clean,
   no server-side exceptions). All 4 were UI-element wait/click timeouts, not assertion failures —
   correlated with real host swap pressure (1.6Gi still in use) from the same OOM event above.
   3 of the 4 (`auth.flow.js`'s `.header-settings-button` wait, `category.flow.js`'s
   `selectCategoryInAdForm` via `advertisement.flow.js`, `timeline.flow.js`'s grid-scroll wait)
   already use documented best-practice wait strategies (no `waitForTimeout`, generous timeouts,
   proper Vaadin-state waits) — genuine environmental flakiness under memory pressure, not a code
   bug. The 4th (`04-provider-profile-flow.spec.js`) was a **real, separate bug**: its own local
   `selectCategory()` helper reinvented the multi-select-combo-box interaction with a plain
   `page.locator('vaadin-multi-select-combo-box-item').filter({hasText}).click()` — no wait for the
   combo-box overlay to open, no shadow-DOM traversal, no virtual-scroller handling — duplicating,
   in a much weaker form, logic `category.flow.js`'s `selectCategoryInAdForm` already solved for
   the advertisement overlay. Fixed by generalizing that function: extracted the shared body into
   `selectInMultiSelectComboBox(page, comboBox, itemLabel)` (takes the combo-box locator directly
   instead of deriving it from a hardcoded `data-testid`), `selectCategoryInAdForm` now a thin
   wrapper over it, and `04-provider-profile-flow.spec.js`'s local `selectCategory()` now delegates
   to the same shared, robust function instead of its own fragile reimplementation. Zero production
   code touched — test infra only. Verified via a full `bash playwright/run.sh e2e --ux` re-run
   against a freshly reset DB (`bash scripts/deploy-and-run.sh --reset-only-db` first, per
   `.claude/rules/playwright.md`): **63/63 passed**, including all 4 previously-failing tests —
   confirms both the real fix (provider-profile category selection) and the environmental theory
   for the other 3 (they passed cleanly once host memory pressure was gone, with no code change).

4. **Docs stage — pre-existing, unrelated bug, filed and fixed.**
   `docs/architecture/scripts/generate-architecture-model.sh` still hardcoded the renamed
   `backlog/issues/`/`backlog/completed/issues/` paths (now `backlog/tasks/`/`backlog/completed/tasks/`),
   confirmed via `git log` to be untouched by this task's own diff and to predate this task
   entirely — broke the `docs` stage of **any** `bash scripts/ci.sh` run with `docs=true`, not
   specific to this batch. Filed and immediately fixed as
   [improvement-194](../completed/tasks/improvement-194-architecture-model-generator-stale-backlog-issues-path.md)
   (now closed) — all 6 hardcoded references corrected, verified by running the generator directly
   (exits 0, regenerated `architecture-model.json`'s backlog counts match a real `ls` exactly).

## Related

- [improvement-028](improvement-028-minimal-ci-pipeline.md) — CI pipeline, not relitigated by this
  batch (source review's finding 2).
- `improvement-072` (`completed/tasks/`) + `marketplace-app/DECISIONS.md` ADR-058 —
  `UiComponentFactory<T>`'s `Configurable<T, ?>` bound, already decided; item 6 extends it, does not
  reopen it.
- `improvement-071`/`improvement-080` (`completed/tasks/`) — prior `TaxonFormOverlayModeHandler`
  cleanups; item 7 is the cross-file duplication those two didn't cover.
- `platform-commons/src/main/java/org/ost/platform/attachment/spi/AttachmentPort.java` — checked
  directly for item 5's grouping claim.

## Operational notes
- token_cost_review: n/a — exact per-run token counts from the earlier `/review` (deep-review-orchestrator) pass were not preserved across a context compaction; 2 CONFIRMED findings from that pass were independently re-verified and applied (see Item 3's notes above); a later re-run against the settled diff found zero findings
- token_cost_research: 364057 (sum of 5 dagu-analyst/sonar-analyst Agent-tool dispatches during this task's final CI-verification phase: 55282 + 73741 + 80634 + 91033 + 63367)
- token_cost_verification: n/a — verification ran via real scripts (build-and-test.sh, ci.sh, deploy-and-run.sh, playwright/run.sh), not Agent-tool calls; see Script/command runs below
- review_signal_ratio: n/a — the earlier `/review` pass's exact total-candidates count was not preserved across context compaction; known outcome: 2/2 surviving candidates from the one completed finder angle (precedent-reviewer) were applied, the other two finder angles were rate-limited (HTTP 429) on that attempt, and a later full re-run found 0 findings
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: multi-item backlog task, execute-then-verify-then-fix-findings
- flows_chosen: direct script/Agent-tool execution per `.claude/rules.md`'s "Scripts"/Task Lifecycle sections (no `/autopilot`-external skill needed for the verification tail)
- flows_matched: yes

### Agent calls
- Check real status of in-flight Dagu CI run after host OOM | subagent_type=dagu-analyst | tokens=55282 | tool_uses=8 | duration_s=82 | mode=background | batch=solo
- Root-cause failed Sonar step | subagent_type=dagu-analyst | tokens=73741 | tool_uses=23 | duration_s=279 | mode=background | batch=solo
- Root-cause failed E2E step | subagent_type=dagu-analyst | tokens=80634 | tool_uses=8 | duration_s=107 | mode=background | batch=solo
- Root-cause failed docs step | subagent_type=dagu-analyst | tokens=91033 | tool_uses=25 | duration_s=325 | mode=background | batch=solo
- Find which new lines lack Sonar coverage | subagent_type=sonar-analyst | tokens=63367 | tool_uses=22 | duration_s=202 | mode=background | batch=solo

### Script/command runs
- bash scripts/ci.sh --foreground (full run: unit+integration+e2e+sonar+archunit+docs) | duration_s=n/a (host OOM killed the watcher mid-run; underlying Dagu run reattached and reached partially_succeeded) | mode=background | result=fail (sonar/e2e/docs failed, root-caused separately)
- bash scripts/build-and-test.sh --no-unit --integration-test AttachmentServiceTest | duration_s=128 | mode=background | result=pass (11/11)
- bash scripts/ci.sh --sonar --foreground (re-check after coverage fix) | duration_s=1231 | mode=background | result=pass (quality gate OK, new_coverage 87.7%)
- bash scripts/deploy-and-run.sh --reset-only-db | duration_s=n/a (~2.5 min, per Build+Health-check steps) | mode=background | result=pass
- bash playwright/run.sh e2e --ux | duration_s=557 | mode=background | result=pass (63/63)
- bash docs/architecture/scripts/generate-architecture-model.sh --with-sonar --with-archunit (direct verification of the improvement-194 path fix) | duration_s=n/a (>120s, exact not captured) | mode=background | result=pass (exit 0, correct backlog counts)

### Review angle yield
- precedent-reviewer | survived=2 | total_candidates=2 (the only finder angle that completed before rate-limiting; both findings applied) | tokens=n/a (not preserved across context compaction)
- dry-kiss-yagni-reviewer | survived=n/a | total_candidates=n/a | tokens=n/a (rate-limited, did not complete on the first attempt)
- solid-reviewer | survived=n/a | total_candidates=n/a | tokens=n/a (rate-limited, did not complete on the first attempt)
