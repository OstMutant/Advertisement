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

**Original premise corrected (2026-09-15):** a `?theme=aura` runtime query-parameter switch,
originally assumed available, does **not** exist in Vaadin Flow — re-verified directly against
multiple official sources during this task; "switching between packaged themes at runtime is not
supported in Vaadin Flow" (only light/dark *color scheme* switching is a real runtime API).
Real, confirmed-working mechanism instead: **build-time** theme selection via
`@StyleSheet(Aura.STYLESHEET)` on `AppShellConfigurator` (`AppShell.java`) — the modern Vaadin 25
replacement for the deprecated `@Theme`/`theme.json` mechanism, directly overlapping with
`improvement-116`'s own scope.

**Real setup that worked, verified end to end:**
1. Added `com.vaadin:vaadin-aura-theme` as an explicit dependency to `marketplace-app/pom.xml`
   (not a transitive dependency of `vaadin-core`/`vaadin-spring-boot-starter` — confirmed via
   `~/.m2` inspection; resolves via the existing `vaadin-bom` import, no explicit version needed).
2. Removed `"lumo": true` from `theme.json` (kept `"name": "my-app"`, so our own `styles.css` +
   all 30 imported component CSS files still load).
3. Added `@StyleSheet(Aura.STYLESHEET)` (`com.vaadin.flow.theme.aura.Aura`) to `AppShell.java`,
   alongside the existing `@Theme("my-app")` and the Lumo-specific
   `@JsModule("@vaadin/vaadin-lumo-styles/vaadin-iconset.js")` (left untouched for this pass).
4. Full reactor build, deploy, and app start all succeeded with zero errors.

**Real verification results (Providers catalog + Advertisements tab, anonymous/empty-state):**
- Zero browser console errors, zero failed network requests (checked via Playwright
  `page.on('console'/'response')`) navigating between tabs under the Aura build.
- Tab switching itself works correctly — confirmed via the `selected` DOM attribute flipping
  correctly on click, not just visually. (An earlier read of a "broken-looking", near-blank
  screenshot turned out to be a false alarm from an early draft of the diagnostic script with too
  short a wait — the *same* blank-page artifact reproduced identically on a plain **Lumo** rebuild
  taken specifically to rule this out, proving it was a cold-start timing issue unrelated to
  Aura.)
- **Visual result, with a real Lumo baseline screenshot taken specifically for comparison:** the
  basic empty-state Advertisements/Providers views look strikingly similar between Lumo and the
  Aura pilot build — same blue accent color, same card/spacing layout, same overall look. This
  is because this app's own 57 `--app-*` custom tokens (not `--lumo-*`) already drive nearly all
  visible chrome in these basic views — the base-theme swap alone doesn't produce a strongly
  differentiated look without also adopting Aura's own token values (Task C's `oklch()`/
  `color-mix()` work would be the actual visual differentiator, not Task A alone).
- **Not tested, real risk still open:** neither a dialog nor the query-block filter panel was
  opened during this pass — both are exactly the shared-chrome files (`dialogs.css`,
  `query-block.css`, `query-status-bar.css`) confirmed earlier to hold `--lumo-*` references with
  no Aura equivalent. Whether those specific surfaces render broken under Aura remains genuinely
  unverified, not ruled out.

