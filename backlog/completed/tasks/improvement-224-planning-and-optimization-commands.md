# improvement-224: planning command (Opus) and optimization command

**Type:** improvement — two new project commands (`/deep-plan` and `/optimize`).
**Module:** `.claude/commands/` (new command files), `.claude/nav/flows.md` (one row per command), possibly `.claude/agents/` (a dedicated agent definition).
**Priority:** 🟡 Top — first in the backlog. Phase 1 (planning command) first, then phase 2 (`/optimize`).
**When:** independent; related to improvement-177 and improvement-215 (how plans are presented and approved).

## Current state

Planning happens in the main thread. Facts checked while filing this task: the `Agent` tool accepts `model: sonnet|opus|haiku|fable`; the `Plan` agent type is read-only and has no `Agent` tool, so it cannot start nested agents; `general-purpose` has every tool and can start nested agents (for example `model: haiku` readers); `deep-review-orchestrator` already works this way, an agent that dispatches reviewer agents. An agent starts cold, sees nothing of the conversation, and returns only its final report to the caller. Project commands live in `.claude/commands/*.md`; each new one needs a row in `.claude/nav/flows.md`. The standing rules: a plan is presented in the Approval Rule format and needs an explicit "yes"; fully specified mechanical work goes to Haiku.

## Why change

Large tasks need a stronger planner than the main thread's model, without the main thread paying for the reading. There is no repeatable way to ask for it: each time the brief, the agent type and the model have to be invented again.

## Expected benefit

One command that turns a task (or a backlog task file) into a self-contained brief, runs a planner on Opus, and returns a plan the main thread presents in the Approval Rule format.

## Approach

Two phases, in this order. The details of each are discussed with the user before anything is written.

### Phase 1 — planning command

Done 2026-10-24: implemented, reviewed (no findings) and the documentation checked; left staged and not committed at the user's request. Not yet run end to end with the real Opus planner.

`/deep-plan <task or task file>` writes a self-contained brief, dispatches a planner on Opus and returns a plan the main thread presents in the Approval Rule format. The agent never edits files. Open (to discuss): `Plan` on Opus (read-only, no nested agents) or `general-purpose` on Opus with nested Haiku readers when the plan needs wide reading, as the default; a dedicated agent file under `.claude/agents/` or a command-only prompt; what the brief contains and how the result is written back into the task file; the `.claude/nav/flows.md` row.

Direction given by the user on 2026-10-24: the planner gets the widest capabilities so the analysis is as good as possible, and may start sub-agents that extend what it can do; the tools for writing and reading plan files must be cheap. Planning works with a research folder under `backlog/` (`backlog/research/`): one subfolder per research topic, so several researches can run in parallel, holding intermediate data from the web and other sources (links, extracts, notes); when the plan is finished and the task is filed, that subfolder is cleaned up.

