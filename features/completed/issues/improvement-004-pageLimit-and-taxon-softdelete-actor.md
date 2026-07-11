# improvement-004: Extract pageLimit() to query-lib + add actorId to TaxonRepository.softDelete()

**Type:** improvement — deduplication + audit consistency
**When:** Wave 1 — bundle with the taxon repository touch (improvement-007, same PR)
**Status:** ✅ RESOLVED (2026-07-11) — both parts implemented as suggested. Part A:
`PaginationSqlBuilder` added to `query-lib`, duplicate `pageLimit()` removed from
`UserRepository`/`AdvertisementRepository`, `query-lib/CLAUDE.md` updated. Part B: `deleted_by`
column added directly to the existing `001-taxon.xml` changeset (DB not yet in production, so
no new migration file — see the file's own note), `Taxon.java`/`TaxonRepository`/`TaxonService`
updated to carry `actorId` through `softDelete()`. Editing an already-applied changeset required
a full `deploy.sh --reset` (Liquibase checksum mismatch otherwise). Bundled with improvement-007
in the same PR as planned. Full e2e suite 47/47 green.

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
1. **DB is not in production yet — do not add a new `002` migration file.** Edit the existing
   `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/001-taxon.xml`
   directly: add `<column name="deleted_by" type="BIGINT"/>` to the `taxon` table's
   `createTable` block (next to `created_by`/`updated_by`, `001-taxon.xml:26-27`). No new
   changeset, no `master.xml` include needed — same file, same changeset.
2. Add `Long deletedBy` field to `Taxon.java`.
3. Update `TaxonRepository` ROW_MAPPER — read `deleted_by`.
4. Update `TaxonRepository.softDelete(@NonNull Long id)` →
   `softDelete(@NonNull Long id, Long actorId)` with SQL:
   `UPDATE taxon SET deleted_at = NOW(), deleted_by = :deletedBy WHERE id = :id`
5. Update `TaxonService.softDelete()` line 85:
   `taxonRepository.softDelete(id)` → `taxonRepository.softDelete(id, actorId)`
