# improvement-250: A Port returns a SQL predicate for cross-module filters that can match an unbounded number of rows (Taxon first)

**Type:** improvement — prepared plan and decision-record texts; nothing is implemented until the user starts it
**Module:** `platform-commons` (`core.model`, `taxon.spi.TaxonPort`), `taxon-spring-boot-starter`, `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`, `integration-tests`, `marketplace-app` (`ArchitectureRulesTest`), the decision files of `taxon-spring-boot-starter`, `marketplace-app` and `platform-commons`
**Priority:** 🔵 larger tech debt: no live bug at today's volume; the user chose the target design and parked the execution
**When:** independent, no blockers; execution waits for the user's go and for decisions 2-8 below; step F0 (decision records) runs first, through `/record-decision`

## Current state

Checked against the code on 2026-11-09:

- `taxon_assignment` belongs to `taxon-spring-boot-starter`. A list filtered by category or city runs three statements per page, in this order: `TaxonAssignmentRepository.findEntityIdsByTaxonIds` (`SELECT DISTINCT entity_id FROM taxon_assignment WHERE entity_type = :entityType AND taxon_id = ANY(:taxonIds)`), then `AdvertisementRepository.findByFilter` and `.countByFilter` with `AND a.id = ANY(:allowedIds)`. The id set travels database → Java → database. `AdvertisementService.getFiltered()` and `.count()` each resolve the filter, so the Taxon lookup runs twice per page; `ProviderProfileService` does the same.
- No cross-starter foreign key, view or function exists in any changelog; every starter runs its own Liquibase. Opaque ids (`Long`, `EntityRef`) are already the only cross-module reference format.
- The Accepted decision of `taxon-spring-boot-starter` ("Filter resolves through `Set<Long>`, not SQL JOIN") fixes the id-set flow and names its own trigger to revisit (a category routinely resolving to more than 10,000 ids); no `WARN` for it exists.
- `marketplace-app`'s "No raw cross-starter SQL joins" decision forbids a consumer from hard-coding another starter's table or column names; its reason is a rename breaking the consumer at runtime with no compile-time warning.
- `platform-commons`'s decision about `TaxonPort.resolveCategoryAndCityFilter` allows one default method on a Port; its governance rule lists "feature-specific models or persistence logic" as NOT ALLOWED.
- No Taxon hierarchy (parent/child) exists in the Taxon code, so a filter value is a flat set of taxon ids.
- Two research passes are done (folders `backlog/research/pluggable-modules-opaque-refs/` and `backlog/research/pluggable-modules-query-fragment-port/`, measurements and SQL scripts inside); the first pass is closed by this task's research, see [improvement-249](../completed/tasks/improvement-249-pluggable-modules-opaque-cross-module-references-research.md).

## Why change

The user wants one general rule for a filter over another module's data, with these goals together: modules do not know each other's classes, tables or repositories; any starter can be removed from `pom.xml` and nothing breaks or is left behind in the remaining modules; filtering, sorting and paging run in PostgreSQL, not on id sets carried through Java; minimal complexity; each module owns its data.

Today the third goal fails: a filter whose result can be large carries its id set through Java, twice per page. At 241,000 matching advertisements one list page costs about 0.9-1.1 s on the server alone (measured), before the JDBC transfer of the ids. The same shape appears for any future filter by another module's data that can match many rows (for example by a user attribute).

## Expected benefit

