# improvement-234: Rules files point at the generated architecture model instead of a README table, and the build-and-test header states why it copies with docker cp

**Type:** improvement — two small documentation fixes taken from the deferred findings bucket
**Module:** `.claude/rules/advertisement-spring-boot-starter.md`, `.claude/rules/audit-spring-boot-starter.md`, `.claude/rules/user-spring-boot-starter.md`, `.claude/rules/attachment-spring-boot-starter.md`, `scripts/build-and-test/run.sh`
**Priority:** closed 2026-11-04 (was 🟢 cheap, low impact)
**When:** closed 2026-11-04, see `## Closure`

## Current state

Verified 2026-11-04:

- The four rules files each say "See `<module>/README.md`'s "Key classes" table for the class list and one-line roles". That table no longer exists: the module READMEs now have `What it provides`, `Data flow` and `Dependencies`.
- The architecture model already holds this fact, generated from the code: every module node in `docs/architecture/data/architecture-model.json` carries `entities`, `keyServices` and `contracts`, each with the class name, its file and a one-line purpose taken from the class Javadoc. The map shows the same lists on each module's page.
- The structured header of `scripts/build-and-test/run.sh` says only "see the tar step's own comment below" and never says why sources go into the container through a tar pipe and results come back through `docker cp`.

## Why change

A class list is a fact derived from the code; copying it into a README or a rules file creates a second home that drifts, which is how the stale "Key classes" pointer appeared. The header gap leaves the reason for a non-obvious mechanism only in a body comment.

## Expected benefit

The class list has one home, the generated model, and always matches the code. The script header explains its own copy mechanism the way the sibling scripts' headers do.

## Approach

1. In the four rules files, replace the "Key classes" sentence with a pointer to the module's lists in the generated map and model (`entities`, `keyServices`, `contracts`), written as markdown links. Considered and rejected: embedding a generated list into the rules files (a second copy plus a new write path from the generator into hand-written files), and pointing at the README's "What it provides" section (a hand-written duplicate that drifts).
2. Add one sentence to the `Input` field of `scripts/build-and-test/run.sh`: sources go into the build container through a tar pipe and results come back through `docker cp`, because host-path mounts fail when the caller is itself a container and hit the Docker Desktop/WSL bind-mount problem.
3. Remove the matching entries (18 and 12) from the deferred bucket.

## Closure (2026-11-04)

Both fixes are done. The four rules files now point at the module's node in the generated map and model for the class list, and the `build-and-test` header states why it uses a tar pipe and `docker cp`. Documentation only; checked by reading the diff, the `run.sh` syntax check, and confirming that the linked files and the four module nodes exist. Entries 12 and 18 left the deferred bucket.

## Related

- [improvement-133](../tasks/improvement-133-deferred-oversized-review-findings.md) — the bucket the two entries come from.
- [improvement-233](../completed/tasks/improvement-233-mechanical-hygiene-batch.md) — the previous batch from the same bucket; its sonar header sentence is the model for the second fix.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 1
- haiku_decomposition_rework_needed: 1 / 1
- adrs_referenced: none

### Agent calls
- apply documentation pointer edits | subagent_type=general-purpose | tokens=87960 | tool_uses=15 | duration_s=70 | mode=background | batch=solo
