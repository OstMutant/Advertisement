# `.claude/skills/`

Skill definitions, each invoked via the Skill tool. Each skill's own `SKILL.md` is that skill's
complete, self-contained description — this file is only a top-level index, never a per-skill
`README.md` inside any individual skill's own subdirectory (see `infra-readme-standards`'s
`.claude/skills/` section for why). A skill's description is how it gets matched to a task, so
each one below is its `SKILL.md` frontmatter `description:` reproduced verbatim, not shortened.

- **`app-readme-standards`** — Conventions for the repo root's two markdown files -- `README.md`
  (marketing/git-visibility landing page) and `INFRASTRUCTURE.md` (technical infra overview).
  Sibling to `module-readme-standards` and `infra-readme-standards`, scoped to the repo root only.
- **`infra-doc-standards`** — File-level and per-function header conventions for
  infrastructure/tooling files -- bash/batch scripts, `docker-compose*.yml`, `.properties` (incl.
  `.env`, `lombok.config`), YAML config, `.gitignore`/`.gitattributes`, JavaScript, and Python
  files.
- **`infra-readme-standards`** — README and Flow-diagram conventions for a script-group
  directory's own `README.md` -- what the tool is, the Mermaid Flow section, ISO 5807 decision
  diamonds, the root `scripts/README.md`, and nested library/support folders.
- **`module-doc-standards`** — Comment conventions for Java source files, `pom.xml`, and
  Liquibase changelogs -- Javadoc on classes/methods (including the mechanically-required SPI
  interface convention), inline comments, `pom.xml` dependency comments. Sibling to
  `module-readme-standards` (module-level `README.md`).
- **`module-readme-standards`** — Conventions for a Java module's own `README.md` -- what it
  provides, its key classes, its dependencies -- for facts that don't fit inside any single
  file's own Javadoc/comments.
