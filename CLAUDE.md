# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

> ## ⛔ NEVER commit without explicit user request
> `git commit` is **forbidden** unless the user says "зроби коміт", "commit", or equivalent.
> `git add` runs automatically after every file change — commit does NOT.
> Violating this rule has happened multiple times. No exceptions.

---

## Approval Rule
**Every action must be approved by the user before execution — no exceptions.**
Before doing anything, provide a precise plan:
- Exactly which files will be changed (full paths)
- Exactly what will be changed in each file (method/class/field/SQL/config)
- Any side-effects or follow-up steps

Wait for explicit confirmation before making any change.

## Git Workflow
- `git add` — run automatically after every file change
- `git commit` — **ONLY** when the user explicitly says to commit — never otherwise

## Language
All repository content must be in **English**: code comments, Javadoc, README files, commit messages, Playwright test descriptions, and any other text checked into the repository.

## Core Stack
- Java 25 (use modern features: Records, Pattern Matching, Switch expressions).
- Spring Boot 4.0.6, Vaadin 25.1.5.
- Pure SQL via `JdbcClient` / `NamedParameterJdbcTemplate` (NO JPA, NO HIBERNATE).
- Liquibase for all schema changes.

---

## Module Layout

```
advertisement-parent (root pom)
├── sql-engine                       — framework-agnostic SQL query-building library
├── advertisement-contracts          — shared kernel: DTOs, domain events, SPI interfaces
├── audit-spring-boot-starter        — audit subsystem: write + read side + activity UI (auto-configured starter)
├── attachment-spring-boot-starter   — photo/attachment module + S3 storage (auto-configured starter)
└── advertisement-app                — main Vaadin application
```

**sql-engine** has no Spring Boot autoconfiguration — it is a plain library. It provides the query API used by all repositories.

**advertisement-contracts** defines the cross-module contracts, organized into three semantic packages:
- `core.*` — shared by all modules: `core.model` (enums: `ActionType`, `ChangeEntry`, `EntityType`, `Role`), `core.config` (`CleanupProperties`, `UserSettings`), `core.i18n` (`I18nService`, `TranslationKey`, etc.), `core.ui` (`Configurable`, `ComponentBuilder`, `Initialization`, `Provider`), `core.spi` (shared SPIs: `AuditActorNameResolver`, `AuditEntityExistenceChecker`, `UserActivityExtension`, `AdvertisementHistoryExtension`)
- `audit.*` — audit subsystem contract: `audit.api` (`AuditPort`, `AuditableSnapshot`, `AuditedField`, `@ConditionalOnAuditEnabled`), `audit.dto` (`ActivityItemDto`, `AdvertisementHistoryDto`, `SnapshotContent`, `UserSnapshotState`), `audit.spi` (`AuditUserProvider`, `AuditUiExtension`)
- `attachment.*` — attachment subsystem contract: `attachment.event` (domain events: `AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`, `AdvertisementMediaUpdatedEvent`), `attachment.spi` (`AdvertisementGalleryExtension`, `AttachmentCurrentUserProvider`, `AttachmentEntityDisplayNameResolver`), `attachment.storage` (`StorageService`, `@ConditionalOnStorageEnabled`)

**audit-spring-boot-starter** auto-configures the full audit subsystem. Write side: `DefaultAuditPort`, `AuditDiffEngine`, `AuditLogRepository`. Read side: `AuditReadRepository`, `ActivityRepository`, `AuditHistoryService`, `AuditQueryService`, `ActivityService`, Vaadin audit UI components. Enabled by default (`audit.enabled=true`); set `audit.enabled=false` to activate `NoOpAuditPort`.

**attachment-spring-boot-starter** auto-configures via Spring Boot's autoconfiguration mechanism. It owns: `Attachment` entity, `AttachmentRepository`, `PhotoSnapshotRepository`, `AttachmentService`, `AttachmentGallery` (Vaadin component), SPI implementations (`AdvertisementGalleryExtensionImpl`, etc.), `AttachmentCleanupJob`, `S3StorageService`, and `NoOpStorageService`.

