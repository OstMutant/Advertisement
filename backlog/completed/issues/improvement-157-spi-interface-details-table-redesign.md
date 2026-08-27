# improvement-157: SPI Interface Details table redesign — split Callers/Implemented By, group by Module → Class → Method

**Type:** improvement — partially shipped (see "Actual shipped shape" below), next increment
designed and approved in chat 2026-08-27, not yet implemented.
**Module:** `docs/architecture/scripts/generate-architecture-model.sh` (`spi_map_json()`,
`renderSpiCallsRows()`/`renderSpiImplementedByRows()`, architecture-map's SPI Interface Details
cards), `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureMetricsExport.java`
(`spiEdges()` — needs extending, see below).
**Priority:** Top — explicit placement, ranked directly after `improvement-156`.
**When:** unblocked — `improvement-156` (shipped 2026-08-27) already provides real method-level
caller/implementor data (`spiEdges`); this issue's remaining work only needs to extend that data
(caller method names, per-interface used/total counts) and render it.

Split out of `improvement-152` Part C once that issue's Part A/D work was ready to close — moved
there verbatim from `improvement-151`'s "Ideas — captured, not started" section.

## Scope

Scoped first to the Audit Subsystem (`org.ost.platform.audit.spi`, `SPI Interface Details (3)`
card) as the experiment, before spreading the same shape to every other subsystem's table.

## Shape

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

## Actual shipped shape (2026-08-27, via `improvement-156`)

