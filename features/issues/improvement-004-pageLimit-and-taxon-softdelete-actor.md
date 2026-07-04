# improvement-004: Extract pageLimit() to query-lib + add actorId to TaxonRepository.softDelete()

**Type:** improvement — deduplication + audit consistency
**When:** Wave 1 — bundle with the taxon repository touch (improvement-007, same PR)

---

## Part A — Extract `pageLimit()` to `query-lib`

**Problem:** Identical private static method duplicated in two repositories:
- `user-spring-boot-starter/.../UserRepository.java:132–137`
- `advertisement-spring-boot-starter/.../AdvertisementRepository.java:142–147`

**Fix:**
1. Create `query-lib/src/main/java/org/ost/query/sort/PaginationSqlBuilder.java`:
   ```java
   @NoArgsConstructor(access = AccessLevel.PRIVATE)
   public final class PaginationSqlBuilder {
       public static String pageLimit(MapSqlParameterSource params, Pageable pageable) {
           if (pageable == null || pageable.isUnpaged()) return "";
           params.addValue("limit",  pageable.getPageSize());
           params.addValue("offset", pageable.getOffset());
           return " LIMIT :limit OFFSET :offset";
       }
   }
   ```
2. Remove `pageLimit()` from `UserRepository` and `AdvertisementRepository`.
3. Replace calls with `PaginationSqlBuilder.pageLimit(params, pageable)`.
4. Update `query-lib/CLAUDE.md` — add `PaginationSqlBuilder` to the class table.

---

## Part B — Add `actorId` to `TaxonRepository.softDelete()`

**Problem:** `TaxonService.softDelete()` receives `Long actorId` but doesn't forward it to
`TaxonRepository.softDelete()` — deletion actor is never persisted. The `taxon` table has
`created_by` / `updated_by` but no `deleted_by`.

**Fix:**
1. New Liquibase file `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/002-taxon-deleted-by.xml`:
   ```xml
   <addColumn tableName="taxon">
       <column name="deleted_by" type="BIGINT"/>
   </addColumn>
   ```
2. Include it in `master.xml`.
3. Add `Long deletedBy` field to `Taxon.java`.
4. Update `TaxonRepository` ROW_MAPPER — read `deleted_by`.
5. Update `TaxonRepository.softDelete(@NonNull Long id)` →
   `softDelete(@NonNull Long id, Long actorId)` with SQL:
   `UPDATE taxon SET deleted_at = NOW(), deleted_by = :deletedBy WHERE id = :id`
6. Update `TaxonService.softDelete()` line 85:
   `taxonRepository.softDelete(id)` → `taxonRepository.softDelete(id, actorId)`