---

## Architecture Guidelines

1. **Explicit over implicit:** Avoid hidden framework magic. If simple Java code works, use it.
2. **Strict Boundaries:** The UI layer MUST NOT call Repositories directly. Always go through `UserService` or `AdvertisementService`.
3. **Modular Storage:** `StorageService` interface lives in `advertisement-contracts` (`attachment.storage`); `S3StorageService` and `NoOpStorageService` live in `attachment-spring-boot-starter`. UI components (like `AttachmentGallery`) MUST degrade gracefully via `ObjectProvider.ifAvailable()` when `storage.s3.enabled=false`.
4. **Validation:** Use declarative validation rules in DTOs.
5. **Database Changes:** Schema MUST only be modified via Liquibase scripts in `db/changelog/changes`.

**Pattern-first:** Before introducing a new abstraction or naming a class, scan the existing codebase for how similar things are already done. Symmetry with existing code is a first-class goal.

---

## UI Component Patterns

### Configurable prototype beans

Vaadin UI components that require runtime data use the `Configurable<T, P>` pattern:

```java
@SpringComponent
@Scope("prototype")
public class MyPanel extends Div
        implements Configurable<MyPanel, MyPanel.Parameters>, Initialization<MyPanel> {

    // Parameters: Java record for ≤4 simple fields; Lombok @Builder for 5+ or any callback
    @Value @lombok.Builder
    public static class Parameters {
        @NonNull Long entityId;
        Runnable onSave;
        Runnable onCancel;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<MyPanel, Parameters> {
        @Getter private final ObjectProvider<MyPanel> provider;
    }

    @Override @PostConstruct
    public MyPanel init() {
        // structural setup only: CSS classes, layout skeleton — no data, no service calls
        addClassName("my-panel");
        return this;
    }

    @Override
    public MyPanel configure(Parameters p) {
        // data loading, button wiring, value binding — called once per use
        return this;
    }
}
```

**Rules:**
- `init()` — structural setup only. No data, no service calls.
- `configure()` — data + behavior. Called once after `init()`.
- `Parameters` as Java record when ≤4 simple fields: `new MyPanel.Parameters(id, name)`.
- `Parameters` with Lombok `@Builder` when 5+ fields or any `Runnable`/`Consumer` callback.
- Inner `Builder` class is required for all `Configurable` beans — wraps `ObjectProvider` via `ComponentBuilder`.
- `Configurable`, `ComponentBuilder`, `Initialization` live in `advertisement-contracts` (`core.ui`) so all modules can use them.

**When NOT to use Configurable:**
- Component has distinct modes with different UI structure → use explicit named methods:
  `configureForView(Long id)`, `configureForEdit(Long id)`, `configureForCreate(String sessionId)`.
- Component needs only 1–2 simple setters → plain setters, no `Parameters`.
- Do NOT use positional-argument methods with 4+ parameters in any module — use `Parameters` instead.

**Cross-module rule:** audit-starter and attachment-starter must follow the same pattern as advertisement-app.
A `build(Long id, String name, Role role, ...)` method with 4+ positional args is a pattern violation.

### I18n in UI components

Each module owns its translation key enum implementing `TranslationKey` (defined in `advertisement-contracts`, package `core.i18n`):
- `advertisement-app` → `CommonMessages implements TranslationKey`
- `audit-spring-boot-starter` → `AuditMessages implements TranslationKey`
- `attachment-spring-boot-starter` → `AttachmentMessages implements TranslationKey`

**Rules:**
- Never use raw `MessageSource` directly in UI components — use `I18nService.get(TranslationKey)`.
- Never use `msg(String key, String fallback)` — missing keys must fail fast, not silently fall back.
- Never build keys dynamically: `"changes.field." + fieldName` — use typed enum with explicit mapping.

---

