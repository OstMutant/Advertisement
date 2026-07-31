# user-spring-boot-starter

Auto-configured User domain with Spring Security integration for the Advertisement Platform.

## What it provides

- User registration, role management (USER / MODERATOR / ADMIN), profile and settings
- Spring Security integration via `UserPrincipal` (`UserDetails` implementation)
- Per-user settings with change-event dispatch (`UserSettingsChangedHook`)
- **SPI implementations:** `UserPort`, `UserAccountPort`, `UserAuthorizationPort`,
  `UserPreferencesPort`, and `AuthenticatedPrincipal` (called by marketplace-app)

## Key classes

| Class | Role |
|---|---|
| `UserPortImpl` / `UserAccountPortImpl` / `UserAuthorizationPortImpl` / `UserPreferencesPortImpl` | Entry points — implement the 4 `User*Port` interfaces, delegate to services |
| `UserService` | User creation, role promotion, profile updates |
| `UserPreferencesService` | Per-actor settings (page sizes) and locale |
| `UserRepository` | Persists and queries `user_information`; supports dynamic filter/sort |
| `UserPrincipal` | Spring Security `UserDetails` — loaded by `UserDetailsService` |

## Dependencies

- `platform-commons` — SPI interfaces (`UserPort`, `UserAccountPort`, `UserAuthorizationPort`, `UserPreferencesPort`, `AuthenticatedPrincipal`, `UserSettingsChangedHook`) and DTOs
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- Spring Boot, Spring Security, Spring JDBC, Liquibase
