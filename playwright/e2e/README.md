# E2E Test Suite

End-to-end tests that simulate real user scenarios against a live app + database.
Tests are **serial and ordered** — each file runs in sequence, and tests within a file depend on shared state.

Run all: `bash /app/playwright/run.sh e2e`
Run with screenshots: `bash /app/playwright/run.sh e2e --ux`

---

## 01-marketplace-empty-flow.spec.js

Runs first against an **empty database** (no users, no advertisements).
Verifies that the public-facing UI is correct for an unauthenticated visitor.

| Test | What it checks |
|------|----------------|
| open app — default locale is English | App loads in English; no add/edit/delete buttons visible; no Users or Reference Data tabs; pagination visible |
| switch to Ukrainian | Language switches to Ukrainian; same access restrictions apply |
| advertisement filter panel accessible without login | Filter panel opens, title filter can be filled and applied, status bar shows active filter text, filter clears and pagination returns |
| switch back to English | Language switches back to English; access restrictions unchanged |

---

## 02-marketplace-authentication-flow.spec.js

Runs second, building up user accounts and verifying auth behavior end-to-end.
Each section depends on state left by the previous one.

### Section 1 — Account creation

| Test | What it checks |
|------|----------------|
| register admin EN — first user becomes ADMIN | First registered user is automatically promoted to ADMIN role |
| register user EN | Regular user account created in English locale |
| register user UK and moderator UK candidates | Two accounts created after switching to Ukrainian locale |
| register moderator EN and admin UK candidates | Two more accounts created after switching back to English |

### Section 2 — Role promotion

| Test | What it checks |
|------|----------------|
| admin EN promotes moderator and admin accounts | Admin logs in, navigates to Users tab, promotes three accounts (MODERATOR × 2, ADMIN × 1), then logs out |

### Section 3 — Locale setup

| Test | What it checks |
|------|----------------|
| set UK locale for userUk and moderatorUk | Each UK user logs in and switches their locale to Ukrainian; preference is saved on logout |

### Section 4 — Authentication

| Test | What it checks |
|------|----------------|
| login — English locale (userEn) | User logs in; Advertisements tab visible; Users and Reference Data tabs hidden |
| cancel logout — stays logged in | Logout confirm dialog appears; clicking Cancel keeps the user logged in |
| confirm logout — logged out | Logout confirm dialog appears; clicking Yes logs the user out |
| login — Ukrainian locale (userUk) | User logs in with saved Ukrainian locale; UI renders in Ukrainian |
| login — Admin role (adminEn) | Admin logs in; Users and Reference Data tabs are visible |
| login — Moderator role (moderatorUk) | Moderator logs in in Ukrainian; Users and Reference Data tabs are visible |
| locale persists across sessions | User sets Ukrainian locale, logs out, logs back in — locale is still Ukrainian; switching to English and re-logging in restores English |
| wrong password — user not logged in | Login with wrong password does not authenticate; header settings button remains hidden |
