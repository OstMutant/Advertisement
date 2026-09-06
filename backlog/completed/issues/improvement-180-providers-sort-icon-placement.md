# improvement-180: Providers catalog needs real created/updated date-range filters — sort control has no field to pair with

**Type:** bug/gap — undocumented design decision, missing filter capability, UX inconsistency
**Module:** `platform-commons` (`providerprofile/dto/ProviderProfileFilterDto.java`),
`provider-profile-spring-boot-starter` (`repository/ProviderProfileRepository.java`),
`marketplace-app` (`ui/views/main/tabs/providers/query/ProviderProfileQueryBlock.java`,
`ui/views/main/tabs/providers/query/ProviderProfileFilterMeta.java`,
`ui/views/main/tabs/providers/ProviderProfileCardView.java`,
`frontend/themes/my-app/provider-profile-card.css`, `services/i18n/I18nKey.java`,
`resources/i18n/messages_en.properties`, `resources/i18n/messages_uk.properties`)
**Priority:** 🟡 high
**When:** independent, no blockers

## Second finding, same verification pass: Share button hover/focus visibility diverges from Advertisement

`ProviderProfileCardView.createShareButton()` gives the card's Share button the CSS class
`"provider-profile-share"`. `provider-profile-card.css` includes that class in the same
`opacity: 0` / `:hover`/`:focus-visible`-reveal rule as `.provider-profile-delete`:

```css
.provider-profile-delete,
.provider-profile-share {
  opacity: 0;
  transition: opacity 0.15s;
}
.provider-profile-card:hover .provider-profile-delete,
.provider-profile-card:hover .provider-profile-share,
.provider-profile-card:focus-visible .provider-profile-delete,
.provider-profile-card:focus-visible .provider-profile-share {
  opacity: 1;
}
```

`AdvertisementCardView.createShareButton()` gives its own Share button the class
`"advertisement-share"` — but `advertisement-card.css`'s equivalent rule only lists
`.advertisement-edit`/`.advertisement-delete`, never `.advertisement-share`. Confirmed via direct
`grep`: `.advertisement-share` has no CSS rule at all in that file. Advertisement's card Share
button is therefore always fully visible; Providers' card Share button incorrectly hides until
hover/focus — an unintentional divergence introduced when the Edit/Delete hover-reveal pattern was
mirrored for Providers, not a deliberate design choice (unlike the sort/filter issue below, there
is no plausible reason to hide Share specifically — Delete hiding until hover/focus, to reduce
accidental clicks, is the only piece of the pattern that makes sense to keep).

**Fix:** remove `.provider-profile-share` from the `opacity: 0` selector and from both
`:hover`/`:focus-visible` reveal selectors in `provider-profile-card.css`, leaving only
`.provider-profile-delete` hover/focus-gated — matching Advertisement's actual behavior exactly.

## Current state

`ProviderProfileQueryBlock.initLayout()` attaches the Providers catalog's only sort control
(`ProviderProfileSortMeta.UPDATED_AT`) to the "Kind" filter row:

```java
filterRow(i18nService, i18nService.get(PROVIDERS_FILTER_KIND), kindField,
        ProviderProfileSortMeta.UPDATED_AT, ProviderProfileFilterMeta.KINDS);
```

The sort icon that appears next to the Kind filter cycles ASC/DESC/NEUTRAL for `updatedAt` — a
field the Kind row has no relationship to at all. `ProviderProfileSortMeta.CREATED_AT` is defined
but completely unreferenced anywhere (confirmed via `grep`) — dead code.

By contrast, `AdvertisementQueryBlock` — the domain this catalog was built to mirror — always
pairs a sort control with a filter row for the *same* field: a "Created" row is a created-date
**range filter** (`QueryDateTimeField` start/end pair) that also sorts by `createdAt`; an "Updated"
row is an updated-date range filter that also sorts by `updatedAt`; a "Title" row filters and sorts
by title. `ProviderProfileFilterDto` has no date-range filter fields at all today (only
`kinds`/`categoryIds`/`cityTaxonId`) — this is the actual root cause, not just a UI placement
mistake: the Providers catalog is missing a real filter capability Advertisement already has, and
the sort control was awkwardly bolted onto Kind as a workaround for that gap rather than the gap
being closed.

This decision was never written into `improvement-179`'s plan (which only said the query-layer
trio "mirrors the Advertisement trio," without addressing what happens where the two domains'
filter fields diverge), never presented for approval per this repo's Approval Rule, and is
undocumented anywhere — no ADR, no code comment explaining why.

## Why change

A sort control that visually sits inside an unrelated filter's row is confusing — a user filtering
by Kind has no reason to expect that same row also controls date-sort order, and nothing in the UI
explains the pairing. More fundamentally, the Providers catalog also just lacks a filter capability
(browsing providers by when they joined/were last active) that the Advertisement catalog already
offers for its own entities — an unexplained gap, not a deliberate scope decision.