Diverges from the "Shape" plan above: two tables (Calls / Implemented By) shipped, but grouped by
**Interface** (via `rowspan` on the Interface/Purpose cells), not Module → Class → Method. No
Method column exists yet — each row shows just `Caller (module)` / `Implementation (module)`, both
as clickable links (file link + `moduleLink()` to that module's page). Clicking a diagram edge
scrolls to and flashes its matching table row. The `AuditAutoConfiguration` DI-wiring-only-caller
question from the original "Shape" section turned out moot: real ArchUnit data never produces it
as a caller in the first place (it was purely a regex false positive), so no explicit
`(DI wiring)` display case is needed.

## Next increment — design approved in chat, not yet implemented (2026-08-27)

Keeps the current shipped structure (two tables, grouped by Interface, no restructure) and adds
two pieces of information to existing cells rather than new columns — mockups below built from
real code (`AdvertisementSaveService`/`UserService` calling `AdvertisementPort`), not placeholder
text:

**1. Full method-level detail folded into the existing Caller cell**, stacked vertically below the
class/module line already there:
```
AdvertisementSaveService
(marketplace-orchestrator)

save() → #save()
save() → #findById()
delete() → #delete()
delete() → #findById()
```
Format is `callerMethod() → #interfaceMethod()` per real call site, one per line — matches this
issue's original "Shape" section's Callers-table method format exactly, just placed inside the
existing Caller cell instead of a separate Method column. Considered and rejected: a separate
Method column (works fine for the simple "just interface method names" depth, but this full
`callerMethod() → #interfaceMethod()` depth reads worse split into its own column — the caller
class's identity gets lost among several call-site lines when they're not visually anchored under
it).

Same treatment for the Implemented By table's Implementation cell, using this issue's original
bare `method()` format (no `ClassName#` prefix, per the original "Shape" section's own reasoning).

**Requires extending `ArchitectureMetricsExport.java`'s `spiEdges()`**: currently
`methodsByCaller` collects a flat `Set<String>` of interface method names per caller class,
discarding which specific method **on the caller** made each call. Needs the origin method name
too — `JavaMethodCall.getOrigin()` returns the calling `JavaMethod` itself (not just
`.getOrigin().getOwner()`, the owning class, already used) — so each caller's entry becomes a list
of `(callerMethod, interfaceMethod)` pairs, not a flat method-name set. `spi_map_json()`'s python3
extraction and `renderSpiCallsRows()`/`renderSpiImplementedByRows()` both need updating to carry
and render the pairs.

**2. A "(N/M methods used)" counter inside the existing Interface cell**, stacked below the
interface link (same vertical pattern as the Caller cell above) — appears once per interface,
already rowspan-grouped, so no repetition problem:
```
AdvertisementPort
(9/9 methods used)
```
N = size of the union of interface methods actually called across all real callers; M = total
methods declared on the interface. Requires `spiEdges()` to also emit each interface's own total
method count (`iface.getMethods().size()`) alongside the existing callers/implementations lists.

## Implemented (2026-08-27) — the increment above, plus a real dead-code-in-contract signal

Both pieces shipped and verified live via headless Chromium (not just JSON inspection):
- **Caller cell**: `ArchitectureMetricsExport.java`'s `spiEdges()` now tracks real
  `(callerMethod, interfaceMethod)` pairs per caller (`call.getOrigin().getName()` for the caller's
  own method, deduped/sorted via a `TreeSet` keyed pair string). Rendered as
  `callerMethod() → #interfaceMethod()`, one per line, stacked under the existing class/module line
  in the same cell — confirmed exact for `AdvertisementSaveService` → `AdvertisementPort`
  (`save() → #save()`, `save() → #findById()`, `delete() → #delete()`, `delete() → #findById()`,
  plus a real call this issue's own manual grep had missed: `buildCurrentSnapshot() → #findById()`).
- **Interface cell**: extended further than planned once real data made it possible — instead of
  just an `(N/M methods used)` count, now lists every one of the interface's own methods
  (`spiEdges()`'s new `allMethods`, matched against the union of real `to` values from all
  callers), with unused ones dimmed and marked `(unused)`. This surfaces genuinely unused SPI
  contract methods as a real finding, not a hypothetical: `AuditDomainHook` is 1/3 used
  (`castIfKnown` called; `findExisting`/`resolveNames` never called by anything) — confirmed
  live in the rendered table.
- **Explicitly not built**, considered and rejected in chat: a third, separate Interface | full
  Signature table (return type + parameter types per method). Reasoning: the dead-code signal above
  already covers this table's main value; a bare signature list (necessarily stripped of
  annotations/Javadoc to stay readable) gives less information than the one-click file link already
  on the Interface cell, and risks the two silently drifting apart over time. Kept as a rejected
  idea here, not a deferred one, in case it resurfaces.
- Method-level "calls" list was deliberately **not** added to the Implemented By table's
  Implementation cell (asymmetric from the Calls table) — a straightforward interface
  implementation always overrides 100% of its methods, so a per-method list there would almost
  always just repeat the full method count with no discriminating signal, unlike the Calls table
  where different callers genuinely use different subsets.

## Closed with two open questions left as documented remainders (2026-08-27)

Not blocking further — revisit only if a real need surfaces:
- **Generic-interface type-argument display** (`AuditActivityEnrichHook<T extends AuditableSnapshot>`
  — a bare `entityType()` method name in the table says nothing about which concrete `T` a given
  caller/implementation pairing is really about). No decision made, no design attempted.
- **Module → Class → Method regrouping** (the original "Shape" section's plan) instead of the
  shipped by-Interface grouping. Not revisited — the by-Interface shape was explicitly kept as-is
  through two real increments, per direct user preference ("мені зараз структура все подобається").

## Related

- `improvement-152` — the issue this was split out of.
- `improvement-156` — the ArchUnit Track B unblock decision gate this issue depended on for real
  method-level data (shipped 2026-08-27).
- `improvement-151` — where the original SPI Map findings and this design's own mockup work came
  from.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a

### Script/command runs
- bash scripts/build-and-test.sh --no-unit --no-integration --archunit-metrics --skip-vaadin (x2, iterative) | duration_s=~60 each | mode=background | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh --with-archunit (x2, iterative) | duration_s=~90 each | mode=background | result=pass
- headless Chromium verification via `docker exec ci-pw-runner node ...` (x2, ad-hoc Playwright scripts) | mode=foreground | result=pass