Decided with the user on 2026-10-24:
- The default planner is the widest one: `general-purpose` on Opus with all tools and cheap Haiku sub-agents for bulk reading and fetching.
- `backlog/research/` is kept in git so a new session can pick a research up where the last one stopped; a topic subfolder is removed (with `git rm`) in the same operation that files the task made from it.
- No hook limits where the planner writes for now (too strict at this stage); the command text states that the planner writes only under `backlog/research/` and never edits other files.
- Layout: `backlog/research/<topic>/` for each research (temporary), and `backlog/research/tools/` (permanent, reserved name, not a topic) with a `README.md` and a subfolder for helper scripts, room for other helpers later. The scripts are plain shell, no model calls: create a topic, append a note or a source link, list the topics, remove a topic. They follow the header standard for scripts.
- The planning command is named `/deep-plan` (chosen by the user from several candidates; no existing project command or skill has this name).
- A dedicated agent file plus a command that calls it (the user finds it more flexible and convenient than a command-only prompt): `/deep-plan` in `.claude/commands/`, the agent in `.claude/agents/` with `model: opus` and its tool list in its header, following the `deep-review-orchestrator` precedent.
- The planner produces two plans, not one: a machine plan for the AI with the maximum of detail, and a human plan that is clear, concise and easy to follow: the goals, which problem the task solves, which means are needed, and what the result will look like. The human plan also shows the cost and the risks up front. The detail level of the machine plan depends on the model that will execute each step: for a weaker model (Haiku) the exact content to write, for a stronger one the exact files and the exact behavior without ready-made code; so every step of the machine plan names its executor model. Further details of both are still to be discussed; the user stressed that many details are still to come.
- What the planner must do (the user's requirements, 2026-10-24), as one comprehensive piece of work aimed at the optimal solution:
  - search the web for best practices, keep the links and notes for later (in the topic folder), and compare the variants to pick the one that fits the goal best;
  - dig as deep as possible so that the result does not have to be redone later, and stay attentive to details;
  - study the project as well: the existing work may already contain something useful; where the project's own practice conflicts with best practice, say so openly;
  - the chosen solution must be as concise as possible and as easy as possible to read for a person looking at the code.
- When the planner stops digging (the user's decision, 2026-10-24): by a completeness criterion, not by a token budget (a budget is too radical): it stops when new sources no longer change the choice, and the plan says why that was enough. If sources conflict, it goes two levels deeper into them once and then simply highlights the conflict instead of digging without end; in most cases best practices are concrete and agree. When a regularity shows up across sources, the plan highlights it.
- Time (the user's decision, 2026-10-24): no hard time limit. While the planning runs the main thread watches its progress and tells the user: if more time is needed it says so and why; if there is progress it says that the process is progressing; if the planner loops it says there is trouble. To make progress visible without reading the agent's transcript, the planner appends timestamped lines to a progress file in the topic folder (stage, sources read, what changed the choice) and a plain script from `backlog/research/tools/` shows the tail and the file times; the loop thresholds are taken from what was found on the web (2026-10-24) as starting values and tuned by practice, and every planning run is logged in a permanent file `backlog/research/tools/observations.md` (date, topic, duration, sources read, which signals fired, what the verdict was) so the values can be corrected from real runs. Findings that shaped the starting values: loop detectors count identical calls (tool plus arguments; one framework warns after 10 and blocks after 20, another uses 3 repeats) and also look at the result (the same arguments with the same result, polling that keeps returning the same state), at alternating patterns (A, B, A, B) and at the similarity of consecutive outputs (a default cutoff of 0.85); repetition alone is weak evidence (a repeated search with a changed query is fine); for research agents the stop signal should come from outside the model, from the marginal information gain per step (stop when new sources stop adding new facts for several steps in a row), because a model asked whether it has enough tends to keep searching; one study sees quality level off at about nine expansion steps; sources advise pairing a progress definition with a cap and tracking the false-positive rate of the detector. Starting values for this project: warn at 3 repeats of the same query or page, warn when 5 consecutive steps add no new source or fact, report a loop when both persist after the planner was told once.
- Everything that can be done with a script is done with a script (no model call), for the helpers in `backlog/research/tools/` and for the bookkeeping around a research; the user accepted this on 2026-10-24.
- The agent file (instructions and tool list) is drafted from the requirements above and reviewed with the user when it is written; no separate decision is needed before that. The supporting files (a row in `.claude/nav/flows.md`, a mention in `.claude/nav/context-loading.md` and in the backlog maintenance rules) are part of the implementation.
- Layout of a topic folder (readable for a person first, convenient for the AI second; confirmed by the user on 2026-10-24): `brief.md` (what the planner is asked: goal, context, constraints, link to the task), `progress.md` (timestamped lines), `sources.md` (a table: link, what was taken, date, whether it confirms or contradicts), `notes.md` (findings, comparison of variants, conflicts with the project's own practice), `plan-human.md`, `plan-machine.md`; every file starts with a one-line purpose, and the files a script reads (progress, sources) use fixed field names.
- What the brief is and how the result returns (agreed in outline; proposal: the brief is the file `brief.md` the main thread writes from a template, the planner is started with only the topic name and reads it itself, so a new session can pick up a research; the planner writes the two plans into the topic folder; after the user approves and the task is filed, both plans move into the task file (the human plan as its description, the machine plan as a section "Execution plan") and the topic folder is removed; decided by the user on 2026-10-24), how the result is written back into the task file, the `.claude/nav/flows.md` row, a mention of the folder in `.claude/nav/context-loading.md` and in the backlog maintenance rules.

Field formats (chosen by the user on 2026-10-24):
- `progress.md`, one bullet per step: `- 14:02 [search] 7 sources (+2 new), choice unchanged: <what was done>`; the choice part reads `choice unchanged` or `choice CHANGED`.
- `sources.md`, a table with the columns link, what was taken, confirms or contradicts, date, type (official documentation, blog, paper, forum), level (0 direct source, 1 and 2 reached by following references when sources conflict), confidence (high, medium, low).
- `brief.md`: goal, link to the task, context (decisions, constraints, rejected options), where to look in the project, completion criterion, available executor models.
- `plan-human.md`: goal, the problem the task solves, the means needed, what the result will look like, cost, risks, where the project's practice differs from best practice, decisions waiting for the user.
- `plan-machine.md`: steps, each with its number, dependency, files, executor model, input, exact change, verification, sources.
- `notes.md`: free form with the sections findings, variants and comparison, differences from the project.
- Helper scripts in `backlog/research/tools/scripts/` (verb first, kebab-case, more can be added when needed): `start-topic.sh`, `list-topics.sh`, `show-status.sh`, `add-note.sh`, `add-source.sh`, `log-progress.sh`, `remove-topic.sh`.

Phase 1 implementation plan (2026-10-24, approved by the user; implemented 2026-10-24 and checked with ShellCheck at style severity (only two SC2001 style notes), a scripted smoke test of all seven scripts on a throw-away topic (name checks, duplicate topic, every argument check, the three loop signals and the `looping` status, notes inserted into existing and new sections, removal only with `--yes`), the two navigation checks and the regeneration of the architecture model; the new command shows up in the command list; the shellcheck stage of the CI now also covers `backlog/research/tools/scripts`; not yet run end to end with the real Opus planner):
- `backlog/research/tools/README.md` (what the folder is, the life cycle of a topic, the layout and field formats above, the list of scripts without repeating their usage, which stays in each script's header) and `backlog/research/tools/observations.md` (a header and a table: date, topic, duration, sources read, signals that fired, verdict).
- `backlog/research/tools/scripts/`, plain bash, one header per file as the standard for scripts requires, exit codes 0 ok, 1 error, 2 usage, no `.bat` twins because the agents run on Linux: a sourced `topic-common.sh` (topic name check: lower-case letters, digits and hyphens, the name `tools` is reserved, the topic folder must or must not exist), `start-topic.sh <topic>` (creates the folder with the six files from templates), `list-topics.sh`, `show-status.sh <topic> [lines]` (tail of `progress.md`, times of the files, number of sources, and the starting loop signals: a query or page used 3 times, 5 steps in a row without a new source), `add-note.sh <topic> <section> <text>`, `add-source.sh <topic> <link> <taken> <verdict> <type> <level> <confidence>`, `log-progress.sh <topic> <stage> <sources> <new> <changed|unchanged> <text>`, `remove-topic.sh <topic> [--yes]` (removes the folder with `git rm -r`).
- Agent `.claude/agents/planning/deep-planner.md` with `.claude/agents/planning/README.md` and a row in `.claude/agents/README.md`: `model: opus`; tools `Read`, `Grep`, `Glob`, `Bash`, `WebSearch`, `WebFetch`, `Agent`, `Write` (no `Edit`); the instructions carry the whole mandate above (web best practices with saved sources, the project's own practice and conflicts, depth and stop rule, the two plans, writing only under `backlog/research/<topic>/`, logging every step through `log-progress.sh`, which executor model gets which kind of step, Haiku sub-agents for bulk reading and fetching) and the final reply format (a short summary and the paths).
- Command `.claude/commands/deep-plan.md` (`/deep-plan <task file or text>`): derives the topic name, runs `start-topic.sh`, writes `brief.md` from the task file and the conversation, starts `deep-planner` in the background with only the topic name, watches `show-status.sh` and tells the user whether there is progress, more time is needed or the planner loops, presents `plan-human.md` when done and asks for approval, after approval files the task with the two plans inside it, runs `remove-topic.sh` and appends the run to `observations.md`.
- Supporting rows: `.claude/nav/flows.md` (command and agent), `.claude/nav/context-loading.md` (a research folder is read when a research is resumed), the maintenance rules in `backlog/BACKLOG.md`, the slash command and agent lists in root `CLAUDE.md`, `.claude/commands/README.md`; then `check-flows-completeness.sh`, `check-hardcoded-counts.sh` and the regeneration of the architecture model.
- Verification: ShellCheck on every script; a scripted smoke test of all seven scripts on a throw-away topic, including crafted `progress.md` lines that must trigger each loop signal; the two nav checks; the command is seen in the command list. Optional afterwards, with the user's approval: one small real `/deep-plan` run to see the whole path and log it in `observations.md`.

### Phase 2 — `/optimize` command

Done 2026-10-24: implemented from the approved execution plan (all 11 steps), then moved from a skill folder to `.claude/commands/` at the user's request, and left staged, not committed. Checks: the helper script `check-batch.sh` passed a scripted smoke test in a throw-away repository (argument error exits 2; totals match a hand count; an untracked file is counted; an unlisted changed file gives a warning and exit 0; a change under `marketplace-orchestrator/`, an added `<dependency>` and added reflection in a tracked or an untracked file each give exit 1; an unknown ref exits 2) and ShellCheck at style severity; the frontmatter of the skill matches the plan; the navigation checks pass; the architecture model lists the new skill and lens; `/review` with no argument ran its usual three lenses on these changes and returned its usual result, which shows the orchestrator edit left `/review` unchanged, and found nothing. Not done: the optional pilot run `/optimize path query-lib` (needs the user's approval and costs tokens). Because of the plan's decision 2, the standing findings inventory is a new task file; the executor of step 1 took the next free number.

Source: a draft supplied by the user on 2026-10-24, kept below as given. The plan made from it by `/deep-plan` and approved by the user follows the draft (sections "Decisions taken", "Plan for people" and "Execution plan").

Facts to check first (the draft itself says not to assume): the harness already has a `simplify` skill (review changed code for reuse and simplification, then apply fixes) and this repository has `/review` with the `dry-kiss-yagni-reviewer`, `solid-reviewer` and `precedent-reviewer` agents, so the overlap has to be settled before a new command is added; OpenRewrite as a complement for deterministic transformations; existing backlog entries about duplicated code (for example improvement-133 entry 31, the Level 3 helpers copied into every class).

> **Objective.** Design and implement a reusable `/optimize` command or skill for this repository. Its purpose is to systematically identify and safely eliminate unnecessary code across the Java application while preserving readability, explicitness, maintainability, type safety, testability and architectural integrity. It must be a repository-specific optimization workflow, not a generic code cleanup prompt. The goal is not merely to make code shorter: it is to reduce the total amount of code that must be written, understood, tested and maintained.
>
> **First step of the draft: inspect existing tooling and conventions.** Identify the current agent/tool environment. Inspect existing commands, skills, agent instructions, repository rules, documentation, ADRs, scripts and backlog conventions. Check whether an existing command already provides overlapping functionality (simplification, refactoring, code review, cleanup). Research the current official documentation for the environment's custom commands and skills. Evaluate whether existing tools such as OpenRewrite can complement the workflow for deterministic, repeatable Java transformations. Prefer extending an existing mechanism over introducing a competing command or duplicating instructions. Do not assume a feature, command, configuration path or tool capability exists; verify it against the actual environment and current documentation.
>
> **Hard constraints.** Do not modify `marketplace-orchestrator` (intentionally present, outside the scope). Do not redesign module boundaries, dependency directions, architectural layers or the modular structure. Do not remove ports, interfaces or abstractions merely because they increase line count. Do not add dependencies unless a specific, demonstrated benefit justifies their maintenance and complexity costs. Do not replace readable explicit code with reflection, generic frameworks, excessive annotations or hidden behavior simply to reduce lines. Do not change application behavior, public contracts, authorization, validation, transaction boundaries, exception semantics or side effects. Do not convert mutable Vaadin Binder DTOs into records without proving behavioral equivalence and checking existing conventions. Do not duplicate functionality already implemented or backlog items already planned. Do not edit application code during discovery and evaluation. Do not record changes in the repository history unless explicitly instructed. Follow the repository's existing approval and verification rules.
>
> **Four stages.**
> 1. DISCOVER: search for concrete opportunities to reduce maintained code: repeated business logic and algorithms; repeated field-copying, mapping, conversion, validation and collection processing; boilerplate that Java language features, Lombok, MapStruct, Spring or already-installed libraries can eliminate; unnecessary forwarding methods, wrappers, classes, constructors, accessors and declarations; abstractions that add indirection without reuse, isolation, extensibility or architectural value; repeated UI form state management and edit/restore/discard behavior; repeated test setup and assertions where a small clear helper would improve the tests; opportunities for deterministic code generation or automated transformations; existing project conventions that could be applied more consistently. These are investigation directions, not presumed defects. Inspect actual call sites, implementation details, tests, rules, ADRs and backlog entries before reporting a finding.
> 2. MEASURE: for every finding estimate the current and the proposed size, net lines removed, added and changed, files affected, whether the same approach applies elsewhere, repository-wide savings without double-counting, readability and cognitive-complexity impact, new abstractions, dependencies, configuration or maintenance costs, behavioral and regression risks, and the verification required. Do not present speculative savings as confirmed. Distinguish confirmed opportunities, opportunities needing a small proof of concept, and ideas rejected because their complexity outweighs their benefits. Prioritize net reduction in maintained code, not fewer lines in one file.
> 3. APPLY: a separate, explicitly approved phase. Present a short implementation plan with exact files and expected changes; work in small, independently reviewable batches; make only the approved changes; inspect the resulting diff; run the smallest relevant tests first, then broader tests as justified; verify behavior and architectural constraints; report actual savings from the final diff; stop after the approved batch and wait for explicit approval. Never apply all discovered suggestions automatically.
> 4. REPORT: highest-value opportunities; findings ranked by net benefit and risk; before/after examples; estimated and, after implementation, actual code reduction; verification results and limitations; rejected ideas and why; relevant backlog items and rules; remaining opportunities. No generic advice ("apply DRY", "use SOLID") without concrete code.
>
> **Finding format.** ID and title; priority; exact file paths and symbols; current implementation (a short real excerpt or precise reference); proposed implementation; expected net reduction (lines and files, assumptions stated); why the result stays readable and maintainable; behavioral risks and required tests; existing backlog/rules/ADR status; recommendation (implement, investigate further, or reject). Do not manufacture findings to fill a report; if no meaningful opportunity is established, say so.
>
> **Repository-specific checks.** Cross-reference existing backlog items before proposing work. Check rules and ADRs before recommending architectural or coding-style changes. Follow the existing `entity.toDto()` conventions only for pure field-copy mappings; do not move SQL joins, localization or enriched DTO construction into entities. Inspect `FilterMapper<T>` and the actual MapStruct implementations before proposing interface or mapping changes; do not assume inherited generic methods preserve concrete `@MappingTarget` behavior. Verify Lombok-generated methods and constructors against all actual usages before removing annotations or declarations. Preserve the mutable DTO behavior Vaadin Binder needs. Distinguish true duplicated behavior from similar-looking code with different authorization, validation, transaction or lifecycle semantics. Respect existing test-layer boundaries and project-specific approval procedures.
>
> **Command interface.** A simple interface for the existing environment, a small set of explicit modes: `/optimize` (discover and rank, no application edits), `/optimize report` (produce or refresh the consolidated findings report), `/optimize apply <finding-or-batch>` (implement only explicitly approved findings), `/optimize verify` (verify approved changes and calculate actual savings). Desired semantics, not mandatory syntax; adapt to the tool's real capabilities and do not create separate commands if the environment has a simpler native way. The default invocation is read-only with respect to application source code.
>
> **Success criteria.** Reusable across sessions; respects repository rules and existing decisions; avoids duplicating existing review workflows; produces concrete, verifiable findings; reduces maintained code without sacrificing readability; keeps discovery, approval, implementation and verification separate; cheap enough to use repeatedly (narrow the analysis to relevant files, targeted searches before broad analysis); discloses what was and was not inspected.
>
> **Deliverables of the draft's first step.** Report what relevant commands, skills and rules exist; which existing mechanisms to reuse; the proposed design and where it lives; how it avoids overlap with the existing review workflows; a concrete implementation plan. No application source changes in that step; wait for explicit approval before implementing.

#### Decisions taken (user, 2026-10-24)

All six proposals of the planner were accepted: (1) `/optimize` without a scope shows the report and asks which module or folder to search; (2) the findings live in a new standing backlog file; (3) SonarQube duplication data is not in the first version; (4) OpenRewrite is not part of the command (the optional one-off `rewrite:dryRun` experiment is kept as an idea in the ideas collection); (5) a skill folder with the script inside it; (6) `disable-model-invocation: true`. Decision (5) was changed by the user later on 2026-10-24: `/optimize` is a command file, `.claude/commands/optimize.md`, with its helper script in `.claude/commands/optimize/`, not a skill folder (the project keeps its workflows in `.claude/commands/`). The plan below was produced by `/deep-plan` (topic `optimize-command`, 2026-10-24) and approved by the user; implementation has not started.

#### Plan for people

##### Goal

One repeatable `/optimize` command that finds code the project does not need to keep, measures how
much it would save, and removes it only in small batches you approve one at a time, without
touching `marketplace-orchestrator`, module boundaries, behavior or readability.

##### The problem this solves

Today nothing in the project looks for removable code on purpose, measures it, or keeps the
results between sessions:

- `/review` finds design problems and verifies each one, but it never edits code, does not measure
  savings, and files each finding as its own task.
- The built-in `/simplify` and `/code-review --fix` only look at the latest changes and apply fixes
  straight away. No ranking, no measurement, no approval per batch.
- Removable code found so far is spread across the deferred-findings task (improvement-133, entries
  4-8, 23, 29-31) with no estimate of what each one would save.

##### The means needed

The command reuses what already works instead of copying it:

1. **One new command, `/optimize`, with four modes** chosen by its first word. It lives in
   `.claude/skills/optimize/` because a skill folder can also hold a helper script (the official
   docs say a command and a skill are now the same thing, and both are typed as `/optimize`):
   - `/optimize <scope>`: find and rank. Never edits application code. The scope is `module <name>`,
     `path <folder>` or `all`.
   - `/optimize report`: shows the ranked list, the estimated and actual savings, what was inspected
     and what was not.
   - `/optimize apply <ID>[,<ID>]`: changes only the findings you name, after a plan and your "yes".
   - `/optimize verify [<ID>]`: measures the real savings from the diff, checks the hard rules and
     runs the tests.
2. **One new read-only reviewer, `code-reduction-reviewer`**, next to the three `/review`
   reviewers. It looks for the kinds of code your draft names (repeated logic and mapping,
   boilerplate that Lombok, MapStruct or Java can remove, forwarding wrappers, layers that add
   nothing, repeated form state and test setup). It carries the project's own checks: the
   `entity.toDto()` rule, `FilterMapper` with MapStruct, the Vaadin Binder DTOs, Lombok usages,
   different security or transaction rules behind similar-looking code. For each finding it gives
   an estimate (lines removed and added, files touched), the risks and the tests needed, and it
   also lists the ideas it rejected and why.
3. **A small extension of the existing review coordinator** (`deep-review-orchestrator`) so it can
   run a chosen reviewer instead of the usual three, skip given folders, scan a single folder, and
   return the checked findings as data. Its checking (each finding re-read in the real file by a
   cheap model, a check that findings do not contradict each other, and a search of the backlog for
   work already planned) is reused as it is. `/review` keeps working the same way.
4. **One helper script** in the skill folder, with no model call. It counts the lines added and
   removed per file from the git diff and fails when a batch touches `marketplace-orchestrator`,
   adds a Maven dependency or adds reflection.
5. **One standing backlog file** that holds every finding (ID `OPT-<n>`, status, estimate, later
   the actual savings), the rejected ideas and a log of what each run inspected. It works the same
   way as the existing deferred-findings task. Because it lives in `backlog/tasks/`, the backlog
   search in point 3 finds it, so a finding is never reported twice.

Applying a batch reuses the rules you already have: a plan in the approval format, a direct
question, one Haiku agent per fully specified edit (the same procedure as `/autopilot`), the
smallest tests first (`build-and-test.sh --unit-test <module>` or `--integration-test <class>`),
then the full suite your Definition of Done requires, and a stop after every batch. Nothing is
committed.

##### What the result will look like

- `/optimize module user-spring-boot-starter` runs in the background and comes back with, for
  example, "4 confirmed, 1 needs a small proof of concept, 3 rejected, 2 already in the backlog;
  16 of 16 Java files read, `target/` not read". It asks whether to add them to the backlog file.
- `/optimize report` prints one table: ID, title, estimated net lines, actual net lines, risk,
  status, plus the totals with overlaps counted only once.
- `/optimize apply OPT-3` shows the exact files and changes, waits for "yes", applies them, runs
  the tests, then prints "OPT-3: -41 +12 = net -29 lines in 3 files, unit tests 212/212 passed",
  writes that into the backlog file and stops.

##### Cost

| Item | Executor | Effort | Tokens (estimate) |
|---|---|---|---|
| Build the command (11 steps, see the machine plan) | sonnet for 7 steps, haiku for 4 | about 1.5-2 hours | sonnet about 300k, haiku about 40k |
| Optional review of the new files with `/review` | inherit (main model) + haiku checks | 15 min | about 200k (one earlier run of the same kind used 217k) |
| Optional pilot run, `/optimize path query-lib` (14 files) | inherit + haiku | 10-20 min | about 80-150k |
| Each later discovery run on one medium module | inherit + haiku | 10-30 min | about 100-300k; one reviewer pass cost 40k-170k in earlier runs |
| Each applied batch | main thread + haiku per edit + test scripts | depends on the batch | about 30-100k, plus test time |

The token numbers are estimates from the project's own past runs, not measurements of this
command. The main thread's own tokens cannot be measured.

##### Risks

- **A reviewer might call similar code "the same" when its security, validation or transaction
  rules differ.** The reviewer is told to check this, every finding is re-read in the real file,
  and nothing changes without your approval and green tests.
- **Large modules (marketplace-app, 212 Java files) are too big for one reviewer pass**, so it would
  skim. The command recommends `path <folder>` scopes for that module and records what was not read.
- **Estimates can be wrong.** They are marked as estimates. Only `verify` writes actual numbers,
  taken from the git diff (all lines, comments and blank lines included).
- **Changing the review coordinator could break `/review`.** The new options are only used when
  they are given; without them it does what it does today. The final check runs `/review` once to
  confirm.
- **The backlog file and improvement-133 overlap.** Entries already in improvement-133 are linked,
  not copied.

##### Where the project's practice differs from best practice

- The project's commands have no frontmatter. The official docs show `disable-model-invocation:
  true` for a command with side effects (their example is a deploy), so the model cannot start it
  by itself. The plan adds it to `/optimize` only.
- The project's skills so far are writing standards that load themselves. `/optimize` would be the
  first skill that runs a workflow, and that is the only reason it is a skill: the folder can hold
  its helper script. The docs treat the two as the same.
- An earlier project decision (in `.claude/DECISIONS.md`) rejected a layer of automatic scanners for
  review. Outside research favours scanners where they are reliable. They agree here: SonarQube
  only counts a Java duplicate at 10 or more repeated statements, and OpenRewrite would have little
  to convert because Lombok is already used almost everywhere (176 files). So the search stays with
  a model, and only the measuring and the rule checks are done by a script.
- A side finding, not part of this plan: the ADR extraction script fails for the `.claude` module
  ("`.claude/DECISIONS.md` not found" although the file exists).

##### Decisions waiting for you

1. **What `/optimize` with no scope does.** (a) shows the report and asks which module or folder to
   search (proposed); (b) searches the modules changed on this branch; (c) searches every module
   except `marketplace-orchestrator` (about 15 runs, expensive).
2. **Where the findings live.** (a) a new standing backlog file, `improvement-<next>-code-reduction-findings`
   (proposed); (b) new entries in improvement-133; (c) a new folder under `backlog/`.
3. **SonarQube duplication data as a starting point.** It needs one word added to the
   `sonar-analyst` setup and a fresh scan, and it misses short repeats. Not in the first version
   (proposed), or in.
4. **OpenRewrite.** Not part of the command (proposed). Optionally a separate one-off task: one
   `rewrite:dryRun` with the Lombok recipes to see what it would really change.
5. **Command or skill folder.** A skill folder with the script inside it (proposed), or
   `.claude/commands/optimize.md` with the script under `scripts/`.
6. **Model auto-start.** Add `disable-model-invocation: true` so only you can start `/optimize`
   (proposed), or leave it out like the other commands.

#### Execution plan

##### Before step 1

- The decisions in `plan-human.md` "Decisions waiting for you" must be answered first. This plan
  assumes the proposed option of each (1a, 2a, 3 no, 4 no, 5 skill folder, 6 yes). If an answer
  differs, the steps named in brackets change: 1b/1c -> step 5; 2b/2c -> steps 1 and 5;
  3 yes -> add a step for `sonar-analyst`'s `SONARQUBE_TOOLSETS` plus a seeding sub-step in step 5;
  5 command file -> steps 4, 5, 8, 9; 6 no -> step 5's frontmatter.
- Every step follows `.claude/rules.md` (Approval Rule per sub-change, `git add` after every file
  change, never commit, comments one line or none, no ticket or ADR numbers in current-state
  files, English only). Re-read `.claude/rules.md` and root `CLAUDE.md` at the start.
- Never edit application code (`*/src/**`) in any step of this plan. Never edit anything under
  `marketplace-orchestrator/`.

##### Steps

##### Step 1 - the standing findings inventory

- **Depends on:** decision 2.
- **Files:** a new `backlog/tasks/improvement-<next>-code-reduction-findings.md`; `backlog/BACKLOG.md`.
- **Executor:** sonnet.
- **Input:** `.claude/commands/new-task.md`; `backlog/tasks/improvement-133-deferred-oversized-review-findings.md`
  (the shape of a standing collection task: Type, Module, Priority, When, Purpose, Entries).
- **Change:** run `/new-task code-reduction findings (running inventory for /optimize)`. Shape the
  file like improvement-133: `**Type:** improvement - running inventory, not a single fix`;
  `**Module:** cross-cutting`; `**Priority:**` the same level as improvement-133 (larger
  tech-debt); `**When:** ongoing`. Sections, in this order:
  - `## Purpose` - two or three sentences: this file holds every finding of the `/optimize` skill,
    its status, its estimate and later its actual saving; the entry format is defined in the
    skill's own file and not repeated here; findings already tracked elsewhere in the backlog are
    linked, not copied.
  - `## Coverage` - empty list; one line per discovery run is added by the skill.
  - `## Findings` - empty.
  - `## Rejected ideas` - empty.
  - the standard `## Operational notes
- token_cost_review: 247702 (orchestrators 55174 and 54211; finders as reported: DRY/KISS/YAGNI 57593, SOLID 40616 and 40108; the precedent lens did not report tokens, so the total is incomplete)
- token_cost_research: 177688 (the first real `/deep-plan` run, topic `optimize-command`)
- token_cost_verification: n/a
- review_signal_ratio: 0 / 0
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: planning a task in depth with a researched plan, then implementing the approved plan and reviewing and checking the documentation of the same change set
- flows_chosen: /deep-plan, /review, a manual check of the documentation targets of /sync-docs
- flows_matched: n/a
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 2
- haiku_decomposition_rework_needed: 0 / 2
- adrs_referenced: ADR-005 (.claude)

### Agent calls
- Deep plan of the optimize command | subagent_type=deep-planner | tokens=177688 | tool_uses=56 | duration_s=1265 | mode=background | batch=solo
- Create task 224 and its backlog row | subagent_type=general-purpose (model haiku) | tokens=70252 | tool_uses=6 | duration_s=89 | mode=background | batch=solo
- Apply the exact edits of steps 6 to 9 | subagent_type=general-purpose (model haiku) | tokens=101108 | tool_uses=14 | duration_s=49 | mode=background | batch=solo
- Code reviews of the staged change sets (two runs: the planning command, the optimize command) | subagent_type=deep-review-orchestrator | tokens=109385 plus finders as above | mode=background | batch=solo each

### Script/command runs
- bash backlog/research/tools/scripts/*.sh smoke test on a throw-away topic | duration_s=about 10 | mode=foreground | result=pass
- bash .claude/commands/optimize/check-batch.sh smoke test in a throw-away repository (two rounds, the first found a pipe problem that was fixed) | duration_s=about 5 | mode=foreground | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh (several runs) | duration_s=about 120 | mode=foreground | result=pass
- bash .claude/nav/scripts/check-flows-completeness.sh and check-hardcoded-counts.sh | duration_s=1 | mode=foreground | result=pass

### Review angle yield
- DRY/KISS/YAGNI | survived=0 | total_candidates=0 | tokens=57593
- SOLID | survived=0 | total_candidates=0 | tokens=40616
- precedent | survived=0 | total_candidates=0 | tokens=n/a
