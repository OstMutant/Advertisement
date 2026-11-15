# improvement-038: `pg_trgm` trigram index — title search is a full table scan

**Type:** improvement — performance, no code changes needed. Migrated from `backlog/process-
improvements.md` Part 3, item 19.
**Module:** `advertisement-spring-boot-starter` (Liquibase changeset only)
**Priority:** 🔵 low now, rising with data volume — first real performance cliff as the advertisement
table grows past what a full scan tolerates comfortably
**When:** trigger-based — do when advertisement row count starts making title search noticeably
slow, or opportunistically alongside any other advertisement-schema touch; not urgent at current
seed-data volumes (tested up to 50 rows this session)

## Problem

`query-lib`'s `SqlOperator.LIKE_IGNORE_CASE` renders `ILIKE :param` with the value wrapped in
leading and trailing `%` (`like(mapping, value)` → `'%value%'`). A leading wildcard `ILIKE`
pattern cannot use a standard B-tree index — every title search is a full table scan today,
currently invisible because table sizes are tiny (dev/test data), but this is the first query
that will visibly degrade as real listing volume grows.

## Suggested fix

- `CREATE EXTENSION pg_trgm` + a GIN trigram index on `advertisement.title`, added as a Liquibase
  changeset in `advertisement-spring-boot-starter`'s own changelog (per the project's rule that
  starters own their own changelogs).
- Full-text search (`tsvector`) is explicitly **not** needed yet per the source audit — only
  revisit if description search (not just title) becomes a requirement; trigram indexing solves
  the `ILIKE '%x%'` pattern specifically, which is what's actually used today.
- Note (from the source audit): PostgreSQL is currently pinned at 15-alpine in
  `docker-compose.db.yml`; current stable is 17 — bump opportunistically if touching DB
  infrastructure for this anyway, not required for the trigram index itself (pg_trgm has worked
  since PG 9.1).

## Plan (2026-11-11)

Checked against the code: `SqlOperator.LIKE_IGNORE_CASE` is still `ILIKE ... ESCAPE '\\'`, `AdvertisementRepository` filters `a.title` with it, no trigram index exists, the starter has one change file with a plain B-tree `idx_advertisement_title` (useless for a leading wildcard, kept: it can serve a sort by title), and the compose file the text names now lives in `scripts/deploy-and-run/` (the image tag comes from the root `.env`).

1. Decision 2026-11-11 (the user): the database is not in production, so the base change file is edited, no new one. In `advertisement-spring-boot-starter/src/main/resources/db/advertisement-changelog/changes/01-advertisement-schema.xml`, after the existing indexes: `CREATE EXTENSION IF NOT EXISTS pg_trgm` and `CREATE INDEX idx_advertisement_title_trgm ON advertisement USING GIN (title gin_trgm_ops)` as `<sql>` (Liquibase's `createIndex` has no operator class). Consequence: the changeset carries `validCheckSum ANY`, so a changed text is not rejected, but a database that already ran it does not get the index until it is reset: the development database with `deploy-and-run.sh --reset`, the CI end-to-end stack with `ci.sh --reset-e2e-stack`; the Testcontainers databases are new on every run.
2. Test `AdvertisementTitleSearchIndexTest` in `integration-tests` (`level1.advertisement`, real Postgres): load a few thousand advertisements with generated titles, `ANALYZE`, then `EXPLAIN` the exact clause the search uses (`a.title ILIKE :title ESCAPE '\\'` with `%needle%`) with sequential scans switched off inside the transaction, and assert the plan uses `idx_advertisement_title_trgm`; a second assertion checks the matching row comes back for a case-insensitive substring. Without the index the plan can only be a sequential scan, so the test fails; checked by leaving the index out for one run.
3. Privilege note for the task text: the extension is created by the database owner (trusted since PostgreSQL 13); the development and CI containers run as the superuser, so no setup is needed there.
4. After: `/deep-review` and `/sync-docs` on the change file and the test, the full `/ci` (the change runs on the real database of the end-to-end stage), task closed, index and map regenerated.

Not done: indexes for the same operator on users (`name`, `email`) and feedback (`reasonText`); they are smaller tables and stay out of this task.

## Related

- `backlog/process-improvements.md` Part 3, item 19 — source item, now superseded by this issue.
- `query-lib/CLAUDE.md` — documents `SqlCondition.like()`/`LIKE_IGNORE_CASE`, the operator this
  index is specifically for.

## Result (2026-11-11)

The base change file `01-advertisement-schema.xml` creates `pg_trgm` and `idx_advertisement_title_trgm` (GIN, `gin_trgm_ops`) on `advertisement.title`, as `sql` because `createIndex` has no operator class. `AdvertisementTitleSearchIndexTest` (two cases) passes; with the index line taken out for one run, the plan is a sequential scan and the plan case fails. A database that already ran the changeset keeps no index until it is reset (the changeset carries `validCheckSum ANY`). Not done: the same index for the user (`name`, `email`) and feedback (`reasonText`) searches.

## Operational notes
- token_cost_review: 61135
- token_cost_research: n/a
- token_cost_verification: 92441
- review_signal_ratio: 0 / 0
- context_loading_task_type: Feature, single module
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: review of the diff, doc sync after code change, schema change
- flows_chosen: /deep-review, /sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 1
- haiku_decomposition_rework_needed: 0 / 1
- adrs_referenced: none

### Agent calls
- diff review | subagent_type=deep-review-orchestrator | tokens=61135 | tool_uses=7 | duration_s=151 | mode=background | batch=solo
- documentation audit of the new test and the change | subagent_type=general-purpose (haiku) | tokens=92441 | tool_uses=11 | duration_s=103 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh, one test class, two runs (the first run and the control without the index) | duration_s=331 | mode=background | result=pass
