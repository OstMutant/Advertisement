# improvement-174: Bounded Contexts' `ports_json` — replace regex extraction with real ArchUnit data

**Type:** improvement — found while reviewing `System › Diagrams › Bounded Contexts` and
`System › Diagrams › Module Dependencies` after closing `improvement-156`/`157`.
**Module:** `docs/architecture/scripts/generate-architecture-model.sh` (`bounded_contexts_json()`).
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

## Not yet done

- Confirm whether any of these three spots produce a real, currently-visible false positive/negative
  on the live Bounded Contexts screen (analogous to the `AuditAutoConfiguration`/
  `AuditActivityEnrichHook` cases `improvement-156` found) — not yet checked against real data.
- Replace all three `ports_json` computations with data read from `spiEdges` (same
  `ARCHUNIT_METRICS_FILE`/`_FALLBACK` + python3-extraction pattern `spi_map_json()` now uses),
  falling back to the current regex when ArchUnit data isn't available — same shape as `improvement-156`'s
  fix, applied to a second consumer of the same underlying fact (which module owns/calls which SPI
  interface).
- Bounded Contexts' `ports_json` only needs interface-level (which domain owns which port), not
  method-level, granularity — confirm whether `spiEdges`'s per-caller `calls[]`/`implementations[]`
  lists are sufficient as-is or need a lighter per-domain rollup.

## Related

- `improvement-156` — the ArchUnit-based `spiEdges` data source this issue reuses, and the
  precedent for the false-positive/negative class of bug this issue investigates in a second
  consumer.
- `improvement-157` — the SPI Interface Details table redesign that consumed the same `spiEdges`
  data on the SPI Map side.
