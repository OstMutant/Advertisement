# improvement-206: Cost optimization for `/review` and `/sync-docs` fan-out subagent dispatches

**Type:** improvement — tooling/cost optimization for the `/review` and `/sync-docs --full-audit`
pipelines
**Module:** `.claude/agents/review/deep-review-orchestrator.md`, `.claude/commands/review.md`,
`.claude/commands/sync-docs.md`
**Priority:** 🟡 Top — user-requested Top-of-backlog placement, 2026-10-05
**When:** independent, no blockers

## Current state

`deep-review-orchestrator.md`'s step 3 (dispatch `dry-kiss-yagni-reviewer`/`solid-reviewer`/
`precedent-reviewer` to find candidates) and step 4 (dispatch one fresh verification subagent per
candidate, to open the real file and confirm the finding actually holds) both run on
`model: inherit` — whatever model the top-level session happens to be using (currently Sonnet 5,
$3/$15 per MTok after the 2026-08-31 intro-pricing window closed; Haiku 4.5 is $1/$5 — a flat 3x
ratio on both input and output today).

Real historical token costs, pulled directly from this repo's own `## Operational notes` blocks
(not estimated): a single step-3 finder-lens dispatch has cost ~40k-170k tokens per run
(`improvement-111`, `improvement-114`, `improvement-182`, `improvement-184`); a single step-4
verification dispatch — open a file, confirm one finding, return a JSON verdict — has cost
~35k-62k tokens per run (`improvement-202`'s solid/precedent verifiers, the 7 verify-step agents
in `improvement-193`'s larger review session). A full orchestrator run end-to-end has ranged from
~97k tokens (small diff) to several hundred thousand (larger scope/multiple rounds).

## Why change

Step 4's job — "open the file at this exact location, confirm the claim is literally there,
return `confirmed`/`rejected`" — is a fully-specified, bounded, mechanical verification task with
no open design decisions, exactly the shape `.claude/rules.md`'s own standing rule already
reserves for Haiku-model dispatch ("Well-specified mechanical implementation work defaults to a
Haiku-model agent"). It is also the most repeated part of any `/review` run — one dispatch per
surviving candidate, in waves of up to 3 — so it's where a cheaper model pays off across the most
calls, unlike step 3 (3 fixed dispatches per run, and judgment-heavy: distinguishing a real SOLID/
DRY violation from noise is not mechanical, so it must stay on the inherited/Sonnet-tier model).

Separately, a real question was raised and not yet answered: when step 4 dispatches multiple
verifier subagents in one wave, does Anthropic's prompt cache (a server-side, byte-prefix-keyed
mechanism, independent of which "conversation" sent the request) ever actually get shared across
those sibling dispatches or between the orchestrator and its children? If two findings happen to
land in the same file, each verifier today re-`Read`s that file from scratch in its own, separate,
cold context — paying the full file-token cost twice with no reuse, since the file content enters
each verifier's prompt dynamically (via its own tool call), not as a static, cacheable prefix set
up in advance. This is a distinct optimization axis from "cheaper model" and must not be assumed
to already work for free.

## Expected benefit

- **Guaranteed, price-ratio-exact: ~67% cost cut on step 4's own token spend** (Haiku is a flat
  3x cheaper than Sonnet on *both* input and output today, so the blended savings is exactly
  66.7% regardless of each dispatch's actual input/output mix — not an estimate contingent on
  file size). Applied to real historical per-dispatch costs (~35k-62k tokens each, step 4 section
  above): a typical run with 3-6 step-4 dispatches (~150k-300k tokens total for that step) drops
  from roughly $0.45-$1.35 to $0.15-$0.45 at today's Sonnet/Haiku pricing — a modest per-run dollar
  amount, but step 4 is the most-repeated part of every run, so it compounds across however many
  `/review` runs this project does over time. Step 3's finding quality is untouched (stays on
  inherit) — this is a step-4-only cost change, not a review-quality change.
