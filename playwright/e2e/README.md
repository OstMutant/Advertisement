# E2E Test Suite

End-to-end tests that simulate real user scenarios against a live app + database.
Tests are **serial and ordered** — each spec file depends on state left by the previous one.

Run all:            `bash /app/playwright/run.sh e2e --ux`
Run with seed data: `bash /app/playwright/run.sh e2e --full --ux`

---

## Test naming pattern

```
{actor} {action} {subject} — {verification1}, {verification2}, {verification3}
```

Example: `moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check`

- After the dash: list each major verified behaviour explicitly
- Use concrete words: "discard", "save with activity diff", "timeline check", "restore", "pagination"
- Avoid vague labels like "badge check" or "flow" as the only descriptor
- Version numbers → plain words ("two saves", not "v5/v6")

---

## 01 — Empty state (no auth)

Runs against an empty database. Verifies unauthenticated UI.

| Test | Flow |
|------|------|
| app loads — English locale, no admin controls visible | load → UI in EN, no admin controls visible |
| language switch — Ukrainian locale active | language switch → UI in UK |
| unauthenticated user — filter panel accessible, title filter, apply and clear | open filter → fill title → apply → clear |
| language switch — English locale restored | language switch → UI in EN |

---

## 02 — Authentication flow

Builds up all user accounts, verifies auth behaviors and audit trail after signup.

### Account creation

| Test | Flow |
|------|------|
| adminEn signs up — first user auto-promoted to ADMIN, settings open, timeline and user audit created | signup → settings open → activity (v1 created with defaults) → timeline (actor picker visible) → users tab → user audit (v1 created) |
| userEn signs up — USER role assigned, settings open, activity created | signup → settings open → activity (v1 created) |
| userUk signs up — USER role assigned, settings open, activity created | switch to UK → signup → settings → activity (v1 created) |
| moderatorUk signs up — USER role assigned, activity created | signup → settings → activity (v1 created) |
| moderatorEn signs up — USER role assigned, activity created | switch to EN → signup → settings → activity (v1 created) |
| adminUk signs up — USER role assigned, activity created | signup → settings → activity (v1 created) |

### Authentication

| Test | Flow |
|------|------|
| userEn logs in — cancel logout keeps session, confirm logout works | login → cancel logout dialog → confirm logout |
| userEn — locale persists across logout and re-login | login → switch to UK → logout → login → UK locale active → switch to EN → logout → login → EN locale active |
| wrong password — login rejected, user stays logged out | fill wrong password → submit → header settings button not visible |

---

## 03 — Promotion flow

Promotes accounts to their final roles, sets UK locales, verifies cross-actor user editing.

### Role promotion

| Test | Flow |
|------|------|
| adminEn promotes moderatorUk to MODERATOR — activity shows updated role, role badge in view and grid | login → users tab → edit user → activity (v2 updated role + v1 created, unchanged items) → VIEW (role badge) → grid (role badge) |
| adminEn promotes moderatorEn to MODERATOR — activity shows updated role, role badge in view and grid | same flow |
| adminEn promotes adminUk to ADMIN — activity shows updated role, role badge in view and grid | same flow |

### UK locale setup

| Test | Flow |
|------|------|
| userUk — first login in English, switches to Ukrainian locale | login (EN) → switch locale → logout |
| moderatorUk — first login in English, switches to Ukrainian locale | login (EN) → switch locale → logout |

### Cross-actor user edit

| Test | Flow |
|------|------|
| adminEn edits userEn name — activity diff, grid updated, restore reverts name | login → rename userEn → grid (new name) → activity (updated+editedName, restore btn) → restore → activity (restore entry+originalName, ≥2 rows) → grid (originalName) |

---

## 04 — Advertisement flow

Full lifecycle: create → edit → restore → cross-user edit, with media, audit and timeline coverage.

| Test | Flow |
|------|------|
| userEn creates advertisement — YouTube, image and video, lightbox plays video, single activity row | login → create (title+desc+YouTube+image+WebM) → activity (v1, no restore btn) → lightbox (play icon, video src) |
| userUk creates advertisement — YouTube, image and video, single activity row | same flow in Ukrainian |
| userEn edits advertisement — discard, two saves with activity diff, admin timeline check | login → **discard: delete all media → gallery restored** → **discard: add YouTube → gallery restored** → save v2 (delete all media, new title+desc) → activity (diff: title+desc+media) → save v3 (text-only) → activity (media field shows `—`) → grid → **admin timeline → ≥2 updated rows** |
| userUk edits advertisement — discard, two saves with activity diff, admin timeline check | same flow → **admin timeline → ≥4 updated rows** |
| userEn restores advertisement — activity diff shows restored media and text, view and card updated | login → activity (3 rows) → restore v1 → form (title+desc+3 media) → save v4 → activity (restore diff) → VIEW → grid (restored title+desc+3 media) |
| userUk restores advertisement — activity diff shows restored media and text, view and card updated | same flow |
| moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check | login as moderator → edit (discard checks + save) → activity (editor badge) → media add+replace → **timeline → ≥4 updated rows, titleText, actorText** |
| adminEn edits UK advertisement — discard, two saves with activity diff, add and replace media, timeline check | login as admin → edit (discard checks + save) → activity (editor badge) → media add+replace → **timeline → ≥4 updated rows, titleText, actorText** |
| userEn verifies lightbox — YouTube to image blanks iframe, WebM to image stops video | YouTube→image: iframe src blanked → WebM→image: video stops |

---

## 05 — Seed, filter, sort, pagination (requires `--full`)

Seeds 50 users and 50 advertisements, then validates filtering, sorting, pagination and settings.

| Test | Flow |
|------|------|
| seed 50 users — parallel signup | parallel signup of 50 users |
| adminEn seeds 50 advertisements — five categories | create 50 ads across 5 categories |
| advertisements — title, date and category filters, column sort, pagination | apply title/date/category filters → sort columns → verify page counts |
| users — email, role and date filters, column sort, pagination | apply email/role/date filters → sort columns → verify page counts |
| adminEn changes page sizes — activity diff, ads and users grids reflect sizes, restore defaults | change sizes (5/3) → activity (both fields shown, diff, restore btn) → ads grid (1–5 of) → users grid (1–3 of) → restore → activity (≥2 rows) → ads grid (1–20 of) → users grid (1–20 of) → change one size → switch to activity tab → save → form visible → restore → close |
| adminEn verifies timeline — ADVERTISEMENT and USER type filters, CREATED and UPDATED action filters, actor filter, pagination | total > SEED_COUNT → ADVERTISEMENT type filter (all rows advertisement) → USER type filter (all rows user) → CREATED action filter (all rows created) → UPDATED action filter (all rows updated) → actor filter by adminEn (count < total) → clear → pagination page 2 |

---

## 06 — Delete flow

| Test | Flow |
|------|------|
| userEn deletes advertisement — cancel keeps card, confirm removes card and shrinks list | create ad → cancel delete → card still visible → confirm delete → card gone → list shrinks |
| adminEn deletes user — cancel keeps row, confirm removes row and shrinks grid | cancel delete → row still visible → confirm delete → row gone → filter clear → absent in full list |
