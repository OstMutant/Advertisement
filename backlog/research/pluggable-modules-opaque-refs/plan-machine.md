# Plan for executors: pluggable-modules-opaque-refs

Implementation batches for the report in `plan-human.md` (part H). **None is executed.** Each step
runs only after the person approved that batch, following `.claude/rules.md`'s Approval Rule (plan
in the task file, two-layer plan, literal question, `git add` after each change, no commit without
being asked). Before any step: re-read `/app/CLAUDE.md`, `/app/.claude/rules.md` and the
`.claude/rules/<module>.md` of every module the step touches.

Shared facts every step may rely on (verified 2026-11-09):

- Starters reach the app only through `marketplace-orchestrator/pom.xml` (`<scope>runtime</scope>`
  for all ten starters); `integration-tests/pom.xml` depends on all of them directly.
- `ComponentFactory<T>` (`platform-commons/src/main/java/org/ost/platform/core/ComponentFactory.java`)
  wraps `ObjectProvider<T>`: `get()` fails when absent, `findIfAvailable()`/`ifAvailable()` degrade.
- Pattern for a consumer-declared factory, already in
  `advertisement-spring-boot-starter/src/main/java/org/ost/advertisement/config/AdvertisementAutoConfiguration.java`:
  `@Bean @ConditionalOnMissingBean public ComponentFactory<TaxonPort> taxonPortFactory(ObjectProvider<TaxonPort> p) { return new ComponentFactory<>(p); }`
- Disposable measurement scripts and plans: `backlog/research/pluggable-modules-opaque-refs/measurements/`.

## Steps

### Step 1 -- Pluggability test, one Spring context per module profile

- **Depends on:** nothing.
- **Executor:** opus (test design: which auto-configurations, which stubs, how to isolate Liquibase per profile).
- **Files:** one new test class under `integration-tests/src/test/java/org/ost/integrationtests/level2/`
  (name and sub-package per `.claude/rules/integration-tests.md`); support code, if any, under
  `integration-tests/src/main/java/org/ost/integrationtests/support/`. No production file.
- **Input:** `integration-tests/src/main/java/org/ost/integrationtests/AbstractPostgresIntegrationTest.java`
  (shared Testcontainers Postgres), `RepositoryTestSupport.java` and `RepositoryTestAutoConfig.java`
  (why the explicit auto-configuration list is used instead of `@EnableAutoConfiguration`), every
  `*AutoConfiguration` listed in each starter's
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
  `marketplace-orchestrator/src/main/java/org/ost/orchestrator/config/OrchestratorAutoConfiguration.java`.
- **Exact behavior:**
  - Use `ApplicationContextRunner` with `AutoConfigurations.of(...)`: the Spring Boot JDBC,
    Liquibase and transaction auto-configurations already in `RepositoryTestAutoConfig`, every
    starter auto-configuration and `OrchestratorAutoConfiguration`. A profile = this list minus one
    starter's auto-configuration(s) (or `FilteredClassLoader` over that starter's package; choose one
    and say why in the test's one-line Javadoc).
  - Profiles: all modules; then each of audit, attachment, taxon, provider-profile, apikey, contact,
    feedback, promotion, advertisement absent. User is expected to be required (its ports are
    injected directly, e.g. `UserAccountPort` in orchestrator services): one profile asserts that the
    context **fails** without it, so the limit is documented by a test.
  - Each profile gets its own empty schema or database on the shared container, so the test can
    assert that Liquibase created the tables of the present modules and none of the absent module.
  - Beans that `marketplace-app` provides in production (for example `CurrentActorHook`, the
    forwarder hooks such as `UiLocaleHook`/`UiLabelHook`, `AuditorAware<Long>`) are provided by the
    test as minimal stubs; list each one with the class that needs it.
  - Per profile assert: the context started (`hasNotFailed()`); the absent module's tables do not
    exist; smoke calls through orchestrator services that touch the absent module do not throw:
    `AdvertisementReadService.getFiltered/count` with a `categoryIds` filter, `ProviderProfileReadService.getFiltered/count`
    with the promoted filter, `ProviderProfileSaveService.delete` path where reachable without UI.
