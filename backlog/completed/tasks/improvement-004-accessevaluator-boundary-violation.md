# improvement-004: AccessEvaluator imports user-starter internals directly

**Type:** improvement — architectural
**Module:** marketplace-app (services/security)
**Priority:** high — violates module independence rule; mirrors improvement-001
**Status:** RESOLVED — 2026-06-26

## Problem

`AccessEvaluator` in `org.ost.marketplace.services.security` imported two internal classes
from `user-spring-boot-starter` directly:

| File | Illegal import |
|------|---------------|
| `AccessEvaluator.java:6` | `org.ost.user.security.OwnershipChecker` |
| `AccessEvaluator.java:7` | `org.ost.user.security.RoleChecker` |

`org.ost.platform.user.security.UserIdMarker` (line 5) is from `platform-commons` — that import is correct.

## Fix applied

Added `isAdmin`, `isModerator`, `isOwner` methods to `UserPort` (platform-commons).
`UserPortImpl` delegates to the existing internal `RoleChecker` / `OwnershipChecker` beans.
`AccessEvaluator` now injects `UserPort` only — all imports from `org.ost.user.*` removed.

See: `platform-commons/DECISIONS.md` ADR-016.
