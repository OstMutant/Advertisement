## user-spring-boot-starter

Auto-configures the User domain including entity, service, security, and Spring Security integration. Active whenever the jar is on the classpath.

Java package root: `org.ost.user`

---

## What it owns

- `User` entity + `UserRepository` — CRUD and bespoke queries
- `UserService` — user creation, role management, profile updates
- `UserSettingsService` — per-user settings (page sizes, locale)
- `UserPrincipal` — Spring Security `UserDetails` implementation
- `UserPortImpl` — implements `UserPort`; thin delegation to services
- `UserSettingsChangedHook` dispatch — fires `UserSettingsChangedHook` implementations on settings change

**Autoconfiguration entry point:** `UserAutoConfiguration`

---

## Schema

Liquibase changelog: `db/user-changelog/user-changelog-master.xml`  
Tables: `user_information` (single table; per-user settings live in its `settings` JSONB column — no separate settings table)

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here.
- `UserPort`, `AuthenticatedPrincipal`, `UserSettingsChangedHook` live in `platform-commons`.
- `@EnableJdbcRepositories(basePackages = "org.ost.user")` declared in `UserAutoConfiguration`.
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
  registrations (see `marketplace-app/DECISIONS.md` ADR-026).
