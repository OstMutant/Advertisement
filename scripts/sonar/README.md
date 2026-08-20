# scripts/sonar

SonarQube is a static-analysis tool that scans the codebase for bugs, code smells, and security
vulnerabilities, enforcing a quality gate on every run. This project runs it locally in an isolated
Docker container instead of a hosted SonarCloud instance, so results stay available offline and the
quality gate can block a local run without depending on an external service — no `pom.xml` changes
(see `DECISIONS.md`).

## Flow

Entry point: `run.sh`.

```mermaid
flowchart TD
    B[run.sh]
    B --> F1[sonar-project.properties]
    F1 --> H{"module list drifted<br/>from pom.xml?"}
    H -->|yes| H1[auto-fix] --> C
    H -->|no| C[docker-compose.sonar.yml]
    C --> D{"image<br/>changed?"}
    D -->|yes| D1[recreate] --> E
    D -->|no| E{"DB migration<br/>NOT_SUPPORTED?"}
    E -->|yes| E1[wipe volumes] --> F2
    E -->|no| F2[sonar-project.properties]
    F2 --> G{"token<br/>invalid?"}
    G -->|yes| G1[regenerate token] --> Z
    G -->|no| Z["scanner runs -><br/>analysis uploaded + report generated"]
```

Each file's own header has its own Description/Usage/Env/Input/Outputs/Returns — this file only
shows how they chain together.

## Dependencies

- Docker (SonarQube server container + scanner container)
- Compiled `target/classes` per module (`sonar.java.binaries` — see `DECISIONS.md`)
