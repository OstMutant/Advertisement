# improvement-146: On-demand Sonar/ArchUnit refresh via a local companion server

**Type:** improvement — design + implementation plan for a single mechanism, extracted verbatim
from `improvement-144` (Part B of that issue's original scope) once `improvement-144`'s other
steps (0/1/3/4/5) were all done and the file closed out.
**Module:** `scripts/architecture/generate-architecture-model.sh`, `docs/architecture/architecture-map.html`,
new `scripts/architecture/architecture-refresh-server.*`.
**Priority:** Closed 2026-08-07 — **decided against building the companion server.** The
ADR-embedding part of this issue's original scope shipped instead (see below), without a server.
**When:** n/a — resolved, not deferred.

## Problem

`architecture-map.html` is a static, self-contained file — confirmed directly: the whole model is
inlined as `const MODEL = {...}` in a `<script>` block, no `fetch()` of a separate JSON file
anywhere in the page. It's opened via `file://`, documented in `docs/architecture/README.md` as
"open in a browser", no server involved today. Refreshing the Sonar/ArchUnit numbers currently
means running `bash scripts/sonar.sh`, then `bash scripts/unit-tests.sh` (which regenerates
`marketplace-app/target/architecture-metrics.json` via the `ArchitectureMetricsExport` JUnit
test), then regenerating the HTML — all by hand, outside the tool, then a manual page reload.

A literal "button that refreshes the data" cannot work on a `file://` page — browser sandboxing
blocks any filesystem/process access from page JS, regardless of framework. Decision already made
(2026-08-06): accept the tradeoff and add a small local companion server, rather than a
copy-command-to-clipboard button. This file plans that server concretely.

## What already exists (confirmed by reading the code, not assumed)

- `ensure_sonar_fresh()` (`scripts/architecture/generate-architecture-model.sh`) — already knows
  how to detect a stale/missing Sonar analysis and re-run `bash scripts/sonar.sh --no-gate`. Gated
  behind `--with-sonar` since `improvement-144` Step 0 — off by default.
- `sonar_metrics_json()` — reads from the already-running SonarQube server's REST API.
- `archunit_metrics_json()` — reads `marketplace-app/target/architecture-metrics.json` if present,
  else `null`. Written only by `ArchitectureMetricsExport`
  (`marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureMetricsExport.java`),
  a plain JUnit test — so `bash scripts/unit-tests.sh` (or `bash scripts/unit-tests.sh
  marketplace-app`) is what actually regenerates it. No standalone way to run just this one test
  class via the existing `scripts/unit-tests.sh <TestClass>` form was verified yet — plan assumes
  `bash scripts/unit-tests.sh marketplace-app` (whole module) unless a narrower invocation is
  confirmed to work during implementation.
- `scripts/ci/entrypoint.sh` already establishes this repo's precedent for a background job with a
  live-progress file (`PROGRESS_FILE="$REPORT_DIR/progress.txt"`, rewritten periodically) instead
  of a blocking synchronous call — the refresh server's own progress reporting should follow the
  same shape, not invent a new one.
- Ports already claimed elsewhere in this repo (avoid colliding): `5432`/`15432` (Postgres),
  `9000`/`9001`/`19000`/`19001` (MinIO), `8080`/`8081`/`18081` (app), `9099` (SonarQube). The
  companion server needs its own free port — not yet picked, see Open questions.
- `--with-sonar`/`--with-archunit` flags on `generate-architecture-model.sh` already exist
  (`improvement-144` Step 0) — the refresh endpoint below can rely on them directly, no longer an
  open question.

## Plan

**1. `scripts/architecture/architecture-refresh-server.sh`** — a thin start/stop wrapper (same
shape as `scripts/sonar.sh`'s own start-if-not-running pattern), launching a small HTTP server
bound to `127.0.0.1:<PORT>` only (never `0.0.0.0` — this is a local dev tool, not a service).
Language choice: Python3's stdlib `http.server` with a custom `BaseHTTPRequestHandler` — `python3`
is already a hard dependency of this generator (`sonar_metrics_json()`'s inline `python3 -c`
block), so this adds no new tool to the environment. (Node is the other already-used option, via
`liquibase-schema-to-json.js` — pick one during implementation, default to Python3 unless a reason
favors Node.)

**2. Endpoints:**
- `POST /refresh` — kicks off, in the background, the same three steps a developer runs by hand
  today: `bash scripts/sonar.sh --no-gate`, then `bash scripts/unit-tests.sh marketplace-app`,
  then `bash scripts/architecture/generate-architecture-model.sh --with-sonar --with-archunit`.
  Returns immediately (202-style), doesn't block the HTTP response on the full multi-minute chain.
- `GET /status` — returns the current stage (`idle` / `sonar` / `unit-tests` / `generating` /
  `done` / `error`) and elapsed seconds, written to a small JSON progress file the background job
  updates — same shape as `scripts/ci/entrypoint.sh`'s `progress.txt`, reused for consistency
  rather than inventing a second progress-reporting convention in the same repo.
- Regenerating `architecture-map.html` in place (not a separate JSON fetch) is the whole point of
  reusing the inline-`MODEL` design — once `/status` reports `done`, the page just calls
  `location.reload()` and the browser re-reads the same `file://` path, picking up the freshly
  written HTML with the new `MODEL` baked in. No second data-fetching mechanism needed.

**3. Page-side button** (`architecture-map.html`, System screen) —
`fetch('http://127.0.0.1:<PORT>/refresh', {method: 'POST'})`, then poll `/status` every few
seconds (same interval convention as this project's other Monitor+progress-file patterns — not
`waitForTimeout`-style guessing), updating a small inline status line, then `location.reload()` on
`done`. Button disabled while a refresh is in flight. If the `fetch()` itself fails (server not
running), show a plain message: "Start the refresh server first: `bash
scripts/architecture/architecture-refresh-server.sh start`" — no auto-start attempt from page JS,
since that's exactly the file-system access browsers block.

**4. CORS — real gotcha, needs verification during implementation, not assumed away.** A page
opened via `file://` has a `null`/opaque origin. `fetch()` from that origin to
`http://127.0.0.1:<PORT>` is a cross-origin request; most browsers require the server to answer
with `Access-Control-Allow-Origin: *` (or explicitly echo `null`) or the request is blocked
client-side before the button does anything useful. The Python `http.server` handler must send
this header on every response. Confirm this actually works in a real browser during
implementation — don't assume the header alone is sufficient without testing it against a
`file://`-opened page specifically (not `http://localhost`-served, which wouldn't exercise the
real constraint this design exists for).

**5. Lifecycle & docs.** `architecture-refresh-server.sh start|stop|status`, PID file under
`scripts/architecture/.refresh-server.pid`, log file under the same reports convention other
scripts use. `docs/architecture/README.md` gets a new short section: this server is optional, only
needed for the in-page refresh button — viewing the map itself still needs nothing running. Not
started automatically by anything else (not `deploy.sh`, not `sync-docs`) — a developer opts in
explicitly when they want the button to work.

## Open questions (resolve before/at implementation start, not silently defaulted)

1. **Port number** — needs a concrete unused pick (e.g. `8899`) confirmed against everything
   listed under "Ports already claimed" above.
2. **Whether `bash scripts/unit-tests.sh <TestClass>` can target just `ArchitectureMetricsExport`**
   instead of the whole `marketplace-app` module, to keep the refresh cycle faster — needs a real
   test run to confirm the narrower form works before committing to it in the plan.
3. **Error surfacing** — if `sonar.sh` or `unit-tests.sh` fails mid-chain (e.g. SonarQube
   unreachable, a real ArchUnit rule violation), `/status` should report `error` with enough detail
   for the button's status line to say something useful, not just "failed". Exact error-payload
   shape not yet designed.
4. **Whether to build this at all** — the tentative priority above is the real open question that
   precedes all the others; confirm before starting implementation.

## ADR on-demand embedding — decided 2026-08-07, independent of the server plan above

**No server needed for this part.** First framed (2026-08-07, during `improvement-145`) as a
possible extension of this issue's companion server — a `GET /adr?module=X&id=Y` endpoint. Revisited
the same day: this issue's own priority is still "not yet decided whether to build the server at
all," and a live server is overkill for data that changes as rarely as ADR text does (only when
someone adds a new decision — unlike Sonar/ArchUnit numbers, which change on every code edit and
genuinely benefit from a live refresh). Decided instead: reuse the generation-time opt-in flag
pattern `generate-architecture-model.sh` already has for the exact same shape of problem
(`--with-sonar`/`--with-archunit`) — a new `--with-adr-details` flag, no HTTP server, no port, no
CORS handling.

**Problem restated:** The ADR popup's full-text embedding (`scripts/architecture/DECISIONS.md`
ADR-008 in this module) accounts for ~605KB of the current ~841KB `architecture-model.json`
(~72%) — baked into every generation run whether or not anyone ever opens the popup, because the
page is `file://`-only with nothing to fetch from at runtime.

