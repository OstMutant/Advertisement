Analyze what changed in the codebase and update only the affected architecture documentation.

Usage:
  /sync-docs               — compare current HEAD against origin/main
  /sync-docs <ref>         — compare current HEAD against <ref> (branch, tag, or SHA)
  /sync-docs --full-audit  — ignore git diff entirely; verify every existing README.md/DECISIONS.md/
                             CLAUDE.md claim against current code (see "Full Audit Mode" below)

**Known limitation of the default (diff-based) mode:** it only re-checks documentation for files
that changed in the given diff. Staleness introduced by a rename/removal in commit A, where no
later commit's diff ever touched the doc mentioning the old name, is invisible to this mode
forever — the diff that would surface it never happens, since the doc file itself isn't part of
any single commit's changed-file set. This is not a bug to fix in the diff logic; it's why
`--full-audit` exists as a separate mode below, for periodic use (e.g. before a release, or every
few weeks), not as a replacement for the fast day-to-day diff mode.

---

## Step 1 — Determine changed files

Run: `git diff --name-only <ref>...HEAD`
Default ref: `origin/main`

If no Java, SQL, or pom.xml files changed → print "No architectural changes detected." and stop.

---

## Step 2 — Map changed files to documentation targets

Use this mapping table:

| Changed file pattern | Documentation targets |
|----------------------|-----------------------|
| `**/*.java` (main or test), `**/pom.xml`, `**/db/changelog/**`, `**/DECISIONS.md`, `backlog/**`, `.claude/commands/*.md`, `.claude/skills/*/SKILL.md`, `.claude/nav/flows.md` | `docs/architecture/data/architecture-model.json` + `docs/architecture/architecture-map.html` — every diagram (Module Dependencies, SPI Map, Database ERD, Bounded Contexts) and every module page's Code Metrics/Architecture Checks/Largest Files sections render live from real source; there is no separate `.md` to hand-edit for any of them. Regenerate via `bash docs/architecture/scripts/generate-architecture-model.sh`. A schema change still needs its new/changed `<column>`/`<createTable>` to carry a real `remarks=` attribute in the changelog itself (single source of truth, see root `CLAUDE.md`) — that edit happens in the changelog, not in the generated doc. **Run this last, after Step 4's other file updates** — the generator reads every `DECISIONS.md` as input, so it must run after those are updated, not before, or it regenerates from stale input. |
| Any `*.java` or `**/pom.xml` | `CLAUDE.md` (per changed module), `DECISIONS.md` (per changed module) |
| Any `*.java` or `**/pom.xml` | `backlog/issues/` — create/close/update tracked issues |
| Any `**/DECISIONS.md` | `.claude/nav/adr-index.md` — regenerate via `bash .claude/nav/scripts/generate-adr-index.sh` |

Print which targets are affected before proceeding.

---

## Step 3 — Read actual source

