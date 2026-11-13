# improvement-248: Remaining work of the AI-instruction refactor: tighten the research pair and the analysts, the multi-line comments, a full documentation sync

**Type:** improvement — the rest of the work opened in improvement-240, split off when that task was closed
**Module:** `.claude/commands/`, `.claude/agents/research/`, `.claude/agents/sonar/`, `.claude/agents/dagu/`, `integration-tests`, `docs/architecture/scripts/generate-architecture-model.sh`, `.claude/rules/`, `.claude/skills/`, `CLAUDE.md`
**Priority:** 🟡 high, at the top of the backlog
**When:** independent, no blockers

## Current state

Done in [improvement-240](../completed/tasks/improvement-240-sync-docs-refactor.md), saved in the history:

- `/sync-docs` rewritten (400 lines to 264) with a scopes table, Step 2b "related documents", a re-read with a file-and-line check, fixed report templates; its default scope is the uncommitted changes, `<ref>` takes committed changes, `task <id>` takes the task's commits plus the uncommitted ones.
- `/deep-review` and the review family (`deep-review-orchestrator`, the command and the four lenses) rewritten from the goal, with a scope `<a>..<b>`, a scope `paths <file>[,<file>]`, one fixed final result, a doc/code mismatch finding and an `inspected` list from every lens; every file passed four rounds of two read-only checks until both said ready.
- `/optimize` renamed `/reduce-code` (in development), tightened, and extended with a research of patterns and best practices (kind `pattern`, field `Source`, its hard limit); new command `/tighten-ai` with its checklist.
- The map generator fails on a reference column without the `References <table>(<column>), no FK` marker; two changelog hooks and the documentation skill remind about it; the module table of the root `README.md` is checked by `check-module-lists.sh`.

## Why change

What is not done keeps the AI instructions uneven (some files tightened against a checklist, others not), leaves five comments and many generator comments against the one-line comment rule, and leaves the documentation as a whole unchecked against the code since no full audit has run.

## Expected benefit

The AI instruction files that remain read fast, have no duplicates or ambiguity and lose no requirement; the comment rule holds in the test classes and the generator; a full audit shows what is stale and what was not checked.

## Approach

Each item below keeps the process used for the finished ones: the goal written and confirmed first, the checklist of `.claude/commands/tighten-ai.md` (no new research for a kind already covered), a coverage table, read-only checks until none finds a lost requirement, one file written per direct yes, never committed without being asked.

1. **Group 2 of the tighten run, the research pair** (`.claude/commands/deep-research.md`, `.claude/agents/research/deep-researcher.md`; 164 lines, 11,738 characters). Goal, confirmed 2026-11-09: `/deep-research` makes the researcher do a deep research from primary sources and the project's practice, driven by the brief, and write the two plans (people and AI), changing nothing else in the project; no requirement is lost: the brief, the stop rule, the loop signals, the formats of the plans. The agent has `Write`, so its limit to `backlog/research/<topic>/` is checked first.
2. **Group 3, the analysts** (`.claude/agents/sonar/sonar-analyst.md`, `.claude/agents/dagu/dagu-analyst.md`; 96 lines, about 5,800 characters). Goal, confirmed 2026-11-09: each gives structured data from its server, read-only; no requirement is lost: what it calls, what it returns, what it forbids.
3. **Five multi-line comments in two test classes:** `TaxonServiceTest` lines 91 to 96 and 108 to 111, `ProviderProfilePaginationScenarioTest` lines 26 to 31, 191 to 192 and 205 to 208. For each: where the rationale goes (task, decision record, or nowhere when it is local context) and one line in its place; decided with the user, not alone.
4. **Multi-line comments of `docs/architecture/scripts/generate-architecture-model.sh`:** count the runs first (an earlier count said 184, a later one said 93), then the same decision for each, one line each.
5. **A full documentation sync:** `/sync-docs --full-audit` (every `README.md`, `DECISIONS.md`, `CLAUDE.md`, `.claude/rules/*.md`, every `*.java` Javadoc and every infrastructure file header, against the current code), fix what it finds, follow its report format.
6. **`/tighten-ai` on the rest:** the other commands, the rules files, the skills and the `.claude/` folder; each run with its own confirmed goal.
7. **A first real run of the new `/sync-docs` text with edits** on a real change, and a `/deep-review` of the pattern-research edit of `/reduce-code` (it was checked by two read-only passes only).
8. **Known leftovers in the review family:** the paragraph "do not flag what needs context outside the scope" now allows confirming reads outside the scope but a lens still has no rule for a very long single file beyond the offset and limit reads; a file deleted by a diff is named under "Not checked" and not reviewed.

## Tighten goal

Run of `/tighten-ai .claude/rules task 248`, goal confirmed 2026-11-12.

The files of `.claude/rules/` guide Claude when it reads or edits code of one module (or backlog tasks): each contains only what is true and mandatory for that module, in the smallest number of exact, checkable lines. Claude follows every requirement as well as before or better and loads fewer tokens when a path-matched file enters its context. The shortening removes repetition, history, explanations of what Claude already knows and facts owned by another file (a reference by name replaces them). It never changes what an instruction requires.

Constraints: the `<!-- #arch-embed:KEY -->` markers of `platform-commons.md` and the structure the architecture generator reads from each file (line 5, the marked blocks) stay valid so that the generator runs; the text of those lines and blocks may change when the proposal says why, and the map is regenerated once at the end; the `paths:` frontmatter of every file stays unchanged.


Run of `/tighten-ai CLAUDE.md task 248`, goal as proposed and accepted by the instruction to follow the command.

The root `CLAUDE.md` is loaded into every session and gives Claude only what is true and mandatory for the whole project: the module overview, the architecture constraints, the coding rules, and where the detailed guidance lives. Claude follows every requirement as well as before or better, and the file takes less context: repetition, history, explanations of what Claude knows, facts that have a home in another file (a reference replaces them) and what Claude can derive from the code are removed. The shortening never changes what an instruction requires.

Constraints: the section titles that other files name (`Architectural Decisions Log`, `Database Changes`, `Architecture Guidelines`, `Module Layout`) stay word for word; the `@.claude/rules.md` import and every item that has no other home are not touched; before a list of classes or the command catalogue is shortened it is checked that each fact really lives in its home.

### Tighten plan

Final plan, 2026-11-12. Research: `backlog/research/tighten-ai-rules/` (`plan-human.md`, `plan-machine.md`), kept until the run is finished. For every open decision the highest-quality option is the default.

Decisions:

- Length target 200 lines (a file that cannot reach it without losing a requirement is reported, not split); class lists become a pointer to the generated map and every sentence that is a rule moves under `Key constraints`; the nine dead pointers to a module `CLAUDE.md` are corrected in the proposal of the file that holds them.
- The raw Docker commands in the `playwright.md` prerequisites are replaced by the project script `deploy-and-run.sh`; `scripts.md` gets a title that names its subject (line 5 and the card text on the map change); the `arch-embed` blocks of `platform-commons.md` are rewritten with the file (markers intact, the generator accepts the file, then one generator run).
- Real class, method and file names used as an illustration are replaced by an abstraction (a generic form, or dropped where the general sentence is complete without it), following the repository rule; a name stays only where the rule is about that class or script (`subject`) or points to where a fact lives (`home`). The behaviour check guards against a weakened requirement, and each proposal shows every such place with its kind.
- `/doctor prompt-audit .claude/rules` (version 2.1.283 or later) runs headless through `claude -p` (tested on the pilot: it prints a report and a proposed diff and applies nothing). The run made during the pilot is the baseline; the second run follows the last file.

Simplified process, 2026-11-12 (fewer approvals and fewer tokens, the gates kept):

- Two approvals per group of files: one to run the pipeline for the group (one orchestrator agent runs inventory, proposal, mechanical checks and review), one to write the group. The report of a group shows only what needs a decision (changed facts, review findings, contradictions); the full proposals lie in the session scratch folder.
- Gates every file passes: (1) a coverage table, every requirement unit classed and none lost; (2) the mechanical checks of plan-machine step 4 with the repository rules R1 to R8; (3) one independent read-only review per group (sonnet; opus for group 4); (4) the behaviour check of step 6 (three probes, two runs per arm, sonnet, a temporary worktree) only where content is removed or moved, that is groups 2 to 4 and the files of group 1 where more than wording goes; (5) the user's direct yes before a group is written and `git add`ed.
- Done: pilot `audit-spring-boot-starter.md` written and staged (35 to 29 lines, 1549 to 1381 characters; mechanical checks, two reviews and the behaviour check passed; the review caught a narrowed scope that was restored; the 12 behaviour runs cost $1.75).

