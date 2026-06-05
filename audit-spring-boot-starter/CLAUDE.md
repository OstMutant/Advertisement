## audit-spring-boot-starter

Auto-configures the full audit subsystem (write + read + Vaadin UI) when the jar is on the classpath. Java package root: `org.ost.audit`.

---

### Module surface

| Side | Classes |
|---|---|
| Write | `DefaultAuditPort`, `AuditLogRepository`, `AuditableSnapshot.diff()` |
| Read  | `AuditReadService` (consolidated history + activity reads) |
| UI    | `AuditHistoryPanel`, `AuditActivityPanel` + renderers (`AuditHistoryRowRenderer`, `AuditActivityRowRenderer`, `AuditHistoryListRenderer`, `AuditActivityListRenderer`), `AuditChangeFormatter`, `AuditSnapshotBinder` |

`AuditAutoConfiguration` owns three infrastructure beans: Liquibase changelog, named `ObjectMapper`, cleanup scheduler. Ports/Hooks impls are picked up via `@ComponentScan`, not declared as `@Bean` methods. `@EnableJdbcRepositories(basePackages = "org.ost.audit.repository")` is mandatory because the marketplace `@SpringBootApplication` scan does not reach this package.

---

### SPI map (all interfaces live in `platform-commons/audit.spi`)

| Direction | Interface | Implemented in |
|---|---|---|
| marketplace → starter | `AuditPort` | starter (`DefaultAuditPort`) |
| marketplace → starter | `AuditUiPort` | starter (`AuditUiPortImpl`) |
| starter → marketplace | `CurrentActorHook` | marketplace |
| starter → marketplace | `AuditDomainHook` | marketplace |
| starter → marketplace | `AuditActivityFieldsHook` | marketplace |
| starter → marketplace | `AuditActivityRowHook` | marketplace (multiple impls) |
| starter → marketplace | `AuditActivityEnrichHook` | marketplace |

→ Suffix semantics and consolidation in @platform-commons/CLAUDE.md

---

### Rules

**ObjectMapper:** the starter ships `@Bean("auditObjectMapper") ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES = false`, `@ConditionalOnMissingBean(name = "auditObjectMapper")`. Every internal injection site qualifies with `@Qualifier("auditObjectMapper")`. Never `@Primary`. Same convention applies to every starter — see `platform-commons/DECISIONS.md`.

**i18n:** all translation keys live in `AuditI18n implements TranslationKey`. Consumers call `I18nService.get(AuditI18n.*)`. Never raw `MessageSource`, never `msg(key, fallback)`.

**Actor vocabulary:** the starter speaks about *actors* and *subjects* — never *users*. DB column is `actor_id`. Hooks are `CurrentActorHook`, not `CurrentUserHook`. Never reintroduce "user" wording in SPIs, log messages, or DTOs.

**Projections never JOIN domain tables.** `EntityHistoryProjection` and `ActivityProjection` return raw `actor_id` / `entity_id`. Names and existence are resolved via bulk SPI calls (`AuditActorNameResolver`, `AuditEntityExistenceChecker`) — single `ANY(:ids)` SELECT per query, never per-row.

**Action badge UI rule (history + activity):** every action badge carries two CSS classes — a base (`entity-history-action` or `activity-feed-action`) and a modifier derived from `ActionType.name().toLowerCase()` (`--created`, `--updated`, `--deleted`, `--restored`). Colors are identical across the two stylesheets. Single-class with hardcoded color is forbidden.

**Restore semantics:** restore flows must source data from `AuditPort.getSnapshotContent(snapshotId, entityType)`. `getPreviousSnapshotContent(...)` returns the *before*-state and is only for diffs / "what changed" UI. Mixing them silently restores the wrong revision.

**`AuditSnapshotBinder<T>`** is the canonical activity-row decorator: pass `Class<T>`, an `is-current` `Predicate<T>`, and an optional `BiConsumer<Long, T> onRestore`. The starter never parses snapshot JSON itself — shape knowledge lives in the consumer.

---

### Repository pattern

`AuditLogRepository` uses `@Repository` + `JdbcClient` (no `CrudRepository`) — every read path is bespoke SQL. `SqlEntityProjection`-style RowMapper as `static final` constants. Follows the project-wide repository policy → @CLAUDE.md.

---

### Where to look first

- New SPI signature → `platform-commons/audit/spi/` (declaration) + this module's `services/` or `ui/` (impl).
- Activity feed row not rendering correctly → `AuditActivityRowRenderer` + the relevant `AuditActivityRowHook` in marketplace.
- New entity type needs history → register `AuditableSnapshot` impl in marketplace; no change in this module.