- **Constraints:** no `@EnableAutoConfiguration`; no change to production code in this step; one-line
  comments only; follow the existing naming of `level2` tests.
- **Expected result:** by reading the wiring, the profiles without audit, attachment, apikey,
  contact, feedback, promotion, provider-profile or advertisement fail with a missing
  `ComponentFactory<...>` bean. Do not commit a red test: report the failing profiles with the exact
  exception lines; they are the input of step 2, and steps 1 and 2 are delivered together.
- **Verification:** `bash scripts/build-and-test.sh --integration --sandbox` (through the activity
  monitor, see `.claude/rules.md` "Scripts"); the "all modules" profile passes.
- **Sources:** https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html,
  https://docs.spring.io/spring-boot/api/java/org/springframework/boot/test/context/FilteredClassLoader.html

### Step 2 -- Consumers declare the ComponentFactory beans they inject

- **Depends on:** step 1 (its failing profiles).
- **Executor:** sonnet.
- **Files:**
  - `taxon-spring-boot-starter/src/main/java/org/ost/taxon/config/TaxonAutoConfiguration.java` -- add `ComponentFactory<AuditPort> auditPortFactory` (used by `TaxonService`).
  - `user-spring-boot-starter/src/main/java/org/ost/user/config/UserAutoConfiguration.java` -- add `ComponentFactory<AuditPort> auditPortFactory` (used by `UserService`, `UserPreferencesService`).
  - `feedback-spring-boot-starter/src/main/java/org/ost/feedback/config/FeedbackAutoConfiguration.java` -- add `ComponentFactory<UserPort> userPortFactory` (used by `FeedbackService`).
  - `marketplace-orchestrator/src/main/java/org/ost/orchestrator/config/OrchestratorAutoConfiguration.java`
    -- add one factory for every `ComponentFactory<XPort>` type injected anywhere in
    `marketplace-orchestrator/src/main`: `AdvertisementPort`, `ApiKeyPort`, `AttachmentAuditPort`,
    `AttachmentPort`, `AuditPort`, `ContactPort`, `FeedbackPort`, `PromotionPlacementPort`,
    `ProviderProfilePort`, `TaxonPort`, `UserPort` (re-check with
    `grep -rhoE "ComponentFactory<\w+>" marketplace-orchestrator/src/main | sort -u`).
  - Also grep `marketplace-app/src/main` and `marketplace-rest-api/src/main` for injected
    `ComponentFactory<XPort>`; if any type is injected there and not covered by the orchestrator,
    report it instead of adding it (marketplace-app has its own rules about port access).
- **Exact behavior:** each new bean is a copy of the pattern above: same bean method name as the
  provider's own declaration (`auditPortFactory`, `userPortFactory`, `advertisementPortFactory`, ...;
  read it from the provider's `*AutoConfiguration`), `@Bean @ConditionalOnMissingBean`, body
  `return new ComponentFactory<>(p);`. No other change. Any `.get()` call that step 1 shows throwing in
  a profile is reported, not changed here (it needs an `isAvailable()` decision per call site; see
  improvement-133 item 11 for the Taxon admin screen case).
- **Verification:** step 1's test passes for every profile except the documented User one;
  `bash scripts/build-and-test.sh --unit --integration --sandbox` green;
  `ArchitectureRulesTest` green (rule `config_classes_are_spring_configuration` and the starter rules).
- **Sources:** https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html

### Step 3 -- Covering index for the Taxon filter lookup

- **Depends on:** nothing. Run only if the person chose "replace the old index" (decision 5); if they
  chose "keep both", drop the `<dropIndex .../>` line and the word "replaces" from the comment.
- **Executor:** haiku.
- **Files:** new `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/002-taxon-assignment-filter-index.xml`;
  edit `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/master.xml`.
- **Input:** `001-taxon.xml` (index `idx_taxon_assignment_taxon_id` on `taxon_assignment(taxon_id)`).
- **Exact change 1 -- create the file with exactly this content:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="taxon-002" author="taxon-starter">

        <!-- Index-only scans for the entity-id filter (taxon_id, entity_type) and the usage count (taxon_id); replaces the taxon_id-only index -->
        <createIndex indexName="idx_taxon_assignment_taxon_type_entity" tableName="taxon_assignment">
            <column name="taxon_id"/>
            <column name="entity_type"/>
            <column name="entity_id"/>
        </createIndex>

        <dropIndex indexName="idx_taxon_assignment_taxon_id" tableName="taxon_assignment"/>

    </changeSet>

