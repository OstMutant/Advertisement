# Plan for people: pluggable-modules-query-fragment-port

Second pass of [improvement-249](../../tasks/improvement-249-pluggable-modules-opaque-cross-module-references-research.md):
one candidate rule studied in depth -- **a Port returns a SQL predicate with its bind values instead
of a set of entity ids, and the consuming module splices it into its own statement.** Nothing in the
project was changed. SQL scripts and raw plans: [`measurements/`](measurements/); sources:
[`sources.md`](sources.md); working notes: [`notes.md`](notes.md).

## Goal

Decide whether this "query fragment" rule fits the project and can be the universal rule for
cross-module filtering, against the five goals together: decoupling, full pluggability, work done
in PostgreSQL, minimal complexity, data ownership. Extra requirement from you: make the column the
fragment binds to as flexible as is still safe.

## The problem this solves

Today a list filtered by category asks Taxon for every matching advertisement id
(`TaxonPort.findEntityIdsWithAnyTaxon`), carries the set through Java and binds it back into the
advertisement query (`a.id = ANY(:allowedIds)`), twice per page (once for the page, once for the
count). At 241k ids that costs about 0.9-1.1 s per page in the measurements below, before the ids'
trip through JDBC, most of it in the two lookups and in planning the huge bound array.

## Short answer

1. **Finding (the choice is yours, decision 1): it fits, with a typed contract; no fatal flaw was
   found.** It is the only measured variant
   that meets all five goals at once: no id set crosses Java (A fails this), nothing of Taxon is left
   in the consumer when Taxon is removed (B fails this), and the work runs in one PostgreSQL statement.
2. **Your hypothesis needs four corrections.** (a) "1-2 ms" holds only for the *page* of a popular
   category; a list page also runs a count, and page + count measured 11-334 ms. (b) For selective
   filters it is slower than today: a category matching 3k ads takes 104 ms against 30 ms. (c) The raw
   `String` + `Map` + `String targetIdColumn` shape is not safe enough: two silently wrong results
   and one precedence bug were reproduced; a typed column descriptor, reserved provider names and
   forced parentheses close all three. (d) It is not universal: it is the right rule only for
   filters that can match an unbounded number of rows; today that is Taxon alone.
3. **Its speed equals variant B's everywhere** (within noise), so B's measured gains come from
   running one statement, not from moving the relation table. Rejecting B costs nothing.
4. **Recorded decisions must change** (your call): taxon ADR-001 superseded, app ADR-034
   annotated, platform-commons ADR-032 superseded, and a named exception to platform-commons'
   "no persistence logic" rule.

## 1. What the pattern is called

| Name in the literature | What it says | How it maps here |
|---|---|---|
| Specification (Evans and Fowler, 1998; DDD) | a predicate object used for selection, validation, building | `SqlPredicate` is a specification in its selection role |
| Strongly typed specification (Khorikov) | the owner fixes the rule; a generic specification that carries the client's expressions is an anti-pattern | Taxon writes the whole predicate; the consumer passes only typed values and a column descriptor, never SQL |
| Query Object (Fowler, PoEAA) | objects that form themselves into SQL, so schema changes stay in one place | Taxon's table and column names stay in Taxon's repository |
| Spring Data `PredicateSpecification.toPredicate(From, CriteriaBuilder)` | the caller hands in the root; the specification builds a predicate against it; specifications are combined with `and`/`or` | `SqlColumn` plays the role of `From`; `SqlPredicate.appendTo(...)` is the `and` |
| jOOQ `Condition` with `QueryPart` template arguments | identifiers travel as typed parts, values as binds | same split: `SqlColumn` for identifiers, `:_name` binds for values |
| Semi-join / correlated `EXISTS` (PostgreSQL docs) | `EXISTS` may reference outer columns, stops at the first row, returns each outer row at most once | the predicate is a correlated `EXISTS` on `taxon_assignment` |
| Predicate pushdown | a filter of one source is pushed into the statement that reads another (`postgres_fdw` does it across servers) | Taxon's filter is pushed into Advertisement's statement instead of being evaluated first |
| Open Host Service + Published Language (DDD context mapping) | the upstream opens one documented protocol for all consumers | `TaxonPort` is the open host; here its "language" is SQL text |