## Expected benefit

The Providers catalog becomes genuinely symmetric with the Advertisement catalog — same filter
capability (date-range filters for created/updated), same UI mechanism (one row = one field,
filters and sorts together), not a new one-off UI shape invented just for this domain. Real user
benefit: providers become filterable/sortable by when they joined or were last active, not just by
kind/category/city.

## Approach — real symmetry, not a new UI pattern

Rejected an earlier draft of this issue that proposed a novel "sort-only row" (a new
`QueryBlock<T>.sortOnlyRow()` method for a row with no filter field) — that would have fixed the
Kind-row attachment but left Providers and Advertisement with *different* sort/filter mechanisms,
just a different asymmetry than the one being fixed. The chosen approach instead gives Providers
the exact same date-range filter capability Advertisement already has, reusing the exact same
`filterRow(i18nService, label, field1, field2, sortMeta, filterMeta1, filterMeta2)` 4-arg overload
already on `QueryBlock<T>` — **no new shared-infrastructure method needed at all**.

1. **`platform-commons/.../providerprofile/dto/ProviderProfileFilterDto.java`** — add 4 fields,
   mirroring `AdvertisementFilterDto` exactly:
   ```java
   private Instant createdAtStart;
   private Instant createdAtEnd;
   private Instant updatedAtStart;
   private Instant updatedAtEnd;
   ```
   Plus the same `@ValidRange(start = "createdAtStart", end = "createdAtEnd", ...)` and
   `@ValidRange(start = "updatedAtStart", end = "updatedAtEnd", ...)` class-level annotations
   `AdvertisementFilterDto` carries.

2. **`provider-profile-spring-boot-starter/.../repository/ProviderProfileRepository.java`** — add 4
   `SqlBoundFilter` entries to the existing `FILTER` builder, mirroring `AdvertisementRepository`'s
   own date-range entries exactly:
   ```java
   SqlBoundFilter.of(createdAtStart, "pp.created_at", (m, v) -> after(m, v.getCreatedAtStart())),
   SqlBoundFilter.of(createdAtEnd,   "pp.created_at", (m, v) -> before(m, v.getCreatedAtEnd())),
   SqlBoundFilter.of(updatedAtStart, "pp.updated_at", (m, v) -> after(m, v.getUpdatedAtStart())),
   SqlBoundFilter.of(updatedAtEnd,   "pp.updated_at", (m, v) -> before(m, v.getUpdatedAtEnd())),
   ```

3. **`marketplace-app/.../providers/query/ProviderProfileFilterMeta.java`** — add
   `CREATED_AT_START`/`CREATED_AT_END`/`UPDATED_AT_START`/`UPDATED_AT_END` `FilterFieldMeta`
   constants, mirroring `AdvertisementFilterMeta`'s own 4 date constants field-for-field (including
   the `ValidationPredicates.range(...)` validators and `toInstant(v)` conversion).

4. **`marketplace-app/.../providers/query/ProviderProfileQueryBlock.java`** — revert the Kind row to
   the plain 3-arg `filterRow(label, kindField, filterMeta)` (no `SortFieldMeta`), and add two new
   rows using the *existing* 4-arg `filterRow(...)` overload — the same one `AdvertisementQueryBlock`
   already uses for its own Created/Updated rows, `QueryDateTimeField` start/end pairs included:
   ```java
   QueryDateTimeField createdStart = new QueryDateTimeField(
           i18nService.get(PROVIDERS_FILTER_DATE_CREATED_START),
           i18nService.get(PROVIDERS_FILTER_TIME_CREATED_START), false);
   QueryDateTimeField createdEnd = new QueryDateTimeField(
           i18nService.get(PROVIDERS_FILTER_DATE_CREATED_END),
           i18nService.get(PROVIDERS_FILTER_TIME_CREATED_END), true);
   filterRow(i18nService, i18nService.get(PROVIDERS_SORT_CREATED_AT), createdStart, createdEnd,
           ProviderProfileSortMeta.CREATED_AT,
           ProviderProfileFilterMeta.CREATED_AT_START, ProviderProfileFilterMeta.CREATED_AT_END);

   // same shape for updatedStart/updatedEnd, ProviderProfileSortMeta.UPDATED_AT,
   // ProviderProfileFilterMeta.UPDATED_AT_START/END
   ```

