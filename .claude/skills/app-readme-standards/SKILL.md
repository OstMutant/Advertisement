---
name: app-readme-standards
description: Conventions for the repo root's two markdown files -- README.md (marketing/git-visibility landing page) and INFRASTRUCTURE.md (technical infra overview). Sibling to module-readme-standards and infra-readme-standards, scoped to the repo root only.
allowed-tools: Read Edit Write
---

# App README Standards

Conventions for the repo root's own markdown files — `README.md` and `INFRASTRUCTURE.md` — as
distinct from `module-readme-standards` (a single module's own `README.md`) and
`infra-readme-standards` (a script-group directory's own `README.md`). No sibling `app-doc-standards`
exists: the repo root has no `.java`/`pom.xml`/Liquibase files of its own to govern.

## `README.md` — marketing/git-visibility, exempt from "one fact, one canonical home"

`README.md` is the page GitHub renders when anyone lands on this repository — its job is to be an
appealing pitch (what this project is, what it does, why it's interesting), not a strict technical
reference. It is a deliberate, explicit exception to `.claude/rules.md`'s "One fact, one canonical
home" rule: content here may legitimately restate a fact that also has a canonical home elsewhere
(e.g. an "Architectural Principles" section overlapping root `CLAUDE.md`'s "Architecture
Guidelines") when restating it serves the pitch — accuracy still matters, but avoiding restatement
does not override making the page compelling to a newcomer.

## `INFRASTRUCTURE.md` — technical infra overview, follows the standard discipline

`INFRASTRUCTURE.md` is the root-level technical counterpart — governed by the same "One fact, one
canonical home" / "Atomic unit first, then directory-level index" discipline as every other
technical document in this repo. It gives a reader landing at the repo root the infrastructure
landscape: what runs the project (Docker Compose stack, deploy scripts), how the pieces fit
together — without restating what `scripts/README.md`, `playwright/README.md`, or any other
directory's own `README.md` already states in full. Reference those files, don't restate their
content.

`Running Locally`, `Helper Scripts`, `Database Scripts`, `Environment Variables`, `Running Without
Docker`, and `AI-Assisted Development Workflow` have been migrated out of root `README.md` (was
346 lines) into `INFRASTRUCTURE.md` (170 lines) — `README.md` (now 187 lines) keeps only a short
`Running & Infrastructure` pointer/quickstart section in their place.

## Where this gets invoked

Anyone (human or Claude) writing or editing root `README.md` or root `INFRASTRUCTURE.md` directly.
