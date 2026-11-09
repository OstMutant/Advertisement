# improvement-241: Database refactoring

**Type:** improvement
**Module:** `<module>/src/main/resources/db/*-changelog/` of every starter
**Priority:** 🟡 high, at the top at the user's request
**When:** independent, no blockers

## To fix

- On the Database ERD `promotion_placement` and `contact_info` show no arrow to `provider_profile`: the `entity_id` remark in both changelogs has no `References` word; rewrite it to `References provider_profile(id), no FK` and regenerate the architecture model.

## ERD links plan

Goal: replace the standalone "Relationships not backed by a real foreign key" list on the Database ERD page with links inside each table's schema. Everything is in `docs/architecture/scripts/generate-architecture-model.sh`.

1. Model: in the node snippet of `db_erd_json` change the regex `/(\w+)\(\w+\)/g` to `/(\w+)\((\w+)\)/g` and add `column: c.name` and `refColumn: tm[2]` to every derived relationship (today only `from`, `to`, `label` are kept and the referenced column is dropped).
2. `renderDbErdTableSchemas`: add a fifth column "Links" to the columns table. For the row of column `c` of table `T`: outgoing = relationships with `to === T` and `column === c.name`, shown as `to <table>.<refColumn>` linking to `db-col-<table>-<refColumn>`; incoming = relationships with `from === T` and `refColumn === c.name`, shown as `from <holder>.<column>` linking to `db-col-<holder>-<column>`. Real table names come from `byUpper`.
3. The hand-preserved relationship without a column (`ATTACHMENT` to `ATTACHMENT_SNAPSHOT`, `has_snapshots`) is shown as one line in the hint under the table heading, linking to `db-table-<table>`.
4. Links are `<a href="#" data-jump="<id>">` with one delegated click handler doing the same `scrollIntoView` and `row-flash` as the existing arrow handler; plain `#` anchors are not used so the page navigation is not affected (how the page handles the hash was not checked).
5. In `renderDbErdExtrasHtml` remove the section "Relationships not backed by a real foreign key" and the `relItems` variable.
6. Not touched: the arrow click handler on the diagram (it already scrolls to the column that holds the reference) and the Legend.
7. Separate step after approval: regenerate `docs/architecture/architecture-map.html` and `docs/architecture/data/architecture-model.json`, then run `/check-architecture-map`.

Decision: the markdown export follows the page, so the two views of the same data do not drift apart. In `exportDbErdMarkdown` remove the section "Relationships not backed by a real foreign key" and add a `Links` column to the table schema as plain text `to <table>.<column>` / `from <table>.<column>` (no clicks in a static document); the Mermaid diagram in the export already draws these relationships dotted.

## Index audit

All 27 indexes were checked against the queries in the repositories. Only the promotion index was also measured on a temporary table; the others are judged by the query code.

Candidates to remove (edit the changelogs in place, the database is not in production):

| Index | Evidence in code |
|---|---|
| `idx_feedback_comment_feedback` (`feedback_id`) | The column already has `unique="true"` in the changelog, so it already has its own unique index; this one duplicates it. |
| `idx_attachment_snapshot_changes` (GIN on `changes_summary`) | `changes_summary` is only read as `changes_summary::text ... WHERE id = :id AND changes_summary IS NOT NULL`; no JSONB operator (`@>`, `?`, `->`) is used anywhere. |
| `idx_taxon_translation_locale_name` (`locale, name`) | Every query goes by `taxon_id` (primary key `(taxon_id, locale)`); no search by `locale` or `name` was found. |
| `idx_promotion_placement_type_reason` | Measured: it only helps when there are several reasons and one is rare; there is a single reason (`TOP_RATED`). |
| `idx_advertisement_ad_kind` | `AdKind` has 3 values (`OFFER`, `REQUEST`, `PRODUCT`), the filter is `IN`; a btree index on such a column is hardly ever used. Not measured. |
| `idx_provider_profile_kind` | `ProviderKind` has 3 values (`MASTER`, `SHOP`, `SUPPORT`). Not measured. |
| `idx_advertisement_title` (`title`) | The title filter is a case-insensitive `LIKE '%...%'`, which a btree index cannot serve; it can only help `ORDER BY title`. Not measured. |

Kept (used by queries): `idx_audit_entity`, `idx_audit_changed_by`, `idx_attachment_entity`, `idx_attachment_deleted_at` (purge by `deleted_at <`), `idx_attachment_snapshot_entity`, `idx_user_deleted_at`, `idx_advertisement_created_at`, `idx_advertisement_created_by`, `idx_advertisement_deleted_at`, `idx_taxon_type_deleted_at`, `idx_taxon_assignment_taxon_id`, `idx_api_key_actor_id`, `idx_contact_view_entity`, `idx_feedback_entity`, and all unique indexes (`uidx_taxon_type_code`, `idx_provider_profile_actor_id`, `idx_contact_info_entity`, `idx_feedback_author_entity`, `idx_feedback_aggregate_entity`, `idx_promotion_placement_entity_reason`).

## ERD check

Checked statically against the model and the generator; clicks in a real browser and links inside `remarks` were not checked.

- Table `audit_log` has no description (`remarks`); no column has an empty description.
- `entity_type` in `contact_info`, `contact_view`, `feedback`, `feedback_aggregate`, `promotion_placement` carries a false "No FK" statement.
- `entity_id` in `contact_info` and `promotion_placement` has no `References` word, so no arrow is drawn.
- The model still lists `taxon_assignment.assigned_by`, which is already removed from the changelog: the model is not regenerated.
- Diagram arrows: 30 of 31 resolve to a column row; the hand-preserved `ATTACHMENT` to `ATTACHMENT_SNAPSHOT` relationship has no column, so its click leads to the table, not to a row.
- All source file links (`t.file`) exist.

