# improvement-050: First-admin TOCTOU race, unbounded category-filter IN clause, anonymous-visitor locale fallback gap, audit timeline version tiebreak, settings JSON default drift

**Type:** bug fix / hardening. Found via an external code-review pass across the whole codebase;
every claim independently re-verified against current source before filing — two of the reviewed
claims turned out to be false or already-fixed and were deliberately excluded (see "Explicitly not
included" below), so nothing here is taken on faith.
**Module:** `user-spring-boot-starter` (`UserService.register()`),
`advertisement-spring-boot-starter` (`AdvertisementRepository.buildIdClause()`),
`taxon-spring-boot-starter` (`DefaultTaxonPort.resolveTranslation()`),
`audit-spring-boot-starter` (`AuditLogRepository.findTimeline()`),
`user-spring-boot-starter` (Liquibase `settings` column default).
**Priority:** low-medium — all are real, but each has a narrow trigger window or low practical
impact today (see reachability notes per item); none are observed production incidents.
**When:** independent, no blockers.

## Problem

### 1. `UserService.register()` — TOCTOU race on first-user ADMIN promotion — SECURITY

**Extracted to [improvement-052](improvement-052-first-admin-registration-toctou-race.md)
(2026-07-15)** — decided separately since it needs a product/security-posture call (accept the
risk vs. change onboarding UX), not a pure engineering fix like items 3/4/5 below. Decision:
accept the risk for now, deferred until the project nears production readiness. See that issue
for the full option writeup.

### 2. `AdvertisementRepository.buildIdClause()` — unbounded `IN (:allowedIds)` for category filters — PERFORMANCE
`/app/advertisement-spring-boot-starter/src/main/java/org/ost/advertisement/repository/AdvertisementRepository.java:95-98`

```java
private static String buildIdClause(MapSqlParameterSource params, Set<Long> ids) {
    params.addValue("allowedIds", ids);
    return " AND a.id IN (:allowedIds)";
}
```

`allowedIds` comes from `TaxonPort.findEntityIdsWithAnyTaxon(EntityType.ADVERTISEMENT,
filter.getCategoryIds())` (`AdvertisementService.resolveCategoryFilter()`) — the full set of
advertisement IDs matching the selected categories, bound as one JDBC parameter list with no
batching or size cap. A sufficiently popular category (tens of thousands of matching
advertisements) would produce a very large `IN` clause — real performance risk, and PostgreSQL/JDBC
driver parameter-count limits become a practical concern at scale.

✅ **Done 2026-07-15.** Real data volume was never established (still unknown), but a fix was
found that removes the actual risk (parameter-count limits, query-plan-cache thrashing) without
needing that data and without the JOIN-based rewrite's architectural cost: `buildIdClause()` now
binds a plain `Long[]` array (`= ANY(:allowedIds)`) instead of a `Set<Long>` (`IN (:allowedIds)`)
— Spring only expands `Collection`-typed bind values into one placeholder per element, so a native
array is passed through as a single JDBC parameter regardless of size, reusing
`findExistingIds()`'s already-proven pattern in the same class. See `marketplace-app/DECISIONS.md`
ADR-036 for the full writeup, including why `unnest()` (an equally valid alternative) wasn't
chosen, the Postgres-coupling tradeoff, and the array size ceiling that replaces the removed
parameter-count ceiling. New tests:
`AdvertisementRepositoryTest.findByFilter_allowedIdsRestrictsToMatchingRows` /
`.countByFilter_allowedIdsRestrictsCount` (the non-null `allowedIds` path had zero prior coverage).
`bash scripts/integration-tests.sh --sandbox AdvertisementRepositoryTest`: 9/9, `BUILD SUCCESS`.

### 3. `DefaultTaxonPort.resolveTranslation()` — exact-match locale comparison fails for anonymous visitors with region-qualified browser locales — SILENT-CORRUPTION (narrow)
`/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/DefaultTaxonPort.java:246-255`

```java
.filter(t -> t.getLocale().equals(locale.toLanguageTag()))
```

Stored `taxon_translation.locale` values are simple language codes (`"en"`, `"uk"`). Traced the one
path that could produce a *region-qualified* `Locale` (e.g. `en-US`) reaching this comparison:
`VaadinLocaleProvider.getCurrentLocale()` — for a **logged-in** user, always
`Locale.forLanguageTag(u.locale())` where `u.locale()` was saved via `LocaleSelectorComponent`
(`Locale.of("en").toLanguageTag()` → always `"en"`, no region, confirmed by reading the selector
component directly) — safe. For an **anonymous/unauthenticated** visitor, it falls back to
`ui.getSession().getLocale()` — Vaadin's own browser-`Accept-Language`-derived locale, which *can*
carry a region (e.g. `en-US`). If that region-qualified locale reaches `resolveTranslation()` (the
public browsing view is accessible without login, per Playwright spec 01), tier-1 exact match never
fires even though a matching `"en"` translation exists, silently falling through to tier 2/3 —
functionally still produces *a* name (not broken), just skips the intended exact-match tier. Real,
but low severity and narrow (anonymous visitors only).

### 4. `AuditLogRepository.findTimeline()` — version numbering has no tiebreaker for same-timestamp rows — SILENT-CORRUPTION
`/app/audit-spring-boot-starter/src/main/java/org/ost/audit/repository/AuditLogRepository.java:142-146`

```java
(SELECT COUNT(*) FROM audit_log b
 WHERE b.entity_type = f.entity_type AND b.entity_id = f.entity_id
   AND b.created_at <= f.created_at)::int AS version,
```

No `id` tiebreaker — confirmed the same shape repeats at line ~186 for a second, similar subquery.
**Failure scenario:** two `audit_log` rows for the same entity with an identical `created_at`
(plausible: `UserService.register()` calls `captureCreation()` twice in immediate succession — once
for the user snapshot, once for default settings — and any code path that writes two audit rows
within the same transaction/millisecond hits this) get the **same** computed `version` number,
which `marketplace-app/DECISIONS.md` ADR-022's "Current state" badge logic relies on to distinguish
timeline entries — a tie could show the wrong entry (or both) as "current."

### 5. Liquibase `settings` column default JSON is missing `timelinePageSize` — CONFIG DRIFT, likely not a live bug
`/app/user-spring-boot-starter/src/main/resources/db/user-changelog/changes/01-user-schema.xml:27`

```xml
<column name="settings" type="JSONB" defaultValue='{"adsPageSize":20,"usersPageSize":20}'>
```

`UserSettingsDto` (`platform-commons`) has a third field, `timelinePageSize`, added later
(`@Builder.Default int timelinePageSize = 20`) — the SQL-level default was never updated to match.
**Likely not a live bug in practice:** `UserSettingsDto` uses `@JsonDeserialize(builder = ...)` +
`@JsonPOJOBuilder` + Lombok's `@Builder.Default` — this combination generally makes Jackson's
builder-based deserialization correctly fall back to the `@Builder.Default` value (20) for a
genuinely absent JSON key, rather than defaulting to `0`. Not independently verified with a live
deserialization test as part of this issue — flagged as "Required verification" below, since the
Jackson/Lombok interaction here is subtle enough to be worth confirming rather than assuming either
way. Regardless of live impact, this is real drift between two places that must stay in sync — the
same class of risk already tracked in
[improvement-044](improvement-044-shared-env-config-consolidation.md) for a different pair of
config sources.

## Explicitly not included (claims checked and found false/stale)

- **"`SettingsPaginationService` leaves the user on a stale page after changing page size, showing
  an empty screen."** False — checked `PaginationBar.setPageSize()`
  (`marketplace-app/.../components/PaginationBar.java:93-95`): it already resets `currentPage = 0`
  as part of the setter itself, plus a second defensive clamp (`if (currentPage >= getTotalPages())
  currentPage = Math.max(0, getTotalPages() - 1)`) a few lines later. Already handled correctly.
- **"`scripts/database/reset.sh` is missing `--project-directory`, so `POSTGRES_IMAGE` resolves
  empty when run from outside the repo root."** Stale — this was a real bug, already found and
  fixed earlier in this same session (improvement-027 Batch 0). Checked the current file directly:
  `docker compose --project-directory "$ROOT" -f "$ROOT/scripts/infra/docker-compose.db.yml" up
  -d` is already present.

## Suggested fix

1. **Item 1 — see [improvement-052](improvement-052-first-admin-registration-toctou-race.md).**
2. ✅ **Item 2 — done 2026-07-15, resolved without needing the real-data-volume answer.** See the
   problem section above and `marketplace-app/DECISIONS.md` ADR-036 — a lower-risk fix (array bind
   via `= ANY()`) removed the actual risk without either "cap defensively" (a band-aid) or the
   JOIN-based rewrite (which would have reversed ADR-034).
3. ✅ **Item 3 — done 2026-07-15.** `DefaultTaxonPort.resolveTranslation()` now compares via
   `locale.getLanguage()` (both the requested locale and `properties.defaultLocale()`, for
   consistency — the latter is always a plain config value with no region today, but costs nothing
   to make consistent), matching `LocaleSelectorComponent`'s already-correct pattern.
   `TaxonPortTranslationFallbackTest` extended with
   `findById_regionQualifiedRequestedLocale_stillMatchesExactLanguageOnTier1` — deliberately
   requests `uk-UA` (not `en-US`) against a taxon with *both* an `en` and a `uk` translation, since
   a same-language request (matching the `en` default locale) can't actually distinguish "tier 1
   matched" from "coincidentally fell through to tier 2" (confirmed directly: an earlier draft of
   this test using `en-US` against an `en`-defaulted taxon passed even with the bug still present).
   TDD-verified against the pre-fix code. `bash scripts/integration-tests.sh --sandbox
   TaxonPortTranslationFallbackTest`: 5/5, `BUILD SUCCESS`.
