# Architecture & Technical Decisions — integration-tests

---

## ADR-001: One module owns every Testcontainers test — domain starters carry zero test code
**Status:** Accepted

**Context:** The natural first instinct is "each starter hosts its own repository tests in its own
`src/test/java`." But a repository test that needs an FK-satisfying row from another domain (e.g.
`AdvertisementRepositoryTest` — `advertisement.created_by` has a `NOT NULL` FK to
`user_information.id`) would then force every such starter to either depend on
`user-spring-boot-starter` itself (a real starter-to-starter coupling, even if test-scoped), or
reinvent its own fixture/stub logic independently — duplicated across every starter that needs a
test actor row.

**Decision:** `integration-tests` is the sole home for every Testcontainers-based repository test
and its supporting fixtures, across every domain starter. Domain starters (`advertisement-spring-
boot-starter`, `user-spring-boot-starter`, etc.) never carry test code for this purpose — no
`src/test/java` additions to them, ever. `integration-tests` depends on whichever starters it needs
to test (`compile` scope, since its own `src/main`/`src/test` reference those classes directly) —
safe only because this module is never shipped, deployed, or depended upon by anything else (a
leaf node with zero inbound edges — see `docs/architecture/01-module-dependencies.md`). This does
**not** violate `.claude/rules.md` "Module Import Rules" ("starters must NOT import from each
other"): that rule governs production runtime composability (a starter must compile and run
standalone when a sibling isn't on the classpath, because `ObjectProvider`-based optional wiring
depends on it) — not test-time verification of a module that is never part of any running
deployment.

**Rejected alternative — a test-only stub schema instead of the real one:** an earlier draft
considered giving `advertisement-spring-boot-starter`'s test a hand-rolled minimal
`user_information` stub table (just enough columns to satisfy the FK) instead of depending on
`user-spring-boot-starter` at all. Rejected: a stub schema can silently drift from the real one and
would mask real cross-module schema-compatibility regressions that a test against the real starter
schema would actually catch.

**Consequences:**
- One canonical fixture (`UserTestFixtures`) is reused by every repository test that needs an
  actor row, instead of duplicated per starter.
- Adding a new `*RepositoryTest` for a starter never touched before means adding that starter as a
  new `compile`-scope dependency of `integration-tests` — never touching the starter's own pom.
- See `integration-tests/CLAUDE.md` for the full narrative.

---

## ADR-002: Singleton Testcontainers Postgres container, shared across the whole `mvn test` run
**Status:** Accepted

**Context:** Testcontainers' default per-test-class container lifecycle pays the ~5-10s Postgres
startup cost once per test class. With multiple `*RepositoryTest` classes across Batches 1-3, that
cost multiplies linearly and dominates total run time — the opposite of the "~30s inner loop" goal
this whole test layer exists for (`improvement-027`).

**Decision:** `AbstractPostgresIntegrationTest` starts exactly one `PostgreSQLContainer` via a
plain static field + static initializer block (not `@Testcontainers`/`@Container`, which are
per-class) — Testcontainers' documented "singleton container" pattern. Every `*RepositoryTest`
class extends it and shares the one running container for the entire `mvn test` reactor
invocation; Ryuk (when not disabled — see ADR-004) tears it down at JVM exit.

**Consequences:**
- Container startup cost is paid once per `mvn test` run, not once per test class.
- Data isolation across test classes sharing the one physical container is achieved by each test
  applying its own starter's real Liquibase changelog (distinct table names, no collision today)
  plus per-class `@BeforeEach` cleanup (see ADR-006) — not by separate containers/databases.
- `@ServiceConnection` (`spring-boot-testcontainers`) wires the `DataSource` for every
  `@SpringBootTest`-based test against this one container automatically — no manual
  `@DynamicPropertySource`.

---

## ADR-003: Shared `.env` (`POSTGRES_IMAGE`) instead of a hardcoded image string
**Status:** Accepted

**Context:** The first draft of `AbstractPostgresIntegrationTest` hardcoded `"postgres:15-alpine"`
as a Java string literal, independent of the identical hardcoded value in
`scripts/infra/docker-compose.db.yml` — a two-places-to-update-in-sync risk, applied to config
instead of SQL/logic.

