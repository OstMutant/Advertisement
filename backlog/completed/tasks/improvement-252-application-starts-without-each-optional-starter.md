# improvement-252: The application starts with any optional starter removed from `pom.xml`

**Type:** improvement — a test that proves the main modularity requirement, plus the fix it is likely to need
**Module:** `integration-tests` (a new level), every starter's auto-configuration, `marketplace-orchestrator`, `platform-commons` (`ComponentFactory`)
**Priority:** 🟡 medium-high: it checks the requirement "removing a starter breaks nothing", which nothing checks today
**When:** independent; its result feeds [improvement-250](improvement-250-query-predicate-port-for-unbounded-cross-module-filters.md), whose rule assumes an absent provider yields no predicate

## Current state

Deduced from the code, then checked for the orchestrator on 2026-11-11 (see the result below): a `ComponentFactory<XPort>` bean is declared by the starter that provides `XPort`, while the orchestrator injects `ComponentFactory<XPort>` into its services. With that starter absent the bean may be missing and the Spring context may fail to start. The orchestrator `pom.xml` lists all ten starters at `runtime` scope, so every build includes them; no test starts the context without one. `ArchitectureRulesTest` checks imports, not startup.

## Why change

The design says every optional starter can be removed and the application still boots (`ComponentFactory.findIfAvailable()`, `ifAvailable`). If the deduction is right, that claim is false for some starters and nobody knows which.

## Expected benefit

A test that names exactly which optional starters can be removed, and a fix so that all of them can.

## Approach

1. Verify first: build a throwaway classpath without one optional starter (Audit, Attachment, Advertisement, Provider Profile, API key, Contact, Feedback, Promotion; Taxon and User are mandatory or need a decision) and start the Spring context; record which starters fail and with what error.
2. Fix: the consuming side declares an absent-safe `ComponentFactory<XPort>` (for example a `@ConditionalOnMissingBean` default, the shape `RepositoryTestSupport` already uses in tests), in `marketplace-orchestrator` or one place in `platform-commons` that does not become a persistence or business class.
3. Test: one parameterised test in `integration-tests` that starts the context once per removable starter, asserts it starts, and asserts the feature that needs the removed starter reports itself unavailable instead of failing.
Options and their cost are decided after step 1; record the decision with `/record-decision` if the declaration site moves.

## Result of the check (step 1, 2026-11-11)

Run: a throw-away test in `marketplace-orchestrator` starts the orchestrator's own beans (`OrchestratorAutoConfiguration`, which scans every orchestrator service) with the `ComponentFactory` beans of all starters except one. The factory beans are not written by hand: the test finds every starter's auto-configuration through `AutoConfiguration.imports` and registers what its `@Bean` methods returning `ComponentFactory` declare, so the mapping is the real code's. Beans the application shell or the mandatory user starter provides (`UiLocaleHook`, `CurrentUserHook`, `UiLabelHook`, `SessionActorHook`, `SettingsChangeHook`, `TransactionTemplate`, the four user ports) are Mockito stubs. With nothing removed the context starts.

| Starter removed | Orchestrator context | Factory beans then missing |
|---|---|---|
| audit | fails | `ComponentFactory<AuditPort>` |
| attachment | fails | `ComponentFactory<AttachmentPort>`, `ComponentFactory<AttachmentAuditPort>` |
| advertisement | fails | `ComponentFactory<AdvertisementPort>` |
| provider-profile | fails | `ComponentFactory<ProviderProfilePort>` |
| apikey | fails | `ComponentFactory<ApiKeyPort>` |
| contact | fails | `ComponentFactory<ContactPort>` |
| feedback | fails | `ComponentFactory<FeedbackPort>` |
| promotion | fails | `ComponentFactory<PromotionPlacementPort>` |
| taxon | starts | none: the advertisement and provider-profile starters also declare `ComponentFactory<TaxonPort>` |
| user (mandatory) | fails | `ComponentFactory<UserPort>` |

So the deduction holds for the orchestrator wiring: none of the eight optional starters can be removed, only taxon "survives" because other starters declare its factory as well. The failure is the one the task predicted, `NoSuchBeanDefinitionException` for the absent `ComponentFactory`, at start-up.

