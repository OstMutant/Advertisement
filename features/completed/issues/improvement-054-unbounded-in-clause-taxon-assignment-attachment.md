# improvement-054: Unbounded `IN (:set)` in `TaxonAssignmentRepository.findAllByEntities()` and `AttachmentRepository.deleteByUrls()` — ✅ DONE (2026-07-15)

**Type:** bug fix (performance / scalability), same bug class already fixed once.
**Module:** `taxon-spring-boot-starter` (`TaxonAssignmentRepository.findAllByEntities()`),
`attachment-spring-boot-starter` (`AttachmentRepository.deleteByUrls()`).
**Priority:** medium — same reasoning as the fix this duplicates: no observed production incident,
but a known parameter-count/query-plan-cache risk at scale, and now a second confirmed instance of
the same bug shape.
**When:** independent, no blockers.

## Problem

Found while writing `TaxonAssignmentRepositoryTest`/`AttachmentRepositoryTest`
([improvement-027](../completed/issues/improvement-027-unit-testcontainers-test-layer.md) Batch 3,
2026-07-15) — flagged there but deliberately left unfixed (test-coverage scope, not a second
performance pass). This is that follow-up.

[improvement-050](../completed/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md)
item 2 already fixed the exact same shape in `AdvertisementRepository.buildIdClause()`: Spring's
`NamedParameterJdbcTemplate` expands a `Collection`-typed bind value into one `?` placeholder per
element, so `IN (:set)` grows unboundedly with the input set's size — real risk of hitting
PostgreSQL/JDBC parameter-count limits and of query-plan-cache thrashing (SQL text shape changes
per list size). Two more instances of the same pattern exist, un-fixed:

### 1. `TaxonAssignmentRepository.findAllByEntities()`
`/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/repository/TaxonAssignmentRepository.java:86-97`

```java
public List<TaxonAssignment> findAllByEntities(@NonNull String entityType, @NonNull Set<Long> entityIds) {
    return jdbcClient.sql("""
                    SELECT entity_type, entity_id, taxon_id, assigned_at, assigned_by
                    FROM taxon_assignment
                    WHERE entity_type = :entityType AND entity_id IN (:entityIds)
                    """)
                     .paramSource(new MapSqlParameterSource()
                             .addValue("entityType", entityType)
                             .addValue("entityIds",  entityIds))
                     .query(ROW_MAPPER)
                     .list();
}
```

Called from `TaxonAssignmentService` (`/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/TaxonAssignmentService.java:73`)
with a page's worth of advertisement IDs — same shape and same caller pattern
(`AdvertisementService.enrichWithCategories()`-style bulk lookup for a list render) as the already
-fixed `buildIdClause()` case.

### 2. `AttachmentRepository.deleteByUrls()`
`/app/attachment-spring-boot-starter/src/main/java/org/ost/attachment/repository/AttachmentRepository.java:155-159`

```java
public int deleteByUrls(@NonNull List<String> urls) {
    return jdbcClient.sql("DELETE FROM attachment WHERE url IN (:urls)")
                     .paramSource(new MapSqlParameterSource("urls", urls))
                     .update();
}
```

Called from `AttachmentCleanupService` (`/app/attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentCleanupService.java:68`)
with the batch of URLs a single scheduled cleanup run selected for deletion — grows with however
many attachments crossed the retention threshold since the last run, unbounded by anything today.

## Suggested fix

Reuse the same fix already proven for `AdvertisementRepository.buildIdClause()` (ADR-036, see
`marketplace-app/DECISIONS.md`) rather than inventing a new approach: bind a plain array
(`Long[]`/`String[]`) via Postgres `= ANY(:param)` instead of a `Collection` via `IN (:param)`.
Spring passes a native array through as a single JDBC parameter regardless of element count, so
this removes the parameter-count risk without a JOIN-based rewrite or any change to caller code
(`Set<Long>`/`List<String>` in, `.toArray(new Long[0])` / `.toArray(new String[0])` at the query
boundary, same as the existing fix).

1. `TaxonAssignmentRepository.findAllByEntities()`: `entity_id IN (:entityIds)` →
   `entity_id = ANY(:entityIds)`, bind `entityIds.toArray(new Long[0])`.
2. `AttachmentRepository.deleteByUrls()`: `url IN (:urls)` → `url = ANY(:urls)`, bind
   `urls.toArray(new String[0])`.

## Verification plan

- Extend the existing `TaxonAssignmentRepositoryTest.findAllByEntities_...` /
  `AttachmentRepositoryTest.deleteByUrls_...` tests (already exist from improvement-027 Batch 3) —
  confirm behavior is unchanged for the normal case (small sets), no new test strictly required to
  prove the array-bind fix itself since `AdvertisementRepositoryTest`'s ADR-036 tests already cover
  that mechanism; a large-set test is optional (not proving anything ADR-036 didn't already prove).
- `bash scripts/integration-tests.sh --sandbox TaxonAssignmentRepositoryTest` and
  `... AttachmentRepositoryTest` — both must stay green.

## Resolution (2026-07-15)

Both fixed exactly as suggested above — `entity_id = ANY(:entityIds)` /
`url = ANY(:urls)`, array-bound instead of `Collection`-bound. No caller-side changes needed.
`TaxonAssignmentRepositoryTest` 8/8, `AttachmentRepositoryTest` 8/8, full `integration-tests`
suite 83/83.

## Related

- [improvement-050](improvement-050-toctou-scalability-locale-audit-tiebreak.md)
  item 2 — the original fix this duplicates, plus `marketplace-app/DECISIONS.md` ADR-036 (full
  writeup: why `= ANY()` over `unnest()`, the Postgres-coupling tradeoff, the array size ceiling
  that replaces the removed parameter-count ceiling).
- [improvement-027](improvement-027-unit-testcontainers-test-layer.md) — where
  this was found, while writing Batch 3's repository tests.
