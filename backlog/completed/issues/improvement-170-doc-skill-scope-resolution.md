# improvement-170: doc-standards vs infra-doc-standards/infra-readme-standards — scope resolution

**Type:** improvement
**Module:** `.claude/skills/module-doc-standards/SKILL.md`, `.claude/skills/module-readme-standards/SKILL.md`,
  `.claude/skills/infra-doc-standards/SKILL.md`, `.claude/skills/infra-readme-standards/SKILL.md`,
  `.claude/skills/app-readme-standards/SKILL.md`, `.claude/nav/`, `docs/architecture/`, root `README.md`,
  root `INFRASTRUCTURE.md`, root `CLAUDE.md`, `.claude/rules/`
**Priority:** Top 🔵
**When:** independent, no blockers

## Current state

Three documentation-convention skills exist today:

- `infra-doc-standards` — file-level + per-function header conventions for infrastructure/tooling
  files (bash/bat/docker-compose/.properties/yaml/js/py). Explicitly scoped to tooling, not "documentation
  about the app itself."
- `infra-readme-standards` — README/Flow-diagram conventions for a script-group directory's own
  `README.md`, sibling to `infra-doc-standards` (file convention vs. directory-README convention,
  two separate skills).
- `doc-standards` — a single checklist (fact/constraint test + a "canonical home" ownership table)
  covering, per its own frontmatter: `CLAUDE.md`, root `README.md`, `docs/architecture/*.md`,
  `.claude/nav/*.md`, `.claude/commands/*.md`, `.claude/rules.md`, `SKILL.md`/`references/*.md` — all
  in one skill, no file-vs-README structural split.

The infra pair has already been rolled out to the *tooling* side of both `.claude/nav/` and
`docs/architecture/` — the shell scripts in `.claude/nav/scripts/` and `docs/architecture/scripts/`
follow `infra-doc-standards`'s header convention (improvement-155, improvement-161/162 tracking
the rollout). That leaves the *content* side of those same two directories (`.claude/nav/*.md`,
`docs/architecture/*.md`) currently governed by `doc-standards` instead.

## Why change