**Recommendation (evidence-based, not a predetermined conclusion):** **no strong case for a full
Aura migration based on this pilot alone.** The pilot's own basic-view comparison showed minimal
visual differentiation — this app's existing custom token system already overrides most of what
a base-theme swap changes — while the one class of real risk this pilot could have caught
(dialogs/query-block's `--lumo-*` references) was left unexercised. A "go" decision would need
either (a) re-running this same setup against a dialog/query-block-opening flow to close that gap,
or (b) treating Task A as answered "no, not worth it standalone" and letting B/C/D (which touch
the same `--app-*` token system directly, with a much clearer, already-demonstrated payoff) carry
this issue's real value instead.

Experimental changes (dependency, `theme.json`, `AppShell.java`) fully reverted after this pilot —
confirmed via `git status --short` showing a clean tree. No merge, per this task's own scope limit.

**Is a future migration to Aura actually required? No.** Confirmed directly: Vaadin's own
position is that "the Lumo theme will still be available and fully supported" going forward — Aura
is an additional option, not a replacement. What genuinely is deprecated (`forRemoval = true`) is
the *selection mechanism* — the `@Theme` annotation and `theme.json` — not Lumo the theme itself.
That mechanism migration (`@Theme("my-app")`/`theme.json` → `@StyleSheet(Lumo.STYLESHEET)`,
staying on Lumo) is real future work, already tracked separately as `improvement-116` — unrelated
to whether this app ever adopts Aura.

**Task A status: done.** Real pilot executed, go/no-go delivered (lean "no" — see Recommendation
above), no forced migration exists, follow-up mechanism work correctly attributed to
`improvement-116` instead of this task.

**Task B moved back to `improvement-039` in full (2026-09-16)** — it always was that issue's own
remaining scope, tracked here only temporarily. A real implementation attempt was made and failed
(UI wiring confirmed broken via manual testing); the full design, the failed attempt's branch, and
what's actually broken are all recorded on `improvement-039` itself now, deprioritized to the
bottom of the backlog pending a decision on whether the feature is even wanted. Do not restart
Task B work here — see `improvement-039`.

**Task C — `oklch()`/`color-mix()` for one accent-color group:** scope to
`--app-accent-primary` and its 9 variants only; derive via `color-mix()` against
white/black/surface tokens; visually compare against current values side by side before deciding
whether to repeat for the other two accent groups.

**Task C — plan (drafted 2026-09-16, pending approval):**

Real facts checked directly (not assumed):
- `--app-accent-primary`'s group is actually **10 color values**, not 9 as the line above says:
  `-primary` (`#3b82f6`, the base), `-strong` (`#1d4ed8`), `-bold` (`#2563eb`), `-light` (`#60a5fa`),
  `-faint` (`#93c5fd`), `-bg` (`#dbeafe`), `-bg-hover` (`#eff6ff`), `-bg-soft` (`#f0f9ff`), `-tint`
  (`#f0f7ff`), `-glow` (`#e8f0fe`) — all in `styles.css` lines 68-78 — plus a separate
  `--app-accent-primary-rgb` channel-triplet helper (`59, 130, 246`).
- `--app-accent-primary-rgb` is consumed via the classic `rgba(var(--x-rgb), alpha)` pattern at
  **7 call sites across 6 files**: `advertisement-query-block.css` (2), `forms.css`,
  `highlight.css`, `timeline-query-block.css`, `user-picker-field.css`, `user-query-block.css`
  (1 each). `color-mix()` produces an opaque color, not a decomposable channel list, so this
  pattern can't survive unchanged once `-primary` itself becomes a `color-mix()` expression.
- **Decision (2026-09-16, explicit):** drop `--app-accent-primary-rgb` entirely and migrate all 7
  call sites to CSS Relative Color Syntax — `rgb(from var(--app-accent-primary) r g b / alpha)` —
  same CSS Color Module Level 5 generation as `color-mix()`/`oklch()` already approved as a safe
  baseline for this project. Chosen over keeping `-rgb` as a separately hand-picked value because
  a second, un-derived value for the same color is exactly the class of drift bug (`--app-text-muted`,
  `improvement-037`) this task exists to prevent — verify current real browser support for
  `rgb(from ...)` before implementing, the same "check the real source, don't assume" discipline
  Task 0 used for dependency versions.
- Reproducing today's exact hex values via `color-mix()` against white/black alone is **not
  possible for 2 of the 9 tokens** — checked the math directly (both plain sRGB and perceptual
  OKLab space, same result either way): `-strong` (`#1d4ed8`, error ~37) and `-bold` (`#2563eb`,
  error ~27) are hand-picked with a real hue shift, not just a lightness change, so no single
  black-mix percentage reproduces them; the other 7 tokens fit within error 0-12 (visually
  negligible). Checked where `-strong`/`-bold` are actually used before deciding how to resolve
  this: text/accent elements, not large fills — `.query-inline-label-sort`,
  `.query-status-bar-*-info`, `.taxon-row-name:hover`, `.user-role-admin` badge text, the admin
  card's `border-top-color`, and one stop of the user-avatar gradient (`user-grid.css`,
  `query-block.css`, `query-status-bar.css`, `taxon-view.css`, `user-overlay.css`).
