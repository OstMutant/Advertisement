# improvement-112: A failing (but present) enrichment port blanks the entire advertisement list instead of degrading

**Type:** improvement — resilience. Found via edge-case review (2026-07-19).
**Module:** `marketplace-orchestrator` (`AdvertisementDisplayEnrichmentService` and `ProviderProfileDisplayEnrichmentService`; the enrichers moved there from `AdvertisementService`)
**Priority:** moved to the very bottom of the backlog 2026-11-04 at the user's request; plan researched, execution not started (was 🟡 medium — a media/user/taxon subsystem hiccup takes down the core browse experience)
**When:** Deferred — batch with any advertisement-service resilience touch; cheap and standalone

## Problem

The three enrichers degrade gracefully when a starter is **absent**, but not when it is
**present and failing**:

```java
return attachmentPortFactory.findIfAvailable()   // absent → orElse(ads): fine
        .map(attachmentPort -> { ... attachmentPort.getMediaSummaries(...) ... })  // present but throws → propagates
        .orElse(ads);
```

If `getMediaSummaries()` (S3/attachment down), `UserPort.findByIds()` (user DB hiccup), or
`TaxonPort.getForEntities()` (taxon timeout) throws, the exception propagates up through
`getFiltered()` to the view's `refresh()` catch-all, which blanks the whole advertisement list and
shows a generic error. So a failure in **decorative** enrichment (thumbnail, author name,
category chips) is treated as fatal to the **core** list — the browse page, the most important
and most public surface, goes blank because a thumbnail lookup failed.

## Suggested fix

Wrap each enricher's port call in its own try/catch that logs and returns the un-enriched `ads` on
failure (same end state as "starter absent"), so a subsystem outage degrades to "ads without
thumbnails / without author names / without category chips" rather than an empty page:

```java
try { ... enrich ... } catch (Exception e) {
    log.warn("Media-summary enrichment failed, rendering list without media", e);
    return ads;
}
```

Optionally surface a subtle, non-blocking hint ("media temporarily unavailable") instead of the
current fatal error path. Keep the `findIfAvailable().orElse(ads)` absent-starter path as-is; this
only adds the present-but-failing path.

## Status (2026-11-04): parked, plan researched, nothing implemented

The task was taken up, a first implementation (a shared guard class catching `RuntimeException` around all ten enricher methods) was written and then reverted by the user's request, and the task was moved to the lowest-priority row of the backlog. Earlier decisions, taken before the research: degrade inside the service for every caller including the REST API; cover `ProviderProfileDisplayEnrichmentService` too; catch `RuntimeException`, warn log with the exception, no throttling, no UI hint; record the policy as a decision in `marketplace-orchestrator`; verify with unit tests and `build-and-test.sh --unit --integration --sandbox`. The research added one finding that conflicts with "every caller": the edit forms and `ProviderProfileSaveService` read category and city through the same enrichment, so a degraded read can feed a later save that silently clears them (decision D1 below). D1 to D4 are not answered yet.

## Plan (researched 2026-11-04, not approved)

### Goal

When a lookup for the extra fields on an advertisement or provider profile (category and city
names, author name and email, photo summary) fails, the list, the detail, the SEO tags and the REST
API still answer, just without those fields. Today one failed lookup turns the whole list into an
error. This plan checks the plan and the code already started for improvement-112, and lists what is
left: tests, the decision record, a few lines of documentation, and the verification run.

### The problem this solves

The browse list is the most public page. Its extra fields come from other parts of the system. If one
of those parts is installed but throws an error, the error goes all the way up and the page shows
nothing. Outside guidance agrees that this is the thing to fix: a page should not "serve no data when
only one of several dependencies is unavailable and partial results can still be returned"
(AWS Well-Architected, REL05-BP01).

Two facts you should know before you decide anything:

- **All three lookups read the same database as the list.** The photo summary reads attachment rows
  and does not call S3. So the task's "S3 is down" example does not reach this code. The failures this
  fix really covers are one subsystem's query failing or timing out, a bug in one module, or no free
  database connection at the moment the lookup starts. When the whole database is down, the list
  fails anyway, and that is correct.
- **Category and city are not only decoration.** The same lookup also fills in the ad's category ids
  and city id, and the edit forms take those values. Details are under Risks.

### The means needed

- The code already written (one small helper and the ten wrapped methods) stays as it is. I checked
  it and it fits the project's rules: Lombok, `@NonNull` checks kept outside the guard, one-line
  comments, no new dependency, no port added. It catches `RuntimeException`, which is the form the
  project's Sonar rule accepts.
- New unit tests for both services and for the helper (mocked lookups, the same style as the
  existing provider-profile test). No new integration or REST-level test: the controller tests mock
  the service, so they could not show anything new, and the integration tests have no way to make one
  lookup fail.