- **Variable, opportunistic on top of that: same-file fusion** (item 2 below) removes the
  redundant full-file-read cost whenever 2+ surviving candidates land in the same file. No fixed
  percentage claimed — zero gain on a run where every finding is in a different file, meaningful
  gain (up to ~(N-1)/N of that file's own read cost) on a run where several findings cluster in
  one file/class. This is a cap on the worst case, not a guaranteed per-run saving — real
  historical same-file-overlap frequency isn't tracked today, so item 5's validation runs should
  also note (informally) how often this actually triggers, to size it better after a few real
  runs instead of guessing further.
- **Variable, same shape: scoped reads** (item 3 below) cut input tokens for the common
  single-location-finding case (read ~60 lines instead of a whole file) — real savings scale with
  how much larger the average reviewed file is than 60 lines, which isn't tracked today either;
  item 5's validation runs should note this too. Deliberately *not* applied to multi-location or
  structural findings, so this never trades token cost for a missed real bug.
- A real, evidence-based answer (not assumption) on whether prompt caching ever helps across
  sibling/parent-child subagent dispatches in this harness (now resolved — it structurally
  doesn't, see item 1 above) — this answer is reusable for any future subagent-heavy command
  without re-investigating.
- **`/sync-docs --full-audit` (items 8-9 below): no fixed percentage claimed, same honesty rule as
  above** — real savings scale with how much of Step A2's claim-checking volume is actually
  ADR-id/README/entity/contract/dependency facts (the kind `architecture-model.json` already
  covers) versus Javadoc-prose/infra-header/structural-hygiene checks (the kind it doesn't) —
  item 10's next real run should note the real split instead of guessing one here.
- **Honest whole-pipeline number for `/review` (the question that actually matters — not the
  step-4-only 67%), 2026-10-05:** step 4 only fires on candidates that *survived* step 3, and real
  historical runs show this varies from **zero** (`improvement-202`: all three finder lenses
  returned 0 survivors, so step 4 never dispatched at all that run — nothing to optimize) up to
  several verifiers in a busy run. Step 3 (3 fixed dispatches, untouched by this task,
  ~176k-312k tokens combined per real historical runs — `improvement-114`/`184`/`182`) is the
  **floor cost of every single `/review` run regardless of this change**. Combining the two real
  effects (Haiku's flat 67% price cut, plus whatever token-volume drop items 2-3 produce on top):
  **realistic expectation is roughly 10-30% off a typical/busy run's total dollar cost, closer to
  0% on a clean run that finds nothing to verify** — not 67% of the whole bill. State this range,
  not the step-4-only figure, whenever reporting this task's actual payoff; the 67% number is real
  but answers a narrower question than "how much cheaper is `/review` overall."

## Approach

1. ✅ **Cache-sharing investigated (2026-10-05) — resolved, not a viable lever.** Confirmed via
   direct research against Claude Code's own documentation and Anthropic's prompt-caching docs:
   - Parallel sibling dispatches (step 4's waves of up to 3) **structurally cannot** share a cache
     entry with each other, regardless of identical prefix content — Anthropic's own docs state a
     cache entry only becomes readable once the *first* response begins streaming, and truly
     parallel requests are all already in flight before that happens, so none can read what
     another is still writing.
   - `fork` subagents cache-share for a *different* reason (they literally resend the parent's
     full conversation history as their own prefix — a continuation, not a coincidentally-matching
     prefix); a plain named-agent `Agent` dispatch (what step 4 uses) starts a genuinely fresh
     conversation with zero shared prefix with its parent or siblings.
   - The dispatching coordinator cannot observe `cache_read_input_tokens`/`cache_creation_input_tokens`
     for a subagent's own call at all — task-completion notifications only expose
     `subagent_tokens`/`tool_uses`/`duration_ms`.
   - Checked two further "recipes" before settling: Anthropic's documented **staggered fan-out**
     (send 1 request, wait for its first streamed token, then fire the rest — this *would* let
     sibling 2/3 read sibling 1's cache) is real but **not implementable through Claude Code's
     subagent/Task abstraction** — the orchestrator only ever sees a subagent's *final* result, no
     hook into its internal token stream to detect "first token arrived." The **Message Batches
     API** (~50% discount) is a separate raw-API surface not exposed through the Task-tool
     mechanism at all, and its latency (most batches ~1h, cap 24h) is wrong for interactive
     `/review` regardless — not worth a harness rewrite for either command.
   - **Conclusion: pre-reading a shared file once and inlining it is NOT the fix.** Inlining would
     only *enlarge* every parallel request with no offsetting cache benefit, since none of them can
     read a cache regardless of content. Dropped as a lever entirely.
2. **Same-file fusion instead (new, 2026-10-05 research finding, real recipe worth adding):**
   when 2+ surviving step-4 candidates land in the *same file*, dispatch **one** verifier covering
   all of them (list every `claim`/`locations` pair for that file in one prompt, ask for one
   `confirmed`/`rejected` verdict per claim back) instead of N separate verifiers each re-opening
   that file from scratch. This is a real, mechanical dedup of the actual redundant cost (the
   repeated full-file read), independent of caching mechanics entirely — it works the same whether
   dispatches are parallel or sequential. Group step 4's wave-building in
   `deep-review-orchestrator.md` by target file before dispatching.
   `/sync-docs --full-audit`'s own Step A3 already does a coarser version of this (grouping files
   into one agent per ~500-1000 lines of content) — no change needed there.
3. **Scoped reads instead of whole-file reads, conditionally (new, 2026-10-05 research finding):**
   confirmed as an established axis for coding-review agents (not a novelty), but with a real,
   documented downside — narrow context windows are a known cause of LLM code-review false
   negatives when the judgment actually depends on surrounding context (e.g. whether input is
   sanitized elsewhere, whether a path is canonicalized elsewhere). **Apply it conditionally, not
   universally**: in step 4's verifier prompt, when a candidate's `locations` array has exactly
   one entry and the claim is self-contained at that line (the common case for one-line SRP/DIP
   findings), instruct the verifier to read `Read`'s `offset`/`limit` for ±30 lines around that
   line instead of the whole file. When `locations` has 2+ entries (already documented in
   `solid-reviewer.md`/`dry-kiss-yagni-reviewer.md` as "spans multiple spots") or the claim
   concerns the file's overall structure/organization, keep today's whole-file read — the risk of
   a missed false negative there outweighs the token savings. Checked and ruled out as not
   applicable: codebase/embeddings indexing (Cursor/Cody-style — solves "where is the relevant
   code when I don't know," but step 4 already has the exact file+line from step 3, so there's
   nothing for an index to find); `strict: true`/structured-output tightening for the verifier's
   own tiny ~15-30 token verdict JSON (no measurable savings on an already-small output).
4. **Apply `model: "haiku"`** to step 4's verification `Agent` dispatches in
   `deep-review-orchestrator.md` only — step 3's three finder-lens dispatches stay on inherit. This
   is the standard, independently-confirmed pattern for this exact shape of problem (Claude Code's
   own built-in `Explore` subagent already runs on Haiku for the same reason — classify by
   complexity, route mechanical sub-tasks to the cheap model, keep judgment-heavy work on the
   capable one) — not a novel idea, just applying it here.
5. **Validate before trusting it as the new default**: run `/review` against 2-3 real diffs
   before/after the change and compare `review_signal_ratio` (already instrumented via this
   project's own `## Operational notes` convention) — per `.claude/rules.md`'s own rule that a
   review-effort-level/model change needs real evidence, not a hunch, before it becomes the
   default. Also informally note how often step 2's same-file fusion and step 3's single-location
   scoped-read condition actually trigger on real diffs, to size their real-world impact instead
   of the worst-case bound stated in "Expected benefit" above.
6. **Visibility while this rolls out** (user-requested, 2026-10-05): once live, every `/review`
   final summary that actually ran step 4 under this change states so explicitly (e.g. "step-4
   verification ran on Haiku per improvement-206") — add this one line to
   `deep-review-orchestrator.md`'s own step 10 output instructions, so it's visible without
   digging into which model actually executed.
7. User will watch real runs directly rather than pre-approving a rollout date — no further
   check-in needed before starting steps 2-4 (same-file fusion, scoped reads, the model-flag
   edit); step 5's validation runs are the actual gate before calling this "done".

### `/sync-docs --full-audit` — a different, also-real lever (new, 2026-10-05 research finding)

The user asked whether some form of pre-built index ("RAG file") could help the `/sync-docs
--full-audit` side specifically. Research confirmed this repo **already has exactly that kind of
artifact**, just not yet pointed at by the audit: `docs/architecture/data/architecture-model.json`
(generated by `bash docs/architecture/scripts/generate-architecture-model.sh`, ~720KB). Verified
directly (not assumed) — each module's node already carries its full current `readme` text, every
`intent` (ADR id+title+file), and `entities`/`keyServices`/`contracts`/`tables` each with a real
`file` path and a real `description`, all pre-extracted from actual source. This matches a named,
current pattern — "Repository Intelligence Graph" (a deterministic architectural map built once
and injected into agent context, instead of each agent independently directory-walking/grepping).
Same conclusion as the indexing angle already ruled out for `/review` step 4: calling this "RAG"
would mis-describe it — it's structural fact-lookup, not semantic search — but the underlying
instinct (reuse a pre-computed source of truth instead of re-deriving the same facts N times) is
sound and this repo already has the artifact for it.

**Independently re-verified (2026-10-05, skeptical second pass — opened the cited papers directly
rather than trusting the first pass's summary):** the RIG paper (`arxiv.org/abs/2601.10112`) is
real and its reported numbers are real (+12.2% agent accuracy, -53.9% completion time across
Claude Code/Cursor/Codex on 8 repos) — but its own scope is build/test/dependency structure
(CMake/CTest-derived), not documentation-accuracy facts, and its abstract never actually contrasts
itself against embeddings-RAG the way the first pass implied — treat it as **analogous** support,
not a precise match for this exact use case. The broader claim holds up better than that one
citation alone, though: independent corroboration exists across CodeGraph, Codebase-Memory,
Aider's repo-map, Coograph, KotaDB, and MCP-based code-graph tooling, which all separately converge
on the same idea — this is a real, broad pattern, not one paper's proposal.

**One honestly-reported gap, no mitigation found:** no published technique addresses the circular
risk that a "freshly regenerated" index can still be *wrong* if the generator script itself has a
latent bug — freshness (by timestamp) and correctness are different properties, and nothing in the
literature verifies the latter. Only the generic fallback applies: occasionally spot-check a
handful of the index's own claims against raw source, rather than trusting it unconditionally
forever. Not a reason to drop items 8-9, but a real limitation worth stating rather than glossing
over.

8. **New Step A0.5 in `.claude/commands/sync-docs.md`'s Full Audit Mode:** run
   `bash docs/architecture/scripts/generate-architecture-model.sh` as the very first step (before
   A1's enumeration), so every later step reads a freshly-regenerated index, not a possibly-stale
   one left over from whenever it last ran — regenerate-before-rely-on is the documented norm for
   this class of derived index. **Refinement worth noting, not adopting yet:** blind full
   regeneration every run is the simple approach, not the most sophisticated one found — a
   content-hash/git-diff-triggered incremental regeneration pattern (re-derive only what actually
   changed, confirmed elsewhere to run ~4x faster than full re-indexing) is better-documented for
   large repos. Not worth building now — `generate-architecture-model.sh`'s current full-regen
   cost is cheap relative to a full audit's own runtime — but flag as the next step if/when
   regeneration itself becomes the bottleneck.
9. **Step A2's per-target checklist gains a first-pass source:** for ADR id/title existence,
   README content, and entity/key-service/contract/table existence-and-description claims, check
   `architecture-model.json`'s matching module node **first**; fall back to direct grep/`Read`
   only for what the JSON doesn't cover. Explicitly does **not** help with (keep doing these
   exactly as today, raw-file-based): Javadoc prose-accuracy judgment (verifying a method's
   Javadoc still describes its actual body — not in the JSON's schema at all), infra/tooling file
   header checks (scripts/config files aren't in this JSON), or ADR structural-hygiene checks
   (numbering gaps, `Status:` vocabulary consistency — a cross-entry check, not a per-fact lookup).
   Step A3's existing per-agent file-size grouping is unaffected — this only changes *what each
   agent checks against first*, not how agents are split.
10. No separate validation round planned for this specific pair of items beyond the next real
    `--full-audit` run noting, informally, how much of Step A2's claim-checking the JSON actually
    resolved vs. how much still fell back to raw grep/Read — same "note it, don't guess" discipline
    as item 5 above, just for this command instead.

## Related

- `.claude/rules.md` — "Well-specified mechanical implementation work defaults to a Haiku-model
  agent" (the rule this task applies to step 4) and "Review-skill effort level — default stays
  put..." (the rule governing step 3's validation requirement).
- `.claude/agents/review/deep-review-orchestrator.md` / `solid-reviewer.md` /
  `dry-kiss-yagni-reviewer.md` / `precedent-reviewer.md` — the files this task edits.
- `.claude/commands/sync-docs.md` Step A3 — a structurally similar parallel-research-agent shape;
  the cache-sharing answer from item 1 above applies there too (confirmed, no change needed on
  that specific axis), but items 8-9 above now bring `/sync-docs --full-audit` properly into this
  task's own scope via a different, real lever (the pre-existing `architecture-model.json` index).
- `docs/architecture/scripts/generate-architecture-model.sh` /
  `docs/architecture/data/architecture-model.json` — the pre-existing generator and its output this
  task's items 8-9 point `/sync-docs --full-audit` at.
