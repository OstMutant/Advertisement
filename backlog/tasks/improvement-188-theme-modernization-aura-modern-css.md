# improvement-188: Theme modernization — Aura pilot + modern CSS capabilities (light-dark, oklch/color-mix, @layer)

**Type:** improvement — theming/CSS modernization, research-and-implementation bundle (4 related
approaches from an external "THEME MODERNIZATION" directive, evaluated against this codebase
2026-09-15)
**Module:** marketplace-app (`src/main/frontend/themes/my-app/**`, `AppShell.java` for theme
selection)
**Priority:** 🟡 high (user-requested top-of-backlog placement, 2026-09-15)
**When:** independent, no blockers

## Current state

`theme.json` sets `"lumo": true` explicitly (deliberate choice, Vaadin 25 requires picking Aura,
Lumo, or neither — no silent default). `styles.css` defines 57 `--app-*` custom properties
(hand-picked hex values), imported by 30 files via `@import` — one global bundle, no per-view CSS
scoping (Vaadin loads the whole theme regardless of which page renders). 69 direct `--lumo-*`
references exist across 13 of those files (`dialogs.css`, `query-block.css`,
`query-status-bar.css` among them — shared chrome, not confined to any one domain page). 43
`!important` overrides exist across 12 files. Dark mode is half-shipped: tokens are named
(`improvement-037`), but no dark palette/toggle exists yet (`improvement-039`, still open).
Confirmed via Vaadin's own docs: both Lumo and Aura already support light/dark color scheme
natively (`@ColorScheme` / `Page.setColorScheme()`) — this is not an Aura-specific capability.

## Why change

