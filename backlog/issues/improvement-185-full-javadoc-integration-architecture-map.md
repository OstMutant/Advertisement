# improvement-185: Full Javadoc integration into architecture-map

**Type:** improvement (exploratory, no design decided yet)
**Module:** docs/architecture/scripts (`generate-architecture-model.sh`, `architecture-map.html`)
**Priority:** low
**When:** independent, no blockers

## Current state

This session added a lightweight per-class "purpose" tooltip: `javadoc_purpose_for()` (a plain
`awk` extraction, no Maven/JDK invocation) reads each class's own class-level Javadoc first
paragraph and embeds it as a `purpose` field on SPI Map caller/interface/implementation entries and
on module-page Entities/Key Services/Contracts list entries; the frontend renders it as a native
`title` hover-tooltip via `fileLink()`'s new optional third argument. This gives "what is this class
for" at a glance, but nothing method-level — no `@param`/`@return`/`@throws` rendering, no
cross-referenced `{@link}` resolution. Clicking a class link still opens the raw `.java` source file
directly (`fileLink()`/`sourceLink()`), not any rendered documentation page — there is no per-class
detail screen in architecture-map today (confirmed while scoping this session's tooltip work; a
dedicated "class page" was considered and deferred in favor of the lighter tooltip). No `mvn
javadoc:javadoc`/`javadoc:aggregate` is wired into this repo's build anywhere (verified —
`scripts/build-and-test.sh` doesn't invoke it).

## Why change

The one-line purpose tooltip says nothing about individual methods. Standard Javadoc tooling already
solves per-method documentation well (cross-referenced HTML, `@param`/`@return`/`@throws`
rendering), and this project's own comment convention (`module-doc-standards`) already writes real
Javadoc syntax — the raw material to generate full docs from already exists in every module's source.

## Expected benefit

A reader browsing architecture-map (SPI Map, module pages) could reach real per-method documentation
(params/return/throws, cross-referenced types) without leaving the tool or opening raw `.java`
source — useful for onboarding and for reviewing an SPI interface's full contract, not just its
one-line purpose.

## Approach

No option chosen yet — three real candidates, different integration cost vs. visual consistency
trade-off:

1. **Separate generated site**, `mvn javadoc:aggregate` (reactor-wide) or per-module, linked from
   architecture-map as an external "View full Javadoc ↗" link next to relevant class/module entries.
   Lowest implementation cost (standard Maven plugin, no custom rendering), but visually disconnected
   from architecture-map's own styling/navigation, and needs a new pipeline step (when does it
   regenerate — part of `generate-architecture-model.sh`, or a separate script/CI stage).
2. **Same generated site, embedded via iframe** on a dedicated architecture-map screen (or modal).
   Closer visual integration than a bare external link, but adds iframe sizing/styling friction and
   a same-origin/build-path dependency architecture-map doesn't have today.
3. **Extend today's own lightweight extraction** (`javadoc_purpose_for()`-style, no Maven/JDK
   invocation) to also pull full method-level Javadoc text, rendered directly inside a new
   architecture-map "class detail" screen. Most consistent look-and-feel with the rest of the tool,
   but re-implements a meaningful subset of what `javadoc:aggregate` already does for free, and needs
   to handle the same edge cases real Javadoc tooling already solves (generics, inherited Javadoc,
   `{@link}` resolution).

Needs a design decision (which option, or a combination) before any implementation.

## Related

- This session's per-class purpose tooltip (SPI Map hover, module-page Entities/Key
  Services/Contracts lists) — implemented directly, not filed as its own issue, the direct
  predecessor this issue extends.
- [improvement-183](improvement-183-rest-api-and-taxon-ui-follow-ups.md) item 11 — the
  `module-doc-standards`/`module-readme-standards` audit governing the Javadoc comments this feature
  would render.
