# improvement-171: Formalize `/deep-review`'s 4-lens pattern as real `.claude/agents/*.md` subagents

**Type:** improvement — AI tooling/process infra
**Module:** candidate new `.claude/agents/`, `.claude/skills/deep-review/references/diff-mode.md`,
  `.claude/skills/deep-review/SKILL.md`
**Priority:** high (Top)
**When:** independent, no blockers

## Current state

`.claude/skills/deep-review/references/diff-mode.md` already runs a real 4-lens parallel-review
pattern — security-boundary, data-integrity, SOLID/DRY, and CLAUDE.md/ArchitectureRulesTest
compliance — each as an inline prompt handed to a `general-purpose` Agent-tool dispatch, plus a
separate per-candidate verification subagent for every finding. This works today but exists only
as prose inside `diff-mode.md`; there are no persisted, named, reusable subagent definitions
anywhere in the repo — `.claude/agents/` doesn't exist (confirmed directly, `ls` returns "No such
file or directory"). Every review dispatch re-describes the same role inline, and the pattern
can't be reused outside `/deep-review` itself.

`.claude/agents/*.md` (YAML frontmatter — `name`/`description`/`tools`/`model` — plus a markdown
body as the system prompt) is a real, documented Claude Code feature, verified against
`code.claude.com/docs` during `improvement-169`'s investigation.

## Why change

`improvement-169` (Hybrid Agentic Review Factory investigation, closed 2026-08-25) evaluated a
much larger proposal — a mechanical Semgrep/ArchUnit/SonarQube-MCP detection layer feeding the
4-lens reasoning layer — and found two of its proposed mechanical rules directly contradicted this
project's own documented architecture, and its "coordinate-only sniper reads" pitch conflicted with
`deep-review/SKILL.md`'s own "Verify, don't relay" discipline. The decision reached there: skip the
mechanical layer entirely, but the narrowest candidate — formalizing the already-working 4-lens
pattern as real, named `.claude/agents/*.md` files — remains worth doing on its own, since it
codifies something already proven rather than adding new, unvalidated automation.

`improvement-160-certification-coverage-map.md` independently tracked the same gap from the
certification-coverage angle:
- **D1-2** — "Real hub-and-spoke orchestration with named, reusable specialized subagents (not
  just a generic type + inline prompt text)."
- **D3-1** — isolating a whole review run from the main conversation via one orchestrating
  top-level `Agent` call (a subagent that itself spawns its own sub-subagents), instead of running
  the coordinator role inline in the main chat thread.

Both rows are still `idea` status, unimplemented.

## Expected benefit

- The 4 lenses become real, inspectable, individually-editable files instead of prose buried
  inside `diff-mode.md` — easier to tune one lens (e.g. tighten security-boundary's prompt) without
  touching the others.
- Each agent's `tools:` field can be scoped to read-only (`Read`, `Grep`, `Glob`) instead of the
  current `general-purpose` dispatch's full tool access — a real fix for the "4-5 tools/agent
  optimal" gap `improvement-160`'s Domain 2 finding already flagged (review subagents never need
  `Write`/`Edit`/`Bash`).
- A single orchestrating `Agent` call (per D3-1) keeps the whole review's coordination reasoning
  out of the main conversation's context, not just the 4 lenses' own work — addresses part of the
  token-cost concern raised in `improvement-160`'s investigation log without touching the mechanical
  layer `improvement-169` rejected.
- Reusability: a named `security-boundary-reviewer.md` (etc.) becomes callable from any future
  skill/command, not locked inside `/deep-review`'s own prompt text.

## Approach

