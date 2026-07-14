# improvement-027: Add a fast unit/Testcontainers test layer — zero coverage outside query-lib, needed before any new starter (esp. payments)

**Type:** improvement — testing infrastructure/process. Expands
`features/process-improvements.md` Part 1 item 3 ("Add a unit/integration test layer") with a
concrete, verified current-state audit and a scoping decision tied to the private roadmap.
**Module:** `integration-tests` only — the new module (see Batch 0 below) that owns every test and
fixture this issue adds, depending on whichever domain starters (`advertisement-spring-boot-
starter`, `user-spring-boot-starter`, ..., `platform-commons`) it needs to test. No domain
starter's own `pom.xml`/`src/test/java` changes — see "Batch 1 resolution" and
`integration-tests/CLAUDE.md` for why.
**Priority:** medium-high — already recorded as a **hard gate** for `F-08` (payments) in the
private roadmap; currently the pure-logic code most worth testing (snapshot diffs, sanitizer,
translation resolution) has zero coverage, and every SQL correctness signal comes only from the
~10-minute Playwright suite
**When:** independent, no blockers — recommended **now**, not deferred to immediately before
F-08, since Phase 1-3 features (F-01…F-07, ~8 weeks) and a brand-new `review-spring-boot-starter`
(F-06) all benefit from a fast inner test loop, and a new starter is cheaper to build with tests
from day one than to retrofit later

## Problem

