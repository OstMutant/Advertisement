# improvement-203: out-of-scope implementation findings (running collection)

**Type:** improvement — meta/process issue, ongoing collection bucket, not a single fix.
**Module:** cross-cutting — whichever module each entry below actually touches.
**Priority:** 🟡 Top — kept close to active work so entries get triaged quickly, not left to rot.
**When:** ongoing; triage entries opportunistically during active implementation work.

## Purpose

A running catch-all for things noticed *during implementation* of some other task that are real
but outside that task's own approved scope — a pre-existing issue in an unrelated file, a question
needing its own separate approval (e.g. a SonarQube false-positive server-side transition), a small
inconsistency spotted while working on something else entirely. Distinct from `improvement-133`
(review findings too large to fit the current batch, needing a design decision before sizing) —
this bucket is for findings that are small/clear but simply belong to a different piece of work,
kept at Top priority so they don't sit forgotten the way a 🔵 tech-debt bucket would.

Each entry below is independent; when one is picked up, evaluate it fresh and resolve it directly.
Remove the entry once resolved — same lifecycle as any other finding, just deferred.

## Entries

### 1. `HeaderBar.java:39` — `onSettingsClosed` field flagged by SonarQube `java:S1450`, likely a false positive (found during improvement-200 Phase 1, 2026-09-30)

SonarQube's quality gate flagged `private Runnable onSettingsClosed` as a field that could be a
local variable. Analysis suggests this is a false positive: `MainView.java:123` calls
`headerBar.setOnSettingsClosed(...)` from outside, *after* Spring has already constructed the
`@UIScope` `HeaderBar` bean and its `@PostConstruct init()` has already built the settings button
(whose click listener reads `onSettingsClosed` lazily, at click time). The field bridges a real
time gap between "button built" and "real callback supplied via setter" — Spring can't inject a
`Runnable` via constructor from another view, so the setter-injection pattern is deliberate, not
an oversight a local variable could replace.

Not resolved inline: confirming and fixing a SonarQube false positive requires marking it in two
places (the SonarQube server itself via a `do_transition` call, plus a `@SuppressWarnings` in
code) and the server-side transition needs its own separate, explicit approval per
`.claude/rules.md`'s "Never mutate SonarQube's own server-side state without a separate, explicit
approval for that specific call" — general approval to "fix Sonar findings" already covered the
other 3 issues found in the same scan (all applied directly), but not this one.

**Also found, same rule, same reason not resolved inline (2026-10-01):** `FeedbackPanel.java:57-58`
— `ratingField`/`textField` flagged by the same `java:S1450` rule, same likely-false-positive
shape. `buildForm()` creates these as live Vaadin components (`StarRatingField`/`UiTextArea`)
already attached to the DOM; `buildEntryRow()`'s Edit-button click handler reaches into the same
instances (`ratingField.setValue(entry.rating())`, `textField.setValue(entry.feedbackText())`) to
pre-fill the on-screen form for editing an existing entry, not a fresh one. A local variable
scoped to `buildForm()` would be unreachable from `buildEntryRow()`, so the Edit button couldn't
update what's actually rendered — the field promotion is deliberate, not an oversight.

### 3. Database ERD silently drops `<addColumn>` migrations (found during an architecture-map audit of `feedback`/`contact-spring-boot-starter`, 2026-10-01)

`contact-spring-boot-starter/src/main/resources/db/contact-changelog/changes/02-contact-view-revealed-value.xml` adds `contact_view.revealed_value` via `<addColumn>`. This column is completely absent from both the generated JSON and the rendered ERD diagram, for two compounding reasons: `DB_ERD_CHANGELOG_FILES`'s discovery filter in `docs/architecture/scripts/generate-architecture-model.sh` (lines 228-237) only includes files matching `<createTable` — a file containing only `<addColumn>` never enters the file list passed to the parser at all; and even if it were passed, `docs/architecture/scripts/liquibase-schema-to-json.js` has no `<addColumn>` handling whatsoever (only `createTable`, `addForeignKeyConstraint`, `createIndex`, `addPrimaryKey`, and a narrow raw-`<sql>` regex pass are implemented). Currently the only `<addColumn>` changelog in the repo, so the gap is isolated to one column today, but the mechanism is entirely missing — any future `addColumn` migration on any table hits the same gap.

Not resolved inline: found during a read-only architecture-map audit, not an implementation task with its own approved scope to fix generator code in.

### 4. `minio/minio:latest` — MinIO Community Edition is archived upstream, no more security patches (found during a general infra check, 2026-10-01)

`scripts/deploy-and-run/docker-compose.minio.yml:20` and `scripts/deploy-and-run/run.sh:196,214` both pin `minio/minio:latest`. MinIO's Community Edition GitHub repo was archived (locked, read-only) on 2026-04-25 after a maintenance wind-down (license changed Apache 2.0 → AGPLv3 in 2025-05, Admin GUI removed 2025-02, Docker Hub publishing stopped 2025-10) — the vendor is steering users to the paid AIStor product instead; confirmed directly via web search, not just the external claim that prompted this (see [TuxCare](https://tuxcare.com/blog/minio-els/), [itsfoss](https://itsfoss.com/news/minio-moves-away-from-open-source/)). No further upstream security fixes will land on the community codebase ever again. Garage (Deuxfleurs) is the most-commonly-recommended lightweight S3-compatible replacement (actively maintained, v2.3.0 released 2026-04-16, same S3 API so `attachment-spring-boot-starter`'s `StorageService` wouldn't need code changes, just a different endpoint) — but it's AGPLv3-licensed and lacks bucket versioning/lifecycle policies/erasure coding, which should be checked against this project's actual usage before committing to the swap.

Not resolved inline: an infra/storage-backend swap needs its own sizing and isn't something to fold into unrelated implementation work; not urgent (the already-pulled image keeps working, this is a forward-looking risk, not an active break) but shouldn't be forgotten either.

### 5. `deploy-and-run.sh` has no "stop, don't restart" mode — manual cleanup falls back to raw `docker rm -f` (found during a resource-contention incident, 2026-10-01)

`deploy-and-run.sh`'s flags are `--restart-infra` (restarts), `--reset` (wipes DB/MinIO volumes, then relaunches), `--reset-only-db` (truncates, still running) — every one of them ends with the stack running again. No flag just stops the stack and leaves it stopped. DB/MinIO are docker-compose-managed (`scripts/deploy-and-run/docker-compose.db.yml`/`docker-compose.minio.yml`), so `docker compose --project-directory . -f <those files> down` is the correct, convention-matching way to stop them (mirrors the `up -d` command root `CLAUDE.md` already documents) — but the app container itself is started via a raw `docker run` inside `run.sh`, not compose, so it has no script-level stop path at all; neither does anything else started the same way (e.g. `playwright/run.sh`'s own `pw-runner`).

**Concrete scenario this would solve:** today's session ran `scripts/ci.sh` (its own isolated stack) and a local `deploy-and-run.sh`/`playwright.sh` pass (the normal dev stack) at the same time, on a 4-CPU/9.7GB sandbox already low on free memory (swap in active use) — real OOM kills (exit 137) resulted. There was no single command to free the normal dev stack's resources without either (a) leaving it running (do nothing) or (b) `--reset` (destroys DB/MinIO data, heavier than just "I don't need this running right now"). Cleanup ended up as manual, ad-hoc `docker rm -f <container names>` instead of a project script, which is exactly what `.claude/rules.md`'s "always use project scripts" rule exists to prevent. The same gap applies any time CI and local dev work need to run back-to-back rather than simultaneously, or before a resource-heavy Playwright `--full --ux` pass.

**Approach options:** (a) add a `--stop`/`--down` flag to `deploy-and-run.sh` that runs `docker compose ... down` for DB/MinIO and `docker rm -f` for the app container, no relaunch; (b) short of a new flag, at least document `docker compose ... down` as the sanctioned manual pattern for DB/MinIO in `scripts.md` (today it documents `up -d` but says nothing about tearing down), leaving the app container's raw `docker rm -f` as an accepted, explicitly-named exception rather than silent/undocumented.

Not resolved inline: new flag design + testing is tooling work outside any current implementation task's own approved scope.

### 6. `FeedbackPanel`'s feedback-entry list has no pagination (moved from `improvement-200`, 2026-10-05)

`FeedbackPanel.refreshList()` always calls `feedbackAccessService.findForEntity(entityType,
entityId, 0, PAGE_SIZE)` — hardcoded page 0, `PAGE_SIZE = 20`, no way to reach entry 21+. The
Port/Service/repository chain already supports real pagination end-to-end
(`findForEntity(EntityType, Long, int page, int size)`); only the UI side is unpaginated. Wanted:
a pagination control under the feedback-entries list (not the comment trees — those stay
full-tree, no change there), reusing the project's existing `PaginationBar` component (already
used by `AdvertisementsView`/`UserView`/`ProvidersView`/`TimelineView`) rather than building a new
pager.

**Fix plan (revised 2026-10-06 — newest-first order + real per-user page-size setting, like the
existing grid views):**

- Feedback entries must list newest first (confirm/adjust `findForEntity`'s current `ORDER BY` so
  the newest entry is always at the top of page 1 — exact SQL change TBD at implementation time).
