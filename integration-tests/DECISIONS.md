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
  [improvement-044](../features/issues/improvement-044-shared-env-config-consolidation.md).
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
(`@EnableAutoConfiguration` + `@EnableJdbcAuditing`, a `MutableAuditorAware`, empty
`ComponentFactory<AuditPort>`/`ComponentFactory<AttachmentPort>` beans) and a per-test DB-cleanup
routine. Written inline the first time, this boilerplate was about to be re-typed verbatim in every
future `*RepositoryTest` (Batch 3: `AuditLogRepositoryTest`, `TaxonAssignmentRepositoryTest`,
`AttachmentRepositoryTest`).

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

## ADR-007: `run.sh --fast` — opt-in skip of Maven's `-am` reactor rebuild
**Status:** Accepted

**Context:** `run.sh` always ran `./mvnw -pl integration-tests -am test` — `-am` ("also-make")
rebuilds every module `integration-tests` depends on (`platform-commons`, `advertisement`/`user`/
`taxon-spring-boot-starter`) on every single invocation, even when none of them changed. Measured
directly in this sandbox: ~100s of pure "nothing to compile" Maven plugin overhead walking through
those 7 reactor modules, on top of the actual test run — meaning re-running a test after only
editing a test file inside `integration-tests` itself (the common case while writing/fixing tests)
paid the same cost as a full rebuild. Root cause: `mvn test` (unlike `mvn install`) never publishes
built artifacts to `~/.m2/repository` — confirmed empirically (`find ~/.m2/repository/org/ost
-name "*.jar"` returned nothing before this session's first `mvn install`) — so without `-am`,
Maven has no JAR to resolve `integration-tests`' starter dependencies from at all.

**Decision:** Keep `-am` as the **default** (safe, always correct, matches prior behavior — works
on a fresh clone with an empty `~/.m2`). Add an explicit, opt-in `--fast` flag to `run.sh` that
drops `-am`, resolving starter dependencies from whatever JARs are already installed in `~/.m2`
instead. Measured: ~1:47 total for a single test class with `--fast` vs. 3-7 min with `-am`, once
`~/.m2` is populated (`./mvnw install -DskipTests`, one-time).

**Rejected alternative — making `--fast` (no `-am`) the default:** considered and rejected. A
fresh clone or CI runner with an empty `~/.m2` would fail outright on the very first run with a
confusing dependency-resolution error, not a clear "you need to run X first" message. Defaulting
to the always-safe `-am` and requiring an explicit opt-in for the faster-but-conditional path is
the same shape as `--sandbox` (opt-in for a machine-specific override) — never silently changes
behavior for someone who didn't ask for it.

**Consequences:**
- `--fast` **requires** a prior `./mvnw install -DskipTests` (or any earlier non-`--fast` run) to
  have populated `~/.m2` with the starter JARs.
- `--fast` **must never** be used immediately after editing a domain starter's own source
  (`advertisement-spring-boot-starter/src/main/java/...`, etc.) — it would silently test against
  the stale JAR still in `~/.m2`, not the edit. Run `./mvnw install -pl <module> -am -DskipTests`
  (or one run without `--fast`) first; `--fast` is safe again afterward for iterating purely on
  `integration-tests`' own test files, which is the overwhelmingly common case once a starter's
  behavior is already covered and you're just fixing/extending the test itself.
- Same rationale applies to the separate, complementary idea of unifying all `*RepositoryTest`
  classes under one shared `@SpringBootTest(classes = {...})` combination so Spring's own
  ApplicationContext cache can be reused across classes within a single `mvn test` run — not
  implemented as part of this ADR (evaluated as roughly break-even for today's 3 repository test
  classes, with a real test-isolation cost — a broken bean/changelog in any one starter would then
  fail every repository test together, not just its own domain's), but revisit once Batch 2/3
  (`AuditLogRepositoryTest`, `AttachmentRepositoryTest`, `TaxonAssignmentRepositoryTest`) land and
  the per-class bootstrap cost starts dominating total suite time more clearly.
