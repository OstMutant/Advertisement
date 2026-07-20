# improvement-087: AuditLogRepository — prev-snapshot subqueries and getLastSnapshot() still lack the `id` tiebreaker improvement-050 added to version numbering

**Type:** bug — silent audit-diff corruption on same-timestamp rows; leftover of the same defect
class improvement-050 item 4 fixed. Found via pattern-focused code review (2026-07-19).
**Module:** `audit-spring-boot-starter` (`repository/AuditLogRepository.java`)
**Priority:** high — same SILENT-CORRUPTION class improvement-050 documented; one-line SQL fixes;
`getLastSnapshot()` feeds restore flows
**When:** independent, no blockers — do first in this batch

## Problem

improvement-050 item 4 (fixed 2026-07-15) added an `id` tiebreaker — `(b.created_at, b.id) <=
(f.created_at, f.id)` — to the two `version`-numbering subqueries, after proving that
same-`created_at` rows for one entity are possible (Postgres `NOW()` is transaction-fixed, so two
audit writes in one transaction tie exactly). But three sibling queries in the same file were left
with the old, tiebreaker-less shape:

1. `findTimeline()`'s `prev_id` subquery — `WHERE b.created_at < f.created_at ORDER BY
   b.created_at DESC LIMIT 1` (strict `<`, no `id`).
2. `findTimeline()`'s `prev_snapshot_data` subquery — identical shape.
3. `getLastSnapshot()` — `ORDER BY created_at DESC LIMIT 1` (no `id` tiebreaker).

Consequences for two tied rows of the same entity:
- The later row (version 2 by the fixed tiebreak) gets a `prev_*` that **skips its true
  predecessor** (strict `<` excludes the tied row) — its Timeline diff is computed against the
  wrong snapshot (or `null`), while its version number says 2. `findRows()` (Activity tab) uses
  window functions with `ORDER BY created_at, id` and is correct — so Activity and Timeline can
  show *different diffs for the same audit row*.
- `getLastSnapshot()` returns a nondeterministic "last" snapshot — this is the restore-flow
  entry point (`DefaultAuditPort`), so a restore may silently start from the wrong state.

## Suggested fix

Align all three with the already-fixed shape:
- `prev_id` / `prev_snapshot_data`: `WHERE (b.created_at, b.id) < (f.created_at, f.id) ORDER BY
  b.created_at DESC, b.id DESC LIMIT 1`.
- `getLastSnapshot()`: `ORDER BY created_at DESC, id DESC LIMIT 1`.

Extend `integration-tests/.../audit/AuditLogRepositoryTest` (built by improvement-050 item 4, which
already inserts tied rows directly via `jdbcClient`) with assertions on `prev_id`/
`prev_snapshot_data` and `getLastSnapshot()` — TDD against the pre-fix code, same as item 4 did.

## Related

- `backlog/completed/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md` item 4 —
  the half of this fix that already shipped, including the tied-row test technique to reuse.
- `backlog/issues/improvement-019-findtimeline-correlated-subqueries.md` — the window-function
  rewrite would make this whole class of drift impossible (one `ORDER BY created_at, id` in one
  place); if 019 is done first, this issue reduces to just the `getLastSnapshot()` fix.
