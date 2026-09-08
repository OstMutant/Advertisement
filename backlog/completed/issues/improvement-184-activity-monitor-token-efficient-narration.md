# improvement-184: Activity Monitor for background scripts — token-efficient, step-aware progress narration chained ahead of the Monitor tool

**Type:** improvement
**Module:** scripts/, .claude/rules.md, .claude/rules/scripts.md, .claude/commands/,
  .claude/nav/flows.md, CLAUDE.md, scripts/deploy-and-run/run.sh
**Priority:** 🔵
**When:** independent, no blockers

## Current state

Per `.claude/rules.md`'s "Scripts" section, the current workflow for tracking a backgrounded
script is: start it with `run_in_background: true`, tee its output to a log file, then attach the
harness's own `Monitor` tool directly to that raw log. Every new stdout line becomes a
notification event delivered into the main agent's own context — the rule already tells the agent
to "stay silent on routine per-event progress" in chat, but that only suppresses commentary, not
the token cost of the line itself already landing in context.

No activity-monitor script, profile system, or narration engine currently exists anywhere in the
repo (`scripts/` has no matching file or directory). A structured, machine-parseable step-output
contract already exists and is in active use: `scripts/utils/agentic-output.sh` emits
`AGENTIC_SUCCESS_BLOCK`/`AGENTIC_ERROR_BLOCK` JSON, consumed by DAG-aware scripts.

Running a script in the background and continuing the conversation with the agent in parallel
already works today via `run_in_background` (Bash or Agent tool) plus the resulting task/`Monitor`
notification — the agent is not blocked while a backgrounded process runs, and picks the result up
once notified. That capability is unrelated to this issue and out of scope here; this issue is
about token cost and the human-facing step display, not concurrency.

There are 7 top-level entry-point scripts today, each with its own log shape: `build-and-test.sh`,
`ci.sh`, `deploy-and-run.sh`, `playwright.sh`, `reset.sh`, `run-all-tests.sh`, `sonar.sh`.
Testcontainers-managed containers (`integration-tests/.../AbstractPostgresIntegrationTest.java`)
start programmatically from JVM test code during `mvn test`, so their lifecycle lines are already
interleaved into whichever script's own log runs the integration-test phase (`build-and-test.sh`)
— no separate log stream needed for that case. One real gap, since resolved for free: the dev-infra
`docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f docker-compose.minio.yml up -d`
(documented in root `CLAUDE.md`) is run directly by a human in their own shell, never through a
project script and never backgrounded/tee'd by the agent — originally nothing in this design could
attach to it. **Resolved by the wrapping interface itself (see "Usage modes" below), no separate
work needed:** since `monitor.sh` wraps any command, not only project scripts, the human can just
run `monitor.sh -- docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f
scripts/deploy-and-run/docker-compose.minio.yml up -d` directly in place of the bare command — no
thin wrapper script to write, just document the option.

`scripts/ci/DECISIONS.md` (ADR-009/ADR-010) already gives CI its own live per-stage view: a
persistent Dagu server with a browser-based web UI (clickable "Start", live per-stage status/logs,
run history). That's real, working step-visibility for CI today — just browser-based, not a
terminal corner, and it doesn't touch the main agent's own token cost, which is this issue's
separate concern.

## Why change

Long noisy background runs — Testcontainers/Maven CI output especially — push large volumes of
raw, low-signal stdout directly into the main agent's own (expensive-model) context via `Monitor`
notifications, at a cost that scales with the log's verbosity, not with its actual signal. There's
also no passive, glanceable, step-aware progress view for a human watching a long CI/deploy run —
only the raw scrolling log or the agent's own occasional narration.

## Expected benefit

- Main-agent token consumption for long background runs drops substantially — it would receive
  already-compacted status lines instead of every raw stdout line.
- The main agent keeps full escalation power — Read/Bash on the raw log — whenever the compact
  layer signals an anomaly, so no debugging depth is lost.

## Usage modes — corrected understanding (2026-09-08)

Two distinct modes, not one — earlier drafts of this issue conflated them:

**1. Agent-driven (the default, primary flow).** The human asks the agent to run something; the
agent runs it in the background (`run_in_background`, already works today), watches it via
`Monitor` chained to the narrated/compact log instead of raw stdout (this issue's actual token-cost
fix), stays silent while things are routine, and — **corrected 2026-09-08** — when the human asks
for status, the agent prints the actual step-tree (the same monospace ✅/⏳/⬜/⚠️/❌ block mode 2
renders) directly in chat, not its own paraphrased prose; a rendered tree is strictly more
informative than the agent's own summary of it. On an actual error, the agent shows that same tree
**plus its own added analysis** underneath (reads the raw log, explains the real cause) — tree
alone on request, tree *and* diagnosis on failure. The human keeps working with the agent in the
same conversation the whole time; **no second terminal pane is needed for this mode at all.**