Groups, in this order:

1. The small module files: `advertisement`, `apikey`, `attachment`, `contact`, `feedback`, `promotion`, `provider-profile`, `taxon` and `user-spring-boot-starter`, `html-sanitizer-lib`, `query-lib`, `marketplace-mcp`.
2. `backlog-tasks.md`, `playwright.md` (script instead of Docker commands, the contradiction about a clean database, the wrong pointer), `marketplace-rest-api.md`.
3. `platform-commons.md` (wrong implementor of `AuditActivityEnrichHook`, wrong forwarder count, the `arch-embed` blocks; one generator run afterwards).
4. `integration-tests.md`, `marketplace-orchestrator.md`, `marketplace-app.md`, `scripts.md` (title that names the subject).
5. `README.md`, after two tests of its mechanism claims on the installed version (loading on `Write`, an anchored pattern) with `claude -p` in a temporary worktree.

Native commands: the built-in Claude Code commands that help with instructions (`/doctor prompt-audit`, `/doctor`, `/context`, `/skill-doctor`, `/skills`, `/memory`, `/compact`, `/insights`) are catalogued in `.claude/commands/README.md` with a column for the benefit each has delivered here; it appears on the map in the AI Tooling card `commands`, as its own section, after the next generator run. Every use of one of them in this task updates its last column; the audit step of `/tighten-ai` uses `/doctor prompt-audit`.

Afterwards: verification of the whole (plan-machine), `/sync-docs` in diff mode, one generator run, the second `/doctor prompt-audit`, the summary in this section; then `/tighten-ai CLAUDE.md task 248` with its own plan. Nothing is committed; the research folder is removed only with the user's explicit approval.

### Pending: measures in /tighten-ai and the audit in /sync-docs

Agreed, not started. Agent: haiku (exact text). Input: `.claude/commands/tighten-ai.md`, `.claude/commands/sync-docs.md`, `.claude/commands/README.md`, `.claude/nav/flows.md`, root `CLAUDE.md`. No third-party text is used, so no source or licence line is needed.

1. `.claude/commands/tighten-ai.md`: in step 4 (Proposal, per file), after "the benefit against the goal", add "the measures before and after (see Before/after measures)". After `## Checklist` add a section `## Before/after measures`: "For each file report, before and after: the number of checklist items satisfied (of 14 for any instruction file, of 20 for a command), the number of names the text gives (path, command, class, section) that do not exist (checked with `ls` and `grep`), and the lines and characters. A rewrite does not lower the satisfied items, does not raise the missing names and lowers the characters; otherwise the proposal says why." No scores, weights or grade bands.
2. `.claude/commands/sync-docs.md`: in Full Audit Mode, Step A2, add a first-level item: "Prompt audit (`--full-audit`, and `--module` for a module with rules): once over `.claude/rules` and the root `CLAUDE.md`, run `claude -p "/doctor prompt-audit <path>" --model sonnet --permission-mode dontAsk --output-format json`; every finding is a candidate that the other checks of this step verify against the code, and a finding that cannot be verified is reported as unverified." In the Step A6 report template add the line `Prompt audit: <found> found, <verified> verified, <applied> applied, <unverified> unverified`. Diff mode is left out because one run costs about $1.1 per folder.
3. Mirrors, one clause each: the `/tighten-ai` and `/sync-docs` rows of `.claude/commands/README.md`, the matching rows of `.claude/nav/flows.md` and the `/tighten-ai` line of the root `CLAUDE.md`; in the `/doctor prompt-audit` row of `.claude/commands/README.md` add "used by `/tighten-ai` and `/sync-docs --full-audit`".
4. `git add`; no commit; the map is generated once at the end.

## Tighten summary (root CLAUDE.md)

Run of `/tighten-ai CLAUDE.md task 248`. Measures: 240 lines and 22,806 characters before, 142 lines and 13,891 characters after (about 9,000 characters, roughly 2,250 tokens, less in every session and in every dispatched agent).

What changed into what:
- the class catalogue of `platform-commons` (it lacked 22 classes that exist) became one line that names where the packages are in the source tree; the two prohibitions that a class does not exist stay;
- the catalogue of commands and agents became pointers to the command README and the navigation table, with the sentences that are instructions kept (which commands run only when asked, that `/reduce-code` is hidden);
- the 17 listed decision-file paths became a pattern plus the generated index (25 files exist); the paragraph about the eight modules without their own file stays;
- the pointers to rules files became a compact list of names with a description each;
- the guidelines on Vaadin (2 and 7) state the shared scope once and the second points to it; capitals used for strength are lower case; three reference implementations became one.

Facts corrected, each with its evidence in the proposal: the sanitizer library has three consumers, not two; the `/run-all-tests` pointer named the wrong file; "Liquibase seed data" in the dev profile has no source (no Liquibase context or seed exists); the production variable list points to `INFRASTRUCTURE.md`; a count of modules was dropped; the description of the scripts rules file now covers deployment, tests, Playwright and CI as well as SonarQube.

Left unchanged: the `@.claude/rules.md` import, the module tree byte for byte, every title, label and number that other files quote, the "(in development)" tag of `/reduce-code`, the Java and repository rules (they apply to Java only but stay in the root file).

Checks: coverage table (127 units), shell checks, review findings checked against the code (one word restored in the UI-indirection rule), behaviour probes (two closed-book, one open-book, two runs per arm, 12 runs, about $1.22, no loss on the probed rules; the old file also made the answers say its class list was out of date).

Prompt audit before and after (`/doctor prompt-audit` over `CLAUDE.md` and its import): before, 12 findings of which 5 concern `CLAUDE.md` (stale consumer list, wrong pointer, incomplete catalogue, two "does not exist" notes, the status tag); after, 11 findings of which 4 concern `CLAUDE.md`: the pointer description (fixed afterwards), the two "does not exist" notes (kept by decision), the missing `backlog-tasks.md` in the compact list and the status tag (flags). The findings on the imported `.claude/rules.md` are unchanged and outside this run: module `CLAUDE.md` files that do not exist, a raw `mvn` command in a list that forbids it, "16 files" against 25, incident sentences, about 16 emphasis headers, and the rule to re-read the whole file before every tool call.

Open outside this run: the Java rules could move to a path-scoped file; the import is 646 lines and twice the size of the root file; `.claude/settings.json` has a hook that points to a starter `CLAUDE.md` that does not exist; `.claude/nav/flows.md` has the same wrong `/run-all-tests` pointer.

## Tighten research result

Research of the kind "rules file" for `/tighten-ai`, filed 2026-11-12 from the topic folder `tighten-ai-rules`, which was removed afterwards. All sources are Anthropic documentation or engineering posts: memory, best practices, prompting best practices, context window, features overview, large codebases, skills best practices, effective context engineering, steering Claude Code, demystifying evals, develop tests, headless mode (each under `code.claude.com/docs/en/`, `platform.claude.com/docs/en/` or `anthropic.com/engineering/`). The 11 open decisions of the research are resolved in `### Tighten plan` above.

### What the evidence says (the standard for a rules file)

