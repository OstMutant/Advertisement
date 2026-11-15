# Plan for people: pluggable-modules-opaque-refs

Research report for [improvement-249](../../tasks/improvement-249-pluggable-modules-opaque-cross-module-references-research.md),
parts A to I of its point 10. Nothing in the project was changed. Raw SQL plans and the scripts that
produced them are in [`measurements/`](measurements/); sources are in [`sources.md`](sources.md).

## Goal

Find the one practical rule for references between modules that gives decoupling, removable
starters, filtering/sorting/paging inside PostgreSQL, little code and clear data ownership, and test
the hypothesis "pluggable modules with opaque cross-module references" on the Taxon filter flow.

## The problem this solves

A list filtered by category today asks Taxon for every matching advertisement id, carries that set
through Java and binds it back into the advertisement query (`a.id = ANY(:allowedIds)`). The
question was whether moving the relation into the owning module (variant B) is better, and whether
every starter can really be removed.

## A. Executive summary

1. **Opaque ids are already the project's rule, and it holds.** Every cross-module reference is a
   plain `Long` or the existing `EntityRef(EntityType, Long)`. No changelog has a foreign key, view or
   function to another starter's table (checked in all 13 changelog files). This matches the outside
   best practice (reference by identity, module-owned migrations, no cross-module joins). No new id
   type is needed.
2. **For Taxon, the current model (A) should stay for now, with two small fixes.** Measured on 1M
   advertisements, variant B (an `advertisement_taxon` table owned by Advertisement) is faster when
   one category matches tens of thousands of rows (31k ids: 211 ms against 556 ms per list page), but
   slower for a rare category (329 ms against 168 ms), and the `COUNT(*)` of a large category costs
   about the same in both. Most of A's extra cost comes from two fixable things inside the current
   model: the Taxon lookup runs **twice per page** (once for the page, once for the count) and its
   index does not cover the query. B needs 2 new tables, a data migration, loses today's real FK
   `taxon_assignment.taxon_id -> taxon(id)`, moves the usage counts of the Taxon admin screen into
   every owner module, and supersedes four recorded decisions.
3. **The hypothesis fits only part of the system.** It is right for references (opaque ids) and for
   database objects (no cross-starter FK/view/function). "The module owns its relation table" is
   worth it only where the owner must filter, sort and page its own rows by that relation at high
   volume — today that is only Advertisement and Provider Profile by Taxon, and only above a measured
   size. For the generic add-on modules (Attachment, Audit, Contact, Feedback, Promotion) the current
   model, where the add-on owns its `entity_type + entity_id` table, is the right one.
4. **"Any starter can be removed" does not hold today, but not because of the database.** By reading
   the wiring: the `ComponentFactory<XPort>` bean that consumers inject is declared only by the starter
   that provides `XPort` (Taxon is the one exception). Removing Audit, Feedback, Contact, Promotion,
   API-key, Attachment, Advertisement or Provider Profile would then fail Spring startup. This was not
   run; a test that proves or disproves it is batch 1. User is a required module by design (its ports
   are injected directly, not optionally).

## B. Current architecture map

Owner of the entity, owner of the relation, format, integrity, effect of removal. "No FK" means the
changelog `remarks` say so and no constraint exists.

| Reference | Entity owner | Relation stored in | Format | Large id set through Java? | Integrity that exists | If the referenced module is removed | Candidate |
|---|---|---|---|---|---|---|---|
| Ad/profile -> category, city | Advertisement, Provider Profile | `taxon_assignment` (Taxon) | `entity_type, entity_id, taxon_id` | **Yes**: `AdvertisementService.resolveTaxonFilter()`, `ProviderProfileService.resolveTaxonFilter()` | PK `(entity_type, entity_id, taxon_id)`, FK `taxon_id -> taxon(id)` (inside Taxon) | Filter silently ignored (`findIfAvailable()` empty, covered by `AdvertisementServiceCategoryFilterTest.getFiltered_taxonStarterAbsent_appliesNoRestriction`); rows stay in the database | Keep A + fixes; B on trigger |
| Entity -> attachments | any `EntityType` | `attachment`, `attachment_snapshot` (Attachment) | `EntityRef` | No (page ids only) | index `idx_attachment_entity` | UI degrades via `ObjectProvider` (app ADR-005) | Keep |
| Entity -> audit history | any | `audit_log` (Audit) | `EntityRef` + `actor_id` | No | index `idx_audit_entity` | Startup likely fails: Taxon/User starters inject `ComponentFactory<AuditPort>` declared only by Audit | Keep model; fix wiring |
| Profile -> contact, reveals | Provider Profile | `contact_info`, `contact_view` (Contact) | `EntityRef` | No | index `idx_contact_info_entity` | Orchestrator needs `ComponentFactory<ContactPort>` | Keep; fix wiring |
| Profile -> feedback, aggregate | Provider Profile | `feedback*`, `feedback_aggregate` (Feedback) | `EntityRef` | Bounded: `findEntityIdsWithRatingAtLeast` in `TopRatedPromotionRule.qualifyingIds()` | FKs only inside Feedback | Orchestrator needs `ComponentFactory<FeedbackPort>` | Keep; fix wiring |
| Profile -> promotion | Provider Profile | `promotion_placement` (Promotion) | `EntityRef` | Bounded by placements: `PromotionCatalogService.scopeFor()` -> `ProviderProfileScope.onlyIds/pinnedGroups` | index `idx_promotion_placement_entity_reason` | Orchestrator needs `ComponentFactory<PromotionPlacementPort>` | Keep |
| Any row -> actor | User | `created_by`, `updated_by`, `deleted_by`, `actor_id`, `author_id`, `viewer_id` columns in each starter | plain `Long` | Purge only: `findOwnerIds`/`clearActorReferences` (bounded) | no FK (app ADR-034) | User cannot be removed (direct `UserPort`/`UserAccountPort` injection) | Keep |

