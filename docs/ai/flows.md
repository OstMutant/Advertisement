# Operational flows — which mechanism handles which situation

A scenario → mechanism map over the operational surface that already exists. Split into two
groups with different freshness guarantees — see "Staying correct" at the bottom.

## Project commands & skills (files in this repo — `.claude/commands/*.md`, `.claude/skills/*/SKILL.md`)

| Situation | Mechanism | Why this one, not another |
|---|---|---|
| New bug/improvement/feature request, not yet tracked | `/feature <title>` | Scaffolds `backlog/issues/<prefix>-NNN-<slug>.md` from the standard template and ranks it in `BACKLOG.md` in the same operation — the only way a new issue is filed without leaving it unranked. |
| A real architectural decision was just made (new pattern, deliberate exception, rejected alternative) | `/record-decision <module> — <title>` | Writes directly into that module's `DECISIONS.md` in the established `## ADR-NNN: Title` / `**Status:**` / Context-Decision-Consequences shape — do not hand-write ADR entries ad hoc. Regenerate [adr-index.md](adr-index.md) (`bash docs/ai/scripts/generate-adr-index.sh`) in the same change — this is also a standing `.claude/rules.md` rule, not just this command's own step. |
| Code changed and the diff might have made a doc stale | `/sync-docs` (default: diff against `origin/main`) | Has its own changed-file→doc-target mapping table; only touches docs affected by the actual diff — cheap, meant for frequent use. |
| Periodic whole-repo doc sanity check, independent of any one diff | `/sync-docs --full-audit` | Diff mode can never catch drift where the *doc* mentioning a renamed/removed thing was never itself part of the commit that changed it — full-audit re-verifies every claim in every `README.md`/`DECISIONS.md`/`CLAUDE.md` against current code, not just recently-touched ones. Also the only place the "Built-in Claude Code skills" table below gets checked — see "Staying correct". |
| A scoped, already-agreed multi-step task that just needs building | `/autopilot <task>` | One plan, one approval, then implementation/tests/docs/issue-lifecycle chained through without further check-ins — the standing per-step Approval Rule still applies to any other task in the same session. |
| UI-visible change needs visual/behavioral verification | `/playwright [scenario] [--ux]` | Always append `--ux` for screenshot-backed verification; see `playwright/CLAUDE.md` for the Monitor+tee run pattern and why specs are order-dependent. |
| Full daily test loop: unit → integration → Playwright | `/run-all-tests` | Runs unit-tests → integration-tests sequentially, Playwright in parallel — the standard local iteration loop, see `docs/ai/adr-index.md`. |
| Full CI-equivalent pass in one shot (unit+integration+e2e+sonar) | `/ci` | Isolated, parameterized, backgrounded by default with a `progress.txt` — the closest local equivalent to a real CI pipeline; see `scripts/ci/DECISIONS.md`. |
| Build the reactor (+ optional unit/integration tests), no local Java needed | `/build-and-test` | Builds via `scripts/build-and-test.sh` into the shared `maven-cache` volume — does not deploy or restart anything running. |
| See a change live | `/deploy-and-run` | Full Docker image rebuild via `scripts/deploy-and-run.sh` — slower (~7-10 min) but production-representative (Vaadin prod bundle, `productionMode=true`); the only path that actually redeploys a running app; reuses `/build-and-test`'s shared jar internally instead of compiling twice. |
| Static analysis / quality gate | `/sonar` | Wraps `scripts/sonar.sh`; blocking by default (`--no-gate` for informational-only). |
| New complete UI domain (View/Overlay/ModeHandlers/QueryBlock/FilterMeta/SortMeta) | `new-domain` skill | Scaffolds the full established pattern set in one pass — see `marketplace-app/CLAUDE.md` "Reference Implementations" for what it mirrors. |
| Extract/read screenshots from the last Playwright `--ux` run | `screenshots` skill | Reads them out of the HTML report's embedded base64 zip — there is no standalone `screenshots/` directory. |
| Need an independently evidence-verified findings list, no code changes at all | `.claude/skills/deep-review` — diff mode (default, last commit or a ref) or `full [module]` (periodic whole-repo SOLID/DRY/KISS sweep) | Never writes code, every finding independently re-verified against the real file before being reported — use when the goal is a trustworthy findings list to hand off or decide on, not an immediate fix. Full mode already spawns one subagent per module with that module's own `CLAUDE.md`/`DECISIONS.md` as context — do not pre-load that yourself. |
| About to write or edit any documentation file (`CLAUDE.md`, `README.md`, `docs/architecture/*`, `docs/ai/*`, a command, a skill, `.claude/rules.md`) | `doc-standards` skill | Pre-write checklist + canonical-ownership table — checks whether the fact being added already has a canonical home before restating it, the mechanism that stops the same fact drifting across up to four files. |
| About to write or edit a script/tooling file's own header (bash/batch, `docker-compose*.yml`, `.properties`) | `infra-doc-standards` skill | Distinct from `doc-standards` — covers infrastructure/tooling files, not documentation about the Java/Vaadin application itself. |
| About to write or regenerate a script-group directory's own `README.md`/Flow diagram | `infra-readme-standards` skill | Sibling to `infra-doc-standards` (split 2026-08-21 once the combined file hit 714 lines/6-8x its sibling skills) — covers README/Mermaid/ISO-5807 conventions only, once every file in the directory already has a complete header. |