**Plan:**
1. New flag `--with-adr-details` on `generate-architecture-model.sh`, same dispatch shape as the
   existing `--with-sonar`/`--with-archunit` flags (`case` branch near the top of the script) —
   off by default.
2. Without the flag: `decisions_json_for()` returns `null` for every module instead of calling
   `md-to-decisions-json.js --stdout "$module"` — same shape `sonar_metrics_json()`/
   `archunit_metrics_json()` already use for their own off-by-default case. Each module node's
   `"decisions"` field becomes `null`; `architecture-model.json` shrinks by the ~605KB/72%.
   `MODEL.allAdrs` (the separate, always-lean `{id, title, status, module}` list the "ADRs"
   screen's card grid and search read, built by `all_adrs_json()` from `docs/ai/adr-index.md`) is
   untouched by this flag — that index has no full body text to begin with, stays present either
   way.
3. With the flag: unchanged from today — `decisions_json_for()` behaves exactly as it does now,
   full per-ADR body embedded, ADR-008's current design untouched.
4. Popup fallback when details are absent: `openAdrPopupForAdr(id, module)` currently does
   `homeNode.decisions.adrs.find(...)` — guard for `homeNode.decisions === null` and render a short
   message ("regenerate with `--with-adr-details` to see full ADR text") instead of the body, same
   `empty-hint`-style pattern the Code Metrics screen already uses for its own
   `--with-sonar`/`--with-archunit` off-by-default case (`"No data -- regenerate with
   <code>--with-sonar</code>..."`).
5. `scripts/architecture/DECISIONS.md` ADR-008 gets a dated Amendment: correct its current
   "legacy — direction now points toward a companion-server-backed on-demand model instead (see
   improvement-146)" line — the resolution that actually shipped is a generation-time flag, not a
   server; the companion server (Sonar/ArchUnit refresh) remains a fully separate, still-tentative
   idea.
