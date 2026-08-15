## ⛔ RE-READ ALL RULES BEFORE EVERY ACTION
Before executing any tool call — re-read this entire file. No exceptions. This explicitly includes
the start of every `/command` or Skill invocation (`/autopilot`, `/deep-review`, `/feature`, etc.)
— re-`Read` this file fresh at that point, not just once at the top of a long session.

Not rules.md alone: re-`Read` the root `/app/CLAUDE.md` plus every module `CLAUDE.md` the task
actually touches (e.g. `marketplace-app/CLAUDE.md` for UI work, `platform-commons/CLAUDE.md` for
SPI work) at that same starting point, so the full picture — conventions and module-specific
constraints together — is fresh before any implementation begins, not assembled piecemeal from
memory partway through.

Same discipline before **every individual script run**, not only once per command/skill
invocation: before `deploy.sh`/`playwright.sh`/`unit-tests.sh`/`integration-tests.sh` etc., re-Read
the relevant `CLAUDE.md` (`scripts/CLAUDE.md`, `playwright/CLAUDE.md`) for that script's current
documented behavior/flags/constraints rather than acting on what was true earlier in the same
session — those files get updated mid-session precisely because a run just revealed a gap.

---

> ## ⛔ NEVER commit without explicit user request
> `git commit` is **forbidden** unless the user says "зроби коміт", "commit", or equivalent.
> `git add` runs automatically after every file change — commit does NOT.
> Violating this rule has happened multiple times. No exceptions.

> ## ⛔ Code quality is the highest-priority goal — surface adjacent quality issues unprompted
> Code quality outranks minimizing diff size, staying strictly inside a written batch's literal
> scope, or matching existing precedent for its own sake. When a task touches a design that has an
> adjacent, obviously-related quality issue — inconsistent layering, a field or method carried by a
> type that most consumers never use, a misleading name, an interface mixing unrelated concerns —
> surface it and propose fixing it as part of the same review, before being asked a second time.
> Look for this class of issue proactively on any change touching a DTO, entity, or interface —
> don't wait to be asked.

> ## ⛔ Rules in this file must state the abstract principle, not a case study
> When adding or editing a rule, write the general principle a reader can apply to an unrelated
> future situation. Do not embed a specific incident's class/method/table names, issue numbers, or
> a blow-by-blow retelling of what happened — that belongs in a commit message or `DECISIONS.md`,
> not here. A rule padded with one incident's specifics reads as "this is about X," making it
> harder to recognize the same principle applies somewhere unrelated to X.

> ## ⛔ Any `DECISIONS.md` edit regenerates the ADR index in the same operation
> Whenever any `DECISIONS.md` file is created, edited, or has an ADR added/changed — by any
> workflow, command, or skill, not only `/decision` — run
> `bash scripts/ai/generate-adr-index.sh` and include the resulting `docs/ai/adr-index.md` diff in
> the same change before considering it complete. This applies even to direct edits made outside
> any specific skill's own steps; the index going stale is not a lesser concern just because the
> edit didn't go through the one command that happens to mention it.

> ## ⛔ A new project-local command/skill file adds its own navigation row in the same operation
> Whenever a new `.claude/commands/*.md` or `.claude/skills/*/SKILL.md` file is added to this repo,
> add its row to `docs/ai/flows.md`'s "Project commands & skills" table in the same change. An
> undocumented operational mechanism is exactly the kind of adjacent quality gap the standing
> "surface it unprompted" rule already covers — don't wait for a later audit to catch it.

> ## ⛔ Code comments: one line or none, never an issue/ticket number
> Every code comment (production code and test code alike) is either **one line** or **not
> written at all**. Never a multi-line block explaining background/rationale in full — that
> belongs in the issue file, ADR, or commit message. Also never mention an issue/ticket number
> (`improvement-NNN`, etc.) inside a code comment — it looks bad and rots as issues get renumbered
> or archived; that traceability belongs in the commit message, not the code. Write the one-line,
> number-free version on the first pass; do not wait to be told to fix it. Violating this rule has
> happened repeatedly. When the rationale being trimmed out is a real fact or design decision (not
> just local code context), route it to its canonical home per
> `.claude/skills/doc-standards/SKILL.md`'s ownership table — write that entry first if it doesn't
> exist yet, then leave a one-line reference in the code — never just delete the explanation.

