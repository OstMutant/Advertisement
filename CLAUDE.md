# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@.claude/rules.md

---

## Core Stack
- Java 25 (use modern features: Records, Pattern Matching, Switch expressions).
- Spring Boot 4.1.0, Vaadin 25.2.3.
- Pure SQL via `JdbcClient` / `NamedParameterJdbcTemplate` (NO JPA, NO HIBERNATE).
- Liquibase for all schema changes.
- **Lombok first:** if a Lombok annotation can replace manual boilerplate (constructors, getters, builders, etc.), it MUST be used — hand-written boilerplate is not acceptable when a Lombok equivalent exists. This includes `@FieldNameConstants` on DTOs/records that need typed field name constants — never write a manual `Fields` inner class.

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
├── taxon-spring-boot-starter         — Taxonomy domain: taxon/category/tag management, TaxonPort (auto-configured starter)
├── provider-profile-spring-boot-starter — Provider profile domain: MASTER/SHOP/SUPPORT catalog entries, ProviderProfilePort (auto-configured starter)
├── integration-tests                 — Testcontainers repository tests + fixtures for every starter (test-only, never shipped)
├── marketplace-orchestrator           — application/BFF layer: cross-domain use-case orchestration between marketplace-app and the domain starters
└── marketplace-app                   — main Vaadin application (all UI)
```

**query-lib** is a plain Java SQL helper library (no Spring Boot autoconfiguration). Provides `SqlFilterBuilder`, `OrderByBuilder` (`org.ost.query.filter/sort`) used directly by repositories as `private static final` constants.

**integration-tests** is the sole home for Testcontainers-based repository tests and their fixtures (`AbstractPostgresIntegrationTest` — shared singleton Testcontainers Postgres instance). Domain starters never carry test code for this purpose themselves — it depends on whichever starters it needs to test (`advertisement-spring-boot-starter`, `user-spring-boot-starter`, `platform-commons`, ...), which is safe only because this module is never shipped or deployed (see `.claude/rules/integration-tests.md` for the full rationale). Requires a reachable Docker daemon; never runs inside `deploy.sh`'s Docker build stage (see `.claude/rules/scripts.md`).

**platform-commons** defines the cross-module contracts, organized into semantic packages:
- `core.*` — shared by all modules: `ComponentFactory` (top-level, not a sub-package), `core.model` (`ActionType`, `ChangeEntry`, `EntityRef`, `EntityType`), `core.config` (`CleanupProperties`), `core.spi` (`CurrentActorHook`), `core.validation` (`ValidRange`)
- `audit.*` — `audit.api` (`AuditableSnapshot`), `audit.dto` (`AuditActivityItemDto`, `AuditSnapshotContentDto`, `AuditTimelineItemDto`, `AuditTimelineFilterDto`), `audit.spi` (`AuditPort`, `AuditDomainHook`, `AuditActivityEnrichHook` — note `AuditActivityFieldsHook` does not exist: field-name-to-label mapping lives entirely in `marketplace-app`'s `AuditTimelineRowRenderer`, not a per-domain Hook)
- `attachment.*` — `attachment.spi` (`AttachmentPort`, `AttachmentAuditPort`) — note `AttachmentMediaChangeHook` does not exist — `attachment.dto` (`AttachmentMediaSummaryDto`, `AttachmentItemDto`, `TempAttachmentDto`), `attachment.model` (`AttachmentMediaContentType`)
- `user.*` — `user.spi` (`UserPort`/`UserAccountPort`/`UserAuthorizationPort`/`UserPreferencesPort` — one logical split, see `.claude/rules/platform-commons.md`; plus `AuthenticatedPrincipal`, `UserSettingsChangedHook`), `user.dto` (`UserDto`, `UserFilterDto`, `UserProfileDto`, `UserSettingsDto`, `UserSnapshotDto`, `SettingsSnapshotDto`, `SignUpDto`), `user.model` (`Role`)
- `advertisement.*` — `advertisement.spi` (`AdvertisementPort`), `advertisement.dto` (`AdvertisementInfoDto`, `AdvertisementFilterDto`, `AdvertisementSaveDto`, `AdvertisementSnapshotDto`), `advertisement.model` (`AdKind`)
- `taxon.*` — `taxon.spi` (`TaxonPort`), `taxon.dto` (`TaxonDto`, `TaxonTranslationDto`, `TaxonSnapshotDto`), `taxon.model` (`TaxonType`)
- `providerprofile.*` — `providerprofile.spi` (`ProviderProfilePort`), `providerprofile.dto` (`ProviderProfileDto`, `ProviderProfileSaveDto`, `ProviderProfileFilterDto`, `ProviderProfileSnapshotDto`), `providerprofile.model` (`ProviderKind`)

→ Package semantics (`api` vs `spi` vs `dto`) and SPI naming conventions: `.claude/rules/platform-commons.md`

→ Audit subsystem (write side, read side, owned classes): `.claude/rules/audit-spring-boot-starter.md`

→ Attachment module (S3 storage, cleanup, owned classes): `.claude/rules/attachment-spring-boot-starter.md`

→ User domain (security, settings, owned classes): `.claude/rules/user-spring-boot-starter.md`

→ Advertisement domain (owned classes): `.claude/rules/advertisement-spring-boot-starter.md`

→ Taxon/reference data domain (owned classes): `.claude/rules/taxon-spring-boot-starter.md`

→ Provider profile domain (owned classes): `.claude/rules/provider-profile-spring-boot-starter.md`

→ Application/BFF orchestration layer (cross-domain use cases, owned classes): `.claude/rules/marketplace-orchestrator.md`

---

## Architecture Guidelines

1. **Explicit over implicit:** Avoid hidden framework magic. If simple Java code works, use it.
2. **Three layers, not two:** `marketplace-app` (UI adapter: Vaadin, auth, locale — application-shell
   concerns only) → `marketplace-orchestrator` (application/BFF layer: cross-domain use-case
   composition) → domain starters (each owns its own bounded context). A domain starter must not
   orchestrate another domain, and `marketplace-app` must not directly compose multiple domain
   Ports for a single application use case — that composition belongs in `marketplace-orchestrator`.
   All Vaadin UI code still lives in `marketplace-app` only; within `marketplace-app`, UI components
   may freely reference each other — no ports, no hooks, no indirection needed between UI classes.
   No single class in `marketplace-orchestrator` may depend on more than two domain `*Port`
   interfaces via `ComponentFactory` — split into smaller, composed use-case services instead (see
   `.claude/rules/marketplace-orchestrator.md`). `marketplace-orchestrator` never touches `JdbcClient`,
   any `*Repository`, or any `*CrudRepository` — it composes results from domain Ports only.
3. **Strict Boundaries:** The UI layer MUST NOT call Repositories directly. Always go through `UserPort` or `AdvertisementPort`.
4. **Modular Storage:** `StorageService` and its implementations live in `attachment-spring-boot-starter` (`org.ost.attachment.services`). UI components MUST degrade gracefully via `ObjectProvider.ifAvailable()` when the attachment starter is absent from the classpath.
5. **Validation:** Use declarative validation rules in DTOs.
6. **Database Changes:** Schema MUST only be modified via Liquibase scripts in `db/changelog/changes`.
   Every `<column>`/`<createTable>` MUST carry a `remarks="..."` attribute with the business-meaning
   explanation (why the column/table exists, cross-references to the ADR that decided its shape).
   This is the single source of truth — `docs/architecture/scripts/generate-architecture-model.sh`'s Database ERD
   page parses these `remarks` live and shows them next to each column/table. Do not duplicate the
   same explanation in a separate markdown file; if the meaning changes, edit the `remarks`
   attribute in the changelog, not a second copy elsewhere.
7. **Starter independence:** No starter has a Vaadin dependency and no starter contains UI code —
   Vaadin only exists in `marketplace-app` (guideline 2 above). Each starter owns its own Liquibase
   changelog under its own `db/*-changelog/` directory; changelogs are never merged into a shared
   file across starters. Every starter `CLAUDE.md`'s own "Key constraints"/"Schema" section states
   only what's specific to that module beyond this baseline.

**Pattern-first:** Before introducing a new abstraction or naming a class, scan the existing codebase for how similar things are already done. Symmetry with existing code is a first-class goal.

**Design by contract — no defensive empty checks:** Methods trust their inputs. If a parameter is not `Optional`, the caller must pass a non-null value; `null` → fail fast (`@NonNull` or `Objects.requireNonNull`). Empty collections are the caller's responsibility — if the caller has nothing to pass, they skip the call. Methods must not have defensive `if (collection.isEmpty()) return emptyResult` guards; that logic belongs at the call site.

**No `Optional` parameters:** `Optional` must never be used as a method parameter type. Callers resolve the `Optional` before calling (via `.map()` / `.flatMap()`).

**`@NonNull` on parameters:** Every public method parameter that must not be null must be annotated with `lombok.NonNull`. This applies to all layers: repositories, services, hooks, ports, UI components. Lombok generates a null-check at the top of the method body — fail fast, no silent NPE.

**Functional `Optional` style:** Prefer `.ifPresent()`, `.ifPresentOrElse()`, `.map()`, `.flatMap()`, `.or()` over imperative `orElse(null)` + null check. Use `orElse(null)` only when the downstream API explicitly accepts a nullable value (e.g. SQL parameters). When a method must return a nullable value, return `optional.orElse(null)` at the boundary — not in intermediate code. Early-exit with `Optional` in complex methods: `Optional<User> maybeUser = ...; if (maybeUser.isEmpty()) return; user = maybeUser.get();`

**No `@Primary` on `ObjectMapper` beans:** when the context has multiple `ObjectMapper` beans, every injection site must use an explicit `@Qualifier("...")` — never mark one bean `@Primary` to resolve the ambiguity implicitly.

### Repository pattern

**Policy:** `*CrudRepository extends CrudRepository<T, Long>` for trivial save/find; plain `@Repository` class with `JdbcClient` for bespoke queries — SQL inlined as text blocks directly in methods.

- Entity classes annotated with `@Table`, `@Id`, `@CreatedDate`, `@LastModifiedDate` where applicable. `@CreatedDate` / `@LastModifiedDate` rely on the project-wide `AuditorAware<Long>` bean in `marketplace-app/JdbcAuditingConfig`.
- Repository = `@Repository` class with `@RequiredArgsConstructor` + `@SuppressWarnings("java:S1192")`. Holds a `*CrudRepository` field for CRUD and a `JdbcClient` field for custom SQL.
- `RowMapper<T>` declared as a `private static final` constant in the repository class.
- Dynamic filtering: `SqlFilterBuilder<F>` declared as a `private static final` constant; built with `SqlBoundFilter.of(filterProperty, sqlExpression, conditionFn)` entries.
- Sorting: `OrderByBuilder.build(sort, aliasMap)` returns the `ORDER BY` clause or an empty string.
- Hand-rolled `INSERT` / `findById` SQL is removed whenever it duplicates what `CrudRepository.save` / `.findById` already provides.
- Starters that ship their own repositories must declare `@EnableJdbcRepositories(basePackages = "...")` in their `@AutoConfiguration`, because the marketplace `@SpringBootApplication` scan only covers `org.ost.marketplace`.
- No `TABLE`/`ALIAS`-style name constants — write the table name literally in the SQL. A SQL
  fragment used in only one method stays inlined in that method, never hoisted into its own
  `private static final String`; only a fragment genuinely reused across 2+ methods becomes a
  constant.

Reference implementations: `UserRepository` in user-spring-boot-starter, `AdvertisementRepository` in advertisement-spring-boot-starter, `AttachmentRepository` in attachment-spring-boot-starter.

→ query-lib SQL API (SqlFilterBuilder, SqlCondition, OrderByBuilder): `.claude/rules/query-lib.md`

→ integration-tests (Testcontainers repository tests + fixtures, why domain starters stay test-code-free): `.claude/rules/integration-tests.md`

---

→ UI Component Patterns (Configurable beans, I18n, Security, Naming, Package structure): `.claude/rules/marketplace-app.md`

---

## Spring Profiles
- `dev` (default) — `localhost:5432` PostgreSQL, `localhost:9000` MinIO, Liquibase seed data.
- `prod` — uses env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`, `S3_PUBLIC_URL`.

Local infrastructure only (IDE dev mode):
```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml up -d
```

---

## Tooling

→ UI verification with Playwright (run commands, Vaadin tips, workflow): `.claude/rules/playwright.md`
→ SonarQube static analysis: `.claude/rules/scripts.md`

**Slash commands available:**
- `/build-and-test` — build the whole reactor (+ optional unit/integration tests) via the shared build-and-test container, no local Java needed
- `/deploy-and-run` — rebuild the Docker image and start the app; reuses `/build-and-test`'s shared jar internally, no duplicate compile
- `/playwright [scenario] [--ux]` — run Playwright tests
- `/sonar` — run SonarQube analysis
- `/record-decision <module> — <title>` — record architectural decision
- `/sync-docs [ref]` — sync architecture docs with code (default: origin/main); **run manually** after significant changes (new module, new SPI, schema changes) — not triggered automatically
- `/run-all-tests [--unit "..."] [--integration "..."] [--playwright "..."] [--background]` — run unit-tests → integration-tests sequentially plus Playwright in parallel; see `.claude/nav/adr-index.md`
- `/ci [flags]` — run the isolated local CI runner (unit+integration+e2e+sonar by default, backgrounded); see `scripts/ci/README.md`/`DECISIONS.md`
- `/feature <title>` — scaffold a new `backlog/issues/<prefix>-NNN-<slug>.md` from the standard template and rank it in `BACKLOG.md`'s priority table
- `/autopilot <task>` — plan once, approve once, then implement/test/document a task end-to-end with no further check-ins until it's done; explicit per-run opt-out of the standing Approval Rule's per-step gating, not a permanent one
- `deep-review-orchestrator` agent — evidence-verified SOLID/DRY code review, findings-only, never writes code; scope to current uncommitted changes, one commit, one module, or the whole repo; every finding independently re-verified before being reported via `ReportFindings` — invoke directly via the `Agent` tool (`subagent_type: "deep-review-orchestrator"`), see `.claude/agents/deep-review-orchestrator.md`

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
- `/app/scripts/ci/DECISIONS.md`
- `/app/scripts/sonar/DECISIONS.md`
- `/app/docs/architecture/scripts/DECISIONS.md`
- `/app/integration-tests/DECISIONS.md`
- `/app/taxon-spring-boot-starter/DECISIONS.md`
- `/app/marketplace-orchestrator/DECISIONS.md`
- `/app/.claude/DECISIONS.md`

Note: `user-spring-boot-starter`, `advertisement-spring-boot-starter`, and
`provider-profile-spring-boot-starter` have no hand-authored `DECISIONS.md` of their own — their
key decisions are recorded in `marketplace-app/DECISIONS.md` and `platform-commons/DECISIONS.md`
instead. Each of these three modules has a generated, pointer-only `DECISIONS.md`
(`bash docs/architecture/scripts/generate-architecture-model.sh`) listing whichever ADRs cross-reference it via
the home ADR's own `**Also affects:**` tag — never hand-edit these three files directly.

→ ADR discovery index (generated, one line per decision across every `DECISIONS.md`):
`.claude/nav/adr-index.md` — see `.claude/nav/README.md` for the full AI-navigation layer.

**Rules:**
- Record any new substantial architectural or technical decision there immediately — before the
  conversation ends — via `/record-decision`, never by hand-writing a `DECISIONS.md` entry
  directly (its worthiness gate and format checks are the point).
- When a decision contradicts or supersedes an existing entry, update or annotate the existing entry rather than only adding a new one.
- Each `DECISIONS.md` also tracks open goals (work not yet done). When implementing something that realizes a stated goal, mark it done in the same PR.
