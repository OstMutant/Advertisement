# Advertisement Platform

A production-oriented service marketplace, built as a hands-on playground for exploring backend
and architectural trade-offs in a real, working system rather than a toy example.

[Architecture](#architectural-principles) · [Module Docs](#module-layout) · [Testing Strategy](#testing-strategy)

---

## What is it?

A marketplace where users publish service/product listings, browse and filter a shared catalog,
and administrators moderate everything through a full audit trail. Every module doubles as a
demonstration of one specific engineering pattern — SPI-based module decoupling, immutable audit
snapshots, optimistic concurrency, SQL without an ORM — applied to a real feature, not an isolated
sample.

---

## About

This is not a finished product — there is no fixed public feature roadmap, and the product side
keeps evolving. The engineering foundation underneath it is the actual point of the project:
- explicit control over data flow and SQL
- composable abstractions without framework magic
- clear responsibility boundaries between layers

---

## Architectural Principles

**Explicit over implicit** — No ORM, no JPA; all SQL is written manually via Spring JDBC, no
hidden query generation or implicit persistence behavior.

**Immutable data flow** — Entities and DTOs are immutable, no shared mutable state between
layers. Every domain write is captured as an immutable, versioned snapshot, not a mutable log
line — snapshots are diffed at read time into a field-level activity timeline, so "what changed"
is always derived from real before/after state, never hand-maintained.

**Optimistic concurrency** — `Advertisement`, `Taxon`, and `User` updates carry a `version`
column; a stale write is rejected with `OptimisticLockingFailureException` instead of silently
overwriting a concurrent change.

**UI as a thin adapter** — Vaadin handles layout and interaction wiring only, no business logic
lives inside UI components.

**Declarative where it matters** — Validation rules, localization keys, and filter definitions are
expressed declaratively and kept strongly typed.

**Attachment lifecycle** — uploads go to S3-compatible storage with transactional metadata (a
failed post-save step rolls back the DB row, verified by a real-transaction Testcontainers test),
scheduled cleanup of orphaned objects, snapshot-based restore, and audit integration.

**Testing** — three layers, each catching a different class of regression: plain JUnit for pure
logic, Testcontainers-backed repository tests against a real Postgres for SQL correctness, and
Playwright for full browser-driven end-to-end flows.

---

## Module Layout

```
advertisement-parent
├── query-lib                         — framework-agnostic SQL query-building library
├── platform-commons                  — shared kernel: DTOs, domain events, SPI interfaces
├── audit-spring-boot-starter         — audit subsystem: write side + read side
├── attachment-spring-boot-starter    — photo/attachment module + S3 storage
├── user-spring-boot-starter          — User domain + Spring Security integration
├── advertisement-spring-boot-starter — Advertisement domain
├── taxon-spring-boot-starter         — Taxonomy domain: categories, tags, classifiers
├── provider-profile-spring-boot-starter — Provider profile domain (backend only, no UI yet)
├── integration-tests                 — Testcontainers repository tests + fixtures (test-only)
├── marketplace-orchestrator           — application/BFF layer: cross-domain use-case orchestration
└── marketplace-app                   — Vaadin application (all UI)
```

Per-module documentation:

| Module | README | Decisions |
|---|---|---|
| query-lib | [README](query-lib/README.md) | [DECISIONS](query-lib/DECISIONS.md) |
| platform-commons | — | [DECISIONS](platform-commons/DECISIONS.md) |
| audit-spring-boot-starter | [README](audit-spring-boot-starter/README.md) | [DECISIONS](audit-spring-boot-starter/DECISIONS.md) |
| attachment-spring-boot-starter | [README](attachment-spring-boot-starter/README.md) | [DECISIONS](attachment-spring-boot-starter/DECISIONS.md) |
| user-spring-boot-starter | [README](user-spring-boot-starter/README.md) | — |
| advertisement-spring-boot-starter | [README](advertisement-spring-boot-starter/README.md) | — |
| taxon-spring-boot-starter | — | [DECISIONS](taxon-spring-boot-starter/DECISIONS.md) |
| provider-profile-spring-boot-starter | — | [DECISIONS](provider-profile-spring-boot-starter/DECISIONS.md) |
| integration-tests | [README](integration-tests/README.md) | [DECISIONS](integration-tests/DECISIONS.md) |
| marketplace-orchestrator | — | [DECISIONS](marketplace-orchestrator/DECISIONS.md) |
| marketplace-app | [README](marketplace-app/README.md) | [DECISIONS](marketplace-app/DECISIONS.md) |
| playwright | [README](playwright/README.md) | [DECISIONS](playwright/DECISIONS.md) |
| scripts | [README](scripts/README.md) | [DECISIONS](scripts/DECISIONS.md) |

---

## Key Technical Decisions

| Decision | Reason |
|---|---|
| Spring JDBC over JPA | Full control over queries, no hidden side effects |
| Composable filter model | Type-safe, reusable query logic without ORM abstractions |
| Immutable entities (`@Value` + `@Builder`) | Predictable state, no accidental mutation |
| Enum-based i18n keys | Compile-time safety for localization strings |
| Rule-oriented validation | Validation logic isolated from UI and service layers |
| SPI extension pattern | Starters extend app behaviour without knowing each other |

---

## Feature Highlights

- **Advertisements** — create/manage listings with rich HTML descriptions (sanitized
  server-side), photos and video; browse the shared catalog with dynamic filter/sort/pagination
  by category, city, and listing type; ownership checks, soft delete + restore, optimistic
  locking.
- **Users** — sign up (rate-limited), manage account settings (locale, page sizes), edit or
  restore a profile; role-based access (Admin/Moderator/User).
- **Taxonomy** — categories/tags with per-locale translations, soft-deletable, many-to-many
  assignment to any entity type; admins manage categories and taxonomy directly.
- **Attachments** — photo/video uploads to S3-compatible storage, YouTube embeds, media history
  for restore.
- **Audit trail** — every domain write captured as a versioned snapshot; admins/moderators review
  every change through a per-entity activity timeline with field-level diffs, restore prior
  versions.
- **i18n** — English/Ukrainian, enum-based translation keys (missing keys fail fast, never a
  silent fallback).
- **Deep links & rich previews** — share a listing link with a rich social-media preview (Open
  Graph, JSON-LD) for social/search previews.

---

## Testing Strategy

Three independent layers, each targeting a different failure mode:

| Layer | Tool | What it catches |
|---|---|---|
| Unit | Plain JUnit 5 (+ Mockito) | Pure logic regressions — no Docker, no database |
| Integration | JUnit 5 + Testcontainers (real Postgres) | SQL correctness — filters, sorts, pagination, optimistic locking, real Liquibase schema |
| End-to-end | Playwright | Full browser-driven flows across the actual Vaadin UI, including auth, CRUD, media, and the audit timeline |

See [Module Layout](#module-layout) above for each layer's own README/DECISIONS, or
[INFRASTRUCTURE.md](INFRASTRUCTURE.md) for the exact commands to run each layer.

---

## Running & Infrastructure

Quickstart: `bash scripts/deploy-and-run.sh`

Full infrastructure details (Docker Compose stack, helper scripts, environment variables, AI dev
workflow, running without Docker): see [INFRASTRUCTURE.md](INFRASTRUCTURE.md).

---

## Roadmap

Actively evolving on both sides: the engineering foundation keeps absorbing new patterns
(the audit/attachment/taxon starters, Testcontainers-based integration tests, and the isolated
local CI runner are all recent additions), and the product surface keeps growing on top of it.
Architectural decisions may be revisited and implementations replaced — that's the point of
treating this as a playground, not a frozen codebase.

Planned directions:
- Extend rule-based validation capabilities
- Improve composability of the generic filtering layer
- Explore alternative API adapters (REST)
- Broaden the marketplace's public-facing feature set (provider profiles, richer discovery)

---

## Author's Note

I value clarity over convenience.  
I prefer explicitness over magic.  
I build systems to be understood, not just used.

Feedback and architectural discussions are welcome.