## Static relationship in the ERD generator

`docs/architecture/scripts/generate-architecture-model.sh`, function `db_erd_conceptual_relationships_json()` (lines 1179-1183), holds one hand-typed relationship: `ATTACHMENT` to `ATTACHMENT_SNAPSHOT`, label `has_snapshots (entity_type/entity_id, no FK)`. `db_erd_json` (line 1210) merges it into the relationships derived from the `remarks` markers. Everything else on the ERD is derived from the Liquibase changelogs.

It violates (see `.claude/nav/adr-index.md`, module `docs/architecture/scripts`) the decision "Database ERD's no-FK point relationships derived from a `remarks=` marker convention, not a hand-curated list" (its hand-curated list was meant only for generic `entity_type`/`entity_id` pairs whose real target is a runtime value; this entry is not one) and the decision "Database ERD rebuilt live from real Liquibase changelogs", and the root `CLAUDE.md` Database Changes guideline (`remarks` as the single source of truth).

Plan:

1. Remove `db_erd_conceptual_relationships_json()` and its use in `db_erd_json` (`curated_json`, `process.argv[1]`, `...curated`); only the relationships derived from the markers remain.
2. Consequence: the dotted arrow `ATTACHMENT` to `ATTACHMENT_SNAPSHOT` disappears from the diagram; item 3 of the ERD links plan (one line in the table hint for a relationship without a column) is no longer needed.
3. Separate step after approval: regenerate `docs/architecture/architecture-map.html` and `docs/architecture/data/architecture-model.json`, then run `/check-architecture-map`.

Not touched: the `EntityType` to domain map `BC_ENTITY_TYPE_DOMAIN` (Bounded Contexts, not Liquibase).

## Promotion placement dates (plan)

Current state, checked in the code:

- Every other table keeps its dates as records of what happened: `created_at`, `updated_at`, `deleted_at`, `revoked_at`, `last_used_at`, `assigned_at`. Whether a row is alive is read from a fact (the row exists, or `deleted_at IS NULL`); no query compares those dates with the current time, except retention by age in days, the edit window of a review and the month boundary of the contact limit, each with a margin far above a second.
- `promotion_placement` has `valid_from` and `valid_until`. Nothing in the application writes either: `insertMissing` inserts only `entity_type`, `entity_id` and `reason`, so `valid_from` is the database `DEFAULT NOW()` of the insert (the same value `created_at` gets) and `valid_until` is always null. `findActive` keeps a row only when `valid_from <= NOW()` and `valid_until` is null or later than `NOW()`.
- That is the only place where a date just written is compared with the clock with no margin. When the database clock steps back between the insert and the read, the new row is hidden for a moment; this is the cause of the failing `PromotedProviderProfileScenarioTest` scenarios (see `### Tests`).

Design, decided by the user on 2026-11-08: a promotion is one row per entity and reason. `reason` holds a value of the enum `PromotionReason`, which carries a `rank` (lower sorts first). Today the enum has one value, `TOP_RATED`, added and removed by the rating rule `TopRatedPromotionRule`. Paid advertising later is one more enum value with a lower rank and its own row for the same profile. The payment module ([improvement-237](../../tasks/improvement-237-purchase-reason-and-payment-module.md), designed later) is the only writer of that reason: it adds the row after payment and removes it when the period ends or the money is returned, as every reason has one writer already. The table needs no dates for this: the period lives in the payment module's own record, and the expiry cleanup runs there on the scheduler, like the cleanup services of the other starters. "Promoted" is the fact that the row exists, as `deleted_at IS NULL` is for deletion. Nothing for payment is added now.

Plan. The database is not in production, so the existing changeset is edited in place and no new one is added:

1. `promotion-spring-boot-starter` changelog `01-promotion-schema.xml`: remove the columns `valid_from` and `valid_until`; `created_at` stays; check the table and column remarks for any mention of the window.
2. `PromotionPlacementRepository`: `findActive` selects by type and reason with no condition on time; the row mapper loses the two fields. The method is renamed `findPromoted` (see "Naming" below).
3. `PromotionPlacementDto` (platform-commons): remove `validFrom` and `validUntil` and the "until when" in its Javadoc.
4. `PromotionCatalogService.withPromotion`: stop passing `promotionValidUntil`. The parameter `withDetails` then does nothing, so it is removed along its whole chain: `ProviderProfileReadService` (`withPromotionDetails`), `ProviderProfileDisplayEnrichmentService.enrichWithPromotion`, and `ProviderProfileApiController` (its calls and the private `withPromotionDetails(actorId)` with the role lookup used only for it).
5. `ProviderProfileDto` (platform-commons): remove the field `promotionValidUntil`.
6. REST and MCP: the field leaves the responses (it was shown to administrators and moderators and is always empty today), the descriptions in `ProviderProfileApiController` and `marketplace-rest-api/README.md`. This changes the public contract.
7. Tests: `PromotionPlacementRepositoryTest` (rows inserted with dates, the window tests, the `validFrom`/`validUntil` checks); `PromotionCatalogServiceTest` (the `withoutDetails` test, the `PromotionPlacementDto` constructor calls and the changed `withPromotion` signature); `ProviderProfileReadServiceTest` and `ProviderProfileDisplayEnrichmentServiceTest` (the changed signatures); `PromotedProviderProfileScenarioTest` (the waiting helper and its `JdbcClient` field go).
8. Documentation: `.claude/rules/promotion-spring-boot-starter.md`; the promotion decision in `platform-commons/DECISIONS.md` is annotated (the table columns and "details for privileged users" no longer hold), then `.claude/nav/adr-index.md` is regenerated.
9. Existing databases keep the two columns until they are reset (`deploy-and-run.sh --reset`, `ci.sh --reset-e2e-stack`); Testcontainers databases are always fresh.