**Where the literature warns.** Fowler (Integration Database) and Grzybek (modular monolith: shared
database access is the "last, last, last resort") warn against modules coupling through a shared
schema. Their reason is schema knowledge spreading into other modules, so a table change has to be
negotiated. The fragment keeps that knowledge inside the provider, which answers the warning at
source-code level. It does not answer it at runtime: the two modules must share one database, one
role and one snapshot, and the consumer runs SQL text it did not write. Spring Modulith's
documentation says nothing about cross-module SQL; a forum answer expects its verification not to
see raw-SQL dependencies at all (unverified). No source found recommends a provider-built SQL
fragment between modules; none forbids it either. It is a known shape (Specification, Query Object)
applied across a module border, which the sources do not discuss directly.

## 2. Security

**What can be attacker-influenced.** From a REST or UI request: the category ids (`Set<Long>`) and
the city id (`Long`). Typed `Long` values cannot carry SQL, and in the proposed contract they are
never written into the text, only bound. The entity type is a Java enum chosen in code. The column
descriptor is a constant in the consumer's repository; it never comes from a request.

**The safe shape** (OWASP: bind markers cover data values only; identifiers must come from an
application-owned allow-list):

1. Values only as named bind parameters. A test passes an id such as `987654321` and asserts that it
   does not appear in the predicate text.
2. Identifiers only through a typed `SqlColumn(alias, column)`, each part checked against
   `^[a-z][a-z0-9_]{0,62}$` in the record's constructor. No quote, dot, space, semicolon or comment
   can get in. This check is the allow-list by construction; there is no free-text column.
3. Reserved names for the provider: its aliases and parameter names start with `_` (`_tac`,
   `:_taxonCategoryIds`); a consumer alias must start with a letter. Measured why: an alias collision
   is silently wrong (below, failure modes).
4. The consumer wraps the predicate in parentheses (`AND (...)`); measured why: a predicate with an
   `OR` appended bare loses the consumer's own filter (0 rows expected, 3 returned).
5. Fail fast on a duplicate parameter name: Spring's `MapSqlParameterSource.addValue` silently
   overwrites an existing name (read in the Spring source), so `appendTo` checks `hasValue` first.
