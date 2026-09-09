# user-spring-boot-starter

Auto-configured User domain with Spring Security integration for the Advertisement Platform —
registration, profile management, role-based authorization, per-actor settings/locale, and
Spring Security principal construction, exposed to the rest of the reactor as narrow
`platform-commons` SPI implementations.

## What it provides

- User registration — first-registered user auto-promoted to `ADMIN`, later signups default to
  `USER` — protected by a Caffeine-backed rate limit on repeated failures.
- Profile updates, scoped by construction to a narrower set of columns than registration writes
  (see Data flow below for how).
- Soft-delete plus a scheduled retention cleanup job that checks the deleted user isn't still an
  advertisement or provider-profile owner before physically purging the row.
- Spring Security integration: `UserDetailsService`, a delegating `PasswordEncoder`,
  `AuthenticationManager`, and `UserPrincipal` (the `UserDetails` implementation loaded on login).
- Per-actor settings (page sizes) and locale, stored separately from the account row, with
  change-event dispatch via `UserSettingsChangedHook`.
- **SPI implementations:** `UserPort`, `UserAccountPort`, `UserAuthorizationPort`,
  `UserPreferencesPort` (called by `marketplace-orchestrator`'s `ActorLookupService`,
  `AuthorizationService`, `EntityExistenceService`, `UserDeleteService`, `UserProfileService`),
  plus `AuthenticatedPrincipal` (called by `marketplace-app`'s `AuthContextService` and
  `marketplace-rest-api`'s `ApiKeyController`).

## Data flow

Each of the four `User*Port` interfaces has its own `*PortImpl` in `org.ost.user.spi`, wired by
`UserAutoConfiguration`'s `ComponentFactory` beans:

- **Query / profile / registration / deletion:** `UserPortImpl` and `UserAccountPortImpl` delegate
  to `UserService`, which reads/writes through `UserRepository` — bespoke `JdbcClient`
  filter/sort/pagination queries plus `UserCrudRepository`/`UserEditableFieldsCrudRepository` for
  trivial save/find — against `user_information`. Registration builds and saves a full `User`
  entity; a profile update instead goes through `UserRepository.updateProfile`, which saves the
  narrower `UserEditableFields` entity onto the same row, so that path's generated `UPDATE` can
  never carry `email`/`passwordHash`. A save, delete, or register also fires a best-effort
  `AuditPort` capture; `cleanup()` additionally calls `AdvertisementPort`/`ProviderProfilePort` to
  confirm a soft-deleted candidate isn't still an owner before purging it and its
  `user_preferences` row.
- **Preferences:** `UserPreferencesPortImpl` delegates to `UserPreferencesService`, which
  reads/writes `user_preferences` through `UserPreferencesRepository` (raw `JdbcClient`, no entity
  class — settings round-trip as a JSONB blob) and fires `UserSettingsChangedHook` plus an
  `AuditPort` capture on save.
- **Authorization:** `UserAuthorizationPortImpl` delegates straight to `RoleChecker`/
  `OwnershipChecker` — plain field comparisons on an already-loaded `UserDto`, no repository
  access.
- **Login:** the `UserDetailsService` bean (`UserAutoConfiguration`) calls
  `UserService.findByEmail` then `UserService.toPrincipal`, which wraps the `User` entity and
  `UserPreferencesService.findLocale` into a `UserPrincipal`; Spring Security's
  `AuthenticationManager` then verifies its password via the delegating `PasswordEncoder`.
  `UserService.refreshSecurityContext` rebuilds and re-installs the `UserPrincipal` in place after
  a profile/role change, without forcing a re-login.

## Schema

Liquibase changelog: `db/user-changelog/user-changelog-master.xml` (single change file,
`changes/01-user-schema.xml`) — column-by-column business meaning lives in that file's own
`remarks=` attributes, not repeated here. Two tables, linked by `actor_id`: `user_information`
(the account row) and `user_preferences` (per-actor settings/locale, created unconditionally by
`UserService.register()` for every new account, never lazily). `user_information.deleted_by` is a
real self-referencing FK; `user_preferences.actor_id` is not — it follows this codebase's
actor-reference-column, no-FK convention instead.

## Dependencies

- `platform-commons` — `UserPort`/`UserAccountPort`/`UserAuthorizationPort`/`UserPreferencesPort`/
  `AuthenticatedPrincipal`/`UserSettingsChangedHook` and the `user.dto`/`user.model` types.
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for `UserRepository`'s dynamic filter/sort.
- `AdvertisementPort`/`ProviderProfilePort` (also `platform-commons`, injected via
  `ComponentFactory`) — consulted only by `UserService.cleanup()`'s ownership check, never a
  compile-time Maven dependency on either sibling starter.
- Spring Boot Security/JDBC/Liquibase/Validation starters, plus Caffeine (rate-limit cache) and
  Jackson 3.x (`tools.jackson.core`, JSONB settings serialization).
- No Maven dependency on any sibling `*-spring-boot-starter` — enforced by this module's own
  `maven-enforcer-plugin` `enforce-no-starter-to-starter-deps` rule.