`features/process-improvements.md` already identified this gap on 2026-07-04 ("the whole project
has **2** JUnit test files, both in query-lib") but the item was never executed — verified still
true today: `find . -name "*Test.java"` returns exactly `query-lib/src/test/java/org/ost/query/
filter/SqlConditionTest.java` and `SqlOperatorTest.java`, nothing else, anywhere.

This was raised again during a review of the codebase's overall quality, initially narrowed to
"add a unit test for `diff()`" — investigating further (this issue) surfaced that the gap is
broader and that **the test-dependency scaffolding itself is missing in most modules**, not just
the test files:

| Module | Has `spring-boot-starter-test`? |
|---|---|
| `query-lib` | yes |
| `marketplace-app` | yes |
| `taxon-spring-boot-starter` | yes |
| `platform-commons` | **no** |
| `advertisement-spring-boot-starter` | **no** |
| `audit-spring-boot-starter` | **no** |
| `attachment-spring-boot-starter` | **no** |
| `user-spring-boot-starter` | **no** |

No module anywhere has a Testcontainers dependency. `platform-commons` — where every
`AuditableSnapshot.diff()` implementation actually lives — cannot currently host a single JUnit
test without a pom change first.

### Concrete, verified candidates (not hypothetical)

**Pure logic, no DB, no Spring context needed:**
- `AdvertisementSnapshotDto.diff()` / `TaxonSnapshotDto.diff()` / `UserSnapshotDto.diff()` /
  `SettingsSnapshotDto.diff()` (`platform-commons/**/dto/`) — read `AdvertisementSnapshotDto
  .diff()` directly: it's `Objects.equals()` field comparisons building `ChangeEntry.FieldChange`
  records, zero side effects, trivially testable by constructing two DTOs and asserting the
  returned list.
- `DefaultTaxonPort.resolveTranslation()` (`taxon-spring-boot-starter/services/`) — a three-step
  locale fallback chain (`requested locale → configured default locale → first available
  translation → null`), currently exercised only incidentally through Playwright's UK/EN locale
  switching tests, never directly.
- `AdvertisementService.sanitizeHtml()` (`advertisement-spring-boot-starter`) — the OWASP
  sanitizer + Jsoup visible-text-length policy from ADR-024/ADR-031 (two distinct limits,
  `DESCRIPTION_MAX_LENGTH` vs `DESCRIPTION_RAW_MAX_LENGTH`) — exactly the kind of two-number
  policy that is easy to silently break in a future edit and hard to notice via UI alone.

**Repository/SQL correctness, needs a real Postgres (Testcontainers):**
- `AdvertisementRepository`, `AuditLogRepository`, `TaxonAssignmentRepository`,
  `AttachmentRepository` — all hand-written SQL via `JdbcClient`, using `query-lib`'s
  `SqlFilterBuilder`/`OrderByBuilder` for dynamic filter/sort. This is exactly the code class
  where a typo or a wrong parameter binding produces a query that still compiles and still
  "looks right" in a diff, but returns wrong rows — the same class of risk called out for
  `payment`/`subscription` tables in F-08/F-09 (state machines, idempotent webhook processing)
  before any money-moving code exists.

### Why now, not "right before F-08"

- `F-08-payments-promoted-listings.md` (private roadmap) already states: *"Hard gate:
  Testcontainers test layer (process-improvements P1 #4) must exist before this starter —
  money-moving code does not ship untested."* This issue is that gate, made concrete.
- `F-06-reviews-ratings.md` is a **new** starter (`review-spring-boot-starter`) landing in Phase 3,
  well before F-08 — establishing the test pattern now means the review starter is built with
  tests from its first commit, not retrofitted after the fact like `advertisement`/`audit`/
  `attachment`/`user`/`taxon` would otherwise need to be.
- Phases 1-3 (F-01 through F-07, ~8 weeks of roadmap work) currently get zero regression signal
  faster than an ~10-minute Playwright run. A same-day-established test layer pays for itself
  across all of that work, not just the payments phase.
- **Explicit scope boundary:** this layer does **not** replace Playwright and would **not** have
  caught either of the two "looked correct, broke in production" incidents from this session
  (deny-by-default routing, `@ConditionalOnBean` bean-registration ordering) — both required a
  real Spring context boot under real HTTP traffic. It catches a different, cheaper-to-verify
  class of bug (SQL correctness, pure-logic regressions) faster and earlier than e2e does; it is
  an addition to the safety net, not a substitute for the full e2e run before any deploy.

## Environment notes (clarified 2026-07-14)

**What Testcontainers actually is, and why plain JUnit/mocks/H2 aren't enough:** Testcontainers is
a Java library that programmatically starts a real Docker container (Postgres, in this case) for
the duration of a test run, applies the module's own Liquibase changelog against it, and tears it
down afterward — isolated, reproducible, no shared state between runs. Plain JUnit itself knows
nothing about databases. The alternatives all fall short for this codebase specifically:
mocking the repository tests the code *around* the SQL, not the SQL itself; an in-memory DB like
H2 has a different dialect than the Postgres-specific features this project actually uses
(`ROW_NUMBER() OVER (PARTITION BY ...)`, `= ANY(:array)`, `ILIKE`, the JSONB `settings` column) —
a green H2 test would be a false positive if the real Postgres behavior differs; a shared dev
Postgres container is fragile and not test-isolated. Concrete illustration from this session: the
`Set<Long>` vs `Long[]` binding bug in `AttachmentRepository.loadMediaStats()` (bound directly to
`= ANY(:entityIds)` instead of converting to an array first) is exactly the class of bug a
Testcontainers repository test would have caught immediately, as a normal assertion failure at
write time — instead it silently hung the advertisement save flow and was only found ~20 minutes
later via a full Playwright run.

**Docker-in-Docker constraint — Testcontainers tests must run outside `deploy.sh`.** Confirmed in
this environment: the Docker daemon is directly reachable (`/var/run/docker.sock` mounted,
`docker version` responds) and `mvn`/`./mvnw` are both present, so Testcontainers works fine when
`mvn test` is run **directly** in this environment. It does **not** work inside the actual deploy
pipeline: `scripts/deploy.sh`/`deploy-dev.sh` build Maven inside a `docker build` stage
(multi-stage `Dockerfile`), which already runs `./mvnw install -DskipTests` — tests are skipped
there today, and even if they weren't, that build stage has no access to the outer Docker socket
(standard Docker-in-Docker isolation; no socket mount is configured for the `builder` stage). Any
Testcontainers tests added by this issue must run via a separate, direct `mvn clean test`
invocation — already the sanctioned pattern in `.claude/rules.md` — never bundled into the image
build.

**What's shared with the existing dev/Playwright Postgres infra, and what isn't.** Shared: the
Docker daemon itself, the `postgres:15-alpine` image (already cached locally — `deploy.sh` output
already shows "Image postgres:15-alpine already present" — no extra pull), and each module's own
Liquibase changelog (Testcontainers tests apply the exact same schema-defining XML the app runs in
production, no separate test-only schema to maintain). **Not** shared: the actual running
`advertisement-db` container from `scripts/infra/docker-compose.db.yml` — Testcontainers always
spins up its own separate, ephemeral Postgres instance per test run (or per shared/singleton
container within one `mvn test` invocation, see Batch 0), deliberately not reusing the persistent
dev container, to keep tests isolated from whatever data happens to be sitting in dev at the time.

**Dynamic-port network reachability — a real sandbox-specific limitation, discovered building
Batch 0.** In this claude-dev sandbox, Testcontainers can create a container via the Docker socket
successfully (confirmed via logs — container starts, JDBC URL computed), but the test JVM cannot
reach the container's *dynamically assigned* published port (`Connection refused`, tried both the
auto-detected Docker host IP and `TESTCONTAINERS_HOST_OVERRIDE=localhost`). A direct TCP probe
confirmed the *static* port already published by `advertisement-db` (`5432`, from
`docker-compose.db.yml`) **is** reachable from this same shell — so the sandbox's networking isn't
universally broken, only dynamic/random high ports specifically aren't forwarded. This is the same
underlying class of problem as the already-documented `playwright/CLAUDE.md` note ("Volume mounts
don't work from inside the claude container") — a Docker Desktop / socket-proxy mismatch between
this sandbox and the actual container network, not a bug in our code or in Testcontainers.

Root cause not fixable from within this repo (it's sandbox infrastructure, not application code).
Workaround: `AbstractPostgresIntegrationTest` reads an optional `INTEGRATION_TESTS_POSTGRES_FIXED_PORT`
environment variable; when set, it forces a fixed host-port binding via
`PostgreSQLContainer.setPortBindings()` instead of Testcontainers' normal random-port assignment.
Unset (the default, e.g. on a real developer machine with normal Docker networking), behavior is
unchanged — Testcontainers picks a random port as designed. This is an env-gated accommodation for
a confirmed external constraint, not a hidden workaround for a code defect — same shape as `docker
cp` replacing `-v` volume mounts elsewhere in this project for the identical class of sandbox
limitation.

**Does a fixed port conflict with parallel test execution?** No, for the case that actually
matters here. The singleton-container pattern means there is exactly **one** container per
`mvn test` reactor run regardless of how many test classes/methods use it — concurrent test
methods within that one JVM all hit the same container over the same port, which is normal
concurrent Postgres access (the same pattern Playwright's own `signUpBulkParallel(browser, users,
poolSize)` already relies on: multiple browser sessions concurrently hitting the one
`advertisement-db`, not one database per session). The real conflict risk is **cross-process**: two
separate `mvn test` invocations racing to bind the identical fixed host port simultaneously — not
a concern today (everything in this project runs one script/process at a time), but worth
revisiting when improvement-028 (CI pipeline) introduces parallel CI jobs. A real CI runner
(GitHub Actions, not this sandbox) likely has normal Docker networking and wouldn't need this
workaround at all; if it somehow does, the fix at that point is a CI-job-index-derived port, not a
single hardcoded one.

## Batch 0 resolution (2026-07-14)

Batch 0 landed with the design discussed above, plus two things found while building it:

1. **`SharedEnvConfig` — no hardcoded `postgres:15-alpine` duplication.** The initial
   `AbstractPostgresIntegrationTest` draft hardcoded `"postgres:15-alpine"` as a Java string
   literal, independently of the identical hardcoded value in
   `scripts/infra/docker-compose.db.yml` — exactly the kind of two-places-to-update-in-sync risk
   this whole issue is about avoiding for SQL/logic bugs, just applied to config instead. Fixed by
   adding a repo-root `.env` (`POSTGRES_IMAGE=postgres:15-alpine`) that both consumers read: Docker
   Compose natively, and a new `SharedEnvConfig.require(key)` helper in `integration-tests` that walks
   up from the JVM's working directory (bounded, 5 parent levels) to find `.env` — resolves
   correctly whether `mvn test` is launched from the repo root, a module subdirectory, or an IDE
   test runner (IntelliJ commonly sets the working directory to the module, not the reactor root,
   when running a single test via the gutter icon). Fails fast with a clear message if `.env` or
   the key isn't found — no silent default. Neither Docker Compose's `.env` auto-load nor this
   upward-search is AI-specific tooling; both work identically for a human running the same
   commands from a terminal or IDE. `deploy.sh`'s own separate `docker pull`/`docker run`
   references to `postgres:15-alpine` were **not** touched here — see
   [improvement-044](improvement-044-shared-env-config-consolidation.md), filed to cover that plus
   the much larger DB/MinIO credential duplication found while investigating this.
2. **Fixed a real bug in `PostgresContainerSmokeTest` itself**, unrelated to the environment: the
   original draft wrapped the `Liquibase` instance in `try-with-resources`, whose `close()` closes
   the underlying `Database` — and with it, the same JDBC `Connection` the test reused afterward
   for its verification query (`PSQLException: This connection has been closed`). Fixed by not
   closing the `Liquibase` instance at all; the outer `Connection`'s own try-with-resources closes
   everything at the end.
3. **`docker compose`'s `.env` auto-load does NOT use the repo root by default when `-f` points
   elsewhere — a real bug the `.env` change introduced, caught by empirical testing, not
   assumption.** Compose's default "project directory" (where it looks for `.env`) is the
   directory containing the first `-f` file, not the invoking shell's working directory. Since
   `scripts/infra/docker-compose.db.yml` lives in `scripts/infra/`, not the repo root,
   `docker compose -f scripts/infra/docker-compose.db.yml ...` — exactly the form both this file's
   own header comment and `scripts/database/reset.sh` used — silently resolved
   `${POSTGRES_IMAGE}` to an empty string ("the POSTGRES_IMAGE variable is not set. Defaulting to
   a blank string", then a hard "service db has neither an image nor a build context specified"
   error). Confirmed by installing the `docker-compose` CLI plugin in this sandbox (it was missing
   entirely — only `docker-buildx` had been installed previously — same official-binary-to-
   `~/.docker/cli-plugins/` install pattern documented in `scripts/CLAUDE.md` for buildx) and
   running `docker compose -f scripts/infra/docker-compose.db.yml config` directly: reproduced the
   blank-image error, then confirmed `docker compose --project-directory . -f
   scripts/infra/docker-compose.db.yml config` resolves `image: postgres:15-alpine` correctly.
   This isn't a sandbox quirk — it's documented, version-independent Compose behavior, so the fix
   (always pass `--project-directory .`/`--project-directory "$ROOT"` when the `-f` file isn't at
   the repo root) is correct on any machine, including a real Windows/Docker Desktop dev machine,
   not just this sandbox. Fixed in `scripts/database/reset.sh` (the one script that actually
   invokes this compose file) and the header-comment example commands in both
   `docker-compose.db.yml` and `docker-compose.app.yml`.

Verified end-to-end in this sandbox with
`TESTCONTAINERS_RYUK_DISABLED=true INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432 mvn -pl integration-tests test`
— container starts, changelog applies, verification query passes.

## Batch 1 resolution — Testcontainers exemplar (2026-07-14)

`AdvertisementRepositoryTest` (7 test methods: find-by-id, title filter, empty filter, sort,
pagination, both optimistic-locking paths on `softDelete`) passes end-to-end against a real
Postgres via Testcontainers — `mvn -pl integration-tests -am test -Dtest=AdvertisementRepositoryTest`,
`Tests run: 7, Failures: 0, Errors: 0`. Two things found while getting the Spring context to boot,
beyond the sandbox-specific issues already covered in "Batch 0 resolution":

1. **`@SpringBootTest(classes = {...explicit @AutoConfiguration classes...})` does not itself
   trigger Spring Boot's autoconfiguration cascade.** Passing `AdvertisementAutoConfiguration` and
   `UserAutoConfiguration` directly as "primary sources" does not import `JdbcClientAutoConfiguration`,
   `DataSourceAutoConfiguration`, etc. — those only activate once `@EnableAutoConfiguration` is
   itself among the loaded classes. Fixed by adding `@EnableAutoConfiguration` to the test's own
   support config (see `RepositoryTestSupport` below).
2. **`UserAutoConfiguration.securityContextRepository()` requires `jakarta.servlet.http
   .HttpServletResponse` on the classpath** (`HttpSessionSecurityContextRepository`'s constructor
   references it directly) — `NoClassDefFoundError` at bean-instantiation time, since
   `integration-tests` has no web/servlet dependency. Fixed by adding `jakarta.servlet:jakarta
   .servlet-api` as a `provided`-scope dependency in `integration-tests/pom.xml` (version managed by
   the inherited `spring-boot-starter-parent` BOM) — this satisfies class loading without pulling in
   an actual servlet container; the bean is never invoked in a repository test, only constructed.

**Reusable "steps" extracted, by analogy with Playwright's `_flows/*.flow.js`** (explicitly
requested so Batch 3's `AuditLogRepositoryTest`/`TaxonAssignmentRepositoryTest`/
`AttachmentRepositoryTest` don't redo this from scratch): `org.ost.integrationtests.support
.RepositoryTestSupport` (the `@TestConfiguration` bean bag — `@EnableAutoConfiguration` +
`@EnableJdbcAuditing`, `MutableAuditorAware`, empty `ComponentFactory<AuditPort>`/
`ComponentFactory<AttachmentPort>` beans) and `org.ost.integrationtests.support.TestDataCleaner
.cleanTables(jdbcClient, "table1", "table2", ...)` (FK-ordered row cleanup between test methods
sharing one cached `@SpringBootTest` context). Full usage example and rationale in
`integration-tests/CLAUDE.md` "Reusable test support (steps/blocks)".

**Plain-unit-test exemplar done too (2026-07-14).** `AdvertisementSnapshotDtoTest` (9 test methods:
no-previous diff, identical snapshots, single-field changes for each of the three fields,
multi-field diff, category-id sort-order normalization in the record's compact constructor) — no
Spring context, no DB, `org.ost.integrationtests.advertisement.AdvertisementSnapshotDtoTest`.
Caught one real assumption bug while writing it: `diff(null)` does **not** return an empty list —
it returns a `FieldChange` per set field with `from=null` (the "everything just got created" shape,
used for the initial creation diff), not "no previous means no changes". The first test draft
assumed the latter and failed against the real implementation; fixed the test's expectation, not
the code.

Batch 1 is now fully complete. Full `integration-tests` module run (`bash scripts/integration-tests.sh
--sandbox`, no scenario filter): **17/17 passing** across `AdvertisementRepositoryTest` (7),
`AdvertisementSnapshotDtoTest` (9), `PostgresContainerSmokeTest` (1), `BUILD SUCCESS`.

## Suggested fix

Exactly the two-layer split `process-improvements.md` already specified, now with concrete scope:

1. **Testcontainers (PostgreSQL) repository tests** for the four repositories listed above, one
   test class per repository, following the existing `query-lib` test style (JUnit 5 + AssertJ,
   already available via `spring-boot-starter-test`, already used in `SqlConditionTest`/
   `SqlOperatorTest`). Use `@Testcontainers` + `@SpringBootTest` (or a slice test if one fits) with
   a real ephemeral Postgres container, apply the module's own Liquibase changelog on startup
   (same schema the app itself runs), assert on actual returned rows for a handful of filter/sort/
   pagination combinations per repository — not exhaustive, just the highest-risk dynamic-SQL
   paths (multi-filter combination, empty filter, sort by each documented column).
2. **Plain unit tests** (no Spring context, no DB) for the pure-logic candidates listed above:
   `diff()` on all four snapshot DTOs, `DefaultTaxonPort.resolveTranslation()`,
   `AdvertisementService.sanitizeHtml()`.
3. Keep Playwright e2e as the outer loop (unchanged); the new layer becomes the inner loop,
   target ~30s per `process-improvements.md`'s own estimate.

### A single `integration-tests` module owns every test — domain starters carry none (superseded design, see Batch 1 resolution below)

**Superseded (2026-07-14) — see "Batch 1 resolution" for the design actually implemented.**
Originally planned as "each repository-test-bearing starter adds `integration-tests` as a
`test`-scope dependency, and hosts its own `*RepositoryTest` in its own `src/test/java`." Revised
during Batch 1 planning: **no domain starter carries any test code for this purpose at all** — the
actual `*RepositoryTest` classes themselves, not just the shared scaffolding, live inside
`integration-tests`. `integration-tests` depends on whichever starters it needs to test (`compile`
scope, since its own sources reference those classes directly) — safe only because this module is
never shipped or deployed, so it never violates "starters must not depend on each other" (that
rule governs production runtime composability). See `integration-tests/CLAUDE.md` for the full
rationale, including why a per-starter stub schema was considered and rejected in favor of this.

`integration-tests` (sibling to `query-lib`, no Spring Boot autoconfiguration of its own for its
scaffolding classes) owns:
- The Testcontainers deps (`org.testcontainers:testcontainers-postgresql`,
  `org.testcontainers:testcontainers-junit-jupiter`, `spring-boot-testcontainers`) +
  `spring-boot-starter-test` — `compile` scope, since being a testing library/suite *is* this
  module's whole purpose.
- `AbstractPostgresIntegrationTest` — a **singleton** Postgres container shared across every test
  class in one `mvn test` reactor run, instead of paying container-startup cost per starter.
  Container startup, not test execution, is the slow part of this pattern; a shared container is
  the difference between "~30s total" (this issue's own estimate) and "~30s × N starters."
- Every `*RepositoryTest` class and shared fixture (e.g. `UserTestFixtures`), organized into
  sub-packages per domain (`org.ost.integrationtests.advertisement`, etc.).
- Data isolation across starters' schemas sharing one physical container: each test applies its
  own starter's real Liquibase changelog against the shared database — distinct table names,
  no collision expected (to be confirmed once Batch 1's `advertisement`/`user_information` pair
  actually lands).

### Dependency changes required first

- Add the new `integration-tests` module (Batch 0, done) to the root reactor `pom.xml`.
- `integration-tests/pom.xml` depends on `platform-commons` and whichever starters its tests need
  (`advertisement-spring-boot-starter`, `user-spring-boot-starter` for Batch 1; more added as
  Batches 2-3 land), plus the Testcontainers/Spring Boot test deps.
- **No other module's `pom.xml` changes** — domain starters and `platform-commons` stay untouched.

## Suggested phased execution

1. ✅ **Batch 0 — `integration-tests` module (done 2026-07-14):** module created (pom, reactor
   registration, Testcontainers deps, the shared `AbstractPostgresIntegrationTest`
   singleton-container base class + `SharedEnvConfig`), `PostgresContainerSmokeTest` proves the
   scaffolding boots a container and applies a trivial changelog successfully — see "Batch 0
   resolution" above.
2. **Batch 1 — one exemplar of each kind (design finalized 2026-07-14, see
   `integration-tests/CLAUDE.md`):** add `advertisement-spring-boot-starter` and
   `user-spring-boot-starter` as `compile`-scope dependencies of `integration-tests` itself (no
   change to either starter's own pom); write `UserTestFixtures` (shared, reusable by later
   batches), one Testcontainers repository test (`AdvertisementRepository`, the most
   filter/sort-heavy one — its FK to `user_information` is exactly why `UserTestFixtures` exists)
   and one plain unit test (`AdvertisementSnapshotDto.diff()`), all inside `integration-tests` —
   to prove the pattern end-to-end and measure actual local run time before committing to the
   "~30s" estimate.
   - ✅ `AdvertisementRepository` Testcontainers exemplar — done 2026-07-14, see "Batch 1
     resolution" above (7/7 tests passing, reusable `support` helpers extracted).
   - ✅ `AdvertisementSnapshotDto.diff()` plain unit test — done 2026-07-14 (9/9 tests passing).
   - **Batch 1 complete.**
3. **Batch 2 — remaining plain unit tests:** the other three `diff()` implementations,
   `resolveTranslation()`, `sanitizeHtml()` — all as test classes inside `integration-tests`,
   adding `taxon-spring-boot-starter` as a dependency of `integration-tests` when
   `resolveTranslation()`'s test needs it.
4. **Batch 3 — remaining Testcontainers repository tests:** `AuditLogRepository`,
   `TaxonAssignmentRepository`, `AttachmentRepository` — same pattern, adding
   `audit-spring-boot-starter`/`attachment-spring-boot-starter` to `integration-tests`' own pom as
   needed, never to the starters themselves.
5. **Ongoing:** require the next new starter (`review-spring-boot-starter`, F-06) to ship its
   repository and pure-logic tests (as new classes inside `integration-tests`, plus a new
   `compile`-scope dependency there) in the same PR as the feature, not as a follow-up.

## Required verification

- `mvn test` must actually run and pass module-by-module after Batch 1 (currently every `mvn
  compile`-only invocation in this project's own deploy scripts skips tests entirely — confirm the
  new tests are wired into the normal `mvn test`/`mvn clean package` lifecycle, not accidentally
  excluded).
- No change to Playwright scope needed — this issue adds a layer, it does not remove or replace
  any existing e2e coverage.

## Related

- `features/process-improvements.md` Part 1, item 3 — the original, still-open item this issue
  makes concrete; do not duplicate, update that item's status once this lands.
- `private/features/F-08-payments-promoted-listings.md` — the hard gate this issue satisfies.
- `private/features/F-06-reviews-ratings.md` — the next new starter that should adopt this
  pattern from its first commit.
- `marketplace-app/DECISIONS.md` ADR-024/ADR-031 — the two-limit description-length policy that
  is one of the concrete `sanitizeHtml()` unit-test candidates.