- One decision record in `marketplace-orchestrator` through `/record-decision`. The project has no
  failure policy for "installed but failing" yet. The closest rule is "degrade when a module is not
  installed" (root `CLAUDE.md`, guideline 4).
- One line in the orchestrator rules file for the new helper, and one line in each service's own
  description.
- The build run you chose: `bash scripts/build-and-test.sh --unit --integration --sandbox`.

### What the result will look like

- A failed category, author or photo lookup logs one warning with its stack trace and returns the
  item without those fields. Everything else on the page or in the response is unchanged.
- The REST list and detail answer 200 (and a create or update 201/200) without the missing fields,
  instead of a 500. This is safer for writes too: today a create that is already saved can still
  answer 500 because of the lookup that runs after it, and a client that retries would create the
  ad a second time.
- When the category lookup fails, `categoryIds` is `null`. When the lookup works and nothing is
  assigned, it is an empty list `[]`. That difference is the only signal an API client gets, unless
  you approve D3 below.
- The same applies to provider profiles, including their REST list and detail. The brief did not list
  those endpoints, but they use the same service, so decisions 1 and 2 already cover them.

### Cost

| Part | Executor | Effort | Tokens (estimate) |
|---|---|---|---|
| Service descriptions, rules line | haiku | minutes | 10-15k |
| Unit tests (3 test files) | sonnet | about 30 min | 80-120k |
| REST and MCP descriptions (only if D3 = yes) | haiku | minutes | 15-20k |
| Decision record through `/record-decision` | main thread | about 15 min, with your approval of the draft | 30-40k |
| Regenerate the ADR index and the architecture model, build and test run | main thread plus scripts | 15-25 min of wall time (not measured) | 15-25k |
| Strict reads for the edit paths (only if D1 = B) | sonnet + opus | about 1 h | 60-90k |

The base plan is about 140-215k tokens, or 215-325k with D1 = B and D3 = yes. If every step ran on opus, the token
count would be about the same and the price higher; haiku cannot do the test or edit-path steps
safely, because they need judgment.

### Risks

1. **Lost categories and city after an edit (silent, rare).** The advertisement edit form takes its
   values from the card in the list. If the category lookup failed while the list loaded, the card
   has no categories. If the lookup then recovers, the edit form opens (it loads the category choices
   itself, which most likely fails too while the lookup is still down). Any save then clears the ad's
   categories and city. The provider-profile form has the same path. A REST client that reads (and
   gets `null`) and then sends a full update (PUT) clears them the same way. The provider PATCH
   reads the current state through the same lookup, so a PATCH that only changes the text can clear
   them too, if the lookup recovers in the instant between the read and the save. The advertisement
   PATCH is safe: it reads strictly. Before this change the list failed, so none of this could
   happen. The loss can be undone from the history, because every save is recorded. Decision D1 is
   about this.