The repository stays a plain `JdbcClient` class without an entity or a `CrudRepository`: the batch `INSERT ... unnest ... ON CONFLICT` is bespoke SQL, which the repository pattern allows.

Naming, decided by the user on 2026-11-08: after the change there is no window, so `findActive` is renamed `findPromoted` (it returns the promoted entries of an entity type, with the reason) in `PromotionPlacementPort`, `PromotionPlacementPortImpl`, `PromotionPlacementService` and `PromotionPlacementRepository`; `PromotionCatalogService.activePlacements()` becomes `promotedPlacements()`; their callers (`ProviderProfileReadService`, `ProviderProfileDisplayEnrichmentService`) and tests (`PromotionPlacementRepositoryTest`, `PromotionCatalogServiceTest`, `ProviderProfileReadServiceTest`, `ProviderProfileDisplayEnrichmentServiceTest`) follow. The method `findActive` of the API-key starter means "keys not revoked" and is not touched, so the rename is made by hand per file, not by a blind search and replace.

Done on 2026-11-08, all nine steps, staged, not committed. Differences from the plan, found while doing it:

- The condition `valid_from <= NOW() AND (valid_until IS NULL OR valid_until > NOW())` is gone from `PromotionPlacementRepository.findPromoted`; the query is `SELECT entity_type, entity_id, reason ... WHERE entity_type = :entityType AND reason IN (:reasons) ORDER BY reason, entity_id`.
- The read service has two methods: `getFiltered` (with promotion marks, no flag) and `getFilteredWithoutPromotion` (same page without marks, used by `SitemapService`). The name is needed because two overloads `getFiltered` collided after the flag was removed.
- Extra scope found by the compile and by a search for callers: `ProviderProfileCardView` (the administrator tooltip with the end date, the `AccessEvaluator` import), `I18nKey.PROMOTION_BADGE_UNTIL` and the key `promotion.badge.until` in `messages_en.properties` and `messages_uk.properties`, `ProvidersView` (the `access` field and the privileged flag), `ProviderProfileApiController` (no `authorizationService`, `getById` without `actorId`), `ProviderProfileApiControllerTest`.
- The interim fix in `PromotedProviderProfileScenarioTest` (the waiting helper) is removed, as planned in step 7: the cause is gone, not waited out.
- `.claude/rules/marketplace-orchestrator.md` also describes the two read methods; the decision in `platform-commons/DECISIONS.md` carries an "Update" paragraph; `marketplace-rest-api/README.md`, `promotion-spring-boot-starter/README.md` and `platform-commons/README.md` no longer mention the period.

After the changes, each with its own report in `## Command run reports`:

- Tests: `bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox`.
- Review: `/deep-review` (default scope, the uncommitted changes; nothing of this task is committed yet, so it covers all of it), with the report of files checked and not checked.
- Docs: `/sync-docs` (default scope, the uncommitted changes), with the same report.
- Map: regenerate the architecture map and run `/check-architecture-map`.
- The results go into `## Operational notes`.

## Progress (2026-11-07)

Done, staged, not committed:

- Changelog remarks: `entity_id` in `promotion_placement` and `contact_info` now carries `References provider_profile(id), no FK`; the false "No FK" is gone from `entity_type` in `contact_info`, `contact_view`, `feedback`, `feedback_aggregate`, `promotion_placement`.
- Correction to the ERD check: `audit_log` was not missing its description. The changelog has it; `liquibase-schema-to-json.js` cut an attribute at the first `>` and the text contains `->`. The parser now reads quoted attribute values whole (`createTable`, `column`, `setColumnRemarks`).
- Seven indexes removed from the changelogs: `idx_feedback_comment_feedback`, `idx_attachment_snapshot_changes`, `idx_taxon_translation_locale_name`, `idx_promotion_placement_type_reason`, `idx_advertisement_ad_kind`, `idx_advertisement_title`, `idx_provider_profile_kind`. The promotion changeset also got `validCheckSum ANY`, like the other starters.
- Generator: the hand-typed `ATTACHMENT` to `ATTACHMENT_SNAPSHOT` relationship and `db_erd_conceptual_relationships_json()` are removed; every derived relationship now keeps `column` and `refColumn`; each table schema has a `Links` column (`to <table>.<column>` / `from <table>.<column>`, clickable through `data-jump`); one `jumpToDbErdElement` serves the diagram arrows and the links; the standalone "Relationships not backed by a real foreign key" section is gone; the markdown export has the same `Links` column and no standalone list.
- Regenerated `architecture-model.json` and `architecture-map.html` (generator exit 0). Checked in the generated model: 32 relationships, all with `column` and `refColumn`, every link end resolves to a real column; arrows from `promotion_placement` and `contact_info` to `provider_profile` exist; `assigned_by`, the seven indexes and the hand-typed relationship are gone; the inline page script parses.

Checked afterwards:

- Browser check (`screenshot-architecture-map.sh`, extended with a Links check, run on the final page): 19 tables each in a module frame, 39 arrows each with a target row, 64 Links links (two per relationship) each with a target row, a real click on every one of them flashes its target row, no standalone relationships section, no page error.
- Tests: see "Tests" below. Per-file documentation checks: see "/sync-docs" below.

Not done:

- Playwright of the application UI (the change touches the generated architecture page, the changelogs and comments, not the application UI).

## Command run reports

### /deep-review, first run (incomplete: no finder lens and no verifier ran)

