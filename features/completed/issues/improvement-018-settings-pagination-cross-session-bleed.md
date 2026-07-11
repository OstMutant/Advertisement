# improvement-018: SettingsPaginationService — cross-session settings bleed (bug) + UI-reference leak

**Status:** ✅ RESOLVED (2026-07-11) — `BindingEntry` now carries the owning `userId`;
`onSettingsChanged` filters entries by `entry.userId().equals(userId)` instead of gating on the
current thread's user, killing the bleed regardless of which thread fires the hook. Added
`bar.addDetachListener(_ -> unregister(bar))` in `register()` for the leak, so cleanup no
longer depends solely on `@PreDestroy`. See `marketplace-app/DECISIONS.md` ADR-028. Playwright
coverage: extended `05-seed-filter-sort-pagination.spec.js` — `adminEn changes page sizes...`
test now opens a second browser context as `userEn` and asserts their grid is unaffected by
`adminEn`'s change. Full e2e suite 47/47 green.

**Type:** bug + improvement — found via external audit (round 5, leak) and internal verification (bleed — the worse half)
**Module:** marketplace-app
**Priority:** medium-high — real multi-user defect, invisible in single-user e2e
**When:** Wave 2, top of the wave — real multi-user bug, fix before public traffic

## Problem A — cross-session settings bleed (bug, found during verification)

`SettingsPaginationService` is a singleton `@Component` holding
`CopyOnWriteArrayList<BindingEntry>` where entries from **all users' UIs** accumulate
(`BindingEntry(bar, extractor, refresh)` — no owner association).

`onSettingsChanged(userId, settings)` (`SettingsPaginationService.java:38-46`) filters only
"is the current thread's user the one who changed settings" — and then updates **every
registered bar of every session**:

```java
authContextService.getCurrentUser()
        .filter(u -> u.id().equals(userId))
        .ifPresent(_ -> entries.forEach(e ->            // ALL entries, ALL sessions
                e.bar().getUI().ifPresent(ui -> ui.access(() -> {
                    e.bar().setPageSize(e.extractor().applyAsInt(settings));
                    e.refresh().run();
                }))));
```

When user X saves a new page size, users Y and Z get X's page size applied to their live
grids (until they re-enter the view, which re-reads their own settings). Single-user e2e
(spec 05 test 43) cannot catch this — it changes settings for one logged-in user at a time.

## Problem B — UI-reference leak risk (external reviewer's original finding)

The singleton holds strong references to `PaginationBar` (→ the whole UI tree). Cleanup
relies on views calling `unregister()` in `@PreDestroy`; any path where that callback is
skipped leaves dead UI trees pinned in the service forever.

## Suggested fix (one change covers both)

1. Add the owner to the entry: `BindingEntry(Long userId, PaginationBar bar, ...)` — captured
   at `register(...)` from `AuthContextService`.
2. In `onSettingsChanged`, iterate only entries with `entry.userId().equals(userId)` — kills
   the bleed regardless of which thread fires the hook (the `getCurrentUser()` pre-filter
   can then be dropped entirely).
3. For the leak: register a detach listener on the bar at `register(...)` time
   (`bar.addDetachListener(e -> unregister(bar))`) so cleanup no longer depends solely on
   `@PreDestroy`; keep `unregister()` for the explicit path.
4. Playwright coverage per the bug-fix rule: two parallel sessions (two browser contexts),
   user X changes page size → assert user Y's grid page size is unchanged.
