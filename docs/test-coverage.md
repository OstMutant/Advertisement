# Playwright Test Coverage

Last updated: 2026-07-01 · 39 passed · 0 failed · 0 skipped · 39 total

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

## e2e/03-marketplace-promotion-flow.spec.js

**› e2e/03-marketplace-promotion-flow.spec.js**
- [x] Promotion flow › adminEn promotes moderatorUk to MODERATOR — activity shows updated role, role badge in view and grid
- [x] Promotion flow › adminEn promotes moderatorEn to MODERATOR — activity shows updated role, role badge in view and grid
- [x] Promotion flow › adminEn promotes adminUk to ADMIN — activity shows updated role, role badge in view and grid
- [x] Promotion flow › userUk — first login in English, switches to Ukrainian locale
- [x] Promotion flow › moderatorUk — first login in English, switches to Ukrainian locale
- [x] Promotion flow › adminEn edits userEn name — activity diff, grid updated, restore reverts name
- [x] Promotion flow › adminEn creates categories Electronics and Vehicles — both in list, create discard clears form
- [x] Promotion flow › adminEn edits Electronics — edit discard reverts, save records activity, restore reverts name, all fields in timeline diff, delete and restore recorded in activity

## e2e/04-marketplace-advertisement-flow.spec.js

**› e2e/04-marketplace-advertisement-flow.spec.js**
- [x] Advertisement flow › userEn creates advertisement — create discard clears form, YouTube, image and video, lightbox plays video, two category rows, categories text and view chips
- [x] Advertisement flow › userUk creates advertisement — YouTube, image and video, single activity row
- [x] Advertisement flow › userEn edits advertisement — discard, two saves with activity diff, all rich formats in view and card, format-only edit, admin timeline check
- [x] Advertisement flow › userUk edits advertisement — discard, two saves with activity diff, admin timeline check
- [x] Advertisement flow › userEn restores advertisement — activity diff shows restored media and text, view and card updated
- [x] Advertisement flow › userUk restores advertisement — activity diff shows restored media and text, view and card updated
- [x] Advertisement flow › moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check
- [x] Advertisement flow › adminEn edits UK advertisement — discard, two saves with activity diff, category added and removed with diff, add and replace media, timeline check
- [x] Advertisement flow › userEn verifies lightbox — YouTube to image blanks iframe, WebM to image stops video
- [x] Advertisement flow › adminEn verifies long description — activity diff shows all fields, collapsible value toggle, card truncated

## e2e/05-seed-filter-sort-pagination.spec.js

**› e2e/05-seed-filter-sort-pagination.spec.js**
- [x] Seed data and query validation › seed 50 users — parallel signup
- [x] Seed data and query validation › adminEn seeds 50 advertisements — five categories
- [x] Seed data and query validation › advertisements — title, date and category filters, column sort, pagination
- [x] Seed data and query validation › users — email, role and date filters, column sort, pagination
- [x] Seed data and query validation › adminEn changes page sizes — activity diff, ads and users grids reflect sizes, restore defaults
- [x] Seed data and query validation › adminEn verifies timeline — ADVERTISEMENT and USER type filters, CREATED and UPDATED action filters, actor filter, pagination

## e2e/06-marketplace-delete-flow.spec.js

**› e2e/06-marketplace-delete-flow.spec.js**
- [x] Delete flow › userEn deletes advertisement — cancel keeps card, confirm removes card and shrinks list
- [x] Delete flow › adminEn deletes user — cancel keeps row, confirm removes row and shrinks grid

