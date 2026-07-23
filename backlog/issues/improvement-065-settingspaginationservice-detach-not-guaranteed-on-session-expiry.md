# improvement-065: `SettingsPaginationService`'s `DetachListener` cleanup isn't guaranteed on abrupt session expiry

**Type:** improvement — resource hygiene, follow-up to an already-fixed issue. Found via direct
code review, verified against current source (2026-07-16).
**Module:** `marketplace-app` (`ui/views/services/pagination/SettingsPaginationService.java`).
**Priority:** low-medium — a follow-up on top of a fix already shipped (improvement-018/ADR-028),
not a fresh unaddressed area; genuine risk is real but narrower than a first-discovery leak.
**When:** independent, no blockers.

## Problem

`SettingsPaginationService` is a singleton `@Component` holding a `CopyOnWriteArrayList<BindingEntry>`,
where each `BindingEntry` carries a direct reference to a `PaginationBar` (and transitively, the UI
subtree it belongs to) plus a `Runnable refresh` that typically closes over the owning `View`.

`improvement-018`/`ADR-028` already fixed the two problems found at the time: cross-session
settings bleed, and cleanup relying solely on `@PreDestroy` (fixed by adding
`bar.addDetachListener(_ -> unregister(bar))` as a safety net). That fix assumes the
`DetachListener` reliably fires whenever a session ends. It's unclear from this codebase alone
whether Vaadin guarantees a full component-tree detach (and therefore this listener firing) on
**every** session-termination path — in particular an abrupt one where the browser tab closes
without the WebSocket/heartbeat cleanly reporting it, versus a session timing out server-side
without an explicit `UI.close()`. If there's a path where the session is destroyed without the
component tree detaching cleanly, `entries` keeps a live reference to a `PaginationBar`/`View` that
can never be interacted with again — a slow, low-rate leak, not a per-request one.

## Suggested fix

Add a second, independent cleanup path that doesn't rely on component-level detach at all — e.g. a
`VaadinServiceInitListener`/`SessionDestroyListener` that explicitly removes every `entries` row
belonging to a destroyed `VaadinSession`'s user, or switch `BindingEntry.bar()` to a
`WeakReference<PaginationBar>` so an entry whose bar has been garbage-collected (regardless of
whether `unregister()` ever ran) is skipped in `onSettingsChanged()` and can be lazily pruned. This
question needs an answer that's outside what's determinable from this codebase's own source (i.e.
Vaadin's actual detach guarantee) before picking between these — worth confirming against Vaadin's
own session-lifecycle documentation before implementing either.

## Related

- `marketplace-app/DECISIONS.md` ADR-028 — the prior fix this issue follows up on (cross-session
  bleed + the `DetachListener` safety net this issue questions the completeness of).
- `backlog/completed/issues/improvement-018-settings-pagination-cross-session-bleed.md` — the
  original issue ADR-028 resolved.