**2. Standalone/autonomous (optional, secondary).** The human can also run the narration engine
directly themselves, independent of the agent entirely — their own terminal, their own pane, for
watching a process without going through chat at all. Same step-tree rendering as mode 1, just
live-redrawn continuously in its own pane instead of printed by the agent on request.

**Interface: `monitor.sh` wraps the target script instead of requiring a separately-managed log
file.** Rather than the original two-command setup (start the script with `tee` into a log file,
then separately point `monitor.sh` at that file), `monitor.sh` takes the target command itself and
runs it internally:
```
scripts/activity-monitor/monitor.sh --profile deploy -- scripts/deploy-and-run.sh
```
`monitor.sh` spawns the wrapped command, captures its raw stdout to its own internal log (so full
escalation detail is still available on error), applies the narration engine to produce the
compact/step output, and exits with the wrapped command's own real exit code — pass/fail stays
fully mechanical, sourced from the wrapped script, never from narration. This single invocation
works for both modes: mode 1 backgrounds this one wrapping command and points `Monitor` at its
narrated output directly; mode 2 is the human running the exact same command themselves in their
own second pane. One command, one moving part, instead of two coordinated ones.

## Approach

**Option A — standalone bash engine + per-source profiles**, based on a design a user supplied in
chat (tail → batch → redact → mechanical pattern-match → cheap-model fallback via a direct API
call, engine/profile split so pattern knowledge stays out of the shared engine). Extend it two
ways: (1) instead of writing only to a human-facing pane, also write the narrated output to a
compact log file, and point the main agent's own `Monitor` tool at that narrated file instead of
the raw stdout log; (2) extend profiles to emit structured step/phase markers, not just free-text
lines, so a renderer can show a stepper instead of a scrolling log.
Trade-off: a new bash tool to build and maintain, a live-loop process the user must remember to
start alongside each long script, and a separate curl-based model call path that bypasses however
the harness normally accounts for model usage.

**Option B — lean on the existing structured-output contract instead of narrating raw text.**
Extend more scripts (CI phases, deploy phases) to emit `AGENTIC_SUCCESS_BLOCK`/
`AGENTIC_ERROR_BLOCK`-shaped step markers directly, and have the main agent/`Monitor` watch only
for those structured lines — no pattern-guessing, no model call needed at all for scripts already
wired this way. For scripts not yet emitting structured markers (raw CI/Testcontainers chatter),
keep a mechanical/model-fallback bridge similar to Option A's `ci` profile until they're migrated.
Trade-off: requires touching each script to emit markers instead of one generic engine that works
against any log unmodified; less "instant" for a script that doesn't have it yet.

A combined approach is also viable: adopt Option A's engine mechanics, but skip the model call
entirely whenever a line already matches the structured `agentic-output.sh` contract, falling back
to free-text narration only for genuinely raw/unstructured chatter.

**Terminal-facing step-list UX — mode 2 (standalone) only, regardless of which option above is
chosen.** The human-facing pane renders as a live-updating step list, not a scrolling narration
log — one line per known phase, checked off on success, replaced with a short failure line plus a
pointer to more detail on error:

```
✅ Build
✅ Deploy container
⏳ Running tests
⬜ Sonar
```

```
✅ Build
✅ Deploy container
❌ Running tests — 3 failures (AdvertisementServiceTest)
   details: /tmp/ci.log (search "FAILURE")
```

This requires each step event to carry a small structured shape — step name, status, an optional
short failure reason, and an optional pointer to the raw log location — rather than one free-text
narrated line per batch; a stricter, smaller contract than plain narration text.

**Container-specific error pointers.** Two cases, different pointer target:
- The container operation *is* the step itself (e.g. `deploy-and-run.sh` starting
  `marketplace-app`) — the pointer is `docker logs <container-name>`, since the failure reason (an
  app crash inside the container) usually never reaches the launching script's own stdout at all,
  only the container's own logs:
  ```
  ✅ Build image
  ❌ Start container — port 8081 already in use
     details: docker logs marketplace-app
  ```
  ```
  ✅ Build image
  ✅ Start container (marketplace-app, port 8081)
  ❌ Health check — no response after 30s
     details: docker logs marketplace-app
  ```
