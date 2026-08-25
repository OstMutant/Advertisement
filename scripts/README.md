# Scripts

Developer helper scripts for building, deploying, and maintaining the project.

All scripts resolve the project root automatically — run them from any directory.

**Self-healing rule:** every script auto-starts or auto-pulls whatever it needs.
If a container is stopped, it starts it. If an image is missing, it pulls it. If nothing exists, it bootstraps from scratch.

---

## Entry points

Each entry point's own real logic lives where noted below — see that file's own header for what
it does, its flags, and its exact behavior; not restated here.

| Entry point | Real logic lives in |
|---|---|
| `deploy-and-run.sh` / `.bat` | `scripts/deploy-and-run/run.sh` |
| `run-local.bat` | self-contained (no delegation) |
| `playwright.sh` / `.bat` | `playwright/run.sh` |
| `build-and-test.sh` / `.bat` | `scripts/build-and-test/run.sh` — see `scripts/build-and-test/README.md` for the full flow |
| `integration-tests/run.sh` | self-contained — a direct alternative to `build-and-test.sh`, needs a local Java install |
| `sonar.sh` / `.bat` | `scripts/sonar/run.sh` |
| `reset.sh` / `.bat` | `scripts/deploy-and-run/reset.sh` — a faster, narrower alternative to `deploy-and-run.sh --reset`: only truncates tables (~1s, containers/volumes stay intact) vs. `--reset`'s full container+volume wipe and rebuild (~7-10 min) |
| `clean.bat` | self-contained (no delegation) |
| `pull-logs.bat` | self-contained (no delegation) |
| `collect-code.bat` | self-contained (no delegation) |
| `claude.bat` | self-contained (no delegation) |
| `run-all-tests.sh` / `.bat` | `scripts/run-all-tests/run.sh` — see `.claude/nav/adr-index.md` for the unit/integration pairing's own history |
| `ci.sh` / `.bat` | `scripts/ci/run.sh` — see `scripts/ci/README.md` and `scripts/ci/DECISIONS.md` |

---

## Folder structure

```
scripts/
  deploy-and-run/  -- deploy pipeline logic, Docker Compose files for local infrastructure,
                       database reset script
  build-and-test/  -- Docker build environment used by build-and-test.sh
  sonar/           -- SonarQube configuration and scanner
  ci/              -- isolated local CI runner
  run-all-tests/   -- run.sh for run-all-tests.sh
  utils/           -- shared library scripts sourced by multiple script-groups
```
