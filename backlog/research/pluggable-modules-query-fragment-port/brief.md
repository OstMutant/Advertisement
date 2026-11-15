# Brief: pluggable-modules-query-fragment-port

Purpose: what the planner is asked to find out and plan; written by the main thread, read by the planner.

## Goal

Study in depth ONE approach as the candidate universal rule for cross-module filtering, and decide
whether it fits this project: **a Port returns a SQL query fragment (predicate) with bind parameters
instead of a set of entity ids; the consuming module splices it into its own statement.**
Working names to relate to established theory: Predicate Provider SPI / Query Fragment Provider,
SQL Specification pattern (Evans/Fowler Specification adapted to plain SQL, analogue of Spring Data
`Specification`), semi-join delegation (`id = ANY(:ids)` replaced by a correlated `EXISTS`).

Five goals the rule must satisfy together (from the user, not negotiable):
1. Strong decoupling: a module does not know another module's Java classes, tables or repositories.
2. Full pluggability: removing any starter from `pom.xml` breaks nothing (build, Spring context,
   Liquibase, SQL) and leaves nothing behind in the remaining modules' own data or schema.
3. Database-level efficiency: filter, sort, aggregate, paginate in PostgreSQL; no large id sets
   carried through Java.
4. Minimal complexity: no extra layers, universal registries, duplicated models, excess Ports/DTOs.
5. Data ownership: each module owns its data and relations.

## Task

[improvement-249](../../tasks/improvement-249-pluggable-modules-opaque-cross-module-references-research.md)
(second pass). The first pass is in `backlog/research/pluggable-modules-opaque-refs/` (read its
`plan-human.md`, `notes.md`, `measurements/` first; do not redo it).

## Context

What the first pass found (facts, with the measurements and SQL scripts in that folder):
- Today: `taxon_assignment` is owned by Taxon; `AdvertisementService.resolveTaxonFilter()` and
  `ProviderProfileService.resolveTaxonFilter()` get `Set<Long>` entity ids from `TaxonPort` and bind
  `a.id = ANY(:allowedIds)`. The user's complaint: ids travel through Java.
- Variant B (relation table `advertisement_taxon` owned by Advertisement) was measured faster on big
  categories but leaves Taxon-shaped columns/ids in Advertisement after Taxon is removed -- the user
  rejected it as NOT real decoupling ("removing a module must leave nothing broken and nothing behind").
- The fragment approach was rejected in pass one only because the brief forbade "hidden cross-module
  JOINs" (marketplace-app "No raw cross-starter SQL joins" decision). The user now wants it studied
  seriously and says the ban can be refined: the join goes through a Port; if the module is absent
  the Port yields nothing and no join is built, so pluggability holds; every listed cost is expected
  to be fixable by a narrow contract.
- The user's pasted proposal (treat as a hypothesis, not as fact; verify every claim):
  ```java
  public record SqlFragment(String sqlClause, Map<String,Object> params) { static SqlFragment empty() ... }
  Optional<SqlFragment> getTaxonFilterFragment(EntityType entityType, String targetIdColumn,
                                               Set<Long> categoryIds, Long cityTaxonId);
  ```
  Taxon builds `EXISTS (SELECT 1 FROM taxon_assignment ta WHERE ta.entity_type = 'ADVERTISEMENT'
  AND ta.entity_id = <targetIdColumn> AND ta.taxon_id IN (:taxonFilterIds))`; Advertisement adds it as
  one more condition in its `SqlFilterBuilder` (query-lib `SqlCondition`). Taxon absent ->
  `ComponentFactory<TaxonPort>.findIfAvailable()` empty -> no fragment -> statement without
  `taxon_assignment`. Claimed: 1 query, no JVM id sets, no table changes, "1-2 ms".
  Known weak points in the claim: the "1-2 ms" is unmeasured (the first pass measured the same
  `EXISTS` shape on a local table: page ~5 ms, count ~185-200 ms at 31k ids); the comparison table
  compared against a `category_ids BIGINT[]` column that was never a real alternative.
- Costs the main thread named, which the user says are fixable (verify whether and how): the consumer
  executes SQL text written by another module (injection and trust); alias/column coupling
  (`targetIdColumn`); bind-parameter name collisions between fragments; contract is SQL text so a
  Taxon table change can break consumers (only an integration test sees both); needs a refinement of
  the marketplace-app decision on cross-starter joins.

## Where to look in the project

- The first-pass folder named above; task file named above.
- `.claude/nav/adr-index.md`, then extract: taxon ADR on "Filter resolves through `Set<Long>`, not SQL
  JOIN"; marketplace-app "No raw cross-starter SQL joins"; platform-commons Port/Hook SPI decisions;
  query-lib decisions. Use `node .claude/nav/scripts/md-to-decisions-json.js --extract <module> <ADR-NNN>`.
- `query-lib` (`SqlFilterBuilder`, `SqlCondition`, `SqlBoundFilter`, `OrderByBuilder`) and its rule
  file `.claude/rules/query-lib.md`: how a fragment would plug in, what binds named parameters.