- The container is infrastructure *inside* a step (e.g. Testcontainers' Postgres inside
  `build-and-test.sh`'s integration-test phase) — it doesn't get its own top-level checklist row;
  its failure is reported as that step's own failure, pointer stays the script's own log:
  ```
  ✅ Build
  ❌ Integration tests — postgres container failed to start (Docker daemon unreachable)
     details: /tmp/build-and-test.log (search "Could not find a valid Docker environment")
  ```

**CI's step list should be sourced from Dagu's own local API, not log narration.** `ADR-010`
(`scripts/ci/DECISIONS.md`) confirms `scripts/ci/dagu/pipeline-metrics.py` already queries Dagu's
local API (`GET /api/v1/dag-runs/{name}/{run-id}`) for real per-step status — including Dagu's
parallel branches (`build` → `unit`/`integration`/`e2e`/`sonar` in parallel → `docs`). A terminal
step-list for CI can poll that same API directly for exact, mechanical, already-parallel-aware
step state, with no LLM narration needed for the step-list itself:
```
✅ Build
├─ ✅ Unit tests
├─ ⏳ Integration tests
├─ ⚠️ Sonar — quality gate failed only on `new_coverage` (0%, JaCoCo not wired — known gap,
│              see improvement-114)
└─ ✅ E2E
⬜ Docs
```
This resolves the earlier "parallel execution" concern — CI's step list is a Dagu-API-polling
concern, structurally simpler than deploy's, not a special case of the log-narration engine.

**JaCoCo/`new_coverage` is a known non-critical failure, not a real regression — resolved.** Per
`improvement-114`, Sonar's `new_coverage` quality-gate condition always reads 0% because JaCoCo
isn't wired into the scan yet — this makes every CI run's Sonar step fail today for a reason that
isn't a real code-quality regression. The step list must tell these apart: a quality-gate failure
where `new_coverage` is the *only* failed condition renders as ⚠️ (known gap, links
`improvement-114`), not ❌; a quality-gate failure involving any *other* condition (new bugs,
vulnerabilities, code smells) still renders as a full ❌. This requires reading Sonar's per-condition
quality-gate breakdown (which conditions failed, not just pass/fail overall) rather than a single
pass/fail flag — the same per-condition data the `sonar-analyst` agent already queries from the
local SonarQube server.

**One shared `agentic` profile + a script→metadata map, not N per-script profile files —
resolved, full scope now all 7 scripts.** Verified: `build-and-test.sh`, `ci.sh`,
`deploy-and-run.sh`, `run-all-tests.sh`, `sonar.sh` already source `agentic-output.sh` and emit the
exact same `AGENTIC_SUCCESS_BLOCK`/`AGENTIC_ERROR_BLOCK` JSON shape; `playwright.sh`/`reset.sh`
don't yet. Since 5 of 7 already speak one identical contract, a bespoke profile file per script
would just duplicate the same `jq`-parsing logic 5 times — the real fix is **one** `profiles/
agentic.sh` that parses that shared contract, plus a small script→metadata map (display label per
`currentStep` value, whether a step is container-associated) supplying only the per-script
differences. `monitor.sh` looks up the wrapped script's basename in that map to auto-select
`agentic` (or `generic` as fallback) — the caller never passes a separate `--profile` flag:
```
monitor.sh -- scripts/deploy-and-run.sh
```
Full scope now includes wiring `playwright.sh` and `reset.sh` to `agentic-output.sh` too (real,
separate work inside those two scripts, not just a new profile file) so all 7 end up on the shared
`agentic` profile — `ci.sh` additionally layers Dagu-API polling on top for its parallel step
detail, everyone else needs nothing beyond the shared profile.

**Symmetric project tooling — this ships like every other script-group in this repo, not as a
bespoke one-off.** Per this project's own standing conventions (`.claude/rules.md`'s
"Script-group directory structure" and "A new project-local command/skill file adds its own
navigation row" rules):
- A thin top-level entry point `scripts/activity-monitor.sh`, forwarding to
  `scripts/activity-monitor/run.sh` — same pattern as `scripts/build-and-test.sh` →
  `scripts/build-and-test/run.sh`.
- A slash command `.claude/commands/activity-monitor.md`, plus its row in `.claude/nav/flows.md`'s
  "Project commands & skills" table (mandatory, same operation) and in root `CLAUDE.md`'s "Slash
  commands available" list, matching how `/build-and-test`/`/deploy-and-run` are already listed.
- `scripts/activity-monitor/README.md`, written per the `infra-readme-standards` skill's
  conventions (what the tool is, its Flow section, ISO 5807 decision diamonds where relevant).
- File-level header comments on every new script file, per the `infra-doc-standards` skill's
  conventions.
- Run `/sync-docs` once this lands — root `CLAUDE.md` already says to run it manually "after
  significant changes (new module, new SPI...)"; a new script-group is exactly that shape of
  change, so the architecture map picks it up.

## Execution plan (drafted 2026-09-08, implemented 2026-09-08 via autopilot)

**Phase 0 — verified current state, changes the plan.** `scripts/deploy-and-run/run.sh` already
`source`s `scripts/utils/agentic-output.sh` and already emits `AGENTIC_ERROR_BLOCK`/
`AGENTIC_SUCCESS_BLOCK` at two granularity points (`currentStep` values `"deploy"` and
`"start-application"`). Its `ERR` trap already runs `docker logs --tail=40 "$APP_CONTAINER"` into
its own log on failure. This is more existing wiring than assumed earlier in this issue — less new
plumbing needed than a from-scratch build.

**Phase 1 — engine core, adapted from the design a user supplied in chat.** Reused as-is:
`redact()` (its `sed` patterns), `bound_chunk()` (byte/line capping with a reserved-space marker),
the mechanical-first/model-fallback split, the "a profile owns only `try_mechanical_format()`"
boundary. Adapted: restructure from "tail an externally pre-existing log file" into the agreed
wrapping interface (`monitor.sh --profile <name> -- <command...>`) — spawn the target command
itself, capture its raw stdout to an internal log, propagate its real exit code. New: a small
step-state table (name → status/reason/pointer) replacing the original's one-free-text-line-per-
batch model. Reused: `monitor.test.sh`'s harness pattern (fake `curl`, `assert` helper) and its
existing cases (redact, bound, idle, mechanical, unknown→one model call, model-failure-visible,
end-to-end secret redaction) — extended with new cases for spawn/exit-code passthrough, step-state
transitions, and docker-logs-vs-script-log pointer selection.

**Phase 2 — shared `agentic` profile + script→metadata map, `deploy-and-run.sh` first.** One
`profiles/agentic.sh` (parse `AGENTIC_SUCCESS_BLOCK`/`AGENTIC_ERROR_BLOCK` JSON via `jq`, update the
step-state table) plus a metadata map (per-script `currentStep`→display-label, container
association). Build and verify against `deploy-and-run.sh` first (its wiring is already fully
verified — see Phase 0).

**`currentStep` granularity — resolved: extend `run.sh` itself, not the profile's pattern-matching.**
`run.sh`'s current `AGENTIC_SUCCESS_BLOCK`/`ERROR_BLOCK` markers are coarser (`"deploy"`,
`"start-application"`) than the desired checklist (Build image / Start container / Health check).
Decision: add finer-grained `emit_agentic_success_block`/`emit_agentic_error_block` calls directly
into `run.sh` at each real step boundary (image build, container start, health check), rather than
having the profile guess step boundaries from `run.sh`'s other printed lines. Reasoning (user's,
2026-09-08): one source of truth, and it belongs at the lowest level — the process that actually
performs the step, not a pattern-matching layer once removed from it. Container-log pointer: since
`run.sh` already tails `docker logs --tail=40` into its own failure output, the pointer in most
failure cases can just be "see the script's own raw log" — reusing existing behavior instead of a
fresh `docker logs` invocation.

