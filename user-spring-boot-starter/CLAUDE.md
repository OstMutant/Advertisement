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

Liquibase changelog: `db/changelog/user-changelog.xml`  
Tables: `app_user`, `user_settings`

Starters own their own Liquibase changelogs — never merge into a shared file.

---

## Key constraints

- No Vaadin dependency. No UI code here.
- `UserPort`, `AuthenticatedPrincipal`, `UserSettingsChangedHook` live in `platform-commons`.
- `@EnableJdbcRepositories(basePackages = "org.ost.user")` declared in `UserAutoConfiguration`.
- First registered user is auto-promoted to `ADMIN` role — enforced in `UserService`.
- `@PreAuthorize` must NOT be placed at class level on service beans — see marketplace-app/CLAUDE.md.
