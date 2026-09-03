# Issue Backlog — prioritized execution order

Index of all open issues in `issues/`, grouped into **execution batches** — sets of issues that
touch the same files/domain and ship together in one pass (see "Execution batches" below).
Each issue file carries the same assignment in its `**When:**` line — if they ever disagree, the
issue file wins and this index must be updated.

Completed work history lives in [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md) — this file tracks only what's still
actionable, so it stays short and scannable.

Historical waves (Week 0 / Wave 1 / Wave 2 = completed phases, see their sections below;
**Wave 3** = with the corresponding domain work; **Deferred** = trigger-based, do not touch until
the trigger fires).

---

## Execution batches (2026-07-19) — priority order, grouped by one-pass fixability

Regrouping of the former flat priority table (2026-07-15 reshuffle, re-ranked 2026-07-19 after
the pattern-focused code review filed improvement-087–095): issues that touch the same files or
domain and can be implemented and verified together now sit in one batch — **one batch = one
pass** (one PR unless noted, one test run). Batches are ordered by priority; inside a batch,
items are listed in suggested execution order. `Origin` is preserved from the previous tables so
provenance isn't lost. If a batch assignment here ever disagrees with an issue file's `**When:**`
line, the issue file wins and this index must be updated.

Trigger-gated work stays in Wave 3 / "Deferred" below and is deliberately not batched, with two
exceptions folded in here because a batch *is* the trigger they were waiting for:
improvement-019 (→ Batch H, an audit-starter touch) and the improvement-008/010/014 cosmetic trio
(→ Batch F, a nearby UI-touching PR).

### At a glance

**Completed** (✅ — see `completed/BACKLOG-ARCHIVE.md` for full detail on each): improvement-072;
117 (F-01 — its one non-automatable item carved out into improvement-118); 120; 119 (F-02); 122
(F-03); 002 (paired ahead of F-04's snapshot-bearing schema); 128 (`EntityActivityOverlay`
rollout across all 5 domains, see `marketplace-app/DECISIONS.md` ADR-067); 134
(`.claude/nav/` navigation layer); 137 (`doc-standards` skill + dedup pass, companion
improvement-139); improvement-124 Batches A/A2 (preferences-table split + `UserDto`/`UserPort`
cleanup, see `marketplace-app/DECISIONS.md` ADR-070/071 and `platform-commons/DECISIONS.md`
ADR-026). improvement-121 (repo-wide SOLID/DRY findings) was filed then deprioritized to lowest
⚪ the same day — an autopilot execution attempt was aborted before landing anything, cleanly
rolled back.

**improvement-140 completed 2026-08-04** — see `completed/BACKLOG-ARCHIVE.md`; finished the
duplicate-fact dedup improvement-137 deferred and replaced its hedges with real fixes.
improvement-138 is now unblocked (Track A).

**improvement-141 completed 2026-08-04** — see `completed/BACKLOG-ARCHIVE.md`; stripped every
issue-number reference and dated "resolved"/"corrected" narrative from current-state docs
(architecture docs, `CLAUDE.md`/`README.md`, skills, commands, scripts, all `DECISIONS.md`) — new
standing rule in `.claude/rules.md`.

**improvement-138 split, improvement-135 closed, 2026-08-28** — Track A's execution history
(fully done) moved to `completed/issues/improvement-138-architecture-control-plane-track-a.md`;
`improvement-135`'s still-open items 3 (does `context-loading.md` empirically reduce reads) and 5
(governing rule gating new `.claude/nav/*`-shaped content, including Track B) were the same open
hypothesis as `improvement-138`'s own Track B/B2 question, so moved into `improvement-138`
verbatim — `improvement-135` had nothing still-open left and is now closed, see
`completed/issues/improvement-135-ai-nav-layer-validation-and-adr-index-ci-check.md`.
`improvement-138` now tracks only Track B plus the absorbed nav-layer-validation question.

