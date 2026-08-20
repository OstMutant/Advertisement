# Playwright UI Tests

Automated E2E tests using `@playwright/test` (headless Chromium). See `playwright.config.js`'s own
header for the run-mode configuration and `e2e/README.md` for the test suite's own structure.

## Requirements

- Docker Desktop (Windows) or Docker Engine (Linux/WSL2)
- App image already built and `marketplace-app` container created (`bash scripts/deploy-and-run.sh` once)
- DB + MinIO containers running (started automatically by `deploy-and-run.sh`)

The `marketplace-app` container is started automatically if stopped. The database is reset
automatically before each run — see "Database reset" below.

## Running

```bash
bash /app/playwright/run.sh                                 # all e2e tests
bash /app/playwright/run.sh e2e                              # e2e suite (01–07), skips spec 05 seed
bash /app/playwright/run.sh e2e --full                       # e2e suite including spec 05 seed (~2 min extra)
bash /app/playwright/run.sh --ux                              # all tests with screenshots
bash /app/playwright/run.sh e2e --full --ux                   # full e2e suite with screenshots
bash /app/playwright/run.sh 01-marketplace-empty-flow --ux    # single spec file, with screenshots
bash /app/playwright/run.sh e2e --grep "adminEn signs up"     # run only tests matching name
```

After run:
- HTML report (with screenshots on failure): `/app/playwright/pw-report/index.html`
- Attached screenshots (`--ux` only) are embedded in the HTML report — use the `/screenshots` skill to extract named ones

See `run.sh`'s own header for what it does automatically (app readiness, DB reset, `pw-runner`
container reuse).

## Test accounts

Tests do not rely on pre-seeded accounts — spec `02-marketplace-authentication-flow` signs up all
accounts used by later specs via the normal sign-up flow (see `e2e/_helpers.js`'s own header for
the account list). The first account to sign up is auto-promoted to `ADMIN` by the app itself; the
other two moderator/admin accounts start as `USER` in spec 02 and are promoted to their final role
in spec 03.

---

## Scenarios (`e2e/`)

All scenarios live in `/app/playwright/e2e/01`–`07-*.spec.js`, run in order against a single
shared browser page per spec file (`test.describe.configure({ mode: 'serial' })`). Shared
helpers live in `e2e/_helpers.js` and `e2e/_flows/*.flow.js`. Each spec file's own header states
what it covers — see `e2e/README.md` for the suite's test-naming convention.

## Adding a new scenario

1. Create `/app/playwright/e2e/my-scenario.spec.js`
2. `const { test, expect, screenshot, TEST_USERS } = require('./_helpers');`
3. Extract flow helpers to `e2e/_flows/*.flow.js` only if shared by 2+ spec files — otherwise
   keep helpers local to the spec file
4. Run with `bash /app/playwright/run.sh my-scenario --ux`
5. Follow the test naming pattern documented in `e2e/README.md`
