# improvement-015: No optimistic locking anywhere — silent last-write-wins on concurrent edits

**Type:** improvement — data integrity, found during strategic architecture review
**Module:** advertisement-spring-boot-starter + user-spring-boot-starter + taxon-spring-boot-starter (all entities)
**Priority:** medium — becomes high before public community traffic
**When:** Wave 2 — must land before public community traffic

## Problem

No entity in the project carries a version field; no repository checks one. Two concurrent
edits of the same advertisement (or user, or taxon) result in silent last-write-wins: the
second save overwrites the first with no error, no warning, no audit anomaly — the audit log
records both versions but the first editor's changes are gone from the live row without
anyone being told.

Today the editor pool is tiny and collisions are improbable. With the planned community
migration (24k members; moderators + owners editing the same listings, admins editing taxons)
collisions become a matter of time. The failure is silent, which makes it the worst kind:
users lose work and blame the platform.

## Suggested fix

Spring Data JDBC supports `@Version` natively:

1. Add `version BIGINT NOT NULL DEFAULT 0` to `advertisement`, `user_information`, `taxon`
   (one Liquibase changeset per starter, per module-independence rule).
2. Add `@Version private Long version;` to each entity — Spring Data JDBC then appends
   `WHERE version = ?` to updates and throws `OptimisticLockingFailureException` on conflict.
3. UI: catch the exception in save flows and show a dedicated notification ("Запис було
   змінено іншим користувачем — оновіть і повторіть") with a reload action; the overlay
   session refresh machinery (`withEntity(fresh)`) already fits.
4. DTOs carry the version through the form (`AdvertisementSaveDto` etc. get a `version`
   field) so the check spans the whole edit session, not just the SQL statement.

## Scope note

Bespoke UPDATE statements in repositories (`updateMedia`, `softDelete`) bypass
`CrudRepository.save` — decide per statement whether they need the version guard
(`softDelete` likely yes, counter-style updates likely no).
