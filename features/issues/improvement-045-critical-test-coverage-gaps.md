# improvement-045: Eight critical, non-UI-observable code paths have zero test coverage

**Type:** improvement — testing infrastructure/process. Follow-up to
[improvement-027](improvement-027-unit-testcontainers-test-layer.md) Batch 1: a targeted audit of
the codebase (not the whole surface) for the highest-risk *untested* logic — the kind a code
review wouldn't catch and Playwright's happy-path e2e flows never exercise.
**Module:** cross-cutting — `marketplace-app` (security, auth), `user-spring-boot-starter`,
`taxon-spring-boot-starter`, `platform-commons` — all via the `integration-tests` module
(no domain starter gets its own test code, per improvement-027's architecture).
**Priority:** high for items 1, 3 (security); medium for the rest — none are observed production
incidents, all are silent-failure-mode risks identified by code inspection. Items 4/5 were
initially suspected live bugs but a full caller trace (2026-07-14) confirmed both code paths are
currently unreachable from production — see "Batch resolution" below.
**When:** independent, no blockers — natural continuation of improvement-027 Batch 2/3, but scoped
narrower (these 8 items specifically, not "every untested class").

## Problem

A targeted audit (not exhaustive) of authorization, concurrency, audit-diff, and soft-delete logic
found 8 concrete gaps, each verified directly against current source (not inferred from names).
Ranked by what's actually at stake if the code silently breaks:

### 1. `AccessEvaluator.canOperate()` — SECURITY — ✅ COVERED (2026-07-14)
`/app/marketplace-app/src/main/java/org/ost/marketplace/services/security/AccessEvaluator.java:43-61`

Single chokepoint for every edit/delete authorization decision app-wide (`admin OR moderator OR
owner-match via UserPort.isOwner()`), called from every overlay/view instead of `@PreAuthorize`
(intentionally absent — Vaadin's view-init timing forbids class-level `@PreAuthorize`, see
`marketplace-app/CLAUDE.md`). Zero unit tests today. **Failure scenario:** a refactor that swaps
argument order in an `isOwner()` call, or changes the `||` to `&&`, lets any logged-in user
edit/delete another user's data — nothing else in the stack blocks it, and no Playwright test
asserts the *negative* case (non-owner blocked) for every entity type.

### 2. `UserRepository.updateProfile()` / `UserService.save()` two-entity `@Version` split — DATA-INTEGRITY — ✅ COVERED (2026-07-14)
`/app/user-spring-boot-starter/src/main/java/org/ost/user/repository/UserRepository.java:104-111`,
`UserService.java:73-83`

`UserProfileUpdate` intentionally omits `email`/`passwordHash` so Spring Data JDBC's generated
`UPDATE` can't touch them even if a caller populates the wrong DTO (see `user-spring-boot-starter
/CLAUDE.md`) — the whole security property rests on this entity boundary plus `@Version` mismatch
correctly throwing `OptimisticLockingFailureException`. Unlike `Advertisement` (covered in
improvement-027 Batch 1), completely untested. **Failure scenario:** a future refactor merges the
two entities back together, or forwards the wrong `version` (already flagged in
`marketplace-app/DECISIONS.md` ADR-029 as a past bug class for Advertisement/Taxon) — concurrent
profile edits silently overwrite each other, or a stale role assignment clobbers a fresher one,
with no error and no test catching it.

### 3. `AuthService.login()` vs `UserService.register()` rate-limit asymmetry — SECURITY — ✅ COVERED (2026-07-14)
`/app/marketplace-app/src/main/java/org/ost/marketplace/services/auth/AuthService.java:40-65`,
`/app/user-spring-boot-starter/src/main/java/org/ost/user/services/UserService.java:97-116`

Both use a Caffeine cache of `AtomicInteger` counters (5 attempts / 15 min, per
`marketplace-app/DECISIONS.md` ADR-026) but differ: login keys on `IP|email` and calls
`loginAttempts.invalidate(key)` on success; register keys on IP alone and never invalidates. The
threshold check (`attempts.get() >= MAX`) reads the counter then separately increments it — a
TOCTOU pattern, unmeasured under concurrency. **Failure scenario:** an off-by-one or wrong-key
regression either locks out legitimate users for 15 minutes or silently stops blocking
brute-force login/signup attempts entirely — both fail with no exception, no log anomaly obvious
enough to notice quickly, and no existing test (Playwright's rate-limit test in spec 02 only
covers the happy path of the *current* correct behavior, not regressions in the counting logic
itself).

### 4. `TaxonRepository.findByIds()` missing `deleted_at IS NULL` filter — ✅ FIXED (2026-07-14)
`/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/repository/TaxonRepository.java:87-96`

Verified directly: `findByIds()` had no soft-delete filter, unlike `findByType`/`countByType` in
the same class. **Full caller trace done before fixing** (per this issue's own "decision point"
rule below) — the initial hypothesis ("advertisement category badges show deleted categories") was
**wrong**: the actual production path for category-badge enrichment is
`AdvertisementService.enrichWithCategories()` → `TaxonPort.getForEntities()`/`getForEntity()` →
`DefaultTaxonPort.buildDtoIndex(..., activeOnly=true)` (`DefaultTaxonPort.java:72`), which already
filters soft-deleted taxons **in Java** (`!activeOnly || t.getDeletedAt() == null`) independently
of the SQL. `TaxonPort.findByIds(ids, locale)` — the one port method whose implementation actually
goes through the unfiltered SQL (`activeOnly=false`) — has **zero callers anywhere in
`marketplace-app` or any starter** (confirmed via `grep -rn "\.findByIds("` across the whole repo).
**Corrected classification:** not a live bug — a latent contract violation on the public `TaxonPort`
SPI (every other method is active-only; this one silently wasn't) that was unreachable today only
by accident of current wiring. Fixed anyway since the fix is zero-risk (no caller to break) and
closes the footgun before anyone adds one. SQL now reads `WHERE id IN (:ids) AND deleted_at IS
NULL`. Regression test: `TaxonRepositoryTest.findByIds_excludesSoftDeletedRows` /
`findByIds_returnsActiveRows` in `integration-tests` (proven red against the old SQL, then green
after the fix — see commit history).

### 5. `TaxonRepository.findByTypeAndCode()` — ✅ FIXED (2026-07-14), same resolution as item 4
`/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/repository/TaxonRepository.java:135-144`

Also verified to have no `deleted_at IS NULL` filter. Caller trace: `TaxonService.findByCode()` →
`DefaultTaxonPort.findByCode()` (implements `TaxonPort.findByCode()`) — also **zero callers**
anywhere in `marketplace-app` or any starter (confirmed via `grep -rn "\.findByCode("`). Same
corrected classification and same fix as item 4: `WHERE type = :type AND code = :code AND
deleted_at IS NULL`. Regression test: `TaxonRepositoryTest.findByTypeAndCode_excludesSoftDeletedRow`
/ `findByTypeAndCode_returnsActiveRow`.

### 6. `DefaultTaxonPort.resolveTranslation()` — SILENT-CORRUPTION — ✅ COVERED (2026-07-14)
`/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/DefaultTaxonPort.java:246-255`

Three-tier fallback (requested locale → configured default locale → first available → `null`),
entirely untested. **Failure scenario:** a regression in the fallback order (e.g. the default-locale
branch silently drops) shows blank category names/descriptions for any taxon missing a translation
in the requester's locale — no exception, easy to miss in manual QA since most seeded taxons have
both EN/UK populated already.

Also found while covering this: `TaxonService.create()`'s own validation requires a translation
for every `TaxonProperties.supportedLocales()` entry, meaning the fallback's tier 2/3 branches can
never be reached through the normal creation UI — only via legacy data or a `supportedLocales`
config change after the fact. Real, if rare — not a reason to skip the test.

### 7. `UserService.applyUserRestore()` — DATA-INTEGRITY
`/app/user-spring-boot-starter/src/main/java/org/ost/user/services/UserService.java:137-147`

Restore-from-snapshot reconstructs `role` via `Role.valueOf(snap.role())` and forwards
`before.getVersion()` (correctly — matches ADR-029's rule of always using the caller-supplied/
current version, never re-deriving from a stale fetch). No test exercises this path.
**Failure scenario:** a broken restore either throws deep in a background UI action (poor error
surfacing) or, worse, restores the wrong role (e.g. silently reverting an admin demotion) — with
no audit signal distinguishing "restore succeeded with wrong data" from "restore succeeded
correctly."

### 8. `SettingsSnapshotDto.diff()` — SILENT-CORRUPTION
`/app/platform-commons/src/main/java/org/ost/platform/user/dto/SettingsSnapshotDto.java:35-48`

Mixes a `prev == null` (whole-snapshot-missing) branch with per-field `!=` comparisons on
primitive `int`s — subtly different null-handling from `UserSnapshotDto`/`TaxonSnapshotDto` (which
both use `Objects.equals()` uniformly per field via the shared `AuditableSnapshot.field()` helper).
Untested. **Failure scenario:** if the `prev == null` short-circuit is ever wrong, either a
moderator's first-ever settings change produces a spurious "changed all 3 fields" audit entry on
account creation, or a real settings change is silently swallowed and never shows up in the
activity timeline.

### Also confirmed still-current (already tracked, not new)
`AdvertisementService.sanitizeHtml()` (`advertisement-spring-boot-starter/.../AdvertisementService
.java:200-214`, includes the Jsoup visible-text-length check from ADR-024) — already flagged as a
Batch 2 candidate in improvement-027, confirmed still untested, not duplicated here as a separate
item.

## Suggested fix

All as new test classes inside `integration-tests` (per improvement-027's established
architecture — no domain starter gets test code of its own):

1. ✅ **Items 4 and 5 — done 2026-07-14.** See the "Batch resolution" notes on each item above:
   caller trace done first, confirmed both methods are dead code in production today (no behavior
   change for any live path), fixed the SQL, added `TaxonRepositoryTest` (4 tests) proven red
   before the fix and green after.
2. ✅ **Item 1 (`AccessEvaluator`) — done 2026-07-14.** `marketplace-app/src/test/java/org/ost
   /marketplace/services/security/AccessEvaluatorTest.java` — plain JUnit 5 + Mockito, no Spring
   context, no Testcontainers (lives in `marketplace-app`'s own `src/test/java`, not
   `integration-tests` — `integration-tests` exists to test domain *starters* without them ever
   depending on `marketplace-app`; the reverse dependency would be backwards). 17 tests: `isLoggedIn`,
   `isPrivileged`/`canView`, `getCurrentUserId`, and the full {admin, moderator, owner,
   non-owner-non-privileged, logged-out} matrix across both `canOperate()` overloads
   (`UserIdMarker` and `Long`) plus their `canNotEdit`/`canNotDelete` inverses. `mvn -pl
   marketplace-app -am test -Dtest=AccessEvaluatorTest`: 17/17, `BUILD SUCCESS`.
3. ✅ **Item 2 (User `@Version` split) — done 2026-07-14.**
   `integration-tests/src/test/java/org/ost/integrationtests/user/UserRepositoryTest.java` — 3
   tests: stale-version `updateProfile()` throws `OptimisticLockingFailureException`,
   current-version `updateProfile()` succeeds and updates name/role, and a dedicated test asserting
   `updateProfile()` genuinely cannot alter `email`/`passwordHash` (reloads the row after an
   unrelated name/role update, asserts both fields byte-identical to their original values). Reuses
   `RepositoryTestSupport` (same shape as `AdvertisementRepositoryTest`/`TaxonRepositoryTest`) —
   `UserService` needs `ComponentFactory<AuditPort>`, already provided there. `mvn -pl
   integration-tests -am test -Dtest=UserRepositoryTest`: 3/3, `BUILD SUCCESS`.
4. ✅ **Item 3 (rate-limit asymmetry) — done 2026-07-14.**
   `marketplace-app/src/test/java/org/ost/marketplace/services/auth/AuthServiceTest.java` (5
   tests: success, bad credentials, threshold blocks before attempting auth, success resets the
   counter, different emails on the same IP tracked separately) and
   `integration-tests/src/test/java/org/ost/integrationtests/user/UserServiceTest.java` (5 tests:
   same shape for `register()`, plus the asymmetry itself — a successful registration does **not**
   reset the IP's counter, unlike login). Both scoped to the threshold/key/reset logic itself, not
   Caffeine's real time-based expiry (would need either a real 15-minute wait or refactoring
   production code to accept an injectable `Ticker` — out of scope for a test-only change; see
   each class's javadoc). `bash scripts/unit-tests.sh AuthServiceTest` /
   `bash scripts/integration-tests.sh --sandbox UserServiceTest`: 5/5 each, `BUILD SUCCESS`.
5. ✅ **Item 6 (`resolveTranslation()`) — done 2026-07-14, tested through the public contract, not
   the package-private method directly.** `integration-tests/src/test/java/org/ost/
   integrationtests/taxon/TaxonPortTranslationFallbackTest.java` (4 tests: requested locale
   available, requested missing → falls back to configured default, both missing → falls back to
   first available, no translations at all → blank name not an error) calls the real
   `TaxonPort.findById()` and asserts on the returned `TaxonDto.name` — never references
   `resolveTranslation()`/`DefaultTaxonPort` by name. Fixture setup uses `TaxonRepository`/
   `TaxonTranslationRepository` directly (bypassing `TaxonService.create()`'s validation, which
   requires every `supportedLocales` entry present and so can never itself produce the
   incomplete-translation state this fallback exists to handle). Rationale for testing via the
   public entry point instead of a same-package trick or widened visibility:
   `integration-tests/DECISIONS.md` ADR-008 — also the pattern to follow for item 7 below
   (`applyUserRestore()` is `private`, same shape of problem). `bash scripts/integration-tests.sh
   --sandbox TaxonPortTranslationFallbackTest`: 4/4, `BUILD SUCCESS`.
6. **Item 7 (`applyUserRestore()`)** — Testcontainers repository/service test: create a user,
   change role, snapshot, change role again, restore, assert final role matches the snapshot and
   `version` was forwarded correctly (not re-derived) — same shape as
   `softDelete_currentVersion_succeedsAndExcludesRowFromFilter` in `AdvertisementRepositoryTest`.
7. **Item 8 (`SettingsSnapshotDto.diff()`)** — plain unit test, by direct analogy with the just-
   completed `AdvertisementSnapshotDtoTest`: no-previous case, identical-snapshots case, single-
   field change, all-fields change.

## Required verification

- Each new test must actually fail against the *current* code before any fix (for items 4/5,
  already done — see above) to prove it's testing real behavior, then pass after — standard TDD
  discipline, not just "add a green test."
- Full `bash scripts/integration-tests.sh --sandbox` run stays green after all additions.
- Items 4/5 did **not** need a Playwright regression check per `.claude/rules.md` "Test Coverage
  After Bug Fixes" — that rule applies to user-observable behavior changes, and the caller trace
  confirmed neither fixed method has a caller today, so no UI-visible behavior changed. If either
  method later gets its first caller, that PR should add the Playwright coverage appropriate to
  whatever feature is calling it, not retroactively here.

## Related

- [improvement-027](improvement-027-unit-testcontainers-test-layer.md) — the test-layer
  architecture and `integration-tests` module this issue's tests live in; this issue is a
  narrower, risk-ranked follow-up, not a duplicate of its Batch 2/3 scope (which is broader/
  less targeted: "remaining diff()s, resolveTranslation(), sanitizeHtml()"). Item 6 here overlaps
  Batch 2's `resolveTranslation()` item directly — implement together, don't duplicate.
- `marketplace-app/DECISIONS.md` ADR-026 (rate limiting), ADR-029 (`@Version` forwarding rule).
- `user-spring-boot-starter/CLAUDE.md` — the `UserProfileUpdate`/`User` entity-split rationale
  item 2 verifies.
