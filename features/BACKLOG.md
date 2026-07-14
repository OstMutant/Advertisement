# Issue Backlog — prioritized execution order

Index of all open issues in `issues/`, grouped by execution wave. Each issue file carries the
same assignment in its `**When:**` line — if they ever disagree, the issue file wins and this
index must be updated.

Waves: **Week 0** = cheap independent quick wins; **Wave 1** = prerequisites for public
shareability; **Wave 2** = quality hardening before public traffic; **Wave 3** = with the
corresponding domain work; **Deferred** = trigger-based, do not touch until the trigger fires.

---

## Week 0 — quick wins (~1 day total)

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
stays open (not moved) — step 2 (real async pipeline) remains deferred, see Deferred section.
Full e2e 46/46 green.

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

**Wave 2 is now fully complete except for independent, unblocked items (see below).**

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

**Still open, no longer blocked:**

| Issue | What | Note |
|---|---|---|
| [improvement-041](issues/improvement-041-advertisement-user-sql-join-and-column-naming.md) | Remove `AdvertisementRepository`'s raw SQL `JOIN user_information` (hardcoded cross-starter table/column names) via `UserPort.findByIds()` bulk lookup + rename `*_user_id` columns to match Taxon's `created_by`/`updated_by`/`deleted_by` convention | medium-high — worse than a Java-level violation, ArchUnit (improvement-030) can't catch raw SQL coupling; mirrors improvement-007's already-completed pattern |
| [improvement-042](issues/improvement-042-advertisement-media-denormalized-columns.md) | Remove `advertisement.media_url`/`media_content_type`/`media_count` denormalized columns via a new `AttachmentPort.getMediaSummaries()` bulk lookup, enriched at read time in `AdvertisementService` (same pattern as 041) | medium — real schema-level coupling to attachment's domain vocabulary, confirmed after an earlier wrong dismissal in this same review; JSONB genericization was considered and rejected (repackages the same coupling, no functional gain — media sort columns are confirmed dead/unused) |
| [improvement-025](issues/improvement-025-leaf-ui-components-plain-classes.md) | Convert ~17 stateless leaf UI widgets (buttons/fields/dialogs/layout) from `@SpringComponent` prototype beans to plain Java classes | brings code in line with existing `marketplace-app/CLAUDE.md` "no Configurable for 1-2 setters" rule; execute in 4 phased batches with a full e2e run after each, not one PR |
| [improvement-026](issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md) | Replace ~10 hand-built `new Button(...)` spots (HeaderBar, PaginationBar, attachment lightboxes/gallery, audit restore button, UserPickerField) with existing `Ui*Button` wrappers | medium priority — HeaderBar's 4 auth buttons and 5 other spots are visibly unstyled (real UX bug, not just duplication); HeaderBar batch touches CSS classes nearly every e2e test selects on, run full e2e immediately after that batch |
| [improvement-027](issues/improvement-027-unit-testcontainers-test-layer.md) | Add Testcontainers repository tests + plain unit tests for pure logic (snapshot `diff()`, sanitizer, `resolveTranslation()`) — only 2 JUnit files exist project-wide, both in query-lib | medium-high — already a recorded hard gate for F-08 (payments) in the private roadmap; do now so Phase 1-3 work and the new F-06 review-starter benefit, not just payments |

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

**Migrated from `features/process-improvements.md` (2026-07-13):** that file was a one-time
2026-07-04 process audit, not a tracked backlog — 16 of its 21 items had never been formalized
into an issue file or a BACKLOG row (only buildx, the owasp-sanitizer bump, virtual threads, and
DelegatingPasswordEncoder had been). All still-relevant remaining items are now proper issues
below; `process-improvements.md` itself has been deleted (fully superseded — its content is
preserved across the issues above and this note, not lost) so there is exactly one living backlog.

