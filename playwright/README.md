# Playwright UI Tests

Automated E2E tests using `@playwright/test` (headless Chromium).

## Requirements

- App running at `localhost:8080` (container `advertisement-app`)
- Docker socket available

## Running

```bash
# All tests
bash /app/playwright/run.sh

# Single scenario
bash /app/playwright/run.sh smoke
bash /app/playwright/run.sh add-advertisement

# With local screenshots (for AI analysis via Read tool)
bash /app/playwright/run.sh --ux
bash /app/playwright/run.sh smoke --ux
```

After run:
- HTML report (with screenshots on failure): `/app/playwright/pw-report/index.html`
- Local screenshots (`--ux` only): `/app/playwright/screenshots/`

## Scenarios

| File | What it tests |
|---|---|
| `smoke.spec.js` | Language switch, full user flow, admin flow |
| `add-advertisement.spec.js` | Creating an advertisement |
| `edit-advertisement.spec.js` | Editing an advertisement |
| `advertisement-history.spec.js` | History: create → edit → restore |
| `history-deep.spec.js` | Versions, badges, restore flow |
| `filter-advertisements.spec.js` | Filtering and sorting advertisements |
| `filter-users.spec.js` | Filtering users (admin) |
| `upload-image.spec.js` | Uploading a single photo |
| `upload-gallery.spec.js` | Uploading multiple photos |
| `photo-activity.spec.js` | Photo diffs in history and activity |
| `verify-photo-history.spec.js` | Photo changes in history tab |
| `verify-thumbnail-history.spec.js` | Thumbnails on cards + history tab |
| `settings.spec.js` | Settings: changing page size |
| `settings-activity.spec.js` | Activity on settings change |
| `activity-types.spec.js` | CREATED / UPDATED / DELETED badges |
| `user-activity.spec.js` | Activity in user profile |
| `user-edit-diff.spec.js` | Diff on user edit |
| `users-view.spec.js` | Users grid (admin): view + edit overlay |
| `test-view.spec.js` | Opening advertisement detail overlay |
| `change-language.spec.js` | Language switching (unauthenticated) |

## Adding a new scenario

1. Create `/app/playwright/my-scenario.spec.js`
2. `const { test, expect, loginAs, ... } = require('./_test-helpers');`
3. Run with: `bash /app/playwright/run.sh my-scenario`

## Test data

| Email | Role |
|---|---|
| `user1@example.com` | regular user |
| `user2@example.com` | regular user |
| `user3@example.com` | admin |

Password for all accounts: `password`