> ## ⛔ A comment above a method states what that method's own body does
> A one-line comment above a method describes what that method actually does, verified by reading
> its body — never a narrative about which other class/method calls it or is called by it, and
> never written from the method name/tag alone without checking the code. If the comment claims to
> summarize the whole method, it must cover every real branch, not just the first path.

> ## ⛔ No issue/ticket numbers or dated "resolved" narrative in current-state documentation
> The same "no ticket numbers" principle above extends to every file that describes the system's
> *current* state — `CLAUDE.md`, `README.md`, `docs/architecture/*.md`, `docs/ai/*.md`, skill/
> command `.md` files, and shell-script comments. None of these may cite an
> `improvement-NNN`/`goal-NNN`/`feature-NNN` reference, and none may carry a dated
> "resolved"/"as of \<date\>" narrative describing a past state that no longer holds. If a fact
> changes, delete the old fact — don't mark it resolved in place; a file describing "what is"
> should read as if it always looked this way. `DECISIONS.md` keeps its own append-only historical
> character (date, decision, reasoning) but drops the issue-number citation the same way — an ADR
> records the decision and why, never which ticket produced it.
>
> **Why:** a ticket citation is a forward-link that only ever goes stale — issues get renumbered,
> merged, or archived, while the file citing them keeps being read as live guidance long after the
> ticket closes. A "resolved" note left in place reads as current information to a reader with no
> way to tell it stopped mattering.
>
> **How to apply:** history lives only in `backlog/completed/issues/*.md` (full detail) and
> `backlog/completed/BACKLOG-ARCHIVE.md` (searchable one-line index) — keep those naming the real
> classes/modules/concepts touched, not just "cleanup pass," so a keyword grep finds them. The
> reverse link — which ticket produced a given current line — is `git blame`/`git log`, already
> free via this repo's `feat(improvement-NNN): ...` commit convention; do not build or maintain a
> new index for this purpose.

> ## ⛔ Comments, README, and other markdown files never cite a specific ADR number
> A code comment, `README.md`, or any other markdown file may say "see `DECISIONS.md`" but must
> never cite a specific `ADR-NNN` number. `DECISIONS.md` itself is the one place ADR numbers are
> written — every ADR entry states which file(s)/module it governs directly in its own text, so a
> reader finds the relevant entry by searching `DECISIONS.md` for the fact in question, not by
> following a numbered pointer planted somewhere else.
>
> **Why:** an ADR number planted in a comment/README is a forward-link like a ticket number — it
> goes stale the moment ADRs get renumbered, split, or superseded, and it invites casually citing
> a number "just in case" rather than actually stating the fact that matters. Traceability should
> flow from the decision record outward (the ADR says what it governs), not from scattered
> pointers inward.
>
> **How to apply:** going forward only — existing `ADR-NNN` citations already in the repo are not
> retroactively scrubbed by this rule; it governs new/edited content from here on.

> ## ⛔ Skills, commands, rules, and README files name a real repo file only when unavoidable
> `SKILL.md`, `.claude/commands/*.md`, `.claude/rules.md`, and any `README.md` stay maximally
> abstract, lean units. Illustrating a convention with an example is fine — but naming a specific
> real file in this repo as that example's reference point is not, unless there is genuinely no way
> to make the point without it, or the user explicitly asks for the concrete reference to stay.
> Prefer a generic or placeholder name (invented content, or a pattern like `<sibling>.sh`) over a
> real path.
>
> **Why:** a named real file is a forward-link exactly like a ticket or ADR number — it goes stale
> the moment that file is renamed, moved, split, or removed, and nobody remembers every markdown
> file that quietly depends on the old name until something breaks. Finding and fixing every stale
> reference afterward means grepping the whole repo for a name that no longer exists — real effort
> spent on upkeep the rule itself exists to prevent.
>
> **How to apply:** going forward only — existing real-file references already in the repo are not
> retroactively scrubbed by this rule; it governs new/edited content from here on. When an example
> genuinely needs to be traceable to real, already-applied behavior (proving a convention was
> actually followed, not just described), naming the real file is the unavoidable case this rule
> already carves out — keep it, and say so, rather than leaving an untraceable abstract mockup in
> its place.