</databaseChangeLog>
```

- **Exact change 2 -- in `master.xml`, old text:**

```xml
    <include file="db/taxon-changelog/changes/001-taxon.xml"
             relativeToChangelogFile="false"/>
```

  **new text:**

```xml
    <include file="db/taxon-changelog/changes/001-taxon.xml"
             relativeToChangelogFile="false"/>
    <include file="db/taxon-changelog/changes/002-taxon-assignment-filter-index.xml"
             relativeToChangelogFile="false"/>
```

- **Verification:** `bash scripts/build-and-test.sh --integration --sandbox` green (the taxon
  repository tests run Liquibase on Testcontainers); `.claude/rules/taxon-spring-boot-starter.md`'s
  "Schema" section does not list indexes of `taxon_assignment`, so no doc edit; then run
  `/check-architecture-map` (the Database ERD page reads the changelogs).
- **Expected effect (measured, `measurements/C1b-index.txt`):** lookup 43 -> 22 ms at 31k ids, ~12 -> 2.8 ms
  for a rare category; usage counts 247 -> 415 ms on 2.6M rows.
- **Sources:** https://www.postgresql.org/docs/current/using-explain.html

### Step 4 -- WARN at the revisit threshold of taxon ADR-001

- **Depends on:** nothing.
- **Executor:** haiku.
- **File:** `taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/TaxonAssignmentService.java`
  (already `@Slf4j`).
- **Exact change 1 -- old text:**

```java
public class TaxonAssignmentService {

    private final TaxonAssignmentRepository assignmentRepository;
```

  **new text:**

```java
public class TaxonAssignmentService {

    private static final int FILTER_ID_SET_WARN_THRESHOLD = 10_000;