**improvement-124 (F-04) shipped in full, 2026-09-02** — `provider_profile` + `user_preferences` +
unified "My Account" overlay, one combined piece of work (see `platform-commons/DECISIONS.md`
ADR-027); Batches A/A2/B/B2/C/D all shipped, the last (D, public Providers catalog) via
`improvement-179` — see `completed/BACKLOG-ARCHIVE.md` for both entries. improvement-136
(`marketplace-orchestrator` extraction) shipped 2026-08-07 — see `completed/BACKLOG-ARCHIVE.md`.
**improvement-147 (package flatten + Bounded Contexts node + true-BFF migration) shipped
2026-08-08** — see `completed/BACKLOG-ARCHIVE.md`; its original single-caller-collaborator
question moved to `improvement-124` Batch 124-C.
**improvement-149 (Diagrams clarity: SPI Map/Bounded Contexts split, Hook relocation, payload
fix) shipped 2026-08-11** — see `completed/BACKLOG-ARCHIVE.md`; follow-up tightened further by
**improvement-150, shipped 2026-08-13** — see `completed/BACKLOG-ARCHIVE.md`.
**improvement-151 (architecture-generator content-drift cleanup) shipped 2026-08-13** — see
`completed/BACKLOG-ARCHIVE.md`; its unfinished `build.sh` topic, a Track B/ArchUnit unblock
investigation, and its SPI Interface Details table redesign idea moved to improvement-152.
**improvement-152 (build/test consolidation + Tooling & Pipelines regroup) shipped 2026-08-17** —
see `completed/BACKLOG-ARCHIVE.md`; Parts B/C/E split to improvement-156/157/155, two Part A loose
ends split to improvement-153/154.
**improvement-154 (deploy-and-run reuses build-and-test.sh's shared jar) shipped 2026-08-17** —
see `completed/BACKLOG-ARCHIVE.md`; also unblocked the `scripts/deploy.sh` → `scripts/deploy-and-run/`
restructure this issue itself was sequenced after, and spun off `improvement-158` (same reuse
pattern applied to `scripts/sonar/run.sh`), also shipped the same session.
**improvement-153 (Dagu-backed local CI visualization, replacing `progress.txt` polling) shipped
2026-08-18** — see `completed/BACKLOG-ARCHIVE.md`.
**improvement-159 (full 9-step ADR system review, executed for real) shipped 2026-08-19** — see
`completed/BACKLOG-ARCHIVE.md`; 229 → 172 active ADRs across all 13 non-empty `DECISIONS.md` files.
**improvement-155 (repo-wide `infra-doc-standards` rollout) shipped 2026-08-20** — see
`completed/BACKLOG-ARCHIVE.md`; `.claude/nav/scripts/` split off to `improvement-161`.
**improvement-168 (AI guidance refactor) shipped 2026-08-25** — see `completed/BACKLOG-ARCHIVE.md`;
memory 55 → 16 files, all 13 module `CLAUDE.md` moved to path-scoped `.claude/rules/*.md`; one
deferred finding split off to `improvement-133` entry 13.
improvement-118 stays blocked (needs a public URL this sandbox doesn't have).

**improvement-111 shipped 2026-09-03** — service-boundary authorization moved to
`marketplace-orchestrator` (not per-starter, not UI-only); see `marketplace-orchestrator/DECISIONS.md`
ADR-007. Unblocks `improvement-073`'s external API scope. See `completed/BACKLOG-ARCHIVE.md`.

| Priority | Tier | Issues (in execution order) | One pass = |
|---|---|---|---|
| Top | 🟡 | 138 | improvement-138 — "Architecture Control Plane". **Track A completed 2026-08-04**, its full execution history archived to `completed/issues/improvement-138-architecture-control-plane-track-a.md` on 2026-08-28. This issue's live scope is now **Track B** (ArchUnit contract/test model + AI-token-savings hypothesis, not started) plus **`improvement-135`'s absorbed items 3/5** (does the existing hand-authored `.claude/nav/` layer earn its cost — mechanism built, empirical answer pending real accumulated data; governing rule — no new `.claude/nav/*`-shaped content, including Track B, until that data shows a gap). `improvement-135` had nothing else still-open and is now closed (see `completed/BACKLOG-ARCHIVE.md`) |
| Top | 🟡 | 073 | improvement-073 — REST API infrastructure; unblocked now that `improvement-111` (authorization at the service boundary) shipped 2026-09-03 |
| Nice to have | — | 035, 096, 129, 036, 039, 065, 114, 063, 028, 130, 131, 133, 172, 142, 177 | everything else — no internal priority order, pick up opportunistically |
| (Blocked) | 🔵 | 118 | F-01 real-world Open Graph preview verification — manual check in an actual Facebook post/Telegram chat, needs a public URL this sandbox doesn't have; pick up whenever that becomes available |

Details, links, and per-batch rationale below.

### Top priority — improvement-138

| Issue | Origin | What |
|---|---|---|
| [improvement-138](issues/improvement-138-architecture-control-plane.md) | New (user-supplied task, 2026-08-04, "FINAL VISION v2" — supersedes an unseen `architecture-observability-vision.md` v1) | A generated, evidence-linked model of the repo (code, tests, pipelines, docs) read through two projections: a token-minimal AI layer (L0-L5) and a Cytoscape.js human visual explorer. **Track A** (visual control from already-structured sources) shipped, execution history archived to `completed/issues/improvement-138-architecture-control-plane-track-a.md`. This issue's live scope is now **Track B** (ArchUnit-based contract/test-coverage model + the AI-token-savings hypothesis, gated by its own B2 "stop" measurement) plus **`improvement-135`'s absorbed items 3/5** (moved in 2026-08-28, since it's the same "does a nav layer save tokens" question, just for the existing hand-authored `.claude/nav/` layer instead of Track B's new generated one) — `improvement-135` had nothing else still-open and is now closed. See the issue file's "Absorbed from `improvement-135`" section for the live governing rule and pending measurement. |

### Top priority — improvement-073

| Issue | Origin | What |
|---|---|---|
| [improvement-073](issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md) → [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | New / Migrated; scope widened 2026-09-01 per explicit user request | REST API infrastructure, two audiences in one issue: (1) dev-gated Playwright seeding endpoints (035 unblocks on this half, then service-layer-seeds spec 05 — full e2e 11 min → ~7-8 min), and (2) a real external/public API for external consumers, prod-reachable. `private/features/F-07-phone-verification.md`'s planned Telegram Bot webhook is a known future consumer of this same infrastructure |

### Nice to have — no internal priority order

| Issue | Origin | What |
|---|---|---|
| [improvement-096](issues/improvement-096-responsive-mobile-adaptation-pass.md) | New (UX review) | Responsive/mobile adaptation — 2 `@media` queries across 26 theme CSS files; its own 4-phase program (mobile Playwright viewport first), schedule before public launch |
| [improvement-129](issues/improvement-129-marketplace-feed-modernization.md) | New (user-supplied AI spec, verified against code) | Modernize `AdvertisementsView`/`AdvertisementCardView` into a LinkedIn/Facebook-Marketplace-style content feed — options-oriented proposal, not a locked plan; confirmed card is already NOT `vaadin-grid` (visual refresh, not structural rebuild) and all 5 named CSS tokens already exist; `ActorProfile`/avatar/"contact" from the original spec confirmed not to exist yet (descope or sequence after improvement-124); several open design questions listed in the issue need a decision before implementation |
| [improvement-036](issues/improvement-036-actuator-structured-logging.md) | Migrated | Actuator + structured JSON logging |
| [improvement-039](issues/improvement-039-dark-mode-lumo-tokens.md) | Migrated | Dark mode — step 2 (palette values + toggle); step 1 shipped via improvement-037 |
| [improvement-065](issues/improvement-065-settingspaginationservice-detach-not-guaranteed-on-session-expiry.md) | Still open | `SettingsPaginationService`'s `DetachListener` cleanup isn't guaranteed on abrupt session expiry |
| [improvement-114](issues/improvement-114-sonar-jacoco-coverage-not-wired.md) | New (found running Sonar) | SonarQube's `new_coverage` quality gate condition always reads 0% — JaCoCo never wired into the scan |
| [improvement-063](issues/improvement-063-playwright-stability-guard-async-init-components.md) | Still open | "Ready" signal for async-initialized custom components (`QuillEditor`, `AttachmentGallery`) |
| [improvement-028](issues/improvement-028-minimal-ci-pipeline.md) | Migrated | Minimal CI pipeline (GitHub Actions) — own open questions (push auth, `gh` CLI, clean runner) still unresolved |
| [improvement-116](issues/improvement-116-vaadin-theme-annotation-migration.md) | New (carved out of improvement-115) | Migrate off deprecated `@Theme` annotation to Vaadin 25's automatic theme discovery — needs full Playwright `--ux` visual pass, deferred out of the mechanical cleanup batch |
| [improvement-130](issues/improvement-130-backlog-issues-folder-rename.md) | New | Rename `backlog/issues/` (and its `completed/` mirror) to a name that doesn't imply bugs-only — purely organizational, 58 files currently cross-reference the old path, schedule when no other issue is mid-filing |
| [improvement-131](issues/improvement-131-priority-emoji-rubric-doc-practice-mismatch.md) | New (found via `/deep-review`) | `.claude/commands/feature.md`'s priority-emoji rubric claims to be "already used throughout this backlog" but only 2 of 29 issues use it (both filed in the same commit that triggered this check) — either drop the rubric or backfill it onto the other 27 |
| [improvement-133](issues/improvement-133-deferred-oversized-review-findings.md) | New (process/meta, filed 2026-07-30) | Running collection bucket for `/code-review`/`/deep-review` findings that are valid but too large for the batch that surfaced them — appended to, never a new issue file per finding; first 2 entries are query-lib compile-time `Fields.*` enforcement and `inSet`/`anyOf` cardinality-semantics asymmetry, both from Batch H's retroactive review |
| [improvement-172](issues/improvement-172-fault-injection-regression-tests-s3-db-sequencing.md) | New (found discussing `improvement-171`'s deleted `data-integrity-reviewer` lens) | Fault-injection regression tests for the S3-vs-DB-transaction sequencing `AdvertisementSaveService`/`UserDeleteService` deliberately handle — no test currently exercises the failure mode `improvement-069` originally fixed |
| [improvement-142](issues/improvement-142-architecture-map-bounded-contexts-follow-ups.md) | New (filed 2026-08-05, continuation of improvement-138 once that file's own scope closed; deprioritized to the bottom of the backlog 2026-08-28 per explicit user request) | `scripts/ai/DECISIONS.md` ADR-019 mechanized Bounded Contexts' domain grouping/relationships live from real code, but a full content-parity check before deleting `docs/architecture/bounded-contexts.md` found four genuinely-unique sections with no live equivalent: `Domain Details`'s per-domain prose (specific cross-domain call narratives, negative facts, architectural nuances), `Shared Kernel`'s full 5-category breakdown with examples, `Domain Independence`, and `Risks & Future Considerations`. `Integration Patterns` confirmed **not** a loss (duplicates SPI Map's Call Flow Examples word-for-word). `bounded-contexts.md` has already been deleted (`git rm`); a draft candidate for the four sections' JSON content is captured directly in the issue file. Fix: wire that content into the generator as hand-preserved static content (same exception class as SPI Map's Call Flow Examples/Implementation Rules), same 01/02/04 discipline. Scoped to Bounded Contexts content parity only — Code-Metrics-related follow-ups now live entirely in `improvement-144` |
| (no issue filed yet — see [improvement-142](issues/improvement-142-architecture-map-bounded-contexts-follow-ups.md)'s captured `07-risk-report.md` content) | Carried forward from the now-deleted `07-risk-report.md`'s "Architectural Debt" list (2026-08-06) | 3 small speculative items, none urgent: extract a centralized `AuthorizationService` if authorization logic grows beyond `AccessEvaluator` (MEDIUM priority/effort); partition `audit_log` for scaling beyond ~1M rows (LOW priority, LARGE effort, not urgent); add unit tests for every `AuditActivityFieldsHook`/`AuditActivityEnrichHook` implementation across all `EntityType` values (MEDIUM priority, SMALL effort — same gap the `SPI Contract Testing` risk in the deleted `07` also named) — file as a real `/feature` issue if/when actually picked up |
| [improvement-177](issues/improvement-177-plan-mode-approval-rule-rollout.md) | New (carved out of `improvement-176` finding 4, filed 2026-08-28) | Replace the project-wide "Approval Rule" in `.claude/rules.md` with real Plan Mode (`EnterPlanMode`/`ExitPlanMode`) — same migration `improvement-176` finding 4 attempted for `autopilot.md` alone, **reverted the same day**: `ExitPlanMode`'s plan file is harness-assigned, conflicting with this project's own plan-lives-in-the-issue-file rule. That's the same conflict this issue's own goal would hit at project scope — real doubt attached, not just deferred, see the issue file's "Reverted precondition" section |

### Blocked — improvement-118

| Issue | Origin | What |
|---|---|---|
| [improvement-118](issues/improvement-118-f01-real-world-og-preview-verification.md) | New (carved out of improvement-117) | Manual real-world Facebook/Telegram preview check — needs a public URL, not automatable; pushed to the bottom 2026-07-25, not actionable in this environment |

---

## Week 0 — quick wins (~1 day total)

**Week 0 is now complete.** History: [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md#week-0--quick-wins).

## Wave 1 — prerequisites for public shareability

**Wave 1 is now fully complete.** History: [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md#wave-1--prerequisites-for-public-shareability).

## Wave 2 — quality hardening before public traffic

**Wave 2 is now fully complete except for independent, unblocked items (see below).** History:
[BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md#wave-2--quality-hardening-before-public-traffic).

Remaining Wave-2-era items now live in the "Execution batches" section above (their `Origin`
column — "Still open" / "Migrated" — preserves the provenance these tables used to carry):
improvement-025 → Batch J; improvement-029/033 → Batch I; improvement-035 → Batch K;
improvement-040 → Batch G; improvement-036/039/028 → Standalone; improvement-038 → Deferred
(trigger-based).

**About the `Migrated` origin:** `backlog/process-improvements.md` was a one-time 2026-07-04
process audit, not a tracked backlog — 16 of its 21 items had never been formalized into an
issue file or a BACKLOG row (only buildx, the owasp-sanitizer bump, virtual threads, and
DelegatingPasswordEncoder had been). All still-relevant items became proper issues on 2026-07-13;
`process-improvements.md` itself has been deleted (fully superseded — its content is preserved
across those issues and this note, not lost) so there is exactly one living backlog.

**Deliberately not migrated as separate issues** (already tracked elsewhere, or explicitly
rejected/deferred with no concrete trigger in the source document — creating an issue for these
would be backlog noise, not hygiene): deep links (→ private `F-01`), thumbnails on upload (→
dependency of private `F-01`), AI-assist (→ private `F-10`), OpenRewrite/PIT/Error Prone/
Checkstyle/JSpecify/CDS-AOT-cache (explicitly deferred or rejected in the source document itself).

## Wave 3 — with the corresponding domain work

(improvement-002, formerly here with trigger "before the first new snapshot-bearing domain",
promoted to Top priority above 2026-07-27 — F-04/improvement-124 is that domain.)

(improvement-019, formerly here with trigger "any audit-starter touch", moved to Batch H above —
that batch is the audit-starter touch it was waiting for.)

Plus: Testcontainers test layer is a hard gate before any payment code.

## Deferred — trigger-based (do not touch until the trigger fires)

| Issue | Trigger |
|---|---|
| [improvement-003](issues/improvement-003-deferred-performance.md) (items A-K) | per-item triggers inside the file |
| [improvement-038](issues/improvement-038-pg-trgm-title-index.md) | `pg_trgm` GIN index on `advertisement.title` — do as data volume grows |
| [improvement-021](issues/improvement-021-attachment-concurrency-and-batching.md) | concurrent gallery editing in practice; item A joins any attachment schema touch |
| [improvement-017](issues/improvement-017-sync-s3-upload-in-request-thread.md) (step 2) | bundled with the thumbnail-pipeline refactor |
| [goal-001](issues/goal-001-activity-field-visibility-by-role.md) | user feedback |
| [improvement-052](issues/improvement-052-first-admin-registration-toctou-race.md) | project nearing production readiness — `UserService.register()` first-admin TOCTOU race, accepted risk for now (narrow window, only the instant of a fresh instance's very first registration); extracted from improvement-050 item 1 |
| [improvement-100](issues/improvement-100-forgot-password-flow-missing.md) | project nearing public launch (same gate as improvement-052) — no password-recovery flow exists; requires an email-infrastructure decision first; natural companion to 052 in a pre-launch hardening pass (improvement-088, formerly grouped here, shipped 2026-07-20) |
| [improvement-109](issues/improvement-109-reference-data-view-no-pagination.md) | category dictionary growing past a couple screens' worth, or a dedicated UI-consistency pass; batch with a reference-data touch |
| [improvement-112](issues/improvement-112-enrichment-failure-blanks-entire-list.md) | batch with any advertisement-service resilience touch; cheap and standalone |
| [improvement-053](issues/improvement-053-advertisement-listing-expiry-archive-strategy.md) | real `advertisement` row count/growth approaching a scale where list-query latency is measurably affected, or a product decision on what "listing expiry" means to sellers/buyers — advertisement archive/expiry storage strategy (status column vs. separate archive table vs. Postgres partitioning), design discussion only, no agreed fix; extracted from improvement-050 item 2 discussion |
| [improvement-055](issues/improvement-055-ui-vaadin-template-consistency-audit.md) | before the next large UI-pattern rollout, or a dedicated UI consistency pass; design discussion only, no agreed fix — most Configurable-shape findings already superseded by improvement-025, remaining findings (CSS naming, TimeZoneUtil/InstantFormatter split, badge/empty-state duplication) need a standardization decision first |
| [improvement-086](issues/improvement-086-postgres-major-version-bump.md) | PostgreSQL 15 → 18 major version bump — do when data volume/feature needs justify it, or PG15's support window starts actually approaching its end, whichever comes first; same trigger shape as improvement-038 |

Former Deferred residents now scheduled: improvement-008/010/014 → Batch F, improvement-095 →
Batch H (see "Execution batches" above). improvement-094 was briefly in Batch D, shipped
2026-07-20. improvement-111 → Top priority (2026-09-01) — its trigger (`improvement-073`'s
external API scope) fired; shipped 2026-09-03, see `completed/BACKLOG-ARCHIVE.md`.

improvement-109/112 moved here from the "Standalone" table above (2026-07-19 index-consistency
fix) — both issue files already said `**When:** Deferred`, but had been mis-ranked as actionable
standalone items when filed.

---

## Maintenance rules

- New issue → add its `**Priority:**` and `**When:**` lines AND a row in the "Execution batches"
  section above (not just left for later triage) — either inside an existing batch it can ship
  with in one pass, or as a new batch / "Standalone" row at a ranked position — all in the same
  change. See `.claude/rules.md` "Issue Lifecycle".
- When a whole batch is completed, remove the batch section; when a batch member is completed
  individually, remove just its row (and the batch's "One pass because" note if only one member
  remains).
- Issue resolved → move the file to `completed/issues/` (per rules.md), remove its row here, AND
  add a one-line `✅ Done (date): ...` entry to [BACKLOG-ARCHIVE.md](completed/BACKLOG-ARCHIVE.md) under the relevant wave
  — all in the same change.
