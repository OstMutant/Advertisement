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
leaf node with zero inbound edges — see `docs/architecture-map.html`'s Module Dependencies page). This does
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
this whole test layer exists for.

**Decision:** `AbstractPostgresIntegrationTest` starts exactly one `PostgreSQLContainer` via a
plain static field + static initializer block (not `@Testcontainers`/`@Container`, which are
per-class) — Testcontainers' documented "singleton container" pattern. Every `*RepositoryTest`
class extends it and shares the one running container for the entire `mvn test` reactor
invocation; Ryuk (when not disabled) tears it down at JVM exit.

**Consequences:**
- Container startup cost is paid once per `mvn test` run, not once per test class.
- Data isolation across test classes sharing the one physical container is achieved by each test
  applying its own starter's real Liquibase changelog (distinct table names, no collision today)
  plus per-class `@BeforeEach` cleanup (see ADR-006) — not by separate containers/databases.
- `@ServiceConnection` (`spring-boot-testcontainers`) wires the `DataSource` for every
  `@SpringBootTest`-based test against this one container automatically — no manual
  `@DynamicPropertySource`.

---

## ADR-006: Reusable test "steps" — `RepositoryTestSupport` / `RepositoryTestAutoConfig` / `TestDataCleaner`
**Status:** Accepted

**Context:** Every `*RepositoryTest` needs the same `@TestConfiguration` bean bag (Spring Boot
autoconfiguration for `JdbcClient`/`DataSource`/Liquibase, a `MutableAuditorAware`, empty
`ComponentFactory<AuditPort>`/`ComponentFactory<AttachmentPort>`/`ComponentFactory<AdvertisementPort>`
beans representing "starter absent from the classpath") and a per-test DB-cleanup routine. Written
inline the first time (`AdvertisementRepositoryTest`), this boilerplate was about to be re-typed
verbatim in every future `*RepositoryTest`. A first cut used `@EnableAutoConfiguration`, which pulls
in every `@AutoConfiguration` class found anywhere on the classpath, not just what a given test
declares — a real problem since `integration-tests`' classpath keeps growing as more starter
dependencies are added: adding a new starter dependency silently broke unrelated tests' Spring
contexts by pulling in that starter's autoconfiguration for every test using the shared config,
regardless of whether that test asked for it.

