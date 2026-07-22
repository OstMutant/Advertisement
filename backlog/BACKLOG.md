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

| Batch | Tier | Issues (in execution order) | One pass = |
|---|---|---|---|
| **G** | 🔵 | 085 | dependency bumps — one full `/run-all-tests` sweep |
| **H** | 🔵 | 019, 095 | audit read-side rewrite — same read-side code |
| **M** | 🔵 | 102, 103 | attachment API simplification — dead SPI decision + surface compression |
| **N** | 🔵 | 104, 105 | audit-rendering simplification — DTO-layer moves; after Batch F |
| **I** | 🔵 | 029, 033 | process & docs tooling — no production code |
| **J** | 🔵 | 025 | leaf UI components — its own 4-phase program, don't merge with F |
| **K** | ⚪ | 073 → 035 | Playwright seeding infrastructure — sequenced pair, 035 unblocks on 073 |
| — | 🟡/🔵/⚪ | 096, 108, 036, 039, 065, 072, 063, 028 | standalone — no one-pass partner (096 = its own responsive program; 108 = query-lib fix) |
| (Deferred) | 🟠 | 111 | authorization at service boundary — trigger: before the first non-UI mutation endpoint (see Deferred table) |

Details, links, and per-batch rationale below.

### Batch G 🔵 — dependency bumps (one pass, full suite after)

| Issue | Origin | What |
|---|---|---|
| [improvement-085](issues/improvement-085-playwright-version-bump.md) | New | Playwright 1.52.0 → 1.61.1 (test tooling, 9 minor versions behind) |

improvement-040 (Spring Boot 4.1.0 + Vaadin 25.2.3 + jsoup/aws-sdk/jetbrains-annotations bump)
shipped 2026-07-21 — see BACKLOG-ARCHIVE.md. Remaining item is version-bump-plus-release-notes
work verified by the same full `/run-all-tests` sweep. (improvement-086, the Postgres major bump,
stays trigger-gated in Deferred — riskier infra change, different cadence.)

### Batch H 🔵 — audit read-side rewrite

| Issue | Origin | What |
|---|---|---|
| [improvement-019](issues/improvement-019-findtimeline-correlated-subqueries.md) | Wave 3 | `findTimeline()` — rewrite the correlated `version`/`prev_*` subqueries as window functions (same shape `findRows()` already uses) |
| [improvement-095](issues/improvement-095-getentityactivity-hardcoded-limit.md) | New | `AuditReadService.getEntityActivity()` — name the hardcoded 100-row activity limit (silent truncation policy is currently invisible) |

One pass because: same read-side code (`AuditLogRepository` + `AuditReadService`). Batch A (087,
091 — the `id`-tiebreaker fixes) shipped 2026-07-20; keep its `(created_at, id)` tiebreaker shape
in this rewrite's window-function `ORDER BY`.

### Batch M 🔵 — attachment API simplification (attachment-spring-boot-starter)

| Issue | Origin | What |
|---|---|---|
| [improvement-102](issues/improvement-102-attachmentmediachangehook-zero-consumers.md) | New (simplification review) | `AttachmentMediaChangeHook` has zero implementations — remove it (recommended) or re-justify in ADR-035 with a named future consumer |
| [improvement-103](issues/improvement-103-attachmentservice-api-surface-reduction.md) | New (simplification review) | `AttachmentService` — compress the 13+-method public API: DTO-return unification, explicit snapshot-capture parameter instead of `*Quiet`/`*SkipSnapshot` twins, dedup video branching, rethink the `restoreToUrls` overload trio |

One pass because: same file/starter, and 102's removal (if chosen) directly shrinks the surface
103 restructures — sequencing them apart guarantees rebase churn. Batch B (090/093, same service)
shipped 2026-07-20, so this batch's `captureMediaChanges()`/`deleteAttachments()` starting point
already reflects that fix.

### Batch N 🔵 — audit-rendering simplification (after Batch F)

| Issue | Origin | What |
|---|---|---|
| [improvement-104](issues/improvement-104-expandactivityfields-feature-envy.md) | New (simplification review) | Move `UserService.expandActivityFields()` (generic snapshot expansion) out of the user domain onto the audit DTO layer |
| [improvement-105](issues/improvement-105-advertisementenrichservice-unify-dual-paths.md) | New (simplification review) | Unify `AdvertisementEnrichService`'s mirrored Timeline/Activity enrichment branches, preserving the two deliberate behavioral differences |

