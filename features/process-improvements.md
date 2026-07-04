# Process Improvements — faster feedback, more control

**Source:** development-process audit, 2026-07-04 (tooling state, test timings, dependency scan
via `versions-maven-plugin` against live repositories). Part 2 added same day: automation,
quality gates, architecture-consistency tooling. Part 3 added same day: Java best practices
and UX/design trends review (verified against code, not generic advice).

Ordered by impact/effort. Each item is independently actionable.

---

# Part 1 — Faster feedback loop

---

## 1. Install buildx + BuildKit cache mounts — deploy 7-10 min → ~3-5 min

**Observed:** `docker buildx version` → "unknown command"; the 2026-07-03 deploy log shows the
DEPRECATED legacy-builder warning. `scripts/CLAUDE.md` already documents the one-time buildx
install — it was never executed in this environment.

**Actions:**
1. Install buildx per `scripts/CLAUDE.md` (curl to `~/.docker/cli-plugins/docker-buildx`).
2. Add `RUN --mount=type=cache,target=/root/.m2 ...` to the Maven steps in `Dockerfile` so
   `dependency:go-offline` and module builds reuse the local repo across image builds.
3. Verify `--progress=plain` streaming works in `deploy.sh` afterwards.

---

## 2. Update security-critical and outdated dependencies

Scanned 2026-07-04 (`./mvnw versions:display-property-updates` / `display-parent-updates` /
`display-dependency-updates`):

| Dependency | Current | Available | Notes |
|---|---|---|---|
| `owasp-java-html-sanitizer` | 20240325.1 | 20260313.1 | **Priority 1** — security-critical (sanitizes user HTML), ~2 years behind |
| `spring-boot-starter-parent` | 4.0.6 | 4.1.0 | minor; pulls current Jackson/Logback/Spring Security |
| `vaadin` | 25.1.5 | 25.2.1 | minor |
| AWS S3 SDK | 2.44.4 | ~2.55.x | routine bump |
| `jetbrains-annotations` | 24.1.0 | 26.x | cosmetic |

**Plan:** bump owasp-sanitizer alone first + full e2e; then Spring Boot 4.1.0 + Vaadin 25.2.1
together in one PR with full e2e. Ignore transitive noise from the Spring Boot BOM
(elasticsearch/kotlin/pulsar — not used directly).

**Process addition:** run `./mvnw versions:display-property-updates` monthly (or wire into CI)
so a security library can no longer silently fall two years behind.

---

## 3. Add a unit/integration test layer — catch SQL bugs in seconds, not 11 minutes

**Observed:** the whole project has **2** JUnit test files (both in query-lib). Every
regression signal currently comes from the 11-minute Playwright suite after a 3-10 minute
deploy.

**Actions:**
1. Testcontainers (PostgreSQL) repository tests for the raw-SQL layer — the most fragile code
   (`AdvertisementRepository`, `AuditLogRepository`, `TaxonAssignmentRepository`,
   `AttachmentRepository`, filter/sort SQL via query-lib).
2. Plain unit tests for service logic that needs no DB (sanitizer policy, diff computation,
   translation resolution in `DefaultTaxonPort`).
3. Keep e2e as the outer loop; the new layer becomes the inner loop (~30 s).

---

## 4. Minimal CI pipeline

**Observed:** no CI at all (no `.github/`, Jenkinsfile, or GitLab config). Tests run only when
someone remembers; the "tests after fixes" rule is enforced by discipline alone.

**Actions:** GitHub Actions (or equivalent):
- every push: `mvn test` + full multi-module build
- PR: + smoke e2e scenario
- nightly: full e2e (`e2e --full`)
- optional: SonarQube step (server and script already exist — currently outside the loop)

---

## 5. Docs-drift guard

