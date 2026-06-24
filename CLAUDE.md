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
├── query-lib                         — SQL filter/sort helper library (SqlFilterBuilder, OrderByBuilder)
├── platform-commons                  — shared kernel: DTOs, domain events, SPI interfaces
├── audit-spring-boot-starter         — audit subsystem: write + read side (auto-configured starter)
├── attachment-spring-boot-starter    — photo/attachment module + S3 storage (auto-configured starter)
├── user-spring-boot-starter          — User domain: entity, service, security, UserPortImpl (auto-configured starter)
├── advertisement-spring-boot-starter — Advertisement domain: entity, service, AdvertisementPortImpl (auto-configured starter)
└── marketplace-app                   — main Vaadin application (all UI)
```

**query-lib** is a plain Java SQL helper library (no Spring Boot autoconfiguration). Provides `SqlFilterBuilder`, `OrderByBuilder` (`org.ost.query.filter/sort`) used directly by repositories as `private static final` constants.

**platform-commons** defines the cross-module contracts, organized into semantic packages:
- `core.*` — shared by all modules: `core.model` (enums: `ActionType`, `ChangeEntry`, `EntityType`), `core.config` (`CleanupProperties`), `core.spi` (`CurrentActorHook`), `core.validation` (`ValidRange`)
- `audit.*` — `audit.api` (`AuditableSnapshot`), `audit.dto` (`AuditActivityItemDto`, `AuditSnapshotContentDto`, `AuditTimelineItemDto`), `audit.spi` (`AuditPort`, `AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook`)
- `attachment.*` — `attachment.spi` (`AttachmentPort`, `AttachmentMediaChangeHook`, `AttachmentAuditHook`), `attachment.dto` (`AttachmentMediaSummaryDto`, `AttachmentItemDto`, `TempAttachmentDto`), `attachment.model` (`AttachmentMediaContentType`)
- `user.*` — `user.spi` (`UserPort`, `AuthenticatedPrincipal`, `UserSettingsChangedHook`), `user.dto` (`UserDto`, `UserFilterDto`, `UserProfileDto`, `UserSettingsDto`, `UserSnapshotDto`, `SettingsSnapshotDto`, `SignUpDto`), `user.model` (`Role`)
- `advertisement.*` — `advertisement.spi` (`AdvertisementPort`), `advertisement.dto` (`AdvertisementInfoDto`, `AdvertisementFilterDto`, `AdvertisementSaveDto`, `AdvertisementSnapshotDto`)

→ Package semantics (`api` vs `spi` vs `dto`) and SPI naming conventions: @platform-commons/CLAUDE.md

→ Audit subsystem (write side, read side, owned classes): @audit-spring-boot-starter/CLAUDE.md

→ Attachment module (S3 storage, cleanup, owned classes): @attachment-spring-boot-starter/CLAUDE.md

→ User domain (security, settings, owned classes): @user-spring-boot-starter/CLAUDE.md

→ Advertisement domain (owned classes): @advertisement-spring-boot-starter/CLAUDE.md

---

## Architecture Guidelines

1. **Explicit over implicit:** Avoid hidden framework magic. If simple Java code works, use it.
2. **UI is a monolith:** All Vaadin UI code lives in `marketplace-app`. Decoupling is required only at the **service ↔ UI boundary** (starters vs marketplace-app). Within `marketplace-app`, UI components may freely reference each other — no ports, no hooks, no indirection needed between UI classes.
3. **Strict Boundaries:** The UI layer MUST NOT call Repositories directly. Always go through `UserPort` or `AdvertisementPort`.
3. **Modular Storage:** `StorageService` and its implementations live in `attachment-spring-boot-starter` (`org.ost.attachment.storage`). UI components MUST degrade gracefully via `ObjectProvider.ifAvailable()` when the attachment starter is absent from the classpath.
4. **Validation:** Use declarative validation rules in DTOs.
5. **Database Changes:** Schema MUST only be modified via Liquibase scripts in `db/changelog/changes`.

**Pattern-first:** Before introducing a new abstraction or naming a class, scan the existing codebase for how similar things are already done. Symmetry with existing code is a first-class goal.

**Design by contract — no defensive empty checks:** Methods trust their inputs. If a parameter is not `Optional`, the caller must pass a non-null value; `null` → fail fast (`@NonNull` or `Objects.requireNonNull`). Empty collections are the caller's responsibility — if the caller has nothing to pass, they skip the call. Methods must not have defensive `if (collection.isEmpty()) return emptyResult` guards; that logic belongs at the call site.

**No `Optional` parameters:** `Optional` must never be used as a method parameter type. Callers resolve the `Optional` before calling (via `.map()` / `.flatMap()`).

**`@NonNull` on parameters:** Every public method parameter that must not be null must be annotated with `lombok.NonNull`. This applies to all layers: repositories, services, hooks, ports, UI components. Lombok generates a null-check at the top of the method body — fail fast, no silent NPE.

**Functional `Optional` style:** Prefer `.ifPresent()`, `.ifPresentOrElse()`, `.map()`, `.flatMap()`, `.or()` over imperative `orElse(null)` + null check. Use `orElse(null)` only when the downstream API explicitly accepts a nullable value (e.g. SQL parameters). When a method must return a nullable value, return `optional.orElse(null)` at the boundary — not in intermediate code. Early-exit with `Optional` in complex methods: `Optional<User> maybeUser = ...; if (maybeUser.isEmpty()) return; user = maybeUser.get();`

### Repository pattern

**Policy:** `*CrudRepository extends CrudRepository<T, Long>` for trivial save/find; plain `@Repository` class with `JdbcClient` for bespoke queries — SQL inlined as text blocks directly in methods.

- Entity classes annotated with `@Table`, `@Id`, `@CreatedDate`, `@LastModifiedDate` where applicable. `@CreatedDate` / `@LastModifiedDate` rely on the project-wide `AuditorAware<Long>` bean in `marketplace-app/JdbcAuditingConfig`.
- Repository = `@Repository` class with `@RequiredArgsConstructor` + `@SuppressWarnings("java:S1192")`. Holds a `*CrudRepository` field for CRUD and a `JdbcClient` field for custom SQL.
- `RowMapper<T>` declared as a `private static final` constant in the repository class.
- Dynamic filtering: `SqlFilterBuilder<F>` declared as a `private static final` constant; built with `SqlBoundFilter.of(filterProperty, sqlExpression, conditionFn)` entries.
- Sorting: `OrderByBuilder.build(sort, aliasMap)` returns the `ORDER BY` clause or an empty string.
- Hand-rolled `INSERT` / `findById` SQL is removed whenever it duplicates what `CrudRepository.save` / `.findById` already provides.
- Starters that ship their own repositories must declare `@EnableJdbcRepositories(basePackages = "...")` in their `@AutoConfiguration`, because the marketplace `@SpringBootApplication` scan only covers `org.ost.marketplace`.

Reference implementations: `UserRepository` in user-spring-boot-starter, `AdvertisementRepository` in advertisement-spring-boot-starter, `AttachmentRepository` in attachment-spring-boot-starter.

→ query-lib SQL API (SqlFilterBuilder, SqlCondition, OrderByBuilder): @query-lib/CLAUDE.md

---

→ UI Component Patterns (Configurable beans, I18n, Security, Naming, Package structure): @marketplace-app/CLAUDE.md

---

## Spring Profiles
- `dev` (default) — `localhost:5432` PostgreSQL, `localhost:9000` MinIO, Liquibase seed data.
- `prod` — uses env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`, `S3_PUBLIC_URL`.

Local infrastructure only (IDE dev mode):
```bash
docker-compose -f scripts/infra/docker-compose.db.yml -f scripts/infra/docker-compose.minio.yml up -d
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
- `/app/platform-commons/DECISIONS.md`
- `/app/query-lib/DECISIONS.md`
- `/app/playwright/DECISIONS.md`
- `/app/scripts/DECISIONS.md`

**Rules:**
- Record any new substantial architectural or technical decision there immediately — before the conversation ends.
- When a decision contradicts or supersedes an existing entry, update or annotate the existing entry rather than only adding a new one.
- Each `DECISIONS.md` also tracks open goals (work not yet done). When implementing something that realizes a stated goal, mark it done in the same PR.
