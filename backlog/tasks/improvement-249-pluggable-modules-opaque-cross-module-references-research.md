# improvement-249: Research: pluggable modules with opaque cross-module references (starting from the Taxon filter flow)

**Type:** improvement — research and review only; no code, table, migration, class or decision record is changed by it
**Module:** `taxon-spring-boot-starter`, `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`, `platform-commons` (the Ports), and every other module that stores a reference to another module's entity
**Priority:** 🟡 high, at the very top of the backlog at the user's request
**When:** independent, no blockers; the run itself goes through `/deep-research` and waits for the user's separate approval before any change

## Current state

Checked on 2026-11-09 against the code and the decision records:

- `taxon_assignment` belongs to `taxon-spring-boot-starter` (`TaxonAssignment`, `TaxonAssignmentRepository.findEntityIdsByTaxonIds`, `DefaultTaxonPort`); `provider-profile-spring-boot-starter`'s changelog remarks say its category and city assignments go through it.
- `AdvertisementService.resolveTaxonFilter()` and `ProviderProfileService.resolveTaxonFilter()` ask `TaxonPort` for the entity ids that carry any of the chosen taxons; `AdvertisementRepository` and `ProviderProfileRepository` then filter with `AND a.id = ANY(:allowedIds)` (`pp.id = ANY(:allowedIds)`), so an id set of any size crosses the Java layer.
- The decision record of `taxon-spring-boot-starter` that fixes this flow ("Filter resolves through `Set<Long>`, not SQL JOIN") is Accepted and states its own trigger to revisit: a single category that routinely resolves to more than 10,000 ids, when a starter-owned JOIN helper or a pre-computed mapping should be considered; it also records that no `WARN` log for the threshold exists yet.
- The decision "No raw cross-starter SQL joins — bulk-lookup port + service-level enrichment" in `marketplace-app` forbids a hidden JOIN between two starters' tables; a JOIN inside one module's own tables does not violate it.
- No open task covers this (searched `backlog/tasks/` and `.claude/nav/adr-index.md` on 2026-11-09).

## Why change

The flow works, but it moves a large id set between the Taxon database access and the Advertisement database access through Java, and the filter, the sort and the page of one module's own rows cannot run as one SQL statement. A starter must also be removable from `pom.xml` without breaking the rest of the system; the research has to show where that already holds and where it does not.

## Expected benefit

A decision, grounded in the code and in measured SQL plans, on one general rule for cross-module references: opaque ids, local association tables, or the current model; the list of modules where the rule applies and where it does not; and a small prioritized set of batches. If the current implementation is better than the alternatives, the report says so with the evidence.

## Approach

Run `/deep-research` on this task (the brief below is its input), then decide with the user which batches become tasks. The research must not confirm the hypothesis at any cost.

### Research brief

#### 1. Goal

Study the current architecture and find the one practical approach to cross-module references that gives all of the following at once:

1. **Strong module decoupling** — a module does not know another module's Java classes, tables or repositories.
2. **Full pluggability** — any Spring Boot starter can be removed from the Maven dependencies, and the rest of the system must still build, run its own Liquibase migrations, start without missing tables, SQL views, functions or other database objects of the removed module, and keep working within the modules that remain.
3. **Database-level efficiency** — relational work, filtering, sorting, aggregation and pagination run in PostgreSQL as far as possible, not by carrying large id sets between modules and Java.
4. **Minimal complexity** — no extra layers, universal registries, duplicated models, excess Ports or DTOs, or synchronisation mechanisms.
5. **Data ownership** — each module owns its data and its relations, and does not hand control of its model to another module.

Find the best balance between these on the real code.

#### 2. Hypothesis to test: pluggable modules with opaque cross-module references

2.1. **A module owns its relations.** If an entity belongs to the Advertisement module, the local relations needed to work with it live in Advertisement: `advertisement`, `advertisement_taxon` (the assigned taxons), `provider_profile`, `provider_profile_taxon`. `taxon_id` is a plain opaque id, not a hard foreign key to another starter's table. Advertisement knows its own tables, stores the ids of external entities or classifiers, does not know how Taxon is implemented, and does not need Taxon to run its local operations. Taxon owns the catalogue, the hierarchy, the translations and the rules; it does not own the relation tables of foreign entities; it offers the operations other modules need through optional contracts.

2.2. **Opaque ids.** Only identifiers cross a module boundary — no pairs of values, no foreign Entity or DTO. Find out where a plain `Long` or `UUID` is enough, where a typed value object is needed, where the existing `EntityRef` fits, whether separate identifiers for external references are needed instead of internal primary keys, and how identity stays stable after a starter is removed and installed again. Do not introduce a type only for abstraction; its practical use must be proved.

