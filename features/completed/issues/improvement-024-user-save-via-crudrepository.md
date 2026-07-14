# improvement-024: Route User profile edits through CrudRepository, like Advertisement/Taxon

**Type:** improvement — consistency/tech-debt, found while discussing improvement-015
**Module:** user-spring-boot-starter
**Priority:** low — current manual guard already works correctly (verified: full e2e green); this
is a symmetry/cleanup refactor, not a bug fix
**When:** Wave 2 — independent, no blockers

## Problem

`UserService.save()` (the real profile-edit path) calls `UserRepository.updateProfile(dto)`, a
hand-written SQL `UPDATE` touching only `name` and `role`, with a manually-coded optimistic-lock
check (`WHERE id = :id AND version = :version`, throwing `OptimisticLockingFailureException` by
hand when zero rows match — see improvement-015 / `marketplace-app/DECISIONS.md` ADR-029).
`Advertisement` and `Taxon` instead go through `CrudRepository.save()`, where Spring Data JDBC's
`@Version` support handles the same check natively. The two domains behave inconsistently for
what is conceptually the same problem, and `UserRepository` carries bespoke lock-checking code
that `AdvertisementRepository`/`TaxonRepository` don't need.

## Suggested fix

Do **not** mirror `AdvertisementService.buildEntity(dto, before)` here — rebuilding a full `User`
via `User.builder()...` and forwarding every unedited field (`email`, `passwordHash`, `locale`,
`createdAt`) reintroduces the exact "forgot to forward a field" failure mode that already bit
improvement-015 twice (`AdvertisementService.buildEntity()` dropping `version`,
`TaxonService.update()` dropping `version`). For `User`, forgetting to forward `passwordHash` or
`email` is worse than those cases — it silently locks the user out or breaks their notifications,
not just a lock-check regression.

Instead, introduce a second, narrower entity mapped to the same table, and give it its own
`CrudRepository`, so the columns the profile-edit path is allowed to touch are enforced by the
Java type system, not by builder discipline:

1. New class `UserProfileUpdate` (`org.ost.user.entity`), `@Value @Builder @Table("user_information")`:
   `Long id` (`@Id`), `String name`, `Role role`, `Long version` (`@Version`) — nothing else.
   `passwordHash`/`email`/`locale`/`createdAt` are not fields on this class, so no code path
   through it can ever reference or overwrite them.
2. New `UserProfileCrudRepository extends CrudRepository<UserProfileUpdate, Long>` (`org.ost.user.repository`),
   alongside the existing `UserCrudRepository`.
3. `UserService.save()` builds a `UserProfileUpdate` (`id`, `name`, `role`, `.version(dto.version())`
   — the version the caller last read, never re-derived from `before` in the same method, same
   rule as Advertisement/Taxon) and calls `userProfileCrudRepository.save(...)`. Spring Data JDBC
   generates an `UPDATE` covering only `name`, `role`, `version` — structurally incapable of
   touching `passwordHash`/`email`, and the native `@Version` check replaces the manual
   `OptimisticLockingFailureException` throw.
4. Remove `UserRepository.updateProfile()` and its manual version-guard SQL — dead code once
   nothing calls it (check `UserService.applyUserRestore()` too, which also calls
   `updateProfile()` — both call sites need the same treatment; `applyUserRestore()` also only
   restores `name`/`role`, so it maps onto `UserProfileUpdate` the same way).

## Risk

Structurally eliminated for `passwordHash`/`email`: they are not mapped properties on
`UserProfileUpdate`, so `CrudRepository.save()` cannot generate SQL touching them, regardless of
builder mistakes. The remaining downside is purely stylistic — two Java classes mapped to the
same `user_information` table (`User` for read/registration, `UserProfileUpdate` for the
profile-edit write path) is an unusual-looking pattern in this codebase, but is a standard and
supported Spring Data JDBC technique (each repository's entity type independently determines its
own mapped column set).

`settings` (JSONB column, managed separately by `UserSettingsRepository`) is not mapped on either
entity, so it is unaffected either way — no special handling needed for it.

## Required test coverage before merging

No new spec file — extend the existing test
`playwright/e2e/03-marketplace-promotion-flow.spec.js:119`
(`'adminEn edits userEn name — activity diff, grid updated, restore reverts name'`), which
already edits `userEn`'s profile as `adminEn`. Add a step after the save: log out, log back in as
`userEn` with their original password, assert success. No existing test currently proves the
password hash survives a profile edit — this is the one assertion that would actually catch a
regression if `passwordHash` ever stopped being forwarded/excluded correctly.