Maven: every starter depends only on `platform-commons` (+ libraries); `marketplace-orchestrator/pom.xml`
pulls all starters at `runtime` scope, so removing one never breaks compilation; the ArchUnit rule
`starters_must_not_import_sibling_starters` (`ArchitectureRulesTest`) guards imports. Liquibase:
each starter has its own `SpringLiquibase` bean and changelog; removing a starter leaves its tables
unused, re-adding it continues with the same ids (`BIGSERIAL`, never reused).

## C. Findings, most important first

1. **Bean wiring breaks removability (likely, not run).** Files: `*AutoConfiguration` of every
   starter declare `ComponentFactory<OwnPort>`; consumers that inject a factory for another port:
   `TaxonService`, `UserService`, `UserPreferencesService` (`ComponentFactory<AuditPort>`),
   `FeedbackService` (`ComponentFactory<UserPort>`), and orchestrator services for all eleven ports
   (`OrchestratorAutoConfiguration` declares none). Only `ComponentFactory<TaxonPort>` is also
   declared by its consumers (`AdvertisementAutoConfiguration.taxonPortFactory`,
   `ProviderProfileAutoConfiguration.taxonPortFactory`, both `@ConditionalOnMissingBean`).
   `RepositoryTestSupport` has to declare empty factories itself "to represent those starters being
   absent", which shows the gap. Change: each consumer declares the factories it injects, the same
   way. Effect: optional starters become removable. Risk: low. Complexity: small (4 config classes).
2. **The Taxon filter runs twice per list page.** `AdvertisementService.getFiltered()` and `.count()`
   each call `resolveTaxonFilter()`; `AdvertisementsView.refresh()` and `AdvertisementApiController`
   call both. Same in `ProviderProfileService`. At 31k ids this is about 160 ms paid twice, plus the
   id set moved twice. This is the taxon-specific part of the existing **improvement-003, item K**
   (double query per page view). Change: one resolution per request (needs a contract choice, see
   decisions). Complexity: medium.
3. **The Taxon lookup index does not cover the query.** `TaxonAssignmentRepository.findEntityIdsByTaxonIds()`
   filters on `entity_type` and `taxon_id`; only `idx_taxon_assignment_taxon_id (taxon_id)` exists, so
   PostgreSQL uses a bitmap heap scan. With `(taxon_id, entity_type, entity_id)` it becomes an
   index-only scan: 43 -> 22 ms at 31k ids, about 12 -> 2.8 ms for a rare category. The same index
   also serves `countByTaxonIds()` (admin usage counts), which got slower in the run (247 -> 415 ms on
   2.6M rows). Change: one Taxon-local changeset. Risk: low. Complexity: trivial.
4. **The revisit trigger of taxon ADR-001 is not instrumented.** The decision itself says no `WARN`
   exists for "more than 10,000 ids". Change: one log line in
   `TaxonAssignmentService.findEntityIdsByTaxonIds()`. Complexity: trivial.
5. **Large bound arrays cost planning time.** At 242k ids the page and count queries each spent
   138-161 ms planning (separate from execution). Not fixable inside A; it is the main argument for
   B at very large categories.
6. **Orphan assignment rows are possible.** `ProviderProfileSaveService.delete()` and
   `AdvertisementSaveService.delete()` clear assignments only through
   `TaxonAssignmentWriteService.clear()` -> `ifAvailable`; if Taxon is absent at that moment the rows
   stay. `AdvertisementService.cleanup()` hard-deletes old soft-deleted ads without touching Taxon.
   Effect today: inflated usage counts only (ids are never reused). Already tracked as
   **improvement-133 #25** (no existence-based orphan cleanup). Variant B would fix it for free with
   `ON DELETE CASCADE` to the owner's own table.
