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
