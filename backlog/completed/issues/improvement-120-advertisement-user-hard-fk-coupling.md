# improvement-120: `advertisement` → `user_information` is the last hard SQL-level FK coupling between starters

**Type:** improvement — architecture/decoupling.
**Module:** `advertisement-spring-boot-starter` (`db/advertisement-changelog/changes/01-advertisement-schema.xml`,
`AdvertisementRepository`, `AdvertisementService`), `platform-commons` (`AdvertisementPort` — new
method needed), `user-spring-boot-starter` (`UserService.cleanup()`).
**Priority:** highest — DB is not in production yet; this is the cheapest point in the project's
lifetime to fix it. Every other starter (`taxon`, `audit`, `attachment`) already stores actor
references as plain `BIGINT` with no DB-level FK to `user_information` — `advertisement` is the
sole remaining exception.
**When:** independent, no blockers. **Schema fix goes directly into the existing
`01-advertisement-schema.xml` changeset (edit in place), not a new incremental changeset** — the
database has never been released, so there is no deployed instance whose Liquibase changelog
history needs preserving.

## Problem

`db/advertisement-changelog/changes/01-advertisement-schema.xml` has **three** physical
`addForeignKeyConstraint` blocks from `advertisement` to `user_information`:

```xml
<addForeignKeyConstraint baseTableName="advertisement" baseColumnNames="created_by"
        constraintName="fk_advertisement_created_by" referencedTableName="user_information"
        referencedColumnNames="id" onDelete="RESTRICT"/>
<addForeignKeyConstraint baseTableName="advertisement" baseColumnNames="updated_by"
        constraintName="fk_advertisement_modified_by" referencedTableName="user_information"
        referencedColumnNames="id" onDelete="SET NULL"/>
<addForeignKeyConstraint baseTableName="advertisement" baseColumnNames="deleted_by"
        constraintName="fk_advertisement_deleted_by" referencedTableName="user_information"
        referencedColumnNames="id" onDelete="SET NULL"/>
```

Verified by comparison against every sibling starter — none of them have an equivalent constraint:
- `taxon-spring-boot-starter`'s `001-taxon.xml`: `created_by`/`updated_by`/`deleted_by` columns
  exist on `taxon`, **no FK** to `user_information`.
- `audit-spring-boot-starter`'s `01-audit-schema.xml`: `actor_id` column, **no FK**.
- `attachment-spring-boot-starter`'s `01-attachment-schema.xml`: `deleted_by_actor_id` column,
  **no FK**.

`advertisement` is the one place a physical SQL constraint ties one starter's schema to another
starter's table — the thing that would make `user-spring-boot-starter` impossible to run against a
separate database instance without `advertisement-spring-boot-starter`'s Liquibase changelog
failing at startup. It also silently contradicts the project's stated pattern (`AdvertisementRepository`
never joins `user_information` in Java, per `advertisement-spring-boot-starter/CLAUDE.md` — but the
DB schema itself still hard-references it).

**This is not dead weight — it is currently load-bearing, and removing it naively would introduce
a real regression.** Traced both consumers of these columns:

1. **`UserDeleteService.delete()`** (interactive admin delete) already cascades correctly — it
   soft-deletes the user's own advertisements (`advertisementSaveService.delete(...)`) *before*
   calling `userPort.delete()`. The FK never actually fires on this path today.
2. **`UserService.cleanup(retentionDays)`** (the retention-purge `@Scheduled` job in
   `UserAutoConfiguration`) is the path that actually relies on the constraint:
   ```java
   // per-row, not one bulk statement -- an FK-blocked row is skipped, not fatal to the batch
   public void cleanup(int retentionDays) {
       for (Long id : candidates) {
           try {
               repository.deleteById(id);
           } catch (DataIntegrityViolationException e) {
               log.warn("Skipped purging user {} - still referenced elsewhere, will retry next run", id);
           }
       }
   }
   ```
   This performs a genuine hard `DELETE`, and `advertisement-spring-boot-starter`'s own equivalent
   job (`AdvertisementService.cleanup()`, wired from `AdvertisementAutoConfiguration`) is a
   **separate, independently-scheduled `@Scheduled` job in a different starter** — there is no
   cross-starter ordering guarantee between the two. The `fk_advertisement_created_by ... ON DELETE
   RESTRICT` constraint is currently the *only* thing preventing `UserService.cleanup()` from hard-
   deleting a user row while `advertisement` rows (even soft-deleted, not-yet-purged ones —
   `AdvertisementRepository.findByCreator()` only returns *active* rows, `deleted_at IS NULL`, so it
   is not a substitute existence check for this purpose) still physically reference that user id.
   The two `SET NULL` constraints (`updated_by`/`deleted_by`) are the same class of problem, just
   silent instead of loud: deleting a `user_information` row today triggers the Postgres engine to
   directly mutate `advertisement` rows, entirely outside of any Java code's visibility or the
   `AdvertisementService` audit/versioning path.

