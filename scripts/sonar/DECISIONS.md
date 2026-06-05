# Architecture & Technical Decisions — SonarQube

---

## 2026-05-15 — SonarQube setup via Docker, no pom.xml changes

**Decision:** SonarQube analysis is configured entirely in `sonar/` — no plugin or properties added to `pom.xml`. The scanner runs in a `sonarsource/sonar-scanner-cli` container via `docker cp` (same pattern as Playwright). The SonarQube server runs separately in Docker (`sonar/docker-compose.sonar.yml`, port 9099).

**Why:** Keeping analysis tooling out of `pom.xml` avoids polluting the build with dev-only infrastructure. The `sonar/run.sh` script starts the server automatically if not running, copies source and compiled classes, runs analysis, and prints the dashboard URL.

**How to run:**
```bash
bash /app/sonar/run.sh   # Linux / WSL
sonar\run.bat            # Windows
```

**Token:** stored in `sonar/sonar-project.properties` (local dev instance, admin:admin, not sensitive).

---

## 2026-05-15 — sonar.java.binaries required for full Java analysis

**Decision:** Compiled `target/classes` directories are copied alongside source files into the scanner container and referenced via `sonar.java.binaries`.

**Why:** Without bytecode, SonarQube Java sensor fails with `AnalysisException: please provide compiled classes`. Source-only analysis is not supported for Java projects. The project must be compiled locally (by IDE or `mvn compile`) before running `run.sh`.

---

## 2026-05-15 — sonar.java.libraries intentionally empty

**Decision:** `sonar.java.libraries` is not set (third-party jars not copied to scanner container).

**Why:** Copying the full Maven local repository (~hundreds of MB) into the scanner container via `docker cp` is impractical. The consequence is unresolved imports during analysis and slightly less precise results for rules that require type resolution across library boundaries. Acceptable for local code quality checks.
