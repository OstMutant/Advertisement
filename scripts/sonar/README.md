# scripts/sonar

SonarQube static analysis tooling for the marketplace app — runs the scanner in an isolated Docker
container against a local SonarQube server, no `pom.xml` changes (see `DECISIONS.md`).

## Flow

Entry points:
```bash
bash scripts/sonar.sh [--no-gate]      # Linux/WSL
scripts\sonar.bat [--no-gate]          # Windows
```

`sonar.sh`/`sonar.bat` are thin wrappers — the real logic lives in `run.sh`, which manages the
SonarQube server container itself (recreating it on a stale image, wiping it if a version jump
makes the embedded database unmigratable — see `DECISIONS.md`) before running the scanner:

```mermaid
flowchart LR
    A1[sonar.sh] -->|Linux/WSL| B[run.sh]
    A2[sonar.bat] -->|Windows, via WSL| B
    B --> C[docker-compose.sonar.yml]
    C -->|image changed?| D{recreate}
    C -->|DB migration NOT_SUPPORTED?| E{wipe volumes}
    B --> F[sonar-project.properties]
    F -->|token invalid?| G{regenerate token}
    F -->|module list drifted from pom.xml?| H{auto-fix}
```

Each file's own header (open the file, or this directory's Tooling & Pipelines card on
`architecture-map.html`) has its own Description/Usage/Env/Input/Outputs/Returns — this file only
shows how they chain together.

## Dependencies

- Docker (SonarQube server container + scanner container)
- Compiled `target/classes` per module (`sonar.java.binaries` — see `DECISIONS.md`)
