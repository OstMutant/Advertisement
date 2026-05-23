# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@.claude/rules.md

---

## Core Stack
- Java 25 (use modern features: Records, Pattern Matching, Switch expressions).
- Spring Boot 4.0.6, Vaadin 25.1.5.
- Pure SQL via `JdbcClient` / `NamedParameterJdbcTemplate` (NO JPA, NO HIBERNATE).
- Liquibase for all schema changes.
- **Lombok first:** if a Lombok annotation can replace manual boilerplate (constructors, getters, builders, etc.), it MUST be used — hand-written boilerplate is not acceptable when a Lombok equivalent exists.

---

## Module Layout

```
advertisement-parent (root pom)
├── sql-engine                       — framework-agnostic SQL query-building library
├── platform-contracts               — shared kernel: DTOs, domain events, SPI interfaces
├── audit-spring-boot-starter        — audit subsystem: write + read side + activity UI (auto-configured starter)
├── attachment-spring-boot-starter   — photo/attachment module + S3 storage (auto-configured starter)
└── marketplace-app                  — main Vaadin application
```

**sql-engine** has no Spring Boot autoconfiguration — it is a plain library. It provides the query API used by all repositories.

**platform-contracts** defines the cross-module contracts, organized into three semantic packages:
- `core.*` — shared by all modules: `core.model` (enums: `ActionType`, `ChangeEntry`, `EntityType`), `core.config` (`CleanupProperties`), `core.i18n` (`I18nService`, `TranslationKey`, etc.), `core.ui` (`Configurable`, `ComponentBuilder`, `Initialization`, `Provider`), `core.spi` (`CurrentActorHook`, `EntityNameHook`)
- `audit.*` — `audit.api` (`AuditableSnapshot`, `AuditedField`, `@ConditionalOnAuditEnabled`), `audit.dto` (`ActivityItemDto`, `EntityHistoryDto`, `SnapshotContentDto`, `SnapshotPayloadDto`), `audit.spi` (`AuditPort`, `AuditUiPort`, `AuditDomainHook`, `ActivityFieldsHook`, `ActivityRowHook`)
- `attachment.*` — `attachment.spi` (`AttachmentPort`, `AttachmentGalleryPort`, `MediaChangeHook`, `AttachmentAuditHook`), `attachment.dto` (`MediaSummaryDto`), `attachment.model` (`MediaContentType`)

→ Package semantics (`api` vs `spi` vs `dto`) and SPI naming conventions: @platform-contracts/CLAUDE.md

**audit-spring-boot-starter** auto-configures the full audit subsystem. Write side: `DefaultAuditPort`, `AuditDiffEngine`, `AuditLogRepository`. Read side: `AuditReadRepository`, `ActivityRepository`, `AuditHistoryService`, `AuditQueryService`, `ActivityService`, Vaadin audit UI components. Enabled by default (`audit.enabled=true`); set `audit.enabled=false` to activate `NoOpAuditPort`. Java package root: `org.ost.audit`.

**attachment-spring-boot-starter** auto-configures via Spring Boot's autoconfiguration mechanism. It owns: `Attachment` entity, `AttachmentRepository`, `PhotoSnapshotRepository`, `AttachmentService`, `AttachmentGallery` (Vaadin component), SPI implementations, `AttachmentCleanupJob`, `S3StorageService`. Java package root: `org.ost.attachment`.

---

## Architecture Guidelines

1. **Explicit over implicit:** Avoid hidden framework magic. If simple Java code works, use it.
2. **Strict Boundaries:** The UI layer MUST NOT call Repositories directly. Always go through `UserService` or `AdvertisementService`.
3. **Modular Storage:** `StorageService` and its implementations live in `attachment-spring-boot-starter` (`org.ost.attachment.storage`). UI components MUST degrade gracefully via `ObjectProvider.ifAvailable()` when `attachment.enabled=false`.
4. **Validation:** Use declarative validation rules in DTOs.
5. **Database Changes:** Schema MUST only be modified via Liquibase scripts in `db/changelog/changes`.

**Pattern-first:** Before introducing a new abstraction or naming a class, scan the existing codebase for how similar things are already done. Symmetry with existing code is a first-class goal.

### Repository pattern

**Policy:** `*CrudRepository extends CrudRepository<T, Long>` for trivial save/find; plain `@Repository` class with `JdbcClient` for bespoke queries — SQL inlined as text blocks directly in methods.