**Decision:** By direct analogy with Playwright's `_flows/*.flow.js` convention (`playwright/
CLAUDE.md`: extract a shared helper only once two or more consumers need it), extracted to
`org.ost.integrationtests.support` (in `src/main`, not `src/test`, so `*RepositoryTest` classes can
import without a test-jar dependency):
- `RepositoryTestAutoConfig` — a composed `@ImportAutoConfiguration` meta-annotation carrying an
  explicit allow-list (`DataSourceAutoConfiguration`, `DataSourceTransactionManagerAutoConfiguration`,
  `JdbcClientAutoConfiguration`, `JdbcTemplateAutoConfiguration`, `DataJdbcRepositoriesAutoConfiguration`,
  `LiquibaseAutoConfiguration`, `TransactionAutoConfiguration`, `ConfigurationPropertiesAutoConfiguration`)
  instead of `@EnableAutoConfiguration`'s classpath-wide pull-in. Domain-starter autoconfiguration
  (`AdvertisementAutoConfiguration`, `TaxonAutoConfiguration`, ...) is never in this list — those are
  always passed explicitly via each test's own `@SpringBootTest(classes = {...})`, the entire point
  being that nothing is ever pulled in by implication: a new starter dependency in `pom.xml` can no
  longer silently affect any existing test's Spring context. This is the sole source of truth for the
  allow-list — every `*RepositoryTest`/`*TransactionTest` (whether it uses `RepositoryTestSupport`
  directly or declares its own local `TestConfig` for extra beans) applies `@RepositoryTestAutoConfig`
  rather than redeclaring the list.
- `RepositoryTestSupport` — the `@TestConfiguration` bean bag, layering `@RepositoryTestAutoConfig` +
  `@EnableJdbcAuditing` + `MutableAuditorAware` + the empty `ComponentFactory<...>` beans on top,
  added to a test's `@SpringBootTest(classes = {...})` list. A test that needs a *different* optional
  port (e.g. `TaxonPort`) declares its own extra `ComponentFactory` bean locally — this class only
  covers the ports every repository test has hit so far, not meant to grow into a bean bag for every
  possible port.
- `TestDataCleaner.cleanTables(jdbcClient, "table1", "table2", ...)` — FK-ordered row deletion, the
  lower-level overload; `cleanAll(jdbcClient)` wraps it with the full FK-safe table list and is what
  `*RepositoryTest` classes should actually call from `@BeforeEach` (see `integration-tests/CLAUDE.md`'s
  "Always use `cleanAll`" note). Catches and swallows Postgres "undefined table" (SQLSTATE `42P01`)
  in `cleanTables()` — the singleton-container design (ADR-002) means a test class can legitimately
  run before some other, later-running test class has applied its own domain's Liquibase changelog,
  so "nothing to clean because the schema doesn't exist yet" is an expected state, not an error.

**Consequences:**
- Full usage example: `integration-tests/CLAUDE.md` "Reusable test support (steps/blocks)".
- Any *new* `@SpringBootTest`-based test class in this module that needs Boot infrastructure beyond
  what's in `RepositoryTestAutoConfig`'s list must extend the list explicitly, not reach for
  `@EnableAutoConfiguration` as a shortcut — that would silently reintroduce the fragility this
  design removes.

---

## ADR-007: `run.sh` auto-detects starter staleness instead of a manual skip-`-am` flag
**Status:** Accepted

**Context:** `run.sh` always ran `./mvnw -pl integration-tests -am test` — `-am` ("also-make")
rebuilds every module `integration-tests` depends on (`platform-commons`, `advertisement`/`user`/
`taxon`/`audit`/`attachment-spring-boot-starter`) on every single invocation, even when none of
them changed. Measured directly in this sandbox: ~100s of pure "nothing to compile" Maven plugin
overhead walking through those reactor modules, on top of the actual test run — meaning
re-running a test after only editing a test file inside `integration-tests` itself (the common
case while writing/fixing tests) paid the same cost as a full rebuild. Root cause: `mvn test`
(unlike `mvn install`) never publishes built artifacts to `~/.m2/repository`, so without `-am`,
Maven has no JAR to resolve `integration-tests`' starter dependencies from at all. A manual opt-in
flag to drop `-am` was considered and rejected: its safety would depend on the developer
remembering to stop using it right after editing a starter's own source, or it would silently test
against a stale `~/.m2` JAR — the kind of footgun this project avoids elsewhere (e.g. the reason
`UserEditableFields` exists as a narrower entity instead of relying on builder discipline — see
`user-spring-boot-starter/CLAUDE.md`).

**Decision:** `run.sh` compares each starter module's newest `.java` file (`find <module>/src/main
-name '*.java' -newer <installed-jar>`) against its installed `~/.m2` JAR's mtime before every run.
If any source is newer than its JAR (or the JAR doesn't exist yet), it runs a targeted `mvn install
-DskipTests` for just those modules first; otherwise it skips straight to `mvn -pl
integration-tests test` (no `-am`) — no flag required for the common case. Confirmed directly, both
directions: (a) an unmodified checkout correctly skips the reinstall and runs fast, (b) touching a
starter `.java` file is correctly detected and triggers a targeted reinstall before the test runs.
A `--no-check` flag bypasses the check entirely (e.g. reproducing behavior against a specific
already-built JAR) — never for normal edit/test iteration, since it reintroduces the exact
stale-JAR risk the auto-detection exists to close.

**Consequences:**
- No developer memory required for the default path — the check runs every time, correctness is
  automatic, not opt-in.
- The `find -newer` check itself costs a fraction of a second per module — negligible next to the
  ~100s of Maven reactor-walk overhead it replaces.
- `--no-check` inherits the dropped-`-am` risk profile explicitly, by name, only when intentionally
  invoked — never the default.
- Unifying all `*RepositoryTest` classes under one shared `@SpringBootTest(classes = {...})`
  combination, so Spring's own ApplicationContext cache could be reused across classes within a
  single `mvn test` run, remains a separate, undecided, not-implemented idea — it carries a real
  test-isolation cost (a broken bean/changelog in any one starter would then fail every repository
  test together, not just its own domain's) that hasn't been weighed against the bootstrap-cost
  savings for the module's current size.

---

## ADR-008: Test package-private/private internal logic through its public entry point, never through a same-package trick or a widened production visibility
**Status:** Accepted

**Context:** `DefaultTaxonPort.resolveTranslation()` is package-private —
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
- **Applies directly to any future test of `UserService.applyUserRestore()`** — that method
  is `private` (stricter than `resolveTranslation()`'s package-private), called only from the
  public `UserService.restoreToSnapshot()`; the same shape of test (call `restoreToSnapshot()`,
  assert on the result, use repository-level fixture setup for any state the public API can't
  reach) applies when that item is implemented — do not reach for `private`-access workarounds
  there either.

---

## ADR-010: `@Tag("testcontainers")` on the shared base class + Surefire `excludedGroups`; `SharedEnvConfig` gains a testable overload

**Status:** Accepted

**Context:** A plain `mvn install`/`mvn test` from the repo root — a normal thing a new
contributor or a future CI pipeline would run — silently required a reachable Docker daemon,
because every Testcontainers-backed test in this module ran unconditionally. If Docker wasn't
running, the failure surfaced deep inside Testcontainers' own connection probing instead of a
clear message. Also `SharedEnvConfig` (the repo-root `.env` reader `AbstractPostgresIntegrationTest`
depends on) had zero test coverage of its own walk-up/missing-file logic.

**Decision:**
- `@Tag("testcontainers")` placed once on `AbstractPostgresIntegrationTest` — JUnit 5 tags declared
  on a superclass are inherited by every subclass, so all 12 Docker-backed `*RepositoryTest`/
  `*ServiceTest` classes (and `PostgresContainerSmokeTest`) got tagged with zero per-class edits,
  and any *future* class extending this base is tagged automatically — nothing to remember.
- `integration-tests/pom.xml` gained a `<surefire.excludedGroups>testcontainers</surefire.excludedGroups>`
  property, wired into `maven-surefire-plugin`'s `<excludedGroups>`. A plain `mvn test`/`mvn install`
  now skips every Docker-backed class by default and touches no Docker socket at all — confirmed
  directly: `./mvnw -pl integration-tests test` with no flags ran only the 9 Docker-free classes
  (41 tests) in 1:23, no container-start log lines anywhere.
- `integration-tests/run.sh` — the sanctioned way to run these tests — passes
  `-Dsurefire.excludedGroups=` (blank, overriding the pom's default) unconditionally, so it keeps
  running the full suite (Docker-backed + Docker-free together) exactly as before this change;
  confirmed via `bash scripts/integration-tests.sh --sandbox` (no scenario), 88/88 green.
- `run.sh` also gained a Docker daemon precheck (`docker info` before invoking `mvn`, clear message
  + exit 1 on failure) and a CI-environment guard (fails fast if `GITHUB_ACTIONS` is set alongside
  `--sandbox` or the sandbox-only `TESTCONTAINERS_RYUK_DISABLED`/`INTEGRATION_TESTS_POSTGRES_FIXED_PORT`
  env vars — these are this claude-dev sandbox's own Docker-networking workarounds, never correct
  on a real CI runner).
- New `SharedEnvConfigTest` (`org.ost.integrationtests`, no `@Tag` — runs under a plain `mvn test`)
  covers: `.env` found in the start directory, found after walking up to a parent, missing
  entirely (`IllegalStateException`), and present but missing the requested key
  (`IllegalStateException` naming the key).

**A real dead end hit during implementation:** the first version of `SharedEnvConfigTest` tried to
simulate different working directories by reassigning the `user.dir` system property before each
call to `SharedEnvConfig.require(String)`. All 4 tests failed — `SharedEnvConfig`'s
`new File("").getAbsoluteFile()` kept resolving the *real* repo-root `.env`
(`POSTGRES_IMAGE=postgres:15-alpine`) regardless of what `user.dir` was set to, confirming directly
that `java.io.File`'s relative-path resolution does not dynamically re-read `user.dir` on this JDK
(a commonly cited "trick" that turned out not to hold here). Fixed by giving `SharedEnvConfig` a
second, package-visible entry point, `require(String key, File startDir)`, with the original
`require(String key)` becoming a one-line delegation to it using the real
`new File("").getAbsoluteFile()`. The test calls the two-arg overload directly against isolated
`@TempDir` trees instead of fighting the JVM's real working directory.

**Why this doesn't repeat ADR-008's rejected pattern (widening visibility for test convenience):**
ADR-008 rejected widening a *starter's* internal method (`DefaultTaxonPort.resolveTranslation()`)
because that starter is a real, shipped production module — widening its surface area for a test
is a production-code change made *for* a test. `SharedEnvConfig` is different in kind: it already
lives in `integration-tests/src/main/java`, not a starter, specifically so this module's own
`src/test/java` can consume it directly (the same placement rationale already documented for
`AbstractPostgresIntegrationTest`/`RepositoryTestSupport`/`TestDataCleaner` — see "What it owns"
above). `integration-tests` is never shipped or deployed (ADR-001), so there is no external
production surface being widened — only this module's own internal test-support plumbing, for
consumption by this module's own tests. ADR-008's actual instruction — "test the *behavior* through
the public entry point" — is still honored: `require(String key)` (the one production callers use)
is unchanged and delegates straight into the tested overload; no behavior was special-cased for
tests.

**Consequences:**
- New contributor / future CI running a bare `mvn test`/`mvn install` from the repo root no longer
  needs Docker at all, and gets a fast, safe sanity build.
- `integration-tests/run.sh` continues to be the only sanctioned way to run the Docker-backed
  suite — unaffected in behavior, verified 88/88 green.
- Any future `*RepositoryTest`/`*ServiceTest` extending `AbstractPostgresIntegrationTest` is tagged
  automatically; no new discipline required per class.
- Doc note added to `integration-tests/CLAUDE.md` (near the `SharedEnvConfig` description):
  the repo-root `.env` is intentionally committed and must only ever hold non-secret, dev-only
  values.
- Explicitly out of scope (per the originating issue): a GitHub Actions workflow itself — this
  repo still has no `.github/workflows/`; the CI-environment guard added here only protects a
  *future* one from a specific copy-paste mistake, it doesn't introduce CI.
