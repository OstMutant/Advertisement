---
paths: ["backlog/tasks/**", "backlog/completed/tasks/**"]
---

## Task file operational notes

Every task file carries an `## Operational notes` block from the moment it is created (`/new-task`
writes it with `n/a`/`none` values). Update its lines during the work as each fact becomes known
— an ADR that shaped a decision, an Agent call, a script run — never reconstructed from memory at
the end. Entries are real observations from the real task, not a synthetic exercise, and not free-form prose (a
later aggregate pass greps/parses this across many task files, the same way `## ADR-NNN:` +
`**Status:**` makes `DECISIONS.md` mechanically indexable — inconsistent formatting defeats that).
Fixed key: value lines, one key per line, `n/a` for anything that doesn't apply to this task:

```
## Operational notes
- token_cost_review: <tokens summed from Agent-tool review-purpose calls, or n/a>
- token_cost_research: <tokens summed from Agent-tool research/investigation calls, or n/a>
- token_cost_verification: <tokens summed from Agent-tool verification/testing calls, or n/a>
- review_signal_ratio: <CONFIRMED/PLAUSIBLE findings that survived verification> / <total candidate findings raised across all finder angles>, or n/a if no /code-review ran this task
- context_loading_task_type: <the matching .claude/nav/context-loading.md row, or n/a>
- context_loading_consulted: <yes/no/n/a>
- context_loading_matched: <yes/no/n/a — did the actual read pattern match that row's guidance>
- flows_situation: <short phrase describing the situation, or n/a>
- flows_chosen: <the command/skill actually used, or n/a>
- flows_matched: <yes/no/n/a — did it match .claude/nav/flows.md's recommendation for that situation>
- haiku_decomposition_used: <yes/no/n/a — did this run's implementation phase (autopilot step 2) or /sync-docs diff-mode's parallelization dispatch Haiku per the eligibility criteria in those commands, or n/a if neither applies>
- haiku_decomposition_dispatch_count: <N Haiku dispatches used for this purpose, or n/a>
- haiku_decomposition_rework_needed: <N dispatches whose output needed a correction after verification> / <N total such dispatches>, or n/a
- adrs_referenced: <ADR-NNN (module), ADR-MMM (module), ... every ADR that actually shaped a decision or was followed in this task, in that exact key shape, or none — merely reading an ADR does not count>

### Agent calls
- <purpose> | subagent_type=<X> | tokens=<N> | tool_uses=<N> | duration_s=<N> | mode=<foreground|background> | batch=<parallel-group-id or solo>
(one line per Agent-tool invocation this task made; omit the whole subsection, not empty, if none)

### Script/command runs
- <script + args> | duration_s=<N> | mode=<foreground|background> | result=<pass|fail|n/a>
(one line per scripts/*.sh invocation this task made; omit the whole subsection, not empty, if none)

### Review angle yield
- <finder angle name> | survived=<N> | total_candidates=<N> | tokens=<N>
(one line per finder angle from that /code-review run — this is what review_signal_ratio's
aggregate is computed from; omit the whole subsection, not empty, if no /code-review ran)
```

Show this same block in the chat final report too, not only in the task file — writing it to the
file alone is invisible to the user unless they go open that file themselves. Include it verbatim
(or immediately adjacent to) the rest of the completion report, every time, not just when a number
happens to look interesting.
Token totals are never a full accounting — the main thread's own token usage is not collected into
this block, state that as context if asked, not inside the block itself (keep the block just the key: value lines, nothing
else, so parsing stays trivial). See `.claude/commands/sync-docs.md`'s Full Audit Mode for where
this data gets aggregated and acted on.

**`review_signal_ratio`** exists to answer, over time and across many tasks, whether a given
`/code-review` effort level actually pays for itself — a low ratio sustained across many runs is
evidence a lower effort level (fewer finder angles, less exhaustive verification) would have found
the same real bugs for less cost; a high ratio is evidence the current level is well-calibrated.
Compute it honestly from the real counts of that run, not a rounded guess. The per-angle
**Review angle yield** breakdown exists specifically so this decision can be made per-angle, not
just for the review process as a whole — an angle that's consistently expensive and low-yield
across many tasks is a candidate to drop or narrow; an angle that keeps finding real bugs
regardless of cost stays. Never drop an angle from one run's data alone.

**`haiku_decomposition_rework_needed`** exists to answer, over time, whether dispatching Haiku
per-unit for autopilot's step 2 (or `/sync-docs` diff-mode's parallelization) — no count or size
threshold, per the standing Haiku-dispatch rule — is actually safe to keep doing with no gate at
all: a high rework rate (corrections needed after verification) would be real evidence that a
threshold is needed after all (some dispatches are getting work that still needed judgment); a
low/zero rate confirms per-unit dispatch with no threshold is working as intended. Compute honestly
per run, same discipline as `review_signal_ratio` — never skip recording a rework event just
because it was caught and fixed quickly.

**`adrs_referenced`** exists to answer, over time, which ADRs are actually load-bearing — applied
in real work — versus ones nobody has used since they were written. `/record-decision` appends the
ADR it creates to the current task's line itself and writes `**Applied:** 1` into that new ADR. When
a task is closed, whoever closes it adds 1 to the `**Applied:** N` line of every ADR in the task's
`adrs_referenced` (adding `**Applied:** 1` right after `**Status:**` where the line is absent),
except an ADR this very task created, already counted at creation — one count per task, however
many times it is cited. `.claude/nav/scripts/generate-adr-index.sh` only
collects that line into an `Applied` column on the generated index (blank when absent, never 0); a consistently-zero count across many tasks is a
signal that ADR is a candidate for lower-priority upkeep (not automatic deletion — some decisions
are rarely touched specifically because they're settled), while a high count marks one worth
keeping especially precise and current.

**Agent calls vs. Script/command runs — different metrics, don't conflate them.** Every Agent-tool
task-notification carries real `<usage>` data (`subagent_tokens`/`tool_uses`/`duration_ms`) — use
it verbatim, don't estimate. Scripts (`build-and-test.sh`, `deploy-and-run.sh`, `playwright.sh`, etc.) are not
LLM calls and have no token cost of their own — never invent one; only record their real wall-clock
duration and pass/fail result. Main-thread (this conversation's own) token usage is recorded per
request in the session transcript (`message.usage` in `~/.claude/projects/<project>/<session>.jsonl`)
and in Claude Code's opt-in OpenTelemetry export, but nothing sums it per task — do not estimate
it; the `token_cost_*` fields above stay Agent-only for this reason.
