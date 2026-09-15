# improvement-142: Architecture Control Plane — Bounded Contexts content parity + ongoing architecture-map.html follow-ups

**Type:** improvement — tracked continuation of `improvement-138`'s Architecture Control Plane work; keeps `improvement-138` itself from growing further after the Bounded Contexts mechanization pass.
**Module:** `scripts/ai/generate-architecture-model.sh`, `docs/architecture-map.html`
**Priority:** low (deprioritized to the bottom of the backlog 2026-08-28 per explicit user request)
**When:** independent, no blockers — opportunistic pickup, no trigger

## Status

`docs/architecture/bounded-contexts.md` has been **deleted** (`git rm`, uncommitted at time of
deletion — recoverable via `git log --diff-filter=D -- docs/architecture/bounded-contexts.md`
once committed, or from this session's history before that). The live Bounded Contexts diagram
(`architecture-map.html` → Diagrams → Bounded Contexts) already covers domain grouping,
entities/services/tables/ports, and all 19 relationships live from real code (ADR-018/ADR-019).
**Not yet done:** the four sections below were never carried over into the live tool — this is
the remaining, concrete work item for whoever picks this issue up next.

## Problem

`scripts/ai/DECISIONS.md` ADR-019 mechanized Bounded Contexts' domain grouping and relationships
live from real code (entities/services/tables/ports per domain, 19 relationships all `"extracted"`
confidence, each with a real evidence file/method reference). Before deleting
`docs/architecture/bounded-contexts.md` (matching the 01/02/04 precedent — migrate, verify content
parity, delete only on explicit go-ahead), a full section-by-section comparison found real
narrative/analytical content with **no live equivalent**:

1. **`## Domain Details`'s per-domain prose** — specific cross-domain call narratives (e.g.
   Advertisement's "Calls `AttachmentPort.getMediaSummaries()` at read time... `UserPort.findByIds()`
   for author enrichment..."), negative facts ("User domain does not depend on other starters"),
   and architectural nuances (`DefaultTaxonPort` is a coordination layer, not pure delegation; why
   `TaxonAuditHook` does not exist). The live Relationships table shows one evidence line per
   relationship — real, but not this level of narrative detail.
2. **`## Shared Kernel`'s full category breakdown** — 5 categories (SPI Interfaces, DTOs, Core
   Models, API Markers, Utility) with concrete examples (`YoutubeUtil`, `AuditableSnapshot`,
   `ComponentFactory<T>`). The live version only shows 3 raw counts (SPI/DTO/Model) — no category
   names beyond those three, no examples.