| Issue | What | Note |
|---|---|---|
| [improvement-027](issues/improvement-027-unit-testcontainers-test-layer.md) | Testcontainers repository tests + plain unit tests (diff/sanitizer/resolveTranslation) | hard gate for F-08 (payments) per private roadmap; do before F-06 (new review-starter) too |
| [improvement-028](issues/improvement-028-minimal-ci-pipeline.md) | Minimal CI pipeline (GitHub Actions: push/PR/nightly) | most valuable once 027 and 030 exist |
| [improvement-029](issues/improvement-029-docs-drift-guard-and-hooks.md) | Docs-drift guard + incremental-compile Claude Code hooks | merges two source items that duplicated each other |
| [improvement-030](issues/improvement-030-archunit-test-module.md) | ArchUnit module — prose architecture rules become build-breaking tests | highest ROI per the original audit; would have caught improvement-011/010 |
| [improvement-031](issues/improvement-031-maven-enforcer-plugin.md) | Maven Enforcer — ban starter→starter deps, dependencyConvergence | low effort |
| [improvement-032](issues/improvement-032-sonarqube-quality-gate-blocking.md) | Make SonarQube quality gate blocking (server/script already exist) | config flip only |
| [improvement-033](issues/improvement-033-quality-gate-skill-and-definition-of-done.md) | `/quality-gate` skill + Definition of Done in rules.md | blocked on 027 + 030 + 032 |
| [improvement-034](issues/improvement-034-feature-workflow-standardization.md) | SPEC.md template + `/feature` skill | low priority, convenience only |
| [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | SQL-seed spec 05 instead of UI-driven — full e2e 11 min → ~7-8 min | low urgency, pure speed |
| [improvement-036](issues/improvement-036-actuator-structured-logging.md) | Actuator + structured JSON logging | do not confuse with already-done improvement-023 (MDC requestId only) |
| [improvement-037](issues/improvement-037-accessibility-contrast-and-aria.md) | Fix WCAG AA contrast failure (header text), focus states, ARIA labels | legally relevant (EAA since June 2025) — do not defer as cosmetic |
| [improvement-038](issues/improvement-038-pg-trgm-title-index.md) | `pg_trgm` GIN index on `advertisement.title` | trigger-based — do as data volume grows |
| [improvement-039](issues/improvement-039-dark-mode-lumo-tokens.md) | Dark mode via Lumo token migration | pair with 037 — same CSS files |
| [improvement-040](issues/improvement-040-spring-boot-vaadin-minor-bump.md) | Spring Boot 4.1.0 + Vaadin 25.2.1 bump | re-check latest versions before starting, scan is 9+ days old |

**Deliberately not migrated as separate issues** (already tracked elsewhere, or explicitly
rejected/deferred with no concrete trigger in the source document — creating an issue for these
would be backlog noise, not hygiene): deep links (→ private `F-01`), thumbnails on upload (→
dependency of private `F-01`), AI-assist (→ private `F-10`), OpenRewrite/PIT/Error Prone/
Checkstyle/JSpecify/CDS-AOT-cache (explicitly deferred or rejected in the source document itself).

## Wave 3 — with the corresponding domain work

| Issue | What | Pairs with |
|---|---|---|
| [improvement-002](issues/improvement-002-snapshot-schema-versioning.md) | Snapshot schema versioning | before the first new snapshot-bearing domain |
| [improvement-019](issues/improvement-019-findtimeline-correlated-subqueries.md) | findTimeline window-function rewrite | any audit-starter touch |

Plus: Testcontainers test layer is a hard gate before any payment code.

## Deferred — trigger-based (do not touch until the trigger fires)

| Issue | Trigger |
|---|---|
| [improvement-003](issues/improvement-003-deferred-performance.md) (items A-K) | per-item triggers inside the file |
| [improvement-021](issues/improvement-021-attachment-concurrency-and-batching.md) | concurrent gallery editing in practice; item A joins any attachment schema touch |
| [improvement-017](issues/improvement-017-sync-s3-upload-in-request-thread.md) (step 2) | bundled with the thumbnail-pipeline refactor |
| [improvement-008](issues/improvement-008-deleted-category-strikethrough.md), [improvement-010](issues/improvement-010-advertisements-view-refresh-error-notification.md), [improvement-014](issues/improvement-014-uuid-filenames-in-media-diff.md) | cosmetic — batch into any nearby UI-touching PR |
| [goal-001](issues/goal-001-activity-field-visibility-by-role.md) | user feedback |

---

## Maintenance rules

- New issue → add its `**When:**` line AND a row here, in the same change.
- Issue resolved → move the file to `completed/issues/` (per rules.md) AND remove its row
  here, in the same change.