No manual read-and-rewrite step is needed for `docs/architecture-map.html`'s content — every
diagram and every module-page section renders live from `pom.xml`/real Java source/real Liquibase
changelogs/a running SonarQube server/the last `ArchitectureMetricsExport` test run, directly
inside `bash docs/architecture/scripts/generate-architecture-model.sh` (see Step 4's ordering note below). A
schema change still needs its new/changed `<column>`/`<createTable>` to carry a real `remarks=`
attribute in the changelog itself (single source of truth, see root `CLAUDE.md`) — that edit
happens in the changelog, not in a generated doc.

---

## Step 4 — Update affected files

There is no `docs/architecture/` markdown content left to hand-edit — `docs/architecture/data/README.md`
is a pointer to the live tool, and every diagram/module-page section renders directly from source
(see Step 3). Start straight from the other targets below.

**DECISIONS.md** (per module) — ADR audit:
- Mark realized open goals as done (add date)
- Replace inline bug/improvement descriptions with `→ [issue-NNN](../backlog/issues/...)` links
- Add new ADR entry if a new architectural pattern or constraint was introduced
- Update Status of superseded entries
- Remove entries whose patterns no longer exist in code

**CLAUDE.md** (per module) — factual corrections only:
- Update "What it owns" if new classes were added or removed
- Fix stale package paths
- Do NOT rewrite prose

**backlog/issues/** — lifecycle:
- Create new issue file if a violation or open goal is detected that is not yet tracked
- Close issue (move to `backlog/completed/issues/`) if the code confirms it is resolved
- Update issue file if scope or constraints changed

**README.md** — update only the block between `<!-- arch:start -->` and `<!-- arch:end -->`:
- Point at the live module dependency graph in `docs/architecture/architecture-map.html` (Diagrams › Module
  Dependencies) rather than embedding a diagram — there is no separate `.md` copy to pull from
- Keep all other README content untouched

**Last action of this step, always, if anything above touched a file the generator reads**
(any `DECISIONS.md`, `backlog/**`, `.claude/commands/*.md`, `.claude/skills/*/SKILL.md`,
`.claude/nav/flows.md`, or `**/pom.xml`): run `bash docs/architecture/scripts/generate-architecture-model.sh` — one
trigger, run once, after every other file this step touches, so
`docs/architecture/data/architecture-model.json`/`docs/architecture/architecture-map.html` never
regenerate from stale input.

---

## Step 5 — Report

Print a summary:
- Which files were updated
- Which issues were created / closed / updated
- Which ADRs were added or status-changed
- Any new violations found

---

## Full Audit Mode (`--full-audit`)

Skip Steps 1-2 entirely (no `git diff`, no changed-file mapping) — every `README.md`,
`DECISIONS.md`, and `CLAUDE.md` in the repo is in scope regardless of recent activity. `CLAUDE.md`
was added to full-audit scope after a direct audit (2026-07-16) found the repo-root `/app/CLAUDE.md`
wrongly claiming `taxon-spring-boot-starter` had no `DECISIONS.md` when it actually did (77 lines,
4 real ADRs) — the default diff-based mode would never catch this class of drift, since a
module-listing claim in a *different* file than the one that changed never appears in any single
commit's diff.

### Step A0 — Regenerate the ADR index

Run `bash .claude/nav/scripts/generate-adr-index.sh` and check whether it changed `.claude/nav/adr-index.md`
(`git diff --stat .claude/nav/adr-index.md`) — if so, a `DECISIONS.md` entry was added/changed since
the index was last regenerated. Include this in the Step A5 report regardless of outcome.

### Step A1 — Enumerate targets

`find . -maxdepth 3 -iname "DECISIONS.md" -o -maxdepth 3 -iname "README.md" -o -maxdepth 3 -iname
"CLAUDE.md"` (excluding `target/`/`node_modules/`) — `maxdepth 3`, not `2`: nested tool
directories (`scripts/ci/`, `scripts/sonar/`, `.claude/nav/scripts/`) each carry their own `DECISIONS.md`/
`README.md` one level deeper than top-level modules; `maxdepth 2` silently skips all three
(confirmed directly). Note which modules have no README.md, no
DECISIONS.md, or no CLAUDE.md — that alone is not necessarily a problem (e.g. a pure-contracts
module may not need a README), but flag it in the report rather than silently skipping.

### Step A2 — Verify every claim, not just changed ones

For each `DECISIONS.md`, check every ADR entry:
- If it names a specific class, method, field, config value, CSS class, or file path — grep/read
  the current source directly and confirm the name still exists and still matches. Do not assume
  from the ADR text.
- Classify each entry: **VALID** (matches), **STALE** (named thing renamed/moved/removed since),
  **SUPERSEDED** (a later ADR replaces this one's decision but the Status line doesn't say so),
  **DONE-GOAL-NOT-MARKED** (an "open"/"future work" item that already shipped).
- Check structural hygiene: ADR numbering has no gaps/duplicates, every entry has a `Status:` line,
  Status vocabulary is consistent (`Accepted`/`Superseded`/`Deprecated`, with dates where useful).

For each `README.md`, cross-check every concrete factual claim (class names, package paths,
commands, table names, port numbers, config values, dependency versions) against current
code/config directly — the same way ADRs are checked. A module's own `CLAUDE.md` is usually the
more current source when the two disagree (verify which one is actually right by reading code,
don't assume CLAUDE.md wins by default) — note any case where `CLAUDE.md` itself turns out to be
the stale one, since it's the one place this skill otherwise treats as ground truth.

For each `CLAUDE.md` (repo-root and per-module), cross-check every concrete factual claim the same
way — class/package names and paths, "What it owns" file lists, cross-module dependency claims
(e.g. which starters have/don't have a `DECISIONS.md`, which starters another module optionally
depends on), config values, naming-convention examples. Pay particular attention to claims *about
other files or modules* (e.g. the repo-root `CLAUDE.md`'s module-layout summary, or a starter's
`CLAUDE.md` describing what another module implements) — these are the claims most likely to drift
silently, since fixing the referenced file/module doesn't naturally prompt anyone to revisit the
claim describing it from outside.

### Step A3 — Parallelize for cost

Given the volume (every ADR/README/CLAUDE.md claim needs an independent code cross-check, not a
batch grep), split the audit across parallel research agents — group by size, not just by module,
so no single agent gets an oversized file. As a rule of thumb: one agent per ~500-1000 lines of
combined DECISIONS.md content, one agent for all README.md files together, one agent for all
CLAUDE.md files together (these tend to be shorter than DECISIONS.md but numerous — one pass
covering all of them is usually cheaper than folding them into the DECISIONS.md agents). Each
agent reports findings only (file, line, claim, verdict, evidence) — it does not edit anything;
the calling context reviews and applies fixes afterward, the same way a normal code-review pass
would.

### Step A4 — Apply fixes

Same rules as Step 4 above (ADR audit / CLAUDE.md factual corrections / README cross-reference),
plus:
- When a decision the ADR rejected turns out to be what the current code actually does (not just
  a naming drift, but a reversed decision), do not silently "fix" the code to match the old ADR.
  Annotate the ADR to document current reality and flag the discrepancy in the report — reverting
  working code is a separate, deliberate call for whoever owns that script/service to make.
  Scripts and test files are exactly where the temptation is highest to change code instead of
  wording, and exactly where an unreviewed change is most likely to break something unrelated to
  documentation. Never touch code in `--full-audit` mode. Documentation always changes to match
  code, never the reverse.

### Step A5 — Aggregate operational self-tracking notes

Per `.claude/rules.md`'s "Final reports record real operational data in a fixed, mechanically-
parseable block" rule, completed issues carry a `## Operational notes` block with fixed `key:
value` lines (`token_cost_review`, `token_cost_research`, `token_cost_verification`,
`context_loading_task_type`, `context_loading_consulted`, `context_loading_matched`,
`flows_situation`, `flows_chosen`, `flows_matched`). Grep `backlog/completed/issues/` (and
`backlog/issues/` for in-progress work) for `## Operational notes` blocks added since the last
full-audit and aggregate mechanically (parse the `key: value` lines directly, the same way
`generate-adr-index.sh` parses `## ADR-NNN:`/`**Status:**` — do not rely on prose parsing):
- Token cost trend by purpose (`token_cost_review`/`research`/`verification`) across the sampled
  issues — sum and average, `n/a` entries excluded from the average, not treated as zero.
- `context_loading_matched` yes/no/n/a tally, grouped by `context_loading_task_type`.
- `flows_matched` yes/no/n/a tally.
Feed the result into `.claude/nav/`'s own governing rule — no new navigation content until this data
shows the existing layer earns its cost: this is real evidence for or against expanding/trimming
the navigation layer, not a synthetic exercise. If too
few blocks have accumulated since the last audit to say anything meaningful, report that plainly —
absence of data is itself a finding (the recording practice may not be happening consistently).

### Step A6 — Report

Same shape as Step 5, plus: a per-file table of ADR verdicts (VALID count vs. each flagged entry
with a one-line reason), the same per-file verdict shape for every README.md and CLAUDE.md
checked, an explicit list of anything found stale in code itself (not just in docs) that was
deliberately left unfixed per the rule above, and Step A5's operational self-tracking aggregate.

---

## Documentation Rules (from SPEC)

- Do NOT invent architecture — only document what exists in code
- If something cannot be proven from code, mark it UNKNOWN
- All diagrams must use Mermaid syntax
- All conclusions must reference actual class names or file paths
- ADR format: see `.claude/commands/record-decision.md` step 3
- Issue format: see `.claude/commands/feature.md` step 3
- Never duplicate issues — update existing ones rather than creating new
