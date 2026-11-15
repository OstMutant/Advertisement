# Brief: pluggable-modules-opaque-refs

Purpose: what the planner is asked to find out and plan; written by the main thread, read by the planner.

## Goal

Find, on the real code, the one practical approach to cross-module references that gives together: strong module decoupling, full pluggability (any starter removable from `pom.xml` without breaking build, Liquibase, startup or the remaining modules), database-level efficiency (filter, sort, aggregate and paginate in PostgreSQL, not by carrying large id sets through Java), minimal complexity, and data ownership. Test the hypothesis "pluggable modules with opaque cross-module references", starting from the Taxon filter flow. The hypothesis must not be confirmed at any cost: if the current model is better, say so with evidence; if the approach fits only some modules, give the limits.

## Task

[improvement-249](../../tasks/improvement-249-pluggable-modules-opaque-cross-module-references-research.md) -- its section "Research brief" (points 1 to 11) is the binding text of this brief: the goal, the hypothesis, the hard constraints, the Taxon flow and the variants A/B/C to compare, the map of all cross-module references, the life-cycle and integrity questions, the pluggability test profiles, the SQL-plan measurements, the check against existing rules and decisions, the report parts A to I, and the limits of the run. Read it in full first; this file adds only what the task file does not say.

## Context

- The user's original brief is the same text in Ukrainian; the task file is its English form. Where the task file says "find it through `.claude/nav/adr-index.md`", the two decisions the user named are: the Accepted decision of `taxon-spring-boot-starter` "Filter resolves through `Set<Long>`, not SQL JOIN", and the Accepted decision of `marketplace-app` "No raw cross-starter SQL joins -- bulk-lookup port + service-level enrichment". The user also asked to read the decisions about modularity, transactions and cross-module Ports and DTOs, and a decision the user remembers as number 005 -- no module is known for it; find the relevant ones through the index and say which you read.
- `marketplace-orchestrator` was introduced deliberately: do not move or redesign it. No split of PostgreSQL into several databases without a concrete need. The explicit-SQL, no-ORM model stays.
- Research and review only: do not change code, tables, migrations, classes, decision records or the backlog. The report and the plan are the only output.
- Measure, do not guess: for the key scenarios take real SQL plans (`EXPLAIN (ANALYZE, BUFFERS)`) on a real PostgreSQL with data of realistic size (the project's integration tests use Testcontainers; the helper scripts and container rules are in `.claude/rules/scripts.md` and `.claude/rules/integration-tests.md`). Any container or database you start for this is disposable and must be removed; never touch the project's running dev stack, its volumes or its database. If a measurement cannot be taken, say so and mark the figure as unverified.

## Where to look in the project

- `taxon-spring-boot-starter`: `TaxonAssignment`, `TaxonAssignmentRepository.findEntityIdsByTaxonIds`, `DefaultTaxonPort`, its Liquibase changelog and its `DECISIONS.md`.
- `advertisement-spring-boot-starter`: `AdvertisementService.resolveTaxonFilter()`, `AdvertisementRepository` (`ANY(:allowedIds)`), its changelog.
- `provider-profile-spring-boot-starter`: `ProviderProfileService.resolveTaxonFilter()`, `ProviderProfileRepository`, its changelog remarks.
- `platform-commons`: every `*Port`/`*Hook` under `<domain>/spi/`, `EntityRef`, `EntityType`; its `DECISIONS.md`.
- Every starter's Liquibase changelog (`<starter>/src/main/resources/db/*-changelog/`) for foreign keys, tables and constraints; the `pom.xml` of each starter and of `marketplace-app` for dependencies and optional wiring; `@AutoConfiguration` classes for conditional beans.
- `marketplace-app/DECISIONS.md`, `.claude/nav/adr-index.md`, `.claude/rules.md`, the matching `.claude/rules/*.md`, `integration-tests/` (the tests that exist), `backlog/BACKLOG.md` and `backlog/tasks/` (to match each finding with an existing task and give its id).

## Completion criterion

`plan-human.md` holds the report parts A to I of the task file's point 10, each finding with file path, class and method; `plan-machine.md` holds the prioritized implementation batches (point 10 H) as numbered steps with executor models, none executed, plus the proposed pluggability test profiles (point 7). Every claim about code is confirmed against the file; every SQL figure comes from a real run or is marked unverified. The decision between variants is written as a proposal.

## Decisions already taken

- The constraints of point 3 of the task file's brief (no cross-starter foreign keys, views or functions without separate justification; no global entity registry, `BIGINT[]` replacement, events, outbox, CQRS or materialised views only to remove this problem; no large rewrites) are criteria, not options.
- No code, migration, ADR or backlog change before the user separately approves it.

## What the planner may decide

Nothing beyond the goal. Which extra variants (C) to examine, and which modules to include in the reference map beyond the named ones, is the planner's choice. The choice between the variants, the general rule and the batches are proposed, never decided; any conflict with a recorded decision is highlighted with the exact contradiction and what would have to change.

## Available executor models

haiku, sonnet, opus
