# improvement-168: AI guidance refactor — memory duplication vs canonical `.claude/rules`, path-scoped rules migration

**Type:** improvement — AI tooling/process infra, design pending
**Module:** `.claude/rules.md`, `CLAUDE.md` (root + all per-module), auto-memory system
  (`/root/.claude/projects/-app/memory/`)
**Priority:** high (Top)
**When:** independent, no blockers — scope not yet decided, see "Open decision" below

## Problem

Two related "mission" prompts proposed consolidating this project's AI-guidance surfaces
(`CLAUDE.md` files, `.claude/rules.md`, and the persistent memory system) into a canonical,
path-scoped `.claude/rules/*.md` structure, plus a "Rule Persistence System" (explicit `@`
anchoring, a re-read-before-action guardrail, a `session_manifest.md`, path-scoped rule loading)
to fight context degradation in long sessions.

Investigated against the real project/tooling state before deciding scope:

**Verified true (via a `claude-code-guide` agent check against official Claude Code docs):**
- `.claude/rules/*.md` with `paths:` YAML frontmatter **is a real, documented feature** — a rule
  file loads into context only when Claude reads a file matching its glob pattern. This is
  different from, and more selective than, this repo's current single `@.claude/rules.md` import
  (which unconditionally loads the full 654-line file on every session, and per this repo's own
  rules.md is manually re-read in full on every tool call).
- Root `CLAUDE.md` genuinely survives `/compact` — it's re-read fresh from disk and re-injected,
  not merely "hoped to persist."
- `@path` imports are unconditional full-file loads — they do **not** themselves provide any
  path-scoping; only the separate `.claude/rules/` + `paths:` mechanism does.

**Verified false / found factually wrong in the mission as written:**
- The mission's proposed migration source, `private/claude/memory/`, is a **stale, gitignored,
  non-canonical mirror** (`/private/` in `.gitignore`, untracked by git) — already diverged from
  the real, live memory the harness actually loads
  (`/root/.claude/projects/-app/memory/`, outside the repo). A migration run against the stated
  path would rewrite/delete an unused copy while leaving the real memory (and its real
  duplication with `CLAUDE.md`/`rules.md`) untouched — cosmetic, not effective.

**Real, demonstrated (not assumed) duplication**, spot-checked directly: several
`private/claude/memory/feedback_*.md` entries substantially restate rules already present near-
verbatim in `.claude/rules.md`/`CLAUDE.md` — confirmed for `feedback_approval_required`,
`feedback_commits_require_permission`, `feedback_short_code_comments`,
`feedback_module_independence`, `feedback_no_workarounds`, `feedback_decisions_log`. Not every
memory entry is duplicate, though — some are genuinely personal/session-scoped facts
(`user_devops_interest.md`, `feedback_language_ukrainian.md` — chat-language preference, not a
repo rule) that don't obviously belong in a checked-in, path-scoped rules directory applicable to
any contributor.

**The "Rule Persistence System" add-on mostly restates what already exists**, rather than adding
new capability:
- "RE-READ RULES BEFORE ACTION" is already the literal first paragraph of `.claude/rules.md`.
- "`@`-import critical rule files into root `CLAUDE.md`" is already done
  (`CLAUDE.md` line 5: `@.claude/rules.md`).
- Its one genuinely new and verified-real idea — path-scoped rule loading — is the same mechanism
  already covered above.
- `session_manifest.md` (a durable scratch of "active rules"/"discussed constraints") has real
  incremental value but substantially overlaps with the Approval Rule's existing requirement to
  write the current plan into the relevant `backlog/issues/<n>.md` file before presenting it.

## Suggested fix

**Open decision — not yet made.** Filed to capture the analysis and keep the idea visible; scope
has not been chosen. Candidates identified so far, narrowest to broadest:

1. **Narrowest, lowest-risk:** split `.claude/rules.md` (654 lines, currently reloaded in full on
   every tool call per its own rule) into `.claude/rules/*.md` with `paths:` frontmatter — no
   touch to the memory system at all. Reduces reload noise; the actual current behavior (module
   `CLAUDE.md` files already loading in full at session start in observed practice, which the
   official docs describe as on-demand-per-directory rather than eager) should be empirically
   re-confirmed before assuming path-scoping will behave as documented in this specific harness
   setup.
2. **Full original scope, corrected:** memory→rules consolidation using the *real* memory path
   (`/root/.claude/projects/-app/memory/`, not the stale `private/claude/memory/` mirror), with an
   explicit up-front split between genuinely repo-wide `ACTIVE_RULE` content (→ `.claude/rules/`)
   and personal/session-scoped `PROJECT_FACT`/preference content (→ stays in the memory system,
   not merged into checked-in rules) — the Migration Map classification step the original mission
   already proposed, just pointed at the right source and with this repo-vs-personal boundary made
   explicit rather than assumed away.
3. **Do nothing:** current `.claude/rules.md` + auto-memory duplication is real but not
   demonstrated to have caused an actual failure yet — status quo is a valid choice.

## Related

- `.claude/rules.md` "RE-READ ALL RULES BEFORE EVERY ACTION" — the existing mechanism the
  "Rule Persistence" add-on largely restates.
