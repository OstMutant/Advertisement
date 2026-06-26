# improvement-004: AccessEvaluator imports user-starter internals directly

**Type:** improvement — architectural
**Module:** marketplace-app (services/security)
**Priority:** high — violates module independence rule; mirrors improvement-001

## Problem

`AccessEvaluator` in `org.ost.marketplace.services.security` imports two internal classes
from `user-spring-boot-starter` directly:

| File | Illegal import |
|------|---------------|
| `AccessEvaluator.java:6` | `org.ost.user.security.OwnershipChecker` |
| `AccessEvaluator.java:7` | `org.ost.user.security.RoleChecker` |

`org.ost.platform.user.security.UserIdMarker` (line 5) is from `platform-commons` — that import is correct.

## Required fix

`OwnershipChecker` and `RoleChecker` must be exposed through `UserPort` or moved to
`platform-commons` as SPI interfaces so `AccessEvaluator` can depend on contracts, not
user-starter internals.

Options:
1. Move `OwnershipChecker` and `RoleChecker` interfaces to `platform-commons/user.spi`.
   Implementations stay in user-starter. `AccessEvaluator` injects them via `ObjectProvider`.
2. Expose ownership/role check methods on `UserPort` directly and delete the separate checker classes.

Option 2 is simpler if the checker interfaces have few methods.

## Constraint

Do not re-introduce `UserPrincipal` or any `org.ost.user.entity.*` / `org.ost.user.services.*`
imports — those were cleaned up in ADR-016 (marketplace-app/DECISIONS.md).
