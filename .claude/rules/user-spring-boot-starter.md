---
paths: ["user-spring-boot-starter/**"]
---

## user-spring-boot-starter

Auto-configures the User domain including entity, service, security, and Spring Security integration. Active whenever the jar is on the classpath.

Java package root: `org.ost.user`

---

## What it owns

See `user-spring-boot-starter/README.md`'s "Key classes" table for the class list and one-line
roles — not restated here. `UserPreferences` is table-only (no entity —
`UserPreferencesRepository` uses raw `JdbcClient`); `UserSettingsChangedHook` implementations
fire on settings change.

**Autoconfiguration entry point:** `UserAutoConfiguration`

---

## Schema

Liquibase changelog: `db/user-changelog/user-changelog-master.xml`  
Tables: `user_information` (auth only — email, password hash, role, name), `user_preferences`
(locale + settings JSONB, one row per actor, keyed by `actor_id` with no FK — matches this
codebase's actor-reference-column convention, e.g. `advertisement.created_by`)

---

## Key constraints

- `UserPort` (query), `UserAccountPort` (save/delete/register/refresh), `UserAuthorizationPort`
  (isAdmin/isModerator/isOwner), `UserPreferencesPort` (settings/locale), `AuthenticatedPrincipal`,
  `UserSettingsChangedHook` all live in `platform-commons`. Split into 4 narrow ports (not 1) —
  each consumer injects only the port(s) it actually calls; no runtime-toggle benefit (all 4 are
  always implemented by this one module), the split is purely for interface cohesion. See
  `docs/ai/adr-index.md` for both the `UserDto`/consumer-repointing side and the
  starter-module-level port-split rationale.
- `@EnableJdbcRepositories(basePackages = "org.ost.user.repository")` declared in `UserAutoConfiguration`.
- First registered user is auto-promoted to `ADMIN` role — enforced in `UserService`.
- `@PreAuthorize` must NOT be placed at class level on service beans — see marketplace-app/CLAUDE.md.
- `passwordEncoder()` bean in `UserAutoConfiguration` uses
  `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (not a raw `BCryptPasswordEncoder`)
  so stored hashes carry an algorithm prefix (`{bcrypt}`) and future algorithm migration doesn't
  require a data rewrite.
- `UserService.register(dto, clientIp)` takes the caller's IP as a plain `String` (never
  `HttpServletRequest`) to stay transport-agnostic — marketplace-app extracts
  `request.getRemoteAddr()` and passes it down. Rate-limited via an in-memory Caffeine cache
  (5 failures / 15 min), counting only `DuplicateKeyException` failures — never successful
  registrations (see `docs/ai/adr-index.md`).
- `User.version` (`@Version`) is used by `UserRepository.save()` (registration, via
  `UserCrudRepository`). The real profile-edit path (`UserService.save()` →
  `UserRepository.updateProfile()`) goes through a second, narrower entity —
  `UserEditableFields` (`id`, `name`, `role`, `updatedAt`, `version` — no `email`/`passwordHash`,
  named for the restricted-column-set it represents, not "profile" — that word is reserved for
  F-04's provider-profile concept) — mapped to the same `user_information` table via its own
  `UserEditableFieldsCrudRepository`. Spring Data JDBC's native `@Version` handling applies (throws
  `OptimisticLockingFailureException` on a version mismatch, same as `Advertisement`/`Taxon`), and
  because `passwordHash`/`email` are not mapped properties on `UserEditableFields`, the generated
  `UPDATE` cannot touch them — this eliminates the class of bug where a profile edit accidentally
  forwards the wrong (or missing) value for a sensitive field, without relying on builder
  discipline. There is no dedicated server-side restore-apply method — restoring a user from a
  snapshot is client-side only (`UserFormOverlayModeHandler.loadRestored()` loads the snapshot's
  name/role into the edit form), and only takes effect once the moderator explicitly saves, going
  through the same `save()` → `updateProfile()` path as any other profile edit. See
  `docs/ai/adr-index.md`.
- `UserPreferencesRepository.saveSettings()` also enforces optimistic locking, but via a version
  embedded **inside** the `settings` JSONB column (`UserSettingsDto.version`) rather than a
  separate SQL column — since this repository already serializes/deserializes the whole DTO
  directly into that one column, the version round-trips through the same Jackson
  (de)serialization for free. The `UPDATE`'s `WHERE` clause checks
  `(settings->>'version')::bigint = :expectedVersion`; 0 affected rows throws
  `OptimisticLockingFailureException`, same as `User.version`/`UserEditableFields` above.
  Deliberately does **not** reuse the row's shared `user_information.version` — that would couple
  a settings save to an unrelated profile-name edit in another tab. The original design and its
  later supersession — `settings`/`locale` now live in their own `user_preferences` table, not on
  `user_information` — are both recorded in `docs/ai/adr-index.md`.