5. **New i18n keys** — `PROVIDERS_FILTER_DATE_CREATED_START`/`_END`, `PROVIDERS_FILTER_TIME_CREATED_START`/`_END`,
   and the `UPDATED` equivalents, in `I18nKey.java` + both `messages_en.properties`/`messages_uk.properties`
   — mirror `ADVERTISEMENT_FILTER_DATE_CREATED_START`/etc.'s exact EN/UK label text pattern, adapted
   to "provider" wording. `PROVIDERS_SORT_CREATED_AT`/`PROVIDERS_SORT_UPDATED_AT` already exist, reused
   as the row labels (same as Advertisement reuses `ADVERTISEMENT_SORT_CREATED_AT`/etc. for its own
   row labels).

## Testing strategy

Update the existing sort `test.step` in `playwright/e2e/04-provider-profile-flow.spec.js` (added
during `improvement-179`'s own verification pass) rather than writing a new test:

1. **Locator**: change from finding the sort icon inside the Kind row (`.query-inline-label-sort`
   with `hasText: 'Kind'`) to the new "Updated" row's own label — same `.query-inline-label-sort`
   CSS-class + text-match pattern already used everywhere else in this file.
2. **Regression guard**: assert the Kind row's own `.sort-icon` count is now `0`.
3. **Real data-array assertions, not just icon state** — assert the actual rendered card order
   (`data-provider-id` array), not just the sort icon's `aria-label`, at each state:
   ```js
   const cardOrder = () => container.locator('.provider-profile-card').evaluateAll(
       els => els.map(el => el.getAttribute('data-provider-id')));

   // DESC (default: updatedAt DESC, createdAt DESC) -- userUk first
   expect(await cardOrder()).toEqual([userUkId, userEnId]);
   // DESC -> NEUTRAL: updatedAt no longer a criterion, createdAt DESC alone -- same order
   expect(await cardOrder()).toEqual([userUkId, userEnId]);
   // NEUTRAL -> ASC: order actually flips -- the strongest real proof sort affects real data
   expect(await cardOrder()).toEqual([userEnId, userUkId]);
   // ASC -> DESC: back to the default
   expect(await cardOrder()).toEqual([userUkId, userEnId]);
   ```
4. **Cover the new `CREATED_AT` row too** — same locator/regression-guard/data-array pattern,
   applied to its own row. Verify empirically (not assumed) whether `userEn`/`userUk`'s `createdAt`
   ordering actually differs from their `updatedAt` ordering by this point in the spec file (`userEn`'s
   profile is edited/saved again after creation earlier in the file, which moves its `updatedAt`
   without touching `createdAt`) — assert the real flip if it diverges, otherwise still assert
   icon-state cycling and the row's isolation from the `UPDATED_AT` row's own icon.
5. **New date-range filter coverage** — add filter interactions for the two new Created/Updated
   date-range rows, mirroring however `AdvertisementQueryBlock`'s own date-range filters are tested
   in `05-marketplace-advertisement-flow.spec.js`/`06-seed-filter-sort-pagination.spec.js` (find and
   follow that existing pattern, don't invent a new one).
6. **Share button visibility regression guard** — assert `.provider-profile-share` is visible
   without hover/focus, right after the initial catalog list assertion.
7. Full clean `e2e --ux` re-run to confirm everything stays green.

## Related

- `improvement-179-provider-profile-catalog.md` (completed) — the parent feature that shipped this
  code without documenting the Kind-row attachment or covering date-range filters.
- `AdvertisementQueryBlock.java`/`AdvertisementFilterDto.java`/`AdvertisementFilterMeta.java`/
  `AdvertisementRepository.java` — the exact pattern this issue mirrors field-for-field.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: 0/0 (deep-review-orchestrator ran, zero findings raised across its lenses — no candidates to survive verification)
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a

### Agent calls
- Dagu CI run status check (sonar trigger) | subagent_type=dagu-analyst | tokens=n/a | tool_uses=n/a | duration_s=n/a | mode=background | batch=solo
- Sonar quality gate detail (post-sonar-run) | subagent_type=sonar-analyst | tokens=n/a | tool_uses=n/a | duration_s=n/a | mode=background | batch=solo
- Deep review of provider profile date-range filter changes | subagent_type=deep-review-orchestrator | tokens=n/a | tool_uses=n/a | duration_s=n/a | mode=background | batch=solo

### Script/command runs
- bash scripts/build-and-test.sh --unit --integration --sandbox | duration_s=186 | mode=background | result=pass (unit 69/69, integration 163/163, 0 failures/errors)
- bash scripts/playwright.sh e2e --ux (confirmed from prior session's log, re-verified this session) | duration_s=389 | mode=background | result=pass (45 passed, 0 failed, 13 skipped)
- bash scripts/ci.sh --sonar (confirmed from prior session's log) | duration_s=n/a | mode=background | result=fail (quality gate ERROR, sole failing condition new_coverage=0% vs 80% threshold, pre-existing tracked gap per improvement-114, zero real new issues)
- bash .claude/nav/scripts/generate-adr-index.sh | duration_s=n/a | mode=foreground | result=pass
