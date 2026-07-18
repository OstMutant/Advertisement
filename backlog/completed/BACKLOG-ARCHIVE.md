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