> ## ⛔ Files that govern Claude's own behavior state the target only — never a before/after
> `CLAUDE.md`, a skill's `SKILL.md`, `.claude/commands/*.md`, and `.claude/rules.md` itself describe
> the convention/standard to follow, directly — never as an "as-is today" vs. "target" comparison.
> State only what should be done; do not narrate what a file currently looks like before the
> convention is applied.
>
> **Why:** these files are read as standing instruction, not as a changelog — a reader (human or
> Claude) consulting one mid-task needs the rule itself, not a reconstruction of what preceded it.
> A before/after pair doubles the content for no operational benefit and reads as unfinished
> migration notes left in place.
>
> **How to apply:** a before/after comparison is fine *in chat*, while proposing a change for
> approval — that's exactly what it's for. Once approved and written into the control file, only
> the target state goes in. Concrete examples illustrating the target are still welcome (e.g. a
> real file's header rewritten to the new convention) — just don't pair each one with what it used
> to look like.

## Approval Rule
**Every action must be approved by the user before execution — no exceptions.**

Before doing anything, present the plan in two layers, in this order:

1. **Plain-language layer (first):** why this is being done and what the outcome will be, in
   words a non-technical reader could follow — no file paths, no method signatures yet.
2. **Technical layer (after):** the exact instruction you would give yourself to execute the
   action — full file paths, exact changes (method signatures, SQL, config values, field names),
   any side-effects or follow-up steps.

Present both layers, then **STOP and wait for explicit confirmation** before executing.

Example format:
> Plain-language: "The activity tab shows the wrong reviewer, so admins can't tell who actually
> approved a change. Fixing it so the correct reviewer's name always shows."
>
> Technical: "Edit `/full/path/File.java`: replace method `getMediaActivity(Long userId)` with
> `merge(Long userId, List<ActivityItemDto> baseItems)` — do it?"

Wait for explicit confirmation before making any change.

Before presenting a plan for a multi-step change, first write the complete, current plan into the
relevant `backlog/issues/<n>.md` file — never present a plan only in chat. Update the issue file
again every time the plan changes (new finding, scope correction), then present a short summary
from that file for approval — never re-paraphrase the whole issue back at length.

## Module Import Rules

**No direct imports between sibling modules.**
- Starters must NOT import from marketplace or from each other.
- Marketplace may import from starters only via platform-commons contracts (Ports/Hooks/DTOs)
  and published UI components — never via internal impl classes (util, service, repository).

## Git Workflow
- `git add` — run automatically after every file change
- `git commit` — **ONLY** when the user explicitly says to commit — never otherwise
- **Before every commit, actually review what's staged — never trust `git add -A`/`git add .` blindly.**
  Run `git status --short` and `git diff --cached --stat` (or fuller `git diff --cached` for a
  smaller change) and read the output before running `git commit`, every time, even when the
  change feels routine. "No untracked files showed up" is not the same check as "the staged diff
  only contains what I meant to commit" — confirmed directly: build artifacts from a new module's
  own `target/` directory landed in a commit because `.gitignore`'s per-module list was never
  updated for that module, and `git add -A` staged them without complaint since nothing about that
  looked untracked or unusual at a glance.

## Language
All repository content must be in **English**: code comments, Javadoc, README files, commit messages, Playwright test descriptions, and any other text checked into the repository.

## `.bat` files — ASCII only, no em-dashes or other Unicode punctuation
`cmd.exe` reads `.bat` files in a legacy codepage, not UTF-8. A multi-byte UTF-8 character (em-dash
`—`, smart quotes, etc.) anywhere in the file — even inside a `::`/`REM` comment — can corrupt
`cmd.exe`'s own batch-label parsing, producing `The system cannot find the batch label specified`
errors on real Windows for labels that objectively exist in the file (confirmed directly: an
em-dash added to a comment in `scripts/collect-code.bat` broke `call :FindFiles`/`call :CountFiles`
elsewhere in the same file). Use plain ASCII `-`/`--` instead of `—`/`–` in every `.bat` file,
including comments. Not an issue in `.sh` files (bash reads UTF-8 natively).

## Test Coverage After Bug Fixes
After fixing a bug, cover all affected flows with Playwright tests before marking the task complete.

## Scripts
Always use project scripts — never raw docker/mvn commands:
- `bash scripts/build-and-test.sh` — build the reactor (+ optional unit/integration tests), no local Java needed
- `bash scripts/deploy.sh` — full rebuild (~7-10 min)
- `bash scripts/playwright.sh [scenario]` — Playwright tests
- `mvn clean test 2>&1 | tee /tmp/test.log` — JUnit tests

**Script-group directory structure:** when a script's own logic needs supporting files (a
Dockerfile, `.properties`, fixtures, multiple scenarios) and gets its own subdirectory under
`scripts/`, that subdirectory owns all of the logic — the corresponding top-level
`scripts/<name>.sh` is a thin entry point only, forwarding arguments to `scripts/<name>/run.sh`
with no logic of its own. A script that stays self-contained, with no subdirectory of its own,
carries its full logic directly in the top-level `scripts/<name>.sh` file — no artificial
subdirectory split for something that doesn't need one.