3. **`## Domain Independence`** — prose on deployability/testability/independence properties, plus
   the `integration-tests` exception note (the one module allowed a real compile-scope dependency
   on more than one starter at once, and why that's safe).
4. **`## Risks & Future Considerations`** — 3 numbered risk items cross-referencing
   `docs/architecture/06-coupling-analysis.md`.

Confirmed **not** a loss: `## Integration Patterns` (3 flows: Entity Lifecycle with Audit, Media
Attachment, Activity Feed Enrichment) duplicates SPI Map's own Call Flow Examples word-for-word,
just narrated from the domain side instead of the call side — dropping it was correct, not a gap.
Column-level entity detail (e.g. `User`'s `id`/`name`/`email`/... breakdown) is also not a loss —
fully covered, in more detail, by the Database ERD page.

## Suggested fix

Same "hand-preserved static content" exception this tool already uses elsewhere (SPI Map's Call
Flow Examples/Implementation Rules, the Database ERD's `conceptualRelationships` list) — carry the
four genuinely-unique sections above into `generate-architecture-model.sh` as static content
(verbatim or lightly adapted for the tool's own voice), rendered in
`renderBoundedContextsExtrasHtml()` alongside the live Domain Contents/Relationships sections.
A draft candidate for the four sections' content (grounded against current module `CLAUDE.md`
files, not copied blindly from the deleted markdown — e.g. corrects the stale "separate
`audit_snapshot` table" claim, since `audit_log` alone stores snapshot data inline) is captured
below for whoever implements this, so it doesn't need to be re-derived from scratch:

```json
{
  "domainDetails": [
    {"domain": "User", "notes": [
      "No cross-starter dependency -- only platform-commons.",
      "UserSettingsChangedHook is implemented in marketplace-app (SettingsPaginationService) to reset pagination defaults when settings change."
    ]},
    {"domain": "Advertisement", "notes": [
      "AdvertisementService.enrichWithMediaSummary() calls AttachmentPort.getMediaSummaries() at read time -- never denormalized onto the advertisement row.",
      "UserPort.findByIds() enriches the author's name/email; advertisement never stores a denormalized author name.",
      "TaxonPort.findEntityIdsWithAnyTaxon() filters by category without a direct SQL JOIN to taxon_assignment."
    ]},
    {"domain": "Audit", "notes": [
      "audit_log is the single table for both the write log and snapshot storage (its own snapshot_data JSONB column) -- there is no separate snapshot table.",
      "Diffs are computed at read time via AuditableSnapshot.diff() on snapshot pairs -- no separate diff service or field-marker annotation."
    ]},
    {"domain": "Attachment", "notes": [
      "attachment_snapshot records file-state changes for restore; StorageService is the S3-compatible backend (MinIO in dev, AWS in prod)."
    ]},
    {"domain": "Taxon", "notes": [
      "TaxonAuditHook does not exist -- both call sites of TaxonAssignmentService.replaceAssignments() already sit inside an advertisement save/delete that produces its own audit snapshot; the advertisement's own AdvertisementSnapshotDto.categoryIds captures the change instead.",
      "DefaultTaxonPort is a coordination layer (resolves translations, filters active records, builds DTOs) -- not pure delegation, unlike most *PortImpl classes."
    ]},
    {"domain": "Provider", "notes": [
      "This starter's own service writes category assignments directly via TaxonPort.replaceAssignments() -- unlike Advertisement, where marketplace-app's AdvertisementSaveService does that write instead.",
      "findOwnerIds() blocks user purge while a profile exists, mirroring AdvertisementPort's created_by protection."
    ]}
  ],
  "sharedKernel": [
    {"category": "SPI Interfaces", "pkg": "*.spi", "desc": "Ports and Hooks for cross-domain communication"},
    {"category": "Data Transfer Objects", "pkg": "*.dto", "desc": "Cross-domain value objects, no behavior"},
    {"category": "Core Models", "pkg": "core.model", "desc": "Enums and markers used across domains (EntityType, ActionType, EntityRef, ChangeEntry)"},
    {"category": "API Markers", "pkg": "audit.api", "desc": "AuditableSnapshot -- marketplace implements this on its own snapshot DTOs so the audit starter can read them"},
    {"category": "Utility", "pkg": "attachment.util", "desc": "Shared helpers used by 2+ modules, e.g. YoutubeUtil"}
  ],
  "domainIndependence": [
    "Deployed independently -- each domain is a starter JAR on the classpath.",
    "Removed entirely -- other modules never import a starter directly, only via its SPI in platform-commons.",
    "Tested in isolation -- the Port interface can be mocked.",
    "Evolved independently -- internal changes inside one starter don't affect any other module."
  ],
  "domainIndependenceException": "marketplace-app depends on every starter and orchestrates UI/views -- the one deliberate exception.",
  "testOnlyException": "integration-tests is the one module allowed a real compile-scope dependency on more than one starter at once, because it verifies real SQL against a real Postgres via Testcontainers rather than mocking the Port interface -- safe only because the module itself is never shipped, deployed, or depended upon by anything else.",
  "risks": [
    "User & Advertisement coupling: no DB-level FK from advertisement to user_information -- see docs/architecture/06-coupling-analysis.md's \"Actor-Reference Coupling\" section for the bulk AdvertisementPort methods that enforce purge-safety instead.",
    "Audit + Attachment optional: marketplace-app UI guards these injections via ComponentFactory/@ConditionalOnBean -- see 06-coupling-analysis.md's \"Optional Dependency Guards\" section.",
    "Taxon cross-cutting: currently only used by the advertisement domain for category assignment, but taxon_assignment is keyed by (entity_type, entity_id) -- no schema change needed to add new entity types."
  ]
}
```

Wire this as a new `bounded_contexts_narrative_json()` bash function (same `cat <<'EOF'` shape as
`spi_call_flow_examples_json()`), add it to the model JSON build, and render it in
`renderBoundedContextsExtrasHtml()` alongside the live Domain Contents/Relationships sections —
same "hand-preserved static content" exception this tool already uses elsewhere (SPI Map's Call
Flow Examples/Implementation Rules, the Database ERD's `conceptualRelationships` list).

Also use this issue going forward as the running tracker for further
`architecture-map.html`/`generate-architecture-model.sh` follow-ups discovered after
`improvement-138`'s own scope closed (e.g. further UX polish, new live-data sources, additional
mechanization passes), so that issue file doesn't keep growing indefinitely across sessions.

## `06`/`07`/`08`/`05` — content with no mechanical source, stays as markdown after `improvement-138` lands (2026-08-05)

`improvement-138` now has a concrete, verified plan (see that file's "Planned — live coupling
checks..." and "Planned — SonarQube integration..." sections) to mechanize: all of `06`'s grep-check
sections + its Module Size table, and 4 pieces of `07` (Module Size Analysis, Largest Java Files,
Constructor Injection Complexity, Package God-Package Analysis — each verified content-by-content,
not assumed duplicate). The pieces below were checked the same session and found to have **no
mechanical source** — real human judgment or narrative analysis, same exception class as
`05-sequence-diagrams.md`. None of this is a to-do — it's the record of what was ruled out and why,
so it isn't re-investigated later:

- `05-sequence-diagrams.md` — entirely hand-maintained for now. Checked: JetBrains SequenceDiagram
  plugin (IDE-only, no headless/CI use), `plantuml-generator-maven-plugin` (real Maven plugin, but
  static call-hierarchy from one start method, not a curated business-flow trace, and outputs
  PlantUML not Mermaid), ArchUnit (checks dependency rules, does not generate sequence diagrams).
  OpenTelemetry is the only real path to an actual call-order trace, and the scope turned out
  smaller than first assessed: `05`'s 4 documented scenarios (Advertisement Creation, Media Change,
  Timeline Query, Restore from Snapshot) already match real flows `playwright/e2e/*.spec.js`
  exercises — Playwright already solves "who runs the scenario", removing that half of the earlier
  estimate. **Tracetest** (docs.tracetest.io) is a real tool that correlates a Playwright-side trace
  with the backend's OpenTelemetry trace into one trace per test. What would still be net-new work,
  not yet attempted: attaching the OTel Java agent to the app container (`playwright/CLAUDE.md`'s
  `docker run` command — one added env var, e.g. `JAVA_TOOL_OPTIONS=-javaagent:...`), isolating the
  spans belonging to one specific scenario out of a full suite run, and writing a trace → Mermaid
  `sequenceDiagram` converter (no existing tool for this found for Java). Smaller than originally
  estimated, but still a separate track — not revisited further this session, picked up later if
  it's judged worth the remaining piece.
- `06-coupling-analysis.md`'s "Actor-Reference Coupling", "Singleton State Isolation", and "Optional
  Dependency Guards" sections — grounded in real code but written as narrative explanation, not a
  fact table. **Corrected decision (2026-08-06):** `06` is deleted in full once its live
  "Architecture Checks" section ships, not kept around in reduced form — these three sections'
  full content is captured further down in this issue for future analysis, same "capture before
  delete" discipline as `bounded-contexts.md`/`05-sequence-diagrams.md`.
- `07-risk-report.md` — **corrected decision (2026-08-06): deleted in full**, not kept in reduced
  form (same treatment as `05`/`06`). "Database Schema Risks", "Dependency Chain Risks", "Code
  Complexity Hot Spots", "Security Risks", "Performance Risks", "Testing Risks", "Architectural
  Debt", and "Summary" — real architectural risk analysis referencing specific ADRs, not simple
  facts — are captured in full further down in this issue. "Architectural Debt" (a TODO table) also
  gets its 3 items moved into `backlog/BACKLOG.md` proper, per this project's own convention for
  tracked work.
- `08-scorecard.md` — checked whether SonarQube's new "Architecture" beta feature (coupling/cohesion
  visual map) could feed the Coupling/Cohesion sections directly: confirmed it has **no public API**
  (metric key `coupling` does not exist on `/api/measures`), so those two sections, plus SPI Design
  Quality/Database Design/Testability, stay human-judgment. Only the classic Sonar metrics
  (ncloc/complexity/code_smells/duplication) are usable, and only where `06`/`07` already reference
  raw counts, not the 1-10 scored dimensions themselves.

**Corrected decision (2026-08-06): all four — `05`, `06`, `07`, and `08` — are deleted in full**
(content captured in this issue first, same discipline throughout). `05`/`06`/`07` each have real
mechanizable pieces that ship first (see `improvement-143`); `08` has none — its capture below
exists purely so the editorial reasoning isn't lost to git archaeology, not because a live
replacement is coming.

## Full content of `05-sequence-diagrams.md`, captured before deletion (2026-08-05)

`improvement-138` removes this file and its wiring from `generate-architecture-model.sh` this
session (the file can never become live without the OpenTelemetry work described above, and the
tool shouldn't keep listing it as one of its "diagrams" while it stays permanently hand-typed).
Full content preserved here so a future OpenTelemetry-based reimplementation doesn't have to
re-derive it from git history. 8 scenarios, each with a `Classes involved` list and a
`sequenceDiagram` Mermaid block, plus a closing "Key Interaction Patterns" section:

1. **Advertisement Creation Flow** — `AdvertisementOverlay` → `AdvertisementFormOverlayModeHandler`
   → `AdvertisementPort` → `AdvertisementPortImpl` → `AdvertisementService` → `DefaultAuditPort`
   (`captureCreation`) → `AuditDomainHookImpl` (`on(CREATED, ...)`).
2. **Advertisement Update with Media Change** — `AttachmentPort` → `DefaultAttachmentPort` →
   `AttachmentService` (S3 upload, `INSERT INTO attachment`); notes
   `AttachmentMediaChangeHook.onChange()` has no implementation registered, event is dropped
   (ADR-035); media summaries are never written to the `advertisement` row, resolved at read time
   via `AdvertisementService.enrichWithMediaSummary()` → `AttachmentPort.getMediaSummaries()`.
3. **Activity Feed Timeline Query** — `TimelineView` → `DefaultAuditPort.getTimelinePage()` →
   `AuditLogRepository` → loop calling `AdvertisementActivityFieldsHookImpl.fields()` per entity
   type → `ActivityEnrichHookImpl.merge()` (groups related activities, merges media changes with
   advertisement changes).
4. **Restore from Snapshot** — UI → `AdvertisementPort.getSnapshotContent()` →
   `DefaultAuditPort`/`AuditReadService`/`AuditLogRepository` → apply restored fields → `save()` →
   `AdvertisementService` → `captureRestore()` (`ActionType.RESTORED`).
5. **User Settings Change Propagation** — `SettingsFormModeHandler` → `UserPort` → `UserPortImpl` →
   `UserSettingsService` (`UPDATE user_information SET settings=jsonb_set(...)`) →
   `UserSettingsChangedHook.onChanged()` — notes no implementations exist yet, infrastructure ready
   for future listeners.
6. **Filter and Sort Advertisement List** — `AdvertisementsView`/`AdvertisementQueryBlock` →
   `AdvertisementPort.getFiltered()` → `AdvertisementService` → `AdvertisementRepository` →
   `SqlFilterBuilder` (query-lib) builds the `WHERE` clause → `NamedParameterJdbcTemplate.query()`.
7. **Taxon Category Assignment to Advertisement** — `AdvertisementSaveService` (marketplace-app,
   not the advertisement starter itself) → `TaxonPort.replaceAssignments()` →
   `DefaultTaxonPort`/`TaxonAssignmentService` (diff added/removed, `INSERT`/`DELETE FROM
   taxon_assignment`); notes `TaxonAuditHook` does not exist — the advertisement's own
   `AdvertisementSnapshotDto.categoryIds` captures the change instead.
8. **Login and Registration Rate Limiting** — two diagrams: `AuthService.login()` (Caffeine cache
   keyed `remoteAddr + "|" + email`, 5 attempts/15 min, increments only on
   `BadCredentialsException`) and `UserService.register()` (keyed on `clientIp`, increments only on
   `DuplicateKeyException`) — notes an earlier version counted every attempt including successes,
   which locked out the Playwright e2e suite's bulk sign-ups from one IP.

**Key Interaction Patterns** (closing section): Port Pattern (`UI → Port.method() → PortImpl (thin
delegate) → Service`), Hook Pattern (`Service → Hook.method() → HookImpl (thin delegate) → custom
marketplace logic`), No Direct Imports rule (marketplace UI never imports starter internal
classes), Delegation Pattern (Port/Hook impls are pure delegation, e.g.
`AdvertisementPortImpl.save()` just calls `AdvertisementService.save()`), Batch Resolution Pattern
(`TaxonPort.findByIds()`/`getForEntities()` resolve a whole page's taxon data in one batched call,
not one lookup per row — `AdvertisementService.enrichWithCategories()` is the caller).

The full original Mermaid source for all 8 diagrams is recoverable via
`git log --diff-filter=D -- docs/architecture/05-sequence-diagrams.md` once the deleting commit
lands (or from this session's history before that) — not re-embedded verbatim here since the class
lists and narrative summary above already capture what a reimplementation needs to decide the
*shape* of a live version; the exact Mermaid arrow-by-arrow text is a mechanical detail the
original file (via git) still holds.

## Narrative sections of `06-coupling-analysis.md`, captured before deletion (2026-08-06)

`improvement-143` deleted `06-coupling-analysis.md` in full once its live "Architecture Checks"
section shipped (the grep-based violation checks) — corrected decision, the file is no longer kept
around in reduced form. Its three sections with no live equivalent (real code facts, but written
as narrative explanation, not a checkable fact table) are captured here in full, same "capture
before delete" discipline as `bounded-contexts.md` and `05-sequence-diagrams.md` above:

**Actor-Reference Coupling: Advertisement → User.** `advertisement`'s actor-reference columns
(`created_by`/`updated_by`/`deleted_by`) are plain `BIGINT` with no FK to `user_information` —
matching `taxon`/`audit`/`attachment`'s convention. Purge-safety is enforced at the application
level, not via a DB constraint: two bulk `AdvertisementPort` methods, `findOwnerIds(Set<Long>)`
(blocks a retention purge while ads exist) and `clearActorReferences(Set<Long>)` (nulls the
columns), called from `UserService.cleanup()`. `updated_by`/`deleted_by` are not exposed to the UI.

**Singleton State Isolation Across UI Sessions.** `SettingsPaginationService` is a singleton
`@Component` holding a `CopyOnWriteArrayList<BindingEntry>` across every user's UI session. Each
`BindingEntry` carries the owning `userId` (captured from `AuthContextService` at `register()`
time), and `onSettingsChanged(userId, settings)` filters by `entry.userId().equals(userId)` before
pushing a new page size — one user's settings change never touches another session's grid. Cleanup
uses both `bar.addDetachListener(...)` and `@PreDestroy`. See `marketplace-app/DECISIONS.md`
ADR-028. File:
`marketplace-app/src/main/java/org/ost/marketplace/ui/views/services/pagination/SettingsPaginationService.java`.

**Optional Dependency Guards.** *Starter level:* `AdvertisementService` injects
`ComponentFactory<AuditPort>`, `ComponentFactory<AttachmentPort>`, `ComponentFactory<TaxonPort>`
and resolves every call through `ifAvailable()`/`findIfAvailable()`. A grep for
`import org.ost.audit.`/`import org.ost.attachment.` in `advertisement-spring-boot-starter` returns
nothing — only `platform-commons` SPI types are referenced. The starter degrades gracefully when a
sibling starter isn't on the classpath. *Marketplace-app UI:* three UI classes inject their starter
ports via `ComponentFactory`, with `@ConditionalOnBean` on the bean definitions themselves (not
just a call-site `ifAvailable()` guard, since these bean definitions live in marketplace-app and
always exist there):

| Class | Injection | Scope | Failure without the starter |
|-------|-----------|-------|------------------------------|
| `AttachmentGalleryService` | `AttachmentPort` | singleton | context fails **at startup** |
| `AttachmentGallery` | `AttachmentPort` | prototype | exception on first build |
| `AuditActivityPanel` | `AuditPort` | prototype | exception on first build |

This ensures attachment/audit starters are genuinely optional, matching `<optional>true</optional>`
in `advertisement-spring-boot-starter`'s `pom.xml`.

Recoverable in full via `git log --diff-filter=D -- docs/architecture/06-coupling-analysis.md`
once the deleting commit lands — the summary above is enough to decide whether/how a future live
version should cover this, not a substitute for the original text if it's needed verbatim.

## Non-mechanizable content of `07-risk-report.md`, captured before deletion (2026-08-06)

`improvement-143` deleted `07-risk-report.md` in full once its 4 mechanizable pieces (Module Size,
Largest Java Files, Constructor Injection, God Packages — now living on the Module/Module
Dependencies pages) shipped. Everything else, captured here for future analysis, same discipline as
above:

**Database Schema Risks.** *FK Constraints Without Cascading Deletes:* `advertisement.created_by`
FK is `RESTRICT` (MEDIUM risk — cannot delete a user with ads, may need manual cleanup, but
intentional: creator is immutable); `updated_by`/`deleted_by` are `SET NULL` (LOW, nullable);
`attachment.entity_id` has no FK at all (generic entity-type/id pair, LOW, orphans possible but the
cleanup job handles it via soft-delete). *JSONB Columns:* `user_information.settings` (LOW,
app-validated), `audit_log.snapshot_data` (MEDIUM — schema varies by `action_type`, needs runtime
validation), `attachment_snapshot.changes_summary` (LOW, optional/flexible). *Soft-Delete Query
Risk:* HIGH — silent bugs if a query forgets `WHERE deleted_at IS NULL`; mitigation is repository
methods that apply the filter automatically rather than relying on every call site remembering it.

**Dependency Chain Risks.** 4 items, already near-100% pointers to `06-coupling-analysis.md`
(marketplace→all-starters tight binding — see the Module Dependencies page instead; optional
dependency guards, marketplace→user internal import coupling, `UserPortImpl` DTO mapping — all
"see `06`"). Since `06` is also being deleted (per the corrected decision above), these pointers
have no target left either — genuinely no unique content survives here.

**Code Complexity Hot Spots.** *Audit Snapshot Diff Engine* (MEDIUM — `AuditableSnapshot.diff()`,
per-snapshot-type, called from `AuditReadService`; needs reflection over `@AuditedField` markers,
JSONB deserialization, null-safe comparison across versions, same-type prev-snapshot correction via
`withSameTypePrevSnapshot()` to skip cross-type `LAG` values — mitigated by per-snapshot-type unit
tests). *Overlay/Form State Machine* (MEDIUM — `AdvertisementOverlay` manages mode transitions
CREATE→EDIT→VIEW→DELETE, form validation, unsaved-changes detection, post-save session updates —
mitigated by the `OverlaySession` record + mode-handler separation). *Query Filter/Sort Builder*
(LOW — `SqlFilterBuilder` in `query-lib` builds dynamic `WHERE` clauses; injection-safe by
construction via `NamedParameterJdbcTemplate`, not string concatenation).

**Security Risks.** *RBAC* (LOW-MEDIUM — `AccessEvaluator` is the single authorization
choke-point, calls `UserAuthorizationPort.isAdmin()`/`isModerator()`/`isOwner()`; no centralized
gateway, each UI component calls `AccessEvaluator` itself — acceptable for current team size,
monitor as it grows). *Spring Security Integration* (LOW — `UserPrincipal implements UserDetails`,
standard pattern). *Password Storage* (LOW — `PasswordEncoderFactories
.createDelegatingPasswordEncoder()`, `{bcrypt}`-prefixed hashes allow a future algorithm migration
e.g. to Argon2id without a data rewrite). *Rate Limiting* (LOW — Caffeine cache, 5 attempts/15 min,
`login()` keyed `remoteAddr + "|" + email`, `register()` keyed on the real client IP via
`server.forward-headers-strategy: framework`; both increment only on real failure, never success —
whether Render actually forwards `X-Forwarded-For` isn't verifiable outside a real deployment).
*URL-Level Access Control* (LOW-MEDIUM — `anyRequest().permitAll()` is deliberate: Vaadin's root
route bootstrap isn't recognized by `HandlerHelper.isFrameworkInternalRequest()`, so deny-by-default
would block every user's first page load; becomes a real gap only if a future REST controller is
added without its own explicit `requestMatchers(...)` ahead of the catch-all).

**Concurrency Risks.** *Singleton State Isolation* — pointer to `06`'s own section (also gone).
*Optimistic Locking* (`Advertisement`/`User`/`Taxon` all have `version BIGINT` + `@Version`;
`Advertisement`/`Taxon` get native Spring Data JDBC checking via `CrudRepository.save()`, `User`'s
real edit path bypasses `CrudRepository` so `UserRepository.updateProfile()` implements the check
by hand; UI shows a conflict notification rather than silently auto-reloading over in-progress
edits).

**Performance Risks.** *Audit Log Unbounded Growth* (MEDIUM — no archival/partitioning strategy yet,
indexes on `(entity_type, entity_id, created_at DESC)`/`(actor_id, created_at DESC)` are adequate
for now; Liquibase could add date-partitioning later). *Large `attachment_snapshot` Queries* (LOW —
JSONB GIN index, snapshots are sparse). *Soft Delete Index Coverage* (LOW — `deleted_at` is
indexed, confirmed adequate).

**Testing Risks.** *SPI Contract Testing* (HIGH — no compile-time enforcement that every
`AuditActivityFieldsHook` implementation handles every `EntityType`; recommendation: unit tests per
hook covering all entity types). *Database Migration Testing* (MEDIUM — Liquibase scripts aren't
tested against real data constraints before production; mitigation: test locally via Docker compose
first). *UI Component Integration* (MEDIUM — Playwright tests must keep pace with UI changes,
maintained separately under `/app/playwright`).

**Architectural Debt** (moved into `backlog/BACKLOG.md` as real tracked items alongside the
deletion, not just archived here): centralize authorization checks (MEDIUM priority/effort — extract
an `AuthorizationService` if auth logic grows beyond `AccessEvaluator`); partition `audit_log`
(LOW priority, LARGE effort — future scaling concern, not urgent); test SPI contracts
systematically (MEDIUM priority, SMALL effort — unit tests for all hook implementations, same gap
as the Testing Risks item above).

**Summary table** (10-category risk rollup, all LOW/LOW-MEDIUM/MEDIUM except Testing's SPI Contract
Safety and Dependency Chain's now-defunct pointers): kept here only as an index into the sections
above, not independently meaningful once they're gone.

Recoverable in full via `git log --diff-filter=D -- docs/architecture/07-risk-report.md` once the
deleting commit lands.

## Full content of `08-scorecard.md`, captured before deletion (2026-08-06)

Confirmed by user decision: `08` is deleted too, same treatment as `05`/`06`/`07`, even though
**none** of it is mechanizable (no live equivalent exists or is planned — this is pure editorial
scoring, captured here purely so the reasoning isn't lost to git archaeology, not because a live
replacement is coming). 7 dimensions, each scored 1-10 with evidence + "why not higher" + concrete
improvement suggestions:

1. **Modularity (7/10).** Starters are independently buildable/deployable Maven modules with their
   own Liquibase migrations, no direct starter-to-starter imports, all SPI in platform-commons. Not
   higher because marketplace-app depends on every starter and can't scale independently, and DB
   schema coupling (FK constraints) creates deploy-time coupling. Suggested: extract a
   `UserReference` SPI if user ever needs to become optional; document that marketplace-app is the
   real monolith and starters are its internal modules.
2. **Coupling (8/10).** No cyclic dependencies, all inter-module calls through SPI Ports/Hooks, no
   Vaadin in starters, `AccessEvaluator`/`UserPortImpl`/`SettingsPaginationService` all cited as
   clean examples. Not higher because audit/attachment implementations can't be swapped (tightly
   wired via Spring beans). Suggested: make starter implementations injectable/swappable.
3. **Cohesion (8/10).** Each domain owns a clean, listed set of classes (entity + service +
   repository + port impl); `I18nKey.java` (438 lines) called out as large-but-logically-cohesive.
   Not higher because hook implementations in marketplace-app span multiple domains (accepted as
   intentional — marketplace is the integrator). Suggested: monitor `I18nKey` growth, split if
   >500 lines; keep hook implementations where they are.
4. **SPI Design Quality (8/10).** Consistent `*Port`/`*Hook` naming + direction, 2-6 methods per
   interface, DTOs separate from SPI, pure-delegation implementations, `ComponentFactory<T>` for
   optional services. Noted: `AttachmentMediaChangeHook` has no implementation today (its former
   receiver was removed) — called acceptable graceful degradation; `AuditPort` is larger (8
   methods) than most, considered acceptable cohesion, not a split candidate. Suggested: add
   pre/post-condition Javadoc per interface method.
5. **Domain Isolation (8/10).** Each domain listed with what it does/doesn't know about others
   (User doesn't know Advertisement; Advertisement only knows Attachment via optional
   `AttachmentPort`; Audit is cross-cutting via hooks; Taxon is self-contained). Not higher because
   the DB-level FK between advertisement and user limits true independence. Suggested: document
   User as a mandatory core domain (not really optional); consider `UserReference` SPI if
   independence becomes critical.
6. **Database Design (8/10).** Generic `entity_type`/`entity_id` pattern for audit/attachment
   (no schema change needed for new entity types), soft-delete columns + indexes, JSONB for
   flexible schema, FK constraints deliberately RESTRICT/SET NULL not CASCADE. Concerns: JSONB
   `audit_log.snapshot_data` schema varies by `action_type` (runtime, not compile-time, validation);
   soft-delete queries need discipline, not enforced by a constraint. Suggested: document the
   JSONB shape per `action_type`; consider a Postgres view (e.g. `advertisement_active`) to
   auto-apply the soft-delete filter; test migrations in Docker before committing.
7. **Testability (7/10).** SPI interfaces are mockable, services have single responsibility,
   repositories take `NamedParameterJdbcTemplate` (injectable), Vaadin components use the
   `Configurable` pattern, Playwright covers e2e. Concerns: Port implementations are pure
   delegation so their unit tests can feel trivial; Hook implementations need per-entity-type test
   coverage with no compile-time enforcement; optional-starter-excluded integration tests aren't
   guarded. Suggested: unit tests for every Hook implementation across all entity types;
   integration tests with optional starters excluded; document per-interface test expectations.

**Overall Assessment table:** average **7.7/10 (GOOD)** across all 7 dimensions (Modularity 7,
Coupling 8, Cohesion 8, SPI Design 8, Domain Isolation 8, Database Design 8, Testability 7).

**Strengths** (7 listed): clear SPI design, no cyclic dependencies, centralized shared kernel,
flexible JSONB schema, modular starters, UI/data separation, good indexing.

**Open Issues:** none open at time of writing — pointed to `06`/`07` for the underlying checks
(both now also deleted, so this pointer has no target left either).

**Recommendations (priority order):** HIGH — document whether User must always be present; add
Hook-implementation unit tests for all entity types. MEDIUM — extract a centralized
`AuthorizationService` if auth logic grows; add DB migration testing to CI/CD; consider Postgres
views for soft-delete filters; document SPI contract expectations. LOW — monitor `I18nKey` growth;
plan `audit_log` partitioning beyond 1M rows; consider CQRS for the audit read side if query
performance degrades.

**Conclusion:** "well-structured modular monolith with solid architectural foundations" — clear
module boundaries via SPI, no circular deps, good separation of concerns, flexible schema.

None of the Recommendations/Improvements listed across the 7 dimensions have been separately
tracked as backlog items — if any are still considered worth doing, they'd need to be filed as
real issues at that point, this capture alone does not do that.

Recoverable in full via `git log --diff-filter=D -- docs/architecture/08-scorecard.md` once the
deleting commit lands.

## Related

- `improvement-138` — the original Architecture Control Plane plan; this issue is its direct
  continuation once the Bounded Contexts mechanization work outgrew that file.
- `improvement-144` — owns everything Code-Metrics-related (descriptions/colors/source-links/
  opt-in flags on the existing per-module section, plus a dedicated card and on-demand refresh
  trigger) — a follow-up briefly drafted here on 2026-08-06 was moved there in full so this issue
  stays scoped to Bounded Contexts content parity only.
- `scripts/ai/DECISIONS.md` ADR-015 (why Bounded Contexts stayed hand-maintained originally),
  ADR-016 (why the Cytoscape+dagre rendering attempt was dropped), ADR-018 (restored via Mermaid's
  native engine), ADR-019 (domain/relationship data mechanized live) — full decision history behind
  this file's hand-maintained-to-live migration.
