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

improvement-072 (promoted to sole top priority 2026-07-23) completed 2026-07-24 — see
`completed/BACKLOG-ARCHIVE.md`. improvement-117 (F-01, product roadmap Phase 1) completed
2026-07-24 — all technical work done; its one non-automatable manual-verification item carved
out into improvement-118. improvement-120 completed 2026-07-25 — see `completed/BACKLOG-ARCHIVE.md`.
improvement-119 (F-02, product roadmap Phase 1 item #2) completed 2026-07-25 — see
`completed/BACKLOG-ARCHIVE.md`. improvement-121 filed 2026-07-26 (repo-wide SOLID/DRY review
findings from the 11-agent audit run after improvement-119 shipped), then deprioritized the same
day to lowest ⚪ after an autopilot execution attempt across all 8 batches was aborted before
landing anything (cleanly rolled back, no code changes from it are in the tree).
improvement-122 (F-03, product roadmap Phase 1 item #3, "Shareability foundation" gate) completed
2026-07-27 — see `completed/BACKLOG-ARCHIVE.md`. **improvement-002 + improvement-124 filed/decided
2026-07-27** — F-04, product roadmap Phase 2 item #1, broadened during planning from a master-only
profile (improvement-123, superseded) into one combined issue: one `actor_profile` table merging
provider-facing fields (master/shop/support, `kind` column mirroring F-03's `AdKind`) with the
locale/settings preferences decoupled out of `user_information` (an earlier plan with these as two
separate tables was merged into one the same day), plus the unified "My Account" overlay this
triggered. One continuous piece of work, not three separate issues (an earlier split into
124/125/126 was merged back into 124 the same day). improvement-002 (snapshot schema versioning)
was paired to land first, since F-04 is the first new snapshot-bearing domain since improvement-002
was filed — **improvement-002 completed 2026-07-28**, see `completed/BACKLOG-ARCHIVE.md`.
improvement-118 remains at the bottom (blocked, not actionable — needs a public URL this sandbox
doesn't have). **improvement-128 filed and completed 2026-07-28** (same day — pilot on Settings,
then rolled out to Advertisement/Taxon/City/User) — the old 1-content-tab + 1-Activity-tab pattern
(`buildContentWithActivity()`) is fully replaced by one shared `EntityActivityOverlay` (stacked
nested overlay, not a tab) across all five domains; see `marketplace-app/DECISIONS.md` ADR-067 and
`completed/issues/improvement-128-activity-restore-panel-redesign.md`. **improvement-134 filed and
completed 2026-07-31** — additive AI-navigation/context-efficiency layer (`docs/ai/adr-index.md`
generated index, `context-loading.md`, `flows.md`, `README.md`; mandatory hooks wired into
`/decision`/`/feature`/`/sync-docs`/`.claude/rules.md`/root `CLAUDE.md`; 4 confirmed stale
`docs/architecture/*.md` items corrected) — see `completed/issues/improvement-134-ai-navigation-context-efficiency-layer.md`.
improvement-124 is again sole top priority, unblocked. improvement-124 Batches A/A2 (preferences
table split + `UserDto`/`UserPort` code-quality cleanup found during review) completed 2026-07-31 —
see `marketplace-app/DECISIONS.md` ADR-070/071 and `platform-commons/DECISIONS.md` ADR-026; Batches
B/C/D (the new `provider-profile-spring-boot-starter` module and unified account overlay) remain.
**improvement-135 filed 2026-07-31**, ranked ahead of improvement-124's remaining batches per
explicit user request — validates whether improvement-134's `docs/ai/` layer actually delivers
(token cost, routing accuracy) and closes a drift gap already found live in this session
(`docs/ai/adr-index.md` missing the ADR-070/071/026 entries this same session added, since they
landed via direct `DECISIONS.md` edits during an `/autopilot` run rather than through `/decision`,
which is the only place regeneration is currently wired). **Item 1 completed 2026-07-31** — no git
hook (checked directly: none exists in this repo, and `.claude/commands/autopilot.md`'s claim of
one was itself inaccurate, now corrected) — instead a standing `.claude/rules.md` rule (any
`DECISIONS.md` edit regenerates the index, regardless of workflow) plus a read-only
`scripts/ai/check-adr-index-freshness.sh` wired as an unconditional stage in `scripts/ci.sh`, and
`generate-adr-index.sh`'s `ADR` column now module-qualified (`ADR-NNN (module)`) to close the
same-number-different-file collision. Items 2-4 (measure whether the layer actually earns its
token/routing cost) remain, need a small scoping pass first.
**improvement-137 completed 2026-08-04** (`doc-standards` skill + repo-wide dedup pass, plus
companion **improvement-139** fixing `deep-review` full-mode's missing `provider-profile-spring-boot-starter`)
— see `completed/BACKLOG-ARCHIVE.md`. `improvement-138` is next, now unblocked.

| Priority | Tier | Issues (in execution order) | One pass = |
|---|---|---|---|
| **Top** | 🔴 | 138 | improvement-138 (new, filed 2026-08-04, sequenced immediately after improvement-137 per explicit user request) — "Architecture Control Plane": a generated, evidence-linked model of the repo (code/tests/pipelines/docs), read through an AI token-minimal layer (L0-L5) and a human visual explorer (Cytoscape.js), split into **Track A** (visual layer from already-structured sources — `pom.xml`, `DECISIONS.md`, `backlog/`, `flows.md` — low risk, ready once 137 lands) and **Track B** (ArchUnit-based contract/test model + an AI-token-savings hypothesis, with its own B2 stop-gate). Plan verified against the real repo before filing — 4 concrete fixes applied: (1) test-scanning needs a separate ArchUnit import, existing `ArchitectureRulesTest` import excludes tests; (2) corrected a false "pre-commit hook already runs automatically" claim that directly contradicted improvement-135's own recent, opposite finding — hook exists but is not installed in this repo's current state; (3) Track B is gated on resolving a conflict with improvement-135 item 5's "no new AI-nav content until proven" rule before it starts; (4) B2's measurement must extend improvement-135's existing `## Operational notes` block, not introduce a new, differently-named one. Track A is not gated by (3) — proceeds independently once 137 lands |
| Top | 🔴 | 136 | improvement-136 (filed 2026-08-04, rescoped 2026-08-04, highest priority per explicit user request) — **paused, not started**: extract a new `marketplace-orchestrator` Maven module (Application/BFF composition layer between `marketplace-app` and the domain starters) to fix `AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService` living in the wrong module, and to give the app room to swap its frontend/add REST later without re-extracting orchestration from a Vaadin-entangled monolith. Supersedes an earlier, narrower "just move it into marketplace-app" plan (kept in the issue file for reference). User paused this before Phase 0 discovery — more discussion pending before implementation starts. Land before improvement-124 Batch 124-C so the `AccountOverlay` UI isn't built against a port contract that's about to change |
| Top | 🟡 | 135, 124 | improvement-135 — item 1 done (ADR-index + flows.md freshness gates). Items 2/3/4 (token cost, context-loading.md, flows.md routing) consolidated into one mechanism: a mechanically-parseable `## Operational notes` block on every completed issue + a `/sync-docs --full-audit` aggregation step — real accumulated data supersedes both items' initial one-off spot-checks (2's real token tally, 4's synthetic 6/6 test). **improvement-124 Batch 124-B completed 2026-08-01** — new `provider-profile-spring-boot-starter` module (`EntityType.PROVIDER_PROFILE`, `ProviderKind` MASTER/SHOP/SUPPORT), backend only; see `platform-commons/DECISIONS.md` ADR-027 / `marketplace-app/DECISIONS.md` ADR-072. Next: **improvement-136** (see above) should land before **Batch 124-B2**/**124-C** — Batch 124-B2 (small cross-domain cleanup surfaced by Batch B's `/code-review` — shared HTML-sanitizer utility, stale-id-during-concurrent-delete fix, touches `advertisement-spring-boot-starter` too), then Batches C/D — the unified "My Account" overlay (name + settings + provider profile in one place, narrower moderator edit permissions, reusing improvement-128's `EntityActivityOverlay`) and the public Providers catalog |
| Nice to have | — | 073 → 035, 096, 129, 036, 039, 065, 114, 063, 028, 130, 131, 133 | everything else — no internal priority order, pick up opportunistically |
| (Deferred) | 🟠 | 111 | authorization at service boundary — trigger: before the first non-UI mutation endpoint (see Deferred table) |
| (Blocked) | 🔵 | 118 | F-01 real-world Open Graph preview verification — manual check in an actual Facebook post/Telegram chat, needs a public URL this sandbox doesn't have; pick up whenever that becomes available |

Details, links, and per-batch rationale below.

### Top priority — improvement-138

| Issue | Origin | What |
|---|---|---|
| [improvement-138](issues/improvement-138-architecture-control-plane.md) | New (user-supplied task, 2026-08-04, "FINAL VISION v2" — supersedes an unseen `architecture-observability-vision.md` v1) | A generated, evidence-linked model of the repo (code, tests, pipelines, docs) read through two projections: a token-minimal AI layer (L0-L5) and a Cytoscape.js human visual explorer. Split into **Track A** (visual control from already-structured sources — `pom.xml`, `DECISIONS.md`/`Status:`, `backlog/` open-vs-completed, `docs/ai/flows.md` — no ArchUnit, low risk) and **Track B** (ArchUnit-based contract/test-coverage model + the actual AI-token-savings hypothesis, gated by its own B2 "stop" measurement). Verified against the real repo before filing, not accepted on the plan's own "verified" framing — 4 fixes applied: (1) `ArchitectureRulesTest`'s existing `@AnalyzeClasses` import uses `ImportOption.DoNotIncludeTests`, so test-scanning needs a second, separate import, not literal reuse; (2) the plan's claim that an automatic pre-commit doc-sync hook "already runs" is false in this repo's current state (`core.hooksPath` is still the default, hook not installed) — directly contradicted `improvement-135`'s own, independently-verified 2026-07-31 finding of the same fact; (3) Track B's new L0-L5 AI-navigation layer conflicts with `improvement-135` item 5's "no new `docs/ai/*` content until proven" governing rule — Track B does not start until that's explicitly resolved (Track A is unaffected, proceeds after 137); (4) B2's token-savings measurement must extend `improvement-135`'s existing `## Operational notes` block, not introduce a differently-named `## AI Context Metrics` block. Full plan, all four corrections applied inline, in the issue file |

### Top priority — improvement-136

| Issue | Origin | What |
|---|---|---|
| [improvement-136](issues/improvement-136-marketplace-orchestrator-extraction.md) | New (found during improvement-124 Batch 124-B's post-implementation review, 2026-08-04); rescoped same day after user-directed discussion | `AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService` orchestrate calls across 2-3 sibling starters' ports (`TaxonPort`/`UserPort`/`AttachmentPort`) from inside their own domain starter — contradicts each starter's own "what it owns" boundary and the root `CLAUDE.md`'s explicit assignment of cross-starter stitching to marketplace-app. Rescoped from "move it into marketplace-app" to "extract a dedicated `marketplace-orchestrator` module" (Application/BFF composition layer, reusable later by a REST adapter or a different frontend) after weighing both options against this repo's own precedent (`platform-commons/DECISIONS.md` ADR-026 — structural cost paid for cohesion before, no runtime-toggle benefit) and confirming the optional-starter-removal mechanism (`ComponentFactory<XPort>` + `platform-commons`-only Port interfaces) is unaffected by which module calls it. **Paused before Phase 0** — user has more points to discuss before implementation starts. Full target spec, decision history, and superseded plan all in the issue file |

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
