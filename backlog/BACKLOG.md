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
(`docs/ai/` navigation layer); 137 (`doc-standards` skill + dedup pass, companion
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

**Still active:** improvement-124 (F-04, sole top priority again) — `provider_profile` +
`user_preferences` + unified "My Account" overlay, one combined piece of work (see
`platform-commons/DECISIONS.md` ADR-027); Batch B shipped, B2/C/D remain. improvement-135
validates whether improvement-134's `docs/ai/` layer earns its cost — item 1 (ADR-index
freshness gate) done; items 2-4 consolidated into the `## Operational notes` +
`/sync-docs --full-audit` mechanism described in the issue file. improvement-136
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
improvement-118 stays blocked (needs a public URL this sandbox doesn't have).

| Priority | Tier | Issues (in execution order) | One pass = |
|---|---|---|---|
| Top | 🟡 | 153 | improvement-153 (filed 2026-08-17) — replace `scripts/ci.sh`'s hand-rolled `progress.txt` polling with Dagu, a single-binary no-database DAG engine with a built-in web UI (clickable trigger, live per-stage status/logs, run history). Jenkins/Woodpecker/local `act`/`gitlab-ci-local` all evaluated and rejected as heavier or a translation mismatch with no real payoff. Also carries a prerequisite moved from improvement-152 Part A: verify `bash scripts/ci.sh --unit --foreground` for real. Design only, not started — open questions on persistent-vs-on-demand container, exact DAG shape, and Docker-socket access model |
| Top | 🟡 | 155 | improvement-155 (filed 2026-08-17, split out of improvement-152 Part E) — repo-wide rollout of the `infra-doc-standards` script-header/README-flow convention. Design shipped, applied for real only to `scripts/sonar/` and `scripts/build-and-test/` so far. Not started: `docs/ai/scripts/`, `scripts/ci/`, root `scripts/*.sh`, `playwright/` (own two-level card-drill-down design, agreed but not implemented), the new `scripts/deploy-and-run/` |
| Top | 🟡 | 156 | improvement-156 (filed 2026-08-17, split out of improvement-152 Part B) — ArchUnit Track B unblock decision gate. Technical prerequisite fixed and verified: `ArchitectureMetricsExport`'s module-coupling exporter (`--archunit-metrics`, real non-placeholder output). Actual unblock still gated on `improvement-138`'s two conditions (`improvement-135` item 5) — neither met. The larger method-level `spi_map_json()` replacement (real caller/implementor edges, closing improvement-157's data gap) remains fully undesigned |
| Top | 🟡 | 157 | improvement-157 (filed 2026-08-17, split out of improvement-152 Part C) — SPI Interface Details table redesign: split Callers/Implemented By into two tables, group by Module → Class → Method instead of today's single class-level table. Design only, no code — depends on improvement-156 for real method-level caller/implementor data; open questions on DI-wiring-only-caller display and generic-interface type-argument display |
| Top | 🟡 | 138 | improvement-138 — "Architecture Control Plane". **Track A completed 2026-08-04**: `scripts/ai/generate-architecture-model.sh` generates `architecture-model.json` (10 modules + 12 commands + 2 skills + 1 backlog-summary node) and `architecture-map.html` — rebuilt mid-session from an initial flat-graph draft into a real breadcrumb-navigated drill-down pyramid (System → Module detail → Track-B placeholder slots, plus Tooling & Pipelines / Backlog / Database Schema / SPI & Contracts branches), after the user rejected the flat-graph draft as not matching the plan's own "System → Module → Contract → ..." vision. Enriched with entities/key-services/contracts/tables reused from `03-bounded-contexts.md`/`04-database-erd.md`, and both files' Mermaid diagrams rendered live via Mermaid.js — all read from already-written docs, no new extraction risk. Wired into `/sync-docs` and a new `check-architecture-model-freshness.sh` CI gate. Also root-caused and fixed 3 pre-existing CI infrastructure bugs found while verifying (user explicitly rejected an initial "flag, don't chase" call): root `Dockerfile` missing `provider-profile-spring-boot-starter` in 3 places (silently masked by Docker layer caching in every normal `deploy.sh` run since the module shipped); `scripts/sonar/run.sh` corrupting its own stored token via a CRLF-induced trailing `\r`; `sonar-project.properties`/`run.sh` only scanning 5 of 9 Java modules. `bash scripts/ci.sh --all --sonar --sandbox`: **docs/unit/integration/e2e all PASS** (79/79, 164/164, 50/50 Playwright), `sonar` completes and uploads a real analysis (its quality gate fails on legitimate findings — 0% new coverage per already-tracked `improvement-114`, 11-14 violations, 4.97% duplication — left as a real finding, not force-passed). **Track B remains gated** on resolving the `improvement-135` item 5 conflict (Finding 3) — not started |
| Top | 🟡 | 142 | improvement-142 (filed 2026-08-05, continuation of improvement-138 once that file's own scope closed) — `bounded-contexts.md` is already deleted; carry its four genuinely-unique sections (Domain Details prose, Shared Kernel category breakdown, Domain Independence, Risks & Future Considerations — draft content captured in the issue file) into `generate-architecture-model.sh` as hand-preserved static content (same 01/02/04 discipline). Scoped to Bounded Contexts content parity only — Code-Metrics-related follow-ups now live entirely in improvement-144 |
| Top | 🟡 | 135, 124 | improvement-135 — item 1 done (ADR-index + flows.md freshness gates). Items 2/3/4 (token cost, context-loading.md, flows.md routing) consolidated into one mechanism: a mechanically-parseable `## Operational notes` block on every completed issue + a `/sync-docs --full-audit` aggregation step — real accumulated data supersedes both items' initial one-off spot-checks (2's real token tally, 4's synthetic 6/6 test). **improvement-124 Batch 124-B completed 2026-08-01** — new `provider-profile-spring-boot-starter` module (`EntityType.PROVIDER_PROFILE`, `ProviderKind` MASTER/SHOP/SUPPORT), backend only; see `platform-commons/DECISIONS.md` ADR-027 / `marketplace-app/DECISIONS.md` ADR-072. **improvement-136 (`marketplace-orchestrator` extraction) shipped 2026-08-07**, unblocking the rest of this line — see `completed/BACKLOG-ARCHIVE.md`. Next: **Batch 124-B2** (small cross-domain cleanup surfaced by Batch B's `/code-review` — shared HTML-sanitizer utility, stale-id-during-concurrent-delete fix, touches `advertisement-spring-boot-starter` too), then Batches C/D — the unified "My Account" overlay (name + settings + provider profile in one place, narrower moderator edit permissions, reusing improvement-128's `EntityActivityOverlay`) and the public Providers catalog |
| Nice to have | — | 073 → 035, 096, 129, 036, 039, 065, 114, 063, 028, 130, 131, 133 | everything else — no internal priority order, pick up opportunistically |
| (Deferred) | 🟠 | 111 | authorization at service boundary — trigger: before the first non-UI mutation endpoint (see Deferred table) |
| (Blocked) | 🔵 | 118 | F-01 real-world Open Graph preview verification — manual check in an actual Facebook post/Telegram chat, needs a public URL this sandbox doesn't have; pick up whenever that becomes available |

Details, links, and per-batch rationale below.

### Top priority — improvement-138

| Issue | Origin | What |
|---|---|---|
| [improvement-138](issues/improvement-138-architecture-control-plane.md) | New (user-supplied task, 2026-08-04, "FINAL VISION v2" — supersedes an unseen `architecture-observability-vision.md` v1) | A generated, evidence-linked model of the repo (code, tests, pipelines, docs) read through two projections: a token-minimal AI layer (L0-L5) and a Cytoscape.js human visual explorer. Split into **Track A** (visual control from already-structured sources — `pom.xml`, `DECISIONS.md`/`Status:`, `backlog/` open-vs-completed, `docs/ai/flows.md` — no ArchUnit, low risk) and **Track B** (ArchUnit-based contract/test-coverage model + the actual AI-token-savings hypothesis, gated by its own B2 "stop" measurement). Verified against the real repo before filing, not accepted on the plan's own "verified" framing — 4 fixes applied: (1) `ArchitectureRulesTest`'s existing `@AnalyzeClasses` import uses `ImportOption.DoNotIncludeTests`, so test-scanning needs a second, separate import, not literal reuse; (2) the plan's claim that an automatic pre-commit doc-sync hook "already runs" is false in this repo's current state (`core.hooksPath` is still the default, hook not installed) — directly contradicted `improvement-135`'s own, independently-verified 2026-07-31 finding of the same fact; (3) Track B's new L0-L5 AI-navigation layer conflicts with `improvement-135` item 5's "no new `docs/ai/*` content until proven" governing rule — Track B does not start until that's explicitly resolved (Track A is unaffected, proceeds after 140); (4) B2's token-savings measurement must extend `improvement-135`'s existing `## Operational notes` block, not introduce a differently-named `## AI Context Metrics` block. Full plan, all four corrections applied inline, in the issue file |

### Top priority — improvement-142

| Issue | Origin | What |
|---|---|---|
| [improvement-142](issues/improvement-142-architecture-map-bounded-contexts-follow-ups.md) | New (filed 2026-08-05, continuation of improvement-138 once that file's own scope closed) | `scripts/ai/DECISIONS.md` ADR-019 mechanized Bounded Contexts' domain grouping/relationships live from real code, but a full content-parity check before deleting `docs/architecture/bounded-contexts.md` found four genuinely-unique sections with no live equivalent: `Domain Details`'s per-domain prose (specific cross-domain call narratives, negative facts, architectural nuances), `Shared Kernel`'s full 5-category breakdown with examples, `Domain Independence`, and `Risks & Future Considerations`. `Integration Patterns` confirmed **not** a loss (duplicates SPI Map's Call Flow Examples word-for-word). `bounded-contexts.md` has already been deleted (`git rm`); a draft candidate for the four sections' JSON content is captured directly in the issue file. Fix: wire that content into the generator as hand-preserved static content (same exception class as SPI Map's Call Flow Examples/Implementation Rules), same 01/02/04 discipline. Scoped to Bounded Contexts content parity only — Code-Metrics-related follow-ups now live entirely in `improvement-144` |

### Top priority — improvement-135

| Issue | Origin | What |
|---|---|---|
| [improvement-135](issues/improvement-135-ai-nav-layer-validation-and-adr-index-ci-check.md) | New (process/AI-tooling meta, filed 2026-07-31, found while reviewing improvement-124's diff) | Validates improvement-134's `docs/ai/` layer instead of assuming it works: (1) **done** — `docs/ai/adr-index.md` can no longer silently drift from its source `DECISIONS.md` files, via a standing `.claude/rules.md` rule + a read-only `scripts/ai/check-adr-index-freshness.sh` gate in `scripts/ci.sh` (no git hook — confirmed none exists in this repo), plus the index's same-number-different-file ADR collision risk fixed (`ADR-NNN (module)`); item 1's own audit also found `docs/ai/flows.md` missing 6 built-in skills, fixed by splitting it into a mechanically-checked "Project commands" table (`scripts/ai/check-flows-completeness.sh`, same CI gate) and an explicitly-not-checkable "Built-in skills" table; (2/3/4) **consolidated** — both items' initial one-off spot-checks (2: a real token tally reconstructed after the fact, ~1.7M tokens across Batch 124-A/A2's `/code-review` passes; 4: a synthetic 6/6-correct blind-subagent routing test) were useful but don't keep answering the question as usage evolves, so replaced with one ongoing mechanism covering all three: every completed issue gets a mechanically-parseable `## Operational notes` block (token cost by purpose, `context_loading_*`/`flows_*` match fields), aggregated by a new `/sync-docs --full-audit` Step A5 — real accumulated evidence feeds item 5's governing rule instead of a synthetic snapshot; explicitly did **not** lower any review's default effort level on cost alone, no counterfactual evidence a cheaper pass would've caught the same real bugs; (5) governing rule — no new `docs/ai/*` content until 2-4's accumulated data shows the existing layer earns its cost |

### Top priority — improvement-124

| Issue | Origin | What |
|---|---|---|
| [improvement-124](issues/improvement-124-provider-profile.md) | New (product roadmap Phase 2, item #1; supersedes [improvement-123](issues/improvement-123-f04-master-profile.md)) | F-04 — per the issue's 2026-07-31 "module/table split reconsidered" update, three tables not one: `user_information` (unchanged), `user_preferences` (Batch A, shipped), and a standalone `provider_profile` table (new `provider-profile-spring-boot-starter` module, `EntityType.PROVIDER_PROFILE`, `ProviderKind` `MASTER`/`SHOP`/`SUPPORT` — `NOT NULL`, row created lazily on first "become a provider" save, not eagerly at registration). Batch B (backend only) shipped 2026-08-01 — see `platform-commons/DECISIONS.md` ADR-027. Remaining: Batch B2 (small cross-domain cleanup), then unified "My Account" overlay — Name/Settings/Provider Profile tabs in one place, reused for both self-service and admin/moderator viewing another user, narrower moderator (view-only) permissions — and the public Providers catalog (OG meta + sitemap for `/providers/:id`). `ProviderProfileSnapshotDto` already follows improvement-002's `schemaVersion` record-component pattern (see `platform-commons/DECISIONS.md` ADR-024) |

### Nice to have — no internal priority order

| Issue | Origin | What |
|---|---|---|
| [improvement-073](issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md) → [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | New / Migrated | Playwright seeding infrastructure (sequenced pair — 035 unblocks on 073), then service-layer-seed spec 05 via those endpoints — full e2e 11 min → ~7-8 min |
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
| (no issue filed yet — see [improvement-142](issues/improvement-142-architecture-map-bounded-contexts-follow-ups.md)'s captured `07-risk-report.md` content) | Carried forward from the now-deleted `07-risk-report.md`'s "Architectural Debt" list (2026-08-06) | 3 small speculative items, none urgent: extract a centralized `AuthorizationService` if authorization logic grows beyond `AccessEvaluator` (MEDIUM priority/effort); partition `audit_log` for scaling beyond ~1M rows (LOW priority, LARGE effort, not urgent); add unit tests for every `AuditActivityFieldsHook`/`AuditActivityEnrichHook` implementation across all `EntityType` values (MEDIUM priority, SMALL effort — same gap the `SPI Contract Testing` risk in the deleted `07` also named) — file as a real `/feature` issue if/when actually picked up |

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
| [improvement-111](issues/improvement-111-authorization-enforced-in-ui-only-not-at-service-boundary.md) | before the first non-UI mutation endpoint (F-01/improvement-073 seeding/any API) — authorization is UI-only today; the service/port boundary trusts `actingUserId`. Hard gate, same shape as the completed improvement-020 baseline; not exploitable in the current Vaadin-only architecture |
| [improvement-109](issues/improvement-109-reference-data-view-no-pagination.md) | category dictionary growing past a couple screens' worth, or a dedicated UI-consistency pass; batch with a reference-data touch |
| [improvement-112](issues/improvement-112-enrichment-failure-blanks-entire-list.md) | batch with any advertisement-service resilience touch; cheap and standalone |
| [improvement-053](issues/improvement-053-advertisement-listing-expiry-archive-strategy.md) | real `advertisement` row count/growth approaching a scale where list-query latency is measurably affected, or a product decision on what "listing expiry" means to sellers/buyers — advertisement archive/expiry storage strategy (status column vs. separate archive table vs. Postgres partitioning), design discussion only, no agreed fix; extracted from improvement-050 item 2 discussion |
| [improvement-055](issues/improvement-055-ui-vaadin-template-consistency-audit.md) | before the next large UI-pattern rollout, or a dedicated UI consistency pass; design discussion only, no agreed fix — most Configurable-shape findings already superseded by improvement-025, remaining findings (CSS naming, TimeZoneUtil/InstantFormatter split, badge/empty-state duplication) need a standardization decision first |
| [improvement-086](issues/improvement-086-postgres-major-version-bump.md) | PostgreSQL 15 → 18 major version bump — do when data volume/feature needs justify it, or PG15's support window starts actually approaching its end, whichever comes first; same trigger shape as improvement-038 |

Former Deferred residents now scheduled: improvement-008/010/014 → Batch F, improvement-095 →
Batch H (see "Execution batches" above). improvement-094 was briefly in Batch D, shipped
2026-07-20.

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