- No id set of an unbounded size crosses Java; the list page and its count are one PostgreSQL statement each, in one snapshot.
- Removing Taxon leaves nothing in Advertisement or Provider Profile (no Taxon table, column or id), the statements simply carry no predicate.
- Measured on 1,000,000 advertisements, median of five rounds, per list page (page + count): 241k matching 334 ms against 907 ms today (today's JDBC transfer not included), 66k 228 against 323 ms, 31k 219 against 231 ms.
- Cost: selective filters get slower (3k matching 104 against 30 ms; 6.3k 82 against 52 ms). The first research pass's local relation table (variant B) measured the same speed as the predicate, so owning the table gives no speed.
- A written rule instead of case-by-case decisions: when an id set is enough and when a predicate is needed.

## Approach

### The rule (chosen by the user)

Predicate where an id set cannot be avoided, the normal path everywhere else:

1. A reference to another module's entity is an opaque id (`Long`, or `EntityRef` where one table points at several entity types). No new id type, no registry.
2. A filter over another module's data whose result is bounded (a page of ids, the promotion placements, the profiles qualifying for a recount, a user's own selection) stays an id set or a bulk lookup through the Port.
3. A filter over another module's data whose result can match an unbounded number of rows is a **predicate built by the owning module**: the Port returns a typed `SqlPredicate`, the consumer splices it into its own page and count statements. Never for reading the other module's columns, never as a join, view or function written by the consumer.

Today rule 3 has exactly one user: the category/city filter of Taxon.

### The contract (typed, never a free string)

Two plain records in `platform-commons` `org.ost.platform.core.model`, JDK and Lombok only:

```java
public record SqlColumn(@NonNull String alias, @NonNull String column) { public String sql(); }
public record SqlPredicate(@NonNull String sql, @NonNull Map<String, Object> params) {
    public String appendTo(@NonNull Predicate<String> isBound, @NonNull BiConsumer<String, Object> bind);
}
// TaxonPort replaces findEntityIdsWithAnyTaxon and the default resolveCategoryAndCityFilter with:
Optional<SqlPredicate> categoryAndCityPredicate(@NonNull EntityType entityType, @NonNull SqlColumn entityId,
                                                Set<Long> categoryIds, Long cityTaxonId);
```

Safety rules built into the two constructors and `appendTo` (each reproduced as a silent wrong result or an injection path with a raw `String` + `Map` + `String targetIdColumn` contract, see Related):

