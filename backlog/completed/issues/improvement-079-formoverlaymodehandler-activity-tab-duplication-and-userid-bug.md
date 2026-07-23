# improvement-079: AbstractFormOverlayModeHandler — extract activity-tab duplication; fix UserFormOverlayModeHandler's userId bug

**Type:** improvement + latent bug fix — structural duplication cleanup that surfaced a real,
currently-masked correctness bug during verification.
**Module:** `marketplace-app` (`ui/views/components/overlay/AbstractFormOverlayModeHandler.java`,
`AdvertisementFormOverlayModeHandler`, `TaxonFormOverlayModeHandler`,
`UserFormOverlayModeHandler`).
**Priority:** high — contains a real (if currently latent) correctness bug, not just duplication;
elevated above the other structural items in this batch.
**When:** after improvement-076/077 (quick wins first); before improvement-078/080 if sequencing
by risk, since this one has an actual behavioral fix riding along with the refactor.

## Problem

`AdvertisementFormOverlayModeHandler`, `TaxonFormOverlayModeHandler`, and
`UserFormOverlayModeHandler` each duplicate near-identical "Edit/Activity tabs wrapping an
`AuditActivityPanel`" choreography in their `buildContentWithActivity`/`buildActivityContent`
methods.

**Confirmed bug found during verification (not in the original proposal's framing — traced
independently through the actual SQL):** Advertisement and Taxon pass
`.userId(access.getCurrentUserId())` (the acting viewer) into `AuditActivityPanel.Parameters`;
User passes `.userId(params.getUser().id())` (the profile subject) instead.

Tracing `AuditActivityPanel` → `AuditPort.getEntityActivity(entityType, entityId, userId,
isPrivileged)` → `AuditReadService.getEntityActivity()` → `AuditLogRepository.findRows()`: the
`userId` parameter becomes `filterActorId`, applied as
`WHERE CAST(:filterActorId AS BIGINT) IS NULL OR actor_id = :filterActorId` when the viewer is
*not* privileged. This means the parameter's real semantics are "if the viewer isn't privileged,
only show activity rows where *they* were the actor" — i.e. it must always be the acting viewer's
ID, never the subject entity's ID. `UserFormOverlayModeHandler`'s `params.getUser().id()` is a
bug: it should be `access.getCurrentUserId()`, matching Advertisement/Taxon.

**Why this hasn't caused an observed incident:** the activity tab is only reachable when
`access.canOperate(params.getUser().id())` is true — for `User` entities, ownership means "is this
your own account," so owner and viewer always coincide (`params.getUser().id() ==
access.getCurrentUserId()`) in the self-view case; for privileged viewers (admin/moderator), the
filter short-circuits to `null` (`showAll`) regardless of which ID was passed. Both existing call
paths mask the bug. It would surface if `canOperate`'s ownership semantics ever changed (e.g. a
future "manage sub-accounts" feature).

## Suggested fix

- Extract the shared choreography into `AbstractFormOverlayModeHandler`:
```java
protected Div buildContentWithActivity(EntityType entityType, Long entityId, boolean canOperate,
        Div editContent, boolean isCreateMode, java.util.function.LongConsumer onRestoreRequested,
        ComponentFactory<AuditPort> auditPortFactory,
        UiComponentFactory<AuditActivityPanel> panelFactory, AccessEvaluator access) {
    if (isCreateMode) return editContent;
    return auditPortFactory.findIfAvailable()
        .map(_ -> { /* build editTab/activityTab/topTabs, delegate to buildTabbedContent(...) */ })
        .orElse(editContent);
}
```
- Standardize all three call sites on `.userId(access.getCurrentUserId())` — fixing
  `UserFormOverlayModeHandler` to match Advertisement/Taxon, not the other way around.
- Add or update a test that actually exercises the previously-masked path (a non-privileged viewer
  with ownership-based `canOperate` access, where subject ID and viewer ID differ) — e2e as it
  exists today would not catch this, since nobody knew the discrepancy existed. A Playwright
  scenario or unit test that would fail under the old `params.getUser().id()` behavior and pass
  under the fix is required before closing this out.

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-076
  through improvement-078 and improvement-080 through improvement-083 for the rest.