6. The predicate text is refused if it contains `;`, `--` or `/*` (a cheap guard against a
   statement break or a comment that would swallow the consumer's `ORDER BY ... LIMIT`).

**Sonar.** `java:S2077` ("Formatting SQL queries is security-sensitive") is a Security Hotspot for
manual review, not an injection finding; the taint rule is `S3649`. The project already builds SQL
with `.formatted(...)` in four repositories and has no `S2077` suppression in code; the fragment adds
one more formatted statement per consumer, of the same kind. (Rule pages not readable here; taken
from Sonar's rule listing and community answers.)

**Trust boundary.** Taxon is first-party. Worst case for a third-party starter that provides a
predicate: its SQL runs on the consumer's connection, inside the consumer's transaction. It can read
any table the role sees (leaking through which rows appear or through timing), call slow or
side-effecting functions (`pg_sleep`, a writing function, `set_config`), take locks, or break the
consumer's statement. But a third-party starter on the classpath already has the `DataSource` and
can run any SQL by itself, so the fragment does not widen what such code can do; it widens only the
blast radius of a mistake. Rule that follows: only first-party starters may provide a `SqlPredicate`
(proposal; an ArchUnit rule can check that only starter `..repository..` classes construct one).

## 3. Contract design

Compared shapes, least to most structured:

| Shape | Safe? | Cost | Verdict |
|---|---|---|---|
| Your proposal: `SqlFragment(String sqlClause, Map params)` + `String targetIdColumn` | by convention only: free-text column, silent parameter overwrite, no parentheses rule, both `Optional` and `empty()` | smallest | not safe enough |
| **Typed record + column descriptor**: `SqlPredicate(sql, params)` + `SqlColumn(alias, column)` | yes, the six rules above live in two constructors and one method | 2 small records, no new dependency | **proposed** |
| Reuse query-lib `SqlCondition` | `SqlCondition` models one column, one operator, one value; `SqlOperator` is package-private; an `EXISTS` does not fit | changes query-lib's frozen API (query-lib ADR-003), and `platform-commons` would have to depend on query-lib, which pulls `spring-boot-starter-data-jdbc` into every module that sees a Port | rejected |
| Port returns an already-parameterised query-lib object | same as above | same | rejected |

**Proposed contract** (types in `platform-commons` `org.ost.platform.core.model`, next to
`EntityRef`; plain JDK records, no Spring import):

```java
public record SqlColumn(@NonNull String alias, @NonNull String column) { String sql(); }      // validated identifiers
public record SqlPredicate(@NonNull String sql, @NonNull Map<String, Object> params) {        // provider-built
    String appendTo(Predicate<String> isBound, BiConsumer<String, Object> bind);             // " AND (" + sql + ")"
}
// TaxonPort, replacing findEntityIdsWithAnyTaxon and the default resolveCategoryAndCityFilter:
Optional<SqlPredicate> categoryAndCityPredicate(@NonNull EntityType entityType, @NonNull SqlColumn entityId,
                                                Set<Long> categoryIds, Long cityTaxonId);
// consumer (AdvertisementRepository), one line in place of buildIdClause:
taxonPredicate == null ? "" : taxonPredicate.appendTo(params::hasValue, params::addValue)
```

- `core.model`, not `*.dto` (it has a constructor check and one method) and not `*.spi` (no data
  records there, platform-commons ADR-002). Names: `SqlPredicate`, not `SqlFragment`: it can only be
  a boolean, which is what makes the placement rules simple. Both names are proposals.
- Rules of the project kept: `@NonNull` on every non-null parameter; no `Optional` parameter (the
  nullable filter values stay nullable, as in today's default method); "module absent" and "no
  filter requested" are both `Optional.empty()` returned by the Port, reached through
  `ComponentFactory<TaxonPort>.findIfAvailable().flatMap(...)` as today; a present but empty
  category set still matches nothing (`= ANY('{}')` is false), the same meaning as today.
- It removes the one default method with logic on a Port (platform-commons ADR-032) and the Java
  intersection in `ProviderProfileService.withTaxonFilter`, and the two "empty set, return early"
  guards in each service (already listed as improvement-133 #23).

**How flexible the column can be (your extra requirement).**

| Level | Example | Safe? | Needed today? |
|---|---|---|---|
| any alias of the consumer | `a.id`, `pp.id`, `sub.id` | yes (validated, qualified) | yes |
| any key column, not only `id` | `pp.actor_id`, `a.parent_id` | yes, same type | no |
| several predicates in one statement | category AND city; the same predicate in each `UNION` branch | yes: reserved `_` names, fail-fast merge, one alias per branch (measured) | category + city, yes |
| composite key: type column + id column | an `EntityRef`-shaped consumer row (`audit_log`, `attachment`) filtered by taxon | yes, a second descriptor `(SqlColumn type, SqlColumn id)` (measured) | no; add when the first consumer needs it |
| placement: `WHERE`, `JOIN ... ON`, a boolean in `ORDER BY`, inside count/page/subquery wrappers | `ORDER BY CASE WHEN <predicate> THEN 0 ELSE 1 END` | yes, while the alias is in scope; out of scope it fails loudly (measured) | `WHERE` only |

**Where flexibility must stop:** an expression instead of a column (`COALESCE(a.x, a.y)`, a cast, a
function) -- that is free SQL from the consumer, which needs a parser to be safe; quoted, mixed-case
or schema-qualified names (`public.a.id`) -- the project uses none; a column chosen by a request --
the descriptor is a code constant, the check is a backstop, not a licence; any consumer SQL inside the
provider's predicate (the generic-specification anti-pattern); the provider's columns in the
consumer's `SELECT` list, aggregates or a join that returns provider data -- that is a cross-module
read, not a filter, and stays forbidden.

## 4. Measured SQL

PostgreSQL 15 (the dev container, in a throwaway database that was dropped afterwards), the first
pass's data script: 1,000,000 ads, 5% soft-deleted, 2,581,289 assignment rows. Five rounds per
statement, median of planning + execution. Values bound as parameters (custom plans), as JDBC binds
them. **F** = the fragment (`EXISTS` on `taxon_assignment`); **A** = today (Taxon lookup twice per
page, ids bound back); **A1** = A with one lookup per page; **B** = the first pass's local table, for
reference only. Per list page = page query + count query (+ lookups for A).

**Realistic state** (covering index `(taxon_id, entity_type, entity_id)` in place, `created_at` in
insertion order -- see the data note below), ms per list page:

| Filter (ads matched) | A today | A1 | **F** | B | F page / F count |
|---|---|---|---|---|---|
| rare category (3,007) | 30 | 29 | **104** | 84 | 90 / 14 |
| category (6,301) | 52 | 49 | **82** | 74 | 52 / 30 |
| category (31,575) | 231 | 221 | **219** | 201 | 4 / 215 |
| category (65,799) | 323 | 303 | **228** | 239 | 3 / 225 |
| category (241,170) | 907 | 813 | **334** | 370 | 1 / 333 |
| category AND city (3,130) | 171 | 100 | **134** | 107 | 47 / 87 |
| rare category AND city (16) | 14 | 7 | **11** | 12 | 6 / 5 |
| category (31k), OFFSET 1000 | 237 | 228 | **276** | 268 | 82 / 194 |
| three categories (40,754) | 257 | 241 | **210** | 248 | 7 / 203 |

A's numbers are server-side only: moving the ids to Java and back (twice per page today) is not
included; the first pass saw 300-400 ms just to fetch 242k ids into a client. A's planning alone is
about 140 ms per statement at 241k ids (a huge bound array); F plans in about 1 ms. Raw data:
[`R8-repeat-P3.txt`](measurements/R8-repeat-P3.txt), medians by
[`06-median.py`](measurements/06-median.py).

**Before the covering index** (current indexes, random `created_at`), per list page: F 245 / 299 /
265 / 440 / 360 ms for 31k / 6.3k / 66k / 3k / 241k ads, against A 355 / 110 / 535 / 89 / 1,105 ms;
category AND city F 422 against A 459 ([`R2-repeat-P1.txt`](measurements/R2-repeat-P1.txt)). The
index helps F's count (index-only semi-join; category AND city 290 -> 85 ms) and A's lookup even
more (31k: 83 -> 10 ms) ([`R4-repeat-P2.txt`](measurements/R4-repeat-P2.txt)).

**Why the rare category is slower in F (and in B).** The page is `ORDER BY created_at DESC LIMIT 20`.
PostgreSQL walks the `created_at` index backward and probes `taxon_assignment` for each ad. Category
150 matches about 1 ad in 316, so 20 hits cost thousands of probes; with two parallel workers each
process runs ahead to ~48 rows, so 45,769 probes were made for 20 rows
([`R10-plans-final.txt`](measurements/R10-plans-final.txt)). A does the same backward walk but tests
each ad against an in-memory hashed array, which is far cheaper per row. The planner's row estimate
(3,977) is right; the per-process overshoot is what it does not price.

**Data note.** In the first pass's data set `created_at` is random per row (correlation 0.0035), so
the backward walk reads a different heap page for every row; real ads are inserted in time order.
After `CLUSTER` (correlation 1) the rare-category page fell from 217 to 90 ms. The first pass's
B-versus-A numbers carry the same bias against B.

**A shape tried and rejected.** `a.id = ANY(ARRAY(SELECT entity_id ...))` (the id set built once
inside PostgreSQL) is the fastest for the rare category (22 ms) but slow for big ones (241k: 2.4 s),
and category AND city ran past a 60 s timeout (a second ~99k-element array searched linearly per
row). It remains only as an idea for a Taxon-internal optimisation (batch F8).

## 5. Decoupling and pluggability check

| Situation | What remains | Verdict |
|---|---|---|
| Taxon removed | consumer code: one nullable parameter and the `appendTo` call; no Taxon table or column name anywhere in Advertisement or Provider Profile; schema and data of the consumer untouched; config none; tests: the consumer's own tests run with no predicate | clean; the statement never names `taxon_assignment` |
| Advertisement or Provider Profile removed | Taxon keeps `taxon_assignment` rows of that entity type (as today); its predicate builder is unused | as today |
| Auto-configuration, bean injection | unchanged: `ComponentFactory<TaxonPort>` declared by both consumers (`@ConditionalOnMissingBean`), the first-pass wiring gap concerns other ports | no new coupling |
| Liquibase order | none: the predicate is built per request after every `SpringLiquibase` bean ran | no new coupling |
| Scheduled jobs, event listeners | none touch the filter | no new coupling |

**Residual coupling, stated honestly.** (1) Same database, same schema and the consumer's database
role must be allowed to read `taxon_assignment`; schema-per-module with separate roles would need a
`GRANT`, a database split would end the approach. (2) The consumer's alias and key column name
travel to the provider at runtime (as a validated descriptor). (3) The consumer runs SQL text it did
not write; a Taxon table change is seen only by a test that runs the predicate against PostgreSQL --
Taxon's own contract test can do it without the consumer (a `VALUES` table as the consumer). (4) Page
and count of the consumer now read Taxon's table in the consumer's snapshot, which is more
consistent than today (lookup and page are separate statements today).

## 6. Universality

| Cross-module reference (first-pass map) | Large id set through Java today? | Fragment fits? | Rule |
|---|---|---|---|
| Ad / profile filtered by category and city (Taxon) | yes, unbounded | yes | **predicate** |
| Profile filtered and pinned by promotion (`PromotionCatalogService.scopeFor` -> `ProviderProfileScope`) | bounded by the number of placements | possible (an `EXISTS` and a boolean `ORDER BY` term, through the orchestrator) | id set is enough; switch only if placements grow large |
| Promotion recount from feedback (`TopRatedPromotionRule.qualifyingIds` -> `FeedbackPort.findEntityIdsWithRatingAtLeast`) | bounded by qualifying profiles; a write path | no: it would be a provider predicate inside another module's `INSERT`, a cross-module write | id set |
| Display enrichment: taxons, actors, attachments, contact, feedback aggregate of one page | page-sized | no gain | bulk lookup by page ids (app ADR-034's pattern) |
| Audit timeline by actor, user purge (`findOwnerIds`, `clearActorReferences`) | small, user-chosen or bounded | no gain | id set |
| Any row -> actor (`created_by`, ...) | none | not a filter | opaque `Long` |

So there is no single universal rule. The smallest set is three rules: (1) references are opaque ids
(`Long` or `EntityRef`); (2) a filter over another module's data that can match an unbounded number
of rows is a provider-built `SqlPredicate`; (3) everything bounded (a page, a placement list, a
user selection) stays an id set or a bulk lookup. Today rule 2 has exactly one user: Taxon.

## 7. Contradictions with recorded decisions

None of these is changed by this research; each is a decision for you (decisions 1, 2 and 6).


- **taxon ADR-001** "Filter resolves through `Set<Long>`, not SQL JOIN" says: "`TaxonPort.findEntityIdsWithAnyTaxon(entityType, taxonIds)` returns a `Set<Long>` ... no SQL JOIN between marketplace tables and starter tables", and "Any new filtering that involves taxon data must follow the same pattern". Its own trigger: "If a single category routinely resolves to >10k advertisement ids ... consider a starter-owned JOIN helper". The predicate is that helper. Needed for path F: **supersede** ADR-001.
- **marketplace-app ADR-034** "No raw cross-starter SQL joins -- bulk-lookup port + service-level enrichment". Its reason: the consumer "hardcod[ed] `user-spring-boot-starter`'s table and column names ... a rename ... would break ... at runtime with zero compile-time warning". With the predicate the consumer hardcodes nothing of Taxon, but a Taxon rename can still break the consumer's statement at runtime (caught by Taxon's own contract test, not by the compiler). Needed for path F (proposal): **annotate**, not supersede -- wording in `plan-machine.md` step F0: a provider-built predicate obtained through a Port is allowed for row restriction only, never to read the other starter's columns. The enrichment rule of ADR-034 stays.
- **platform-commons ADR-032** (the default method `resolveCategoryAndCityFilter`): superseded, the method disappears.
- **platform-commons governance** ("Feature-specific models or persistence logic" is NOT ALLOWED): `SqlColumn`/`SqlPredicate` are persistence-shaped; they need a named exception (a new platform-commons ADR).
- **query-lib ADR-003** (API frozen): untouched by the proposal, because query-lib is not changed.
- **`ArchitectureRulesTest`**: no existing rule breaks (`ports_live_only_in_platform_commons`, `no_optional_method_parameters`, `starters_must_not_import_sibling_starters` all hold, because the types are in platform-commons). Optional new rule: only starter `..repository..` classes construct a `SqlPredicate`.

## 8. Failure modes

All demonstrated on temp tables in [`measurements/R5-failure-modes.txt`](measurements/R5-failure-modes.txt):

| Failure | What happens | Closed by |
|---|---|---|
| unqualified consumer column (`= id`) and Taxon later adds an `id` column | silently wrong: 3 rows become 10, no error | `SqlColumn` is always `alias.column` |
| consumer alias equals Taxon's internal alias (`ta`) | inner alias wins, silently wrong (10 rows) | provider aliases start with `_`, consumer aliases cannot |
| predicate with `OR` appended without parentheses | consumer's own filter lost (3 rows instead of 0) | `appendTo` always wraps |
| predicate used in a count wrapper or subquery that renames the alias | loud error "missing FROM-clause entry" | pass the wrapper's alias in `SqlColumn` |
| predicate in both branches of a `UNION` | works, one alias per branch; same parameter values may repeat | fail-fast merge allows only new names |
| Taxon table renamed or column changed | consumer statement fails at runtime | Taxon contract test runs the predicate against PostgreSQL |
| provider table missing but a predicate still built | loud error | cannot happen through `findIfAvailable()`; only a stale bean could |
| a slow predicate | the whole list query is slow, and the consumer cannot see why | contract test with `EXPLAIN` on a seeded table, and the measurement set below |
| generic plan after five executions (pgJDBC `prepareThreshold` = 5) | one plan for every category: F's page keeps its good shape (rare 120 ms, big 1 ms), F's count gets worse (rare 165 instead of 20 ms; 241k 684 instead of 333 ms); A is hit harder (page at 241k 1,175 instead of ~176 ms); forced in [`R9-generic-plan.txt`](measurements/R9-generic-plan.txt) | shared with today's A, not created by the fragment; whether the project's statements really reach generic plans was not measured; `plan_cache_mode` or pgJDBC's `prepareThreshold` are the levers if they do |
| read consistency | page and count each see one snapshot that includes Taxon's rows -- better than today, where lookup and page are separate statements | nothing needed |

**Tests.** (1) Unit, platform-commons: the identifier pattern, the reserved names, the forbidden
tokens, `appendTo`. (2) Contract test, Taxon (integration-tests level 1): every case of today's
`TaxonPortResolveCategoryAndCityFilterTest`, executed against a `VALUES` consumer; values never in the
text. (3) Consumer tests (level 1): the repository with a test-built predicate; the service with Taxon
absent passes no predicate. (4) Both modules (existing level-1 Testcontainers context): the list
filtered by category and city returns the right rows. (5) The first pass's pluggability profiles: with
Taxon removed no statement names `taxon_assignment`.

## 9. Plan

Two paths; batch details in [`plan-machine.md`](plan-machine.md).

**Path F (the predicate):**

| # | Batch | Executor | Risk | Depends on |
|---|---|---|---|---|
| F0 | Decision records: supersede taxon ADR-001 and commons ADR-032, annotate app ADR-034, new commons ADR for the two types | sonnet via `/record-decision` | none | your decisions |
| F1 | `SqlColumn`, `SqlPredicate` + unit tests | sonnet | low | F0 |
| F2 | Taxon builds the predicate; `findEntityIdsWithAnyTaxon` and the default method go; contract test | sonnet | medium | F1 |
| F3 | Advertisement splices it | sonnet | medium | F2 |
| F4 | Provider Profile splices it (promotion ids stay) | sonnet | medium | F2 |
| F5 | Covering index (first pass Step 3) | haiku | low | F2, decision 3 |
| F6 | ArchUnit: who may build a predicate | sonnet | low | F4, decision 4 |
| F7 | Docs and architecture map | sonnet | none | F4 |
| F8 | Rare-category page, only on a production number | opus | medium | F4, decision 5 |

**Path A+ (keep the id set):** the first pass's batches 1-6, unchanged.

## The means needed

Executors sonnet (F0-F4, F6, F7), haiku (F5), opus only for F8. Verification with
`bash scripts/build-and-test.sh --unit --integration --sandbox` and the Playwright `e2e --full --ux`
scenario. No new dependency, no schema change (F5 optional).

## What the result will look like

With path F: Advertisement and Provider Profile each run one page and one count statement that
contain Taxon's `EXISTS`; no advertisement id travels through Java for a filter; `TaxonPort` loses
`findEntityIdsWithAnyTaxon` and its default method and gains one method; platform-commons gains two
small records. A list filtered by a big category takes about a third of today's time (241k ads:
334 against 907 ms, before counting today's JDBC transfer); a rare category takes about 100 ms
instead of 30 ms. With Taxon removed, the lists start and list unfiltered, and no statement names a
Taxon table.

## Cost

| Batch | Executor | Effort | Tokens (estimate) |
|---|---|---|---|
| F0 | sonnet | 1 h | 40-60k |
| F1 | sonnet | 1 h | 30-50k |
| F2 | sonnet | 2-3 h | 80-120k |
| F3 | sonnet | 1-2 h | 50-80k |
| F4 | sonnet | 1-2 h | 50-80k |
| F5 | haiku | 15 min | 10-20k |
| F6 | sonnet | 1 h | 30-50k |
| F7 | sonnet | 1 h | 40-60k |
| F8 (only on demand) | opus | 0.5-1 day | 150-250k |

Path F without F8: about 1.5 days, 330-520k tokens. Path A+: the first pass's estimate.

## Risks

- **Selective filters get slower** (3k ads: 30 -> 104 ms; 6.3k: 52 -> 82 ms). If most real
  categories are rare, the average page gets slower while the worst case gets much faster.
  Batch F8 exists for this, only on a production number.
- **The numbers are one synthetic data set on one machine**: the dev container's default
  `shared_buffers` (128 MB, smaller than the 290 MB ad table), invented category skew, +-30%.
  A's JDBC transfer was not measured, so A is shown faster than it really is.
- **Runtime coupling stays**: a Taxon schema change can break the consumer's statement at run time;
  only Taxon's contract test (step F2) sees it before production. A future split into separate
  databases, or per-module database roles, would end this approach (a `GRANT` at least).
- **Generic plans** after five executions can make every variant's count slower; not measured in
  the running application.
- **Precedent**: once a provider predicate is allowed, it may be used where an id set is enough;
  the rule has to say "unbounded filters only".
- **The count stays the biggest cost** for big categories in every variant (200-330 ms); only a
  different count strategy changes that, which is improvement-003 item K, not this task.
- The measurement used a throwaway database inside the running dev Postgres container, because the
  project's hook blocks a raw `docker run`; it was dropped at the end.

## Where the project's practice differs from best practice

- **The project's way (taxon ADR-001, app ADR-034): never let another module's tables into a
  statement; pass ids.** That matches the modular-monolith literature (Grzybek, Fowler) for keeping
  schemas private. The other way: a provider-built predicate pushed into the consumer's statement
  (Specification / Query Object across a module border). Its reason: no unbounded id set through
  Java, one statement, one snapshot. Its cost: the modules must share one database and role, the
  consumer runs SQL text it did not write, and a provider table change is caught only by a test
  against PostgreSQL. The plan keeps the project's way until you decide (decision 1).
- **platform-commons governance forbids persistence-shaped types**; the predicate types would be the
  first. Alternative homes (query-lib, or a new tiny library) cost a new dependency for
  platform-commons; kept as a decision (decision 2).

## Decisions waiting for you

1. **The rule**: path F -- a Taxon-built predicate for unbounded cross-module filters, Taxon only today
   (supersedes taxon ADR-001) -- or path A+ (keep the id set, the first pass's batches). Trade-off:
   F is better from ~30k matching ads up and removes the Java id set; A+ is better for selective
   filters (30 against 104 ms) and changes no recorded decision.
2. **Where the two types live**: `platform-commons` `core.model` with a named exception to its
   "no persistence logic" rule (proposed: no new dependency); or query-lib, which makes
   `platform-commons` depend on query-lib and its JDBC starter; or a new tiny library (a new module).
3. **Covering index** `(taxon_id, entity_type, entity_id)` (batch F5, the first pass's batch 3): it
   helps both paths (proposed: yes); replace the `taxon_id` index or keep both (first pass's decision 5).
4. **ArchUnit guard** that only starter repositories build a `SqlPredicate`, and the rule "only
   first-party starters provide predicates" (batch F6): yes or no.
5. **A latency target** for one list page (page + count); it decides whether batch F8 ever runs.
6. **app ADR-034**: annotate it (proposed wording in `plan-machine.md` step F0) or supersede it.
7. **Names**: `SqlPredicate` and `SqlColumn` (proposed) or `SqlFragment` as in your proposal.
8. **Composite key descriptor** (type column + id column): later, when a first consumer needs it
   (proposed), or now.
9. Still open from the first pass and unchanged by this one: what a category filter does when Taxon
   is absent (ignored today), and which batches become backlog tasks.
