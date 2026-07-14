# improvement-027: Add a fast unit/Testcontainers test layer — zero coverage outside query-lib, needed before any new starter (esp. payments)

**Type:** improvement — testing infrastructure/process. Expands
`features/process-improvements.md` Part 1 item 3 ("Add a unit/integration test layer") with a
concrete, verified current-state audit and a scoping decision tied to the private roadmap.
**Module:** cross-cutting — `platform-commons`, `advertisement-spring-boot-starter`,
`audit-spring-boot-starter`, `attachment-spring-boot-starter`, `user-spring-boot-starter`,
`taxon-spring-boot-starter`, `query-lib`, plus a new `test-support` module (see Batch 0 below)
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

### A shared `test-support` module, not per-starter Testcontainers setup

Rather than each of the four repository-test-bearing starters (`advertisement`, `audit`,
`attachment`, `taxon`) independently declaring Testcontainers dependencies and independently
starting its own Postgres container, add one new plain-Java module — `test-support`, sibling to
`query-lib` (same shape: no Spring Boot autoconfiguration, just a shared library, consumed by
multiple modules — the precedent `platform-commons/CLAUDE.md`'s "used by ≥2 modules" rule already
sanctions). It owns:
- The Testcontainers BOM (`org.testcontainers:testcontainers-bom`) + `org.testcontainers:postgresql`
  + `org.testcontainers:junit-jupiter`, plus `spring-boot-starter-test` — all `compile` scope here,
  since being a testing library *is* this module's whole purpose.
- One shared base class (e.g. `AbstractPostgresIntegrationTest`) holding a **singleton** `@Container`
  Postgres instance — Testcontainers' documented pattern for sharing one running container across
  every test class in a single `mvn test` reactor run, instead of paying container-startup cost
  once per starter. Container startup, not test execution, is the slow part of this pattern; a
  shared container is the difference between "~30s total" (the estimate this issue is built
  around) and "~30s × 4 starters."
- Each of the four repository-test-bearing starters adds `test-support` as a `test`-scope
  dependency (transitively pulls in JUnit/AssertJ/Testcontainers) instead of declaring the
  Testcontainers deps individually.
- Data isolation across starters sharing one physical container is an open design detail to settle
  during Batch 0 — most likely a separate database name (or schema) per starter's test suite
  against the one shared Postgres instance, so `advertisement`'s tests can't collide with
  `taxon`'s.

`platform-commons` and any starter only adding **plain unit tests** (no DB, e.g. `diff()` tests)
does not need `test-support` — it only needs `spring-boot-starter-test` (test scope) directly,
same as before.

### Dependency changes required first

- Add the new `test-support` module (Batch 0, see below) to the root reactor `pom.xml`.
- Add `spring-boot-starter-test` (test scope) to `platform-commons`, `user-spring-boot-starter`
  (plain unit tests only, no DB — already present in `query-lib`, `marketplace-app`,
  `taxon-spring-boot-starter`).
- Add `test-support` (test scope) to `advertisement-spring-boot-starter`,
  `audit-spring-boot-starter`, `attachment-spring-boot-starter`, `taxon-spring-boot-starter`
  (repository/Testcontainers tests).

## Suggested phased execution

1. **Batch 0 — `test-support` module:** create the new module (pom, reactor registration,
   Testcontainers BOM + deps, the shared `AbstractPostgresIntegrationTest` singleton-container base
   class), settle the per-starter data-isolation approach (separate DB/schema per starter against
   the one shared container). No actual repository tests yet — this batch proves the scaffolding
   boots a container and applies a trivial changelog successfully.
2. **Batch 1 — one exemplar of each kind:** add `test-support` as a test dependency to
   `advertisement-spring-boot-starter`, `spring-boot-starter-test` to `platform-commons`; write one
   Testcontainers repository test (`AdvertisementRepository`, the most filter/sort-heavy one) and
   one plain unit test (`AdvertisementSnapshotDto.diff()`) to prove the pattern end-to-end and
   measure actual local run time before committing to the "~30s" estimate.
3. **Batch 2 — remaining plain unit tests:** the other three `diff()` implementations,
   `resolveTranslation()`, `sanitizeHtml()`.
4. **Batch 3 — remaining Testcontainers repository tests:** `AuditLogRepository`,
   `TaxonAssignmentRepository`, `AttachmentRepository`, each starter adding `test-support` as its
   test dependency.
5. **Ongoing:** require the next new starter (`review-spring-boot-starter`, F-06) to ship its
   repository and pure-logic tests (using `test-support`) in the same PR as the feature, not as a
   follow-up.

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
