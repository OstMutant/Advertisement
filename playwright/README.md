# Playwright UI Tests

Automated E2E tests using `@playwright/test` (headless Chromium, single worker — tests are
serial and ordered, each spec file builds on state left by the previous one).

## Requirements

- Docker Desktop (Windows) or Docker Engine (Linux/WSL2)
- App image already built and `marketplace-app` container created (`bash scripts/deploy.sh` once)
- DB + MinIO containers running (started automatically by `deploy.sh`)

The `marketplace-app` container is started automatically if stopped. The database is reset
automatically before each run — see "Database reset" below.

## Running

### Linux / WSL

```bash
bash /app/playwright/run.sh                                 # all e2e tests
bash /app/playwright/run.sh e2e                              # e2e suite (01–06), skips spec 05 seed
bash /app/playwright/run.sh e2e --full                       # e2e suite including spec 05 seed (~2 min extra)
bash /app/playwright/run.sh --ux                              # all tests with screenshots
bash /app/playwright/run.sh e2e --full --ux                   # full e2e suite with screenshots
bash /app/playwright/run.sh 01-marketplace-empty-flow --ux    # single spec file, with screenshots
bash /app/playwright/run.sh e2e --grep "adminEn signs up"     # run only tests matching name
```

### Windows

`run.bat` is the entry point on Windows. It delegates to `run.sh` via WSL.

```bat
playwright\run.bat
playwright\run.bat e2e --full
playwright\run.bat e2e --full --ux
playwright\run.bat 01-marketplace-empty-flow
playwright\run.bat /?
```

Requirements: WSL2 + Docker Desktop with WSL integration enabled (`Settings → Resources → WSL Integration`).

After run:
- HTML report (with screenshots on failure): `/app/playwright/pw-report/index.html`
- Attached screenshots (`--ux` only) are embedded in the HTML report — use the `/screenshots` skill to extract named ones

## What run.sh does automatically

1. **App readiness** — if `marketplace-app` is stopped, starts it and waits for `Started Application`
   in Docker logs (up to 120 s). If the container doesn't exist, prints the exact `docker run` command and exits.
2. **Database reset** — checks whether `user_information` is already empty; if so, skips the reset
   entirely. Otherwise stops `marketplace-app`, truncates all app tables (`scripts/database/reset-clean.sql`),
   then starts the app again and waits for `Started Application`. Tables are never truncated while
   the app is live.
3. **pw-runner container** — reuses (or creates) a long-lived `mcr.microsoft.com/playwright:v1.52.0-jammy`
   container, installs the `playwright`/`@playwright/test` npm packages once, syncs the current spec
   files from `e2e/` via `docker cp`, then runs `npx playwright test`.

To start from an already-clean DB without the stop/reset/start cycle, deploy with `--reset-db`
(`bash scripts/deploy.sh --reset-db` or `bash scripts/deploy-dev.sh --reset-db`) — run.sh will
detect the empty tables and skip its own reset.

## Test accounts

Tests do not rely on pre-seeded accounts — spec `02-marketplace-authentication-flow` signs up all
accounts used by later specs via the normal sign-up flow (`e2e/_helpers.js` → `TEST_USERS`):

| Key | Email | Role | Locale |
|---|---|---|---|
| `userEn` | `user.en@example.com` | USER | en |
| `userUk` | `user.uk@example.com` | USER | uk |
| `moderatorEn` | `moderator.en@example.com` | MODERATOR | en |
| `moderatorUk` | `moderator.uk@example.com` | MODERATOR | uk |
| `adminEn` | `admin.en@example.com` | ADMIN | en |
| `adminUk` | `admin.uk@example.com` | ADMIN | uk |

Password for all accounts: `password`. The first account to sign up (`adminEn`) is
auto-promoted to `ADMIN` by the app itself; `moderatorEn`/`moderatorUk`/`adminUk` start as `USER`
and are promoted to their final role in spec 03.

---

## Scenarios (`e2e/`)

All scenarios live in `/app/playwright/e2e/01`–`06-*.spec.js`, run in order against a single
shared browser page per spec file (`test.describe.configure({ mode: 'serial' })`). Shared
helpers live in `e2e/_helpers.js` and `e2e/_flows/*.flow.js`.

Full test-by-test breakdown: see `e2e/README.md`.

| Spec | What it covers |
|---|---|
| `01-marketplace-empty-flow` | Unauthenticated UI: locale switch, filter panel, no admin controls |
| `02-marketplace-authentication-flow` | Sign-up for all test accounts, login/logout, locale persistence, wrong-password and login-rate-limit rejection |
| `03-marketplace-promotion-flow` | Role promotion, UK locale setup, cross-actor user edit, category CRUD, max-boundary user/category seeding |
| `04-marketplace-advertisement-flow` | Advertisement create/edit/restore lifecycle, media (YouTube/image/video), activity diffs, timeline, max-content boundaries |
| `05-seed-filter-sort-pagination` | *(only with `--full`)* Seeds 50 users + 50 ads, validates filters/sort/pagination, page-size settings, timeline filters |
| `06-marketplace-delete-flow` | Delete confirmation flow for advertisements and users |

## Adding a new scenario

1. Create `/app/playwright/e2e/my-scenario.spec.js`
2. `const { test, expect, screenshot, TEST_USERS } = require('./_helpers');`
3. Extract flow helpers to `e2e/_flows/*.flow.js` only if shared by 2+ spec files — otherwise
   keep helpers local to the spec file
4. Run with `bash /app/playwright/run.sh my-scenario --ux`
5. Follow the test naming pattern documented in `e2e/README.md`