**Decision:** A repo-root `.env` (`POSTGRES_IMAGE=postgres:15-alpine`) is the single source read by
both consumers: Docker Compose natively, and `SharedEnvConfig.require(key)` — a small helper that
walks up from the JVM's working directory (bounded, 5 parent levels) to find `.env`, so it resolves
correctly whether `mvn test` is launched from the repo root, a module subdirectory, or an IDE test
runner (IntelliJ commonly sets the working directory to the module, not the reactor root, when
running a single test via the gutter icon). Fails fast with a clear message if `.env` or the key
isn't found — no silent default.

**Consequences:**
- Renaming the Postgres version updates both consumers from one place.
- `deploy.sh`'s own separate `docker pull`/`docker run` references to `postgres:15-alpine` were
  **not** touched by this decision — tracked separately in
  [improvement-044](../backlog/issues/improvement-044-shared-env-config-consolidation.md).
- Neither Docker Compose's `.env` auto-load nor `SharedEnvConfig`'s upward search is AI-specific —
  both work identically for a human running the same commands from a terminal or an IDE.

---

## ADR-004: Sandbox-only env vars — `TESTCONTAINERS_RYUK_DISABLED` / `INTEGRATION_TESTS_POSTGRES_FIXED_PORT`
**Status:** Accepted

**Context:** In the claude-dev sandbox this module was built in, Testcontainers can create a
container via the Docker socket successfully, but the test JVM cannot reach the container's
*dynamically assigned* published port (`Connection refused`, confirmed via both the auto-detected
Docker host IP and `TESTCONTAINERS_HOST_OVERRIDE=localhost`) — while a direct TCP probe confirmed
the *static* port already published by `advertisement-db` (`5432`) **is** reachable from the same
shell. Ryuk (the container reaper) also cannot connect back to the test JVM in this sandbox. Both
are a Docker Desktop / socket-proxy mismatch specific to this sandbox's networking, not a bug in
this repo's code or in Testcontainers itself — the same class of issue as the already-documented
`playwright/CLAUDE.md` note ("Volume mounts don't work from inside the claude container").

**Decision:** `AbstractPostgresIntegrationTest` reads an optional
`INTEGRATION_TESTS_POSTGRES_FIXED_PORT` env var; when set, it forces a fixed host-port binding via
`PostgreSQLContainer.setPortBindings()` instead of Testcontainers' normal random-port assignment.
`TESTCONTAINERS_RYUK_DISABLED=true` disables the reaper. Both are env-gated — unset (the default,
e.g. on a real developer machine with normal Docker networking), behavior is unchanged.
`scripts/integration-tests.sh --sandbox` sets both automatically; omit `--sandbox` on a normal
machine.

**Consequences:**
- Not fixable from within this repo — it's sandbox infrastructure, not application code.
- A real CI runner (GitHub Actions, not this sandbox) likely has normal Docker networking and
  wouldn't need either variable; if it somehow does, the fix at that point is a CI-job-index-derived
  port, not a single hardcoded one (see `improvement-027`'s "Does a fixed port conflict with
  parallel test execution?" note for the reasoning on why a single fixed port is safe today but not
  necessarily under future parallel CI jobs).

---

## ADR-005: `jakarta.servlet-api` as a `provided`-scope dependency
**Status:** Accepted

**Context:** `UserAutoConfiguration` declares a `securityContextRepository()` bean
(`new HttpSessionSecurityContextRepository()`) whose constructor references
`jakarta.servlet.http.HttpServletResponse` directly. `integration-tests` has no web/servlet
dependency (it is a plain test module, not a Vaadin/Spring MVC application) — booting
`UserAutoConfiguration` in `AdvertisementRepositoryTest`'s Spring context (required for the FK to
`user_information`) failed with `NoClassDefFoundError: jakarta/servlet/http/HttpServletResponse`
at bean-instantiation time.

**Decision:** Add `jakarta.servlet:jakarta.servlet-api` as a `provided`-scope dependency in
`integration-tests/pom.xml` (version managed by the inherited `spring-boot-starter-parent` BOM —
no explicit version pinned). This satisfies class loading for the bean's constructor without
pulling in an actual servlet container — the bean is never invoked by a repository test, only
constructed during context startup.

