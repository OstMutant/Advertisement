# Audit UI Updating — Spec

## Problem statement

The current audit UI mixes two conceptually different things in the same tab:
- **Restorable history** — changes to a specific entity (settings, user profile) that can be rolled back
- **Activity feed** — everything the actor did across the whole system (ads, users, settings)

This makes the History tab noisy and confusing: it shows ad/user operations alongside settings changes, even though only settings changes are restorable in that context.

## Goals

### Settings overlay
- **History tab** (existing): show only settings changes for this user — with restore capability. Remove ad/user operations from this tab.
- **All Activity tab** (new): show everything this user did across the whole system (ads, users, settings) — no restore buttons, read-only feed.

### User overlay (admin view)
- **History tab** (existing): show only user profile changes for this user — with restore capability. Remove unrelated operations.
- **All Activity tab** (new): show everything this user did across the whole system — no restore buttons, read-only feed.

## Non-goals

- Advertisement overlay — not changing (history tab stays as-is)
- Changing the restore mechanism itself
- Pagination or infinite scroll for the activity feed
- Filtering within the All Activity tab

## Resolved questions

<!-- Q: ...
     A: ... -->

## Deferred items

<!-- Things that came up but are not part of this feature -->
