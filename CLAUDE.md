# CLAUDE.md

What holds for the whole project in every session: the stack, the module map, the architecture and coding rules, and where the detailed guidance lives.

@.claude/rules.md

---

## Core Stack
- Java 25 (use modern features: Records, Pattern Matching, Switch expressions).
- Spring Boot and Vaadin (versions in the root `pom.xml`).
- Pure SQL via `JdbcClient` / `NamedParameterJdbcTemplate` (no JPA, no Hibernate).
- Liquibase for all schema changes (guideline 6).
- **Lombok first:** if a Lombok annotation can replace manual boilerplate (constructors, getters, builders, etc.), it must be used; hand-written boilerplate is not acceptable when a Lombok equivalent exists. Field name constants: `.claude/rules.md` "DTO Field Name Constants".

---

## Module Layout

```
advertisement-parent (root pom)
├── query-lib                         — SQL filter/sort helper library (SqlFilterBuilder, OrderByBuilder)
├── html-sanitizer-lib                 — shared HTML sanitizer + visible-text-length validator library
├── platform-commons                  — shared kernel: DTOs, domain events, SPI interfaces
├── audit-spring-boot-starter         — audit subsystem: write + read side (auto-configured starter)
├── attachment-spring-boot-starter    — photo/attachment module + S3 storage (auto-configured starter)
├── user-spring-boot-starter          — User domain: entity, service, security, UserPortImpl (auto-configured starter)
├── advertisement-spring-boot-starter — Advertisement domain: entity, service, AdvertisementPortImpl (auto-configured starter)
├── taxon-spring-boot-starter         — Taxonomy domain: taxon/category/tag management, TaxonPort (auto-configured starter)
├── provider-profile-spring-boot-starter — Provider profile domain: MASTER/SHOP/SUPPORT catalog entries, ProviderProfilePort (auto-configured starter)
├── apikey-spring-boot-starter         — API-key credential domain: entity, hasher, ApiKeyPortImpl (auto-configured starter)
├── contact-spring-boot-starter        — Contact domain: phone/deep-link reveal for any EntityRef-owning entity, ContactPortImpl (auto-configured starter)
├── feedback-spring-boot-starter       — Feedback domain: rating+text feedback and its aggregate for any EntityRef-owning entity, FeedbackPortImpl (auto-configured starter)
├── promotion-spring-boot-starter      — Promotion domain: promotion placements (why an entity is lifted out of the catalog) for any EntityRef-owning entity, PromotionPlacementPortImpl (auto-configured starter)
├── integration-tests                 — Testcontainers repository tests + fixtures for every starter (test-only, never shipped)
├── marketplace-orchestrator           — application/BFF layer: cross-domain use-case orchestration between marketplace-app and the domain starters
├── marketplace-rest-api               — external REST API adapter: non-Vaadin HTTP delivery channel over marketplace-orchestrator, sibling to marketplace-app
├── marketplace-mcp                    — MCP server adapter: wraps the REST API for AI agents over HTTP with the caller's own API key, no project dependencies
└── marketplace-app                   — main Vaadin application (all UI)
```

- `integration-tests` is the only home of Testcontainers-based repository tests and their fixtures; domain starters never carry test code for this purpose.
- `integration-tests` may depend on whichever modules it tests only because it is never shipped or deployed. It needs a reachable Docker daemon and never runs inside the Docker build stage of `scripts/deploy-and-run.sh`.
- `platform-commons` contracts: one package per domain under `platform-commons/src/main/java/org/ost/platform/`, shared ones under `core`.
- Field-name-to-label mapping of the audit timeline lives in `marketplace-app`'s `AuditTimelineRowRenderer`, not in a per-domain hook; `AuditActivityFieldsHook` does not exist.
- `AttachmentMediaChangeHook` does not exist.

Module rules files in `.claude/rules/`, each loaded when a matching file is read (`.claude/rules/README.md`):
- `platform-commons.md` (package semantics, SPI naming), `marketplace-orchestrator.md`, `marketplace-rest-api.md`, `marketplace-mcp.md`, `marketplace-app.md` (UI component patterns), `query-lib.md` (SQL API), `html-sanitizer-lib.md`, `integration-tests.md`.
- `audit-spring-boot-starter.md`, `attachment-spring-boot-starter.md`, `user-spring-boot-starter.md`, `advertisement-spring-boot-starter.md`, `taxon-spring-boot-starter.md`, `provider-profile-spring-boot-starter.md`, `apikey-spring-boot-starter.md`, `contact-spring-boot-starter.md`, `feedback-spring-boot-starter.md`, `promotion-spring-boot-starter.md`.
- `playwright.md` (UI verification with Playwright), `scripts.md` (deployment, SonarQube, unit and integration test scripts, Playwright runs, the local CI runner).

