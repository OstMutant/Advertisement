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
| 5 | Marketplace integration: tab, form, view, filter, cards | `[ ]` |
| 6 | Audit hook implementation | `[ ]` |
| 7 | Seed + Playwright + decoupling verification | `[ ]` |

---

## Step 1 — Module skeleton + autoconfiguration

**Status:** `[ ]`

**Prompt:**
> Create a new Maven module `taxon-spring-boot-starter` at the repository root, mirroring the structure of `attachment-spring-boot-starter`.
>
> - Add it to the parent `pom.xml` as a module.
> - `taxon-spring-boot-starter/pom.xml`: depend on `platform-commons`, `query-lib`, `spring-boot-starter`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, `liquibase-core`, Lombok. Mirror versions and `<parent>` from the attachment starter. **No Vaadin dependency** — this starter is pure domain/data.
> - Java package root: `org.ost.taxon`. Create empty packages `config`, `entities`, `repository`, `services`.
> - `org.ost.taxon.config.TaxonAutoConfiguration`:
>   - `@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration")`
>   - `@ComponentScan(basePackageClasses = TaxonAutoConfiguration.class)`
>   - `@EnableJdbcRepositories(basePackages = "org.ost.taxon.repository")`
>   - Liquibase bean for `classpath:db/taxon-changelog/master.xml` (mirror the attachment starter's pattern).
> - `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` listing `org.ost.taxon.config.TaxonAutoConfiguration`.
> - `src/main/resources/db/taxon-changelog/master.xml` referencing the future single file `001-taxon.xml`.
> - Add a starter-level `DECISIONS.md` seeded with one initial entry titled **"Filter resolves through `Set<Long>`, not SQL JOIN"**. Body records: (a) decision — `findEntityIdsWithAnyTaxon` returns an in-memory id set that marketplace pushes into the existing `advertisementIds` filter clause, not a SQL JOIN; (b) **why** — keeps marketplace SQL free of starter table names, preserving compile-time decoupling; (c) **trigger to revisit** — if a single category routinely resolves to >10k advertisement ids in production, or if multi-category AND-semantics is ever requested, switch to a starter-owned JOIN helper or a pre-computed materialised mapping.
> - Add the new starter as a `runtime` dependency to `marketplace-app/pom.xml`, same shape as attachment.
>
> **Dockerfile:**
> - In the "Copy module POMs" block: `COPY taxon-spring-boot-starter/pom.xml taxon-spring-boot-starter/`
> - In the "Copy the source code" block: `COPY taxon-spring-boot-starter/src ./taxon-spring-boot-starter/src`
>
> **Acceptance:**
> 1. `mvn -pl taxon-spring-boot-starter,marketplace-app -am clean compile` succeeds.
> 2. `bash scripts/deploy-dev.sh` starts the app to the `Started Application` line with no auto-config errors.
> 3. Login page still loads at `http://localhost:8081/login`.

**Notes:** _(filled after execution)_

---

## Step 2 — Schema, entities, repositories

**Status:** `[ ]`

**Prompt:**
> In `taxon-spring-boot-starter`:
>
> - **Liquibase** — single consolidated changeset file `db/taxon-changelog/001-taxon.xml`. Creates three tables: `taxon`, `taxon_translation`, `taxon_assignment`. Schema per `features/taxonomy/DESIGN.md`. Include all indexes from DESIGN, including the partial unique index `(type, code) WHERE code IS NOT NULL`. No seed data. Liquibase author `taxon-starter`.
> - **Entities** (`org.ost.taxon.entities`): `Taxon`, `TaxonTranslation`, `TaxonAssignment`. Lombok `@Value @Builder`, `@FieldNameConstants`, JDBC `@Table`, `@Id`, audit annotations where applicable. `Taxon.type` is of type `TaxonType` (the enum from `platform-commons`).
> - **Repositories** (`org.ost.taxon.repository`): for each entity, a `*CrudRepository extends CrudRepository<T, K>` plus a `@Repository` class with `JdbcClient` for bespoke queries. Static final `RowMapper`, text blocks, `@SuppressWarnings("java:S1192")`, `@RequiredArgsConstructor`.
> - **Filter / sort** for the management grid via `query-lib`'s `SqlFilterBuilder` and `OrderByBuilder` — declare as `private static final` constants in the taxon repository.
>
> Note: `TaxonType` must exist in `platform-commons` before this step compiles. If step 4 hasn't been done yet, add a minimal scaffold of the enum (just `CATEGORY` value) first.
>
> **DB reset:** in `/app/scripts/database/reset.sql`, extend the `TRUNCATE TABLE` list with the three new tables (before their dependents, in FK-safe order):
> ```sql
> TRUNCATE TABLE taxon_assignment, taxon_translation, taxon,
>                attachment_snapshot, attachment, audit_log, advertisement, user_information
>     RESTART IDENTITY CASCADE;
> ```
>
> **Acceptance:** `mvn -pl taxon-spring-boot-starter clean compile` succeeds; Liquibase migrations run cleanly against a fresh database.

**Notes:** _(filled after execution)_

---

## Step 3 — Services + `TaxonPort` implementation

**Status:** `[ ]`

**Prompt:**
> In `taxon-spring-boot-starter/services`:
>
> - `TaxonService` — CRUD on entries + translations: create with full set of translations, edit translations, soft-delete, restore, list (filterable by `TaxonType`, with deleted flag toggle), find by `(type, code)`, find by id. Validates that every supported locale has both name and description before persisting. Validates uniqueness of `(type, locale, name)` excluding the entry being edited.
> - `TaxonAssignmentService` — assign / unassign / replaceAssignments (transactional, with diff computation), getForEntity, usageCount(taxonId). On every change, calls the optional `TaxonAuditHook` via `ObjectProvider.ifAvailable(...)`.
> - `TaxonProperties` — `@ConfigurationProperties("taxon")` record: `Locale defaultLocale = Locale.ENGLISH; List<Locale> supportedLocales = List.of(uk, en)`.
> - `DefaultTaxonPort implements TaxonPort` — facade over the two services, resolves translations to the requested `Locale` with fallback to `defaultLocale` then first available. Pure delegation — no business logic in the port itself.
>
> All public method parameters annotated `@NonNull`. No `Optional` as parameter type. No defensive `isEmpty()` checks on inputs.
>
> **Acceptance:** `mvn -pl taxon-spring-boot-starter clean test` succeeds. Unit tests for the locale fallback resolver and for the assignment diff logic.

**Notes:** _(filled after execution)_

---

## Step 4 — SPI surface in `platform-commons`

**Status:** `[ ]`

**Prompt:**
> In `platform-commons`:
>
> - `org.ost.platform.taxon.model.TaxonType` — enum with `CATEGORY`. Javadoc explaining that adding new values is a release-level change.
> - `org.ost.platform.taxon.dto.TaxonDto` — `@Value @Builder`: `id`, `type` (TaxonType), `code`, `name`, `description`, `deleted`.
> - `org.ost.platform.taxon.dto.TaxonTranslationDto` — `@Value @Builder`: `locale`, `name`, `description`.
> - `org.ost.platform.taxon.spi.TaxonPort` — signatures per `features/taxonomy/DESIGN.md`.
> - `org.ost.platform.taxon.spi.TaxonAuditHook` — `onAssignmentChanged(EntityType, Long, Long, AssignmentChange)` + nested enum.
> - **No `TaxonUiPort`** — all UI lives in marketplace-app and calls `TaxonPort` directly.
>
> Update `platform-commons/CLAUDE.md`: add `taxon.*` to the package semantics table (`taxon.spi`, `taxon.dto`, `taxon.model`).
>
> Wire `DefaultTaxonPort` (step 3) to implement the new interface — adjust types to use the new DTOs and the `TaxonType` enum.
>
> **Contract test** — `taxon-spring-boot-starter/src/test/java/org/ost/taxon/spi/TaxonPortContractTest.java`:
> - `@SpringBootTest` (or a slice) verifying the `TaxonPort` bean is present in the context and resolves to `DefaultTaxonPort`.
> - For key methods on `TaxonPort`, assert a non-null result for a happy-path call.
>
> **Acceptance:**
> 1. `mvn -pl platform-commons,taxon-spring-boot-starter -am clean test` green.
> 2. `TaxonPortContractTest` passes.

**Notes:** _(filled after execution)_

---

## Step 5 — Marketplace integration: tab, form, view, filter, cards

**Status:** `[ ]`

> **Note:** All UI components for taxonomy are written in `marketplace-app` from scratch. No delegation via `TaxonUiPort`. All calls go through `ObjectProvider<TaxonPort>` with `ifAvailable(...)`. Marketplace source must import only from `org.ost.platform.taxon.*` — never from `org.ost.taxon.*`.

**Substep 5a — Reference Data management view (UI to be designed before implementation):**

The exact layout and interaction model for the Reference Data management tab will be designed in a separate design discussion before this substep is implemented. The following components are expected:
- `TaxonManagementView` (`@Route("reference-data")`) in `ui/views/main/tabs/referencedata/` — secured to `ADMIN` and `MODERATOR`.
- Editor for creating / editing a category with locale tabs (EN / UK), validation badges per tab.
- Grid with soft-delete / restore / "show deleted" toggle.
- Tab added to `MainView`, visible only to `ADMIN` / `MODERATOR`.

**Substep 5b — Advertisement form / view:**

> - In `AdvertisementFormOverlayModeHandler`: add a category multi-select after the last existing field, wired to `taxonPort.ifAvailable(p -> form.add(buildCategorySelector(p, entityId)))`. The selector calls `TaxonPort.replaceAssignments` on change.
> - In `AdvertisementViewOverlayModeHandler`: add a read-only chip strip, wired to `taxonPort.ifAvailable(p -> view.add(buildCategoryChips(p, entityId, locale)))`.

**Substep 5c — Category filter in AdvertisementQueryBlock:**

> - Add `Set<Long> categoryIds` field to `AdvertisementFilterDto` (Lombok, default empty set).
> - In `AdvertisementQueryBlock`, add a multi-select chip combo populated with `TaxonPort.getAllByType(CATEGORY, locale)` via `taxonPort.ifAvailable(...)`. Bind value into `filter.categoryIds` on change.
> - In `AdvertisementFilterMapper`, propagate `categoryIds`.
> - In `AdvertisementService.list(filter, ...)`, resolve `filter.getCategoryIds()` via `taxonPort.findEntityIdsWithAnyTaxon(ADVERTISEMENT, categoryIds)` and push into the existing `advertisementIds` clause. Empty resolved set → short-circuit with empty page.
> - `AdvertisementRepository` MUST NOT reference any `taxon*` table — verify by grep.

**Substep 5d — Category chips on cards:**

> - In `AdvertisementCardView`, after `AdvertisementService.list(...)` returns: call `taxonPort.ifAvailable(p -> p.getForEntities(ADVERTISEMENT, adIds, locale)).orElse(Map.of())` once per page.
> - Pass the per-ad `List<TaxonDto>` down to each card component via its `Parameters`.
> - In the card component, render chips from the pre-loaded list. Empty list → render nothing.

**Acceptance:**
> 1. `bash scripts/deploy-dev.sh` succeeds and app starts.
> 2. Rolling decoupling grep — both must return empty:
>    ```bash
>    grep -rn "org.ost.taxon" marketplace-app/src/main/java
>    grep -rn "taxon_assignment\|taxon_translation\|FROM taxon\|JOIN taxon" marketplace-app/src/main/java
>    ```
> 3. `bash scripts/playwright.sh smoke --ux` — existing smoke tests must still pass.

**Notes:** _(filled after execution)_

---

## Step 6 — Audit hook implementation

**Status:** `[ ]`

**Prompt:**
> In `marketplace-app/services/audit/taxon`:
>
> - `TaxonAuditHookImpl implements TaxonAuditHook` — pure delegation, calls one method on a marketplace audit service: `TaxonActivityService.recordAssignmentChange(EntityType, Long entityId, Long taxonId, AssignmentChange)`. No business logic in the hook.
> - `TaxonActivityService` — produces an `AuditActivityItemDto` capturing the change, with a localised message ("Added category: %s" / "Removed category: %s"). Category name resolved at render time via `TaxonPort.findById(taxonId, currentLocale)`.
> - Translation keys for the activity messages added to `I18nKey`.
>
> **Hook unit test** — `marketplace-app/src/test/java/.../TaxonAuditHookImplTest.java`:
> - Mock `TaxonActivityService`.
> - Invoke `onAssignmentChanged(EntityType.ADVERTISEMENT, 42L, 7L, AssignmentChange.ASSIGNED)`.
> - Assert: `TaxonActivityService` called exactly once with the same arguments; `verifyNoMoreInteractions`.
>
> **Acceptance:**
> 1. `mvn -pl marketplace-app test` passes including `TaxonAuditHookImplTest`.
> 2. Rolling decoupling grep (re-run, must still return empty).

**Notes:** _(filled after execution)_

---

## Step 7 — Seed data + Playwright smoke tests + decoupling verification

**Status:** `[ ]`

**Prompt:**
> **Dev seed** — in `/app/scripts/database/seed.sql`, append demo categories (idempotent via `ON CONFLICT`):
> - Codes + translations: `ELECTRONICS` (Electronics / Електроніка), `AUTO` (Auto / Авто), `REAL_ESTATE` (Real Estate / Нерухомість), `SERVICES` (Services / Послуги), `JOBS` (Jobs / Робота), `OTHER` (Other / Інше). Each with a short description in both locales.
> - Random assignment: attach 1-2 categories to each existing test advertisement via `taxon_assignment`. Use `INSERT ... SELECT` with deterministic `MOD(advertisement.id, n)` to stay readable. Must come AFTER the advertisement inserts.
>
> **Playwright tests** in `/app/playwright/`:
> - `taxonomy/reference-data-smoke.spec.js` — login as admin, open Reference Data tab, see seeded entries, create a new category with both locales (verify per-tab error badge by leaving one locale blank), edit it, soft-delete, verify disappears, toggle "show deleted", verify reappears, restore.
> - `taxonomy/advertisement-categories.spec.js` — login as user, create an ad without selecting a category, confirm no chip strip (zero-category state intentional). Edit the ad, attach two categories, confirm chips visible on card and in detail view. Use category filter, confirm list narrows. Open audit history, confirm "Added category: <name>" entries.
>
> **Decoupling verification:**
> 1. `grep -rn "org.ost.taxon" marketplace-app/src/main/java` → must return nothing.
> 2. `grep -rn "taxon_assignment\|taxon_translation\|FROM taxon\|JOIN taxon" marketplace-app/src/main/java` → must return nothing.
> 3. Comment out `taxon-spring-boot-starter` in `marketplace-app/pom.xml`, run `mvn -pl marketplace-app -am clean package -DskipTests` — must succeed.
> 4. With starter excluded: `bash scripts/deploy-dev.sh`, confirm app starts, login works, ads list loads, ad can be opened and edited. Reference Data tab, category filter, chips must be absent — nothing else changes.
> 5. Restore pom entry, rebuild, confirm surfaces reappear.
>
> **Acceptance:** both Playwright scenarios pass; decoupling verification steps 1–5 pass.

**Notes:** _(filled after execution)_

---

## After all steps complete

- Update `marketplace-app/DECISIONS.md` if `categoryIds → advertisementIds` collapse or card batching exposes anything non-obvious.
- Update `platform-commons/DECISIONS.md` if new SPI conventions emerged.
- Update `taxon-spring-boot-starter/DECISIONS.md` with notable implementation decisions.
- Run full Playwright suite: `bash scripts/playwright.sh --ux`.
- Run `mvn clean test` to confirm unit tests pass.
- Mark the status overview at the top of this file fully `[x]`.

---

## Verification cheat sheet

```bash
# Full reactor compile
mvn -pl taxon-spring-boot-starter,marketplace-app -am clean compile

# Unit + contract tests
mvn -pl taxon-spring-boot-starter,marketplace-app -am test

# Rolling decoupling grep (run from Step 5 onwards — both must return empty)
grep -rn "org.ost.taxon" marketplace-app/src/main/java && echo "FAIL" || echo "OK"
grep -rn "taxon_assignment\|taxon_translation\|FROM taxon\|JOIN taxon" marketplace-app/src/main/java && echo "FAIL" || echo "OK"

# Dev deploy
bash scripts/deploy-dev.sh

# Playwright
bash scripts/playwright.sh taxonomy/reference-data-smoke --ux
bash scripts/playwright.sh taxonomy/advertisement-categories --ux

# Full decoupling proof (Step 7 only)
# 1. Comment out taxon-spring-boot-starter in marketplace-app/pom.xml
mvn -pl marketplace-app -am clean package -DskipTests   # must succeed
bash scripts/deploy-dev.sh                              # app must still start
# 2. Restore dep, rebuild, confirm taxonomy surfaces are back
```

### What to run after each step

| Step | Verification commands |
|---|---|
| 1 | reactor compile · `deploy-dev.sh` (clean startup) |
| 2 | `mvn -pl taxon-spring-boot-starter compile` · `deploy-dev.sh` (Liquibase runs) |
| 3 | `mvn -pl taxon-spring-boot-starter test` (resolver + diff unit tests) |
| 4 | `mvn -pl platform-commons,taxon-spring-boot-starter test` (incl. `TaxonPortContractTest`) |
| 5 | `deploy-dev.sh` · **rolling decoupling grep** · `playwright.sh smoke --ux` |
| 6 | `mvn -pl marketplace-app test` (hook delegation test) · **rolling decoupling grep** |
| 7 | `playwright.sh taxonomy/reference-data-smoke --ux` · `playwright.sh taxonomy/advertisement-categories --ux` · full decoupling proof |