**Consequences:**
- Any future `*RepositoryTest` that boots `UserAutoConfiguration` (or any other starter's
  autoconfiguration that happens to declare a servlet-dependent bean) does not need to repeat this
  fix — the dependency is module-wide.
- `provided` scope, not `compile` — this module never ships, so there is no risk of it leaking
  transitively into anything downstream.

---

## ADR-006: Reusable test "steps" — `RepositoryTestSupport` / `TestDataCleaner`
**Status:** Accepted

**Context:** `AdvertisementRepositoryTest` (Batch 1) needed a `@TestConfiguration` bean bag
(originally `@EnableAutoConfiguration` — see ADR-009 for why this later changed to
`@ImportAutoConfiguration` with an explicit class list — plus `@EnableJdbcAuditing`, a
`MutableAuditorAware`, empty `ComponentFactory<AuditPort>`/`ComponentFactory<AttachmentPort>`
beans) and a per-test DB-cleanup routine. Written inline the first time, this boilerplate was about
to be re-typed verbatim in every future `*RepositoryTest` (Batch 3: `AuditLogRepositoryTest`,
`TaxonAssignmentRepositoryTest`, `AttachmentRepositoryTest`).

**Decision:** Extract to `org.ost.integrationtests.support` (in `src/main`, not `src/test`, so
`*RepositoryTest` classes can import without a test-jar dependency), by direct analogy with
Playwright's `_flows/*.flow.js` convention (`playwright/CLAUDE.md`: extract a shared helper only
once two or more consumers need it):
- `RepositoryTestSupport` — the `@TestConfiguration` bean bag, added to a test's
  `@SpringBootTest(classes = {...})` list.
- `TestDataCleaner.cleanTables(jdbcClient, "table1", "table2", ...)` — FK-ordered row deletion,
  called from `@BeforeEach`.

**Consequences:**
- A test that needs a *different* optional port (e.g. `TaxonPort` for a future
  `TaxonAssignmentRepositoryTest`) declares its own extra `ComponentFactory` bean locally —
  `RepositoryTestSupport` only covers the ports every repository test has hit so far
  (`AuditPort`, `AttachmentPort`); it is not meant to grow into a bean bag for every possible port.
- Full usage example: `integration-tests/CLAUDE.md` "Reusable test support (steps/blocks)".

---

## ADR-007: `run.sh` auto-detects starter staleness instead of a manual skip-`-am` flag
**Status:** Accepted (revised same day — see "Revision" below; original manual-flag design is not
current)