**Phase 2b — extend the shared profile to the other 4 already-wired scripts.** `build-and-test.sh`,
`ci.sh` (plus its Dagu-API layer for parallel detail), `run-all-tests.sh`, `sonar.sh` — each just
needs its own metadata-map entries (step labels), no new parsing logic, since they already emit the
same JSON contract `deploy-and-run.sh` does.

**Phase 2c — dropped, already done.** Corrected 2026-09-08 during autopilot execution: the earlier
"`playwright.sh`/`reset.sh` don't emit the contract" claim was wrong — it came from grepping
`/app/scripts/playwright*`/`/app/scripts/reset*`, which never matches `scripts/playwright.sh`'s
real delegate (`/app/playwright/run.sh`, outside `scripts/` entirely) or `scripts/reset.sh`'s real
delegate (`/app/scripts/deploy-and-run/reset.sh`, a differently-named subdirectory). Direct greps
against the real delegate files confirm both already call `emit_agentic_success_block`/
`emit_agentic_error_block` — matching `scripts/DECISIONS.md` ADR-013, which already states all 7
top-level scripts were wired. All 7 scripts go on the shared `agentic` profile from the start; no
script-internal wiring work needed in this issue at all.

**Phase 3 — Mode 1 wiring (agent-driven, default).** Update `.claude/rules.md`'s "Scripts" section:
background `monitor.sh -- <script>` as the one command instead of today's raw-`tee` +
`Monitor`-on-raw-log pattern; the harness's `Monitor` tool points at `monitor.sh`'s own
narrated-output stream; document the on-request tree-print / on-error tree-plus-analysis behavior
from "Usage modes" above.