- `.claude/rules.md` Approval Rule's "write the plan into the issue file first" — overlaps with
  the proposed `session_manifest.md` idea.
- `backlog/issues/improvement-167-dag-aware-agent-friendly-script-execution-contract.md` — same
  pattern of a large speculative-infrastructure mission investigated against real project state
  before committing to a scope, filed the same session.

## Scope decision (2026-08-25)

User picked **candidate 2 (full consolidation)**, expanded with a second goal, run in two
sequential phases — revision first, migration only after the revision's findings are reviewed
and a migration plan is separately approved.

**Goal 1 — remove duplicates** between the auto-memory system
(`/root/.claude/projects/-app/memory/`) and the canonical `.claude/rules.md` / `CLAUDE.md` files
(root + every module).

**Goal 2 (tentative — confirm via evidence in the revision phase)** — investigate whether the
per-module `CLAUDE.md` files, currently pulled in via unconditional `@path` imports from root
`CLAUDE.md`, are worth converting to the path-scoped `.claude/rules/*.md` (`paths:` frontmatter)
mechanism instead, to stop loading every module's full guidance on every session regardless of
which module the task actually touches.

**Goal 3 — maximum guarantee that rules actually load.** Whatever the final loading mechanism
(eager `@import`, path-scoped `.claude/rules/*.md`, or a mix), the revision phase must establish
how reliable each mechanism actually is in this harness before Phase 2 picks one for any given
rule — not assume the documented behavior holds. Concretely: does a `paths:` glob ever fail to
match a file that should match it; does a subagent (`Agent` tool call) inherit the same root
`CLAUDE.md`/`@import` content the main thread gets, or does it start blind; is there a case where
moving a rule out of eager `@import` into path-scoped loading measurably lowers the chance it's
in context when it matters (e.g. the rule matters for a decision that doesn't touch a matching
file path at all, like the Approval Rule itself). Rules whose violation is highest-cost (Approval
Rule, commit-permission rule, the re-read-before-action rule itself) are candidates to stay
eager-loaded regardless of Goal 2's footprint argument — Phase 1 must surface this tension
explicitly, not resolve it by default toward the smaller footprint.

### Phase 1 — Revision (audit only, no file changes to rules/memory/CLAUDE.md)

1. **Confirm the eager-load behavior directly from this session's own evidence.** This
   conversation's opening system-reminder already dumped the full contents of root `CLAUDE.md`
   plus all 13 `@`-imported files (`.claude/rules.md` and every module `CLAUDE.md`) before any
   file was touched and before any module-specific task was stated — direct proof that `@path`
   imports in root `CLAUDE.md` are unconditional/eager, not path-scoped, matching what the
   original investigation already established for `@.claude/rules.md` specifically. Record the
   measured token/line footprint this costs every session (sum of all `@`-imported files' line
   counts).