Report of the `deep-review-orchestrator` agent, as received:

- Scope: `git diff HEAD` without `backlog/` and `.claude/`; 9 files, 30 insertions, 35 deletions.
- Checked and against what: the four taxon files against a repo-wide search for `assignedBy`, `assigned_by`, `replaceAssignments(` and `.assign(`; the SQL of `TaxonAssignmentRepository` against the removed column; `001-taxon.xml` and the feedback changelog for `validCheckSum ANY`; `TaxonAssignmentRepositoryTest` for the 3-argument `assign`; the feedback remarks and Javadoc as text-only changes.
- Finder angles that ran: none. The three lenses and the verifiers were not dispatched; the agent reviewed the small diff directly with searches and says this departs from the normal procedure.
- Findings: none confirmed, none rejected. One low-severity note: the generated `architecture-map.html` and `architecture-model.json` still mentioned `assigned_by` (regenerated since).
- Fixed: none (the agent never writes code).
- Not checked: build and tests; bodies of unchanged `TaxonAssignmentService` and `DefaultTaxonPort` beyond the diff hunks; `backlog/` and `.claude/` edits; `.claude/rules/taxon-spring-boot-starter.md`.

Claude's check:

- Confirmed by `git diff HEAD --stat`: 9 files, 30 insertions, 35 deletions, the same files.
- Confirmed by search: no `assignedBy` or `assigned_by` left outside `backlog/` and `docs/` (the docs hits were the stale generated files).
- Not confirmed: the argument count of the 8 `assign(` calls in `TaxonAssignmentRepositoryTest` was not counted.
- Quality of the run: "no findings" is not the result of the defined procedure, because no lens and no verifier ran; it is a direct read of a small diff. The agent also cited a backlog task as the authority for editing changelogs in place, which is not a code fact.

### /deep-review, second run (full procedure, after all changes)

Report of the `deep-review-orchestrator` agent, as received (the prompt required the full procedure and forbade the direct-read shortcut):

- Scope: `git diff HEAD`, 16 files in two chunks (9 and 7); left out the generated `architecture-map.html` and `architecture-model.json`, all markdown under `backlog/`, `.claude/commands/optimize.md` and `.claude/nav/flows.md`.
- Checked and against what: the seven changelogs (index removals, `validCheckSum ANY`, remarks) against the marker rule of `module-doc-standards` and the in-place-edit convention; the five taxon files and the integration test against searches for `assignedBy`, `assigned_by`, `replaceAssignments(`; `FeedbackDto` and `FeedbackPort` (Javadoc only); `liquibase-schema-to-json.js` and all of `generate-architecture-model.sh` (relationship derivation, `Links` column, click handler) against the rules and the ERD decision.
- Procedure: the three finder lenses ran (first passes plus re-runs for files they had read only through the diff); the Haiku verifier step ran; the second verifier step was skipped because only one candidate survived.

| Angle | Candidates raised | Survived verification | Tokens |
|---|---|---|---|
| dry-kiss-yagni | 1 | 0 | 396,812 |
| solid | 0 | 0 | 384,139 |
| precedent | 1 | 1 (low) | 412,837 |

- Confirmed, low confidence: `contact_view.entity_id` has no `References ..., no FK` marker (line 49 of the contact changelog).
- Rejected after verification: `jumpToDbErdElement` duplicating the edge-tap handler and `jumpToRelationshipRow` (only the scroll-and-flash sequence overlaps with the edge-tap handler; a minor optional cleanup).
- Fixed: none (the agent never writes code).
- Not checked: whether `ATTACHMENT` to `ATTACHMENT_SNAPSHOT` could still be derived from a marker; the remaining `[^>]*` regexes in the parser for a `>` inside `createIndex`, `addPrimaryKey` and `constraints` attributes (no real failure shown); the generated files were not compared; no tests were run by the agent; three `DECISIONS.md` files were only searched, not read in full.

Claude's check:

- The surviving finding is a false positive. Changeset `03-contact-view-entity-remarks` sets the column's remarks to `... References provider_profile(id) or advertisement(id) depending on entity_type, no FK`, and the regenerated model has the arrows `PROVIDER_PROFILE` to `CONTACT_VIEW` and `ADVERTISEMENT` to `CONTACT_VIEW` on `entity_id`. The reviewer read only the `createTable` line. The remark text does live in two places (the `createTable` line without the marker, changeset 03 with it).
- The full procedure ran this time, but its cost was large (1,193,788 tokens of finder dispatches plus 86,863 of verifier, as reported by the agent) for 16 files and one wrong survivor.
- `ReportFindings` was called with an empty list.

### Tests

`bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox`, 9m2s, exit 0, run after all changes except the two document edits and the second regeneration. Surefire: `attachment-spring-boot-starter` 10, `html-sanitizer-lib` 6, `integration-tests` 957, `marketplace-app` 99, `marketplace-mcp` 241, `marketplace-orchestrator` 257, `marketplace-rest-api` 291, `platform-commons` 5, `query-lib` 43; 1909 tests, 0 failures, 0 errors, 0 skipped. The integration tests ran against a real PostgreSQL (Testcontainers) with the changed changelogs. The first monitor of this run ended early on a stale `tree.txt` of the previous day's run; the result above is from the log's exit line and the fresh tree. A second full run after the Javadoc, README and header edits and the older fixes (including `DefaultTaxonPort.findByIds`): the unit phase passed; the integration phase ran 957 tests with 1 failure, `ProviderProfilePaginationScenarioTest.noSortParam_pagesInUiDefaultOrderWithoutRepeatOrGap` (the list comes back in another order than the test expects). That class failed again when rerun alone on the working tree, and failed on a clean checkout of `HEAD` in a temporary worktree too, each time in a different order, while the first full run passed it. So the failure does not come from these changes. To see why, I temporarily printed `id`, `createdAt` and `updatedAt` of the listed profiles and ran the class four more times between 21:08 and 21:35: all four passed, with the profiles in the expected order and strictly decreasing timestamps (the printing lines are removed again). The three failures all fall between 20:28 and 20:45, and the earlier full run (19:36) passed. The cause is a known one, not new: the host clock of WSL2/Docker can step back between two inserts, so a test that assumes insertion order over `created_at`/`updated_at` fails now and then. It was already fixed for the other tests of this class (the ADR in integration-tests about deriving the expected order from observed timestamps); this test was missed. The test expected the reverse of creation order, while the default order is `promoted`, `updatedAt`, `createdAt` descending. Fix: the test now takes the expected order from the stored `updated_at` values via `TimestampObservations`, the same way `sortByEachField_bothDirections` does. The class passed once after the change (exit 0); one pass does not prove stability, because the failure was rare and a clock step was not reproduced here. A third full run (after the map was regenerated) failed on another test of the same kind, `PromotedProviderProfileScenarioTest.list_promotedFilter_keepsOnlyThePromotedAndTheirCount` (total 0 instead of 1); the class passed when run alone. Proof: a temporary test repeated that scenario 150 times and compared the JVM and database clocks after each round; both clocks went back by 50-100 ms in exactly four rounds, and exactly those four rounds returned one promoted profile fewer, with the new placement already in `promotion_placement` and active a moment later. The placement list reads `valid_from <= NOW()` and `valid_from` is the database `NOW()` of the insert, so after a backward clock step a fresh placement is hidden for a moment. The code is correct; the tests must not depend on that moment. Fix: the helper that creates a profile with reviews in that test now waits (up to 5 s, polling the database) until no placement of the profile starts in the future. The class then passed. The temporary test was deleted. A full run after both test fixes and a second map regeneration: `bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox`, 14m54s, exit 0. Surefire: `attachment-spring-boot-starter` 10, `html-sanitizer-lib` 6, `integration-tests` 957, `marketplace-app` 100, `marketplace-mcp` 241, `marketplace-orchestrator` 257, `marketplace-rest-api` 291, `platform-commons` 5, `query-lib` 43; 1910 tests, 0 failures, 0 errors, 0 skipped (the first full run had 1909, with `marketplace-app` at 99; the difference was not investigated). Both fixed tests passed. One passing full run does not prove the tests are stable, because both failures were rare.

### /sync-docs (default)

- Only step 1 ran. `git diff --name-only origin/main...HEAD` returned 184 files from the commits that are ahead of `origin/main` (74 Java, 14 `pom.xml`, 9 changelog XML, 9 `DECISIONS.md`, 28 `backlog/`, 17 `.claude/`, 9 `docs/`, 10 `scripts/`).
- Of the 15 uncommitted or staged files, only 3 appear in that list (`backlog/BACKLOG.md`, task 237, `FeedbackPort.java`); the rest of this work is invisible to the default mode. This is the defect recorded in improvement-240.
- The default-range run stopped there: the 184 committed files are not this work, so nothing was checked or updated from that list.

Second part, done by hand over the real change set (staged and unstaged files) instead of the default range:

- Checked: every current document (`*.md`, scripts, `*.js`, `*.yml`, outside `backlog/` and the generated files) searched for the removed or changed names: `db_erd_conceptual_relationships_json`, "hand-curated" and "hand-preserved", "Relationships not backed by a real foreign key", the seven index names, `assigned_by` and `assignedBy`.
- Found stale and fixed: the sentence in `module-doc-standards/SKILL.md` about the hand-preserved list; the Status lines of the two ERD decisions in `docs/architecture/scripts/DECISIONS.md` (they still said the hand-curated list stands).
- Regenerated: `.claude/nav/adr-index.md` (two Status lines changed) and, after the tests, `architecture-model.json` and `architecture-map.html`.
Third part, the per-file checks, done by three read-only Haiku agents; I checked their claims against the code before every edit:

- Javadoc of the 7 changed Java files (34 rows as reported: 24 valid, 7 stale, 3 rule violations, 0 missing). Fixed: `FeedbackPort` (save, saveComment and deleteComment named the author although only the use-case services check the author; "stays censored forever" for confirmed violations although an approval restores them; two comments naming a caller instead of the method; the moderation queue description), `FeedbackModerationStatus` (the same "permanent" claim) and the matching sentence in `.claude/rules/feedback-spring-boot-starter.md`, and the class comment of `TaxonAssignmentRepositoryTest` (one line, no "dynamic SQL", no "category only").
- Headers of the three changed scripts. Fixed: the parser header (Input lacked `setColumnRemarks`, Outputs said an object while it prints an array, Returns named an XML parse failure that cannot happen) and its stale "6 files" comment; the generator header (an ArchUnit contradiction, `--with-archunit` never runs the export, `--with-ci-metrics`, the sonar token condition and two inputs undocumented) and the long comment above `db_erd_json` (now one line pointing to the standard); the screenshot script (the Links click now covers every link, the header says each run deletes the old PNGs).
- Module READMEs and the script-group README. Fixed: `feedback` (no advertisement target, the column is `feedback_content.content_text`), `advertisement` (the purge caller is `UserPurgeEligibilityService`, there is no `UserService.cleanup()`), `attachment` (a third service exists), `platform-commons` (`attachment.util`, `ProviderProfileScope`, Caffeine and `spring-boot-autoconfigure` in the dependencies), `docs/architecture/scripts/README.md` (`--no-sonar-rescan`, the unconditional ADR index call).
- Older problems found by the audits, fixed afterwards: the opening paragraph of the feedback, contact and promotion READMEs (now a one-paragraph purpose); class names removed from the dependency lines of the taxon, feedback, advertisement and provider-profile READMEs; the seven ticket numbers in generator comments; the stale comment above `navigateToTable()` and the section comment above `simplifyDbType()`; the input guard `if (taxonIds.isEmpty())` in `DefaultTaxonPort.findByIds` (its only caller, `AdvertisementAuditEnrichService`, already skips an empty set); four false foreign-key statements in `.claude/rules/integration-tests.md`. The other empty checks in `DefaultTaxonPort` test lists the method fetched itself, not its input, and stay.
- Still open: 184 runs of multi-line comments in the generator; condensing them without losing rationale needs a decision where each rationale goes (a case for the deferred findings bucket).

