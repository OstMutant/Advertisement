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
bash scripts/integration-tests.sh --no-check TaxonRepositoryTest  # skip the staleness check below
```

`run.sh` auto-detects whether `platform-commons`/`advertisement`/`user`/`taxon-spring-boot-starter`
changed since their last install and only rebuilds those before testing (~1:47-3:35 vs. 3-7 min
walking the full reactor every time) — no manual flag needed for the common case. `--no-check`
skips that detection entirely, testing against whatever is already in `~/.m2` even if stale — only
for deliberately reproducing behavior against an older build. See `CLAUDE.md` for the full rule.

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
| `taxon/TaxonRepositoryTest` | Testcontainers + `@SpringBootTest` | `TaxonRepository.findByIds()`/`findByTypeAndCode()` correctly exclude soft-deleted rows (`deleted_at IS NULL`) — improvement-045 item 4/5 fix |
| `taxon/TaxonPortTranslationFallbackTest` | Testcontainers + `@SpringBootTest` | `TaxonPort.findById()`'s translation-fallback chain (requested locale → configured default → first available → blank), tested through the public port, not the package-private `resolveTranslation()` — see `DECISIONS.md` ADR-008 |
| `taxon/TaxonServiceTest` | Testcontainers + `@SpringBootTest` | `TaxonService.update()` preserves `deletedBy` on an already soft-deleted taxon (Spring Data JDBC's full-row `UPDATE` was silently reverting it to `NULL`) — improvement-049 item 1 |
| `user/UserRepositoryTest` | Testcontainers + `@SpringBootTest` | `UserRepository.updateProfile()` — optimistic locking, and that the narrower `UserProfileUpdate` entity structurally cannot touch `email`/`passwordHash` |
| `user/UserServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `UserService.register()` rate-limiting: threshold blocks before save, duplicate-key failures count, successful registration does **not** reset the IP counter (asymmetry vs. login), different IPs tracked separately |
| `user/UserServiceRestoreTest` | Testcontainers + `@SpringBootTest` | `UserService.restoreToSnapshot()` (the public entry point to private `applyUserRestore()`) — role/name reverted, `version` forwarded from the row's current state not re-derived, unknown snapshot id returns empty — see `DECISIONS.md` ADR-008 |
| `user/SettingsSnapshotDtoTest` | Plain JUnit, no Spring, no DB | `SettingsSnapshotDto.diff()` — pure field-comparison logic, direct analogy with `AdvertisementSnapshotDtoTest` |
| `user/UserSettingsDtoTest` | Plain JUnit, no Spring, no DB | Confirms Jackson's builder-based deserialization correctly applies `UserSettingsDto`'s `@Builder.Default timelinePageSize = 20` for a JSON payload missing that key — improvement-050 item 5's "Required verification" |
| `attachment/AttachmentServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `AttachmentService.commitTempUploadsQuiet()` cleans up already-moved files on a mid-batch `storageService.move()` failure, instead of leaking them — improvement-049 item 2 |
| `attachment/AttachmentServiceTransactionTest` | Testcontainers + `@SpringBootTest` + `@MockitoBean` | `AttachmentService.upload()` rolls back its DB row (real transaction, real Postgres) when a post-save step throws — improvement-049 item 3 |
| `attachment/AttachmentCleanupServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `AttachmentCleanupService.deleteAttachments()` deletes DB rows before S3 objects (`InOrder`-verified), and a storage failure never affects the already-completed DB delete — improvement-049 item 4 |
| `audit/AuditLogRepositoryTest` | Testcontainers + `@SpringBootTest` | `AuditLogRepository.findTimeline()`/`getSnapshotContent()`'s `version`-numbering subqueries get an `id` tiebreaker for same-`created_at` rows — improvement-050 item 4; first `AuditLogRepositoryTest`, improvement-027 Batch 3 starts here |

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

### `taxon/TaxonRepositoryTest`

| Test | Verifies |
|---|---|
| `findByIds_excludesSoftDeletedRows` | A soft-deleted taxon id is silently dropped from the bulk lookup, not returned |
| `findByIds_returnsActiveRows` | Non-deleted rows still come back correctly |
| `findByTypeAndCode_excludesSoftDeletedRow` | Same `deleted_at IS NULL` fix applied to the type+code lookup |
| `findByTypeAndCode_returnsActiveRow` | Non-deleted rows still come back correctly |

