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

### 2. `ContactRevealPanel`/`FeedbackPanel` (and provider-profile account-tab's own contact block) all render nested in the same flat card — no visual card-per-section separation (found during improvement-200 Phase 1 UI wiring, 2026-09-30)

`.overlay__view-card` (`advertisement-overlay.css`) is one flex column (`gap: 12px`) — every
child (title/description/chips/badge/`ContactRevealPanel`/`FeedbackPanel`/meta) renders as a flat
stack inside the same single white card, no border/background distinguishing one section from
another. `.contact-reveal-panel` has zero CSS rules of its own. The same pattern also exists in
`ProviderProfileViewModeHandler` (`AccountOverlay`'s "Provider Profile" tab) — its own
`buildContactViewsBlock(...)` is likewise just `card.add(...)`'d into the one shared card, not a
separate section.

**Wanted instead:** each section (contact, feedback, and any future similar block) as its own
genuinely separate sibling card — full `.overlay__view-card`-shaped treatment (own white
background, border, top accent, shadow, radius), stacked with a gap between them — not a nested
sub-box inside the description card. Applies symmetrically everywhere `ContactRevealPanel`/
`FeedbackPanel` (or `ProviderProfileViewModeHandler`'s own contact block) are used:
`ProviderProfileCatalogViewModeHandler`, `AdvertisementViewOverlayModeHandler`,
`ProviderProfileViewModeHandler`.

Not resolved inline: user explicitly deferred this until every phase of `improvement-200` itself
is implemented first, rather than context-switching into a UI/CSS restructuring pass mid-Phase-1.
A first implementation attempt (a nested `.overlay__view-subcard` sub-box, not the wanted separate
sibling card) was made and then explicitly reverted at the user's request before this entry was
filed — confirmed via `git diff` that the revert left only the legitimate `FeedbackPanel` wiring
diff behind, no leftover fragments.

### 3. Database ERD silently drops `<addColumn>` migrations (found during an architecture-map audit of `feedback`/`contact-spring-boot-starter`, 2026-10-01)

`contact-spring-boot-starter/src/main/resources/db/contact-changelog/changes/02-contact-view-revealed-value.xml` adds `contact_view.revealed_value` via `<addColumn>`. This column is completely absent from both the generated JSON and the rendered ERD diagram, for two compounding reasons: `DB_ERD_CHANGELOG_FILES`'s discovery filter in `docs/architecture/scripts/generate-architecture-model.sh` (lines 228-237) only includes files matching `<createTable` — a file containing only `<addColumn>` never enters the file list passed to the parser at all; and even if it were passed, `docs/architecture/scripts/liquibase-schema-to-json.js` has no `<addColumn>` handling whatsoever (only `createTable`, `addForeignKeyConstraint`, `createIndex`, `addPrimaryKey`, and a narrow raw-`<sql>` regex pass are implemented). Currently the only `<addColumn>` changelog in the repo, so the gap is isolated to one column today, but the mechanism is entirely missing — any future `addColumn` migration on any table hits the same gap.

Not resolved inline: found during a read-only architecture-map audit, not an implementation task with its own approved scope to fix generator code in.

### 4. `minio/minio:latest` — MinIO Community Edition is archived upstream, no more security patches (found during a general infra check, 2026-10-01)

`scripts/deploy-and-run/docker-compose.minio.yml:20` and `scripts/deploy-and-run/run.sh:196,214` both pin `minio/minio:latest`. MinIO's Community Edition GitHub repo was archived (locked, read-only) on 2026-04-25 after a maintenance wind-down (license changed Apache 2.0 → AGPLv3 in 2025-05, Admin GUI removed 2025-02, Docker Hub publishing stopped 2025-10) — the vendor is steering users to the paid AIStor product instead; confirmed directly via web search, not just the external claim that prompted this (see [TuxCare](https://tuxcare.com/blog/minio-els/), [itsfoss](https://itsfoss.com/news/minio-moves-away-from-open-source/)). No further upstream security fixes will land on the community codebase ever again. Garage (Deuxfleurs) is the most-commonly-recommended lightweight S3-compatible replacement (actively maintained, v2.3.0 released 2026-04-16, same S3 API so `attachment-spring-boot-starter`'s `StorageService` wouldn't need code changes, just a different endpoint) — but it's AGPLv3-licensed and lacks bucket versioning/lifecycle policies/erasure coding, which should be checked against this project's actual usage before committing to the swap.

Not resolved inline: an infra/storage-backend swap needs its own sizing and isn't something to fold into unrelated implementation work; not urgent (the already-pulled image keeps working, this is a forward-looking risk, not an active break) but shouldn't be forgotten either.

## Related

- `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` — sibling bucket, for
  findings too large to size immediately rather than simply out of scope.
