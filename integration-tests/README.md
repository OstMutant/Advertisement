# Integration Tests

Testcontainers-based repository tests (real Postgres, real Liquibase schema, real SQL) and plain
unit tests for pure logic (`diff()`, sanitizers, translation resolvers) that would otherwise have
no home — the fast inner test loop for this project. All Testcontainers-based tests for **every**
domain starter live here; domain starters themselves carry zero test code for this purpose. See
`CLAUDE.md` for the full architecture rationale and `DECISIONS.md` for the ADRs behind it.

This does **not** replace Playwright (`/app/playwright/`) — it's an addition to the safety net for
a different, cheaper-to-verify class of bug (SQL correctness, pure-logic regressions), not a
substitute for the full e2e run before any deploy.

## Requirements

- A reachable Docker daemon (Testcontainers starts a real ephemeral Postgres container per `mvn
  test` reactor run — see ADR-002 in `DECISIONS.md`)
- Nothing else needs to be running first — unlike Playwright, this does not need the app itself,
  MinIO, or the persistent dev Postgres container

## Running

### Linux / WSL

```bash
bash scripts/integration-tests.sh                             # all tests
bash scripts/integration-tests.sh smoke                       # just PostgresContainerSmokeTest
bash scripts/integration-tests.sh AdvertisementRepositoryTest # one class by name
bash scripts/integration-tests.sh --sandbox smoke             # + this sandbox's Docker workarounds
bash scripts/integration-tests.sh --fast TaxonRepositoryTest  # skip -am reactor rebuild — fast
                                                                # iteration on this module's own
                                                                # test files (~1:47 vs 3-7 min);
                                                                # requires a prior `mvn install`,
                                                                # and must NOT be used right after
                                                                # editing a starter's own source —
                                                                # see CLAUDE.md "--fast" for why
```

### Windows

```bat
scripts\integration-tests.bat
scripts\integration-tests.bat AdvertisementRepositoryTest
```

Delegates to `wsl bash /app/integration-tests/run.sh`.

### Direct Maven (no script, no reports folder)

```bash
mvn -pl integration-tests -am test
```

`-am` also builds whichever starters `integration-tests` currently depends on — required, since
they aren't otherwise built by a scoped `-pl integration-tests` alone.

### From an IDE (IntelliJ)

Run any `*Test` class directly via the gutter icon — no script needed. `SharedEnvConfig` resolves
the repo-root `.env` correctly even when IntelliJ sets the module directory (not the reactor root)
as the working directory (see ADR-003 in `DECISIONS.md`). On a normal developer machine, no env
vars are required — the sandbox-only `--sandbox` workarounds (ADR-004) do not apply.

## What `run.sh` does

1. Applies `--sandbox` workarounds if passed (`TESTCONTAINERS_RYUK_DISABLED=true`,
   `INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432`) — omit on a normal developer machine.
2. Runs `./mvnw -pl integration-tests -am test`, optionally scoped to one test class via
   `-Dtest=<ClassName> -Dsurefire.failIfNoSpecifiedTests=false`.