**Observed (proof it's needed):** 2026-07-03/04 review found three stale docs — wrong table
names in `user-spring-boot-starter/CLAUDE.md` and `audit-spring-boot-starter/CLAUDE.md`, and a
resolved-but-still-open finding in `docs/architecture/06-coupling-analysis.md`. `/sync-docs`
is manual and demonstrably under-used.

**Actions:** a git hook (hook infrastructure already exists — the commit-approval hook works)
that warns/blocks when files under `*/db/**changelog**` change without a matching CLAUDE.md
update in the same starter. Alternatively: a CI check comparing Liquibase `tableName=` values
against the "Tables:" line of each starter's CLAUDE.md.

---

## 6. SQL-based seeding for Playwright spec 05 — e2e --full 11 min → ~7-8 min

**Observed:** spec `05-seed-filter-sort-pagination` seeds 50 users + 50 advertisements through
the browser UI (2 × 1.5 min). The signup flow is already covered by spec 01; re-exercising it
50 times adds time, not coverage.

**Actions:** seed via SQL fixture (docker exec psql) or a test-only endpoint before the spec;
keep one UI-created entity to preserve the end-to-end path. After seeding is data-isolated,
gradually enable `workers: 2` (currently `workers: 1, fullyParallel: false`).

---

# Part 2 — Automation, quality gates, architecture consistency

**Context:** architecture rules currently live as prose in CLAUDE.md / rules.md and are
enforced by discipline and review only. The 2026-07-03/04 audit showed the limit of that
approach: three stale docs, unguarded port injections (improvement-011), and a view deviating
from its own refresh() pattern (improvement-010) — all of which survived multiple sessions
unnoticed. The theme of Part 2: convert prose rules into machine-checked gates.

---

## 7. ArchUnit test module — prose rules become build-breaking tests

**Highest ROI of Part 2.** One new test module (e.g. `architecture-tests`, or tests inside
marketplace-app) codifying the existing rules as JUnit tests. ~1 day of work, enforced forever.

Direct rule mapping from CLAUDE.md / rules.md:

| Existing prose rule | ArchUnit rule |
|---|---|
| UI must not call repositories directly | `noClasses().that().resideInAPackage("..ui..").should().dependOnClassesThat().resideInAPackage("..repository..")` |
| No Vaadin in starters | `noClasses().that().resideInAPackage("org.ost.(audit\|attachment\|user\|advertisement\|taxon)..").should().dependOnClassesThat().resideInAPackage("com.vaadin..")` |
| Ports/Hooks live in platform-commons only | `classes().that().haveSimpleNameEndingWith("Port").and().areInterfaces().should().resideInAPackage("org.ost.platform..")` |
| No `@PreAuthorize` at class level on services | custom condition over class annotations |
| No `Optional` method parameters | custom condition over method signatures |
| `config` (not `configuration`) packages | naming rule |
| `*PortImpl` / `*HookImpl` delegation-only | partial: forbid Jackson/stream imports in `*.spi` impl classes |

Note: Spring Modulith is NOT needed here — Maven multi-module already gives compile-time
isolation between starters; ArchUnit covers the intra-module rules that Maven cannot.

---

## 8. Maven Enforcer plugin — dependency hygiene at build time

A few lines of config in the root pom:
- ban direct starter→starter dependencies in poms (currently convention only)
- `dependencyConvergence` — catch conflicting transitive versions
- `requireJavaVersion` / `requireMavenVersion`
- optionally `banDuplicatePomDependencyVersions`

---

## 9. SonarQube quality gate → blocking

The server and `scripts/sonar.sh` already exist but results are informational. Enable
fail-on-quality-gate in the scanner invocation so new issues fail the run. Zero new
infrastructure — flipping a switch on an existing tool.

---

## 10. `/quality-gate` skill + Definition of Done

A single command chaining the gates from fastest to slowest with one summary report:
1. compile + ArchUnit (~30 s)
2. unit/Testcontainers tests (~1-2 min, once Part 1 item 3 exists)
3. Sonar quality gate (blocking)
4. smoke e2e (~2-3 min)

Record in rules.md as the Definition of Done: a feature is not complete until `/quality-gate`
is green, the relevant e2e scenario passes, DECISIONS.md is updated (if architectural), and
the issue file is moved to `features/completed/`.

---

## 11. Claude Code hooks for the recurring failure modes

Hook infrastructure already works (the commit-approval PreToolUse hook is live). Add:
- **changelog→docs guard:** PostToolUse hook on edits under `*/db/**changelog**` → remind (or
  block commit) until the owning starter's CLAUDE.md Schema section is touched in the same
  change — directly addresses the docs drift proven in this audit (Part 1, item 5).
- **incremental compile:** PostToolUse hook on `*.java` edits → `mvn compile -pl <module> -q`
  so compilation errors surface in seconds instead of at deploy time.

---

## 12. Feature-workflow standardization

- **SPEC.md template + `/feature <name>` skill:** recent specs converged on
  Goal / Problem / Files / Steps / Acceptance criteria (see
  `features/leaf-widgets-plain-classes/SPEC.md`). Scaffold new features from that template so
  every feature starts with measurable acceptance criteria.
- **OpenRewrite (deferred):** worth adopting only if mechanical mass-refactors become regular —
  official recipes exist for Spring Boot upgrades (4.0→4.1); custom recipes possible for
  conversions like leaf-widgets. Low priority until then.

**Explicitly rejected for now:** PIT mutation testing (premature without a unit layer),
Error Prone/NullAway (duplicates Lombok `@NonNull` at doubtful gain), Checkstyle (Sonar
already covers style).

---

# Part 3 — Java best practices & UX/design trends

**Context:** reviewed 2026-07-04 against the actual code (config files, `UserAutoConfiguration`,
`SqlOperator`, attachment starter, theme CSS, `@Route` declarations) — every gap below is
verified, not assumed.

**Already at or above modern standard (keep as-is):** record patterns in switch
(`case ChangeEntry.FieldChange(var field, var from, var to)`), unnamed variables `_`, text
blocks for SQL, constructor injection everywhere, immutable DTOs via records/`@Value`,
documented Optional discipline.

---

## 13. Enable virtual threads — one config line

**Observed:** `spring.threads.virtual.enabled` absent from all application configs.

For a Vaadin app on blocking JDBC this is the free win of the Loom era: UI requests stop
pinning platform threads. Vaadin 24.4+ is fully compatible. Add the property, run full e2e.
Cheapest item in this entire document.

---

## 14. Deep links for advertisements — the whole app lives at one URL

**Observed:** the only route is `@Route("")` on `MainView`. An advertisement cannot be linked,
shared, bookmarked, or opened from history; browser Back has no meaningful behavior.

For a marketplace, a shareable card URL is core mechanics (and a prerequisite for SEO if the
listing ever becomes public). Vaadin supports it: `@Route("ads/:id")` or a URL parameter on
the main view that opens the overlay on navigation. **Highest-priority UX item.** Deserves its
own feature SPEC when picked up.

---

## 15. DelegatingPasswordEncoder instead of raw BCrypt

**Observed:** `UserAutoConfiguration.java:54` returns `new BCryptPasswordEncoder()` directly.

Two issues: OWASP has recommended Argon2id since 2023, and a raw encoder without
`PasswordEncoderFactories.createDelegatingPasswordEncoder()` makes any future algorithm
migration painful (no `{bcrypt}` prefix on stored hashes → no seamless upgrade path). Switch
to the delegating encoder **before real users exist**; optionally move the default to
Argon2id at the same time.

---

## 16. Thumbnail generation on upload — cards currently load raw S3 originals

**Observed:** no resize/thumbnail code anywhere in attachment-spring-boot-starter (only a
YouTube thumbnail URL helper). The list view loads full-size originals: 50 ads × multi-MB
photos.

Minimum: generate a thumbnail at upload time + `loading="lazy"` on card images. Modern
target: WebP/AVIF variants + blur-up placeholder (LQIP). Deserves its own feature SPEC.

---

## 17. Actuator + structured logging — zero observability today

**Observed:** no actuator, no metrics, no tracing in any pom; `/health` is a custom endpoint.

Baseline for any production service: `spring-boot-starter-actuator` (health/metrics/env) and
structured JSON logs — one property in current Spring Boot
(`logging.structured.format.console=ecs`). Without it, the first production incident is
debugged by grepping plain text.

---

## 18. Accessibility fixes — including a legal angle

The European Accessibility Act applies to e-commerce since June 2025.

**Verified concrete failure:** header text `#94a3b8` on white = ~2.5:1 contrast — fails WCAG
AA (4.5:1 required for body text). Card description `#64748b` = 4.76:1, barely passes.

Actions: fix header contrast, add visible focus states, ARIA labels on custom chips and the
lightbox. The existing 'N' keyboard shortcut with its shadow-DOM guard is a good foundation.

---

## 19. pg_trgm index — ILIKE '%x%' is a full scan

**Observed:** `SqlOperator.LIKE_IGNORE_CASE` renders `ILIKE :param` with leading wildcard —
no index can serve it. First performance cliff as data grows.

Fix without code changes: `CREATE EXTENSION pg_trgm` + a GIN trigram index on
`advertisement.title` (Liquibase changeset in the advertisement starter). Full-text search
(tsvector) only when description search is needed. Note: PostgreSQL is at 15-alpine; current
is 17 — bump opportunistically.

---

## 20. Dark mode — blocked by hardcoded hex colors

**Observed:** light-only theme; component CSS uses raw hex (`#94a3b8`, `#64748b`, `#1e293b`,
...) instead of Lumo custom properties.

Dark mode is a default expectation in 2026 and Lumo ships a dark palette nearly free — but
only after the custom CSS migrates from hardcoded hex to `var(--lumo-*)` tokens. Two steps:
(1) tokenize the theme CSS, (2) add the toggle + `prefers-color-scheme` default.

---

## 21. Later / optional

- **JSpecify null annotations** — Spring Framework 7 (already in use here) adopted JSpecify;
  `@NullMarked` packages give compile-time nullness on top of Lombok's runtime `@NonNull`.
  Adopt during a large refactor, not as a standalone pass.
- **AI-assist in the ad form** (description generation, category suggestion from photo/text) —
  standard on major marketplaces since 2025; also a natural showcase for this project's
  Claude-driven workflow. pgvector for "similar ads" belongs here too.
- **CDS/AOT cache** (Spring Boot 4) — faster restarts; nice-to-have.

**Explicitly skipped:** infinite scroll (admin persona — pagination is right), command
palette (premature), micro-animations (cosmetics).

---

## Suggested order

Part 1 (speed):
1. buildx + cache mounts (one hour, halves every deploy forever)
2. owasp-sanitizer bump + full e2e
3. Minimal CI (mvn test + build)
4. Testcontainers repository tests
5. Spring Boot 4.1.0 + Vaadin 25.2.1 in one PR + full e2e
6. SQL seeding for spec 05

Part 2 (control):
7. ArchUnit module (biggest consistency win, ~1 day)
8. Sonar quality gate → blocking (existing tool, config flip)
9. `/quality-gate` skill + DoD in rules.md
10. Hooks: changelog→docs guard, incremental compile
11. Maven Enforcer
12. CI on top (Part 1, item 3) makes all gates unavoidable

Part 3 (best practices & trends), by cost/impact:
13. Virtual threads (1 line)
14. Deep links for ads (1-2 days — own feature SPEC)
15. DelegatingPasswordEncoder (1 hour — do before real users)
16. Thumbnails on upload (1 day — own feature SPEC)
17. Actuator + structured logging (half a day)
18. Header contrast + focus states (hours)
19. pg_trgm index on title (1 hour)
20. Dark mode via Lumo tokens (2-3 days)
