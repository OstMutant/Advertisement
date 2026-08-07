# Architecture Documentation

Complete architecture documentation for the Marketplace modular monolith (Java 25 / Spring Boot 4.1.0 / Vaadin 25.2.3).

Every diagram and data table lives in `docs/architecture/architecture-map.html` — an interactive, generated
tool, not a set of hand-maintained markdown files. This directory holds no `.md` content files;
this `README.md` is the only file here.

## What's in `architecture-map.html`

- **Module Dependencies** — Maven dependency graph, rendered live from `pom.xml`, domain-colored,
  click a node to open its module page. Includes a live "Architecture Checks" section (real grep-
  based coupling verification), Largest Java Files, Constructor Injection, and Largest Packages
  tables.
- **SPI Map** — which Ports and Hooks live in `platform-commons` and who implements them, rendered
  live from real Java source, grouped by subsystem, click a node to open its real `.java` file.
- **Database ERD** — tables with columns, types, constraints, indexes, and foreign keys, rendered
  live from the real Liquibase changelogs; each column's/table's own `remarks=` attribute is the
  single source of truth for what it means (see root `CLAUDE.md`'s "Database Changes" guideline).
- **Bounded Contexts** — domain grouping (entities/services/tables/SPI ports per domain) and
  cross-domain relationships, both derived live from real code signals (real `@Table`/`*Service`
  classes, real SPI `implements` relationships, real Hook `entityType()` declarations).
- **Module page** (per module) — a "Code Metrics" section with real SonarQube numbers (lines of
  code, complexity, cognitive complexity, code smells) and real ArchUnit numbers (Efferent/
  Afferent Coupling, Instability, Abstractness), when their respective data sources are available.

## How to use it

1. Open `docs/architecture/architecture-map.html` in a browser.
2. **System** screen — entry points to Diagrams, Tooling & Pipelines, Backlog, and Docker.
3. **Diagrams → Module Dependencies** — start here for module structure and the live coupling
   checks.
4. **Diagrams → SPI Map** — all Ports/Hooks and their implementations.
5. **Diagrams → Database ERD** — full schema.
6. **Diagrams → Bounded Contexts** — domain boundaries and cross-domain relationships.
7. Click any module to see its own page — dependencies, entities, key services, contracts, ADRs,
   and Code Metrics.

## Generated from

- Project root: `/app`
- Analyzed source directories: `query-lib`, `platform-commons`, every `*-spring-boot-starter`,
  `marketplace-orchestrator`, `marketplace-app`.
- Analyzed schemas: every Liquibase migration under `*/src/main/resources/db/*/changes/`.
- `pom.xml` files for every module listed in the root `pom.xml`'s `<modules>` block.
- A running SonarQube server (`localhost:9099`) for code-quality metrics, and the last
  `bash scripts/unit-tests.sh` run's `ArchitectureMetricsExport` output for coupling metrics —
  both optional; the tool degrades gracefully when either is unavailable.