7. **Filtering by category with Taxon absent returns an unfiltered list** (by design, tested). For a
   REST caller that passes `categoryIds` this is a silent wrong answer. Related UI part already
   tracked as **improvement-133 #11**. Decision for you, not a defect fix.
8. **Stale sentence in app ADR-034.** It says the advertisement changelog still has FK constraints to
   `user_information`; the changelog has none (remarks: "no FK"). Documentation only.
9. **Duplicated empty-set guard** in both services' `getFiltered`/`count`: already
   **improvement-133 #23**; batch 6 below would remove it as a side effect.

## Measured SQL (PostgreSQL 15, 1,000,000 ads, 5% soft-deleted, 2,581,265 assignment rows)

Sum per list page = page query + count query (+ the Taxon lookup twice for A), planning included.
One machine (WSL2), warm cache, third run of three; treat as +-30%. Java/JDBC transfer of the id set
is **not** included (a psql client fetch of 242k ids took 300-400 ms; JDBC itself not measured).

| Filter (ids matched) | A today | B local table | A+ (index, one lookup) derived |
|---|---|---|---|
| rare category (2,893) | 168 ms | 329 ms | ~85 ms |
| category (6,300) | 163 ms | 45 ms | ~100 ms (no index run) |
| category (31,279) | 556 ms | 211 ms | ~260 ms |
| category (65,553) | 754 ms | 547 ms | ~580 ms (no index run) |
| category (242,101) | 1,240 ms | 512 ms | ~1,060 ms (no index run) |
| category AND city (3,060) | 743 ms | 228 ms | ~265 ms |
| category (31k), page at OFFSET 1000 | 675 ms | 388 ms | ~375 ms |

"A+ derived" = one Taxon lookup (the index-run time where it was measured, else the time without the
index) + the measured page and count; it was not run as one chain.

## D. Target architecture (proposed rules)

1. **Opaque id**: every cross-module reference is a plain `Long`; where one table points at several
   entity types, `EntityRef` (`entity_type`, `entity_id`). No new typed id, no external-id column, no
   registry: ids are `BIGSERIAL` and never reused, so they stay stable across removing and re-adding
   a starter.