- `FeedbackPanel.java`: new constructor-injected field `private final PaginationBar
  paginationBar;` (prototype-scoped bean, same direct-injection pattern the four existing Views
  already use) — **with** a real `SettingsPaginationBinding` registration, matching
  `AdvertisementsView`/`UserView`/`ProvidersView`/`TimelineView`'s own pattern, not the bare
  default page size. Needs a feedback-list page-size field added to `UserSettingsDto`/
  `SettingsSnapshotDto` (exact field name/plumbing TBD at implementation time, mirroring how the
  existing grid-page-size setting is wired end-to-end).
- `configure(Parameters p)`: call `paginationBar.resetToFirstPage()` right after the `!available`
  early-return check, before `refresh(entityRef)` — so reopening this panel for a different entity
  never starts on a stale page left over from a previously-viewed entity. Add `paginationBar` to
  the DOM between `listContainer` and `formContainer`: `add(headerContainer); add(listContainer);
  add(paginationBar); buildFormContainer(entityRef); add(formContainer);`. Also wire
  `paginationBar.setPageChangeListener(_ -> refreshList(entityRef));` here (captures `entityRef` in
  the closure, same as the rest of `configure()`'s listener wiring already does).
- `refreshHeader(EntityRef entityRef)`: after the existing `FeedbackAggregateDto aggregate = ...`
  fetch, add `paginationBar.setTotalCount(aggregate.reviewCount());` — `reviewCount` is already the
  exact total `findForEntity` paginates over, no new count query needed.
- `refreshList(EntityRef entityRef)`: change the hardcoded `0` to `paginationBar.getCurrentPage()`
  in the `findForEntity(...)` call.
- Always-visible bar, no show/hide toggle — matches `AdvertisementsView`'s own convention
  (`PaginationBar.setTotalCount`'s existing button-disable logic already makes prev/next inert on a
  single page, no separate visibility rule needed).
- Playwright (`04-provider-profile-flow.spec.js`): extend an existing feedback-list flow (or add a
  step) that creates more than `PAGE_SIZE` feedback entries on one entity and asserts page
  navigation — if this is expensive to seed per-test, scope it against spec 06's existing bulk-seed
  infrastructure rather than creating 21+ entries one at a time inside this flow.

Not resolved inline: moved here from `improvement-200` at user request, 2026-10-05, once that
task's own phases were otherwise done — a UI-only addition unrelated to the rest of this bucket's
findings, grouped here just to keep `improvement-200` itself closable.

### 7. Feedback scope trim — remove from advertisements, collapse comment-tree to a single owner-reply on provider profiles (research-backed decision, revised 2026-10-06)

Web research (OLX/eBay/Facebook Marketplace/Vinted/Depop, real sources) confirmed this project's
own instinct: reviews belong on the **seller/provider profile**, not on individual **one-off
listings** — the listing-vs-profile split correlates with repeatable-product platforms (Etsy/
Amazon/Airbnb, where the same listing is bought/booked by many people, review-on-item makes sense)
vs one-off-item platforms (OLX/eBay-classifieds/Vinted/Depop/Facebook Marketplace, where an item
sells once and disappears — review lives on the seller, shown as a compact badge/link on the
listing, not a separate review widget). Separately, the existing unbounded-then-capped-at-3-level
comment tree (reactions, tombstoning, moderation queue for comments) has no real-world precedent —
every major review system checked (Google Business, Yelp, TripAdvisor, App Store/Play Store, eBay
feedback) allows the reviewed party exactly **one reply**, never a multi-level public thread.

**Decided (2026-10-06, revised same day after further discussion):**
- **Advertisements lose feedback entirely** — remove `FeedbackPanel` from
  `AdvertisementViewOverlayModeHandler.java` (keep `ContactRevealPanel` — that already *is* the
  "message seller" mechanism these platforms use instead of per-listing reviews). The ~2 existing
  dev-only `feedback` rows with `entity_type='ADVERTISEMENT'` get a proper Liquibase data-cleanup
  changeset (cascading through `feedback_content`/`feedback_rating`/`feedback_comment`/
  `feedback_comment_reaction` in the same FK-safe order `FeedbackService.deleteFeedbackHard()`
  already uses) — no precedent for a pure-data `<delete>` changeset in this repo yet, but leaving
  orphaned `entity_type` rows forever is worse. This is a data-destructive step — gets its own
  explicit confirmation before running, even though the row count is trivial dev data.
- **Feedback entries: unchanged from today** — any registered user leaves one (edit window
  already enforced), only the original author edits it.
- **Report/flag on feedback: kept, and upgraded with a reason.** Today's `flagFeedback` has no
  text input at all — the reporter just clicks, the moderator sees zero context on *why*. New: a
  popup dialog with a description field + Apply/Cancel, built on the **existing**
  `BaseDialog`/`DialogLayout` scaffolding `ConfirmActionDialog` already uses (`addFormContent` for
  a `UiTextArea` in place of the static `Paragraph`, `getFooter()` for the two buttons) — no new
  dialog base class. `flagFeedback(feedbackId, actorId, reason)` gains the `reason` parameter,
  stored on `feedback_content` (see schema note below). This is a real gap-fix, not scope creep —
  every real platform checked (Facebook, YouTube, Google) asks for a reason when reporting.
- **Comment on a feedback entry: corrected model — the reviewed party replies to their own
  review, not platform staff.** Only the **provider-profile owner being reviewed** may leave the
  one comment on a feedback entry about them (not any registered user, not an admin/moderator) —
  this is the real eBay/Google-Business "owner responds" pattern, a closer match to precedent than
  an earlier same-day draft of this entry that had admin/moderator write it. Only that same owner
  may edit (within the existing 48h window) or delete their own comment — **no one else can edit
  or delete it, including admins** (this supersedes an earlier same-day draft's "or any ADMIN"
  edit-override idea and its own open question about bypassing the edit window — both dropped,
  there is no override). Report/flag stays available on the comment too (any other user can report
  it, same reason-popup pattern as feedback). Admin/moderator power over a comment is limited to
  **hide-as-censored only** — no edit, no delete — exactly the same `moderation_status` mechanism
  `feedback` itself already uses, just never an edit/delete override.
- **Schema simplification, not just a UI cap:** `feedback_comment.parent_comment_id` (always NULL
  under the new model) gets dropped entirely, replaced by a `UNIQUE` constraint on `feedback_id`
  (the database itself enforces "at most one comment," no manual check needed). Removes a real
  class of now-dead code: `computeCommentDepth`, `pruneDanglingTombstones`,
  `deleteDescendantsThenSelf`, the `WITH RECURSIVE` query, the entire `feedback_comment_reaction`
  table/UI (reactions only made sense for a public thread, not one owner reply), and likely
  `CommentTreePanel.java` itself — replaced by a much smaller single-reply component. Today's
  `improvement-200` Phase 5 depth-cap work becomes moot under this model and gets removed, not
  kept dormant. New: `feedback_content.moderation_reason` (nullable text) — the report popup's
  description, stored on the same shared content row `moderation_status` already lives on (one
  column serves both feedback and comment reports, no new table needed for the reason text).
- **Moderation view: one grid, not two, with a type discriminator.** A new
  `ModerationQueueItemDto` (enum `type` — `FEEDBACK`/`COMMENT`, content text, `moderation_reason`,
  reporter/author display name) — built by merging `findHiddenFeedback`/`findHiddenComments` at
  the service layer, **not** a new persistence table; the enum is a presentation-level
  discriminator over the two existing read paths. `ModerationView` renders one
  `Grid<ModerationQueueItemDto>` instead of two separate grids, columns: Type, Content, Author,
  Reason, Reported, Action (Approve only, rendered as an icon button — `UiIconButton`, matching
  the project's existing icon-button convention, not a text button). No separate "View" column — the Author cell itself is
  the deep link (via the existing `AppLinkService`, same mechanism the share button already uses)
  to where the content actually lives (the provider-profile/advertisement overlay in context), not
  to the author's own account page. No emoji/decorative icons anywhere in this grid — `Type` is a
  plain text badge, the Author link is styled as a link (not marked with a link glyph), consistent
  with the rest of the app's admin grids.

Real refactor spanning backend (`feedback-spring-boot-starter`, `platform-commons`,
`marketplace-orchestrator`), UI (`marketplace-app`), a Liquibase changelog change, and Playwright/
integration-test surgery — sized like a dedicated task, but user explicitly chose to keep it in
this bucket rather than file a new numbered task, 2026-10-06. File-by-file plan below, written
2026-10-06 against the real current code (verified via direct read, not assumed) — split into
phases, each a self-contained, fully-specified unit suitable for a single `Haiku`-model `Agent`
dispatch. Verify (build + relevant tests) after each phase before starting the next — do not batch
all phases into one autonomous run.

**Phase A — schema (Liquibase, `feedback-spring-boot-starter`) — revised 2026-10-06: no prod DB
exists yet, so no additive/data-cleanup changesets are needed.** Edit
`feedback-spring-boot-starter/src/main/resources/db/feedback-changelog/changes/01-feedback-schema.xml`
directly to its final target shape (dev DB already has the feedback tables dropped and the
`01-feedback-schema` row removed from `databasechangelog`, so the next app start re-applies this
file from scratch):
- Remove the `feedback_comment.parent_comment_id` column from the table definition.
- Remove the `feedback_comment_reaction` table definition entirely.
- Add a `UNIQUE` constraint on `feedback_comment.feedback_id` (DB now enforces "at most one
  comment per feedback" — no manual check needed in service code).
- Add `feedback_content.moderation_reason` — `TEXT`, nullable, `remarks="Moderator-facing reason
  text captured from the report popup when a user flags a feedback entry or its reply; null until
  the first report."`
No second changeset file, no `<delete>` cleanup changeset — the old advertisement-feedback dev
rows are already gone along with the rest of the dropped tables.

**Done (2026-10-06):** `01-feedback-schema.xml` rewritten to this final shape via a Haiku agent
dispatch, verified line-by-line against the plan above.

**Done (2026-10-06), backend+orchestrator (revised phase grouping):** entities, repository,
`platform-commons` feedback contracts, `FeedbackService`, `FeedbackPortImpl`, and
`FeedbackAccessService` (orchestrator) rewritten together via a Haiku agent dispatch, for the
single-reply model (no reactions, no comment tree, `moderationReason` threaded through,
`findCommentByFeedback` replacing the recursive comment-tree query). Verified via a real
`scripts/build-and-test.sh --no-unit --no-integration` run, not just the agent's own report:
`platform-commons`/`feedback-spring-boot-starter`/`marketplace-orchestrator`/`marketplace-rest-api`
all compile clean. `integration-tests` (`FeedbackRepositoryTest`/`FeedbackServiceTest`) and
`marketplace-app` (`CommentTreePanel`/`FeedbackPanel`) are now red, as explicitly expected — they
still reference the removed reaction/comment-tree APIs and get fixed in their own later phases
(UI, then integration tests, then Playwright), not this one.

**Done (2026-10-06), UI + remaining orchestrator pieces:** `ReportReasonDialog`, `FeedbackReplyPanel`
(new), `FeedbackCommentWriteService` (new orchestrator owner-check service), `AuthorizationService.isOwner(Long,Long)`
overload, `AccessEvaluator.isStrictOwner`, `ModerationQueueItemDto`/`ModerationQueueItemType` (new
platform-commons DTO+enum), `FeedbackAccessService.findModerationQueue`, rewritten `FeedbackPanel`
(pagination via `PaginationBar`+`SettingsPaginationBinding`+new `UserSettingsDto.feedbackPageSize`/
`SettingsSnapshotDto.feedbackPageSize`, newest-first already backend-sorted), rewritten `ModerationView`
(single merged grid), `CommentTreePanel` deleted, `FeedbackPanel` removed from
`AdvertisementViewOverlayModeHandler`, `ownerUserId` wired into `ProviderProfileCatalogViewModeHandler`,
`ComponentFactoryConfig` bean swap, i18n keys/translations (EN+UK) updated — all via two Haiku agent
dispatches (one bug found and fixed in between: `ModerationQueueItemDto` is a Lombok `@Value` class,
not a record, so `.getX()` getters are required, not `.x()` — the first dispatch used record-style
accessors on it). Verified via two real `scripts/build-and-test.sh --no-unit --no-integration` runs
(not just the agent's own report) plus a manual line-by-line read of every touched `marketplace-app`
file: `platform-commons`/`feedback-spring-boot-starter`/`user-spring-boot-starter`/
`marketplace-orchestrator`/`marketplace-rest-api` all compile clean. `marketplace-app` itself could
not get a real compiler pass in this session — `build-and-test.sh` always builds the whole reactor
with no per-module skip, and the reactor stops at the still-red `integration-tests` (Phase 5) before
reaching `marketplace-app` in build order; manual review found no issues, but real compiler
confirmation for `marketplace-app` is still outstanding until Phase 5 lands.

**Done (2026-10-06), Playwright e2e:** `04-provider-profile-flow.spec.js` and
`05-marketplace-advertisement-flow.spec.js` rewritten for the single-reply model: the old
multi-level comment-tree/reaction/depth-cap test steps replaced with owner-only reply visibility
(non-owner sees no trigger), one-reply-only enforcement (trigger/composer disappear once a reply
exists), owner edit/delete within the window, and a report-with-reason popup; the moderation test
rewritten around the merged single `.moderation-grid` with a working author deep link; the
advertisement-side feedback-submission test replaced with an assertion that `.feedback-panel` is
gone. Three real bugs found and fixed via the real `deploy-and-run.sh --reset` + `playwright.sh e2e
--ux` loop (not assumed from code reading alone): (1) a `ModerationQueueItemDto` accessor mismatch
(`@Value` class getters vs. record-style calls — caught at compile time); (2) `userUk`'s UI locale
is Ukrainian, so a bilingual tab-label regex was needed where the original test only matched
English; (3) the real root cause of the stubborn report-dialog hang — the dialog's `page.evaluate()`
-driven native `.click()` (required for an existing Vaadin-dialog rendering quirk) doesn't trigger
the browser's natural focus-blur sequence a real mouse click would, so the just-typed reason text
never synced to the server before the confirm handler's own `isBlank()` guard read it; fixed with
an explicit `press('Tab')` blur before the click. **Final verified result: BUILD SUCCESS across the
whole reactor, 306/306 integration tests, 52/52 Playwright e2e tests — the single-reply feedback
refactor (entry 7) is complete.**

**Found and fixed (2026-10-06), separate from entry 7's own scope but surfaced by it:**
`scripts/deploy-and-run/reset-clean.sql`'s single multi-table `TRUNCATE` statement still listed the
dropped `feedback_comment_reaction` table. Since a multi-table `TRUNCATE` is atomic, the whole
statement failed whenever this script's fast-path ran (`playwright.sh`'s own "Database has data"
auto-truncate, not `deploy-and-run.sh --reset`'s full volume wipe) — leaving every table
untruncated and causing a cascade of unrelated-looking failures (stale users/data from a prior run)
across the whole e2e suite. Caught only because the user ran `playwright.sh e2e --full --ux`
directly against an already-populated database (a path this session's own verification never
exercised, since every verification run here used a full `--reset` instead). Fixed by removing the
dropped table from the `TRUNCATE` list. Re-verified end-to-end after the fix: full `--reset` +
`playwright.sh e2e --full --ux` — **65/65 passed** (includes spec 06's 60-user/60-ad seed, not run
in the earlier 52/52 verification).

**Phase 8 — UI polish round (2026-10-06, real user review of the running app — done 2026-10-07):**

**A. "Add feedback" trigger — reposition + restyle.** `FeedbackPanel.configure()`: reorder
`add(headerContainer)` → `buildFormContainer(entityRef); add(formContainer);` → `add(listContainer);
add(paginationBar);` (currently formContainer is added last, after pagination). `buildAddTrigger()`:
replace the icon-only `UiIconButton` with a `UiPrimaryButton` (icon + visible text "Add feedback"),
same convention as `AdvertisementsView.buildAddButton()`'s `add-advertisement-button`.

**B. ModerationView — cards instead of a Grid, plus cross-tab navigation.**
- Replace `Grid<ModerationQueueItemDto>` with one `Div`-per-row card, styled like `.feedback-entry`
  (reuse the same background/padding/border-radius), each field on its own labeled line: a NEW
  top-of-card "Reported profile" row (always first, linking to the `entityId` profile being
  reported about), then Type, Content (stars + text when `rating != null`), Author (link only if
  the author owns a provider profile, else plain text), Reason, Reported (timestamp), Action
  (Approve icon button).
- `ProviderProfileReadService`: add `findByActorId(Long actorId)` wrapper (mechanical, same shape
  as its existing `findById`) — `ModerationView` calls this per-row to resolve the author's own
  profile id (for the Author link) and `findById(entityId)` for the "Reported profile" row's
  display name. No `FeedbackAccessService`/DTO change needed — this resolution happens entirely in
  `ModerationView` via the already-established multi-orchestrator-service UI composition pattern
  (not a `*Port`, so the orchestrator's 2-port limit doesn't apply).
- Cross-tab navigation (approved design, mirrors `headerBar.setOnSettingsClosed`'s existing
  callback-injection shape):
  - `ModerationView`: new field `private Consumer<Long> onOpenProviderProfile;` + public
    `setOnOpenProviderProfile(Consumer<Long>)` setter. Both the "Reported profile" link and the
    "Author" link (when present) call `onOpenProviderProfile.accept(id)` on click instead of doing
    any navigation themselves.
  - `ProvidersView`: new public `openProfileOverlay(Long providerId, Runnable onClose)` —
    generalizes `openPendingDeepLinkIfAny()`'s body (`providerProfileReadService.findById(id).map(this::enrichSingle).map(profile -> { overlay.openForView(profile, this::refresh, onClose); return true; })`)
    into a reusable method both the pending-deep-link path and this new path call.
  - `MainView.init()`: inside the `if (feedbackAccessService.isAvailable())` block where
    `moderationTab`/`moderationView` are already in scope (and `providersTab`/`providersView` are
    in scope from the outer `if (providerProfileReadService.isAvailable())` block), wire:
    `moderationView.setOnOpenProviderProfile(id -> { Tab previousTab = tabs.getSelectedTab();
    tabs.setSelectedTab(providersTab); providersView.openProfileOverlay(id, () ->
    tabs.setSelectedTab(previousTab)); });` — only reachable when both tabs exist, matching the
    existing nested-`if` structure.

**C. Discard (X) button — two-step smart logic for edit flows only.**
`FeedbackPanel.buildEntryRow`'s `discardButton` and `FeedbackReplyPanel.buildComposer`'s
`cancelButton` (when `existing != null`, i.e. editing, not adding): first click reverts the
draft field(s) to the saved value if they currently differ; a second click (fields already match
the saved value) closes edit mode back to view mode instead of just re-reverting a no-op. Add-flow
composers (`existing == null`) are unchanged — already close immediately on first click.

**D. Reply visual styling — match `.feedback-entry` minus stars, indent to show nesting.**
New/updated CSS in `feedback-panel.css`:
- `.feedback-reply-node` — same background-color/padding/border-radius as `.feedback-entry`, no
  star-related styling.
- `.feedback-reply-panel` (or the node itself) gets a left margin/indent so it visually reads as
  "reply to the feedback entry above it."
- `.feedback-reply-actions` — right-aligned (`justify-content: flex-end` or `margin-left: auto`
  per entry's own `.feedback-entry-header-actions` convention), not left-flowing as currently.
- `.feedback-reply-composer` (the edit/add form state) — no left indent, full width, so the form
  itself doesn't visually inherit the reply's indent.
- Fix the censored-state + report-button vertical misalignment for the reply's hidden state.

**E. "1–2 of 2 records" — confirmed NOT a bug.** Verified directly against the dev DB: two real,
distinct `feedback` rows exist on that profile (ids 3 and 5, different `author_id`s), both from the
user's own manual testing. Pagination is displaying them correctly — no code change needed here.

**F. Dead CSS cleanup (surfaced opportunistically, not separately requested).** `feedback-panel.css`'s
`.comment-*` rules (`.comment-tree-panel`, `.comment-node`, `.comment-author`, `.comment-text`,
`.comment-deleted-text`, `.comment-actions*`, `.comment-reply-*`, `.comment-edit-icon`,
`.comment-delete-icon`, `.comment-toggle-*`, `.comment-reaction-*`, `.comment-header`,
`.comment-inline-form*`, `.comment-close-icon`) are dead now that `CommentTreePanel.java` is
deleted — remove them in the same pass.

**Done (2026-10-07), items A/C/D/E/F:** `FeedbackPanel`/`FeedbackReplyPanel` reorder + restyle,
two-step smart discard/cancel logic, reply indent/right-aligned-actions CSS, dead `.comment-*` CSS
removed. Verified directly against the dev DB that "1-2 of 2 records" (item E) reflects two real,
distinct rows, not a bug.

**Done (2026-10-07), item B (Moderation cards + cross-tab navigation):** `ModerationView` rewritten
from a two-`Grid` layout to a card-based layout (`.moderation-card` per item), with a new
`setOnOpenProviderProfile(Consumer<Long>)` callback-injection setter (mirrors `MainView`'s existing
`headerBar.setOnSettingsClosed` forwarder pattern) wired in `MainView.init()` so a profile-link
click switches to the Providers tab and opens that profile's overlay, returning to Moderation on
close. `ProvidersView` gained a public `openProfileOverlay(Long, Runnable)`, generalized from
`openPendingDeepLinkIfAny()`'s existing body. `AuthorizationService.isOwner(Long, Long)` id-taking
overload and `ProviderProfileReadService.findByActorId(Long)` wrapper added to support this and the
earlier owner-only-reply logic.

Real bug found and fixed during Playwright re-verification, not caught by compile or unit tests:
the "Reported profile" card link rendered as an empty, invisible `<span>`
(`.moderation-profile-link`) because `buildReportedProfileValue` read `profile.getActorName()`
directly off the raw `ProviderProfileReadService.findById()` result — `ProviderProfileRepository`'s
`ROW_MAPPER` never selects `actor_name` at all; that field is only ever populated by a separate
`ProviderProfileDisplayEnrichmentService.enrichWithActor()` step (the same pattern `ProvidersView`
already uses). Fixed by chaining `.map(enrichmentService::enrichWithActor)` before reading
`actorName`. Also added the CSS rules for `.moderation-list`/`.moderation-card`/
`.moderation-field-row`/`.moderation-field-label`/`.moderation-profile-link`/`.moderation-actions`,
entirely absent from `feedback-panel.css` until this fix (a second, independent gap from the same
Haiku dispatch, surfaced and fixed in the same pass rather than left for later).

**Final verified result:** `build-and-test.sh --unit --no-integration` — 355/355 unit tests;
`deploy-and-run.sh --reset-only-db` — clean deploy; `playwright.sh e2e --ux` — **52/65 passed, 13
skipped (spec 06 seed, expected without `--full`), 0 failed**, including the moderation test's
"working author link" assertion. Phase 8 (entry 7's post-deployment UI polish round) is complete.

Minor remaining nuances noted during this review are deliberately out of scope here — to be filed
as a separate task.

---

**Phase 9 — second round of real-app review (2026-10-07), plan only, not yet implemented.**

**G. Moderation card layout — left-alignment + scroll background.** Cards currently render via
`buildFieldRow`'s flat `label + value` `Div` with no column alignment (`.moderation-field-row` is
`display:flex; gap:6px; align-items:baseline`), so every value sits immediately after its own
label's natural width, producing a ragged left edge once several rows with different label lengths
stack — fixable by giving `.moderation-field-label` a fixed `min-width` large enough for the
longest label ("Reported profile:") and keeping `.moderation-field-row` as a two-column flex. The
scroll-background issue (gray card background turns white while scrolling with many items) has no
confirmed root cause yet — each `.moderation-card` carries its own `background-color` via
`.feedback-entry`, and the `12px` gap between cards (`.moderation-list`'s `gap`) already shows the
plain page background between cards even without scrolling, so the most likely explanation is
simply that this gap becomes visually obvious only once there are enough cards to scroll — but this
needs a real Playwright screenshot of a populated, scrolled Moderation tab before committing to a
fix, per this repo's own rule against re-diagnosing a purely visual question from code alone.
**Done (2026-10-07):** `.moderation-field-label`'s `min-width` raised from 120px to 150px (the
longest label, "Reported profile:", didn't reliably fit at 120px bold, causing ragged alignment).
The scroll-background concern was resolved by direct visual evidence rather than guesswork: a new
Playwright test (`04-provider-profile-flow.spec.js`, "moderatorEn views a 7-item moderation
queue...") seeds 7 real flagged items and screenshots the scrolled card list
(`moderation-queue-scrolled-many-items`) — the real screenshot shows consistent card backgrounds
with no white gap; the 12px list gap was never a bug, confirming the original hypothesis. **Status: done.**

**H. Moderation pagination + its own Settings page-size field.** `FeedbackAccessService.findModerationQueue`
currently fetches up to 50 of each hidden-feedback/hidden-comment type, merges, sorts, and returns
the whole list — no real paging. Since moderation-queue size is bounded by how much gets reported
(not a catalog-scale dataset), the right fix is in-memory paging of the already-merged, already-sorted
list — no new SQL/count query needed: `findModerationQueue(int page, int size)` merges+sorts the
full hidden set (dropping the `Pageable` cap on the two sub-fetches, or raising it to a safe high
bound), then returns `items.subList(...)` for the requested page while the caller uses `items.size()`
as the total count. `ModerationView` wires a `PaginationBar` + `SettingsPaginationBinding` exactly
like `FeedbackPanel` already does, bound to a **new** `moderationPageSize` setting
(`UserSettingsDto`/`SettingsSnapshotDto`/`SettingsEditDto`, same shape as the existing 5 page-size
fields — this is the "add it directly in `01-user-schema.xml`'s JSONB `defaultValue` literal, no
migration" case, since the column is JSONB and we're pre-prod). New `IntegerField` +
`SETTINGS_MODERATION_PAGE_SIZE_LABEL` i18n key in `SettingsFormModeHandler`, gated the same way
`usersPageSize`/`timelinePageSize` already are (`privileged`-only — Moderation is MODERATOR/ADMIN
only, same gate `access.canView()` already expresses for those two). **Status: ready to implement.**

**H2. `feedbackPageSize` is already wired end-to-end (`FeedbackPanel` already binds it via
`SettingsPaginationBinding`) but has no Settings UI field at all** — confirmed by reading
`SettingsFormModeHandler`/`SettingsEditDto`: both only carry `adsPageSize`/`usersPageSize`/
`timelinePageSize`/`providerProfilesPageSize`, missing `feedbackPageSize` entirely. Add it the same
way as the other 4 (ungated — feedback is visible to every logged-in user, same as ads/provider
profiles). **Status: ready to implement, mechanical, same shape as H's new field.**

**I. Moderation queue default sort — newest-first, reversing an earlier explicit decision.**
`findModerationQueue` currently sorts `Comparator.comparing(ModerationQueueItemDto::getCreatedAt)`
(oldest-first) — a deliberate choice recorded in `ModerationView`'s own class javadoc ("oldest-first
merged queue"). Flipping the default to newest-first is a real reversal of that earlier decision,
not a bugfix — needs explicit confirmation before implementing, and the superseded ADR entry
updated via `/record-decision` once done (not a fresh, unrelated ADR). **Confirmed (2026-10-07): intentional reversal, approved.** Change `Comparator.comparing(...)` to
`.reversed()`, update `ModerationView`'s class javadoc, and update the superseded ADR entry via
`/record-decision` (not a fresh, unrelated one) once implemented. **Status: ready to implement.**

**J moved out of Phase 9 — see "Phase 10" below, its own section of this same task file (kept here,
not a separate backlog task, per your explicit instruction).**

**K. New moderator action — "permanently confirm as a genuine violation," icon button.** Two
real, already-distinct code paths exist today and need a decision between them, not an assumption:
- **Option 1 (recommended, zero schema change):** wire the *already fully implemented, currently
  unused-by-any-UI* `FeedbackAccessService.rejectFeedback`/`rejectComment` (hard-delete, full
  `FeedbackPort`→`FeedbackPortImpl`→`FeedbackService.rejectFeedback`/`rejectComment` chain already
  exists end-to-end, confirmed by reading the code) as a second icon button next to Approve in
  `ModerationView`'s action row. The content and its row are permanently removed; this already
  matches "a moderator's confirmed judgment that it's genuinely wrong," since a soft "confirmed
  but still censored forever" state doesn't exist anywhere else in this schema today.
- **Option 2 (schema change required):** introduce a third `FeedbackModerationStatus` value (e.g.
  `CONFIRMED_HIDDEN`) so the content survives forever in its current "censored" placeholder state
  (visible to the public page as `[hidden]`, same as today's `HIDDEN`) but drops out of the
  Moderation queue — needs a `01-feedback-schema.xml` edit (enum column is `VARCHAR(20)`, no schema
  change needed for the value itself, but `FeedbackModerationStatus` enum, `findHiddenFeedback`/
  `findHiddenComments`'s `WHERE moderation_status = 'HIDDEN'` filter, and the queue-removal logic
  all need updating) plus a new `FeedbackService.confirmFeedbackViolation`/`confirmCommentViolation`
  method distinct from `approveFeedback`/`approveComment`.
**Confirmed (2026-10-07): Option 2, approved.** New `FeedbackModerationStatus.CONFIRMED_HIDDEN`
value — content stays rendered as the existing censored placeholder forever (same code path as
today's `HIDDEN`), but the item is excluded from `findHiddenFeedback`/`findHiddenComments`'s
`moderation_status = 'HIDDEN'` queue filter (so it drops out of the Moderation queue once
confirmed) and therefore out of `findModerationQueue`. New `FeedbackService.confirmFeedbackViolation`/
`confirmCommentViolation` methods (parallel to `approveFeedback`/`approveComment`, setting
`CONFIRMED_HIDDEN` instead of `NEW`), exposed through `FeedbackPort`/`FeedbackPortImpl`/
`FeedbackAccessService`, with a new icon button in `ModerationView`'s action row next to Approve.
No `01-feedback-schema.xml` edit needed for the column itself (`moderation_status` is already
`VARCHAR(20)`, room for a longer enum name) — only the enum class, the two repository query
filters, and the new service/Port/UI methods. **Status: ready to implement.**

**L. Feedback/reply icon-button positioning — one consistent vertical column everywhere.**
Confirmed by reading both files: `FeedbackPanel.buildEntryRow`'s View-mode icons (Edit, Report) sit
horizontally inside the header row (`.feedback-entry-header-actions`), while its Edit-mode icons
(Save, Discard) already sit in a vertical, bordered side column (`.feedback-entry-edit-actions`).
`FeedbackReplyPanel.buildReplyNode`'s View-mode icons (Edit, Delete, Report) sit horizontally,
right-aligned below the text (`.feedback-reply-actions`), while its composer icons (Save, Cancel)
are entirely unstyled (`.feedback-reply-composer-actions` has no CSS rule at all, so they default-stack
vertically with no border/padding). Unify all four into one shared CSS class (e.g.
`.entity-actions-column`, carrying `.feedback-entry-edit-actions`'s existing
`display:flex; flex-direction:column; gap:4px; border-left:1px solid var(--app-border-default);
padding-left:8px; justify-content:center;`), and restructure both components' DOM so content and
its action column sit side-by-side as two flex children of the row/node — not content-with-icons-
inline or icons-below-content as today. Remove the four now-dead bespoke action-row CSS rules this
replaces. **Status: ready to implement — fully specified, no open design question, good Haiku
candidate.**

**Review:** after all of Phase 9 lands, run a `/review` pass — but only after `improvement-206` is
implemented, per your explicit instruction; noted here now, not scheduled yet.

**Done (2026-10-07), Phase 9 (H/H2/I/K/L/G) + Phase 11 (M/N/O/P/Q/R/S) — all implemented and
verified, dispatched via Haiku agents throughout, each independently re-verified against real
diffs/build output, never trusted from agent self-report alone:**
- **H/H2:** `moderationPageSize` (new, privileged-gated) and `feedbackPageSize` (existing field,
  previously missing its own UI control) added end-to-end — `UserSettingsDto`, `SettingsSnapshotDto`
  (6th field + audit diff/allFields entries), `SettingsEditDto`, `SettingsFormModeHandler` (both new
  `IntegerField`s wired through activate/save/discardChanges/handleRestoreFromActivity/loadRestored/
  buildBinder), `UserPreferencesService.toSettingsSnapshot`. `FeedbackAccessService.findModerationQueue`
  restructured into a `ModerationQueuePage` record (items + true total count) with in-memory paging
  over a `buildFullModerationQueue()` helper (no SQL/count-query complexity needed — queue size is
  bounded by real reports). `ModerationView` wired with a real `PaginationBar` +
  `SettingsPaginationBinding`. A second, independently-found adjacent gap fixed in the same pass:
  `AuditTimelineRowRenderer`'s `USER_SETTINGS` field-label switch was missing cases for both
  `feedbackPageSize` and the new `moderationPageSize` (would have rendered unlabeled in the audit
  timeline) — added both, plus their `CHANGES_SETTING_*` i18n keys.
- **I:** Moderation queue default sort flipped from oldest-first to newest-first (confirmed
  intentional reversal) — `Comparator...reversed()`, class/method javadoc updated.
- **K:** New `FeedbackModerationStatus.CONFIRMED_HIDDEN` value + `isHidden()` helper (both `HIDDEN`
  and `CONFIRMED_HIDDEN` render the same censored placeholder); new
  `confirmFeedbackViolation`/`confirmCommentViolation` methods through
  `FeedbackService`→`FeedbackPort`/`FeedbackPortImpl`→`FeedbackAccessService` (with the same
  audit-capture convention every other moderation action already uses); new "Confirm violation"
  button in `ModerationView` next to Approve. A real, previously-undiscovered dead-code finding
  surfaced and fixed in the same pass (user-approved before touching): 6 unused i18n keys
  (`MODERATION_BUTTON_REJECT`/`MODERATION_CONFIRM_REJECT_*`/`MODERATION_NOTIFICATION_REJECTED`) for
  a "Reject" button that had full i18n scaffolding but was never wired to any UI — confirmed via
  grep, removed.
- **L:** Feedback/reply icon buttons unified into one shared vertical column
  (`.entity-actions-column`, same visual treatment everywhere) instead of the previous
  inconsistent mix (view-mode icons inline in the header row, edit-mode icons in their own bordered
  column, reply icons right-aligned below text, composer icons unstyled). `FeedbackPanel`/
  `FeedbackReplyPanel` DOM restructured; four now-dead CSS rules removed.
- **M/N:** `FeedbackRepository.upsertAggregate`'s SQL now joins `feedback_content` and filters
  `moderation_status = 'NEW'` — hidden/flagged feedback no longer inflates the public aggregate.
  `flagFeedback`/`approveFeedback` now call `upsertAggregate` (previously only `rejectFeedback` did,
  leaving the aggregate stale after every flag/approve).
- **O:** `FeedbackService.save()`'s update branch now takes `entityType`/`entityId`/`authorId` from
  the existing record, not the incoming DTO — an update call can no longer move a feedback entry to
  a different entity or reassign its author (confirmed unreachable today — zero feedback endpoints
  exist in `marketplace-rest-api` — but a real service-layer invariant fix regardless).
- **P:** `FeedbackComment` update identity verified already correct — no fix needed.
- **Q:** `FailureRateLimiter.recordFailure()` was being called after *successful* feedback/comment
  submissions to track a daily quota — semantically backwards. Added a neutrally-named
  `recordAttempt()` alias (delegates to the same counter), switched `FeedbackService`'s two
  call sites to it; `AuthService`/`UserService`/`ContactRevealRateLimiter`'s correct
  `recordFailure()` usages left untouched.
- **R:** 7 new regression tests added (`FeedbackRepositoryTest` x2, `FeedbackServiceTest` x5)
  covering aggregate exclusion/recompute/zero-state and the identity-hijack fix.
- **S:** Concurrency test added (`concurrentSaves_sameEntity_aggregateConvergesToCorrectValue`) —
  two threads racing `save()` on the same entity, asserting the aggregate converges to the correct
  value after an explicit recompute (the materialized aggregate row can legitimately race
  transiently; the underlying `feedback`/`feedback_rating` rows themselves never get corrupted).
- **Real Playwright bugs found and fixed along the way** (via real `playwright.sh` runs, not
  assumed): `audit.flow.js`'s `runVerifySettingsAfterSignupFlow` had a stale field count
  (`toHaveCount(privileged ? 4 : 2)`, now `6 : 3`) that cascaded into failing most of the rest of
  the suite (specs are serial — one early failure skips downstream setup); `settings.flow.js`'s
  `changePageSizes`/`getPageSizes` read the `users` field at the wrong index (2, now 3) after
  `feedback` was inserted before it; `04-provider-profile-flow.spec.js`'s existing
  `assertRightAligned` call referenced a CSS class L's own redesign removed
  (`.feedback-entry-header-actions`, now `.entity-actions-column`/`.feedback-entry-row` with an
  explicit 20px tolerance for the card's own real, intentional 16px padding); the new scroll-test
  itself needed three of its own fixes before going green — a reviewer
  (`userEn`) who already had a leftover entry on the target profile from an earlier test (redesigned
  to use 4 fresh reviewers instead of 5), a race between a transient "thanks for your feedback"
  toast and Playwright's own polling (fixed by asserting the persisted entry instead of the toast),
  and `adminUk`'s already-documented TEST_USERS-locale-vs-real-UI-locale quirk (same fix pattern
  already established elsewhere in the same file) plus an unscoped `.pagination-count` locator
  matching the Advertisements tab's own pagination bar too (scoped to `.moderation-view-layout`).

**Final verified result:** `build-and-test.sh --unit --no-integration` — 355/355 unit tests;
`build-and-test.sh --no-unit --integration` — 315/315 integration tests; `deploy-and-run.sh
--reset-only-db` — clean deploy; `playwright.sh e2e --ux` — **53/66 passed, 13 skipped (spec 06
seed, expected without `--full`), 0 failed**. Phase 9 and Phase 11 are both complete. Phase 10 (J)
remains open, deliberately deferred per your own instruction.

---

**Phase 10 — Moderation tab restructured to mirror AdvertisementsView (2026-10-07), plan only, not
yet implemented, scoped deliberately after Phase 9 (per your instruction: see how H/I's simpler
pagination+sort addition feels first).**

Replace the current bespoke card-list+plain-pagination shape with the same `QueryBlock`/
`QueryStatusBar`/`FilterMeta`/`SortMeta` infrastructure `AdvertisementsView`/`UserView` already use,
so Moderation gains real filter/sort controls (type, date range, reason text) instead of only a
hardcoded sort direction. This needs, at minimum:
- A real `ModerationQueueFilterDto` (type/date-range/reason-text) and corresponding `*FilterMeta`
  (`Fields.*` constants, no raw strings — per this repo's own Query Layer Pattern) at the
  `marketplace-orchestrator` level, since filtering a merged, two-source (`feedback`+
  `feedback_comment`) result set can't reuse either domain's existing `SqlFilterBuilder` as-is.
- A `*SortMeta` exposing at least created-date asc/desc (replacing H/I's single hardcoded
  direction with a real user-facing toggle) and whichever other columns the card view actually
  shows (type, author).
- Deciding whether the result stays card-based (just adding `QueryBlock`+`QueryStatusBar` chrome
  above the existing `.moderation-card` list) or moves to an actual `Grid<ModerationQueueItemDto>`
  — Phase 8 deliberately moved away from a `Grid` because "field content is too long for grid
  cells" (see `ModerationView`'s own class javadoc); reintroducing `QueryBlock`/`QueryStatusBar`
  does not require reversing that specific decision, the two are separable.
- Re-deciding how H's in-memory-pagination approach interacts with real filtering — filtering
  before paging still works against the same merged in-memory list, just adds a filter predicate
  before the `subList` slice, so this doesn't invalidate H's design, only extends it.

**Status: plan only — not scoped to exact files/methods yet (needs its own technical-layer pass
once picked up), deliberately sequenced after Phase 9 so H/I's simpler addition can be evaluated
first.**

---

**Phase 11 — Feedback aggregate/identity runtime-consistency fixes (2026-10-07), plan only, not
yet implemented. Backend-only, independent of Phase 9/10 (those are UI). Every item below was
verified directly against the current code before being added here — not assumed from a spec.**

**M. Public aggregate counts HIDDEN feedback — confirmed real bug.**
`FeedbackRepository.upsertAggregate()` joins only `feedback`+`feedback_rating`, with no
`feedback_content`/`moderation_status` filter at all — a moderator hiding a feedback entry today has
zero effect on its entity's public `avg_rating`/`review_count`. Fix: add the join + filter —
```sql
INSERT INTO feedback_aggregate (entity_type, entity_id, avg_rating, review_count, updated_at)
SELECT :entityType, :entityId, COALESCE(AVG(r.rating), 0), COUNT(*), NOW()
FROM feedback f
JOIN feedback_rating r ON r.feedback_id = f.id
JOIN feedback_content fc ON fc.id = f.content_id
WHERE f.entity_type = :entityType AND f.entity_id = :entityId
  AND fc.moderation_status = 'NEW'
ON CONFLICT (entity_type, entity_id) DO UPDATE
SET avg_rating = EXCLUDED.avg_rating, review_count = EXCLUDED.review_count, updated_at = EXCLUDED.updated_at
```
A bare aggregate `SELECT` with no `GROUP BY` always returns exactly one row even when the `WHERE`
matches nothing (`COUNT(*)=0`, `AVG→NULL→COALESCE→0`), so "all feedback hidden" already naturally
upserts a correct `0/0` row with just this change — no extra branch needed. **Status: ready to
implement.**

**N. Aggregate not recomputed after flag/approve — confirmed real bug.**
`FeedbackService.flagFeedback`/`approveFeedback` (lines 176-185, 199-204) never call
`repository.upsertAggregate(...)` — only `rejectFeedback` does today. Fix: add one call to each,
reusing the `FeedbackView existing` already read in both methods (already carries `entityType()`/
`entityId()`, no extra query needed):
```java
repository.flagContent(existing.contentId(), reason);
repository.upsertAggregate(existing.entityType(), existing.entityId());
```
```java
repository.updateModerationStatus(existing.contentId(), FeedbackModerationStatus.NEW);
repository.upsertAggregate(existing.entityType(), existing.entityId());
```
`flagComment`/`approveComment` need **no** equivalent call — a reply carries no rating, so comment
moderation never affects `feedback_aggregate` in the first place; confirmed by reading
`FeedbackCommentView`'s fields (no `entityType`/`entityId`, only `feedbackId`). **Status: ready to
implement.**

**O. `FeedbackService.save()` update path can hijack identity — confirmed real weakness, low
current severity.** The update branch (`dto.id() != null`) builds `saved` from `dto.entityType()`/
`dto.entityId()`/`dto.authorId()` instead of the `existing` record just read — so nothing at the
service layer stops an update call from moving a feedback entry to a different entity or
reassigning its author, if a caller ever passed different values. Confirmed currently unreachable
in practice: `marketplace-rest-api` exposes zero feedback endpoints (grepped, zero matches) and the
only real caller, `FeedbackPanel`'s edit flow, always sends the same `entityRef`/author back
unchanged — so this is a latent defense-in-depth gap, not a live bug. Fix: use `existing`'s values
for the identity fields, and use those same values (not `dto`'s) for the `upsertAggregate` call that
follows:
```java
saved = Feedback.builder()
        .id(existing.id())
        .entityType(existing.entityType())
        .entityId(existing.entityId())
        .authorId(existing.authorId())
        .contentId(contentId)
        .build();
```
`dto.entityType()`/`dto.entityId()`/`dto.authorId()` become write-ignored on update (API shape
unchanged, no new validation exception introduced — simplest fix matching "don't break the API
unnecessarily"). **Status: ready to implement.**

**P. `FeedbackComment` update identity — verified already correct, no fix needed.**
`saveComment()`'s update branch already builds `saved` from `existing.id()`/`existing.contentId()`/
`existing.authorId()`/`existing.feedbackId()`, not from `dto` — confirmed by reading the code.
**Status: no action.**

**Q. `FailureRateLimiter.recordFailure()` misused to count successful feedback/comment submissions
— confirmed, low priority.** `FeedbackService.save()`/`saveComment()` call `recordFailure()` after
a *successful* create to track the daily-submission quota — functionally correct (the counter is
generic under the hood) but semantically backwards, and `recordFailure` is also used correctly
elsewhere (`AuthService`, `UserService` — real login/registration failures), so renaming the method
itself would misname those correct call sites. Confirmed-safe local fix: add a second, neutrally-named
method to `FailureRateLimiter` —
```java
/** Same counter as {@link #recordFailure}, under a name that fits a non-failure quota use (e.g. a daily submission cap). */
public void recordAttempt(@NonNull String key) {
    recordFailure(key);
}
```
and switch `FeedbackService`'s two call sites to `recordAttempt`. Purely additive, zero risk to the
3 existing correct callers. **Status: ready to implement.**

**R. Tests to add/update** (`integration-tests` module, `level1/feedback/`):
- Aggregate: single NEW (count=1, avg=rating); NEW+HIDDEN (only NEW counted); NEW→HIDDEN (count
  drops); HIDDEN→NEW via approve (count rises); delete removes it from the aggregate; all-HIDDEN
  yields a `0/0` row, not a missing one; mixed ratings average correctly.
- Identity: update preserves `authorId`/`entityType`/`entityId` even if the `FeedbackSaveDto` passed
  to `save()` carries different values for them.
- Moderation: `flagFeedback`/`approveFeedback`/`rejectFeedback` each leave the aggregate consistent
  with the resulting visible-feedback set.
- Regression-only (no new behavior, just confirms P stays true): comment update still preserves its
  own identity; single-reply invariant still enforced.

**S. Concurrency test — optional, attempt only if it reproduces cleanly.** Two concurrent
`save()`/moderation-transition calls against the same entity via `integration-tests`' real
Testcontainers Postgres; assert the resulting aggregate matches *some* valid end state (not a
specific interleaving) — `upsertAggregate`'s own `ON CONFLICT ... DO UPDATE` plus each write running
in its own `@Transactional` method already gives read-committed-level protection against a
corrupted row (never a negative count or an average outside the actual rating range), so this is a
confirmation test, not expected to uncover a new bug. If it does reveal a real race, fix it with the
smallest local change (e.g. a `SELECT ... FOR UPDATE` on the aggregate row); if it can't be
reproduced reliably in this environment, say so plainly rather than inventing a speculative fix.

**Explicitly out of scope for this phase** (per your own constraint list): moving Feedback across
modules, merging `FeedbackContent`/`FeedbackRating`/`FeedbackComment`, deleting any
repository/service, JPA migration, replacing Spring Data JDBC, rewriting `FeedbackPanel`, threaded
comments, a new moderation or authorization architecture, schema changes beyond what M/N/O/Q
actually require (none of them need one), or any unrelated refactor.

**Status: plan only, ready to implement — M/N/O/Q/P are each independently small and verified; R's
test list is the acceptance gate; S is exploratory and may conclude "not reproducible" without that
being a failure of the phase.**

**Done (2026-10-06), integration-tests:** `FeedbackRepositoryTest`/`FeedbackServiceTest` rewritten
for the single-reply model (removed reaction/depth/tombstone tests, added `findCommentByFeedback`/
`flagContent`-with-reason coverage; fixed a real behavioral conflict the first pass missed — the
daily-rate-limit test used to create 30 comments on one feedback entry, which now violates the new
`feedback_comment.feedback_id` unique constraint, so it was redesigned to spread across 30 distinct
feedback entries instead); `SettingsSnapshotDtoTest` updated for the 5th `feedbackPageSize` field;
`TestDataCleaner.cleanAll` no longer references the dropped `feedback_comment_reaction` table; new
`level2/orchestrator/FeedbackCommentWriteServiceTest` (first-ever Level 2 test, scaffolded per
`integration-tests/CLAUDE.md`'s "only once a concrete test needs it" rule) covers the owner-check,
one-reply-only enforcement, and "no admin override" — one real bug found and fixed along the way
(`OrchestratorAutoConfiguration`'s package-wide `@ComponentScan` pulled in unrelated services with
unmet bean dependencies; fixed by listing only the exact service classes this test needs, plus a
local stub `UiLabelHook` bean for `UserActorNameService`'s transitive dependency). Verified via a
real `scripts/build-and-test.sh --no-unit --integration` run (two iterations — first caught the
Spring-context bug above): **BUILD SUCCESS across the whole 17-module reactor**, including
`marketplace-app` (first real compiler confirmation for it this session) — **306/306 integration
tests pass, 0 failures, 0 errors**.

**Phase B — entities & repository (`feedback-spring-boot-starter`).**
- `org.ost.feedback.entities.FeedbackComment` — remove `parentCommentId` field.
- Delete `org.ost.feedback.entities.FeedbackCommentReaction` entirely.
- `org.ost.feedback.entities.FeedbackContent` — add `moderationReason` (`String`) field.
- `FeedbackRepository.java`:
  - Remove `FeedbackCommentReactionView` record, `findReactionsByComments`, `findReactionType`,
    `upsertReaction`, `deleteReaction`, `countChildren`, `deleteCommentSubtree` (lines ~328-410
    region per the investigation).
  - Replace the `WITH RECURSIVE tree` query in `findCommentsByFeedback` (lines ~213-232) with a
    plain `findCommentByFeedback(Long feedbackId)` returning `Optional<FeedbackCommentView>` (at
    most one row now that the UNIQUE constraint exists) — drop `FeedbackCommentView.parentCommentId`.
  - `findAllCommentsByFeedbackIncludingHidden` (lines ~236-255, used only by `deleteFeedbackHard`'s
    cascade) — same simplification, at most one row.
  - `deleteCommentHard` — drop the reaction-delete step (table is gone).
  - `saveContent`/`updateModerationStatus` — thread `moderationReason` through (new param).
  - `findHiddenFeedback`/`findHiddenComments` — select `moderation_reason` too, so the DTOs built
    from these rows carry it.

**Phase C — platform-commons `feedback.*` contracts.**
- `FeedbackPort.java` — remove `saveReaction`; change `flagFeedback(Long feedbackId, Long actorId)`
  to `flagFeedback(Long feedbackId, Long actorId, String reason)`; same for `flagComment`.
- `FeedbackDto.java` — add `String moderationReason`.
- `FeedbackCommentDto.java` — remove `parentCommentId`, `reactorIdsByType`, `myReaction`; add
  `String moderationReason`.
- `FeedbackCommentSaveDto.java` — remove `parentCommentId` field and the `MAX_DEPTH` constant.
- Delete `FeedbackCommentReactionSaveDto.java`.
- Delete `org.ost.platform.feedback.model.FeedbackReactionType` (confirmed only 3 other
  referencing files, all touched in this same refactor — `CommentTreePanel.java` being deleted in
  Phase E, `FeedbackServiceTest.java` updated in Phase G, `FeedbackCommentReactionSaveDto.java`
  deleted above).
- `FeedbackCommentSnapshotDto.java` — add `moderationReason` alongside the existing
  `moderationStatus` field, so the audit trail captures the report reason too (currently only
  tracks status).
- New `ModerationQueueItemDto.java` (`org.ost.platform.feedback.dto`), `@Value @Builder
  @FieldNameConstants`: `type` (new enum `ModerationQueueItemType{FEEDBACK,COMMENT}`, same
  package), `Long id`, `Long entityId` (the `PROVIDER_PROFILE` id the content is attached to — for
  building the deep link), `String contentText`, `Integer rating` (nullable, FEEDBACK rows only),
  `String moderationReason`, `Long authorId`, `String authorName`, `Instant createdAt`.

**Phase D — `FeedbackService.java` + `FeedbackAccessService.java` (orchestrator).**
- `FeedbackService.saveComment` — remove the `computeCommentDepth` check (depth no longer exists);
  add an authorization check that `actorId` equals the `actorId` (owner) of the `ProviderProfile`
  the parent feedback's `entityId` points to (needs a `ProviderProfilePort.findById` call — confirm
  at implementation time whether `FeedbackService` is allowed to depend on `ProviderProfilePort`
  directly, or whether this check must move up to `FeedbackAccessService` in the orchestrator,
  since a starter must not depend on a sibling starter's port — **this is a real open design
  question, resolve it before writing code, don't guess**). Remove `saveReaction`, `groupReactions`,
  `deleteDescendantsThenSelf`, `pruneDanglingTombstones`, `computeCommentDepth`.
- `deleteComment` — drop the tombstone-vs-hard-delete branch (`countChildren` is gone); always hard
  delete, same owner+edit-window check as today.
- `flagFeedback`/`flagComment` — add `reason` param, pass through to
  `FeedbackRepository.updateModerationStatus`/`saveContent`.
- `FeedbackAccessService.java` (orchestrator) — add `findModerationQueue(Pageable)`: call
  `feedbackPort.findHiddenFeedback`/`findHiddenComments`, map each to `ModerationQueueItemDto`
  (type discriminator), merge+sort by `createdAt` ascending (oldest-first, per the already-agreed
  grid order), return one `List`. Update `flagFeedback`/`flagComment` call sites for the new
  `reason` param.

**Phase E — UI (`marketplace-app`).**
- New `ui/views/components/dialogs/ReportReasonDialog.java` — modeled on
  `ConfirmActionDialog.java`: constructor `(String title, String reasonFieldLabel, String
  confirmLabel, String cancelLabel, Consumer<String> onConfirm)`, `DialogLayout.addFormContent`
  gets a `UiTextArea` instead of `ConfirmActionDialog`'s static `Paragraph`, `getFooter()` gets the
  same two-button layout, Apply calls `onConfirm.accept(textArea.getValue())`.
- `FeedbackPanel.java` — replace the `ConfirmActionDialog` report flow (line ~303) with
  `ReportReasonDialog`, wiring to the new `flagFeedback(id, actorId, reason)` signature. Replace
  the `commentTreePanelFactory`-built `CommentTreePanel` with the new `FeedbackReplyPanel` (below).
- Delete `CommentTreePanel.java` entirely.
- New `ui/views/components/FeedbackReplyPanel.java` — single-comment display (or empty state),
  reply form shown only when `currentUserId == providerProfile.actorId()` and no comment exists
  yet, edit/delete controls shown only to that same owner within the existing edit window, a report
  button (via `ReportReasonDialog`) visible to any other viewer when a comment exists. No reaction
  buttons, no recursion, no depth handling.
- `AdvertisementViewOverlayModeHandler.java` — remove the `feedbackPanel`/`feedbackPanelFactory`
  field, its construction, and its placement in the view body; keep `ContactRevealPanel` untouched.
- `ProviderProfileCatalogViewModeHandler.java` — update `FeedbackPanel` wiring only insofar as it
  now embeds `FeedbackReplyPanel` instead of `CommentTreePanel` (the panel itself already has
  access to the `ProviderProfileDto` being viewed, so the owner-check in `FeedbackReplyPanel` needs
  that DTO passed in — confirm the exact constructor/parameter plumbing when implementing, since
  `FeedbackPanel`'s current constructor doesn't carry a `ProviderProfileDto` reference yet per the
  investigation).
- `ModerationView.java` — replace `feedbackGrid`+`commentGrid` with one
  `Grid<ModerationQueueItemDto>`, columns Type (plain text/badge from the enum), Content (text
  excerpt + star rating when `type==FEEDBACK` and `rating!=null`), Author (rendered as an `Anchor`
  with `href` from `AppLinkService.providerProfileUrl(entityId)`, `target="_blank"`, text =
  `authorName` — no separate View column), Reason (`moderationReason`), Reported (`createdAt`,
  oldest first), Action (Approve-only `UiIconButton`, same as today). Backing call switches from
  two `findHiddenFeedback`/`findHiddenComments` calls to the new
  `feedbackAccessService.findModerationQueue(pageable)`. Remove the now-dead
  `MODERATION_BUTTON_REJECT`/`MODERATION_CONFIRM_REJECT_*` i18n keys from `I18nKey.java` (confirmed
  unused by the UI even today) — only if this phase also confirms `rejectFeedback`/`rejectComment`
  service methods have no other caller; if they do, leave the i18n keys and service methods alone,
  this phase only touches the grid.

**Phase F — integration tests (`integration-tests`).**
- `FeedbackServiceTest.java` (31 tests) / `FeedbackRepositoryTest.java` (13 tests) — remove every
  test covering: comment depth/`MAX_DEPTH`, reactions (`saveReaction`/`FeedbackReactionType`),
  tombstoning/`deleteDescendantsThenSelf`/`pruneDanglingTombstones`, multi-reply-per-feedback.
  Add tests for: only the profile owner can `saveComment`; a second `saveComment` on the same
  feedback fails (UNIQUE constraint / explicit check); `flagFeedback`/`flagComment` persist
  `reason` and it's readable back via `findHiddenFeedback`/`findHiddenComments`; hide-as-censored
  still works with no edit/delete override for admins.

**Phase G — Playwright (`playwright/e2e/`).**
- `04-provider-profile-flow.spec.js` lines ~746-918 — replace the nested reply/edit/reaction/
  delete/3-level-depth-cap test.steps with: owner-only reply box visibility, one reply allowed
  (second attempt blocked/hidden), owner edit/delete within window, report-with-reason popup
  (assert the reason text field + Apply/Cancel), non-owner cannot see a reply box.
- Lines ~1113-1214 (moderation test) — update to the single merged grid: report a feedback and a
  comment (each via the new reason popup), open `ModerationView`, assert both rows appear in the
  one grid with their reason text and a working Author link, approve both, re-verify visible again.
- `05-marketplace-advertisement-flow.spec.js` lines ~151-176 — remove the feedback-submission test
  block entirely (advertisements no longer have a `FeedbackPanel`); assert only `ContactRevealPanel`
  is present where the feedback panel used to be.

Open design question flagged above (Phase D) must be resolved — with the user, not assumed — before
any Phase D/E code is written: does the "is this actor the provider-profile owner" check live in
`FeedbackService` (via a new direct dependency on `ProviderProfilePort`, which would be a new
cross-starter dependency needing its own approval) or in `FeedbackAccessService` in the
orchestrator (consistent with the existing "starters don't depend on sibling starters" rule, using
its already-present `ComponentFactory<ProviderProfilePort>` — but `FeedbackAccessService` is
currently capped at 2 `*Port` dependencies per the architecture rule, and already uses
`FeedbackPort`+`AuditPort`, so adding a third needs either an exception or splitting into a new
use-case service).

### Handoff for a Haiku execution agent (entry 7, phases B-G) — plan only, no code changed yet

**State before starting:** `01-feedback-schema.xml` is at its committed (`HEAD`) version, i.e. the old schema with `parent_comment_id` and `feedback_comment_reaction`. The target-schema rewrite still has to be done as part of phase A. Dev DB `experiments`: feedback tables dropped and the `01-feedback-schema` row removed from `databasechangelog`, so the next app start re-applies the file. Nothing is committed.

**Decided design (approved):** only the reviewed provider-profile owner writes the one reply. Owner check lives in a new orchestrator service `marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/FeedbackCommentWriteService.java`, NOT in `FeedbackService` (starter must not depend on `ProviderProfilePort`) and NOT in `FeedbackAccessService` (would hold 3 `*Port` deps, ArchUnit limit is 2). It depends on: `FeedbackAccessService`, `ProviderProfileReadService.findById(entityId)` (owner = `actorId`), `ActorLookupService`, `AuthorizationService`. `saveComment` uses strict ownership (`AuthorizationService.isOwner`, never `canOperate` — admins do not reply). `editComment`/`deleteComment` require the comment author within the 48h window. Add overload `isOwner(Long actingUserId, Long ownerId)` to `AuthorizationService` following the existing `canOperate(Long, Long)` pattern.

**Phase B — entities and repository (`feedback-spring-boot-starter`, package `org.ost.feedback.entity` and `org.ost.feedback.repository`)**
- Delete `entity/FeedbackCommentReaction.java`.
- `entity/FeedbackComment.java`: remove field `parentCommentId`.
- `entity/FeedbackContent.java`: add `String moderationReason`.
- `repository/FeedbackRepository.java`: remove reaction methods (`findReactionsByComments`, `findReactionType`, `upsertReaction`, `deleteReaction`, `FeedbackCommentReactionView`), `countChildren`, `deleteCommentSubtree`; replace the `WITH RECURSIVE` comment query with a single-reply lookup; drop `parentCommentId` from views; thread `moderationReason` through `saveContent`/`updateModerationStatus`; select `moderation_reason` in `findHiddenFeedback`/`findHiddenComments`.

**Phase C — contracts (`platform-commons`, package `org.ost.platform.feedback`)**
- `spi/FeedbackPort.java`: remove `saveReaction`; `flagFeedback(Long, Long, String reason)` and `flagComment(Long, Long, String reason)`.
- `dto/FeedbackDto.java`, `dto/FeedbackCommentDto.java`: add `moderationReason`; remove `parentCommentId`, `reactorIdsByType`, `myReaction` from the comment DTO.
- `dto/FeedbackCommentSaveDto.java`: remove `parentCommentId` and `MAX_DEPTH`.
- `dto/FeedbackCommentSnapshotDto.java`: add `moderationReason` next to `moderationStatus`.
- Delete `dto/FeedbackCommentReactionSaveDto.java` and `model/FeedbackReactionType.java`.
- New `dto/ModerationQueueItemDto.java` (`@Value @Builder @FieldNameConstants`): `ModerationQueueItemType type`, `Long id`, `Long entityId`, `String contentText`, `Integer rating` (nullable), `String moderationReason`, `Long authorId`, `String authorName`, `Instant createdAt`. New enum `ModerationQueueItemType { FEEDBACK, COMMENT }` in the same package.

**Phase D — orchestrator (`marketplace-orchestrator`)**
- `services/FeedbackCommentWriteService.java`: as described above.
- `services/FeedbackAccessService.java`: remove `saveReaction`; pass `reason` through `flagFeedback`/`flagComment`; add `findModerationQueue(Pageable)` merging `findHiddenFeedback` and `findHiddenComments` into `ModerationQueueItemDto`, sorted by `createdAt` ascending.
- `services/AuthorizationService.java`: add `isOwner(Long, Long)`.
- `FeedbackService.java` (starter): keep one-reply check, remove `saveReaction`, `groupReactions`, `deleteDescendantsThenSelf`, `pruneDanglingTombstones`, `computeCommentDepth`; `deleteComment` always hard-deletes.

**Phase E — UI (`marketplace-app`)**
- New `ui/views/components/dialogs/ReportReasonDialog.java`, modeled on `ConfirmActionDialog.java`: text area for the reason, Apply/Cancel, calls `onConfirm` with the entered text.
- New `ui/views/components/FeedbackReplyPanel.java`: shows the single reply or empty state; reply form only when current user is the profile owner and no reply exists; edit/delete only for the author within 48h; report button (via `ReportReasonDialog`) for other viewers.
- Delete `ui/views/components/CommentTreePanel.java`.
- `FeedbackPanel.java`: use `ReportReasonDialog` for reports; embed `FeedbackReplyPanel`.
- `AdvertisementViewOverlayModeHandler.java`: remove the `FeedbackPanel` field, construction, and placement; keep `ContactRevealPanel`.
- `ui/views/main/tabs/moderation/ModerationView.java`: one `Grid<ModerationQueueItemDto>` (Type, Content, Author as link via `AppLinkService.providerProfileUrl(entityId)`, Reason, Reported, Action = Approve icon button). No emoji.

**Phase F — integration tests (`integration-tests`, `level1/feedback`)**
- `FeedbackServiceTest.java`, `FeedbackRepositoryTest.java`: remove tests for depth, reactions, tombstones, multiple replies per feedback.
- Add: non-owner cannot save reply; a second reply on the same feedback is rejected; `flagFeedback`/`flagComment` persist and return `reason`; admin cannot edit or delete a reply.

**Phase G — Playwright (`playwright/e2e/`)**
- `04-provider-profile-flow.spec.js` (about lines 746-918 nested reply/edit/reaction/depth test; about lines 1113-1214 moderation test): replace with owner-only reply, single-reply limit, report-with-reason popup, merged moderation grid.
- `05-marketplace-advertisement-flow.spec.js` (about lines 151-176): remove the feedback block; assert `ContactRevealPanel` is present.

**Rules for the executing agent:** verify build and tests after each phase before starting the next; do not commit; do not change `01-feedback-schema.xml` again; record the single-reply decision via `/record-decision` after phase D; follow the rules in `.claude/rules.md` and the module `CLAUDE.md` files for each touched module.

## Related

- `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` — sibling bucket, for
  findings too large to size immediately rather than simply out of scope.
- `backlog/completed/tasks/improvement-200-f06-reviews-ratings.md` — the original F-06 feature
  this entry trims back; its own Phase 3 (comment-tree schema) and Phase 5 (depth-cap/rate-limit)
  history explains what this entry removes and why those were reasonable at the time.
