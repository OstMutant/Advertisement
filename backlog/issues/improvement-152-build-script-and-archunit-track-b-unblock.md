# improvement-152: `scripts/build.sh` (redundant recompile fix) + ArchUnit Track B unblock investigation + SPI Interface Details table redesign + Tooling & Pipelines regroup + repo-wide script documentation convention

**Type:** improvement + investigation + design idea — five independent topics filed together
(three split out of `improvement-151` once that issue's own real (verified) work was done and
ready to close; Parts D and E added later per explicit user request).
**Module:** `.claude/skills/doc-standards/SKILL.md`, `.claude/rules.md`, every repo script
(`scripts/**/*.sh`, `*.bat`, `docker-compose*.yml`, `*.properties`), `scripts/architecture/
generate-architecture-model.sh` (Part E); `scripts/architecture/generate-architecture-model.sh`
(Part D); `scripts/unit-tests.sh`, `scripts/unit-tests/run.sh`, `scripts/integration-tests.sh`,
`integration-tests/run.sh`, `scripts/run-all-tests.sh` (Part A); `marketplace-app/src/test/java/
org/ost/marketplace/architecture/ArchitectureMetricsExport.java`, `scripts/architecture/
generate-architecture-model.sh` (Part B and Part C).
**Priority:** Top — inherits `improvement-151`'s original "explicit user request to rank at the
very top" positioning for Part A; Part B and Part C are investigation/design-only, not yet
actioned; Parts D and E are actioned immediately, ahead of A/B/C.
**When:** Part E — done for `scripts/sonar/` and `scripts/build-and-test/`; rolling onto every
other script (`scripts/ai/`, `scripts/ci/`, root `scripts/*.sh`, `playwright/`) is explicit future
work, not started. Part D — done. Part A — done, see the final-state update at the end of its own
section below. Part B — blocked, see below; not started. Part C — independent, not started.

## Part E — Repo-wide script documentation convention: self-contained script header, README describes flow, architecture-map surfaces both live (design done — `.claude/skills/infra-doc-standards/SKILL.md`; real application to `scripts/sonar/` still pending)

**Design shipped**, in its own dedicated skill (`.claude/skills/infra-doc-standards/SKILL.md`, split
out from `doc-standards` once the two topics — Java-app docs vs. infra/script docs — turned out to
need separate ownership; `doc-standards` keeps a one-line "out of scope, see infra-doc-standards"
pointer, no duplicate content between the two). Covers: the 7-field file-level header (`Description`/
`Usage`/`Uses`/`Env`/`Input`/`Outputs`/`Returns`), per-function headers for `source`d library files,
`.bat` delegator forwarding (`same as <file>`), Dockerfile and `.properties` field adaptations, and
the `README.md` "Flow" section (Mermaid diagrams, multi-entry-point handling, condition-tracing
rule). Grounded in the Google Shell Style Guide and ISO/ANSI flowchart notation, both confirmed via
live web search, not assumed. `scripts/architecture/DECISIONS.md` ADR-032 covers the generator-side
mechanism (`script_headers_json()`, generalized from the `scripts/architecture`-only original) and
two real bugs found and fixed while verifying it (extractor over-scanning past the header block;
header must sit immediately after the shebang, before `set -e`). `scripts/sonar/DECISIONS.md` ADR-008
covers a real, applied feature that came out of this same work: `run.sh` now auto-validates
`sonar-project.properties`'s module list against `pom.xml` before every run.

**Done, applied for real** (was "design-only so far" — now actually executed against
`scripts/sonar/`):
1. ✅ **Applied the skill to `scripts/sonar/`** — all 4 files (`docker-compose.sonar.yml`,
   `run.bat`, `run.sh`, `sonar-project.properties`) carry real, finalized headers (7-field for
   `.sh`, delimiter-framed; 6-field for `docker-compose.yml`; 3-field for `.properties`; `run.bat`'s
   `same as run.sh` forwarding template). `README.md` rewritten with the `## Flow` section (2 entry
   points converging into one Mermaid diagram, 3 decision diamonds tracing real conditional
   behavior — image recreate, DB-migration wipe, token regen, module-list auto-fix).
   Verified end to end: `bash scripts/sonar/run.sh --no-gate` still reaches `ANALYSIS SUCCESSFUL`
   after all header edits.