One pass because: both reshape the same audit-rendering DTO boundary. Explicitly sequenced after
Batch F — 084/101 rewrite parts of the very code this batch unifies.

### Batch I 🔵 — process & docs tooling

| Issue | Origin | What |
|---|---|---|
| [improvement-029](issues/improvement-029-docs-drift-guard-and-hooks.md) | Migrated | Docs-drift guard + incremental-compile Claude Code hooks |
| [improvement-033](issues/improvement-033-quality-gate-skill-and-definition-of-done.md) | Migrated | `/quality-gate` skill + Definition of Done in rules.md — likely reduces to recording the Definition of Done since `/ci` already does the chaining |

One pass because: both are Claude-Code-process changes (hooks + skill/DoD recording), no
production code touched.

### Batch J 🔵 — leaf UI components (its own multi-phase program)

| Issue | Origin | What |
|---|---|---|
| [improvement-025](issues/improvement-025-leaf-ui-components-plain-classes.md) | Still open | Convert ~17 stateless leaf UI widgets from `@SpringComponent` beans to plain Java classes |

Already internally batched by its own plan: 4 phased conversion batches with a full e2e run after
each — do not merge with Batch F.

### Batch K ⚪ — Playwright seeding infrastructure (sequenced pair)

| Issue | Origin | What |
|---|---|---|
| [improvement-073](issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md) | New | Test-only, dev-gated REST endpoints for Playwright seeding |
| [improvement-035](issues/improvement-035-sql-seeding-for-playwright-spec-05.md) | Migrated | Service-layer-seed spec 05 via those endpoints — full e2e 11 min → ~7-8 min |

One pass because: 035 is blocked solely on 073 — doing them back-to-back removes the blocker
churn (endpoints first, spec-05 seeding on top).

### Standalone — no one-pass partner

| Issue | Origin | What | Tier |
|---|---|---|---|
| [improvement-096](issues/improvement-096-responsive-mobile-adaptation-pass.md) | New (UX review) | Responsive/mobile adaptation — 2 `@media` queries across 26 theme CSS files; its own 4-phase program (mobile Playwright viewport first), schedule before public launch | 🟡 |
| [improvement-036](issues/improvement-036-actuator-structured-logging.md) | Migrated | Actuator + structured JSON logging | 🔵 |
| [improvement-039](issues/improvement-039-dark-mode-lumo-tokens.md) | Migrated | Dark mode — step 2 (palette values + toggle); step 1 shipped via improvement-037 | 🔵 |
| [improvement-065](issues/improvement-065-settingspaginationservice-detach-not-guaranteed-on-session-expiry.md) | Still open | `SettingsPaginationService`'s `DetachListener` cleanup isn't guaranteed on abrupt session expiry | 🔵 |
| [improvement-072](issues/improvement-072-uicomponentfactory-generics-design-debt.md) | Still open | Generics/type-safety design debt (`UiComponentFactory`, raw hook dispatch, `castIfKnown`) — needs a design decision | 🔵 |
| [improvement-108](issues/improvement-108-ilike-wildcard-not-escaped.md) | New (edge-case review) | `query-lib` `like()` doesn't escape `%`/`_`/`\` — every text filter mis-matches search terms containing them; self-contained query-lib fix | 🟡 |
| [improvement-063](issues/improvement-063-playwright-stability-guard-async-init-components.md) | Still open | "Ready" signal for async-initialized custom components (`QuillEditor`, `AttachmentGallery`) | ⚪ |
| [improvement-028](issues/improvement-028-minimal-ci-pipeline.md) | Migrated | Minimal CI pipeline (GitHub Actions) — own open questions (push auth, `gh` CLI, clean runner) still unresolved | ⚪ |

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

| Issue | What | Pairs with |
|---|---|---|
| [improvement-002](issues/improvement-002-snapshot-schema-versioning.md) | Snapshot schema versioning | before the first new snapshot-bearing domain |

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
| [improvement-046](issues/improvement-046-list-stability-under-concurrent-edits.md) | product decision on which option (A-E) to pursue — offset pagination over the activity-sorted advertisement list has no stable-view guarantee under concurrent edits; captures a design discussion, not an agreed fix |
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