`doc-standards` was originally conceived more narrowly — for documentation describing the
Java/Vaadin application and its modules (`CLAUDE.md`, a module's own `README.md`) — not as a
catch-all for every kind of project documentation. Its current scope already reaches further:
root `README.md` (a project-wide landing page, external-facing audience) and `.claude/nav/*.md` +
`docs/architecture/*.md` (structured navigation/architecture material, closer in shape to
`infra-readme-standards`'s directory-level "sequence between things" content than to a single
module's own description) are folded into the same one skill, with no acknowledgment that these
may have different audiences, different content shapes, or benefit from the same file-vs-README
structural split the infra pair already uses. The boundary needs to be explicitly designed, not
left as an accretion of frontmatter globs.

## Expected benefit

Each documentation domain gets a skill whose name and scope actually matches what it governs,
closing the ambiguity surfaced in conversation (which skill decides content for `.claude/nav/*.md`,
`docs/architecture/*.md`, or the root `README.md`) — applying `doc-standards`'s own "one fact, one
canonical home" principle to the skills themselves, not just to the facts they check.

## Approach

Landed on a refined version of Option C, worked out piece by piece and grounded against the real
file inventory (not the abstract A/B/C framing first drafted). Three targets: item 1 done, item 3's
skill done (content migration still open), item 2 scoped but not yet implemented:

**1. `doc-standards` → `module-doc-standards` + new `module-readme-standards` (done 2026-08-25,
superseding an earlier same-day version of this item).**
Went through two designs the same day. First pass narrowed `doc-standards` to `CLAUDE.md` +
`.claude/rules/*.md` + module `README.md` combined in one skill (matching the original "code"
framing literally). Second pass replaced that with a closer mirror of the infra pair, once the
real distinction became clear: `module-doc-standards` now covers only Java source files' own
Javadoc/comments and `pom.xml` dependency comments (the atomic-unit level, analogous to
`infra-doc-standards`'s file headers) — a full rewrite, not the CLAUDE.md/rules/README content
from the first pass. A new sibling skill, `module-readme-standards`, was created for a Java
module's own `README.md` specifically (facts that don't fit inside any single file's Javadoc),
formalizing the `What it provides`/`Key classes`/`Dependencies` shape the 7 starter modules had
already independently converged on (verified directly — `advertisement-`/`user-spring-boot-starter`
match exactly; `query-lib`/`marketplace-app` diverge for real structural reasons, kept as
allowed variation, not forced into the template).
Root `CLAUDE.md` and `.claude/rules/*.md` ended up **not** covered by either new skill — they're
instructions for Claude's own operation, not documentation about Java code or a module's own
`README.md` — explicitly marked "not yet covered by a dedicated skill" everywhere they're
referenced, pending item 2 below.

**Consolidation found and fixed along the way (not originally scoped, surfaced during this same
work, across both passes):** the "one fact, one canonical home" principle itself was independently
stated in two places — `doc-standards/SKILL.md`'s "The core rule" section and
`infra-doc-standards/SKILL.md`'s "⛔ One fact, one canonical home" section (a distinct,
infra-specific application: file-header-before-README ordering, not the general principle, but
reusing the same name). Fixed by adding one rule to `.claude/rules.md` ("⛔ One fact, one canonical
home — the single governing rule for all documentation", with "single source of truth" as a
synonym for searchability), later extended with a second part ("Atomic unit first, then
directory-level index") once the module pair's design made clear the same ordering discipline
would otherwise need restating a third time for Java/`pom.xml` vs. module `README.md`.
`infra-doc-standards`'s section renamed to "⛔ Files first, then README" (its own specific
application) with all internal cross-references (in both `infra-doc-standards` and
`infra-readme-standards`) updated to match.

**Known gap surfaced by this split, not yet resolved:** the original `doc-standards`' "Canonical
ownership table" (module dependencies → `architecture-map.html`, SPI mapping, ADR rationale →
`DECISIONS.md`, task routing → `context-loading.md`, situation routing → `flows.md`, backlog issue
format, cross-cutting rules) was general/cross-cutting content that doesn't fit either new skill's
narrower domain (not Java-comment-specific, not module-README-specific) — it was removed from the
live `module-doc-standards/SKILL.md` when that file was rewritten and currently has no home. Its
rows almost all point at `.claude/nav/`/`docs/architecture/` files or command files, so it logically
belongs inside item 2's future skill — flagged here so it isn't silently lost; still recoverable
from this file's own git history (`doc-standards/SKILL.md`'s pre-rewrite version) when item 2 is
picked up.

Ripple-effect edits made across both passes (grep-verified against the live repo throughout):
`.claude/skills/doc-standards/` renamed (`git mv`) then rewritten; `.claude/rules.md`'s
"Documentation Standards" section and code-comment rule; `infra-doc-standards/SKILL.md` and
`infra-readme-standards/SKILL.md` intros + internal cross-references; `.claude/nav/flows.md`'s
"Project commands & skills" table (now 5 rows: `module-doc-standards`, `module-readme-standards`,
`infra-doc-standards`, `infra-readme-standards`, and an explicit "not yet covered" row for
`CLAUDE.md`/`.claude/rules/*.md`).

Currently-open backlog issues that mention `doc-standards` in passing (133, 135, 138, 160, 161,
162) and `docs/architecture/scripts/DECISIONS.md`'s historical entries — left untouched; incidental
mentions in issues/decisions about other work, not current-state documentation this repo's citation
rules govern.

**Follow-up pass (same day):** grounded `module-doc-standards` against real usage and external
standards before considering it finished:
- Web research: Oracle's official Javadoc guide (first-sentence-is-everything convention, already
  matches this project's "one line or none" rule — cited as the base standard, mirroring how
  `infra-doc-standards` cites the Google Shell Style Guide); Maven `pom.xml` comment best practices
  (no single authoritative spec exists, multiple independent sources converge on "comment only the
  non-obvious", already matched the design); Liquibase changelog best practices (surfaced the
  separate `<comment>` element as a real option this repo doesn't currently use — flagged, not
  adopted).
