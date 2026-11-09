# improvement-252: The application starts with any optional starter removed from `pom.xml`

**Type:** improvement — a test that proves the main modularity requirement, plus the fix it is likely to need
**Module:** `integration-tests` (a new level), every starter's auto-configuration, `marketplace-orchestrator`, `platform-commons` (`ComponentFactory`)
**Priority:** 🟡 medium-high: it checks the requirement "removing a starter breaks nothing", which nothing checks today
**When:** independent; its result feeds [improvement-250](improvement-250-query-predicate-port-for-unbounded-cross-module-filters.md), whose rule assumes an absent provider yields no predicate

## Current state

Deduced from the code, not run: a `ComponentFactory<XPort>` bean is declared by the starter that provides `XPort`, while the orchestrator injects `ComponentFactory<XPort>` into its services. With that starter absent the bean may be missing and the Spring context may fail to start. The orchestrator `pom.xml` lists all ten starters at `runtime` scope, so every build includes them; no test starts the context without one. `ArchitectureRulesTest` checks imports, not startup.

## Why change

The design says every optional starter can be removed and the application still boots (`ComponentFactory.findIfAvailable()`, `ifAvailable`). If the deduction is right, that claim is false for some starters and nobody knows which.

## Expected benefit

A test that names exactly which optional starters can be removed, and a fix so that all of them can.

## Approach

1. Verify first: build a throwaway classpath without one optional starter (Audit, Attachment, Advertisement, Provider Profile, API key, Contact, Feedback, Promotion; Taxon and User are mandatory or need a decision) and start the Spring context; record which starters fail and with what error.
2. Fix: the consuming side declares an absent-safe `ComponentFactory<XPort>` (for example a `@ConditionalOnMissingBean` default, the shape `RepositoryTestSupport` already uses in tests), in `marketplace-orchestrator` or one place in `platform-commons` that does not become a persistence or business class.
3. Test: one parameterised test in `integration-tests` that starts the context once per removable starter, asserts it starts, and asserts the feature that needs the removed starter reports itself unavailable instead of failing.
Options and their cost are decided after step 1; record the decision with `/record-decision` if the declaration site moves.

## Related

- [improvement-246](improvement-246-technical-debt-investigation.md): the report, outside-scope note.
- [improvement-250](improvement-250-query-predicate-port-for-unbounded-cross-module-filters.md): depends on the same assumption.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: new task from the findings of the debt investigation
- flows_chosen: /new-task
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
