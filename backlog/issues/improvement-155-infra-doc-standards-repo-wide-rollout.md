# improvement-155: Repo-wide script documentation convention rollout — self-contained script header, README describes flow, architecture-map surfaces both live

**Type:** improvement — design done, real application rolled out to `scripts/sonar/` and
`scripts/build-and-test/` only so far; the rest is not started.
**Module:** every repo script (`scripts/**/*.sh`, `*.bat`, `docker-compose*.yml`, `*.properties`),
`.claude/skills/infra-doc-standards/SKILL.md`, `docs/architecture/scripts/generate-architecture-model.sh`
(`script_headers_json()`).
**Priority:** Top — explicit placement, ranked directly after `improvement-154`.
**When:** independent, no blockers.

Split out of `improvement-152` Part E once that issue's own Part A/D work was ready to close —
same "split once the parent issue's real work is done" pattern `improvement-152` itself was split
out of `improvement-151` under.

## Design (already shipped)

Lives in its own dedicated skill (`.claude/skills/infra-doc-standards/SKILL.md`, split out from
`doc-standards` once the two topics — Java-app docs vs. infra/script docs — turned out to need
separate ownership; `doc-standards` keeps a one-line "out of scope, see infra-doc-standards"
pointer, no duplicate content between the two). Covers: the 7-field file-level header
(`Description`/`Usage`/`Uses`/`Env`/`Input`/`Outputs`/`Returns`), per-function headers for
`source`d library files, `.bat` delegator forwarding (`same as <file>`), Dockerfile and
`.properties` field adaptations, and the `README.md` "Flow" section (Mermaid diagrams,
multi-entry-point handling, condition-tracing rule). Grounded in the Google Shell Style Guide and
ISO/ANSI flowchart notation, both confirmed via live web search, not assumed.
`docs/architecture/scripts/DECISIONS.md` ADR-032 covers the generator-side mechanism
(`script_headers_json()`, generalized from the original `docs/architecture/scripts`-only version)
and two real bugs found and fixed while verifying it (extractor over-scanning past the header
block; header must sit immediately after the shebang, before `set -e`). `scripts/sonar/DECISIONS.md`
ADR-008 covers a real, applied feature that came out of this same work: `run.sh` now
auto-validates `sonar-project.properties`'s module list against `pom.xml` before every run.

## Done, applied for real (only these two directories so far)

1. ✅ **`scripts/sonar/`** — all 4 files (`docker-compose.sonar.yml`, `run.bat`, `run.sh`,
   `sonar-project.properties`) carry real, finalized headers (7-field for `.sh`, delimiter-framed;
   6-field for `docker-compose.yml`; 3-field for `.properties`; `run.bat`'s `same as run.sh`
   forwarding template). `README.md` rewritten with the `## Flow` section (2 entry points
   converging into one Mermaid diagram, 3 decision diamonds tracing real conditional behavior —
   image recreate, DB-migration wipe, token regen, module-list auto-fix). Verified end to end:
   `bash scripts/sonar/run.sh --no-gate` still reaches `ANALYSIS SUCCESSFUL` after all header edits.
2. ✅ **`scripts/build-and-test/`** — headers and README Flow diagram applied as the underlying
   code evolved through this same session's build/test consolidation work.
