# marketplace-app

The main Vaadin application — all UI lives here. Depends on all starters via Spring Boot autoconfiguration.

## Responsibilities

- All Vaadin views, panels, overlays, and UI components
- Authentication flow (login, signup, logout)
- Advertisement, User, and Timeline views
- Audit history and activity feed rendering
- Attachment gallery and lightbox UI
- I18n (English + Ukrainian) via `I18nKey` enum and `I18nService`
- Security access evaluation (`AccessEvaluator`, `RoleChecker`, `OwnershipChecker`)

## Key packages

| Package | Contents |
|---|---|
| `config/` | Spring configuration (DB auditing, UI factories) |
| `services/i18n/` | `I18nKey`, `I18nService`, `LocaleProvider`, `InstantFormatter` |
| `services/auth/` | `AuthContextService` — current-user access |
| `services/security/` | `AccessEvaluator`, `RoleChecker`, `OwnershipChecker` |
| `repository/activity/` | Activity feed SQL + projections |
| `ui/core/` | `Configurable<T,P>`, `Initialization<T>`, `UiComponentFactory<T>` |
| `ui/views/components/` | Reusable panels, overlays, audit/attachment UI |

## UI patterns

Prototype beans use `Configurable<T, Parameters>` + `UiComponentFactory`. See [CLAUDE.md](CLAUDE.md) for full pattern rules.

## Dependencies

- All starters (`audit`, `attachment`, `user`, `advertisement`) via autoconfiguration
- `platform-commons` — SPI contracts and DTOs
- Vaadin 25, Spring Boot 4, Spring Security