### `taxon/TaxonPortTranslationFallbackTest`

Fixture setup uses `TaxonRepository`/`TaxonTranslationRepository` directly (bypassing
`TaxonService.create()`'s validation) to reach incomplete-translation states the public API alone
can't produce.

| Test | Verifies |
|---|---|
| `findById_requestedLocaleAvailable_returnsRequestedTranslation` | Exact-match tier: requested locale's own translation wins |
| `findById_requestedLocaleMissing_fallsBackToConfiguredDefaultLocale` | Missing requested locale falls back to `TaxonProperties.defaultLocale` |
| `findById_requestedAndDefaultLocaleMissing_fallsBackToFirstAvailableTranslation` | Both missing falls back to whichever translation exists |
| `findById_noTranslationsAtAll_returnsBlankNameNotError` | Zero translations is a blank name, not an exception |

### `user/UserRepositoryTest`

| Test | Verifies |
|---|---|
| `updateProfile_staleVersion_throwsOptimisticLockingFailureException` | Optimistic locking rejects a stale-`version` profile update |
| `updateProfile_currentVersion_succeedsAndUpdatesNameAndRole` | Correct-version update succeeds |
| `updateProfile_cannotAlterEmailOrPasswordHash` | `UserProfileUpdate`'s narrower entity (no `email`/`passwordHash` mapped properties) structurally cannot touch those columns, even if attempted — see `user-spring-boot-starter/CLAUDE.md` |

### `user/UserServiceTest`

| Test | Verifies |
|---|---|
| `register_success_savesUser` | Baseline: a normal registration succeeds |
| `register_duplicateEmail_incrementsAttemptsAndPropagatesException` | A duplicate-key failure counts toward the rate-limit bucket and still propagates |
| `register_thresholdReached_throwsIllegalStateException_beforeAttemptingSave` | Once the threshold is hit, the save is never attempted — fails fast |
| `register_successAfterDuplicateKeyFailures_doesNotResetAttempts` | The register/login rate-limit asymmetry: unlike login, a successful registration does **not** reset the IP's attempt counter |
| `register_differentIpsTrackedSeparately` | Two different IPs never share a rate-limit bucket |

### `user/UserServiceRestoreTest`

Needed its own `TestConfig` rather than reusing `RepositoryTestSupport` (bean-name collision when
both the stub `ComponentFactory<AuditPort>` and the real `AuditAutoConfiguration` are present) and
registers `UserSnapshotDto` on the `auditObjectMapper` bean itself — `AuditAutoConfiguration`'s
default mapper has no `AuditableSnapshot` subtypes registered outside `marketplace-app`'s own
`JacksonConfig`. See the class's own javadoc for the full rationale.

| Test | Verifies |
|---|---|
| `restoreToSnapshot_revertsNameAndRole_andForwardsCurrentVersionNotStale` | Name/role reverted to the snapshot's values; `version` forwarded from the row's current state post-change, not re-derived from a stale fetch |
| `restoreToSnapshot_unknownSnapshotId_returnsEmpty` | An unresolvable snapshot id returns `Optional.empty()`, not an exception |

### `user/SettingsSnapshotDtoTest`

No Spring context, no DB — direct analogy with `AdvertisementSnapshotDtoTest`.

| Test | Verifies |
|---|---|
| `diff_noPrevious_returnsChangesForAllFields` | `diff(null)` reports all 3 page-size fields as changed |
| `diff_identicalSnapshots_returnsNoChanges` | No spurious changes when nothing actually changed |
| `diff_adsPageSizeChanged_returnsSingleFieldChange` | Only `adsPageSize` changing produces exactly one `FieldChange` |
| `diff_usersPageSizeChanged_returnsSingleFieldChange` | Only `usersPageSize` changing produces exactly one `FieldChange` |
| `diff_timelinePageSizeChanged_returnsSingleFieldChange` | Only `timelinePageSize` changing produces exactly one `FieldChange` |
| `diff_allFieldsChanged_returnsAllChangedFields` | All 3 fields changing surface in one `diff()` call |

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