- Entity classes annotated with `@Table`, `@Id`, `@CreatedDate`, `@LastModifiedDate` where applicable. `@CreatedDate` / `@LastModifiedDate` rely on the project-wide `AuditorAware<Long>` bean in `marketplace-app/JdbcAuditingConfig`.
- Repository = `@Repository` class with `@RequiredArgsConstructor` + `@SuppressWarnings("java:S1192")`. Holds a `*CrudRepository` field for CRUD and a `JdbcClient` field for custom SQL.
- `RowMapper<T>` declared as a `private static final` constant in the repository class.
- Dynamic filtering: `SqlFilterBuilder<F>` declared as a `private static final` constant; built with `SqlBoundFilter.of(filterProperty, sqlExpression, conditionFn)` entries.
- Sorting: `OrderByBuilder.build(sort, aliasMap)` returns the `ORDER BY` clause or an empty string.
- Hand-rolled `INSERT` / `findById` SQL is removed whenever it duplicates what `CrudRepository.save` / `.findById` already provides.
- Starters that ship their own repositories must declare `@EnableJdbcRepositories(basePackages = "...")` in their `@AutoConfiguration`, because the marketplace `@SpringBootApplication` scan only covers `org.ost.marketplace`.

Reference implementations: `UserRepository` / `AdvertisementRepository` in marketplace-app, `AttachmentRepository` in attachment-spring-boot-starter.

→ sql-engine query API (SqlFilterBuilder, SqlCondition, OrderByBuilder): @sql-engine/CLAUDE.md

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
- `Configurable`, `ComponentBuilder`, `Initialization` live in `platform-contracts` (`core.ui`) so all modules can use them.

**When NOT to use Configurable:**
- Component has distinct modes with different UI structure → use explicit named methods:
  `configureForView(Long id)`, `configureForEdit(Long id)`, `configureForCreate(String sessionId)`.
- Component needs only 1–2 simple setters → plain setters, no `Parameters`.
- Do NOT use positional-argument methods with 4+ parameters in any module — use `Parameters` instead.

**Cross-module rule:** audit-starter and attachment-starter must follow the same pattern as marketplace-app.
A `build(Long id, String name, Role role, ...)` method with 4+ positional args is a pattern violation.

### I18n in UI components

Each module owns its translation key enum implementing `TranslationKey` (defined in `platform-contracts`, package `core.i18n`):
- `marketplace-app` → `CommonMessages implements TranslationKey`
- `audit-spring-boot-starter` → `AuditMessages implements TranslationKey`
- `attachment-spring-boot-starter` → `AttachmentMessages implements TranslationKey`

**Rules:**
- Never use raw `MessageSource` directly in UI components — use `I18nService.get(TranslationKey)`.
- Never use `msg(String key, String fallback)` — missing keys must fail fast, not silently fall back.
- Never build keys dynamically: `"changes.field." + fieldName` — use typed enum with explicit mapping.

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

→ SPI interface naming (`*Port`, `*Extension`, `*Consumer`, etc.): @platform-contracts/CLAUDE.md

### Package structure (marketplace-app)
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

## Tooling

→ UI verification with Playwright (run commands, Vaadin tips, workflow): @playwright/CLAUDE.md
→ SonarQube static analysis: @scripts/CLAUDE.md

**Slash commands available:**
- `/build` — rebuild Docker image and start app
- `/playwright [scenario] [--ux]` — run Playwright tests
- `/sonar` — run SonarQube analysis
- `/decision <module> — <title>` — record architectural decision

---

## Architectural Decisions Log

Significant decisions are recorded in per-module `DECISIONS.md` files:
- `/app/marketplace-app/DECISIONS.md`
- `/app/audit-spring-boot-starter/DECISIONS.md`
- `/app/attachment-spring-boot-starter/DECISIONS.md`
- `/app/platform-contracts/DECISIONS.md`
- `/app/sql-engine/DECISIONS.md`
- `/app/playwright/DECISIONS.md`
- `/app/scripts/DECISIONS.md`

**Rules:**
- Record any new substantial architectural or technical decision there immediately — before the conversation ends.
- When a decision contradicts or supersedes an existing entry, update or annotate the existing entry rather than only adding a new one.
- Each `DECISIONS.md` also tracks open goals (work not yet done). When implementing something that realizes a stated goal, mark it done in the same PR.
