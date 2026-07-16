# Issue Backlog — prioritized execution order

Index of all open issues in `issues/`, grouped by execution wave. Each issue file carries the
same assignment in its `**When:**` line — if they ever disagree, the issue file wins and this
index must be updated.

Completed work history lives in [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md) — this file tracks only what's still
actionable, so it stays short and scannable.

Waves: **Week 0** = cheap independent quick wins; **Wave 1** = prerequisites for public
shareability; **Wave 2** = quality hardening before public traffic; **Wave 3** = with the
corresponding domain work; **Deferred** = trigger-based, do not touch until the trigger fires.

---

## Priority order (2026-07-15) — critical → cheapest → high-ROI → larger tech-debt

Reshuffle of every currently-actionable, non-trigger-gated issue (the two Wave-2 tables below:
"Still open, no longer blocked" and "Migrated from `process-improvements.md`" — kept as `Origin`
here so provenance isn't lost). Wave 3 and the "Deferred — trigger-based" section are **not**
reordered — they're already gated by an external trigger, so ranking within them doesn't matter
until that trigger fires.

| # | Issue | Origin | What | Why this tier |
|---|---|---|---|---|
| 1 | [improvement-028](issues/improvement-028-minimal-ci-pipeline.md) | Migrated | Minimal CI pipeline (GitHub Actions: push/PR/nightly) | 🟡 grouped with #2 — build the CI first, `improvement-027` prerequisite already done |
| 2 | [improvement-032](issues/improvement-032-sonarqube-quality-gate-blocking.md) | Migrated | Make SonarQube quality gate blocking (server/script already exist) | 🟡 grouped with #1 — config flip is cheap on its own, but only becomes real once #1's CI exists to run it on every push, not just manually |
| 3 | [improvement-057](issues/improvement-057-notificationservice-close-button-decision.md) | Still open | Decide whether `NotificationService`'s close button converts to `UiIconButton` | 🟢 cheapest — trivial decision, no code required either way |
| 4 | [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | Migrated | SQL-seed spec 05 instead of UI-driven — full e2e 11 min → ~7-8 min | 🟢 cheapest — contained, pure speed |
| 5 | [improvement-034](issues/improvement-034-feature-workflow-standardization.md) | Migrated | SPEC.md template + `/feature` skill | 🟢 cheapest — low priority, convenience only, but low effort too |
| 6 | [improvement-030](issues/improvement-030-archunit-test-module.md) | Migrated | ArchUnit module — prose architecture rules become build-breaking tests | 🟡 high ROI — "highest ROI per the original audit; would have caught improvement-011/010" |
| 7 | [improvement-056](issues/improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md) | Still open | `UserPickerField`'s `CallbackDataProvider` offset→page pagination correctness bug + inline search-button gap | 🟡 high ROI — item 2 is a real correctness bug at scale, not just cosmetic |
| 8 | [improvement-058](issues/improvement-058-taxon-assignment-audit-trail-missing.md) | Still open | Category/taxon assignment changes on advertisements are never recorded to the audit log — a stated ADR-019 goal never shipped | 🟡 high ROI — real, silent audit-trail gap, found via `/sync-docs --full-audit` |
| 9 | [improvement-048](issues/improvement-048-service-layer-test-coverage.md) | Still open | Cover `marketplace-app`'s non-UI service layer with unit tests | 🟡 high ROI — follow-up to already-done improvement-045 item 1, pattern already proven |
| 10 | [improvement-047](issues/improvement-047-integration-tests-ci-safety.md) | Still open | Keep `integration-tests` out of a plain `mvn install`/`mvn test`, Docker precheck, `.env` doc note | 🟡 high ROI — CI-safety, corrected version of an external PR review |
| 11 | [improvement-044](issues/improvement-044-shared-env-config-consolidation.md) | Still open | Consolidate DB/MinIO credentials duplicated across 4-5 files into root `.env` | 🟡 high ROI — pure drift-risk reduction |
| 12 | [improvement-025](issues/improvement-025-leaf-ui-components-plain-classes.md) | Still open | Convert ~17 stateless leaf UI widgets from `@SpringComponent` beans to plain Java classes | 🔵 larger tech-debt — no bug, 4 phased batches, brings code in line with an existing but unfollowed rule |
| 13 | [improvement-036](issues/improvement-036-actuator-structured-logging.md) | Migrated | Actuator + structured JSON logging | 🔵 larger tech-debt |
| 14 | [improvement-039](issues/improvement-039-dark-mode-lumo-tokens.md) | Migrated | Dark mode — step 1 (tokenization) done via improvement-037, step 2 (palette values + toggle) remains | 🔵 larger tech-debt — infra prerequisite already shipped |
| 15 | [improvement-040](issues/improvement-040-spring-boot-vaadin-minor-bump.md) | Migrated | Spring Boot 4.1.0 + Vaadin 25.2.1 bump | 🔵 larger tech-debt — routine, re-check latest versions before starting |
| 16 | [improvement-029](issues/improvement-029-docs-drift-guard-and-hooks.md) | Migrated | Docs-drift guard + incremental-compile Claude Code hooks | 🔵 larger tech-debt |
| 17 | [improvement-033](issues/improvement-033-quality-gate-skill-and-definition-of-done.md) | Migrated | `/quality-gate` skill + Definition of Done in rules.md | 🔵 larger tech-debt — still practically blocked (needs 030 + 032 landed first, not just 027) |
| 18 | [improvement-038](issues/improvement-038-pg-trgm-title-index.md) | Migrated | `pg_trgm` GIN index on `advertisement.title` | 🔵 larger tech-debt — trigger-based (do as data volume grows), listed here only for completeness |

---

## Week 0 — quick wins (~1 day total)

**Week 0 is now complete.** History: [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md#week-0--quick-wins).

## Wave 1 — prerequisites for public shareability

**Wave 1 is now fully complete.** History: [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md#wave-1--prerequisites-for-public-shareability).

## Wave 2 — quality hardening before public traffic

**Wave 2 is now fully complete except for independent, unblocked items (see below).** History:
[BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md#wave-2--quality-hardening-before-public-traffic).

**Still open, no longer blocked:**

| Issue | What | Note |
|---|---|---|
| [improvement-025](issues/improvement-025-leaf-ui-components-plain-classes.md) | Convert ~17 stateless leaf UI widgets (buttons/fields/dialogs/layout) from `@SpringComponent` prototype beans to plain Java classes | brings code in line with existing `marketplace-app/CLAUDE.md` "no Configurable for 1-2 setters" rule; execute in 4 phased batches with a full e2e run after each, not one PR |
| [improvement-044](issues/improvement-044-shared-env-config-consolidation.md) | DB and MinIO/S3 credentials duplicated across 4-5 files each (compose YAML, `application-dev.yml`, `deploy.sh`, `playwright/run.sh`) — consolidate into the shared root `.env` established by improvement-027's Batch 0 | medium — no current bug (copies still agree), pure drift-risk reduction; found while investigating improvement-027's `postgres:15-alpine` duplication |
| [improvement-047](issues/improvement-047-integration-tests-ci-safety.md) | Keep `integration-tests` out of a plain `mvn install`/`mvn test` (Maven profile vs. `@Tag` decision), Docker precheck in `run.sh`, `SharedEnvConfig` unit test, `.env` doc note, CI-guard for sandbox env vars | medium — corrected/de-duplicated version of an external PR #20 review; original review's test-coverage items were already improvement-045 (partially done), its `.env.example` suggestion didn't fit this repo's committed-no-secrets `.env` model |
| [improvement-048](issues/improvement-048-service-layer-test-coverage.md) | Cover `marketplace-app`'s non-UI service layer (`AdvertisementSaveService`, `AdvertisementEnrichService`, `AuthContextService`) with unit tests in `marketplace-app/src/test/java`, mirroring `src/main`'s package layout — one consistent home for this layer's tests | medium — follow-up to improvement-045 item 1 (`AccessEvaluatorTest`), which proved the pattern; verified this UI-free `services/*` layer already exists (zero `com.vaadin.*` imports across all 10 files) |
| [improvement-056](issues/improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md) | `UserPickerField`'s inline search-button variant gap + `CallbackDataProvider` offset→page pagination correctness bug (only lazy-loading grid in the app, untriggered so far because the Playwright seed volume exactly matches Grid's default page size) | medium — item 2 is a real correctness bug at scale, found while scoping improvement-026 Batch 4 |
| [improvement-057](issues/improvement-057-notificationservice-close-button-decision.md) | Decide whether `NotificationService`'s close button converts to the `UiIconButton` wrapper (plain `@Service`, raw Lumo glyph icon — not a mechanical refactor) | low — consistency-only design decision, deliberately excluded from improvement-026 |
| [improvement-058](issues/improvement-058-taxon-assignment-audit-trail-missing.md) | Category/taxon assignment changes on advertisements are never recorded to the audit log — `TaxonAuditHookImpl`/`TaxonActivityService` referenced by ADR-019 were never implemented, `services/audit/taxon/` is an empty package | medium — a stated architectural goal never shipped; found during a full `/sync-docs --full-audit` pass |

**Migrated from `backlog/process-improvements.md` (2026-07-13):** that file was a one-time
2026-07-04 process audit, not a tracked backlog — 16 of its 21 items had never been formalized
into an issue file or a BACKLOG row (only buildx, the owasp-sanitizer bump, virtual threads, and
DelegatingPasswordEncoder had been). All still-relevant remaining items are now proper issues
below; `process-improvements.md` itself has been deleted (fully superseded — its content is
preserved across the issues above and this note, not lost) so there is exactly one living backlog.

| Issue | What | Note |
|---|---|---|
| [improvement-028](issues/improvement-028-minimal-ci-pipeline.md) | Minimal CI pipeline (GitHub Actions: push/PR/nightly) | most valuable once 027 and 030 exist |
| [improvement-029](issues/improvement-029-docs-drift-guard-and-hooks.md) | Docs-drift guard + incremental-compile Claude Code hooks | merges two source items that duplicated each other |
| [improvement-030](issues/improvement-030-archunit-test-module.md) | ArchUnit module — prose architecture rules become build-breaking tests | highest ROI per the original audit; would have caught improvement-011/010 |
| [improvement-032](issues/improvement-032-sonarqube-quality-gate-blocking.md) | Make SonarQube quality gate blocking (server/script already exist) | config flip only |
| [improvement-033](issues/improvement-033-quality-gate-skill-and-definition-of-done.md) | `/quality-gate` skill + Definition of Done in rules.md | blocked on 027 + 030 + 032 |
| [improvement-034](issues/improvement-034-feature-workflow-standardization.md) | SPEC.md template + `/feature` skill | low priority, convenience only |
| [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | SQL-seed spec 05 instead of UI-driven — full e2e 11 min → ~7-8 min | low urgency, pure speed |
| [improvement-036](issues/improvement-036-actuator-structured-logging.md) | Actuator + structured JSON logging | do not confuse with already-done improvement-023 (MDC requestId only) |
| [improvement-038](issues/improvement-038-pg-trgm-title-index.md) | `pg_trgm` GIN index on `advertisement.title` | trigger-based — do as data volume grows |
| [improvement-039](issues/improvement-039-dark-mode-lumo-tokens.md) | Dark mode — step 1 (tokenization) done via improvement-037, only step 2 (palette + toggle) remains | infra prerequisite already shipped |
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
| [improvement-008](issues/improvement-008-deleted-category-strikethrough.md), [improvement-010](issues/improvement-010-advertisements-view-refresh-error-notification.md) (scope now also covers `TimelineView.refresh()`, same missing-notification shape, found via improvement-055), [improvement-014](issues/improvement-014-uuid-filenames-in-media-diff.md) | cosmetic — batch into any nearby UI-touching PR |
| [goal-001](issues/goal-001-activity-field-visibility-by-role.md) | user feedback |
| [improvement-046](issues/improvement-046-list-stability-under-concurrent-edits.md) | product decision on which option (A-E) to pursue — offset pagination over the activity-sorted advertisement list has no stable-view guarantee under concurrent edits; captures a design discussion, not an agreed fix |
| [improvement-052](issues/improvement-052-first-admin-registration-toctou-race.md) | project nearing production readiness — `UserService.register()` first-admin TOCTOU race, accepted risk for now (narrow window, only the instant of a fresh instance's very first registration); extracted from improvement-050 item 1 |
| [improvement-053](issues/improvement-053-advertisement-listing-expiry-archive-strategy.md) | real `advertisement` row count/growth approaching a scale where list-query latency is measurably affected, or a product decision on what "listing expiry" means to sellers/buyers — advertisement archive/expiry storage strategy (status column vs. separate archive table vs. Postgres partitioning), design discussion only, no agreed fix; extracted from improvement-050 item 2 discussion |
| [improvement-055](issues/improvement-055-ui-vaadin-template-consistency-audit.md) | before the next large UI-pattern rollout, or a dedicated UI consistency pass; design discussion only, no agreed fix — most Configurable-shape findings already superseded by improvement-025, remaining findings (CSS naming, TimeZoneUtil/InstantFormatter split, badge/empty-state duplication) need a standardization decision first |

---

## Maintenance rules

- New issue → add its `**When:**` line AND a row here, in the same change.
- Issue resolved → move the file to `completed/issues/` (per rules.md), remove its row here, AND
  add a one-line `✅ Done (date): ...` entry to [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md) under the relevant wave
  — all in the same change.