Limits of this check, to settle in step 2: it covers the orchestrator only. The application shell, the REST API, the MCP adapter and the starters that inject another starter's factory (the advertisement and feedback services) were not started; a real start of the whole application without one starter is still needed to find a failure that comes from them. The ports behind the factories are absent, not mocked, which is the case the task is about.

## Plan (approved 2026-11-11)

Checked against the project's own rules before writing: the rule already exists in the decision on the typed wrapper over `ObjectProvider` (it says the `@Bean` must exist on every consuming config class, not only in the port's defining starter, and warns that nothing fails at compile time). The orchestrator declares none of the 11 factories it consumes; the taxon and user starters consume the audit factory without declaring it. So the fix follows the decision, it does not change it. Ruled out: a factory in `platform-commons` (that module may hold no Spring bean), one generic injection-point factory and plain `ObjectProvider` fields (the same decision rejects both).

1. Red first (done 2026-11-11: red with 14 missing factories, green after step 2). Add one rule to `ArchitectureRulesTest` (it already discovers the starter packages and reads field generics by reflection): for the orchestrator and every starter, each `ComponentFactory<T>` field type used by a class of that module needs a `@Bean` returning `ComponentFactory<T>` in a configuration class of the same module. Run it, record the violations; they are the exact fix list.
2. Fix (done 2026-11-11: 11 factories in `OrchestratorAutoConfiguration`, the audit factory in the taxon and user starters, the user factory in the feedback starter). In each reported config class add `@Bean @ConditionalOnMissingBean public ComponentFactory<T> xFactory(ObjectProvider<T> p)`, the shape every starter already uses. Expected: `OrchestratorAutoConfiguration` gets 11, the taxon and user starters the audit factory, the feedback starter the user factory; the rule's own list decides. The rule goes green; the orchestrator's unit tests stay green.
3. Real start (done 2026-11-11, `OptionalStarterAbsenceTest`: 19 cases pass; with the orchestrator's factories reverted 7 of the 9 without-a-starter cases fail, so the test does catch the fault). A level-2 test in `integration-tests` (`level2.orchestrator`, the only home for tests with a real Postgres): start the application's beans once per optional starter with that starter's configuration left out (hiding its classes with a filtered class loader was tried and dropped: it breaks the proxies of the Spring Data repositories), assert the context starts and a feature that needs the hidden starter reports itself unavailable. This finds failures outside the orchestrator (REST, MCP, the other starters), which the first check did not cover.
4. Done 2026-11-11. Remove the throw-away probe test in `marketplace-orchestrator` once steps 1 to 3 hold; its question is answered by the rule and the real start.
5. Documentation done 2026-11-11 except the reviews: the matching constraint in the orchestrator rules file and the decision's enforcement note (via `/record-decision`'s rules, regenerate the ADR index); `/sync-docs` and `/deep-review` before saving.

Code rules that apply to every new line: Lombok first, `@NonNull` on public parameters, class-level Javadoc, one-line comments, no task or ADR numbers in code.

## Result of the REST sweep (2026-11-11)

A throw-away test starts the REST context against a real Postgres (orchestrator, starters, REST adapter, security) once with every starter and once per optional starter without it, and calls every REST operation (71) as an administrator, every path variable an id that does not exist, an empty JSON body. The full application has no operation that fails.

| Starter removed | Operations that fail where the full application does not |
|---|---|
| audit, attachment, advertisement, provider-profile, contact, promotion, taxon | none |
| apikey | `POST /api/api-keys`: unhandled `NoSuchBeanDefinitionException` (the full application answers 401) |
| feedback | the four moderation operations (approve and confirm a violation, for a review and for a reply): the same unhandled exception (the full application answers 404) |

Cause: the orchestrator reads through `findIfAvailable()` or `ifAvailable()` in 99 places and degrades correctly, but writes through `ComponentFactory.get()` in 40 places (feedback 14, attachment 9, advertisement save 7, provider-profile save 4, API key 3, taxon catalog 2, contact 1), and the REST adapter has no handling of an absent feature. An empty body stops most write operations at validation, so the sweep reached 5 of those paths; the real number is larger.

## Plan for the fix of the sweep findings (option 1 chosen 2026-11-11)

