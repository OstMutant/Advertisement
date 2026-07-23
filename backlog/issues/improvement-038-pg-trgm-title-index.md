# improvement-038: `pg_trgm` trigram index — title search is a full table scan

**Type:** improvement — performance, no code changes needed. Migrated from `backlog/process-
improvements.md` Part 3, item 19.
**Module:** `advertisement-spring-boot-starter` (Liquibase changeset only)
**Priority:** low now, rising with data volume — first real performance cliff as the advertisement
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

## Related

- `backlog/process-improvements.md` Part 3, item 19 — source item, now superseded by this issue.
- `query-lib/CLAUDE.md` — documents `SqlCondition.like()`/`LIKE_IGNORE_CASE`, the operator this
  index is specifically for.
