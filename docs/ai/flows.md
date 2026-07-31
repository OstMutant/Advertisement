# Operational flows — which mechanism handles which situation

A scenario → mechanism map over the operational surface that already exists (root `CLAUDE.md`'s
"Slash commands available" list, `.claude/commands/*.md`, `.claude/skills/`). Every entry below
points at something that already exists — this file adds no new process, only navigation over it.

| Situation | Mechanism | Why this one, not another |
|---|---|---|
| New bug/improvement/feature request, not yet tracked | `/feature <title>` | Scaffolds `backlog/issues/<prefix>-NNN-<slug>.md` from the standard template and ranks it in `BACKLOG.md` in the same operation — the only way a new issue is filed without leaving it unranked. |
| A real architectural decision was just made (new pattern, deliberate exception, rejected alternative) | `/decision <module> — <title>` | Writes directly into that module's `DECISIONS.md` in the established `## ADR-NNN: Title` / `**Status:**` / Context-Decision-Consequences shape — do not hand-write ADR entries ad hoc. Regenerate [adr-index.md](adr-index.md) (`bash scripts/ai/generate-adr-index.sh`) in the same change per the mandatory hook in `.claude/commands/decision.md`. |
| Code changed and the diff might have made a doc stale | `/sync-docs` (default: diff against `origin/main`) | Has its own changed-file→doc-target mapping table; only touches docs affected by the actual diff — cheap, meant for frequent use. |
| Periodic whole-repo doc sanity check, independent of any one diff | `/sync-docs --full-audit` | Diff mode can never catch drift where the *doc* mentioning a renamed/removed thing was never itself part of the commit that changed it (confirmed live during improvement-134's own audit — see "Related" in that issue) — full-audit re-verifies every claim in every `README.md`/`DECISIONS.md`/`CLAUDE.md` against current code, not just recently-touched ones. |
| Need the current diff reviewed for correctness/reuse/efficiency, findings applied directly | `/code-review [--fix] [--comment]` | Can write code (`--fix` applies its own findings; `--comment` posts inline on a PR) — the right tool when the goal is "fix it now," not just "tell me what's wrong." |
| Need an independently evidence-verified findings list, no code changes at all | `.claude/skills/deep-review` — diff mode (default, last commit or a ref) or `full [module]` (periodic whole-repo SOLID/DRY/KISS sweep) | Never writes code, every finding independently re-verified against the real file before being reported — use when the goal is a trustworthy findings list to hand off or decide on, not an immediate fix. Full mode already spawns one subagent per module with that module's own `CLAUDE.md`/`DECISIONS.md` as context — do not pre-load that yourself. |
| A scoped, already-agreed multi-step task that just needs building | `/autopilot <task>` | One plan, one approval, then implementation/tests/docs/issue-lifecycle chained through without further check-ins — the standing per-step Approval Rule still applies to any other task in the same session. |
| Verify a change actually works in the running app | `verify` skill (confirm a fix/PR works) or `run` skill (launch and drive the app) | Type checks and test suites verify code correctness, not feature correctness — use these when the goal is seeing the real behavior, not just green tests. |
| UI-visible change needs visual/behavioral verification | `/playwright [scenario] [--ux]` | Always append `--ux` for screenshot-backed verification; see `playwright/CLAUDE.md` for the Monitor+tee run pattern and why specs are order-dependent. |
| Full daily test loop: unit → integration → Playwright | `/run-all-tests` | Runs unit-tests → integration-tests sequentially, Playwright in parallel — the standard local iteration loop, see `scripts/DECISIONS.md` ADR-004. |
| Full CI-equivalent pass in one shot (unit+integration+e2e+sonar) | `/ci` | Isolated, parameterized, backgrounded by default with a `progress.txt` — the closest local equivalent to a real CI pipeline; see `scripts/ci/DECISIONS.md`. |
| See a change live, full rebuild | `/build` | Full Docker image rebuild via `scripts/deploy.sh` — slower (~7-10 min) but production-representative (Vaadin prod bundle, `productionMode=true`). |
| See a change live, fast iteration | `deploy-dev` skill | JAR hot-swap via `scripts/deploy-dev.sh`, no image rebuild (~3-4 min) — requires infra + the app container already running once via `/build`. |
| Static analysis / quality gate | `/sonar` | Wraps `scripts/sonar.sh`; blocking by default (`--no-gate` for informational-only). |
| New complete UI domain (View/Overlay/ModeHandlers/QueryBlock/FilterMeta/SortMeta) | `new-domain` skill | Scaffolds the full established pattern set in one pass — see `marketplace-app/CLAUDE.md` "Reference Implementations" for what it mirrors. |
| Extract/read screenshots from the last Playwright `--ux` run | `screenshots` skill | Reads them out of the HTML report's embedded base64 zip — there is no standalone `screenshots/` directory. |

See [context-loading.md](context-loading.md) for *what to read* once you know *what to run* —
the two are complementary, not overlapping.
