## UI Verification with Playwright

After making UI changes, verify them by running the Playwright script inside Docker.

### Prerequisites
- DB and MinIO already running (started separately via scripts/infra/docker-compose.db.yml / scripts/infra/docker-compose.minio.yml)
- App image built with: `docker build -f Dockerfile -t marketplace-app .` (uses `-Pproduction`, always run with `SPRING_PROFILES_ACTIVE=prod`)
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
bash /app/playwright/run.sh smoke            # one scenario
bash /app/playwright/run.sh smoke --ux       # with local screenshots for AI analysis
bash /app/playwright/run.sh --ux             # all tests with screenshots
bash /app/playwright/run.sh e2e --ux         # e2e suite (specs 01–06, skips spec 05 seed)
bash /app/playwright/run.sh e2e --full --ux  # e2e suite including spec 05 (seeds 50 users + 50 ads)
```

**`--full` flag:** spec `05-seed-filter-sort-pagination` is skipped by default (it takes ~2 min to seed 100 entities). Pass `--full` to include it. Spec 06 (delete flow) works correctly in both modes — it creates its own ad to delete.

**IMPORTANT:** Volume mounts don't work from inside the claude container (Docker socket path issue).
`run.sh` uses `docker cp` internally — always use `run.sh`, never raw `docker run -v`.

### Workflow for UI changes
1. Make code changes
2. Rebuild image: `docker rm -f marketplace-app && docker build -f Dockerfile -t marketplace-app .`
3. Start app (command above)
4. Wait for start: run `docker logs -f marketplace-app` with `run_in_background: true`, then use Monitor tool — it streams stdout and notifies when `"Started Application"` appears
5. Run relevant scenario: `bash /app/playwright/run.sh <scenario>`
6. For UX analysis add `--ux` flag → read screenshots from `/app/playwright/screenshots/` with `Read` tool

### Vaadin-specific notes
- Vaadin uses Shadow DOM — always fill via inner input: `vaadin-text-field input`, `vaadin-text-area textarea`, `vaadin-email-field input`, `vaadin-password-field input`
- Overlays/dialogs have class `.advertisement-overlay` — scope selectors inside it to avoid hitting main page buttons
- Playwright version must match image: `playwright@1.52.0` + `mcr.microsoft.com/playwright:v1.52.0-jammy`
- `IFrame.setSrc()` / `.setProperty()` are silently ignored post-render — use `Page.executeJs()` + `setAttribute()` instead

### Adding new scenarios
1. Create `/app/playwright/my-scenario.spec.js`
2. `const { test, expect, loginAs, screenshot } = require('./_test-helpers');`
3. Run with `bash /app/playwright/run.sh my-scenario`
