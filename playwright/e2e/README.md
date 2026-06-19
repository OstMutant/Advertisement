# E2E Test Suite

End-to-end tests that simulate real user scenarios against a live app + database.
Tests are **serial and ordered** — each spec file depends on state left by the previous one.

Run all:            `bash /app/playwright/run.sh e2e --ux`
Run with seed data: `bash /app/playwright/run.sh e2e --full --ux`

---

## 01 — Empty state (no auth)

Runs against an empty database. Verifies unauthenticated UI.

| Test | Flow |
|------|------|
| default locale is English | load → UI in EN, no admin controls visible |
| switch to Ukrainian | language switch → UI in UK |
| filter panel accessible without login | open filter → fill title → apply → clear |
| switch back to English | language switch → UI in EN |

---

## 02 — Authentication flow

Builds up all user accounts, verifies auth behaviors and audit trail after signup.

### Account creation

| Test | Flow |
|------|------|
| adminEn signs up — first user is ADMIN | signup → settings open → activity (v1 created with defaults) → users tab → user audit (v1 created) |
| userEn signs up — USER role | signup → settings open → activity (v1 created) |
| userUk signs up — USER role | switch to UK → signup → settings → activity (v1 created) |
| moderatorUk signs up — MODERATOR candidate | signup → settings → activity (v1 created) |
| moderatorEn signs up — MODERATOR candidate | switch to EN → signup → settings → activity (v1 created) |
| adminUk signs up — ADMIN candidate | signup → settings → activity (v1 created) |

### Authentication

| Test | Flow |
|------|------|
| userEn logs in, cancels logout | login → cancel logout dialog → confirm logout |
| locale persists across logout and re-login | login → switch to UK → logout → login → UK locale active → switch to EN → logout → login → EN locale active |
| wrong password — stays logged out | fill wrong password → submit → header settings button not visible |

---

## 03 — Promotion flow

Promotes accounts to their final roles, sets UK locales, verifies cross-actor user editing.

### Role promotion

| Test | Flow |
|------|------|
| adminEn promotes moderatorUk to MODERATOR | login → users tab → edit user → activity (v2 updated role + v1 created, unchanged items) → VIEW (role badge) → grid (role badge) |
| adminEn promotes moderatorEn to MODERATOR | same flow |
| adminEn promotes adminUk to ADMIN | same flow |

### UK locale setup

| Test | Flow |
|------|------|
| userUk — first login defaults to EN, switches to Ukrainian | login (EN) → switch locale → logout |
| moderatorUk — same | login (EN) → switch locale → logout |

### Cross-actor user edit

| Test | Flow |
|------|------|
| adminEn: cross-actor user name edit visible in activity and grid, restore reverts | login → rename userEn → grid (new name) → activity (updated+editedName, restore btn) → restore → activity (restore entry+originalName, ≥2 rows) → grid (originalName) |

---

## 04 — Advertisement flow

Full lifecycle: create → edit → restore → cross-user edit, with media and audit coverage.

| Test | Flow |
|------|------|
| userEn creates advertisement | login → create (title+desc+YouTube+image+WebM) → activity (v1, no restore btn) → lightbox (play icon, video src) |
| userUk creates advertisement | same flow in Ukrainian |
| userEn edits advertisement | login → discard changes check → edit (delete all media, new title+desc) → activity (v2 → diff, media field) → text-only edit → activity (v3) → grid (updated title+desc) |
| userUk edits advertisement | same flow |
| userEn restores advertisement | login → activity (3 rows) → restore v1 → form (title+desc+3 media) → save → activity (v4 restore entry → diff) → VIEW → grid (restored title+desc+3 media) |
| userUk restores advertisement | same flow |
| moderatorEn edits EN advertisement — cross-user edit with badge check | login as moderator → edit ad → activity (current-editor badge) → media replace |
| adminEn edits UK advertisement — cross-user edit with badge check | login as admin → edit ad → activity (current-editor badge) → media replace |
| userEn verifies lightbox | YouTube→image blanks iframe; WebM→image stops video |

---

## 05 — Seed, filter, sort, pagination (requires `--full`)

Seeds 50 users and 50 advertisements, then validates filtering, sorting, pagination and settings.

| Test | Flow |
|------|------|
| seed 50 users via signup | parallel signup of 50 users |
| seed 50 advertisements as adminEn | create 50 ads across 5 categories |
| advertisement filters, sort, and pagination | apply title/date/category filters → sort columns → verify page counts |
| user filters, sort, and pagination | apply email/role/date filters → sort columns → verify page counts |
| settings: change page sizes, verify in activity and views, restore defaults | change sizes (5/3) → activity (both fields shown, → diff, restore btn) → ads grid (1–5 of) → users grid (1–3 of) → restore → activity (≥2 rows) → ads grid (1–20 of) → users grid (1–20 of) → change one size → switch to activity tab → save → form visible → restore → close |

---

## 06 — Delete flow

| Test | Flow |
|------|------|
| userEn: cancel delete keeps card, confirm delete removes card | create ad → cancel delete → card still visible → confirm delete → card gone → list shrinks |
| adminEn: cancel delete keeps user row, confirm delete removes row | cancel delete → row still visible → confirm delete → row gone → grid shrinks |