3. ✅ **Generator regenerated and re-verified after both.** Surfaced two more real bugs in
   `script_headers_json()` while doing so (`Output`→`Outputs` rename never reached the parser's own
   regex; `Env`/`Returns` were never in its field list at all — see
   `docs/architecture/scripts/DECISIONS.md` ADR-032's third/fourth bug notes) — fixed and
   re-verified via screenshot: all 7 header columns render correctly, with each file-type's
   deliberately-omitted fields (e.g. `Returns` for Docker, 4 fields for `.properties`) rendering
   empty rather than absorbing a neighboring field's content.

**Still open on these two:** remove the `scripts/sonar`-specific illustrative example content from
`infra-doc-standards/SKILL.md` now that the real files carry the real headers (the skill's own copy
is now a `doc-standards`-style "one fact, one canonical home" duplicate) — needs explicit
confirmation before editing the skill again.

## Not yet started — the actual repo-wide rollout

Roll the same convention onto every other script directory:
- `docs/ai/scripts/` (moved from `scripts/ai/` this session — see `scripts/DECISIONS.md` ADR-002's
  annotated exception)
- `scripts/ci/`
- root `scripts/*.sh`/`*.bat`
- `playwright/` (see its own two-level design note below)
- the new `scripts/deploy-and-run/` (in progress, same session)

### `playwright/` — two-level directory structure, design discussed, not started

`playwright/` is the first `SCRIPT_GROUP_DIRS` candidate with real nested content the current
`-maxdepth 1` file-glob never reaches: `playwright/e2e/` (7 `.spec.js` + `_helpers.js`) and
`playwright/e2e/_flows/` (14 `.flow.js`, `require()`d by 2+ spec files — the JS analogue of the
existing "per-function header for files `source`d by other scripts" convention). `playwright/e2e/`
also has its own `README.md`, a test catalog — a genuinely different document shape from the
`## Flow` call-chain section the convention defines for a script directory's `README.md`.

**Design direction agreed in chat, not yet implemented:** card-then-drill-down, one level at a
time — not stacking `playwright`/`e2e`/`_flows` as multiple sections on one page. Clicking the
"Playwright" card on the Tooling & Pipelines list shows only `playwright/`'s own top-level files
(`run.sh`/`run.bat`/`playwright.config.js`/`reporter.js`) + its own `README.md`, plus one
subdirectory card ("e2e"). Clicking that card opens its own page: `playwright/e2e/`'s own files +
its own `README.md` (the existing test catalog, unchanged), plus one further subdirectory card
("_flows"). Clicking that opens `playwright/e2e/_flows/`'s own files, no further subdirectory
cards. Each level is `SCRIPT_GROUP_DIRS`'s existing flat, `-maxdepth 1`-per-directory shape —
nothing new needed for file discovery — the new part is a drill-down screen (generalizes the same
card→detail pattern already used for the System module screen and the Diagrams groupKey screen)
that resolves child cards purely from other `SCRIPT_GROUP` node ids sharing the parent id as a
prefix (`playwright/e2e` is a child of `playwright`; `playwright/e2e/_flows` is a child of
`playwright/e2e`) — no hardcoded "e2e"/"_flows" names in the renderer.

Still open, not decided:
- Whether `.spec.js`/`.flow.js` files get a lighter-weight header-field adaptation (most fields
  `None`, `Description` + a pointer to `e2e/README.md`'s own per-test detail) instead of the full
  7-field script header — proposed in chat, not confirmed.
- Exact mechanics of the new drill-down screen/router state (a new `screen` type vs. extending the
  existing `pipelines` screen with a path array) — not designed yet, only the card-per-level UX is
  agreed.

### Skill improvements raised while applying it to `scripts/ci/` (2026-08-18) — not yet actioned

- The skill's own file-type coverage (script/`.bat`/Dockerfile/`.properties`) has no dedicated,
  strict comment-convention section for YAML files, Python files, or JavaScript files — all three
  were touched applying the skill to `scripts/ci/` (`ci.yaml`, `pipeline-metrics.py`) without one
  to follow, improvised ad hoc instead. Needs a real section per file type, not an improvisation
  each time a new language shows up.
- A reminder/rule that applying the skill to one directory should stay bounded to that directory,
  not follow every adjacent stale reference found elsewhere in the repo while working — a real
  tendency observed this session (fixing unrelated stale references in other files while the skill
  was scoped to `scripts/ci/`).
- Organize the skill's file-type sections into a tree-shaped hierarchy — raised in chat, not
  specified further; revisit what this means concretely when this item is actually picked up.

## Original problem statement

While fixing `scripts/sonar`'s README/comments, a top-of-file "Usage:" comment got trimmed down to
a bare pointer at `README.md` ("see README.md 'How to run'") under the "one line or none"
code-comment rule. Rejected: a script's own top comment must be self-contained — what it does,
what parameters are valid, what result to expect — not a redirect to another file. This surfaced a
real, repo-wide gap: the original `docs/architecture/scripts/*.sh`/`*.js` header convention
(`Description:`/`Uses:`/`Input:`/`Output:` — see `docs/architecture/scripts/DECISIONS.md` ADR-022)
was (a) undocumented as a standing rule anywhere outside that one generator function's own code,
(b) hardcoded to only scan that one directory, never applied elsewhere, and (c) had no field for
valid CLI parameters/flags.

### Research grounding (2026-08-14, web search)

Checked against real bash-scripting convention before finalizing: the
[Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html) — the most widely
cited authoritative bash convention — requires a top-of-file header comment (brief overview) and,
for functions, a structured block: `Description`, `Globals`, `Arguments`, `Outputs`, `Returns`.
This validates both the general shape (structured header block, not prose) and specifically the
missing field this issue added — Google's `Arguments` is exactly the `Input:`/`Uses:` fields'
missing sibling for "what CLI flags does invoking this take."

### Decision — three-layer documentation, one canonical home each

1. **Script's own top-of-file header — self-contained, mechanically parsed.** Every script gets a
   structured header, extending the original 4-field convention with a 5th field: `Description:` /
   `Usage:` (valid CLI parameters/flags) / `Uses:` / `Input:` / `Output:`. A deliberate, explicit
   exception to the "one line or none" code-comment rule (a structured metadata block, not prose
   rationale).
2. **README.md (per script-group directory) — describes the *flow between* scripts, not each
   script's own behavior again.** One script's output frequently feeds another's input (e.g.
   `scripts/sonar.sh` → `scripts/sonar/run.sh`; `scripts/ci.sh` → `scripts/ci/run.sh` →
   `scripts/build-and-test.sh`/`deploy-and-run.sh`+`playwright/run.sh`/`sonar.sh`). README's job is
   to document that chain — what calls what, in what order, what the end-to-end result is —
   referencing each script's own header instead of restating its Description/Usage/Output.
3. **`architecture-map.html`'s Tooling & Pipelines cards — renders both live, no third copy.**
   `architecture_tooling_self_docs_json()` (renamed/generalized, no longer hardcoded to one
   directory) scans every `SCRIPT_GROUP_DIRS` entry's own files for this header, parses the 5
   fields, and renders a per-file table on that group's own card. The README's flow/chain content
   renders alongside via the already-built `readme_json_for()` + `mdBlockToHtml()` path — two
   distinct sections on one page, sourced from two distinct files, no restatement between them.

Per `.claude/rules.md`'s "state the abstract principle, not a case study" rule, the concrete
5-field spec and the "header vs. README vs. architecture-map" layering lives in
`.claude/skills/doc-standards/SKILL.md`, not restated as a case study anywhere else.

## Related

- `improvement-152` Part E — the section this issue was split out of.
- `docs/architecture/scripts/DECISIONS.md` ADR-022 (the original 4-field convention), ADR-032
  (the generalized `script_headers_json()` mechanism and the bugs found while building it).
- `scripts/sonar/DECISIONS.md` ADR-008 (the real feature — module-list auto-validation — that came
  out of applying this convention to `scripts/sonar/`).
- `scripts/DECISIONS.md` ADR-002 (annotated exception covering the `scripts/architecture` →
  `docs/architecture/scripts` and `scripts/ai` → `docs/ai/scripts` moves referenced throughout this
  issue's file paths).