2.3. **Local association tables.** Check where a relation table in the module that owns the entity is worth it: the table belongs to one module; a foreign key to the module's own entity is allowed; a foreign key to another starter's table is not allowed where it breaks pluggability; external ids are stored as opaque references; indexes, UNIQUE and other local constraints give as much integrity as possible. Do not assume every relation should become an association table; decide by real scenarios and real SQL.

2.4. **Database-level operations.** PostgreSQL does the relational work over data that belongs to one module, without carrying intermediate results through Java. Instead of `Taxon DB → TaxonPort → Set<Long> of 10,000 entity ids → Advertisement DB WHERE id = ANY(:allowedIds)`, consider `TaxonPort → a small set of taxon ids → Advertisement DB → local advertisement_taxon → EXISTS / JOIN / pagination`: Taxon answers for the classification and hierarchy semantics, Advertisement filters its own entities in its own SQL. Also check for other scenarios where cross-module work carries large data sets.

#### 3. Hard constraints (criteria, not recommendations)

**Pluggable starters.** Removing any starter from `pom.xml` must not break the rest. Without a separate justification, do not introduce: a foreign key in one starter's Liquibase changelog to another starter's tables; SQL views that depend on another starter's tables; SQL functions that depend on another starter's objects; shared tables whose life cycle needs several modules present; required Java dependencies between domain starters. Also check the indirect ones: Spring auto-configuration, Liquibase ordering, bean injection, configuration properties, scheduled jobs, event listeners and SQL queries.

**Keep the current architecture.** Do not move or redesign `marketplace-orchestrator`; do not propose splitting PostgreSQL into several databases without a concrete need; do not break the explicit-SQL model or the no-ORM rule; do not get around the "no raw cross-starter SQL joins" decision of `marketplace-app` with hidden cross-module JOINs; no global Entity Registry without a proved need; no `BIGINT[]` on entities as an automatic replacement for normalised relations; no Spring Events, Outbox, CQRS or materialised views only to remove the current problem; no large rewrites.

#### 4. The real Taxon flow

Start with the current Taxon implementation and analyse: (1) `taxon_assignment` and its owner; (2) `TaxonPort` and all its implementations; (3) `AdvertisementService.resolveTaxonFilter()` and its logic; (4) `TaxonAssignmentRepository.findEntityIdsByTaxonIds()`; (5) `AdvertisementRepository` and the use of `ANY(:allowedIds)`; (6) the same flow in Provider Profile; (7) parent and child taxons in the hierarchy; (8) creating, changing, deleting and re-assigning taxons; (9) bulk operations and pagination; (10) transaction boundaries and tests.

Compare at least: **A. the current model** — one `taxon_assignment` in Taxon, entity ids passed through the Port; **B. opaque id plus local association tables** — `advertisement_taxon` and `provider_profile_taxon` belong to their modules, Taxon keeps its own taxonomy and gives the ids of the needed taxons through a contract; **C. other variants**, if they really fit the constraints better (none added only to have more).

For each variant assess: the SQL; the number of database calls; the volume of data passed through Java; the indexes and whether they can be used; pagination; data integrity; behaviour when a Taxon is deleted; behaviour when the Taxon starter is removed or disabled; the number of changed classes, tables and contracts; the difficulty of migrating existing data.

#### 5. All cross-module references

Build the full map of cross-module references in the project, at least for Advertisement, Provider Profile, Taxon, Attachment, Feedback, Promotion and User, and any other module where a dependency turns up. For each reference establish:

| Question | What to establish |
|---|---|
| Entity owner | Which module owns the main entity? |
| Relation owner | Which module stores the association now? |
| Reference format | One id, a pair of values, `EntityRef`, a polymorphic reference? |
| SQL | Is a large id set carried through Java? |
| Integrity | Which FK/UNIQUE/CHECK really exist? |
| Pluggability | What happens if either side is removed? |
| Life cycle | What happens on delete, disable or reconnect of a module? |
| Candidate | Is a local association table, an opaque id or the current approach needed? |

Pay special attention to references where one module stores references to entities of several other modules. Polymorphic references are not automatically bad: decide where they are justified and where they add needless complexity.

#### 6. Life cycle and integrity

