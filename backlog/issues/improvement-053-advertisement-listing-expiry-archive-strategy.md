# improvement-053: Advertisement listing expiry / archive strategy — design discussion, no agreed fix yet

**Type:** feature design — captures a design discussion and research findings, not an agreed
implementation. Same shape as [improvement-046](improvement-046-list-stability-under-concurrent-edits.md)
("captures a design discussion, not an agreed fix").
**Module:** `advertisement-spring-boot-starter` (`advertisement` table, `AdvertisementRepository`).
**Priority:** low — no current bug, no current volume problem (see "Why deferred" below). Filed now
so the design isn't lost, not because it's urgent.
**When:** trigger-based — see "Trigger to revisit" below. Do not implement any of the options
listed here until that trigger fires.

## Problem

Raised during [improvement-050](completed/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md)
item 2's discussion (2026-07-15): `advertisement` currently has no listing lifecycle beyond
soft-delete (`deleted_at`/`deleted_by`) — every non-deleted row, regardless of age, stays in the
same active table and is scanned/filtered/sorted by every list query forever. The user's framing:
accumulating tens of thousands of ads in a relatively short window (e.g. ~50,000/month) is a
realistic scale to plan for, and at that scale an "active" listings table with no expiry/archival
boundary will keep growing unbounded, degrading list-query and index performance over time even
with proper indexing.

This is explicitly **not** the same problem improvement-050 item 2 solved — that was a
parameter-count/query-shape risk in one filter clause, already fixed via `= ANY()` array binding.
This issue is about the underlying **data volume growth problem** that fix didn't (and wasn't
meant to) address: what happens to old/expired listings over time, independent of any single
query's parameter binding.

## Research: how other systems handle this (2026-07-15)

Two related but distinct concerns, both real for a classifieds/marketplace domain specifically:

**1. Listing expiry as a product feature** (a listing has a natural "shelf life" — after N days
it should stop appearing in active search/browse, independent of storage concerns). Common
industry pattern: an explicit `valid_for`/expiry timestamp computed as `create_date +
post_validity_interval_in_days`, filtered out of active listing queries once past. This is a
product/UX decision (what should "expired" mean to a seller/buyer — auto-relist? notify the
seller? hide but keep visible in "my listings"?) as much as a data design one.

**2. Data archiving as a scalability strategy** (once a listing is no longer "active" by whatever
rule #1 defines, where does its row live, and does query performance for the *active* set benefit
from moving it elsewhere). Three broad strategies, in increasing order of implementation cost and
decreasing order of long-term scalability:
   - **Status/flag column + filtered queries** (cheapest): add e.g. `expires_at` or a
     `status`/`is_archived` column to the existing table; every active-listing query adds a
     `WHERE`/index-partial-predicate. Simplest to implement (this project already has the
     `deleted_at IS NULL` pattern for exactly this shape), but doesn't reduce the physical table's
     row count or index size — the "active" working set gets smaller relative to total rows, but
     the full table (including archived rows) is still one physical structure everything scans
     through unless a partial index specifically targets the active subset.
   - **Separate archive table**: move expired rows out of `advertisement` into a parallel
     `advertisement_archive` table (via a scheduled job or trigger). Keeps the hot table small and
     fast; archived data is still queryable (e.g. "my past listings") via a UNION or a separate
     query path, at the cost of a second schema to keep in sync and a data-movement job to write
     and maintain.
   - **Postgres native table partitioning** (highest long-term payoff at real scale): partition
     `advertisement` by a time dimension (e.g. `created_at` monthly/quarterly range partitions).
     Dropping/detaching an old partition is near-instant regardless of row count (vs. a `DELETE`
     of millions of rows, which is slow, locks pages, and needs `VACUUM` to reclaim space); Postgres
     can also skip scanning partitions that don't match a query's date range (partition pruning),
     which speeds up time-bounded queries even before anything is archived/dropped. Highest
     implementation cost: requires the partition key to be part of every unique constraint/PK
     (a real schema change), a partition-maintenance strategy (manual, or `pg_partman` for
     automatic partition creation/retention), and is harder to retrofit onto a table that's already
     live with data than to design in from the start.

None of the three is a strict "correct" answer independent of the actual access pattern this
project ends up needing (is "my past listings" a real feature? does the active browse/search query
ever need to reach into old data? how often does archived data get read vs. just retained for
audit/compliance?) — that access pattern isn't established yet, which is exactly why this is
deferred rather than decided now.

## Why deferred

No current volume problem — the actual current `advertisement` row count is far below any scale
where table size affects query performance (confirmed no evidence of this being a live issue
today). Designing and implementing any of the three strategies above now would be speculative:
optimizing for a data volume that doesn't exist yet, before the product-level expiry rule (which
directly shapes *what* gets archived and *when*) is even decided. Consistent with this project's
"Don't design for hypothetical future requirements" guidance (`CLAUDE.md`).

## Trigger to revisit

Revisit when either:
- Real `advertisement` row count/growth rate approaches a scale where list-query latency is
  measurably affected (the user's own framing: sustained growth toward the tens-of-thousands/month
  range), or
- A product decision is made about what "listing expiry" should mean to sellers/buyers (independent
  of scale) — at that point, the archival *storage* strategy becomes a much narrower engineering
  question once the *access pattern* (how often, by whom, is expired/archived data actually read)
  is known.

At that point, re-evaluate the three storage strategies above against the now-known access pattern
and real data volume — this issue is a starting point for that discussion, not a spec to implement
as-is.

## Related

- [improvement-050](completed/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md)
  item 2 / `marketplace-app/DECISIONS.md` ADR-036 — the immediate parameter-binding fix that
  prompted this discussion; solves a different, narrower problem (one query's parameter shape, not
  overall table growth).
- [improvement-052](issues/improvement-052-first-admin-registration-toctou-race.md) — same
  "extracted from a discussion, deliberately deferred with an explicit trigger" shape.

## Sources consulted (2026-07-15)

- [Designing an online classifieds data model](https://www.red-gate.com/blog/designing-an-online-classifieds-data-model/) — Red-Gate: `post_validity_interval_in_days` / expiry pattern.
- [Optimizing Data Archiving Strategies](https://medium.com/pipedrive-engineering/optimizing-data-archiving-strategies-a-comprehensive-guide-to-smarter-database-management-7f15d8fd5c52) — Pipedrive Engineering: partitioning, separate archive tables, archive-field-on-existing-table as the three cornerstone strategies.
- [Data Archiving Strategy — GeeksforGeeks](https://www.geeksforgeeks.org/data-engineering/data-archiving-strategy/)
- [Data archiving and retention in PostgreSQL — Data Egret](https://dataegret.com/2025/05/data-archiving-and-retention-in-postgresql-best-practices-for-large-datasets/) — partition-drop vs. row-delete performance.
- [Auto-archiving and Data Retention Management in Postgres with pg_partman — Crunchy Data](https://www.crunchydata.com/blog/auto-archiving-and-data-retention-management-in-postgres-with-pg_partman)
- [PostgreSQL: Documentation: Table Partitioning](https://www.postgresql.org/docs/current/ddl-partitioning.html)