### Promotion refactor: tests (2026-11-08)

`bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox`, 8m13s, exit 0, 1905 tests, 0 failures. The count is lower than the earlier 1910 because the tests of the validity window and of the details flag were removed with the code they tested. This run is after all code changes of the refactor and before the README edit of `/sync-docs` below (a document-only edit).

### Promotion refactor: CI and the clock (2026-11-08)

- `bash scripts/activity-monitor.sh -- bash scripts/ci.sh --foreground --reset-e2e-stack`: the first run failed in the sync stage (`ls-files` 141, `tar` 141, extract 137, the runner died, cause not found); the computer was rebooted; the rerun after removing the stale containers ended with exit 1 after 34m39s, with the integration tests and the Sonar stage failing. The Sonar failure was not read yet; the CI run was started before the promotion refactor, so it does not show the state after it.
- The CI integration log after the reboot shows the host clock stepping back about 1.4 to 1.7 s every 28 s or so (earlier 50 to 100 ms). A later sampling of the database clock for 60 s showed no step, so the behaviour is transient. A fresh promotion placement was hidden by `valid_from <= NOW()` in exactly those moments; the refactor removes that comparison. Other places that compare a date just written with the clock are not known in the code (the retention, the edit window of a review and the contact month limit all have margins far above a second).
- The reruns after the refactor are recorded in "Promotion refactor: CI reruns (2026-11-08)" below.

### /deep-review, third run (after the promotion refactor, default scope)

Report of the `deep-review-orchestrator` agent, as received:

- Scope: `git diff HEAD`, 79 files; 33 code files checked in four chunks, the three lenses (dry-kiss-yagni, solid, precedent) on each chunk: 12 lens runs.
- Checked and against what: chunk A (feedback and promotion contracts, `ProviderProfileDto`, the promotion repository, service, port implementation and changelog), chunk B (taxon assignment, the taxon and provider-profile changelogs, four orchestrator services, `ProviderProfileApiController`), chunk C (three orchestrator tests, the REST controller test, three integration tests, `I18nKey`, `ProviderProfileCardView`, `ProvidersView`, both message files), chunk D (the advertisement, attachment, contact and feedback changelogs); each against the diff slices and the rules.
- Findings: none raised by any lens (survived 0, candidates 0 for all 12 runs); no verifier ran; `ReportFindings` was called with an empty list.
- Rejected candidates listed by the agent: the in-place changeset edits with `validCheckSum ANY`; the `findActive` rename and the removed columns (follow the decision update); the dropped `isEmpty()` guard in `DefaultTaxonPort.findByIds`; the new meaning of `getFiltered`.
- Not checked: all `.md`, `backlog/`, the generated map files, the generator scripts; not every lens read every chunk C file in full; the caller `ProvidersView` was outside the agent's chunk A and B reading.
- Cost: the orchestrator 94,505 tokens, 17 tool uses, 3,145 s; the 12 lens dispatches about 0.9 M tokens in total (figure of the agent).

### /sync-docs (default, uncommitted changes, after the promotion refactor)

- Scope: `git diff --name-only HEAD`, 79 files.
- Checked: a repository-wide search (outside `backlog/` and the generated files) for `valid_from`, `valid_until`, `validUntil`, `promotionValidUntil`, `PROMOTION_BADGE_UNTIL`, `promotion.badge.until`, `withPromotionDetails`, `assigned_by`, `activePlacements`, `findActive`; the changed Java files for a class-level Javadoc and for task or decision numbers in the added lines; the added Javadoc lines of the main sources read in the diff against the method bodies.
- Found stale and fixed: three phrases in `promotion-spring-boot-starter/README.md` ("until when", "active now", "validity window covers now"). The search found nothing else; the original text of the decision in `platform-commons/DECISIONS.md` keeps the old columns by design (append-only, an "Update" follows it); `findActiveByKeyHash` in the API-key starter is a different meaning.
- Regenerated: `.claude/nav/adr-index.md` (282 entries), `architecture-model.json`, `architecture-map.html`, `arch-embed-index.md` (exit 0). `check-architecture-model-freshness.sh`: up to date, exit 0.
- Not checked: no Haiku agents ran this time; the Javadoc of every method of the changed files was not compared with its body (only the added lines in the diff); no `pom.xml` or infrastructure script was in the diff apart from the generator files the review covered.
- Not mine and left alone: `.claude/commands/optimize.md`, `backlog/research/optimize-table-references/`.

### Promotion refactor: CI reruns (2026-11-08)