## Built-in Claude Code skills (not files in this repo — global/plugin, can drift silently, see "Staying correct")

| Situation | Mechanism | Why this one, not another |
|---|---|---|
| Need the current diff reviewed for correctness/reuse/efficiency, findings applied directly | `/code-review [--fix] [--comment]` | Can write code (`--fix` applies its own findings; `--comment` posts inline on a PR) — the right tool when the goal is "fix it now," not just "tell me what's wrong." Hunts for bugs *and* quality; compare `simplify` below. |
| Quality-only cleanup of already-changed code (reuse, simplification, efficiency) — no bug hunting | `simplify` skill | Narrower than `/code-review`: does not look for correctness bugs, only applies quality fixes. Use `/code-review` when bugs are in scope too. |
| Security review of pending changes on the current branch | `security-review` skill | Distinct focus from `/code-review`/`deep-review` — security-specific, not general correctness/quality. |
| Review an existing GitHub PR (not your own local working diff) | `review` skill | For someone else's PR, or your own already-pushed one — `/code-review` is for the local uncommitted/recent diff. |
| Verify a change actually works in the running app | `verify` skill (confirm a fix/PR works) or `run` skill (launch and drive the app) | Type checks and test suites verify code correctness, not feature correctness — use these when the goal is seeing the real behavior, not just green tests. |
| Change Claude Code's own settings (permissions, env vars, hooks) for this project | `update-config` skill | Edits `settings.json`/`settings.local.json` directly — do not hand-edit these files outside the skill. |
| Repeated permission prompts for the same read-only commands | `fewer-permission-prompts` skill | Scans transcripts for common safe calls, proposes a project `settings.json` allowlist. |
| Run a prompt/command repeatedly on an interval | `loop` skill | In-session recurring execution (e.g. poll a deploy every 5 min) — for a persistent cron-style schedule instead, see `schedule` below. |
| Recurring cloud agent on an actual cron schedule | `schedule` skill | Persists across sessions, unlike `loop`. |

See [context-loading.md](context-loading.md) for *what to read* once you know *what to run* —
the two are complementary, not overlapping.

## Staying correct

**Project commands & skills table** — mechanically checkable: every `.claude/commands/*.md` and
`.claude/skills/*/SKILL.md` file in this repo should have a corresponding row here. When adding a
new command/skill file, add its row in the same change (per `.claude/rules.md`'s "surface adjacent
quality issues unprompted" rule — an undocumented flow is exactly that class of issue).

**Built-in Claude Code skills table** — cannot be mechanically checked from inside this repo: these
skills aren't files here, they're part of the Claude Code installation itself, and the only way to
even see the current list is the "Available skills" system-reminder injected into a live session.
A newly-added built-in skill can go silently uncovered here, caught only by a manual audit, not
any automated check. Re-check this table's completeness during
`/sync-docs --full-audit` by comparing it against whatever skill list is actually available at
that time — this is a periodic, judgment-based check, not a continuous guarantee. Deliberately
excluded here as out of scope for this project's day-to-day work: `keybindings-help`
(environment-level, not repo work), `init` (this repo already has `CLAUDE.md` everywhere), `claude-api`
(a reference lookup, not an action taken on this repo).