2. **A misleading log line inside a save.** `ProviderProfileSaveService` uses the same category lookup
   inside its save and delete transaction. If the lookup fails there, the database transaction is
   already marked for rollback (Spring's default), so the save still fails. Nothing is corrupted, but
   the log says "returning the data without it" and the user gets a general rollback error instead of
   the real cause. D1 = B fixes this too.
3. **Link previews without an image for up to 5 minutes.** The SEO listener caches its result for 5
   minutes. A failed photo lookup is now cached as "no image". Before, nothing was cached. The impact
   is low.
4. **Programming errors get hidden too.** Catching `RuntimeException` also catches an error in our own
   mapping code. Each one is still logged as a warning with its stack trace, and the new tests cover
   the mapping. A narrower catch (database and transaction errors only) is possible, because every
   lookup today reads the database. It would stop protecting the page as soon as a lookup fails in a
   different way. I recommend keeping your decision.
5. **Log volume.** At most three warnings with a stack trace per page view while a lookup is failing.
   No throttling, as you decided. Logback's built-in duplicate filter would mute every repeated
   warning in the whole application after 5, so it does not fit. The project has no metrics yet; a
   failure counter belongs to improvement-036.
6. **Fallbacks can hide failures.** Amazon's Builders' Library warns that fallback paths are hard to
   test and can make an outage wider. Its example is falling back to a second, heavier system. Here
   the fallback is "return what we already have", the simplest path there is, with no second system.
   AWS REL05 asks for exactly this, and also asks that the degraded path be tested. The unit tests in
   this plan cover that.

### Where the project's practice differs from best practice

- **Degrading is a new failure policy.** The project's rule today covers only a module that is not
  installed. No decision covers "installed but failing". Best practice (AWS REL05) treats the failing
  case the same as the missing one for display data. This plan follows your decision 4 and records it
  as a new decision. The record should say clearly that the catch is not a "defensive check" (that
  rule is about trusting method inputs) and not a workaround (the root cause is that a core read
  depends hard on display data, and the catch makes that dependency soft).
- **REST contract.** Common practice for partial data (for example GraphQL: a failed field becomes
  `null` and an error entry tells the client) is to tell the client that a field is missing. The
  project's API says nothing about it. The `Warning` header that once served this purpose is obsolete
  (RFC 9111). The cheapest signal is a documented sentence (D3).
- **The task file has no `## Operational notes` block**, which the project's rules require, and which
  `/record-decision` writes into. It must be added first (step 4 of the executor plan).

### Decisions waiting for the user (D1-D4 unanswered)

**D1. Edit and save paths (conflicts with decision 1, "degrade for every caller").** My proposal: B.

- A: keep it as decided. Record risks 1 and 2 in the decision record. No extra code.
- B: degrade where the data is shown, and read strictly where it is about to be edited or saved.
  `ProviderProfileSaveService` uses a strict variant of the category lookup (this also fixes risk 2
  and the provider PATCH). The advertisement edit form takes the category and city it edits from a
  strict read instead of from the card. Extra cost:
  about 1 hour and 60-90k tokens, one change in a UI form, and two tests updated. The happy path does
  not change, so your decision 5 (no Playwright run) can stay, but a UI file is touched; say if you
  want the Playwright run anyway.
- C: degrade only author and photo, and keep category and city strict everywhere. The smallest
  change, but the list still goes blank when the category lookup fails.

**D2. The 5-minute "no image" cache in link previews (risk 3).** My proposal: accept it and mention
it in the decision record.

**D3. Tell API and MCP clients that a field can be missing.** My proposal: yes. Add one sentence to
the four read operations (advertisement list and detail, provider list and detail) and one shared
sentence to the four matching MCP tool descriptions: "`categoryIds` is `null` rather than a list
when the category and city lookup was temporarily unavailable; the names and the city are then
`null` too, and author or photo fields can be `null` for the same reason. Read again until
`categoryIds` is a list before sending an update." The advice is safe with any answer to D1. Text
only, no code or contract shape change. The alternatives (a `Warning` header, a flag in the body, a
5xx) are compared in the research notes. None of them is worth its cost here.

**D4. The same gap elsewhere (open point 7).** The history view (category names and photo changes in
`AdvertisementAuditEnrichService`) and the author names in the history and in provider reviews
(`UserActorNameService`, used by `FeedbackAccessService`) also fail as a whole when one lookup fails.
They are not the core browse page. My proposal: leave them out of this task and add one entry to the
deferred findings bucket (improvement-133), with your approval.

The open points from the brief, in short: 1 catch type: keep `RuntimeException`; no circuit breaker
(a new dependency, and every lookup is an in-process database call); no metrics until
improvement-036. 2 logging: keep it as decided. 3 REST: 200 with missing fields, plus D3. 4
transactions: see risk 2 and D1. 5 earlier decisions: none conflict; the new record is
`marketplace-orchestrator` ADR-012. 6 tests: unit tests only. 7 other gaps: D4.

## Execution plan

### Before any step

- The project's Approval Rule applies to every step: the main thread presents each step (both
  layers) and waits for a literal "yes" before it dispatches. `git commit` only when the user asks.
- Re-read `/app/.claude/rules.md`, `/app/CLAUDE.md` and `/app/.claude/rules/marketplace-orchestrator.md`
  before starting. Step 5 also needs `/app/.claude/rules/marketplace-rest-api.md` and
  `/app/.claude/rules/marketplace-mcp.md`; step 4 needs `/app/.claude/rules/marketplace-app.md`.
- Decisions D1-D4 in the plan above must be answered first. Steps 3 and 4 run only if D1 = B,
  step 5 only if D3 = yes, step 9 only if D4 = yes. With D1 = A, step 8's ADR records risks 1 and 2
  as accepted. With D1 = C, replace steps 3 and 4 with: remove the `DisplayEnrichmentGuard` wrapping
  from the four category/city methods (two per service) and drop their failure tests from step 2.
- The implementation already in the working tree (`DisplayEnrichmentGuard.java` and the ten wrapped
  methods) is kept unchanged. It was reviewed against the project rules and needs no correction.
- After every file change: `git add <file>` (project rule).

### Steps

#### Step 1 -- service class Javadoc

- Depends on: nothing.
- Files: `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/AdvertisementDisplayEnrichmentService.java`,
  `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ProviderProfileDisplayEnrichmentService.java`
- Executor: haiku.
- Input: the current class Javadoc of each file. The advertisement one wrongly names an
  "Advertisement" port; the class only uses the Taxon and User lookups and the Attachment port.
- Exact change, file 1. Old:
  ```
  /**
   * Assembles the display-only fields of {@link AdvertisementInfoDto} (category/city names, author
   * name/email, media summary) from the Advertisement, Taxon, User, and Attachment ports.
   */
  ```
  New:
  ```
  /**
   * Assembles the display-only fields of {@link AdvertisementInfoDto} (category/city names, author
   * name/email, media summary) from the Taxon, User and Attachment lookups, returning the input
   * without a field group whose lookup throws.
   */
  ```
