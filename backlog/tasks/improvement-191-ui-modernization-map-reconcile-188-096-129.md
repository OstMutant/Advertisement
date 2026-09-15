# improvement-191: UI/UX Modernization Map — reconciled with improvement-188/096/129

**Type:** improvement
**Module:** marketplace-app/src/main/frontend/themes/my-app/**, marketplace-app UI views/components
**Priority:** medium-high
**When:** independent, no blockers — informed by improvement-188's already-answered theme question

## Current state
A second external directive ("UI/UX MODERNIZATION — ADVERTISEMENT — v2") was submitted, proposing
a full visual/UX modernization pass across `marketplace-app` (AdvertisementCard, Query Block,
Header/Tabs, surface system, typography, color discipline, vertical rhythm, responsive composition,
dialogs, Timeline, User UI, attachments, Provider Profile), with its own Wave 1/2/3 ordering and a
mandatory "UI Modernization Map" produced and human-approved before any implementation. Verified
against real code: its §1 architecture-boundary claim is accurate —
`ArchitectureRulesTest.marketplace_app_must_not_depend_on_platform_commons_spi_directly` exists
exactly as described — and its cited "a geometry-only Playwright pass hid a visually wrong result"
lesson is real (`improvement-126`). However its §0/§16 theme-baseline investigation duplicates
`improvement-188` Task A (Aura pilot already run, go/no-go delivered "lean no" — `theme.json`
already explicitly sets `"lumo": true`, a deliberate choice), and its Wave 1 AdvertisementCard
scope plus §13 mobile/responsive requirements substantially overlap `improvement-129` (card/feed
modernization, open design questions) and `improvement-096` (responsive/mobile adaptation pass,
own 4-phase program).

## Why change
Executing the external document as a fresh, standalone initiative would re-investigate an
already-answered question and produce a competing plan for work two existing backlog items already
own. The genuinely new, non-duplicated contribution is the document's process discipline (a Map
produced and approved before touching code) plus Wave 2/3 scope that isn't currently owned by any
existing task.

## Expected benefit
One reconciled entry point for UI modernization that doesn't re-litigate `improvement-188`'s
answered theme question or duplicate `improvement-129`/`improvement-096`'s already-scoped work —
the Map becomes the real coordination point between all four issues instead of four independent
plans converging on the same files.

## Approach
1. Do not re-run the theme investigation — cite `improvement-188` Task A's "lean no" finding
   directly as the answered baseline for every downstream visual decision.
2. Produce the UI Modernization Map (Component/View, current behavior/problem, proposed change,
   UX rationale, responsive behavior, architecture impact, files affected, risk, Wave), scoped to
   what isn't already owned elsewhere:
   - AdvertisementCard findings fold into `improvement-129` instead of being replanned here.
   - Mobile/responsive verification piggybacks on `improvement-096`'s existing 4-phase program
     instead of a second one.
   - This task's own scope: Query Block, Header/Tabs (Wave 1); surface system, typography, color
     discipline, vertical rhythm (Wave 2); dialogs, Timeline, User UI, attachment/gallery, Provider
     Profile visual refinement (Wave 3).
3. Stop after the Map — same human-approval gate the source document specifies, matching this
   project's own Approval Rule/Task Lifecycle.
4. Visual QA per the source document's §24: real screenshots reviewed by hand, not just a green
   Playwright assertion — same lesson already learned in `improvement-126`.

## Related
[improvement-188](improvement-188-theme-modernization-aura-modern-css.md) — theme baseline already
answered, cite rather than re-investigate. [improvement-129](improvement-129-marketplace-feed-modernization.md)
— owns AdvertisementCard/feed scope. [improvement-096](improvement-096-responsive-mobile-adaptation-pass.md)
— owns mobile/responsive verification. [improvement-126](../completed/tasks/improvement-126-timeline-activity-diff-findings.md)
— source of the geometry-only-Playwright visual QA lesson. `ArchitectureRulesTest` — the real
ArchUnit rule this task's Wave 2/3 changes must keep passing.
