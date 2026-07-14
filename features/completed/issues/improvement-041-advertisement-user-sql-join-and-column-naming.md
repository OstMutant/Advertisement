# improvement-041: Advertisement repository JOINs `user_information` directly — hardcoded cross-starter SQL, plus inconsistent actor-column naming

**Type:** improvement — architectural, cross-module coupling. Found during a review of
`entity-extensions/SPEC.md`'s premise ("advertisement schema knows about attachment") — that
premise turned out to be the *weaker* coupling; the actually serious one is a raw SQL join, found
by reading `AdvertisementRepository` directly.
**Module:** `advertisement-spring-boot-starter`, `platform-commons` (`UserPort`, `AdvertisementInfoDto`)
**Priority:** medium-high — this is a stronger violation of the "starters must not depend on each
other" rule (`.claude/rules.md`) than a Java import would be: it can't be caught by ArchUnit
(improvement-030) at all, since there is no Java class dependency to detect — the coupling exists
only as a raw SQL string referencing another starter's table/column names.
**When:** independent, no blockers — well-precedented fix, mirrors the already-completed
improvement-007 (`TaxonPort.findByIds()` bulk lookup) almost exactly

## Problem

### 1. Raw SQL JOIN across starter boundaries (the serious one)

`AdvertisementRepository.findAdvertisementById()` and `.findByFilter()` both do:

```sql
FROM advertisement a LEFT JOIN user_information u ON a.created_by_user_id = u.id
```

selecting `u.name AS created_by_user_name, u.email AS created_by_user_email` directly inside
`advertisement-spring-boot-starter`. This hardcodes `user_information`'s table name and column
names (`u.id`, `u.name`, `u.email`) inside a different starter. If `user-spring-boot-starter` ever
renames that table or those columns, `advertisement-spring-boot-starter` breaks at runtime with
zero compile-time warning — worse than a Java-level violation, which at least Maven's module
graph or ArchUnit could flag. Verified this is genuinely used, not dead code:
`AdvertisementCardView.java:187` reads `ad.getCreatedByUserEmail()` to display the author's email
on the advertisement card.

### 2. Inconsistent actor-reference column naming (the smaller one, but a real signal)

`advertisement` table columns: `created_by_user_id`, `last_modified_by_user_id`,
`deleted_by_user_id` (verified in `01-advertisement-schema.xml`). Compare `taxon` table (verified
in `001-taxon.xml`): `created_by`, `updated_by`, `deleted_by` — no domain suffix at all, even
though the value is exactly the same kind of thing (a `User.id`, populated via
`AuditorAware<Long>`/hand-set on delete). Advertisement's schema embeds the word "user" into its
own column names for no functional reason — the ID is opaque either way, populated by
`JdbcAuditingConfig`'s `AuditorAware<Long>` bean, and the explicit naming is exactly the kind of
naming-level coupling signal that should not exist between two starters that must not depend on
each other. Taxon already established the better convention; Advertisement should match it.

### Explicitly not the problem (reconsidered from an earlier, wrong instinct)

`advertisement.media_url`/`media_content_type`/`media_count` — these are true denormalized
columns, but synced through an already-typed, already-audited channel (`AttachmentMediaChangeHook`
→ `MediaChangeHookImpl` → `AdvertisementRepository.updateMedia()`), not a raw cross-starter SQL
join. `entity-extensions/SPEC.md`'s generic-JSONB proposal was evaluated against this specific
coupling and rejected (see prior discussion) — it would remove type safety without removing any
actual SQL-level cross-starter reference, since none exists for media. Do not conflate this issue
with that one; the JOIN above is the real violation, the media columns are not.

## Suggested fix

Mirror the already-completed `improvement-007` pattern (`TaxonPort.findByIds()` bulk lookup),
applied to `UserPort` instead of `TaxonPort`, and respecting the **Port implementation rule**
that ports are pure delegation only — the actual enrichment logic belongs in a service, not in
`UserPortImpl` or `AdvertisementPortImpl`:

1. **Add a bulk lookup to `UserPort`** (`platform-commons`): a method returning enough data to
   replace the JOIN's `name`/`email` columns — either extend the existing
   `findActorNames(Collection<Long>) -> Map<Long, String>` to also carry email, or add a new
   `findByIds(Set<Long>) -> Map<Long, UserDto>` (the latter is more consistent with
   `TaxonPort.findByIds()`'s shape and more reusable beyond this one call site — prefer it).
   `UserPortImpl`'s implementation must be **pure delegation** — one call to a
   `UserService`/`UserRepository` bulk method, no merging/business logic inside the port itself
   (the merging happens one layer up, in step 3).
2. **Remove the `LEFT JOIN user_information` from `AdvertisementRepository`** — both
   `findAdvertisementById()` and `findByFilter()` and their `ORDER BY` alias map, which currently
   includes `u.id`/`u.name`/`u.email` sort options. Verified (2026-07-14) these three are dead:
   `AdvertisementSortMeta` (`marketplace-app`) exposes only `TITLE`/`CREATED_AT`/`UPDATED_AT` — no
   UI path ever builds a `Sort` containing `created_by_user_id`/`_name`/`_email`, so
   `OrderByBuilder.build()` never reaches those alias entries. Delete all three alongside the
   `JOIN`, no replacement sort logic needed. Return only `created_by_user_id` (or `created_by`,
   see naming fix below) from the raw SQL — no cross-starter table reference at all.

   **If sort-by-author is ever requested in the future:** do not "just sort in memory after
   enrichment" — pagination (`LIMIT`/`OFFSET`) runs in SQL *before* enrichment, so an in-memory
   sort only orders the current page, not the full result set, which is silently wrong. The
   correct fix at that point is the same pattern already sanctioned for
   `media_url`/`media_content_type` (see "Explicitly not the problem" above): denormalize
   `created_by_user_name` onto `advertisement`, kept in sync via a typed hook (mirroring
   `AttachmentMediaChangeHook`/`MediaChangeHookImpl`) fired whenever a user's name changes — not
   a reintroduced JOIN, which would recreate the exact violation this issue removes. Not needed
   now; recorded here so a future reader doesn't have to re-derive it.
3. **Add actor-info enrichment to `AdvertisementService`**, mirroring the existing
   `enrichWithCategories()` method exactly (same file, same class, already using
   `ComponentFactory<TaxonPort> taxonPortFactory` for the identical shape of problem): add a
   `ComponentFactory<UserPort> userPortFactory` field, and an `enrichWithActorInfo(ads)` step
   analogous to `enrichWithCategories(ads, locale)` — this is where the actual "merge id → name/
   email into the DTO" logic lives, in the **service**, not in either port implementation.
4. **Rename the three actor-reference columns** to match Taxon's already-established convention,
   via direct edit of `01-advertisement-schema.xml` (DB not yet in production, same practice used
   for every prior schema edit this project has done — requires `deploy.sh --reset` afterward for
   the Liquibase checksum):
   - `created_by_user_id` → `created_by`
   - `last_modified_by_user_id` → `updated_by`
   - `deleted_by_user_id` → `deleted_by`
   - Update the index on `created_by_user_id` (line 70 of the current changeset) to match.
   - Update `Advertisement.java` (`createdByUserId`→`createdBy`, `lastModifiedByUserId`→
     `updatedBy`), `AdvertisementRepository.java`, `AdvertisementService.java`,
     `AdvertisementInfoDto.java` (platform-commons), `AdvertisementEditDto.java`
     (marketplace-app) — six files total, verified scope via `grep -rl`, no more hidden
     references.

## Scope check performed

`grep -rl "createdByUserId|lastModifiedByUserId|deletedByUserId|created_by_user_id|
last_modified_by_user_id|deleted_by_user_id"` across `advertisement-spring-boot-starter`,
`platform-commons`, `marketplace-app` (excluding generated/`target` output) returns exactly:
`Advertisement.java`, `AdvertisementRepository.java`, `AdvertisementService.java`,
`01-advertisement-schema.xml`, `AdvertisementInfoDto.java`, `AdvertisementEditDto.java` — six
files, a well-bounded rename.

## Required test coverage

Full Playwright e2e suite must stay green — the advertisement card's author-email display
(`AdvertisementCardView.java:187`) is the concrete regression detector for the enrichment change;
verify it still shows the correct email after the JOIN is replaced with the bulk `UserPort` call.

## Related

- `features/issues/improvement-042-advertisement-media-denormalized-columns.md` — sibling issue,
  same enrichment-at-read-time pattern applied to `AttachmentPort` instead of `UserPort`; both add
  a step next to `enrichWithCategories()` in `AdvertisementService`, consider one PR for both.
- `features/completed/issues/improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md` —
  the pattern this issue mirrors almost exactly, just for `UserPort` instead of `TaxonPort`.
- `features/entity-extensions/SPEC.md` — the JSONB proposal this issue's investigation grew out
  of; concluded that proposal doesn't address this specific coupling (see "Explicitly not the
  problem" above).
- `platform-commons/CLAUDE.md` — "`*PortImpl` — pure delegation only" rule, the constraint this
  issue's fix is designed around (enrichment logic goes in `AdvertisementService`, not in
  `UserPortImpl`).
- `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/001-taxon.xml` — the
  reference naming convention (`created_by`/`updated_by`/`deleted_by`) this issue brings
  `advertisement` in line with.