- **Resolved (2026-09-16): one single mechanism for all 9 tokens, not color-mix() with 2
  exceptions.** `color-mix()` toward black/white can't express a hue shift because black/white are
  achromatic; CSS Relative Color Syntax's `calc()` support on individual channels can. Computed
  each of the 9 tokens' exact `(L, C, H)` deltas from the base in OKLCH space (Björn Ottosson's
  sRGB↔OKLab matrices, `python3` one-off computation, not eyeballed):

  | Token | ΔL | ΔC | ΔH |
  |---|---|---|---|
  | `-strong` | −0.1349 | +0.0292 | +4.56° |
  | `-bold` | −0.0769 | +0.0272 | +3.07° |
  | `-light` | +0.0907 | −0.0446 | −5.19° |
  | `-faint` | +0.1860 | −0.0924 | −8.00° |
  | `-bg` | +0.3088 | −0.1564 | −4.23° |
  | `-bg-hover` | +0.3474 | −0.1738 | −5.21° |
  | `-bg-soft` | +0.3540 | −0.1755 | −23.19° |
  | `-tint` | +0.3501 | −0.1750 | −8.26° |
  | `-glow` | +0.3301 | −0.1673 | +1.96° |

  Each token becomes `oklch(from var(--app-accent-primary) calc(l + ΔL) calc(c + ΔC) calc(h + ΔH))`
  — exact reproduction of today's value (these deltas are computed to fit exactly, by
  construction), zero visual change, and — the actual point — if `--app-accent-primary` itself is
  ever changed, every derived token shifts relative to the new base automatically, instead of
  needing 9 manual re-picks. Browser support for CSS Relative Color Syntax (the `from` keyword,
  works with `oklch()`/`rgb()`/any output function) checked directly via caniuse: **92.29% global,
  full support in Chrome/Edge 131+, Safari 18+, Firefox 133+ since late 2024/early 2025** — same
  safe-baseline tier already accepted for `color-mix()`/`oklch()`/`light-dark()` in this task.

Proposed concrete changes:
1. Keep `--app-accent-primary: #3b82f6;` as the one hand-picked seed value.
2. Convert all 9 derived tokens to `oklch(from var(--app-accent-primary) calc(l + ΔL) calc(c + ΔC)
   calc(h + ΔH))` using the exact deltas computed above.
3. Remove `--app-accent-primary-rgb`; rewrite all 7 `rgba(var(--app-accent-primary-rgb), alpha)`
   call sites to `rgb(from var(--app-accent-primary) r g b / alpha)` in the 6 files listed earlier
   — same Relative Color Syntax mechanism, not a separate technique.
4. Contrast re-check: any of the 10 tokens used for text (not just backgrounds/borders) gets a real
   WCAG AA check, same discipline as `--app-text-muted` (`improvement-037`) — expected to pass
   unchanged since the derived values exactly reproduce today's hex, but verify rather than assume.
5. Visual verification: side-by-side comparison (screenshot) of every surface that renders an
   accent-primary token today (the files named above) before/after, checked directly — expected to
   be pixel-identical given the exact-fit construction, confirm rather than assume.
6. Playwright: existing `e2e --full --ux` suite re-run to confirm no visual regression; no new
   spec needed since no behavioral change is intended, only a mechanism change.
7. `marketplace-app/DECISIONS.md`: new ADR via `/record-decision` for the OKLCH relative-color-syntax
   mechanism (covering both the 9-token ladder and the `-rgb` → `rgb(from ...)` migration as one
   unified decision, not two).
