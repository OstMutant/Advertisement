# improvement-157: SPI Interface Details table redesign — split Callers/Implemented By, group by Module → Class → Method

**Type:** improvement — design only, no code written.
**Module:** `docs/architecture/scripts/generate-architecture-model.sh` (`spi_map_json()`,
architecture-map's SPI Interface Details cards).
**Priority:** Top — explicit placement, ranked directly after `improvement-156`.
**When:** independent to file, but loosely depends on `improvement-156` (a real ArchUnit-based
`spi_map_json()` replacement) for the method-level caller/implementor data this redesign actually
needs — likely sequenced together in practice.

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

## Not yet done

- No decision made on the `AuditAutoConfiguration`/DI-wiring-only-caller display shape.
- No decision made on the generic-interface type-argument display shape.
- No real extraction work started — depends on `improvement-156` producing method-level
  caller/implementor data first (or a narrower, standalone grep-based extraction if that issue
  stays blocked long-term — not decided which path to take).

## Related

- `improvement-152` — the issue this was split out of.
- `improvement-156` — the ArchUnit Track B unblock decision gate this issue depends on for real
  method-level data.
- `improvement-151` — where the original SPI Map findings and this design's own mockup work came
  from.