- First rerun after the refactor: exit 1, 25m29s. Every stage passed except E2E: `04-provider-profile-flow.spec.js:861` expected a `title` with "Top rated" on the promotion badge for an administrator; the tooltip with the end date was removed with `valid_until`. A second failure, `global.setup.js:22` (the "Sign up" button not found in 10 s), was flaky and passed on retry. Fix: the step, the unused `pageAs`/`adminAccount` and the header line of that test were removed (the single scenario then passed against a fresh dev stack, 52 s, after `deploy-and-run.sh --reset`).
- Second rerun: exit 1, 26m20s. E2E, API flows, ArchUnit and docs passed; the integration tests failed with `ProviderProfilePaginationScenarioTest.sortByEachField_bothDirections:180` (`2026-11-08T18:42:32.913204Z` is not after `18:42:32.952127Z`) and Sonar stopped for it (`did not pass its tests (no tests.passed marker)`). Cause: `updated_at` is written by `@LastModifiedDate` from the JVM clock; the wall clock of the WSL2 virtual machine steps back about 0.5 s and 0.7 s every 30 s (measured with `CLOCK_REALTIME` against `CLOCK_MONOTONIC_RAW`; the log of that run has 19 backward steps), and one fell between the create and the update of the test. What sets the clock back was not found (no container has `CAP_SYS_TIME`; the Hyper-V time synchronisation device with `hv_utils` is present but not proven as the cause). The code of this task does not touch `updated_at`.
- Fix: `.isAfter(...)` became `.isNotEqualTo(...)` at `ProviderProfilePaginationScenarioTest:180` and at `TaxonServiceTest:106` (the `isBeforeOrEqualTo(Instant.now())` there was removed too).
- Third rerun: exit 0, 34m16s, every stage green (lint, shellcheck, unit, integration, E2E, API flows, Sonar, ArchUnit, docs).

### /sync-docs, second run (default scope, 84 files)

- Related documents (Step 2b): a search of all current documents for the changed names found nothing stale except the original text of the promotion decision, which keeps its history by design and has the "Update" paragraph; module lists were checked in `README.md` (diagram and table), `CLAUDE.md` and `.claude/rules/README.md`; `check-module-lists.sh`, `check-flows-completeness.sh` and `check-hardcoded-counts.sh` passed.
- Javadoc and comments of 30 changed Java files: three Haiku agents read-only, one per group of modules; every claim they raised was checked in the code before editing. Fixed: `ProviderProfileDisplayEnrichmentServiceTest` (the end date for privileged callers), `FeedbackModerationStatus.isHidden`, `TaxonAssignmentService` ("with different callers"), `ProvidersView.refreshOnTabSelect`, `ProviderProfilePaginationScenarioTest` line 265 (default order starts with promoted), `TaxonServiceTest` (a task number in the class comment).
- Regenerated `architecture-model.json`, `architecture-map.html`, `arch-embed-index.md` (exit 0).
- Found, not fixed: five multi-line comments in two test classes and the multi-line comments of the map generator; they moved to [improvement-240](../../tasks/improvement-240-sync-docs-refactor.md).
- Not checked: `pom.xml` and script headers outside the files already covered; the Haiku agents read only the changed files.

### Moved to other tasks

- The sort line of the list pages shows the wrong order, no test checks it, `WHERE 1=1` in two repositories, the index with `created_at` first on `audit_log`: [improvement-243](../../tasks/improvement-243-sort-status-line-and-where-1-1.md).
- Multi-line comments (two test classes, the map generator) and a full documentation sync: [improvement-240](../../tasks/improvement-240-sync-docs-refactor.md).

## Remaining

- Save the work in the repository history only when the user says so; `.claude/commands/optimize.md` and `backlog/research/optimize-table-references/` are not part of this task and stay out of it.
- After that, close the task: move this file to `backlog/completed/tasks/`, remove its row from `backlog/BACKLOG.md`, add a one-line entry to `backlog/completed/BACKLOG-ARCHIVE.md`, add 1 to `**Applied:**` of ADR-011 (integration-tests) and ADR-035 (platform-commons), regenerate `.claude/nav/adr-index.md`.
- Not done: Playwright of the application UI, except the full E2E suite of the third CI rerun, which passed.

## Operational notes
- token_cost_review: 1440248
- token_cost_research: n/a
- token_cost_verification: 1014888
- review_signal_ratio: 1/2 (the surviving finding was rejected on a manual check)
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: new improvement request, not yet tracked
- flows_chosen: /new-task
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 6
- haiku_decomposition_rework_needed: 0 / 6
- adrs_referenced: ADR-011 (integration-tests), ADR-035 (platform-commons)