---

## Architecture Guidelines

1. **Explicit over implicit:** Avoid hidden framework magic. If simple Java code works, use it.
2. **Three layers, not two:** `marketplace-app` (UI adapter: Vaadin, auth, locale; application-shell concerns only) → `marketplace-orchestrator` (application/BFF layer: cross-domain use-case composition) → domain starters (each owns its own bounded context).
   - A domain starter must not orchestrate another domain.
   - `marketplace-app` must not directly compose multiple domain Ports for a single application use case; that composition belongs in `marketplace-orchestrator`.
   - Vaadin exists only in `marketplace-app`, and all Vaadin UI code lives there; within `marketplace-app`, UI components may freely reference each other (no ports, no hooks, no indirection needed between UI classes).
   - No single class in `marketplace-orchestrator` may depend on more than two domain `*Port` interfaces via `ComponentFactory`; split it into smaller, composed use-case services (`.claude/rules/marketplace-orchestrator.md`).
   - `marketplace-orchestrator` never touches `JdbcClient`, any `*Repository` or any `*CrudRepository`; it composes results from domain Ports only.
3. **Strict Boundaries:** The UI layer must not call Repositories directly. Always go through a domain `*Port` (guideline 2 covers cross-domain use cases).
4. **Modular Storage:** `StorageService` and its implementations live in `attachment-spring-boot-starter` (`org.ost.attachment.services`). UI components must degrade gracefully via `ObjectProvider.ifAvailable()` when the attachment starter is absent from the classpath.
5. **Validation:** Use declarative validation rules in DTOs.
6. **Database Changes:** Schema must only be modified via Liquibase scripts in the owning starter's `db/<name>-changelog/changes` (guideline 7).
   - Every `<column>`/`<createTable>` must carry a `remarks="..."` attribute with the business meaning (why the column/table exists, cross-references to the ADR that decided its shape).
   - These `remarks` are the single source of truth: the Database ERD page of `docs/architecture/scripts/generate-architecture-model.sh` parses them live. Do not duplicate the explanation in a separate markdown file; when the meaning changes, edit the `remarks` attribute in the changelog.
7. **Starter independence:** No starter has a Vaadin dependency and no starter contains UI code (guideline 2).
   - Each starter owns its own Liquibase changelog under its own `db/*-changelog/` directory; changelogs are never merged into a shared file across starters.
   - Every starter rules file's "Key constraints"/"Schema" section states only what is specific to that module beyond this baseline.

**Pattern-first:** Before introducing a new abstraction or naming a class, scan the existing codebase for how similar things are already done. Symmetry with existing code is a first-class goal.

**Design by contract — no defensive empty checks:** Methods trust their inputs.
- If a parameter is not `Optional`, the caller must pass a non-null value; `null` → fail fast (`@NonNull` or `Objects.requireNonNull`).
- Empty collections are the caller's responsibility: a caller with nothing to pass skips the call. Methods must not have defensive `if (collection.isEmpty()) return emptyResult` guards; that logic belongs at the call site.

**No `Optional` parameters:** `Optional` must never be used as a method parameter type. Callers resolve the `Optional` before calling (via `.map()` / `.flatMap()`).

**`@NonNull` on parameters:** Every public method parameter that must not be null must be annotated with `lombok.NonNull` (fail fast, no silent NPE), in all layers: repositories, services, hooks, ports, UI components.

**Functional `Optional` style:**
- Prefer `.ifPresent()`, `.ifPresentOrElse()`, `.map()`, `.flatMap()`, `.or()` over imperative `orElse(null)` + null check.
- Use `orElse(null)` only when the downstream API explicitly accepts a nullable value (e.g. SQL parameters).
- When a method must return a nullable value, return `optional.orElse(null)` at the boundary, not in intermediate code.
- Early exit with `Optional` in complex methods: `Optional<User> maybeUser = ...; if (maybeUser.isEmpty()) return; user = maybeUser.get();`

**No `@Primary` on `ObjectMapper` beans:** when the context has multiple `ObjectMapper` beans, every injection site must use an explicit `@Qualifier("...")`; never mark one bean `@Primary` to resolve the ambiguity implicitly.

