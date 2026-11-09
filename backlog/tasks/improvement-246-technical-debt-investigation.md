# improvement-246: Technical debt investigation of the project (ten hypotheses, read-only)

**Type:** improvement — a read-only investigation, no change until its report is decided
**Module:** the whole repository (`Dockerfile`, root and module `pom.xml`, `.claude/`, `scripts/ci/`, `platform-commons`, `marketplace-orchestrator`, `marketplace-rest-api`, `marketplace-mcp`)
**Priority:** 🟡 medium, at the top of the backlog at the user's request
**When:** independent, no blockers

## Current state

Checked on 2026-11-08 in the repository, before the investigation (the investigation itself has not run):

- H1: `Dockerfile` has no `COPY */pom.xml` line; it has `COPY . .` (line 19) in the `builder` stage, so the whole tree is copied. The premise of H1 does not hold as written.
- H2: the eight `DECISIONS.md` files have 0 `## ADR` headings and 8 to 16 lines each. `promotion-spring-boot-starter/DECISIONS.md` calls itself a generated index that lists decisions of other modules through their `**Also affects:**` tag, so the format is intended.
- H3: `.claude/rules.md` has 919 lines and 60,170 characters; 15 sections start with `> ## ⛔`.
- H4: a sentence-level comparison of the four skill files (81,469 bytes together) found 4 sentences longer than 70 characters that occur in two files, 887 characters in total; the check did not look for consecutive sentences.
- H5: `archunit-junit5` has a literal version `1.4.2` in `marketplace-app/pom.xml` and `marketplace-mcp/pom.xml`; the other versions in the module poms use `${...}` properties. The root `<dependencyManagement>` lists no `archunit-junit5`, `springdoc-openapi-starter-webmvc-ui`, `vaadin-dev` or `caffeine`. The root enforcer has `dependencyConvergence` (`pom.xml:241`).
- H6: the root `pom.xml` has `spring-boot-starter-parent` 4.1.1 and `vaadin.version` 25.3.2; root `CLAUDE.md` says Spring Boot 4.1.0 and Vaadin 25.2.3.
- H7: `maven-compiler-plugin` in the root `pom.xml` (lines 198 to 202) uses `<source>` and `<target>`, not `<release>`.
- H8: `.claude/nav/adr-index.md` has 283 decision rows, 33 of them with "superseded", "corrected" or "reversed" in the line (about 12 percent).
- H9: `scripts/ci/run.sh` has about 20 option lines (counted by a pattern, not read in full), and one of them is not named in any file of `backlog/completed`.
- H10 was not checked.
- Second prompt (deep verification of recent changes, below), checked on 2026-11-08: the names it uses exist in the code: `FeedbackRepository.deleteByEntity` (line 413), `FeedbackPort.deleteByEntity(EntityType, Long)`, `deleteForReasonExcept` (2 files), `EntityRef(EntityType, Long)` in `platform-commons`, `.dockerignore`, the `runtime` scope of the starters in `marketplace-orchestrator`, and `<exclude>org.ost:*-spring-boot-starter</exclude>` in all ten starters; `ContactRepository.deleteByEntity(EntityType, Long)` has the same shape as the feedback one. Its sections A and B overlap with the index audit already done in the database refactoring (six of seven removed indexes judged by the query code, one measured on a temporary table); its section G overlaps with H1 and H5 above.

## Why change

The user pasted an investigation prompt about the technical debt of the project and asked whether it makes sense; the checks above show which hypotheses hold as written (H2 fact, H3, H5 in part, H6, H7), which have a wrong premise (H1) and which are not yet checked.

## Expected benefit

A report with a status, evidence, root cause, impact, the smallest fix and its risk for each hypothesis, a summary table and the three fixes to do first.

## Approach

Run the investigation exactly as the prompt below describes: read-only, no commit, no build or test without separate permission, report in English, chat in Ukrainian. Hypothesis H1 starts from what `COPY . .` does and whether the build depends on `~/.m2`; H2 states that the eight files are generated indexes.

### The investigation prompt (as given by the user)

#### Role
You are an audit engineer. Investigate, confirm or refute concrete hypotheses about technical debt in this repository. Change nothing and commit nothing. The truth is in the code and configuration, not in .claude/rules.md/CLAUDE.md/DECISIONS.md (those are what is being checked).

