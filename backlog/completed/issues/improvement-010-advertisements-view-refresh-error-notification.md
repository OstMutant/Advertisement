# improvement-010: AdvertisementsView.refresh() swallows errors without user notification

**Type:** improvement — pattern deviation found during app code review
**Module:** marketplace-app
**Priority:** low — only visible when a refresh actually fails; no data risk
**When:** Batch F (UI dedup & polish, PR 2) — see `backlog/BACKLOG.md` "Execution batches" (2026-07-19; formerly Deferred "any nearby UI-touching PR")

## Problem

The project-wide View pattern (`.claude/rules.md`, "refresh() — always with try/catch")
requires the catch block to notify the user via `notificationService.error(...)` in addition
to logging and clearing content. `AdvertisementsView.refresh()`
(`AdvertisementsView.java:163-166`) only logs and clears:

```java
} catch (Exception ex) {
    log.error("Failed to refresh advertisements", ex);
    advertisementContainer.removeAll();
    paginationBar.setTotalCount(0);
}
```

`NotificationService` is not injected into the class at all. When a refresh fails (DB hiccup,
bad filter state), the user silently gets an empty list that looks identical to "no results" —
no error feedback, contrary to the view's own reference-implementation status (rules.md lists
`AdvertisementsView` as the init-structure reference and `UserView` as the refresh-guard
reference).

Related minor cleanup in the same area: `AdvertisementService.save(dto, actingUserId)`
(`advertisement-spring-boot-starter/.../AdvertisementService.java:95`) never uses
`actingUserId` in its body — authorship is handled by the `AuditorAware` bean. Either drop the
parameter from the service method (keeping it on the port only if the contract demands it) or
use it explicitly.

## Suggested fix

1. Inject `NotificationService` into `AdvertisementsView` and add
   `notificationService.error(...)` (with the appropriate `I18nKey`) to the catch block,
   matching `UserView`'s refresh guard.
2. Decide on `actingUserId` in `AdvertisementService.save(...)`: remove the unused parameter
   or wire it into the entity build explicitly.
