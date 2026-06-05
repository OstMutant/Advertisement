# Taxonomy — Implementation plan

Each step is intended to produce one self-contained commit. Status keys:
- `[ ]` — not started
- `[~]` — in progress
- `[x]` — done

Update statuses as work progresses; refine prompts before executing each step; add a **Notes** block under any step that deviated from plan.

---

## Status overview

| # | Step | Status |
|---|---|---|
| 1 | Module skeleton + autoconfiguration | `[ ]` |
| 2 | Schema, entities, repositories | `[ ]` |
| 3 | Services + `TaxonPort` implementation | `[ ]` |
| 4 | SPI surface in `platform-commons` | `[ ]` |
| 5 | Vaadin UI: management content, selector, chips | `[ ]` |
| 6 | Marketplace integration: tab, form, view, filter, cards | `[ ]` |
| 7 | Audit hook implementation | `[ ]` |
| 8 | Seed + Playwright + decoupling verification | `[ ]` |

---

## Step 1 — Module skeleton + autoconfiguration

**Status:** `[ ]`

**Prompt:**
> Create a new Maven module `taxon-spring-boot-starter` at the repository root, mirroring the structure of `attachment-spring-boot-starter`.
>
> - Add it to the parent `pom.xml` as a module.
> - `taxon-spring-boot-starter/pom.xml`: depend on `platform-commons`, `query-starter`, `spring-boot-starter`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, `vaadin-spring`, `liquibase-core`, Lombok. Mirror versions and `<parent>` from the attachment starter.
> - Java package root: `org.ost.taxon`. Create empty packages `config`, `entities`, `repository`, `services`, `ui`.
> - `org.ost.taxon.config.TaxonAutoConfiguration`:
>   - `@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration")`
>   - `@ComponentScan(basePackageClasses = TaxonAutoConfiguration.class)`
>   - `@EnableJdbcRepositories(basePackages = "org.ost.taxon.repository")`
>   - Liquibase bean for `classpath:db/taxon-changelog/master.xml` (mirror the attachment starter's pattern).
>   - Named `ObjectMapper` bean `taxonObjectMapper` (only if actually needed in step 3; otherwise skip for now).
> - `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` listing `org.ost.taxon.config.TaxonAutoConfiguration`.
> - `src/main/resources/db/taxon-changelog/master.xml` referencing the future single file `001-taxon.xml` (all schema and seed changesets will live in that one file while the app is pre-production).
> - Add a starter-level `DECISIONS.md` seeded with one initial entry titled **"Filter resolves through `Set<Long>`, not SQL JOIN"**. Body records: (a) decision — `findEntityIdsWithAnyTaxon` returns an in-memory id set that marketplace pushes into the existing `advertisementIds` filter clause, not a SQL JOIN; (b) **why** — keeps marketplace SQL free of starter table names, preserving compile-time decoupling; (c) **trigger to revisit** — if a single category routinely resolves to >10k advertisement ids in production (measure with a `WARN` log line in `AdvertisementService.list` when `resolvedIds.size() > 10_000`), or if multi-category AND-semantics is ever requested, switch to a starter-owned JOIN helper or a pre-computed materialised mapping. Until then, the `Set<Long>` round-trip is the chosen trade-off.
> - Add the new starter as a `runtime` dependency to `marketplace-app/pom.xml`, same shape as attachment.
>
> **Build infrastructure (must land in the same commit so `docker build` keeps working):**
> - `/app/Dockerfile` — in the builder stage, add the new module's POM and source alongside the existing ones:
>   - In the "Copy module POMs" block: `COPY taxon-spring-boot-starter/pom.xml taxon-spring-boot-starter/`
>   - In the "Copy the source code" block: `COPY taxon-spring-boot-starter/src ./taxon-spring-boot-starter/src`
> - `/app/scripts/clean.bat` — already updated (taxon target dir added to the cleanup list).
> - Verify `/app/scripts/collect-code.bat` picks up the new module's files automatically (it does — generic `*.java` / `pom.xml` find ignoring `target/`). No edit required.
>
> **Acceptance:**
> 1. `mvn -pl taxon-spring-boot-starter,marketplace-app -am clean compile` succeeds (full reactor — proves nothing in marketplace was broken).
> 2. `bash scripts/deploy-dev.sh` starts the app to the `Started Application` line with no auto-config errors in logs (auto-config bean wakes up, even though no UI is wired yet).
> 3. Login page still loads at `http://localhost:8081/login`.

**Notes:** _(filled after execution)_

---

## Step 2 — Schema, entities, repositories

**Status:** `[ ]`

**Prompt:**
> In `taxon-spring-boot-starter`:
>
> - **Liquibase** — single consolidated changeset file `db/taxon-changelog/001-taxon.xml` (everything in one file while the app is not yet in production; will be split / cleaned up once the DB is reset for prod). Creates **three tables** (no `taxon_type` table — type is a `VARCHAR(64)` column storing the enum name): `taxon`, `taxon_translation`, `taxon_assignment`. Schema per `features/taxonomy/DESIGN.md`. Include indexes from DESIGN, including the partial unique index `(type, code) WHERE code IS NOT NULL`. **No seed data** — the starter ships schema only; demo data lives in `scripts/database/seed.sql` (step 8). Liquibase author `taxon-starter`.
> - **Entities** (`org.ost.taxon.entities`): `Taxon`, `TaxonTranslation`, `TaxonAssignment`. Lombok `@Value @Builder`, `@FieldNameConstants`, JDBC `@Table`, `@Id`, audit annotations where applicable. `Taxon.type` is of type `TaxonType` (the enum from `platform-commons`) and Spring Data JDBC handles the enum-to-VARCHAR conversion automatically.
> - **Repositories** (`org.ost.taxon.repository`): for each entity, a `*CrudRepository extends CrudRepository<T, K>` plus a `@Repository` class with `JdbcClient` for bespoke queries (find translations by entry id, find assignments by entity, usage counts, lookup by type+code). Follow the existing pattern: static final `RowMapper`, text blocks, `@SuppressWarnings("java:S1192")`, `@RequiredArgsConstructor`.
> - **Filter / sort** for the management grid via `query-starter`'s `SqlFilterBuilder` and `OrderByBuilder` — declare them as `private static final` constants in the entry repository.
>
> Note: `TaxonType` enum must be added to `platform-commons/taxon/model/` as part of step 4 (SPI surface). Step 2 references it as an import from `platform-commons`. If step 4 hasn't been done yet, do a minimal scaffold of the enum in `platform-commons` first (just `CATEGORY` value) and complete the rest of the SPI work in step 4.
>
> Acceptance: `mvn -pl taxon-spring-boot-starter clean compile` succeeds; the starter Liquibase migrations run cleanly against a fresh database.
>
> **DB reset script (must land in the same commit so `bash scripts/reset-db.sh` keeps working once the new tables exist):**
> - `/app/scripts/database/reset.sql` — extend the `TRUNCATE TABLE` list with the three new taxon tables, placed before the dependents-already-listed ones (dependency order matters for FK cascades):
>   ```sql
>   TRUNCATE TABLE taxon_assignment, taxon_translation, taxon,
>                  attachment_snapshot, attachment, audit_log, advertisement, user_information
>       RESTART IDENTITY CASCADE;
>   ```

**Notes:** _(filled after execution)_

---

## Step 3 — Services + `TaxonPort` implementation

**Status:** `[ ]`

**Prompt:**
> In `taxon-spring-boot-starter/services`:
>
> - `TaxonService` — CRUD on entries + translations: create with full set of translations, edit one or all translations, soft-delete, restore, list (filterable by `TaxonType`, with `deleted` flag toggle), find by `(type, code)`, find by id. Validates that every supported locale has both name and description before persisting. Validates uniqueness of `(type, locale, name)` excluding the entry being edited.
> - `TaxonAssignmentService` — assign / unassign / replaceAssignments (transactional, with diff computation), getForEntity, usageCount(entryId). On every change, calls the optional `TaxonAuditHook` (`ObjectProvider`, `ifAvailable`).
> - Configuration: `@ConfigurationProperties("taxon")` record `TaxonProperties { Locale defaultLocale = Locale.ENGLISH; List<Locale> supportedLocales = List.of(uk, en); }`.
> - `DefaultTaxonPort implements TaxonPort` — facade over the two services, resolves translations to the requested `Locale` with fallback to `defaultLocale` then first available.
>
> All public method parameters annotated `@NonNull`. No `Optional` as parameter type. No defensive `isEmpty()` checks on inputs. `TaxonType` parameters are passed as the enum, never as a string.
>
> Acceptance: `mvn -pl taxon-spring-boot-starter clean test compile` succeeds. Unit tests for the locale fallback resolver and for the assignment diff logic.

**Notes:** _(filled after execution)_

---

## Step 4 — SPI surface in `platform-commons`

**Status:** `[ ]`

**Prompt:**
> In `platform-commons`:
>
> - `org.ost.platform.taxon.model.TaxonType` — enum with `CATEGORY` as the only initial value. Javadoc explaining that adding new values is a release-level change requiring UI integration. (May already exist as a scaffold from step 2 — complete it here.)
> - `org.ost.platform.taxon.dto.TaxonDto` (id, type, code, name, description, deleted) — `@Value @Builder`. `type` field is the `TaxonType` enum, not a string.
> - `org.ost.platform.taxon.dto.TaxonTranslationDto` (locale, name, description).
> - `org.ost.platform.taxon.spi.TaxonPort` — signatures per `DESIGN.md`, including the batched `getForEntities(EntityType, Set<Long>, Locale)` and the filter helper `findEntityIdsWithAnyTaxon(EntityType, Set<Long>)`.
> - `org.ost.platform.taxon.spi.TaxonUiPort` — five methods: `buildSelector`, `buildChips`, `buildChipsFromData(List<TaxonDto>)` (pre-loaded variant for card lists), `buildFilterField(TaxonType)` (returns a `Component` that is also a `HasValue<?, Set<Long>>`), and `buildManagementContent()`.
> - `org.ost.platform.taxon.spi.TaxonAuditHook` — single method `onAssignmentChanged(EntityType, Long, Long, AssignmentChange)` plus nested enum.
>
> Update the SPI suffix table in `platform-commons/CLAUDE.md` to mention the new Port and Hook (so the convention table stays current).
>
> Wire `DefaultTaxonPort` (step 3) to implement the new interface — adjust types to use the new DTOs and the `TaxonType` enum. Same for `TaxonUiPort` impls planned in step 5.
>
> **Contract test** — `taxon-spring-boot-starter/src/test/java/org/ost/taxon/spi/TaxonPortContractTest.java`:
> - `@SpringBootTest` (or a slice with `@Import(TaxonAutoConfiguration.class)`) verifying the `TaxonPort` and `TaxonUiPort` beans are present in the context and resolve to `DefaultTaxonPort` / `DefaultTaxonUiPort`.
> - For every method declared on `TaxonPort`, assert a non-null result for a happy-path call (use an embedded DB or testcontainers Postgres if needed; otherwise mock the repositories). Goal: catch SPI ↔ impl drift at build time, not at runtime in marketplace.
>
> **Acceptance:**
> 1. `mvn -pl platform-commons,taxon-spring-boot-starter -am clean test compile` green.
> 2. `TaxonPortContractTest` passes — proves SPI surface matches the implementation.

**Notes:** _(filled after execution)_

---

## Step 5 — Vaadin UI: management content, selector, chips

**Status:** `[ ]`

**Prompt:**
> In `taxon-spring-boot-starter/ui`:
>
> - `TaxonManagementContent` — `@SpringComponent @Scope("prototype")`, implements `Configurable<TaxonManagementContent, Parameters>` and `Initialization`. Parameters: `TaxonType` (defaults to `CATEGORY`). UI: title + grid + toolbar (Add, Edit, Delete/Restore, Show deleted toggle). Uses `query-starter` filter / sort UI for the grid.
> - `TaxonEditor` — modal `Dialog` with one Vaadin `Tab` per supported locale. Each tab: required `TextField name`, required `TextArea description`. Save button validates all locales filled, calls `TaxonService.create` or `update`. Has a `Builder` (`ComponentBuilder`) and `Parameters` (`@Builder`: entry id optional, onSave callback, onCancel callback).
>   - **Per-tab validation badge** — when the user clicks Save while on tab A and locale B has empty fields, a generic "fix the errors" message is not enough; the user does not see which tab is broken. Render a `Span` with CSS class `tab-error-badge` inside each `Tab` caption (next to the locale label). Each per-locale `Binder` carries an `addStatusChangeListener(status -> badge.setVisible(status.hasErrors()))`. On Save, call `Binder.validate()` for every locale before invoking the service; if any returns errors, leave the dialog open. The badge gives the user an immediate "tab X has an error" signal without forcing them to click through every tab.
> - `TaxonSelector` — multi-select combo of chips (using Vaadin `MultiSelectComboBox` or custom chip strip). Configured with `(EntityType, Long entityId, TaxonType)`. On value change, calls `TaxonAssignmentService.replaceAssignments`.
> - `TaxonChipsDisplay` — read-only horizontal layout of `Span` chips. Two construction modes: from `(EntityType, Long, TaxonType)` triggering a fetch, or from a pre-loaded `List<TaxonDto>` (used by card lists to avoid N+1).
> - `TaxonFilterField` — multi-select chip combo of all active taxons of a given type, implements `HasValue<?, Set<Long>>` so callers can bind it. Populated from `TaxonPort.getAllByType(type, locale)`.
> - `DefaultTaxonUiPort implements TaxonUiPort` — instantiates the above via `ObjectProvider`s; methods return raw `Component`.
>
> i18n: starter-local `TaxonMessages implements TranslationKey` + `messages_uk.properties` / `messages_en.properties` in starter resources. Use `I18nService.get(TranslationKey)` only — no raw `MessageSource`.
>
> **Acceptance:**
> 1. `mvn -pl taxon-spring-boot-starter clean compile` green.
> 2. `bash scripts/deploy-dev.sh` brings the app up cleanly — no UI is wired yet, but the new beans must instantiate without error during context refresh. Tail the logs and confirm absence of `BeanCreationException` mentioning any taxon class.
>
> _End-to-end UI verification is intentionally deferred to Step 6, where the marketplace wires these components into real routes._

**Notes:** _(filled after execution)_

---

## Step 6 — Marketplace integration: tab, form, view, filter, cards

**Status:** `[ ]`

**Prompt:**
> In `marketplace-app`, integrate every taxonomy surface via `ObjectProvider<TaxonPort>` / `ObjectProvider<TaxonUiPort>` and `ifAvailable(...)`. No file may import anything from `org.ost.taxon.*`; only `org.ost.platform.taxon.*` types are allowed.
>
> **Management view and tab:**
> - Add `TaxonManagementView` (`@Route("reference-data", layout = MainLayout)`) — secured to `ADMIN` and `MODERATOR` via the existing security mechanism (mirror how other admin routes are secured; if no example exists, use `@RolesAllowed`). The view's body delegates to `taxonUiPort.ifAvailable(p -> add(p.buildManagementContent()))`. Empty-state message if the SPI is absent.
> - Add a tab labelled "Reference Data" / "Довідники" to `MainView`, visible only when `RoleChecker.isAtLeastModerator(currentUser)` AND `taxonUiPort.ifAvailable(...).isPresent()`. The tab routes to `TaxonManagementView`.
> - Translation keys added to `CommonMessages` and `messages_*.properties`: `referenceData.tab`, `referenceData.title`, `referenceData.unavailable`.
>
> **Advertisement form / view:**
> - In `AdvertisementFormOverlayModeHandler`, add the selector after the last existing field: `taxonUiPort.ifAvailable(p -> form.add(p.buildSelector(EntityType.ADVERTISEMENT, advertisementId, TaxonType.CATEGORY)))`. Skip in create-before-save mode if `advertisementId` is not yet assigned — decide during implementation whether to render the selector after the first save, or to assign the id earlier.
> - In `AdvertisementViewOverlayModeHandler`, add the chips strip: `taxonUiPort.ifAvailable(p -> view.add(p.buildChips(EntityType.ADVERTISEMENT, advertisementId, TaxonType.CATEGORY)))`.
>
> **Category filter in `AdvertisementQueryBlock`:**
> - Add `Set<Long> categoryIds` field to `AdvertisementFilterDto` (Lombok-built, default empty set, validated `@NotNull` not required — Spring deserialises empty as empty).
> - In `AdvertisementQueryBlock`, inject `ObjectProvider<TaxonUiPort>` and add the filter field via `taxonUiPort.ifAvailable(p -> queryBlock.add(p.buildFilterField(TaxonType.CATEGORY)))`. Bind the field's value into `filter.categoryIds` on change, triggering a refilter.
> - In `AdvertisementFilterMapper`, propagate the field from UI filter DTO to the service-layer filter object.
> - In `AdvertisementService.list(filter, ...)`, resolve `filter.getCategoryIds()` (if non-empty AND `taxonPort.ifAvailable(...).isPresent()`) into a concrete id set via `taxonPort.findEntityIdsWithAnyTaxon(ADVERTISEMENT, categoryIds)`, then push that set into the existing `advertisementIds`-style filter clause. If the resolved set is empty, short-circuit and return an empty page without firing SQL.
> - `AdvertisementRepository` MUST NOT mention any `taxon*` table in its SQL — verify by grep.
>
> **Category chips on cards:**
> - In `AdvertisementCardView` (the card-list view), after `AdvertisementService.list(...)` returns the page: `Map<Long, List<TaxonDto>> taxonsByAdId = taxonPort.ifAvailable(p -> p.getForEntities(EntityType.ADVERTISEMENT, ads.stream().map(Advertisement::getId).collect(toSet()), locale)).orElse(Map.of());`
> - Pass the per-ad list into `AdvertisementCardMetaPanel` via its `Parameters` (new field `List<TaxonDto> categories`).
> - In `AdvertisementCardMetaPanel.configure(...)`, render the chips via `taxonUiPort.ifAvailable(p -> add(p.buildChipsFromData(params.getCategories())))`. If the list is empty, render nothing (no placeholder, no jumping height).
>
> **Acceptance:**
> 1. `bash scripts/deploy-dev.sh` succeeds and the app starts to `Started Application`.
> 2. **Rolling decoupling grep** — both must return empty:
>    ```bash
>    grep -rn "org.ost.taxon" marketplace-app/src/main/java
>    grep -rn "taxon_assignment\|taxon_translation\|FROM taxon\|JOIN taxon" marketplace-app/src/main/java
>    ```
>    Catches accidental coupling immediately rather than at Step 8.
> 3. **Minimal Playwright smoke** — create `/app/playwright/taxonomy-integration-smoke.spec.js` (the bare gate; the full E2E lives in Step 8). Steps:
>    - Login as `user3@example.com` (ADMIN) → confirm Reference Data tab visible in main nav.
>    - Logout, login as `user1@example.com` (USER) → confirm tab NOT visible.
>    - As USER, open the advertisements list → confirm the category filter field is rendered in the query block.
>    - Open any existing advertisement card → confirm a chip strip container exists in the card meta panel (may be empty if no category assigned yet — only the placeholder element is required).
>    - Run via: `bash scripts/playwright.sh taxonomy-integration-smoke`. Must be green.

**Notes:** _(filled after execution)_

---

## Step 7 — Audit hook implementation

**Status:** `[ ]`

**Prompt:**
> In `marketplace-app/services/audit/taxon`:
>
> - `TaxonAuditHookImpl implements TaxonAuditHook` — pure delegation, calls a new method on a marketplace audit service (e.g. `ActivityService.recordTaxonAssignmentChange(EntityType, Long entityId, Long entryId, AssignmentChange)`). No business logic in the hook.
> - In the audit service: produce an `AuditActivityItemDto` capturing the change, with a localised message (`"Added category: %s"` / `"Removed category: %s"`). The category name is resolved at render time via `TaxonPort.findById(entryId, currentLocale)` so renames after the fact reflect in history.
> - Translation keys for the activity messages added to `CommonMessages`.
> - In the advertisement audit history panel, ensure taxon assignment items appear alongside attachment changes (no special wiring should be needed if the activity feed reads all activity items uniformly — verify).
>
> **Hook unit test** — `marketplace-app/src/test/java/.../TaxonAuditHookImplTest.java`:
> - Mock the marketplace audit service.
> - Invoke `onAssignmentChanged(EntityType.ADVERTISEMENT, 42L, 7L, AssignmentChange.ADDED)`.
> - Assert: the audit service was called **exactly once** with the same arguments and nothing else (`verifyNoMoreInteractions`). This enforces the "*HookImpl — pure delegation" rule from `platform-commons/CLAUDE.md`.
>
> **Acceptance:**
> 1. `mvn -pl marketplace-app test` passes including `TaxonAuditHookImplTest`.
> 2. **Rolling decoupling grep** (re-run from Step 6, must still return empty):
>    ```bash
>    grep -rn "org.ost.taxon" marketplace-app/src/main/java
>    grep -rn "taxon_assignment\|taxon_translation\|FROM taxon\|JOIN taxon" marketplace-app/src/main/java
>    ```
> 3. Manual: assign / unassign a category on an ad produces an entry in its activity tab; soft-deleting the category and re-viewing history still renders the name (resolved by id at render time).

**Notes:** _(filled after execution)_

---

## Step 8 — Seed data + Playwright smoke tests + decoupling verification

**Status:** `[ ]`

**Prompt:**
> **No Liquibase seed in the starter** — the starter ships schema only. Decision: "no default category, empty assignment set is a valid state" (see SPEC + DESIGN). All demo data lives in `scripts/database/seed.sql`.
>
> **Dev seed (manual test data, only when `bash scripts/seed-db.sh` is invoked):** in `/app/scripts/database/seed.sql`, append demo categories so the filter and chip surfaces have something to show. Insert pattern (idempotent via `ON CONFLICT (type, code) WHERE code IS NOT NULL DO NOTHING` if the partial unique index supports it; otherwise guard via `NOT EXISTS`):
> - Codes + translations to insert: `ELECTRONICS` (Electronics / Електроніка), `AUTO` (Auto / Авто), `REAL_ESTATE` (Real Estate / Нерухомість), `SERVICES` (Services / Послуги), `JOBS` (Jobs / Робота), `OTHER` (Other / Інше). Each row needs the `description` filled in both locales (one short sentence per locale is enough).
> - Random assignment: after categories are inserted, attach 1-2 random categories to each existing test advertisement via `taxon_assignment` (use `INSERT ... SELECT` with `random()` and a `LIMIT` per ad, or a deterministic `MOD(advertisement.id, n)` scheme — pick whatever stays readable in SQL).
> - Order matters: the inserts must come AFTER the existing `advertisement` insert block in `seed.sql` because assignments reference advertisement ids.
>
> **Playwright tests** in `/app/playwright/`:
> - `reference-data-smoke.spec.js` — login as admin, open Reference Data tab, see the demo entries seeded above, create a new category with both locales (and verify the per-tab error badge by leaving one locale blank, attempting Save, confirming the badge appears on the empty tab), edit it, soft-delete it, verify it disappears, toggle "show deleted", verify it reappears, restore it.
> - `advertisement-categories.spec.js` — login as user, create an advertisement without selecting a category, confirm the card renders with **no** chip strip (zero-category state is intentional). Edit the ad, attach two categories, confirm both chips visible on the card and in the detail view. Open the category filter, pick one of them, confirm the list narrows down to ads carrying it. Open audit history, confirm "Added category: <name>" entries.
>
> Acceptance for Playwright: both scenarios pass via `bash scripts/playwright.sh reference-data-smoke` and `bash scripts/playwright.sh advertisement-categories`.
>
> **Decoupling verification** (run after Playwright passes):
> 1. `grep -rn "org.ost.taxon" marketplace-app/src/main/java` → MUST return nothing (marketplace references only `org.ost.platform.taxon`).
> 2. `grep -rn "taxon_assignment\|taxon_translation\|FROM taxon\|JOIN taxon" marketplace-app/src/main/java` → MUST return nothing (no starter tables in marketplace SQL).
> 3. Comment out the `taxon-spring-boot-starter` dependency in `marketplace-app/pom.xml` and run `mvn -pl marketplace-app -am clean package -DskipTests`. **Must succeed.**
> 4. With the starter still excluded, run `bash scripts/deploy-dev.sh` and confirm the app starts, login works, the advertisements list loads, an ad can be opened and edited. The Reference Data tab, category filter, and chips must be absent — but nothing else changes.
> 5. Restore the pom entry, rebuild, confirm the surfaces reappear.
>
> Record the verification outcome under **Notes** below.

**Notes:** _(filled after execution)_

---

## After all steps complete

- Update `marketplace-app/DECISIONS.md` only if integration in `AdvertisementService` / `AdvertisementCardView` exposed anything non-obvious (e.g. how `categoryIds` collapses into `advertisementIds` before the SQL fires).
- Update `platform-commons/DECISIONS.md` if any new SPI conventions emerged.
- Update `taxon-spring-boot-starter/DECISIONS.md` with anything notable that came up during implementation.
- Run full Playwright suite: `bash scripts/playwright.sh`.
- Run `mvn clean test` to confirm unit tests pass.
- Mark the status overview at the top of this file fully `[x]`.

---

## Verification cheat sheet

Quick-reference commands the executor can copy-paste between steps. Mapped per step in the table below.

### Commands

```bash
# Full reactor compile (covers Step 1, 4, 5)
mvn -pl taxon-spring-boot-starter,marketplace-app -am clean compile

# Unit + contract tests (Step 3, 4, 7)
mvn -pl taxon-spring-boot-starter,marketplace-app -am test

# Rolling decoupling grep (run from Step 6 onwards — both must return empty)
grep -rn "org.ost.taxon" marketplace-app/src/main/java && echo "FAIL: marketplace imports starter internals" || echo "OK"
grep -rn "taxon_assignment\|taxon_translation\|FROM taxon\|JOIN taxon" marketplace-app/src/main/java && echo "FAIL: starter tables in marketplace SQL" || echo "OK"

# Start the app (Step 1, 2, 5, 6)
bash scripts/deploy-dev.sh

# Playwright (Step 6 minimal smoke, Step 8 full E2E)
bash scripts/playwright.sh taxonomy-integration-smoke
bash scripts/playwright.sh reference-data-smoke
bash scripts/playwright.sh advertisement-categories

# Full decoupling proof — Step 8 only
# 1. Comment out the taxon-spring-boot-starter <dependency> in marketplace-app/pom.xml
mvn -pl marketplace-app -am clean package -DskipTests   # must still succeed
bash scripts/deploy-dev.sh                              # app must still start & login work
# 2. Restore the dependency line, rebuild, confirm taxonomy surfaces are back
```

### What to run after each step

| Step | Verification commands |
|---|---|
| 1 | reactor compile · `deploy-dev.sh` (clean startup) |
| 2 | `mvn -pl taxon-spring-boot-starter compile` · `deploy-dev.sh` (Liquibase runs on fresh DB) |
| 3 | `mvn -pl taxon-spring-boot-starter test` (resolver + diff unit tests) |
| 4 | `mvn -pl platform-commons,taxon-spring-boot-starter test` (incl. `TaxonPortContractTest`) |
| 5 | reactor compile · `deploy-dev.sh` (no BeanCreationException for taxon beans) |
| 6 | `deploy-dev.sh` · **rolling decoupling grep** · `playwright.sh taxonomy-integration-smoke` |
| 7 | `mvn -pl marketplace-app test` (hook delegation test) · **rolling decoupling grep** |
| 8 | `playwright.sh reference-data-smoke` · `playwright.sh advertisement-categories` · full decoupling proof (remove pom dep, rebuild, deploy, restore) |