## sql-engine API

Two patterns for repositories depending on query complexity:

### Simple/filterable queries — `SqlEntityProjection` + `RepositoryCustom`

Define `SqlSelectField<T>` constants, an `SqlEntityProjection`, a `SqlFilterBuilder`, then extend `RepositoryCustom<T, F>`:

```java
// 1. Define fields
static final SqlSelectField<Long> ID    = longVal("a.id", "id");
static final SqlSelectField<String> TITLE = str("a.title", "title");

// 2. Projection (FROM source)
SqlEntityProjection<AdvertisementDto> projection =
    new SqlEntityProjection<>(List.of(ID, TITLE, ...), "advertisements a");

// 3. RowMapper lives in RepositoryCustom subclass
@Override
protected AdvertisementDto mapRow(ResultSet rs, int rowNum) {
    return new AdvertisementDto(ID.extract(rs), TITLE.extract(rs), ...);
}

// 4. Inherited methods
findByFilter(filter, pageable)   // SELECT ... WHERE ... ORDER BY ... LIMIT/OFFSET
countByFilter(filter)            // SELECT COUNT(*) FROM ...
```

### Complex/structural queries — `SqlFixedQuery<T>`

For CTEs, UNION ALL, self-joins — the developer writes the full SQL:

```java
public class ActivityProjection extends SqlFixedQuery<ActivityItemDto> {
    static final SqlSelectField<Long> ID = longVal("s.id", "snapshot_id");

    public ActivityProjection(ObjectMapper om) {
        super(List.of(ID, ...));
    }

    @Override public String querySql() { return "WITH ... UNION ALL ..."; }

    @Override public ActivityItemDto mapRow(ResultSet rs, int n) {
        return new ActivityItemDto(ID.extract(rs), ...);
    }
}
```

### Conditions (`SqlCondition` factory methods)
`SqlCondition.like()`, `.equalsTo()`, `.after()`, `.before()`, `.inSet()` — all null-safe via `.applyIfPresent()`. Conditions are composed in a `SqlFilterBuilder` subclass that adds named parameters to `MapSqlParameterSource`.

---

## Security: @PreAuthorize and Vaadin

`@EnableMethodSecurity` is active. **Never put `@PreAuthorize` at class level on service beans.** Vaadin initializes view beans on the first HTTP request before the user authenticates; a class-level annotation causes an `AuthorizationDeniedException` during view wiring, preventing any view from loading.

- Method-level `@PreAuthorize` is fine for future REST controller endpoints.
- Services (`AdvertisementService`, `ActivityService`, etc.) intentionally have no `@PreAuthorize`.
- `/health` is intentionally public (load balancer probe).

---

## Naming Conventions

### Class suffixes
- `*Projection` — SQL query object (extends `SqlFixedQuery<T>` or used with `SqlEntityProjection`). Defines SQL, field mappings, and `mapRow()`. Lives in `repository/*`.
- `*Service` — stateless business logic. Lives in `services/` or `ui/views/services/` (UI-layer services).
- `*Panel` — Spring bean that assembles a Vaadin UI subtree (returns `Div`/component). Lives in `ui/views/components/`.
- `*Util` — static-only utility class (`@NoArgsConstructor(access = PRIVATE)`). Lives in `ui/views/utils/`.
- `*Binding` — prototype bean that manages a lifecycle (register/unregister listeners). Lives next to the service it supports (e.g. `ui/views/services/pagination/`).
- `*Overlay` — full-screen Vaadin overlay (extends `AbstractEntityOverlay` or `BaseOverlay`).
- `*Config` — Spring `@Configuration` class. Infrastructure-level configs live in `config/`. Feature-scoped factory configs stay next to the components they configure.