**Phase 4 — Mode 2 (standalone) step-list renderer.** Build the redraw-in-place checklist display
(✅/⏳/⬜/⚠️/❌, per "Terminal-facing step-list UX" above) as `monitor.sh`'s own terminal output when
attached to a real terminal — shared with mode 1's on-request tree print, not a separate renderer.

**Phase 5 — symmetric project tooling.** `scripts/activity-monitor.sh` thin entry point,
`.claude/commands/activity-monitor.md` + its `.claude/nav/flows.md` row + its `CLAUDE.md` slash-
command list row, `scripts/activity-monitor/README.md`, file-level headers on every new script,
`/sync-docs` run — per "Symmetric project tooling" above.

**Phase 6 — tests + one real dry run.** Run the extended `monitor.test.sh`; also do one
deliberately-broken real deploy (e.g. pre-occupy port 8081) to verify the ❌ + pointer path fires
on real behavior, not just unit assertions.

**Explicitly still deferred, not part of this slice:** CI's Dagu-API polling implementation itself
(step 2b only adds its metadata-map entries, not the API-polling code — separate follow-up); the
dev-infra `docker-compose` wrapping decision (still an open question below).

## Implementation status (2026-09-08, via autopilot)

Shipped: `scripts/activity-monitor/run.sh` (engine — renamed from the drafted `monitor.sh` to
match this project's own `scripts/<name>/run.sh` script-group convention, per the Approach's own
"symmetric tooling" note), `profiles/agentic.sh` (shared profile, all 7 scripts), 4 finer-grained
`emit_agentic_success_block` calls added to `scripts/deploy-and-run/run.sh` (infra/build/
start-container/start-application), `scripts/activity-monitor.sh` thin entry point,
`.claude/commands/activity-monitor.md`, `README.md`, `.claude/nav/flows.md` and `CLAUDE.md` rows,
`.claude/rules.md`/`.claude/rules/scripts.md` updated to the new default backgrounding pattern for
the 6 non-CI scripts (`ci.sh` keeps its own Dagu-API-based mechanism, unchanged — see
`.claude/rules/scripts.md`). 24/24 tests pass (`run.test.sh`); one real dry run against the actual
`deploy-and-run.sh` confirmed the pipeline end to end.

Two real findings, not assumptions, that changed the implementation from the drafted plan:
- **`jq` isn't installed anywhere in this sandbox and isn't a dependency of any other script in
  this repo** — confirmed directly before committing to it. `profiles/agentic.sh` was rewritten to
  extract fields via targeted `sed` instead (safe here specifically because the JSON producer is
  this repo's own fixed-shape `agentic-output.sh`, not arbitrary external JSON); `jq` stays a real
  dependency only of the `generic` profile's model-narration fallback path, guarded with a visible
  "jq not installed" marker instead of a silent failure.
- **`scripts/deploy-and-run.sh` has no execute bit in this checkout**, and this project's own
  convention invokes every script via an explicit `bash scripts/X.sh`, never direct execution —
  confirmed the same "Permission denied" failure on a plain manual `scripts/deploy-and-run.sh`
  invocation, not specific to the new wrapper. `run.sh`'s script-name detection now recognizes an
  interpreter prefix (`bash`/`sh`/`python3`/...) and reads the real script from the next argument
  instead of the interpreter's own name.

Known, deliberately deferred (not silently dropped): the Sonar/JaCoCo ⚠️-vs-❌ distinction
described above under "JaCoCo/`new_coverage` is a known non-critical failure" is **not**
implemented — it needs Sonar's own per-condition quality-gate breakdown (which specific condition
failed), not just the single `description` string `AGENTIC_ERROR_BLOCK` carries today; a Sonar
quality-gate failure currently renders as a plain ❌ regardless of cause. Building that would mean
querying SonarQube's own API for per-condition data (the same shape `sonar-analyst` already
queries) — real, separate work, not started. CI's Dagu-API polling step list (Phase 2b's own scope
note) is unchanged/not built — `ci.sh` keeps its existing `watch-run.py` mechanism.

## Open questions

