## integration-tests

Sole home for every Testcontainers-based repository test and its supporting fixtures, across
every domain starter. Owns both the shared scaffolding (container lifecycle, `.env` config) and
the actual test classes themselves — domain starters never carry any of this.

Java package root: `org.ost.integrationtests`

---

## Architecture decision: why this module exists (2026-07-14)

**Domain starters (`advertisement-spring-boot-starter`, `user-spring-boot-starter`, etc.) must
stay 100% free of test code for this purpose — no `src/test/java` additions to them, ever, for
Testcontainers repository tests or their fixtures.** All of it — the actual `*RepositoryTest`
classes, and any shared fixture utility a test needs (e.g. `UserTestFixtures`, creating a test
user row for FK-dependent inserts) — lives here instead, in one dedicated module.

**Why this doesn't violate "starters must not depend on each other"** (`.claude/rules.md`
"Module Import Rules"): that rule governs production runtime composability — a starter must
compile and run standalone when a sibling starter isn't on the classpath, because `ObjectProvider`
-based optional wiring depends on it. `integration-tests` is never shipped, never deployed, never
a runtime dependency of anything — it exists only to run `mvn test` against real starters through
a real Postgres. It can freely depend on `advertisement-spring-boot-starter`,
`user-spring-boot-starter`, `platform-commons`, and any future starter it needs to test
(`compile` scope, since this module's own `src/main`/`src/test` need those classes directly), all
without those starters ever becoming aware of each other or of this module.

**Why not scatter tests into each starter's own `src/test/java` instead:** a repository test that
needs a FK-satisfying user row (e.g. `AdvertisementRepository` — `advertisement.created_by` is
`NOT NULL` with an FK to `user_information.id`) would otherwise force each starter needing this to
either (a) depend on `user-spring-boot-starter` itself (a real, if test-scoped, starter-to-starter
coupling that ArchUnit/Enforcer — improvement-030/031 — would eventually need to special-case), or
(b) reinvent its own fixture/stub logic independently, duplicating it across every starter that
needs a test actor row (advertisement, and later audit/attachment/taxon in Batch 3). Centralizing
in `integration-tests` gives one canonical fixture, reused everywhere, with zero coupling leaking
into any domain starter's own dependency graph.

**Rejected alternative — a test-only stub schema instead of the real one:** an earlier draft of
this design considered giving `advertisement-spring-boot-starter`'s test a hand-rolled minimal
`user_information` stub table (just enough columns to satisfy the FK) instead of depending on
`user-spring-boot-starter` at all. Rejected: a stub schema can silently drift from the real one
(exactly the class of risk this same session spent significant effort closing for
`POSTGRES_IMAGE` — see improvement-027/044) and would mask real cross-module schema-compatibility
regressions that a test against the real `user-spring-boot-starter` schema would actually catch.

---

## What it owns

- `AbstractPostgresIntegrationTest` — shared singleton-container base class. A single Postgres
  Testcontainers instance, started once via a static initializer (not per test class), shared
  across every test class in one `mvn test` reactor run via Testcontainers' documented "singleton
  container" pattern. Uses `@ServiceConnection` (`spring-boot-testcontainers`) so
  `@SpringBootTest`-based repository tests get their `DataSource` autoconfigured against the
  container automatically — no manual `@DynamicPropertySource` wiring needed.
- `SharedEnvConfig` — reads the repo-root `.env` file (walking up from the JVM's working
  directory, so it resolves whether launched from the repo root, a module subdirectory, or an IDE
  test runner). `AbstractPostgresIntegrationTest` uses it to source `POSTGRES_IMAGE`, the single
  source of truth also read natively by `scripts/infra/docker-compose.db.yml` — renaming the
  Postgres version updates both consumers from one place, no drift possible.
- Per-starter `*RepositoryTest` classes (e.g. `advertisement/AdvertisementRepositoryTest`) and
  plain unit tests for pure logic that would otherwise have no home (e.g. `diff()` on
  `platform-commons` snapshot DTOs) — organized into sub-packages per domain
  (`org.ost.integrationtests.advertisement`, etc.) to keep the module navigable as it grows.
- `support/RepositoryTestSupport` and `support/TestDataCleaner` — reusable "steps", by analogy
  with Playwright's `_flows/*.flow.js` (see below).

---

## Reusable test support (steps/blocks) (2026-07-14)

Extracted after `AdvertisementRepositoryTest` (Batch 1) proved the pattern — the same boilerplate
was about to be re-typed verbatim in every future `*RepositoryTest` (Batch 3:
`AuditLogRepositoryTest`, `TaxonAssignmentRepositoryTest`, `AttachmentRepositoryTest`). Mirrors the
Playwright convention: extract to a shared file only once two or more consumers need the same
helper, keep spec/test-specific logic local otherwise.

- `org.ost.integrationtests.support.RepositoryTestSupport` — a `@TestConfiguration` bean bag: adds
  `@EnableAutoConfiguration` + `@EnableJdbcAuditing` (needed because `@SpringBootTest(classes =
  {...@AutoConfiguration classes...})` does not itself trigger Spring Boot's autoconfiguration
  cascade — `JdbcClient`, `DataSource`, etc. only appear once `@EnableAutoConfiguration` is present
  among the loaded classes), the `MutableAuditorAware` bean, and empty `ComponentFactory<AuditPort>`
  / `ComponentFactory<AttachmentPort>` beans (representing "audit/attachment starter absent from
  the test classpath", the same shape `AdvertisementService` sees in production when those optional
  starters aren't installed — not a stub). A test that needs a *different* optional port (e.g.
  `TaxonPort`) declares its own extra `ComponentFactory` bean locally — this class only covers the
  ports every repository test has hit so far.
- `org.ost.integrationtests.support.TestDataCleaner.cleanAll(jdbcClient)` — deletes every row from
  every table this module currently knows about, across every domain, in one FK-safe order. Every
  `@SpringBootTest` reuses its cached ApplicationContext (and database) across all test methods in
  the class, so without this, later tests see earlier tests' leftover rows — call it from
  `@BeforeEach` before creating fixture data. **Always use `cleanAll`, not the lower-level
  `cleanTables(jdbcClient, "table1", ...)` overload, in `*RepositoryTest` classes** — every
  `*RepositoryTest` shares one physical singleton container for the whole `mvn test` run (ADR-002),
  so a class that only cleans its own domain's tables can fail on a foreign-key violation left
  behind by a *different* domain's test class that ran earlier (confirmed directly:
  `AdvertisementRepositoryTest`'s last test method leaves an `advertisement` row referencing
  `user_information`, which broke `UserRepositoryTest`'s narrower cleanup before `cleanAll`
  existed). Add new tables to `cleanAll` (in FK-safe position) whenever a new domain's
  `*RepositoryTest` is added.
