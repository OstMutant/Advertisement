# improvement-174: Bounded Contexts' `ports_json` — replace regex extraction with real ArchUnit data

**Type:** improvement — found while reviewing `System › Diagrams › Bounded Contexts` and
`System › Diagrams › Module Dependencies` after closing `improvement-156`/`157`.
**Module:** `docs/architecture/scripts/generate-architecture-model.sh` (`bounded_contexts_json()`,
and `MODULE_CONTRACT` population feeding the Module screen's own "Contracts" section -- see the
third occurrence noted below).
**Priority:** Top — direct continuation of `improvement-156`/`157`'s fix, same root cause, same
real data already available.
**When:** unblocked, ready to implement — `improvement-156`'s `spiEdges` (real, bytecode-derived
implementor/caller data) already exists and covers exactly the data this issue needs.

## Why this is being looked at

`improvement-156` replaced `spi_map_json()`'s regex-based caller/implementor extraction with real
ArchUnit bytecode data, after finding and fixing a confirmed false positive
(`AuditAutoConfiguration` showing up as an `AuditPort` caller purely from a `@Bean`-wiring import)
and a false negative (`AuditActivityEnrichHook` missing a real second caller). While reviewing the
two other live diagrams (`01-module-dependencies`, `bounded-contexts`) for the same class of issue:

- **`01-module-dependencies` (`module_deps()`) — no issue found.** It's an AWK parser of real
  `pom.xml` `<dependency>` tags matched against the known module list — a literal declared fact
  from the build file (what Maven will actually resolve), not a text-pattern guess over source
  code. No false-positive/negative risk analogous to SPI Map's regex scan exists here at all.
- **`bounded_contexts_json()` — same class of bug found, not yet confirmed as a real occurrence.**
  Its `ports_json` (which SPI ports/hooks belong to each domain, shown on the Bounded Contexts
  screen) is built via the exact same shape of regex `spi_map_json()` used before the fix, in three
  places:
  - UI domain: `grep -rl 'implements .*Hook' "$REPO_ROOT/marketplace-app/.../spi"` — text
    `implements` match, not bytecode assignability.
  - Orchestrator domain: `ComponentFactory<X>|X field;|implements X` — literally the same pattern
    as the old `IMPL_PATTERN`/`CALLER_PATTERN` this issue's predecessor already proved produces
    false positives (DI-wiring-only references, not real usage).
  - Every other starter (default branch): `implements\s+.*\b${iface}\b` — same `IMPL_PATTERN` shape
    again.