None remaining. All resolved (kept here for the record): build order (Phase 2 → 2b → 2c); the
dev-infra `docker-compose` gap (covered free by the wrapping interface — see "Current state");
prior art (web search done — see "Prior art" below); where the step list renders (mode 2 only —
see "Usage modes"); `PostToolUse` hooks as an alternative mechanism (ruled out — see "Certification
alignment check"); Phase 2's `currentStep` granularity (extend `run.sh` itself — see Phase 2 above).

## Certification alignment check (2026-09-08)

Checked this plan against the real private certification document
(`/app/private/AICertificationAndAudit/AICertification.txt`, the same source `improvement-160`
verified claims against directly rather than assuming) — targeted at the sections actually
relevant to this design (redaction/secrets, cost optimisation, structured output, hooks), not an
exhaustive read of all 5 domains.

**Confirmed match — synchronous API calls, not the Message Batches API.** The document's "Batch
Processing Strategies" section states the Message Batches API trades a 50% cost cut for "no
guaranteed latency SLA... results may arrive in minutes or take up to 24 hours," and states plainly
it's for "latency-tolerant workflows where results are consumed later," never for a blocking/live
workflow — its own worked example contrasts a synchronous pre-merge CI review against an overnight
batch report. This design's narration (live, batches every few seconds, watched in real time) is
squarely the blocking/synchronous case — the plan's direct synchronous API call per batch is
already the certified-correct choice, not a missed cost optimisation.

**Confirmed match — structured output over free text for machine/automated consumption.** The
document's CI/CD Integration domain makes the same point this plan already leans on: "In CI,
Claude Code's output has to be machine-parseable. No human is reading it" (`--output-format json`).
That flag itself governs Claude Code's own CLI output specifically and doesn't apply here (our
target scripts are plain bash, not Claude Code sessions) — but the underlying principle matches
exactly why this plan prefers `agentic-output.sh`'s existing JSON contract over LLM narration
wherever a script already emits it, falling back to narration only for genuinely unstructured
output.

**Checked and ruled out — `PostToolUse` hooks cannot replace the external engine.** Task Statement
1.5 ("Agent SDK Hooks") describes `PostToolUse` as running "after a tool executes but before the
model processes the result" — close to Mode 1's own goal in wording. Spiked via the
`claude-code-guide` agent against Claude Code's real hooks/`Monitor` documentation: hooks fire
"per tool call," and `Monitor` is fire-and-forget — its own tool call returns immediately, and every
subsequent notification arrives as a separate async conversation event, not as part of that
original tool call. `PostToolUse` therefore fires once, not per streamed notification, and cannot
intercept or transform `Monitor`'s live output while it's still watching. (An undocumented
`Notification` hook event might apply to `Monitor`'s streamed events specifically — unconfirmed,
not worth designing around.) Also independently ruled out on its own terms, regardless of this
finding: hooks only exist inside a Claude Code session, so they could never help mode 2
(standalone/autonomous, no agent session involved at all) even if this had worked for mode 1. The
external wrapping-engine architecture (Phase 1 as planned) is necessary for both modes — no change
to the plan.

## Prior art (web search, 2026-09-08)

No exact match found for this issue's specific combination (tail a script's raw log → redact →
mechanical pattern-match → cheap-model fallback narration, chained ahead of the main agent's own
log-watching tool specifically to cut its token cost). Adjacent findings:
- The harness's own `Monitor` tool already works as described in "Current state" — confirmed, not
  something to reinvent.
- `openai/codex` has an open, unresolved GitHub issue ("Show background process progress (similar
  to /agents)") asking for the same underlying capability in a competing AI coding CLI — a real,
  recognized gap in the space, not a solved problem elsewhere.
- Adjacent (not equivalent) tooling exists for the general "visual agent/process status in a
  terminal corner" shape: `Pane` (runpane.com, per-pane status dot: blocked/working/done), `ctop`
  (aakashadesara/ctop, CPU/tokens/context/cost dashboard for AI coding sessions), `cmux`, `herdr` —
  none of these narrate a raw log's content with an LLM; they visualize already-known process
  state (running/blocked/done), not "what is this log actually saying right now."

## Related

- `.claude/rules.md` "Scripts" section — current `Monitor` tool workflow this issue proposes to
  chain behind a narration layer, not replace.
- `scripts/utils/agentic-output.sh` — existing structured step-output contract.
- `scripts/ci/DECISIONS.md` (ADR-009/ADR-010, Dagu-backed local CI visualization) — confirmed
  overlap: Dagu already gives CI a live per-stage browser view; see "Current state" above.

