# improvement-250: A Port returns a SQL predicate for cross-module filters that can match an unbounded number of rows (Taxon first)

**Type:** improvement — revised design and final plan (2026-11-13); nothing is implemented until the user starts it
**Module:** `platform-commons` (`core.model`, `taxon.spi.TaxonPort`), `taxon-spring-boot-starter`, `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`, `integration-tests`, `marketplace-app` (`ArchitectureRulesTest`), the decision files of `taxon-spring-boot-starter` and `platform-commons`
**Priority:** 🔵 larger tech debt: no live bug at today's volume; the user chose the target design and parked the execution
**When:** independent, no blockers; execution waits for the user's go and for decisions 3, 4, 5, 7, 8, 9 and 10 below; step F0 (decision records) runs first, through `/record-decision`

## Current state

Checked against the code on 2026-11-09 and again on 2026-11-13:

- `taxon_assignment` belongs to `taxon-spring-boot-starter`. A list filtered by category or city runs three statements per page, in this order: `TaxonAssignmentRepository.findEntityIdsByTaxonIds` (`SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = :entityType AND taxon_id = ANY(:taxonIds)`), then `AdvertisementRepository.findByFilter` and `.countByFilter` with `AND a.id = ANY(:allowedIds)`. The id set travels database → Java → database. `AdvertisementService.getFiltered()` and `.count()` each resolve the filter, so the Taxon lookup runs twice per page; `ProviderProfileService` does the same.
- The old contract has exactly two callers outside Taxon: `AdvertisementService` and `ProviderProfileService` (each calls `TaxonPort.resolveCategoryAndCityFilter`); inside Taxon it runs through `DefaultTaxonPort` → `TaxonAssignmentService` → `TaxonAssignmentRepository.findEntityIdsByTaxonIds`.
- Provider profiles have a second, unrelated use of an id array: `ProviderProfileScope.onlyIds()` (the promoted placements, a bounded list) is bound as `allowedIds` in `ProviderProfileRepository.buildWhere`; the service intersects it in Java with the taxon ids. The promoted ids stay an id set; only the intersection moves into SQL.
- No cross-starter foreign key, view or function exists in any changelog; every starter runs its own Liquibase. Opaque ids (`Long`, `EntityRef`) are already the only cross-module reference format. `taxon_assignment` has one index, on `taxon_id`.
- The Accepted decision of `taxon-spring-boot-starter` ("Filter resolves through `Set<Long>`, not SQL JOIN") fixes the id-set flow and names its own trigger to revisit (a category routinely resolving to more than 10,000 ids; the options it names are a starter-owned JOIN helper or a pre-computed mapping); no `WARN` for it exists.
- `marketplace-app`'s "No raw cross-starter SQL joins" decision forbids a consumer from hard-coding another starter's table or column names; its reason is a rename breaking the consumer at runtime with no compile-time warning.
- `platform-commons`'s decision about `TaxonPort.resolveCategoryAndCityFilter` allows one default method on a Port as a named exception to its governance rule; the rule lists "business logic of any kind" and "feature-specific models or persistence logic" as NOT ALLOWED.
- No Taxon hierarchy (parent/child) exists in the Taxon code, so a filter value is a flat set of taxon ids.
- `taxon-spring-boot-starter` has `<scope>runtime</scope>` in `marketplace-app`'s pom. The list query blocks (`AdvertisementQueryBlock`, `ProviderProfileQueryBlock`) build the category and city filter rows unconditionally from `taxonCatalogService.getAllByType(...)`, which returns an empty list without Taxon, so without Taxon two empty dropdowns are shown that do nothing. `MainView` already hides the reference-data tab with `taxonCatalogService.isAvailable()`.
- Two research passes are done (folders `backlog/research/pluggable-modules-opaque-refs/` and `backlog/research/pluggable-modules-query-fragment-port/`, measurements and SQL scripts inside, `measurements/01-schema-data.sql` to `09-plans-final.sql`, `06-median.py`); the first pass is closed by this task's research, see [improvement-249](improvement-249-pluggable-modules-opaque-cross-module-references-research.md).

## Why change

The user wants one general rule for a filter over another module's data, with these goals together: modules do not know each other's classes, tables or repositories; any starter can be removed from `pom.xml` and nothing breaks or is left behind in the remaining modules; filtering, sorting and paging run in PostgreSQL, not on id sets carried through Java; minimal complexity; each module owns its data.

Today the third goal fails: a filter whose result can be large carries its id set through Java, twice per page. At 241,000 matching advertisements one list page costs about 0.9-1.1 s on the server alone (measured), before the JDBC transfer of the ids. The same shape appears for any future filter by another module's data that can match many rows (for example by a user attribute).

## Expected benefit

- No id set of an unbounded size crosses Java; the list page and its count are one PostgreSQL statement each, in one snapshot.
- Removing Taxon leaves nothing in Advertisement or Provider Profile (no Taxon table, column or id), the statements simply carry no predicate.
- Measured on 1,000,000 advertisements, median of five rounds, per list page (page + count): 241k matching 334 ms against 907 ms today (today's JDBC transfer not included), 66k 228 against 323 ms, 31k 219 against 231 ms.
- Cost: selective filters get slower (3k matching 104 against 30 ms; 6.3k 82 against 52 ms). The first research pass's local relation table (variant B) measured the same speed as the predicate, so owning the table gives no speed.
- A written rule instead of case-by-case decisions: when an id set is enough and when a predicate is needed.
- One existing exception disappears (the default method with logic on `TaxonPort`) and no new one is added.

## Approach

Revision of 2026-11-13, asked for by the user ("look for ways without exceptions"): the two types are plain value objects with no behaviour beyond derivations of their own fields, so no governance exception is needed, and the "No raw cross-starter SQL joins" decision is not contradicted, so it is not annotated.

### The rule (chosen by the user)

Predicate where an id set cannot be avoided, the normal path everywhere else:

1. A reference to another module's entity is an opaque id (`Long`, or `EntityRef` where one table points at several entity types). No new id type, no registry.
2. A filter over another module's data whose result is bounded (a page of ids, the promotion placements, the profiles qualifying for a recount, a user's own selection) stays an id set or a bulk lookup through the Port.
3. A filter over another module's data whose result can match an unbounded number of rows is a **predicate built by the owning module**: the Port returns a typed `SqlPredicate`, the consumer splices it into its own page and count statements. Never for reading the other module's columns, never as a join, view or function written by the consumer.

Today rule 3 has exactly one user: the category/city filter of Taxon.

### The contract (typed, never a free string)

Two plain records in `platform-commons` `org.ost.platform.core.model`, JDK and Lombok only, next to `EntityRef`:

```java
public record SqlColumn(@NonNull String alias, @NonNull String column) { public String sql(); }   // alias + "." + column
public record SqlPredicate(@NonNull String sql, @NonNull Map<String, Object> params) {
    public String asAndClause();   // " AND (" + sql + ")"
}
// TaxonPort replaces findEntityIdsWithAnyTaxon and the default resolveCategoryAndCityFilter with:
Optional<SqlPredicate> categoryAndCityPredicate(@NonNull EntityType entityType, @NonNull SqlColumn entityId,
                                                Set<Long> categoryIds, Long cityTaxonId);
```

The consumer binds the values itself: `params.addValues(predicate.params())` and appends `predicate.asAndClause()` to its own `WHERE`.

Why this needs no exception: the governance rule allows shared value objects used by two or more modules (Taxon, Advertisement and Provider Profile use these), and the narrow existing allowance covers a pure derivation over a value type's own fields (`SqlColumn.sql()` and `SqlPredicate.asAndClause()` are exactly that; `ChangeEntry` in the same package is the precedent). There is no `appendTo` with callbacks, no bind logic, no logic that touches a database. The constructors check the invariants of the type's own fields and fail fast, the same principle the project requires of every method; this is the one judgment call of the design: read strictly, the rule knows validation only as declarative annotations, and if the user reads it that way the checks move into Taxon's builder and into the consumer's splice point, at the cost of the same check written three times.

Invariants built into the two constructors (each reproduced as a silent wrong result or an injection path with a raw `String` + `Map` + `String targetIdColumn` contract, see Related):

