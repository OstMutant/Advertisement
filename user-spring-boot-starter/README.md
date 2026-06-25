# user-spring-boot-starter

Auto-configured User domain with Spring Security integration for the Advertisement Platform.

## What it provides

- User registration, role management (USER / MODERATOR / ADMIN), profile and settings
- Spring Security integration via `UserPrincipal` (`UserDetails` implementation)
- Per-user settings with change-event dispatch (`UserSettingsChangedHook`)
- **SPI implementations:** `UserPort` and `AuthenticatedPrincipal` (called by marketplace-app)

## Key classes

| Class | Role |
|---|---|
| `UserPortImpl` | Entry point — implements `UserPort`, delegates to services |
| `UserService` | User creation, role promotion, profile updates |
| `UserSettingsService` | Per-user settings (page sizes, locale preference) |
| `UserRepository` | Persists and queries `app_user`; supports dynamic filter/sort |
| `UserPrincipal` | Spring Security `UserDetails` — loaded by `UserDetailsService` |

## Dependencies

- `platform-commons` — SPI interfaces (`UserPort`, `AuthenticatedPrincipal`, `UserSettingsChangedHook`) and DTOs
- `query-lib` — `SqlFilterBuilder`, `OrderByBuilder` for dynamic queries
- Spring Boot, Spring Security, Spring JDBC, Liquibase