    private final TaxonAssignmentRepository assignmentRepository;
```

- **Exact change 2 -- old text:**

```java
    public Set<Long> findEntityIdsByTaxonIds(@NonNull EntityType entityType, @NonNull Set<Long> taxonIds) {
        return assignmentRepository.findEntityIdsByTaxonIds(entityType.name(), taxonIds);
    }
```

  **new text:**

```java
    public Set<Long> findEntityIdsByTaxonIds(@NonNull EntityType entityType, @NonNull Set<Long> taxonIds) {
        Set<Long> entityIds = assignmentRepository.findEntityIdsByTaxonIds(entityType.name(), taxonIds);
        if (entityIds.size() > FILTER_ID_SET_WARN_THRESHOLD) {
            log.warn("Taxon filter resolved to a large id set: entityType={}, taxonIds={}, entityIds={}",
                    entityType, taxonIds, entityIds.size());
        }
        return entityIds;
    }
```

- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox` green.
- **Sources:** taxon `DECISIONS.md` ADR-001 "Trigger to revisit" (in-repo).

### Step 5 -- Decision records

- **Depends on:** steps 1-4 done (or explicitly dropped by the person).
- **Executor:** sonnet, through `/record-decision` only (never a hand-written `DECISIONS.md` entry),
  then `bash .claude/nav/scripts/generate-adr-index.sh` in the same change.
- **Changes:**
  1. Annotate taxon ADR-001 (do not supersede): the measured table of `plan-human.md` ("Measured
     SQL"), the instrumented `WARN` of step 4, and the trigger the person chose for variant B
     (decision 2). Stamp `**Verified:** <date>` (its claims still hold in the code).
  2. Annotate app ADR-034: its "Explicitly not touched" paragraph says the advertisement changelog
     still has FK constraints to `user_information`; `01-advertisement-schema.xml` has none (columns
     say "no FK"). Replace with the current fact.
  3. New decision in the module the person names (proposal: `platform-commons`, which owns
     `ComponentFactory`): "a module declares, with `@ConditionalOnMissingBean`, every
     `ComponentFactory<XPort>` it injects; the pluggability test (step 1) enforces it". State the
     rejected alternative: factories declared only by the providing starter.
- **Verification:** `generate-adr-index.sh` output diff contains only the touched ADRs.
- **Sources:** https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html

### Step 6 -- One Taxon resolution per list request

- **Depends on:** the person's decision 3; coordinate with `backlog/tasks/improvement-003-deferred-performance.md` item K
  and `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` item 23 (both cover parts of this).
- **Executor:** opus.
- **Goal:** a list page (UI `AdvertisementsView.refresh()`, `ProvidersView.refresh()`, REST
  `AdvertisementApiController`/`ProviderProfileApiController` list endpoints) resolves the Taxon filter
  once, not once for the page and once for the count.
- **Constraints:** no cross-starter SQL (app ADR-034); `TaxonPort.resolveCategoryAndCityFilter`
  stays a side-effect-free default method (commons ADR-032); `marketplace-orchestrator` keeps its
  role and the "at most two domain Ports per class" rule; no cache with its own setting unless the
  person asks.
- **Open decisions for the executor to present, not take:** (a) one Port method returning the page
  and the total (new record in `platform-commons/.../advertisement/dto` and `providerprofile/dto`),
  or (b) the starter service takes an already-resolved constraint from the orchestrator (moves the
  Taxon call out of the starters, which also removes their only cross-domain Port use), or (c) keep
  two calls. Show the changed classes and contracts per option.
- **Verification:** unit tests for the chosen shape, `AdvertisementServiceCategoryFilterTest`
  adapted, full `--unit --integration` suite, Playwright `e2e --full --ux` (list pages change).

### Step 7 -- Variant B for Advertisement (only when the trigger fires)

- **Depends on:** step 4's `WARN` showing the trigger in real use, and the person's decision 1.
- **Executor:** opus.
- **Goal:** `advertisement_taxon (advertisement_id BIGINT NOT NULL REFERENCES advertisement(id) ON DELETE CASCADE, taxon_id BIGINT NOT NULL, PRIMARY KEY (advertisement_id, taxon_id))`
  plus index `(taxon_id, advertisement_id)` in the Advertisement changelog; the filter becomes
  `EXISTS (SELECT 1 FROM advertisement_taxon t WHERE t.advertisement_id = a.id AND t.taxon_id = ANY(:taxonIds))`
  (one `EXISTS` per AND-ed group: categories, city).
- **Constraints:** no FK to `taxon`; the data copy from `taxon_assignment` runs only when that table
  exists (Liquibase `tableExists` precondition), so Advertisement installs without Taxon; Taxon still
  owns the catalogue, translations and validation of taxon ids; supersede taxon ADR-001, revise
  commons ADR-031/032 and app ADR-079 through `/record-decision`.
- **Open decisions:** where usage counts for the Taxon admin screen come from (a new count method on
  the owner's Port summed in the orchestrator, or Taxon counts only its remaining rows); whether
  Provider Profile follows; how the rare-category plan is kept fast (measured B: 293 ms vs A: 61 ms for
  2.9k ids, backward scan on `created_at`; see `measurements/S2-rare-cat150-2.9k.txt`).
- **Verification:** re-run `measurements/03-functions.sql` scenarios against the new schema; full
  suite and Playwright `e2e --full --ux`.
- **Sources:** https://milanjovanovic.tech/blog/modular-monolith-data-isolation,
  https://docs.spring.io/spring-modulith/reference/runtime.html

## Verification of the whole

1. `bash scripts/build-and-test.sh --unit --integration --sandbox` green (through
   `scripts/activity-monitor.sh`, per `.claude/rules.md`).
2. Step 1's pluggability test green for every profile; the User profile fails as documented.
3. Manual check per profile is not needed beyond the test; for one profile (Taxon absent) remove the
   dependency from `marketplace-orchestrator/pom.xml` locally, run `/deploy-and-run`, open the
   advertisement list, revert the pom (no commit of the removal).
4. Playwright `e2e --full --ux` only if step 6 or 7 ran.
5. `/check-architecture-map` after step 3 or 7 (schema changed); ADR index regenerated after step 5.