Sources: [memory](https://code.claude.com/docs/en/memory), [best practices](https://code.claude.com/docs/en/best-practices), [context window](https://code.claude.com/docs/en/context-window), [features overview](https://code.claude.com/docs/en/features-overview), [large codebases](https://code.claude.com/docs/en/large-codebases), [prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices), [skills best practices](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices), [context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents), [steering Claude Code](https://claude.com/blog/steering-claude-code-skills-hooks-rules-subagents-and-more). All are Anthropic's; they agree with each other.

**Loading mechanism**

- A rules file with a `paths:` block loads when Claude reads, writes or edits a matching file. The `paths:` block is removed before the text reaches Claude.
- The loaded text sits in the conversation. When the conversation is compacted it is summarized away, and it comes back only when a matching file is touched again.
- A rules file without a `paths:` block loads in every session, like `CLAUDE.md`.
- A rules file loaded on demand is cut at 40,000 characters (200K window); Claude gets only the start. The largest file here has 27,259.
- These files are context, not enforcement. A rule with zero exceptions belongs in a hook.

**Length**: target under 200 lines per instruction file; "longer files consume more context and reduce adherence". The 500 lines of checklist item 7 come from the skills guide and are meant for a skill body.

**Content**: keep what Claude cannot work out from the code: exact commands, pitfalls, decisions, conventions that differ from the defaults, a short reason. Cut directory layouts, dependency lists, architecture overviews and anything derivable from the code. Procedures belong in a skill. Anthropic's own example of a rules file is a title and two bullets.

**Structure**: one topic per file; headers and short bullets, no dense paragraphs; numbered steps only where order matters.

**Wording**: concrete enough to verify ("Run `npm test` before committing", not "Test your changes"); no two instructions that contradict; plain wording without IMPORTANT or capitals (emphasis makes Claude over-apply a rule); say what to do; a short reason helps Claude apply a rule correctly; one or two exact examples, not a list of edge cases; one term per concept; no text that goes out of date.

### The checklist of `tighten-ai.md` against the evidence

| Item | Verdict | Note for a rules file |
|---|---|---|
| 1 goal first | kept, no source for rules files | One sentence, placed after the heading on line 5 (the generator reads line 5) |
| 2 concrete and checkable | confirmed | |
| 3 stated once, no conflict | confirmed | |
| 4 explicit scope | confirmed | A one-clause reason is the documented way to get the right generalisation |
| 5 reference, not restate | confirmed | |
| 6 no story | confirmed | The sources keep a short reason; item 6 already allows one clause |
| 7 nothing Claude knows; below 500 lines | first half confirmed, **the number is contradicted** | 200 lines is the documented target; see decision 1 |
| 8 one term per concept | confirmed | |
| 9 what survives compaction comes first | **does not apply as worded** | The 5,000 tokens are for commands and skills. A rules file is dropped at compaction and reloads on the next matching file; only the 40,000-character cut keeps the start |
| 10 headers, steps, bullets | confirmed | |
| 11 exact commands for fragile work | confirmed | |
| 12 hard limits in one place | confirmed | In these files that place is the `Key constraints` section |
| 13 no count that changes | confirmed | |
| 14 hook candidates are flagged | confirmed | |

Added by the research (A1 to A8):

- **A1** Keep only what cannot be derived from the module's code; a class list, a package layout or a method list becomes a pointer to its home (decision 4).
- **A2** Only what is specific to this module; what holds for every module is owned by the root files and is referenced.
- **A3** A multi-step procedure or a list of script flags is referenced at its home (the script header, a skill), with at most the one command needed.
- **A4** No emphasis for strength (IMPORTANT, capitals, a whole sentence in bold).
- **A5** At most one exact example per rule (decision 10 for examples that name a real class or file).
- **A6** Every file or section a line points to exists.
- **A7** A length target (decision 1); never over 40,000 characters.
- **A8** Unchanged by a rewrite: the `paths:` block, line 5, the five `arch-embed` blocks, and every section title that a file outside the folder names.

### Where the 22 files differ from the standard today

| What | Where | Typical |
|---|---|---|
| Hand-written class lists | 10 of the 14 files with `What it owns`; 4 already point to the generated map (`audit`, `attachment`, `advertisement`, `user`) | `marketplace-orchestrator.md` lines 15 to 190 describe each class in turn; `marketplace-rest-api.md` lines 17 to 103 |
| Over 200 lines | `scripts.md` 358, `marketplace-app.md` 326, `marketplace-orchestrator.md` 309, `integration-tests.md` 213 | |
| History and proof wording ("confirmed", "measured", "no longer", "now", dated headings) | `scripts.md` 18 places, `marketplace-app.md` 10, `marketplace-orchestrator.md` 8, `marketplace-rest-api.md` 8; three dates in `integration-tests.md` | "confirmed by direct testing, not assumption" |
| Dense paragraphs (lines over 400 characters) | `platform-commons.md` 6, `scripts.md` 3, `marketplace-app.md` 2, `playwright.md` 2 | the CI runner section of `scripts.md` is one block of about 60 lines |
| Copies of script headers and procedures | `scripts.md` (the flag list of `ci.sh`, manual plugin install), `playwright.md` (prerequisites) | 44 lines of `ci.sh` flags |
| Pointers to files that do not exist | 9 pointers in 7 files name a module `CLAUDE.md`; only the root `CLAUDE.md` exists | "see `marketplace-app/CLAUDE.md`" |
| Counts that change | `marketplace-orchestrator.md` ("Five so far", "all 10 starter"), `scripts.md` ("all 9"), `README.md` ("11 of the 19 rule files") | |
| The same thing twice in one file | `platform-commons.md`: the paragraph "Why ports and hooks must live in `platform-commons`" repeats the marked block `why-port-hook-glossary` | |
| Possible conflict with a root rule | `playwright.md` prerequisites give raw `docker build` and `docker run` commands; `.claude/rules.md` says to use project scripts only | shown to you, not resolved by the executor |
| Real names used as examples | 23 places in 12 files write "e.g." followed by a real class, method or file | "e.g. `UserTestFixtures`" |

Not found in the folder: an ADR number (the files already point to the generated ADR index, 37 times in 10 files) and a task or ticket number.

### How it is checked that quality does not drop

Three layers, each proving a different thing:

1. **The words survive**: the coverage table and the read-only reviewer (already in `/tighten-ai`).
2. **The repository's own rules hold on the new text**: a mechanical check and the same reviewer, with the exact list (abstract principle, no ticket number, no dated narrative, no ADR number, no real file as an example unless unavoidable, target state only, one canonical home, `paths:` block, marked blocks and line 5 unchanged). Written out in `plan-machine.md`, steps 4 and 5.
3. **Claude behaves the same**: a before/after check on concrete tasks. This is the new part.

What the evidence says about layer 3 ([evaluating agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents), [develop tests](https://platform.claude.com/docs/en/test-and-evaluate/develop-tests), [skills best practices](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices), [headless mode](https://code.claude.com/docs/en/headless)):

- The recommended method is a comparison against a baseline on tasks that mirror real work, with several runs of each task because answers vary, a clean environment for each run, a pass or fail statement fixed before the run, and a person reading the answers.
- Anthropic says there is no built-in way to run such checks for instructions, and calls 20 to 50 tasks "a start".
- **No reliable method exists to prove that Claude behaves the same or better with a shortened file.** A small check can catch a clear loss on the rules it probes; it cannot show that nothing else was lost.

The check that follows from this, per file: three questions, each built on a rule of the file that Claude would not follow without being told; each asks Claude to read one file of the module and describe, without editing, how it would do a task that tempts a violation. The same question is put twice to Claude with the old file and twice with the new file, in a throw-away copy of the repository, through `claude -p`. The new file passes when it passes everything the old file passed. A question that the old file itself fails is reported to you: the rule is not followed even today (a hook candidate).

Cost of layer 3: 12 short runs per file. My estimate, not measured: 0.4 to 0.6 million tokens per file on sonnet, so 9 to 13 million for all 22 files, several times the cost of the rewrite itself. `claude -p` prints the real cost of each run, so the pilot file gives the real figure before the rest is decided (decision 9).

### Risks

- A sentence inside a class list is really a rule and gets dropped with the list. Guard: the coverage table, the rule "a fact is replaced by a pointer only after it is found at its home", and the reviewer.
- A file outside the folder names a section title that the rewrite renames. Guard: the titles are listed before the rewrite and checked after it.
- The map goes stale: line 5 of `scripts.md` and `playwright.md` is shown on the map, and `docs/architecture/data/arch-embed-index.md` stores the line numbers of the five markers of `platform-commons.md`. Guard: line 5 and the blocks stay byte-identical; the line numbers are decision 3.
- The behaviour check gives false comfort: it probes three rules per file with two runs each. It is reported as "no loss found on the probed rules", never as "quality proven".
- The behaviour check was not tried here: whether `claude -p` is signed in inside the working container is not verified, and the project's own hooks run in such a session.
- The saving in tokens is real only when a module file is opened; it does not change what loads in every session, except for `README.md` (decision 2).

### Where the project's practice differs from best practice

1. **Length limit.** The checklist says 500 lines; the documentation says 200 for an instruction file.
2. **`README.md` inside `.claude/rules/` has no `paths:` block.** By the documentation such a file loads in every session (6,144 characters). The brief assumed every file has the block. It is a document about the folder, not a rule for Claude.
3. **`README.md` states two facts that the current documentation states differently:** it says a rule does not load on `Write` and that no anchored pattern is known; the documentation says Read, Write or Edit, that a pattern is relative to the project root, and lists `/*.md` as an anchored form. The project observed the opposite on an earlier version; the installed version was not tested.
4. **Class lists in rules files.** The project already moved four files to a pointer to the generated map; best practice says the same for the other ten.
5. **Procedures in rules files.** Best practice puts a procedure in a skill or leaves it in the script header; `scripts.md` and `playwright.md` carry them inline.
6. **Line 5 of `scripts.md` is "Deployment",** the title of its first section, and the map shows it as the description of the whole `scripts/` card.
7. **Real names as examples: the repository rule and the evidence pull in opposite directions.** The repository rule says new or edited text names a real file only when unavoidable, because the name goes stale. Anthropic's guidance says an instruction should be concrete ("API handlers live in `src/api/handlers/`", "examples are concrete, not abstract") and that Claude follows an example closely. See decision 10.

## Execution plan

Executor plan of the research, valid as the frame of every group of `### Tighten plan` (the steps 4 to 7 of `.claude/commands/tighten-ai.md` stay the frame; `.claude/rules.md` holds throughout). Where it names a decision of the research, `### Tighten plan` holds the answer.

### The per-file checklist (the standard)

Items 1 to 14 of `.claude/commands/tighten-ai.md` hold, read for a rules file as follows, plus A1 to A8.

| Id | Check | Source |
|---|---|---|
| 1 | One sentence of purpose directly after the heading on line 5; no second introduction | checklist |
| 2 | Every instruction is a command, a path, a condition or a count | S1 |
| 3 | Every behaviour once; no two lines conflict, inside the file or with `CLAUDE.md` / `.claude/rules.md` | S1 |
| 4 | Scope words explicit ("every", "only", "never"); a reason is one clause | S3 |
| 5 | A fact owned elsewhere is a reference by name | S8 |
| 6 | No history, date, incident, "confirmed", "measured", "no longer", "now", before/after | S7 |
| 7 | Nothing Claude already knows is explained | S7, S2 |
| 8 | One term per concept | S7 |
| 9 | Not applicable as worded; replaced by A7 and by the flag in step 2 for a rule that must hold when no module file is open | S4 |
| 10 | Headers and short bullets; no paragraph over 3 lines; numbered steps only where order matters | S1 |
| 11 | Exact command for a fragile operation | S7 |
| 12 | The module's hard limits are one list (`Key constraints`) | checklist |
| 13 | No count or "today" fact | S7 |
| 14 | A rule that a hook, a test or a script could enforce is flagged, never moved | S2, S5 |
| A1 | Only what cannot be derived from the module's code: a class list, package layout, method list or dependency list is a pointer to its home (as decision 4 says) | S1, S2 |
| A2 | Only what is specific to this module; what holds for every module is referenced in the root files | S1, S5 |
| A3 | A multi-step procedure or a flag list is a reference to its home plus at most one command | S5, S9 |
| A4 | No IMPORTANT, no capitals for strength, no whole sentence in bold; a bold label at the start of a bullet is allowed | S2, S3 |
| A5 | At most one example per rule, exact; an example that names a real class, method or file is handled as decision 10 says | S3, S8 |
| A6 | Every path or section a line names exists | S1 |
| A7 | The length target of decision 1; never over 40,000 characters | S1 |
| A8 | Byte-identical to the old text: lines 1 to 3 (the `paths:` block), line 5, every `<!-- #arch-embed:KEY -->` block with its markers; every section title named from outside the folder is still present | project constraint |

### The repository rules for every new text (R1 to R8)

From `.claude/rules.md`, as the brief lists them. They are checked on the new text of each file in
step 4 (mechanically, where a pattern can find a breach) and in step 5 (by the reviewer, all eight).

| Id | Rule | Checked by |
|---|---|---|
| R1 | The text states the abstract principle; no case study, no retelling of an incident | reviewer |
| R2 | No task or ticket number (`improvement-NNN`, `feature-NNN`, `goal-NNN`) | pattern, reviewer |
| R3 | No dated "resolved" or "as of" narrative, no description of a past state | pattern, reviewer |
| R4 | No specific ADR number; "see `.claude/nav/adr-index.md`" is the allowed form | pattern, reviewer |
| R5 | No named real repository file as an example unless unavoidable or kept by the user's word (decision 10 says how each case is handled; a name that is the subject of the rule, or the home a reference points to, is not an example) | reviewer, with the list of step 2 |
| R6 | The target state only; never a before/after pair | pattern, reviewer |
| R7 | One fact in one canonical home: a fact owned by another file is referenced, never restated; the smallest unit that can hold the fact (a script header, a class's Javadoc) is its home | reviewer |
| R8 | The `paths:` block, the `arch-embed` blocks and the line the generator reads (line 5) are unchanged | pattern (same as A8) |

### The order of the files

1. Pilot: `audit-spring-boot-starter.md` (35 lines; already close to the standard, so it shows what a finished file looks like and measures the real cost of step 6).
2. Small module files: `html-sanitizer-lib.md`, `attachment-spring-boot-starter.md`, `promotion-spring-boot-starter.md`, `contact-spring-boot-starter.md`, `marketplace-mcp.md`, `apikey-spring-boot-starter.md`, `taxon-spring-boot-starter.md`, `query-lib.md`, `advertisement-spring-boot-starter.md`, `provider-profile-spring-boot-starter.md`, `feedback-spring-boot-starter.md`, `user-spring-boot-starter.md`.
3. Middle files: `backlog-tasks.md`, `playwright.md` (line 5 is on the map; decision 5), `marketplace-rest-api.md`.
4. `platform-commons.md` (the five marked blocks; decisions 3 and 11 first).
5. Large files: `integration-tests.md`, `marketplace-orchestrator.md`, `marketplace-app.md`, `scripts.md` (line 5 is on the map; decision 7).
6. `README.md` last and only if decision 2 says (a).

### Steps

#### Step 1. Baseline

- Depends on: decisions 1, 2, 3, 4, 9 and 10 answered.
- Files: none written.
- Executor: haiku (or the main thread by a single Bash call).
- Input: the repository at its current state; `.claude/rules/` must be unmodified against `HEAD` (`git status --short .claude/rules` prints nothing).
- Exact change: none. Run and keep the output:

```bash
cd /app
wc -lm .claude/rules/*.md
for f in .claude/rules/*.md; do printf '%s | %s | %s\n' "$f" "$(sed -n 2p "$f")" "$(sed -n 5p "$f")"; done
grep -c 'arch-embed' .claude/rules/*.md | grep -v ':0'
git grep -n 'rules/[a-z-]*\.md' -- ':!.claude/rules' ':!backlog'
```

- Verification: 22 files; 21 print a `paths:` line as line 2 (`README.md` does not); only `platform-commons.md` has `arch-embed` lines (10). The last command is the list of outside references; from it, write down per rules file every section or bullet title quoted by an outside file. Known ones: `scripts.md` "Local CI Runner"; `platform-commons.md` "SPI Interface Naming", "Hook and Port Implementation Rules"; `marketplace-orchestrator.md` "Not every cross-domain call moves here", the "≤2 domain `*Port` types per class" bullet, and "Forwarder SPI pattern" (named from inside the folder). The list from the command is the authority, not this sentence.
- Sources: project constraint.

#### Step 2. Inventory of one file

- Depends on: step 1.
- Files: one file of `.claude/rules/`, read whole; nothing written.
- Executor: opus for `platform-commons.md`, `integration-tests.md`, `marketplace-orchestrator.md`, `marketplace-app.md`, `scripts.md`, `README.md`; sonnet for the other 16.
- Input: the file; `CLAUDE.md`; `.claude/rules.md`; the outside-title list of step 1 for this file.
- Exact behaviour: number every requirement unit of the file (a sentence or bullet that states one thing) and give each exactly one class:
  - `C` constraint: how to change the module's code safely, a naming rule, a forbidden dependency, a place where the code misleads. Kept.
  - `F` fact about the current state (a class and its role, a table list, a count, what a script does). Find its home (the class's Javadoc, the script header, the changelog `remarks`, the generated map, a root file) and confirm by `grep` or `Read` that the home states it. Found: becomes a reference. Not found: stays in the file and is listed under "home missing".
  - `P` procedure or flag list. Same test as `F` against the script header or the skill.
  - `H` history, proof, explanation of what Claude knows. Removed; a reason survives as one clause on its rule.
  - `X` example. One per rule stays.
  - `G` invariant of A8. Untouched.
  - A sentence inside a class description that says what must hold (who may call, what must never happen, which exception is thrown) is `C`, not `F`.
- Also list every place where a real class, method or file is named, with its kind: `subject` (the rule is about it), `home` (a reference to where a fact lives) or `illustration` ("e.g." and the like). Only `illustration` falls under decision 10.
- Constraints: decide nothing. List separately, for the user: a conflict (item 3) with both lines quoted; a rule a hook or test could enforce (item 14), naming the existing test when the file already names one; a "does not exist" note (keep as a prohibition, or remove as history: the user chooses); a dead pointer (A6) with the file that holds the content today; a rule that must hold when no file of the module is open.
- Verification: every line of the file from line 5 on belongs to a numbered unit; the count of units is stated.
- Sources: S1, S2, S8.

#### Step 3. Proposal for that file

- Depends on: step 2 for the same file.
- Files: none in the repository; the new text is put in a temporary file outside the repository (the session's scratch folder), named like the rules file.
- Executor: the same model as step 2 for that file.
- Input: the inventory.
- Exact behaviour: write the new text against the checklist and R1 to R8, then the outputs step 4 of `tighten-ai.md` asks for: lines and characters before and after; the coverage table (unit number, old text shortened, class, the line of the new text that keeps it or the home that holds it); what changed into what; the benefit against the goal; the flagged list of step 2; the list of `illustration` names with what decision 10 made of each.
- Constraints:
  - Lines 1 to 5 are copied from the old file byte for byte; line 6 is empty; the purpose sentence is line 7.
  - A marked block of `platform-commons.md` is copied byte for byte with its two marker lines; text outside the blocks that repeats a block is removed and the block stays.
  - Section titles of the outside-title list keep their exact words.
  - The words of a kept requirement may be shortened; its subject, scope, condition and strength may not change. "must", "never", "only" stay where they were; none is added.
  - The project's section names (`What it owns`, `Schema`, `Key constraints`) are kept where the file has them.
- Verification: steps 4, 5 and 6.
- Sources: S1, S3, S7.

#### Step 4. Mechanical checks of the proposal, the repository rules included

- Depends on: step 3.
- Files: none written.
- Executor: haiku.
- Input: `f` = the path of the rules file, `NEW` = the path of the temporary file.
- Exact change: none. Run exactly:

```bash
cd /app
OLD=$(mktemp); git show HEAD:"$f" > "$OLD"
# R8 / A8
diff <(head -3 "$OLD") <(head -3 "$NEW") && echo "R8 frontmatter: ok"
diff <(sed -n 5p "$OLD") <(sed -n 5p "$NEW") && echo "R8 line 5: ok"
diff <(sed -n '/<!-- #arch-embed:/,/<!-- \/#arch-embed -->/p' "$OLD") \
     <(sed -n '/<!-- #arch-embed:/,/<!-- \/#arch-embed -->/p' "$NEW") && echo "R8 embeds: ok"
# A7, item 10
wc -lm "$OLD" "$NEW"
awk 'length > 400 {print "long line " FNR ": " length " characters"}' "$NEW"
# R2, R4
grep -nE 'improvement-[0-9]+|feature-[0-9]+|goal-[0-9]+|ADR-[0-9]+' "$NEW"
# R3, R6, items 6 and 13
grep -nE '20[0-9]{2}-[0-9]{2}-[0-9]{2}' "$NEW"
grep -niE 'resolved|as of|no longer|anymore|used to|previously|formerly|before:|after:|confirmed|measured|so far|today|\bnow\b' "$NEW"
# A4
grep -nE 'IMPORTANT|CRITICAL|[A-Z]{2,} [A-Z]{4,}' "$NEW"
# R5 candidates (for the reviewer, not a verdict)
grep -nE 'e\.g\.|for example|such as' "$NEW"
# A6
grep -oE '[A-Za-z0-9_./-]+/[A-Za-z0-9_.-]+\.(md|sh|java|xml|yml|yaml|json|py|js|bat)' "$NEW" | sort -u \
  | while read -r p; do [ -e "$p" ] || [ -e ".claude/rules/$p" ] || echo "A6 check by hand: $p"; done
# outside titles
git grep -n -F "rules/$(basename "$f")" -- ':!.claude/rules' ':!backlog'
```

- Verification: the three `ok` lines are printed (for `README.md` the first two do not apply: it has no `paths:` block); the new file has fewer characters than the old one and at most 40,000; the R2/R4 list is empty; every hit of the R3/R6 and A4 patterns is either removed or explained in the proposal as a false hit (a word used in its plain sense); the R5 candidate lines are handed to step 5; every path printed by the A6 line is looked at and either exists under another base or is corrected; every title quoted in the last list is found with `grep -F -- '<title>' "$NEW"`. Report the output as it is; fix nothing.
- Sources: `.claude/rules.md`; S1 for the character limit.

#### Step 5. Independent review (lost requirements and repository rules)

- Depends on: step 4 without an open failure.
- Files: none written.
- Executor: sonnet, read-only (step 5 of `tighten-ai.md` fixes this model); a fresh agent, never a fork.
- Input: the prompt below with its six placeholders filled. The agent sees no conversation, so everything goes into the prompt.
- Exact prompt:

```text
You are a read-only reviewer. You edit nothing, write no file and propose no wording.

A rules file of this repository was rewritten to be shorter. Claude Code loads such a file into the
conversation when a file matching its `paths:` block is read or edited. The rewrite must keep every
requirement of the old text exactly: same subject, same scope, same condition, same strength.

GOAL OF THE REWRITE
<the text of "## Tighten goal" from the task file, with its constraints>

OLD TEXT (<path of the rules file>)
<the old file, whole>

NEW TEXT
<the proposed file, whole>

COVERAGE TABLE
<the table: unit number, old text, class, where the new text or another file keeps it>

DECISION ON REAL NAMES USED AS EXAMPLES
<the user's answer to decision 10, and the proposal's list of names with their kind:
subject, home or illustration>

OUTPUT OF THE MECHANICAL CHECK
<the output of step 4>

Do these four checks and report each under its own heading.

1. LOST OR CHANGED REQUIREMENTS. Go through the old text yourself, sentence by sentence; do not
   trust the coverage table. For each requirement, find where the new text keeps it. Report each one
   that is missing, weaker, stronger, or changed in subject, scope or condition. Report each row of
   the coverage table that is wrong or missing.

2. REFERENCES. Wherever the new text replaces content with a reference to another file or a
   section, open that target with the Read tool and confirm that it exists and states the fact the
   old text stated. Report each target that is missing or does not state it.

3. REPOSITORY RULES. Check the new text, outside lines that are byte-identical to the old text,
   against each rule and report every breach with its line:
   R1. It states the abstract principle; no case study and no retelling of an incident.
   R2. No task or ticket number (improvement-NNN, feature-NNN, goal-NNN).
   R3. No dated "resolved" or "as of" narrative and no description of a past state.
   R4. No specific ADR number; "see .claude/nav/adr-index.md" is the allowed form.
   R5. No named real repository file as an example, unless it is unavoidable or the decision above
       keeps it. A name that is the subject of the rule, or the home a reference points to, is not
       an example. Report each name whose kind in the proposal's list is wrong.
   R6. The target state only; never a before/after pair.
   R7. One fact in one canonical home: a fact owned by another file is referenced, not restated.
       Report each fact the new text restates although CLAUDE.md, .claude/rules.md, a script header
       or a class comment already states it (open the file to confirm).
   R8. Lines 1 to 3 (the `paths:` block), line 5, and every block between
       `<!-- #arch-embed:KEY -->` and `<!-- /#arch-embed -->` are byte-identical to the old text.

4. CHECKLIST. Report each line of the new text that fails one of these: every instruction is
   concrete and checkable; every behaviour is stated once and no two lines conflict, with each other
   or with CLAUDE.md and .claude/rules.md; scope words are explicit; no history; nothing Claude
   already knows is explained; one term per concept; headers and short bullets, no paragraph over
   three lines; no count that changes with the code; no emphasis for strength (IMPORTANT, capitals,
   a whole sentence in bold); at most one example per rule; every path or section named exists.

Format of every finding: check number, the line of the new text (or "missing"), the quotation from
the old text, the quotation from the new text, one sentence saying what differs. Under a check with
no finding write "none". End with one line: "READY" when all four checks have no finding, otherwise
"NOT READY: <number of findings>".
```

- Verification: a finding is fixed in the proposal (back to step 3, then step 4) and a fresh reviewer runs again until it answers `READY`. The main thread does not state its own verdict in place of the reviewer's.
- Sources: checklist item 20; S2 (a fresh reviewer has not seen the reasoning); `.claude/rules.md`.

#### Step 6. Behaviour check (before and after)

- Depends on: step 5 `READY`; decision 9 (which files, which model, whether a temporary copy of the repository is allowed). Without that answer, or for a file decision 9 leaves out, the step is skipped and the proposal says "behaviour not checked".
- Files: none of the repository. A temporary `git worktree` in the session's scratch folder, removed at the end.
- Executor: opus writes the three probes (design judgement); haiku runs the commands; the main thread grades.
- What it can show: a clear loss on the three probed rules. It cannot show that the new file is as good as the old one (S10: several trials and 20 to 50 tasks are called a start; S7: there is no built-in way to run such checks). Report its result as "no loss found on the probed rules" or as the loss found, never as proof.
- Exact behaviour:
  1. From the inventory choose three `C` units that Claude would not follow without being told (not derivable from the code), preferring units whose wording changed most or whose content became a reference. For each write a probe before any run: (a) one file of the module that matches the `paths:` block, to be read first, so that the rules file loads; (b) a task that tempts a breach of the unit; (c) one to three pass statements, each answerable yes or no from the answer alone (a name that must appear, a name or action that must not).
  2. The probe prompt has this exact shape: `Read <file>. Then, without editing or creating anything, describe how you would <task>: the files you would create or change, the class and method names, and the rules of this module your plan follows.`
  3. Run each probe twice with the old file and twice with the new file, each run a fresh session:

```bash
cd /app
W="<scratch folder>/tighten-probe"
git worktree add --detach "$W" HEAD
cd "$W"
run() { claude -p "$1" --model <model of decision 9> --permission-mode dontAsk --output-format json; }
# old arm: the worktree holds the old file
run "<probe 1>" > ../p1-old-1.json; run "<probe 1>" > ../p1-old-2.json   # same for probes 2 and 3
# new arm: the same worktree with the one file replaced
cp "$NEW" "$W/$f"
run "<probe 1>" > ../p1-new-1.json; run "<probe 1>" > ../p1-new-2.json   # same for probes 2 and 3
cd /app && git worktree remove --force "$W"
```

  4. Grade each of the 12 answers (`.result` of the JSON) against its pass statements. Add up `.total_cost_usd` of the 12 files.
- Verification: the new arm passes, in both runs, every statement the old arm passed in both runs. A statement the old arm failed in a run is not counted and is reported to the user as "not followed with the present text" (a hook candidate, item 14). A loss sends the file back to step 3. The proposal carries the three probes, the 12 grades and the cost. After the step, `git worktree list` shows only `/app`.
- Not verified by this research: that `claude -p` is signed in inside the working container; that the project's hooks let a read-only `-p` session run unhindered. The pilot file answers both; if a run fails, show the error lines and stop the step, the rewrite itself is not blocked.
- Sources: S10, S11, S7, S12.

#### Step 7. Approval and writing

- Depends on: step 5 `READY` and step 6 passed or skipped as decision 9 says.
- Files: the one rules file.
- Executor: the main thread asks; haiku writes.
- Input: the approved text in the temporary file.
- Exact change: show the proposal, write it into the task as `tighten-ai.md` step 4 says, ask "write this file?" and wait for a direct yes. Then replace the whole content of the rules file with the content of the temporary file, byte for byte, and run `git add` on it. Stop after the file. No commit.
- Verification: `cmp "$NEW" "$f"` prints nothing; `git diff --cached --stat -- "$f"` shows only this file; the three R8 checks of step 4 run again with `"$f"` in place of `"$NEW"` and print their `ok` lines.
- Sources: `.claude/rules.md` Approval Rule.

#### Step 8. `platform-commons.md` only: the marker line numbers

- Depends on: step 7 for `platform-commons.md`; decision 3.
- Files: none of `.claude/rules/`.
- Executor: the main thread.
- Exact behaviour: the rewrite moves the five markers, and `docs/architecture/data/arch-embed-index.md` stores their line numbers. With decision 3 (a): after its own approval, run the map generator once the way `.claude/rules.md` "Scripts" prescribes for a script, and stage the regenerated files. With (b): this file is not rewritten in this run.
- Verification: `grep -n 'arch-embed:' .claude/rules/platform-commons.md` gives the same line numbers as the `Source` column of `docs/architecture/data/arch-embed-index.md`.
- Sources: project constraint.

#### Step 9. Summary

- Depends on: the last file.
- Files: the task file only.
- Executor: the main thread.
- Exact behaviour: step 7 of `tighten-ai.md`: the files, the totals before and after (`wc -lm .claude/rules/*.md` against the baseline of step 1), what changed into what, the benefit against the goal, what was left unchanged and why, which files had the behaviour check and its result and cost, and one list of everything flagged (hook candidates, conflicts, "home missing" facts, real names kept as examples, files still over the length target).

### Which model for which step

| Step | Model | Why |
|---|---|---|
| 1, 4 | haiku | fixed commands, the output is reported as it is |
| 2, 3 | opus for the six hard files, sonnet for the other 16 | classifying a sentence as rule or fact is where a requirement can be lost |
| 5 | sonnet | fixed by `tighten-ai.md`; the prompt is complete |
| 6 | opus writes the probes, haiku runs them, the main thread grades | choosing a probe is judgement; running it is not |
| 7 | haiku | writes an approved text byte for byte |
| 8, 9 | main thread | needs the user's answers |

### Verification of the whole

Run after the last file and report the output:

```bash
cd /app
wc -lm .claude/rules/*.md
for f in .claude/rules/*.md; do
  diff -q <(git show HEAD:"$f" | head -3) <(head -3 "$f") > /dev/null || echo "frontmatter changed: $f"
  diff -q <(git show HEAD:"$f" | sed -n 5p) <(sed -n 5p "$f") > /dev/null || echo "line 5 changed: $f"
done
diff <(git show HEAD:.claude/rules/platform-commons.md | sed -n '/<!-- #arch-embed:/,/<!-- \/#arch-embed -->/p') \
     <(sed -n '/<!-- #arch-embed:/,/<!-- \/#arch-embed -->/p' .claude/rules/platform-commons.md) && echo "embeds unchanged"
grep -nE 'improvement-[0-9]+|feature-[0-9]+|goal-[0-9]+|ADR-[0-9]+|20[0-9]{2}-[0-9]{2}-[0-9]{2}' .claude/rules/*.md
awk 'FNR==1 && NR>1 {if (n>200) print prev ": " n " lines"} {n=FNR; prev=FILENAME} END {if (n>200) print prev ": " n " lines"}' .claude/rules/*.md
git worktree list
git status --short | grep -v '^M  .claude/rules/' | grep -v 'backlog/'
```

Expected: no "changed" line except "frontmatter changed" for `README.md` when it was rewritten (it has no `paths:` block, so its first three lines are ordinary text); the comparison is against `HEAD`, so it holds as long as nothing was committed during the run. "embeds unchanged" is printed. The number and date pattern finds nothing. The files over 200 lines match the summary. `git worktree list` shows only `/app`. The last command prints nothing except the files decision 3 (a) regenerated. Then `/check-architecture-map` reports the map up to date: line 5 of `scripts.md` and `playwright.md` and the five blocks are the only parts of these files the map shows. With decision 8 yes, the user runs `/doctor prompt-audit .claude/rules` and its findings are added to the summary.

## Tools found for instruction quality

Found by a web search, none of them installed or run unless a line says so. Third-party items are code or prompts that would read the whole repository: read the source, the licence and the last commit first; installing a plugin or a hook changes the user's Claude Code configuration and is a separate decision.

**From Anthropic**
- Plugin `claude-md-management` ([listing](https://claude.com/plugins/claude-md-management)): an audit of `CLAUDE.md` quality and `/revise-claude-md`, which proposes updates from a session's learnings. A [feature request](https://claudeissues.com/issue/68440-feature-claude-md-management-plugin-should-support-ongoing-knowledge-ecosystem-m) says it knows nothing of skill directories and files everything in `CLAUDE.md`; an `--auto` flag without confirmation is mentioned only by third parties.
- Skill `claude-md-improver` (repository `anthropics/claude-plugins-official`): scans all `CLAUDE.md` files, scores them against templates, writes a quality report, then makes targeted updates. Described for `CLAUDE.md`, not for `.claude/rules/`.
  Its `SKILL.md` was read (not installed): tools Read, Glob, Grep, Bash and Edit; one `find` for discovery (`CLAUDE.md`, `.claude.md`, `.claude.local.md`, 50 results at most); no network; a report with six weighted criteria (commands, architecture clarity, non-obvious patterns, conciseness, currency, actionability) before any edit; a diff and a confirmation before editing. Its proposals are additions (commands, gotchas, architecture), not trimming, which pulls against this project's small `CLAUDE.md`.
- Skill `skill-creator` (repositories `anthropics/skills` and `anthropics/claude-plugins-official`): creates and improves skills and measures them; for skills, not for rules.
- Hook event `InstructionsLoaded` (Claude Code v2.1.64 or later): logs which `CLAUDE.md` and `.claude/rules/*.md` files load, when and why (`session_start`, `path_glob_match`, `include`, `compact`); it cannot block or change loading; reported bugs: it does not fire after `/clear` or after compaction ([article](https://dev.to/gentic_news/stop-rewriting-claudemd-use-context-and-instructionsloaded-to-debug-why-5a71)).
- Built-in `/context`, `/doctor`, `/doctor prompt-audit`, `/skill-doctor`, `/skills`, `/memory`: catalogued in [`.claude/commands/README.md`](../../../.claude/commands/README.md).

**Community (status not verified)**
- Linters and auditors: `ctxlint` (token budget, signal-to-noise, stale file and script references, `slim` mode), `ctxweight` (always-on token cost, redundancy, contradictions, linter overlap), `cclint`, `claudemd-audit` (bloat, self-evident rules, files over 500 lines), `agentlinter` (30 rules), `optimize-claude-md`, `grading-claude-agents-md` (100-point rubric).
- Authoring: `authoring-claude-md` ([skills.sh](https://www.skills.sh/sammcj/agentic-coding/authoring-claude-md), the only one written for `.claude/rules/` files), `legacy-to-ai-ready` (generates `CLAUDE.md` and rules from a codebase; not for existing files).
- Collections and articles: [awesome-claude-code](https://github.com/hesreallyhim/awesome-claude-code), [awesome-claude-md](https://github.com/TakatoPhy/awesome-claude-md), [Audit your Agent files](https://addyosmani.com/blog/audit-your-agent-files/) (most audited files exceed the 200-line guidance; in a study of 100 repositories 62% had linter-leak smells, 42% bloat, 35% skill leak).

**What fits this project:** mechanical checks that `/doctor` lacks (references that still exist, token cost of what loads) come from `ctxlint` or `ctxweight`; `InstructionsLoaded` would show that the `paths:` of the 22 rules files fire as assumed; `claude-md-improver` applies to the root `CLAUDE.md`; most of the rest repeats `/doctor prompt-audit`, `/tighten-ai` and `/sync-docs`, and `/revise-claude-md` conflicts with the rule of one fact in one home.

## Tighten research result (root CLAUDE.md)

Research of the kind "root project-instructions file" for `/tighten-ai`, filed from the topic folder `tighten-ai-project-instructions`. Sources are Anthropic documentation and posts (memory, best practices, context window, features overview, large codebases, skills, subagents, prompting best practices, steering Claude Code, CLAUDE.md files) and one independent study of AGENTS.md files. The decisions of the research were taken in the run (see the summary above).

#### What the evidence says about a root file

Sources: [memory](https://code.claude.com/docs/en/memory), [best practices](https://code.claude.com/docs/en/best-practices), [context window](https://code.claude.com/docs/en/context-window), [features overview](https://code.claude.com/docs/en/features-overview), [large codebases](https://code.claude.com/docs/en/large-codebases), [skills](https://code.claude.com/docs/en/skills), [subagents](https://code.claude.com/docs/en/sub-agents), [prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices), the Anthropic posts [steering Claude Code](https://claude.com/blog/steering-claude-code-skills-hooks-rules-subagents-and-more), [large codebases](https://claude.com/blog/how-claude-code-works-in-large-codebases-best-practices-and-where-to-start) and [using CLAUDE.md files](https://claude.com/blog/using-claude-md-files), and one independent study, [Evaluating AGENTS.md](https://arxiv.org/abs/2602.11988).

**Loading and compaction**

- The root file loads whole at the start of every session, as a message after the system prompt. It is context, not enforcement.
- After compaction the root file and rules without a `paths:` block are read again from disk. Nothing of the root file is lost at compaction, and the position of a line in the file does not decide whether it survives.
- Every dispatched agent starts with the same files, except the built-in Explore and Plan agents and an agent defined with `omitClaudeMd`. The size of the root file is paid again for each agent.
- No 40,000-character cut applies to the root file; that cut is for files that load on demand.

**Imports (`@`)**

- An imported file loads together with the file that imports it. Imports organise, they do not save context. Each import counts as its own file for the length warning, and a combined limit at session start exists.
- The import line must stand outside a code span. Imports nest to four levels.

**Length**

- "Target under 200 lines per CLAUDE.md file. Longer files consume more context and reduce adherence." Every line "takes a little of Claude's attention on every task, whether it's relevant or not".
- A line that Claude keeps missing is not fixed by emphasis: the file is too long, or the line belongs in a hook.

**What belongs in the root file**

- Only what Claude could not work out from the code: the commands that confirm work, places where the code misleads, decisions that are not in the repository, things corrected twice.
- Rules that hold everywhere and in every session. A rule for one part of the code belongs in a path-scoped rule; a procedure belongs in a skill; a rule with no exception belongs in a hook.
- Pointers: "The root file should be pointers and critical gotchas only." A list of the top-level folders with one line each is "a table of contents".
- Not in it: descriptions of what the code already shows. `/doctor` "cuts content Claude can derive from the codebase, such as directory layouts, dependency lists, and architecture overviews". The independent study found the same: overviews in such files did not help agents find files, while instructions in them were followed, and the files raised cost by about 20%.
- Names and descriptions of every command and agent that Claude may start itself are already put into every session by Claude Code. A catalogue of them in the root file is a second copy.

**Structure and wording** (same as for rules files): headers and short bullets, no dense paragraphs; concrete and checkable; no two instructions that contradict, across the root file, the import and the rules; plain wording without capitals for strength; a short reason; exact examples.

**One conflict between the sources.** Older pages say a layout belongs in the file ("project layout", "directory layout, monorepo structure", "a high-level directory structure"); the newer pages list "Describe your architecture and file layout in CLAUDE.md" as advice to stop following. Followed one level down, they agree on this reading: a one-line-per-module map and pointers stay, a description of what is inside a module goes.

#### The checklist of `tighten-ai.md` for a root file

| Item | Verdict for a root file |
|---|---|
| 1 goal first | kept; no source asks for it; one sentence replaces the present line 3 |
| 2, 3, 4, 6, 8, 10, 11, 13, 14 | confirmed |
| 3, added reading | "no conflict" covers the import and the rules files too, since all load together |
| 5 reference, not restate | confirmed, with a limit (P9 below) |
| 7 | first half confirmed; **the 500 lines are contradicted**: 200 lines per file is the documented target for a `CLAUDE.md` |
| 9 what survives compaction comes first | **does not apply**: the root file is read again from disk whole |
| 12 hard limits in one place | confirmed; here it means no limit is stated both in the root file and in `.claude/rules.md` |

Added for a root file (P1 to P10):

- **P1** Only what Claude cannot work out from the code. A class list, a package catalogue or a dependency list becomes a pointer or goes. The one-line-per-module map stays.
- **P2** Only what holds for the whole project. What matters for one module is referenced in its rules file.
- **P3** A procedure, a flag list or a command catalogue is referenced at its home.
- **P4** No capitals for strength; the word itself ("must", "never") stays.
- **P5** At most one example per rule.
- **P6** Every path, command or section the file names exists.
- **P7** Target 200 lines; lines and characters are both reported, because the lines of this file are long.
- **P8** Unchanged by the rewrite: the import line; the section titles, guideline numbers and labels that other files quote; the lines of the module tree that the map generator reads.
- **P9** A pointer may replace a fact only, and only after the fact is found at its home. A requirement stays in the root file. (New: a fact behind a pointer is seen only when Claude opens the home; the documentation says so in its own words for a file that is "told in words" to be read.)
- **P10** Nothing is said both in the root file and in the import, since both are always loaded.

#### The earlier rules-file research: what is reused and what is not

| Earlier conclusion | For the root file |
|---|---|
| The checklist verdicts for items 2 to 8 and 10 to 14 | reused |
| A1, A3, A4, A5, A6 | reused as P1, P3, P4, P5, P6 |
| A2 "only what is specific to this module" | turned round: only what holds for every module (P2) |
| A7 length 200 lines, cut at 40,000 characters | 200 lines reused; the cut does not apply |
| A8 `paths:` block, line 5, marked blocks | replaced by P8 |
| Loads when a matching file is read; dropped at compaction | does not apply |
| Replacement for item 9 (flag a rule that must hold when no module file is open) | not needed: the root file is always present |
| Hard limits live under `Key constraints` | does not apply; see item 12 |
| Repository rules R1 to R7 for new text | reused; R8 is replaced by P8 |
| Three layers of checking (words, repository rules, behaviour) | reused; the probes change (below) |
| "No reliable method proves unchanged behaviour" | reused unchanged |

#### Where the root file differs from the standard today

| What | Where (lines of the present file) | Size | Typical |
|---|---|---|---|
| Class catalogue of `platform-commons`, out of step with the code | 48 to 59 | 2,640 characters | lists 3 of 9 `feedback.dto` classes |
| Catalogue of 17 commands and 3 agents; the same facts are in `.claude/commands/README.md`, `.claude/nav/flows.md` and in the list Claude Code loads itself | 177 to 197 | 4,390 | one line has 493 characters |
| 17 absolute paths of decision files, findable by name and through the generated index | 204 to 220 | about 760 | `/app/query-lib/DECISIONS.md` |
| 19 rules files pointed to in 24 places; the list with its patterns is in `.claude/rules/README.md`, which loads in every session because it has no `paths:` block | 61 to 87, 151, 153, 157, 174, 175 | about 1,900 | four files are pointed to twice |
| Paragraphs about one module | 42 to 46 | 1,188 | line 46 is one paragraph of 666 characters |
| The same thing twice | 12 to 13 and guideline 6 (Liquibase); 14 and the `DTO Field Name Constants` section of `.claude/rules.md`; guidelines 2 and 7 (Vaadin only in `marketplace-app`); guidelines 6 and 7 (each starter owns its changelog); 123 and 127 (`@NonNull`); 236 to 238 and the Definition of Done of `.claude/rules.md` (`/record-decision`) | | |
| Dense paragraphs | guideline 2 (11 lines), lines 123, 129 (450 and 520 characters) | | |
| Capitals for strength | 12, 14, 105, 106, 108, 109 | | "NO JPA, NO HIBERNATE" |
| Explanation of what Claude knows | 3, 127 | | "Lombok generates a null-check at the top of the method body" |
| A count that changes | 228, 230 | | "these eight modules" |
| Real classes as an example | 46, 149 | | "Reference implementations: `UserRepository` ..." |
| Facts with a home in `INFRASTRUCTURE.md` | 162 to 168 | 538 | the list of `prod` variables |

Inside the class catalogue two notes are prohibitions, not list entries ("`AuditActivityFieldsHook` does not exist", "`AttachmentMediaChangeHook` does not exist"). They stay as rules whatever happens to the list.

#### How it is checked that behaviour does not get worse

The three layers of the earlier plan, with the root file under test.

1. The words survive: the coverage table and the read-only reviewer.
2. The repository rules R1 to R7 and the invariants P8 hold on the new text: shell checks (tried on the present file, they pass) and the same reviewer.
3. Claude behaves the same: probes through `claude -p`, twice with the old file and twice with the new one. Two kinds, because a root file is always loaded:
   - closed-book ("without reading any file, describe how you would ...") for requirements whose wording changed; the answer can only come from what is always loaded;
   - open-book for a fact that became a pointer ("which classes does package X hold"); the new file passes when Claude finds the right answer at the home.

It is reported as "no loss found on the probed rules", never as proof.

### Risks

- A sentence inside a catalogue is a requirement and leaves with the catalogue. Guard: every unit is classed, the two "does not exist" notes are named in advance, the reviewer reads the old text itself.
- A fact moves from "always in context" to "found only if Claude looks". Guard: P9, the home check, the open-book probes. This risk is larger than in a rules file.
- A home is itself incomplete: `platform-commons/README.md` repeats the class catalogue and stops before `contact`, `feedback` and `promotion`. The only complete home is the source tree.
- A quoted title, number or label changes and a test message or a document points at nothing. Guard: P8 with its shell check; more names are quoted than the brief lists (see Decisions, 2).
- The map changes: the generator takes each module's description from the tree. Guard: the tree stays byte for byte unless you decide otherwise.
- The pointer list is shortened on the ground that `.claude/rules/README.md` loads in every session; the open group 5 of the rules run may change that file.
- The probes cover a few rules of about a hundred; they catch a clear loss only.
- The saving of this run is limited: the import is twice the size of the root file and is outside the scope.

## Related

- [improvement-240](../completed/tasks/improvement-240-sync-docs-refactor.md): the task this was split from.
- [improvement-246](../completed/tasks/improvement-246-technical-debt-investigation.md): its hypothesis about the size of `.claude/rules.md` and the duplication in the skills.
- [improvement-244](../completed/tasks/improvement-244-research-wiki-or-keep-architecture-control-plane.md).

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
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none

### Commands used
- /tighten-ai | uses=2 | failed=0 | note=rules pilot shortened 11% at a cost out of proportion for a file near the standard; the root CLAUDE.md run shortened it by 39% of characters with no loss on three probes
- /doctor prompt-audit | uses=4 | failed=0 | note=about $0.7 to $1.2 per run; found real stale references; runs differ; its findings are checked against the code before use
- /deep-research | uses=2 | failed=0 | note=two PLAN READY on the Fable model in 12 to 16 min, about 245k to 252k subagent tokens each; the second reused the first
- claude-md-improver (not installed, procedure followed) | uses=1 | failed=0 | note=scored the root CLAUDE.md 74 of 100, found three real stale references; its additions-first rubric pulls against the one-home rule
- /new-task | uses=1 | failed=0 | note=filed the review-findings task at the top of the backlog
- /simplify | uses=1 | failed=1 | note=run on a tightened rules file: four agents, about 255k subagent tokens, about 50 tokens per load of possible saving and findings that weaken constraints or contradict each other; nothing applied, the angles are written for code

### Agent calls
- research of the root-file kind (deep-researcher) | subagent_type=deep-researcher (fable) | tokens=244269 | tool_uses=53 | duration_s=716 | mode=background | batch=solo
- inventory and proposal for the root CLAUDE.md | subagent_type=general-purpose (opus) | tokens=192828 | tool_uses=36 | duration_s=482 | mode=background | batch=solo
- independent review of the CLAUDE.md proposal | subagent_type=general-purpose (sonnet) | tokens=87817 | tool_uses=9 | duration_s=91 | mode=background | batch=solo
- three behaviour probes, 12 runs | subagent_type=general-purpose (haiku) | tokens=69545 | tool_uses=6 | duration_s=287 | mode=background | batch=solo
- writing the approved CLAUDE.md | subagent_type=general-purpose (haiku) | tokens=59762 | tool_uses=3 | duration_s=18 | mode=background | batch=solo
(the agent calls of the rules-file run are not itemised here)