### Repository pattern

**Policy:** `*CrudRepository extends CrudRepository<T, Long>` for trivial save/find; plain `@Repository` class with `JdbcClient` for bespoke queries, SQL inlined as text blocks directly in methods.

- Entity classes annotated with `@Table`, `@Id`, `@CreatedDate`, `@LastModifiedDate` where applicable. `@CreatedDate` / `@LastModifiedDate` rely on the project-wide `AuditorAware<Long>` bean in `marketplace-app`'s `JdbcAuditingConfig`.
- Repository = `@Repository` class with `@RequiredArgsConstructor` + `@SuppressWarnings("java:S1192")`. Holds a `*CrudRepository` field for CRUD and a `JdbcClient` field for custom SQL.
- `RowMapper<T>` declared as a `private static final` constant in the repository class.
- Dynamic filtering: `SqlFilterBuilder<F>` declared as a `private static final` constant; built with `SqlBoundFilter.of(filterProperty, sqlExpression, conditionFn)` entries.
- Sorting: `OrderByBuilder.build(sort, aliasMap)` returns the `ORDER BY` clause or an empty string.
- Hand-rolled `INSERT` / `findById` SQL is removed whenever it duplicates what `CrudRepository.save` / `.findById` already provides.
- Starters that ship their own repositories must declare `@EnableJdbcRepositories(basePackages = "...")` in their `@AutoConfiguration`, because the marketplace `@SpringBootApplication` scan only covers `org.ost.marketplace`.
- No `TABLE`/`ALIAS`-style name constants; write the table name literally in the SQL.
- A SQL fragment used in only one method stays inlined in that method, never hoisted into its own `private static final String`; only a fragment genuinely reused across 2+ methods becomes a constant.

Reference implementation: `UserRepository` in `user-spring-boot-starter`.

---

## Spring Profiles
- `dev` (default): local PostgreSQL and SeaweedFS, see `marketplace-app/src/main/resources/application-dev.yml`.
- `prod`: configured by environment variables, see `marketplace-app/src/main/resources/application-prod.yml` and `INFRASTRUCTURE.md` "Environment Variables".

Local infrastructure only (IDE dev mode):
```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.seaweedfs.yml up -d
```

---

## Tooling

Project commands and agents, with their arguments and when to use each: `.claude/commands/README.md` and `.claude/nav/flows.md`.
- Run manually, not triggered automatically: `/check-architecture-map` before saving changes that can touch the architecture map; `/docker-clean` to remove the project's containers; `/sync-docs` after significant changes (new module, new SPI, schema changes).
- `/autopilot` is an explicit per-run opt-out of the standing Approval Rule's per-step gating, not a permanent one.
- `/reduce-code` (in development) is not in the command list Claude Code loads itself; its file is `.claude/commands/reduce-code.md`.
- The `deep-review-orchestrator` agent never writes code and never calls `ReportFindings`; the main thread calls it.

---

## Architectural Decisions Log

Significant decisions are recorded in the `<folder>/DECISIONS.md` of the module or tooling folder they concern; `.claude/nav/adr-index.md` is the generated index of every decision (navigation layer: `.claude/nav/README.md`).

Note: `user-spring-boot-starter`, `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`, `apikey-spring-boot-starter`, `marketplace-rest-api`, `marketplace-mcp`, `feedback-spring-boot-starter` and `promotion-spring-boot-starter` have no hand-authored `DECISIONS.md` of their own.
- Their key decisions are recorded in `marketplace-app/DECISIONS.md` and `platform-commons/DECISIONS.md` instead, except `marketplace-mcp`, whose cross-listed decisions are recorded in `marketplace-app/DECISIONS.md` and `docs/api-collections/DECISIONS.md`.
- Each of these modules has a generated, pointer-only `DECISIONS.md` (`bash docs/architecture/scripts/generate-architecture-model.sh`) listing whichever ADRs cross-reference it via the home ADR's own `**Also affects:**` tag; never hand-edit these files directly.

**Rules:**
- Record any new substantial architectural or technical decision there immediately, before the conversation ends, via `/record-decision`; never hand-write a `DECISIONS.md` entry directly (its worthiness gate and format checks are the point).
- When a decision contradicts or supersedes an existing entry, update or annotate the existing entry rather than only adding a new one.
- Each `DECISIONS.md` also tracks open goals (work not yet done). When implementing something that realizes a stated goal, mark it done in the same PR.