8. Only after Task C ships for the primary group: decide, based on the real result, whether to
   repeat the same treatment for the gallery/violet accent groups — not decided or scoped now.

**Task C status: done (2026-09-16).** All 9 tokens converted to `oklch(from ...)`, `-rgb` helper
removed and its 7 call sites migrated to `rgb(from ...)` — `styles.css` plus
`advertisement-query-block.css`/`forms.css`/`highlight.css`/`timeline-query-block.css`/
`user-picker-field.css`/`user-query-block.css`. First Playwright run caught one real, expected
consequence (not a bug in the derivation): `playwright/e2e/_flows/user-management.flow.js`'s
`ROLE_COLOR.admin` hardcoded the old `rgb(29, 78, 216)` literal — the browser now legitimately
serializes the same color as `oklch(0.488166 0.217197 264.381)` (matching the computed `-strong`
deltas to 6 significant figures, confirming the derivation is exact) since that's the function the
token is declared in; updated the test literal accordingly. That one fix also cleared 3 further
failures in later spec files (order-dependent suite, downstream of the first assertion failure, not
independent bugs). Full re-run: **63/63 Playwright tests passed.** Recorded as
`marketplace-app/DECISIONS.md` ADR-083. Gallery/violet accent groups intentionally not touched —
open follow-up, no decision made either way yet.

**Task D — `@layer` cascade layers:** define an explicit layer order once
(`@layer tokens, base, components, overrides;`), assign each of the 30 imported files to its
matching layer, move file by file, verifying rendering is unchanged after each move (cascade
layers change specificity resolution — a bulk move can silently change which rule wins).

**Task D — approach (2026-09-16): incremental checkpoints, not one 29-file batch.** Per explicit
user preference after the `improvement-039` autopilot incident — do one small piece, verify it,
continue only if that verification is clean, stop and report immediately otherwise. Real count
checked: `styles.css` itself imports **29 local files** (plus one Google Fonts `@import`, not part
of this migration) and holds its own `:root` token block + a handful of global rules directly
(`html`/`body`, `.svg-icon`, `.overlay-chips-label`, `.entity-meta*`, `vaadin-button`,
`:focus-visible`) — not inside any imported file. 43 `!important` overrides exist across 12 of the
29 files (`highlight.css` 12, `card-lightbox.css` 8, `advertisement-overlay.css`/
`attachment-gallery.css` 4 each, the rest 1-6).

**Checkpoint 1 (proposed first slice, not yet started):**
1. Add `@layer tokens, base, components, overrides;` to the very top of `styles.css` — declares
   the order only, moves nothing into any layer yet, so this line alone has zero visual effect
   (nothing references `@layer` yet).
2. Wrap `styles.css`'s own `:root` token block in `@layer tokens { ... }` and its own directly-written
   global rules in `@layer base { ... }` — the one file already worked on twice this task (Task
   B/C), so the most context exists here; also the natural root of the dependency graph, since
   every other file consumes these tokens.
3. Verify: `deploy-and-run` + `playwright e2e --full --ux` before touching any of the other 29
   files. Stop and report here if anything looks off, per the incremental-checkpoint approach —
   do not proceed to categorizing/moving the remaining files in the same pass.