### Agent calls
- code review of uncommitted changes | subagent_type=deep-review-orchestrator | tokens=62991 | tool_uses=5 | duration_s=57 | mode=background | batch=solo
- write To fix section into this task | subagent_type=general-purpose (haiku) | tokens=67679 | tool_uses=4 | duration_s=39 | mode=background | batch=solo
- write ERD links plan into this task | subagent_type=general-purpose (haiku) | tokens=68615 | tool_uses=5 | duration_s=68 | mode=background | batch=solo
- write index audit and ERD check into this task | subagent_type=general-purpose (haiku) | tokens=70106 | tool_uses=5 | duration_s=64 | mode=background | batch=solo
- write static relationship section into this task (declined to edit without a direct user approval) | subagent_type=general-purpose (haiku) | tokens=68713 | tool_uses=2 | duration_s=47 | mode=background | batch=solo
- fix remarks and drop seven indexes in seven changelogs | subagent_type=general-purpose (haiku) | tokens=102242 | tool_uses=24 | duration_s=671 | mode=background | batch=solo
- full code review of the final change set | subagent_type=deep-review-orchestrator | tokens=88964 | tool_uses=16 | duration_s=781 | mode=background | batch=solo (its nested finder dispatches, 1193788 tokens, and the verifier, 86863 tokens, are the agent's own figures)
- Javadoc check of the 7 changed Java files (read-only) | subagent_type=general-purpose (haiku) | tokens=158291 | tool_uses=17 | duration_s=201 | mode=background | batch=audit-1
- header check of the 3 changed scripts (read-only) | subagent_type=general-purpose (haiku) | tokens=163544 | tool_uses=15 | duration_s=218 | mode=background | batch=audit-1
- README accuracy check of 8 module READMEs and the script-group README (read-only) | subagent_type=general-purpose (haiku) | tokens=221603 | tool_uses=28 | duration_s=655 | mode=background | batch=audit-1

- code review of the promotion refactor (79 files) | subagent_type=deep-review-orchestrator | tokens=94505 | tool_uses=17 | duration_s=3145 | mode=background | batch=solo (its 12 nested lens dispatches, about 0.9 M tokens, are the agent's own approximate figure and are not in token_cost_review)
- Javadoc and comment check of 13 files (platform-commons, promotion, taxon), read-only | subagent_type=general-purpose (haiku) | tokens=147979 | tool_uses=23 | duration_s=272 | mode=background | batch=sync-docs-2
- Javadoc and comment check of 9 files (orchestrator, rest-api), read-only | subagent_type=general-purpose (haiku) | tokens=130259 | tool_uses=13 | duration_s=152 | mode=background | batch=sync-docs-2
- Javadoc and comment check of 7 files (marketplace-app, integration-tests), read-only | subagent_type=general-purpose (haiku) | tokens=106349 | tool_uses=12 | duration_s=114 | mode=background | batch=sync-docs-2

### Script/command runs
- bash scripts/activity-monitor.sh -- bash scripts/ci.sh --foreground --reset-e2e-stack (first rerun after the refactor) | duration_s=1529 | mode=background | result=fail (E2E: the promotion badge tooltip)
- bash scripts/activity-monitor.sh -- bash scripts/deploy-and-run.sh --reset | duration_s=n/a | mode=background | result=pass
- bash scripts/activity-monitor.sh -- bash scripts/playwright.sh 04-provider-profile-flow --grep "promoted profiles inside the Providers list" --ux | duration_s=52 | mode=background | result=pass
- the same ci.sh (second rerun) | duration_s=1580 | mode=background | result=fail (integration: line 180; Sonar stopped for it)
- the same ci.sh (third rerun) | duration_s=2056 | mode=background | result=pass
- bash docs/architecture/scripts/screenshot-architecture-map.sh | duration_s=n/a | mode=background | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh (three more regenerations) and check-architecture-model-freshness.sh (two more runs) | duration_s=n/a | mode=background | result=pass
- bash scripts/activity-monitor.sh -- bash scripts/ci.sh --foreground --reset-e2e-stack (first run) | duration_s=n/a | mode=background | result=fail (sync stage, runner died)
- the same, rerun after removing stale containers | duration_s=2079 | mode=background | result=fail (integration tests and Sonar stage)
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox (after the promotion refactor) | duration_s=493 | mode=background | result=pass (1905 tests)
- bash .claude/nav/scripts/generate-adr-index.sh (promotion refactor) | duration_s=n/a | mode=foreground | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh (promotion refactor) | duration_s=n/a | mode=background | result=pass
- bash docs/architecture/scripts/check-architecture-model-freshness.sh (promotion refactor) | duration_s=n/a | mode=background | result=pass (up to date)
- bash docs/architecture/scripts/generate-architecture-model.sh | duration_s=n/a | mode=background | result=pass
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox | duration_s=542 | mode=background | result=pass
- bash .claude/nav/scripts/generate-adr-index.sh | duration_s=n/a | mode=foreground | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh (after the document edits and the tests) | duration_s=n/a | mode=background | result=pass
- bash docs/architecture/scripts/screenshot-architecture-map.sh (after the last regeneration) | duration_s=n/a | mode=foreground | result=pass
- bash docs/architecture/scripts/check-architecture-model-freshness.sh (first run, while Javadoc and generator edits were still being made) | duration_s=n/a | mode=background | result=fail (stale, not trustworthy because of the concurrent edits)

- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox (second full run) | duration_s=446 | mode=background | result=fail (1 unstable test, not caused by these changes)
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --no-unit --integration-test ProviderProfilePaginationScenarioTest --sandbox (working tree) | duration_s=162 | mode=background | result=fail (the same test)
- the same class on a clean checkout of HEAD, first try | duration_s=60 | mode=background | result=fail (mvnw without the execute right in the temporary worktree, not a test failure)
- the same class on a clean checkout of HEAD | duration_s=161 | mode=background | result=fail (the same test, another order)
- the same class with temporary printing lines, four runs (21:08, 21:29, 21:32, 21:35) | duration_s=about 190 each | mode=background | result=pass (all four)
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --no-unit --integration-test ProviderProfilePaginationScenarioTest --sandbox (after the test fix) | duration_s=n/a | mode=background | result=pass
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox (after the map regeneration) | duration_s=444 | mode=background | result=fail (PromotedProviderProfileScenarioTest, one of 957)
- the failed class alone | duration_s=243 | mode=foreground | result=pass
- the temporary 150-round loop test of that scenario | duration_s=about 600 | mode=background | result=pass (4 rounds with the clock back, same 4 rounds with a wrong total)
- the class after the test fix | duration_s=319 | mode=foreground | result=pass
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox (after both test fixes) | duration_s=894 | mode=background | result=pass (1910 tests)

### Review angle yield
- dry-kiss-yagni | survived=0 | total_candidates=1 | tokens=396812
- solid | survived=0 | total_candidates=0 | tokens=384139
- precedent | survived=1 | total_candidates=1 | tokens=412837