2. **Where the relation lives**: in the module that provides the capability when the relation is
   generic over entity types and the owner never pages its own rows by it (Attachment, Audit, Contact,
   Feedback, Promotion — and Taxon today). In the owning module (`<owner>_<capability>` table, FK only
   to the owner's own table, `ON DELETE CASCADE`, opaque `taxon_id`) only when the owner must filter
   and page by it at high volume — trigger below.
3. **Filtering**: a Port may return an id set only when it is bounded (a page of ids, the promoted
   placements) or below the trigger. Never a cross-starter join, view, function or SQL fragment.
4. **Optional integrations**: each consumer declares the `ComponentFactory` beans it injects
   (`@ConditionalOnMissingBean`); `get()` only behind an `isAvailable()` check. User stays a required
   module.
5. **Deletion and stale references**: local constraints give per-module integrity only; cross-module
   cleanup is done by the orchestrator in the delete transaction when the other module is present;
   rows left while it was absent are harmless because ids are not reused, and are cleaned by the
   job improvement-133 #25 will design.
6. **Trigger for variant B (proposal)**: a single filter value routinely matching more than ~30,000
   rows of one owner type, measured by the new `WARN`, or a list page above its latency target.

## E. Taxon migration plan

Now: keep A, apply batches 2-5 (wiring, index, warning, decision notes). Only if the trigger fires,
for Advertisement first: (1) add `advertisement_taxon` in the Advertisement changelog, copy rows
from `taxon_assignment` in the same changeset when that table exists (Liquibase precondition);
(2) Advertisement's save takes `categoryIds`/`cityTaxonId` itself; the orchestrator stops calling
`replaceAssignments` for ads; (3) the filter becomes `EXISTS` on the local table, with taxon ids
(small set) still validated through `TaxonPort`; (4) usage counts: Taxon asks owners through a new
Port method, or the admin screen sums counts in the orchestrator; (5) delete ADVERTISEMENT rows from
`taxon_assignment`. Provider Profile follows only if its own volume triggers it.

## F. Broader plan

Same change needed nowhere else today. Attachment, Audit, Contact, Feedback, Promotion: keep their
`EntityRef` tables. User references: keep plain columns. All modules: batch 2 wiring fix.

## G. Rules and decisions

- Stay as they are: app ADR-034 (no cross-starter joins), app ADR-005 (graceful degradation),
  commons ADR-034 (feedback self-contained, `EntityRef`), taxon ADR-001 (id set), commons ADR-031/032,
  app ADR-079, app ADR-023 (`TransactionTemplate` orchestration).
- Annotate taxon ADR-001 with the measured numbers and the new trigger (batch 5); fix the stale FK
  sentence in app ADR-034.
- New rule to record (batch 5): "a consumer declares the `ComponentFactory` beans it injects".
- Only if B is adopted later: ADR-001 superseded; ADR-031 and ADR-032 revised (the default method
  disappears); ADR-079's ArchUnit rule changes target.
- Decisions read: taxon ADR-001, ADR-005; app ADR-005, ADR-023, ADR-034, ADR-079 (context part);
  commons ADR-031, ADR-032, ADR-034. "Decision 005" exists in seven files; the relevant one is app
  ADR-005 (modular storage, `ObjectProvider` degradation). Orchestrator ADR-005 was seen in the index
  only.

## H. Batches (details in `plan-machine.md`)

| # | Batch | Risk | Depends on |
|---|---|---|---|
| 1 | Pluggability test: context per profile (all, each optional starter absent) on Testcontainers | low | - |
| 2 | Consumer-declared `ComponentFactory` beans | low | 1 (shows which profiles fail) |
| 3 | Taxon index `(taxon_id, entity_type, entity_id)` | low | - |
| 4 | `WARN` at the ADR-001 threshold | none | - |
| 5 | Decision notes (ADR-001 annotation, ADR-034 fix, factory rule) | none | 1-4 |
| 6 | One Taxon resolution per list request (with improvement-003 K) | medium | your choice below |
| 7 | Variant B for Advertisement — only when the trigger fires | high | 4 |

## I. Rejected alternatives

- **Global entity registry**: not needed; `EntityType` + id already identify every row, ids are never reused.
- **Required cross-starter FKs**: would make Taxon (or any add-on) impossible to remove; the existing remarks already say "no FK".
- **Shared views/functions over several starters**: same coupling as a hidden join (app ADR-034).
- **`BIGINT[]` category column on `advertisement`**: loses the FK and PK uniqueness, and GIN filtering does not help the `ORDER BY created_at LIMIT` page; not measured further.
- **Large id sets through Java without a limit**: kept only below the trigger, with a warning.
- **Events, outbox, CQRS, materialised views**: nothing here is asynchronous or cross-database; one transaction already covers the writes (app ADR-023).
- **Taxon supplying a SQL fragment / `EXISTS` on `taxon_assignment` from Advertisement** (the "starter-owned JOIN helper" ADR-001 mentions): it is a cross-starter join, so it is rejected; the run also showed its speed equals B's, so the gain of B comes from one statement, not from who owns the table.

## The means needed

Executors: opus for batch 1 and 6 (and 7 if ever), sonnet for 2 and 5, haiku for 3 and 4. The
project's `build-and-test.sh --unit --integration --sandbox` for verification. No new dependency.

## What the result will look like

Each optional starter can be dropped from `marketplace-orchestrator/pom.xml` and a test proves the
app still starts; the category filter uses one covered index and warns when it grows past the
threshold; the decision records carry the measured numbers and the rule for when to switch to B.

## Cost

| Batch | Executor | Effort | Tokens (estimate) |
|---|---|---|---|
| 1 | opus | 0.5-1 day | 150-250k |
| 2 | sonnet | 1-2 h | 60-100k |
| 3 | haiku | 15 min | 10-20k |
| 4 | haiku | 15 min | 10-15k |
| 5 | sonnet (via `/record-decision`) | 1 h | 40-60k |
| 6 | opus | 0.5 day | 100-150k |
| 7 (only on trigger) | opus | 2-3 days | 400-600k |

## Risks

- The startup failures in finding 1 are deduced from the code, not run; batch 1 confirms them first.
- The measurements are one synthetic data set on one machine; the category skew was invented
  (cube of a random number), real data may differ.
- Batch 3 makes the admin usage counts slower (measured 247 -> 415 ms on 2.6M rows).
- Batch 4 can log on every page view of a very popular category.

## Where the project's practice differs from best practice

None found. The project already uses opaque ids, module-owned changelogs and no cross-module joins
or foreign keys, which is what the sources recommend. The one place the hypothesis differs from the
project ("the owning module keeps the relation table") is not a best practice the sources require;
it is a trade-off, shown above as variant B with a measured trigger.

## Decisions waiting for you

1. **Variant**: keep A with the fixes now and switch to B only on the trigger (proposed), or move
   Advertisement to B now.
2. **Trigger value** for B: ~30,000 rows per filter value (proposed) or another number.
3. **Batch 6 shape**: one Port call returning page and total together, or keep two calls and accept
   the double lookup until improvement-003 K is done.
4. **Category filter with Taxon absent**: keep "ignore the filter" (today, tested), or reject the
   request.
5. **Batch 3 index**: replace the old index (proposed, one index, slower admin counts) or keep both.
6. Which batches become backlog tasks.