4. Only after Checkpoint 1 passes clean: propose the next slice (a first batch of "obviously
   `base`" files, or "obviously `components`" files) as its own separate, small approval — the
   full 29-file categorization is deliberately not decided all at once up front, since getting a
   file's layer wrong is exactly the kind of thing that should be caught early on one file, not
   discovered after 29 are already moved.

**Checkpoint 1 status: done (2026-09-16).** `@layer tokens, base, components, overrides;` declared
at the top of `styles.css`; its own `:root` token block wrapped in `@layer tokens`, its own global
rules (`html, body`, `.svg-icon`, `.overlay-chips-label`, `.entity-meta*`, `vaadin-button`,
`.primary-button:focus-visible` etc.) wrapped in `@layer base`. Checked directly for real selector
conflicts with the other 29 files before wrapping (found 2 same-selector matches — `html, body` in
`main-view.css`, `.svg-icon.*` in `sort-icon.css` — both touch disjoint CSS properties, no actual
conflict). First verification run hit 10 Playwright failures starting at a rate-limit test timeout
cascading into `ECONNREFUSED` for every later test — root-caused as stale DB/app state, not the
`@layer` change (none of the failures were visual/CSS assertions). Full `--reset` (DB+MinIO volume
wipe) + redeploy + re-run: **63/63 Playwright tests passed.**

**Checkpoint 2 status: done (2026-09-16).** First real slice of the 29 imported files:
`query-block.css`, `query-status-bar.css`, `sort-icon.css` wrapped in `@layer components`
(all three had zero `!important`, chosen as the lowest-risk starting cluster). Checked for real
cross-file selector conflicts before moving: one found (`.query-datetime-date`/`-time`, also
targeted by `highlight.css` with `!important`) — confirmed safe, since an unlayered `!important`
rule always outranks any layered rule regardless of layer order, so `highlight.css`'s override
keeps winning unaffected by this move. Deploy + Playwright `e2e --full --ux`: **63/63 passed.**

**Checkpoint 3 status: done (2026-09-16).** Per explicit user preference to move faster once the
process proved clean, batched all remaining zero-`!important` files in one pass instead of
one-by-one: `dialogs.css`, `advertisements-view.css`, `advertisement-card.css`, `user-grid.css`,
`user-picker-field.css`, `user-layout.css`, `locale-selector.css`, `header-bar.css`,
`provider-profile-overlay.css`, `provider-profile-query-block.css`, `providers-view.css`,
`provider-profile-card.css`, `activity-feed.css`, `entity-activity.css`, `notification.css` (15
files) wrapped in `@layer components`. Checked cross-file class-selector overlap programmatically
across all 29 files first (first pass had a broken regex matching numeric CSS values like `0.15s`
as fake "classes" — corrected before trusting the result). One real overlap found
(`.advertisement-card`, defined in both `advertisements-view.css` and `advertisement-card.css`) —
safe because both files are in this same batch, moving into the same layer together, so their
relative priority is unchanged. Deploy + Playwright `e2e --full --ux`: **63/63 passed.**

**18 of 29 files now layered (3 + 15). 11 remain unlayered — all 11 already carry `!important`**
(`highlight.css` 12, `card-lightbox.css` 8, `user-overlay.css` 6, `advertisement-overlay.css`/
`attachment-gallery.css` 4 each, `taxon-view.css` 3, `advertisement-query-block.css`/`forms.css`/
`main-view.css`/`timeline-query-block.css`/`user-query-block.css` 1 each) — real, pre-existing
specificity battlegrounds where `@layer` reordering has actual regression risk, unlike the 18
already done. Reverting to small, individually-verified steps for these, not one big batch.
Committed as `f01ad27e`.

**Checkpoint 4 status: done (2026-09-17).** Lowest-risk slice of the remaining 11: the 5 files
with exactly one `!important` each — `advertisement-query-block.css`, `forms.css`, `main-view.css`,
`timeline-query-block.css`, `user-query-block.css` — wrapped in `@layer components`. Checked
cross-file conflicts first: one real overlap found (`.overlay__form-fields-card`, also defined in
`advertisement-overlay.css`, not yet layered) — confirmed safe, since the two files' rules for that
selector set entirely disjoint CSS properties (`forms.css`'s is `border-top` only; the other sets
background/border/padding/layout), no actual competition regardless of layer order. Deploy +
Playwright `e2e --full --ux`: **63/63 passed.** 23 of 29 files now layered; 6 remain
(`taxon-view.css` 3, `advertisement-overlay.css`/`attachment-gallery.css` 4 each, `user-overlay.css`
6, `card-lightbox.css` 8, `highlight.css` 12), continuing one-by-one from here.

**Checkpoint 5 status: done (2026-09-17).** `taxon-view.css` (3 `!important`, all self-contained on
`.taxon-row-deleted`, no cross-file target) wrapped in `@layer components`. No selector conflicts
found with any other file. Deploy + Playwright `e2e --full --ux`: **63/63 passed.** 24 of 29 files
now layered; 5 remain (`advertisement-overlay.css`/`attachment-gallery.css` 4 each, `user-overlay.css`
6, `card-lightbox.css` 8, `highlight.css` 12).

**Checkpoint 6 status: done (2026-09-17).** `advertisement-overlay.css` (4 `!important`, all on a
`.overlay__view-card`/modifier-class accent-border pattern, self-contained within the file) wrapped
in `@layer components`. Two cross-file selector overlaps found (`.attachment-gallery` with the
not-yet-layered `attachment-gallery.css`; `.overlay__form-fields-card` with the already-layered
`forms.css`) — both confirmed safe, disjoint properties in each case (margin-top only vs.
background/border/padding; border-top only vs. the same). Deploy + Playwright `e2e --full --ux`:
**63/63 passed.** 25 of 29 files now layered; 4 remain (`attachment-gallery.css` 4,
`user-overlay.css` 6, `card-lightbox.css` 8, `highlight.css` 12).

**Checkpoint 7 status: done (2026-09-17).** `attachment-gallery.css` (4 `!important`, same
modifier-class accent-border pattern as `advertisement-overlay.css`, self-contained) wrapped in
`@layer components`. One cross-file overlap (`.attachment-gallery` with `advertisement-overlay.css`)
already checked and confirmed safe in the previous checkpoint. Deploy + Playwright `e2e --full
--ux`: **63/63 passed.** 26 of 29 files now layered; 3 remain (`user-overlay.css` 6,
`card-lightbox.css` 8, `highlight.css` 12).

**Checkpoint 8 status: done (2026-09-17).** `user-overlay.css` (6 `!important`: the same
accent-border modifier pattern plus a `.user-view-meta-row .labeled-field` override beating a
Vaadin/Lumo component default, not a custom-CSS cross-file conflict) wrapped in `@layer
components`. No cross-file selector conflicts found. Deploy + Playwright `e2e --full --ux`:
**63/63 passed.** 27 of 29 files now layered; 2 remain (`card-lightbox.css` 8, `highlight.css`
12) — the two highest-risk files, left for last.

**Checkpoint 9 status: done (2026-09-17).** `card-lightbox.css` (8 `!important`, all on
`.card-lightbox__nav`/`.card-lightbox__close` overriding Vaadin button defaults, self-contained)
wrapped in `@layer components`. No cross-file selector conflicts found. Deploy + Playwright
`e2e --full --ux`: **63/63 passed.** 28 of 29 files now layered; only `highlight.css` (12
`!important`, the highest-risk file) remains.

**Checkpoint 10 status: done (2026-09-17) — all 29 files now layered.** `highlight.css` (12
`!important`, the cross-cutting field-state utility applied across many other files' fields) wrapped
in `@layer components`. Only known cross-file overlap (`.query-datetime-date`/`-time` with
`query-block.css`) already verified safe in Checkpoint 2, and now both files sit in the same layer
anyway so their relative order is unchanged from before this whole migration started. Deploy +
Playwright `e2e --full --ux`: **63/63 passed.**

**File migration complete: 29/29 imported files + `styles.css`'s own tokens/base rules all now
explicitly layered (`@layer tokens, base, components, overrides;`).** Every file ended up in
`@layer components` — none needed `overrides` (no file's whole purpose was "beat everything else,"
which is what that layer is for) and none needed to stay outside `base`/`tokens` beyond
`styles.css`'s own root-level content. **Not yet done — the original motivating goal of Task D**:
review whether any of the 43 pre-existing `!important` declarations can now be removed, now that
explicit layer order (not accidental specificity/source-order wins) governs priority within
`@layer components`. This is a separate, not-yet-started pass — every `!important` found during
this migration was left in place as-is; none were evaluated for removability yet.

## `!important` reduction pass (started 2026-09-17)

**Real structural finding, checked before touching any code:** every one of the 29 files ended up
in the exact same layer (`@layer components`) — none needed `base`/`overrides`. `@layer` only
changes priority *between* layers; within one layer, plain specificity/source-order rules apply
exactly as before this whole migration. So `@layer` by itself cannot have made any of the 43
`!important`s removable purely by virtue of the file-migration just completed — any that turn out
removable are removable for an unrelated, pre-existing reason, not because of anything Task D did
structurally.

**Empirical test 1 (`advertisement-overlay.css`, confirmed removable — 4 of 43):**
`.overlay__view-card { border-top: ... !important; }` plus its three modifier classes
(`--offer`/`--request`/`--product`, each `border-top-color: ... !important;`) — removing only the
3 modifiers' `!important` broke a real Playwright assertion (`assertComputedColor` on
`borderTopColor`, in `advertisement.flow.js`), because the base rule's own still-`!important`
`border-top-color` (via the shorthand) then beat the now-non-important modifier regardless of
specificity/order — `!important` always beats non-important, unconditionally. Removing the base
rule's `!important` too (all 4 together) fixed it: deploy + Playwright `e2e --full --ux`, **63/63
passed**, including the exact assertion that failed before. **Real mechanism confirmed:** the base
rule's own `!important` was unnecessary to begin with (no real Vaadin/Lumo or cross-file
conflict found for this exact rule); once removed, its modifiers no longer needed to match it.
**This class of `!important` is only removable as a whole group (base + all its modifiers
together), never one at a time** — confirmed directly, not assumed.