#### Method
1. For each hypothesis read the real files with Read/Grep/Glob.
2. Every statement carries a file path and a line number. No "it seems", "probably".
3. If a hypothesis is not confirmed, say so; that is a valuable result.
4. For outside knowledge (version compatibility, framework behaviour) use WebSearch/WebFetch with a link to the primary source (release notes, Maven Central, the official compatibility matrix), not a blog.
5. Write nothing to files. Do not run build/test without separate permission; report first.

#### Hypotheses

H1. The Dockerfile does not build the whole reactor. File: `Dockerfile` (root). Compare `COPY */pom.xml` with `<modules>` in the root `pom.xml` and with the dependencies of `marketplace-orchestrator/pom.xml` + `marketplace-app/pom.xml`. Which modules are missing? Will the build fail, or quietly take stale JARs from `~/.m2`? Is the problem reproducible in a clean environment?

H2. 8 DECISIONS.md without `## ADR-NNN:` headings. Files: `advertisement-spring-boot-starter`, `apikey-spring-boot-starter`, `feedback-spring-boot-starter`, `marketplace-mcp`, `marketplace-rest-api`, `promotion-spring-boot-starter`, `provider-profile-spring-boot-starter`, `user-spring-boot-starter`. Confirm the format is non-standard, show the first 10 lines of each, estimate how many ADR entities are really there and whether they can be mechanically brought to `## ADR-NNN:` without losing content. What % of ADR coverage does `adr-index.md` lose?

H3. `.claude/rules.md` has grown. Count: lines, approximate tokens, number of sections with ⛔, number of sections with "violated multiple times"/"violated repeatedly". Classify each ⛔ rule: (a) has a natural file trigger → can move to `.claude/rules/<domain>.md`; (b) has none → must stay eager. Give numbers: X% can move, Y% stays.

H4. Duplication in `.claude/skills/`. Files: `module-doc-standards`, `module-readme-standards`, `infra-doc-standards`, `infra-readme-standards`. Find paragraphs that repeat verbatim or almost verbatim (threshold: ≥2 consecutive sentences with the same meaning). Estimate how many tokens this costs when a skill loads and whether it breaks the own rule "one fact, one canonical home".

H5. Dependency convergence risks. Compare `<dependencyManagement>` in the root `pom.xml` with the actual `<version>` in `marketplace-app/pom.xml`, `marketplace-mcp/pom.xml`, `marketplace-rest-api/pom.xml`, `integration-tests/pom.xml`. Find versions pinned locally but not in the root (candidates: `archunit-junit5`, `springdoc-openapi-starter-webmvc-ui`, `vaadin-dev`, `jsoup`, `caffeine`). Does `dependencyConvergence` pass now? Is there a risk when a new module is added?

H6. Compatibility of Spring Boot 4.1.1 + Vaadin 25.3.2 + Java 25 + Jackson 2/3. Check through WebSearch/Maven Central: do these versions exist? Are Vaadin 25.3.2 and Spring Boot 4.1.1 compatible (official matrix)? Does Vaadin 25 support Java 25? Are there known Jackson 2.x + 3.x conflicts in `platform-commons`? Give links to primary sources.

H7. `maven-compiler-plugin` `<source>/<target>` instead of `<release>`. File: root `pom.xml`, the `maven-compiler-plugin` section. Explain the difference for Java 25, estimate the risk of leaving it vs moving to `<release>`.

H8. Quality of `adr-index.md` — Superseded chains. Count ADRs whose status contains "Superseded"/"Accepted — X superseded by Y"/"corrected"/"reversed". How many ADRs describe a state on a date, not the current state? Are there ADRs that can be deleted or merged without losing information? Estimate how much the index shrinks after cleaning.

H9. CI complexity. Files: `scripts/ci.sh`, `scripts/ci/run.sh`, `scripts/ci/dagu/ci.yaml`, `.claude/commands/ci.md`. Count: flags in `ci.sh`, steps in the DAG, volumes, containers, env variables. Compare with what is really used in `backlog/completed/` (grep for `/ci --<flag>`). How many flags were never used? Can it be simplified without losing functionality?

H10. Other debt. Go through `platform-commons`, `marketplace-orchestrator`, `marketplace-rest-api`, `marketplace-mcp` and record 1–3 concrete findings that are not in H1–H9. Do not invent: better 0 than a stretch.

#### Report format

For each hypothesis:

```
### HN. <name>
**Status:** confirmed / rejected / needs-further-check
**Evidence:** <path:line, quote>
**Root:** <why it happened — one sentence>
**Impact:** <what breaks or what it costs — concrete, with a number where possible>
**Fix:** <the smallest action, estimate: XS/S/M/L>
**Fix risk:** <what can break>
```