- Both live under `src/main/java` (not `src/test/java`) so `*RepositoryTest` classes in
  `src/test/java` can import them without a test-jar dependency — same placement rationale as
  `AbstractPostgresIntegrationTest`/`UserTestFixtures`.

Usage in `AdvertisementRepositoryTest`:
```java
@SpringBootTest(classes = {
        AdvertisementAutoConfiguration.class,
        UserAutoConfiguration.class,
        RepositoryTestSupport.class
})
class AdvertisementRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired private RepositoryTestSupport.MutableAuditorAware auditorAware;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabaseAndCreateActor() {
        TestDataCleaner.cleanAll(jdbcClient);
        User actor = UserTestFixtures.createTestUser(userRepository, "Test Actor", "actor-" + UUID.randomUUID() + "@example.com");
        auditorAware.setCurrentUserId(actor.getId());
    }
}
```

---

## Key constraints

- Requires a reachable Docker daemon at test time — see `scripts/CLAUDE.md` "Unit / Testcontainers
  Tests" for the Docker-in-Docker constraint (never runs inside `deploy.sh`'s image build, only
  via a direct `mvn test`).
- The Postgres image tag is sourced from the repo-root `.env` (`POSTGRES_IMAGE`), the same value
  `scripts/infra/docker-compose.db.yml` reads natively — no hardcoded duplication, though the two
  are never the same running container (Testcontainers always starts its own ephemeral instance,
  never reuses the persistent dev one). `deploy.sh`'s own `docker pull`/`docker run` for Postgres
  still hardcode the tag separately — tracked as a follow-up in
  `backlog/issues/improvement-044-shared-env-config-consolidation.md`, out of scope here.
- Data isolation across `*RepositoryTest` classes sharing the one physical container: each
  applies its own starter's real Liquibase changelog against the shared container/database:
  Postgres allows multiple independent tables per database, so distinct starters' schemas
  coexist without collision as long as table names don't clash (they don't, today).
- `PostgresContainerSmokeTest` (Batch 0) proves only the container-starts +
  changelog-applies mechanics, using the plain `liquibase.Liquibase` API directly — not
  `@SpringBootTest`, since it only needs the container and Liquibase, not a Spring context.
- `INTEGRATION_TESTS_POSTGRES_FIXED_PORT` / `TESTCONTAINERS_RYUK_DISABLED` env vars: sandbox-only
  workarounds for this specific claude-dev environment's Docker networking limitations (dynamic
  Testcontainers ports aren't reachable here, only statically-published ones). Unset on a normal
  developer machine — Testcontainers' defaults just work there. See `scripts/CLAUDE.md`.
- **`run.sh` auto-detects starter staleness — no manual flag needed.** `integration-tests`
  depends on `platform-commons`/`advertisement`/`user`/`taxon`/`audit`/`attachment-spring-boot-starter`
  as real compiled JARs from `~/.m2`, not source, so Maven's `-am` ("also-make") reactor rebuild is
  only actually needed when one of those changed. By default `run.sh` compares each of those modules' newest
  `.java` file against its installed `~/.m2` JAR's mtime; if any source is newer (or the JAR is
  missing), it runs a targeted `mvn install -DskipTests` for just those modules first, then always
  tests via `mvn -pl integration-tests test` (no `-am`) — measured ~1:47-3:35 total for a single
  test class when nothing needed reinstalling, vs. 3-7 min walking the full 9-module reactor every
  time (~100s of "nothing to compile" Maven plugin overhead even when nothing changed). Confirmed
  the detection actually triggers a reinstall when a starter file changes, not just when nothing
  changed. `run.sh --no-check` bypasses the check entirely (test against whatever's in `~/.m2`
  right now, even if stale — for deliberately reproducing behavior against an older build, not for
  normal iteration). See `DECISIONS.md` ADR-007.
