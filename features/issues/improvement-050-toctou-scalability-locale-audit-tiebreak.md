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
`/app/user-spring-boot-starter/src/main/java/org/ost/user/services/UserService.java:97-103`

```java
boolean isFirstUser = repository.countByFilter(UserFilterDto.empty()).equals(0L);
User newUser = User.builder()...role(isFirstUser ? Role.ADMIN : Role.USER)...build();
```

`@Transactional` at PostgreSQL's default `READ COMMITTED` isolation does **not** prevent two
concurrent transactions from both observing `count() == 0` before either commits its `INSERT`.
**Failure scenario:** two people register within the same instant on a freshly-deployed, empty
instance — both could be promoted to `ADMIN`. **Reachability:** narrow — only matters in the exact
window of an instance's very first-ever registration (once that window passes, `isFirstUser` is
permanently `false` for everyone else), so this is a real but low-frequency risk, not a standing
one.

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
driver parameter-count limits become a practical concern at scale. **Reachability:** depends
entirely on how large a single category's advertisement count can realistically get in this
marketplace's actual data volume today — not verified as part of this issue; low urgency unless a
category is already approaching that scale.

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

1. **Item 1:** either accept the narrow-window risk as-is (documented, not silently ignored), or
   close it with a DB-level unique partial index / advisory lock around the first-registration
   check, or simply always assign `Role.USER` at registration and require an explicit, out-of-band
   admin-promotion step (config flag, CLI, or manual DB update) instead of automatic first-user
   promotion — a decision for whoever owns the onboarding UX, not a pure engineering call.
2. **Item 2:** no fix without first establishing real data volume — if a category realistically
   never exceeds a few hundred/thousand advertisements in this marketplace's growth trajectory,
   this may not be worth the complexity of batching or a JOIN-based rewrite. Revisit with real
   numbers before choosing between "leave as-is," "cap `allowedIds` size defensively," or "rewrite
   as a JOIN to `taxon_assignment`" (the JOIN option would need its own ADR, since `advertisement
   -spring-boot-starter/CLAUDE.md` currently documents a deliberate no-raw-cross-starter-SQL-join
   policy — ADR-034 — that this would need to explicitly revisit, not quietly violate).
3. **Item 3:** use `locale.getLanguage()` instead of `locale.toLanguageTag()` for the comparison
   (matches `LocaleSelectorComponent`'s own already-correct pattern at line 68:
   `wrapper.locale().getLanguage().equals(current.getLanguage())`) — low-risk, one-line change.
   Regression test: extend `TaxonPortTranslationFallbackTest` (improvement-045 item 6) with a case
   using a region-qualified `Locale` (e.g. `Locale.forLanguageTag("en-US")`) requesting a taxon that
   only has an `"en"` translation, asserting it matches on tier 1, not falling through to tier 2/3.
4. **Item 4:** add an `id` tiebreaker to both `version`-numbering subqueries:
   `WHERE (b.created_at, b.id) <= (f.created_at, f.id)` (or the row's own equivalent alias).
   Regression test belongs in a future `AuditLogRepositoryTest` (improvement-027 Batch 3, not yet
   built) — create two audit rows for the same entity with an identical `created_at` (forced via
   direct SQL insert, bypassing the normal `NOW()`-based write path), assert they get distinct
   version numbers.
5. **Item 5:** update the Liquibase default to `{"adsPageSize":20,"usersPageSize":20,
   "timelinePageSize":20}` for consistency — cheap, no migration needed since it only affects the
   column's default expression, not existing rows. Before/instead of editing SQL, first do the
   "Required verification" check below to know whether this is purely cosmetic drift or an actual
   live gap.

## Required verification

- Item 5: write a quick, throwaway deserialization test (or verify manually) — does Jackson
  actually apply `UserSettingsDto`'s `@Builder.Default timelinePageSize = 20` when deserializing a
  JSON payload that has `adsPageSize`/`usersPageSize` but no `timelinePageSize` key at all? This
  determines whether item 5 is a real live-data gap or purely a config-drift cleanup.
- Item 2: get a real count — what's the largest number of advertisements currently (or plausibly
  soon) assigned to a single category? Changes the urgency assessment entirely.
- After any fix, run `bash scripts/integration-tests.sh --sandbox` plus the relevant Playwright
  specs (registration flow for item 1, category filter for item 2, locale switch for item 3,
  timeline for item 4).

## Related

- [improvement-049](improvement-049-taxon-attachment-incomplete-rollback-bugs.md) — same review
  pass's other findings (Taxon/Attachment), filed separately since they're a different module pair.
- [improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) item 6 —
  `TaxonPortTranslationFallbackTest`, the test class item 3's regression coverage extends.
- [improvement-044](improvement-044-shared-env-config-consolidation.md) — the same
  two-places-must-stay-in-sync risk class as item 5.
- `advertisement-spring-boot-starter/CLAUDE.md` ADR-034 — the no-raw-cross-starter-SQL-join policy
  item 2's JOIN-based alternative would need to explicitly revisit.
