# Brief: db-normalization-module-decoupling

Purpose: what the planner is asked to find out and plan; written by the main thread, read by the planner.

## Goal

Find out, from the web and from this project's own practice, which patterns let the database be normalized while every module still talks to the others only through one API (its `*Port`), with no loss of functionality. The target is quality first: the fewest changes, the most decoupling, the most flexibility. The plan may also improve the project's rules and decision records where they stand in the way or have gaps.

## Task

none yet (related: `backlog/tasks/improvement-240-sync-docs-refactor.md` holds a finding about the same area, the no-FK marker of the Database ERD)

## Context

The project is a modular monolith: Java, Spring Boot, pure SQL through `JdbcClient` (no JPA), Liquibase for every schema change. Each domain starter owns its own tables and its own changelog; no starter may depend on another starter; optional starters are reached through `ComponentFactory<XPort>` against interfaces in `platform-commons`; cross-domain use cases are composed in `marketplace-orchestrator`, which never touches SQL.

How the data is tied together today (verified in the code and in the generated model of the Database ERD):

- Real foreign keys exist in only two places, both inside one module: the feedback tables (content, entry, rating, reply) and the taxon tables (translation, assignment).
- Everything that crosses a module has no foreign key. A polymorphic pair `entity_type` + `entity_id` names the owner (an advertisement, a provider profile, a user, a taxon) in the attachment, attachment snapshot, audit log, contact, feedback, feedback aggregate, taxon assignment and promotion placement tables. An actor reference (`created_by`, `updated_by`, `deleted_by`, `author_id`, `actor_id`, and similar) names a user without a foreign key.
- The recorded reason: the attachment starter was made generic over the owner, so a gallery can belong to any entity without a schema change; starters must run without their neighbours, so a foreign key into another module's table was not allowed.
- Consequences already seen: rows left without an owner (the reviews of a deleted provider profile stayed until a cascade was added in the orchestrator's delete transaction); the integrity of these links is held by application code only; a link appears on the Database ERD only if the column's `remarks=` carries the fixed phrase `References <table>(<column>), no FK`, and nothing checks that phrase; one link is written by hand in the generator.
- Planned growth: avatars and many more kinds of attachments, owned by different entity types.
- The attachment tables hold different data (current files, one row per file; history of the gallery, one row per change) and share only the owner pair, so the question is the owner link, not duplicated data.

Options named in the conversation, none decided: a link table per owner type with real foreign keys; one registry table of owners that the other tables point to; keeping the owner pair and adding a purpose column (gallery, avatar, and so on). The first two need a table in one module to reference a table in another, which the rule about starter independence forbids today.

The user's own wording of the goal: a normalized database, modules that talk through one API, functionality unchanged, minimal changes, maximum decoupling and flexibility.

## Where to look in the project

- The changelogs: `<module>/src/main/resources/db/*-changelog/changes/*.xml` of every starter, and each column's `remarks=`.
- The contracts: `platform-commons` (`core.model` for `EntityRef`/`EntityType`, and every `*.spi` port).
- The rules: `CLAUDE.md` (architecture guidelines 2, 3, 6, 7), `.claude/rules.md`, `.claude/rules/*.md` of each starter, `.claude/rules/platform-commons.md`, `.claude/skills/module-doc-standards/SKILL.md` (the no-FK marker section).
- The decisions: `.claude/nav/adr-index.md`, filtered for the decoupling of attachment, audit and feedback, the generic owner, the no-FK marker and the orchestrator's delete cascade; read single entries with `node .claude/nav/scripts/md-to-decisions-json.js --extract <module> <ADR-id>`.
- The cascades and composition: `marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/` (`ProviderProfileSaveService`, `AdvertisementSaveService`, `UserDeleteService`, `UserCleanupService`, `UserPurgeEligibilityService`).
- The diagram generator: `docs/architecture/scripts/generate-architecture-model.sh` and `docs/architecture/scripts/liquibase-schema-to-json.js`.
- The architecture tests: `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`.

## Completion criterion

Two plans, one for the people who decide and one for the AI executors, that:

1. compare at least three real approaches found in the sources (patterns for polymorphic ownership, ways to keep integrity across module boundaries, modular-monolith practice for data ownership), each with its cost in changes, its effect on decoupling and flexibility, and the sources behind it;
2. say which approach fits this project's own constraints best and why, and what each gives up;
3. show how functionality stays unchanged: the migration steps, the order, how data already in the tables is carried over, and how each step is verified;
4. list every change the rules and the decision records would need, as a before and after;
5. name every conflict with the project's current rules or accepted decisions, and every point where the sources disagree.

## Decisions already taken

- Pure SQL through `JdbcClient`, no JPA; every schema change through Liquibase.
- Modules are reached only through `*Port` interfaces in `platform-commons`; the orchestrator composes cross-domain use cases and never touches SQL.
- No code, comment or document of the repository is written in another language than English; no commit is made by the research.
- The functionality of the application must not change.

## What the planner may decide

Nothing beyond the goal. A conflict with best practice or with the project's rules (for example a foreign key across modules against the rule of starter independence) is highlighted with the evidence, and the decision is left to the user.

## Available executor models

haiku, sonnet, opus
