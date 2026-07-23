# Backlog Archive — completed work history

Chronological record of everything resolved from [BACKLOG.md](BACKLOG.md). Each entry documents
what shipped, how it was verified, and links to the corresponding file in `completed/issues/` (or
a legacy `completed/<name>/` folder for pre-issue-convention work — see
[Maintenance rules](BACKLOG.md#maintenance-rules)). This file is never reordered — entries stay in
the order they were done, oldest first within each wave.

---

## Week 0 — quick wins

✅ Done (2026-07-04): toast position (improvement-012, commit 0f02b91d), header email overflow
(improvement-009, commit 0f02b91d), unused vaadin-core dependency (improvement-016, commit
0f02b91d), virtual threads (commit 0f02b91d), DelegatingPasswordEncoder (commit 0f02b91d),
owasp-sanitizer bump 20240325.1→20260313.1 (commit a9ed6d7e) — all verified with a full
reactor build, `deploy-dev.sh`, and a green 46/46 e2e run each time. Resolved issues moved to
`completed/issues/`.

Also fixed this session, not originally tracked as Week-0 items: a Quill false-dirty-state bug
and 3-layer description length validation (commit b7d64cc2 — closes
`completed/issues/issue-description-length-tag-spam.md`, unblocks improvement-006; see
`marketplace-app/DECISIONS.md` ADR-021 update and ADR-024).

✅ Done (2026-07-10): buildx + BuildKit cache mounts (Part 1/3 of process-improvements, commit
8f29a12f) — installed `docker buildx`, added `--progress=plain` streaming to `deploy.sh`, and
`--mount=type=cache` for `/root/.m2` and `/root/.vaadin` in the Dockerfile build stage. Cuts
the Maven/Vaadin build step from ~182s to ~145s on a `--no-cache` rebuild. A `node_modules`
cache mount was tried and reverted — `vaadin-maven-plugin` rm-rf's and recreates that directory
every build, incompatible with a fixed cache mountpoint.

**Week 0 is now complete.**

## Wave 1 — prerequisites for public shareability

✅ Done (2026-07-04): improvement-005 — plain-text card excerpt (Jsoup `.text()` instead of raw
`innerHTML`) + sanitizer allowlist merge (`<pre>` added, `mailto:`/extra tags kept as accepted
divergence). Moved to `completed/issues/`. Updated an outdated Playwright assertion
(`e2e/_flows/advertisement.flow.js`, card step) that expected rich HTML tags in the card —
full e2e 46/46 green.

✅ Also done: improvement-017 step 1 — upload size cap lowered `500 MB → 50 MB`
(`AttachmentUploadButton.java:9`), sized for realistic ad photos/short demo videos. Issue file
stays open (not moved) — step 2 (real async pipeline) remains deferred, see `BACKLOG.md`'s
Deferred section. Full e2e 46/46 green.

✅ Done (2026-07-07): improvement-020 — security baseline. Deny-by-default
(`anyRequest().denyAll()`) was implemented, deployed, and broke the whole app (0/46 e2e — root
Vaadin route never rendered under a real browser hit, only `curl` was tested first). Reverted
to `anyRequest().permitAll()`; see `marketplace-app/DECISIONS.md` ADR-025 for why deny-by-default
doesn't apply to this app's single-route Vaadin SPA model, and the resulting process rule for
future REST controllers. Rate limiting (Caffeine, `AuthService.login()` /
`UserService.register()`) implemented, then corrected to count only real failures — not
successes — after it broke bulk e2e signups from a shared IP (see ADR-026). Moved to
`completed/issues/`. Full e2e suite 47/47 green (47, not 46 — new `rateLimitUser` test added to
spec 02).

✅ Done (2026-07-11): improvement-007 — `TaxonPort.findByIds()` bulk lookup (kills the N+1 in
`DefaultTaxonPort.resolveDtos()`/`buildDtoIndex()`) + `AttachmentSnapshotService
.captureAndGetId()`. Bundled with improvement-004 — `PaginationSqlBuilder` extracted to
query-lib, `deleted_by` added to `taxon` (edited directly into `001-taxon.xml` since the DB
isn't in production yet, not a new migration). Both moved to `completed/issues/`. Editing an
already-applied changeset required a full `deploy.sh --reset` (Liquibase checksum mismatch
otherwise). Full e2e suite 47/47 green.

✅ Done (2026-07-11): improvement-022 — registration rate limiter shared-bucket risk (found
2026-07-10 by external audit). `server.forward-headers-strategy: framework` added to
`application-prod.yml` so `request.getRemoteAddr()` resolves the real client IP behind Render's
proxy instead of Render's own edge address. See `marketplace-app/DECISIONS.md` ADR-027 (also
records why a coarser global backstop limiter was considered and dropped — registration
failures have no natural per-target key to count against, unlike login). Moved to
`completed/issues/`. Full e2e suite 47/47 green. Note: whether Render actually forwards
`X-Forwarded-For` isn't verifiable from this dev environment — worth confirming once actually
deployed.

**Wave 1 is now fully complete.**

## Wave 2 — quality hardening before public traffic

✅ Done (2026-07-13): improvement-013 — raw camelCase field names in Activity diffs
(`nameEn:`, `categoryIds:`, `adsPageSize:`) while Timeline showed humanized labels for USER
rows. The `*ActivityFieldsHookImpl` label mappings were already complete — the gap was that
`AuditTimelineRowRenderer.buildEntityChangesDiv()` never called `labelHook.labelFor()`. Fixed by
threading the resolved `AuditActivityFieldsHook` through both call sites (Timeline enrich-hook
branch, overlay Activity-tab overload); see `marketplace-app/DECISIONS.md` ADR-030. Moved to
`completed/issues/`. Updated one Playwright assertion from a raw-field-tolerant regex to the
actual humanized label. Full e2e suite 48/48 green.

**Wave 2 is now fully complete except for independent, unblocked items — see `BACKLOG.md`'s
Wave 2 "Still open, no longer blocked" / "Migrated" tables.**

✅ Done (2026-07-04): tag-spam validator + 3-layer Jsoup-based length validation
(`issue-description-length-tag-spam` → moved to `completed/issues/`), alongside a fix for a
Quill false-dirty-state bug (edit form showed Save/Discard as active on open for rich-text
descriptions — not separately tracked as an issue, fixed directly; see
`marketplace-app/DECISIONS.md` ADR-021 update). Full e2e 46/46 green.

✅ Done (2026-07-11): improvement-018 — `SettingsPaginationService` cross-session settings
bleed (real multi-user bug: user X's page size change was silently applied to every other
logged-in user's live grid) + UI-reference leak risk (cleanup relied solely on `@PreDestroy`).
Fixed by adding `userId` ownership to `BindingEntry` and a `bar.addDetachListener(...)` safety
net; see `marketplace-app/DECISIONS.md` ADR-028. Moved to `completed/issues/`. Extended the
existing page-size Playwright test with a second-session bleed check instead of a new spec
file. Full e2e suite 47/47 green.

✅ Done (2026-07-13): improvement-015 — optimistic locking. `version BIGINT` added to
`advertisement`, `user_information`, `taxon` (edited directly into existing changesets, DB not
yet in production); `@Version` on all three entities, with a manual guard for `User` (its real
edit path bypasses `CrudRepository`) and for `softDelete` on `Advertisement`/`Taxon`. UI shows a
dedicated conflict notification, no auto-reload (see `marketplace-app/DECISIONS.md` ADR-029,
`platform-commons/DECISIONS.md` ADR-019). Moved to `completed/issues/`. New Playwright test:
two-session concurrent edit, stale save shows conflict instead of silently overwriting. Full e2e
suite green.

✅ Done (2026-07-14): improvement-024 — `User` profile edits moved onto native
`CrudRepository.save()`, matching Advertisement/Taxon. Instead of mirroring
`AdvertisementService.buildEntity()`'s "rebuild via Builder, forward every unedited field from
`before`" pattern, introduced a second, narrower entity `UserProfileUpdate`
(`id`/`name`/`role`/`updatedAt`/`version`, no `email`/`passwordHash`) mapped to the same
`user_information` table via its own `UserProfileCrudRepository` — the sensitive-field-overwrite
risk this refactor would otherwise carry is closed at the type level (those fields aren't mapped
properties, so the generated `UPDATE` can't reference them), not by builder discipline. See
`marketplace-app/DECISIONS.md` ADR-029 update. Moved to `completed/issues/`.

✅ Done (2026-07-14): improvement-041 — `AdvertisementRepository`'s raw SQL
`LEFT JOIN user_information` removed (both `findAdvertisementById()` and `findByFilter()`),
replaced with `UserPort.findByIds()` bulk lookup + `AdvertisementService.enrichWithActorInfo()`
(mirrors `enrichWithCategories()`). Three dead `ORDER BY` sort-alias entries
(`created_by_user_id`/`_name`/`_email`) removed alongside the join — confirmed dead,
`AdvertisementSortMeta` never exposed sort-by-author. Actor-reference columns renamed to match
Taxon's convention: `created_by_user_id`→`created_by`, `last_modified_by_user_id`→`updated_by`,
`deleted_by_user_id`→`deleted_by` (`01-advertisement-schema.xml` edited directly, `deploy.sh
--reset` required). See `marketplace-app/DECISIONS.md` ADR-034 (also records the sort-by-author
escape hatch for the future: denormalize via a hook, like `media_url`, never rejoin or sort
in-memory post-pagination). Moved to `completed/issues/`.

✅ Done (2026-07-14): improvement-042 — `advertisement.media_url`/`media_content_type`/
`media_count` denormalized columns removed. New `AttachmentPort.getMediaSummaries()` bulk lookup
(`AttachmentRepository.loadMediaStats(EntityType, Set<Long>)`, Postgres `ROW_NUMBER() OVER
(PARTITION BY entity_id ...)`) + `AdvertisementService.enrichWithMediaSummary()` (mirrors
`enrichWithCategories()`/`enrichWithActorInfo()`). The write-triggered sync path was deleted
entirely, not just emptied: `MediaChangeHookImpl`, `AdvertisementService.onMediaChanged()`, and
`AdvertisementPort.onMediaChanged()` (confirmed unused by marketplace-app) are all gone —
`AttachmentMediaChangeHook` still fires from `AttachmentService` but now has zero listeners, a
valid gracefully-degraded state. Three dead media sort-aliases removed alongside the columns. See
`marketplace-app/DECISIONS.md` ADR-035. Moved to `completed/issues/`.

✅ Done (2026-07-14): improvement-043 — `OrderByBuilder.build()` no longer snake-cases the
incoming `Sort.Order` property before lookup; every repository's alias map is now keyed by the
relevant DTO/entity's `Fields.*` constants (`AdvertisementInfoDto`, `UserDto`, `Taxon`,
`AuditTimelineItemDto` — a fourth repository, `AuditLogRepository`, was found during
implementation and missed by the original scope check). Found and fixed a real, pre-existing
instance of the exact bug this issue
warns about while re-keying: `TaxonRepository.SORT_ALIASES` had `"createdAt"`/`"updatedAt"` keys
in camelCase (not snake_case like Advertisement/User), which never matched the snake-cased lookup
— silently dead, harmless only because `DefaultTaxonPort` always hardcodes `Sort.by("id")` and
never lets a caller choose. No SQL/behavior change elsewhere. Moved to `completed/issues/`.

✅ Done (2026-07-15): [improvement-051](completed/issues/improvement-051-parallel-test-suite-orchestration.md)
— `scripts/run-all-tests.sh`: `unit-tests.sh` → `integration-tests.sh` sequential (both can race on
the same starter modules' `target/` dirs), `playwright.sh` parallel from the start (no Maven
reactor overlap); `/run-all-tests` slash command added. End-to-end run confirmed both the
sequencing and failure-detection paths. Committed in `a699a990`; issue file moved to
`completed/issues/` afterward (bookkeeping only, no code change).

✅ Done (2026-07-15): [improvement-054](completed/issues/improvement-054-unbounded-in-clause-taxon-assignment-attachment.md)
— `TaxonAssignmentRepository.findAllByEntities()` and `AttachmentRepository.deleteByUrls()` both
switched from `IN (:set)` to `= ANY(:array)`, reusing the array-bind fix improvement-050 item 2
already proved (ADR-036) — no caller-side changes needed. `TaxonAssignmentRepositoryTest` 8/8,
`AttachmentRepositoryTest` 8/8, full `integration-tests` suite 83/83.

✅ Done (2026-07-15): [improvement-045](completed/issues/improvement-045-critical-test-coverage-gaps.md)
— all 8 critical untested code paths covered: `AccessEvaluatorTest` (17/17) +
`AuthServiceTest`/`UserServiceTest` (5/5 each) + `UserRepositoryTest` (3/3) + `TaxonRepository`
soft-delete SQL fix + `TaxonPortTranslationFallbackTest` (4/4) + `UserServiceRestoreTest` (2/2 —
tested via public `UserService.restoreToSnapshot()`, see `integration-tests/DECISIONS.md` ADR-008,
not the private `applyUserRestore()`) + `SettingsSnapshotDtoTest` (6/6).

✅ Done (2026-07-15): [improvement-049](completed/issues/improvement-049-taxon-attachment-incomplete-rollback-bugs.md)
— all 4 real bugs fixed and TDD-verified: `TaxonService.update()` now forwards `deletedBy`
(`TaxonServiceTest` 2/2); `AttachmentService.commitTempUploadsQuiet()`'s `storageService.move()`
moved inside the `try` so mid-batch failures clean up already-moved files (`AttachmentServiceTest`
2/2, plain Mockito, no Spring); `AttachmentService.upload()` made `@Transactional` so a
post-commit audit-capture failure rolls back the DB row too (`AttachmentServiceTransactionTest`
2/2, real Testcontainers + `@MockitoBean` for S3/audit); `AttachmentCleanupService.deleteAttachments()`
now deletes DB rows before S3 objects, with `@Transactional` removed from `cleanup()` so the DB
delete actually commits before the S3 loop runs, not just textually reordered
(`AttachmentCleanupServiceTest` 2/2, `InOrder`-verified). Full `integration-tests` suite: 49/49,
twice consecutively.

✅ Done (2026-07-15): [improvement-050](completed/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md)
— all 5 findings resolved: item 1 extracted to
[improvement-052](issues/improvement-052-first-admin-registration-toctou-race.md) (deliberately
deferred, accepted risk); item 2 fixed via `= ANY()` array binding instead of `IN (:set)` —
removes the parameter-count risk without the real-data-volume answer or a JOIN-based rewrite that
would have reversed ADR-034 (`AdvertisementRepositoryTest` 9/9, see `marketplace-app/DECISIONS.md`
ADR-036); item 3 fixed via `Locale.getLanguage()` instead of `.toLanguageTag()`
(`TaxonPortTranslationFallbackTest` 5/5); item 4 fixed via an `id` tiebreaker on both
`AuditLogRepository` version-numbering subqueries (new `AuditLogRepositoryTest` 2/2, first
improvement-027 Batch-3 test); item 5's Liquibase default updated after confirming via
`UserSettingsDtoTest` (2/2) it wasn't a live bug. Full `integration-tests` suite: 56/56, twice
consecutively.

✅ Done (2026-07-15): [improvement-027](completed/issues/improvement-027-unit-testcontainers-test-layer.md)
— Batches 0-3 all complete, the `integration-tests` module's original scope fully delivered.
Batch 2 (plain unit tests): `TaxonSnapshotDto.diff()` (7/7) and `AdvertisementService
.sanitizeHtml()` (`AdvertisementServiceHtmlSanitizationTest` 4/4, tested through the real public
`save()` entry point per ADR-008) were the last two pure-logic candidates. Batch 3 (Testcontainers
repository tests): `TaxonAssignmentRepositoryTest` (8/8 — idempotent `assign()`, both directions
of bulk lookup, both count variants) and `AttachmentRepositoryTest` (8/8 — soft-delete visibility,
the two-step restore-to-urls flow, retention-based cleanup selection, both `loadMediaStats()`
overloads including the `ROW_NUMBER()` bulk one) were the last two repositories.
`AuditLogRepository` had already landed via improvement-050 item 4. **New finding, not yet
fixed:** `TaxonAssignmentRepository.findAllByEntities()` and `AttachmentRepository.deleteByUrls()`
both still have the same unbounded `IN (:set)` shape improvement-050 item 2 already fixed once for
`AdvertisementRepository` — flagged in the issue, not fixed as part of this batch (test-coverage
scope, not a second performance pass). Full `integration-tests` suite: 83/83, twice consecutively.
(Fixed the same day — see [improvement-054](completed/issues/improvement-054-unbounded-in-clause-taxon-assignment-attachment.md).)

✅ Done (2026-07-13): improvement-011 — UI components hard-injecting starter ports
(`AttachmentGalleryService`, `AttachmentGallery`, `AuditActivityPanel`). The consolidated
"Option C" (`@ConditionalOnBean` on the component classes) was tried first and **empirically
broke the app** (48/48 → 8/48) due to a Spring Boot bean-registration-ordering issue — reverted.
Fixed instead with plain `ComponentFactory<Port>` wrapping (Option A) plus moving the
availability gate at six call sites from the wrapping UI factory to the port's own factory; two
pre-existing instances of the same wrong-level gate were found and fixed in
`TaxonFormOverlayModeHandler`/`UserFormOverlayModeHandler` along the way. See
`marketplace-app/DECISIONS.md` ADR-033. Moved to `completed/issues/`. Full e2e suite 48/48 green.

✅ Done (2026-07-13): improvement-023 — `RequestCorrelationFilter` (MDC `requestId`, 8-char
console pattern) + closed silent-logging gaps found during the review: `TaxonService`,
`AuthService` (login/logout — a real security-observability gap), `AttachmentService`,
`TaxonAssignmentService`, `AttachmentSnapshotService`, `UserSettingsService`,
`AdvertisementSaveService`, both cleanup services (now log deleted-row counts, not just "ran"),
and `LoginDialog`'s missing catch-all exception log. See `marketplace-app/DECISIONS.md`
ADR-032. Moved to `completed/issues/`. Verified via `docker logs` — distinct requestId per
request. Full e2e suite 48/48 green.

✅ Done (2026-07-13): improvement-006 — `QuillEditor` character counter ("N / 2000", reads
`quill.getText()`) + `advertisement.description` DB column widened from unbounded `TEXT` to
`VARCHAR(20000)` — **not** `VARCHAR(2000)` as the issue originally suggested; the column stores
raw HTML including formatting tags, and 20000 is the already-established raw-size cap
(`DESCRIPTION_RAW_MAX_LENGTH`, ADR-024), not the 2000 visible-text limit. Capping at 2000 would
have rejected legitimately-formatted descriptions. See `marketplace-app/DECISIONS.md` ADR-031.
Moved to `completed/issues/`. Counter visually confirmed via Playwright screenshot. Full e2e
suite 48/48 green.

✅ Done (2026-07-16): [improvement-026](issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md)
— raw `new Button(...)` spots converted to `Ui*Button` wrappers across 4 phased batches: Batch 1
`HeaderBar` (4 auth buttons → `UiPrimaryButton`, CSS classes preserved exactly for Playwright's
login-check selectors), Batch 2 `PaginationBar` (4 nav buttons → `UiIconButton`), Batch 3
attachment lightboxes/gallery (`AttachmentLightbox`, `CardLightboxViewer`, `AttachmentThumbnail`
promoted from plain/partial-Spring classes to full `@SpringComponent` beans so each injects its
own `UiComponentFactory<UiIconButton>`, matching the codebase-wide rule that every `Ui*Button`
consumer is itself a bean), Batch 4 `AuditActivityRowRenderer`'s restore button (→
`UiTertiaryButton` + `LUMO_SMALL` layered on top) and `UserPickerField`'s clear/open buttons (→
`UiIconButton`, two new tooltip keys). Full e2e 48/48 green after every batch. A Batch 4
regression was found and fixed during verification: giving `UserPickerField`'s clear button a real
tooltip for the first time broke `e2e/_flows/filter.flow.js`'s shared `clearFilter()`/
`applyFilter()` helpers (selector collision inside `TimelineQueryBlock`) — fixed by scoping both
helpers to `.query-action-block`. `NotificationService`'s close-button decision extracted to
[improvement-057](issues/improvement-057-notificationservice-close-button-decision.md);
`UserPickerField`'s inline search-button gap plus an unrelated pagination-correctness bug
extracted to
[improvement-056](issues/improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md).
See `marketplace-app/DECISIONS.md` ADR-037.

✅ Done (2026-07-16): [improvement-037](issues/improvement-037-accessibility-contrast-and-aria.md)
— WCAG AA contrast failure fixed via full theme-CSS tokenization (49 unique hex colors / ~180
occurrences across 21 files named as `--app-*` custom properties in `styles.css`'s `:root`,
bigger scope than originally requested but done together with improvement-039's identical
prerequisite per both issues' own suggestion — see `marketplace-app/DECISIONS.md` ADR-038). The
failing `.header-auth-row` color `#94a3b8` (~2.5:1) was merged into the already-compliant
`#64748b` (~4.76:1) as one shared `--app-text-muted` token — the only value intentionally
changed, every other token preserves its prior color exactly. Also added: `aria-label` on
`UiIconButton` (fixes every icon-only button app-wide in one shared-component edit), `role="list"`/
`"listitem"` + `aria-label` on the category chip list, and a `.primary-button/.tertiary-button
/.icon-button:focus-visible` rule matching the pre-existing `.advertisement-card:focus-visible`
treatment. Verified twice with full `deploy.sh` + `bash scripts/playwright.sh e2e --full --ux`
(48/48 both times) plus a direct browser check confirming `--app-text-muted` resolves to the
compliant `#64748b`. improvement-039 (dark mode) is now unblocked at the infrastructure level —
its own prerequisite shipped here, only the actual dark palette + toggle remain.

✅ Done (2026-07-16): [improvement-031](issues/improvement-031-maven-enforcer-plugin.md) — Maven
Enforcer added to root `pom.xml` (`dependencyConvergence`, `requireJavaVersion [25,)`,
`requireMavenVersion [3.9,)`, active for every module via inheritance) plus a `bannedDependencies`
starter-to-starter ban activated individually in each of the 5 starter poms (not at the root,
since marketplace-app/integration-tests legitimately depend on starters). Turning the rules on
immediately found two real, previously-invisible problems: `advertisement-spring-boot-starter`
had vestigial `<optional>true</optional>` Maven dependencies on `audit-`/
`attachment-spring-boot-starter` with zero actual Java usage (removed), and `dependencyConvergence`
caught a genuine `commons-text` version conflict via `liquibase-core`'s two dependency paths
(1.15.0 direct vs. 1.13.1 via opencsv — pinned to 1.15.0). Verified via full `deploy.sh --no-cache`
+ `bash scripts/playwright.sh e2e --full --ux`, 48/48. See `marketplace-app/DECISIONS.md` ADR-039.

✅ Done (2026-07-16): [improvement-059](issues/improvement-059-local-isolated-parameterized-ci-runner.md)
— local, isolated, parameterized CI runner: one `scripts/ci/Dockerfile` container
(Docker-outside-of-Docker — host's `docker.sock` mounted, `--network host`), run via
`scripts/ci.sh --unit/--integration/--e2e/--sonar/--all`. Isolated e2e stack reuses the
existing `deploy.sh`/`playwright/run.sh` unchanged, now made parameterizable via env-var overrides
(`ci-*` container/network/volume names, ports 15432/19000/19001/18081) rather than a new compose
file — no e2e logic duplicated. `ci-m2-cache` named volume caches Maven deps across runs; reports
collected into `ci-reports/<timestamp>/` via `docker cp`. DinD was considered and rejected in favor
of DooD (matches how GitHub Actions' own `services:` model works, keeping the migration path to
improvement-028 clean) — see `scripts/ci/DECISIONS.md` ADR-001. Verified each stage standalone:
`--unit` 22/22, `--integration --sandbox` 83/83 (including the highest-risk DooD-inside-DooD
Testcontainers path), `--e2e` 35/48 matching the non-containerized baseline exactly. Surfaced and
fixed three real, pre-existing bugs along the way: an Enforcer `dependencyConvergence` conflict in
`integration-tests` (nothing had run `mvn -pl integration-tests test` since improvement-031 —
fixed by bumping `liquibase-core` to 5.0.3 and pinning `commons-io` to 2.22.0 to match, after
confirming `testcontainers` is already on its latest release so the pin can't be avoided by
upgrading either side alone); `playwright/run.sh` never forwarding `APP_URL` into the `pw-runner`
container's actual environment (invisible in normal dev use since its default already matched
`playwright.config.js`'s own fallback); and `deploy.sh`'s unconditional `docker container prune -f`
/`docker volume prune -f` acting host-wide — confirmed directly to delete the dev
`marketplace-app`/`pw-runner`/`sonarqube` containers outright when they happened to be stopped
during a `scripts/ci.sh` run (data survived in untouched named volumes, containers didn't) — fixed by
moving both behind a new, opt-in `deploy.sh --prune-all` flag rather than dropping the capability.
improvement-028 (GitHub Actions) is now unblocked. See `scripts/ci/DECISIONS.md` ADR-001.

✅ Done (2026-07-16): [improvement-032](issues/improvement-032-sonarqube-quality-gate-blocking.md)
— `scripts/sonar/run.sh` now passes `-Dsonar.qualitygate.wait=true` by default (script exits
non-zero if the gate is `ERROR`), with `--no-gate` restoring the old informational-only behavior.
`scripts/ci.sh`'s `sonar` stage takes the default. Turning this on surfaced a real bug that would
have silently defeated it even with the flag added: the scanner was piped through `tee`, so
`EXIT_CODE=$?` was reading `tee`'s exit status (always 0), never the scanner's — fixed by reading
`${PIPESTATUS[0]}`, bracketed with `set +e`/`set -e` (not a trailing `|| true`, which would itself
have clobbered `PIPESTATUS`) so `set -e` doesn't abort before the HTML report gets generated on a
gate failure. Verified directly both ways: default mode correctly exits `3` on a real gate failure
(35 pre-existing issues in this codebase) with a clear message and a report; `--no-gate` reports
`EXECUTION SUCCESS` regardless. As of this fix, `scripts/ci.sh`'s default run reports its `sonar`
stage as `FAILED` until those 35 issues are addressed or the gate reconfigured — intended, not a
bug. See `scripts/sonar/DECISIONS.md` (2026-07-16 entry).

✅ Done (2026-07-16): [improvement-034](issues/improvement-034-feature-workflow-standardization.md)
— `/feature <title>` skill (`.claude/commands/feature.md`) scaffolds a new
`backlog/issues/<prefix>-NNN-<slug>.md` from the shape already in consistent use across this
backlog (auto-numbered across both `backlog/issues/` and `backlog/completed/issues/`, filled from
conversation context, reading source first when needed rather than leaving placeholders), and
inserts a ranked row into `BACKLOG.md`'s priority table in the same operation — enforcing the
`.claude/rules.md` "Issue Lifecycle" rule automatically. Corrected from the original wording along
the way: the issue originally proposed a `backlog/<name>/SPEC.md`-per-directory template, citing
`backlog/entity-extensions/SPEC.md` as an example — confirmed neither that file nor any other
`SPEC.md` exists anywhere in the repo anymore; retargeted to formalize the `backlog/issues/`
one-file-per-issue shape that actually won out in practice instead.

✅ Done (2026-07-16): [improvement-030](issues/improvement-030-archunit-test-module.md) — ArchUnit
(`com.tngtech.archunit:archunit-junit5:1.4.2`) added to `marketplace-app`'s existing test tree
(`src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`), not a new module —
`marketplace-app` already depends on every starter + `platform-commons` + `query-lib`, so its test
classpath sees everything these rules need with zero new module/dependency wiring, and the checks
run automatically via the existing `scripts/unit-tests.sh`/`scripts/ci.sh --unit` stage. All 7
prose rules from the original issue codified (Port/Hook split into two `@ArchTest` rules rather
than one combined `.or()` rule): UI-must-not-call-repositories, no-Vaadin-in-starters,
Ports/Hooks-live-only-in-platform-commons, no-class-level-`@PreAuthorize`-on-services,
no-`Optional`-method-parameters (custom `ArchCondition`, no ArchUnit built-in for this),
no-`configuration`-packages, `*PortImpl`/`*HookImpl`-delegation-only. All 8 `@ArchTest` fields
passed cleanly on first run (codebase already followed these rules by discipline) — verified via
`bash scripts/unit-tests.sh ArchitectureRulesTest` (8/8) and a full `bash scripts/unit-tests.sh`
run (all suites still green). The delegation-only rule needed no explicit exception list for
`DefaultTaxonPort`/`DefaultAuditPort`/`DefaultAttachmentPort` (documented coordination-layer
exceptions) — it only targets the `*PortImpl`/`*HookImpl` suffix, which none of those three match,
so the existing `Default*Port` vs. `*PortImpl` naming convention already draws the needed line for
free. See `marketplace-app/DECISIONS.md` ADR-041. Note: improvement-010 (a view deviating from the
`refresh()` pattern) is not one of the 7 codified rules (a behavioral convention, not a
dependency-direction rule ArchUnit expresses cleanly) — still open, needs its own fix. Also
unblocked improvement-033 (`/quality-gate` skill), whose three prerequisites (027/030/032) are now
all done.

✅ Done (2026-07-17): [improvement-056](issues/improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md) —
`UserPickerField`'s `CallbackDataProvider` offset→page pagination bug fixed via a new
`OffsetPageable` (`query-lib`), a `Pageable` carrying a raw offset directly, plus a new
`UserPort.getFilteredByOffset()` method — the repository's SQL needed no changes at all, since it
already used `pageable.getOffset()` correctly; only the `Pageable` it received was wrong. Also
closed the companion gap: `UiIconButton` gained an `inline` variant (`LUMO_TERTIARY_INLINE`) so the
picker's search button no longer needs a raw `Button`. The bug had never triggered in Playwright
because the seed spec's 50 users exactly matched Vaadin `Grid`'s default page size (always one
aligned fetch) — `05-seed-filter-sort-pagination.spec.js`'s `SEED_COUNT` bumped 50→60 and the
timeline actor-filter test retargeted to a user past the first page, with a grid-scroll step added
to `fillActorPicker`, specifically to exercise the previously-buggy path. Verified via full
`bash scripts/playwright.sh e2e --full --ux`, 48/48 passed. See `marketplace-app/DECISIONS.md`
ADR-042.

✅ Closed, not fixed (2026-07-17): [improvement-074](issues/improvement-074-mockito-self-attach-dynamic-agent-slow-first-test.md) —
investigation into the ~40-90s delay on whichever test runs first in each Maven test JVM fork. The
original diagnosis (Mockito's dynamic self-attach) was disproven: configuring Mockito as a real
`-javaagent` removed the self-attach warning but not the delay. JFR profiling
(`jdk.ExecutionSample`/`jdk.NativeMethodSample`) found the actual cost is JUnit Platform's own
`ServiceLoader`-based classpath scan at `LauncherFactory.openSession()` — unrelated to Mockito.
Two further fixes tested and also ruled out: disabling JUnit's launcher interceptors
(`-Djunit.platform.launcher.interceptors.enabled=false`, no change) and relocating `~/.m2` off
this sandbox's 9p-mounted Windows drive to a native path (measurably sped up every *other* reactor
module's build 2-4x — a real, separate finding worth doing manually if wanted — but left this
specific delay unchanged at ~43s). Root cause of the JUnit-launcher-session classpath scan itself
remains unidentified; all experimental changes reverted, nothing applied to the repo or
environment. Closed as investigated-not-fixed rather than left open against a disproven diagnosis.

✅ Done (2026-07-17): [improvement-058](issues/improvement-058-taxon-assignment-audit-trail-missing.md) —
the Timeline tab (global activity feed) showed raw taxon ids instead of resolved category names in
audit diffs, while the per-advertisement Activity tab already showed names correctly for the same
data. Original framing (based on ADR-019's "must be audited" text) overstated the gap as "taxon
assignments not audited at all" — direct code tracing found every category change is already
captured via the advertisement's own audit snapshot (`AdvertisementSnapshotDto.categoryIds`); the
real, narrower bug was a raw-id-vs-name display inconsistency between the two rendering paths.
Root cause: `AuditTimelineItemDto` carried only the current snapshot, not the previous one
(unlike `AuditActivityItemDto`), so `AdvertisementEnrichService` couldn't resolve the "before" side
of a category diff for the Timeline path. Fixed by adding `prevSnapshotData` to
`AuditTimelineItemDto` (populated from `AuditLogProjection.prevSnapshot()`, already available,
previously unused) — both rendering paths now share one fully-typed `resolveCategories()` helper,
switched to `TaxonPort.findByIds()` (targeted batch lookup) instead of `listAllByType()` (scan-all).
Consolidated the one unavoidable `instanceof ChangeEntry.FieldChange` check into a single default
method, `ChangeEntry.replaceIfField()`. `TaxonAuditHook` (SPI) removed entirely rather than
implemented — zero implementations existed, and both call sites already sit inside an advertisement
save/delete producing its own audit snapshot; also removed `TaxonPort.assign()`/`unassign()`/
`findByCode()` and `TaxonRepository.findByTypeAndCode()` (zero callers, confirmed by direct trace),
along with the improvement-045 regression tests the latter had (a deliberate trade — clean removal
over a safety net for an already-unreachable method). Documentation corrected across `CLAUDE.md`,
`docs/architecture/`, and `DECISIONS.md` files (marketplace-app ADR-043, platform-commons ADR-017
note, taxon-spring-boot-starter ADR-004 marked Superseded). Verified via full
`bash scripts/unit-tests.sh` (30/30), `integration-tests` `Taxon*` suite (24/24), and full e2e
Playwright suite (48/48) — including a new `changesText: 'Vehicles'` assertion in
`04-marketplace-advertisement-flow.spec.js` proving the Timeline row now shows the resolved
category name, not a raw id.

✅ Done (2026-07-17): [improvement-066](issues/improvement-066-usersettingsrepository-missing-version-check.md) —
`UserSettingsRepository.save()` had no optimistic-locking version check at all, unlike every other
mutable entity in this codebase (ADR-029) — two browser tabs of the same user editing settings
could silently clobber each other, last-write-wins on the whole JSONB blob, no conflict signal.
Fixed by embedding the version **inside** the `settings` JSONB column itself
(`UserSettingsDto.version`) rather than adding a new SQL column or reusing
`user_information`'s shared `version` (would have spuriously coupled a settings save to an
unrelated profile-name edit in another tab) — `save()`'s `UPDATE` now checks
`(settings->>'version')::bigint = :expectedVersion`, throwing `OptimisticLockingFailureException`
on a mismatch. UI (`SettingsEditDto`, `SettingsFormModeHandler`) threads `version` through every
lifecycle path (`activate`, `save`, `discardChanges`, `handleRestoreFromActivity`, `loadRestored`)
per the same discipline ADR-029 already requires elsewhere. Schema default updated to include
`"version":0` so fresh users don't start with a missing key; the live dev DB's column default was
fixed directly via `ALTER TABLE` rather than a new Liquibase changeset, since the app is not yet in
production (a real changeset is still required before any production deploy — editing an
already-applied changeset's `defaultValue` has no retroactive effect). Documented in
`marketplace-app/DECISIONS.md` ADR-044 and `user-spring-boot-starter/CLAUDE.md`. Verified via new
`integration-tests/.../user/UserSettingsRepositoryTest` (3/3, real Postgres) and a full e2e
Playwright regression run (48/48 — no new Playwright assertions added, per explicit direction that
dry-test coverage was sufficient for this fix).

✅ Done (2026-07-17): [improvement-048](issues/improvement-048-service-layer-test-coverage.md) —
`marketplace-app`'s non-UI service layer (`org.ost.marketplace.services.*`, zero `com.vaadin.*`
imports) had no dedicated test tree, unlike the precedent already set by improvement-045's
`AccessEvaluatorTest`. Added `services/advertisement/AdvertisementSaveServiceTest` (5 tests: create
vs update capture, the `attachmentSnapshotId` fallback in both directions, graceful completion with
optional ports absent), `services/advertisement/AdvertisementEnrichServiceTest` (9 tests:
`mergeMediaChanges()`/`enrichActivityItems()` media-hook merge and no-op paths, category-name
resolution with `TaxonPort` present/absent, non-`ADVERTISEMENT` passthrough,
`getMediaStateForSnapshot()`), and `services/auth/AuthContextServiceTest` (5 tests: authenticated,
unauthenticated, non-`AuthenticatedPrincipal` principal, exception-swallow paths via direct
`SecurityContextHolder` set/clear). Re-verified target classes' current shape before writing tests
since `AdvertisementEnrichService` had changed since the issue was filed (ADR-043's
`ChangeEntry.replaceIfField()`/`prevSnapshotData` refactor postdates it). `ComponentFactory<T>`
mocked directly (a plain non-final class); Mockito's default-empty-values behavior already returns
`Optional.empty()`/no-ops for unstubbed `findIfAvailable()`/`ifAvailable()`, matching the
"optional starter absent" shape with zero extra stubbing. Verified via
`bash scripts/unit-tests.sh marketplace-app` — BUILD SUCCESS, all 19 new tests green, plus
`ArchitectureRulesTest` (8/8) confirming no ArchUnit violations.

✅ Done (2026-07-17): [improvement-047](issues/improvement-047-integration-tests-ci-safety.md) —
a plain `mvn install`/`mvn test` from the repo root silently required a reachable Docker daemon,
because every Testcontainers-backed test in `integration-tests` ran unconditionally; a missing
Docker daemon surfaced as an unclear failure deep inside Testcontainers' own connection probing.
Fixed via `@Tag("testcontainers")` placed once on `AbstractPostgresIntegrationTest` (JUnit 5 tags
on a superclass are inherited, so all 12 Docker-backed test classes got tagged with zero per-class
edits) plus `<excludedGroups>testcontainers</excludedGroups>` wired into `maven-surefire-plugin`
via a property in `integration-tests/pom.xml` — a bare `mvn test` now runs only the 9 Docker-free
classes (41 tests, 1:23, zero Docker activity). `integration-tests/run.sh` (the sanctioned way to
run the full suite) overrides the exclusion back to blank unconditionally, verified unaffected
(88/88 green). Also added: a Docker daemon precheck and a CI-environment guard (fails fast if
`GITHUB_ACTIONS` + this sandbox's `--sandbox`/`TESTCONTAINERS_RYUK_DISABLED`/
`INTEGRATION_TESTS_POSTGRES_FIXED_PORT` are set together) to `run.sh`; a new `SharedEnvConfigTest`
(4 tests, no Docker); and a one-line `.env`-is-intentionally-committed-and-non-secret doc note in
`integration-tests/CLAUDE.md`. Hit a real dead end along the way: reassigning the `user.dir` system
property per test (the originally planned way to simulate different working directories) turned
out not to actually affect how `java.io.File` resolves relative paths on this JDK — fixed by giving
`SharedEnvConfig` a second, package-visible `require(String, File)` entry point the test calls
directly against `@TempDir` trees, with the original `require(String)` becoming a one-line
delegation to it. Full design rationale — including why this doesn't repeat
`integration-tests/DECISIONS.md` ADR-008's rejected "widen visibility for test convenience"
pattern, since `SharedEnvConfig` is this module's own internal test-support plumbing rather than a
starter's shipped production surface — is in ADR-010.

✅ Done (2026-07-17): [improvement-044](issues/improvement-044-shared-env-config-consolidation.md) —
DB credentials (`experiments`/`experiments_user`/`experiments_user_password`) and MinIO/S3
credentials (`admin`/`admin12345`, bucket `advertisement`, region `us-east-1`) were each hardcoded
independently across 4-5 files of different formats (`docker-compose.db.yml`/`.minio.yml`/
`.app.yml`, `application-dev.yml`, `deploy.sh`, `scripts/database/reset.sh`) — not a live bug, but
a real drift risk on the next credential rotation. Consolidated into the repo-root `.env` (already
established for `POSTGRES_IMAGE` by improvement-027): compose files reference `${VAR}` directly
(including inside `minio-init`'s inline shell entrypoint), `application-dev.yml` uses
`${VAR:default}` Spring placeholders with the current values as a safety-net default for IDE runs
that never source `.env`. The tricky part was `deploy.sh`/`reset.sh`, both of which
`scripts/ci/entrypoint.sh` already overrides via env vars (e.g. `DB_PORT=15432`) for its isolated
e2e stack — a naive `source .env` would have silently clobbered those overrides, so `.env` is
instead parsed into `ENV_*`-prefixed vars used only as a second-tier fallback under any
already-exported value, preserving the exact existing precedence. Also collapsed
`playwright/run.sh`'s `v1.52.0-jammy` image tag (two occurrences, same file) and the separate
`playwright@1.52.0`/`@playwright/test@1.52.0` npm pins into `PLAYWRIGHT_VERSION`/`PLAYWRIGHT_IMAGE`
variables. Deliberately left hardcoded: `docker-compose.app.yml`'s `DB_PORT: 5432`/
`S3_ENDPOINT: http://minio:9000` and `deploy.sh`'s app-container `DB_PORT=5432` — these are the
containers' own internal Docker-network ports, a different concept from the host-facing `.env`
value despite sharing the same number today. Documented in `scripts/DECISIONS.md` ADR-009.
Verified via a full `bash scripts/deploy.sh --reset` (fresh DB/MinIO volumes+containers+image) and
a full e2e Playwright run, 48/48 green.

✅ Done (2026-07-18): [improvement-061](issues/improvement-061-supportutil-tolong-silent-truncation-id-filter.md) —
the user id range filter used a `Double`-backed `NumberField` with `SupportUtil.toLong(Double)`
(`value.longValue()`) silently truncating fractional input (`123.99` → `123`) with no validation
error. The issue's own suggested fix (add a whole-number check to the DTO-level `idValid`
predicate) turned out structurally impossible during implementation: that predicate only ever sees
the `Long` field on the DTO *after* the setter already ran `toLong()` and destroyed the fractional
part. The "alternative considered" (`IntegerField`, 32-bit) was also rejected once the actual
column type was checked — `user_information.id` is `BIGSERIAL` (64-bit), same as every domain
table in this project; `IntegerField` would impose an artificial ceiling below the real schema
range. Fixed instead with a new `QueryLongField` UI component (text-backed, mirrors
`QueryNumberField`'s `Configurable` structure) parsing raw text directly to `Long` via new
`SupportUtil.toLongOrNull(String)` — no `Double` anywhere in the pipeline. Un-parseable input is
flagged via the component's own native Vaadin `invalid`/error-message state, confirmed not
conflicting with `HighlighterUtil`'s separate CSS-class styling. `SupportUtil.toLong(Double)`
removed (zero other callers). Documented in `marketplace-app/DECISIONS.md` ADR-045. Verified via
`SupportUtilTest` (8/8) and a new Playwright assertion in `05-seed-filter-sort-pagination.spec.js`
(typing `1.5` sets Vaadin's `invalid` attribute, typing `1` clears it) — full e2e suite 48/48
green.

✅ Done (2026-07-18): [improvement-079](issues/improvement-079-formoverlaymodehandler-activity-tab-duplication-and-userid-bug.md) —
`UserFormOverlayModeHandler.buildActivityContent()` passed `.userId(params.getUser().id())` (the
profile subject) into `AuditActivityPanel.Parameters` instead of the acting viewer's id, unlike
`AdvertisementFormOverlayModeHandler`/`TaxonFormOverlayModeHandler` which both correctly pass
`access.getCurrentUserId()`. Traced through `AuditPort.getEntityActivity()` →
`AuditLogRepository.findRows()`'s `filterActorId` SQL condition to confirm this must always be the
viewer, not the subject — currently masked because `canOperate` for `User` only allows self-view
(owner == viewer) or privileged viewers (whose filter short-circuits to `null` regardless). Fixed
alongside extracting the near-identical "Edit tab + lazily-loaded Activity tab" choreography,
independently duplicated across all three form handlers, into a new
`AbstractFormOverlayModeHandler.buildContentWithActivity(ActivityTabParams)` — a `@Value
@lombok.Builder` parameter object per the "5+ fields" convention; `formTabs`/`editTab` moved from
per-subclass private fields to `protected` base-class fields; Taxon's own now-redundant private
`buildContentWithActivity(Div)` helper was deleted outright. See `marketplace-app/DECISIONS.md`
ADR-046. Moved to `completed/issues/`. New `UserFormOverlayModeHandlerTest` (plain Mockito, no
Spring context) constructs the handler with a viewer id deliberately different from the
profile-subject id and asserts the panel receives the viewer's id — fails pre-fix, passes post-fix;
no Playwright test added since the buggy path isn't reachable through any real UI flow today (see
masking note above). Full e2e suite (specs 01-06, `--ux`) re-run after the change: 35/35
non-skipped tests green, including the User/Advertisement/Taxon activity-tab flows this refactor
directly touches.

✅ Done (2026-07-18): [improvement-060](issues/improvement-060-advertisementenrichservice-listallbytype-instead-of-findbyids.md) —
found already resolved on re-check: the issue's target method (`resolveCategoryNames()`, using
`listAllByType()` + an in-memory `.filter()`) no longer exists under that shape.
`AdvertisementEnrichService.resolveNames()` (its current form) already calls the bulk
`TaxonPort.findByIds(ids, Locale.ENGLISH)` lookup the issue was asking for — a side effect of
improvement-058 (2026-07-17, "Timeline tab resolves category names instead of raw taxon ids"),
which rewrote this method for an unrelated reason and picked up the same fix along the way. No
code change needed; moved directly to `completed/issues/`.

✅ Done (2026-07-18): [improvement-067](issues/improvement-067-taxontranslationrepository-unbounded-in-clause.md) —
`TaxonTranslationRepository.findAllByTaxonIds()` was the one method improvement-054 missed when it
fixed this same unbounded-`IN`-clause pattern in `TaxonAssignmentRepository`/`AttachmentRepository`.
Switched to `WHERE taxon_id = ANY(:taxonIds)` with `taxonIds.toArray(new Long[0])`, matching the
existing pattern exactly (one placeholder regardless of collection size, avoids Postgres's
parameter-count limit and query-plan cache churn). Mechanical, same-shape fix as improvement-054 —
no new ADR (improvement-054 itself has none either, just this archive entry). Caught a gap while
verifying: no existing test exercised this method at all — `TaxonPort.findByIds()` is the only
public entry point that drives it with more than one id
(`DefaultTaxonPort.buildDtoIndex()` -> `TaxonService.getTranslationsForMany()`), so a new test,
`findByIds_resolvesTranslationsForMultipleTaxonsInOneCall()`, was added to
`TaxonPortTranslationFallbackTest` to actually exercise the array-bind SQL with 2 taxons. Verified
via `bash scripts/integration-tests.sh --sandbox TaxonPortTranslationFallbackTest` — 6/6 green
(5 pre-existing + the new one).

✅ Done (2026-07-18): [improvement-064](issues/improvement-064-s3storageservice-inputstream-not-closed.md) +
[improvement-069](issues/improvement-069-attachment-s3-move-inside-db-transaction-orphans-on-rollback.md) —
fixed together, both touch the attachment upload/cleanup path. improvement-064:
`AttachmentService.upload()`/`uploadTemp()` now explicitly close the `InputStream` they're given
(AWS SDK v2's `RequestBody.fromInputStream()` documents that it never does) via a `closeQuietly()`
helper that logs, not throws, on a close failure — deliberately not try-with-resources, which would
have made a post-upload close failure look like the upload itself failed. improvement-069: went
beyond the two cheap mitigations (reorder + log-on-rollback) to the full fix — `AdvertisementSaveService
.save()` now runs the S3 gallery commit as the last mutation before its transaction's own commit
(shrinks the failure window) and logs `ERROR` via `TransactionSynchronizationManager` if the
transaction still rolls back after it (guarded by `isSynchronizationActive()`, required so the
existing mocked-`TransactionTemplate` unit tests don't throw `IllegalStateException`); AND
`AttachmentCleanupService` (already scheduled nightly via `CleanupProperties.cronExpression()` —
correction from an earlier draft of this fix that almost added a second, redundant scheduler)
gained a third pass, `sweepOrphanedEntityFiles()`, that cross-checks S3 objects under each
`EntityType`'s folder against a new `AttachmentRepository.findExistingUrls()` bulk lookup and
deletes whichever have no matching DB row at all — closing the "Required verification" question
improvement-049 had explicitly left open. See `attachment-spring-boot-starter/DECISIONS.md` ADR-011
and `marketplace-app/DECISIONS.md` ADR-047. New tests: `AttachmentServiceTest` (2), `AttachmentCleanupServiceTest`
(2) — both plain Mockito, no Spring context. Verified via `bash scripts/unit-tests.sh marketplace-app`
(`AdvertisementSaveServiceTest` 5/5), `bash scripts/integration-tests.sh --sandbox
AttachmentServiceTest,AttachmentCleanupServiceTest` (8/8) and `AttachmentRepositoryTest` (8/8, real
Postgres), plus a full Playwright e2e pass (35/35 non-skipped).

✅ Done (2026-07-18): [improvement-070](issues/improvement-070-attachmentsnapshotrepository-unsafe-array-cast-silent-swallow.md) —
`AttachmentSnapshotRepository.extractUrls()`'s unsafe `(String[])` cast (resting on driver
convention, not a `java.sql.Array` contract guarantee) wrapped in a silent `catch (Exception _)`.
Rejected the initially-proposed `(Object[])` cast + `Stream.of(...).map(String::valueOf)` fix on
user request (standing preference against casts, not just unsafe ones) in favor of a genuinely
cast-free rewrite: `java.sql.Array.getResultSet()` — part of the `Array` interface itself, returns
a two-column `ResultSet` (index, value) read via `getString(2)`, so the driver handles type
conversion, not this code. `catch (Exception _)` narrowed to `catch (SQLException e)` with a
`log.warn(...)`. See `attachment-spring-boot-starter/DECISIONS.md` ADR-012. New
`AttachmentSnapshotRepositoryTest` (real Postgres, first coverage this repository has ever had) —
3/3 green, round-tripping multiple urls through `insert()`/`getPrevUrls()`/`getUrlsById()`. Full
attachment-domain integration test sweep (`AttachmentServiceTest`, `AttachmentServiceTransactionTest`,
`AttachmentSnapshotRepositoryTest`, `AttachmentCleanupServiceTest`, `AttachmentRepositoryTest`) —
21/21 green, no regression in adjacent tests.

✅ Done (2026-07-18): [improvement-068](issues/improvement-068-attachment-audit-shows-uuid-not-original-filename.md) —
`AttachmentSnapshotService.filename(url)` derived the displayed media name from the S3 object key
(always `UUID + extension`), so Activity/Timeline diffs showed meaningless UUIDs instead of the
uploaded file's real name. A dedicated research pass (user-prompted: "check whether this applies
to Activity and views too") confirmed the bug was fully isolated to this one method — gallery/
lightbox/card components already display the real `attachment.filename` column throughout, and
Activity/Timeline rendering only shows whatever string this method already produced at capture
time. Fixed by resolving real filenames via a new `resolveFilenames()` bulk lookup
(`AttachmentRepository.findByEntityAndUrls()`) into a `Map<url, filename>` — keyed by url, not
filename, so two attachments sharing an identical original filename can't collide; each url still
resolves independently. Falls back to the old UUID-derived name only when no matching row exists
(e.g. an attachment purged past the 90-day retention window). See
`attachment-spring-boot-starter/DECISIONS.md` ADR-013. New `AttachmentSnapshotServiceTest` (4
tests, plain Mockito) covers real-name resolution, the no-match fallback, `getMediaStateForSnapshot()`,
and the duplicate-filename-no-collision case. Full attachment-domain integration sweep (25/25) and
a full Playwright e2e pass (35/35 non-skipped) both green.

✅ Done (2026-07-18): [improvement-071](issues/improvement-071-taxonformoverlaymodehandler-raw-uicomponentfactory.md) —
`TaxonFormOverlayModeHandler` was the only one of the four `OverlayFormBinder`-using form handlers
declaring its factory field with a raw type (`UiComponentFactory<OverlayFormBinder>`, with
`@SuppressWarnings("rawtypes")`/`"unchecked"`). Parameterized to
`UiComponentFactory<OverlayFormBinder<TaxonEditDto>>`, matching
`AdvertisementFormOverlayModeHandler`/`UserFormOverlayModeHandler`/`SettingsFormModeHandler`
exactly; both suppressions removed as no longer needed. Purely cosmetic type-safety alignment, no
ADR (matches an already-established pattern, nothing new decided). `bash scripts/unit-tests.sh
marketplace-app` 58/58. Full Playwright e2e: first run hit 4 unrelated failures (`.header
-settings-button` not appearing post-login/signup — a frontend/browser timing issue, not a server
error per `docker logs`), confirmed flaky by an immediate clean retry at 35/35 non-skipped green —
not caused by this change (a compile-time-only generics fix cannot alter runtime UI behavior).

✅ Done (2026-07-18): [improvement-075](issues/improvement-075-timeline-actor-filter-multi-select.md) —
Timeline actor filter now supports multiple actors: picking a row in `UserPickerField`'s dialog
adds to the selection instead of replacing it (dialog still closes after each pick), each selected
actor shows as a removable chip, and the query matches "any of the selected actors" via a new
`= ANY(:actorIds)` SQL condition. `AuditTimelineFilterDto.actorId` (`Long`) → `actorIds`
(`Set<Long>`, see `platform-commons/DECISIONS.md` ADR-020); new `SqlOperator.ANY_OF`/
`SqlCondition.anyOf(Set<Long>)` in query-lib rather than an `inSet()` overload (erasure clash with
the existing `<E extends Enum<E>>` generic method) or reusing `IN` (same unbounded-placeholder
class improvement-054/067 already fixed twice — see `query-lib/DECISIONS.md` ADR-005);
`UserPickerField` rewritten from `CustomField<UserDto>` to `CustomField<Set<UserDto>>` with a
chip-list UI (new `user-picker-field.css`, new `USER_PICKER_REMOVE_TOOLTIP` i18n key). Caught and
fixed a real regression before it shipped: `TimelineView.refresh()`'s non-privileged-viewer self
-scoping used `Set.of(access.getCurrentUserId())`, which throws `NullPointerException` when that
id is null (an unauthenticated/transient session state) — `Set.of()` rejects null elements where a
plain `Long`-typed builder setter silently accepted them; this broke Vaadin's `TimelineView` bean
construction and failed *every* Playwright test at first run (app-wide startup failure, not
Timeline-specific) until guarded. New tests: `SqlConditionTest`/`SqlOperatorTest` (`anyOf`/
`ANY_OF`), `AuditLogRepositoryTest` (real Postgres, `= ANY()` matches multiple actors).

A first full Playwright pass (46/48) surfaced a spec 04 max-content failure that was initially
(wrongly) assumed unrelated flakiness; screenshot inspection showed the real cause: `Save error:
Duplicate key <url> (attempted merging values ...)` — `AttachmentSnapshotService.resolveFilenames()`
(improvement-068 code, not this issue) used `Collectors.toMap(url, filename)` with no merge
function, which throws whenever two `attachment` rows share a URL (a soft-deleted row and its
re-added replacement, e.g. a YouTube video removed then re-added) — only exercised by spec 04's
10-item gallery-replace scenario, never by this issue's own lighter tests. Fixed with a
`(a, b) -> a` merge function. Also found and fixed while re-checking the UI end to end:
`.advertisement-category-chip` had `white-space: nowrap` with no `max-width`/`text-overflow`, so a
maximum-length (255-char) category name rendered as one unbounded, layout-breaking chip. Root
-caused a second, unrelated Playwright red herring during this pass: `removeActorChip()` (this
issue's own flow helper) asserted the post-removal chip count immediately after the click with no
wait for the Vaadin server round-trip — unlike `applyFilter`/`fillActorPicker`, which already wait
on `waitForVaadin()`/dialog-closed. Fixed by calling the existing `waitForVaadin()` helper
(exported from `filter.flow.js`) after the click, matching the established pattern rather than
inventing a new one.

A second round of user-driven style review (after this pass had already reported "done") found the
new `UserPickerField` chip UI didn't actually match the rest of the app: its clear-all button used
`VaadinIcon.CLOSE_CIRCLE`, an icon used nowhere else in the codebase for a close/remove action
(confirmed by grep — every other place uses `CLOSE_SMALL`), sitting an unjustifiably large gap from
the search button (Vaadin's default `HorizontalLayout` spacing, ~1rem, plus a redundant
`margin-left` stacked on top), and the field had no visible border/box in its static state at all
— unlike every native Vaadin field, which gets one for free from Lumo, because this is a hand-built
`CustomField` that never got that styling. Fixed: `CLOSE_SMALL` everywhere, `setSpacing(false)` +
a single `gap: 6px`, and a bordered-box treatment on `.user-picker-layout` matching `forms.css`'s
established `vaadin-*-field::part(input-field)` pattern (border, radius, `:focus-within` accent).
The same review surfaced a second, previously-missed instance of the max-length-category overflow
bug: `.taxon-row-name` (Reference Data's category list) had no overflow handling at all, so a
255-char name ballooned into a multi-line block dwarfing its sibling pills — fixed with the same
`max-width`/`ellipsis` pattern. Per explicit instruction, the max-length-category edge case was
then given *permanent* automated coverage instead of remaining a disposable verification script:
one of spec 03's 10 seeded "Boundary-XX" categories was changed to an actual 255-char name
(`MAX_CATEGORY_NAME`), so spec 04's existing max-content assertions exercise it on every run
— no new test case added, matching the standing "check everything, don't add new tests" directive
for this pass.

Full Playwright e2e (`--full`, seeds spec 05) run five times total across both rounds of this
pass: the true count is 48/48 green — the max-content duplicate-key crash and the chip-removal
race were real bugs, not flakiness; an intermediate isolated `05-seed-filter-sort-pagination`-only
run also showed a `page sizes` test failing on login ("Invalid email or password"), traced to
running spec 05 alone against a freshly reset DB without spec 02 (which creates the fixture
accounts) having run first — a test-invocation artifact, not a bug, confirmed by the full-suite
rerun passing that test cleanly.

A third round ("what about with the actor?") found `.user-picker-chip` had the identical missing
-truncation bug as the category chip — fixed with `max-width` + a `.user-picker-chip-name` class
(`min-width: 0` needed since flex items default to sizing by content). Given the same permanent
-coverage treatment: spec 05's timeline actor-filter test now picks `maxEn` (100-char name) as its
second actor instead of another short "Seed User N". This surfaced a real flake in the shared
`fillActorPicker()` flow helper itself — `maxEn`/`maxUk` share an *identical* generated name, so
the picker grid shows two equal-text rows, and the existing scroll-based lookup could land on a
position where the target row was only partially clipped by the grid's overflow (Playwright still
reports such a row as "visible", so the click silently misses and the dialog never closes). Fixed
by adding an opt-in `useSearch` mode to `fillActorPicker()` that types into the picker's own
-search field instead of scrolling, used only for this call — the original `'Seed User 60'` call
keeps its scroll-based path unchanged, since that one deliberately exercises the picker grid's
second-page lazy loading (improvement-056). Full e2e 48/48 green after this round too, including a
rerun confirming a one-off "TLS connection disconnected" failure (spec 04, immediately after a
plain login, nowhere near any of this session's code) did not reproduce — checked via screenshot
before being ruled out, not assumed.

✅ Done (2026-07-18): [improvement-076](issues/improvement-076-advertisementcardview-redundant-stoppropagation.md) —
removed the redundant `.getElement().addEventListener("click", ...).addEventData("event.stopPropagation()")`
calls in `AdvertisementCardView.createEditButton()`/`createDeleteButton()`; confirmed
`BaseActionButton.applyConfig()` already registers the identical listener for both buttons.

✅ Done (2026-07-18): [improvement-077](issues/improvement-077-advertisementcardview-dead-updatedat-null-check.md) —
removed the dead `ad.getUpdatedAt() == null ||` half of `AdvertisementCardView.createMetaPanel()`'s
`neverEdited` check — `updatedAt` is `@LastModifiedDate`, never null on a persisted row. Kept the
live `.equals(ad.getCreatedAt())` half unchanged.

✅ Done (2026-07-18): [improvement-082](issues/improvement-082-cardlightboxviewer-redundant-queryselector.md) —
`CardLightboxViewer.update()` no longer uses `document.querySelector`-based page-level JS; each of
the three call sites was checked individually before removal rather than deleted in bulk. The
iframe-`src` re-sets were literal duplicates of the direct `iframe.getElement().setAttribute(...)`
call immediately above them — deleted outright. The video pause/clear and `.load()` calls had no
existing direct equivalent, so they were kept but rewritten as `videoEl.executeJs(...)` on the
already-held `Element` reference (same pattern already used by `AttachmentLightbox`), rather than
a page-wide `querySelector` that could cross-control a second open lightbox instance.

✅ Done (2026-07-20): [improvement-087](issues/improvement-087-audit-prev-snapshot-and-last-snapshot-missing-id-tiebreaker.md) —
`AuditLogRepository.findTimeline()`'s `prev_id`/`prev_snapshot_data` subqueries and
`getLastSnapshot()` now compare `(created_at, id)` tuples / order by `id DESC` as a tiebreaker,
matching the shape improvement-050 item 4 already fixed for `version` numbering. TDD: three new
tied-row tests in `AuditLogRepositoryTest` (reusing the raw-`jdbcClient`-insert technique) were
confirmed red against the old strict-`<`/no-tiebreaker SQL before the fix, green after.

✅ Done (2026-07-20): [improvement-091](issues/improvement-091-loadmediastats-nondeterministic-main-attachment.md) —
`AttachmentRepository.loadMediaStats` (single + bulk) now orders by `created_at ASC, id ASC`, so
the "main attachment" pick on tied `created_at` is deterministic and the single/bulk variants
agree. Fixed alongside improvement-087 (Batch A) — same defect class, same tied-row test
technique, one PR covering both starters.

✅ Done (2026-07-20): [improvement-090](issues/improvement-090-attachment-cleanup-restore-race-and-video-rows-never-purged.md) —
`AttachmentRepository.deleteByUrls` now re-checks `deleted_at IS NOT NULL` and returns only the
urls it actually removed (`RETURNING url`), so a row restored concurrently between candidate
collection and delete survives (item 1); `findUrlsDeletedOlderThan` no longer excludes video
content types from the DB-purge candidate list, only from the S3-delete step inside
`AttachmentCleanupService` (item 2); a third item was folded in after a follow-up question about
restore-vs-retention interaction — `attachment_snapshot` rows had no purge at all, now cleaned up
by age via a new `AttachmentSnapshotRepository.deleteOlderThan()`, same shape as
`AuditLogRepository.deleteOlderThan()` (item 3). Covered by new/rewritten tests in
`AttachmentCleanupServiceTest`, `AttachmentRepositoryTest`, and `AttachmentSnapshotRepositoryTest`.

✅ Done (2026-07-20): [improvement-093](issues/improvement-093-capturemediachanges-silent-skip-without-actor.md) —
`AttachmentService.captureMediaChanges()` now uses `orElseThrow()` instead of silently skipping the
snapshot when no actor is present, matching `delete()`'s fail-fast contract in the same class.
Required updating one existing `AttachmentServiceTest` case that had stubbed an absent actor while
expecting a normal upload to succeed; added a new case asserting the throw.

✅ Done (2026-07-20): [improvement-106](issues/improvement-106-timeline-non-admin-empty-actorids-fail-open.md) —
`TimelineView.refresh()` now fails closed (empty feed, no query) when a non-admin's actor id isn't
resolvable, instead of building a filter with an empty `actorIds` set that `SqlCondition.anyOf()`
silently turns into "no restriction." `query-lib/DECISIONS.md` ADR-006 records why `anyOf`/`inSet`
themselves were left unchanged (their null-on-empty behavior is correct for the admin/optional-
filter path) and the rule for future access-narrowing callers instead.

✅ Done (2026-07-20): [improvement-088](issues/improvement-088-authservice-login-session-fixation.md) —
`AuthService.login()` now calls `request.changeSessionId()` right after successful authentication,
before `saveContext()`. Chose the plain Servlet API over `VaadinService.reinitializeSession()`
(the issue's other suggested option) because the latter needs a live `VaadinRequest` bound via
`CurrentInstance`, which the existing plain-Mockito `AuthServiceTest` suite has none of.

✅ Done (2026-07-20): [improvement-107](issues/improvement-107-embed-video-url-no-validation-and-sandbox-escape.md) —
`AttachmentService.addVideoTemp()`/`addVideo()` now validate the embed URL (scheme must be
http/https, host must be in an allowlist) before persisting a `CT_EMBED` attachment; both
lightbox classes' iframe `sandbox` attribute dropped `allow-same-origin`. Allowlist scoped to
Vimeo (YouTube already has its own path via `YoutubeUtil`) after confirming with the user — the
placeholder text previously advertised "YouTube, Facebook..." but no Facebook resolver ever
existed, so both EN/UK placeholders were corrected to "YouTube, Vimeo" to match reality.

✅ Done (2026-07-20): [improvement-092](issues/improvement-092-advertisement-audit-capture-split-across-modules.md) —
delete-side audit capture moved from `AdvertisementService.delete()` (starter) into
`AdvertisementSaveService.delete()` (marketplace-app), reusing the existing `buildCurrentSnapshot()`
helper save already had — one module now owns all advertisement audit orchestration. Recorded as
`marketplace-app/DECISIONS.md` ADR-050. `AdvertisementCardView` now calls the new service method
directly instead of going through `ComponentFactory<AdvertisementPort>.ifAvailable(...)`.

✅ Done (2026-07-20): [improvement-094](issues/improvement-094-resolvecategoryfilter-null-sentinel.md) —
`AdvertisementService.resolveCategoryFilter()` now returns `Optional<Set<Long>>` instead of a
nullable `Set<Long>` (`empty()` = no filter/taxon starter absent, `of(ids)` possibly empty =
filter resolved) — the repository's own `null`-means-no-filter contract is untouched. New
`AdvertisementServiceCategoryFilterTest` covers all four states through `getFiltered()`/`count()`.

✅ Done (2026-07-20): [improvement-062](issues/improvement-062-missing-readonly-transactional-on-port-impls.md) —
`UserPortImpl`, `AdvertisementPortImpl`, and `DefaultTaxonPort` all got class-level
`@Transactional(readOnly = true)` plus per-method `@Transactional` overrides on their write
methods, matching `DefaultAuditPort`'s existing pattern.

✅ Done (2026-07-20): [improvement-089](issues/improvement-089-userservice-hard-delete-no-audit-trail.md) —
Option A (soft-delete, aligning with the rest of the platform). `user_information` gained
`deleted_at`/`deleted_by` columns (added directly to the existing `01-user-schema` changeset, not
a new one — app isn't in production yet). `UserService.delete()` now soft-deletes + captures a
deletion snapshot; `UserPort.delete()` gained an `actingUserId` parameter. `findByEmail` (login)
and the user list (`findByFilter`/`countByFilter`) now exclude soft-deleted rows; `findById`/
`findActorNames`/`findByIds` stay unfiltered (historical resolution still works), matching
`TaxonRepository.findById`'s precedent rather than `AdvertisementRepository`'s stricter one.

Also added, after discovering `advertisement.created_by`'s `ON DELETE RESTRICT` FK would otherwise
block purging a deleted user who ever posted a still-active ad: new marketplace-app
`UserDeleteService` cascades to soft-delete the user's own advertisements first (each with its own
audit capture via `AdvertisementSaveService.delete()`), and a new 90-day retention cleanup job
(`UserService.cleanup()`, `UserAutoConfiguration`'s scheduler) purges old soft-deleted rows —
per-row with try/catch around `DataIntegrityViolationException` rather than one bulk `DELETE`, so
a row still blocked by some other reference is skipped and retried the next run instead of failing
the whole batch.

Actor-name resolution for historical audit rows now annotates deleted actors via a new
`UserActorNameService` (marketplace-app) — `AuditDomainHookImpl.resolveNames()` was kept a pure
delegation per the `*HookImpl` rule, with the actual name+deleted-flag combining logic living in
the new service instead. New `I18nKey.AUDIT_ACTOR_DELETED_NAME` (`"{0} (deleted)"` / uk
`"{0} (видалено)"`).

✅ Done (2026-07-21): [improvement-078](issues/improvement-078-queryblock-filterrow-helper.md) —
new `QueryBlock.filterRow()` helper family (3 overloads: single-field no-sort, single-field+sort,
two-field+sort) collapses the repeated `add()` + sort-register + filter-register boilerplate;
`AdvertisementQueryBlock`/`UserQueryBlock`/`TimelineQueryBlock` all migrated to use it.

✅ Done (2026-07-21): [improvement-081](issues/improvement-081-lightbox-embedurl-and-iframe-attrs-duplication.md) —
new `org.ost.marketplace.ui.views.utils.LightboxUtil` (`resolveEmbedUrl()` +
`applyEmbedIframeAttributes()`) extracted from the duplicated logic in `AttachmentLightbox` and
`CardLightboxViewer`; both now delegate to it instead of each keeping its own copy.

✅ Done (2026-07-21): [improvement-084](issues/improvement-084-snapshot-dto-diff-field-boilerplate.md) —
`AuditableSnapshot` gained two `diffField()` static helper overloads (`String` via
`Objects.equals`, `int`/boxed `Integer` for "no previous value" detection); `TaxonSnapshotDto`,
`UserSnapshotDto`, `SettingsSnapshotDto`, and `AdvertisementSnapshotDto` all migrated their
`diff()` methods to use it, collapsing 12 of the 13 duplicated field-diff blocks (the 13th,
`AdvertisementSnapshotDto`'s `categoryIds` list-diff, stays bespoke).

PR 1 of Batch F verified: `./mvnw` compile clean across `platform-commons` + `marketplace-app`,
all `*SnapshotDtoTest` classes green (22 tests), and a full `bash scripts/deploy.sh --reset` +
`bash scripts/playwright.sh e2e --ux` run — 35/35 non-skipped e2e tests passed (13 skipped, no
`--full`). The `--reset` was also needed to clear an unrelated pre-existing drift: the dev
Postgres volume predated the improvement-089 in-place changeset edit (`user_information.deleted_at`),
so `UserRepository.findByEmail()` was failing with `column u.deleted_at does not exist` on this
container before the reset — confirms editing an already-applied changeset in place (per
improvement-089's explicit non-prod exception) requires a volume reset on any environment that
ran the old version of that changeset.

✅ Done (2026-07-21): [improvement-083](issues/improvement-083-advertisementcardview-thumbnail-click-no-op-when-attachment-port-unavailable.md) —
`AdvertisementCardView.createThumbnail()`'s click handler now uses
`attachmentPortFactory.findIfAvailable().ifPresentOrElse(...)` instead of `.ifAvailable(...)`,
showing a new `ADVERTISEMENT_CARD_NOTIFICATION_MEDIA_UNAVAILABLE` notification when the starter
becomes unavailable mid-session instead of silently doing nothing.

✅ Done (2026-07-21): [improvement-008](issues/improvement-008-deleted-category-strikethrough.md) +
[improvement-101](issues/improvement-101-audit-diff-unresolved-category-ids.md) — both traced to
the same root cause: `TaxonRepository.findByIds()` had a `deleted_at IS NULL` filter (added for
improvement-045) that made a soft-deleted category invisible to its only caller,
`DefaultTaxonPort.indexById()`. This meant a deleted category didn't render struck-through in the
advertisement view overlay as improvement-008 originally assumed — it vanished from the category
list entirely — and its name could never be resolved for audit-diff rendering (improvement-101),
falling back to a bare numeric id. Fixed at the root: removed the SQL filter (see
`taxon-spring-boot-starter/DECISIONS.md` ADR-005), flipped `DefaultTaxonPort.getForEntity()`'s
`activeOnly` flag to `false`, added the `.advertisement-category-chip--deleted` (strikethrough)
CSS class + `cat.isDeleted()` check in `AdvertisementViewOverlayModeHandler`, and wrapped deleted
category names in a plain `<s>` tag in `AdvertisementEnrichService.resolveNames()` (rendered
as-is since `AuditChangeFormatter` already sets diff values via `innerHTML`) — no new i18n text,
just the same strikethrough treatment as the view overlay, per explicit user direction against a
textual "(deleted)" suffix. `TaxonRepositoryTest.findByIds_excludesSoftDeletedRows` rewritten to
`findByIds_includesSoftDeletedRows`. Playwright: extended the existing Electronics delete/restore
test in `03-marketplace-promotion-flow.spec.js` (rather than adding a new test) with a step that
assigns Electronics to a throwaway ad before deletion, then verifies the view-overlay chip and the
activity-diff row both render it struck through, before the existing restore step runs.

✅ Done (2026-07-21): [improvement-010](issues/improvement-010-advertisements-view-refresh-error-notification.md) —
`AdvertisementsView.refresh()`'s catch block now calls
`notificationService.error(ADVERTISEMENT_VIEW_NOTIFICATION_REFRESH_ERROR)`, matching `UserView`'s
refresh guard. Also removed `AdvertisementService.save()`'s unused `actingUserId` parameter
(authorship is handled entirely by `@CreatedBy`/`AuditorAware` — the parameter was never read in
the method body) — cascaded through `AdvertisementPort.save()`, `AdvertisementPortImpl.save()`,
and the call site in `AdvertisementSaveService.save()` (which keeps its own `actorId` parameter,
still needed for audit capture).

✅ Done (2026-07-21): [improvement-014](issues/improvement-014-media-diff-counts-summary.md) — no
code change. Decided to keep the full before/after filename list in media-change diff rows rather
than collapsing it to a counts summary ("2 added, 1 removed") — explicit user direction: seeing
which specific files were added/removed/kept matters more than a shorter row.

✅ Done (2026-07-21): [improvement-080](issues/improvement-080-taxonformoverlaymodehandler-locale-field-dedup.md) —
`TaxonFormOverlayModeHandler` collapsed its four separately-wired EN/UK locale fields
(`nameEnField`/`descriptionEnField`/`nameUkField`/`descriptionUkField`) into a private
`LocaleField` record (holding the two UI fields plus `ValueProvider`/`Setter` accessor pairs for
both `TaxonEditDto` and `TaxonSnapshotDto`), built once at the top of `activate()`. All five
duplicated usage sites now loop over `localeFields`: field `configure()`, value-change wiring,
`buildBinder()`'s `asRequired`/`StringLengthValidator`/`bind()` chain, and the two
`TaxonEditDto`-copy sites (`discardChanges()`/`loadRestored()`, extracted into a shared
`copyLocaleFields()`) plus the `TaxonSnapshotDto`-to-`TaxonEditDto` copy in
`handleRestoreFromActivity()`. Per the issue's risk note (binder validation is the delicate part
of this file), manually re-verified after the refactor that saving is still blocked when either
locale's name is left blank (row count unchanged, overlay stays open) — confirmed for both EN and
UK, not just one. Also added a one-line comment to `TaxonService`'s two snapshot builders
(`buildSnapshotFromData()`/`buildSnapshotFromTranslations()`) noting they stay hardcoded to en/uk
because `TaxonSnapshotDto` has a fixed 4-field shape — the DTO-shape change needed for a true
`supportedLocales()`-driven loop is out of scope here, per the issue's own escape hatch.

**Batch F complete** (078, 081, 084, 083, 008, 010, 014, 101, 080 — all done across three PRs).

✅ Done (2026-07-21): [improvement-097](issues/improvement-097-modal-scrim-and-lightbox-close-placement.md) —
added a `--app-modal-scrim` token applied to `vaadin-dialog-overlay::part(backdrop)` and
`vaadin-confirm-dialog-overlay::part(backdrop)` (previously fully transparent — every dialog and
the header behind it were left undimmed). Lightbox close button (`.card-lightbox__close`, shared
by both `AttachmentLightbox` and `CardMediaLightbox`) moved from `position: fixed` (viewport
corner, visually detached from the frame) to `position: absolute` within its now-`position:
relative` container. `AttachmentLightbox` (a hand-rolled `Div`, not a Vaadin `Dialog`) gained
Esc-to-close (`Shortcuts.addShortcutListener`) plus focus-into/restore-on-close — `CardMediaLightbox`
already got both for free from Vaadin's own `Dialog`.

✅ Done (2026-07-21): [improvement-098](issues/improvement-098-aria-labels-icon-only-controls.md) —
`BaseActionButton.applyConfig()` (grid edit/delete buttons) now sets `aria-label` alongside
`title` — most other icon-only controls (pagination arrows, gallery delete/add-video, notification
close, `UserPickerField` chips) already got this for free via the existing `UiIconButton`
mechanism; the real gaps were `BaseActionButton`, `SortIcon` (a bare `Span`, gained `role="button"`
+ `aria-label` synced to its current sort-state tooltip), and `QueryActionButton` (filter
apply/clear). New `07-accessibility.spec.js` walks every main tab as `adminEn` and asserts no
icon-only `vaadin-button`/`button` lacks an `aria-label`. Also added Tab-focus-trapping to
`BaseOverlay` for the big edit overlays (kept), but an accompanying "focus first field on open"
attempt was reverted after manual testing showed it actually focused the overlay's own
`.overlay__breadcrumb-back` button (plain `querySelectorAll` doesn't reach into Vaadin fields'
shadow DOM, so the breadcrumb "back" button was the only host-level `[tabindex]` match) — user
correctly called this out as `хоме`/`Reference Data` getting focused instead of any real field.

✅ Done (2026-07-21): [improvement-099](issues/improvement-099-confirm-dialogs-action-verbs-danger-styling.md) —
turned out much smaller than filed: `ConfirmActionDialog` (advertisement/user/taxon delete,
discard-changes) already took `confirmKey`/`cancelKey` as parameters and already applied
`ButtonVariant.LUMO_ERROR` unconditionally — someone had already fixed that half. The only actual
generic-text case was `LogoutDialog` (a raw Vaadin `ConfirmDialog`, separate from
`ConfirmActionDialog`), which said "Yes"/"Так"; now reuses the existing `HEADER_LOGOUT` key
("Log Out"/"Вийти"). Required fixing three Playwright helpers that hardcoded the old button text
(`auth.flow.js`, `seed.flow.js`, `05-seed-filter-sort-pagination.spec.js`) — the header's own
"Log Out" button and the dialog's confirm button now share the same text, so the locator also
needed `.last()` to disambiguate (a bare `getByRole` matched both).

✅ Done (2026-07-21): [improvement-110](issues/improvement-110-no-unsaved-changes-guard-on-tab-switch-and-unload.md) —
partially implemented, then partially reverted after manual verification. The `beforeunload`
half works as filed: `BeforeUnloadUtil.sync(hasChanges)`, called from all three form handlers'
`updateButtons()`, registers/unregisters a native "leave site?" browser prompt. The tab-switch
half (`MainView` consulting the active view before hiding it) was **removed entirely** after
confirming live that `.base-overlay` is `position: fixed; inset: 0; z-index: 100` — it already
covers the entire viewport, including the top-nav tab bar, whenever any edit overlay is open. A
user literally cannot click another tab while an overlay is open (confirmed via Playwright's own
`elementFromPoint`-based pointer-event interception, not assumption), so the "click another tab
while editing" scenario the issue described cannot happen through the current UI and the guard
code for it was dead on arrival — removed per YAGNI rather than kept "for later."

**Batch L complete** (097, 098, 099, 110 — all done in one PR; 110 shipped as beforeunload-only).

✅ Done (2026-07-21): [improvement-040](issues/improvement-040-spring-boot-vaadin-minor-bump.md) —
routine dependency bump in root `pom.xml`: `spring-boot-starter-parent` 4.0.6 → 4.1.0,
`vaadin.version` 25.1.5 → 25.2.3, `jsoup.version` 1.22.1 → 1.22.2, `aws-s3-sdk.version` 2.44.4 →
2.48.4, `jetbrains-annotations.version` 24.1.0 → 26.1.0 (`mapstruct.version` left at 1.6.3 — latest
available is still a pre-release Beta). The issue file had gone stale twice before (per its own
warning), so every target version was re-verified directly against Maven Central's
`maven-metadata.xml` rather than trusted from the file — this caught two more stale claims: Vaadin
was listed as 25.2.4 (actual latest: 25.2.3) and aws-s3-sdk as 2.48.3 (actual latest: 2.48.4), both
corrected before applying. Verified with the full suite (Vaadin is UI-critical): unit-tests 72/72,
integration-tests (Testcontainers) 127/127, Playwright e2e --full --ux 49/49, all clean of
ERROR/FAILED in the actual log content, not just the summary line. Batch G's remaining item is
improvement-085 (Playwright bump).

✅ Done (2026-07-22): [improvement-085](issues/improvement-085-playwright-version-bump.md) —
bumped Playwright from 1.52.0 to 1.61.1 (9 minor versions) in `playwright/run.sh`
(`PLAYWRIGHT_VERSION`) and `playwright/CLAUDE.md`, keeping the `-jammy` image tag suffix (verified
`v1.61.1-jammy` exists on `mcr.microsoft.com`, still Ubuntu 22.04-based — the plain, unsuffixed
tag switched to a newer Ubuntu base back in 1.47, unrelated to this bump). Read the 1.53-1.61
release notes before touching anything, per the issue's own instruction, since this is a much
larger gap than a routine patch bump: no breaking change in that range affects this project's
patterns (`_react`/`_vue`/`:light` selectors, `page.accessibility`, `Locator.ariaRef()`,
`videosPath`/`videoSize` — none of these are used here), so the manual `shadowFind`/
`shadowFindAll` helpers in `e2e/_flows/*.flow.js` needed no changes. Verified with
`bash scripts/playwright.sh e2e --full --ux`: 49/49 passed, 0 `failed`/`Error` in the actual log
content. **Batch G complete** (040, 085 — both done, in two separate PRs/commits).

✅ Done (2026-07-22): [improvement-019](issues/improvement-019-findtimeline-correlated-subqueries.md)
and [improvement-095](issues/improvement-095-getentityactivity-hardcoded-limit.md) — audit
read-side rewrite, one PR. `AuditLogRepository.findTimeline()` computed `version`/`prev_id`/
`prev_snapshot_data` via three correlated subqueries per returned row (worst: a `COUNT(*)` with a
`<=` inequality) — rewritten to the same window-function shape the sibling `findRows()` already
used (`ROW_NUMBER()`/`LAG() OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id)`),
preserving the `(created_at, id)` tiebreaker shape from improvement-087/091. Confirmed
`idx_audit_entity (entity_type, entity_id, created_at DESC)` already covers the window's
partition/order, so the rewrite is a single indexed pass instead of N per-row subquery probes —
not a hidden regression. Simplified away the old `filtered`/`f` two-CTE split and the
`innerOrderBy`/`outerOrderBy`/`.replace("al.", "f.")` alias hack it needed, since the new single
`numbered` CTE reuses the same `al` alias `FILTER`/`SORT_ALIASES` already assume. Verified against
the existing 6 `AuditLogRepositoryTest` cases (tied-`created_at` version/prev-id/prev-snapshot,
actor-filter) unchanged — they test observable behavior, not SQL shape, and all passed without
modification. Separately, `AuditReadService.getEntityActivity()`'s bare `100`-row literal was
extracted to `ENTITY_ACTIVITY_MAX_ROWS` with a one-line comment on the silent-truncation policy —
extraction only, YAGNI on speculative paging (per the issue's own guidance). Full suite: unit-tests
and integration-tests (127/127) both clean. **Batch H complete.**

✅ Done (2026-07-22): [improvement-102](issues/improvement-102-attachmentmediachangehook-zero-consumers.md)
and [improvement-103](issues/improvement-103-attachmentservice-api-surface-reduction.md) —
attachment API simplification, one PR. Deeper verification beyond both issue files (direct
whole-repo grep for every candidate method's callers) found more dead code than either issue
anticipated: `AttachmentService.delete(Long)` (the snapshot-capturing delete) had **zero callers
anywhere**, not even in tests, and `restoreToUrlsAndCapture` was dead across all **three** layers
(`AttachmentPort` interface, `DefaultAttachmentPort`, `AttachmentService`) despite being fully
wired through the SPI — removed entirely rather than folded into a flag parameter. Renamed
`deleteSkipSnapshot` → `delete` end-to-end (interface, impl, `AttachmentGallery.java` call site)
since it's now the only delete method. Merged the `*Dto`/entity twins
(`upload`/`uploadDto`, `addVideo`/`addVideoDto`, `getByEntityId`/`getByEntityIdDtos`,
`getByEntityAndUrls`/`getByEntityAndUrlsDtos`) into single DTO-returning methods —
`DefaultAttachmentPort` was confirmed the only real DTO consumer, so the entity variants added no
value once merged. Extracted `resolveVideoDescriptor(url)` to dedupe the yt-vs-embed branching
between `addVideo`/`addVideoTemp`. Deliberately **did not** introduce a `SnapshotCapture` enum for
`commitTempUploads`/`commitTempUploadsQuiet` as the issue's suggested shape proposed — after
removing the dead capturing `delete()`, that pair was the only remaining candidate, already
self-documenting by name, and `commitTempUploadsQuiet` is only ever called from tests; an enum
would have been a speculative abstraction for zero real ambiguity. Also deviated from the issue's
suggested "callers resolve `CurrentActorHook` themselves" shape for `restoreToUrls`'s actor
parameter — that would have pushed actor-resolution logic into `DefaultAttachmentPort`, violating
`platform-commons/CLAUDE.md`'s "`*PortImpl` — pure delegation only" rule; actor resolution stays
inside `AttachmentService`, matching every other method in the class. `AttachmentMediaChangeHook`
removed entirely from `platform-commons` (interface, `ObjectProvider` field, all 7
`notifyMediaChanged()` call sites) — annotated `marketplace-app/DECISIONS.md` ADR-035 rather than
only adding a new entry, since that ADR's "zero listeners is a valid degraded state" call is what
this issue overturned. Full suite verified: unit-tests and integration-tests (127/127) both clean;
full Playwright e2e --full --ux needed two reruns to get a clean signal — first run hit an
unrelated Timeline actor-picker scroll timeout, a retry of just that spec hit a different, also
unrelated login timeout, and a full clean rerun passed 49/49 with zero repeats of either failure,
confirming both were environment flakes rather than regressions from this attachment-only change.
**Batch M complete.**

✅ Done (2026-07-22): [improvement-104](issues/improvement-104-expandactivityfields-feature-envy.md)
and [improvement-105](issues/improvement-105-advertisementenrichservice-unify-dual-paths.md) —
audit-rendering simplification, one PR. Verification beyond the issue file found the duplication
was worse than described: the same null-safe "expand changes against the snapshot" three-liner was
copy-pasted **four** times, not two — inline in `TaxonActivityFieldsHookImpl` and
`AdvertisementActivityFieldsHookImpl`, and routed through `UserService.expandActivityFields()` →
`UserPort.expandActivityFields()` for both `UserActivityFieldsHookImpl` and
`UserSettingsActivityFieldsHookImpl`. Confirmed no sibling copy exists for `AuditActivityItemDto`
— `AuditActivityFieldsHook.expandFields()`'s signature only ever takes `AuditTimelineItemDto`, so
the issue's "give both DTOs the method" contingency didn't apply. Added
`AuditTimelineItemDto.expandedChanges()` as a narrow, documented exception to
`platform-commons/CLAUDE.md`'s "`*.dto` has no behavior" rule (see `platform-commons/DECISIONS.md`
ADR-021) — justified by the same-file precedent of `withChanges()` (a pure derivation over the
record's own fields, no service calls). All four call sites now read `item.expandedChanges()`;
deleted `UserService.expandActivityFields()` / `UserPort.expandActivityFields()` /
`UserPortImpl.expandActivityFields()` entirely, and removed the now-unused `UserPort` field from
both User-domain hooks. Separately, `AdvertisementEnrichService`'s mirrored
`mergeTimelineItem()`/`mergeActivityItem()` were unified behind one private `mergeChanges()`
worker, parameterized by a `skipMediaMergeIfUnchanged` boolean that preserves the one deliberate
behavioral difference (Activity skips the media entry when `attachmentSnapshotId` is unchanged
from the previous version; Timeline always merges it) — the other difference (Timeline's
entity-type guard; Activity's DTO has no entity type to guard on) stayed as each method's own
early-return, since it's inherent to the DTO shapes, not an arbitrary branch. Deliberately did
**not** unify `collectTimelineCategoryIds()`/`collectActivityCategoryIds()` — each is 4-5 lines,
and a generic adapter across the two different DTO types would have cost more readability than the
duplication itself (per the issue's own "if generic comes out less readable, don't force it"
guidance). Verified with unit-tests, integration-tests (127/127), and a clean full Playwright
e2e --full --ux run (49/49) — the existing Timeline/Activity diff assertions are the natural
characterization test for this exact enrichment path. **Batch N complete.**

✅ Done (2026-07-22): [improvement-029](issues/improvement-029-docs-drift-guard-and-hooks.md) and
[improvement-033](issues/improvement-033-quality-gate-skill-and-definition-of-done.md) — process
tooling, one PR, both re-scoped after empirical verification. For 029: measured the
incremental-compile hook's real cost in this sandbox before building it — `mvn compile -pl
query-lib -q` (smallest module, nothing to recompile) took ~29s, `mvn compile -pl marketplace-app
-q` (largest module, nothing to recompile) took ~95-108s, both stable across repeated runs. This is
the same class of ~100s "nothing to compile" Maven overhead `integration-tests/CLAUDE.md` already
documents for this sandbox — a hook firing after every `*.java` edit at that cost would be a net
productivity loss, not the "seconds" feedback the issue wanted, so it was **not built** (documented
here rather than silently dropped; likely fine on a normal developer machine, untestable in this
sandbox). Built only the docs-drift guard half: two new `PostToolUse` hooks (`Edit` and `Write`
matchers) in `.claude/settings.json` — fires when the edited `file_path` matches
`*-spring-boot-starter/*/db/*changelog*` (the real pattern shared by all 5 domain starters'
Liquibase changelogs, confirmed by listing every changelog path in the repo), printing a reminder
naming the specific owning starter's `CLAUDE.md` to check. Deliberately warning-only, not blocking
the next commit like the existing commit-approval hook — matches the issue's own "do the hook
first (immediate, local)" framing, full blocking would need session-spanning state-tracking
disproportionate to a first pass. Verified the shell logic in isolation against three realistic
sample JSON payloads (a real starter changelog match, a plain `.java` non-match, and
marketplace-app's own empty placeholder changelog correctly *not* matching since it lacks the
`-spring-boot-starter` path segment) before wiring it into the committed hook, then confirmed via a
live edit-and-revert against a real changelog file. For 033: confirmed `/ci` (improvement-059)
already chains unit → integration → e2e → Sonar in one command, exactly as the issue's own note
predicted — no new skill built. Recorded the Definition of Done as a new section in
`.claude/rules.md` (full suite green, `DECISIONS.md` updated if architectural, issue moved to
`completed/issues/`), referencing `/ci`/`scripts/ci.sh` instead of the never-built `/quality-gate`.
**Batch I complete.**

✅ Done (2026-07-22): [improvement-108](issues/improvement-108-ilike-wildcard-not-escaped.md) —
promoted to the top of the queue in the same day's full-backlog priority review (a real,
currently-confirmed correctness bug beats everything else waiting, which was all tech debt or
nice-to-haves). `SqlCondition.like()` now escapes `\` (first), `%`, and `_` before wrapping the
value in `%…%`, and `SqlOperator.LIKE_IGNORE_CASE` declares an explicit `ESCAPE '\'` clause so
Postgres knows which character is the escape marker — fixed once in `query-lib`, every consumer
(advertisement title, user name/email filters) benefits without its own change. Considered a
regex one-liner (`value.replaceAll("([%_\\\\])", "\\\\$1")`) as a more compact equivalent; kept
the three sequential `.replace()` calls since the backslash-must-go-first ordering constraint is
clearer spelled out than folded into a regex alternation. Added `SqlConditionTest`/`SqlOperatorTest`
cases for `%`/`_`/`\`/mixed inputs and the new clause text; updated the existing
`like_nonNull_wrapsValueWithPercent` test's expected clause. Recorded `query-lib/DECISIONS.md`
ADR-007 and updated the `like()` row in `query-lib/CLAUDE.md`. Caught a real verification trap
mid-fix: `~/.m2`'s installed `query-lib` jar was 2 hours stale relative to the source fix (it
isn't one of the starters `integration-tests/run.sh`'s auto-staleness-check watches), so the
first integration-tests run after the fix silently tested the *old*, unescaped behavior and
passed for the wrong reason — caught by comparing the jar's mtime against the source file's
before trusting the green run, forced a `mvn install -pl query-lib`, and reran. Verified with the
full suite: unit-tests (27/27 in query-lib), integration-tests (127/127) against the freshly
installed jar, and Playwright e2e --full --ux (49/49).

✅ Done (2026-07-22): [improvement-025](issues/improvement-025-leaf-ui-components-plain-classes.md)
(Batch J) — converted ~17 stateless leaf UI widgets from `@SpringComponent @Scope("prototype")`
beans implementing `Configurable`/`Initialization` to plain Java classes, executed in 4 phased
batches on `feature/leaf-ui-buttons-batch1` with a full Playwright run after each (per the issue's
own plan): Batch 1 — buttons (`UiPrimaryButton`, `UiTertiaryButton`, `UiIconButton`,
`DeleteActionButton`, `EditActionButton`, `OverlayBreadcrumbBackButton`), which also found and fixed
an unrelated pre-existing `LogoutDialog` race condition (`marketplace-app/DECISIONS.md` ADR-052).
Batch 2 — fields (`UiTextField`, `UiTextArea`, `UiEmailField`, `UiPasswordField`, `UiComboBox`,
`UiLabeledField`), preserving all 61 `data-testid`-dependent Playwright selectors byte-identical
(ADR-053). Batch 3 — structural/no-dep components (`EmptyStateView`, `DialogLayout`,
`OverlayLayout`); `PaginationBar` was reviewed here too but deliberately kept a Spring bean
permanently — it's read from a separately-invoked `refresh()` in three `View` classes and already
had a test mocking it as an injected collaborator, a materially different (and riskier) shape than
the other three (ADR-054). Batch 4 — `ConfirmActionDialog`, the last phase, with `I18nKey`
resolution moved to its four call sites; also found and fixed an unrelated pre-existing Playwright
flake in `fillActorPicker`'s `useSearch` path (`timeline.flow.js`) — missing `await
waitForVaadin(page)` after the picker dialog's search-button click let the cell lookup race the
server-side filter's async re-render, reproduced 3/3 times including against a freshly-reset
database before being traced and fixed; verified green twice in a row after the fix (ADR-055).
Final state across all four batches: `UserFormOverlayModeHandler`'s constructor dropped from 13 to
10 parameters; zero Spring/`Configurable`/`Initialization` scaffolding remains on any of the
converted widgets; unit-tests (72/72, including ArchUnit), integration-tests (127/127), and
Playwright e2e --full --ux (49/49, confirmed twice) all green on the final batch.

✅ Done (2026-07-23): [improvement-113](issues/improvement-113-query-elements-leaf-components-plain-classes.md)
(Batch L) — sibling refactor to improvement-025, found during a post-025 audit of the rest of the
Vaadin UI layer for the same anti-pattern: the entire `ui/query/elements/*` tree (the query-bar/
filter-panel widgets) still carried `@SpringComponent @Scope("prototype") + Configurable +
Initialization`, accounting for 8 of the remaining 21 `@Bean` declarations in
`MarketplaceUiConfiguration`. Converted in 6 dependency-ordered batches: dead-code removal
(`QueryComboField<T>`, `QueryNumberField` — both had zero real consumers anywhere, deleted rather
than converted); `SvgIcon` (zero deps); `SortIcon` (the one real exception — re-resolves its
tooltip dynamically on every direction change, so it keeps `I18nService` as a plain field,
`PaginationBar`-style, instead of the "resolve once, pass a `String`" template used everywhere
else — design decision discussed and confirmed with the user before implementation);
`QueryActionButton`+`QueryActionBlock`; `QueryInlineRow` (cascaded into `QueryBlock.filterRow()`'s
shared signature, `I18nKey`→`String`); and the remaining simple fields (`QueryTextField`,
`QueryLongField`, `QueryDateTimeField`, `QueryMultiSelectComboField<T>`). All three domain
`*QueryBlock` subclasses lost every `UiComponentFactory<T>` field this family required. See
`marketplace-app/DECISIONS.md` ADR-056. Verified with unit-tests (72/72, including ArchUnit),
integration-tests (127/127), and Playwright e2e --full --ux (49/49, first try — no recurrence of
the `fillActorPicker` flake fixed in the improvement-025 Batch 4 entry above).