2. ✅ **Regenerated `architecture-model.json`/`architecture-map.html`.** Surfaced two more real
   bugs in `script_headers_json()` while doing so (`Output`→`Outputs` rename never reached the
   parser's own regex; `Env`/`Returns` were never in its field list at all — see
   `scripts/architecture/DECISIONS.md` ADR-032's third/fourth bug notes) — fixed and re-verified via
   screenshot: all 7 header columns now render correctly on the Sonar card, with each file-type's
   deliberately-omitted fields (e.g. `Returns` for Docker, 4 fields for `.properties`) rendering
   empty rather than absorbing a neighboring field's content.
3. **Still open:** remove the `scripts/sonar`-specific illustrative example content from
   `infra-doc-standards/SKILL.md` now that the real files carry the real headers (the skill's own
   copy is now a `doc-standards`-style "one fact, one canonical home" duplicate) — not done this
   pass, needs explicit confirmation before editing the skill again.

Follow-up beyond `scripts/sonar/` (also not started): roll the same convention onto every other
script in the repo (root `scripts/*.sh`/`*.bat`, `scripts/ai/`, `scripts/ci/`, `playwright/`).

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

### Problem

While fixing `scripts/sonar`'s README/comments (Part D follow-up work), a top-of-file "Usage:"
comment got trimmed down to a bare pointer at `README.md` ("see README.md 'How to run'") under the
"one line or none" code-comment rule. Rejected: a script's own top comment must be self-contained
— what it does, what parameters are valid, what result to expect — not a redirect to another file.
This surfaced a real, repo-wide gap: `scripts/architecture/*.sh`/`*.js` already have a real,
mechanically-parsed 4-field header (`Description:`/`Uses:`/`Input:`/`Output:` — see
`scripts/architecture/DECISIONS.md` ADR-022, read live by `architecture_tooling_self_docs_json()`
and shown on the "Build architecture page" card), but this convention (a) is undocumented as a
standing rule anywhere outside that one generator function's own code, (b) is hardcoded to only
scan `scripts/architecture/` (`architecture_tooling_self_docs_json()`'s `folder =
os.path.join(repo_root, 'scripts', 'architecture')`), never applied to any other script directory,
and (c) has no field for valid CLI parameters/flags — exactly the gap that triggered this.

### Research grounding (2026-08-14, web search)

Checked against real bash-scripting convention before finalizing, per explicit request: the
[Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html) — the most widely
cited authoritative bash convention — requires a top-of-file header comment (brief overview) and,
for functions, a structured block: `Description`, `Globals`, `Arguments`, `Outputs`, `Returns`.
This validates both the general shape (structured header block, not prose) and specifically the
missing field this issue exists to add — Google's `Arguments` is exactly our `Input:`/`Uses:`
fields' missing sibling for "what CLI flags does invoking this take."

### Decision — three-layer documentation, one canonical home each

1. **Script's own top-of-file header — self-contained, mechanically parsed.** Every script gets a
   structured header, extending the existing 4-field convention with a 5th field:
   `Description:` / `Usage:` (valid CLI parameters/flags — the new field) / `Uses:` / `Input:` /
   `Output:`. This is a deliberate, explicit exception to the "one line or none" code-comment rule
   (a structured metadata block, not prose rationale) — the rule needs an explicit carve-out
   documenting this, not just tacit precedent in one directory.
2. **README.md (per script-group directory) — describes the *flow between* scripts, not each
   script's own behavior again.** One script's output frequently feeds another's input (e.g.
   `scripts/sonar.sh` → `scripts/sonar/run.sh`; `scripts/ci.sh` → `scripts/ci/run.sh` →
   `scripts/unit-tests.sh`/`integration-tests.sh`/`deploy.sh`+`playwright/run.sh`/`sonar.sh`).
   README's job is to document that chain — what calls what, in what order, what the end-to-end
   result is — referencing each script's own header (by name/path) instead of restating its
   Description/Usage/Output. The existing `scripts/sonar/README.md` "Key files" table's Role
   column currently *restates* a shortened version of what the header now says — needs trimming
   once headers exist, per "one fact, one canonical home."
3. **`architecture-map.html`'s Tooling & Pipelines cards — renders both live, no third copy.**
   `architecture_tooling_self_docs_json()` (renamed/generalized, no longer hardcoded to
   `scripts/architecture`) scans every `SCRIPT_GROUP_DIRS` entry's own files for this header,
   parses the 5 fields (added: `Usage`), and renders a per-file table on that group's own card —
   the exact mechanism already built for "Build architecture page," extended repo-wide. The
   README's flow/chain content renders alongside via the already-built `readme_json_for()` +
   `mdBlockToHtml()` path (Part D/ADR-031) — two distinct sections on one page, sourced from two
   distinct files, no restatement between them.

### Where the convention itself gets documented (not the case study)

Per `.claude/rules.md`'s own "state the abstract principle, not a case study" rule: the concrete
5-field spec and the "header vs. README vs. architecture-map" layering belongs in
`.claude/skills/doc-standards/SKILL.md` (already the "how do facts about this repo get organized
across files" hub, already has a "Code comment rationale" ownership-table row added this session) —
a new dedicated section, not a second skill file. `.claude/rules.md`'s "Code comments: one line or
none" rule gets one added sentence carving out this structured header as an explicit exception,
cross-referencing the doc-standards section for the exact field spec — same "one canonical home,
cross-referenced" shape as the fix already applied earlier this session for where trimmed-comment
rationale goes.

### Scope for the first pass

`scripts/sonar/` (the 4 files already touched this session: `run.sh`, `run.bat`,
`docker-compose.sonar.yml`, `sonar-project.properties`) as the proving case — including a header
adapted for YAML/properties comment syntax (`#`-prefixed, same 5 fields). Generalizing
`architecture_tooling_self_docs_json()` to scan every `SCRIPT_GROUP_DIRS` entry (not just
`scripts/architecture`) is part of this pass so the Sonar card actually shows the new headers
live — doing the convention without the live-render half would leave it exactly as
un-discoverable as the gap this issue starts from. Rolling the header onto every *other* script in
the repo (root `scripts/*.sh`/`*.bat`, `scripts/ai/`, `scripts/ci/`, `playwright/`) is explicitly
**not** in this pass — flagged as follow-up work once the convention/mechanism is proven on one
directory, not assumed to be needed here.

## Part D — Tooling & Pipelines screen: regroup script cards by tool, not by ai/scripts category (done)

**Shipped.** See `scripts/architecture/DECISIONS.md` ADR-030.

### Problem

`renderPipelines()` (`scripts/architecture/generate-architecture-model.sh`) groups all 6
`SCRIPT_GROUP_DIRS` (`scripts/ai`, `scripts/architecture`, `scripts`, `scripts/ci`,
`scripts/sonar`, `playwright`) into only two buckets via `SCRIPT_GROUP_CATEGORY`: `ai` → "AI
Tooling" heading (currently `scripts/ai` + `scripts/architecture` together, alongside the
Commands/Skills tables), `scripts` → "Other Scripts" heading (currently `scripts`, `scripts/ci`,
`scripts/sonar`, `playwright` all together). The "How this page is built" self-docs table
(`MODEL.architectureToolingSelfDocs`, describing `scripts/architecture`'s own generator scripts)
renders as a separate trailing section instead of being part of that directory's own card.

### Fix

Split into one group heading/card per tool instead of the current 2-bucket split (Docker and
Runtime sections are unaffected — stay exactly as they are today):

- **AI Tooling** — `scripts/ai` only (drop `scripts/architecture` out of this bucket)
- **Build architecture page** — `scripts/architecture` (the architecture-map generator itself),
  with the existing "How this page is built"/`architectureToolingSelfDocs` table folded into this
  card instead of a separate trailing section
- **Playwright** — `playwright`
- **Sonar** — `scripts/sonar`
- **CI** — `scripts/ci`
- **Other Scripts** — `scripts` (root-level scripts not covered by any of the above)

Implementation: replace the binary `SCRIPT_GROUP_CATEGORY` (`ai`/`scripts`) with a 1:1
per-directory group-heading label, one heading per `SCRIPT_GROUP_DIRS` entry.

## Part A — `scripts/build.sh`: unit-tests.sh → integration-tests.sh redundantly recompiles/reinstalls the same modules

Moved verbatim from `improvement-151` (never implemented there — that issue's actual code work all
went into an unrelated architecture-generator cleanup instead, bundled in per explicit user
request; see `improvement-151`'s own text for that history).

### Problem

`scripts/unit-tests/run.sh` runs `./mvnw -pl "$MODULES" -am test` — the Maven `test` goal, which
compiles the reactor but never runs `install`, so nothing lands in `~/.m2`.

`integration-tests/run.sh` has its own staleness check (`DECISIONS.md` ADR-007): it compares each
of `platform-commons`/`advertisement`/`user`/`taxon`/`audit`/`attachment`/
`provider-profile-spring-boot-starter`'s newest `.java` file against its `~/.m2`-installed JAR's
mtime, and runs `./mvnw install -pl <stale modules> -am -DskipTests` for anything stale before
testing.

Because `unit-tests.sh` never installs, running `integration-tests.sh` right after
`unit-tests.sh` — even with zero further code changes — always finds every module "stale"
relative to whatever was last installed (which could be from a much earlier session) and
re-triggers a full `mvn install` for all of them. Confirmed (originally, in `improvement-151`): a
`bash scripts/run-all-tests.sh --integration "--sandbox"` run took 16m23s for the unit-tests leg
and a further 2m07s for integration-tests immediately after — the second number is almost entirely
redundant recompilation of source `unit-tests.sh` had already compiled seconds earlier.
`scripts/DECISIONS.md` ADR-004 already notes the partial version of this: `unit-tests.sh`'s
`-am` reactor build pre-warms `target/classes` so the recompile inside that later `install` finds
"nothing to compile" — but the `mvn install` invocation itself still always runs, paying its own
Maven-startup/dependency-resolution overhead for no reason.

### Suggested fix — done

Implemented as `scripts/build.sh` / `scripts/build.bat` (thin wrappers) → `scripts/build/run.sh`
(real logic): a single `mvn install -DskipTests` for platform-commons + all 6 domain starters —
**not** "+ marketplace-orchestrator" as originally guessed above; confirmed directly against
`integration-tests/pom.xml` that `marketplace-orchestrator` is not one of its dependencies at all.
The module list is read live from `pom.xml`'s own `<module>` entries (`platform-commons` plus
anything named `*-spring-boot-starter`) instead of hardcoded, so a newly added starter is picked
up automatically. Verified end to end: `bash scripts/build.sh` reaches `BUILD SUCCESS`, installing
all 7 modules into `~/.m2` in ~1:18.

No changes needed to `unit-tests.sh`/`integration-tests.sh` themselves — both already degrade
correctly to "nothing to compile"/"nothing stale" once this runs first:
- `unit-tests.sh`'s `mvn test` skips recompilation via Maven's own incremental compiler check
  (unchanged `target/classes`).
- `integration-tests.sh`'s existing staleness check (ADR-007) compares `~/.m2`-installed JAR
  mtimes against source — once `build.sh` has installed fresh JARs, it finds nothing stale and
  skips its own `install` step entirely, going straight to `mvn -pl integration-tests test`.

Not yet done: wiring `build.sh` as `run-all-tests.sh`'s first step (before launching the
unit-tests → integration-tests sequence and the parallel Playwright leg — Playwright itself never
touches Maven, so it doesn't need or benefit from this, see ADR-004), and
`integration-tests/run.sh`'s own hardcoded `STARTER_MODULES` list (line 99) still duplicates this
same list by hand instead of also reading it from `pom.xml` — flagged, not yet actioned per
explicit "розберемось пізніше" instruction.

### Environment findings from verifying this (candidate README/DECISIONS.md content, not yet moved there)

Discovered while testing `scripts/build.sh` for real:

- This Claude Code sandbox runs as its own Docker container (`claude-dev`, image `claude-j25-dev`)
  — a sibling to `marketplace-app`/`advertisement-db`/etc., not nested inside them. Its
  `/var/run/docker.sock` is the *host's* socket (Docker-outside-of-Docker, same mechanism
  `scripts/ci/DECISIONS.md` ADR-001 already documents for the CI runner).
- Its `/app` is a **bind mount** of the real project folder on the host Windows machine, and its
  `~/.m2` is a bind mount of the host's real `~/.m2` — not copies. Any `mvnw` invocation run
  directly in this sandbox (`scripts/build.sh`, `scripts/unit-tests.sh`, `integration-tests.sh`)
  writes to the exact same files a locally-running IDE build would.
- **Real write-conflict risk**: running a build in this sandbox and a build in a local IDE at the
  same time, against the same modules, both write to the same physical `target/`/`~/.m2` files —
  last-writer-wins, or a corrupted/partial jar if truly concurrent. No existing script currently
  guards against this.
- `deploy-dev.sh`'s containerized build (`deploy-dev-env`) does *not* have this risk — but not by
  design intent. Its actual documented reason (`deploy-dev-env/Dockerfile`'s own comment) is a
  Windows/WSL Java path-translation problem, not conflict avoidance. The isolation is a side
  effect of *how* it delivers source into the container (a tar pipe into a throwaway container's
  own filesystem, never the host bind mount) — confirmed the resulting JAR only ever reaches the
  running `marketplace-app` container via `docker cp`, never back to the host's own
  `marketplace-app/target/`.
- Maven's `jar:jar` goal skips repackaging when it detects no compiled-class changes ("Nothing to
  compile - all classes are up to date") — so after `scripts/build.sh` runs, not every one of the
  7 modules' `target/*.jar` gets a fresh timestamp, even though all 7 do get freshly copied into
  `~/.m2` by the `install:install` goal regardless. Confirmed directly: of the 7, only
  `audit-spring-boot-starter`'s jar was rebuilt (real source changed since the prior build); the
  other 6 kept their prior `target/*.jar` file untouched.

### Follow-up design (not yet started) — one isolated container for build/unit-tests/integration-tests/deploy-dev

Grew out of trying to run `scripts/build.bat` for real on a Windows machine without a working Java
in WSL (`Error: JAVA_HOME is not defined correctly`) — the same root cause
`scripts/build-deploy-env/Dockerfile`'s own comment already documents for why `deploy-dev.sh`
containerizes its own build (Windows/WSL Java path-translation problem — WSL can technically
invoke a Windows `.exe` via interop, but a Linux-path-generating script like `mvnw` and a
Windows-path-expecting `java.exe` don't translate paths for each other automatically).

**Rejected first idea:** bind-mount the real host `~/.m2` into the container so `scripts/build.sh`
writes to the same repository a WSL-run `integration-tests.sh` would read. Rejected in favor of
the design below — a fully isolated, container-only `.m2` is safer (no risk of a container build
corrupting the developer's real local repository) and, combined with moving every Maven-touching
script into the same container, sidesteps the whole "does WSL have Java" question for every one of
them, not just `build.sh`.

**Design:**
1. One container (rename target still open — reflects it now serves build, both test suites, and
   deploy-dev, not just deploy-dev) with its own **persistent, isolated named Docker volume** for
   `.m2` — populated once, reused across runs (not re-downloaded every invocation), never touching
   the host's real `~/.m2`.
2. Parameterized entrypoint: a Maven goal/module-set to run inside the container (`install` for
   the build step, `test` for either test suite), plus an optional `--deploy <container-name>` flag
   — when present, after the build step, copy the resulting `marketplace-app` JAR into the named
   target container and restart it (generalizes today's `deploy-dev-env/build.sh`, which hardcodes
   the target container name, into a reusable parameter).
3. **Sequencing unlocks real parallelism.** Today `unit-tests.sh` → `integration-tests.sh` run
   strictly sequentially specifically to avoid two concurrent `mvn` processes both deciding to
   install/write into `~/.m2` at the same time (a real corruption risk, not just a style
   preference). Once the shared container's `.m2` is fully warmed by one `build` step *first*,
   neither test suite needs to write to it anymore — both only *read* dependencies, which is safe
   to do concurrently across separate `mvn` processes. New order: `build` (once) → `unit-tests` ‖
   `integration-tests` (parallel, each its own container instance, same already-warmed `.m2`
   volume) ‖ Playwright (already parallel today, unaffected — it never touches Maven at all, only
   needs `marketplace-app` up to date, i.e. the same `--deploy` step `deploy-dev.sh` already uses).
4. Playwright's own container (`pw-runner`) is unrelated to this change — it never runs Maven, it
   only exercises an already-running `marketplace-app` over HTTP.

**Affected, once actually implemented:** the container (currently `scripts/build-deploy-env/`,
name still open), `scripts/build.sh`/`scripts/build.bat`, `scripts/build/run.sh`,
`scripts/unit-tests/run.sh`, `integration-tests/run.sh`, `scripts/deploy-dev.sh`,
`scripts/run-all-tests.sh` (the parallel-orchestration rewrite). Sequenced after the rename in
Part A's own "done" section above (`build-env` → `deploy-dev-env`) since this design builds on
that same container. Not started — this is the recorded plan, not yet actioned; explicit
confirmation needed before implementation begins, given the size (5+ files, changes to how
`run-all-tests.sh` orchestrates parallelism).

**Name settled:** `scripts/build-deploy-tests/` (supersedes the earlier `build-deploy-env` guess)
— reflects the three real capabilities (build, deploy, run tests), not just two.

**Deploy-target coupling — resolved.** A restart after a JAR swap is fully generic (`docker
restart <name>` works for any JVM container reading its jar at startup, no app-specific knowledge
needed). "Does the target container exist yet, and if not, how do I create it" is genuinely
app-specific (image, ports, env vars, network) and stays **outside** the generic container —
`deploy-dev.sh` keeps its own existing pre-check ("does `marketplace-app` exist? if not, call
`deploy.sh`") *before* invoking the generic container's own `--deploy <name>` step, which itself
only ever handles "copy into an already-running container + restart it" and fails with a clear
error if the named container doesn't exist. The generic container never learns how to build any
specific app from scratch — that knowledge stays wherever it already lives today.

**Grounding: this shape matches established CI pipeline vocabulary, not an ad-hoc invention.**
Checked (2026-08-15 web search) against GitLab CI/Azure Pipelines/GitHub Actions, which all
converge on the same structure: a **stage** is a sequential phase (`build → test → deploy`);
**jobs** inside one stage run in **parallel** by default; **`needs:`**-style dependencies let a
later job skip waiting for an entire stage; **artifacts** are what one job produces and another
consumes. Sources:
[Reintech — Mastering Multi-Stage Pipelines in GitLab CI](https://reintech.io/blog/mastering-multi-stage-pipelines-gitlab-ci),
[OneUptime — Parallel Jobs in Azure Pipelines](https://oneuptime.com/blog/post/2026-02-16-how-to-set-up-parallel-jobs-in-azure-pipelines-to-speed-up-build-and-test-execution/view),
[DevOpsil — GitLab CI Pipeline Optimization: Caching, DAG, and Parallel Jobs](https://devopsil.com/articles/2026-03-21-gitlab-ci-pipeline-optimization-speed).
This design's `build` → `unit-tests ‖ integration-tests ‖ sonar` → optional `deploy` is
exactly that shape (one stage, N parallel jobs, next stage), just without the vocabulary. Adopting
an actual heavyweight tool (Jenkins et al.) as a running service would be a mismatch for this
project's local dev-sandbox scale — and the project already has its own lightweight, hand-rolled
equivalent of the same philosophy, `scripts/ci.sh` (stages + parallelism + progress tracking, all
in bash) — so this design borrows the proven *structure*, not the tooling.

**Folder/file skeleton (proposed, not yet built):**
```
scripts/build-deploy-tests/
  Dockerfile          -- JDK 25 + Docker CLI (today's scripts/deploy-dev-env/Dockerfile content)
  run.sh              -- parameterized entrypoint: --deploy <container-name>, --unit true/false,
                          --integration true/false; tar-pipes source in, runs against an isolated
                          persistent named .m2 volume (never the host's real ~/.m2), writes
                          progress to progress.txt the same way scripts/ci.sh already does
  README.md
```
Thin wrappers in `scripts/` each call this same `run.sh` with different flags: `build.sh`/`.bat`
(no flags — build only), `deploy-dev.sh`/`.bat` (`--deploy marketplace-app`), `run-all-tests.sh`
(`--unit true --integration true`, replacing its current sequential `unit-tests.sh` →
`integration-tests.sh` calls with one parallel containerized run).

**Still open, not decided:**
1. Whether `scripts/unit-tests.sh`/`scripts/integration-tests.sh` survive as their own thin
   entry points (for running just one suite standalone, still delegating into the same `run.sh`
   with one flag set), or collapse entirely into `build-deploy-tests/run.sh` with no separate
   top-level wrapper at all.
2. `scripts/deploy-dev-env/docker-compose.yml` is confirmed dead code (never invoked by anything,
   see the "Environment findings" note above) — new `build-deploy-tests/` should not carry a
   `docker-compose.yml` forward at all, direct `docker build`/`docker run` only, matching what
   `deploy-dev.sh` actually already does today in practice.
3. **`--deploy <container> --profile dev|prod` — resolved the same way as "container doesn't
   exist yet."** `docker restart` cannot change an existing container's environment variables —
   they are fixed at `docker run` time, so switching profile actually requires removing and
   re-creating the container (full `docker run` knowledge: ports, network, volumes) — the same
   app-specific knowledge the generic container must never own. Resolution: the generic step
   checks the target container's *current* running profile against the requested one; same
   profile → cheap `docker cp` + `docker restart` (today's behavior); different profile → fail
   with a clear error instead of attempting any recreate itself. The caller (which does have the
   full `docker run` knowledge) decides what to do next — no auto-recreate logic inside the
   generic container at all.

**Settled: a `.properties` file carries the default flag values, same shape as
`sonar-project.properties`.** `scripts/build-deploy-tests/build-deploy-tests.properties` holds
defaults (`deploy=marketplace-app`, `profile=dev`, `unit=true`, `integration=true`); `run.sh`
reads these as its baseline, and any CLI flag passed explicitly (`--deploy`, `--profile`,
`--unit`, `--integration`) overrides just that one value for that single invocation — mirrors how
`scripts/sonar/run.sh --no-gate` already overrides a default behavior on top of
`sonar-project.properties`. Lets `run-all-tests.sh`/CI pass explicit flags regardless of the file's
defaults, while a developer's routine local call (`bash run.sh`, no flags) gets their own usual
defaults without retyping them every time.

**Real bugs found and fixed while testing `build.bat`/`sonar.bat` for real on Windows (done,
separate from the design above):**
- `scripts/build.bat`/`scripts/sonar.bat` hardcoded `wsl bash /app/...` — only resolves inside this
  Claude Code sandbox's own mount convention, not a real developer's WSL (confirmed directly:
  `bash: /app/scripts/build/run.sh: No such file or directory` on a real Windows machine). Fixed
  both to use the same `wsl wslpath -u "%~dp0..."` dynamic-resolution pattern `deploy.bat`/
  `deploy-dev.bat` already used correctly — `ci.bat`/`integration-tests.bat`/`playwright.bat`/
  `unit-tests.bat` still carry the same bug, not yet fixed (deferred, not in this pass).
- `scripts/sonar/run.sh` itself had the identical hardcoded-`/app` bug in 8 places
  (`COMPOSE_FILE`, `PROPS_FILE`, the module-list-validation Python step's `pom_file`, the `mvnw`
  compile invocation, the per-module source/target copy loop, `REPORT_DIR`) — fixed by computing
  `ROOT="$(cd "$(dirname "$0")/../.." && pwd)"` once at the top and routing every one of them
  through it. Verified both fixes for real: re-ran `bash scripts/sonar/run.sh --no-gate` in this
  sandbox (still reaches `ANALYSIS SUCCESSFUL`), and the user re-ran `sonar.bat` on the real
  Windows machine — it now correctly resolves the path and progresses all the way to the
  "Compiling modules" step, where it hits the *already-known* missing-Java-in-WSL gap (not a new
  bug — the exact problem this whole "Follow-up design" section exists to solve).

**Another real consumer for the same container, surfaced by that Windows test:**
`scripts/sonar/run.sh`'s own "Compiling modules" step (`mvnw compile`, to produce `target/classes`
for the scanner to analyze) hits the identical missing-Java-in-WSL problem `build.sh` does — it's
not unique to `build-deploy-tests`. Once that container exists, `sonar/run.sh` could delegate
compilation to it instead of calling `mvnw` directly, which would fix `sonar.bat` on Windows too,
for free. Open question, not decided: how the compiled classes get from the build container to the
`sonar-scanner` container (both are Docker containers, neither is the host) — two candidate
mechanisms:
1. A **shared Docker volume** between `build-deploy-tests` and `sonar-scanner` — the build
   container writes `target/classes` there, the scanner container reads directly from the same
   volume, no host hop at all.
2. **Two hops through the host** — `docker cp` the build container's output to a host temp
   directory, then `docker cp` that into `sonar-scanner` — closer to `sonar/run.sh`'s current code
   shape (only the copy *source* changes, from `$ROOT/$module/target/classes` to a temp dir), but
   less clean than option 1.

Option 1 (shared volume) is also strictly faster: `docker cp` isn't a plain file copy — Docker
tar-archives the source inside the source container, streams it over the Docker API, then
extracts it on the other end, once per direction per module. A shared volume mount needs none of
that — both containers do plain filesystem I/O against the same underlying storage, zero
archive/stream/extract overhead.

**Initial idea (superseded below — see "Takari Concurrent Local Repository investigated and
rejected" in the Step 1 section further down): concurrent callers of the build step handled by
the Takari Concurrent Local Repository Maven extension, not a hand-rolled lock.** If
independently-invoked entry points (`sonar.bat`, `unit-tests.bat`, etc.) each unconditionally
trigger the build step as their own first action rather than assuming one central orchestrator ran
it first, two of them could race to `mvn install`/`compile` against the same shared, isolated
`.m2` volume at the same time — the same class of concurrent-write risk already identified for
host-vs-sandbox builds earlier in this issue, and a real, documented Maven limitation (its default
local-repository access is not designed for concurrent multi-process/multi-thread use and can
corrupt repository metadata under real concurrency — confirmed via
[Baeldung — Caching Maven Dependencies with Docker](https://www.baeldung.com/ops/docker-cache-maven-dependencies)
and [mvysny — Docker buildx mount cache](https://mvysny.github.io/docker-build-cache/), 2026-08-15
web search grounding this rather than assuming it).

An initial idea (wrap the whole build invocation in `flock` against a lock file in the persistent
volume) looked like a coarser, hand-rolled substitute for a purpose-built tool that already
exists: the **[Takari Concurrent Local Repository](https://github.com/takari/takari-local-repository)**
Maven extension ([Maven coordinates](https://mvnrepository.com/artifact/io.takari.aether/takari-concurrent-localrepo):
`io.takari.aether:takari-concurrent-localrepo`; background on the company/author —
[Takari — About Us](http://takari.io/aboutUs.html), founded by Jason van Zyl, Maven's original
creator), built specifically to make local-repository access safe under real concurrent Maven
invocations (its own stated use case: CI systems building several projects in parallel).
Registered declaratively via `.mvn/extensions.xml` — no wrapping required around every invocation,
and it only serializes the actual repository-write operations that need it rather than the whole
build, so it preserves more real parallelism than an external `flock` around the entire command
would. Also confirmed as the same underlying principle Docker BuildKit itself already ships for
the analogous build-time case (`--mount=type=cache,sharing=locked` — exclusive, wait-for-access
cache mounts) — not directly reusable here since that's a `docker build`-time feature and this
design uses `docker run`, but it validates that serialized-access-to-a-shared-cache is the
established pattern, not something invented for this project.

**Step 1 — done, verified.** Implemented the build-only slice of the design (the part that fixes
the confirmed real problem: Windows/WSL without a working Java cannot run `scripts/build.sh`):
- `scripts/deploy-dev-env/` renamed to `scripts/build-deploy-tests/` (`git mv` + every reference
  fixed: `deploy-dev.sh`, `scripts/README.md`, `generate-architecture-model.sh`'s `DOCKER_FILES`/
  `SCRIPT_GROUP_*` tables). Its dead `docker-compose.yml` (see "Environment findings" above)
  deleted, not carried forward.
- `scripts/build-deploy-tests/build.sh` (runs inside the container) gained a `BUILD_ONLY=true`
  mode: reads the module list live from `pom.xml` (same logic `scripts/build/run.sh` had) and runs
  `mvn install` against it. Default mode (no `BUILD_ONLY`) is untouched — still `deploy-dev.sh`'s
  existing package/hot-swap/restart flow.
- **Takari Concurrent Local Repository investigated and rejected** in favor of `flock`: its
  documented successor (Maven Resolver 1.7+'s built-in Named Locks, shipped since Maven 3.9.0 —
  [Maven 3.9.0 release notes](https://maven.apache.org/docs/3.9.0/release-notes.html),
  [Artifact Resolver — Local Repository](https://maven.apache.org/resolver-1.x/local-repository.html),
  [Named Locks (resolver 1.7.3)](https://maven.apache.org/resolver-archives/resolver-1.7.3/maven-resolver-named-locks/index.html))
  only protects **threads within one JVM**, not **separate `mvn`
  processes** — our actual scenario (each `docker run` is its own process). Takari's own OS-level
  file locks would cover that, but it's an archived, no-longer-maintained dependency for something
  a single `flock` line does just as well, with no external dependency at all. Both `mvn`
  invocations in `build.sh` (`install` in build-only mode, `clean package` in deploy mode) now run
  through `flock /root/.m2/.build.lock -c "..."` — the lock file lives inside the shared
  `maven-cache` named volume itself, so it's visible to every container instance that mounts that
  volume, not scoped to one container.
- New outer `scripts/build.sh` (host/WSL-facing): ensures the image exists, tar-pipes source in,
  `docker run` with `-v maven-cache:/root/.m2 -e BUILD_ONLY=true` (no `docker.sock` mount — this
  mode never touches another container). `scripts/build.bat` updated to call it via the same
  `wslpath -u` pattern already fixed for `sonar.bat`. Old `scripts/build/run.sh` (direct `mvnw`
  call, no container) and its `README.md` deleted — no direct-execution fallback kept, per explicit
  instruction; `scripts/build.sh` always goes through the container now.
- `scripts/build-deploy-tests/README.md` written per `infra-doc-standards` (tool-intro paragraph,
  two Flow diagrams — `build.sh` and `deploy-dev.sh` as separate diagrams, little shared logic past
  the image itself — plus an "Environment notes" section on the isolated-`.m2`/`flock` mechanics).
  `architecture-map.html`'s "Build" card updated to point at the new directory.
- **Verified end to end in this sandbox:** `bash scripts/build.sh` reaches `BUILD SUCCESS`,
  installing all 7 modules into the container's isolated `.m2` (cold-cache run, ~31s, downloads
  everything fresh — expected, first use of the new named volume).

**Verified end to end on the real Windows machine this whole design exists for.** `./scripts/build.bat`
now reaches `BUILD SUCCESS` on Windows/WSL — the original blocking problem
(`Error: JAVA_HOME is not defined correctly`) is resolved. ~26s total, all 7 modules installed.

**Real bug found and fixed during that Windows run: `tar` (via WSL) failed on 10 files with
`Permission denied`** — `docs/ai/adr-index.md` and 9 Vaadin-generated files under
`marketplace-app/src/main/frontend/generated/` (a live IDE/dev-server process likely holding them
open — not confirmed which one). The overall run still succeeded only because `tar -czf - ... |
docker run ...` is a plain pipe without `pipefail`, so bash's `set -e` only inspects `docker run`'s
own exit status, not `tar`'s — `docker run` received a partial-but-sufficient archive (missing only
files irrelevant to a library-module install) and completed normally. This was luck, not design: if
a file actually needed by the 7 installed modules had hit the same permission error, the build would
have failed with a confusing downstream compile error instead of a clear cause. Fixed properly, not
left to luck: both `scripts/build.sh` and `scripts/deploy-dev.sh`'s `tar` invocations now
`--exclude` `marketplace-app/src/main/frontend/generated` (Vaadin regenerates this directory from
scratch during `package` regardless, so excluding pre-existing copies loses nothing) and
`docs/ai/adr-index.md` (not consumed by either script's build). **Re-verified on the real Windows
machine** — re-ran `./scripts/build.bat`, zero `tar` permission errors, `BUILD SUCCESS` in 25.6s.

**Also done: explicit, fixed container names for both entry points** (previously `deploy-dev.sh`
reused the image name as its container name too, confusingly; `build.sh` had no fixed name at
all). `build.sh` now runs as `advertisement-build-only`, `deploy-dev.sh` as
`advertisement-deploy-dev` — both attachable directly via `docker exec -it <name> bash` while a
run is in progress, without first needing `docker ps` to find an auto-generated name. Documented
in both scripts' `Description` header field and `scripts/build-deploy-tests/README.md`'s
Environment notes. New standing rule added to `infra-doc-standards/SKILL.md`: any script that
gives its own container/image a fixed, meaningful name records that name in the `Description`
field, not just in the script body.

**Also done: `scripts/build.sh` gained `--reset-cache`/`--rebuild-image` flags, and both
`build.sh`/`deploy-dev.sh` now detect Dockerfile staleness automatically** — comparing
`scripts/build-deploy-tests/Dockerfile`'s mtime against the existing image's `docker image
inspect -f '{{.Created}}'` timestamp, rebuilding automatically when the Dockerfile is newer
(previously only checked "does an image with this name exist at all," never freshness).
`build.sh` also prunes dangling images (`docker image prune -f`) after every run, matching
`deploy.sh`'s existing precedent.

**Decision, made explicitly, not a silent revert:** `build-deploy-tests/build.sh`'s (deploy mode)
cleanup step briefly had its unconditional `docker container prune -f`/`docker volume prune -f`
narrowed to image-only pruning, matching the host-wide-pruning-is-opt-in-only policy documented in
`scripts/CLAUDE.md`/`scripts/ci/DECISIONS.md` ADR-001 (a real past incident: unscoped pruning
removed *other*, unrelated stopped containers/volumes on the same machine, not just this
project's). Reverted back to the original unconditional all-three-types prune after being told
that exact risk plainly and choosing speed/simplicity over it anyway — recorded here as a
deliberate, informed choice for this specific script, not a precedent that overrides the general
policy elsewhere (`deploy.sh`'s `--prune-all` gate, and the general rule itself, both stay as they
are).

**Not yet done — the rest of the design above:** `--deploy <container>` flag (wiring
`deploy-dev.sh` through the same parameterized `run.sh`-style entrypoint rather than its own
separate call path), `--unit`/`--integration` flags and the `run-all-tests.sh` parallelization,
the `.properties`-file defaults, and `sonar/run.sh` delegating compilation to this container
instead of calling `mvnw` directly.

### `deploy-dev` simplification — design discussed, not yet implemented

Reconsidered whether `deploy-dev.sh` needs its own distinct "check if `marketplace-app` exists,
hot-swap into it" flow at all, now that build already runs through the shared, `flock`-protected
container.

**Problem with the current shape:** `deploy-dev.sh`'s inner `build.sh` runs its own `mvn clean
package` for `marketplace-app` specifically, entirely separate from `scripts/build.sh`'s own `mvn
install` for the 7 library modules — two different Maven invocations, both going through the same
container image but never sharing a build step with each other.

**Simplified design, agreed in chat:**
1. The shared `build` step's scope grows to also produce `marketplace-app`'s own runnable JAR
   (not just install the 7 library modules) — one Maven invocation covers everything any consumer
   needs, instead of two separate ones.
2. The resulting JAR is copied to a **fixed path inside the same shared `maven-cache` volume**,
   outside Maven's own `.m2/repository` tree — proposed: `/root/.m2/artifacts/marketplace-app.jar`
   (overwritten on every build). No second volume needed — the existing volume already persists
   across separate container invocations, which is exactly what's needed here: the artifact must
   survive between a `build` run and a later, separate `reload` run.
3. **`marketplace-app` itself stays the same, persistent container** — never destroyed and
   recreated from scratch. A new, much thinner "reload" step replaces `deploy-dev.sh`'s current
   hot-swap logic: copy the JAR from that fixed shared-volume path into the running
   `marketplace-app` container, `docker restart` it. No `mvn` invocation happens in this step at
   all anymore — it only ever reads what the shared `build` step already produced.
4. Net effect: `deploy-dev.sh` (or whatever replaces it) becomes "run build, then reload" instead
   of "run its own separate package + hot-swap" — one shared build step for every consumer
   (tests, sonar once that's wired up, and now the running dev app too), reload reduced to a pure
   copy+restart with no Maven work of its own.

**Not yet decided:** exact command/flag shape for triggering "reload" (a flag on `build.sh`
itself? a separate `scripts/reload.sh`?), and whether the container-existence check
(`marketplace-app` missing → fall back to `deploy.sh`) still belongs in this reload step or moves
elsewhere — same app-specific-knowledge boundary question already resolved once for the generic
`--deploy <container>` design above, likely resolved the same way (reload step never learns how to
create a container from scratch, only ever acts on one already running).

**Confirmed: the same "one shared build, everyone else just reads the result" pattern extends to
Sonar and Playwright too, in the future** — not just `deploy-dev`. Sonar's own case is already
recorded above ("Another real consumer for the same container"). Playwright's e2e suite doesn't
run Maven at all, but it does need `marketplace-app` up and running with current code before its
tests can exercise it — the same `reload` step this section designs (copy the shared-volume JAR
into the running container, restart) is exactly what it would need too, not a separate mechanism
of its own.

**Done: `deploy-dev.sh` eliminated, folded into `deploy.sh --reload`; `.properties` defaults +
real `--unit`/`--integration` test execution — verified end to end.** Full sequence implemented
and confirmed working in this sandbox:
- `scripts/deploy-dev-env/` → `scripts/build-tests/` (second rename, "deploy" dropped from the
  name entirely once the concept did). `scripts/deploy-dev.sh`/`.bat` deleted. `deploy.sh` gained
  `--reload` (see `scripts/DECISIONS.md` ADR-012 for the full reasoning, including the reversal of
  the earlier "don't merge deploy.sh/deploy-dev.sh" rejection this same issue recorded above).
- `scripts/build-tests/build.sh` (inside the container) now always builds the **whole reactor**
  (not just the 7 library modules) and always refreshes `marketplace-app.jar` at a fixed path
  inside the shared `maven-cache` volume (`/root/.m2/artifacts/marketplace-app.jar`) — no separate
  "build-only" vs "full" mode, one behavior regardless of caller, per the explicit "at every
  build, overwrite the artifact — whoever reads it later doesn't need to know or care who built
  it or when" instruction.
- `scripts/build-tests/build-tests.properties` — new `.properties` defaults file (`unit=true`,
  `integration=true`), same shape as `sonar-project.properties`. `scripts/build.sh` reads it as
  baseline; `--unit`/`--no-unit`/`--integration`/`--no-integration` override per invocation.
- Real test execution added inside the container: unit (`./mvnw -pl
  query-lib,marketplace-app,marketplace-orchestrator test`, no Docker needed) and integration
  (`./mvnw -pl integration-tests test`, needs `docker.sock` mounted in so Testcontainers can reach
  the host's Docker daemon and spin up its own Postgres as a sibling container). `docker.sock` is
  mounted into the container only when `integration=true` (least privilege — never mounted for a
  unit-only or build-only run). Sandbox-only Testcontainers workarounds
  (`TESTCONTAINERS_RYUK_DISABLED`, `INTEGRATION_TESTS_POSTGRES_FIXED_PORT`) pass through into the
  container only if already set in the caller's own environment.
- **Verified end to end in this sandbox:** `bash scripts/build.sh` (both flags at their
  `true` default) — whole-reactor build succeeds, 53 unit tests pass (0 failures), 85 integration
  tests pass (0 failures, Testcontainers successfully created its Postgres container via the
  passed-through `docker.sock`), all inside one container invocation.

**Also not yet done — keeping the build flow clean of stale images/containers, raised after the
Windows test above.** Two real gaps identified, both to close by applying policy this repo has
already established elsewhere rather than inventing new behavior:
1. **Image staleness is never checked, only existence.** `docker image inspect "$BUILD_IMAGE"`
   only asks "does an image with this name exist at all" — if `scripts/build-deploy-tests/
   Dockerfile` changes (new package, JDK bump), the stale image keeps getting reused silently,
   unlike `scripts/sonar/run.sh`'s own image-freshness check (compares image IDs, rebuilds on
   mismatch). Fix: add the same kind of freshness check here, plus an explicit `--rebuild-image`
   flag for forcing it on demand.
2. **No automatic dangling-image cleanup after a build**, unlike `deploy.sh` (which already runs
   `docker image prune -f` post-build — safe, since it only removes images nothing references,
   never actively-used ones). Fix: add the same `docker image prune -f` call after `build.sh`/
   `deploy-dev.sh` finish, matching existing precedent instead of introducing a new pattern.

**Explicitly staying manual, not automatic — consistent with this repo's own established
policy** (`scripts/CLAUDE.md`, `scripts/ci/DECISIONS.md` ADR-001): host-wide `docker container
prune`/`docker volume prune` are never run automatically anywhere in this repo, only via the
explicit, opt-in `--prune-all` flag — because they can remove *other*, unrelated stopped
containers/volumes on the same machine, not just this project's own (a real past incident, not a
hypothetical). `--reset-cache` (already implemented in `deploy-dev.sh`, needs adding to
`build.sh` too for consistency) stays the explicit, opt-in mechanism for wiping the shared
`maven-cache` volume — never automatic, same reasoning.

**Superseded — the merge below did happen, once the reasoning that blocked it no longer applied:**
- ~~Merging `deploy.sh`/`deploy-dev.sh` into one parametrized script — considered; `deploy.sh` (318
  lines, infra bootstrap, Liquibase self-heal, CI isolated-stack env-var overrides) and
  `deploy-dev.sh` (64 lines, assumes infra/app already running) are different-shaped flows, not
  parameter variations of the same one; merging would either bloat `deploy.sh` with a branch where
  most of its own flags become meaningless, or duplicate bootstrap logic. Not pursued.~~ Reversed:
  `deploy-dev.sh` deleted entirely, its capability now lives as `deploy.sh --reload`. What changed
  since the original rejection: `deploy-dev.sh`'s own `mvn package` got replaced by the shared,
  `flock`-protected `build-tests` container step (the same one `scripts/build.sh` and future
  sonar/Playwright consumers use) — so "reload" is no longer a full flow of its own with real
  bootstrap logic to duplicate, just a thin "run the shared build step, then copy+restart" branch,
  genuinely a parameter variation now rather than a different-shaped flow. `--reload` skips infra
  entirely and exits early, before any of `deploy.sh`'s own Step 1/2/3 logic runs, so none of its
  existing flags become meaningless for the normal (non-reload) path.

**Reversed again:** the `deploy.sh --reload` addition itself was reverted (`deploy.sh`/`deploy.bat`
restored to their pre-`--reload` committed state, by explicit instruction — only those two files,
`deploy-dev.sh`/`.bat` stayed deleted). See the final-state update at the end of this Part A
section for the current, real state: there is no fast JAR hot-swap/live-redeploy mechanism in this
repo anymore, only the build step itself (`scripts/build-and-test.sh`).

**Explicitly out of scope, investigated and rejected in `improvement-151` (still applies):**
- Making `deploy.sh`'s Docker image build reuse host-compiled classes — rejected on principle:
  the deploy image must build reproducibly from source in an isolated context, not from
  potentially-stale/uncommitted host artifacts. Only the external-dependency download cache
  (`--mount=type=cache,target=/root/.m2` in BuildKit) is legitimately shared.
- `ArchitectureRulesTest`'s own ~195s ArchUnit classpath scan and Vaadin's ~61s
  `prepare-frontend` class scan — both inherent to the tools themselves, not fixable via build
  artifact reuse.

### Final state (done, committed `7ece7117`) — supersedes every intermediate name/design above

Part A is complete. The directory/entry-point name churned twice more since "Name settled:
`scripts/build-deploy-tests/`" above — final name is `scripts/build-and-test/`, matching the
established `scripts/sonar.sh`→`scripts/sonar/`, `scripts/ci.sh`→`scripts/ci/` convention (entry
point basename == subdirectory name). Structure:

- **`scripts/build-and-test.sh` / `.bat`** — thin entry points (2-3 lines each, no logic of their
  own), matching the `sonar.sh`/`ci.sh` precedent exactly. New standing rule recorded in
  `.claude/rules.md`'s "Scripts" section: a script-group subdirectory owns all its own logic; the
  top-level `scripts/<name>.sh` is a thin delegator to `scripts/<name>/run.sh`.
- **`scripts/build-and-test/run.sh`** — all host-side logic moved here from the old top-level
  `build.sh` (flag parsing, `.properties` defaults, image-staleness check, `--reset-cache`/
  `--rebuild-image`, tar-pipe, `docker run`, dangling-image prune).
- **`scripts/build-and-test/build.sh`** — runs inside the container: full-reactor `mvn install`,
  optional unit/integration tests, `flock`-serialized against the shared `maven-cache` volume.

**`deploy.sh --reload` and the container-side hot-swap branch were both removed, not just
reverted in one place.** Per explicit instruction ("ми відмовились від цього, просто виставляєм
джарку і невідомо хто її підбере" — we're not doing this anymore, we just always publish the jar
and it's someone else's problem who picks it up): `deploy.sh`/`deploy.bat` were restored to their
pre-`--reload` state, and the `RELOAD` branch (container inspect/`docker cp`/`docker restart`/wait
loop/prune, needing `docker.sock` mounted in) was deleted from `build-and-test/build.sh` entirely.
`docker.io` (Docker CLI) was then also removed from the `Dockerfile` — nothing left inside the
container calls the `docker` binary; `RUN_INTEGRATION`'s Testcontainers reaches `docker.sock`
directly via its own Java client, no CLI needed. **Net effect: there is currently no fast JAR
hot-swap / live-redeploy mechanism anywhere in this repo** — `build-and-test.sh` only refreshes
`marketplace-app.jar` in the shared volume; nothing deploys or restarts a running container.
`deploy-dev.sh`/`.bat` remain deleted (never restored). `scripts/DECISIONS.md` ADR-012 was
annotated (not silently rewritten) to record this.

**Widespread documentation drift this caused was found (independent-review agent + manual sweep)
and fixed**, all describing the now-removed `--reload`/hot-swap capability as if it still existed:
`CLAUDE.md`, `scripts/CLAUDE.md`, `scripts/README.md`, `scripts/DECISIONS.md`, `docs/ai/flows.md`,
`playwright/README.md`, `docs/architecture/runtime-notes.md`, and root `README.md`'s own script
table. The `.claude/commands/deploy-dev.md` slash command was repurposed into
`.claude/commands/build-and-test.md` (`/build-and-test`), now invoking the real
`scripts/build-and-test.sh` instead of a command that no longer does anything.

**`infra-doc-standards/SKILL.md` gained three more rules** while applying the standard to this
directory for real: `Env` fields must distinguish "set automatically by whatever invokes this
file" from "may be exported directly by a user"; `UPPER_CASE` is correct (not a bug) for
constants including CLI-flag-derived values, per the Google Shell Style Guide's own "Constants and
Environment Variable Names" section — corrected after an earlier, wrongly-scoped attempt at a
blanket lowercase-for-locals rule would have contradicted every existing script in this repo
(`deploy.sh`, `sonar/run.sh`); `Description` stays lean, flag detail belongs in `Usage`/`Env`.

**Architecture map**: `scripts/build-and-test` gained an explicit `SCRIPT_GROUP_FILE_ORDER` entry
(`run.sh build.sh build-and-test.properties Dockerfile` — real flow order, was falling back to
alphabetical) and its Tooling & Pipelines card was renamed from the stale "Build" (with a stale
"scripts/build — warms ~/.m2 for tests" description, left over from an even earlier iteration) to
"Build and Test". `architecture-model.json`/`architecture-map.html` regenerated and validated
fresh after every change in this batch.

### Real bug found and fixed: `RUN_INTEGRATION` silently ran zero real Testcontainers tests

`integration-tests/pom.xml` defaults `surefire.excludedGroups=testcontainers` (so a plain `mvn
test` from the repo root never needs a reachable Docker daemon) — `integration-tests/run.sh`
overrides this back to empty via `-Dsurefire.excludedGroups=`, the sanctioned way to actually run
these tests. `scripts/build-and-test/build.sh`'s `RUN_INTEGRATION` branch never applied that
override. Confirmed empirically: `bash scripts/build-and-test.sh --no-unit --integration` reported
"85 tests, BUILD SUCCESS" in 11s — none of them a real `*RepositoryTest`, no Testcontainers
Postgres ever started (11s is physically too fast). Fixed by adding
`-Dsurefire.excludedGroups=` to that `flock`/`mvn` invocation. Re-verified: 165 tests now run for
real (including `ProviderProfileRepositoryTest` and every other `*RepositoryTest`, real
Testcontainers Postgres via `docker.sock`), `BUILD SUCCESS`, 48.985s, 0 failures/errors — matches
`integration-tests/run.sh`'s own real suite.

### Known gaps vs. `unit-tests.sh`/`integration-tests.sh` — to close before further consolidation

Compared directly against `scripts/unit-tests/run.sh` and `integration-tests/run.sh`'s real code.
Not yet closed — `build-and-test`'s `RUN_UNIT`/`RUN_INTEGRATION` are a thinner subset of what the
standalone scripts already do:

**Missing from `RUN_UNIT` (vs. `unit-tests.sh`/`run.sh`):**
1. No module/single-test-class selection — always runs all of
   `query-lib,marketplace-app,marketplace-orchestrator`, no equivalent of
   `unit-tests.sh marketplace-app` / `unit-tests.sh AccessEvaluatorTest`.
2. No report artifacts copied to the host — Surefire reports/`run.log` stay inside the throwaway
   container only, lost once it exits (`--rm`); `unit-tests.sh` copies them to
   `scripts/unit-tests/reports/surefire/<module>/`.
3. No clear PASSED/FAILED console summary or list of failing test files.
4. No explicit exit-code-driven trailer ("Full log:"/"Surefire reports:").

**Missing from `RUN_INTEGRATION` (vs. `integration-tests.sh`/`run.sh`):**
1. No scenario/single-test-class selection (`smoke`, `AdvertisementRepositoryTest`).
2. No `--sandbox` flag shorthand — the sandbox workarounds only reach the container via raw
   `TESTCONTAINERS_RYUK_DISABLED`/`INTEGRATION_TESTS_POSTGRES_FIXED_PORT` env vars already set in
   the caller's own shell (functionally present, just no ergonomic alias).
3. No `GITHUB_ACTIONS` guard — `integration-tests/run.sh` fails fast if a sandbox-only workaround
   leaks into a real CI run; `build-and-test` has no equivalent check.
4. No Docker-daemon-reachable precheck before Testcontainers starts — `integration-tests/run.sh`
   fails with a clear message first; `build-and-test` would surface a less clear error deep inside
   Testcontainers' own connection probing.
5. No staleness check — `build-and-test` always runs a full-reactor `mvn install` every time
   (cheap via Maven's own incremental compile, but a different, less targeted strategy than
   `integration-tests/run.sh`'s per-starter mtime comparison that skips installing entirely when
   nothing changed).
6. No report artifacts copied to the host (same gap as `RUN_UNIT` above).
7. No PASSED/FAILED summary / failing-test-file list (same gap as `RUN_UNIT` above).

Explicit instruction: close these gaps before any further consolidation/parallelization work on
`run-all-tests.sh`.

### Plan to close the gaps (approved, in progress)

**Shared across `RUN_UNIT`/`RUN_INTEGRATION`:**
- Reports reach the host via a mounted volume, not a copy race against `--rm`:
  `run.sh` adds `-v "$ROOT/scripts/build-and-test/reports:/reports"` to the `docker run` call;
  `build.sh` (in-container) copies each module's `target/surefire-reports` into `/reports/...`
  after its own test invocation.
- `build.sh` prints the same PASSED/FAILED console summary + failing-test-file listing style
  `unit-tests/run.sh`/`integration-tests/run.sh` already use, for both `RUN_UNIT` and
  `RUN_INTEGRATION`, driven off each `mvn test` call's own exit code.

**`RUN_UNIT` only:**
- New disguised argument `UNIT_TEST_ARG` (forwarded from a new `--unit-test <module-or-class>`
  flag on `run.sh`, same shape as `unit-tests.sh`'s own single positional arg) — `build.sh` uses it
  to narrow `-pl`/`-Dtest=` instead of always running all 3 modules.

**`RUN_INTEGRATION` only:**
- New disguised argument `INTEGRATION_TEST_ARG` (from `--integration-test <scenario>` on `run.sh`),
  same narrowing role as above.
- `--sandbox` flag on `run.sh` — sets `TESTCONTAINERS_RYUK_DISABLED=true`/
  `INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432` itself, in addition to the existing raw-env-var
  passthrough (both stay supported, `--sandbox` is just the ergonomic alias).
- `GITHUB_ACTIONS` guard on `run.sh`, before the container starts — same check
  `integration-tests/run.sh` already has (fail fast if a sandbox-only workaround leaks into real
  CI).
- Docker-daemon-reachable precheck on `run.sh`, before the container starts — same `docker info`
  check `integration-tests/run.sh` already has.
- Staleness check (`integration-tests/run.sh`'s per-starter mtime comparison) — **not ported**;
  `build-and-test` keeps its always-full-reactor-install strategy, a deliberate different
  trade-off, not a gap.

**Not part of this pass:** the actual unit+integration parallelization design in
`run-all-tests.sh` — explicitly deferred until this gap-closing work is done first.

### Gap-closing plan — done, verified end to end

All items implemented in `scripts/build-and-test/run.sh` (host) + `scripts/build-and-test/build.sh`
(container). Real bug found and fixed along the way, not anticipated by the plan: the first
attempt used a bind-mount volume (`-v "$REPORT_DIR:/reports"`) to get Surefire reports onto the
host — confirmed empty every time, same documented class of failure `playwright/run.sh` and
`sonar`'s scanner container already work around (`scripts/CLAUDE.md` "Docker socket constraint"):
a host-path bind mount resolves against the wrong filesystem when the caller is itself a Docker
container (this claude-dev sandbox). Fixed the same way those two already do — dropped `--rm` from
the `docker run`, `build.sh` copies reports to `/tmp/reports` inside the container, `run.sh` pulls
them out via `docker cp advertisement-build-only:/tmp/reports/. "$REPORT_DIR/"` after the run,
then explicitly `docker rm`s the container.

Verified for real, this sandbox:
- `bash scripts/build-and-test.sh --sandbox --unit --integration` — 165 integration tests (real
  `*RepositoryTest` classes, real Testcontainers Postgres) + all unit tests, both PASSED summaries
  print correctly, reports land in `scripts/build-and-test/reports/surefire/<module>/` on the host.
- `--unit-test marketplace-orchestrator` — correctly narrows to only that module's 4 test classes.
- `--integration-test smoke` — correctly narrows to only `PostgresContainerSmokeTest`.
- Container cleanup confirmed: `docker ps -a --filter name=advertisement-build-only` empty after
  each run.
- `scripts/build-and-test/reports/` added to `.gitignore` (same pattern as
  `scripts/unit-tests/reports/`, `integration-tests/reports/`).

`GITHUB_ACTIONS` guard and the Docker-daemon-reachable precheck were added to `run.sh` matching
`integration-tests/run.sh`'s own logic exactly, but not separately exercised in this sandbox (no
way to simulate `GITHUB_ACTIONS=true` or a down daemon safely here) — code-reviewed against the
original, not independently run.

### Unit + integration parallelization inside `build-and-test/build.sh` — done, verified

Once the shared `~/.m2` install step finishes (still `flock`-serialized against other concurrent
callers), the unit-test and integration-test phases only *read* it and touch different `target/`
directories (`query-lib`/`marketplace-app`/`marketplace-orchestrator` vs `integration-tests`) — no
write conflict, so no lock needed *between* them. Restructured `build.sh`'s test phase: when both
`RUN_UNIT`/`RUN_INTEGRATION` are true, `run_unit_tests`/`run_integration_tests` (each its own
function, output captured to `/tmp/unit-tests.log`/`/tmp/integration-tests.log`) launch as
background jobs, `wait` for both, then print each captured log + PASSED/FAILED summary in a fixed
order (unit then integration) so parallel execution doesn't interleave output into something
unreadable. Single-suite runs (`--unit`/`--integration` alone) skip the backgrounding entirely, no
behavior change for that case.

**Verified end to end, this sandbox:** `bash scripts/build-and-test.sh --sandbox --unit
--integration` — exit 0, both suites PASSED (unit 50.220s, integration 56.531s, ran concurrently
per their own `[INFO] Total time` lines), reports for both landed on the host correctly. Total
wall time ≈ install time + max(unit, integration) instead of install + unit + integration
sequentially — the slower suite's own duration is the floor, not fixable by this change alone, but
real savings versus running them one after another.

**Future consideration, not decided, not started:** now that `build-and-test`'s `RUN_UNIT`/
`RUN_INTEGRATION` have reached feature parity with the standalone `scripts/unit-tests.sh`/
`scripts/integration-tests.sh` (module/test selection, host-copied reports, PASSED/FAILED
summaries, `--sandbox`), the two standalone scripts may become redundant duplication rather than a
genuinely separate capability. Retiring them in favor of `build-and-test.sh` exclusively is a
real future option — not decided, not scheduled, needs its own explicit go-ahead before touching
either script (both are still referenced throughout `scripts/CLAUDE.md`, `run-all-tests.sh`, and
CI-adjacent tooling, so removal is a real, multi-file change, not a quick delete).

### Real gap found, not fixed: Playwright in `run-all-tests.sh` never sees `build-and-test.sh`'s fresh build

Confirmed by reading `playwright/run.sh` directly: it only checks that `marketplace-app` is
already running and drives HTTP tests against it — zero interaction with the `maven-cache` Docker
volume, `marketplace-app.jar`, or anything `build-and-test.sh` just produced. `run-all-tests/
run.sh` runs `build-and-test.sh --unit --integration` and `playwright.sh` in parallel, but the two
are completely disconnected — `playwright.sh` tests whatever was last deployed via a separate,
manual `bash scripts/deploy.sh` run, which could be arbitrarily stale relative to current source.
This isn't a regression from this session's work — the original `run-all-tests.sh` had the exact
same property (`unit-tests.sh`/`integration-tests.sh` never updated `marketplace-app` either) — but
it's now a sharper gap given `deploy.sh --reload`'s hot-swap mechanism was removed entirely this
same session, so there is currently **no automated path at all** from "fresh build" to "what
Playwright actually tests." `run-all-tests.sh` reporting `ALL PASSED` does not mean current source
passed Playwright — only that whatever's currently deployed did.

**Not fixed now, explicit decision:** the fix requires redesigning the whole test/deploy chain
around `build-and-test` as the one shared build step every consumer (unit, integration, Sonar,
Playwright, and a real dev-deploy) reads from — the same "one shared build, everyone else just
reads the result" pattern already recorded earlier in this issue for Sonar's own case. This is a
real, multi-file redesign (`deploy.sh`, `playwright/run.sh`, possibly a real `--deploy` step
resurrected in `build-and-test` itself, this time reading the artifact instead of rebuilding it),
not a small patch — needs its own explicit scoping/go-ahead before starting, not bundled into this
already-large batch.

### Why this is being looked at

While reviewing the SPI Map diagram's data quality (`improvement-151`'s Step 0 work), found
concrete, confirmed evidence that the current grep-regex-based extraction in
`generate-architecture-model.sh`'s `spi_map_json()` has real accuracy gaps:
- All 14 `platform-commons` `*.spi` interfaces' Javadoc `Port: marketplace → X-starter.`/
  `Hook: ... → marketplace.` first lines are stale post-BFF-migration — the real caller/implementor
  module is always `marketplace-orchestrator`, never `marketplace-app` directly, confirmed against
  `MODEL.spiMap.details[].callers`/`.implementations` for every single one of them.
- `spi_map_json()`'s `IMPL_PATTERN`/`CALLER_PATTERN` are field-declaration/`implements`-clause text
  regexes — class-level only, no method-level data, and blind to any caller that doesn't fit one of
  3 hardcoded field-declaration shapes (a method-parameter injection, a local variable, etc.).
- `AuditAutoConfiguration` shows up as an `AuditPort` "caller" purely because it imports the type
  for `@Bean` wiring — a false positive at the method-call level, found while mocking up the SPI
  Interface Details table redesign (see `improvement-151`'s "Ideas" section).

Real ArchUnit-based bytecode analysis (not text regex, not hand-written Javadoc) would close all
three gaps — see chat discussion this session for the fuller reasoning (real caller/implementor
edges verified by bytecode, method-level granularity, distinguishing real calls from DI-wiring-only
references, and a path to a real Test Coverage overlay).

### What already exists — correction: not actually working (found 2026-08-16)

- `ArchitectureMetricsExport` (`marketplace-app/src/test/java/org/ost/marketplace/architecture`) —
  an ArchUnit exporter meant to write `marketplace-app/target/architecture-metrics.json` (module-
  level Efferent/Afferent Coupling, Instability, Abstractness), read by
  `generate-architecture-model.sh --with-archunit`. **Confirmed broken, not just stale docs.** The
  class name doesn't match Surefire's default include patterns (`*Test`/`Test*`/`*Tests`/
  `*TestCase` — "ArchitectureMetricsExport" matches none), so a plain `mvn test` — via
  `unit-tests.sh` or anything else — never discovers or runs it at all; it silently never executes.
  Forcing it directly (`-Dtest=ArchitectureMetricsExport`, which bypasses the naming filter) shows
  it also **fails** when run scoped to fewer than all modules:
  `IllegalArgumentException: This package does not contain any sub package 'org.ost.audit'` — its
  `@AnalyzeClasses(packages = "org.ost")` scan needs every module's classes on the classpath
  simultaneously, not just the 2-3 modules any of today's test scripts scope to. Net effect:
  `--with-archunit`'s module-coupling table has almost certainly never actually populated from a
  real run — this was already broken before this session, unrelated to any change made here.
- The Code Quality screen already has placeholder rows anticipating the *next* gap beyond this one
  (interface/method-level data, not just module coupling): "Contract method signatures & types",
  "Implementation classes", "Methods", "Test coverage (DIRECT/INDIRECT/E2E)" — all tagged
  `needs ArchUnit exporter`.

### The real blocker — this is a decision gate, not a technical one

This is exactly `improvement-138`'s **Track B**, and it is **officially blocked**, per that issue's
own recorded Finding 3: Track B conflicts with `improvement-135` item 5's governing rule — *"No new
navigation file, no new metadata field, no expansion of `adr-index.md`'s schema... until items 2-4
show the existing layer is pulling its weight, or a specific, evidenced discovery failure
demonstrates a gap the current layer can't cover."* Track B's L0-L5 AI Context Layer is squarely
the kind of new AI-navigation content that rule was written to gate.

`improvement-138` records exactly two conditions that unblock Track B (still both unmet as of this
writing — confirmed against `improvement-135`'s own current status: item 3's mechanism is built,
but its empirical answer is still pending real accumulated data):
1. `improvement-135` item 3 produces real accumulated evidence (via `/sync-docs --full-audit`)
   showing the *existing* `context-loading.md`/`flows.md` layer is not sufficient for
   architecture/impact-analysis-shaped tasks — a genuine discovery-gap finding.
2. The user explicitly decides Track B itself *is* that evidenced-gap exception — recorded plainly
   as a deliberate decision (who decided it and why), not silently assumed.

Today's SPI Map findings (above) are **not** the same thing as condition 1 — they're evidence the
*generator's own data source* has accuracy problems, not evidence about `context-loading.md`'s
AI-context-loading efficiency specifically (`improvement-135` item 3's actual hypothesis). They
could plausibly support condition 2 (a user decision that this particular gap justifies starting
Track B early), but that's the user's call to make explicitly, not something to assume from this
issue's own existence — same framing `improvement-138`'s Finding 3 already established.

Two known implementation-detail fixes already recorded in `improvement-138`, to apply whenever B
actually starts (not blockers, just corrections to the original plan):
- Finding 1: the production-code exporter and any future test-scanning exporter need two separate
  `@AnalyzeClasses` configurations — `ArchitectureRulesTest`'s existing import uses
  `ImportOption.DoNotIncludeTests`, so it cannot be literally reused for a test-scanning exporter.
- Finding 4: any token-cost/AI-context measurement Track B adds must extend `improvement-135`'s
  existing `## Operational notes` block, not introduce a differently-named block.

### Not yet done

- No decision made on which of the two unblock conditions applies, or whether to wait longer.
- No design work started on what an ArchUnit-based `spi_map_json()` replacement would actually look
  like (query shape, caching/staleness relative to `bash scripts/unit-tests.sh`, how it'd feed the
  SPI Interface Details table redesign from `improvement-151`'s "Ideas" section).

## Part C — SPI Interface Details table redesign — split Callers/Implemented By, group by Module → Class → Method

Moved verbatim from `improvement-151`'s "Ideas — captured, not started" section once that issue's
own real work was ready to close. Design only, no code written.

Scoped first to the Audit Subsystem (`org.ost.platform.audit.spi`, `SPI Interface Details (3)`
card) as the experiment, before spreading the same shape to every other subsystem's table.

**Shape:**
1. Split the current single table (`Caller(s) | Interface | Direction | Implementation(s) |
   Purpose`) into **two separate tables** — one for Callers, one for Implemented by.
2. Inside each table, group rows: **Module** (1st column) → **Class** (2nd column) → **Method**
   (3rd column).
3. Method column format:
   - Callers table: `callerMethod() → Interface#interfaceMethod()` — the caller's own method next
     to the specific interface method it invokes, not just "this class touches this interface"
     class-level as today.
   - Implemented by table: bare `method()` per line (the row's own Class column already identifies
     which class it belongs to, so no `ClassName#` prefix needed there — only needed if a future
     variant ever combines multiple implementation classes into one cell).

A concrete mockup for the Audit Subsystem, built from real grep against the current code (not
placeholder text), was shown and approved in chat — worth re-deriving rather than restating in
full here, but the real findings that came out of building it are worth keeping:

- **`AuditAutoConfiguration` is not a real per-method caller.** It only shows up in today's
  class-level scan because it imports `AuditPort`/`DefaultAuditPort` for `@Bean` wiring — there's
  no method call to put in a Method column for it. The redesigned table needs an explicit answer
  for this shape (drop it from Callers entirely, or show it as `(DI wiring)` with no method), not
  silently reuse today's class-level "callers" list as-is.
- **Zero method-level data exists in the model today.** `spi_map_json()`'s `IMPL_PATTERN`/
  `CALLER_PATTERN` are field-declaration/`implements`-clause regexes — class-level only, no method
  parsing. Building this table for real (not a hand-typed mockup) needs new extraction work,
  larger in scope than the existing class-level scan: per interface method, grep each candidate
  caller/implementor file for an actual invocation/override of that specific method.
- **Generic interfaces need extra thought.** `AuditActivityEnrichHook<T extends AuditableSnapshot>`
  — a bare `entityType()` method name in the table says nothing about which concrete `T` a given
  implementation (e.g. one bean per `EntityType`) is registered for; may need the type argument
  shown alongside the method, not decided how.

**Natural link to Part B**: a real ArchUnit-based exporter (once Track B unblocks) would be the
mechanical data source this table redesign actually needs (method-level caller/implementor pairs) —
the two parts are independent to file but likely sequenced together in practice.

## Related

- `improvement-151` — where the original `build.sh` topic, the SPI Map findings motivating Part B,
  and Part C's own design work all came from; see that issue for the full architecture-generator
  cleanup history.
- `improvement-138` — Track B's own issue, Finding 3 (the blocking rule) and Findings 1/4 (fixes to
  apply once unblocked).
- `improvement-135` — item 5 (the governing rule blocking Track B), item 3 (the evidence Track B is
  waiting on).
- `scripts/DECISIONS.md` ADR-007 — the existing staleness-check mechanism Part A extends the
  benefit of.
- `scripts/DECISIONS.md` ADR-004 — `run-all-tests.sh`'s sequential-Maven/parallel-Playwright
  design; `build.sh` slots in as its new first step.
- `scripts/CLAUDE.md` "Plain Unit Tests" / "Unit / Testcontainers Tests" sections — the two
  scripts' documented behavior Part A would update once implemented.