- values travel only as named bind parameters; a test asserts that a bound id never appears in the predicate text;
- identifiers only through `SqlColumn`, each part matching `^[a-z][a-z0-9_]{0,62}$` (an allow-list by construction; no quote, dot, space, semicolon or comment can enter);
- parameter names of a predicate start with `_` and must occur in its text as `:name`; the parameter names of a consumer's own statements never start with `_`, so a collision cannot happen between a provider's parameter and a consumer's; Taxon builds category and city as one predicate, so two predicates of one provider never meet in one statement;
- `asAndClause()` wraps the text in parentheses (a predicate with `OR` appended bare lost the consumer's own filter: 0 rows expected, 3 returned);
- the predicate text is refused when it contains `;`, `--` or `/*`;
- only first-party starter repositories may construct a `SqlPredicate` (ArchUnit rule, decision 4).

How flexible the column is: any alias and any key column of the consumer, several predicates in one statement (category AND city, one predicate per `UNION` branch), `WHERE`, `JOIN ... ON`, a boolean `ORDER BY` term, inside count/page/subquery wrappers (the wrapper's alias is passed); a composite key (type column + id column) by a second descriptor added when its first consumer needs it. It stops at: an expression instead of a column (`COALESCE`, a cast, a function), quoted or schema-qualified names, a column chosen by a request, consumer SQL inside the provider's predicate, the provider's columns in the consumer's `SELECT`, aggregates or a join returning provider data.

### What the consumer's statement looks like (sketch, final text after step F-M)

```sql
SELECT ... FROM advertisement a
WHERE a.deleted_at IS NULL
  AND (EXISTS (SELECT 1 FROM taxon_assignment _tac            -- written by Taxon
               WHERE _tac.entity_type = :_taxonEntityType
                 AND _tac.entity_id   = a.id                  -- the consumer's SqlColumn
                 AND _tac.taxon_id    = ANY(:_taxonCategoryIds)))
ORDER BY a.created_at DESC LIMIT 20
```

Behaviour: no Taxon starter, or neither filter requested, means an empty `Optional` and the clause is not added; a present but empty category set matches nothing (0 rows, never "no restriction").

### The one real cost (not an exception)

Any in-database filter over another module's data makes the consumer's statement touch the owner's table when it runs: the code of the consumer never names it, but the database must hold both tables, and the application's database role must be allowed to read `taxon_assignment`. It matters only if the modules are ever split across databases or database roles; then the statement fails, while the id-set flow would survive. The two paths that avoid it are rejected: the id set through Java (fails the filter-in-database goal at scale) and a relation table in the consumer (leaves Taxon ids behind after Taxon is removed).

### Options compared (what the research measured or read)

| Option | Result | Verdict |
|---|---|---|
| A+ — keep the id set, add the covering index and one lookup per page | no recorded decision changes; selective filters stay fast; the id set still crosses Java | the fallback if the user withdraws decision 1 |
| **F — the predicate (this task)** | meets all five goals; big filters 2-3 times faster, selective ones slower | chosen |
| B — relation table owned by Advertisement / Provider Profile | the same speed as F; leaves Taxon ids in the consumer after Taxon is removed | rejected by the user as not real decoupling |
| Database function called by the consumer | not measured; hides the table but adds a database object and a planner risk (a non-inlined function hides its row estimate); everything it gives, the predicate gives | not taken; may be measured in step F-M |
| `a.id = ANY(ARRAY(SELECT ...))` shape | 22 ms for a rare category, 2.4 s at 241k, a timeout with a city filter | only as a Taxon-internal shape for selective taxons (step F8) |

### Residual risks the plan accepts

- Same database, same schema and a role that may read `taxon_assignment` (see "The one real cost").
- The consumer runs SQL text it did not write: a Taxon table change is seen only by a test against PostgreSQL (Taxon's own contract test runs the predicate against a `VALUES` table as the consumer).
- Selective filters get slower; the count of a big filter stays the most expensive part in every variant (a different count strategy belongs to improvement-003 item K).
- The measurements are one synthetic data set on one machine, ±30%, with `created_at` random per row (the worst case for the page walk); with `created_at` in insertion order a rare-category page fell from 217 to 90 ms.

### Decisions waiting for the user

1. Done by the user: the rule above (the predicate where an id set cannot be avoided, the normal path elsewhere).
2. Resolved by the design: the two types live in `platform-commons` `core.model` as plain value objects, with no governance exception; the alternatives (query-lib, a new tiny library) are not needed. One judgment call remains: constructor invariants (see "The contract").
3. The covering index `(taxon_id, entity_type, entity_id)`: yes (proposed); replace the `taxon_id` index (proposed) or keep both. It makes the admin usage counts slower (247 → 415 ms measured on 2.6M rows).
4. The ArchUnit guard (only starter repositories build a predicate) and the rule "only first-party starters provide predicates": yes (proposed) or no.
5. A latency target for one list page (page + count); it decides whether step F8 ever runs; a number, or "none, F8 is skipped".
6. Resolved by the design: "No raw cross-starter SQL joins" is not annotated (the consumer writes no foreign name; the new Taxon record states the relation). Step F7 reads the entries of the other decision files that describe the id-set flow as current and annotates those.
7. Names: `SqlPredicate` and `SqlColumn` (proposed) or `SqlFragment`.
8. The composite-key descriptor: later, when a first consumer needs it (proposed), or now.
9. What a category filter does when Taxon is absent: ignored today, covered by a test; for a REST caller it is a silent unfiltered list. Keep (proposed) or answer 503 for a request that carries a category or city filter.
10. The UI part of the same case: hide the category and city filter rows of the two list query blocks when `taxonCatalogService.isAvailable()` is false. Proposed as a separate small task, independent of this change (about 10-15 lines per block plus a component test; the create/edit forms were not checked).

### Defaults used for execution (2026-11-13, each changes only if the user says so)

- 3: the covering index is built only if the measurement of step F-M, on PostgreSQL 18, lowers the median of page plus count by at least 20% for the 241k and 66k filters; it then replaces `idx_taxon_assignment_taxon_id` (the new index starts with the same column). Measured: that gate is not met (11%), but the index removes the regression of the selective filters (up to 4.7 times slower without it) for +24% on the full-scan usage count; the user chose to build it on 2026-11-13, so F5 is part of the plan.
- 4: the ArchUnit guard is built (F6).
- 5: no latency target, so step F8 is not run.
- 7: `SqlPredicate` and `SqlColumn`.
- 8: the composite-key descriptor waits for its first consumer.
- 9: a REST request with a category or city filter and no Taxon keeps being answered unfiltered; the behaviour is now stated in the Taxon record and pinned by the Taxon-absent test.
- 10: done here as step F9, with the two query blocks and the two create/edit forms; category and city are not required fields (no `@NotEmpty` on `categoryIds`), so the forms work without Taxon and only show dead dropdowns.

## Decision-record updates (drafts; written only through `/record-decision`, then regenerate the index)

**1. `taxon-spring-boot-starter`: new decision "Category and city filter is a Taxon-built SQL predicate the owner splices into its own statement"** — marks the Accepted "Filter resolves through `Set<Long>`, not SQL JOIN" as Superseded by it.

- Context: the id-set flow carries an id set of unbounded size through Java, twice per page (241k ids: 0.9-1.1 s per list page on the server, planning of the bound array about 140 ms per statement); the revisit trigger of the superseded decision (a category above 10k ids) is reached in the measurement set; the revisit options it named were a starter-owned JOIN helper or a pre-computed mapping; the predicate is the first.
- Decision: `TaxonPort.categoryAndCityPredicate(entityType, entityId, categoryIds, cityTaxonId)` returns `Optional<SqlPredicate>`; Taxon writes the whole `EXISTS` on `taxon_assignment` (aliases `_tac`, `_tay`, parameters `_taxon*`); the consumer passes its own `SqlColumn` and splices the result into its page and count statements; empty when neither filter is requested or Taxon is absent; a present but empty category set matches nothing. Promotion pins and placements stay id sets. This is also the one home of the general three-rule statement (opaque ids; bounded results as id sets; unbounded filters as predicates); the other record points to it. `SqlColumn` and `SqlPredicate` are plain value objects in `platform-commons` `core.model`; a consumer writes no foreign table or column name, so the decision about raw cross-starter joins is kept as it is.
- Consequences: removing Taxon leaves no Taxon name in the consumer; a Taxon schema change is caught by Taxon's contract test run against PostgreSQL; the two early "empty set" returns and the Java intersection in the profile service go; the deployment assumption is one database and a role that may read `taxon_assignment`.
- Rejected: the relation table in the consumer (leaves Taxon ids behind), a database function (adds an object for no gain), keeping the id set (fails the filter-in-database goal at scale).

**2. `platform-commons`: marks the decision about `TaxonPort.resolveCategoryAndCityFilter` as a default method Superseded** — the method disappears; the default-method exception stays valid for its other uses (it is not removed from the governance text unless no default method with logic remains; checked in step F7). The new value types need no record of their own: they are inside the existing allowance for shared value objects and pure derivations, stated in the Taxon record.

**Not changed:** the decision about raw cross-starter joins in `marketplace-app`; the query-lib API-freeze decision (query-lib is untouched); the rules about `Optional` parameters and sibling imports (the types live in `platform-commons`).

**`ArchitectureRulesTest`:** no existing rule breaks; one optional new rule in step F6.

## Execution plan

Steps F1-F4 are one change set (the contract is replaced, not added next to the old one): run them in order and build after F4. Common rules for every step: read `/app/CLAUDE.md`, `/app/.claude/rules.md` and the `.claude/rules/<module>.md` of every module touched before editing; `lombok.NonNull` on every non-null public parameter; no `Optional` parameter; comments one line or none, no task or ADR number; `git add` after each file change, never `git commit`. Executor models: sonnet F0-F4, F6, F7; haiku F5; opus F-M and F8.

### Execution phases (each ends at a gate; the next one starts only after the user's yes)

1. **Measure and record:** F-M on PostgreSQL 18, then F0. Gate: the user sees the measured table and the chosen predicate text.
2. **Contract, Taxon and consumers (one change set):** F1, F2, F3, F4 with their repository, service, level 3 and Taxon-absent tests. After F2 the Advertisement and Provider Profile starters and their tests no longer compile (they call the removed methods), and the reactor builds before any test runs, so no build or test can run before F4: the gate is one, after F4: `bash scripts/build-and-test.sh --unit --integration --sandbox` green and the three mutation checks of the test table done.
3. **Index and guard:** F5 (built, the user said yes), F6. Gate: integration tests and `ArchitectureRulesTest` green.
4. **UI:** F9. Gate: marketplace-app unit tests green and Playwright `e2e --full --ux` green (the filters still show with Taxon present).
5. **Documents and closing:** F7, the map and ADR index regenerated, an independent review, one full CI run with `--reset-e2e-stack`, the task closed by the lifecycle rules. Nothing is committed unless the user asks.

Status (updated as work happens):

- 2026-11-13: phase 1 done: F-M measured (results and the full table under step F-M); the user chose to build the covering index; F0 written (two decision records, the index regenerated).
- 2026-11-13: phase 2 started without its plan being shown (a mistake the user pointed out): the main thread wrote F1 (`SqlColumn`, `SqlPredicate` and their two tests in `platform-commons`) and F2 (`TaxonPort`, `DefaultTaxonPort`, `TaxonAssignmentService`, `TaxonAssignmentRepository`, the new `TaxonCategoryAndCityPredicateTest`, the case removed from `TaxonAssignmentRepositoryTest`, `TaxonPortResolveCategoryAndCityFilterTest` deleted) directly. Nothing is compiled or tested yet. The user said to keep it; step P2-0 below checks it. From here on every step follows the plan below, with a Haiku agent wherever the change is fully specified.
- 2026-11-13: phase 2 done and its gate passed. P2-0: three deviations of the written F1/F2 files found and fixed by a Haiku agent (a false-failing assertion in the Taxon test, a two-sentence Javadoc, two test classes without a class Javadoc). P2-1 and P2-2 (Haiku): F3 and F4 applied; the agent corrected the OR test of the plan (the predicate ids had to be swapped, otherwise the test could not fail without the parentheses). P2-3 (Sonnet): category AND city on both lists, promoted AND category, and the Taxon-absent test with the table renamed during the calls. P2-4: `build-and-test.sh --unit --integration --sandbox` green: 1014 unit tests (43, 23, 45, 10, 256, 291, 241, 105 per module) and 1009 integration tests, no failure; then the three mutations on the working tree, each caught and reverted: no parentheses in `asAndClause()` (`findByFilter_predicateWithOrIsParenthesisedSoTheConsumerFilterStillApplies`), values in the predicate text (`textDoesNotDependOnTheValues_andNoValueTravelsInIt`), an empty category set that restricts nothing (`presentButEmptyCategorySet_matchesNothingInsteadOfEverything`); 3 failures of 24 in the two classes run, nothing else failed.
- 2026-11-13: phase 3 done and its gate passed. F5 (Haiku): `idx_taxon_assignment_cover (taxon_id, entity_type, entity_id)` replaces `idx_taxon_assignment_taxon_id` in the base changeset of `001-taxon.xml` (edited in place, the changeset carries `validCheckSum ANY`; a database that already ran it keeps the old index until it is reset). F6 (Haiku): the rule `sql_predicates_are_built_only_in_starter_repositories`; the probe (a temporary class building a `SqlPredicate` in `org.ost.marketplace.probe`) showed that the first version never failed, because `noClasses().should(condition)` negates the condition and turns its `violated` events into satisfied ones; the rule was changed to `classes().should(condition)` and the same probe then failed it with the class named; the probe class was removed. The same `noClasses().should(new ArchCondition…)` with `SimpleConditionEvent.violated` is used by four other rules of `ArchitectureRulesTest` (Optional parameters, sibling starter dependency, `TaxonPort.replaceAssignments` callers, the orchestrator Port count), which by the same reasoning cannot fail; not verified for those four, reported to the user. Full run after the probe was removed: 1015 unit tests (43, 23, 45, 10, 256, 291, 241, 106 per module) and 1009 integration tests, no failure; `ArchitectureRulesTest` 26 tests green.
- 2026-11-13: phase 4 done and its gate passed. F9 (Haiku edits, Sonnet tests): the category and city rows of `AdvertisementQueryBlock` and `ProviderProfileQueryBlock` are built only when `taxonCatalogService.isAvailable()`; the advertisement form sets its two combo boxes to null (it already guarded every use); the provider profile form got guards at its seven uses; `AdvertisementQueryBlockTest` and `ProviderProfileQueryBlockTest` (2 cases each, available and unavailable). Mutation check on the working tree: with the condition replaced by `if (true)` both "unavailable" tests failed and both "available" tests failed on an unused stub; the probe was reverted. Unit tests: 1019 (43, 23, 45, 10, 256, 291, 241, 110 per module), no failure. Deploy with `deploy-and-run.sh --reset --dev-tools` (2 min 43 s): the fresh database holds `idx_taxon_assignment_cover` and `pk_taxon_assignment` on `taxon_assignment`. Playwright `e2e --full --ux`: 69 passed in 11.5 min, none failed, including the category and city filter spec over 60 seeded advertisements.
- 2026-11-13: phase 5 done and the task closed. The documentation of F7 was edited by Haiku agents from exact texts (four rules files, four READMEs, two decision records, the Javadoc of `TaxonPort`), then `/sync-docs` with six read-only audits found 14 stale or missing comments, all corrected after one yes; `/check-architecture-map` reported the map up to date; `/deep-review` at its default effort found no confirmed finding and one medium-confidence DRY item (a third copy of `createCategory` in the level 3 tests), left to the user; the full `ci.sh --foreground --reset-e2e-stack` run passed all 15 stages in 26 min 56 s (unit 8 min 11 s, integration 11 min 32 s, e2e 19 min 28 s, Sonar quality gate green). Step F8 was not run: no latency target was set.

#### Plan of the remaining work (Approval Rule format; each step names who does it)

Rules of this plan: an agent prompt is self-contained (a fresh agent has no conversation), quotes the approval of the user for that step, lists the files it may touch and forbids everything else, forbids `git commit`, and asks for the exact diff back; the main thread then reads the real diff and runs the build or tests. If an agent refuses to work for lack of a provable approval (an earlier one did), the main thread stops and reports it to the user; it does not write the change itself.

**Phase 2 — contract, Taxon and consumers**

- **P2-0 Check what is written (F1, F2).** *Summary:* read each of the eleven files named in the status line against the F1 and F2 text of this task (invariants, names, `_tac`/`_tay`/`_taxon*` parameters, `Long[]` binding, the imports); no build is possible yet. *Agent:* no. *Model:* —. *Input:* the eleven files. *Result:* a list of deviations, each fixed by a Haiku agent with the exact correction, or "matches the spec".
- **P2-1 F3 Advertisement.** *Summary:* `AdvertisementRepository`: add `public static final SqlColumn ID = new SqlColumn("a", "id");`, change `findByFilter(filter, pageable, SqlPredicate taxonPredicate)` and `countByFilter(filter, SqlPredicate taxonPredicate)`, replace `buildIdClause` by a method that returns `""` for a null predicate and otherwise calls `params.addValues(taxonPredicate.params())` and returns `taxonPredicate.asAndClause()`, remove the `allowedIds` parameter and its comment; `AdvertisementService`: replace the id-set resolution by `resolveTaxonPredicate(filter)` (`taxonPortFactory.findIfAvailable().flatMap(p -> p.categoryAndCityPredicate(EntityType.ADVERTISEMENT, AdvertisementRepository.ID, filter.getCategoryIds(), filter.getCityTaxonId()))`), pass `.orElse(null)` from `getFiltered` and `count`, delete the two early returns on an empty set; `AdvertisementRepositoryTest`: the two `allowedIds` cases become predicate cases (page and count) with a test-built `new SqlPredicate("a.id = ANY(:_ids)", Map.of("_ids", new Long[]{...}))`, plus one case with a predicate text that contains `OR` (`"a.id = :_one OR a.id = :_two"`) and a consumer filter, proving the parentheses keep the consumer's filter; `AdvertisementServiceCategoryFilterTest`: Mockito cases for predicate passed through, null when Taxon is absent, null when no filter is requested, and delete the two "returns empty without querying" cases and the Javadoc naming the removed method. *Agent:* yes. *Model:* haiku. *Input:* the four files above and the F3 text of this task. *Result:* the four files changed as described; the diff returned and read by the main thread.
- **P2-2 F4 Provider Profile** (parallel with P2-1, other files). *Summary:* `ProviderProfileRepository`: `findByFilter` and `countByFilter` gain a nullable `SqlPredicate taxonPredicate` after the scope; `buildWhere` keeps `pp.id = ANY(:allowedIds)` for `scope.onlyIds()` and adds the predicate (`params.addValues`, `asAndClause()` content without the leading ` AND ` when joined with the other conditions: the predicate text wrapped in parentheses as one more entry of the `conditions` list); `public static final SqlColumn ID = new SqlColumn("pp", "id");`; `ProviderProfileService`: delete `withTaxonFilter` and the Java intersection, add `resolveTaxonPredicate(filter)` with `EntityType.PROVIDER_PROFILE`, pass the scope unchanged and the predicate separately; `ProviderProfileRepositoryTest`: the three `allowedIds` cases stay (promotion scope), add predicate alone and predicate AND `onlyIds` together, page and count; `ProviderProfileServiceTest`: the five mock cases of the taxon filter become predicate pass-through, null without Taxon, null without a filter. *Agent:* yes. *Model:* haiku. *Input:* the four files and the F4 text. *Result:* the four files changed; diff returned and read by the main thread.
- **P2-3 Level 3 and Taxon-absent tests.** *Summary:* in `AdvertisementPaginationScenarioTest` and `ProviderProfilePaginationScenarioTest` add category AND city together; in `PromotedProviderProfileScenarioTest` (or the profile pagination test) add `promoted=true` with `categoryIds`; a new level 2 test next to `OptionalStarterRestAbsenceTest` that builds the same context without the Taxon starter, renames `taxon_assignment` to `taxon_assignment_hidden` before the calls and back in a `finally`, and asserts `GET /api/advertisements` and `GET /api/provider-profiles` with `categoryIds` and `cityTaxonId` answer 200 and return the unfiltered lists. *Agent:* yes. *Model:* sonnet, because the seeding and the context reuse are design decisions that cannot be written as an exact diff up front. *Input:* the three scenario tests and the absence test. *Result:* the new cases; diff read by the main thread.
- **P2-4 Gate.** *Summary:* `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --integration --sandbox` (the Monitor on its `tree.txt`); then the three mutation checks, each applied by the main thread and reverted: drop the parentheses of `asAndClause()` (the `OR` case must fail), put a value into the text of the Taxon predicate (the text-independence test must fail), return no restriction for an empty category set (the matches-nothing case must fail). *Agent:* no. *Model:* —. *Input:* the working tree. *Result:* green counts reported as numbers, the three mutations shown failing and reverted, everything `git add`ed.

**Phase 3 — index and guard**

- **P3-1 F5.** *Summary:* in `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/001-taxon.xml` replace the `createIndex` named `idx_taxon_assignment_taxon_id` by one named `idx_taxon_assignment_cover` on `taxon_assignment` with the columns `taxon_id`, `entity_type`, `entity_id` in that order, and one XML comment line above it stating why (the predicate's index-only lookups); `createIndex` has no `remarks` attribute, so the comment is the rule's one-line form. The changeset is edited in place as the project does for base changesets; the agent verifies that it carries `validCheckSum ANY` and stops with a report if it does not. A database that already ran the old changeset keeps the old index until it is reset. *Agent:* yes. *Model:* haiku. *Input:* that file. *Result:* the one changed block; diff read by the main thread.
- **P3-2 F6.** *Summary:* add to `ArchitectureRulesTest` the `@ArchTest` rule `sql_predicates_are_built_only_in_starter_repositories` built as `classes().should(new ArchCondition<JavaClass>(...))` adding `violated` events (not `noClasses()`, which negates the condition and makes the rule unable to fail; the existing rule `starters_must_route_taxon_assignment_writes_through_orchestrator` uses `noClasses()` with the same event type), a custom `ArchCondition` over constructor calls of `org.ost.platform.core.model.SqlPredicate`, allowed only from a `..repository..` package of a starter; the agent checks that test classes are outside the import scope and says so. *Agent:* yes. *Model:* haiku. *Input:* `ArchitectureRulesTest.java`. *Result:* one new rule; the main thread then adds a probe (a `SqlPredicate` built in a service), sees the rule fail, and reverts it.
- **P3-3 Gate.** *Summary:* `build-and-test.sh --unit --integration --sandbox` again, then the same script with `--archunit-metrics` only if the rule needs it. *Agent:* no. *Result:* green counts.

**Phase 4 — UI (F9)**

- **P4-1 Edits.** *Summary:* in `AdvertisementQueryBlock` and `ProviderProfileQueryBlock` build the category field and the city field and call their `filterRow` only inside `if (taxonCatalogService.isAvailable())`; in `AdvertisementFormOverlayModeHandler` and `ProviderProfileFormOverlayModeHandler` do the same for the category and city fields and their bindings, nothing else changes. *Agent:* yes. *Model:* haiku. *Input:* the four files. *Result:* the four files changed; diff read by the main thread.
- **P4-2 Component tests.** *Summary:* a test per query block in the style of `TimelineViewTest`: `isAvailable()` false means no category or city field is present, true means both are. *Agent:* yes. *Model:* sonnet (the construction of the Vaadin block with mocks is a design choice). *Input:* `TimelineViewTest` and the two blocks. *Result:* two test classes.
- **P4-3 Gate.** *Summary:* `build-and-test.sh --unit --sandbox`, then `bash scripts/activity-monitor.sh -- scripts/playwright.sh e2e --full --ux` (always with `--ux`; the app must be deployed first with `deploy-and-run.sh`, and because F5 changed a base changeset the dev database needs `--reset`). *Agent:* no. *Result:* counts, and the filter specs green.

**Phase 5 — documents and closing**

- **P5-1 Document edits.** *Summary:* the main thread writes the exact new texts for each place of step F7 (the four rules files, the three READMEs, the Javadoc and comments) and a Haiku agent applies them verbatim. *Agent:* yes. *Model:* haiku. *Input:* the F7 list and the texts. *Result:* the places changed; diff read by the main thread.
- **P5-2 Check and generate.** *Summary:* `/sync-docs` in diff mode (its own sonnet checker), `bash .claude/nav/scripts/generate-adr-index.sh`, `bash docs/architecture/scripts/generate-architecture-model.sh`, `/check-architecture-map`. *Agent:* no for the commands the main thread runs; the skill dispatches its own checkers. *Result:* the sync report shown as it is, the map up to date.
- **P5-3 Independent review.** *Summary:* `/deep-review` of the uncommitted changes at its default effort (read-only; the level is stated in the report). *Agent:* the skill's own read-only agents. *Result:* findings shown to the user for decision; nothing is changed without it.
- **P5-4 Full CI.** *Summary:* `bash scripts/activity-monitor.sh -- bash scripts/ci.sh --foreground --reset-e2e-stack` (the reset is needed because a base changeset changed). *Agent:* no. *Result:* every stage green, artifacts synced to the host.
- **P5-5 Close the task.** *Summary:* move this file to `backlog/completed/tasks/`, remove its rows from `BACKLOG.md`, add the archive entry, add 1 to the `Applied` of every ADR in `adrs_referenced` that this task did not create (ADR-001, ADR-034, ADR-032), update `Commands used` and the counters in the commands README, regenerate the index; the Operational notes block is shown in the final report. *Agent:* yes for the exact bookkeeping edits. *Model:* haiku. *Result:* the task closed; no commit is made unless the user asks.

### Step F-M — Measure the open shapes (before F2)

- **Executor:** opus. **Depends on:** nothing. **Gates:** the predicate text of F2 and the decision on F8.
- **Goal:** decide the exact text Taxon builds, by measurement: (a) `EXISTS` against a `JOIN` with `DISTINCT` (the JOIN may let the planner start from the small `taxon_assignment` side for a selective filter); (b) a database function against the inline `EXISTS` (only to confirm the verdict above); (c) the rare-category page with parallel workers disabled for the statement; (d) the shape chosen by the size of the taxon (`ANY(ARRAY(subquery))` for a selective taxon).
- **Input:** the data and SQL scripts in `backlog/research/pluggable-modules-query-fragment-port/measurements/` (`01-schema-data.sql` the data, `03-run.sql` and `06-median.py` the five-round median, `09-plans-final.sql` and `R10-plans-final.txt` the last plans). The two research folders stay in `backlog/research/` as history and are never removed without the user's explicit approval.
- **Where and on what:** the first pass ran on PostgreSQL 15 in a database created in the dev container; the project is on 18 now (the dev container is 18.6), so the numbers are measured again, not carried over. A throw-away database in the running dev Postgres container (`docker exec ... psql`, the same practice as the first pass; no other database of that server is touched), dropped at the end. Shapes: the id set of today (A), the `EXISTS` predicate (F), the `JOIN ... DISTINCT` form, and F with the covering index created inside the throw-away database (the gate of F5). (b) is dropped (confirmatory only) and (d) belongs to F8.
- **Result:** a measured table on `created_at` in insertion order, five rounds, page and count, 3k / 6.3k / 31k / 66k / 241k matching, set beside the PostgreSQL 15 numbers; the chosen predicate text for F2; the yes or no for F5 by the 20% rule of the defaults.
- **Done 2026-11-13 on PostgreSQL 18.6** (1,000,000 advertisements, 2,581,390 assignments, five rounds, medians of planning plus execution in ms per list page = page + count; `created_at` in insertion order, as real data; A = today's flow with its two lookups per page, F = the `EXISTS` predicate, J = the `JOIN` of the distinct ids; "cover" = the covering index in place of `idx_taxon_assignment_taxon_id`):

  | Filter (matching rows) | A today | F | F with cover | J |
  |---|---|---|---|---|
  | category 1 (241k) | 1088 | 383 | 336 | 1694 |
  | category 2 (66k) | 486 | 288 | 257 | 735 |
  | three categories (41k) | 409 | 279 | 249 | 632 |
  | category 5 (31k) | 289 | 255 | 273 | 594 |
  | category 50 (6.3k) | 117 | 262 | 35 | 70 |
  | category 150 (3k) | 68 | 318 | 127 | 32 |
  | category 5 AND city 201 (3.1k) | 325 | 296 | 147 | 876 |
  | category 5, offset 1000 | 422 | 458 | 346 | 610 |
  | category 150 AND city 250 (22) | 73 | 55 | 10 | 29 |

  The usage-count query of the admin screen over all 300 taxa (a scan of all 2.58M rows): 383 ms with the present index, 474 ms with the covering one (+24%).
- **Verdicts:** `EXISTS` (F) is the text for F2: the `JOIN` form is 2.5 to 5 times slower on the big filters (1694 against 383 ms at 241k) and is better only for the selective ones (3k: 32 against 318 ms), a shape for F8. The big-filter gain of the predicate holds on 18 (241k: 336 against 838 ms with both sides on the covering index, 2.5 times; 66k: 257 against 345). Without the covering index the selective filters regress against today (6.3k: 262 against 117 ms, 3k: 318 against 68 ms); with it only the 3k case stays slower (127 against 48 ms). The F5 gate of the defaults (a gain of 20% for 241k and 66k) is not met (11% and 11%), because the index helps the selective filters, not the big ones: the gate measured the wrong cases. Recommendation: build the covering index (F5), at the cost of +24% on the full-scan usage count.

#### Measurement record of step F-M (PostgreSQL 18.6, 2026-11-13)

Data: 1,000,000 advertisements (5% soft-deleted), 200 categories and 100 cities, 2,581,390 assignments of both entity types, schema as in the changelogs; load 4 min 13 s; throw-away database `research_fragment`, dropped afterwards. Phases: `P1-random-current` = the first pass's random `created_at` with the present index (the worst case for the page walk); `P3-ordered-current` = `advertisement` clustered on `idx_advertisement_created_at` (insertion order, as real data) with the present index; `P3-ordered-covering` = the same with `idx_taxon_assignment_cover (taxon_id, entity_type, entity_id)` in place of `idx_taxon_assignment_taxon_id`. Five rounds each, medians of planning plus execution, in ms. Columns: ids = matching entities; A today = two lookups + page + count; A1 = one lookup + page + count; F = `EXISTS` page + count; J = `JOIN` of the distinct ids page + count.

| Scenario | Phase | ids | A today | A1 | F | J | F page | F count | J page | J count |
|---|---|---|---|---|---|---|---|---|---|---|
| S1 cat5 (31k) | P1-random-current | 31409 | 238 | 211 | 272 | 631 | 10.3 | 261.5 | 312.8 | 318.0 |
| S1 cat5 (31k) | P3-ordered-current | 31409 | 289 | 263 | 255 | 594 | 7.1 | 247.8 | 298.9 | 295.6 |
| S1 cat5 (31k) | P3-ordered-covering | 31409 | 281 | 273 | 273 | 612 | 3.5 | 269.5 | 315.5 | 296.8 |
| S1a cat50 (6.3k) | P1-random-current | 6308 | 86 | 78 | 354 | 62 | 262.5 | 91.8 | 32.3 | 29.8 |
| S1a cat50 (6.3k) | P3-ordered-current | 6308 | 117 | 108 | 262 | 70 | 165.5 | 96.9 | 40.6 | 29.1 |
| S1a cat50 (6.3k) | P3-ordered-covering | 6308 | 95 | 93 | 35 | 54 | 9.0 | 26.1 | 28.3 | 25.8 |
| S1b cat2 (66k) | P1-random-current | 65907 | 509 | 396 | 302 | 740 | 5.0 | 297.1 | 369.2 | 371.0 |
| S1b cat2 (66k) | P3-ordered-current | 65907 | 486 | 389 | 288 | 735 | 3.8 | 284.4 | 371.8 | 363.1 |
| S1b cat2 (66k) | P3-ordered-covering | 65907 | 345 | 327 | 257 | 653 | 2.9 | 253.8 | 331.1 | 322.0 |
| S2 rare cat150 (3k) | P1-random-current | 2978 | 82 | 78 | 482 | 29 | 432.7 | 49.6 | 14.9 | 14.0 |
| S2 rare cat150 (3k) | P3-ordered-current | 2978 | 68 | 64 | 318 | 32 | 266.6 | 51.4 | 16.5 | 15.0 |
| S2 rare cat150 (3k) | P3-ordered-covering | 2978 | 48 | 47 | 127 | 28 | 114.6 | 12.7 | 14.0 | 13.7 |
| S3 cat1 (241k) | P1-random-current | 241645 | 993 | 786 | 376 | 1733 | 2.3 | 373.3 | 994.3 | 738.3 |
| S3 cat1 (241k) | P3-ordered-current | 241645 | 1088 | 877 | 383 | 1694 | 1.6 | 381.6 | 966.5 | 727.4 |
| S3 cat1 (241k) | P3-ordered-covering | 241645 | 838 | 747 | 336 | 1560 | 1.4 | 334.9 | 897.9 | 662.4 |
| S4 cat5 AND city201 | P1-random-current | 3094 | 315 | 188 | 336 | 838 | 142.3 | 193.2 | 416.9 | 421.6 |
| S4 cat5 AND city201 | P3-ordered-current | 3094 | 325 | 195 | 296 | 876 | 109.7 | 186.7 | 438.2 | 437.4 |
| S4 cat5 AND city201 | P3-ordered-covering | 3094 | 163 | 108 | 147 | 582 | 63.0 | 84.1 | 285.6 | 296.3 |
| S5 cat5 OFFSET 1000 | P1-random-current | 31409 | 447 | 378 | 595 | 642 | 322.6 | 272.2 | 331.7 | 310.1 |
| S5 cat5 OFFSET 1000 | P3-ordered-current | 31409 | 422 | 353 | 458 | 610 | 205.1 | 252.9 | 312.3 | 298.0 |
| S5 cat5 OFFSET 1000 | P3-ordered-covering | 31409 | 293 | 285 | 346 | 595 | 102.8 | 243.3 | 300.6 | 294.8 |
| S6 rare cat150 AND city250 | P1-random-current | 22 | 75 | 38 | 55 | 28 | 38.0 | 16.5 | 13.9 | 14.2 |
| S6 rare cat150 AND city250 | P3-ordered-current | 22 | 73 | 37 | 55 | 29 | 36.0 | 18.9 | 14.6 | 14.4 |
| S6 rare cat150 AND city250 | P3-ordered-covering | 22 | 10 | 6 | 10 | 10 | 4.7 | 5.0 | 5.1 | 5.0 |
| S7 three cats 5,50,150 | P1-random-current | 40564 | 379 | 296 | 276 | 642 | 8.3 | 268.2 | 324.9 | 317.1 |
| S7 three cats 5,50,150 | P3-ordered-current | 40564 | 409 | 331 | 279 | 632 | 6.2 | 272.5 | 317.3 | 314.9 |
| S7 three cats 5,50,150 | P3-ordered-covering | 40564 | 295 | 282 | 249 | 577 | 4.5 | 244.5 | 286.8 | 290.6 |

Usage-count query (`SELECT taxon_id, COUNT(*) ... WHERE taxon_id = ANY(all 300) GROUP BY taxon_id`, both index states an index-only scan of 2,581,390 rows), five runs each: present index 410, 395, 365, 373, 383 ms (median 383); covering index 602, 466, 474, 478, 468 ms (median 474), +24%.

What the numbers also say: the covering index lowers the page of a selective filter by 7-30 times (category 50: 165.5 → 9.0 ms; category 150: 266.6 → 114.6 ms) because the backward walk of `idx_advertisement_created_at` probes `taxon_assignment` once per scanned row and the covering index makes each probe an index-only lookup; clustering `created_at` alone (P1 against P3-ordered-current) moves the numbers by 10-50%, so the random-order data set of the first pass was not unfair to F by much on 18. The scratch scripts (a copy of the first pass's functions with the `JOIN` form added, the five-round runner and the median script) were kept in the session scratch folder only.

### Step F0 — Record the decisions

- **Executor:** sonnet, through `/record-decision` only. **Depends on:** decision 7. **Files:** `taxon-spring-boot-starter/DECISIONS.md`, `platform-commons/DECISIONS.md` and `.claude/nav/adr-index.md` (regenerated by the command).
- **Change:** exactly the two records drafted above (with the user's changes); the superseded statuses set; the index regenerated.
- **Verification:** `bash .claude/nav/scripts/generate-adr-index.sh` ran; the index shows the new rows and the Superseded status.

### Step F1 — The two contract types

- **Executor:** sonnet. **Depends on:** F0. **Files (new):** `platform-commons/src/main/java/org/ost/platform/core/model/SqlColumn.java`, `SqlPredicate.java`, and `platform-commons/src/test/java/org/ost/platform/core/model/SqlColumnTest.java`, `SqlPredicateTest.java`. **Input:** `EntityRef.java` in the same package (style of a plain record); `platform-commons/pom.xml` (no Spring JDBC, no query-lib).
- **`SqlColumn`:** compact constructor rejects (`IllegalArgumentException`, the message names the bad value) an `alias` or `column` not matching `^[a-z][a-z0-9_]{0,62}$`; `sql()` returns `alias + "." + column`; one-line Javadoc.
- **`SqlPredicate`:** compact constructor copies `params` with `Map.copyOf`; rejects a `sql` containing `;`, `--` or `/*`; rejects a parameter name not matching `^_[a-z][A-Za-z0-9]*$`; rejects a parameter name that does not occur in `sql` as `":" + name`. `asAndClause()` returns `" AND (" + sql + ")"`. No other methods, no `empty()` (absence is `Optional.empty()` at the Port), no binding method.
- **Tests (JUnit 5, AssertJ):** `SqlColumn` accepts `a`/`id`, `pp`/`actor_id`; rejects `A`, `_ta`, `a.id`, `a id`, `a;`, `"a"`, `a--`, empty, 64 characters. `SqlPredicate` rejects `;`, `--`, `/*`, a name without `_`, a name absent from the text; `asAndClause()` wraps in parentheses and a text with `OR` keeps the surrounding `AND` filter meaning (the check is in the consumer tests of F3 and F4, which run it against PostgreSQL); `params()` is immutable.
- **Verification:** `bash scripts/build-and-test.sh --unit --sandbox` green.

### Step F2 — Taxon builds the predicate; the id-set contract goes

- **Executor:** sonnet. **Depends on:** F1, F-M. **Files:** `platform-commons/.../taxon/spi/TaxonPort.java`, `taxon-spring-boot-starter/.../services/DefaultTaxonPort.java`, `.../services/TaxonAssignmentService.java`, `.../repository/TaxonAssignmentRepository.java`; tests under `integration-tests/src/test/java/org/ost/integrationtests/level1/taxon/`: `TaxonPortResolveCategoryAndCityFilterTest.java` (a mock test of the default method, six cases) is deleted and replaced by `TaxonCategoryAndCityPredicateTest.java`; the case `findEntityIdsByTaxonIds_returnsEntitiesTaggedWithAnyOfTheGiven...` of `TaxonAssignmentRepositoryTest.java` is replaced by the predicate cases. `git grep` shows no other caller of the removed methods outside tests and documents.
- **`TaxonPort`:** remove `findEntityIdsWithAnyTaxon`, the default `resolveCategoryAndCityFilter` and its private `resolveTaxonIdFilter`; add `Optional<SqlPredicate> categoryAndCityPredicate(@NonNull EntityType entityType, @NonNull SqlColumn entityId, Set<Long> categoryIds, Long cityTaxonId)` with a one-line Javadoc (empty when neither filter is requested; a present but empty `categoryIds` matches nothing).
- **`TaxonAssignmentRepository`:** replace `findEntityIdsByTaxonIds` by a method that builds, without running anything, the predicate text inline: category part `EXISTS (SELECT 1 FROM taxon_assignment _tac WHERE _tac.entity_type = :_taxonEntityType AND _tac.entity_id = <entityId.sql()> AND _tac.taxon_id = ANY(:_taxonCategoryIds))`, city part the same with alias `_tay` and `_tay.taxon_id = :_taxonCityId`, joined with ` AND ` when both are requested (the final text follows step F-M). Values: `entityType.name()`, `categoryIds.toArray(new Long[0])`, `cityTaxonId`. Every column of `taxon_assignment` is qualified with the `_ta*` alias; no value is concatenated into the text.
- **`TaxonAssignmentService`, `DefaultTaxonPort`:** pure delegation.
- **Test `TaxonCategoryAndCityPredicateTest` (Testcontainers, the base class of the test it replaces):** the six cases of the old test (neither, only category, only city, both overlapping, both disjoint, category matching nothing), each run against a consumer the test owns (`SELECT c.id FROM (VALUES (...)) AS c(id) WHERE TRUE` + `predicate.asAndClause()` + `params.addValues(predicate.params())` with `new SqlColumn("c", "id")`); plus: the text contains none of the ids passed in; an empty `categoryIds` returns no row; the predicate appended after a consumer filter that ends in `OR` still returns only the rows both parts allow.

### Step F3 — Advertisement splices the predicate

- **Executor:** sonnet. **Depends on:** F2. **Files:** `advertisement-spring-boot-starter/.../repository/AdvertisementRepository.java`, `.../services/AdvertisementService.java`, `integration-tests/.../level1/advertisement/AdvertisementRepositoryTest.java` (the two `allowedIds` cases), `AdvertisementServiceCategoryFilterTest.java` (its Javadoc names the removed method and the deleted default-method test).
- **Repository:** `public static final SqlColumn ID = new SqlColumn("a", "id");`; `findByFilter(filter, pageable, SqlPredicate taxonPredicate)` and `countByFilter(filter, SqlPredicate taxonPredicate)`, the predicate nullable (null = no restriction); `buildIdClause` becomes `taxonPredicate == null ? "" : bound clause` in the same position of the `formatted(...)` call, where the bound clause is `params.addValues(taxonPredicate.params())` followed by `taxonPredicate.asAndClause()`; the `allowedIds` parameter and its comment go.
- **Service:** `resolveTaxonPredicate(filter)` returns `taxonPortFactory.findIfAvailable().flatMap(p -> p.categoryAndCityPredicate(EntityType.ADVERTISEMENT, AdvertisementRepository.ID, filter.getCategoryIds(), filter.getCityTaxonId()))`; `getFiltered` and `count` pass `.orElse(null)`; the two early returns on an empty set go.
- **Tests:** the `allowedIds` repository cases become predicate cases with a test-built `new SqlPredicate("a.id = ANY(:_ids)", Map.of("_ids", new Long[]{...}))`, count and page both; the service test (Mockito): the predicate is passed through when present, null when Taxon is absent, null when no filter is requested; the two "returns empty without querying" cases go.

### Step F4 — Provider Profile splices the predicate

- **Executor:** sonnet. **Depends on:** F2 (independent of F3). **Files:** `provider-profile-spring-boot-starter/.../repository/ProviderProfileRepository.java`, `.../services/ProviderProfileService.java`, `integration-tests/.../level1/providerprofile/ProviderProfileRepositoryTest.java`, `ProviderProfileServiceTest.java` (five mock cases of the taxon filter).
- **Behavior:** as F3 with `new SqlColumn("pp", "id")` and `EntityType.PROVIDER_PROFILE`. `ProviderProfileScope` stays (promotion ids and pins are bounded; it lives in `platform-commons`). The Java intersection of `onlyIds` with the taxon ids in `withTaxonFilter` goes: `buildWhere` keeps `pp.id = ANY(:allowedIds)` for `scope.onlyIds()` and, independently, adds the taxon predicate, both ANDed in SQL (`findByFilter` and `countByFilter` gain the predicate parameter).
- **Tests:** the three `allowedIds` repository cases stay (they now cover the promotion scope only); new cases: predicate alone; predicate AND `onlyIds` together (the promoted-and-categorised list, the one path whose semantics moved from Java to SQL), page and count both; the service test passes the predicate through, null when Taxon is absent or no filter is set.
- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox` green; `ArchitectureRulesTest` green; then the Playwright `e2e --full --ux` scenario (the category and city filters are UI-visible).

### Step F5 — Covering index (only if F-M measured the gain of the defaults)

- **Executor:** haiku. **Depends on:** F2. **Change:** one Taxon-local Liquibase changeset in the Taxon changelog creating the index on `taxon_assignment (taxon_id, entity_type, entity_id)` (replacing or joining the `taxon_id` index per decision 3), with a `remarks` attribute stating why it exists (the ERD page of the map reads it). With the predicate the index serves the count's hash semi-join (an index-only scan instead of a bitmap heap scan).
- **Verification:** the integration test suite green; `EXPLAIN` of the predicate on seeded data shows the index-only scan.

### Step F6 — ArchUnit guard (only if decision 4 says so)

- **Executor:** sonnet. **Depends on:** F4. **File:** `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`. **Change:** one `@ArchTest` rule `sql_predicates_are_built_only_in_starter_repositories`: no class outside a `..repository..` package of a starter calls a constructor of `org.ost.platform.core.model.SqlPredicate`, in the same custom `ArchCondition` style as the existing rule `starters_must_route_taxon_assignment_writes_through_orchestrator`; check, do not assume, that test classes are outside the import scope.
- **Verification:** the test green; temporarily constructing a `SqlPredicate` in a service makes it fail (revert the probe).

### Step F7 — Documentation sync

- **Executor:** sonnet, through `/sync-docs` (diff mode), then `/check-architecture-map`. **Depends on:** F4 (F5, F6 when done). The exact list, found by searching for the removed names on 2026-11-13:
  - rules files: `.claude/rules/advertisement-spring-boot-starter.md` (the bullet about `resolveTaxonIdFilter()` and `findEntityIdsWithAnyTaxon()`), `.claude/rules/provider-profile-spring-boot-starter.md` (the sentence about the filters via `findEntityIdsWithAnyTaxon()`), `.claude/rules/marketplace-orchestrator.md` (the line naming `AdvertisementService.resolveTaxonIdFilter()`), `.claude/rules/platform-commons.md` (the paragraph "Second, separate narrow exception — for `*.spi` interfaces", which names `resolveCategoryAndCityFilter`: removed when no default method with logic remains, otherwise its example is replaced); editing it moves the `arch-embed` markers, so `docs/architecture/data/arch-embed-index.md` is regenerated by the map generator;
  - module READMEs: `advertisement-spring-boot-starter/README.md` (the sentence about the filter), `provider-profile-spring-boot-starter/README.md` (three places), `platform-commons/README.md` (the `core.model` class list gains `SqlColumn` and `SqlPredicate`); the root `CLAUDE.md` names no `core.model` class and needs no change;
  - decision files: `taxon-spring-boot-starter/DECISIONS.md` and `platform-commons/DECISIONS.md` through step F0; `marketplace-app/DECISIONS.md` names the old flow somewhere and is read, then annotated only if it states the id-set flow as current; `advertisement-spring-boot-starter/DECISIONS.md` and `provider-profile-spring-boot-starter/DECISIONS.md` are generated pointer files and are never edited by hand;
  - code documentation (`module-doc-standards`): Javadoc of `SqlColumn`, `SqlPredicate` and the new `TaxonPort` method, the comments of the two repositories' predicate splice, the removed `Long[]` comment of `AdvertisementRepository.buildIdClause`; the `remarks` of the F5 changeset;
  - generated: `bash .claude/nav/scripts/generate-adr-index.sh` and `bash docs/architecture/scripts/generate-architecture-model.sh` once at the end; `docs/api-collections` is unchanged (the REST contract does not change).

### Step F8 — Rare-category page (only on a production number)

- **Executor:** opus. **Depends on:** F4, F-M and a latency target from decision 5. **Goal:** the page of a rare category within the target, without changing the consumer contract; the change stays inside Taxon's predicate builder or a Taxon-local index; measured before and after on data whose `created_at` follows insertion order. **Open:** whether Taxon switches the predicate shape for selective taxons and how it learns selectivity (the usage counts it already keeps, or a threshold).

### Step F9 — Hide the category and city filters without Taxon (decision 10)

- **Executor:** sonnet. **Depends on:** nothing in F1-F8 (independent; it runs after F4 only to keep the Playwright gate single). **Files:** `marketplace-app/.../tabs/advertisements/query/AdvertisementQueryBlock.java`, `.../tabs/providers/query/ProviderProfileQueryBlock.java`, `.../tabs/advertisements/overlay/modes/AdvertisementFormOverlayModeHandler.java`, `.../header/account/ProviderProfileFormOverlayModeHandler.java`; new test beside `TimelineViewTest` (it already unit-tests a view with a mocked `isAvailable()`).
- **Change:** the category row and the city row (the field creation and its `filterRow` call, or the form field and its binding) are built only when `taxonCatalogService.isAvailable()` is true; the filter values stay `null` and the statements already work without them.
- **Tests:** a component test per block with `isAvailable()` false (no category or city field present) and true (both present); the Playwright filter specs (`06-seed-filter-sort-pagination.spec.js`) are the proof for the present case.

## How it is covered by tests

| Layer | What proves it | Status |
|---|---|---|
| Types | `SqlColumnTest`, `SqlPredicateTest` (platform-commons unit tests): every refused identifier, text and parameter name; immutability | new, F1 |
| Taxon builds the right text | `TaxonCategoryAndCityPredicateTest` on PostgreSQL against a test-owned `VALUES` table: six filter cases, no id in the text, empty category set matches nothing, `OR`-ending consumer filter keeps its meaning | replaces the deleted default-method test, F2 |
| Consumers splice it correctly | `AdvertisementRepositoryTest`, `ProviderProfileRepositoryTest`: page and count with a predicate, with and without the promotion `onlyIds` | changed, F3 and F4 |
| Consumers resolve it | `AdvertisementServiceCategoryFilterTest`, `ProviderProfileServiceTest` (Mockito): predicate passed through, null without Taxon or filter | changed, F3 and F4 |
| The real stack, unchanged | `AdvertisementPaginationScenarioTest` and `ProviderProfilePaginationScenarioTest` (level 3, REST, real Postgres) already filter by category and by city; they must pass without a line changed, which is the strongest proof that behaviour did not move | unchanged, regression net |
| Combined filters over REST | category AND city on both lists; promoted AND category on provider profiles. Checked 2026-11-13: none exists (`PromotedProviderProfileScenarioTest` only sends `promoted=true`, the two pagination tests send one filter each) | add, F3 and F4 |
| Taxon absent | a new test next to `OptionalStarterRestAbsenceTest` (level 2, the same context builder; that test sends no query parameter, so it never reaches the filter): with the Taxon starter left out, `GET /api/advertisements` and `GET /api/provider-profiles` with `categoryIds` and `cityTaxonId` answer 200 and are unfiltered. The database is one shared container whose `taxon_assignment` table already exists from earlier contexts, so the table is not missing by itself: the test renames it for the duration of the calls and renames it back in a `finally` (test classes run one at a time, no parallel setting), which proves that no statement names it | add, F4 |
| UI end to end | Playwright `06-seed-filter-sort-pagination.spec.js` (category 12 of 60, city 20 of 60) and `04-provider-profile-flow.spec.js`, run as `e2e --full --ux` | unchanged, final gate |
| Architecture | the F6 rule with its temporary probe | optional, F6 |
| The tests catch the known failure modes | before closing, mutate once each: drop the parentheses of `asAndClause()` (the `OR` case must fail), put an id into the text (the "no id in text" test must fail), return the whole set for an empty category set (the matches-nothing case must fail); revert each | F4 verification |

## Verification of the whole

1. `bash scripts/build-and-test.sh --unit --integration --sandbox` green.
2. Playwright `e2e --full --ux` green (the category and city filters, both list pages).
3. `ArchitectureRulesTest` green; `git grep -n "findEntityIdsWithAnyTaxon\|resolveCategoryAndCityFilter\|allowedIds" -- '*.java'` finds only the promotion `allowedIds` binding of `ProviderProfileRepository` and its tests.
4. Pluggability: the Taxon-absent test (the table renamed during the calls) is green: lists answer 200 and unfiltered with Taxon removed and no statement names `taxon_assignment`.
5. Documentation: the list of step F7 is done, `/check-architecture-map` reports the map up to date.
6. Before closing, the user re-runs the measurement on production-sized data if decision 5 set a latency target.

Estimate without F-M and F8: about 1.5 days, 330-520k tokens (F0 30-50k, F1 30-50k, F2 80-120k, F3 and F4 50-80k each, F5 10-20k, F6 30-50k, F7 50-70k; the level 3 additions about 20-30k more); F-M 0.5 day, 100-150k tokens (opus); F8 0.5-1 day, 150-250k (opus).

## Related

- [improvement-249](improvement-249-pluggable-modules-opaque-cross-module-references-research.md): the research this task comes from; its two research folders (`backlog/research/pluggable-modules-opaque-refs/`, `backlog/research/pluggable-modules-query-fragment-port/`) hold the measurements, the SQL scripts and the sources; they stay as history and step F-M reads them from there.
- Done elsewhere: the test that starts the Spring context with each optional starter absent and the declaration of every `ComponentFactory` a module holds ([improvement-252](improvement-252-application-starts-without-each-optional-starter.md)); the Taxon case of its REST variant is the pluggability proof of this task.
- Still open and independent: the orphan-assignment cleanup is improvement-133 item 25; the double lookup per page and the exact count are improvement-003 item K (the double lookup disappears with this task); the duplicated empty-set guard is improvement-133 item 23 (this task removes it as a side effect). The filter rows shown without Taxon (decision 10) are not tracked anywhere: item 11 of improvement-133 is the "Add" button of the reference-data views, a different case.
- The decisions of `taxon-spring-boot-starter`, `marketplace-app` and `platform-commons` named above (find them through `.claude/nav/adr-index.md`); this task changes the first and the last in step F0 and leaves the middle one as it is.

## Operational notes
- token_cost_review: 87824 (the deep-review orchestrator; the harness does not itemise its lenses)
- token_cost_research: 321267 (second research pass only; the first pass was not recorded)
- token_cost_verification: 627658 (the six read-only Javadoc audits of /sync-docs)
- review_signal_ratio: 0 / 1 (the deep-review raised one candidate, a medium-confidence DRY item left to the user; nothing was confirmed)
- context_loading_task_type: Cross-module feature
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: a researched plan executed in phases, each with its own plan and approval
- flows_chosen: Haiku agents for exactly specified edits, Sonnet agents for tests that need design, /record-decision, /sync-docs, /check-architecture-map, /deep-review, /ci
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 19 (13 implementation, correction and apply dispatches, 6 read-only audits of /sync-docs)
- haiku_decomposition_rework_needed: 1 / 19 (the ArchUnit rule of the first F6 dispatch could not fail and was corrected by a second dispatch)
- adrs_referenced: ADR-001 (taxon-spring-boot-starter), ADR-034 (marketplace-app), ADR-032 (platform-commons), ADR-006 (taxon-spring-boot-starter), ADR-036 (platform-commons)

### Agent calls
- Deep research, second pass (query-fragment port) | subagent_type=deep-researcher | tokens=321267 | tool_uses=135 | duration_s=3019 | mode=background | batch=solo
- P2-0 corrections of F1/F2 (haiku) | subagent_type=general-purpose | tokens=93222 | tool_uses=10 | duration_s=68 | mode=background | batch=p2-0
- P2-1 F3 Advertisement (haiku) | subagent_type=general-purpose | tokens=119052 | tool_uses=15 | duration_s=172 | mode=background | batch=p2-1-2
- P2-2 F4 Provider Profile (haiku) | subagent_type=general-purpose | tokens=147612 | tool_uses=33 | duration_s=249 | mode=background | batch=p2-1-2
- P2-3 level 3 and Taxon-absent tests (sonnet) | subagent_type=general-purpose | tokens=103423 | tool_uses=12 | duration_s=84 | mode=background | batch=solo
- P3-1 F5 covering index (haiku) | subagent_type=general-purpose | tokens=62907 | tool_uses=5 | duration_s=26 | mode=background | batch=p3
- P3-2 F6 ArchUnit rule (haiku) | subagent_type=general-purpose | tokens=81733 | tool_uses=5 | duration_s=51 | mode=background | batch=p3
- P3-2 correction of the rule polarity (haiku) | subagent_type=general-purpose | tokens=79791 | tool_uses=4 | duration_s=27 | mode=background | batch=solo
- P4-1 F9 hide taxon fields (haiku) | subagent_type=general-purpose | tokens=112304 | tool_uses=14 | duration_s=101 | mode=background | batch=p4
- P4-2 component tests of the query blocks (sonnet) | subagent_type=general-purpose | tokens=77800 | tool_uses=9 | duration_s=84 | mode=background | batch=p4
- P5-1 documentation edits (haiku) | subagent_type=general-purpose | tokens=112617 | tool_uses=23 | duration_s=197 | mode=background | batch=p5
- P5-1 decision record corrections (haiku) | subagent_type=general-purpose | tokens=79757 | tool_uses=8 | duration_s=38 | mode=background | batch=p5
- sync-docs audit, advertisement starter (haiku) | subagent_type=general-purpose | tokens=95138 | tool_uses=8 | duration_s=89 | mode=background | batch=sync-audit
- sync-docs audit, provider profile starter (haiku) | subagent_type=general-purpose | tokens=100019 | tool_uses=8 | duration_s=90 | mode=background | batch=sync-audit
- sync-docs audit, integration tests (haiku) | subagent_type=general-purpose | tokens=85945 | tool_uses=9 | duration_s=87 | mode=background | batch=sync-audit
- sync-docs audit, taxon starter (haiku) | subagent_type=general-purpose | tokens=99158 | tool_uses=10 | duration_s=120 | mode=background | batch=sync-audit
- sync-docs audit, platform-commons (haiku) | subagent_type=general-purpose | tokens=118402 | tool_uses=13 | duration_s=144 | mode=background | batch=sync-audit
- sync-docs audit, marketplace-app (haiku) | subagent_type=general-purpose | tokens=128996 | tool_uses=11 | duration_s=142 | mode=background | batch=sync-audit
- sync-docs apply, TaxonPort Javadoc (haiku) | subagent_type=general-purpose | tokens=73658 | tool_uses=11 | duration_s=45 | mode=background | batch=sync-apply
- sync-docs apply, integration test comments (haiku) | subagent_type=general-purpose | tokens=90183 | tool_uses=10 | duration_s=65 | mode=background | batch=sync-apply
- sync-docs apply, marketplace-app comments (haiku) | subagent_type=general-purpose | tokens=108882 | tool_uses=12 | duration_s=121 | mode=background | batch=sync-apply
- sync-docs apply, backlog notes (haiku) | subagent_type=general-purpose | tokens=97327 | tool_uses=6 | duration_s=40 | mode=background | batch=sync-apply
- Code review (deep-review-orchestrator) | subagent_type=deep-review-orchestrator | tokens=87824 | tool_uses=20 | duration_s=371 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --integration --sandbox (phase 2 gate) | duration_s=573 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration-test "TaxonCategoryAndCityPredicateTest,AdvertisementRepositoryTest" (three mutations, expected failures) | duration_s=173 | mode=background | result=fail
- scripts/build-and-test.sh --unit-test ArchitectureRulesTest --no-integration (probe, rule could not fail) | duration_s=190 | mode=background | result=pass
- scripts/build-and-test.sh --unit-test ArchitectureRulesTest --no-integration (probe, corrected rule) | duration_s=173 | mode=background | result=fail
- scripts/build-and-test.sh --unit --integration --sandbox (phase 3 gate) | duration_s=604 | mode=background | result=pass
- scripts/build-and-test.sh --unit --no-integration (phase 4 gate) | duration_s=280 | mode=background | result=pass
- scripts/build-and-test.sh --unit-test marketplace-app --no-integration (mutation of the query blocks, expected failures) | duration_s=n/a | mode=background | result=fail
- scripts/deploy-and-run.sh --reset --dev-tools | duration_s=163 | mode=background | result=pass
- scripts/playwright.sh e2e --full --ux (69 passed) | duration_s=681 | mode=background | result=pass
- scripts/ci.sh --foreground --reset-e2e-stack | duration_s=1616 | mode=background | result=pass

### Commands used
- /record-decision | uses=1 | failed=0 | note=wrote both decision records after the PostgreSQL 18 measurement
- /sync-docs | uses=1 | failed=0 | note=six read-only audits found 14 stale or missing comments, three of them real inaccuracies in an SPI Javadoc; one yes covered all corrections
- /check-architecture-map | uses=1 | failed=0 | note=up to date after one generator run
- /deep-review | uses=1 | failed=0 | note=no confirmed finding, one DRY item left to the user
- /ci | uses=1 | failed=0 | note=full run succeeded in 27 minutes with the e2e stack reset