Vaadin's own platform team describes Lumo as "starting to show its age" (github.com/vaadin/platform#7457)
and positions Aura as computing its color/contrast tokens from a small base-token set rather than
hand-picking each one — architecturally the direct fix for the exact class of bug this codebase
already hit once by hand (`--app-text-muted`'s WCAG AA contrast fix, `improvement-037`). Separately,
three native CSS capabilities (`light-dark()`, `oklch()`/`color-mix()`, `@layer`) are now safely
usable (long shipped across all major engines) and would improve the existing `--app-*` token
system regardless of the Aura decision.

## Expected benefit

- Task B: finishes `improvement-039` (real, user-visible dark mode) using a cleaner mechanism than
  a duplicated `[data-theme="dark"]` override block.
- Task C: accent-color ladder (9 values) derived from 1 base value instead of 9 hand-picked hex
  codes — fewer future contrast-drift bugs, single edit point.
- Task D: removes some/all of the 43 existing `!important` overrides by giving the cascade an
  explicit, predictable layer order — real, measured technical debt, not hypothetical.
- Task A: not a shipped improvement by itself — produces a go/no-go recommendation, backed by real
  rendered evidence, on whether a full Aura migration is worth a later, separately-scoped effort.

## Approach

**Task 0 — bump Vaadin patch version (prerequisite to Task A, 2026-09-15):** root `pom.xml:40`'s
`<vaadin.version>` is `25.2.3`; latest stable release in the same minor line is `25.2.8`
(patch-only, no new features, github.com/vaadin/platform releases). Aura already exists as of
25.0, so this bump isn't required for Aura itself to be available — it's picked up first simply to
run the pilot against the current patch release rather than an older one. `25.3.0` (still beta as
of 2026-09-15) adds a global theme editor for Aura plus a `vaadin-dev` CLI with live CSS/theme
push — not adopted yet, tracked here for whenever it stabilizes.
1. `pom.xml:40` — `<vaadin.version>25.2.3</vaadin.version>` → `<vaadin.version>25.2.8</vaadin.version>`.
2. `bash scripts/build-and-test.sh --unit --integration --sandbox` — full reactor build + tests.
3. `bash scripts/deploy-and-run.sh` (full rebuild — Vaadin version bump changes frontend
   bundling) + `bash scripts/playwright.sh e2e --full --ux` — full visual regression pass.

**Real finding during step 2 (2026-09-15):** bumping Vaadin to 25.2.8 pulls in `flow-server
25.2.9` transitively, which depends on `jsoup 1.23.1` — conflicts with this project's own explicit
`jsoup.version` pin (`1.22.2`, used by `html-sanitizer-lib`). Caught immediately by the existing
Maven Enforcer `DependencyConvergence` rule (`improvement-031`), not discovered by accident.
Widened the scope from "just bump Vaadin" to a full dependency-version audit, done once rather than
piecemeal:

| Dependency | Was | Now | Note |
|---|---|---|---|
| Spring Boot parent | 4.1.0 | 4.1.1 | patch |
| Vaadin | 25.2.3 | 25.2.8 | patch, this task's original scope |
| jsoup | 1.22.2 | 1.23.2 | forced by the convergence conflict above; picked latest (1.23.2) over the minimum-required (1.23.1) |
| AWS SDK v2 (`aws-s3-sdk.version`) | 2.48.4 | 2.54.18 | minor |
| liquibase-core | 5.0.3 | 5.0.4 | patch |
| maven-enforcer-plugin | 3.5.0 | 3.6.3 | patch |
| jacoco | 0.8.14 | 0.8.16 | patch |
| commons-io | 2.22.0 | 2.22.0 | already latest, no change |
| commons-text | 1.15.0 | 1.15.0 | already latest, no change |
| jetbrains-annotations | 26.1.0 | 26.1.0 | already latest, no change |
| mapstruct | 1.6.3 | 1.6.3 | deliberately NOT bumped — 1.7.0 exists only as a Beta, not adopting a beta dependency |
| Java | 25 | 25 | deliberately NOT bumped — 26 is a non-LTS interim release (next LTS is Java 29, Sept 2027); this project follows LTS-only |
| springdoc-openapi | 2.8.5 | *(unchanged here)* | **major version (2.x→3.x) — deliberately deferred to its own separate step below, not bundled with the safe patch/minor bumps** |

4. Re-run `bash scripts/build-and-test.sh --unit --integration --sandbox` after all the above
   version bumps together, then `bash scripts/deploy-and-run.sh` + full Playwright `e2e --full
   --ux` regression pass — same verification as steps 2-3, against the final combined version set.

**Two more real convergence conflicts found and fixed during step 4 (not predicted by the table
above), same root cause class as the jsoup one — Maven Enforcer `DependencyConvergence` catching a
real transitive mismatch each time, not false positives:**
- `commons-collections4`: bumping `liquibase-core` to `5.0.4` surfaced a mismatch between its own
  direct dependency on `commons-collections4:4.6.0` and its `opencsv:5.12.0` dependency's own
  `commons-collections4:4.5.0` — fixed by adding an explicit `commons-collections4:4.6.0` entry to
  root `pom.xml`'s `dependencyManagement`.
- `jsoup`, round 2: setting `jsoup.version` to `1.23.2` was not sufficient by itself — that
  property only drives `html-sanitizer-lib`'s own direct dependency declaration, it was never
  registered in root `pom.xml`'s `dependencyManagement`, so Maven's convergence check still saw
  `marketplace-app`'s two resolved paths (via `html-sanitizer-lib` at `1.23.2`, via
  `vaadin-spring`→`flow-server` at `1.23.1`) as genuinely different declared versions. Fixed by
  adding an explicit `org.jsoup:jsoup:${jsoup.version}` entry to `dependencyManagement` too — a
  property alone, only ever referenced from one module's own `<dependency>` block, does not
  propagate project-wide the way an actual `dependencyManagement` entry does.
