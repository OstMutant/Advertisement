# improvement-233: Mechanical hygiene batch — unused dependencies, multi-class test argument, sonar header, Liquibase master headers

**Type:** improvement — five small mechanical fixes taken from the deferred findings bucket, shipped in one build and test pass
**Module:** `marketplace-mcp/pom.xml`, `attachment-spring-boot-starter/pom.xml`, `integration-tests/run.sh`, `scripts/sonar/run.sh`, the seven master changelogs under `*/src/main/resources/db/*/` (advertisement, attachment, audit, provider-profile, taxon, user, and `marketplace-app`'s own)
**Priority:** closed 2026-11-04 (was 🟢 cheap, low impact)
**When:** closed 2026-11-04, see `## Closure`

## Current state

Each item was verified against the code on 2026-11-04:

- `marketplace-mcp/pom.xml` declares `org.jetbrains:annotations` (provided) and no class in the module imports from `org.jetbrains`.
- `attachment-spring-boot-starter/pom.xml` declares `org.ost:query-lib` and `jackson-datatype-jsr310`; nothing under the module's `src` imports `org.ost.query` or uses the Jackson time module. `marketplace-app` uses `org.ost.query` in one file and still gets it through the other starters, which declare it themselves.
- `integration-tests/run.sh` sets `SCENARIO="$arg"` for every non-flag argument, so `run.sh A B C` runs only `C`.
- The structured header of `scripts/sonar/run.sh` does not say why it uses `docker cp`; `playwright/run.sh` does, in its own header.
- Seven master changelogs have no file-level header comment; `apikey`, `contact` and `feedback` already have one.

## Why change

Dead dependencies cost build time and mislead readers about what a module needs; a script that silently drops arguments wastes a test cycle; a missing header breaks the repository's own "always a header" rule for master changelogs.

## Expected benefit

Smaller dependency lists, a test script that runs every class named, and masters that all follow the documented header shape. Five entries leave the deferred bucket.

## Approach

1. Remove the unused dependency blocks from the two pom files (the `jetbrains-annotations.version` property stays, other modules use it).
2. In `integration-tests/run.sh`, accumulate the scenario with a comma (`SCENARIO="${SCENARIO:+$SCENARIO,}$arg"`) and add a `Usage` line for several classes.
3. Add one sentence to `scripts/sonar/run.sh`'s `Input` field: sources are copied into the scanner container with `docker cp`, because volume mounts do not work when the caller is itself a container.
4. Add the header from the `module-doc-standards` skill to the seven master changelogs; the application's own master gets a variant that says it is empty because each starter loads its own changelog. Change files under `changes/` are not touched, their header shape is still undecided.
5. Remove the five matching entries from the deferred bucket.
6. Verify: `bash scripts/build-and-test.sh --unit --integration --sandbox`, and a run of `integration-tests/run.sh --sandbox` with two class names whose reports must both appear.

## Closure (2026-11-04)

All five items are done. `bash scripts/build-and-test.sh --unit --integration --sandbox` passed (1818 tests, none failed or skipped). The `run.sh` change was checked by its Maven command line (`-Dtest=SharedEnvConfigTest,PostgresContainerSmokeTest`) and by simulating its argument loop; a full two-class run with `--no-check` stopped earlier, because the local `~/.m2` has no `marketplace-mcp` jar, which is unrelated. Entries 3, 19, 20 and 33 left the deferred bucket; entry 12 now covers only the `build-and-test` header, which still does not state the `docker cp` reason.

## Related

- [improvement-133](../tasks/improvement-133-deferred-oversized-review-findings.md) — the bucket the five entries come from.
- `.claude/skills/module-doc-standards/SKILL.md` — the master changelog header shape.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: build the reactor and run unit and integration tests
- flows_chosen: /build-and-test
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 1
- haiku_decomposition_rework_needed: 0 / 1
- adrs_referenced: none

### Agent calls
- apply five mechanical edits | subagent_type=general-purpose | tokens=79558 | tool_uses=5 | duration_s=51 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --integration --sandbox | duration_s=626 | mode=background | result=pass
- integration-tests/run.sh --sandbox --no-check SharedEnvConfigTest PostgresContainerSmokeTest | duration_s=n/a | mode=background | result=fail (local ~/.m2 lacks marketplace-mcp, unrelated)