At the end — a summary table (H1–H10: status, effort, impact) and the top 3 to fix first.

#### Limits
- Read-only. No Write/Edit/Bash that changes files.
- No commits.
- Build/test only with separate permission.
- Report language: English (per `.claude/rules.md` Language); in chat — Ukrainian.

### The second investigation prompt: deep verification of recent changes (as given by the user, translated)

#### Deep Verification of Recent Changes: Subtle Bugs and Regressions

Run a deep verification of the latest changes in the repository. This is not a general code review and not a search for style improvements. The goal is to find subtle errors, hidden regressions and unaccounted consequences of the changes that may not show up in ordinary happy-path testing.

##### 1. Context and rules

- Compare the current HEAD with the previous state of the changes.
- Check not only the changed lines but all dependent places: callers, SQL, DTOs, Ports, API, UI, tests, Liquibase, configuration.
- Do not propose abstract best practices.
- Do not invent problems without evidence.
- Do not change the code. This is an audit only.
- Do not refactor and do not widen the scope.
- `marketplace-orchestrator` is a deliberate architectural decision. Do not propose moving, dismantling or redesigning it.
- Do not duplicate known backlog items. Check the existing tasks and ADRs before forming new recommendations.
- If confirming a finding needs a test run, SQL or `EXPLAIN`, run it if possible. If not, state clearly what remained unchecked.

##### 2. Priority areas

**A. SQL and data integrity.** Check:
- whether the removed indexes match real queries, filters, sorting and JOINs;
- whether selectivity or the execution plan of queries got worse;
- whether composite indexes work correctly given the column order;
- whether an index was removed that a query uses through another combination of conditions;
- whether full table scans appear on large tables;
- whether the new DELETE/UPDATE queries match the real relations between records;
- whether data still used by other records can be deleted;
- whether there are hidden assumptions of 1:1 or 1:N not enforced by UNIQUE/FK constraints;
- whether NULL, empty collections, duplicates and missing records are handled correctly;
- whether the semantics of filtering, sorting or pagination changed.

Check the new `FeedbackRepository.deleteByEntity()` in particular: the order of deleting related records; the behaviour of a data-modifying CTE in PostgreSQL; deleting `feedback_content` if other records can reference it; the behaviour when there is no feedback; the behaviour with partly present related data; atomicity and rollback; possible race conditions with parallel operations. Do not count a problem as confirmed only because it is theoretically possible; check the actual constraints, callers and data model.

**B. Liquibase and compatibility of existing databases.** Check every changelog change:
- whether already applied changesets were edited instead of new ones being created;
- what exactly `validCheckSum ANY` does and whether it masks a divergence between schemas;
- whether existing installations get the same changes as a clean installation;
- whether old columns and indexes are really removed;
- whether a new database is created correctly while an old one stays with the old schema;
- whether the order of the migration changesets is safe;
- whether data is lost during migration;
- whether a migration can be run again or after a partial failure;
- whether changelog, Java model and SQL repository agree.

Check separately the removal of `assigned_by`, `valid_from` / `valid_until`, and of the promotion, feedback, provider profile and taxon indexes. For each change say whether it is correct for: 1. a clean database; 2. a database where the previous changesets are already applied; 3. a database with real data.

**C. Promotion: the change of semantics.** Check the whole flow after `valid_from` / `valid_until` were removed: creating, updating and deleting a promotion placement; determining the promoted status; filtering and sorting; the API response; the UI display; the sitemap; the provider profile read flow; external consumers of the DTO/API. Find out: whether expiration is really not needed anywhere else; whether old records with a non-null `valid_until` remain; whether the behaviour of old records changed; whether every place that used to take expiration into account now has equivalent logic; whether a difference appeared between the UI, the REST API and the sitemap. Check `findPromoted()` and `deleteForReasonExcept()` together with the existing indexes, especially the efficiency of the `entity_type + reason` conditions.

**D. Transactions and concurrency.** Check the new and changed operations for: partial execution; a missing transaction where one is needed; wrong transaction boundaries; calls to another module inside a transaction; a rollback that does not cover all changes; deadlocks; race conditions; lost updates; wrong behaviour with parallel delete/update. Check in particular the integration of `FeedbackAccessService.deleteByEntity()` into `ProviderProfileSaveService.delete()`: whether one transaction covers all needed changes; whether orphan records remain; whether the optional feedback module behaves correctly; whether the order of operations and the audit logging are kept.