- **Real verified result:** `bash scripts/build-and-test.sh --unit --integration --sandbox` — full
  reactor `BUILD SUCCESS`, 766 tests across 6 modules (`query-lib` 43, `html-sanitizer-lib` 6,
  `marketplace-app` 71, `marketplace-orchestrator` 116, `marketplace-rest-api` 100,
  `integration-tests` 430), 0 failures, 0 errors, confirmed via fresh Surefire report timestamps.
- Also caught before it reached a build at all: the web-search-sourced "jacoco 0.8.16" version
  from the earlier table doesn't exist on Maven Central — verified directly against
  `repo.maven.apache.org/.../jacoco-maven-plugin/maven-metadata.xml`, real latest is `0.8.15`.
  Corrected before the first build attempt for that dependency; every other version in the table
  above was cross-checked the same way (`maven-metadata.xml`, not just search-summary text) after
  this one turned out wrong.
- `bash scripts/deploy-and-run.sh --reset-only-db` + `bash scripts/playwright.sh e2e --full --ux` —
  full visual/behavioral regression pass, **63/63 tests passed**, 0 failures. First attempt showed
  8 early failures, root-caused to a deploy/test sequencing race (the app container was still
  restarting when Playwright's first test hit it) — not a real regression from any version bump;
  confirmed by checking the container's actual `StartedAt` timestamp against the Playwright run's
  own start time, then re-verified clean by explicitly polling the health endpoint (`curl
  localhost:8081/` → `200`) before re-triggering Playwright, which then passed fully.

**Task 0 complete and verified — all 6 bumps + 2 forced dependencyManagement pins are correct and
safe: full backend test suite (766/766) and full Playwright e2e suite (63/63) both green.**

**Follow-up, not yet done — `springdoc-openapi` 2.x → 3.x:** major version, real breaking-change
risk (`marketplace-rest-api` actually uses it for OpenAPI schema generation) — needs its own
verification (generated schema diff, not just a green compile) before merging, tracked as a
separate step rather than bundled into the patch-bump pass above.

**Task A — Aura pilot (exploratory, time-boxed, does not block B/C/D):**
Vaadin supports switching themes at runtime via a `?theme=aura` query parameter — no parallel
branch/deployment needed. Pilot scope: Providers catalog page. Confirmed limitation not in the
original prompt: because Vaadin bundles the whole theme as one stylesheet (no per-view scoping),
the pilot will also render shared chrome (`dialogs.css`, `query-block.css`,
`query-status-bar.css`) that references `--lumo-*` tokens with no Aura equivalent — the plan must
account for partial-fidelity rendering on those shared pieces, not just the page's own card CSS.
Ends in a written recommendation only — no merge.

**Task B — `light-dark()` for `improvement-039`:** define light+dark values together per token at
declaration time instead of a separate override block; add the toggle + `prefers-color-scheme`
default `improvement-039` already specifies; verify dark-direction contrast explicitly, the same
way `--app-text-muted` was checked.

**Task C — `oklch()`/`color-mix()` for one accent-color group:** scope to
`--app-accent-primary` and its 9 variants only; derive via `color-mix()` against
white/black/surface tokens; visually compare against current values side by side before deciding
whether to repeat for the other two accent groups.

**Task D — `@layer` cascade layers:** define an explicit layer order once
(`@layer tokens, base, components, overrides;`), assign each of the 30 imported files to its
matching layer, move file by file, verifying rendering is unchanged after each move (cascade
layers change specificity resolution — a bulk move can silently change which rule wins).

Chosen order (per explicit user preference, 2026-09-15): start with Task A.

## Related

- [improvement-039](improvement-039-dark-mode-lumo-tokens.md) — Task B directly implements this
  issue's still-open remaining scope; when B ships, close `improvement-039` as part of that work
  rather than separately.
- [improvement-116](improvement-116-vaadin-theme-annotation-migration.md) — touches the same
  `@Theme` mechanism Task A's runtime theme-switch relies on; sequence 116 after Task A settles,
  not before, to avoid changing two theme-selection mechanisms at once.
