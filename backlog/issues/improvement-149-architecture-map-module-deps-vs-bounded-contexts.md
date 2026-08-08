# improvement-149: clarify (or fix) System › Diagrams › Module Dependencies vs Bounded Contexts

**Type:** investigation — may resolve as "working as designed, needs a clearer explanation on the
  page itself" or as a real generator fix, not decided yet
**Module:** `scripts/architecture/generate-architecture-model.sh`, `docs/architecture/architecture-map.html`
**Priority:** 🔴 top — explicit user request to rank at the top of the backlog
**When:** independent, no blockers

## Problem

While reviewing `improvement-147`'s true-BFF migration (marketplace-app repointed from direct
domain `*Port` access to going through `marketplace-orchestrator`), the user checked
`System › Diagrams › Module Dependencies — Dependency Graph` expecting to see the improvement
reflected there, and it looked completely unchanged. The explanation given in that conversation
(Module Dependencies reads real `pom.xml` `<dependency>` blocks — a Maven build-graph fact — while
Bounded Contexts reads real Java source for `ComponentFactory<XPort>`/`implements XPort` evidence
— a code-level API-usage fact — and this migration only changed the second, not the first) was
**not accepted as clear** ("ти якусь хуйню мені чешеш" — still doesn't understand it). This issue
exists to actually resolve that gap in understanding, or fix the diagram/its explanatory text if
the confusion turns out to be a real UX problem with the page rather than just an unclear verbal
explanation.

## Point 1 — explain why Module Dependencies looks the way it does, in a way that actually lands

Re-derive and re-explain from scratch, concretely, probably with a live walkthrough of the actual
generator code and the actual rendered diagram (not just prose) — the previous chat explanation
didn't work. Things to ground the explanation in:
- Read `scripts/architecture/generate-architecture-model.sh`'s real Module Dependencies section
  (the `<dependency>` block state machine around line 336, `moduleNodes`/`moduleDeps` construction)
  end to end, not from memory.
- Show the actual rendered graph (screenshot or direct HTML/JSON inspection) before and after
  `improvement-147`'s migration, side by side, so the "nothing changed" claim is either visibly
  confirmed or shown to be wrong.
- If the diagram is correct and the confusion is purely about *why* it's correct, figure out what
  concrete artifact (an annotated example, a different diagram, added text on the page) would
  actually make it click — a repeat of the same verbal explanation that already failed is not an
  acceptable outcome for round two.

## Point 2 — Bounded Contexts vs Module Dependencies: what's actually different

Both are Cytoscape-rendered graphs on the same `architecture-map.html` page. Nail down, concretely
and with real generator-code line references for each:
- What node set does each one draw (modules vs. domains — are these ever not 1:1?).
- What edge set does each one draw, and from what evidence (pom.xml dependency declarations vs.
  Java-source `ComponentFactory`/`implements`/import evidence).
- A concrete example change that would move each diagram and NOT the other (a real, testable
  litmus test — not just an abstract description), e.g.: adding a new `<dependency>` block to a
  pom.xml with no code using it yet vs. writing a new Java class that imports a `*Port` without any
  new Maven dependency.
- Whether having two diagrams that can diverge (as they just did) is actually useful information
  or is more confusing than helpful — this needs an honest answer, not an assumption that more
  diagrams are automatically better.

## Related

- `backlog/completed/issues/improvement-147-marketplace-orchestrator-followups.md` — the migration
  whose effect on these two diagrams triggered this investigation.
- `scripts/architecture/DECISIONS.md` ADR-015 — already recorded a decision that Bounded Contexts
  stays a separate, hand-distinct diagram from Module Dependencies/SPI Map; read this first before
  re-deriving Point 2 from scratch, since the original reasoning may already answer it (or may
  itself need updating if it doesn't hold up under this issue's own litmus-test approach).
- `scripts/architecture/DECISIONS.md` ADR-003/ADR-016/ADR-018/ADR-019/ADR-025 — related generator
  design history for both diagrams' evolution.
