# improvement-146: On-demand Sonar/ArchUnit refresh via a local companion server

**Type:** improvement — design + implementation plan for a single mechanism, extracted verbatim
from `improvement-144` (Part B of that issue's original scope) once `improvement-144`'s other
steps (0/1/3/4/5) were all done and the file closed out.
**Module:** `scripts/architecture/generate-architecture-model.sh`, `docs/architecture/architecture-map.html`,
new `scripts/architecture/architecture-refresh-server.*`.
**Priority:** 🟡 **tentative — not yet decided whether this will actually be built.** Positioned
right after `improvement-145` in the backlog per explicit request, but that is a position marker
only, not a commitment to implement; revisit before starting.
**When:** independent, no blockers, once/if greenlit.

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

## Related idea, not in scope (found 2026-08-07, during improvement-145)

The ADR popup's full-text embedding (`scripts/architecture/DECISIONS.md` ADR-008) accounts for
~605KB of the current ~841KB `architecture-model.json` (~72%) — a `file://`-page constraint, same
root cause as this issue's own Sonar/ArchUnit refresh problem (no server, so everything needed
upfront must be baked in). If this companion server is ever built, a natural extension is a
`GET /adr?module=X&id=Y` endpoint backed by the already-built
`scripts/architecture/md-to-decisions-json.js --extract` (built for Claude's own on-demand reads,
`improvement-145`), letting the popup fetch one ADR on demand instead of embedding all of them.
Not folded into the plan above — this issue's own priority is still "not yet decided whether to
build at all"; revisit this extension only if/when that's greenlit.

## Related

- `improvement-144` (completed) — Part A/C of the original scope (Code Quality + ADRs
  screens, opt-in flags, tooling reorg); this file is that issue's Part B, split out once the rest
  was done.
- `improvement-143` — implemented the SonarQube/ArchUnit Code Metrics section this refresh flow
  operates on.
- `improvement-138` — the original Architecture Control Plane plan.
- `docs/architecture/README.md` — documents the tool's current no-server property; this issue's
  companion server is an explicit, opt-in exception to that, not a replacement of it.