- Exact change, file 2. Old:
  ```
  /**
   * Assembles the display-only fields of {@link ProviderProfileDto} (category/city names, actor
   * name/email) from the Taxon and User ports.
   */
  ```
  New:
  ```
  /**
   * Assembles the display-only fields of {@link ProviderProfileDto} (category/city names, actor
   * name/email) from the Taxon and User lookups, returning the input without a field group whose
   * lookup throws.
   */
  ```
- Verification: `git diff` shows only these two Javadoc blocks changed.
- Sources: https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_mitigate_interaction_failure_graceful_degradation.html

#### Step 2 -- unit tests

- Depends on: nothing (step 3 adds one more test to the same provider test file, if it runs).
- Files (all in `/app/marketplace-orchestrator/src/test/java/org/ost/orchestrator/services/`):
  new `DisplayEnrichmentGuardTest.java`, new `AdvertisementDisplayEnrichmentServiceTest.java`,
  existing `ProviderProfileDisplayEnrichmentServiceTest.java`.
- Executor: sonnet.
- Input: `DisplayEnrichmentGuard.java`, `AdvertisementDisplayEnrichmentService.java`,
  `ProviderProfileDisplayEnrichmentService.java`, `CategoryAndCitySplit.java`, and the existing
  `ProviderProfileDisplayEnrichmentServiceTest.java` as the style reference (JUnit 5,
  `@ExtendWith(MockitoExtension.class)`, `@Mock` fields, a `newService()` factory, AssertJ, method
  names `method_condition_expectedResult`, one-line class Javadoc, no comments inside tests).
- Exact behavior to test:
  - `DisplayEnrichmentGuardTest`: (a) the supplier's value is returned when it succeeds;
    (b) when the supplier throws a `RuntimeException`, the `original` argument is returned
    (`isSameAs`); (c) an `Error` thrown by the supplier (for example `AssertionError`) propagates
    (`assertThatThrownBy`), which pins "RuntimeException only".
  - `AdvertisementDisplayEnrichmentServiceTest`, with `@Mock TaxonLookupService`,
    `@Mock ActorLookupService`, `@Mock ComponentFactory<AttachmentPort>`, `@Mock AttachmentPort`:
    - For each of the six public enrichers (`enrichWithCategoriesAndCity`,
      `enrichWithCategoryAndCity`, `enrichWithActorInfo`, `enrichWithActor`,
      `enrichWithMediaSummary`, `enrichWithMedia`): the lookup throws, and the input list or DTO is
      returned unchanged (`isSameAs`). Use realistic failures from `spring-tx` (on the module's
      classpath): `org.springframework.dao.DataAccessResourceFailureException` for the taxon and
      user lookups, `org.springframework.transaction.CannotCreateTransactionException` for the
      attachment port.
    - Success: categories and city are mapped from `TaxonLookupService.getForEntities`
      (`TaxonDto.builder()` as in the existing test); author name and email from
      `ActorLookupService.findByIds` (`UserDto` is a record: id, name, email, role, createdAt,
      updatedAt, version); media url, content type and count from `AttachmentPort.getMediaSummaries`,
      and an ad missing from the summaries map gets count 0 (`AttachmentMediaSummaryDto.empty()`).
    - Media starter absent (`findIfAvailable()` returns `Optional.empty()`): the input is returned
      (`isSameAs`) and the port is never called.
    - `enrichSingle`: the taxon lookup throws while the actor and media lookups succeed; the result
      has author and media set and `categoryIds` null, which proves the three guards are
      independent.
  - `ProviderProfileDisplayEnrichmentServiceTest`: keep both existing tests; add one failure test per
    public method (four): the lookup throws, the input is returned (`isSameAs`). Replace the class
    Javadoc with one sentence covering the class, without an ADR number, for example:
    `/** {@link ProviderProfileDisplayEnrichmentService} -- a profile with no city must not throw (see `.claude/nav/adr-index.md`), and a failing lookup returns the profile unchanged. */`
- Constraints: Lombok first; no comments inside test bodies; no ticket or ADR numbers; no new test
  dependency; no Spring context (plain Mockito).
- Verification: `git diff --stat` lists only these three files. They compile and pass in step 11.
  Before that, read each test and confirm each assertion matches the service's real code path.
- Sources: https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_mitigate_interaction_failure_graceful_degradation.html,
  https://builder.aws.com/content/3EuS9Sakq7L3VLQIF3qzfMfke1Y/avoiding-fallback-in-distributed-systems,
  https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/CannotCreateTransactionException.html

#### Step 3 -- (only if D1 = B) strict category/city read inside ProviderProfileSaveService

- Depends on: step 2 (same provider test file).
- Files: `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ProviderProfileDisplayEnrichmentService.java`,
  `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ProviderProfileSaveService.java`,
  `/app/marketplace-orchestrator/src/test/java/org/ost/orchestrator/services/ProviderProfileSaveServiceTest.java`,
  `/app/marketplace-orchestrator/src/test/java/org/ost/orchestrator/services/ProviderProfileDisplayEnrichmentServiceTest.java`