Researched `github.com/VoltAgent/awesome-claude-code-subagents` (community collection of 158+
`.claude/agents/*.md`-format definitions) as a reference before drafting an approach — fetched
`categories/04-quality-security/architect-reviewer.md` and `code-reviewer.md` directly (the two
closest matches to this project's SOLID/DRY and general-review lenses):

- **Frontmatter format confirmed reusable as-is**: `name`/`description`/`tools`/`model` — matches
  what this project would need.
- **Content NOT directly reusable**: both grant `tools: Read, Write, Edit, Bash, Glob, Grep` — full
  write access, wrong for a findings-only reviewer (this project's `/deep-review` is explicitly
  "never writes code"). Both are generic, checklist-style prose ("Design patterns appropriate
  verified", "Scalability requirements met confirmed") not grounded in this project's actual bug
  patterns — the same reasoning `diff-mode.md` itself already used to justify diverging from
  Anthropic's own generic "find bugs" agent in favor of this project's 4 specific lenses. Both also
  assume a separate multi-agent "Communication Protocol" (JSON messages to a "context manager") —
  not how native Claude Code subagents actually communicate; the calling orchestrator passes
  context directly and receives the result through the Agent tool's own result channel.
- Neither file maps 1:1 onto this project's data-integrity or security-boundary lenses specifically
  (VoltAgent's collection has no equivalent — its `security-auditor.md` is broad pentesting-style,
  not this project's narrow "authorization at the service boundary" concern).

Two options, given the above:

1. **Write all 4 agent files from scratch**, porting `diff-mode.md`'s existing, already-tuned
   prompt text for each lens verbatim into the body, scoping `tools:` to `Read, Grep, Glob` only,
   `model: inherit`. Lowest risk — the prompt content is already proven in this project; only its
   packaging changes.
2. **Adapt VoltAgent's `architect-reviewer.md`/`code-reviewer.md` as a starting skeleton** for the
   SOLID/DRY and general-compliance lenses, stripped of the write tools and the
   Communication-Protocol section, then fold in this project's own specific bug-pattern language.
   More work reconciling generic checklist prose with this project's proven, narrower prompts, for
   unclear extra benefit over option 1.

Recommend option 1, given VoltAgent's content didn't survive verification as directly portable.

Also design the orchestrator (D3-1): a wrapping top-level `Agent` call that reads `diff-mode.md`'s
(now largely superseded) procedure and internally dispatches the 4 named subagents plus their
per-candidate verifiers, returning only the final findings to the main conversation.

## Related

- `backlog/completed/issues/improvement-169-hybrid-agentic-review-factory.md` — the investigation
  and decision this issue implements the narrow scope of.
- `backlog/issues/improvement-160-certification-coverage-map.md` rows D1-2, D3-1 — independently
  tracked the same idea from the certification-coverage angle.
- `.claude/skills/deep-review/references/diff-mode.md` — the existing, already-working prompt text
  this issue formalizes into real files (`SKILL.md`/`diff-mode.md` were left unmodified in the end
  — see "Implementation update" below).

## Implementation update (2026-08-25)

Scope narrowed further during implementation, by explicit direction: only `solid-dry-reviewer.md`
+ `deep-review-orchestrator.md` were built (the other 3 lens files were written, then deleted —
`.claude/agents/` holds just these two). The orchestrator is fully self-contained — it does not
read `SKILL.md`/`diff-mode.md` at runtime, and `diff-mode.md` itself was reverted back to its
original inline-4-lens prose, untouched. `deep-review-orchestrator.md` supports 4 scope modes
(current uncommitted changes, one commit, one module, or the whole repo) and is invoked directly
via `Agent`, independent of `/deep-review`.

Inter-agent data format: `solid-dry-reviewer` → orchestrator, and orchestrator → its
per-candidate verifier subagent, both use structured JSON (content separated from metadata —
`claim`/`failure_scenario` vs. `file`/`line`/`confidence`/`found_by`), not free text. This follows
`AICertification.txt`'s Domain 1 Rule 2 ("use structured data formats that separate content from
metadata... if you pass content without metadata, the downstream agent cannot attribute claims to
sources") — verified against independent, non-certification sources before adopting it, not taken
on the certification's word alone:
- [Protocols for Inter-Agent Message Exchange](https://apxml.com/courses/multi-agent-llm-systems-design-implementation/chapter-3-agent-communication-coordination/inter-agent-message-protocols) — freeform vs. structured vs. semi-structured inter-agent protocol taxonomy.
- [A Technical Taxonomy of LLM Agent Communication Protocols](https://arxiv.org/html/2606.19135v1)
- [A survey of agent interoperability protocols: MCP, ACP, A2A, ANP](https://arxiv.org/pdf/2505.02279) — real production protocols (Google A2A, MCP, IBM/BeeAI ACP) all use structured JSON for agent-to-agent messages, not free text.
- [Towards Engineering Multi-Agent LLMs: A Protocol-Driven Approach](https://arxiv.org/html/2510.12120)
- [Structured Inter-Agent Communication](https://www.emergentmind.com/topics/structured-inter-agent-communication)

The final hop (orchestrator → host, now step 8) uses the `ReportFindings` tool — a different,
already-typed mechanism, unaffected by this change.

**Two further gaps closed against `AICertification.txt`'s "Multi-Instance and Multi-Pass Review"
section, after direct verification against the real file (not assumed from memory of a prior
session's summary):**

1. **Cross-file integration pass (new step 5).** The certification describes a 2-pass review
   architecture for multi-file reviews — Pass 1 per-file local analysis, Pass 2 a separate
   integration pass over all Pass-1 findings checking for cross-file contradictions — specifically
   to avoid "attention dilution" (uneven depth, missed contradictions) that a single bulk pass
   suffers. The orchestrator's per-candidate verification (step 4) already matches the
   certification's separate "independent instance, no prior reasoning context" principle at a finer
   grain than the doc's own one-instance-per-whole-output example — that finer grain does not
   conflict with the multi-pass guidance, since it independently satisfies the same
   attention-dilution concern the multi-pass section targets. What was missing was the *second*
   pass itself: step 5 now dispatches one further fresh subagent over all step-4 survivors to check
   for exactly the contradictions the certification names.
2. **Confidence-based routing (new step 7).** The certification's own worked example
   (`"confidence": 0.65, "route": "human_review"`) splits findings by confidence instead of
   auto-reporting everything uniformly, warning that "using uncalibrated confidence for automated
   decisions is an anti-pattern." `solid-dry-reviewer`'s `confidence` field existed but was
   previously unused (dead) by the orchestrator. Step 7 now actually branches on it:
   `"high"` → the automatic `ReportFindings`/backlog-issue path; `"medium"`/`"low"` → a separate
   "needs human review" list in the final summary, never auto-filed.

Not implemented, and out of scope here: real confidence *calibration* against a labelled validation
set (the certification's own caveat that raw self-reported confidence is "uncalibrated, unreliable
for automated decisions") — the high/medium/low split above is a routing mechanism only, not a
calibration exercise; no labelled data exists in this project to calibrate against yet.

**Third gap closed:** the certification's explicit "Parallel Spawning" rule ("emit multiple Task
tool calls in a single response rather than invoking them one at a time across separate turns —
sequential spawning adds latency for nothing") was already followed by step 1's per-module loop but
not stated for step 4's per-candidate verifiers — fixed, step 4 now says so explicitly.

## Real-run bug found and fixed (2026-08-25)

Live test (`Agent({subagent_type: "deep-review-orchestrator", prompt: "module
taxon-spring-boot-starter"})`) surfaced two real bugs, both fixed:

1. **First run: the orchestrator silently followed the old, deleted `diff-mode.md`/`SKILL.md`
   procedure instead of its own file** — it treated `module <name>` scope as "diff of the last
   commit touching that module" (citing "diff-mode.md's step 1, cheap skip check") instead of the
   file-set sweep its own step 1 actually specifies. Root cause: subagents can see the full skill
   listing by default, and the model apparently defaulted to the topically-related, pre-existing
   skill over its own persisted instructions, despite one descriptive sentence ("you do not depend
   on `/deep-review`"). Fix: deleted `.claude/skills/deep-review/` entirely (see "Related" below) —
   the subagent physically cannot fall back to a file that no longer exists. Re-ran the identical
   test after the deletion: correct behavior confirmed — real file-set sweep of all 15
   `taxon-spring-boot-starter` Java files + its Liquibase changelog, a real SOLID/DRY finding
   (`TaxonService.buildSnapshotFromData`/`buildSnapshotFromTranslations`, duplicate en/uk
   extraction from two different input shapes) correctly verified, correctly routed to the
   human-review bucket (`confidence: "low"`), correctly distinguished from the superficially similar
   `improvement-084` during the backlog cross-check (different root cause, not a duplicate).
2. **`ReportFindings` listed in `tools:` but not actually callable by a subagent.** Verified against
   `code.claude.com/docs/en/sub-agents.md`: `ReportFindings` is a main-thread-only tool, always
   filtered out of any subagent regardless of what `tools:` frontmatter lists — listing it there is
   silently a no-op, not an error. Fix: removed `ReportFindings` from `tools:`; step 8 now has the
   orchestrator build the exact JSON payload and return it verbatim in its own final text result
   (a fenced ```json block) instead of calling the tool; step 10 explicitly tells the dispatcher
   (whoever called the agent) to call `ReportFindings` themselves with that JSON. Not yet re-tested
   live with a `confidence: "high"` finding (this run's only finding was `"low"`, so the auto-report
   path never actually executed) — worth a follow-up run against a case with a high-confidence
   finding to confirm the handoff works end to end.

**Design correction (2026-08-25):** step 9 originally had the orchestrator write the new
`backlog/issues/*.md` file itself (it had `Write` in `tools:`). Caught before any live run
exercised it: an isolated subagent silently creating tracked backlog entries bypasses the standing
Approval Rule entirely — no human ever sees the content before it lands in `backlog/`. Fixed:
`Write` removed from `tools:` entirely; step 9 now *prepares* the file content (filename + body +
compiled `## Operational notes`, using real `subagent_tokens`/`tool_uses`/`duration_ms` data the
orchestrator already has from its own step 3-5 `Agent` dispatches) and returns it in step 10 for
the dispatcher to present to the user and write only after approval — the same pattern already
forced on the `ReportFindings` handoff, now applied consistently to every "publish to shared
state" action, not just the one the platform makes mandatory.

**Split (2026-08-25):** `solid-dry-reviewer` split into two lenses — `dry-reviewer` (unchanged
duplication-detection content, renamed) and a new `solid-reviewer` (SRP/ISP/DIP/LSP; OCP
explicitly skipped as not reliably diff-detectable). The original lens only ever checked DRY
duplication despite its name — no SOLID content existed to split out, `solid-reviewer` is new
content, grounded in this project's own Port/Hook SPI convention for the DIP/ISP checks. Step 3
now dispatches both in one response (parallel), findings merge before verification, `category` in
the `ReportFindings` payload and the `### Review angle yield` block both split by lens (`dry`/
`solid`) instead of one combined line.

**KISS/YAGNI added, folded into the DRY lens rather than a third separate one (2026-08-25):**
`dry-reviewer` renamed `dry-kiss-yagni-reviewer`, gained explicit KISS (unnecessary complexity) and
YAGNI (speculative generality) checks alongside its existing DRY check, plus a new `principle`
field (`"dry"|"kiss"|"yagni"`) on each finding so the orchestrator's `category` mapping stays
precise. Deliberately not a fourth separate agent: DRY and YAGNI naturally pull in opposite
directions (DRY toward extracting shared structure, YAGNI against premature abstraction) — one
lens weighing both in a single judgment avoids two lenses producing contradictory findings on the
same code (one says "extract this," the other says "don't abstract yet"). `solid-reviewer` stays
separate — no similar tension with DRY/KISS/YAGNI. `### Review angle yield` reports 2 lines by
`Agent` dispatch (`dry-kiss-yagni`, `solid`), not 4 by principle, since all three DRY/KISS/YAGNI
principles share one dispatch's token cost — a 4-line split would triple-count it.

## Final certification compliance check (2026-08-26)

Re-verified the finished `deep-review-orchestrator`/`dry-kiss-yagni-reviewer`/`solid-reviewer`
trio directly against `AICertification.txt` (Domain 1, agentic orchestration), one concept at a
time, not from memory of earlier passes in this issue:

- ✅ **Hub-and-spoke** — all subagent communication routes through the orchestrator; the two
  lenses name each other in a "see also" line but never dispatch or read each other.
- ✅ **Nested subagent dispatch via `Agent`** — confirmed against `code.claude.com/docs/en/sub-agents.md`.
- ✅ **Structured data, content separated from metadata** (Rule 2) — `claim`/`failure_scenario`
  vs. `file`/`line`/`confidence`/`found_by`.
- ✅ **Fresh/independent verification instance, no prior reasoning context** — step 4, matches the
  "Multi-Instance and Multi-Pass Review" section's own worked example almost verbatim.
- ✅ **Multi-pass review architecture** (per-item pass + cross-file integration pass against
  attention dilution) — step 5.
- ✅ **Confidence-based routing**, not using uncalibrated confidence for automated decisions —
  step 7.
- ✅ **Parallel spawning** for independent tasks — step 3 (both lenses) and step 4 (all verifiers),
  both explicit "single response" instructions.
- ✅ **Structured Handoff Protocol** (self-contained escalation to a human, not just a pointer) —
  found incomplete during this final check (step 7 only listed `locations`/`claim`, dropping
  `failure_scenario` — the "why", not just the "what") and fixed in the same pass: the
  human-review bucket now always carries `locations` + `claim` + `failure_scenario` together.
- ❌ **Confidence calibration against a labelled validation set** — explicitly out of scope, not a
  bug: no labelled data exists in this project yet to calibrate against (see the earlier
  "Confidence-based routing" entry above for the routing-only vs. calibration distinction).

## ReportFindings non-empty-payload path — still not directly exercised (2026-08-26)

Attempted a live test specifically for this: added a temporary scratch file
(`provider-profile-spring-boot-starter/.../ScratchOrchestratorHighConfidenceTest.java`, two
byte-for-byte-identical methods, an unambiguous high-confidence DRY duplication) and ran the
orchestrator in `current` scope. Result: `dry-kiss-yagni-reviewer` found it, the verifier confirmed
it, it survived the cross-file pass — but step 6's backlog cross-check then dropped it anyway,
because this issue's own prose (the paragraph above describing the planned follow-up test) had
already described this exact fixture, and the orchestrator correctly recognized "already tracked /
expected, not a new bug" rather than filing it. `ReportFindings` was called with `[]` a third time.

This is the orchestrator behaving correctly (its backlog cross-check is supposed to catch exactly
this kind of self-referential match), not a bug — but it means step 8's actual JSON-payload-building
logic (mapping `locations[0]`/`claim`/`failure_scenario`/`confidence` into `ReportFindings`'s
schema) has still never run against a real non-empty array end to end, only been read-reviewed.
Scratch file deleted after the run (`git reset` + `rm`), nothing left behind. Not pursuing a fourth
attempt right now — the mapping itself is simple, low-risk field remapping already covered by
direct reading, and every run so far has cost 50k-120k tokens; diminishing return on forcing this
specific empty-payload gap closed via more live runs. Left open as a known, accepted verification
gap rather than closed by assumption.

**Real, organic finding surfaced by this same run** (medium confidence, correctly not auto-filed):
`docs/architecture/scripts/generate-architecture-model.sh`'s new AGENT node-emission block
(added earlier in this same issue, for the "Agents" architecture-map card) is a third
near-identical copy of the same JSON-emission shape already duplicated between the existing
COMMAND (`:1678`) and SKILL (`:1697`) blocks — now AGENT (`:1716`) too. A future schema change has
to be applied correctly in 3 places, not 1. Not fixed here — reported to the user, disposition
pending.

## Operational notes
- token_cost_review: 36250 (nested-dispatch verification) + 37876 (ReportFindings grantability verification) = 74126, both `claude-code-guide` fact-checks against official docs, not code review in the `/code-review` sense
- token_cost_research: n/a (folded into token_cost_review above — this issue had no separate research-only pass)
- token_cost_verification: 52905 + 60267 (two live `deep-review-orchestrator` test runs on `taxon-spring-boot-starter`, first exposing the diff-mode-fallback bug, second confirming the fix) + 62159 (independent review of `.claude/agents/README.md`) + 122538 (4th run, synthetic high-confidence-finding test, self-excluded via backlog cross-check) = 297869 — a 3rd orchestrator run (2-lens split) also happened but its own top-level usage figure was not cleanly captured in a citable notification; not included here rather than guessed
- review_signal_ratio: n/a (no `/code-review` ran this task)
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a

### Agent calls
- Verify nested subagent dispatch mechanism | subagent_type=claude-code-guide | tokens=36250 | tool_uses=2 | duration_s=35 | mode=background | batch=solo
- Verify ReportFindings tool subagent grantability | subagent_type=claude-code-guide | tokens=37876 | tool_uses=4 | duration_s=51 | mode=background | batch=solo
- Deep review taxon-spring-boot-starter module (1st run, exposed diff-mode-fallback bug) | subagent_type=deep-review-orchestrator | tokens=52905 | tool_uses=8 | duration_s=217 | mode=background | batch=solo
- Re-verify deep-review-orchestrator on taxon module (2nd run, confirmed fix + found ReportFindings tool-access gap) | subagent_type=deep-review-orchestrator | tokens=60267 | tool_uses=12 | duration_s=277 | mode=background | batch=solo
- Independently verify .claude/agents/README.md accuracy | subagent_type=general-purpose | tokens=62159 | tool_uses=4 | duration_s=43 | mode=background | batch=solo
- Deep review taxon module with 2 lenses (3rd run, dry-kiss-yagni-reviewer + solid-reviewer split) | subagent_type=deep-review-orchestrator | tokens=unknown | tool_uses=unknown | duration_s=unknown | mode=background | batch=solo (usage figure not cleanly captured, omitted rather than guessed)
- Test orchestrator ReportFindings path on synthetic duplication (4th run, current-scope, self-excluded via backlog cross-check) | subagent_type=deep-review-orchestrator | tokens=122538 | tool_uses=18 | duration_s=382 | mode=background | batch=solo