- Found and fixed a real gap: `*.spi` interface Javadoc is mechanically parsed by
  `generate-architecture-model.sh`'s `spi_javadoc_purpose_for()` into the SPI Map diagram — this
  was completely missing from the skill's first draft. Now referenced (not restated — the
  mechanical parsing detail stays canonical in `.claude/rules/platform-commons.md`) as a required,
  sanctioned exception to "one line": a wrapped paragraph is "one logical statement," which the
  skill now defines explicitly (resolves an ambiguity the SPI convention's own multi-physical-line
  example would otherwise seem to contradict).
- Extended scope to Liquibase changelogs' `remarks=` (referenced from root `CLAUDE.md`, not
  restated) as this skill's third atomic-unit type, alongside Java and `pom.xml`.
- Added an "Applying this standard — what running the skill over a module means" section (mirroring
  `infra-readme-standards`'s equivalent), covering the whole module's Javadoc/`pom.xml`/Liquibase,
  not just newly-written lines — the mechanism the real violation below is expected to be caught by
  next time this skill actually runs over that module, not fixed by hand now.

**Real violation found while grounding this (not fixed — deferred to a future skill run, per
explicit direction):** `attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentCleanupService.java`'s
`deleteAttachments`-adjacent method carries a 5-line Javadoc block citing an issue number
("improvement-049 item 4") — violates both the ticket-number ban and, arguably, the one-statement
form. Left as-is intentionally; the "Applying this standard" mechanism above is meant to catch this
class of finding whenever `module-doc-standards` is actually run over
`attachment-spring-boot-starter`.

**`pom.xml`/Liquibase header-treatment resolved (same day, second follow-up):** applied the
already-established "type-level constant, field omitted, stated once" pattern (Dockerfile's
`Returns`, `.properties`' `Uses`/`Input`/`Outputs`/`Returns`) to its logical conclusion — `pom.xml`
and each module's Liquibase **master** changelog (`*-changelog-master.xml`, always just an
`<include>` list) get **no file-level header at all** (every field would be constant boilerplate);
only individual Liquibase **change** files (`changes/NN-*.xml`) carry a genuine per-file fact and
get one — exact header shape still not designed, flagged rather than invented inline. An earlier
version of this analysis incorrectly framed `pom.xml`'s `Description` as "duplicating
`module-readme-standards`" — corrected: the real issue was low information value (near-identical
text across all 11 `pom.xml` files), not a canonical-home conflict; `pom.xml`'s own facts (build
descriptor identity) and README's `What it provides` (business capabilities) were never the same
fact to begin with.

**2. New skill for `.claude/nav/*.md` + `docs/architecture/data/*.md` + the commands/rules/skills
operational tier (scope decided and corrected, not yet named or implemented).**
Confirmed by inspecting the real files: `.claude/nav/README.md` already exists and organically
converged on a content-index shape (a table: File | What | Why it exists | Where it fits | When to
consult | How it stays fresh) — not a Mermaid Flow/call-chain diagram, because there's no
script-to-script execution order here, just a curated content set.
**Scope corrected after inspecting `docs/architecture/`'s real layout** (initially assumed to be
one flat bucket — wrong): `docs/architecture/scripts/` is a genuine script-group directory
(`generate-architecture-model.sh` and siblings, real entry points) — that's `infra-doc-standards`/
`infra-readme-standards` territory already (improvement-162's rollout candidate), not this skill's.
`docs/architecture/` top level (`architecture-doc.sh`/`.bat` as thin delegators +
`architecture-map.html` as generated output) matches the same thin-entry-point-plus-subdirectory-
logic shape `.claude/rules.md`'s "Script-group directory structure" already describes — also likely
`infra-readme-standards`' job, not a new skill. `docs/architecture/data/` already has its own
`README.md` and real hand-written content (`arch-embed-index.md`, `runtime-notes.md`) — this is the
actual "content" bucket this skill covers, not the whole `docs/architecture/` tree.
Given the earlier "docs/ai is more logical" decision, `.claude/commands/*.md`, `.claude/rules.md`,
and `SKILL.md`/`references/*.md` still move here too, growing this skill's scope beyond just
`.claude/nav/` + `docs/architecture/data/`.
Not yet named; not yet decided whether it stays one skill or needs its own internal split once its
full corrected scope is actually drafted. Also now needs to absorb the orphaned "Canonical ownership
table" content flagged in item 1 above — that table's fact types (module dependencies, SPI mapping,
ADR rationale, task/situation routing, backlog issue format, cross-cutting rules) are exactly the
kind of cross-cutting, `docs/ai`-adjacent facts this skill already covers in principle.

