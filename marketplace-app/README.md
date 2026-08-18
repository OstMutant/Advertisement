# marketplace-app

The main Vaadin application — all UI lives here. Depends only on `platform-commons` and
`marketplace-orchestrator` — never directly on a domain starter; `marketplace-orchestrator` is the
one module that pulls in every starter and composes cross-domain use cases.

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
| `services/security/` | `AccessEvaluator` only — see "Responsibilities" above for where role/ownership checks actually live |
| `ui/views/components/audit/` | Activity/Timeline row renderers (`AuditActivityListRenderer`, `AuditTimelineRowRenderer`, etc.) — the read-side SQL itself lives in `audit-spring-boot-starter`'s `AuditReadService`; there is no marketplace-app `repository/activity/` package |
| `ui/views/main/tabs/timeline/` | Dedicated top-level Timeline tab (`TimelineView`) |
| `ui/core/` | `Configurable<T,P>`, `Initialization<T>`, `UiComponentFactory<T>` |
| `ui/views/components/` | Reusable panels, overlays, audit/attachment UI |

## UI patterns

Prototype beans use `Configurable<T, Parameters>` + `UiComponentFactory`. See [CLAUDE.md](CLAUDE.md) for full pattern rules.

## Dependencies

- `marketplace-orchestrator` — the application/BFF layer; pulls in every domain starter and
  composes cross-domain use cases, so marketplace-app never depends on a starter directly
- `platform-commons` — SPI contracts and DTOs
- Vaadin 25, Spring Boot 4, Spring Security