- Executor: sonnet.
- Input: the four files. Facts verified in the code: `ProviderProfileSaveService.enrich()` calls
  `enrichWithCategoryAndCity`. It is reached from `findById`/`findByActorId`, which serve `save()`
  (`loadForEdit` and the audit snapshot, both inside `tx.execute`), `delete()` (inside
  `tx.executeWithoutResult`), `findCurrentSnapshot()` (the provider REST PATCH), and the Vaadin
  provider form, view mode and catalog overlay.
- Exact behavior:
  - In `ProviderProfileDisplayEnrichmentService`, add a package-private method
    `ProviderProfileDto enrichWithCategoryAndCityOrThrow(@NonNull ProviderProfileDto profile, @NonNull Locale locale)`.
    It holds the current unguarded body
    (`applyCategoryAndCityData(profile, taxonLookupService.getForEntity(EntityType.PROVIDER_PROFILE, profile.getId(), locale))`)
    and has a one-line Javadoc saying it lets a lookup failure propagate. The public
    `enrichWithCategoryAndCity` keeps its guard and calls the new method inside the supplier.
  - `ProviderProfileSaveService.enrich()` calls `enrichWithCategoryAndCityOrThrow` instead.
  - `ProviderProfileSaveServiceTest`: the `lenient().when(displayEnrichmentService.enrichWithCategoryAndCity(any(), any()))`
    stub is switched to the new method. Mockito can stub a package-private method from the same
    package.
  - `ProviderProfileDisplayEnrichmentServiceTest`: one test that the `OrThrow` variant propagates the
    lookup's exception.
- Constraints: no new class; no new port; `@NonNull` on the parameters; the class stays within the
  ArchUnit port limit (no new fields).
- Verification: `git grep -n "enrichWithCategoryAndCity" -- marketplace-orchestrator/src/main`
  shows `ProviderProfileSaveService` calling only the `OrThrow` variant. Tests pass in step 11.
- Sources: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html,
  https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/AbstractPlatformTransactionManager.html#setGlobalRollbackOnParticipationFailure(boolean),
  https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_mitigate_interaction_failure_graceful_degradation.html

#### Step 4 -- (only if D1 = B) the advertisement edit form edits strictly-read category and city

- Depends on: step 3 (same pattern; an `OrThrow` variant may be reused).
- Files: `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/overlay/modes/AdvertisementFormOverlayModeHandler.java`;
  possibly `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/AdvertisementDisplayEnrichmentService.java`
  and its test from step 2.
