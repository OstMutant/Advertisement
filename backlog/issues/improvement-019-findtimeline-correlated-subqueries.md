# improvement-019: findTimeline uses 3 correlated subqueries per row — sibling query already does it right

**Type:** improvement — SQL performance, found by external code audit (round 6), verified
**Module:** audit-spring-boot-starter
**Priority:** medium — fine at current scale; first audit-side bottleneck at growth; fix is cheap
**When:** Wave 3 / opportunistic — bundle with any audit-starter touch

## Problem

`AuditLogRepository.findTimeline()` computes `version`, `prev_id`, and `prev_snapshot_data`
via three correlated subqueries executed per returned row:

```sql
SELECT f.*,
       (SELECT COUNT(*) FROM audit_log b WHERE ... AND b.created_at <= f.created_at) AS version,
       (SELECT id FROM audit_log b WHERE ... ORDER BY b.created_at DESC LIMIT 1)     AS prev_id,
       (SELECT snapshot_data::text FROM audit_log b WHERE ... LIMIT 1)               AS prev_snapshot_data
FROM filtered f
```

A 20-row timeline page = 60 point lookups; the `COUNT(*)` with `created_at <=` inequality is
the worst of the three. External stress-test analysis names this the first failing subsystem
at scale (large logs × OFFSET pagination × per-row subqueries).

**The fix already exists in the same file:** the sibling entity-history query
(`AuditLogRepository.java:105-107`) computes the same three values with window functions —
`ROW_NUMBER() OVER (PARTITION BY entity_type, entity_id ORDER BY created_at, id)` and
`LAG(...) OVER (...)`. `findTimeline` simply predates that approach.

## Suggested fix

Rewrite `findTimeline` to the sibling's window-function pattern (compute version/prev via
`ROW_NUMBER`/`LAG` in the CTE before LIMIT/OFFSET, partitioned by entity). No triggers, no
materialized columns, no schema change — the external reviewer's heavier suggestions are not
needed at this scale.

Note: window functions over the full per-entity partition still cost more as `audit_log`
grows — the escalation path (retention → partitioning) is tracked in improvement-003 item I;
keyset pagination in item E.

## When

Bundle with any audit-starter touch; no urgency at current volume.
