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

✅ Done (2026-07-23): [improvement-115](issues/improvement-115-intellij-inspection-cleanup-pass.md)
— full triage of a project-wide IntelliJ IDEA inspection export (`/app/errors/*.xml`, 33 inspection
files), run in 4 ordered sub-passes. Batch 1 (safe mechanical): 10 unused imports,
`SequencedCollectionMethodCanBeUsed` ×20 (`.get(0)`→`.getFirst()`), diamond/method-ref, dangling
Javadoc, `FieldCanBeLocal` ×2, redundant suppression, `Collectors.toSet()`→`new HashSet<>()` ×2,
dead i18n property, `ClassCanBeRecord` ×2 (one Spring bean deliberately kept a class). Batch 2
(bugs): fixed a real gap where `AdvertisementSaveService.save()`'s update path could NPE on a
concurrent soft-delete race (no guard existed for `before == null`, unlike the sibling `delete()`
method — added a matching guard + regression test); migrated Testcontainers'
`org.testcontainers.containers.PostgreSQLContainer` (deprecated, forRemoval) to the new non-generic
`org.testcontainers.postgresql.PostgreSQLContainer`; `setAcceptedFileTypes`→`setAcceptedMimeTypes`;
found and fixed an unrelated pre-existing build break in `integration-tests/pom.xml`
(`junit-jupiter-api` resolved to `runtime` scope transitively, invisible to `src/main` compilation
— added an explicit `compile`-scope dependency, see `integration-tests/DECISIONS.md` ADR-011). The
Vaadin `@Theme` deprecation was carved out into its own deferred issue,
[improvement-116](issues/improvement-116-vaadin-theme-annotation-migration.md) — needs a full
visual-regression pass, not a mechanical fix. Batch 3: added missing `@NonNull`/`@jspecify.NonNull`
across 20 real gaps (6 more were a `NullableProblems` inspection quirk on an interface that already
followed the project's `@NonNull` convention — left alone). Batch 4 (dead code, largest): removed
~40 confirmed-dead methods/fields (unused i18n enum constants, orphaned package markers, dead
repository/service methods) while explicitly excluding known false positives (`*CrudRepository`
"not implemented" — Spring Data JDBC proxies; `ArchitectureRulesTest` fields — ArchUnit reflection;
`@PostConstruct`/`@ClientCallable`-invoked methods); simplified several SPI methods with
provably-dead parameters end-to-end (`AuditPort.captureUpdate`'s `before`, `AuditActivityEnrichHook`'s
`subjects`/`entityRef`, `AuditDomainHook.resolveDisplayName`'s `entityType` — see
`platform-commons/DECISIONS.md` ADR-022); removed `UserPort.restoreToSnapshot()` and
`AttachmentPort.getMediaSummary(EntityRef)` (both confirmed to have zero real callers, per explicit
user confirmation); and collapsed `AbstractViewOverlayModeHandler`'s secondary/tertiary-tab
machinery, dead since the Timeline-tab extraction — tracing `activate()`'s logic showed the
tab-rendering branch was already unreachable, not merely unused (see `marketplace-app/DECISIONS.md`
ADR-057). Verified after every sub-pass: unit-tests (73/73), integration-tests (125/125 final,
down from 127 after deleting `UserServiceRestoreTest.java`'s 2 tests for the removed feature), and
a full Playwright e2e --full --ux run (a first run hit 3 cascading failures traced to one transient
login timeout in spec 03 skipping fixture-creating tests that spec 04/05 depended on; a full
re-run confirmed 49/49 green, not a regression).

✅ Done (2026-07-24): [improvement-072](issues/improvement-072-uicomponentfactory-generics-design-debt.md)
— resolved all three generics/type-safety design-debt items. (1) `UiComponentFactory<T extends
Configurable<T, ?>>` bound enforced at compile time; the 10 non-`Configurable` consumers
(`AuditActivityListRenderer`, `AuditHistoryListRenderer`, `AuditHistoryRowRenderer`,
`AuditActivityRowRenderer`, `AttachmentGalleryService`, `AttachmentGallery`'s `thumbnailFactory`,
`CardMediaLightbox`'s `viewerFactory`, `AttachmentThumbnail`, `UserPickerField`, and the three
`AdvertisementCardView`/`AdvertisementFormOverlayModeHandler`/`AdvertisementViewOverlayModeHandler`
`galleryServiceFactory` fields) migrated to plain `ComponentFactory<T>`; `OverlayFormBinder`'s
single shared raw-typed factory bean split into 4 concrete beans, one per `EditDto` — see
`marketplace-app/DECISIONS.md` ADR-058. (2) `AuditReadService`'s raw `List`/`AuditActivityEnrichHook`
dispatch investigated and deliberately kept as-is — verified directly (not just argued) that
dropping the hook interface's generic parameter to eliminate the dispatch-loop cast only pushes an
unchecked cast into `AdvertisementEnrichService`, which previously needed none; the raw-type +
`@SuppressWarnings` idiom is the accepted, pragmatic form for this "runtime-dispatched
heterogeneous collection" shape — see `platform-commons/DECISIONS.md` ADR-023. (3) `Class<T>
targetClass` type token added to `AuditPort.getSnapshotContent()`/`AuditDomainHook.castIfKnown()`;
`AuditDomainHookImpl` now uses `targetClass.cast(...)` in a `try`/`catch (ClassCastException)` —
zero `instanceof`, zero `switch`, zero `@SuppressWarnings("unchecked")` — see
`platform-commons/DECISIONS.md` ADR-023. Along the way, `bash scripts/ci.sh`'s e2e/sonar stages
were found to have no retrievable failure reason once their container was torn down; fixed by
having both stages write their own `run.log` (`scripts/ci/DECISIONS.md` ADR-006), which then
surfaced a real, separate root cause for a recurring `e2e` failure — the isolated CI Postgres
volume was never recreated between runs and had been stuck since before commit `3063048d`
retrofitted `deleted_at`/`deleted_by` onto an already-applied `01-user-schema` changeset (Liquibase
never re-runs a recorded changeset); fixed by having `teardown_e2e_stack()` also remove
`CI_DB_VOLUME`/`CI_MINIO_VOLUME` (ADR-007), scoped as a non-prod fix since this project has no
deployed database that could hit the same permanent-gap risk. Verified: unit-tests (73/73),
`bash scripts/ci.sh --sandbox` full chain — unit/integration/e2e all PASSED (e2e was the one that
surfaced and then confirmed-fixed the Postgres volume issue); `sonar` stage still fails, but solely
on the pre-existing, separately tracked `new_coverage` gap
([improvement-114](issues/improvement-114-sonar-jacoco-coverage-not-wired.md) — JaCoCo was never
wired into the scanner, unrelated to this issue's changes; `new_violations` and
`new_duplicated_lines_density` both passed clean).

✅ Done (2026-07-24): [improvement-117](issues/improvement-117-f01-deep-links-og-tags.md) — F-01
deep links + Open Graph meta tags, the product roadmap's Phase 1 community-migration mechanic.
Four passes, each with its own `marketplace-app/DECISIONS.md` ADR: (1) ADR-059 —
`AdvertisementDeepLinkView` (`@Route("ads")`) + `OgMetaRequestListener`
(`IndexHtmlRequestListener`, Caffeine-cached) + `HtmlExcerptUtil` extraction; no `SecurityConfig`
change needed since `/ads/**` was already covered by `anyRequest().permitAll()`. (2) ADR-060 —
"Share" button (`AppLinkService` + `ShareUtil`: native Web Share API on mobile, clipboard-copy
fallback on desktop) on both the card and view overlay. (3) ADR-061 — `sitemap.xml`
(`SitemapController`, pages through the existing `AdvertisementPort` with no new port method,
genuine new endpoint so it does get its own `SecurityConfig` permit entry); found and fixed an
unrelated bug while verifying — `deploy.sh` never set `APP_PUBLIC_URL`, so local links pointed at
the container's internal port instead of the externally-published one. (4) ADR-062 — found a real
bug via `curl`-based crawler simulation (`twitter:card` used the wrong HTML attribute, `property=`
instead of Twitter's required `name=`), fixed alongside `og:image` cache-busting versioning,
JSON-LD `Product` markup, and full browser History API sync (`pushState` on open/close,
`Back`/`Forward` via `History.setHistoryStateChangeHandler`) — verified with a real
`page.goBack()` in Playwright. Every item verified via one cumulative, incrementally-extended
Playwright test; full e2e suite 50/50 after each pass, unit-tests 73/73. The one inherently
non-automatable item — sharing a real `/ads/:id` link into an actual Facebook post and Telegram
chat, needs a public URL this sandbox doesn't have — carved out into
[improvement-118](../issues/improvement-118-f01-real-world-og-preview-verification.md).

✅ Done (2026-07-25): [improvement-046](issues/improvement-046-list-stability-under-concurrent-edits.md)
— list stability after edit, option E (client-side variant) + a lightweight "N changes — Refresh"
banner, chosen after external research showed the dashboard "live/paused" pattern makes E far
cheaper than the server-side snapshot originally costed. `AdvertisementOverlay`/`UserOverlay`/
`TaxonOverlay` each split their single `onSaved` callback into `onUpdated` (splice one row/card in
place after an EDIT save, no refetch), `onListChanged` (full refresh, CREATE only — row count
changes), and `onClosed` (Advertisement/User only — cheap `count()`-only staleness check driving
the banner; no banner for Taxon, which has no pagination and so never had the underlying "wrong
page" symptom, converted only for consistency of approach). See `marketplace-app/DECISIONS.md`
ADR-063 for full per-domain detail, including a real bug found and fixed mid-implementation
(Taxon's CREATE overlay briefly auto-closed after save, breaking 3 Playwright tests that expect it
to stay open until an explicit close). Verified: unit-tests 73/73 (incl. ArchUnit), full Playwright
`e2e --full --ux` 50/50 on the post-fix re-run. Deliberately deferred: option D (keyset/cursor
pagination), still the correct eventual fix for the deeper, unrelated instability from *other*
users' concurrent inserts/deletes — tracked inside the issue file, not split out separately.

✅ Done (2026-07-25): [improvement-120](issues/improvement-120-advertisement-user-hard-fk-coupling.md)
— removed the last hard SQL-level FK coupling between starters (`advertisement` → `user_information`,
3 constraints: `created_by` RESTRICT, `updated_by`/`deleted_by` SET NULL), found during F-02
planning review. Edited `01-advertisement-schema.xml` in place (pre-prod, no incremental
changeset). Traced the constraint as load-bearing before removing it — `UserService.cleanup()`'s
retention-purge job relied on the DB blocking a hard delete when `advertisement` rows still
referenced the user. Replaced with two new `AdvertisementPort` methods split by the two
constraints' different original semantics: `findOwnerIds()` (mirrors RESTRICT, blocks purge while
the user still owns an ad) and `clearActorReferences()` (mirrors SET NULL, nulls
`updated_by`/`deleted_by` instead of blocking). Verified no UI regression, not assumed — neither
column is ever exposed to the UI (`AdvertisementInfoDto` has no such fields), and
Activity/Timeline actor-name resolution is a separate mechanism (`audit_log.actor_id`) that never
had a DB constraint either. `created_by` (the only actor reference actually shown in the UI) ends
up *more* protected than before. See `marketplace-app/DECISIONS.md` ADR-064. Bonus, per user
request: `deploy.sh` now auto-recovers from the resulting local-dev Liquibase checksum mismatch
(edited-in-place changeset vs. an already-applied old version) — detects the specific
`ValidationFailedException` signature and auto-wipes/retries once instead of requiring a manual
`--reset`, verified live by deliberately corrupting a dev DB's stored checksum and confirming
recovery. Hit and fixed two real bash pitfalls along the way (`$?` after `if ! cmd; then` capturing
the `if` test's own status, not `cmd`'s; the `ERR` trap firing regardless of `set +e` for any
command outside a tested `if`/`&&`/`||` construct) — see `scripts/DECISIONS.md` ADR-010. Verified:
unit-tests 74/74, integration-tests 126/126 (full suite), Playwright `e2e --full --ux` 50/50
(twice).

✅ Done (2026-07-25): [improvement-119](issues/improvement-119-f02-city-dictionary-geo-filter.md)
— F-02 city dictionary + geo filter, product roadmap Phase 1 item #2. Added `TaxonType.CITY`
(`platform-commons`) reusing the existing `taxon_assignment` mechanism — zero schema changes
anywhere. Caught (by reading the actual source first, not assuming) that
`TaxonAssignmentService.replaceAssignments()` diff-replaces *all* taxon types in one call, so
category ids and the city id must be unioned into one `Set<Long>` before a single call, and that
`getForEntity(s)`'s result is type-unfiltered, requiring every consumer (save-service snapshot
builder, enrich-service, filter resolver) to split by `TaxonDto.getType()` client-side. Pre-empted
the earlier categoryIds raw-id-fallback bug for `cityTaxonId` from the start
(`AdvertisementEnrichService.resolveCity()`), then found via unit tests that both `resolveCity()`
and the pre-existing `resolveCategories()` were appending a spurious empty diff entry when the
*other* field's ids were the only thing that resolved a non-empty `nameById` — fixed by guarding
the append on the field's own ids actually being non-empty/non-null. New, separate `City*` admin
classes by analogy with `Taxon*` (not a parameterized shared class — `TaxonOverlay` is a
`@UIScope` singleton, needs two distinct instances for two simultaneous tabs), new "Cities" tab in
`ReferenceDataView`. Single-select `ComboBox<TaxonDto>` on the advertisement query block/form/view/
card (categories use `MultiSelectComboBox` — first single-select precedent in this codebase).
Playwright: extended existing tests rather than new spec files (per explicit instruction) — city
admin lifecycle folded into spec 03's category tests, `city`/`cityToSet` params added to spec 04's
create/edit flows, city filter folded into spec 05's seed/filter test. Found and fixed two more
real bugs via the Playwright run itself: `openReferenceDataTab()` assumed the "Categories" sub-tab
was always showing, but Vaadin's `Tabs` retains its last selection across visibility toggles, so
visiting the new "Cities" sub-tab once broke every later call — fixed by explicitly reselecting
Categories every time; and the new `cityToSet` step added an extra save to an existing edit-
lifecycle test, shifting a downstream activity-version assertion by one. See
`marketplace-app/DECISIONS.md` ADR-065 and `taxon-spring-boot-starter/DECISIONS.md` ADR-003's
update. Verified: unit-tests 75/75, integration-tests 128/128, Playwright `e2e --full --ux` 50/50
(after both bugfixes above).

✅ Done (2026-07-27): [improvement-122](issues/improvement-122-f03-listing-types.md) — F-03
listing types (Offer/Request/Product), product roadmap Phase 1 item #3, the last piece of the
"Shareability foundation" gate. Unlike F-02's city facet, a genuine new `advertisement.ad_kind
VARCHAR(20) NOT NULL DEFAULT 'OFFER'` column (mandatory, closed set, no admin dictionary needed) —
new `AdKind` enum in `platform-commons`, added to the existing (never-released)
`01-advertisement-schema.xml` changeset. First use of Vaadin's `RadioButtonGroup` in this codebase
(mandatory single-select, always visible — neither `ComboBox` nor `MultiSelectComboBox` fit).
Caught a Binder `readInitialValues()` default-value hazard by reasoning before writing code, not by
a failing test: setting the default on the widget directly would be silently overwritten, so it's
set on `AdvertisementEditDto` instead. Playwright's own run then surfaced four real bugs: (1) the
activity diff showed the raw enum name (`"OFFER"`, `"PRODUCT"`) instead of a localized label — fixed
via `AdvertisementEnrichService.resolveAdKind()`, which — unlike `resolveCategories()`/
`resolveCity()` — only relabels an entry `diff()` already produced instead of manufacturing one,
since listing type (unlike category/city) is never absent and would otherwise inject a "Listing
type" line into every single activity row; (2) a second `MultiSelectComboBox` filter on the same
query block broke the pre-existing category filter's overlay-visibility wait, since a stale, hidden
overlay from an earlier combo can still be the one a bare `.first()` locator resolves to — fixed by
waiting on each combo's own `opened` property instead; (3) the default listing-type badge assertion
hardcoded the English label regardless of the logged-in user's actual (per-account, persisted) UI
locale; (4) an exact per-type filter-count assertion in spec 05 was inherently fragile, since
(unlike optional category/city) every advertisement always has some listing type, so leftover
non-seed ads from earlier specs always inflate one of the three buckets — switched to `>=` (same
idiom `verifyDateRangeFilters()` already used for this exact class of problem). See
`marketplace-app/DECISIONS.md` ADR-066. Verified: unit-tests 77/77, integration-tests unaffected
(schema/repository-only change), Playwright `e2e --full --ux` 50/50.

✅ Done (2026-07-27): [improvement-125](issues/improvement-125-overlay-accent-color-sync.md) — synced
the view-overlay's accent border color with `AdKind` (advertisements) / `Role` (users), matching
the already-correct card left-border / role-badge colors that existed only in the list view, not
the detail overlay. `AdvertisementViewOverlayModeHandler`/`UserViewOverlayModeHandler` each gained
one modifier class name; `advertisement-overlay.css`/`user-overlay.css` gained the corresponding
CSS rules reusing the exact color variables the badges already used — no new colors introduced.
Users grid row border explicitly deferred, out of scope. Extended (not added) Playwright coverage:
`assertViewOverlayHasAdKind` (advertisement flow) and `runPromoteUserFlow` (user promotion flow)
now also assert the accent-border modifier class. Implemented via `/autopilot`. Along the way,
found and fixed an unrelated real bug blocking the verification deploy: the repo-root `.env` had
CRLF line endings, silently breaking `deploy.sh`'s/`reset.sh`'s manual `.env` parser (a trailing
`\r` on every parsed value) and hanging `deploy.sh` indefinitely on the MinIO health check — see
`scripts/DECISIONS.md` ADR-011. Verified: full Playwright `e2e --ux` run, 37/37 non-skipped passed.
**Reopened same day, now fully closed (2026-07-27):** Phase 2 — `.attachment-gallery` (view mode
only; users have no gallery) synced to `AdKind`, same modifier-class pattern as Phase 1; "Listing
type" label renamed to "Advertisement kind" across all 4 English and 4 Ukrainian ("Тип оголошення"
→ "Вид оголошення") keys. Phase 3 — the view-mode header strip (`.overlay__view-card-header`,
shows "Advertisement"/"View" text) synced to `AdKind`/`Role` the same way; edit mode
(`.overlay__form-card-header`, always blue) deliberately left untouched since `AdKind`/role can
change interactively before save and live-updating it is a separate, bigger feature not undertaken
here. Playwright coverage extended further: `assertViewOverlayHasAdKind`/`runPromoteUserFlow` now
assert actual computed `getComputedStyle` colors (not just class-name presence) via a new shared
`assertComputedColor` helper in `_helpers.js`, since a class being present doesn't by itself prove
the CSS rule actually won. Verified: full Playwright `e2e --full --ux`, 50/50 passed.
**Phase 4 (2026-07-28, closes this issue):** dialed the whole thing back further per direct UX
feedback — removed the header/gallery background fill (Phase 3's tinted band), then removed the
header/gallery *text* color too — the accent now lives only in the border, the calm signal the
user settled on after seeing the fuller-color version and finding it too busy. Verified: full
Playwright `e2e --full --ux`, 50/50 passed, plus direct visual confirmation via screenshot.

✅ Done (2026-07-28): [improvement-126](issues/improvement-126-timeline-activity-diff-findings.md) —
Timeline row header no longer repeats the entity's display name (already shown in full in the
always-visible field-dump body) — `AuditTimelineRowRenderer`/`AuditTimelineListRenderer` dropped
`nameSpan()`/`displayNames` entirely, body untouched by design. Phase 2 (found the same day):
actor + timestamp right-aligned as one adjacent group in both Timeline (`.activity-feed-row`, wrapped
in a new `.activity-feed-right-group` div since the editor badge is conditionally present) and the
per-entity Activity tab (`.entity-activity-meta`, fixed a real `width: 100%` bug — the meta div was
shrink-to-fit, so `margin-left: auto` on the actor span was only reaching the edge of that narrow
box, not the actual card edge). A first-pass geometry-only Playwright assertion had gone green while
the Activity tab was still visually broken (it measured against the same too-narrow container that
was the bug), caught only by looking at an actual rendered screenshot directly — added adjacency
checks (`timeBox.x - (actorBox.x + actorBox.width) < 20px`) to the tests afterward so a regression
back to "technically right-aligned but visually far apart" would actually fail. Verified: full
Playwright `e2e --full --ux`, 50/50 passed, confirmed visually via screenshot both before and after
the wrapper-group fix. [improvement-127](issues/improvement-127-entitytype-localization-taxon-color.md)
(EntityType i18n + TAXON badge color) carved out from this fix, completed separately same day — see below.

✅ Done (2026-07-28): [improvement-127](issues/improvement-127-entitytype-localization-taxon-color.md) —
`EntityType` Timeline badge (`AuditTimelineRowRenderer.typeSpan()`) and the Timeline "Entity type"
filter dropdown (`TimelineQueryBlock`, found widening scope during investigation — same raw-enum-
name gap) now show localized labels (`Advertisement`/`User`/`User Settings`/`Category`, EN+UK) via
a new `I18nKey.forEntityType(EntityType)` mirroring `forAdKind(AdKind)`'s shape, instead of the raw
Java enum name. `TAXON` also got its own badge color — brand-new teal
(`--app-status-entity-taxon-bg`/`-text`), not reused from any existing status/action color, added
to `activity-feed.css` alongside the existing advertisement/user/user_settings modifiers. Wording
and color both confirmed with the user before implementing; executed end-to-end via `/autopilot`.
Verified: `unit-tests.sh` (77/77), `integration-tests.sh --sandbox` (130/130), full Playwright
`e2e --full --ux` (50/50), plus direct visual confirmation of the new teal `Category` badge via
screenshot.

✅ Done (2026-07-28): [improvement-002](issues/improvement-002-snapshot-schema-versioning.md) —
snapshot schema versioning for all three JSON-persisted blobs in the system. Landed as the
prerequisite for F-04/improvement-124 (first new snapshot-bearing domain since this issue was
filed). Went through two intermediate designs before the final one — a reflection-based
`@SchemaVersion` annotation + `JsonNode` tree-parsing with a legacy-shape fallback for
`attachment_snapshot.changes_summary`, then a shared `SchemaVersionCheck` tree-reading helper —
both reverted per direct user feedback favoring a genuinely bound field over reflection/tree-
parsing, and because this app has never run in production (no real legacy-shaped data to protect
against). Final design: `AdvertisementSnapshotDto`/`TaxonSnapshotDto`/`UserSnapshotDto`/
`SettingsSnapshotDto` and `AttachmentMediaChange` each gained a real `int schemaVersion` record
component (last position) plus a `SCHEMA_VERSION` constant and a second, non-canonical constructor
matching the old parameter list so every existing call site kept compiling unchanged;
`UserSettingsDto` (a Lombok builder class, not a record) got the same field via `@Builder.Default`
directly, no extra constructor needed. `AuditableSnapshot.schemaVersion()` is now a plain abstract
interface method. Full rationale and the two rejected designs: `platform-commons/DECISIONS.md`
ADR-024. Verified: `unit-tests.sh` (77/77), `integration-tests.sh --sandbox` (133/133, incl. 4 new
schema-version tests), full Playwright `e2e --full --ux` (50/50) — the first two full-suite runs
surfaced an unrelated, pre-existing timing fragility in `runSubmitLoginFlow`
(`playwright/e2e/_flows/auth.flow.js`): an 8s timeout was too tight for `LoginDialog`'s full
`ui.getPage().reload()` on login (not an in-place push update) under sandbox load. Root-caused via
repeated full-suite runs (zero server-side exceptions any run, different unrelated failure each
time) before fixing (wait for `networkidle`, 15s timeout).

✅ Done (2026-07-28): improvement-128 — Activity/restore panel redesign, filed and fully completed
the same day. The 1-content-tab + 1-Activity-tab pattern (`buildContentWithActivity()`) didn't
generalize past one content tab, surfaced while planning improvement-124's 3-tab Account overlay.
Replaced with one shared `EntityActivityOverlay` (`ui/views/components/audit/`) — a stacked nested
overlay, not a tab, with a real multi-segment breadcrumb (`Home`/list-view label / form-section
label / current) via new `OverlayLayout.setBreadcrumbLinks()`. Piloted on Settings first (own
`SettingsActivityOverlay`, later deleted once generalized), then rolled out to Advertisement,
Taxon, City, and User the same day, deleting `AbstractFormOverlayModeHandler`'s dead tab machinery
once all five callers migrated. Two real bugs caught by the user testing the running app mid-pilot
(X wired to the wrong target; a doubled/uneven breadcrumb separator) and one real bug caught by an
explicit stale-CSS-reference sweep before the final rollout run (`.settings-activity-*` classes
still referenced after Settings moved onto the generic component). Full rationale, both correction
rounds, and the rollout details: `marketplace-app/DECISIONS.md` ADR-067,
`completed/issues/improvement-128-activity-restore-panel-redesign.md`. Verified (final, full
rollout): `unit-tests.sh` 77/77, `integration-tests.sh --sandbox` 133/133 (no schema/repository
changes — pure UI refactor), Playwright `e2e --full --ux` 50/50. Unblocks improvement-124, which
can now call `EntityActivityOverlay.openFor()` directly for its Account overlay's 2 history icons.

✅ Done (2026-07-29): improvement-121 — superseded by improvement-132. The new `.claude/skills/
deep-review/` skill's first full-mode run (9-agent, all 9 modules) individually re-verified every
one of improvement-121's original 24 findings against current code before merging: 18 still
accurate (folded into improvement-132's module sections), 6 resolved as stale/invalid/already-fixed
(a `vaadin-grid`-free `BaseActionButton` extraction already landed; the tab-based activity pattern
one item described was deleted entirely by the same-day breadcrumb/ADR-067 refactor; a SQL-constant
-extraction suggestion contradicted this project's own documented `@SuppressWarnings("java:S1192")`
convention; two doc-drift items were already fixed; one query-lib finding was independently
re-derived, not duplicated). improvement-132 also added 13 newly-found items (including one live
i18n bug — `AdvertisementService.findById()` hardcoding English category names on the detail view).
Full reconciliation table: `completed/issues/improvement-121-solid-dry-review-findings.md`'s
supersession banner, `completed/issues/improvement-132-full-repo-solid-dry-review-2026-07-29.md`'s
"How this was found" section.

✅ Done (2026-07-31): improvement-132 — all 11 execution batches resolved. Batches A, B, C, D, F,
G, H, I, J, K (items 1-11, 13-15, 17-31) fixed directly across 2026-07-30/31, each its own commit
(see this file's earlier entries and each module's `DECISIONS.md` for individual batch rationale —
notably ADR-014 in `attachment-spring-boot-starter` for Batch I's corrected `AttachmentVideoUtil`
shape, ADR-025 in `platform-commons` for Batch G's corrected `UserSettingsService` instance-method
shape). Batch E (item 12, `TaxonFormOverlayModeHandler`/`CityFormOverlayModeHandler` pure
duplication) needs a design decision rather than a mechanical edit, so it was deferred to
`issues/improvement-133-deferred-oversized-review-findings.md` entry 8 for later analysis instead
of holding this issue open indefinitely. Full detail:
`completed/issues/improvement-132-full-repo-solid-dry-review-2026-07-29.md`.

✅ Done (2026-07-31): improvement-134 — additive AI-navigation/context-efficiency layer, filed and
implemented same day via `/autopilot` once the spec was approved. `docs/ai/adr-index.md`
(mechanically generated by `scripts/ai/generate-adr-index.sh` from every `DECISIONS.md`'s own
`## ADR-NNN:`/`**Status:**` text, 174 entries across 12 files), `docs/ai/context-loading.md`,
`docs/ai/flows.md`, and `docs/ai/README.md` (the layer's entry point). Explicitly rejected with
evidence: a `module-index.md` (duplicates `docs/architecture/03-bounded-contexts.md`) and a
`database-ownership.md` (duplicates `04-database-erd.md`) — see `scripts/ai/DECISIONS.md` ADR-001.
Mandatory hooks wired into `.claude/commands/decision.md`/`feature.md`/`sync-docs.md`,
`.claude/rules.md`, and root `CLAUDE.md`; while touching `decision.md`/`sync-docs.md`, also fixed
two pre-existing bugs the audit surfaced (a stale ADR-authoring template and a `find -maxdepth 2`
that silently skipped nested `scripts/*/DECISIONS.md` files). 4 confirmed stale
`docs/architecture/*.md` items corrected (`AttachmentMediaChangeHook` references left over from
improvement-102, FK-coupling/optional-deps sections left over from improvement-120/improvement-011).
`/code-review --fix` (8-angle, high effort) caught and fixed a real bug in the generator itself
(multi-line ADR `Status:`/heading text was silently truncated) plus two more instances of the same
stale-doc pattern Phase 3 was already fixing. Full detail:
`completed/issues/improvement-134-ai-navigation-context-efficiency-layer.md`.

✅ Done (2026-08-04): improvement-137 — new `.claude/skills/doc-standards/SKILL.md` (canonical-
ownership table + fact-vs-constraint test + pre-write checklist) plus a repo-wide documentation
dedup pass, executed via `/autopilot` with Explore-agent-cluster discovery (4 parallel agents, one
per file cluster). Fixed: stale "9 modules"/table/SPI counts across `docs/architecture/*.md`
(including a fuller regeneration of `01-module-dependencies.md`'s graph/table to add the missing
`provider-profile-spring-boot-starter` node), a `RoleChecker`/`OwnershipChecker` duplication in
`marketplace-app`, a missing `UserEditableFields` README entry, and ADR/issue-format restatements
in `.claude/commands/sync-docs.md`/`deep-review/SKILL.md` collapsed to references. Added
`scripts/ai/check-hardcoded-counts.sh` (new CI gate, `scripts/ai/DECISIONS.md` ADR-002) mirroring
`check-adr-index-freshness.sh`/`check-flows-completeness.sh`. Deliberately deferred (too large for
a dedup pass, cross-referenced to `improvement-138` instead): `02-spi-map.md`'s SPI diagram still
names a removed/renamed hook and is missing the `UserPort` split/`ProviderProfilePort`;
`03-bounded-contexts.md`/`04-database-erd.md`'s deeper content predates `taxon`/`provider-profile`.
`/code-review --fix` (high effort, 8 finder angles + 8 verifiers) caught and fixed 8 confirmed
findings, including two real regressions this same session introduced (a dead section
cross-reference in `marketplace-app/README.md`; an incorrect "compile" scope claim for
`taxon`/`provider-profile` in `01-module-dependencies.md`, actually `runtime`-scoped) and a stale
"7.1/10" architecture score synced to the real current 7.7/10 from `08-scorecard.md`. Companion
**improvement-139** (`deep-review` full-mode's module scope list missing
`provider-profile-spring-boot-starter`) fixed in the same change. `bash scripts/unit-tests.sh`:
108/108 passed. Full detail: `completed/issues/improvement-137-doc-standards-skill-and-dedup-cleanup.md`,
`completed/issues/improvement-139-deep-review-missing-provider-profile-module.md`.

✅ Done (2026-08-04): improvement-140 — documentation shrink pass finishing what improvement-137
deferred, executed via `/autopilot`. Deduped restated facts (DAG/no-cycles, "marketplace-app
depends on all starters", "Vaadin only in marketplace-app", AccessEvaluator-resolved, optional-
deps-not-guarded, `UserPort`'s 4-way split, `query-lib`'s API tables, SPI-implementation lines) to
one canonical location each across `docs/architecture/*.md`/starter `README.md`/`CLAUDE.md` pairs.
Replaced improvement-137's own hedges with real fixes: `02-spi-map.md` rewritten (removed the
staleness banner, `AttachmentMediaChangeHook`, renamed `AttachmentAuditHook`→`AttachmentAuditPort`,
added `UserPort`'s split + `ProviderProfilePort`); `docs/architecture/README.md`'s Key Metrics
table recomputed with real numbers instead of "not re-verified this pass"; `03-bounded-contexts.md`
gained a full Provider Profile domain section; `04-database-erd.md` gained `user_preferences`/
`provider_profile` tables and corrected `user_information` (settings/locale moved out per
ADR-070). Consolidated 2 verbatim starter-wide constraints ("No Vaadin dependency"/"own Liquibase
changelog") into root `CLAUDE.md`, trimmed from all 6 starter `CLAUDE.md`s; `doc-standards/SKILL.md`
gained a clarification that an identical-everywhere constraint is really a fact. Compressed
`platform-commons/DECISIONS.md` ADR-025 item 20's 18-line narrative to 7 lines. Trimmed
`BACKLOG.md`'s "At a glance" from a 54-line narrative wall to a short completed-list + active-work
summary. Net result: **+32 lines (+0.4%)** vs. the 7,434-line baseline, not a shrink —
`CLAUDE.md`/`BACKLOG.md` shrank as intended (-38/-27) but `docs/architecture/*.md` grew (+113)
because the real fixes filled genuine content gaps (Provider Profile section, 2 missing ERD
tables) rather than restating existing content; reported plainly rather than reframed as a shrink
that didn't happen. A follow-up `/code-review --fix` pass (8 parallel finder agents + verify)
caught 6 more pre-existing stale facts sitting directly adjacent to the edited lines
(`AccessEvaluator`'s description still named the pre-split `UserPort` instead of
`UserAuthorizationPort`; "optional deps not guarded" and "Advertisement→User FK coupling" both
already resolved but still described as open in 3 files each; a stale `I18nKey.java` line count;
a "largest file" column dropped with a pointer that delivered nothing) and fixed all of them
directly. `bash scripts/unit-tests.sh`: 79/79 passed;
`check-adr-index-freshness.sh`/`check-flows-completeness.sh`/`check-hardcoded-counts.sh`: all
pass. Full detail: `completed/issues/improvement-140-documentation-shrink-and-dedup-completion.md`.

✅ Done (2026-08-04): improvement-141 — new standing rule (`.claude/rules.md`): current-state
documentation (`CLAUDE.md`, `README.md`, `docs/architecture/*.md`, `docs/ai/*.md`, skill/command
`.md` files, `.sh` script comments) never cites an `improvement-NNN`/`goal-NNN`/`feature-NNN`
ticket and never carries dated "resolved"/"corrected \<date\>" narrative about a prior state —
history lives only in `backlog/completed/`, discoverable via `git blame`/keyword grep, not
embedded forward-links. `DECISIONS.md` keeps its append-only ADR character but likewise drops the
issue-number citation from every entry. Executed across 54 files: 11 "✅ RESOLVED" blocks removed
from `docs/architecture/*.md`; all 8 `CLAUDE.md`, 5 `README.md`, `deep-review` skill, 3 commands,
8 `.sh` scripts, and all 12 `DECISIONS.md` (largest: `marketplace-app/DECISIONS.md`'s 113
references, delegated to a subagent following the pattern already proven by hand on the other 11
files, then independently spot-verified). A follow-up sweep (Phase 9) caught the same "dated
narrative" smell without an attached ticket number in 9 more files (`playwright/CLAUDE.md`/
`README.md`, `query-lib/README.md`, `integration-tests/CLAUDE.md`/`README.md`,
`marketplace-app/README.md`, `advertisement-spring-boot-starter/README.md`, `scripts/README.md`).
`docs/ai/adr-index.md` (180 entries) and `architecture-model.json`/`architecture-map.html`
regenerated; all CI freshness gates (`check-adr-index-freshness.sh`/`check-flows-completeness.sh`/
`check-architecture-model-freshness.sh`/`check-hardcoded-counts.sh`) and `bash
scripts/unit-tests.sh` (79/79) green. Full detail:
`completed/issues/improvement-141-strip-issue-references-from-current-docs.md`.

✅ Done (2026-08-06): improvement-143 — the `docs/architecture/05-08-*.md` mechanization batch
extracted from improvement-138, executed end-to-end via `/autopilot`. All seven planned pieces
landed: SonarQube metrics (`ncloc`/`complexity`/`cognitiveComplexity`/`codeSmells`/
`javaFileCount`) and ArchUnit `componentDependencyMetrics()` (Efferent/Afferent Coupling,
Instability, Abstractness) on each module's Code Metrics section; 6 new `@ArchTest` rules added to
`ArchitectureRulesTest` (14 total) closing real gaps against `.claude/rules.md`/module
`CLAUDE.md` text (starter-to-starter imports, marketplace-internal-impl imports, `*Util`
non-instantiability, `*Config`/`@Configuration`, `MessageSource` confinement, package-level cycle
freedom); a live "Architecture Checks" section (real grep-based coupling verification) plus
"Largest Java Files"/"Constructor Injection"/"Largest Packages" tables on the Module Dependencies
page; `05-sequence-diagrams.md`, `06-coupling-analysis.md`, `07-risk-report.md`, and
`08-scorecard.md` all deleted (full content captured in `improvement-142` beforehand); and
`architecture-map.html`/`architecture-model.json` moved from `docs/` into `docs/architecture/`
(5 relative-link generators + 4 CI freshness gates + every doc referencing the old path updated).
A `/code-review --fix` self-review pass (8 parallel finder agents + 1-vote verify per candidate)
found and fixed several real bugs before the test run: a wrong package derivation in the
starter-to-starter coupling check (`org.ost.provider-profile` instead of the real `org.ost.provider`,
confirmed via direct `find`), a vacuous ArchUnit `slices()` pattern (`(**)` instead of `(*)..`,
confirmed by compiling a standalone `PackageMatcher` test program against the real 1.4.2 jar), an
`esc()` crash on numeric fields (confirmed via a live Playwright container run), test files
polluting two production-complexity tables, and 2 ticket-number-in-comment rule violations; one
finding (replacing the hand-rolled starter-coupling rule with `slices().notDependOnEachOther()`)
was deliberately skipped after `javap` showed it would need multiple `ignoreDependency()` calls to
reproduce correctly. `bash scripts/unit-tests.sh`: 85/85 passed (includes all 14
`ArchitectureRulesTest` rules); `bash scripts/integration-tests.sh --sandbox`: 164/164 passed;
Playwright e2e skipped as not required — no `marketplace-app` Vaadin UI was touched, only the
standalone `architecture-map.html` tool, already verified directly via isolated Playwright
container runs during implementation. `scripts/ai/DECISIONS.md` ADR-020 records the full decision;
`docs/ai/adr-index.md` regenerated. Full detail:
`completed/issues/improvement-143-architecture-docs-05-08-mechanization-batch.md`.

✅ Done (2026-08-06): improvement-144 — opt-in `--with-sonar`/`--with-archunit` flags on
`generate-architecture-model.sh` (default off, `ensure_sonar_fresh` no longer runs unconditionally);
architecture-generation tooling (`generate-architecture-model.sh`, the freshness/screenshot
scripts, both Node parsers, `architecture-map-screenshots/`, and the whole `DECISIONS.md`, ADR
numbers unchanged) moved from `scripts/ai/` into a new sibling `scripts/architecture/` directory
(`scripts/ai/` keeps only the ADR-index/flows/doc-standards scripts, now with no `DECISIONS.md` of
its own); every `scripts/architecture/` script gained a standardized 4-field header
(`Description:`/`Uses:`/`Input:`/`Output:`) that the System screen's "How this page is built"
section now reads dynamically instead of hardcoding; two new System-screen cards/screens —
**ADRs** (flat, deduplicated, filterable-by-status, grouped-by-module list of every ADR across
every module, full-content popup reusing the Module page's existing mechanism, a glossary Overview
section explaining what an ADR is/how it's used/its boundaries) and **Code Quality** (SonarQube +
ArchUnit metrics per module, one table per source, derived ratio columns color-coded
green/yellow/red against real thresholds, an Overview section explaining every field) — both
replacing content that used to live inline on every Module page. Full detail across
`scripts/architecture/DECISIONS.md` ADR-021/022/023/024. The one piece of the original scope not
built — a companion-server on-demand refresh trigger — was split into `improvement-146` once
everything else landed, since that piece's priority was still undecided while the rest was ready
to close. Full detail:
`completed/issues/improvement-144-code-metrics-dedicated-card-refresh-trigger.md`.

✅ Done (2026-08-07): improvement-145 — `md-to-decisions-json.js` gained
`--extract <module> <ADR-NNN>[,...]`, printing the requested ADR(s) as raw markdown instead of the
whole `DECISIONS.md` (94-98% fewer tokens across 4 measured samples). Wired into the actual
AI-facing flow, not just documented: `docs/ai/context-loading.md`'s single-module task rows now
say "filter the index, then `--extract`" instead of "open the whole file"; `docs/ai/README.md`
notes it as `adr-index.md`'s companion. `scripts/architecture/DECISIONS.md`'s "AI-layer L3" open
goal struck through as done; ADR-008 (the embed-everything design behind the human-facing ADR
popup) briefly marked legacy here, floating a possible future companion server — superseded the
same day by `improvement-146`'s entry below (decided against a server; resolved with an opt-in
generation flag instead). Also resolved in place
in the same conversation (not relocated to a separate issue, per direct decision): the
`architecture-map.html`/Tooling & Pipelines restructuring thread — new "Docker" and "Runtime"
groups, "How this page is built" relocated off the System screen, removed a duplicated ADR-listing
block, and two rounds of dead-code/dead-data cleanup this surfaced (`renderAdrList`/
`openAdrPopupForIntent`/`adrFileLink`, and unused `.intent` payload on `SCRIPT_GROUP` nodes).
Tightened `.claude/commands/decision.md`'s ADR-worthiness gate: a tool being about "architecture"
doesn't exempt its own UI/layout changes from the gate. Full detail:
`completed/issues/improvement-145-adr-extraction-token-efficiency.md`.

✅ Done (2026-08-07): improvement-146 — closed with the companion server explicitly **decided
against** (cost — new long-running process, port/lifecycle, unverified CORS — outweighed a rare,
low-friction problem), not deferred. The issue's other half — ADR-008's 605KB/72%
full-text-embedding duplication in `architecture-model.json` — shipped instead, without a server:
a new `--with-adr-details` opt-in flag on `generate-architecture-model.sh` (same pattern as
`--with-sonar`/`--with-archunit`), off by default (842KB → 244KB). `openAdrPopupForAdr()` now
always opens the popup (title/status from the always-lean `MODEL.allAdrs`), falling back to a
source-file link + a generic pointer to the Tooling & Pipelines screen — not a hardcoded command —
when the full text isn't embedded. ADR-008 amended twice (measurement + the same-day
reconsideration), dropping the ticket-number citation the first Amendment had briefly reintroduced.
`docs/architecture/runtime-notes.md` gained an "Architecture map tooling" group covering every
script involved in building the map (parameters, manual invocation, sandbox notes), replacing the
single `--extract`-only bullet it had before. Full detail:
`completed/issues/improvement-146-code-quality-refresh-companion-server.md`.

✅ Done (2026-08-07): improvement-136 — extracted a new `marketplace-orchestrator` Maven module
(Application/BFF composition layer between `marketplace-app` and the domain starters), moving
`AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService` (both starter-internal
cross-domain composition) and `marketplace-app`'s own `AdvertisementSaveService`/`UserDeleteService`
into `orchestrator.advertisement.{enrich,save}`/`orchestrator.providerprofile.enrich`/
`orchestrator.user.delete`, built on shared single-port `orchestrator.shared.*` collaborators
(`TaxonLookupService`, `ActorLookupService`, `TaxonAssignmentWriteService`,
`AttachmentSnapshotReaderService`, `AttachmentSoftDeleteService`). `AdvertisementPort`/
`ProviderProfilePort` dropped their `Locale` parameter (see `platform-commons/DECISIONS.md`
ADR-028) now that enrichment happens downstream of the port call. Two new ArchUnit rules
(`orchestrator_classes_depend_on_at_most_two_domain_ports`, `orchestrator_has_no_persistence_access`)
enforce the module's own boundary going forward. `AdvertisementAuditEnrichService` stayed in
`marketplace-app` (needs `LocaleProvider`/`I18nService`, an application-shell concern the
orchestrator must never depend on) — a real correction caught mid-implementation, not assumed at
planning time. Adjacent fix: `ProviderProfilePort.findOwnerIds()` purge-guard, documented but never
wired, now actually checked in `UserService.cleanup()` and the moved `UserDeleteService`. Root
`CLAUDE.md`'s "Architecture Guidelines" now describe three layers, not two.
`/code-review --fix` (8 finder angles + verification) found and fixed real issues: duplicated
`enrichSingle()` across two UI classes (centralized), stale `advertisement-spring-boot-starter`/
`provider-profile-spring-boot-starter` `CLAUDE.md` claims, a ticket number in a Javadoc comment, a
multi-line code comment, a defensive empty-check misplaced inside a method body, a missing singular
`findById` on `TaxonLookupService`. Verification found and fixed 3 more real, pre-existing
infrastructure gaps unrelated to this issue's own logic (same "forgot to update the module list"
class of bug `improvement-138` already hit once): root `Dockerfile` missing
`marketplace-orchestrator` in 3 places, `scripts/ci/Dockerfile` missing `nodejs` (needed by the
architecture-model generator), and the same module-list gap in `scripts/sonar/`'s config. A CI-stack
Playwright run showed 3 failures; re-verified via the standard `deploy.sh --reset` +
`playwright.sh e2e --full --ux` dev workflow — **50/50 passed**, confirming the CI-stack failures
were Docker-in-Docker environment flakiness, not a real regression. Full detail:
`completed/issues/improvement-136-marketplace-orchestrator-extraction.md`.

✅ Done (2026-08-08): improvement-147 — flattened `marketplace-orchestrator`'s 9 pre-existing
service classes (scattered across 5 domain-scoped sub-packages) into one flat
`org.ost.orchestrator.services` package; added `marketplace-orchestrator` as a real, evidence-based
node on the Bounded Contexts diagram (`scripts/architecture/generate-architecture-model.sh`) instead
of the previous hardcoded `Shared`/`UI` + `*-spring-boot-starter`-suffix domain discovery — replaced
via a self-describing `pom.xml` `<architecture.boundedContext>` property on every module; and made
`marketplace-app` a true BFF client with zero direct domain `*Port` access, closing the gap between
`improvement-136`'s own contradictory spec (target diagram showed no direct UI-to-starter arrow, but
its stated rule only banned composing multiple ports). 25 marketplace-app classes repointed to 6
new/extended `marketplace-orchestrator` services (`AdvertisementReadService`, `TaxonCatalogService`,
`AttachmentMediaService`, `AuditQueryService`, extended `ActorLookupService`, `UserProfileService`)
plus a new `EntityExistenceService` — a named, ArchUnit-allowlisted exception to the module's ≤2-port
rule for `AuditDomainHookImpl`'s per-`EntityType` existence routing. 4 UI presence-only
`ifAvailable()` gates also moved through the orchestrator (superseding that carve-out in
`marketplace-orchestrator/CLAUDE.md`), and a residual 6 direct `User*Port` usages found beyond the
original 25-class audit (`UserPickerField`, `LocaleSelectorComponent`, `SignUpDialog`, `UserView`,
`SettingsPaginationService`) were repointed too — `AccessEvaluator`'s `UserAuthorizationPort` is the
one deliberate, documented residual exception (security-boundary infrastructure, not a domain
read-model). See `marketplace-orchestrator/DECISIONS.md` ADR-003. Real bug found only by an actual
`deploy.sh` container boot (invisible to every Maven-level test): the planned service name
`AuditReadService` collided with `audit-spring-boot-starter`'s own pre-existing internal class of
the same simple name, producing a `ConflictingBeanDefinitionException` at startup — renamed to
`AuditQueryService`. `/code-review --fix` (8 finder angles + verification) found and fixed one real
clarity defect (`AdvertisementFormOverlayModeHandler.save()` gating its write on a sibling read
service's `isAvailable()` instead of its own save service's); a `TaxonManagementView`/
`CityManagementView` graceful-degradation gap was confirmed pre-existing (not introduced by this
migration) and proposed for `improvement-133`'s deferred-findings bucket rather than fixed inline.
`unit-tests.sh`: 75/75; `integration-tests.sh --sandbox`: 165/165; `deploy.sh --reset` +
`playwright.sh e2e --full --ux`: **50/50 passed**. The issue's original single-caller-collaborator
question (`TaxonAssignmentWriteService`/`AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService`)
moved in full to `improvement-124` Batch 124-C, the real second-consumer test. Full detail:
`completed/issues/improvement-147-marketplace-orchestrator-followups.md`.

✅ Done (2026-08-11): improvement-149 — `System › Diagrams` clarity pass, four fronts. **SPI Map**
split into 7 per-subsystem tabs (was one dense 71-node canvas), with hover tooltips explaining
each Direction value. **Bounded Contexts** migrated from Mermaid's hand-rolled scroll-drag to
Cytoscape (native pan/zoom/drag/click, matching Module Dependencies/SPI Map), then split further
into 4 tabs by relationship *category* — Service Calls (BFF)/Hook Callbacks/Cross-Starter
Exceptions/Derived Facts — after a real evidence bug was found and fixed: the old generator
blanket-labeled every Hook implementation as an "Audit ->" edge regardless of real caller, found by
the user simply asking "why does Audit call UI" and checking the evidence instead of re-explaining
the diagram; the fix revealed `Orchestrator -> UI`/`Attachment -> Orchestrator` edges the old code
had silently mislabeled as `Audit -> UI`. Diagram edges and Relationships-table rows are now
mutually clickable (arrow -> scroll to evidence row and back). **Hook relocation**: 6 `*Hook`
implementations moved `marketplace-app` -> `marketplace-orchestrator` via two new forwarder SPIs
(`UiLabelHook`/`SessionActorHook`, `org.ost.orchestrator.spi` — not `platform-commons`, since
their only caller is `marketplace-orchestrator` itself, a mandatory dependency); `pom.xml`'s 6
starter `<dependency>` blocks moved the same direction, superseding the Enforcer rule that used to
ban it (`marketplace-orchestrator/DECISIONS.md` ADR-004, `platform-commons/DECISIONS.md` ADR-029).
Simplified across 3 further rounds on direct user pushback each time — a typed `AuditLabelKey` enum
was added then removed again once raw `Fields.*` constants (already compiler-checked) proved
sufficient; `AuditActivityFieldsHook` (4 near-identical per-domain implementations, zero real
domain-specific logic left) was removed entirely, collapsing into one field-label switch directly
in `AuditTimelineRowRenderer`. **Payload accuracy**: the Relationships table's "What crosses"
column for Hook Callback edges was one hardcoded text shared across all 4 real edges, citing
`AuditActivityFieldsHook` — an interface deleted earlier in the same pass — and simply wrong for 3
of the 4; fixed with `BC_HOOK_PAYLOAD`, a real per-interface method-signature map. Along the way:
`ChangeEntry.mapField()` added (`platform-commons/core/model`, mirrors the existing
`replaceIfField()`), `AuditTimelineRowRenderer.applyLabel()` refactored from a hand-rolled `switch`
to one line using it; `platform-commons/CLAUDE.md`'s "Narrow exception" rule reworded to state the
pure-derivation principle package-agnostically (`*.dto`/`*.api`/`core.model`) instead of literally
scoped to `*.dto`, since `ChangeEntry` now demonstrates it twice (`platform-commons/DECISIONS.md`
ADR-021 amended). `spi_call_flow_examples_json()`'s 3 hand-typed narrative examples found stale
(citing deleted interfaces/classes) — logged as entry 10 in `improvement-133` rather than fixed
inline, bigger scope than this pass. `improvement-150` filed mid-session as a tighter follow-up
(`marketplace-app` should depend on nothing but `marketplace-orchestrator`, not even
`platform-commons`/`query-lib`). `unit-tests.sh`: 72/72; `integration-tests.sh --sandbox`:
165/165; `deploy.sh --reset` + `playwright.sh e2e --full --ux`: **50/50 passed**. Full detail:
`completed/issues/improvement-149-architecture-map-module-deps-vs-bounded-contexts.md`.

- ✅ Done (2026-08-12): improvement-148 — re-verified optional-starter removability after the
  true-BFF migration. `taxon-spring-boot-starter` removal passed cleanly (app boots, degrades
  gracefully). `provider-profile-spring-boot-starter` removal **failed to boot**
  (`UnsatisfiedDependencyException` — `UserService`'s mandatory `ComponentFactory<ProviderProfilePort>`
  field had no fallback producer once that starter left the classpath; the only producers lived in
  the starter itself and in `AdvertisementAutoConfiguration`, both absent). Fixed by adding the
  missing `@Bean` to `UserAutoConfiguration`, mirroring the existing `ComponentFactory<TaxonPort>`
  fallback pattern (`platform-commons/DECISIONS.md` ADR-006 amendment). Re-ran the removal test
  after the fix: clean boot, `/health` 200, no errors. `EntityExistenceService`'s 4-branch
  degradation spot-checked via code inspection — structurally sound. `unit-tests.sh`: 72/72
  (including `ArchitectureRulesTest`); `integration-tests.sh --sandbox`: 165/165. No Playwright run
  — config-only fix, not UI-visible. Full detail:
  `completed/issues/improvement-148-reverify-optional-module-removal-after-bff-migration.md`.

- ✅ Done (2026-08-13): improvement-150 — tightened improvement-149 Point 5 to zero direct
  `*Port`/`*Hook` (SPI) usage from `platform-commons` in `marketplace-app` (not a literal
  zero-dependency goal — DTOs/enums/`ComponentFactory<T>` stay). Removed the unused `query-lib`
  dependency; fixed 17 real Sonar findings; triaged an IDE "unused declaration" dump (4 real
  write-only `@LastModifiedDate`/`@LastModifiedBy` fields documented, not deleted; 4 false
  positives); swept all `*Port`/`*Hook` interfaces for dead methods (2 removed); moved
  `ActivityEnrichHookImpl`/`AdvertisementAuditEnrichService` into `marketplace-orchestrator`
  behind new `CurrentLocaleHook`/extended `UiLabelHook` forwarders; repointed
  `AuditActivityListRenderer`/`AuditTimelineListRenderer`/`AuditTimelineRowRenderer` off raw
  `AuditDomainHook`/`AuditActivityEnrichHook` onto orchestrator services; moved `AccessEvaluator`'s
  authorization checks into a new `AuthorizationService` (removing dead `UserIdMarker` along the
  way); added `SettingsChangeHook`/`CurrentUserHook` forwarders for `SettingsPaginationService`/
  `AuthContextService`; added the `ArchitectureRulesTest` guard banning any direct
  `platform-commons..spi..` import from `marketplace-app` (allowlist of one: `AuthenticatedPrincipal`).
  Result: 5 forwarder-SPI pairs total, documented in `marketplace-orchestrator/CLAUDE.md`'s new
  "Forwarder SPI pattern" section. `unit-tests.sh`: PASSED; `integration-tests.sh --sandbox`:
  165/165; `deploy.sh --reset` + `playwright.sh e2e --full --ux`: **50/50 passed**. Full detail:
  `completed/issues/improvement-150-marketplace-app-zero-deps-except-orchestrator.md`.

- ✅ Done (2026-08-13): improvement-151 — architecture-generator content-drift cleanup
  (`scripts/architecture/generate-architecture-model.sh`). Removed `spi_call_flow_examples_json()`
  and the whole "Call Flow Examples" section outright (stale hardcoded class references, some
  already moved/deleted). Built the `#arch-embed:KEY` marker convention — a depth-tracked bash
  extractor (`arch_embed_raw()`/`arch_embeds_json()`) reads marked excerpts directly out of
  `platform-commons/CLAUDE.md` instead of hand-copied HTML strings, closing a real drift gap (a
  `<!-- #arch-diagram:KEY -->` marker already existed but nothing read it). Used for
  Implementation Rules (moved out of per-subsystem SPI Map tabs into one instance at the bottom of
  `System › Diagrams`) and 4 new SPI/Port/Hook glossary paragraphs. New
  `docs/architecture/arch-embed-index.md` — a generated, repo-wide index of every `#arch-embed`
  marker (mirrors `docs/ai/adr-index.md`'s discovery role), regenerated as part of the same script
  run, not a separate manually-triggered one. New standing rule in `.claude/rules.md` ("a comment
  above a method states what that method's own body does"). **Notable mid-session correction, twice
  over**: a systematic re-check found roughly half of this issue's own "Done" bullets (a
  `screenshot-architecture-map.sh` fix, an entire fictional `@flow` source-tag mechanism with an
  11-item design log and a fabricated "Verified" paragraph, clickable-module-link claims, a
  "Diagram-Specific Comments" file/table) described work that was never actually present in the
  repo — confirmed file-by-file (grep, `git log`, direct file existence checks) and removed
  outright rather than kept for "historical value." Real work verified via multiple successful
  `bash scripts/architecture/generate-architecture-model.sh` regenerations (`Valid JSON` each time)
  plus standalone Node scripts exercising the real render functions against real `MODEL.archEmbeds`
  data. This issue's original topic (`scripts/build.sh`, a redundant-recompile fix never
  implemented here), a Track B/ArchUnit unblock investigation this issue's own SPI Map findings
  motivated, and its SPI Interface Details table redesign idea all moved to `improvement-152`.
  Docs/tooling-only change — no Java touched, so no unit/integration/Playwright run applicable.
  Full detail: `completed/issues/improvement-151-scripts-avoid-redundant-recompile.md`.

- ✅ Done (2026-08-17): improvement-152 — `scripts/build.sh` redundant-recompile fix + Tooling &
  Pipelines regroup. Part A: consolidated `scripts/unit-tests.sh`/`scripts/integration-tests.sh`
  into `scripts/build-and-test.sh` (parallel unit+integration, module/test-class selection,
  host-copied reports, PASSED/FAILED summaries, `--sandbox`); found and fixed a real bug along the
  way (`RUN_INTEGRATION` silently ran zero real Testcontainers tests). Legacy standalone scripts
  deleted, `scripts/ci/entrypoint.sh` rewired to one merged `build_and_test` stage. Part D:
  Tooling & Pipelines screen regrouped from a 2-bucket AI/Other split into one card per tool.
  Both parts done and verified. **Parts B, C, and E each split out to their own issue once A/D
  were ready to close** — not because they were abandoned, but because each had grown into
  independent, separately-trackable work: `improvement-156` (ArchUnit Track B unblock decision
  gate — its own technical prerequisite, `ArchitectureMetricsExport`'s module-coupling exporter,
  fixed and verified as part of this issue via a new `--archunit-metrics` flag before the split),
  `improvement-157` (SPI Interface Details table redesign), `improvement-155` (repo-wide
  `infra-doc-standards` script-header/README-flow convention rollout). Two smaller loose ends from
  Part A also moved out: `improvement-153` (verify the merged CI stage for real) and
  `improvement-154` (deploy reusing `build-and-test.sh`'s shared-volume jar, closing the
  documented Playwright-freshness gap). Full detail:
  `completed/issues/improvement-152-build-script-and-archunit-track-b-unblock.md`.

- ✅ Done (2026-08-17): improvement-154 — `scripts/deploy.sh` restructured into
  `scripts/deploy-and-run/` (`run.sh`, `reset.sh`, `docker-compose*.yml`; `scripts/infra`/
  `scripts/database` folded in), 3 duplicate DB-truncate implementations unified into one shared
  `reset.sh`. `deploy-and-run.sh` no longer duplicates `build-and-test.sh`'s compile step — by
  default it now runs the app directly against the shared `maven-cache` volume from a plain
  `eclipse-temurin:25-jre` container (`docker run` mounts a named volume directly; no image build,
  no bridge container needed at all — an earlier version of this fix used a jar-extraction
  bridge-container + a thin Dockerfile, replaced once the app-container step turned out not to
  need a built, tagged image in the common case). `--from-scratch` keeps the original full,
  isolated, tagged-image build for when one is genuinely needed. `run-all-tests.sh` now runs
  `deploy-and-run.sh` (always clearing app data first) sequentially before `playwright.sh`,
  closing the documented Playwright-freshness gap. Found and fixed a real bug this reuse surfaced:
  `build-and-test.sh`'s fixed container name collided under two concurrent invocations — added an
  overridable `BUILD_CONTAINER_NAME` plus defensive cleanup. `infra-doc-standards` applied to
  `scripts/deploy-and-run/`; new architecture-map card. Verified end to end with real Docker runs
  at every stage, including a full `run-all-tests.sh --sandbox` pass (unit 53/53, integration
  165/165, e2e ALL PASSED). Also removed `scripts/hooks/`/`scripts/install-hooks.sh` (never
  installed/used in this environment — the "should this run repo-wide" question was already
  flagged as an explicit user decision in `improvement-138`; user confirmed removal). Full detail:
  `completed/issues/improvement-154-deploy-reuses-build-and-test-jar.md`.

- ✅ Done (2026-08-17): improvement-158 — same shared-jar-reuse pattern from improvement-154
  applied to `scripts/sonar/run.sh`: drops its own local `mvnw compile` (no local Java needed
  anymore), calls `scripts/build-and-test.sh` first, mounts the shared `maven-cache` volume
  directly into the sonar-scanner container instead of copying host-compiled classes in
  (`sonar.java.binaries` doesn't accept jars directly, confirmed via SonarQube docs — only
  directories of `.class` files, so `build-and-test/build.sh` now also refreshes each module's own
  `target/classes` into the volume, alongside the jar). Found and fixed a real pre-existing bug
  during testing: `--no-gate` used to also clear `sonar.qualitygate.wait=true`, so the scanner
  returned before SonarQube finished processing the just-uploaded report — the HTML-report step
  then hit an API timeout and silently skipped writing the report file. The wait flag now always
  stays on; `--no-gate` only controls whether a failed gate makes the script itself exit non-zero.
  Verified end to end: real scan uploaded, gate evaluated (3 real issues found), HTML report
  written (2810 bytes). Full detail:
  `completed/issues/improvement-158-sonar-reuses-build-and-test-jar.md`.

- ✅ Done (2026-08-18): improvement-153 — replaced `scripts/ci.sh`'s hand-rolled `progress.txt`
  polling with Dagu (single-binary DAG engine, built-in web UI). `ci-runner` becomes a persistent
  container running `dagu start-all`; `scripts/ci/dagu/ci.yaml` defines the same stage sequence
  as a real DAG (`build` → `unit`/`integration`/`e2e`/`sonar` in parallel → `docs`), each step
  calling the same existing scripts, no stage logic reimplemented. `scripts/ci/run.sh` rewritten
  into a thin trigger (build/start the container, fire a DAG run via `docker exec`). Three problems
  found only by actually running it, none documented in Dagu's own docs: `dagu start <name>`
  resolves against `$DAGU_HOME/dags`, not the server's `--dags` directory (fixed by triggering via
  file path); each step runs in an isolated working directory by default (fixed with
  `working_dir: /app`); a `--network host` container's bound ports aren't reachable from a real
  browser in this sandbox, unlike an explicit `-p` publish (fixed with a small `alpine/socat` proxy
  sidecar reading the actual default-bridge gateway IP from Docker, since `host.docker.internal`
  resolved to an address that refused the connection here). Also fixed, unrelated to Dagu itself:
  the image's build-time binary installs (buildx/compose/Dagu, ~190MB) re-downloaded on every
  rebuild whenever an earlier Docker layer's cache missed, often in this sandbox — moved to a
  `ci-tools-cache` named volume with a download-if-missing check at container start
  (`scripts/ci/docker-entrypoint.sh`) instead of image build time; `--refresh-tools` forces a
  re-download. Verified end to end: image build → container start → Dagu web UI reachable from a
  real external browser → "Start" dialog renders a field per DAG param (confirmed by the user) →
  `unit` stage genuinely passing (53/53) → persistent dev stack (`marketplace-app`) still healthy
  throughout.

  **Follow-up, same day:** added a `pipeline_metrics` DAG step (per-step status/duration, feeding
  `generate-architecture-model.sh --with-ci-metrics`) and an on-demand `archunit_metrics` step
  (module-coupling export), plus a `--skip-vaadin` flag on `build-and-test.sh`/`build.sh` so
  test-only stages skip the unneeded ~3-4 min Vaadin frontend bundle (`unit` 209s→124s,
  `archunit_metrics` 298s→132s, measured). Running the resulting default pipeline end to end
  surfaced several more real gaps: `archunit_metrics` flipped on by default (its own cost is small
  next to `e2e`'s); `run-all-tests.sh` was missing the same `--skip-vaadin` its own `ci.yaml`
  equivalent already had; `scripts/ci/watch-run.py` added as a Monitor-backed replacement for
  manually polling Dagu's API (two real bugs fixed building it — Python stdout buffering without
  `python3 -u`, and a silent stall looking identical to a healthy long step without a heartbeat
  line); `keep_infra` renamed `keep_e2e_infra` with its default flipped to `true` (debugging-
  friendly by default), which in turn exposed a real, pre-existing gap: `e2e`'s own deploy call
  passed no DB-reset flag at all, unlike `run-all-tests.sh` — fixed with a new `reset_e2e_db` param
  (`--reset-only-db` by default, full `--reset` opt-in for schema changes) after a run without it
  produced a stale-data test failure that a re-run with the reset applied did not reproduce.
  Verified end to end twice more: a full `ci.sh --reset-e2e-db` run (`e2e` now succeeded) and two
  direct `deploy-and-run.sh` + `playwright.sh e2e --full --ux` runs (50/50 passed both times,
  including the specific test that had failed on stale data). Full detail:
  `completed/issues/improvement-153-dagu-local-ci-visualization.md`.

✅ Done (2026-08-19): improvement-159 — full 9-step ADR system review, all steps executed for real,
  not just analyzed. Classified all 229 ADRs across 16 `DECISIONS.md` files (KEEP/MERGE/SUPERSEDE/
  DEMOTE/REMOVE/REWRITE), fixed 8 stale `Status:` fields, updated `.claude/rules.md`'s ADR-citation
  rule to point at `docs/ai/adr-index.md` instead of raw `DECISIONS.md`/ADR-number citations and
  swept 68 citations across 38 files to match, renamed `/decision` → `/record-decision` with a new
  worthiness-gate granularity rule ("one ADR per decision, not per batch") and an update-discipline
  rule ("supersede, don't append a correction layer"), added an optional lazy `**Verified:**` field
  (+ index column) replacing a one-time code-consistency audit that failed mid-run. Migration
  actually applied across all 13 non-empty `DECISIONS.md` files by 6 parallel agents: **229 → 172
  active ADRs**. A post-migration cross-file integrity sweep found and fixed 6 dangling references
  to deleted ADR numbers. Full detail: `completed/issues/improvement-159-adr-system-review-and-
  refinement.md`.

✅ Done (2026-08-20): improvement-155 — repo-wide rollout of the `infra-doc-standards` convention
  (structured 7-field headers, README `## Flow` sections) completed across every real script
  directory: `scripts/sonar/`, `scripts/build-and-test/`, `scripts/ci/` (+ nested `dagu/`),
  `scripts/deploy-and-run/`, `scripts/utils/`, `playwright/` (+ nested `e2e/`/`e2e/_flows/`), and
  root `scripts/*.sh`/`*.bat` (20 files, verified via 3 successive skill runs + independent
  fresh-agent reviews). Real bugs found and fixed along the way: stale `scripts/infra`/
  `scripts/database` paths in `collect-code.bat`; hardcoded sandbox-only `/app/...` absolute paths
  in `playwright.bat`/`architecture-doc.bat` (now `wslpath -u "%~dp0..."`, matching every other
  `.bat` delegator); the skill's own `README` duplication rule was strengthened twice mid-rollout
  (README is always a full rewrite, never a patch onto pre-existing content; a shared environmental
  constraint across unrelated files belongs in each file's own header, not README) after the rule's
  first version let a real "Container reference"/"Docker socket constraint" duplication violation
  through two self-review passes in a row — only caught by a fresh, cold-context question.
  `infra-doc-standards/SKILL.md`'s own illustrative `scripts/sonar/README.md` example lost its
  file-name pointer (kept the text, per "name a real file only when unavoidable"). `docs/ai/scripts/`
  split off to its own tracked issue, `improvement-161`, rather than folded in here. Full detail:
  `completed/issues/improvement-155-infra-doc-standards-repo-wide-rollout.md`.

✅ Done (2026-08-21): improvement-163 — separated raw process logs (`scripts/logs/<script>/`) from
  structured test reports (each script's own `reports/`/`pw-report/`) across build-and-test,
  integration-tests, playwright, sonar, and run-all-tests; verified for real via 6 acceptance-
  criteria test runs, all passing. Root-caused and fixed two real bugs found along the way: `docker
  cp` failing when two nested destination directories are missing at once (fixed by creating
  `scripts/logs/` once in `clean.bat`, not duplicated per entry point), and `sonar-scanner-cli`'s
  non-root container user rejecting writes into its own reports volume (fixed with `--user root`).
  Removed the flaky `wait_for_container_files_or_keep` timeout check entirely after it produced
  false failures under real load; deleted `scripts/utils/wait-for-container-files.sh`. Added
  `scripts/pull-logs.bat` to pull logs/reports from persistent containers without running a new
  test. One item (`architecture-map.html`'s script-header truncation/formatting bug, diagnosed but
  not implemented) split out to `improvement-164` rather than folded in here. Full detail:
  `completed/issues/improvement-163-scripts-tooling-improvements.md`.

✅ Done (2026-08-21): improvement-165 — investigated three third-party `.claude/`-layer linters
  (agnix, AgentLint, AgentLinter) as candidates to mechanically validate `CLAUDE.md`/`SKILL.md`/
  hooks/commands, none currently checked in this repo. Ran all three read-only against the real
  repo; spot-checked the highest-severity findings in `CLAUDE.md`/`.claude/rules.md` specifically
  against the real file content — every one checked (agnix's `<container>`/`<path>` "unclosed XML
  tag", AgentLinter's "always call/never call" "contradiction") turned out to be a false positive
  rooted in this project's own writing conventions (shell placeholders in inline code, contrastive
  phrasing). Closed with no tool adopted — the underlying gap (no mechanical validation of the
  `.claude/` layer) stays open for a future attempt. Full detail:
  `completed/issues/improvement-165-investigate-agnix-claude-layer-linter.md`.

✅ Done (2026-08-21): improvement-166 — `scripts/collect-code.bat` gains a `--claude-only` mode:
  bundles just `.claude/` rules/commands/skills, every `CLAUDE.md` (root + per-module), and
  `private/claude/memory/` into `claude-context.txt`, skipping the full project source scan. Two
  real bugs found and fixed via actual Windows `cmd.exe` runs: `::`-style comments inside a new
  parenthesized `if` block broke `cmd.exe`'s block parser (converted to `REM` and stripped of all
  literal parentheses, since a stray paren in a comment can misparse the block the same way);
  `CLAUDE.md` files were initially missing from the bundle since `--claude-only` skips the general
  `*.md` scan that picks them up in full-project mode, fixed with an explicit `:FindFiles
  "CLAUDE.md"` call. Full detail: `completed/issues/improvement-166-collect-code-claude-only-mode.md`.

✅ Done (2026-08-25): improvement-164 — fixed `architecture-map.html`'s script-header display
  (dropped the 20-line read cap, joined continuation lines with `\n` instead of a space, added
  `white-space: pre-wrap`). Also fixed `architecture-doc.sh`'s tar-pipe `Permission denied` on its
  own prior output files (excluded from the upload tar, `chmod 644` after `docker cp`), added
  per-phase progress logging, and excluded `scripts/logs/` from the Scripts tree. Removed the dead
  Docker/Runtime sections from System › Tooling & Pipelines. Split the oversized
  `infra-doc-standards` skill into `infra-doc-standards` (file/function headers) and a new
  `infra-readme-standards` (README/Flow-diagram conventions), then ran both across `scripts/` end
  to end, finding and fixing two real gaps (`claude.bat`'s Unicode header markers, `sonar/README.md`
  + `docker-compose.sonar.yml`'s field/flow-step gaps). Full detail:
  `completed/issues/improvement-164-architecture-map-script-header-truncation.md`.

✅ Done (2026-08-25): improvement-168 — AI guidance refactor across two independent sub-phases.
  Phase 2.1 (memory): audited all 55 auto-memory files against `.claude/rules.md`/every
  `CLAUDE.md`, classified each (duplicate/partial/unique/stale), then deleted 16 pure duplicates,
  migrated 19 more into canonical files (9 as a new "Investigation & Review Discipline" section in
  `.claude/rules.md`) before deleting the memory copy, rewrote 2 stale-but-keep entries, and
  deleted 4 further entries confirmed stale/completed against real code (one Phase 1
  misclassification — a top-level Timeline nav tab wrongly called "superseded" — caught and
  corrected in the process). Memory: 55 → 16 files, `MEMORY.md` re-verified 1:1 against disk, zero
  dangling `[[...]]` links. Phase 2.2 (CLAUDE.md): moved all 13 module `CLAUDE.md` files to
  path-scoped `.claude/rules/*.md` (confirmed live, via a real probe test, that Claude Code's
  `paths:` frontmatter mechanism works, that subagents inherit the eager-loaded set too, and that
  a `paths:` glob is not anchored to the repo root — documented in a new `.claude/rules/README.md`
  and `.claude/README.md`). Fixed `docs/architecture/scripts/generate-architecture-model.sh`'s 3
  literal-path dependencies on the moved files in the same change, including two self-inflicted
  bugs (a `SIGPIPE` from a `tail|head` replacement, a `cd`-drift `127`) — root-caused and fixed,
  verified via a full regeneration + a node-by-node description diff showing zero regressions.
  Recorded as `.claude/DECISIONS.md` ADR-001 (new file for this module). One out-of-scope finding
  (stale repo-wide prose references to the old `<module>/CLAUDE.md` paths) deferred to
  `improvement-133` entry 13 rather than fixed inline or dropped. Full detail:
  `completed/issues/improvement-168-ai-guidance-memory-vs-canonical-rules.md`.

✅ Done (2026-08-25): improvement-170 — `doc-standards` vs `infra-doc-standards`/`infra-readme-standards`
  scope resolution, across 9 items. `doc-standards` split into `module-doc-standards` +
  `module-readme-standards`; new `app-readme-standards` skill (root `README.md`/`INFRASTRUCTURE.md`
  split); `docs/ai/` → `.claude/nav/` (`git mv` + 74-file reference update). Item 2's original plan
  (a new skill for `.claude/nav`/`docs/architecture/data`/commands-rules-skills) resolved
  differently in the end: no new skill — `infra-readme-standards` extended with a `.claude/skills/`
  section (top-level index only, never per-skill `README.md`), `infra-doc-standards` applied as-is
  to `.claude/nav/scripts/*`. Final item: `architecture-map.html`'s "AI Tooling" card rebuilt as a
  `.claude`-rooted tree (same mechanism as "Scripts"), hardcoded Commands/Skills tables removed,
  README made the sole canonical file list (chip-row now last-resort only), and a real bug fixed —
  `mdInlineToHtml()`/`mdBlockToHtml()` never supported markdown `[text](url)` link syntax, so every
  README link across the tool was inert bracket text. Recorded as
  `docs/architecture/scripts/DECISIONS.md` ADR-033 (supersedes ADR-010). `docs/architecture/data/*.md`
  content-governance and the orphaned "Canonical ownership table" both remain open gaps, not picked
  up by this resolution. Full detail:
  `completed/issues/improvement-170-doc-skill-scope-resolution.md`.

✅ Done (2026-08-25): improvement-161 — `.claude/nav/scripts/` `infra-doc-standards` rollout. Landed
  as a byproduct of `improvement-170`'s item 1/9 work rather than its own implementation pass: all
  4 scripts (`check-adr-index-freshness.sh`, `check-flows-completeness.sh`,
  `check-hardcoded-counts.sh`, `generate-adr-index.sh`) now carry the 7-field header, and
  `.claude/nav/scripts/README.md` exists with a `## Flow` section and mermaid diagram covering the
  `check-adr-index-freshness.sh` → `generate-adr-index.sh` relationship and the `docs` CI stage.
  Verified directly against current files, then closed. Full detail:
  `completed/issues/improvement-161-ai-docs-scripts-infra-doc-standards-rollout.md`.

✅ Done (2026-08-25): improvement-169 — Hybrid Agentic Review Factory investigation, closed with a
  decision: scope chosen is the narrowest candidate — formalize `diff-mode.md`'s already-working
  4-lens parallel-agent + per-candidate verification pattern as real, named `.claude/agents/*.md`
  files plus one orchestrating Agent call, no Semgrep/Sonar-MCP/ArchUnit mechanical-layer
  expansion (two of the mission's proposed rules had directly contradicted this project's own
  documented architecture). Actual implementation split off as a new issue. Full detail:
  `completed/issues/improvement-169-hybrid-agentic-review-factory.md`.

✅ Done (2026-08-25): improvement-162 — `docs/architecture/` reorganization: `architecture-doc.sh`/
  `.bat` relocated from `scripts/` to `docs/architecture/` (one level above
  `docs/architecture/scripts/`); `docs/architecture/data/` split off holding `arch-embed-index.md`,
  `architecture-model.json`, `README.md`, `runtime-notes.md`; every real reference to the 4 moved
  files updated. Landed across `improvement-163`/`164`/`166` rather than under this issue's own
  number — closing verification confirmed every open item resolved on disk, including the
  previously-flagged gap (`docs/architecture` now a tracked `SCRIPT_GROUP_DIRS` entry) and the
  approved-in-principle Dockerfile step (`docs/architecture/scripts/Dockerfile`, no more runtime
  `apt-get install python3`), plus `scripts/claude.bat`'s reuse-vs-`--recreate` container logic
  with the temporary `claude-dev-test` test name reverted back to `claude-dev`. Full detail:
  `completed/issues/improvement-162-architecture-map-refactor.md`.

✅ Done (2026-08-26): improvement-171 — formalized `/deep-review`'s reasoning layer as real
  `.claude/agents/*.md` subagents, per `improvement-169`'s decided narrow scope. Final shape:
  `deep-review-orchestrator` (self-contained coordinator, 4 scope modes, no `Write` tool — prepares
  backlog-issue content and the `ReportFindings` JSON payload for the dispatcher to act on after
  human approval, never writes/reports itself) dispatching two lenses in parallel —
  `solid-reviewer` (SRP/ISP/DIP/LSP) and `dry-kiss-yagni-reviewer` (DRY/KISS/YAGNI merged into one
  lens on purpose, since DRY and YAGNI pull in opposite directions). `security-boundary-reviewer`/
  `data-integrity-reviewer` were drafted then dropped — their concerns folded into
  `improvement-111` (an ArchUnit rule candidate) and `improvement-172` (fault-injection integration
  tests) instead, both cheaper/more reliable than an LLM lens for already-identified risk classes.
  Verified against `AICertification.txt` point by point (hub-and-spoke, structured
  content/metadata-separated JSON, independent fresh-instance verification, multi-pass
  attention-dilution guard, confidence-based routing, parallel spawning, structured handoff
  protocol) — one real gap (human-review handoff missing `failure_scenario`) found and fixed during
  the final check. `.claude/skills/deep-review/` deleted entirely after a live run showed the
  orchestrator silently falling back to its stale logic despite explicit instructions not to depend
  on it. `docs/architecture/scripts/generate-architecture-model.sh` gained a new AGENT node type
  (own "Agents" card under System › Tooling & Pipelines › AI Tooling) plus a shared
  `emit_pipeline_md_node()` helper deduplicating what had become a 3-way-copied COMMAND/SKILL/AGENT
  JSON-emission block — a real finding the orchestrator itself surfaced during a live test run on
  its own diff. Recorded as `.claude/DECISIONS.md` ADR-002. One known, accepted verification gap
  left open: the `ReportFindings` non-empty-payload path was never directly exercised end to end —
  every live run's own backlog cross-check correctly excluded its candidates (either real issues
  already tracked, or a deliberately-injected test fixture the orchestrator recognized from this
  issue's own text). Full detail: `completed/issues/improvement-171-formalize-deep-review-agents.md`.

✅ Done (2026-08-26): improvement-167 — DAG-aware agent-friendly script execution contract,
  narrowed after investigation to a minimal shared-utility candidate (full mission scope rejected
  as overbuilt for the project's real scale — no concurrent self-invocation, no state files
  written today). New `scripts/utils/agentic-output.sh` (`emit_agentic_success_block`/
  `emit_agentic_error_block`) emits a single-line JSON marker (`AGENTIC_SUCCESS_BLOCK`/
  `AGENTIC_ERROR_BLOCK`, `errorCategory`/`isRetryable`/`currentStep`/`description`/
  `durationSeconds`) on every real exit path of all 7 top-level `scripts/*.sh` entry points
  (`deploy-and-run/run.sh`, `deploy-and-run/reset.sh`, `sonar/run.sh`, `build-and-test/run.sh`,
  `run-all-tests/run.sh`, `ci/run.sh`, `playwright/run.sh`) — mechanism (trap-based vs. inline)
  matched to each script's own real control-flow shape rather than forced uniformly, since a
  uniform `trap ERR` would have broken `run-all-tests.sh`'s intentional dual-branch-to-completion
  design. Error-category taxonomy (`transient`/`validation`/`business`/`permission`) verified
  directly against the real private certification document, correcting the originating issue's own
  invented `environment` category. Recorded as `scripts/DECISIONS.md` ADR-013. Verified live:
  `deploy-and-run.sh` end to end (success path) and `build-and-test.sh --unit --integration
  --sandbox` (163 tests, 0 failures) both printed the expected `AGENTIC_SUCCESS_BLOCK`. No error
  path exercised live in any of the 7 scripts — verified by syntax check + review only. Full
  detail: `completed/issues/improvement-167-dag-aware-agent-friendly-script-execution-contract.md`.

✅ Done (2026-08-26): improvement-173 — infra housekeeping: `.claude/skills/README.md`/
  `.claude/commands/README.md` audited (no drift found); `improvement-160`'s D3-3/D3-8/D5-7 rows
  re-verified (citations corrected for renamed/deleted files); SonarQube MCP integration built as
  an agent-scoped `sonar-analyst` subagent (wrapper script keeps its token fresh on every
  dispatch, since the session-wide-`.mcp.json` idea couldn't survive SonarQube's token volatility)
  and verified live end to end, including a real `/sonar --metrics` scan that found and closed 3
  genuine `java:S7467` false positives (new two-part SonarQube-false-positive rule added to
  `.claude/rules.md`); Dagu MCP integration built as `dagu-analyst` (no wrapper needed, plain HTTP
  endpoint) but its `dagu_read`/`dagu_change`/`dagu_execute` tools never connect — confirmed a
  genuine Claude-Code-client-side limitation with inline HTTP `mcpServers` in agent frontmatter,
  independent of Dagu's own version (tried the 2.14.0 → 2.15.3 bump specifically to rule this out);
  documented REST-API fallback works reliably instead. `/review` and `/sonar --metrics`/`/ci
  --metrics` commands added; `INFRASTRUCTURE.md` restructured top-down per a new
  `app-readme-standards/SKILL.md` procedure. The `/ci --metrics` live test surfaced and led to
  fixing a real, unrelated bug: the CI `docs` stage only ever flagged staleness, never fixed it,
  and the underlying generator had two real container-vs-host drift bugs (`.dockerignore` dropping
  root `README.md`/`INFRASTRUCTURE.md`/`CLAUDE.md`; Dagu's own runtime `wiki/` folder mistaken for
  a real script-group) plus four unsorted `grep -rl`/`find` pipelines making its own output
  non-deterministic — all fixed and verified (two independent regenerations now byte-identical,
  real freshness gate passes clean). Full detail:
  `completed/issues/improvement-173-skill-readme-audit-sonar-dagu-mcp-integration.md`.

✅ Done (2026-08-27): improvement-156 — real ArchUnit-based `spi_map_json()` replacement.
  Reclassified first: the original "Track B, gated by `improvement-135` item 5" framing conflated
  "uses ArchUnit" with "is Track B" — Track B is actually defined by its AI-token-hypothesis
  purpose (a new layer Claude reads instead of source), and this issue's deliverable feeds only
  the existing human-facing SPI Map screen, so the gate never applied; `improvement-138`'s Finding
  3 corrected accordingly. `ArchitectureMetricsExport.java` gained a `spiEdges()` method: real
  bytecode-derived implementor/caller edges per `platform-commons` `*.spi` interface
  (`getAllRawInterfaces()`/`getCallsOfSelf()`, method-level, not text regex), plus the previously-
  missing `marketplace-orchestrator` module mapping. `spi_map_json()` now consumes this data
  (falling back to the old regex per-interface when absent) instead of its own text scan. Verified
  fixed, both in the JSON and in the actually-rendered table via headless Chromium: the documented
  `AuditAutoConfiguration` false positive is gone, and a previously-undocumented false negative
  (`AuditActivityEnrichHook` was missing a real second caller) is fixed too. A two-table SPI
  Interface Details redesign (Calls / Implemented By, rowspan-grouped, clickable module links,
  click-edge-to-row linking) was also built and verified live while testing this — tracked
  separately under `improvement-157`, since its shape diverges from that issue's original spec
  (grouped by Interface, no Method column, vs. the planned Module → Class → Method). Full detail:
  `completed/issues/improvement-156-archunit-track-b-unblock-decision.md`.

✅ Done (2026-08-27): improvement-157 — SPI Interface Details table redesign, built on
  `improvement-156`'s real method-level data. Shipped shape diverges from the original plan: two
  tables (Calls / Implemented By) grouped by Interface (not Module → Class → Method), each Caller
  cell showing real `callerMethod() → #interfaceMethod()` call-site pairs (bytecode-derived, not
  regex), and each Interface cell listing every one of its own methods with unused ones dimmed and
  marked `(unused)` — a real dead-code-in-contract finding, not hypothetical:
  `AuditDomainHook` is 1/3 used (`findExisting`/`resolveNames` never called by anything). A
  separate Interface | full-signature table was considered and rejected (the dead-code list already
  covers its value; a stripped-down signature list gives less than the existing one-click file
  link). Closed with two documented open questions, not blocking: generic-interface type-argument
  display, and whether to eventually regroup by Module → Class → Method. Full detail:
  `completed/issues/improvement-157-spi-interface-details-table-redesign.md`.

✅ Done (2026-08-28): improvement-174 — replaced Bounded Contexts' `ports_json`
  (`bounded_contexts_json()`, all 3 domain branches) and the Module screen's `MODULE_CONTRACT`
  regex-based SPI ownership checks with a shared `spi_owns_iface()` helper reading `improvement-156`'s
  real `spiEdges` data, falling back to the original regex only when no ArchUnit data is available.
  Surfaced and fixed two real false negatives beyond the original plan while verifying: UI domain's
  port list first dropped from 3 to 0 (its real Hook interfaces live in
  `marketplace-orchestrator/.../spi/`, not `platform-commons`), then grew to 5 once
  `ArchitectureMetricsExport`'s `isPlatformSpiPackage` was generalized to `isSpiPackage`
  (any module's own `*.spi` package, not hardcoded to `platform-commons`) — 2 more real Hook
  implementations (`CurrentUserHook`, `SettingsChangeHook`) live outside the `spi/` wrapper package
  entirely and were never reachable by any directory-scoped regex. Also: `module-link` accent
  styling applied to Bounded Contexts' Domain Contents links (previously indistinguishable from
  plain text inside `.adr-item`), Relationships table redesigned grouped-by-label with a
  rowspan-merged Payload column (same shape as `improvement-157`'s SPI Map split). Separately fixed
  `scripts/ci/dagu/ci.yaml`'s `docs` step, which never passed `--with-sonar`/`--with-archunit` to
  the generator regardless of whether the same run's `sonar`/`archunit_metrics` steps actually
  produced data — now conditional on `${params.sonar}`/`${params.archunit_metrics}`, verified via a
  real full `bash scripts/ci.sh --all --sonar` run landing real (non-null) SonarQube/ArchUnit data
  in the committed `architecture-map.html`. Two adjacent ideas noted but not implemented: real
  ArchUnit cycle-detection for `coupling_checks_json()` (recorded in `improvement-114`), and making
  the generator's own passive data-reads unconditional by default (would touch ADR-021). Full
  detail: `completed/issues/improvement-174-bounded-contexts-ports-archunit-replacement.md`.

✅ Done (2026-08-28): improvement-138 Track A — Architecture Control Plane's visual-control track
  (generated `architecture-model.json`/`architecture-map.html`, live Module Dependencies/SPI
  Map/Database ERD/Bounded Contexts pages replacing their hand-maintained markdown, `/sync-docs`
  wiring + freshness CI gate). Split out of the still-open `improvement-138` issue at the user's
  request (done vs. not-done separation) — full execution history moved to
  `completed/issues/improvement-138-architecture-control-plane-track-a.md`. Track B (ArchUnit
  contract/test model + AI-token hypothesis) remains not started, gated on the governing rule
  absorbed from `improvement-135` item 5 (see below), and stays tracked under the still-open
  `improvement-138`.

✅ Done (2026-08-28): improvement-135 closed — items 1 (ADR-index/`flows.md` freshness gates), 2
  (review-skill token-cost measurement practice adopted), and 4 (6/6 workflow-routing spot-check)
  were done. Items 3 (does `context-loading.md` empirically reduce reads — mechanism built,
  empirical answer pending real accumulated `## Operational notes` data) and 5 (governing rule: no
  new `.claude/nav/*`-shaped content until items 2-4 show the existing layer earns its cost) were
  the only still-open parts — moved verbatim into `improvement-138`'s "Absorbed from
  `improvement-135`" section, since they're the same open hypothesis as that issue's own Track
  B/B2 question (does a generated nav layer save tokens), just applied to the existing
  hand-authored layer instead of a new one. Nothing still-open remained in `improvement-135`
  itself, so the issue closed. Full detail:
  `completed/issues/improvement-135-ai-nav-layer-validation-and-adr-index-ci-check.md`.

✅ Done (2026-08-28): improvement-160 closed at explicit user request — not because the work
  finished. AI certification practical-coverage investigation (5 domains: Orchestration, Tool
  Design/MCP, Claude Code Config, Prompt Engineering, Context Management/Reliability) against this
  repo. Real shipped mechanisms found along the way: SonarQube/Dagu MCP servers
  (`improvement-173`), `argument-hint`/`allowed-tools` skill frontmatter, YAML/JavaScript doc-header
  coverage (`improvement-155`). Every remaining coverage-map row stays at `idea` status — none
  implemented. Archived as the record of what was investigated; reopen from the coverage map
  instead of re-researching if any `idea` row gets picked up later. Full detail:
  `completed/issues/improvement-160-ai-certification-practical-coverage.md` and
  `completed/issues/improvement-160-certification-coverage-map.md`.

✅ Done (2026-08-28): improvement-176 closed — `/autopilot` review, 5 findings against real repo
  state and the private certification document, all resolved. Fixed directly in
  `.claude/commands/autopilot.md`: duplicate step "4." numbering (renumbered 1-6); a stale/false
  claim that no git-commit hook exists (corrected to describe the real `PreToolUse:Bash` hook +
  `/tmp/commit-approved` marker, already documented in `.claude/rules.md`'s "Two-call rule"); step
  1 rewritten to use real `EnterPlanMode`/`ExitPlanMode` instead of a hand-rolled chat gate (real
  tool mechanics verified directly first — no plan-text parameter, `ExitPlanMode` already requests
  approval on its own); step 3 rewritten to dispatch this project's own `/review`→
  `deep-review-orchestrator` instead of a stale "8 finder angles" reference to the deleted
  `deep-review` skill (`improvement-171`). Decided against, user's explicit call: hook-enforcing
  destructive git commands (force-push/`reset --hard`/etc.) — no clean approval release-valve like
  the commit hook's marker file, would block `/autopilot` from ever completing a run that
  genuinely needs one; stays prompt-enforced, done only in genuine extreme necessity. A follow-up
  full-document certification audit (same session) found one confirmed existing match (parallel
  Agent/Task spawning, already correct) and one candidate finding (whether `.claude/rules.md`'s
  `@import` content survives `/compact` the same way root `CLAUDE.md` does) that the user decided
  to treat as an accepted precaution rather than open a new issue for. The project-wide Approval
  Rule → Plan Mode question (originally this issue's finding 4 before scope was narrowed) continues
  in `improvement-177`. Full detail:
  `completed/issues/improvement-176-autopilot-review-certification-findings.md`.

✅ Done (2026-08-30): improvement-178 closed — unified `SettingsOverlay`/`UserOverlay` into one
  `AccountOverlay` (Name/Settings/Provider Profile tabs), new `ProviderProfileSaveService` in
  `marketplace-orchestrator` mirroring `AdvertisementSaveService`, new
  `AccessEvaluator.canEditUserAccount()`/`canViewUserAccount()`. Five real bugs found and fixed
  during manual testing/review: categories not enriched on re-edit; moderator permission bypass on
  Name/Settings; provider profile created under the wrong owner (`targetUserId`/`actingUserId`
  split — `platform-commons/DECISIONS.md` ADR-030); missing Provider Profile view CSS;
  `UserDeleteService` bypassing the audit-capturing delete path. `ProviderProfileFormOverlayModeHandler`
  hardened with the same defensive `canEditUserAccount()` re-check its Name/Settings siblings
  already had. `marketplace-app/DECISIONS.md` ADR-074 records the overlay unification. Playwright
  `04-provider-profile-flow.spec.js` added (4 tests); specs 04-07 renumbered to 05-08. `/ci` full
  run green except `sonar` — a pre-existing, separately-tracked `new_coverage=0%` gap
  (`improvement-114`, unrelated to this issue's own code, which has 0 new violations). Full detail:
  `completed/issues/improvement-178-account-overlay-provider-profile-tab.md`.

✅ Done (2026-08-31): improvement-175 closed — new `html-sanitizer-lib` module (plain Java library,
  mirrors `query-lib`'s shape) replaces the duplicated `HTML_SANITIZER`/`sanitizeHtml()` logic in
  `AdvertisementService`/`ProviderProfileService` with one shared `HtmlSanitizer.sanitize()`;
  `AdvertisementSaveService`/`ProviderProfileSaveService` in `marketplace-orchestrator` now throw
  `OptimisticLockingFailureException` instead of silently proceeding when a `save()` target row was
  deleted concurrently (`before == null` for a non-new `dto`) — reaches
  `AbstractEntityOverlay.handleSave()`'s existing conflict-handling UI path for free.
  `platform-commons/DECISIONS.md` ADR-027 annotated resolved; new `html-sanitizer-lib/DECISIONS.md`
  ADR-001 and `marketplace-orchestrator/DECISIONS.md` ADR-006 record the two decisions. `deep-review-orchestrator`
  self-review found 0 SOLID/DRY/KISS/YAGNI findings; surfaced 2 stale-doc references (a rules-file
  bullet naming a deleted method, a test class Javadoc describing the old behavior) fixed directly
  in the same run. `/ci` full run green (unit/integration/e2e/archunit/docs) except `sonar` — the
  same pre-existing, separately-tracked `new_coverage=0%` gap (`improvement-114`, unrelated, 0 new
  violations confirmed twice via `sonar-analyst`). Full detail:
  `completed/issues/improvement-175-shared-sanitizer-stale-id-delete-race.md`.

✅ Done (2026-09-02): improvement-179 closed — public Providers catalog (`ProvidersView`/
  `ProviderProfileCardView`/`ProviderProfileDeepLinkView`/`overlay/ProviderProfileCatalogOverlay`+
  `ProviderProfileCatalogViewModeHandler`/query-layer trio), `OgMetaRequestListener`/`AppLinkService`
  extended to provider profiles, a Delete button wired into `ProviderProfileViewModeHandler`, new
  Playwright coverage added to `04-provider-profile-flow.spec.js` (category filter + sort-icon
  coverage, `SUPPORT`-disabled-not-removed assertions). This closes `improvement-124`'s last open
  batch (124-D) — see that issue's own archive entry below. Five real bugs found and fixed during
  verification: (1) `OverlayNavigationRegistry` resolves ADR-059's own documented single-handler
  `History` conflict between `AdvertisementOverlay` and the new catalog overlay —
  `marketplace-app/DECISIONS.md` ADR-076; (2) deep-link tab-selection — `MainView`'s
  `pendingDeepLinkCheckers` map now actually selects the Providers tab when a provider deep link
  opens directly, `ProvidersView`/`AdvertisementsView.openPendingDeepLinkIfAny()` both return
  `boolean`; (3) sitemap cache staleness across specs — `SitemapController` thinned to a new
  `marketplace-orchestrator` `SitemapService` with `invalidate()` called from both save services
  (also ADR-076); (4) `ProviderProfileFormOverlayModeHandler`'s `setReadOnly()` calls were running
  before `buildBinder()` established the Binder's clean baseline, causing a spurious "unsaved
  changes" dialog for a privileged actor viewing another user's already-saved profile — reordered
  after `buildBinder()`; (5) a non-privileged actor's own already-`SUPPORT` profile hit the same
  false-dirty/required-validation failure, since `kindField`'s item list excluded `SUPPORT`
  entirely — fixed via `RadioButtonGroup.setItemEnabledProvider` (kept in the list, disabled, not
  hidden, when it's the actor's real current value). A separate, unrelated infra bug found during
  Sonar re-verification: `scripts/sonar/run.sh`/`scripts/build-and-test/build.sh` both hardcoded a
  module-copy list missing `html-sanitizer-lib` (added when that module was extracted), so the
  scanner referenced a source folder that was never actually copied in — fixed in both scripts.
  Playwright `e2e --ux` green (45 passed, 13 skipped, 0 failed). Sonar's 3 real findings (S7467
  unnamed-pattern catch, S8491 dangling duplicate Javadoc, S1192 duplicated string literal) fixed,
  confirmed `new_violations: 0` via the SonarQube API; the quality gate still shows `ERROR` solely
  on the pre-existing, separately-tracked `new_coverage=0%` gap (`improvement-114`, unrelated to
  this issue's own code). Full detail: `completed/issues/improvement-179-provider-profile-catalog.md`.

✅ Done (2026-09-02): improvement-124 closed — F-04's last open batch (124-D, public Providers
  catalog) shipped via `improvement-179` (see that entry above); nothing left in this issue itself.
  Full history: Batch A (`user_preferences` split) and A2 (`UserDto`/`UserPort` cleanup) shipped
  2026-07-31; Batch B (`provider-profile-spring-boot-starter` module, backend only) shipped
  2026-08-01 (`platform-commons/DECISIONS.md` ADR-027); Batch B2 (shared HTML sanitizer,
  concurrent-delete race fix) moved to and shipped via `improvement-175`; Batch C (unified
  `AccountOverlay`, `ProviderProfileSaveService`) moved to and shipped via `improvement-178`;
  Batch D (this entry) moved to and shipped via `improvement-179`. Full detail:
  `completed/issues/improvement-124-provider-profile.md`.
