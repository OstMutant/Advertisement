# Playwright Test Coverage

Last updated: 2026-07-23 · 49 passed · 0 failed · 0 skipped · 49 total

`[x]` passed &nbsp; `[!]` failed &nbsp; `[-]` skipped

## e2e/01-marketplace-empty-flow.spec.js

**› e2e/01-marketplace-empty-flow.spec.js**
- [x] Language switch (no auth) › app loads — English locale, no admin controls visible
- [x] Language switch (no auth) › language switch — Ukrainian locale active
- [x] Language switch (no auth) › unauthenticated user — filter panel accessible, title filter, apply and clear
- [x] Language switch (no auth) › language switch — English locale restored

## e2e/02-marketplace-authentication-flow.spec.js

**› e2e/02-marketplace-authentication-flow.spec.js**
- [x] Authentication flow › adminEn signs up — first user auto-promoted to ADMIN, settings open, timeline and user audit created
- [x] Authentication flow › userEn signs up — USER role assigned, settings open, activity created
- [x] Authentication flow › userUk signs up — USER role assigned, settings open, activity created
- [x] Authentication flow › moderatorUk signs up — USER role assigned, activity created
- [x] Authentication flow › moderatorEn signs up — USER role assigned, activity created
- [x] Authentication flow › adminUk signs up — USER role assigned, activity created
- [x] Authentication flow › userEn logs in — cancel logout keeps session, confirm logout works
- [x] Authentication flow › userEn — locale persists across logout and re-login
- [x] Authentication flow › wrong password — login rejected, user stays logged out
- [x] Authentication flow › rateLimitUser exceeds login attempts — 5 wrong passwords rejected, 6th blocked with too-many-attempts message, correct password still blocked during lockout

## e2e/03-marketplace-promotion-flow.spec.js

**› e2e/03-marketplace-promotion-flow.spec.js**
- [x] Promotion flow › adminEn promotes moderatorUk to MODERATOR — activity shows updated role, role badge in view and grid
- [x] Promotion flow › adminEn promotes moderatorEn to MODERATOR — activity shows updated role, role badge in view and grid
- [x] Promotion flow › adminEn promotes adminUk to ADMIN — activity shows updated role, role badge in view and grid
- [x] Promotion flow › userUk — first login in English, switches to Ukrainian locale
- [x] Promotion flow › moderatorUk — first login in English, switches to Ukrainian locale
- [x] Promotion flow › adminEn edits userEn name — activity diff, grid updated, restore reverts name, userEn relogin after edit
- [x] Promotion flow › adminEn creates categories Electronics and Vehicles — both in list, create discard clears form
- [x] Promotion flow › adminEn edits Electronics — edit discard reverts, save records activity, restore reverts name, all fields in timeline diff, delete and restore recorded in activity, advertisement view and activity diff show struck-through category while deleted
- [x] Max-boundary users and categories › maxEn signs up — 100-char name accepted, admin verifies user created
- [x] Max-boundary users and categories › maxUk signs up — 100-char name accepted, admin verifies user created
- [x] Max-boundary users and categories › adminEn seeds 10 boundary categories — for max category selection in spec 04

## e2e/04-marketplace-advertisement-flow.spec.js

**› e2e/04-marketplace-advertisement-flow.spec.js**
- [x] Advertisement flow › userEn creates advertisement — create discard clears form, YouTube, image and video, lightbox plays video, two category rows, categories text and view chips
- [x] Advertisement flow › userUk creates advertisement — YouTube, image and video, single activity row
- [x] Advertisement flow › userEn edits advertisement — discard, two saves with activity diff, all rich formats in view and card, format-only edit, admin timeline check
- [x] Advertisement flow › userUk edits advertisement — discard, two saves with activity diff, admin timeline check
- [x] Advertisement flow › userEn restores advertisement — activity diff shows restored media and text, view and card updated
- [x] Advertisement flow › userUk restores advertisement — activity diff shows restored media and text, view and card updated
- [x] Advertisement flow › moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check
- [x] Advertisement flow › userEn and moderatorEn edit the same advertisement in two sessions — stale save shows conflict, first save wins
- [x] Advertisement flow › adminEn edits UK advertisement — discard, two saves with activity diff, category added and removed with diff, add and replace media, timeline check
- [x] Advertisement flow › userEn verifies lightbox — YouTube to image blanks iframe, WebM to image stops video
- [x] Advertisement flow › adminEn verifies long description — activity diff shows all fields, collapsible value toggle, card truncated
- [x] Max-content advertisement boundary › maxEn creates max-content EN advertisement — 255-char title, 10 categories, YouTube + image + video, lightbox, activity
- [x] Max-content advertisement boundary › maxUk creates max-content UK advertisement — 255-char title, 10 categories, YouTube + image + video, lightbox, activity
- [x] Max-content advertisement boundary › maxEn edits EN max-content advertisement — discard restores 3 items, replace all media with 10-item gallery, 255-char title v2, activity v2, gallery in view and card
- [x] Max-content advertisement boundary › maxUk edits UK max-content advertisement — discard restores 3 items, replace all media with 10-item gallery, 255-char title v2, activity v2, gallery in view and card

## e2e/05-seed-filter-sort-pagination.spec.js

**› e2e/05-seed-filter-sort-pagination.spec.js**
- [x] Seed data and query validation › seed 60 users — parallel signup
- [x] Seed data and query validation › adminEn seeds 60 advertisements — five categories
- [x] Seed data and query validation › advertisements — title, date and category filters, column sort, pagination
- [x] Seed data and query validation › users — email, role and date filters, invalid fractional ID input, column sort, pagination
- [x] Seed data and query validation › adminEn changes page sizes — activity diff, ads and users grids reflect sizes, restore defaults, no cross-session bleed
- [x] Seed data and query validation › adminEn verifies timeline — ADVERTISEMENT and USER type filters, CREATED and UPDATED action filters, multi-actor filter with chip removal, pagination

## e2e/06-marketplace-delete-flow.spec.js

**› e2e/06-marketplace-delete-flow.spec.js**
- [x] Delete flow › userEn deletes advertisement — cancel keeps card, confirm removes card and shrinks list
- [x] Delete flow › adminEn deletes user — cancel keeps row, confirm removes row and shrinks grid

## e2e/07-accessibility.spec.js

**› e2e/07-accessibility.spec.js**
- [x] Accessibility — icon-only controls have accessible names › adminEn — no unlabeled icon-only buttons across all main tabs

