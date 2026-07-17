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
| 1 | [improvement-061](issues/improvement-061-supportutil-tolong-silent-truncation-id-filter.md) | Still open | `SupportUtil.toLong(Double)` silently truncates fractional user-id filter input, no validation error shown | 🟡 high ROI — real, silent filter-correctness bug, found via direct code review |
| 2 | [improvement-079](issues/improvement-079-formoverlaymodehandler-activity-tab-duplication-and-userid-bug.md) | New | `UserFormOverlayModeHandler` passes the wrong `userId` (subject, not viewer) into `AuditActivityPanel` — a real, currently-masked audit-visibility bug, found while verifying a UI dedup proposal | 🟡 high ROI — traced through actual SQL, confirmed bug (not just duplication); currently masked by `User` entity ownership semantics but real |
| 3 | [improvement-060](issues/improvement-060-advertisementenrichservice-listallbytype-instead-of-findbyids.md) | Still open | `AdvertisementEnrichService.resolveCategoryNames()` loads the entire category table instead of `TaxonPort.findByIds()` | 🟡 medium ROI — no bug at today's volume, but a genuine memory-wall risk as the taxon table grows; fix is a one-call swap to an API that already exists |
| 4 | [improvement-067](issues/improvement-067-taxontranslationrepository-unbounded-in-clause.md) | Still open | `TaxonTranslationRepository.findAllByTaxonIds()` uses an unbounded `IN (:taxonIds)` clause | 🟡 medium ROI — same class of issue improvement-054 already fixed elsewhere, proven pattern, one method missed |
| 5 | [improvement-064](issues/improvement-064-s3storageservice-inputstream-not-closed.md) | Still open | `S3StorageService.upload()` never closes the `InputStream` it's given | 🟡 medium ROI — cheap, safe defensive fix; real severity uncertain (Vaadin's own upload machinery may already cover it) |
| 6 | [improvement-069](issues/improvement-069-attachment-s3-move-inside-db-transaction-orphans-on-rollback.md) | Still open | S3 file move (`commitTempUploadsQuiet`) happens inside `AdvertisementSaveService`'s DB transaction — orphaned S3 objects on rollback, never swept | 🟡 medium ROI — real data-integrity gap, confirmed structural (not yet an observed incident); orphans are permanently unswept by existing cleanup |
| 7 | [improvement-070](issues/improvement-070-attachmentsnapshotrepository-unsafe-array-cast-silent-swallow.md) | Still open | `AttachmentSnapshotRepository.extractUrls()` — unsafe `(String[])` cast wrapped in a silent catch-all with no logging | 🟡 medium ROI — real robustness/observability gap; a real failure here would look like "attachments vanished," not an error |
| 8 | [improvement-068](issues/improvement-068-attachment-audit-shows-uuid-not-original-filename.md) | Still open | Attachment audit/timeline diffs show S3 UUID keys instead of original filenames | 🟡 medium ROI — real, user-visible UX bug on every media change, no data-loss risk |
| 9 | [improvement-071](issues/improvement-071-taxonformoverlaymodehandler-raw-uicomponentfactory.md) | Still open | `TaxonFormOverlayModeHandler` uses a raw `UiComponentFactory<OverlayFormBinder>` — the other 3 form handlers already don't | 🟡 medium ROI — cheap, safe, proven pattern already used by 3 sibling handlers, just not adopted here yet |
| 10 | [improvement-075](issues/improvement-075-timeline-actor-filter-multi-select.md) | New | Timeline actor filter — support selecting multiple actors (add, not replace), removable chips | 🟡 medium ROI — real UX improvement, user-requested; moderate cross-layer scope (DTO/SQL trivial, UI is the bulk of the work) |
| 11 | [improvement-076](issues/improvement-076-advertisementcardview-redundant-stoppropagation.md) | New | `AdvertisementCardView` — remove redundant stopPropagation listeners (already registered by `BaseActionButton`) | 🟡 medium ROI — trivial, safe, zero-risk quick win |
| 12 | [improvement-077](issues/improvement-077-advertisementcardview-dead-updatedat-null-check.md) | New | `AdvertisementCardView` — remove dead `updatedAt == null` half of a check (`@LastModifiedDate` guarantees non-null) | 🟡 medium ROI — trivial, safe, zero-risk quick win |
| 13 | [improvement-082](issues/improvement-082-cardlightboxviewer-redundant-queryselector.md) | New | `CardLightboxViewer` — remove redundant `document.querySelector` JS calls, use held `iframe`/`videoEl` references directly; fixes a latent cross-instance control bug | 🟡 medium ROI — confirmed redundant code plus a genuine (low-probability) correctness bug |
| 14 | [improvement-078](issues/improvement-078-queryblock-filterrow-helper.md) | New | `QueryBlock` — extract `filterRow()` helper to collapse ~13 duplicated filter-row blocks across `AdvertisementQueryBlock`/`UserQueryBlock`/`TimelineQueryBlock` | 🟡 medium ROI — confirmed duplication, moderate structural change, additive/low-risk |
| 15 | [improvement-080](issues/improvement-080-taxonformoverlaymodehandler-locale-field-dedup.md) | New | `TaxonFormOverlayModeHandler` — collapse ~60-70 duplicated lines of EN/UK locale field wiring across 5 methods | 🟡 medium ROI — confirmed duplication, but touches binder validation — do carefully |
| 16 | [improvement-081](issues/improvement-081-lightbox-embedurl-and-iframe-attrs-duplication.md) | New | Extract duplicated embed-URL resolution + iframe security attributes from `AttachmentLightbox`/`CardLightboxViewer` | 🟡 medium ROI — verbatim-identical logic, low-risk extraction |
| 17 | [improvement-083](issues/improvement-083-advertisementcardview-thumbnail-click-no-op-when-attachment-port-unavailable.md) | New | `AdvertisementCardView` thumbnail — decide UX when `AttachmentPort` unavailable mid-session (narrower than originally framed — verified media fields are never denormalized, ADR-035) | 🟡 medium ROI — real but narrow edge case after correction |
| 18 | [improvement-025](issues/improvement-025-leaf-ui-components-plain-classes.md) | Still open | Convert ~17 stateless leaf UI widgets from `@SpringComponent` beans to plain Java classes | 🔵 larger tech-debt — no bug, 4 phased batches, brings code in line with an existing but unfollowed rule |
| 19 | [improvement-036](issues/improvement-036-actuator-structured-logging.md) | Migrated | Actuator + structured JSON logging | 🔵 larger tech-debt |
| 20 | [improvement-039](issues/improvement-039-dark-mode-lumo-tokens.md) | Migrated | Dark mode — step 1 (tokenization) done via improvement-037, step 2 (palette values + toggle) remains | 🔵 larger tech-debt — infra prerequisite already shipped |
| 21 | [improvement-040](issues/improvement-040-spring-boot-vaadin-minor-bump.md) | Migrated | Spring Boot 4.1.0 + Vaadin 25.2.1 bump | 🔵 larger tech-debt — routine, re-check latest versions before starting |
| 22 | [improvement-029](issues/improvement-029-docs-drift-guard-and-hooks.md) | Migrated | Docs-drift guard + incremental-compile Claude Code hooks | 🔵 larger tech-debt |
| 23 | [improvement-033](issues/improvement-033-quality-gate-skill-and-definition-of-done.md) | Migrated | `/quality-gate` skill + Definition of Done in rules.md — now unblocked (027/030/032 all done), likely reduces to just recording the Definition of Done since `/ci` already does the chaining | 🔵 larger tech-debt — cheap now that its prerequisites and most of its substance already exist |
| 24 | [improvement-062](issues/improvement-062-missing-readonly-transactional-on-port-impls.md) | Still open | `UserPortImpl`/`AdvertisementPortImpl`/`DefaultTaxonPort` have no `@Transactional(readOnly=true)`, unlike `DefaultAuditPort` | 🔵 larger tech-debt — consistency/read-correctness, no known bug from this today |
| 25 | [improvement-065](issues/improvement-065-settingspaginationservice-detach-not-guaranteed-on-session-expiry.md) | Still open | `SettingsPaginationService`'s `DetachListener` cleanup isn't guaranteed on abrupt session expiry | 🔵 larger tech-debt — follow-up on top of an already-shipped fix (ADR-028), not a fresh leak |
| 26 | [improvement-072](issues/improvement-072-uicomponentfactory-generics-design-debt.md) | Still open | Generics/type-safety design debt — `UiComponentFactory<T>`'s dual role, `AuditReadService`'s raw hook dispatch, `AuditDomainHookImpl.castIfKnown`'s missing type token | 🔵 larger tech-debt — needs a design decision, not a mechanical fix; no observed correctness bug from any of the three today |
| 27 | [improvement-038](issues/improvement-038-pg-trgm-title-index.md) | Migrated | `pg_trgm` GIN index on `advertisement.title` | 🔵 larger tech-debt — trigger-based (do as data volume grows), listed here only for completeness |
| 28 | [improvement-063](issues/improvement-063-playwright-stability-guard-async-init-components.md) | Still open | No "ready" signal for async-initialized custom components (`QuillEditor`, `AttachmentGallery`) — potential Playwright flakiness | ⚪ lowest priority — preventive, no flaky failure from this cause actually observed yet |
| 29 | [improvement-028](issues/improvement-028-minimal-ci-pipeline.md) | Migrated | Minimal CI pipeline (GitHub Actions: push/PR/nightly) | ⚪ lowest priority — improvement-059 (its former blocker) is done, but its own open questions (push authorization, `gh` CLI setup, clean-runner environment) are unrelated and still unresolved |
| 30 | [improvement-073](issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md) | New | Add test-only, dev-gated REST endpoints for Playwright seeding (not this app's first REST controller — `HealthController` already exists, just not profile-gated/business-logic-invoking) | ⚪ lowest priority — deliberately sequenced after everything achievable on the existing codebase; new infrastructure surface area, not a fix |
| 31 | [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | Migrated | Service-layer-seed spec 05 via REST (raw SQL confirmed non-viable — breaks Test 6's timeline/audit assertions) — full e2e 11 min → ~7-8 min | ⚪ lowest priority — blocked on improvement-073; pure speed optimization, no coverage gap |

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

**Migrated from `backlog/process-improvements.md` (2026-07-13):** that file was a one-time
2026-07-04 process audit, not a tracked backlog — 16 of its 21 items had never been formalized
into an issue file or a BACKLOG row (only buildx, the owasp-sanitizer bump, virtual threads, and
DelegatingPasswordEncoder had been). All still-relevant remaining items are now proper issues
below; `process-improvements.md` itself has been deleted (fully superseded — its content is
preserved across the issues above and this note, not lost) so there is exactly one living backlog.

| Issue | What | Note |
|---|---|---|
| [improvement-028](issues/improvement-028-minimal-ci-pipeline.md) | Minimal CI pipeline (GitHub Actions: push/PR/nightly) | ⚪ lowest priority — improvement-059 (local runner) is done; own open questions (push auth, `gh` CLI) remain |
| [improvement-029](issues/improvement-029-docs-drift-guard-and-hooks.md) | Docs-drift guard + incremental-compile Claude Code hooks | merges two source items that duplicated each other |
| [improvement-030](issues/improvement-030-archunit-test-module.md) | ArchUnit module — prose architecture rules become build-breaking tests | highest ROI per the original audit; would have caught improvement-011/010 |
| [improvement-033](issues/improvement-033-quality-gate-skill-and-definition-of-done.md) | `/quality-gate` skill + Definition of Done in rules.md | unblocked (027/030/032 all done) — `/ci` already does most of the chaining this issue describes |
| [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | Service-layer-seed spec 05 via REST (not raw SQL — would lose audit_log rows Test 6 depends on) — full e2e 11 min → ~7-8 min | blocked on improvement-073 (REST endpoint infra) |
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

- New issue → add its `**Priority:**` and `**When:**` lines AND a *ranked* row in the Priority
  order table above (not just left for later triage) — all in the same change. See `.claude/rules.md`
  "Issue Lifecycle".
- Issue resolved → move the file to `completed/issues/` (per rules.md), remove its row here, AND
  add a one-line `✅ Done (date): ...` entry to [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md) under the relevant wave
  — all in the same change.
