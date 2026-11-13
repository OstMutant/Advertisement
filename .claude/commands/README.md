# `.claude/commands/`

Slash-command definitions, each invoked directly by name (`/new-task`, `/ci`, ...). Every command
here is independent — no file-to-file call chain between them, so no Flow diagram; each is its own
self-contained entry point, described below and linked to its real file.

Each table has a **Good for** and a **Not so good for** column (comments on where the command helps and where it does not) and a **Uses** column (how many times the command has been run on this project's work; the **Failed** column counts the runs among them that errored, were blocked or gave nothing usable; the counts come from the `### Commands used` of closed tasks, see [`.claude/rules/backlog-tasks.md`](../rules/backlog-tasks.md); keep each table sorted by Uses, highest first, then alphabetically). Counting starts at 0.

## Project commands

| Command | What it does | Good for | Not so good for | Uses | Failed |
|---|---|---|---|---|---|
| [`/activity-monitor`](activity-monitor.md) | Wraps a backgrounded script and shows its live step checklist instead of raw stdout. | Watching long runs at token cost proportional to real signal. | Pass/fail itself: that is always the wrapped command's exit code, not narration. | 0 | 0 |
| [`/autopilot`](autopilot.md) | Plans once, gets one approval, then runs the whole task end-to-end and reports when done or blocked. | An already-scoped, concrete task that only needs building. | Unscoped work; the standing per-step approval returns for the next task. | 0 | 0 |
| [`/build-and-test`](build-and-test.md) | Builds the whole reactor in the shared build-and-test container; optionally runs unit and integration tests. | Building without a local Java install; refreshing the shared jar. | Deploying or restarting anything that is running. | 0 | 0 |
| [`/check-architecture-map`](check-architecture-map.md) | Checks that the architecture map files on disk still match what the generator produces. | Before saving changes that can touch the map; manual runs only. | Regular generation runs; machine-specific test and run-log sections are ignored. | 0 | 0 |
| [`/ci`](ci.md) | Runs the local, isolated, parameterized CI runner. | One-shot unit, integration, e2e and Sonar pass, backgrounded by default. | Quick single-stage checks; `--foreground` blocks until the stage finishes. | 1 | 0 |
| [`/deep-research`](deep-research.md) | Researches a task in depth with the `deep-researcher` agent (Opus) and writes a human plan and a machine plan. | Choosing between variants before approving a larger change. | Small or well-understood changes; a plain plan in the main thread is enough. | 0 | 0 |
| [`/deep-review`](deep-review.md) | Runs a full code review through the `deep-review-orchestrator` agent; every finding is re-verified. | Evidence-verified SOLID/DRY/KISS/YAGNI findings on a chosen scope. | Writing code; it never edits and never verifies findings itself. | 0 | 0 |
| [`/deploy-and-run`](deploy-and-run.md) | Rebuilds the `marketplace-app` Docker image and starts a fresh container with the project deploy script. | Seeing a change live in a production-representative build. | Quick iteration: a full rebuild takes about 7-10 minutes. | 0 | 0 |
| [`/docker-clean`](docker-clean.md) | Removes the project's containers, and only the volumes and caches the user names. | Starting a full run on fresh containers; manual runs only. | Being called by another script or command; it never is. | 0 | 0 |
| [`/new-domain`](new-domain.md) | Creates a complete new UI domain in `marketplace-app` following the established patterns. | Scaffolding the whole View, Overlay, handlers and query set in one pass. | not stated | 0 | 0 |
| [`/new-task`](new-task.md) | Scaffolds a new tracked task from the standard template and ranks it in the backlog priority table. | Filing a new request without leaving it unranked. | Re-opening a settled decision; the ADR index is checked first. | 0 | 0 |
| [`/playwright`](playwright.md) | Runs Playwright UI tests for the marketplace app. | Visual and behavioral checks of UI-visible changes, with `--ux` always added. | Running without the app; it deploys first when the app is down. | 0 | 0 |
| [`/record-decision`](record-decision.md) | Records a new architectural decision in the appropriate `DECISIONS.md` file. | Decisions that are expensive to reverse or constrain later work. | Bug fixes, layout tweaks and self-evident changes; a commit message suffices. | 0 | 0 |
| [`/reduce-code`](reduce-code.md) (in development) | Finds code this repository no longer needs to maintain, measures the saving and removes it in approved batches; helper script in [`reduce-code/`](reduce-code/check-batch.sh). | Measured removal of maintained code, one approved batch at a time. | The latest diff only, or unmeasured design findings; use `simplify` or `/deep-review`. | 0 | 0 |
| [`/run-all-tests`](run-all-tests.md) | Runs unit, integration and Playwright suites for daily iteration. | The standard local loop against freshly deployed, clean data. | Progress detail: the tree shows one step, not sub-suite progress. | 0 | 0 |
| [`/screenshots`](screenshots.md) | Finds and reads named screenshots from the last Playwright run. | Reading screenshots out of the HTML report's embedded archive. | not stated | 0 | 0 |
| [`/sonar`](sonar.md) | Runs SonarQube static analysis for the marketplace app. | Quality gate plus a real check for new BUG and CRITICAL issues. | Informational-only runs unless `--no-gate` is passed. | 0 | 0 |
| [`/sync-docs`](sync-docs.md) | Finds every document related to a change and corrects it against the code; `--full-audit` also runs a prompt audit. | Keeping documentation in step with a diff; `--full-audit` for periodic drift. | Changing code logic; diff mode misses docs that no change touched. | 0 | 0 |
| [`/tighten-ai`](tighten-ai.md) | Rewrites AI instructions to be shorter, more exact and stricter without losing a requirement, and reports before/after measures (checklist items satisfied, missing names, lines and characters). | Shortening commands, rules, skills and agents with a coverage table. | Code and documentation; use `/reduce-code`. | 0 | 0 |

## Native commands (built-in Claude Code)

These are Claude Code's own commands and bundled skills, not files of this repo (source: <https://code.claude.com/docs/en/commands>).

| Command | What it does | Good for | Not so good for | Uses | Failed |
|---|---|---|---|---|---|
| `/code-review [--fix] [--comment]` | Reviews the current diff for correctness bugs and quality; `--fix` applies findings, `--comment` posts them on a PR. | Fixing the current diff now when bugs and quality are both in scope. | Someone else's PR (use `review`) or security-only review (use `security-review`). | 0 | 0 |
| `/compact [instructions]` | Summarizes the conversation to free context. | Long sessions. | Not tried here. | 0 | 0 |
| `/context [all]` | Shows current context usage with suggestions for context-heavy tools and memory bloat. | What `CLAUDE.md` and the rules cost in every session. | Not tried here. | 0 | 0 |
| `/doctor` | Setup checkup: installation, settings, extensions, `CLAUDE.md` content derivable from code, duplicates, always-loaded guidance that could load on demand. Asks before changing anything. | The size of the root `CLAUDE.md`. | Not tried here. | 0 | 0 |
| `/doctor prompt-audit [path]` | Audits the instructions under the path for outdated, dead and conflicting content; reports and proposes a diff, applies nothing. Used by `/tighten-ai` and `/sync-docs --full-audit`. Needs Claude Code v2.1.283 or later. | Rules and `CLAUDE.md` files before and after a rewrite; runs without a dialog: `claude -p "/doctor prompt-audit <path>" --model sonnet --permission-mode dontAsk --output-format json`. One run over the rules folder cost about $1.1 and found dead pointers, stale counts, contradictions and history in current-state text; 11 verified findings were applied and none rejected; two runs found different sets, so its claims are checked against the code. Two runs before counting began. | Does not shorten text and does not prove behaviour is unchanged; it was run without git blame, so it cannot tell which of two conflicting texts is newer. | 0 | 0 |
| `/import [codex, gemini or cursor]` | Imports configuration from another tool. | Not applicable. | Not applicable. | 0 | 0 |
| `/init` | Creates a `CLAUDE.md` for a project. | Not applicable: the file exists. | Not applicable. | 0 | 0 |
| `/insights` | Generates an HTML report about recent sessions on this machine. | Seeing where tokens and effort go. | Not tried here. | 0 | 0 |
| `/memory` | Edits `CLAUDE.md` files and shows or toggles auto memory entries. | Editing `CLAUDE.md` and memory entries. | Not tried here. | 0 | 0 |
| `/simplify` | Reviews changed code for reuse, simplification, efficiency and altitude cleanups, then applies the fixes. | Quality-only cleanup of already-changed code. | Bug hunting; use `/code-review` when bugs are in scope. | 0 | 0 |
| `/skill-doctor` | Shows what each skill costs in context and how often it is used. Needs Claude Code v2.1.252 or later and a feature flag. | Finding unused skills. | Not tried here. | 0 | 0 |
| `/skills` | Lists the available skills; sorting by token count is available. | The context cost of each skill. | Not tried here. | 0 | 0 |

When a task is closed, add its command counts to the Uses and Failed cells and extend the two comment cells of a command when the task taught something new about it.
