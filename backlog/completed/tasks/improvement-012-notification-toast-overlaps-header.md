# improvement-012: Success/error toast overlaps header action buttons

**Type:** improvement — UX, found during screenshot review of full e2e run
**Module:** marketplace-app
**Priority:** low — cosmetic, self-resolves when the toast times out
**When:** Week 0 — quick-wins batch (one line)
**Status:** ✅ RESOLVED (2026-07-04) — `Notification.Position.BOTTOM_END` (commit 0f02b91d)

## Problem

`NotificationService.createNotification()` positions every toast at
`Notification.Position.TOP_END` (`NotificationService.java:70`). The header bar also lives in
the top-right corner, so a toast ("Advertisement saved", validation errors, etc.) renders
directly on top of the Settings and Log Out buttons — confirmed on at least three e2e
screenshots (`adv-useren-create-saved`, `lightbox` flow, `max-en-create-saved`). Until the
toast expires the user cannot reach Settings / Log Out, and on the logged-out header it covers
Log In / Sign Up.

## Suggested fix

Move toasts out of the header zone — either:
- `Notification.Position.BOTTOM_END` (common convention, never collides with any control), or
- keep TOP_END but offset below the header via a theme CSS override on
  `vaadin-notification-container` (e.g. `top: var(--header-height)`).

Single-line change in `NotificationService.createNotification()` for the first option; no
call-site changes needed.
