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
bash integration-tests/run.sh                             # all tests
bash integration-tests/run.sh smoke                       # just PostgresContainerSmokeTest
bash integration-tests/run.sh AdvertisementRepositoryTest # one class by name
bash integration-tests/run.sh --sandbox smoke             # + this sandbox's Docker workarounds
bash integration-tests/run.sh --no-check TaxonRepositoryTest  # skip the staleness check below
```

`run.sh` auto-detects whether `platform-commons`/`advertisement`/`user`/`taxon`/`audit`/
`attachment`/`provider-profile-spring-boot-starter` changed since their last install
and only rebuilds those
before testing (~1:47-3:35 vs. 3-7 min walking the full reactor every time) — no manual flag
needed for the common case. `--no-check` skips that detection entirely, testing against whatever
is already in `~/.m2` even if stale — only for deliberately reproducing behavior against an older
build. See `CLAUDE.md` for the full rule.

### Windows

```bat
wsl bash integration-tests/run.sh
wsl bash integration-tests/run.sh AdvertisementRepositoryTest
```

Or, without a local Java install at all: `scripts\build-and-test.bat --integration` (a different
tool, container-based — no `--no-check`, single-class selection via `--integration-test` instead).

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
| `advertisement/AdvertisementServiceHtmlSanitizationTest` | Plain JUnit + Mockito, no Spring, no DB | `AdvertisementService`'s HTML sanitization policy (OWASP sanitizer + Jsoup visible-text-length cap), tested through the real public `save()` entry point, not the `private static sanitizeHtml()` directly |
| `advertisement/AdvertisementServiceCategoryFilterTest` | Plain JUnit + Mockito, no Spring, no DB | `AdvertisementService.getFiltered()`/`count()` resolve a category-name filter to taxon ids before querying the repository, short-circuiting to empty/zero without a repository call when no taxon matches, and degrading to no restriction when the taxon starter is absent |
| `taxon/TaxonRepositoryTest` | Testcontainers + `@SpringBootTest` | `TaxonRepository.findByIds()` — deliberately **includes** soft-deleted rows (not excludes; see `taxon-spring-boot-starter/CLAUDE.md` on why `DefaultTaxonPort.indexById()` needs deleted taxons visible) |
| `taxon/TaxonPortTranslationFallbackTest` | Testcontainers + `@SpringBootTest` | `TaxonPort.findById()`'s translation-fallback chain (requested locale → configured default → first available → blank), tested through the public port, not the package-private `resolveTranslation()` — see `DECISIONS.md` ADR-008 |
| `taxon/TaxonServiceTest` | Testcontainers + `@SpringBootTest` | `TaxonService.update()` preserves `deletedBy` on an already soft-deleted taxon (Spring Data JDBC's full-row `UPDATE` was silently reverting it to `NULL`) |
| `taxon/TaxonSnapshotDtoTest` | Plain JUnit, no Spring, no DB | `TaxonSnapshotDto.diff()` — pure field-comparison logic, direct analogy with `AdvertisementSnapshotDtoTest` |
| `taxon/TaxonAssignmentRepositoryTest` | Testcontainers + `@SpringBootTest` | `TaxonAssignmentRepository`'s many-to-many join table: idempotent `assign()` (`ON CONFLICT DO NOTHING`), `unassign()`/`deleteAllByEntity()` scoping, both directions of bulk lookup, both count variants |
| `user/UserRepositoryTest` | Testcontainers + `@SpringBootTest` | `UserRepository.updateProfile()` — optimistic locking, and that the narrower `UserProfileUpdate` entity structurally cannot touch `email`/`passwordHash` |
| `user/UserServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `UserService.register()` rate-limiting: threshold blocks before save, duplicate-key failures count, successful registration does **not** reset the IP counter (asymmetry vs. login), different IPs tracked separately |
| `user/SettingsSnapshotDtoTest` | Plain JUnit, no Spring, no DB | `SettingsSnapshotDto.diff()` — pure field-comparison logic, direct analogy with `AdvertisementSnapshotDtoTest` |
| `user/UserSettingsDtoTest` | Plain JUnit, no Spring, no DB | Confirms Jackson's builder-based deserialization correctly applies `UserSettingsDto`'s `@Builder.Default timelinePageSize = 20` for a JSON payload missing that key |
| `attachment/AttachmentServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `AttachmentService.commitTempUploadsQuiet()` cleans up already-moved files on a mid-batch `storageService.move()` failure, instead of leaking them |
| `attachment/AttachmentServiceTransactionTest` | Testcontainers + `@SpringBootTest` + `@MockitoBean` | `AttachmentService.upload()` rolls back its DB row (real transaction, real Postgres) when a post-save step throws |
| `attachment/AttachmentCleanupServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `AttachmentCleanupService.deleteAttachments()` deletes DB rows before S3 objects (`InOrder`-verified), and a storage failure never affects the already-completed DB delete |
| `attachment/AttachmentRepositoryTest` | Testcontainers + `@SpringBootTest` + `@MockitoBean` | Soft-delete visibility, the two-step restore-to-urls flow, retention-based cleanup selection, and both `loadMediaStats()` overloads (including the bulk one's `ROW_NUMBER() OVER (PARTITION BY entity_id ...)` window function) |
| `attachment/AttachmentSnapshotRepositoryTest` | Testcontainers + `@SpringBootTest` + `@MockitoBean` | `AttachmentSnapshotRepository`'s URL-history round-trip (insert + `getPrevUrls`/`getUrlsById`) and `deleteOlderThan()`'s retention-window cutoff |
| `attachment/AttachmentSnapshotServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `AttachmentSnapshotService`'s filename resolution (real filename vs. URL-segment fallback when no matching attachment row exists) and independent resolution of duplicate original filenames across URLs |
| `audit/AuditLogRepositoryTest` | Testcontainers + `@SpringBootTest` | `AuditLogRepository.findTimeline()`/`getSnapshotContent()`'s `version`-numbering subqueries get an `id` tiebreaker for same-`created_at` rows |
| `providerprofile/ProviderProfileRepositoryTest` | Testcontainers + `@SpringBootTest` | Real SQL correctness for `ProviderProfileRepository` — filter (kind, cityTaxonId), sort, pagination, `findOwnerIds()`, optimistic-locked `delete()` — against real `provider-profile-spring-boot-starter` + `user-spring-boot-starter` autoconfiguration |
| `providerprofile/ProviderProfileServiceTest` | Plain JUnit + Mockito, no Spring, no DB | `ProviderProfileService`'s HTML sanitization policy (mirrors `AdvertisementServiceHtmlSanitizationTest`) and the `kind == SUPPORT` requires-privileged-actor authorization rule (accept/reject) |
| `providerprofile/ProviderProfileSnapshotDtoTest` | Plain JUnit, no Spring, no DB | `ProviderProfileSnapshotDto.diff()` — pure field-comparison logic — plus a Jackson polymorphic round-trip (de)serialization test, the first of any `AuditableSnapshot` subtype to have one |
| `SharedEnvConfigTest` | Plain JUnit, no Spring, no DB | `SharedEnvConfig.require()` walking up directories to find the repo-root `.env`, and its failure modes (no `.env` in range, key missing from an `.env` that does exist) |
| `user/UserPreferencesRepositoryTest` | Testcontainers + `@SpringBootTest` | `UserPreferencesRepository.save()`'s optimistic locking embedded in the `settings` JSONB column's own `version` field (fresh row starts at 0, stale version throws, correct version succeeds and increments) |

### `PostgresContainerSmokeTest`

| Test | Verifies |
|---|---|
| container starts, changelog applies, verification query succeeds | Testcontainers container lifecycle + Liquibase mechanics work end-to-end — the load-bearing assumption every other test class in this module depends on |

### `advertisement/AdvertisementRepositoryTest`

Boots both `advertisement-spring-boot-starter` and `user-spring-boot-starter`'s real
autoconfiguration in one Spring context (satisfying `AdvertisementAutoConfiguration`'s
`@DependsOn("userLiquibase")`, needed for changelog ordering — not for an FK constraint, since
`advertisement.created_by` → `user_information.id` has no SQL-level FK). Uses
`RepositoryTestSupport` + `TestDataCleaner` (see `CLAUDE.md` "Reusable test support").

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
| `findByIds_includesSoftDeletedRows` | A soft-deleted taxon id is deliberately still returned by the bulk lookup — `findByIds()` has no `deleted_at` filter, by design (its only caller, `DefaultTaxonPort.indexById()`, needs deleted taxons visible; see `taxon-spring-boot-starter/CLAUDE.md`) |
| `findByIds_returnsActiveRows` | Non-deleted rows come back correctly alongside the deleted ones |

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

### `advertisement/AdvertisementServiceCategoryFilterTest`

Plain JUnit + Mockito, no Spring, no DB.

| Test | Verifies |
|---|---|
| `getFiltered_noCategoryFilterRequested_appliesNoRestriction` | No category filter in the request means no restriction applied |
| `getFiltered_categoryFilterMatchesNothing_returnsEmptyWithoutQueryingRepository` | A category filter that resolves to zero taxon ids short-circuits to empty, never reaching the repository |
| `getFiltered_categoryFilterMatchesSome_appliesResolvedIds` | Resolved taxon ids are passed through to the repository query |
| `getFiltered_categoryFilterRequestedButTaxonStarterAbsent_appliesNoRestriction` | Graceful degradation when the taxon starter isn't on the classpath |
| `count_categoryFilterMatchesNothing_returnsZeroWithoutQueryingRepository` | Same short-circuit behavior for `count()` |

### `attachment/AttachmentSnapshotRepositoryTest`

Testcontainers + `@SpringBootTest` + `@MockitoBean`.

| Test | Verifies |
|---|---|
| `insert_and_getPrevUrls_roundTripsMultipleUrlsInOrder` | Multiple URLs in one snapshot round-trip in insertion order |
| `insert_and_getUrlsById_roundTripsUrls` | Lookup by snapshot id returns the same URLs that were inserted |
| `getPrevUrls_noSnapshotExists_returnsEmpty` | No prior snapshot returns an empty result, not an exception |
| `deleteOlderThan_removesOnlyRowsOlderThanTheGivenDays` | Retention cleanup only removes rows past the cutoff, leaves recent ones |

### `attachment/AttachmentSnapshotServiceTest`

Plain JUnit + Mockito, no Spring, no DB.

| Test | Verifies |
|---|---|
| `captureAndGetId_firstSnapshot_usesRealFilenameNotUuidKey` | A first snapshot resolves to the attachment's real filename, not its storage UUID key |
| `captureAndGetId_noMatchingAttachmentRow_fallsBackToUrlSegment` | No matching attachment row falls back to the URL's own filename segment |
| `getMediaStateForSnapshot_resolvesRealFilename` | Media-state lookup for a snapshot resolves the real filename the same way |
| `captureAndGetId_duplicateOriginalFilenamesAcrossUrls_bothResolveIndependently` | Two URLs sharing the same original filename resolve independently, no collision |

### `SharedEnvConfigTest`

Plain JUnit, no Spring, no DB.

| Test | Verifies |
|---|---|
| `require_envInStartDirectory_returnsValue` | `.env` found directly in the starting directory resolves the requested key |
| `require_envInParentDirectory_walksUpAndReturnsValue` | `.env` found by walking up parent directories still resolves |
| `require_noEnvFileWithinSearchRange_throwsIllegalStateException` | No `.env` anywhere in range fails fast |
| `require_envFilePresentButKeyMissing_throwsIllegalStateExceptionMentioningKey` | An `.env` that exists but lacks the requested key fails fast, naming the missing key |

### `user/UserPreferencesRepositoryTest`

Testcontainers + `@SpringBootTest`.

| Test | Verifies |
|---|---|
| `save_freshUser_startsAtVersionZeroAndSucceeds` | A user with no prior settings row starts at `version` 0 |
| `save_staleVersion_throwsOptimisticLockingFailureException` | A stale `version` embedded in the `settings` JSONB column is rejected |
| `save_currentVersion_succeedsAndIncrementsVersion` | A correct `version` succeeds and increments — same optimistic-locking shape as `User.version`, but scoped to the JSONB column, not a SQL column |

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
5. Run with `bash integration-tests/run.sh <ClassName> --sandbox` (drop `--sandbox` on a
   normal developer machine).
6. Update the table above.