**Run all scripts with Monitor + tee pattern:**
1. Launch Monitor (`persistent: true`) watching the log file every 10s — reports stuck/error/success
2. Run synchronously with `timeout: 600000` piped to `tee /tmp/<script>.log`
3. User sees full streaming output directly

**Before running Playwright** — kill old processes first:
1. `docker exec pw-runner pkill -f "node.*playwright" 2>/dev/null; true`
2. Launch Monitor watching `/tmp/playwright.log` (10s interval, catch `failed|Error|passed`)
3. Then run: `bash scripts/playwright.sh [scenario] 2>&1 | tee /tmp/playwright.log`

**Before running deploy.sh** — launch Monitor watching `/tmp/deploy.log` (10s interval, catch `ERROR|BUILD SUCCESS|Started Application`), then run: `bash scripts/deploy.sh [args] 2>&1 | tee /tmp/deploy.log`

## Issue Lifecycle

Before filing a new ADR (`/decision`) or a new backlog issue (`/feature`), consult
`docs/ai/adr-index.md` (if present) for an already-decided overlapping ADR — mandatory, not
best-effort. See `docs/ai/README.md` for what the file is and how it stays current.

When filing a **new** issue in `backlog/issues/`:
- Always assign a `**Priority:**` line in the issue file itself — never leave it blank/TBD.
- Always add it to `backlog/BACKLOG.md`'s Priority order table at a ranked position (not just the
  "Still open" listing table) in the same operation — a new issue is never left unranked pending
  future triage. See `backlog/BACKLOG.md`'s "Maintenance rules".

When an issue in `backlog/issues/` is resolved (fix is implemented and committed):
- Move the file to `backlog/completed/issues/` — **immediately, in the same operation as the fix**
- Do not leave resolved issues in `backlog/issues/`
- Remove its row from `backlog/BACKLOG.md` and add a one-line `✅ Done` entry to
  `backlog/completed/BACKLOG-ARCHIVE.md` under the relevant wave — same operation, see
  `backlog/BACKLOG.md`'s "Maintenance rules"

### Out-of-scope-but-valid findings — propose adding to the standing deferred-findings bucket, never drop silently
When a `/code-review`/`/deep-review` (or any other review) finding is real and worth fixing but
its solution is too large to fit in the current batch/PR (a new abstraction, an architectural
change, a cross-module refactor), do not silently skip it and do not fix it inline outside the
approved scope either. At the end of the review, propose appending it as a new entry to this
project's standing collection bucket for exactly this class of finding — search `backlog/issues/`
for the file covering "deferred oversized review findings" — state what it covers and why it
doesn't fit now, and wait for approval before writing the entry. Do not create a brand-new issue
file per finding; that scatters oversized findings across dozens of one-off files instead of one
triage-able list. Only carve a finding out into its own issue once it's actually being picked up
and sized for real work.