- `platform-commons/.../taxon/spi/TaxonPort.java`, `core/ComponentFactory`, the Advertisement and
  Provider Profile repositories and services, `taxon-spring-boot-starter` repository and port impl,
  `ArchitectureRulesTest` (what ArchUnit rules a fragment contract would touch).
- Every other place where a large or unbounded id set crosses a module boundary through Java:
  `TopRatedPromotionRule.qualifyingIds()` / `FeedbackPort.findEntityIdsWithRatingAtLeast`,
  promotion placements, attachments, contact, audit -- for each decide whether a fragment contract fits,
  needs a different mechanism, or is not needed.

## Completion criterion

A report that lets the user decide, covering at least:
1. **Theory**: what this pattern family is called and how the names relate (Specification, Predicate
   Provider / Query Fragment SPI, semi-join, plus any closer established name such as query-object /
   criteria contributions, Spring Modulith / jMolecules treatment of cross-module queries, DDD
   "published language" or "open host", Hibernate/jOOQ/Spring Data `Specification` composition across
   modules, database-view-free "pushdown" of predicates). Sources from the web with links; state where
   the literature says this pattern is good, where it warns against it (leaky abstraction, shared
   persistence). Then show how it maps onto this project's `Port`/`ComponentFactory`/`SqlFilterBuilder`.
2. **Security**: SQL-injection analysis of a fragment contract. Which parts can be attacker-influenced
   (category ids from a REST request, column name, entity type), what the safe shape is (typed
   predicate object instead of a string; identifier whitelist or a column descriptor type; values only
   through bind parameters; unique parameter naming; no string concatenation of values), what the
   Sonar rules and this project's `JdbcClient`/`NamedParameterJdbcTemplate` usage allow, what a
   test must assert. Trust boundary: Taxon code is first-party, but state the worst case if a
   third-party starter provides a fragment.
3. **Contract design**: the minimal safe contract (compare: raw `String`+`Map`; typed record with a
   column reference type; a small predicate/`SqlCondition` object reusing `query-lib`; a port that
   returns an already-parameterised `SqlCondition`). Where it lives in `platform-commons`, naming per
   project convention (`api` vs `spi` vs `dto`), how it fits the `@NonNull`/no-`Optional`-parameter
   rules, and how "module absent" is represented without `null`.
4. **Measured SQL**: run the real shape on a disposable Postgres (same approach as the first pass,
   reuse its data scripts): page and count with the fragment (`EXISTS` on `taxon_assignment` with its
   current PK and with the proposed covering index), the rare-category case that was slower in B, the
   category-and-city case, OFFSET 1000, the 242k case; compare with A (ids through Java) and B from the
   first pass. Report the plan, execution and planning time; explain the rare-category result.
5. **Decoupling and pluggability check**: with the fragment approach, exactly what remains in the
   consumer when the provider is absent (code, schema, data, config, tests) and what remains in the
   provider when the consumer is absent. Check the indirect couplings from the first brief (auto-
   configuration, Liquibase order, bean injection, scheduled jobs, event listeners). State honestly any
   residual coupling: shared table/column names, alias, same database.
6. **Universality**: take every cross-module reference in the project (the first-pass map) and say, per
   reference, whether the fragment contract applies as the single universal rule, and if not, what the
   smallest second mechanism is. If no universal rule exists, say so plainly and name the minimal set of
   rules.
7. **Contradictions with recorded decisions**: the exact wording of the marketplace-app cross-starter
   join decision and the taxon "Set<Long>" decision, and the exact refinement/supersession that would be
   needed (as a proposal; the user decides). Also `ArchitectureRulesTest` and any ArchUnit rule.
8. **Failure modes**: Taxon changes its table; alias collision; a fragment that is slow; fragment
   used inside `UNION`/subquery/count wrappers; pagination wrappers that rename the alias; transaction
   and read-consistency; testing strategy (a test that sees both modules, a contract test per fragment,
   pluggability profiles from the first pass).
9. **Plan**: small prioritised batches with executor model, risk, dependencies, tests; plus the
   alternative of keeping A+ if the research shows the fragment approach is worse, with evidence. The
   research must not confirm the approach at any cost: if a flaw is fatal, say so.

## Decisions already taken

- All five goals above hold; removing a module must leave nothing broken and nothing behind in the
  remaining modules (user, explicitly).
- No required cross-starter foreign keys, views or functions; `marketplace-orchestrator` keeps its role;
  no ORM; explicit SQL through `JdbcClient`/`NamedParameterJdbcTemplate`; no global entity registry;
  no `BIGINT[]` replacement; no events/Outbox/CQRS; no database split; no large rewrites.
- Variant B (module-owned relation table) is rejected by the user as not real decoupling; do not
  re-propose it as the answer (it may appear in the comparison as measured reference only).
- The first-pass fixes (consumer-declared `ComponentFactory` beans, covering index, `WARN`
  threshold) are independent of this question; mention only if the fragment contract interacts with them.
- Research and report only: no project file is changed; no decision record is created; no backlog edit.

## What the planner may decide

Nothing beyond the goal. Highlight, never decide: any conflict with a recorded decision, any change of
the cross-starter join rule, any new Port/DTO, any batch that touches production code. The planner
chooses its own sources, experiments and the measurement set within the criteria above.

## Available executor models

haiku, sonnet, opus