## Operational notes
- token_cost_review: 312497
- token_cost_research: n/a
- token_cost_verification: 344615
- review_signal_ratio: 5 / 6
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: watching a long backgrounded script without paying token cost proportional to raw log volume
- flows_chosen: /activity-monitor (newly created this task)
- flows_matched: n/a (mechanism didn't exist before this task)

### Agent calls
- Claude Code hooks/Monitor documentation check (PostToolUse feasibility) | subagent_type=claude-code-guide | tokens=70882 | tool_uses=5 | duration_s=69 | mode=background | batch=solo
- Code review of activity-monitor changes | subagent_type=deep-review-orchestrator | tokens=n/a (see sub-calls) | tool_uses=n/a | duration_s=n/a | mode=background | batch=solo
- DRY/KISS/YAGNI review of activity-monitor | subagent_type=general-purpose | tokens=79348 | tool_uses=17 | duration_s=77 | mode=background | batch=review-step3
- SOLID review of activity-monitor | subagent_type=general-purpose | tokens=66903 | tool_uses=8 | duration_s=40 | mode=background | batch=review-step3
- Precedent review of activity-monitor | subagent_type=general-purpose | tokens=166246 | tool_uses=63 | duration_s=176 | mode=background | batch=review-step3
- Verify ERR trap step-attribution finding | subagent_type=general-purpose | tokens=63346 | tool_uses=4 | duration_s=21 | mode=background | batch=review-step4
- Verify profile extension point leak finding | subagent_type=general-purpose | tokens=52263 | tool_uses=5 | duration_s=19 | mode=background | batch=review-step4
- Verify command-file staleness finding | subagent_type=general-purpose | tokens=56264 | tool_uses=5 | duration_s=47 | mode=background | batch=review-step4
- Verify commands README missing entry finding | subagent_type=general-purpose | tokens=48362 | tool_uses=4 | duration_s=14 | mode=background | batch=review-step4
- Verify BACKLOG.md staleness finding | subagent_type=general-purpose | tokens=58752 | tool_uses=7 | duration_s=25 | mode=background | batch=review-step4
- Cross-file contradiction check for review findings | subagent_type=general-purpose | tokens=45124 | tool_uses=0 | duration_s=2 | mode=background | batch=review-step5-solo

### Script/command runs
- scripts/activity-monitor/run.test.sh | duration_s=1 | mode=foreground | result=pass (24/24)
- scripts/activity-monitor.sh -- bash scripts/deploy-and-run.sh (real dry run) | duration_s=~180 | mode=background | result=pass (exit 0)
- .claude/nav/scripts/generate-adr-index.sh | duration_s=1 | mode=foreground | result=pass

### Review angle yield
- dry-kiss-yagni | survived=1 | total_candidates=1 | tokens=79348
- solid | survived=2 | total_candidates=2 | tokens=66903
- precedent | survived=3 | total_candidates=3 | tokens=166246

## Follow-up (2026-09-08): elapsed time, per-step descriptions, stall detection

Real usage (a live `deploy-and-run.sh` demo run) surfaced a genuine trade-off: `tree.txt` only
redraws on a real step transition, so a long-running step (an 8-minute build, say) shows nothing
new for the whole duration — a human watching can't tell "still working" from "stalled." User
feedback, approved for implementation:

- **Elapsed time per step.** `render_tree()` walks a new `SCRIPT_STEP_SEQUENCE` (script basename →
  its known steps in order, one entry per script). A completed step shows its real duration
  (`STEP_COMPLETED_AT[name]` minus the previous step's completion time, or `WRAPPER_START_TIME` for
  the first step); the first not-yet-terminal step in sequence shows a live "running Xm Ys" elapsed
  counter; steps after it show `⬜` with no timer (not yet reached).
- **Short one-line description per step**, from a new `STEP_DESCRIPTIONS["script:step"]` map
  (same key shape as `STEP_LABELS`), rendered under/alongside each step's label.
- **Stall detection — mechanical, no LLM, no raw-log narration added back.** `main()`'s existing
  byte-offset poll loop already tracks whether `raw.log` grew since the last poll; add
  `LAST_ACTIVITY_AT` (epoch, updated only when it actually grew). `render_tree()` computes
  `NOW - LAST_ACTIVITY_AT` for the currently-active step only; past a configurable
  `STALL_THRESHOLD_SECONDS` (default 120s) with zero new output, append `⚠️ no output for Xm Ys` to
  that step's line — a visual heuristic only, never affects exit code or pass/fail.
- Explicitly rejected (per the same feedback): reintroducing raw stdout narration into the tree, or
  any LLM call, just to answer "is it still working" — the elapsed-time + stall marker answers that
  question with data the engine already collects, at zero extra cost.

**Revised further, same session, before implementation:** a fixed global stall threshold was
rejected too (Playwright runs / a combined unit+integration `build-and-test` step are legitimately
silent far longer than infra/container steps — one number would either false-positive on those or
be too slow to catch a real hang elsewhere). Replaced with `STEP_STALL_THRESHOLD["script:step"]`
per-step overrides (300s for `playwright.sh:playwright-run` and `build-and-test.sh:build-and-test`,
120s default elsewhere).

**Revised again — adaptive history + container-state check, not CPU-tree checking.** A CPU-based
"is the process actually working" signal was proposed and rejected once traced through: this
project's heaviest steps (build, test) delegate their real work into Docker containers, invisible
to the wrapping script's own host process tree — a CPU check would read near-zero exactly during
the steps most worth watching. Implemented instead:
- `get_stall_threshold()` — reads `$WORK_DIR/history.tsv` (`step_id<TAB>duration_seconds`, one line
  per past successful run's step, appended by `record_step_history()` once at the end of a
  successful `main()` run, never truncated between runs). If a step has prior history, the
  effective threshold becomes `2 × its own past maximum duration`, replacing the flat/per-step
  constant; first-run-ever (no history) falls back to that constant.
- `container_state_warning()` — for steps in `STEP_IS_CONTAINER` (now holding the real container
  name, not a boolean), checks `docker inspect -f '{{.State.Status}}' <container>` directly instead
  of a silence timer — a container that stopped/died while its step is still shown "running" is a
  direct, immediate signal, checked in place of (not in addition to) the log-silence check for
  those specific steps.

**Corrected data-model bug found during this same pass:** `mark_step`/`STEP_STATUS` etc. were
originally keyed by each step's already-resolved display *label* (`profiles/agentic.sh` looked the
label up before calling `mark_step`). Reworked to key by the raw `currentStep` id instead —
`render_tree`/`render_step_line` resolve `STEP_LABELS`/`STEP_DESCRIPTIONS` at render time. This
also surfaced and fixed a real, separate finding: most of the 7 scripts only ever emit **one** real
success-tracked milestone per run (e.g. `playwright.sh`'s `check-app-container`/`start-app`/
`restart-app`/`arg-parsing` are early-failure attribution points only, never followed by their own
success marker) — `SCRIPT_STEP_SEQUENCE` lists only the ids that actually complete in the happy
path; the rest still render correctly if they fire, via the existing "arrived but not in the
declared sequence" fallback path, just without pending/duration semantics.

