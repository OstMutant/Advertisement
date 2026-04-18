# Advertisement Project Architecture Rules

## Core Stack
- Java 25 (Use modern features: Records, Pattern Matching, Switch expressions).
- Spring Boot (Web, Security, Data JDBC).
- Pure SQL via `NamedParameterJdbcTemplate` (NO JPA, NO HIBERNATE).
- Vaadin for UI.

## Architecture Guidelines
1. **Explicit over implicit:** Avoid hidden framework magic. If simple Java code works, use it.
2. **Strict Boundaries:** The UI layer MUST NOT call Repositories directly. Always use `UserService` or `AdvertisementService`.
3. **Modular Storage:** We use a strict modular structure. `storage-api` is the contract, `storage-s3-spring-boot-starter` is the implementation. UI components (like `AttachmentGallery`) MUST degrade gracefully (using `ObjectProvider.ifAvailable()`) if `storage.s3.enabled=false`.
4. **Validation:** Use declarative validation rules in DTOs.
5. **Database Changes:** Database schema must ONLY be modified via Liquibase scripts in `db/changelog/changes`.

When writing code or refactoring, strictly respect these boundaries. Think about which module a feature belongs to before implementing it.

## UI Verification with Playwright

After making UI changes, verify them by running the Playwright script inside Docker.

### Prerequisites
- App must be running: `docker run -d --name advertisement-app --network host -e SPRING_PROFILES_ACTIVE=prod -e DB_HOST=localhost -e DB_PORT=5432 -e DB_NAME=experiments -e DB_USER=experiments_user -e DB_PASSWORD=experiments_user_password -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 -e S3_REGION=us-east-1 -e S3_PUBLIC_URL=http://localhost:9000/advertisement advertisement-app`
- DB and MinIO already running (started separately via docker-compose.db.yml / docker-compose.minio.yml)
- App image built with: `docker build -f Dockerfile -t advertisement-app .` (uses `-Pproduction`, always run with `SPRING_PROFILES_ACTIVE=prod`)

### Scripts location
All scenarios live in `/app/playwright/`. Run via `run.sh`:
```bash
bash /app/playwright/run.sh add-advertisement
bash /app/playwright/run.sh edit-advertisement
bash /app/playwright/run.sh add-advertisement --ux   # with screenshots
```

**IMPORTANT:** Volume mounts don't work from inside the claude container (Docker socket path issue).
`run.sh` uses `docker cp` internally — always use `run.sh`, never raw `docker run -v`.

### Workflow for UI changes
1. Make code changes
2. Rebuild image: `docker rm -f advertisement-app && docker build -f Dockerfile -t advertisement-app .`
3. Start app: `docker run -d --name advertisement-app --network host -e SPRING_PROFILES_ACTIVE=prod -e DB_HOST=localhost -e DB_PORT=5432 -e DB_NAME=experiments -e DB_USER=experiments_user -e DB_PASSWORD=experiments_user_password -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 -e S3_REGION=us-east-1 -e S3_PUBLIC_URL=http://localhost:9000/advertisement advertisement-app`
4. Wait for start: `docker logs advertisement-app | grep "Started Application"`
5. Run relevant scenario: `bash /app/playwright/run.sh <scenario>`
6. For UX analysis add `--ux` flag → read screenshots from `/tmp/screenshots/` with `Read` tool

### Vaadin-specific notes
- Vaadin uses Shadow DOM — always fill via inner input: `vaadin-text-field input`, `vaadin-text-area textarea`, `vaadin-email-field input`, `vaadin-password-field input`
- Overlays/dialogs have class `.advertisement-overlay` — scope selectors inside it to avoid hitting main page buttons
- Playwright version must match image: `playwright@1.52.0` + `mcr.microsoft.com/playwright:v1.52.0-jammy`

### Adding new scenarios
1. Create `/app/playwright/my-scenario.js`
2. `const { check, screenshot, login } = require('./_common');`
3. Run with `bash /app/playwright/run.sh my-scenario`