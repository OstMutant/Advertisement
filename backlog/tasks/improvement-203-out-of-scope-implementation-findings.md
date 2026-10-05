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

**Fix plan:**

- `FeedbackPanel.java`: new constructor-injected field `private final PaginationBar
  paginationBar;` (prototype-scoped bean, same direct-injection pattern the four existing Views
  already use — no `SettingsPaginationBinding` registration, since that ties page size to a
  per-user grid setting in `UserSettingsDto` that has no feedback-list equivalent and isn't being
  added here; `PaginationBar`'s own default page size is used as-is).
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

## Related

- `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` — sibling bucket, for
  findings too large to size immediately rather than simply out of scope.