2. **Catalog every memory file** in `/root/.claude/projects/-app/memory/` (via its `MEMORY.md`
   index) against the full text of `.claude/rules.md` + every `CLAUDE.md` now confirmed loaded.
   Classify each into one of:
   - `DUPLICATE` — near-verbatim restates a rule already present in rules.md/CLAUDE.md.
   - `PARTIAL` — overlaps an existing rule but adds real, non-restated value (a "why", an
     incident detail, a nuance the canonical rule doesn't carry).
   - `UNIQUE_REPO_RULE` — a genuine repo-wide rule with no canonical-file counterpart at all.
   - `PERSONAL_OR_SESSION` — not a repo rule (chat-language preference, user-role facts,
     project-status facts) — stays in memory regardless of migration scope.
3. **Test loading-reliability questions raised by Goal 3** directly, not from documentation alone:
   whether an `Agent`-tool subagent call receives root `CLAUDE.md`'s `@import` content at all
   (spawn a minimal agent and check); whether `docs/ai/context-loading.md` or any other repo
   evidence already documents path-scoped-loading gaps; what the practical failure mode looks like
   if a `paths:` glob doesn't match a touched file (rule silently absent vs. some fallback).
4. **Produce a revision report** (classification table, one row per memory file; the eager-load
   footprint numbers from step 1; the Goal 3 reliability findings from step 3) and present it
   before any migration plan is drafted or any file is touched.

### Phase 2 — Migration (separate plan, separate approval, only after Phase 1 is reviewed)

Concrete plan below, drafted after Phase 1. **Not yet approved to execute — waiting for explicit
go-ahead.**

## Phase 1 — Revision report (completed 2026-08-25)

### Step 1 — Eager-load footprint

Root `CLAUDE.md` + its 13 `@`-imports (`.claude/rules.md` + 12 module `CLAUDE.md`) measured
directly (`wc -l`/`wc -c`):

| File | Lines |
|---|---|
| `CLAUDE.md` (root) | 193 |
| `.claude/rules.md` | 654 |
| `scripts/CLAUDE.md` | 312 |
| `marketplace-orchestrator/CLAUDE.md` | 193 |
| `integration-tests/CLAUDE.md` | 174 |
| `marketplace-app/CLAUDE.md` | 141 |
| `platform-commons/CLAUDE.md` | 130 |
| `playwright/CLAUDE.md` | 106 |
| `user-spring-boot-starter/CLAUDE.md` | 76 |
| `provider-profile-spring-boot-starter/CLAUDE.md` | 66 |
| `advertisement-spring-boot-starter/CLAUDE.md` | 63 |
| `taxon-spring-boot-starter/CLAUDE.md` | 61 |
| `query-lib/CLAUDE.md` | 52 |
| `attachment-spring-boot-starter/CLAUDE.md` | 43 |
| `audit-spring-boot-starter/CLAUDE.md` | 29 |
| **Total** | **2293 lines / 155,418 bytes (~39K tokens at ~4 bytes/token)** |

Plus `MEMORY.md` (the memory index, also eager-loaded): 56 lines / 12,421 bytes. Grand total
eager-loaded baseline every session, before any file is touched: **~2349 lines / ~168KB**.

**Confirmed directly, not assumed:** this conversation's own opening system-reminder dumped every
one of these 14 files in full before any module-specific task was stated — direct proof the
`@path` chain is unconditional/eager for the whole set, not just `.claude/rules.md`.

**Additional finding beyond the original question:** reading any file inside a module directory
(e.g. `scripts/`) re-injects that module's `CLAUDE.md` a second time as a fresh system-reminder,
on top of the eager `@import` copy already in context from session start. The `@import` mechanism
and a separate per-directory-touch mechanism both fire independently — the module `CLAUDE.md`
content isn't merely eager, it's also duplicated into context again on every touch of that
directory, compounding the footprint rather than substituting for it.

### Step 3 — Goal 3 reliability findings (tested empirically, not from docs)

1. **Subagents inherit the full eager-loaded set.** A minimal probe `Agent` call (zero tool uses,
   answering purely from its own initial context) confirmed it received: root `CLAUDE.md`, the
   first heading of `.claude/rules.md`, several module `CLAUDE.md` files by name and content, and
   `MEMORY.md`'s index content — all without being told any of it. **No "blind subagent" risk for
   anything in the eager-loaded set.**
2. **`.claude/rules/*.md` with `paths:` frontmatter is confirmed working in this exact harness**
   (not just per official docs) — verified live: created a temporary
   `.claude/rules/zz-probe-168.md` with `paths: ["**/*.probe168"]` and a marker line, created a
   dummy `*.probe168` file, `Read` it. Result:
   - When the dummy file lived **outside the repo** (`/tmp/.../scratchpad/`), the rule did **not**
     fire — path-scoping only activates for files inside the project tree.
   - When the dummy file lived **inside `/app`** (`scripts/`), the rule fired correctly — the
     marker line appeared as a fresh system-reminder alongside the `Read` result, in the same
     turn that also re-injected `scripts/CLAUDE.md` (the Step 1 duplication finding above).
   - Both probe files were deleted immediately after the test; `git status --short` confirmed no
     residue was left in the working tree.
3. **No existing repo evidence of path-scoped-loading gaps** — `docs/ai/context-loading.md` has
   no mention of path-scoping, on-demand loading, or `.claude/rules/` at all (checked directly,
   zero matches); there is no pre-existing `.claude/rules/` directory in this repo today.
4. **The Goal-3 tension is real, not hypothetical.** Given (1) and (2): a rule moved out of eager
   `@import` into a `paths:`-scoped file will *not* be visible for a decision that never touches a
   matching file path — e.g. the Approval Rule itself is not gated on any file type, it applies to
   the act of deciding whether to proceed, so it has no natural `paths:` glob. Rules with that
   shape (Approval Rule, commit-permission rule, the re-read-before-action rule) must stay
   eager-loaded regardless of Goal 2's footprint case; only rules whose relevance is genuinely
   tied to touching a specific file type/directory are real path-scoping candidates.

### Step 2 — Memory file classification (55 files, excl. `MEMORY.md` itself)

Legend: **DUP** = near-verbatim restates an existing canonical rule · **PARTIAL** = overlaps a
canonical rule but adds a real, non-restated nuance (a "why", an incident, an edge case) ·
**UNIQUE** = genuine repo-wide rule, no canonical counterpart today · **PERSONAL** = not a repo
rule, stays in memory regardless of scope · **STALE** = references paths/scripts/class names that
no longer exist or contradicts current canonical content — a data-hygiene issue independent of
the duplication question.

| Memory file | Class | Note |
|---|---|---|
| `feedback_always_build.md` | DUP + STALE | duplicates CLAUDE.md's "Scripts" section; cites dead `scripts/deploy.sh`/`deploy-dev.sh` |
| `feedback_always_run_smoke_tests.md` | DUP + STALE | duplicates rules.md's "Test Coverage After Bug Fixes"; cites a `/app/playwright/screenshots/` dir that playwright/CLAUDE.md says no longer exists |
| `feedback_always_use_scripts.md` | DUP + STALE | duplicates CLAUDE.md's "Scripts" section; cites dead `deploy-dev.sh`/`unit-tests.sh`/`integration-tests.sh` names |
| `feedback_always_ux.md` | UNIQUE | "always pass `--ux`" is not stated as mandatory anywhere in playwright/CLAUDE.md |
| `feedback_ambiguous_yes_reask.md` | UNIQUE | no canonical counterpart |
| `feedback_answer_literal_scope_only.md` | UNIQUE | no canonical counterpart |
| `feedback_apply_full_standard_not_just_accuracy.md` | UNIQUE | no canonical counterpart |
| `feedback_approval_required.md` | PARTIAL | core two-layer/wait-for-explicit-yes rule duplicates rules.md's Approval Rule closely; several refinements (per-sub-change plan, "test passed" ≠ approval for lifecycle steps) are not in the canonical text |
| `feedback_ask_before_extra_actions.md` | DUP | fully subsumed by rules.md's Approval Rule + Git Workflow |
| `feedback_audit_diff_always_full_fields.md` | UNIQUE (domain fact, not a process rule) | describes `AuditableSnapshot.expandWithChanges()` behavior — candidate for a Javadoc/ADR note instead of a process rule |
| `feedback_before_after_how_format.md` | UNIQUE | no canonical counterpart |
| `feedback_best_practices.md` | PARTIAL | overlaps rules.md's "Code quality is highest-priority" but a distinct angle (no prototype shortcuts) |
| `feedback_commits_require_permission.md` | PARTIAL | core rule duplicates rules.md's commit-approval callout; the "two-call rule" (PreToolUse hook timing) is a real nuance not stated there |
| `feedback_contractor_client_tone.md` | UNIQUE | no canonical counterpart |
| `feedback_decisions_log.md` | DUP | duplicates CLAUDE.md's "Architectural Decisions Log" section (though less precise — doesn't mention the mandatory `/record-decision` command) |
| `feedback_decoupling_priority.md` | DUP concept + STALE detail | concept duplicates CLAUDE.md's SPI/Port-Hook pattern; cites the old `platform-contracts` package name and old class names (`AuditUserProvider`, `MediaChangeConsumer`, etc.) that no longer exist |
| `feedback_dont_auto_regenerate_for_backlog_edits.md` | UNIQUE | no canonical counterpart |
| `feedback_dont_chase_screenshot_when_json_already_answers.md` | UNIQUE | no canonical counterpart |
| `feedback_dont_create_issues_unprompted.md` | UNIQUE | no canonical counterpart |
| `feedback_dont_substitute_direct_action_instructions.md` | UNIQUE | no canonical counterpart |
| `feedback_git_workflow.md` | DUP | duplicates rules.md's Git Workflow section |
| `feedback_ground_proposals_in_real_data_offer_options.md` | UNIQUE | no canonical counterpart |
| `feedback_issue_log_no_approval.md` | UNIQUE | a real carve-out from the Approval Rule not stated in rules.md |
| `feedback_language.md` | DUP | duplicates rules.md's "Language" section verbatim |
| `feedback_language_ukrainian.md` | PERSONAL | chat-language preference |
| `feedback_module_independence.md` | DUP + STALE | duplicates CLAUDE.md's module-independence guidelines; cites the old `advertisement-app` module name |
| `feedback_monitor_wait_then_tail.md` | DUP | duplicates rules.md's "Scripts" Monitor section closely (this memory is now the canonical text's source) |
| `feedback_never_self_conclude_review_findings.md` | UNIQUE (unverified against `.claude/commands/autopilot.md`, not in current context) | possibly duplicates command-file content not loaded this session |
| `feedback_no_git.md` | **STALE + ORPHANED — CONTRADICTS current rules** | says "never run git add/commit"; current rules.md says the opposite (`git add` auto after every change). Not indexed in `MEMORY.md` at all — an orphan that could actively mislead if ever read directly |
| `feedback_no_primary_objectmapper.md` | UNIQUE | not stated anywhere in canonical files |
| `feedback_no_tail_on_streaming_commands.md` | STALE/SUPERSEDED | self-marked "СПРОСТОВАНА" in `MEMORY.md`, superseded by `feedback_monitor_wait_then_tail.md` |
| `feedback_no_unsolicited_next_steps.md` | UNIQUE | no canonical counterpart |
| `feedback_no_unsolicited_recommendations.md` | UNIQUE | no canonical counterpart |
| `feedback_no_wait_for_timeout.md` | UNIQUE | not stated in playwright/CLAUDE.md |
| `feedback_no_workarounds.md` | UNIQUE relative to project files (covered by the base harness system prompt, not this repo's own canonical files) | |
| `feedback_precise_plan_before_action.md` | DUP | duplicates rules.md's Approval Rule two-layer structure closely |
| `feedback_present_dont_conclude.md` | UNIQUE | no canonical counterpart |
| `feedback_read_task_output.md` | UNIQUE | no canonical counterpart |
| `feedback_report_issues_immediately.md` | PARTIAL | overlaps rules.md's "Error Reporting" but broader (any bug, not just script failures) |
| `feedback_repository_sql_pattern.md` | PARTIAL + STALE example | adds detail (no TABLE/ALIAS constants) beyond CLAUDE.md's Repository pattern section; cites `AuditLogRepository` as the reference impl where CLAUDE.md now names `UserRepository`/`AdvertisementRepository`/`AttachmentRepository` |
| `feedback_respect_stated_backlog_order.md` | UNIQUE | no canonical counterpart |
| `feedback_run_all_tests.md` | DUP concept + STALE detail | cites a non-existent `sql-engine` module and `./mvnw test`, both outdated |
| `feedback_short_code_comments.md` | DUP (self-acknowledged) | the memory file's own text says the rule was escalated into rules.md's "⛔ Code comments" entry |
| `feedback_show_errors.md` | DUP | duplicates rules.md's "Error Reporting" section |
| `feedback_stop_monitor_after_test.md` | UNIQUE | no canonical counterpart |
| `feedback_sync_docs_manual.md` | DUP | duplicates CLAUDE.md's "`/sync-docs` ... run manually" line |
| `feedback_test_naming_pattern.md` | DUP | duplicates playwright/CLAUDE.md's "Test naming pattern" section near-verbatim |
| `feedback_timeline_content_verification.md` | UNIQUE | not stated in playwright/CLAUDE.md |
| `project_history_undo_activity_plan.md` | STALE/COMPLETED | describes a plan whose outcome (soft delete, snapshots, activity history) appears already implemented differently, per marketplace-app/CLAUDE.md's `EntityActivityOverlay` pattern |
| `project_playwright_migration.md` | STALE/COMPLETED | describes a `@playwright/test` migration; current playwright/CLAUDE.md shows a materially different, more evolved structure (`e2e/*.spec.js`, `_flows/*.flow.js`, Playwright 1.61.1 vs. the 1.52.0 cited here) already in place |
| `project_run_all_tests_orchestrator.md` | STALE | cites dead `unit-tests.sh`/`integration-tests.sh` names and a `features/issues/` path that doesn't match the current `backlog/issues/` convention |
| `project_timeline_tab_plan.md` | STALE/SUPERSEDED (needs user confirmation) | planned a top-level Timeline nav tab; marketplace-app/CLAUDE.md instead describes a per-overlay history icon button opening `EntityActivityOverlay` — a different actual outcome |
| `reference_db_access.md` | REFERENCE | not independently verified stale or current this pass |
| `reference_playwright_ui_testing.md` | STALE/SUPERSEDED | cites `advertisement-app`, Playwright 1.52.0, and a screenshots directory playwright/CLAUDE.md says no longer exists |
| `user_devops_interest.md` | PERSONAL | user trait/preference |

**Rollup:** of 55 files — DUP or DUP+STALE: 17 · PARTIAL: 6 · UNIQUE: 24 · PERSONAL: 2 ·
REFERENCE: 2 · STALE/SUPERSEDED/ORPHANED (not a duplication candidate, a deletion/rewrite
candidate instead): 9 (some entries counted under more than one axis, e.g. DUP *and* STALE).

## Phase 2 — Migration plan (concrete, 2026-08-25)

**Status: drafted, not yet approved to execute.** Split into two independently approvable/
executable sub-phases (no dependency between them, either order is fine):

- **Phase 2.1 — Memory cleanup** (Goal 1: delete/migrate/rewrite memory files)
- **Phase 2.2 — CLAUDE.md migration** (Goal 2 + Goal 3's constraint: move all 13 module
  `CLAUDE.md` into `.claude/rules/`)

### Phase 2.2 — CLAUDE.md migration: all 13 module `CLAUDE.md` move to `.claude/rules/` (revised 2026-08-25)

**Revised decision: move all 13, not 10 — including the 3 the generator depends on.** Leaving 3
of 13 behind for an implementation detail of one script would be exactly the kind of asymmetry
this project's own "Pattern-first ... symmetry with existing code is a first-class goal" guideline
and doc-standards' "one fact, one canonical home" argue against. The generator-side fix is small
and bounded, not a rewrite — see below.

`docs/architecture/scripts/generate-architecture-model.sh` physically parses 3 specific module
`CLAUDE.md` files by path (not just via root `CLAUDE.md`'s `@import`), confirmed by direct read of
the script:

- `platform-commons/CLAUDE.md` — contains 5 `<!-- #arch-embed:KEY -->` marked blocks
  (`spi-glossary`, `port-glossary`, `hook-glossary`, `why-port-hook-glossary`,
  `spi-implementation-rules`) extracted verbatim into the generated architecture page, via the
  `ARCH_EMBED_KEYS` array.
- `scripts/CLAUDE.md` / `playwright/CLAUDE.md` — `SCRIPT_TREE_ROOTS=(scripts playwright)`;
  `emit_script_tree_node()` reads `head -1` of each for the "Scripts"/"Playwright" card
  description (the function is called recursively for every subdirectory too, but no subdirectory
  under `scripts/`/`playwright/` currently has its own `CLAUDE.md`, so only these 2 top-level
  reads actually resolve to anything today).

Confirmed via `SCRIPT_GROUP_DIRS`/`SCRIPT_TREE_ROOTS`/a full-script grep that no other module
`CLAUDE.md` is read by this generator — every other repo-wide reference to a module `CLAUDE.md`
path (in `DECISIONS.md`, `README.md`, `docs/ai/*.md`, other scripts' own comments) is a prose
pointer, not a runtime file read.

**Required generator changes (3 bounded spots, not a rewrite):**
1. `ARCH_EMBED_KEYS` array (5 entries) — change each `"platform-commons/CLAUDE.md:<key>"` to
   `".claude/rules/platform-commons.md:<key>"`.
2. `emit_script_tree_node()`'s `desc=` lookup line — change
   `$REPO_ROOT/$d/CLAUDE.md` to `$REPO_ROOT/.claude/rules/$d.md` (covers both `scripts` and
   `playwright` roots via the same `$d` variable).
3. The repo-wide arch-embed marker discovery scan (`find "$REPO_ROOT" -name "CLAUDE.md" ...`) —
   extend to also scan `.claude/rules/*.md`, so markers moved into that directory stay
   discoverable by the same convention-discovery mechanism.
4. **Mandatory regression check after the script edit:** run
   `bash docs/architecture/scripts/generate-architecture-model.sh` and diff the resulting
   `architecture-model.json`/`architecture-map.html` against the pre-change version — the SPI
   glossary content and the Scripts/Playwright card descriptions must render identically, byte-for-
   byte on the extracted text, not just "look about right."

**All 13 — source → destination → `paths:` glob:**

| Source | Destination | `paths:` |
|---|---|---|
| `platform-commons/CLAUDE.md` | `.claude/rules/platform-commons.md` | `["platform-commons/**"]` |
| `scripts/CLAUDE.md` | `.claude/rules/scripts.md` | `["scripts/**"]` |
| `playwright/CLAUDE.md` | `.claude/rules/playwright.md` | `["playwright/**"]` |
| `audit-spring-boot-starter/CLAUDE.md` | `.claude/rules/audit-spring-boot-starter.md` | `["audit-spring-boot-starter/**"]` |
| `attachment-spring-boot-starter/CLAUDE.md` | `.claude/rules/attachment-spring-boot-starter.md` | `["attachment-spring-boot-starter/**"]` |
| `user-spring-boot-starter/CLAUDE.md` | `.claude/rules/user-spring-boot-starter.md` | `["user-spring-boot-starter/**"]` |
| `advertisement-spring-boot-starter/CLAUDE.md` | `.claude/rules/advertisement-spring-boot-starter.md` | `["advertisement-spring-boot-starter/**"]` |
| `taxon-spring-boot-starter/CLAUDE.md` | `.claude/rules/taxon-spring-boot-starter.md` | `["taxon-spring-boot-starter/**"]` |
| `provider-profile-spring-boot-starter/CLAUDE.md` | `.claude/rules/provider-profile-spring-boot-starter.md` | `["provider-profile-spring-boot-starter/**"]` |
| `marketplace-orchestrator/CLAUDE.md` | `.claude/rules/marketplace-orchestrator.md` | `["marketplace-orchestrator/**"]` |
| `query-lib/CLAUDE.md` | `.claude/rules/query-lib.md` | `["query-lib/**"]` |
| `integration-tests/CLAUDE.md` | `.claude/rules/integration-tests.md` | `["integration-tests/**"]` |
| `marketplace-app/CLAUDE.md` | `.claude/rules/marketplace-app.md` | `["marketplace-app/**"]` |

**Mechanics per file:** move content to the destination path with `paths:` frontmatter added →
delete the original `<module>/CLAUDE.md` → in root `CLAUDE.md`, replace the `@<module>/CLAUDE.md`
import line with a plain-text (non-`@`) pointer sentence, so the module's purpose is still
discoverable at session start without eager-loading its full body. For the 3 generator-dependent
files, the generator-script edits (above) happen in the same change, before/alongside the file
move — never move the file first and fix the generator later, which would leave
`generate-architecture-model.sh` broken in between.

**Expected effect:** eager module-`CLAUDE.md` set drops from 13 files / 2036 lines to 0 for any
task that doesn't touch a module directory at all (root `CLAUDE.md` keeps a one-line-per-module
pointer, unchanged); a task that does touch a given module's directory pays that module's cost at
first touch instead of upfront, same as the previous 10-file plan.

### Phase 2.1 — Memory cleanup: memory file disposition

**DELETE outright (16 — fully covered by canonical text already):** `feedback_always_build`,
`feedback_always_run_smoke_tests`, `feedback_always_use_scripts`, `feedback_ask_before_extra_actions`,
`feedback_decisions_log`, `feedback_git_workflow`, `feedback_language`, `feedback_module_independence`,
`feedback_monitor_wait_then_tail`, `feedback_precise_plan_before_action`, `feedback_short_code_comments`,
`feedback_show_errors`, `feedback_sync_docs_manual`, `feedback_test_naming_pattern`,
`feedback_no_tail_on_streaming_commands` (self-marked superseded), `feedback_no_git` (contradicts
current rules, orphaned).

**MIGRATE nuance → specific canonical file, then DELETE (6):**

| Source | Nuance to carry over | Destination |
|---|---|---|
| `feedback_approval_required.md` | per-sub-change plan requirement; "test passed" ≠ lifecycle approval | `.claude/rules.md` Approval Rule |
| `feedback_commits_require_permission.md` | two-call rule (PreToolUse hook timing) | `.claude/rules.md` commit-approval callout |
| `feedback_decoupling_priority.md` | concrete "violations to avoid" checklist (correct `platform-contracts`→`platform-commons` while migrating) | `platform-commons/CLAUDE.md` SPI section |
| `feedback_repository_sql_pattern.md` | no TABLE/ALIAS constants; inline single-use SQL | `CLAUDE.md` Repository pattern section |
| `feedback_best_practices.md` | "not a prototype" framing | `.claude/rules.md` Code-quality rule |
| `feedback_report_issues_immediately.md` | broader than script failures — any bug | `.claude/rules.md` Error Reporting |

**REWRITE stale facts, keep in memory (2):** `feedback_run_all_tests.md`,
`project_run_all_tests_orchestrator.md` — update dead script/module names to current reality.

**STALE/SUPERSEDED — needs user confirmation before delete/archive (4):**
`project_history_undo_activity_plan.md`, `project_playwright_migration.md`,
`project_timeline_tab_plan.md`, `reference_playwright_ui_testing.md`.

**NEW entry in `.claude/rules.md` (9 — process rules applicable to anyone working this repo):**
`feedback_apply_full_standard_not_just_accuracy`, `feedback_never_self_conclude_review_findings`,
`feedback_dont_substitute_direct_action_instructions`,
`feedback_dont_chase_screenshot_when_json_already_answers`,
`feedback_dont_auto_regenerate_for_backlog_edits`, `feedback_ground_proposals_in_real_data_offer_options`,
`feedback_no_workarounds`, `feedback_always_ux`, `feedback_stop_monitor_after_test`.

**NEW entry in a module `CLAUDE.md` (3 — code conventions):** `feedback_no_primary_objectmapper` →
`CLAUDE.md` general Spring section; `feedback_no_wait_for_timeout` +
`feedback_timeline_content_verification` → `playwright/CLAUDE.md`.

**Domain fact, not a process rule (1):** `feedback_audit_diff_always_full_fields` → a Javadoc note
on `expandWithChanges()` in `platform-commons` instead of a memory entry.

**Stay in memory unchanged — personal collaboration style, not a repo rule (13):**
`feedback_ambiguous_yes_reask`, `feedback_answer_literal_scope_only`, `feedback_contractor_client_tone`,
`feedback_before_after_how_format`, `feedback_no_unsolicited_next_steps`,
`feedback_no_unsolicited_recommendations`, `feedback_present_dont_conclude`,
`feedback_respect_stated_backlog_order`, `feedback_dont_create_issues_unprompted`,
`feedback_issue_log_no_approval`, `feedback_read_task_output`, `feedback_language_ukrainian`,
`user_devops_interest`.

**Stay as REFERENCE (2):** `reference_db_access`; `reference_playwright_ui_testing` pending the
STALE/SUPERSEDED confirmation above.

### Not yet decided — waiting for explicit go-ahead before any file is touched

This plan is written but not executed. Phase 2.1 and Phase 2.2 each need their own explicit
go-ahead before their own files are touched — approving one is not approval for the other, and
either can go first (no dependency between them).

## Phase 2.1 — executed (2026-08-25)

All memory-file dispositions from the plan above were carried out exactly as written:

- 16 pure-duplicate files deleted outright.
- 6 files migrated (nuance merged into `.claude/rules.md`/`CLAUDE.md`/`platform-commons/CLAUDE.md`)
  then deleted.
- 9 files added as new `.claude/rules.md` entries (Approval Rule refinements, commit two-call
  rule, Code-quality "not a prototype" note, Error Reporting extension, and a new "Investigation &
  Review Discipline" section; `--ux`/Monitor-stop notes folded into the existing Scripts section)
  then deleted.
- 3 files migrated into module `CLAUDE.md` (`@Primary`-on-`ObjectMapper` → root `CLAUDE.md`;
  `waitForTimeout`/timeline-content-verification → `playwright/CLAUDE.md`) then deleted.
- 1 file migrated to a one-line Javadoc on `AuditableSnapshot.expandWithChanges()` in
  `platform-commons`, then deleted.
- 2 files (`feedback_run_all_tests`, `project_run_all_tests_orchestrator`) rewritten in place with
  current script names/architecture instead of the stale 2026-05/07 facts they carried.
- `MEMORY.md` index rewritten to match — verified 1:1 against the actual file list, zero orphaned
  index entries, zero files missing an index entry.
- 6 internal `[[...]]` cross-references that pointed at now-deleted
  `feedback_precise_plan_before_action`/`feedback_approval_required` were fixed to point at the
  Approval Rule in `.claude/rules.md` instead — caught by an explicit dangling-link check after the
  edits, not assumed clean.

**Left untouched, pending separate confirmation (per the plan):** the 4 STALE/SUPERSEDED files
(`project_history_undo_activity_plan`, `project_playwright_migration`, `project_timeline_tab_plan`,
`reference_playwright_ui_testing`) — still present in memory, flagged in the index as pending.

**Result:** memory dropped from 55 files to 20 (+ `MEMORY.md`). Phase 2.2 (CLAUDE.md → path-scoped
`.claude/rules/` migration) is still separately not approved.

## Phase 2.2 — executed (2026-08-25)

All 13 module `CLAUDE.md` files moved to `.claude/rules/<module>.md` with `paths:` frontmatter,
exactly per the plan above (revised to move all 13, not 10). Root `CLAUDE.md`'s 13 `@import`
lines replaced with plain-text (non-`@`) pointers to the new paths, including the inline prose
references at lines 37/43/78 (not only the pointer-list lines) — found during execution, fixed in
the same change.

**Generator fix — required two more rounds than planned, both self-inflicted, both root-caused
and fixed, not worked around:**
1. First failure (exit 141, `SIGPIPE`): my own `tail -n +5 file | head -1` replacement for the old
   `head -1 file` desc-lookup — `head -1` closes the pipe while `tail` may still be writing,
   fatal under this script's `set -euo pipefail`. Fixed by replacing the whole pipe with one
   `sed -n '5{s/^#* *//;p}' file` call — no upstream process to SIGPIPE.
2. Second failure (exit 127, "command not found"): the background shell's cwd had drifted to
   `.claude/rules` from an earlier `cd` during a manual duplicate-content check; the script was
   invoked by a relative path that no longer resolved. Fixed by `cd /app` before retrying — not a
   script bug.
3. Third run succeeded (exit 0). Verified directly, not assumed: all 5 arch-embed keys resolve
   correctly from `.claude/rules/platform-commons.md` with correct line numbers/content; the
   `Scripts`/`Playwright` card descriptions resolve correctly from `.claude/rules/scripts.md`/
   `.claude/rules/playwright.md`; a full node-by-node description diff against the last real run
   (2026-08-21, from git `HEAD`) showed exactly 2 differences, both unrelated pre-existing content
   drift (a skill description text change, a new skill added since then) — zero regressions from
   this migration.

**New finding, not anticipated in the plan:** a `paths:` glob is not anchored to the repo root —
`"scripts/**"` also matched `docs/architecture/scripts/generate-architecture-model.sh` (confirmed
live: reading that file auto-loaded `.claude/rules/scripts.md`). Documented in the new
`.claude/rules/README.md` so a future glob author knows to write narrower patterns if an unrelated
directory elsewhere in the repo could share a path segment.

**Two new files added, out of scope for the original plan but a natural completion of it:**
`.claude/rules/README.md` (the path-scoped-rules mechanism + the glob-anchoring nuance) and
`.claude/README.md` (the `.claude/` directory's own structure + both loading mechanisms + every
verified fact from this issue's Phase 1/2.2 work, so a future reader doesn't have to reconstruct
it from this issue file).

**Left deliberately out of scope:** the wider repo's prose references to the old
`<module>/CLAUDE.md` paths (in `README.md`, various `DECISIONS.md` files, `docs/ai/*.md`, a few
command files) — none of them are runtime-read (confirmed in Phase 1's dependency check), so
they're stale pointers, not broken functionality. Not fixed here since it wasn't part of the
approved plan; recorded as entry 13 in
`backlog/issues/improvement-133-deferred-oversized-review-findings.md` instead of being dropped
silently.

## Phase 2.1 pending-confirmation follow-up — resolved (2026-08-25)

The 4 files left untouched at the end of Phase 2.1 (flagged `STALE/SUPERSEDED — pending user
confirmation`) were each verified directly against current code/config, not re-assumed from the
original classification:

- `project_history_undo_activity_plan.md` — **stale, deleted.** The real implementation uses a
  single `audit_log` table with a `snapshot_data` JSON column (`audit-spring-boot-starter`), not
  the separate `advertisement_snapshot`/`user_snapshot` tables the memory's plan called for — a
  different architecture was actually built.
- `project_playwright_migration.md` — **stale/completed, deleted.** `playwright/` now contains
  only `e2e/*.spec.js` + `playwright.config.js` — zero legacy non-`.spec.js` scenario files
  remain; the migration the memory describes as in-progress finished completely.
- `project_timeline_tab_plan.md` — **completed, deleted, and the original Phase 1 classification
  for this file was itself wrong** — corrected here rather than left standing. Phase 1 had called
  it "superseded" based on `marketplace-app/CLAUDE.md`'s per-entity history icon-button pattern,
  but that's a different feature. Direct check of `MainView.java` found a real, working top-level
  `Timeline` tab (`timelineTab`) alongside Advertisements/Users/Reference Data — the plan was
  implemented exactly as written, not replaced by something else.
- `reference_playwright_ui_testing.md` — **stale, deleted.** Confirmed directly:
  `/app/playwright/screenshots/` does not exist; current, accurate Playwright reference info
  already lives in `.claude/rules/playwright.md`.

`MEMORY.md` updated to match — verified 1:1 against the real file list again, zero dangling
`[[...]]` links. **Final memory count: 55 → 16 files (+ `MEMORY.md`).**