For each proposed approach check: what happens to an opaque id after the external entity is deleted; after a Taxon is deleted; whether an id can be reused; whether orphan references are possible; how to clean up without a required dependency between starters; how deleting an entity works when the integration starter is absent; how transactions stay correct; whether partly done operations can occur; whether tombstones, soft delete or stable external identifiers are needed. Separate: (1) guarantees that local database constraints can give; (2) guarantees that a module gives through its own business logic; (3) guarantees possible only when the integration module is present. Do not promise full referential integrity where the database cannot give it without breaking pluggability.

#### 7. Real pluggability tests

Propose a practical test strategy for at least these run profiles: all modules present; Taxon absent; Feedback absent; Attachment absent; Promotion absent; each module absent in turn. For each profile: the Maven build, the Spring context startup, the Liquibase migrations, no queries to missing tables, the available functionality, and the correctness of optional integrations. Assess how to add these checks with the fewest changes to the existing test architecture (unit, Testcontainers, Playwright).

#### 8. Performance and SQL

For the key scenarios compare real SQL plans, not only the theoretical number of queries: `EXPLAIN (ANALYZE, BUFFERS)` where possible, indexes, cardinality, `EXISTS` against `JOIN`, the effect on pagination and sorting, behaviour on large data sets. Do not propose an optimisation only because it looks more elegant; a measured gain or a clear simplification of the model is required.

#### 9. Existing rules and decisions

Before any conclusion read the current project rules and `.claude/nav/adr-index.md`, and in particular the Accepted decision of `taxon-spring-boot-starter` ("Filter resolves through `Set<Long>`, not SQL JOIN") and the decision of `marketplace-app` ("No raw cross-starter SQL joins"), plus the decisions about modularity, transactions, cross-module Ports and DTOs and ownership found through the index. Check that the proposal does not contradict a recorded decision; do not create duplicate decision records or backlog items. Match each problem to the existing backlog; if a task exists, give its ID; if the approach contradicts a decision, state the exact contradiction and what has to change.

#### 10. Expected result

A concrete technical report, not a general overview:

- **A. Executive summary:** do opaque cross-module references suit this project; can they be a general rule; which limits or exceptions are needed.
- **B. Current architecture map:** the real cross-module references with their tables, classes, Ports and SQL.
- **C. Findings**, most important first (needless id passing through Java, wrong ownership, broken pluggability, needless layers and conversions, integrity problems, unjustified cross-module dependencies); each with the exact file path and class/method, the current implementation, the problem, the proposed change, the expected effect, the risks and the complexity.
- **D. Target architecture:** a clear set of rules (when an opaque id, when a local association table, when a Port, when a typed reference model; how to filter in SQL without large intermediate sets; how to keep integrations optional; how to treat deletion and stale references).
- **E. Taxon migration plan:** the order of changes, data migration, compatibility and checks, or a reasoned decision to keep the current implementation.
- **F. Broader migration plan:** which other modules need the same change and which stay as they are.
- **G. Rule and decision proposals:** the current mandatory rules, the historical decisions, and the ones that need review.
- **H. Prioritized implementation batches:** small independent steps with risk and dependencies.
- **I. Rejected alternatives**, each checked for real use: a global Entity Registry; required cross-starter foreign keys; shared views or functions that depend on other starters; `BIGINT[]` instead of normalised relations; passing large id sets through Java; events, Outbox or CQRS without a concrete need.

#### 11. Limits of the run

Research and review only. Do not change code; do not create tables, migrations, classes or decision records; do not edit the backlog; do not run automatic refactors. Do not invent classes, tables, scenarios or constraints — confirm everything with the code, and tie every recommendation to a place in the repository. Do not propose large rewrites for architectural purity. Keep `marketplace-orchestrator` in its current role. Priority: minimal changes, maximum decoupling, flexibility, readability and SQL efficiency. If the current implementation is better than an alternative, say so with the evidence; if an approach works only for part of the modules, state the limits of its use. First do the full research and write the report and the plan, then stop and wait for a separate approval of any change.

## Related

- The Accepted decision of `taxon-spring-boot-starter`, "Filter resolves through `Set<Long>`, not SQL JOIN" (find it through `.claude/nav/adr-index.md`); its trigger to revisit is the case this research measures.
- The decision of `marketplace-app` "No raw cross-starter SQL joins — bulk-lookup port + service-level enrichment" (find it through the same index).
- [improvement-244](improvement-244-research-wiki-or-keep-architecture-control-plane.md) and [improvement-248](improvement-248-ai-instructions-tighten-and-doc-sync-remaining.md): other open research and tooling work; no overlap in scope.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