**Third occurrence found (2026-08-27), while adding real file links/Javadoc descriptions to the
Module screen's Entities/Key services/Contracts sections:** `MODULE_CONTRACT` (feeds the Module
screen's own "Contracts (Port/Hook)" section — a third, independent consumer, not
`bounded_contexts_json()`'s `ports_json` and not `spi_map_json()`) uses the exact same
`implements\s+.*\biface\b`/`ComponentFactory<X>` regex shape, in the same two spots (per-starter
default case, Orchestrator's `ComponentFactory<X>` override). Not fixed as part of that work
(out of scope there — only file links/descriptions were added, the underlying class list itself
was left as-is) — noted here since it's the same root cause this issue already tracks.

## Plan — agreed 2026-08-27, not yet implemented

One shared bash helper, `spi_owns_iface(module, interface)`: reads the same `spiEdges` data
(`ARCHUNIT_METRICS_FILE`/`_FALLBACK`, same lookup `spi_map_json()` already uses) and checks whether
`module` appears in that interface's real `implementations[].module` or `callers[].module` list.
Returns a 3-way signal via exit code: `0` = real data confirms ownership, `1` = real data confirms
no ownership, `2` = no ArchUnit data available at all (caller falls back to its own existing regex
unchanged — never silently loses coverage when `--archunit-metrics` hasn't been run this session).

Applied at all four now-known occurrences of the same regex shape:
1. `bounded_contexts_json()` — UI domain's `ports_json`.
2. `bounded_contexts_json()` — Orchestrator domain's `ports_json`.
3. `bounded_contexts_json()` — every other starter (default branch) `ports_json`.
4. `MODULE_CONTRACT` population (Module screen's own "Contracts" section, both its default and
   Orchestrator-override branches).

Each site keeps iterating the same `find .../spi/*.java` file list it already does (still needed
for the interface's own file path) — only the *membership check* (currently a `grep -qlP` line)
gets replaced by `spi_owns_iface`, falling back to that exact same `grep` line when the exit code
is `2`.

**Known ordering fix needed first** (same class of bug hit earlier this session with
`javadoc_purpose_for`): `ARCHUNIT_METRICS_FILE`/`ARCHUNIT_METRICS_FILE_FALLBACK` are currently
declared at line ~1109, but `MODULE_CONTRACT` (occurrence 4) is populated at top-level script scope
around line ~316 — calling `spi_owns_iface` there before those variables are assigned would
silently always take the "no data" (`2`) path. Both variable declarations and the new
`spi_owns_iface` function itself must move above line ~316 before this can work correctly.

**Unrelated small visual fix found on the same screen, bundled into this issue rather than filed
separately (small, same screen, same review pass):** Bounded Contexts' "Domain Contents" section
links (entities/services/tables/ports, `bcItemLink()`) are visually indistinguishable from plain
text — only the cursor changes on hover, no color/underline. Root cause: they render inside a
`<div class="adr-item">`, and `.adr-item a { color: var(--ink); ... }` (shared with the unrelated
ADR-history list, which deliberately wants ink-colored links) makes them the same color as
surrounding body text. Fix: add `class="module-link"` to `bcItemLink()`'s own `<a>` tag (reuses the
existing accent-colored link style already used everywhere else, without touching the shared
`.adr-item` rule other callers rely on). Also: the domain's own module name next to its label
(`${esc(d.module)}`, e.g. `attachment-spring-boot-starter`) is plain text today — make it
`moduleLink(d.module)` instead, same as everywhere else on this page.

**General principle stated for future diagram work (2026-08-27), not a separate action item here:**
the four live diagrams (Module Dependencies, SPI Map, Bounded Contexts, Database ERD) should share
one consistent visual language for links/module references wherever their content shape allows it
(the `module-link`/accent-color treatment this fix applies), rather than each diagram's styling
drifting independently based on whichever container class it happened to render inside. The
ADR-history list's own `.adr-item` ink-colored style is a deliberate, separate exception — not
something this consistency goal argues for changing.

**Relationships table redesign (2026-08-27), same shape as `improvement-157`'s SPI Map split:**
`BC_LABEL_CATEGORY` shows 3 of the 4 category tabs (orchestration/hooks/exceptions) have exactly
one `label` value in their whole Relationships table — the Label column there just repeats the
same text on every row. The 4th tab (derived) mixes two distinct labels ("audited via", "can have")
in one table. Fix: group `relationships` by `label`, render one `<table>` per label (heading
`<h4>Label (N)</h4>`, the existing per-label hover-meaning moved onto that heading), Label column
removed from the table itself (Relationship | Payload | Evidence only) since it's now redundant
with the heading. `exportBoundedContextsMarkdown()` stays untouched — it deliberately keeps a flat
one-document-with-Category-column export, a different, already-justified shape.

**Payload column rowspan (2026-08-27):** `BC_LABEL_PAYLOAD[label]` is one fixed generic text per
label for every label except "calls back via Hook implementations" (which has real per-edge
payload granularity, `rel_payload`) — so within each of the new per-label tables above, Payload is
identical across every row for 3 of the 4 labels. Reuses the existing generic `consecutiveRowspan()`
helper (already built for Code Quality's Findings tables) to group consecutive same-payload rows
into one rowspan cell, plus the existing `rowspan-table` CSS class for vertical centering — no new
helper or CSS needed, purely applying what already exists.

## Implemented and verified (2026-08-27)

- `spi_owns_iface(module, interface)` added to `generate-architecture-model.sh` (ahead of the
  `MODULE_ENTITY`/`MODULE_KEYSERVICES`/`MODULE_CONTRACT` population block, alongside the
  `ARCHUNIT_METRICS_FILE`/`_FALLBACK` variables it reads) — 3-way exit-code lookup against
  `spiEdges`, applied at all 4 occurrences (`bounded_contexts_json()`'s UI/Orchestrator/default
  branches, plus `MODULE_CONTRACT`'s default + Orchestrator-override branches), each still falling
  back to its original regex only when the exit code is `2` (no ArchUnit data this session).
- **Real false negative found and fixed during verification, beyond the original plan's scope:**
  UI domain's `ports_json` dropped from 3 to 0 real ports after the initial rewrite — its original
  regex scanned `marketplace-app/.../spi/*.java` directly (interface-agnostic `implements .*Hook`),
  while the other 3 occurrences scan `platform-commons/.../spi/*.java` for candidate interface
  names. UI's 3 real Hook interfaces (`CurrentLocaleHook`, `SessionActorHook`, `UiLabelHook`) are
  declared in `marketplace-orchestrator/src/main/java/org/ost/orchestrator/spi/`, not
  `platform-commons` — a directory the other occurrences never had reason to scan. Fixed by:
  1. Generalizing `ArchitectureMetricsExport.spiEdges()`'s own package filter from
     `isPlatformSpiPackage` (hardcoded to `org.ost.platform.*.spi`) to `isSpiPackage`
     (`packageName.endsWith(".spi")`, any module) — a dynamic mechanism: any future module's own
     `*.spi` package with a real interface is picked up automatically, no hardcoded package list to
     maintain. Confirmed safe: every other `.spi` package outside `platform-commons` currently
     holds only `*Impl` classes (filtered out by the existing `iface.isInterface()` check), never a
     second interface declaration — verified by listing every `.spi` package repo-wide.
  2. UI's `ports_json` now iterates the union of `platform-commons/.../spi/*.java` and
     `marketplace-orchestrator/.../spi/*.java` as candidates, `spi_owns_iface` first, regex
     fallback scoped to `marketplace-app/.../spi` only on `exit=2`.
  This surfaced a second, larger real false negative on top of the one just described: UI's port
  list actually grew from 3 to **5** once real ArchUnit data was available —
  `CurrentUserHook` (implemented by `AuthContextService`, `services/auth/`) and
  `SettingsChangeHook` (implemented by `SettingsPaginationService`,
  `ui/views/services/pagination/`) are real Hook implementations that live outside the dedicated
  `spi/` wrapper package entirely (per `marketplace-app/CLAUDE.md`'s own documented exception for
  these two forwarders) — the old directory-scoped regex could never have found them regardless of
  which directory it scanned. Confirmed via `ArchitectureMetricsExport`'s real bytecode data, not
  assumed.
- `bcItemLink()` now emits `class="module-link"` on its `<a>` tag; `renderBoundedContextsExtrasHtml`'s
  domain header now uses `moduleLink(d.module)` instead of plain text. Verified via computed style
  in headless Chromium: links inside `.adr-item` now render in the accent color
  (`rgb(43, 108, 176)`), previously indistinguishable from body text.
- Relationships table redesigned: grouped by `label`, one `<table class="rowspan-table">` per label
  with an `<h4 title="...">Label (N)</h4>` heading carrying the hover-meaning (previously a
  per-cell `title` attribute); Label column removed from the table body. Verified per category tab:
  `orchestration`/`hooks`/`exceptions` each render exactly 1 grouped table (single label), `derived`
  renders 2 (`audited via (3)`, `can have (1)`) — matching the real label distribution.
- Payload column rowspan applied via the existing `consecutiveRowspan()` helper — verified a
  `rowspan="7"` cell renders with `vertical-align: middle` (the `table.simple.rowspan-table`
  selector specificity fix from `improvement-157` already covers this).
- `exportBoundedContextsMarkdown()` confirmed untouched, as planned (different, already-justified
  flat shape).

All of the above verified by regenerating `architecture-map.html` (`--with-archunit`, after a real
`bash scripts/build-and-test.sh --archunit-metrics` rebuild to pick up the broadened `isSpiPackage`)
and inspecting real DOM/data in headless Chromium — not simulated. SPI Map's own interface set
(15 platform-commons interfaces) confirmed unchanged by the `isSpiPackage` broadening, since its
own per-subsystem walk only ever looked at `platform-commons/.../{subsystem}/spi/*.java` to begin
with.

## Related

- `improvement-156` — the ArchUnit-based `spiEdges` data source this issue reuses, and the
  precedent for the false-positive/negative class of bug this issue investigates in a second
  consumer.
- `improvement-157` — the SPI Interface Details table redesign that consumed the same `spiEdges`
  data on the SPI Map side.