**E. Cross-module contracts.** Check the changes of Ports, DTOs and service contracts: whether a new needless dependency appeared; whether the same information is passed in several ways; whether enrichment is duplicated; whether needless queries between modules appeared; whether behaviour changed when an optional module is absent; whether the signatures of Port, implementation and callers agree; whether the module isolation rules are broken. Consider `FeedbackPort.deleteByEntity(EntityType, Long)` separately: whether the signature fits the current idea of typed references; whether using the existing `EntityRef` is appropriate; whether a new local format for passing a composite identity is created; whether the change would really reduce code and coupling and not just add an abstraction. Do not propose a global registry or new tables only to replace two arguments by one. First assess the existing types and real scenarios.

**F. Promotion enrichment and performance.** Check: whether repeated queries were really removed; whether promotion placements are loaded again in the same scenario; whether all placements are loaded when only the ids of the current page are needed; whether a hidden N+1 appeared; whether the volume of data passed between layers grew; whether pagination got worse; whether the number of SQL queries changed in the REST/UI/sitemap scenarios. Do not propose caching or asynchrony without a measured problem.

**G. Docker and Maven.** Check the new Dockerfile: whether `COPY . .` works correctly with the real `.dockerignore`; whether needless files get into the build context; whether secrets, local configs or build output are copied; whether the Maven reactor build is broken; whether `-pl marketplace-app -am` works; whether the efficiency of the Docker layer cache is lost; whether the Maven cache mount is really used; whether the build behaviour changed against the old version. Check also the change of dependency scope in `marketplace-orchestrator`: compile/runtime/test classpaths; production packaging; Spring Boot auto-configuration discovery; integration tests; running the application built as a JAR; whether differences appeared between the IDE and the Maven build. Check the wildcard exclusion `org.ost:*-spring-boot-starter`: whether it excludes dependencies that were not meant to be excluded.

**H. Hidden functional regressions.** For every changed behaviour find: all callers; all API/UI entry points; negative scenarios; boundary values; scenarios with missing or stale data; scenarios with parallel requests; compatibility with existing data. Check the removal of fields from the DTO/API and the changes of promotion behaviour with special care: not only the internal callers but also the public contracts.

##### 3. Test review

Review the matching unit, Testcontainers and Playwright tests. Do not only check that tests exist: check whether they would really have found the defects of the sections above. Find: tests that check only the happy path; tests that fix the state themselves before the assertion; missing rollback/concurrency/integration scenarios; tests that no longer match the new behaviour; cases where a mock hides an SQL or transaction problem. For SQL, CTEs, constraints and Liquibase prefer a real PostgreSQL through Testcontainers, not H2 or a mock. If you run tests, give the exact commands and results. Do not claim that a test passed if it was not actually run.

##### 4. Result format

No general description of the changes. A list of concrete findings is needed. For each finding: 1. Name of the problem. 2. Severity: Critical / High / Medium / Low. 3. Status: Confirmed / Likely / Needs verification. 4. File and the exact method/SQL/changeset. 5. What exactly is wrong. 6. The concrete scenario in which the problem occurs. 7. The consequence for data, performance or behaviour. 8. The minimal fix. 9. The test that will prove the problem is fixed.

Split the result into: Confirmed bugs (proven errors); Potential regressions (reasoned risks that need checking); Missing tests (important scenarios without test coverage); Performance concerns (only those with a concrete basis); Verified safe changes (changes that were checked and create no problems).

##### 5. Final conclusion

At the end give: the top 5 problems by priority; what must be fixed before the merge; what can be left as it is; which changes need a check on real data; which new backlog items are really needed; which are already covered by existing tasks or ADRs. For each recommendation say whether it is a bug fix, a data migration, a performance optimization, test coverage or an optional improvement.

**The main thing:** look not for what could be done differently but for what in the current changes can really break, worsen the work or create a hidden problem. Every conclusion must rest on concrete code, SQL, schema or a test. If the evidence is not enough, say so.

## Related

- [improvement-244](improvement-244-research-wiki-or-keep-architecture-control-plane.md): research on the documentation tools.
- [improvement-243](../completed/tasks/improvement-243-sort-status-line-and-where-1-1.md): holds the decision about an index with `created_at` first on `audit_log`, which section A of the second prompt can check on real data; the two tasks stay separate.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: new improvement request, not yet tracked
- flows_chosen: /new-task
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