6. Regenerate `architecture-model.json`/`architecture-map.html` both with and without the new
   flag; confirm the size delta roughly matches the ~605KB estimate and the popup fallback message
   renders correctly in the no-flag case, full ADR content still renders correctly in the
   with-flag case.

**Relationship to the Sonar/ArchUnit refresh plan above:** fully independent — this needs no
server, no port, no CORS handling, and can ship regardless of whether the companion server
(steps 1-5 in the main Plan section) is ever built. The server plan's own "tentative — not yet
decided" priority is unaffected by this decision.

**Result — built and verified 2026-08-07.** Steps 1-3 and 6 above implemented exactly as planned;
step 4 came out better than planned — instead of a footer hint appended to a plain file-open,
`openAdrPopupForAdr(id, module, title, status)` now always opens the dialog (title/status come
straight from the caller's `MODEL.allAdrs` row, always available regardless of the flag), with the
body falling back to a real source-file link plus a generic "see Tooling & Pipelines" pointer
(deliberately not naming the exact flag/command in the popup itself, to avoid coupling the message
to one script's CLI shape) when the module's full text isn't embedded. Step 5's Amendment split
into two dated entries on ADR-008 (one recording the 605KB/72% measurement and the initial
companion-server framing, one recording the same-day reconsideration and the flag actually
shipped) — also dropped the improvement-146 ticket citation from ADR-008's Status/Amendment text,
per the standing rule against ticket numbers in current-state docs.

Verified directly: default (no-flag) regeneration — 842KB → 244KB (−598KB, matches the ~605KB
estimate), zero nodes carry non-null `decisions`, `MODEL.allAdrs` still full (199 entries). With
`--with-adr-details` — 12 nodes (all of `FULL_DECISIONS_MODULES`) carry embedded decisions, size
back to ~843KB. `check-architecture-model-freshness.sh` passes against the committed (no-flag)
regeneration. `docs/ai/adr-index.md` regenerated (ADR-008's status line changed). Script header
comment (`# Uses:`) updated so `architectureToolingSelfDocs` — read live by the Tooling & Pipelines
screen — reflects the new conditional `md-to-decisions-json.js` invocation; spot-checked the
generated JSON directly for the corrected text (a line-wrap edit briefly introduced a stray space
mid-flag-pair, caught and fixed before commit).

## Result — companion server decided against, 2026-08-07

Discussed directly: the server's only real benefit is skipping ~4 manual commands (`sonar.sh` →
`unit-tests.sh` → regenerate → reload) in a scenario that happens rarely (the architecture map
isn't opened daily), against a real build cost (new long-running process, start/stop/PID
lifecycle, port coordination, and the CORS gotcha in the Plan section above still unverified in a
real browser). That cost/benefit compares unfavorably against how the ADR-embedding half of this
same issue actually got resolved — a real, measured problem (605KB/72% of the file) fixed with a
few lines behind an existing flag pattern, no new process at all. No concrete trigger (growing
pain, repeated request) makes the server worth revisiting later, so this is a closed decision, not
a deferred one — if the manual-refresh friction ever becomes a real complaint, re-open a new issue
with that concrete evidence rather than reviving this speculative plan.

The Plan/Open questions sections above are kept as-is (not deleted) as the design record, in case
a future issue wants to start from it rather than from scratch.

## Related

- `improvement-144` (completed) — Part A/C of the original scope (Code Quality + ADRs
  screens, opt-in flags, tooling reorg); this file is that issue's Part B, split out once the rest
  was done.
- `improvement-143` — implemented the SonarQube/ArchUnit Code Metrics section this refresh flow
  operates on.
- `improvement-138` — the original Architecture Control Plane plan.
- `docs/architecture/README.md` — documents the tool's current no-server property; this issue's
  companion server would have been an explicit, opt-in exception to that — moot now that it's not
  being built.

## Operational notes

- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- context_loading_task_type: Feature, single module
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: a backlog item with an already-written plan, split between a decided-against
  server piece and a small opt-in-flag piece that shipped
- flows_chosen: none
- flows_matched: no