3. Streams full Maven/Testcontainers output live.
4. Writes `integration-tests/reports/run.log` (full output) and
   `integration-tests/reports/surefire/` (one `.txt`/`.xml` report per test class, copied from
   Maven's own `target/surefire-reports/`).

---

## Test classes (`src/test/java/org/ost/integrationtests/`)

| Class | Kind | What it covers |
|---|---|---|
| `PostgresContainerSmokeTest` | Testcontainers, no Spring context | Proves the scaffolding itself: container starts, Liquibase applies a trivial changelog, a verification query succeeds |
| `advertisement/AdvertisementRepositoryTest` | Testcontainers + `@SpringBootTest` | Real SQL correctness for `AdvertisementRepository` — the highest-risk dynamic-SQL paths (filter, sort, pagination, optimistic locking), against real `advertisement-spring-boot-starter` + `user-spring-boot-starter` autoconfiguration |
| `advertisement/AdvertisementSnapshotDtoTest` | Plain JUnit, no Spring, no DB | `AdvertisementSnapshotDto.diff()` — pure field-comparison logic, zero side effects |

### `PostgresContainerSmokeTest`

| Test | Verifies |
|---|---|
| container starts, changelog applies, verification query succeeds | Testcontainers container lifecycle + Liquibase mechanics work end-to-end — the load-bearing assumption every other test class in this module depends on |

### `advertisement/AdvertisementRepositoryTest`

Boots both `advertisement-spring-boot-starter` and `user-spring-boot-starter`'s real
autoconfiguration in one Spring context (satisfying `AdvertisementAutoConfiguration`'s
`@DependsOn("userLiquibase")` and the FK from `advertisement.created_by` to
`user_information.id`). Uses `RepositoryTestSupport` + `TestDataCleaner` (see `CLAUDE.md`
"Reusable test support").

| Test | Verifies |
|---|---|
| `save_and_findAdvertisementById_returnsPersistedRow` | Save + find-by-id round-trip, including `createdBy`/`version` populated correctly |
| `findByFilter_titleFilter_returnsOnlyMatchingRows` | `SqlFilterBuilder`'s title `ILIKE` filter matches only the intended rows |
| `findByFilter_emptyFilter_returnsAllRows` | An empty filter returns every non-deleted row, no accidental narrowing |
| `findByFilter_sortByTitle_ordersAscending` | `OrderByBuilder`'s sort-alias map produces a correct `ORDER BY` |
| `findByFilter_pagination_respectsLimitAndOffset` | `PaginationSqlBuilder`'s `LIMIT`/`OFFSET` clause slices correctly across pages |
| `softDelete_staleVersion_throwsOptimisticLockingFailureException` | Optimistic locking rejects a `softDelete()` call with a stale `version` |
| `softDelete_currentVersion_succeedsAndExcludesRowFromFilter` | A correct-version `softDelete()` succeeds and the row disappears from subsequent filtered queries |

### `advertisement/AdvertisementSnapshotDtoTest`

No Spring context, no DB — `AdvertisementSnapshotDto.diff()` is pure `Objects.equals()` field
comparison building `ChangeEntry.FieldChange` records.

| Test | Verifies |
|---|---|
| `diff_noPrevious_returnsChangesForAllSetFields` | `diff(null)` treats a missing previous snapshot as "everything just got created" — a `FieldChange` per set field with `from=null`, not an empty list (the shape used for the creation-time diff) |
| `diff_identicalSnapshots_returnsNoChanges` | No spurious changes when nothing actually changed |
| `diff_titleChanged_returnsSingleFieldChange` | Title-only change produces exactly one `FieldChange` |
| `diff_descriptionChanged_returnsSingleFieldChange` | Description-only change produces exactly one `FieldChange` |
| `diff_categoryIdsChanged_returnsSortedJoinedStrings` | Category-id list changes render as sorted, comma-joined strings (not insertion order) |
| `diff_multipleFieldsChanged_returnsAllChangedFields` | Multiple simultaneous field changes all surface in one `diff()` call |
| `diff_categoryIdsAddedFromEmpty_fromIsEmptyString` | Going from no categories to some categories renders `from=""`, not `from=null` |
| `constructor_categoryIdsAlwaysSorted_regardlessOfInputOrder` | The record's compact constructor normalizes `categoryIds` ordering regardless of insertion order |
| `constructor_nullCategoryIds_defaultsToEmptyList` | A `null` `categoryIds` argument never leaks a `null` into the record — defaults to `List.of()` |

---

## Adding a new scenario

1. Decide the kind: Testcontainers repository test (needs real SQL correctness) or plain unit test
   (pure logic, no DB).
2. Testcontainers: extend `AbstractPostgresIntegrationTest`, add the starter(s) you're testing as
   new `compile`-scope dependencies of `integration-tests/pom.xml` if not already present (never
   touch the starter's own pom), reuse `RepositoryTestSupport` + `TestDataCleaner` from
   `support/` if your test needs actor-row auditing or optional-port stubbing (see `CLAUDE.md`
   "Reusable test support" for the exact shape).
3. Plain unit test: just a normal JUnit 5 test class, no base class needed.
4. Place it in `src/test/java/org/ost/integrationtests/<domain>/`.
5. Run with `bash scripts/integration-tests.sh <ClassName> --sandbox` (drop `--sandbox` on a
   normal developer machine).
6. Update the table above.
