# improvement-189: Restructure root README.md for portfolio-first presentation

**Type:** improvement — documentation presentation, no content/fact changes
**Module:** `README.md` (root)
**Priority:** 🟡 high (user-requested top-of-backlog placement, 2026-09-15)
**When:** independent, no blockers

## Current state

Verified directly against the real file (2026-09-15):
- Opening paragraph is self-description, not user-facing: "a hands-on playground for exploring
  backend and architectural trade-offs" (line 3) — describes the author's intent, not what the
  product is or does.
- The "not a finished product" disclaimer sits in the "About" section near the very top (line 20),
  one of the first things a skimming reader hits.
- `Quickstart: bash scripts/deploy-and-run.sh` is near the very end of the document (line 149 of
  ~180), right before "Roadmap"/"Author's Note".
- Module layout (line 60) is a plain ASCII tree, and it's stale — missing three modules added
  since this file was last touched (`apikey-spring-boot-starter`, `marketplace-rest-api`,
  `html-sanitizer-lib`).
- No screenshots anywhere in the document; `docs/screenshots/` doesn't exist.
- README.md has zero references to `backlog/` anywhere — the technical-debt tracking process
  (`backlog/BACKLOG.md`) isn't mentioned at the top level at all.
- Deep-dive content (Architectural Principles, Key Technical Decisions, Testing Strategy) is
  solid and accurate — this task changes ordering/structure only, not technical claims.

## Why change

The document is currently ordered for a reader who has already decided to dig deep, not for
someone giving the project its first skim. Portfolio/first-impression readability is the goal.

## Expected benefit

A reader gets the concrete value proposition, a working Quick Start, and pointers to the
project's strongest artifacts (backlog process, query-lib, authorization model) before hitting any
deep-dive content — without losing any of the existing technical depth, just reordered.

## Approach

Reorder into this structure, top to bottom (screenshots explicitly out of scope for this pass —
no image capture, no `<!-- TODO: screenshot --> ` placeholders either, just skip that concern
entirely):

1. Title + a single-line tech-stack badge/list under it: Java 25 · Spring Boot 4.1 · Vaadin 25 ·
   PostgreSQL · S3 · Playwright · Testcontainers.
2. Quick Start moved up here (was line 149) — the run command plus 2-3 lines on what the reader
   will actually see after running it (port, default demo login, Swagger link).
3. Rewritten opening paragraph — one concrete, user-perspective sentence (what it is, who it's
   for, what you can do with it) replacing the current self-description line. The "why/how it's
   built" framing moves down into the existing "About" section instead.
4. New "If you have 5 minutes" pointer section: `AuthorizationService`/`AccessEvaluator`
   (authorization model), `SqlCondition`/`OrderByBuilder` in query-lib (hand-written SQL, no ORM),
   `backlog/BACKLOG.md` + one representative `backlog/tasks/*.md` file (technical-debt tracking
   with dates and self-correction — note: real path is `backlog/tasks/`, not `backlog/issues/`),
   one representative Playwright spec (`06-seed-filter-sort-pagination.spec.js`, confirmed to
   exist).
5. New short section (2-4 sentences) surfacing the backlog/technical-debt tracking process,
   linking `backlog/BACKLOG.md` — currently absent at the top level entirely.
6. "Not a finished product" disclaimer moves down into the existing "About" section, as context
   for readers already interested, not a top-third signal.
7. Module Layout: replace the ASCII tree with a Mermaid `graph TD`/`flowchart LR` diagram showing
   real dependency direction (`platform-commons` → starters → `marketplace-orchestrator` →
   `marketplace-app`) — and fix the stale module list while at it (add the 3 missing modules
   found above).
8. Everything else (Architectural Principles, Key Technical Decisions, Feature Highlights, Testing
   Strategy, Roadmap, Author's Note) stays as-is, just moved below the new "hook" content from
   steps 1-7.

No new facts invented, no technical claims changed — structure/ordering/diagram only, per the
"one fact, one canonical home" rule (nothing here duplicates a fact that already lives in `pom.xml`
or elsewhere; it just surfaces existing facts in a better order).

## Real facts verified before writing (2026-09-15)

Against the actual running app (redeployed with a clean DB earlier this session), not assumed:
- Port 8081, `/swagger-ui/index.html` and `/v3/api-docs` both confirmed live (`200`) —
  `marketplace-rest-api` is a compile dependency of `marketplace-app`, same jar, same port.
- No seed/demo data or default admin credentials exist anywhere in the codebase (confirmed via
  grep across Liquibase changelogs and `application*.yml`) — instead,
  `UserService.register()` has a real `isFirstUser` check that promotes the first-ever registered
  account to `Role.ADMIN`. The Quick Start section describes this real mechanism, not an invented
  "demo login".
- Module tree was stale: missing `apikey-spring-boot-starter`, `marketplace-rest-api`,
  `html-sanitizer-lib` (all three confirmed to have both `README.md` and `DECISIONS.md`, added to
  the per-module table too).
- Roadmap's "Explore alternative API adapters (REST)" was stale — the REST API already exists and
  ships; reworded to reflect what's actually still planned for it (hypermedia/`PATCH`, tracked
  separately as `improvement-186`).

## Round 2 — user review feedback applied (2026-09-15)

Real, valid critique on the first draft, each verified/applied:
1. **Dead local-machine links claimed present** (`file:///D:/Ost/dev/...`,
   `http://localhost:63342/...`) — checked directly, `grep` across the actual `README.md` (both
   the new version and `git show HEAD:README.md`, the original) and every other `.md` file in the
   repo: **no such links exist anywhere**. Almost certainly the user's local IntelliJ IDEA
   Markdown-preview server (port 63342 is JetBrains' own built-in preview) resolving this file's
   ordinary relative links (`query-lib/README.md` etc.) into absolute local URLs in its own
   preview pane — a viewer-side artifact, not a defect in the committed file. Reported back
   instead of "fixing" something that wasn't actually broken in the file.
2. **"Author's Note" read as a redundant manifesto** — the whole document already demonstrates
   these values through the Architecture/Testing/Decisions sections; restating them as a closing
   personal-values list repeated the same point a fourth time. Cut from 3 lines to 1.
3. **Reader flow: product before engineering** — `Feature Highlights` (renamed
   `What can you do in it?`) moved from after `Key Technical Decisions`/`Module Layout` to
   directly after `What is it?`, ahead of the "5 minutes" technical tour. Final order: Quick Start
   → What is it? → What can you do in it? → If you have 5 minutes → Technical debt tracking →
   About → Architectural Principles → Module Layout → Key Technical Decisions → Testing Strategy →
   Roadmap → Author's Note.
4. **Overly categorical phrasing** — "type-safe by construction" (If you have 5 minutes' query-lib
   bullet) and "no shared mutable state between layers" (Immutable data flow principle) both
   softened to claims the actual code supports without inviting a "find the counterexample" read —
   removed "by construction", reworded the mutable-state claim to describe the actual mechanism
   (value objects, no shared references) rather than an absolute guarantee.

## Related

- Source: user-supplied external restructuring directive, adapted (dropped the screenshot-capture
  requirement per explicit user instruction; corrected `backlog/issues/` → `backlog/tasks/`, the
  real path since `improvement-130`).