4. ✅ **Item 4 — done 2026-07-15.** Added an `id` tiebreaker — `(b.created_at, b.id) <= (f.created_at,
   f.id)` / `(a.created_at, a.id)` — to both `version`-numbering subqueries (`findTimeline()` and
   `getSnapshotContent()`). New `integration-tests/src/test/java/org/ost/integrationtests/audit/
   AuditLogRepositoryTest.java` (2 tests, first `AuditLogRepositoryTest` — improvement-027 Batch 3
   starts here): two rows inserted directly via `jdbcClient` with an identical `created_at`
   (bypassing `AuditLogRepository.save()`'s `NOW()`-based write path, which can never reliably tie),
   asserting both `findTimeline()` and `getSnapshotContent()` return distinct version numbers (1
   and 2, not both 1 or both 2). Needed its own `TestConfig` (same `@ImportAutoConfiguration`
   allow-list as `UserServiceRestoreTest`, see `integration-tests/DECISIONS.md` ADR-009) — no
   `UserAutoConfiguration` needed, `audit_log.actor_id` has no FK. TDD-verified: both tests failed
   against the pre-fix code (`expected: 2 not to be equal to: 2` — literally the same tied version
   number on both sides). `bash scripts/integration-tests.sh --sandbox AuditLogRepositoryTest`:
   2/2, `BUILD SUCCESS`.
5. ✅ **Item 5 — done 2026-07-15.** Verified first (see "Required verification" below, now
   resolved): Jackson's builder-based deserialization does correctly apply
   `@Builder.Default timelinePageSize = 20` for a JSON payload missing that key — confirmed by a
   direct test, not assumed. Not a live bug. Updated the Liquibase default anyway (cheap, no
   migration, closes the drift risk for good) to
   `{"adsPageSize":20,"usersPageSize":20,"timelinePageSize":20}`.
   `integration-tests/src/test/java/org/ost/integrationtests/user/UserSettingsDtoTest.java` (2
   tests: missing-key falls back to the builder default; all-keys-present uses the provided
   values). `bash scripts/integration-tests.sh --sandbox UserSettingsDtoTest`: 2/2, `BUILD SUCCESS`.

**All items resolved (2026-07-15).** Item 1 extracted to
[improvement-052](improvement-052-first-admin-registration-toctou-race.md) (accepted risk,
deferred to pre-production — a deliberate deferral, not an open loose end of this issue). Items
2/3/4/5 all done. Closing this issue.

## Required verification

- ✅ Item 5: resolved above — Jackson correctly applies the builder default; not a live bug.
- ✅ Item 2: resolved without needing this — the chosen fix (array bind) removes the actual risk
  regardless of real category size, so the data point is no longer load-bearing for this issue
  (still generically useful to know for capacity planning, just not a blocker here anymore).
- Full `integration-tests` suite run after item 2's fix: `AdvertisementRepositoryTest` 9/9. Full
  suite (items 2/3/4/5 together) not re-run as one pass — recommended before the next commit, not
  a re-open of this issue. Playwright specs (category filter for item 2, locale switch for item 3,
  timeline for item 4) not run as part of this issue — items 2/3/4 were verified at the
  integration-tests level (real Postgres); a Playwright pass is still worth doing as a follow-up,
  not blocking this close.

## Related

- [improvement-049](improvement-049-taxon-attachment-incomplete-rollback-bugs.md) — same review
  pass's other findings (Taxon/Attachment), filed separately since they're a different module pair.
- [improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) item 6 —
  `TaxonPortTranslationFallbackTest`, the test class item 3's regression coverage extends.
- [improvement-044](improvement-044-shared-env-config-consolidation.md) — the same
  two-places-must-stay-in-sync risk class as item 5.
- `advertisement-spring-boot-starter/CLAUDE.md` ADR-034 — the no-raw-cross-starter-SQL-join policy
  item 2's JOIN-based alternative would need to explicitly revisit.