- Executor: opus (a design choice in UI code).
- Goal: the edit DTO's `categoryIds`/`cityTaxonId` never come from a display read that may have
  degraded. A failed lookup must stop the form from opening (the overlay's existing error path)
  rather than open it with empty categories that a save would then clear.
- Facts verified in the code: `activate()` builds the DTO with `mapper.toAdvertisementEdit(params.getAd())`,
  where the ad comes from the list card or `enrichSingle`. `discardChanges()` rebuilds it through
  `advertisementReadService.findById(...).map(enrichSingle)`. The category converter maps null ids
  to an empty selection. `AdvertisementSaveService.save` turns null `categoryIds` into `Set.of()`
  and replaces all assignments. `AdvertisementSaveService.findCurrentSnapshot(id)` already reads
  category and city strictly (it is used by the REST PATCH), but it also reads the attachment
  snapshot id.
- Open decisions for the executor: (a) which strict read to use: `findCurrentSnapshot` (exists,
  but it also makes the form depend on the attachment lookup), or a new public
  `enrichWithCategoryAndCityOrThrow` on `AdvertisementDisplayEnrichmentService`, symmetric with
  step 3; (b) whether the strict read happens in `activate()` and `discardChanges()`, or once where
  the overlay opens edit mode; (c) confirm which existing handler shows the error when the read
  throws while the form opens (`AbstractEntityOverlay` catches around the mode switch, line 92;
  check this).
- Constraints: `marketplace-app` calls orchestrator services only, never a `*Port`; the restore-from-
  history path (`handleRestoreFromActivity`, which overwrites categories from the snapshot) keeps
  working; one-line comments or none; the happy path behaves the same, so the existing Playwright
  flows stay valid. Whether to run Playwright anyway is the user's call (decision 5 says no).
- Verification: the main thread reviews the diff. If a public method was added, add a unit test that
  it propagates. Then run step 11.
- Sources: https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_mitigate_interaction_failure_graceful_degradation.html

#### Step 5 -- (only if D3 = yes) REST and MCP descriptions

- Depends on: nothing.
- Files: `/app/marketplace-rest-api/src/main/java/org/ost/restapi/api/AdvertisementApiController.java`,
  `/app/marketplace-rest-api/src/main/java/org/ost/restapi/api/ProviderProfileApiController.java`,
  `/app/marketplace-mcp/src/main/java/org/ost/mcp/tools/ToolDescriptions.java`,
  `/app/marketplace-mcp/src/main/java/org/ost/mcp/tools/AdvertisementTools.java`,
  `/app/marketplace-mcp/src/main/java/org/ost/mcp/tools/ProviderProfileTools.java`
- Executor: haiku.
- Input: the five files. Every old text below occurs exactly once in its file (checked with
  `grep -c`).
- Exact changes:
  1. `AdvertisementApiController.java`. After the line
     `    private static final String WRITE_PERMISSION = "the owner of the advertisement, an administrator or a moderator may call it, with their API key.";`
     insert a blank line and then:
     ```
         private static final String MISSING_LOOKUP_NOTE = " categoryIds is null rather than a list when the category and city lookup was temporarily unavailable; categoryNames, cityTaxonId and cityName are then null too, and the author and media fields can be null for the same reason. Read again until categoryIds is a list before sending an update.";
     ```
     Then replace `or the shared default for anonymous callers.")` with
     `or the shared default for anonymous callers." + MISSING_LOOKUP_NOTE)`, and replace
     `description = "Public. Returns one advertisement with its ETag, the version to send back via If-Match on update or delete. 404 when the id is unknown.")`
     with
     `description = "Public. Returns one advertisement with its ETag, the version to send back via If-Match on update or delete. 404 when the id is unknown." + MISSING_LOOKUP_NOTE)`.
  2. `ProviderProfileApiController.java`. Directly before the line
     `    // Mirrors ProviderProfileSortMeta's UI-sortable set -- marketplace-rest-api can't import that`
     insert:
     ```
         private static final String MISSING_LOOKUP_NOTE = " categoryIds is null rather than a list when the category and city lookup was temporarily unavailable; categoryNames, cityTaxonId and cityName are then null too, and actorName and actorEmail can be null for the same reason. Read again until categoryIds is a list before sending an update.";

     ```
     Then replace `or the shared default for anonymous callers.")` with
     `or the shared default for anonymous callers." + MISSING_LOOKUP_NOTE)`, and replace
     `description = "Public. Returns one provider profile with its ETag, the version to send back via If-Match on update or delete. 404 when the id is unknown.")`
     with
     `description = "Public. Returns one provider profile with its ETag, the version to send back via If-Match on update or delete. 404 when the id is unknown." + MISSING_LOOKUP_NOTE)`.
  3. `ToolDescriptions.java`. After the line
     `    public static final String TOTAL_COUNT_NOTE = "totalCount is the number of all items matching the filter, across all pages. ";`
     insert a blank line and then:
     ```
         public static final String MISSING_LOOKUP_NOTE = "categoryIds is null rather than a list when the category and city lookup was temporarily unavailable; "
                 + "categoryNames, cityTaxonId and cityName are then null too, and author or media fields can be null for the same reason. "
                 + "Read again until categoryIds is a list before sending an update. ";
     ```
  4. `AdvertisementTools.java`. Replace `+ "settings. " + ToolDescriptions.TOTAL_COUNT_NOTE` with
     `+ "settings. " + ToolDescriptions.TOTAL_COUNT_NOTE + ToolDescriptions.MISSING_LOOKUP_NOTE`. Replace
     ```
                 description = "Returns one advertisement by id with its category and city names, author and media summary. "
                         + "The result has item and etag; the etag is the version a later update or delete must send as If-Match. "
     ```
     with
     ```
                 description = "Returns one advertisement by id with its category and city names, author and media summary. "
                         + ToolDescriptions.MISSING_LOOKUP_NOTE
                         + "The result has item and etag; the etag is the version a later update or delete must send as If-Match. "
     ```
  5. `ProviderProfileTools.java`. Replace `+ "settings. " + ToolDescriptions.TOTAL_COUNT_NOTE` with
     `+ "settings. " + ToolDescriptions.TOTAL_COUNT_NOTE + ToolDescriptions.MISSING_LOOKUP_NOTE`. Replace
     ```
                 description = "Returns one provider profile by id with its category and city names. "
                         + "The result has item and etag; the etag is the version a later update or delete must send as If-Match. "
     ```
     with
     ```
                 description = "Returns one provider profile by id with its category and city names. "
                         + ToolDescriptions.MISSING_LOOKUP_NOTE
                         + "The result has item and etag; the etag is the version a later update or delete must send as If-Match. "
     ```
- Verification: `git grep -n "MISSING_LOOKUP_NOTE"` shows 2 definitions plus 2 uses in each REST
  controller, and 1 definition plus 2 uses in each MCP tool file. Step 11 compiles it. After step 11,
  optionally check `/v3/api-docs` on a running app; this is not required.
- Sources: https://spec.graphql.org/October2021/#sec-Handling-Field-Errors,
  https://www.rfc-editor.org/rfc/rfc9111.html#section-5.5,
  https://alivedise.github.io/backend-engineering-essentials/resilience/graceful-degradation

#### Step 6 -- orchestrator rules: one line for the new helper

- Depends on: nothing.
- File: `/app/.claude/rules/marketplace-orchestrator.md`
- Executor: haiku.
- Input: the "What it owns" list; the `CategoryAndCitySplit` bullet ends with this line (two
  leading spaces):
  ```
    fold the exact same `TaxonType.CATEGORY`/`TaxonType.CITY` split into their own DTO's builder.
  ```
- Exact change: directly after that line, insert:
  ```
  - `DisplayEnrichmentGuard` (package-private utility) — runs one display-only enrichment and returns
    the unenriched input when it throws a `RuntimeException`, logged at warn; shared by
    `AdvertisementDisplayEnrichmentService` and `ProviderProfileDisplayEnrichmentService`.
  ```
  The `paths:` frontmatter is not touched, so the glob check is not needed.
- Verification: `git diff` shows only the three inserted lines.
- Sources: none (project practice: the `CategoryAndCitySplit` entry).

#### Step 7 -- task file bookkeeping

- Depends on: the user's answers to D1-D4.
- File: `/app/backlog/tasks/improvement-112-enrichment-failure-blanks-entire-list.md`
- Executor: main thread (it writes the chosen decisions; haiku could add the block alone).
- Exact change: (a) in "Plan (2026-11-04, awaiting approval)", record the answers to D1-D4 and the
  steps chosen from this plan, and mark items done with the date as they land (Approval Rule);
  (b) the file has no `## Operational notes` block, which the project rules require. Append it at the
  end with the standard keys (`token_cost_review`, `token_cost_research`,
  `token_cost_verification`, `review_signal_ratio`, `context_loading_task_type`,
  `context_loading_consulted`, `context_loading_matched`, `flows_situation`, `flows_chosen`,
  `flows_matched`, `haiku_decomposition_used`, `haiku_decomposition_dispatch_count`,
  `haiku_decomposition_rework_needed`, `adrs_referenced`), each `n/a` or `none`, copied in the
  exact shape `.claude/rules.md` shows. Fill them as facts become known.
- Verification: `grep -n "^## Operational notes" <file>` prints one line.
- Sources: none (project rule).

#### Step 8 -- the decision record

- Depends on: steps 1-7 (so the record states what the code really does).
- Files: `/app/marketplace-orchestrator/DECISIONS.md`, `/app/.claude/nav/adr-index.md` (generated),
  the task file's `adrs_referenced` line.
- Executor: main thread through `/record-decision marketplace-orchestrator — <title>`. The command
  presents its own draft and waits for approval.
- Input: the highest entry is ADR-011, so the new one is ADR-012. The worthiness gate passes: it
  constrains future code (every later display enrichment follows it) and changes a public contract
  (REST responses can miss fields).
- Draft to hand to the command (adjust to the D1-D4 answers):
  - Title: `Display enrichment degrades when a present lookup fails: the core read returns without the display fields`
  - `**Also affects:** marketplace-rest-api, marketplace-app`
  - Context: a core read (advertisement and provider lists, detail, edit form load, SEO tags, REST
    list/detail/create/update) depended hard on display-only lookups (Taxon, User, Attachment). An
    absent starter already degraded through `findIfAvailable()`; a present but failing one blanked
    the whole read.
  - Decision: each public method of `AdvertisementDisplayEnrichmentService` and
    `ProviderProfileDisplayEnrichmentService` runs its lookup through `DisplayEnrichmentGuard`. On a
    `RuntimeException` it logs a warning with the stack trace and returns the input unchanged, for
    every caller, the REST API included (a 200/201 without those fields instead of a 5xx). No
    throttling, no UI hint, no metrics until the project has metrics. A degraded category lookup
    leaves `categoryIds` null, which differs from the empty list of "nothing assigned". [D1 = B:
    reads that feed an edit or a write (`ProviderProfileSaveService`, the advertisement edit form)
    use the `OrThrow` variant, so a failure there stays a failure.] [D1 = A: the edit/write risks
    are accepted: a transient failure can clear categories/city on the next save, recoverable from
    history; inside `ProviderProfileSaveService`'s transaction the save still fails with
    `UnexpectedRollbackException`.] [D3: the REST/MCP descriptions state the null rule.] [D2: the SEO
    listener may cache a "no image" result for its 5-minute TTL.] This is not a defensive input check
    (inputs stay trusted, `@NonNull` stays outside the guard). It is the root-cause fix: the read's
    dependency on display data becomes a soft dependency.
  - Rejected alternatives: degrade only in the UI (the orchestrator would not own the policy); a
    Resilience4j circuit breaker or fallback (new dependency and AOP; every lookup is an in-process
    call on the same database); catching only `DataAccessException` (misses
    `CannotCreateTransactionException`); a `Warning` header (obsolete, RFC 9111); the guard inside
    `TaxonLookupService`/`ActorLookupService` (those also serve write paths).
- Also, opportunistically (project rule): `marketplace-app` ADR-034 and ADR-035 were confirmed
  against `AdvertisementDisplayEnrichmentService` during this research (bulk lookups, read-time
  enrichment). Offer to stamp `**Verified:** <date>` on both, in the same index regeneration.
- After writing: `bash .claude/nav/scripts/generate-adr-index.sh` (mandatory, same change), and add
  `ADR-012 (marketplace-orchestrator)` to the task's `adrs_referenced`.
- Verification: `grep -n "ADR-012" /app/.claude/nav/adr-index.md` shows the row for each module.
- Sources: https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_mitigate_interaction_failure_graceful_degradation.html,
  https://builder.aws.com/content/3EuS9Sakq7L3VLQIF3qzfMfke1Y/avoiding-fallback-in-distributed-systems,
  https://resilience4j.readme.io/docs/circuitbreaker,
  https://rules.sonarsource.com/java/tag/error-handling/RSPEC-2221

#### Step 9 -- (only if D4 = yes) deferred-bucket entry

- Depends on: nothing.
- File: `/app/backlog/tasks/improvement-133-deferred-oversized-review-findings.md`
- Executor: main thread (it needs the bucket's own entry format; read the file first).
- Exact content of the entry: "Present-but-failing lookups outside the core read still fail the
  whole surface: `AdvertisementAuditEnrichService` (taxon names and media changes in the history
  timeline/activity) and `UserActorNameService` (actor names in audit; feedback author names
  through `FeedbackAccessService.resolveAuthorNames`). Same class of defect as the display-enrichment
  degradation policy (see `.claude/nav/adr-index.md`), different surfaces; left out of that change to
  keep it to the core browse read."
- Verification: the entry appears once in the bucket.
- Sources: none.

#### Step 10 -- regenerate the architecture model

- Depends on: steps 1-9.
- Executor: main thread (a script).
- Change: `bash docs/architecture/scripts/generate-architecture-model.sh`. It regenerates the
  pointer-only `DECISIONS.md` of `marketplace-rest-api` from ADR-012's `Also affects` tag, and the
  model that lists the new class. Then `/check-architecture-map`.
- Verification: the check reports no drift; `git status` shows only the expected generated files.

#### Step 11 -- verification of the whole

- Depends on: all steps above.
- Executor: main thread (a script, backgrounded).
- Change: re-read `/app/scripts/CLAUDE.md` and `.claude/nav/flows.md` (project rule), then run
  `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --integration --sandbox` with
  `run_in_background: true`, and watch `/tmp/activity-monitor/build-and-test/tree.txt` with Monitor;
  stop the Monitor at the final result.
- Pass criteria: the build is green; the new tests in `marketplace-orchestrator` are counted and pass
  (report the counts); `ArchitectureRulesTest` and the existing `AdvertisementApiControllerTest`/
  `ProviderProfileApiControllerTest`, `ProviderProfileSaveServiceTest` and MCP tool tests pass
  unchanged (apart from the stub switch in step 3). No Playwright run, per decision 5, unless the
  user asks for one after step 4.
- Then, only after the user asks for the commit: move the task file to `backlog/completed/tasks/`,
  remove its `BACKLOG.md` rows (the priority table line and the "Still open" row), add the
  `✅ Done` line to `backlog/completed/BACKLOG-ARCHIVE.md`, add 1 to `**Applied:**` of every ADR in
  `adrs_referenced` that the task did not create, and regenerate the ADR index.

### Estimated cost

| Step | Executor | Tokens (estimate) |
|---|---|---|
| 1 | haiku | 5-8k |
| 2 | sonnet | 80-120k |
| 3 (D1 = B) | sonnet | 25-35k |
| 4 (D1 = B) | opus | 35-55k |
| 5 (D3 = yes) | haiku | 15-20k |
| 6 | haiku | 4-6k |
| 7-10 | main thread | 40-60k |
| 11 | main thread + script | 10-20k; build wall time not measured (assume 15-25 min) |

Base (steps 1, 2, 6-11): about 140-215k. With D1 = B and D3 = yes: about 215-325k.

## Verification

Unit-test each enricher with a `ComponentFactory` whose port throws → assert the original `ads`
list is returned unchanged (not an exception). `AdvertisementService`'s enrich methods are already
covered by the service-layer unit tests established in improvement-048's pattern.

## Related

- `backlog/tasks/improvement-010-advertisements-view-refresh-error-notification.md` — same
  `refresh()` error surface; that issue adds the missing notification, this one prevents the
  blank-list trigger in the first place. Coordinate so they don't fight over the catch block.
- `marketplace-app/DECISIONS.md` ADR-035 — read-time media enrichment is the design that makes
  this failure path reachable on every list render.