## Suggested fix

1. **Schema:** edit `01-advertisement-schema.xml` in place (no new changeset — pre-prod, per
   `**When:**` above) — remove all three `addForeignKeyConstraint` blocks; keep
   `idx_advertisement_created_by` (already exists) as a plain index, same pattern `taxon` already
   uses for its own actor columns.
2. **Close the safety-net gap `UserService.cleanup()` currently gets from the FK:** add a bulk
   existence-check method to `AdvertisementPort` (e.g. `existsAnyReferenceTo(Long userId)` or a
   `Set<Long> filterReferenced(Set<Long> userIds)` bulk variant matching this codebase's existing
   preference for bulk lookups over per-row queries) — checking `created_by OR updated_by OR
   deleted_by = :userId` **without** the `deleted_at IS NULL` filter `findByCreator()` has, since a
   soft-deleted-but-not-yet-purged advertisement still physically holds the reference. Wire this
   into `UserService.cleanup()` (via `ComponentFactory<AdvertisementPort>`, optional — mirrors how
   `AdvertisementService` already treats `TaxonPort`/`UserPort` as optional) as an explicit
   pre-check before `repository.deleteById(id)`, replacing the `DataIntegrityViolationException`
   catch with a proactive skip-and-log — same observable behavior (user purge retried next cycle
   when still referenced), but no longer dependent on a DB constraint to enforce it.
3. Decide what `updated_by`/`deleted_by` should do at the *application* level once `SET NULL` is no
   longer automatic — most likely nothing needs to change (those columns are informational, a
   purged user's id lingering on an old advertisement row is harmless), but confirm this
   explicitly rather than silently dropping the behavior.
4. Verify: unit-tests (`UserService.cleanup()` behavior, mocked `AdvertisementPort`), integration-
   tests (`UserRepositoryTest`/`AdvertisementRepositoryTest` — confirm the FK constraints are truly
   gone from the live schema after a fresh Liquibase run), and a full Playwright regression (no
   user-facing behavior should change).

## Implemented (2026-07-25)

Shipped exactly per the suggested fix, with the `created_by`/`updated_by`+`deleted_by` split
refined during implementation (see below) — `AdvertisementPort` gained two methods instead of one
generic existence check: `findOwnerIds(Set<Long> userIds)` (mirrors the old RESTRICT — blocks
purge) and `clearActorReferences(Set<Long> userIds)` (mirrors the old SET NULL — nulls the columns
instead of blocking). `UserService.cleanup()` now calls `clearActorReferences()` unconditionally
first, then skips `deleteById()` only for ids still in `findOwnerIds()`'s result.

**Point 3 resolved — verified no UI impact, not just assumed:**

| Scenario | Advertisement card/view | Activity tab | Timeline tab |
|---|---|---|---|
| Admin soft-deletes a user | Their own ads cascade-soft-delete too (unchanged); `createdByUserName` still resolves (row still exists) | Actor name shown with a `(deleted)` suffix (`AUDIT_ACTOR_DELETED_NAME`) — unchanged | Same `(deleted)` suffix — unchanged |
| Retention purge, user still owns an ad | **Blocked by `findOwnerIds()`** — nothing changes, retried next cycle | n/a (user not deleted) | n/a |
| Retention purge, user only ever appeared as `updated_by`/`deleted_by` on someone else's ad | `updated_by`/`deleted_by` get nulled — **but neither column is ever exposed to the UI**: confirmed `AdvertisementInfoDto` has no such fields and `AdvertisementMapper` doesn't map them | Actor name for old rows referencing the purged id falls back to `""` (blank) — same as any `audit_log.actor_id` with no matching user row today, since `audit_log` never had a DB constraint either; not a new gap | Same blank-name fallback, pre-existing behavior |

Net effect: no UI regression. `created_by` (the only actor reference actually surfaced in the UI)
is *more* protected after this change than before, since `findOwnerIds()` checks it unconditionally
regardless of soft-delete state.

**Verified:** unit-tests 74/74 (`marketplace-app`, cascades through the full reactor since
`AdvertisementPort` is a `platform-commons` interface change), integration-tests 9/9
(`UserServiceTest`, `cleanup()` mocks rewritten from FK-exception-catching to explicit
`findOwnerIds`/`clearActorReferences` mocks).

## Related

- `advertisement-spring-boot-starter/CLAUDE.md` — already documents the Java-level decoupling
  (`AdvertisementRepository` never joins `user_information`); this issue closes the matching gap at
  the schema level.
- `taxon-spring-boot-starter/CLAUDE.md`, `001-taxon.xml` — the soft-FK pattern to mirror.
- `marketplace-app/DECISIONS.md` (candidates: ADR touching `UserDeleteService`/cleanup ordering, if
  one exists — check before writing a new one for this fix).