**Empirical test 2 (`attachment-gallery.css`, confirmed removable — 4 more of 43):** the identical
`.attachment-gallery` base + `--offer`/`--request`/`--product` modifier pattern, same fix (all 4
`!important` removed together). Deploy + Playwright `e2e --full --ux`: **63/63 passed.**

**Empirical test 3 (`user-overlay.css`, confirmed removable — 4 more of 43):** same pattern again,
different class name (`.user-view-card` + `--admin`/`--user`/`--moderator`, not
`.overlay__view-card` — structurally identical, no actual naming collision with the other two
files). Deploy + Playwright `e2e --full --ux`: **63/63 passed.**

**12 of 43 `!important` confirmed removable so far** (3 instances of the same base-rule-never-
actually-needed-`!important` accent-border pattern, 4 each). Remaining 31 no longer match this
exact shape — scanned the rest directly: `user-overlay.css`'s own leftover 2 (`.user-view-meta-row
.labeled-field` flex-direction/align-items, overriding a Vaadin component default, not a same-file
base rule), `taxon-view.css`'s 3 (`.taxon-row-deleted`, single class, no competing same-file rule
found), `main-view.css`'s 1 (`display: none`), `card-lightbox.css`'s 8 (`.card-lightbox__nav`/
`.card-lightbox__close` overriding Vaadin button defaults), and `highlight.css`'s 12 (field-state
utility, several genuinely look like they're fighting Vaadin/Lumo defaults, not our own files) are
all structurally different from the confirmed-safe pattern — each would need its own individual
empirical test rather than being assumed safe by pattern match, and are less likely to be pure
historical cruft given they don't have a redundant same-file base rule sitting behind them.

**Empirical test 4 (`user-overlay.css`'s remaining 2, confirmed removable — 14 of 43 total):**
`.user-view-meta-row .labeled-field { flex-direction: column !important; align-items: flex-start
!important; }` — no existing Playwright assertion covers this specific property, so verified by
reading the actual screenshot (`user-management-promoted-admin-view`, confirmed via the Java side
— `AccountNameViewModeHandler.java` applies `.user-view-meta-row` in exactly this screenshotted
view) after removal: the CREATED AT/UPDATED AT fields still render label-above-value, left-aligned,
column layout unchanged. Deploy + Playwright `e2e --full --ux`: **63/63 passed** (no regression
elsewhere either). This one turned out unnecessary too, despite initially looking like a genuine
Vaadin-component override — worth remembering that "looks like it's fighting Vaadin" is not
reliable enough to skip testing.

**Empirical test 5 (`main-view.css`, confirmed removable — 15 of 43 total):**
`.main-pages > *[hidden], .main-pages > *.vaadin-hidden { display: none !important; }` — hides
inactive tab pages, a high-blast-radius candidate (if broken, multiple tabs' content would render
simultaneously, which the extensive tab-navigating e2e suite would almost certainly catch via
locator ambiguity errors, not just a visual diff). Deploy + Playwright `e2e --full --ux`: **63/63
passed**, no such conflicts surfaced.

**Empirical test 6 (`taxon-view.css`, confirmed removable — 18 of 43 total):**
`.taxon-row-deleted { color; text-decoration: line-through; cursor: default; }` (all 3 properties).
No existing assertion on this exact styling, verified via the `taxon-07-electronics-deleted`
screenshot: the deleted "Electronics" category still renders with strikethrough text, muted color,
and the "(deleted)" badge, unchanged. Deploy + Playwright `e2e --full --ux`: **63/63 passed.**

**Empirical test 7 (`card-lightbox.css`, confirmed removable — 26 of 43 total, the most involved
investigation of this pass):** `.card-lightbox__nav`/`.card-lightbox__close` (8 `!important` total)
target a `UiIconButton extends Vaadin Button` — a real Shadow DOM web component. First attempt
added a permanent `assertComputedColor` check (`backgroundColor`/`color`) to
`advertisement.flow.js`'s existing lightbox flow — it failed (`expected rgba(255,255,255,0.15),
got rgba(0,0,0,0)`), which first looked like a real regression. Restoring the `!important`
produced the *exact same* failure, and removing the `@layer components` wrapper entirely (fully
reverting to pre-Task-D state) *also* produced the identical result — proving the assertion itself
was invalid, not a real regression: `background-color` set on a Shadow DOM host in plain CSS does
not reach whatever paints the component's actual internal visual, so `getComputedStyle` on the
host can never observe this rule's effect either way. The permanent assertion was reverted (kept
would have been a permanently-failing, meaningless check).

**Real ground truth established via targeted diagnostic (not assumed):** cropped
`locator.screenshot()` of just the nav button, first with the real translucent-white value (came
back blank/inconclusive — too subtle to read), then with the same property forced to solid `red`
with no `!important` at all — **byte-identical screenshot hash to the original**, proving
`background` genuinely never paints through on this element regardless of value or `!important`.
Same technique on `color` (inherited property, unlike `background`) forced to `lime` — this time a
visibly green icon appeared, confirming `color` *does* paint through and needs no `!important`
either. Restored the real intended values (translucent white background, white color) with all 8
`!important` removed. Deploy + Playwright `e2e --full --ux`: **63/63 passed.**

**Lesson for the remaining file (`highlight.css`):** `getComputedStyle`-based verification is
unreliable for any Shadow DOM host element — a passing/failing computed-style check on such an
element proves nothing about its real rendering. Screenshot-based ground truth (ideally a
high-contrast diagnostic color swap, not just checking the real subtle value) is the only reliable
method for this class of `!important`. `highlight.css`'s rules target plain `<div>`/field-wrapper
classes, not Vaadin custom elements directly, so this specific pitfall is less likely there, but
should still be verified the same way rather than assumed.

Chosen order (per explicit user preference, 2026-09-15): start with Task A.

## Related

- [improvement-039](improvement-039-dark-mode-lumo-tokens.md) — dark mode's real home; this
  issue's former "Task B" moved back there in full 2026-09-16 after a failed implementation
  attempt, now deprioritized pending a decision on whether the feature is wanted at all.
- [improvement-116](improvement-116-vaadin-theme-annotation-migration.md) — touches the same
  `@Theme` mechanism Task A's runtime theme-switch relies on; sequence 116 after Task A settles,
  not before, to avoid changing two theme-selection mechanisms at once.