### Final reports — no file-by-file diff table
When reporting completed work (autopilot's step-5 final report, or any other end-of-task summary),
do not include a "what changed" table/list enumerating each file with a description of its diff —
the user reads the actual code/diff themselves and finds a full file-by-file recap redundant.
Keep the report human: what was done in plain terms, test results (counts, not just "passed"),
and review-finding decisions. Skip the enumerated file-changes section entirely.

### Final reports record real operational data in a fixed, mechanically-parseable block
Whenever completing an issue, append an `## Operational notes` block to that issue file — real
observations from the real task just done, not a synthetic exercise, and not free-form prose (a
later aggregate pass greps/parses this across many issue files, the same way `## ADR-NNN:` +
`**Status:**` makes `DECISIONS.md` mechanically indexable — inconsistent formatting defeats that).
Fixed key: value lines, one key per line, `n/a` for anything that doesn't apply to this task:

```
## Operational notes
- token_cost_review: <tokens summed from Agent-tool review-purpose calls, or n/a>
- token_cost_research: <tokens summed from Agent-tool research/investigation calls, or n/a>
- token_cost_verification: <tokens summed from Agent-tool verification/testing calls, or n/a>
- review_signal_ratio: <CONFIRMED/PLAUSIBLE findings that survived verification> / <total candidate findings raised across all finder angles>, or n/a if no /code-review ran this task
- context_loading_task_type: <the matching docs/ai/context-loading.md row, or n/a>
- context_loading_consulted: <yes/no/n/a>
- context_loading_matched: <yes/no/n/a — did the actual read pattern match that row's guidance>
- flows_situation: <short phrase describing the situation, or n/a>
- flows_chosen: <the command/skill actually used, or n/a>
- flows_matched: <yes/no/n/a — did it match docs/ai/flows.md's recommendation for that situation>

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

Show this same block in the chat final report too, not only in the issue file — writing it to the
file alone is invisible to the user unless they go open that file themselves. Include it verbatim
(or immediately adjacent to) the rest of the completion report, every time, not just when a number
happens to look interesting.
Token totals are never a full accounting — no tool reports main-thread token usage, state that as
context if asked, not inside the block itself (keep the block just the key: value lines, nothing
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

**Agent calls vs. Script/command runs — different metrics, don't conflate them.** Every Agent-tool
task-notification carries real `<usage>` data (`subagent_tokens`/`tool_uses`/`duration_ms`) — use
it verbatim, don't estimate. Scripts (`unit-tests.sh`, `deploy.sh`, `playwright.sh`, etc.) are not
LLM calls and have no token cost of their own — never invent one; only record their real wall-clock
duration and pass/fail result. Main-thread (this conversation's own) token usage has no reporting
tool at all — not a policy choice, a real gap, confirmed via `ToolSearch` finding no matching tool
before writing this rule (2026-08-08) — do not estimate it either; the `token_cost_*` fields above
stay Agent-only for this reason.

### Review-skill effort level — default stays put; deviation allowed only when obvious, always disclosed
A review-style skill's effort level defaults to whatever it defaults to when the user doesn't name
one explicitly — do not silently change that default based on cost, habit, or a hunch. The user can
always specify a level explicitly for a given run; when they haven't, choose a level other than the
default only when it is obviously justified by the change's own size/risk (e.g. a one-line typo
fix vs. a cross-module architectural rewrite), never as a routine cost-saving move. Whichever level
actually ran — default or deviated — state it plainly in the report every time; a silent choice is
not acceptable even when the choice itself was reasonable.

## Definition of Done
A feature or fix is not complete until all of the following hold:
- The relevant full test suite is green: `bash scripts/unit-tests.sh` + `bash scripts/integration-
  tests.sh --sandbox` always; the full Playwright `e2e --full --ux` scenario too whenever the
  change touches UI-visible behavior. `bash scripts/ci.sh` (`/ci`) runs this whole chain
  (unit → integration → e2e → Sonar) in one pass when a single command is preferred over running
  each stage separately.
- `DECISIONS.md` (the relevant module's) is updated if the change is architectural — a new
  decision, or an annotation to an existing one it supersedes.
- The issue file is moved from `backlog/issues/` to `backlog/completed/issues/`, its `BACKLOG.md`
  row removed, and a `✅ Done` entry added to `BACKLOG-ARCHIVE.md` — see "Issue Lifecycle" above.

## After Interruption
After any [Request interrupted by user] — full stop. No further tool calls, no continuation, no fixes.
Wait for the next explicit user message before doing anything.

## Error Reporting
When running any script or command that fails, immediately read the error output and show the specific error lines in the chat. Never just report "it failed" without the actual error details.

## Documentation Standards
Before writing or editing any documentation file, consult `.claude/skills/doc-standards/SKILL.md`.

---

## Overlay Pattern

### OverlaySession — immutable state machine
Every overlay uses a `record OverlaySession(Mode mode, EntityDto entity, Runnable onSaved, boolean enteredFromView)`.
Mode transitions return new instances — never mutate fields directly:
```java
session = session.toEdit();          // correct
session = session.toView();          // correct
session = session.withEntity(fresh); // correct — after save
session.mode = Mode.EDIT;            // wrong — record, mutation is impossible
```

### switchTo() — the only way to transition between modes
Always call `switchTo()` to transition between overlay modes. Never call `launchSession()`
for transitions — `launchSession()` resets the entire layout and triggers an unnecessary
JS scroll-reset. `launchSession()` is only for the initial overlay open.

```java
// correct — transitioning between modes
session = session.toEdit();
switchTo();

// wrong — resets layout
session = session.toEdit();
launchSession(this::switchTo);
```

### currentFormHandler — reset before switchTo()
At the start of `switchTo()` always reset `currentFormHandler = null` before the switch expression.
Without this, after VIEW→EDIT→VIEW the handler remains non-null and `hasUnsavedChanges()`
returns `true` even though the form is already closed.

```java
@Override
protected void switchTo() {
    currentFormHandler = null;  // always the first line
    OverlayModeHandler handler = switch (session.mode()) { ... };
    ...
}
```

### afterSave() — update entity in session
After saving in EDIT mode always update the entity in the session via `withEntity()`:
```java
if (session.mode() == Mode.EDIT) {
    currentFormHandler.afterSave(true);
    EntityDto fresh = currentFormHandler.getSavedDto();
    if (fresh != null) session = session.withEntity(fresh);
} else {
    closeToList();
}
```

### discardChanges() — the only name for resetting a form
The method for resetting form state in FormOverlayModeHandler is always named `discardChanges()`.
In `doCancel()` of the overlay always call it before transitioning:
```java
if (currentFormHandler != null) currentFormHandler.discardChanges();
```

---

## View Pattern

### init() — structure and visibility
The `@PostConstruct init()` method in all View classes is always `protected`, never `public`.
The order inside init() is fixed:

```java
@PostConstruct
protected void init() {
    // 1. CSS class and sizing
    // 2. Build main component (grid / container)
    // 3. Build contentWrapper
    // 4. add(contentWrapper, overlay)
    // 5. Subscriptions (queryBar, pagination, shortcuts)
    // 6. settingsPaginationBinding.register(...)
    // 7. refresh()
}
```

Do not split init() into small `initXxx()` methods if each does 1–2 lines.
Extract into a separate method only when logic is complex (e.g. Grid column configuration
via a dedicated `*GridConfigurator` class).

### refresh() — always with try/catch
The `refresh()` method in all View classes is always `private` and always guarded:

```java
private void refresh() {
    try {
        // fetch + render logic
    } catch (ConstraintViolationException ex) {
        log.warn("Validation error: {}", ex.getMessage(), ex);
        showValidationErrors(ex);
        clearContent();
        paginationBar.setTotalCount(0);
    } catch (Exception ex) {
        log.error("Failed to refresh view", ex);
        notificationService.error(...);
        clearContent();
        paginationBar.setTotalCount(0);
    } finally {
        queryStatusBar.update(); // if queryStatusBar is present in this view
    }
}
```

Never leave `refresh()` without try/catch — an unhandled exception means a blank screen for the user.

### Refresh method name
The data refresh method in a View is always named `refresh()`, never `refreshGrid()`,
`refreshData()`, or any other variant.

---

## Query Layer Pattern

### FilterMeta and SortMeta — Fields.* constants only
In `*SortMeta` and `*FilterMeta` classes, fields always reference typed `Fields.*` constants
from the DTO, never raw strings:

```java
// correct — compiler catches renames
SortFieldMeta.of(AdvertisementInfoDto.Fields.updatedAt, ADVERTISEMENT_SORT_UPDATED_AT)

// wrong — silent failure on refactoring
SortFieldMeta.of("updatedAt", ADVERTISEMENT_SORT_UPDATED)
```

---

## Service Class Section Headers

When a service class (or any class with 2+ clearly distinct concerns — query/filter, CRUD,
enrichment, sanitization, etc.) grows past a handful of methods, divide it into labeled blocks
with a one-line comment separator, placed directly above the first method of each block:

```java
// ── Query & filter ───────────────────────────────────────────────────────

public List<AdvertisementInfoDto> getFiltered(...) { ... }
...

// ── CRUD ─────────────────────────────────────────────────────────────────

public Long save(...) { ... }
...
```

One line, no explanation of what the block does beyond the label itself — same "one line or none"
comment rule applies. Do not add this to small classes with a single concern; it's for classes
where a reader scrolling through needs a map of what comes next.

## DTO Field Name Constants

When a DTO needs field name constants (e.g. for `*SortMeta` or `*FilterMeta`), always use
the Lombok `@FieldNameConstants` annotation — never write a manual `Fields` inner class:

```java
// correct — Lombok generates Fields.id, Fields.name, etc.
@FieldNameConstants
public record UserDto(Long id, String name, ...) {}

// wrong — manual boilerplate
public record UserDto(Long id, String name, ...) {
    public static final class Fields {
        public static final String id = "id";
        ...
    }
}
```

`@FieldNameConstants` works on records since Lombok 1.18.22 (project uses 1.18.34+).
Import: `lombok.experimental.FieldNameConstants`.

---

## Form Handler Pattern

### buildBinder() — separate method
Binder creation and field binding logic is always extracted into a separate `buildBinder(EntityDto dto)`
method, never inlined into `activate()`.

### History access — an icon button opening EntityActivityOverlay, not a tab
`AbstractFormOverlayModeHandler` has no tab machinery at all (`buildTabbedContent()`,
`buildContentWithActivity()`, `ActivityTabParams` were removed once all five domains migrated —
see `marketplace-app/DECISIONS.md` ADR-067). `layout.setContent(...)` is called unconditionally;
history/restore is a header-action icon button (`.{domain}-history-button`) that opens the shared
`EntityActivityOverlay` (`ui/views/components/audit/`) stacked on top via `BaseOverlay.openNested()`:

```java
private UiIconButton buildHistoryButton() {
    UiIconButton historyBtn = new UiIconButton(getValue(USER_ACTIVITY_BUTTON), VaadinIcon.CLOCK.create());
    historyBtn.addClassName("user-history-button");
    historyBtn.addClickListener(_ -> entityActivityOverlay.openFor(EntityActivityOverlay.Parameters.builder()
            .entityRef(new EntityRef(EntityType.USER, params.getUser().id()))
            .parentSteps(params.getBreadcrumbSteps())
            .parentFormLabel(params.getUser().name())
            .currentLabelKey(USER_ACTIVITY_BUTTON)
            .onRestoreRequested(this::handleRestoreFromActivity)
            .build()));
    return historyBtn;
}
```

---

## Breadcrumb Pattern

### BreadcrumbStep — a growing stack, not a fixed back-link
`record BreadcrumbStep(String label, Runnable onClick)` (`ui/views/components/overlay/`) — `label`
is an already-resolved `String` (not an `I18nKey`), so it also fits dynamic labels (e.g.
`UserOverlay`'s current user name). `AbstractEntityOverlay.buildBreadcrumbSteps()` returns the
list-tab step by default, plus a "View" step (`OVERLAY_BREADCRUMB_VIEW`) whenever
`isEditMode() && enteredFromView()` — never rewrite existing segments when navigating deeper, only
append. `OverlayLayout.setBreadcrumbLinks(List<Component>)` renders the chain with `›` separators
only *between* links. `EntityActivityOverlay.openFor(...)` extends the calling overlay's own
`buildBreadcrumbSteps()` (passed as `parentSteps`) with one more segment (`parentFormLabel`), so
the nested history overlay's breadcrumb reflects the real navigation path taken, not a fixed
2-segment pair. See `marketplace-app/DECISIONS.md` ADR-067 for the full history of this design.

---

## Reference Implementations

When adding a new domain, use these as reference:
- View: `AdvertisementsView` (init structure) + `UserView` (refresh guard)
- Overlay: `AdvertisementOverlay` (OverlaySession, afterSave, mode switching)
- ViewModeHandler: `AdvertisementViewOverlayModeHandler` (AbstractViewOverlayModeHandler)
- FormModeHandler: `UserFormOverlayModeHandler` (buildBinder separate)
- QueryBlock: `AdvertisementQueryBlock` and `UserQueryBlock` (identical structure)
- FilterMeta: `AdvertisementFilterMeta` (Fields.* constants)
- SortMeta: `AdvertisementSortMeta` (Fields.* constants)