**Verified:** 30/30 `run.test.sh` assertions (6 new: sequence-based duration/elapsed/pending
rendering, adaptive-threshold fallback-vs-history behavior, container-state warning on a
nonexistent container). One more real dry run against the actual `deploy-and-run.sh` — real
per-step durations recorded and displayed live (`infra 3s`, `build 1m59s`/119s history entry,
`start-container 4s`, `start-application/Health check 21s`), `history.tsv` populated correctly,
exit 0. `bash docs/architecture/scripts/generate-architecture-model.sh` re-run after this session's
changes (new module + `DECISIONS.md`/command-file/`flows.md` edits) — confirmed
`scripts/activity-monitor/` now appears in `docs/architecture/data/architecture-model.json` (12
matches, including `run.sh`/`run.test.sh`/`profiles/agentic.sh`), which had been missed in the
original implementation pass.

**Corrected again — one command, one terminal, no separate `--watch` needed for the common
standalone case.** A human running the main `-- <command>` invocation directly (not backgrounded by
the agent) was still stuck needing a second `--watch` command in a second pane to see live
progress, since the main loop only wrote `tree.txt` without ever printing to its own stdout. Fixed:
`main()`'s poll loop now redraws (`clear` + `cat tree.txt`) every `BATCH_SECONDS` when its own
stdout is a real terminal (`[[ -t 1 ]]`) — silent otherwise (e.g. backgrounded by the agent via
`run_in_background`, where stdout isn't a terminal at all). `--watch` stays for its narrower real
use: peeking at a run already started elsewhere from a separate terminal. Also fixed: `tree.txt`
now refreshes every poll cycle even when no new raw output arrived (previously only updated when
`process_batch` ran), so a running step's live elapsed/stall timer keeps ticking during a silent
stretch instead of freezing. `run.sh`/`README.md`/`.claude/commands/activity-monitor.md` updated to
match. Verified: `run.test.sh` still 30/30 (the added branches are no-ops in the test harness's
own non-interactive context); a real fake-script run confirmed the poll/exit-code/tree-write
mechanics are unaffected.