- values travel only as named bind parameters; a test asserts a bound id never appears in the predicate text;
- identifiers only through `SqlColumn`, each part matching `^[a-z][a-z0-9_]{0,62}$` (an allow-list by construction; no quote, dot, space, semicolon or comment can enter);
- provider aliases and parameter names start with `_`, consumer aliases cannot, so an alias collision cannot shadow a column silently;
- `appendTo` wraps the text in parentheses after ` AND ` (a predicate with `OR` appended bare lost the consumer's own filter: 0 rows expected, 3 returned);
- `appendTo` throws on a duplicate parameter name (Spring's `MapSqlParameterSource.addValue` silently overwrites);
- the predicate text is refused when it contains `;`, `--` or `/*`;
- only first-party starter repositories may construct a `SqlPredicate` (ArchUnit rule, decision 4).

How flexible the column is: any alias and any key column of the consumer, several predicates in one statement (category AND city, one predicate per `UNION` branch), `WHERE`, `JOIN ... ON`, a boolean `ORDER BY` term, inside count/page/subquery wrappers (the wrapper's alias is passed); a composite key (type column + id column) by a second descriptor added when its first consumer needs it. It stops at: an expression instead of a column (`COALESCE`, a cast, a function), quoted or schema-qualified names, a column chosen by a request, consumer SQL inside the provider's predicate, the provider's columns in the consumer's `SELECT`, aggregates or a join returning provider data.

### Options compared (what the research measured or read)

| Option | Result | Verdict |
|---|---|---|
| A+ — keep the id set, add the covering index and one lookup per page | no recorded decision changes; selective filters stay fast; the id set still crosses Java | the fallback if the user withdraws decision 1 |
| **F — the predicate (this task)** | meets all five goals; big filters 2-3 times faster, selective ones slower | chosen |
| B — relation table owned by Advertisement / Provider Profile | the same speed as F; leaves Taxon ids in the consumer after Taxon is removed | rejected by the user as not real decoupling |
| Database function called by the consumer | not measured; hides the table but adds a database object and a planner risk (a non-inlined function hides its row estimate); everything it gives, the predicate gives | not taken; may be measured in step F-M |
| `a.id = ANY(ARRAY(SELECT ...))` shape | 22 ms for a rare category, 2.4 s at 241k, a timeout with a city filter | only as a Taxon-internal shape for selective taxons (step F8) |

### Residual risks the plan accepts

- Same database, same schema and a role that may read `taxon_assignment` (a database split or per-module roles would end the approach: a `GRANT` at least).
- The consumer runs SQL text it did not write: a Taxon table change is seen only by a test against PostgreSQL (Taxon's own contract test runs the predicate against a `VALUES` table as the consumer).
- Selective filters get slower; the count of a big filter stays the most expensive part in every variant (a different count strategy belongs to improvement-003 item K).
- The measurements are one synthetic data set on one machine, ±30%, with `created_at` random per row (the worst case for the page walk); with `created_at` in insertion order a rare-category page fell from 217 to 90 ms.

### Decisions waiting for the user

1. Done by the user: the rule above (the predicate where an id set cannot be avoided, the normal path elsewhere).
2. Where the two types live: `platform-commons` `core.model` with a named exception to its "no persistence logic" governance rule (proposed, no new dependency); or query-lib (makes `platform-commons` depend on query-lib and its JDBC starter); or a new tiny library.
3. The covering index `(taxon_id, entity_type, entity_id)`: yes (proposed); replace the `taxon_id` index (proposed) or keep both. It makes the admin usage counts slower (247 → 415 ms measured on 2.6M rows).
4. The ArchUnit guard (only starter repositories build a predicate) and the rule "only first-party starters provide predicates": yes or no.
5. A latency target for one list page (page + count); it decides whether step F8 ever runs.
6. Annotate or supersede the "No raw cross-starter SQL joins" decision (proposed: annotate).
7. Names: `SqlPredicate` and `SqlColumn` (proposed) or `SqlFragment`.
8. The composite-key descriptor: later, when a first consumer needs it (proposed), or now.
9. Open from the first pass: what a category filter does when Taxon is absent (ignored today, covered by a test; for a REST caller it is a silent unfiltered list).

## Decision-record updates (drafts; written only through `/record-decision`, then regenerate the index)

**1. `taxon-spring-boot-starter`: new decision "Category and city filter is a Taxon-built SQL predicate the owner splices into its own statement"** — marks the Accepted "Filter resolves through `Set<Long>`, not SQL JOIN" as Superseded by it.

- Context: the id-set flow carries an id set of unbounded size through Java, twice per page (241k ids: 0.9-1.1 s per list page on the server, planning of the bound array about 140 ms per statement); the revisit trigger of the superseded decision (a category above 10k ids) is reached in the measurement set; the revisit options it named were a starter-owned JOIN helper or a pre-computed mapping; the predicate is the first.
- Decision: `TaxonPort.categoryAndCityPredicate(entityType, entityId, categoryIds, cityTaxonId)` returns `Optional<SqlPredicate>`; Taxon writes the whole `EXISTS` on `taxon_assignment` (aliases `_tac`, `_tay`, parameters `_taxon*`); the consumer passes its own `SqlColumn` and splices the result into its page and count statements; empty when neither filter is requested or Taxon is absent; a present but empty category set matches nothing. Promotion pins and placements stay id sets.
- Consequences: removing Taxon leaves no Taxon name in the consumer; a Taxon schema change is caught by Taxon's contract test run against PostgreSQL; the two early "empty set" returns and the Java intersection in the profile service go.
- Rejected: the relation table in the consumer (leaves Taxon ids behind), a database function (adds an object for no gain), keeping the id set (fails the filter-in-database goal at scale).

**2. `marketplace-app`: annotate (not supersede) "No raw cross-starter SQL joins — bulk-lookup port + service-level enrichment"** — add the rule text and keep the rest:

> A consumer never writes another starter's table or column names. A filtering predicate built by the owning starter and obtained through its Port as a `SqlPredicate` is not a raw cross-starter join: it is allowed for row restriction only (`WHERE`, or a boolean in `ORDER BY`), never to read the other starter's columns, and only where the result of an id-set lookup can be unbounded. Bounded results (a page of ids, a placement list, a user's selection) stay bulk lookups through the Port. Only first-party starters provide predicates.

This is also the one home of the general three-rule statement (opaque ids; bounded results as id sets; unbounded filters as predicates); the other two records point to it.

**3. `platform-commons`: new decision "`SqlColumn` and `SqlPredicate` — typed building blocks for a provider-built filter predicate"** — a named, bounded exception to "feature-specific models or persistence logic" in the governance list: two plain records, no Spring or JDBC import, validation in the constructors, no business logic; both types are used by every module that provides or splices a predicate (more than one consumer). Marks the decision about `TaxonPort.resolveCategoryAndCityFilter` as a default method Superseded (the method disappears; the default-method exception stays valid for its other uses).

**Not changed:** the query-lib API-freeze decision (query-lib is untouched); the rules about `Optional` parameters and sibling imports (the types live in `platform-commons`).

**`ArchitectureRulesTest`:** no existing rule breaks; one optional new rule in step F6.

## Execution plan

Steps F1-F4 are one change set (the contract is replaced, not added next to the old one): run them in order and build after F4. Common rules for every step: read `/app/CLAUDE.md`, `/app/.claude/rules.md` and the `.claude/rules/<module>.md` of every module touched before editing; `lombok.NonNull` on every non-null public parameter; no `Optional` parameter; comments one line or none, no task or ADR number; `git add` after each file change, never `git commit`. Executor models: sonnet F0-F4, F6, F7; haiku F5; opus F-M and F8.

### Step F-M — Measure the open shapes (before F2)

- **Executor:** opus. **Depends on:** nothing. **Gates:** the predicate text of F2 and the decision on F8.
- **Goal:** decide the exact text Taxon builds, by measurement: (a) `EXISTS` against a `JOIN` with `DISTINCT` (the JOIN may let the planner start from the small `taxon_assignment` side for a selective filter); (b) a database function against the inline `EXISTS` (only to confirm the verdict above); (c) the rare-category page with parallel workers disabled for the statement; (d) the shape chosen by the size of the taxon (`ANY(ARRAY(subquery))` for a selective taxon).
- **Input:** the data and SQL scripts in `backlog/research/pluggable-modules-query-fragment-port/measurements/` (the first pass's data script, the five-round median script, the clustered `created_at` phase). The two research folders stay in `backlog/research/` as history and are never removed without the user's explicit approval.
- **Result:** a measured table on `created_at` in insertion order, five rounds, page and count, 3k / 6.3k / 31k / 66k / 241k matching; the chosen predicate text for F2.

### Step F0 — Record the decisions

- **Executor:** sonnet, through `/record-decision` only. **Depends on:** decisions 2, 6 and 7. **Files:** the three `DECISIONS.md` files named above and `.claude/nav/adr-index.md` (regenerated by the command).
- **Change:** exactly the three records drafted above (with the user's changes); the superseded and annotated statuses set; the index regenerated.
- **Verification:** `bash .claude/nav/scripts/generate-adr-index.sh` ran; the index shows the new rows and the Superseded status.

### Step F1 — The two contract types

- **Executor:** sonnet. **Depends on:** F0. **Files (new):** `platform-commons/src/main/java/org/ost/platform/core/model/SqlColumn.java`, `SqlPredicate.java`, and `platform-commons/src/test/java/org/ost/platform/core/model/SqlColumnTest.java`, `SqlPredicateTest.java`. **Input:** `EntityRef.java` in the same package (style of a plain record); `platform-commons/pom.xml` (no Spring JDBC, no query-lib).
- **`SqlColumn`:** compact constructor rejects (`IllegalArgumentException`, the message names the bad value) an `alias` or `column` not matching `^[a-z][a-z0-9_]{0,62}$`; `sql()` returns `alias + "." + column`; one-line Javadoc.
- **`SqlPredicate`:** compact constructor copies `params` with `Map.copyOf`; rejects a `sql` containing `;`, `--` or `/*`; rejects a parameter name not matching `^_[a-z][A-Za-z0-9]*$`; rejects a parameter name that does not occur in `sql` as `":" + name`. `appendTo(isBound, bind)` throws `IllegalStateException` when `isBound` is true for a name, else calls `bind`; returns `" AND (" + sql + ")"`. No other methods, no `empty()` (absence is `Optional.empty()` at the Port).
- **Tests (JUnit 5, AssertJ):** `SqlColumn` accepts `a`/`id`, `pp`/`actor_id`; rejects `A`, `_ta`, `a.id`, `a id`, `a;`, `"a"`, `a--`, empty, 64 characters. `SqlPredicate` rejects `;`, `--`, `/*`, a name without `_`, a name absent from the text; `appendTo` binds every parameter, wraps in parentheses, throws on an already bound name.
- **Verification:** `bash scripts/build-and-test.sh --unit --sandbox` green.

### Step F2 — Taxon builds the predicate; the id-set contract goes

- **Executor:** sonnet. **Depends on:** F1, F-M. **Files:** `platform-commons/.../taxon/spi/TaxonPort.java`, `taxon-spring-boot-starter/.../services/DefaultTaxonPort.java`, `.../services/TaxonAssignmentService.java`, `.../repository/TaxonAssignmentRepository.java`, `integration-tests/src/test/java/org/ost/integrationtests/level1/taxon/TaxonPortResolveCategoryAndCityFilterTest.java` (replaced by `TaxonCategoryAndCityPredicateTest.java` in the same package). `git grep` shows no other caller of the removed methods outside tests.
- **`TaxonPort`:** remove `findEntityIdsWithAnyTaxon`, the default `resolveCategoryAndCityFilter` and `resolveTaxonIdFilter`; add `Optional<SqlPredicate> categoryAndCityPredicate(@NonNull EntityType entityType, @NonNull SqlColumn entityId, Set<Long> categoryIds, Long cityTaxonId)` with a one-line Javadoc (empty when neither filter is requested; a present but empty `categoryIds` matches nothing).
- **`TaxonAssignmentRepository`:** replace `findEntityIdsByTaxonIds` by a method that builds, without running anything, the predicate text inline: category part `EXISTS (SELECT 1 FROM taxon_assignment _tac WHERE _tac.entity_type = :_taxonEntityType AND _tac.entity_id = <entityId.sql()> AND _tac.taxon_id = ANY(:_taxonCategoryIds))`, city part the same with alias `_tay` and `_tay.taxon_id = :_taxonCityId`, joined with ` AND ` when both are requested (the final text follows step F-M). Values: `entityType.name()`, `categoryIds.toArray(new Long[0])`, `cityTaxonId`. Every column of `taxon_assignment` is qualified with the `_ta*` alias; no value is concatenated into the text.
- **`TaxonAssignmentService`, `DefaultTaxonPort`:** pure delegation.
- **Test `TaxonCategoryAndCityPredicateTest` (Testcontainers, the base class of the test it replaces):** the six cases of the old test (neither, only category, only city, both overlapping, both disjoint, category matching nothing), each run against a consumer the test owns (`SELECT c.id FROM (VALUES (...)) AS c(id) WHERE TRUE` + `predicate.appendTo(params::hasValue, params::addValue)` with `new SqlColumn("c", "id")`); plus: the text contains none of the ids passed in; an empty `categoryIds` returns no row.

### Step F3 — Advertisement splices the predicate

- **Executor:** sonnet. **Depends on:** F2. **Files:** `advertisement-spring-boot-starter/.../repository/AdvertisementRepository.java`, `.../services/AdvertisementService.java`, `integration-tests/.../level1/advertisement/AdvertisementRepositoryTest.java`, `AdvertisementServiceCategoryFilterTest.java`, `.claude/rules/advertisement-spring-boot-starter.md` (the bullet naming `resolveTaxonIdFilter()` and `findEntityIdsWithAnyTaxon()`).
- **Repository:** `public static final SqlColumn ID = new SqlColumn("a", "id");`; `findByFilter(filter, pageable, SqlPredicate taxonPredicate)` and `countByFilter(filter, SqlPredicate taxonPredicate)`, the predicate nullable (null = no restriction); `buildIdClause` becomes `taxonPredicate == null ? "" : taxonPredicate.appendTo(params::hasValue, params::addValue)` in the same position of the `formatted(...)` call; the `allowedIds` parameter and its comment go.
- **Service:** `resolveTaxonPredicate(filter)` returns `taxonPortFactory.findIfAvailable().flatMap(p -> p.categoryAndCityPredicate(EntityType.ADVERTISEMENT, AdvertisementRepository.ID, filter.getCategoryIds(), filter.getCityTaxonId()))`; `getFiltered` and `count` pass `.orElse(null)`; the two early returns on an empty set go.
- **Tests:** the `allowedIds` repository cases become predicate cases with a test-built `new SqlPredicate("a.id = ANY(:_ids)", Map.of("_ids", new Long[]{...}))`; the service test: the predicate passed through when present, null when Taxon is absent, null when no filter is requested; the two "returns empty without querying" cases go.

### Step F4 — Provider Profile splices the predicate

- **Executor:** sonnet. **Depends on:** F2 (independent of F3). **Files:** `provider-profile-spring-boot-starter/.../repository/ProviderProfileRepository.java`, `.../services/ProviderProfileService.java`, `integration-tests/.../level1/providerprofile/ProviderProfileRepositoryTest.java`, `ProviderProfileServiceTest.java`.
- **Behavior:** as F3 with `new SqlColumn("pp", "id")` and `EntityType.PROVIDER_PROFILE`. `ProviderProfileScope` stays (promotion ids and pins are bounded). The Java intersection of `onlyIds` with the taxon ids in `withTaxonFilter` goes: `buildWhere` adds `pp.id = ANY(:allowedIds)` for `scope.onlyIds()` and, independently, the taxon predicate, both ANDed in SQL.
- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox` green; `ArchitectureRulesTest` green; then the Playwright `e2e --full --ux` scenario (the category and city filters are UI-visible).

### Step F5 — Covering index (only if decision 3 says so)

- **Executor:** haiku. **Depends on:** F2. **Change:** one Taxon-local Liquibase changeset in the Taxon changelog creating the index on `taxon_assignment (taxon_id, entity_type, entity_id)` (replacing or joining the `taxon_id` index per decision 3), with a `remarks` attribute stating why it exists. With the predicate the index serves the count's hash semi-join (an index-only scan instead of a bitmap heap scan).
- **Verification:** the integration test suite green; `EXPLAIN` of the predicate on seeded data shows the index-only scan.

### Step F6 — ArchUnit guard (only if decision 4 says so)

- **Executor:** sonnet. **Depends on:** F4. **File:** `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`. **Change:** one `@ArchTest` rule `sql_predicates_are_built_only_in_starter_repositories`: no class outside a `..repository..` package of a starter calls a constructor of `org.ost.platform.core.model.SqlPredicate`, in the same custom `ArchCondition` style as the existing Taxon-assignment-write rule; check, do not assume, that test classes are outside the import scope.
- **Verification:** the test green; temporarily constructing a `SqlPredicate` in a service makes it fail (revert the probe).

### Step F7 — Documentation sync

- **Executor:** sonnet, through `/sync-docs` (diff mode), then `/check-architecture-map`. **Depends on:** F4 (F5, F6 when done). **Files:** whatever `/sync-docs` names; at least the root `CLAUDE.md` `core.model` list in the `platform-commons` paragraph (add `SqlColumn`, `SqlPredicate`), and the Taxon rule file if it names the removed method.

### Step F8 — Rare-category page (only on a production number)

- **Executor:** opus. **Depends on:** F4, F-M and a latency target from decision 5. **Goal:** the page of a rare category within the target, without changing the consumer contract; the change stays inside Taxon's predicate builder or a Taxon-local index; measured before and after on data whose `created_at` follows insertion order. **Open:** whether Taxon switches the predicate shape for selective taxons and how it learns selectivity (the usage counts it already keeps, or a threshold).

### Verification of the whole

1. `bash scripts/build-and-test.sh --unit --integration --sandbox` green.
2. Playwright `e2e --full --ux` green (the category and city filters, both list pages).
3. `ArchitectureRulesTest` green; `git grep -n "findEntityIdsWithAnyTaxon\|resolveCategoryAndCityFilter\|allowedIds" -- '*.java'` finds only the promotion `allowedIds` binding of `ProviderProfileRepository`.
4. Pluggability: with Taxon removed from `marketplace-orchestrator/pom.xml`, the advertisement and provider-profile lists start and list unfiltered, and no statement names `taxon_assignment` (log the SQL in that test; this needs the wiring profile test of the first research pass, see Related).
5. Before closing, the user re-runs the measurement on production-sized data if decision 5 set a latency target.

Estimate without F-M and F8: about 1.5 days, 330-520k tokens (F0 40-60k, F1 30-50k, F2 80-120k, F3 and F4 50-80k each, F5 10-20k, F6 30-50k, F7 40-60k); F-M 0.5 day, 100-150k tokens (opus); F8 0.5-1 day, 150-250k (opus).

## Related

- [improvement-249](../completed/tasks/improvement-249-pluggable-modules-opaque-cross-module-references-research.md): the research this task comes from; its two research folders (`backlog/research/pluggable-modules-opaque-refs/`, `backlog/research/pluggable-modules-query-fragment-port/`) hold the measurements, the SQL scripts and the sources; they stay as history and step F-M reads them from there.
- Independent of this task and still needed (to be filed as separate tasks when the user decides): a test that starts the Spring context with each optional starter absent, and the fix that makes each consumer declare the `ComponentFactory` beans it injects (today only the starter that provides a Port declares its factory, so removing Audit, Feedback, Contact, Promotion, API-key, Attachment, Advertisement or Provider Profile would likely fail startup; deduced from the code, not run); the orphan-assignment cleanup is improvement-133 item 25; the unfiltered list when Taxon is absent for a REST caller is improvement-133 item 11 (UI part); the double lookup per page and the exact count are improvement-003 item K; the duplicated empty-set guard is improvement-133 item 23 (this task removes it as a side effect).
- The decisions of `taxon-spring-boot-starter`, `marketplace-app` and `platform-commons` named above (find them through `.claude/nav/adr-index.md`); this task changes none of them until step F0.

## Operational notes
- token_cost_review: n/a
- token_cost_research: 321267 (second research pass only; the first pass was not recorded)
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: Cross-module feature
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: a researched plan has to be turned into a tracked task
- flows_chosen: /deep-research then /new-task
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: ADR-001 (taxon-spring-boot-starter), ADR-034 (marketplace-app), ADR-032 (platform-commons)

### Agent calls
- Deep research, second pass (query-fragment port) | subagent_type=deep-researcher | tokens=321267 | tool_uses=135 | duration_s=3019 | mode=background | batch=solo
