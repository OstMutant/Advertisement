# improvement-024: Route User profile edits through CrudRepository, like Advertisement/Taxon

**Type:** improvement — consistency/tech-debt, found while discussing improvement-015
**Module:** user-spring-boot-starter
**Priority:** low — current manual guard already works correctly (verified: full e2e green); this
is a symmetry/cleanup refactor, not a bug fix
**When:** Wave 2 — independent, no blockers, but handle carefully (see risk below)

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

Mirror the `AdvertisementService.buildEntity(dto, before)` pattern:

1. `UserService.save()` fetches `before` (already does, for the audit diff), builds a full `User`
   entity via `User.builder()...`, forwarding every field the form does **not** edit from
   `before` — `email`, `passwordHash`, `locale`, `createdAt` — plus `.version(dto.version())`
   (the version the caller last read, same rule as Advertisement/Taxon: never re-derive it from
   `before` inside the same method).
2. Replace `repository.updateProfile(dto)` with `repository.save(user)` (already exists,
   delegates to `UserCrudRepository.save()`).
3. Remove `UserRepository.updateProfile()` and its manual version-guard SQL — dead code once
   nothing calls it (check `UserService.applyUserRestore()` too, which also calls
   `updateProfile()` — both call sites need the same treatment).

## Risk — read before implementing

Spring Data JDBC's `CrudRepository.save()` issues a full `UPDATE` covering **every mapped
column** on `User`, not just changed ones (no dirty-checking, unlike JPA). If `buildEntity()`
forgets to forward `passwordHash` or `email` from `before`, a routine profile edit would silently
null out the user's password hash or email — a much worse bug than the one this refactor is
meant to clean up. This exact class of mistake (rebuilding an entity via `Builder` and forgetting
to forward an unedited field) already happened twice while implementing improvement-015
(`AdvertisementService.buildEntity()` dropping `version`, `TaxonService.update()` dropping
`version`) — treat it as a known failure mode, not a hypothetical one.

`settings` (JSONB column, managed separately by `UserSettingsRepository`) is not mapped on the
`User` entity, so it is unaffected either way — no special handling needed for it.

## Required test coverage before merging

Playwright: edit a user's profile (name/role), then verify the user can still log in with their
existing password afterward — the profile-edit flow must not touch the password hash. No
existing test currently proves this.