**Context:** `run.sh` always ran `./mvnw -pl integration-tests -am test` — `-am` ("also-make")
rebuilds every module `integration-tests` depends on (`platform-commons`, `advertisement`/`user`/
`taxon`/`audit-spring-boot-starter` — `audit` added later, see ADR-009) on every single invocation,
even when none of them changed. Measured
directly in this sandbox: ~100s of pure "nothing to compile" Maven plugin overhead walking through
those 7 reactor modules, on top of the actual test run — meaning re-running a test after only
editing a test file inside `integration-tests` itself (the common case while writing/fixing tests)
paid the same cost as a full rebuild. Root cause: `mvn test` (unlike `mvn install`) never publishes
built artifacts to `~/.m2/repository` — confirmed empirically (`find ~/.m2/repository/org/ost
-name "*.jar"` returned nothing before this session's first `mvn install`) — so without `-am`,
Maven has no JAR to resolve `integration-tests`' starter dependencies from at all.

**Original decision (superseded same day):** keep `-am` as the default, add an opt-in `--fast`
flag to drop it. Rejected on reflection — `--fast` was a manual opt-in the developer had to
remember to use *and* had to remember to stop using right after editing a starter's own source, or
it would silently test against a stale `~/.m2` JAR. A flag whose safety depends on the developer's
memory is exactly the kind of footgun this project avoids elsewhere (e.g. the reason
`UserProfileUpdate` exists as a narrower entity instead of relying on builder discipline — see
`user-spring-boot-starter/CLAUDE.md`).

**Revision — automatic staleness detection, no flag required for the common case:** `run.sh` now
compares each starter module's newest `.java` file (`find <module>/src/main -name
'*.java' -newer <installed-jar>`) against its installed `~/.m2` JAR's mtime before every run. If
any source is newer than its JAR (or the JAR doesn't exist yet), it runs a targeted `mvn install
-DskipTests` for just those modules first; otherwise it skips straight to `mvn -pl
integration-tests test` (no `-am`). Confirmed directly, both directions: (a) an unmodified checkout
correctly skips the reinstall and runs fast, (b) `touch`-ing a starter `.java` file is correctly
detected and triggers a targeted reinstall before the test runs. A `--no-check` flag remains for
deliberately bypassing the check (e.g. reproducing behavior against a specific already-built JAR)
— never for normal edit/test iteration, since it reintroduces the exact stale-JAR risk the
auto-detection exists to close.

**Consequences:**
- No developer memory required for the default path — the check runs every time, correctness is
  automatic, not opt-in.
- The `find -newer` check itself costs a fraction of a second per module — negligible next to the
  ~100s of Maven reactor-walk overhead it replaces.
- `--no-check` inherits the old `--fast` flag's risk profile explicitly, by name, only when
  intentionally invoked — never the default.
- Same rationale applies to the separate, complementary idea of unifying all `*RepositoryTest`
  classes under one shared `@SpringBootTest(classes = {...})` combination so Spring's own
  ApplicationContext cache can be reused across classes within a single `mvn test` run — not
  implemented as part of this ADR (evaluated as roughly break-even for today's 3 repository test
  classes, with a real test-isolation cost — a broken bean/changelog in any one starter would then
  fail every repository test together, not just its own domain's), but revisit once Batch 2/3
  (`AuditLogRepositoryTest`, `AttachmentRepositoryTest`, `TaxonAssignmentRepositoryTest`) land and
  the per-class bootstrap cost starts dominating total suite time more clearly.

---

## ADR-008: Test package-private/private internal logic through its public entry point, never through a same-package trick or a widened production visibility
**Status:** Accepted

**Context:** `DefaultTaxonPort.resolveTranslation()` (improvement-045 item 6) is package-private —
it has no direct external impact of its own; it only matters through the public
`TaxonPort.findById()`/`getAllByType()` contract that calls it internally via `toDto()`. Two ways
to unit-test it directly were considered and rejected:
1. A test class placed in the exact same package name (`org.ost.taxon.services`) inside
   `integration-tests/src/test/java` — Java's package-private access works across separate JARs/
   modules as long as there's no `module-info.java` (confirmed none exists in this project), so
   this technically compiles. Rejected: breaks `integration-tests`' own established package
   convention (`org.ost.integrationtests.<domain>`) purely to route around a visibility a starter
   deliberately chose, for a method with no meaning outside its one caller.
2. Widening `resolveTranslation()` to `public` on `DefaultTaxonPort` so a normal
   `org.ost.integrationtests.taxon` test could call it directly. Rejected: weakens encapsulation of
   a genuine internal implementation detail purely for test convenience — nothing outside
   `DefaultTaxonPort` has ever needed to call it, and widening visibility is a production-code
   change made *for* a test, not a real requirement.

**Decision:** Test the *behavior* through the public contract that actually exercises the internal
method, using repository-level fixture setup (bypassing service-layer validation) when needed to
reach a state the public API itself can't produce. Concretely, `TaxonPortTranslationFallbackTest`
(`org.ost.integrationtests.taxon`) calls the real `TaxonPort.findById()` and asserts on the
returned `TaxonDto.name` for each fallback tier — never references `resolveTranslation()` or
`DefaultTaxonPort` by name at all. Fixture setup goes through `TaxonRepository`/
`TaxonTranslationRepository` directly (not `TaxonPort.create()`), because `TaxonService.create()`'s
own validation requires a translation for every `TaxonProperties.supportedLocales()` entry — the
public creation path can never produce the incomplete-translation state this fallback logic exists
to handle in the first place (e.g. a taxon created before a new locale was added to
`supportedLocales`). That's a real, if rare, production state, not a test-only fiction — the same
reasoning `TaxonRepositoryTest`/`AdvertisementRepositoryTest` already use for building entities
directly via repositories instead of through the service layer.

**Consequences:**
- No production visibility was widened; no test lives outside `integration-tests`' own package
  convention.
- The test is slightly heavier (real Testcontainers + Liquibase + Spring context, not a bare
  Mockito unit test) than a hypothetical isolated `resolveTranslation()` test would have been —
  accepted, since it also proves the full `toDto()`/repository wiring path works, not just the
  fallback algorithm in isolation.
- **Applies directly to improvement-045 item 7** (`UserService.applyUserRestore()`) — that method
  is `private` (stricter than `resolveTranslation()`'s package-private), called only from the
  public `UserService.restoreToSnapshot()`; the same shape of test (call `restoreToSnapshot()`,
  assert on the result, use repository-level fixture setup for any state the public API can't
  reach) applies when that item is implemented — do not reach for `private`-access workarounds
  there either.

---

## ADR-009: `@ImportAutoConfiguration` explicit allow-list instead of `@EnableAutoConfiguration` in shared test config
**Status:** Accepted

**Context:** `RepositoryTestSupport` and `UserServiceRestoreTest.TestConfig` both originally used
`@EnableAutoConfiguration` (with `UserServiceRestoreTest.TestConfig` also carrying a hand-written
`ComponentFactory<AttachmentPort>` stub bean to compensate for one side effect of it). This
annotation pulls in every `@AutoConfiguration` class found anywhere on the classpath, not just what
a given test actually declares — a real problem specifically for `integration-tests`, whose own
design (ADR-001) means its classpath keeps growing over time as Batches 2/3 add more starter
dependencies. Confirmed directly, twice, in the same session: adding `audit-spring-boot-starter` as
a dependency (for `UserServiceRestoreTest`, improvement-045 item 7) silently broke
`AdvertisementRepositoryTest`/`TaxonRepositoryTest`/`TaxonPortTranslationFallbackTest`/
`UserRepositoryTest` in a full-suite run — the classpath-wide cascade pulled in the real
`AuditAutoConfiguration` for every test using `RepositoryTestSupport`, whose `defaultAuditPort` bean
then failed to construct (missing `CurrentActorHook`, which `RepositoryTestSupport` never
provisions, by design — see its own javadoc). A single-class `-Dtest=X` run never surfaces this,
since the break depends only on what's on the classpath, not on which test is selected.

An `exclude = AuditAutoConfiguration.class` deny-list fixed that one occurrence, but was explicitly
rejected as the long-term direction: it's a reactive fix that has to be remembered and repeated for
every future starter dependency (Batch 3 alone adds three more candidates), with no compiler or
test enforcement that anyone actually does it — the exact same silent-break shape recurring on a
schedule tied to how often `integration-tests`' `pom.xml` grows.

**Decision:** Replace `@EnableAutoConfiguration` with `@ImportAutoConfiguration({...})` and an
explicit class list, in both `RepositoryTestSupport` and `UserServiceRestoreTest.TestConfig`. The
list covers exactly the Spring Boot JDBC/Liquibase/Transaction infrastructure every
`@SpringBootTest` in this module needs — nothing from any domain starter:
```java
@ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,               // org.springframework.boot.jdbc.autoconfigure
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcClientAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        DataJdbcRepositoriesAutoConfiguration.class,      // org.springframework.boot.data.jdbc.autoconfigure
        LiquibaseAutoConfiguration.class,                 // org.springframework.boot.liquibase.autoconfigure
        TransactionAutoConfiguration.class,               // org.springframework.boot.transaction.autoconfigure
        ConfigurationPropertiesAutoConfiguration.class    // org.springframework.boot.autoconfigure.context —
                                                           // UserServiceRestoreTest.TestConfig only, needed
                                                           // once AuditAutoConfiguration's own @ConfigurationProperties
                                                           // consumer is genuinely wired in (see below)
})
```
Domain-starter autoconfiguration (`AdvertisementAutoConfiguration`, `TaxonAutoConfiguration`, ...)
is never in this list — those are always passed explicitly via each test's own
`@SpringBootTest(classes = {...})`, which is the entire point: nothing is ever pulled in by
implication again, so a new starter dependency in `pom.xml` can no longer silently affect any
existing test's Spring context.

**Spring Boot 4.0.6 packaging note (worth recording — easy to get wrong without checking):** what
used to be one monolithic `spring-boot-autoconfigure` jar in Boot 3.x is now split into several
per-feature modules — `spring-boot-jdbc`, `spring-boot-data-jdbc`, `spring-boot-liquibase`,
`spring-boot-transaction` each contribute a handful of `*AutoConfiguration` classes under their own
new package roots (`org.springframework.boot.jdbc.autoconfigure`, etc.), not
`org.springframework.boot.autoconfigure.jdbc` like in Boot 3.x. `@ImportAutoConfiguration` itself
did not move — still `org.springframework.boot.autoconfigure.ImportAutoConfiguration`. Verified
directly against the actual jars in `~/.m2` (`jar tf ... | grep AutoConfiguration.class`), not
assumed from prior Boot 3.x knowledge.

**Second bug this surfaced — `TestDataCleaner.cleanTables()` assumed every domain's schema always
exists:** switching away from `@EnableAutoConfiguration` exposed a second, independent latent bug.
`TestDataCleaner.cleanAll()` unconditionally `DELETE FROM`s every table across every domain (by
design, ADR-006 — the singleton container means any test class can run after any other). Before
this ADR, `RepositoryTestSupport`'s old classpath-wide cascade happened to also apply *every*
starter's Liquibase changelog for *every* test's context as a side effect, so every domain's tables
always existed by the time any test's `@BeforeEach` ran, regardless of execution order. With the
explicit allow-list, only the specific domain starters a test actually lists in its own
`@SpringBootTest(classes = {...})` get their Liquibase changelog applied to the shared database —
meaning `cleanAll()` could legitimately run before some other, later-running test class has created
a given domain's tables. Fixed in `TestDataCleaner.cleanTables()`: catch `BadSqlGrammarException`
and swallow only the Postgres "undefined table" case (SQLSTATE `42P01`) — nothing to clean if the
schema doesn't exist yet is an expected state under the singleton-container design, not an error.

**Third bug this surfaced — `AuditAutoConfiguration` never self-registered `CleanupProperties`:**
`AuditCleanupService` (owned by `audit-spring-boot-starter`) directly injects `CleanupProperties`,
but unlike `AdvertisementAutoConfiguration`/`AttachmentAutoConfiguration` (which each correctly
self-declare `@EnableConfigurationProperties(CleanupProperties.class)` because they too directly
consume it) or `TaxonAutoConfiguration` (same pattern, for `TaxonProperties`), `AuditAutoConfiguration`
never did. It silently worked in every context that happened to also load Advertisement or
Attachment's autoconfiguration (true in production — `marketplace-app/Application.java` also
declares it, redundantly but harmlessly) — a real, if previously invisible, production risk: any
hypothetical minimal deployment running audit-spring-boot-starter without advertisement/attachment
present would have failed to start. Fixed directly in `AuditAutoConfiguration` (not worked around in
test config) — see `audit-spring-boot-starter/DECISIONS.md`.

**Consequences:**
- Adding a new starter dependency to `integration-tests/pom.xml` can never again silently break an
  unrelated test's Spring context — the failure mode this ADR exists to close is now structurally
  impossible, not just less likely.
- `integration-tests/run.sh`'s `STARTER_MODULES` staleness-check list (ADR-007) must include
  `audit-spring-boot-starter` now that it's a real dependency — added in the same change (was
  missing, which meant the `AuditAutoConfiguration` fix below wasn't picked up by the staleness
  check until installed manually once).
- Any *new* `@SpringBootTest`-based test class in this module that needs Boot infrastructure beyond
  what's in the list above (e.g. a future `@EnableCaching`-dependent test) must extend the list
  explicitly, not reach for `@EnableAutoConfiguration` as a shortcut — that would silently
  reintroduce the exact fragility this ADR removes.
- Full verification: two consecutive full `bash scripts/integration-tests.sh --sandbox` runs
  (`mvn -pl integration-tests test`, all classes together, no `-Dtest` filter), 41/41 green both
  times.
