# marketplace-app

The main Vaadin application — all UI lives here. Depends on all starters via Spring Boot autoconfiguration.

## Responsibilities

- All Vaadin views, panels, overlays, and UI components
- Authentication flow (login, signup, logout)
- Advertisement, User, Timeline, and Reference Data (taxon) views
- Audit history and activity feed rendering
- Attachment gallery and lightbox UI
- I18n (English + Ukrainian) via `I18nKey` enum and `I18nService`
- Security access evaluation (`AccessEvaluator`, in `marketplace-app`) plus role/ownership checks
  (`RoleChecker`, `OwnershipChecker` — these live in `user-spring-boot-starter`, not here; corrected
  2026-07-13, verified via direct file search)

## Key packages

| Package | Contents |
|---|---|
| `config/` | Spring configuration (DB auditing, UI factories) |
| `services/i18n/` | `I18nKey`, `I18nService`, `LocaleProvider`, `InstantFormatter` |
| `services/auth/` | `AuthContextService` — current-user access |
| `services/security/` | `AccessEvaluator` only (`RoleChecker`/`OwnershipChecker` are owned by user-spring-boot-starter — see above) |
| `ui/views/components/audit/` | Activity/Timeline row renderers (`AuditActivityListRenderer`, `AuditTimelineRowRenderer`, etc.) — the read-side SQL itself lives in `audit-spring-boot-starter`'s `AuditReadService`, not a marketplace-app `repository/activity/` package (that package does not exist; corrected 2026-07-13) |
| `ui/views/main/tabs/timeline/` | Dedicated top-level Timeline tab (`TimelineView`) |
| `ui/core/` | `Configurable<T,P>`, `Initialization<T>`, `UiComponentFactory<T>` |
| `ui/views/components/` | Reusable panels, overlays, audit/attachment UI |

## UI patterns

Prototype beans use `Configurable<T, Parameters>` + `UiComponentFactory`. See [CLAUDE.md](CLAUDE.md) for full pattern rules.

## Dependencies

- All starters (`audit`, `attachment`, `user`, `advertisement`, `taxon`) via autoconfiguration
- `platform-commons` — SPI contracts and DTOs
- Vaadin 25, Spring Boot 4, Spring Security