### Package structure (advertisement-app)
- `config/` — app-level Spring configuration (`config/db/`, `config/ui/` for sub-domains)
- `services/audit/` — entire audit subsystem: services + snapshots + diff engine + annotation
- `services/auth/` — authentication context (interface + impl)
- `repository/activity/`, `repository/audit/`, `repository/advertisement/`, `repository/user/` — SQL repositories + projections per domain
- `ui/views/components/` — reusable Vaadin UI components (incl. `activity/` subpackage)
- `ui/views/utils/` — pure static utilities only (`*Util` classes)
- `ui/views/services/` — UI-layer Spring services; `*Binding` beans live in the same subpackage as the service they support

### Cross-module consistency
All modules use `config` (not `configuration`) for Spring configuration packages.

---

## Spring Profiles
- `dev` (default) — `localhost:5432` PostgreSQL, `localhost:9000` MinIO, Liquibase seed data.
- `prod` — uses env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`, `S3_PUBLIC_URL`.

Local infrastructure only (IDE dev mode):
```bash
docker-compose -f docker-compose.db.yml -f docker-compose.minio.yml up -d
```

---

## UI Verification with Playwright

After making UI changes, verify them by running the Playwright script inside Docker.

### Prerequisites
- DB and MinIO already running (started separately via docker-compose.db.yml / docker-compose.minio.yml)
- App image built with: `docker build -f Dockerfile -t advertisement-app .` (uses `-Pproduction`, always run with `SPRING_PROFILES_ACTIVE=prod`)
- App must be running:
```bash
docker run -d --name advertisement-app --network host \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=localhost -e DB_PORT=5432 -e DB_NAME=experiments \
  -e DB_USER=experiments_user -e DB_PASSWORD=experiments_user_password \
  -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement \
  -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 \
  -e S3_REGION=us-east-1 -e S3_PUBLIC_URL=http://localhost:9000/advertisement \
  advertisement-app
```

### Scripts location
All scenarios live in `/app/playwright/`. Run via `run.sh`:
```bash
bash /app/playwright/run.sh                  # all tests
bash /app/playwright/run.sh smoke            # one scenario
bash /app/playwright/run.sh smoke --ux       # with local screenshots for AI analysis
bash /app/playwright/run.sh --ux             # all tests with screenshots
```

**IMPORTANT:** Volume mounts don't work from inside the claude container (Docker socket path issue).
`run.sh` uses `docker cp` internally — always use `run.sh`, never raw `docker run -v`.

### Workflow for UI changes
1. Make code changes
2. Rebuild image: `docker rm -f advertisement-app && docker build -f Dockerfile -t advertisement-app .`
3. Start app (command above)
4. Wait for start: `docker logs advertisement-app | grep "Started Application"`
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

---

## SonarQube Analysis

All config lives in `/app/sonar/`. SonarQube server runs in Docker on `localhost:9099`.

### Start server manually (if needed)
```bash
docker compose -f sonar/docker-compose.sonar.yml up -d
```

### Run analysis
```bash
bash /app/sonar/run.sh        # Linux / WSL
sonar\run.bat                 # Windows
```

The script starts SonarQube automatically if not running, copies source files into a scanner container via `docker cp`, and runs `sonar-scanner-cli`. Results: `http://localhost:9099/dashboard?id=advertisement`.

**IMPORTANT:** Same Docker socket constraint as Playwright — never use `docker run -v`. The script uses `docker cp` internally.

---

## Architectural Decisions Log

Significant decisions are recorded in per-module `DECISIONS.md` files:
- `/app/advertisement-app/DECISIONS.md`
- `/app/audit-spring-boot-starter/DECISIONS.md`
- `/app/attachment-spring-boot-starter/DECISIONS.md`
- `/app/playwright/DECISIONS.md`
- `/app/sonar/DECISIONS.md`

**Rules:**
- Record any new substantial architectural or technical decision there immediately — before the conversation ends.
- When a decision contradicts or supersedes an existing entry, update or annotate the existing entry rather than only adding a new one.
- Each `DECISIONS.md` also tracks open goals (work not yet done). When implementing something that realizes a stated goal, mark it done in the same PR.