A write to a feature whose starter is absent answers 503 with a message naming what is missing, instead of an unhandled `NoSuchBeanDefinitionException`.

1. Done 2026-11-11. `platform-commons`, package `org.ost.platform.core`, beside the other typed exceptions: `OptionalComponentUnavailableException` (a `RuntimeException` that carries the missing type in a typed field, no cast anywhere, class Javadoc stating why it is a type of its own: callers map it without reading a message).
2. Done 2026-11-11. `ComponentFactory.get()` keeps calling `provider.getObject()` and turns a plain missing bean into that exception, naming the missing type; `NoUniqueBeanDefinitionException` (several candidates, a real wiring fault) is rethrown unchanged. `findIfAvailable()`, `getIfAvailable()` and `ifAvailable()` are untouched.
3. Done 2026-11-11. `ApiExceptionHandler` maps `OptionalComponentUnavailableException` to 503 with `ErrorResponse`, like its neighbours; one case in `ApiExceptionHandlerTest`.
4. Done 2026-11-11 (5 cases). A unit test for `ComponentFactory` in `platform-commons` (it has none): present bean, absent bean (typed exception naming the type), two beans (the Spring exception unchanged), the three absent-safe methods.
5. Done 2026-11-11 (10 cases; with the old `get()` put back, exactly the apikey and feedback cases fail with the five operations named). The throw-away sweep becomes the regression test `OptionalStarterRestAbsenceTest` in `integration-tests` (level 2, REST): no operation of the 71 fails with a 5xx other than 503 or an unhandled exception for any hidden starter; the shell beans it needs are declared explicitly instead of discovered by retrying. Its list of needed beans comes from one run that prints them.
6. Done 2026-11-11 except the reviews. Documentation: the REST rules file (the exception mapping list), the decision on the typed wrapper (`get()` now throws the typed exception; annotate, regenerate the ADR index), the sweep result and this plan marked done in this task.
7. Open: the reviews and the full run. Checks: the new and changed tests, the sweep with the fix (apikey and feedback now 503, the rest unchanged), `/sync-docs`, `/deep-review`, the full `/ci`.

Not changed: the 40 `get()` call sites (writes to an absent feature stay failing, now with a typed error), the Vaadin UI (it guards writes with `isAvailable()` and its error handling is untouched), the MCP adapter (it relays the REST answer).

## Result of the fix (2026-11-11)

The sweep run again with the fix: no operation of the 71 fails for any hidden starter; the four moderation operations and the API-key issue answer 503 (the regression test asserts exactly these). The start-up test (19 cases), the unit tests of the factory (5) and of the REST error mapping (17), and the architecture rule pass. The only shell bean the REST context needs from the application shell is the `RestClient` builder.

## Related

- [improvement-246](../completed/tasks/improvement-246-technical-debt-investigation.md): the report, outside-scope note.
- [improvement-250](improvement-250-query-predicate-port-for-unbounded-cross-module-filters.md): depends on the same assumption.

## Operational notes
- token_cost_review: 103897
- token_cost_research: n/a
- token_cost_verification: 902798
- review_signal_ratio: 0 / 0
- context_loading_task_type: Architectural change (a rule of the typed-wrapper decision enforced across modules)
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: full CI-equivalent pass, review of the diff, doc sync after code change
- flows_chosen: /ci, /deep-review, /sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 10
- haiku_decomposition_rework_needed: 0 / 10
- adrs_referenced: ADR-006 (platform-commons), ADR-033 (marketplace-app)

### Agent calls
- two diff reviews | subagent_type=deep-review-orchestrator | tokens=103897 | tool_uses=12 | duration_s=154 | mode=background | batch=solo
- documentation audits of the changed Java files, two rounds (six, then four agents; durations summed, not wall-clock) | subagent_type=general-purpose (haiku) | tokens=902798 | tool_uses=76 | duration_s=1247 | mode=background | batch=parallel

### Script/command runs
- scripts/ci.sh (full), two runs, wall-clock taken from the longest chain of the stage timings | duration_s=3041 | mode=background | result=pass
- scripts/build-and-test.sh, single test classes, fourteen runs counted from the monitor timings | duration_s=2730 | mode=background | result=pass
