---
paths: ["playwright/**"]
---

## UI Verification with Playwright

After making UI changes, verify them by running the Playwright script inside Docker.

### Prerequisites
- DB and MinIO already running (started separately via scripts/deploy-and-run/docker-compose.db.yml / scripts/deploy-and-run/docker-compose.minio.yml)
- App image built with: `docker build -f Dockerfile -t marketplace-app .` (always run with `SPRING_PROFILES_ACTIVE=prod`, which sets `vaadin.productionMode=true`)
- App must be running:
```bash
docker run -d --name marketplace-app --network host \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=localhost -e DB_PORT=5432 -e DB_NAME=experiments \
  -e DB_USER=experiments_user -e DB_PASSWORD=experiments_user_password \
  -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement \
  -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 \
  -e S3_REGION=us-east-1 -e S3_PUBLIC_URL=http://localhost:9000/advertisement \
  marketplace-app
```

### Scripts location
All scenarios live in `/app/playwright/`. Run via `run.sh`:
```bash
bash /app/playwright/run.sh                  # all tests
bash /app/playwright/run.sh --ux             # all tests with screenshots
bash /app/playwright/run.sh e2e --ux         # e2e suite (specs 01–06, skips spec 05 seed)
bash /app/playwright/run.sh e2e --full --ux  # e2e suite including spec 05 (seeds 60 users + 60 ads)
bash /app/playwright/run.sh 01-marketplace-empty-flow --ux  # single spec file, with screenshots
```

**`--full` flag:** spec `05-seed-filter-sort-pagination` is skipped by default (it takes ~2 min to seed 120 entities — `SEED_COUNT = 60`). Pass `--full` to include it. Spec 06 (delete flow) works correctly in both modes — it creates its own ad to delete.

**IMPORTANT:** Volume mounts don't work from inside the claude container (Docker socket path issue).
`run.sh` uses `docker cp` internally — always use `run.sh`, never raw `docker run -v`.

**Don't run a single spec file in isolation unless it's genuinely self-contained.** Per
`playwright/README.md`, specs are serial and ordered — most later specs depend on state earlier
specs create (registered `TEST_USERS`, seeded categories/cities). Running e.g. spec 04 alone
against a database that never had specs 01-03 run against it fails at the very first login, not at
whatever behavior the spec was meant to verify. Run the full `e2e --ux` suite (or `e2e --full --ux`
only when the change actually touches spec 05's seeded-pagination scenario — `--full` is not
needed just to make a later spec's preconditions exist, since categories/cities are seeded in spec
03, not spec 05).

**Always deploy with a clean database (`bash scripts/deploy-and-run.sh --reset-only-db` — truncate,
fast — or `--reset` — full DB/MinIO volume wipe, only needed when the schema itself changed)
immediately before running the full `e2e --ux` suite for verification purposes** — never reuse
whatever state happens to be sitting in the dev DB from an earlier run this session. This is not
optional/best-effort: rerunning the same full suite twice against a non-reset DB produces test
failures from state pollution (e.g. leftover rows from the first run's category/city assignments)
that look like real regressions but aren't — confirmed directly (a `category added and removed
with diff` assertion failed on a reused DB, passed clean on a fresh one, no code difference between
the two runs). Clear the data first, every time, rather than debugging a failure and only then
guessing it might be stale state. `bash scripts/run-all-tests.sh` already enforces this
automatically — its own `deploy-and-run.sh` call always clears data first (`--reset-only-db` by
default, `--reset` when `--reset` is passed to `run-all-tests.sh` itself), never conditional — so
this manual step is only needed when running `playwright.sh`/`playwright/run.sh` directly, outside
that orchestrator.

### Workflow for UI changes
1. Make code changes
2. Rebuild image: `docker rm -f marketplace-app && docker build -f Dockerfile -t marketplace-app .`
3. Start app (command above)
4. Wait for start: run `docker logs -f marketplace-app` with `run_in_background: true`, then use Monitor tool — it streams stdout and notifies when `"Started Application"` appears
5. Run relevant scenario: `bash /app/playwright/run.sh <scenario>`
6. For UX analysis add `--ux` flag → screenshots are embedded in the HTML report (`/app/playwright/pw-report/index.html`) — there is no standalone `/app/playwright/screenshots/` directory; use the `/screenshots` skill to extract and read them by name

### Vaadin-specific notes
- Vaadin uses Shadow DOM — always fill via inner input: `vaadin-text-field input`, `vaadin-text-area textarea`, `vaadin-email-field input`, `vaadin-password-field input`
- Overlays/dialogs have class `.advertisement-overlay` — scope selectors inside it to avoid hitting main page buttons
- Playwright version must match image: `playwright@1.61.1` + `mcr.microsoft.com/playwright:v1.61.1-jammy`
- `IFrame.setSrc()` / `.setProperty()` are silently ignored post-render — use `Page.executeJs()` + `setAttribute()` instead
- Never use `page.waitForTimeout()` to wait for animations or dialog rendering — fixed timeouts are fragile and slow. Wait on the relevant Vaadin state attribute instead (e.g. `vaadin-confirm-dialog-overlay[opened]` with `state: 'attached'` for confirm dialogs; the equivalent attribute for other overlays).
- When asserting timeline/activity rows, verify actual displayed content (actor name, entity title, diff text) — CSS class presence alone is not enough to confirm the *right* data rendered.

### Helper organization rules

- `e2e/_helpers.js` — only truly shared utilities used across **multiple** files: `TEST_USERS`, media constants, overlay/notification helpers, `screenshot`, `downloadPng`. Do NOT add anything that is only used in one spec or one flow file.
- `e2e/_flows/*.flow.js` — flow-specific helpers live in the same file where they are used. Extract to a shared flow file only when two or more flow files need the same helper.
- Spec-specific helpers (navigation, tab switching, etc.) that are only used in one spec file belong as local functions at the top of that spec file.

### Test naming pattern

```
{actor} {action} {subject} — {verification1}, {verification2}, {verification3}
```

Example: `moderatorEn edits EN advertisement — discard, two saves with activity diff, add and replace media, timeline check`

Rules:
- After the dash: list each major verified behaviour explicitly
- Use concrete operation words: "discard", "save with activity diff", "add and replace media", "timeline check", "restore", "pagination"
- Avoid vague labels like "badge check", "flow" as the only descriptor
- Version numbers (v5/v6) → plain words ("two saves", "three edits")
- Include "timeline check" when timeline is verified, "activity diff" when diff content is asserted

### Extend an existing test before adding a new one

When a new check belongs to a flow an existing test already sets up (same actor, same entity,
same overlay already open), add a `test.step(...)` to that test instead of writing a new
top-level `test(...)`. Only add a new `test(...)` when the scenario needs its own independent
setup that doesn't fit inside an existing test's flow. Extending means less duplicated
create/login/logout boilerplate and keeps related assertions next to the state they depend on.
Update the test's name (per "Test naming pattern" below) to list the newly added verification.

### Adding new scenarios
1. Create `/app/playwright/e2e/my-scenario.spec.js`
2. `const { test, expect, screenshot } = require('./_helpers');`
3. Run with `bash /app/playwright/run.sh my-scenario`