**Actual resolution (done 2026-08-25, driven by making `.claude/`'s own file headers/READMEs
reachable through `architecture-map.html` — see item 9): no new skill was created.** The ambiguity
resolved by extending the existing infra pair directly instead. `infra-readme-standards` gained a
`.claude/skills/` section (top-level index only, never a per-skill `README.md` — each skill's own
`SKILL.md` is already that unit's complete description). `infra-doc-standards` was applied as-is to
`.claude/nav/scripts/*` (5 files, full structured headers — the existing convention already covered
these file types, no new skill needed). Real README content was written/rewritten under the
existing `infra-readme-standards` discipline for `.claude/nav/README.md`, new
`.claude/commands/README.md`, `.claude/rules/README.md`, and new `.claude/skills/README.md`.
`docs/architecture/data/*.md` was **not** touched by this resolution — still open if its own
content-governance gap resurfaces. The orphaned "Canonical ownership table" content flagged above
is still homeless — not picked up by this resolution either, remains a real gap.

**3. Root `README.md` + new root `INFRASTRUCTURE.md` (done 2026-08-25 — skill created, file
created, content migrated).**
Resolved: root gets two sibling files, not one — `README.md` (existing, marketing/git-visibility
pitch) and `INFRASTRUCTURE.md` (new, technical infra overview: Docker Compose stack, deploy
scripts, how the pieces fit together). New skill `.claude/skills/app-readme-standards/SKILL.md`
governs both. `README.md` is a deliberate, explicit exception to "one fact, one canonical home" —
restating a fact (even one that also lives in root `CLAUDE.md`) is allowed when it serves the pitch.
`INFRASTRUCTURE.md` follows the standard discipline strictly (references `scripts/README.md` etc.,
never restates). No sibling `app-doc-standards` — repo root has no `.java`/`pom.xml`/Liquibase files
of its own.
**Migration completed (2026-08-25):** `Running Locally`, `Helper Scripts`, `Database Scripts`,
`Environment Variables`, `Running Without Docker`, `AI-Assisted Development Workflow` moved
verbatim into new `INFRASTRUCTURE.md` (170 lines). Root `README.md` (346 → 187 lines) keeps only a
short `Running & Infrastructure` quickstart/pointer section in their place, plus a fixed
cross-reference in `Testing Strategy` (previously pointed at the now-removed `#helper-scripts`
anchor).

**Follow-up: within-`README.md` redundancy found and merged (2026-08-25, separate from the
migration above — this is content the marketing exemption doesn't cover, since it's two pitch
sections restating each other, not a pitch section restating a technical file).** `What can users
do?` and `Feature Highlights` both listed the same product capabilities in different words — merged
into one `Feature Highlights` section, topic-grouped, no fact dropped. `Engineering Highlights` and
`Architectural Principles` also merged into one `Architectural Principles` section — `Auditing`'s
content folded into `Immutable data flow` (the same underlying fact: immutable versioned snapshots),
the rest kept as their own bullets. Top-of-file anchor nav's `[Engineering Highlights]` link removed
(heading no longer exists). `README.md`: 187 → 178 lines.

**Side effect discovered while resolving item 3 (not originally scoped, done 2026-08-25):** briefly
considered a directory-scoped `app-doc-standards` (root-only comment conventions, mirroring
`module-doc-standards`), then found a real jurisdiction conflict — `infra-doc-standards` is already
scoped by *file type*, not directory, so root `Dockerfile`/`Dockerfile.ai` already belong to it
regardless of location. Resolved by extending `infra-doc-standards` instead of creating a competing
directory-scoped skill: `.env`/`lombok.config` now explicitly share the existing `.properties`
three-field table (same `KEY=VALUE` shape); a new `.gitignore`/`.gitattributes` section added
(pattern-list files, no file-level header, one-line comment per logical pattern group instead).
`mvnw`/`mvnw.cmd`/`mvn.bat` (Maven-wrapper-generated) explicitly out of scope — not hand-maintained,
not commented.

**`infra-doc-standards` actually applied to the repo root (done 2026-08-25):** ran the extended
convention over every root-level infra file. `Dockerfile` got a full structured header (was
missing entirely). `Dockerfile.ai` — normalized CRLF→LF (matching the rest of the repo) then
replaced its free-form comment block with a structured header, dropping a hardcoded
machine-specific Windows path (`D:\Ost\dev\Advertisement\`) that had no place in checked-in
documentation. `.env` — reformatted its existing prose comment into the 3-field shape and dropped
a direct citation of `backlog/completed/issues/improvement-044-...md`, a real violation of the
"header fields never name a real file as a pointer" rule once `.env` fell under this skill's scope.
`lombok.config` — added the 3-field header (had none). `.gitignore` — added a missing group comment
above the IDE-files block, and dropped an `(improvement-059)` ticket citation from an existing group
comment. `.gitattributes`/`.dockerignore` — reviewed, no change needed (both short and
self-explanatory, "none" is a valid outcome).
**Found, not fixed (flagged only, not a comment/header issue):** `.gitignore` lines 56-57
(`/user-spring-boot-starter/target`, `/advertisement-spring-boot-starter/target`, no trailing
slash) duplicate entries already covered by the "Maven build output" block above — a real
functional redundancy in the ignore rules themselves, out of scope for a header/comment pass.

**4. `docs/ai/` → `.claude/nav/` (done 2026-08-25).**
The old `docs/ai/`'s content (`README.md`, `context-loading.md`, `flows.md`, `adr-index.md`,
`scripts/`) was "instructions for Claude's own navigation/operation" — the same conceptual bucket
item 2 already groups `.claude/commands/*.md`/`.claude/rules.md`/`SKILL.md` into. Decided to
physically move it into `.claude/`, not just group it there conceptually — renamed to
`.claude/nav/` rather than `.claude/ai/` or `.claude/docs/ai/`, since "AI" is redundant inside
`.claude/` itself; `nav` mirrors the directory's own self-description ("AI navigation layer").

**Functional-script check done before executing (grep-verified, not assumed):** of every file that
looked "functional" at first pass, only one actually had a real functional dependency on the
literal path — `docs/architecture/scripts/generate-architecture-model.sh` (`ADR_INDEX=`/`FLOWS=`
variables, `SCRIPT_GROUP_DIRS` array). Every other candidate (`playwright/playwright.config.js`,
`playwright/run.sh`, `check-architecture-model-freshness.sh`, `scripts/build-and-test/*.sh`,
`scripts/sonar/run.sh`, `scripts/run-all-tests/run.sh`, `scripts/ci/README.md`) only cited
`docs/ai/adr-index.md` inside a comment/prose pointer — the exact sanctioned "see
`docs/ai/adr-index.md`" reference pattern this repo's own ADR-citation rule already requires, not a
real runtime dependency.

**Execution:** `git mv docs/ai .claude/nav` (whole tree, `scripts/` included — the "does
`docs/ai/scripts/` move too" question resolved itself: moving the parent directory moves it,
`infra-doc-standards` governance of those scripts is unaffected by physical location, the same way
`docs/architecture/scripts/` is governed by it regardless of nesting). Then a repo-wide
`docs/ai/` → `.claude/nav/` replace across every tracked file referencing it (74 files,
`git ls-files | grep -v '^backlog/completed/'` as the scope filter) — `generate-architecture-model.sh`'s
3 functional lines verified correct afterward; `.claude/nav/scripts/*.sh`'s own `REPO_ROOT`
computation (`.../../../..`) unaffected, same directory depth as before. `backlog/completed/`'s 18
historical files deliberately left untouched (append-only history, same convention as elsewhere in
this issue). One self-inflicted side effect caught and fixed: the blanket sed also rewrote this
issue file's and `BACKLOG.md`'s own prose *describing* the migration (which necessarily used
`docs/ai/` as the old-path name) — corrected by hand afterward so the historical narrative still
reads correctly.

**5. `architecture-map.html`: root `README.md`/`INFRASTRUCTURE.md` shown inline (done 2026-08-25,
adjacent to this issue's own scope — the tool these two files now surface in).** Investigated
`docs/architecture/scripts/generate-architecture-model.sh`'s real card structure (research agent
plus direct verification, not assumed) before touching a large/critical generator: `renderSystem()`
(hardcoded `.card.special-card` divs) and the `PIPELINE_GROUPS`/`renderPipelines()` pair
(data-driven + bespoke render branches per group). Added a `root_md_json_for()` bash helper
(mirrors `readme_json_for()`, generalized to any root-level file) and two new `MODEL` JSON fields
(`rootReadme`/`rootInfrastructure`).
First pass added these as click-through cards (a 6th System card + a 4th `PIPELINE_GROUPS` entry,
each navigating to its own screen) — **corrected after direct user feedback**: the actual ask was
an inline block at the bottom of the existing page, not a new screen behind a click. Reverted the
cards/screens; `README.md` now renders inline at the bottom of `renderSystem()`'s existing page
(after its `card-grid`), `INFRASTRUCTURE.md` inline at the bottom of `renderPipelines()`'s list
view (after its `card-grid`) — both via the same `mdBlockToHtml()`/`sourceLink()` pattern already
used for every other README on this tool, no separate screen, no new click required. Regenerated
and verified after the correction: valid JSON, both fields still populated with real content.

**6. `infra-doc-standards`/`infra-readme-standards` run over the whole `docs/architecture/` tree
(done 2026-08-25).** `infra-doc-standards` check: all 9 files (`architecture-doc.sh`/`.bat` +
6 files in `scripts/`) already had complete structured headers — nothing to fix.
`infra-readme-standards` found a real gap: **`docs/architecture/data/README.md` was
mis-scoped and stale** — it described the whole tool (System screen, Diagrams, etc.) instead of
just `data/`'s own files, and claimed "no other .md files" here when `arch-embed-index.md`/
`runtime-notes.md` already existed. Also caught the tool description itself citing a "Docker" card
on the System screen that doesn't exist (real cards: Tooling & Pipelines, Backlog, Diagrams, ADRs,
Code Quality) — corrected while moving the content, not left stale in its new home either.
Fixed: `data/README.md` narrowed to its actual 3 files; new `docs/architecture/README.md` (top
level) absorbs the general tool description plus a `Flow` section tracing the real
`architecture-doc.sh` → `generate-architecture-model.sh` (± `check-architecture-model-freshness.sh`,
which re-runs the generator a genuine second time, not once) → `screenshot-architecture-map.sh`
sequence; new `docs/architecture/scripts/README.md` for the actual script-group (traced
`check-architecture-model-freshness.sh`'s and `generate-architecture-model.sh`'s real subprocess
calls into `liquibase-schema-to-json.js`/`md-to-decisions-json.js`, kept `screenshot-architecture-map.sh`
and `md-to-decisions-json.js --extract` as genuinely separate flows rather than forcing one
diagram).
Wired into `architecture-map.html`: `docs/architecture/scripts/README.md` was picked up
**automatically** on regeneration (already-existing `readme_json_for()`/`renderScriptGroupSection()`
machinery, no code change) — confirms the earlier item-2 scope note that this directory was already
`infra-readme-standards` territory. `docs/architecture/README.md` (not `SCRIPT_GROUP`-scoped) got
the same treatment as item 5's inline blocks: new `archDocsReadme` MODEL field, rendered inline at
the bottom of the "Build architecture page" pipeline view. Regenerated and verified: both fields
populated with real content, `docs/architecture/scripts`'s own node's `readme` field non-empty.

**7. "Build architecture page" — folder-cards for `scripts/`/`data/` (done 2026-08-25, direct
follow-up to item 6).** After item 6, "Build architecture page" still just flatly dumped
`docs/architecture/scripts`'s files with no representation of `docs/architecture/data/` at all —
unlike "Scripts" (`renderScriptTree()`), which shows a real folder-card drill-down. Considered
converting `docs/architecture` into a third `SCRIPT_TREE_ROOTS` entry, rejected as overkill — its
only real subdirectories are `scripts/` and `data/`, neither nests further (confirmed by reading
the existing "Only `.claude/nav/scripts`/`docs/architecture/scripts` stay single-level flat" design
comment, which already explains why arbitrary-depth tree wasn't used here in the first place).
Instead: added `docs/architecture/data` as a new `SCRIPT_GROUP_DIRS` entry (category "Build
architecture page", explicit `SCRIPT_GROUP_FILE_ORDER` — the fallback `find` only matches
`*.sh|*.js|*.bat|*.yml|*.yaml|*.properties|Dockerfile`, so `.json`/`.md` files need an explicit file
list or they'd silently show zero files). Verified `script_headers_json()`'s Python parser degrades
safely for non-script files before relying on it — a markdown file's own `# heading` looks
comment-like to the parser, but the code only keeps a file if it finds a literal `Description:`
field, so `arch-embed-index.md`/`runtime-notes.md` correctly produce zero headers, not garbage ones
(confirmed via the real generated JSON, `"headers": []`). Two fixed folder-cards ("📁 scripts",
"📁 data") added to "Build architecture page"'s own view (`view.subGroup` navigation, same
card-then-drill-in shape as `renderScriptTree()`, without its full recursive machinery) — clicking
either renders that one `SCRIPT_GROUP` node via the existing `renderScriptGroupSection()`; the
`docs/architecture/README.md` inline block and the Cytoscape/Mermaid rendering-tech hint stay on
the card-list (root) view, not per-subgroup. Regenerated and verified: 45 nodes (was 44), new
`docs/architecture/data` node has the correct 3-file list, empty `headers`, and a populated
`readme`.

**Item 7 follow-up: two real bugs found and fixed after direct user testing (done 2026-08-25).**
1. **Navigation was actually broken.** Item 7's first pass invented a new `view.subGroup` field for
   the 2-card drill-down, but `navigateBack()` and `renderBreadcrumb()` only ever special-cased
   `view.path` (the mechanism `renderScriptTree()` already uses, fully groupId-agnostic) — clicking
   "← back" from inside `scripts`/`data` skipped straight past the 2-card list, and the breadcrumb
   never reflected the drill-down depth at all. Root cause: introduced a second, parallel
   "current subview" field instead of reusing the one that already worked, exactly the "one fact,
   one canonical home" duplication this whole issue exists to prevent, applied to navigation state
   instead of documentation content. Fixed by replacing `subGroup` with `path: ["scripts"]`/
   `path: ["data"]` — `navigateBack()`/`renderBreadcrumb()` needed zero new code, both already
   handle any `groupId` with a non-empty `path` correctly.
2. **Duplicate content inside the `data` card.** `renderScriptGroupSection(n)` was called without
   `skipHeading=true`, so a heading+chip-row rendered above the README even though `data/`'s files
   have no parsed headers (nothing in the chip row explains anything) and the README right below
   already fully describes the same 3 files in prose — two places, one of them empty-looking, both
   claiming to answer "what's in this folder." Fixed by passing `skipHeading=true`, matching
   `renderScriptTree()`'s own leaf-level call exactly.
Verified for real, not assumed: wrote a throwaway Playwright script, ran it inside the existing
`arch-map-shot` container `screenshot-architecture-map.sh` already leaves warm (raw `docker run`
blocked by a project hook, reused the already-running container instead — no rule bypassed), and
clicked through the actual flow: enter group → 2 cards → click `data` → correct breadcrumb, no
duplicate heading → click back → correct breadcrumb, 2 cards again → click `scripts` → correct
breadcrumb, real file headers shown. All confirmed working, not inferred from reading the code
alone.

**Item 8: header-always policy for JSON/Java/`pom.xml`/Liquibase + `docs/architecture` entry-point
node (done 2026-08-25).** Separate, larger correction, made directly by the user overriding the
"atomic unit may legitimately have no header" framing items 1/6/7 had used: **every** unit capable
of carrying a comment now always does, no "self-explanatory, skip it" exemption for any of them —
`module-doc-standards`'s Java class-level Javadoc exception removed (every class now gets one,
even a self-explanatory `record`), `pom.xml` and the Liquibase master changelog both go from "no
header, type-level constant" to "always carries one" (XML comment before the root tag). JSON is
the one confirmed genuine exception — real technical constraint, not a design choice: standard
JSON (RFC 8259) has no comment syntax at all, so a JSON file's description always lives in its
directory's `README.md` instead (`docs/architecture/data/README.md` already does this correctly).
The central `.claude/rules.md` rule was corrected to match exactly this final split, after an
inconsistent intermediate version (briefly framed JSON's own `generated_by`/`generated_note` fields
as satisfying the header requirement) was caught during a fresh review and reverted.
Follow-up, requested separately: `architecture-doc.sh`/`.bat` (docs/architecture/'s own real entry
points, one level above `scripts/`/`data/`) had no representation in `architecture-map.html` at
all — added `docs/architecture` as a 4th `SCRIPT_GROUP_DIRS` entry and render its node inline at
the root of "Build architecture page" (both real headers + its own README, before the two
folder-cards) — which also let the earlier `archDocsReadme` one-off `MODEL` field/render path be
deleted entirely: `docs/architecture`'s `README.md` is now picked up by the same
`readme_json_for()`/`renderScriptGroupSection()` mechanism as every other `SCRIPT_GROUP` dir, one
fewer parallel path to the same fact.
**Process note, not a design decision:** this item involved several real Approval Rule violations
mid-execution (writing before showing the exact diff, and once counting a directive as an answer
to an already-asked question that was never literally answered) — corrected each time, and
`.claude/rules.md`'s own Approval Rule section gained one clarifying sentence as a direct result
("a directive-sounding instruction is not itself a literal answer to a literal question that was
already asked").

**9. `architecture-map.html`'s "AI Tooling" card rebuilt as a `.claude`-rooted tree, same mechanism
as "Scripts" — hardcoded Commands/Skills tables removed, README made the sole canonical file list,
real markdown-link rendering added (done 2026-08-25, direct trigger for item 2's actual resolution
above; recorded as `docs/architecture/scripts/DECISIONS.md` ADR-033, supersedes ADR-010).**
Concrete gap that triggered this: `.claude/nav/scripts/*`'s newly-written headers (item 1's sibling
work) and the new `.claude/skills/README.md`/`.claude/commands/README.md`/`.claude/rules/README.md`
(item 2's actual resolution above) had no way to be seen through the tool at all — "AI Tooling" was
still ADR-010's original flat Commands/Skills-table design. Full decision rationale, mechanism, and
live-verification detail: see ADR-033. Real bug found and fixed as part of this (a correctness fix,
not a separate design decision): `mdInlineToHtml()`/`mdBlockToHtml()` never supported markdown
`[text](url)` link syntax at all — every README link across the whole tool (not just new ones) was
inert bracket text until this fix.

## Related

- `.claude/skills/module-doc-standards/SKILL.md`, `.claude/skills/module-readme-standards/SKILL.md`,
  `.claude/skills/infra-doc-standards/SKILL.md`, `.claude/skills/infra-readme-standards/SKILL.md`,
  `.claude/skills/app-readme-standards/SKILL.md`
- improvement-155 (repo-wide `infra-doc-standards` rollout, shipped) — established the
  file-header/README-convention split this issue's Option A/C would extend to a new domain.
- improvement-161 (`.claude/nav/scripts/` — `infra-doc-standards` rollout, still open) — the tooling
  side of `.claude/nav/`, already correctly scoped to `infra-doc-standards`; this issue is about the
  content side (`.claude/nav/*.md`) the tooling rollout doesn't touch.
- improvement-137 (`doc-standards` skill creation, completed) — original scope this issue proposes
  narrowing/renaming.
