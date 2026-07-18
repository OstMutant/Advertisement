# improvement-067: `TaxonTranslationRepository.findAllByTaxonIds()` uses an unbounded `IN (:taxonIds)` clause

**Type:** improvement — scalability, same class of issue already fixed elsewhere in the codebase.
Found via direct code review, verified against current source (2026-07-16).
**Module:** `taxon-spring-boot-starter` (`repository/TaxonTranslationRepository.java`).
**Priority:** medium — no bug at today's taxon-count, same risk profile improvement-054 already
fixed for `TaxonAssignmentRepository`/`AttachmentRepository`; the fix is a proven, one-line-per-query
pattern already used elsewhere in this codebase.
**When:** independent, no blockers.

## Problem

`TaxonTranslationRepository.findAllByTaxonIds()`:
```java
public List<TaxonTranslation> findAllByTaxonIds(@NonNull Collection<Long> taxonIds) {
    return jdbcClient.sql("... WHERE taxon_id IN (:taxonIds)")
                     .paramSource(new MapSqlParameterSource("taxonIds", taxonIds))
                     .query(ROW_MAPPER)
                     .list();
}
```
passes `taxonIds` straight into an `IN (:taxonIds)` clause — Spring's named-parameter JDBC expands
this into one `?` placeholder per element at query time. `improvement-054` fixed this exact pattern
in `TaxonAssignmentRepository`/`AttachmentRepository` for the same reason (Postgres's ~65535
parameter-per-query limit, and query-plan cache degradation from a different placeholder count on
every call). This method wasn't touched by that fix and still has the unbounded form. Called
whenever a large batch of taxon entries needs their translations loaded at once (e.g. via
`TaxonPort.listAllByType(..., includeDeleted=true)` on a large taxonomy).

## Suggested fix

Apply the same fix improvement-054 already established: switch the SQL to `WHERE taxon_id =
ANY(:taxonIds)` (Postgres native array parameter, one placeholder regardless of collection size)
and pass `taxonIds.toArray(new Long[0])` (or the equivalent array-typed parameter) instead of the
raw `Collection`.

## Related

- `backlog/completed/issues/improvement-054-unbounded-in-clause-taxon-assignment-attachment.md` —
  the original fix this issue extends to a method it missed, same root cause and same fix shape.
- `advertisement-spring-boot-starter/src/main/java/org/ost/advertisement/repository/AdvertisementRepository.java`
  and `attachment-spring-boot-starter/.../AttachmentRepository.java` — existing `= ANY(:ids)`
  usages to match.
