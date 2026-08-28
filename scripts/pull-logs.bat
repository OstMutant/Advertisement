@echo off
REM ---------------------------------------------------------------------------
REM Description: Pulls whatever logs/reports are currently present in the persistent, reused
REM   containers (run-all-tests-reports, pw-runner, sonar-scanner) onto the host, without running
REM   any tests -- useful right after a run whose own copy step was skipped or failed, or just to
REM   inspect the latest state on demand. Native cmd.exe `docker cp` (no WSL path translation
REM   involved) -- same reason run-all-tests.bat's own copy step is native instead of WSL-side, see
REM   .claude/nav/adr-index.md. Silently skips any container that isn't currently running (2>nul) --
REM   not every container is expected to exist at any given moment. Does not cover build-and-test
REM   (its container is normally removed automatically right after a successful run -- nothing left
REM   to pull) or integration-tests/run.sh (runs mvn directly on the host, no container at all).
REM Usage: scripts\pull-logs.bat
REM   (no flags)
REM Uses: cmd.exe (docker cp only).
REM Env: None.
REM Input: whatever is currently in run-all-tests-reports/pw-runner/sonar-scanner's own
REM   /reports/... paths.
REM Outputs: scripts\logs\run-all-tests\, playwright\pw-report\ + scripts\logs\playwright\,
REM   scripts\logs\sonar\ -- each only if its source container is currently running.
REM Returns: 0 always (missing containers are skipped, not treated as failures).
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0.."
set ROOT=%CD%\

if not exist "%ROOT%scripts\logs\run-all-tests" mkdir "%ROOT%scripts\logs\run-all-tests"
docker cp run-all-tests-reports:/reports/run-all-tests/. "%ROOT%scripts\logs\run-all-tests\" 2>nul

if not exist "%ROOT%scripts\logs\playwright" mkdir "%ROOT%scripts\logs\playwright"
docker cp pw-runner:/reports/playwright/. "%ROOT%playwright\pw-report\" 2>nul
docker cp pw-runner:/reports/playwright-log/. "%ROOT%scripts\logs\playwright\" 2>nul

if not exist "%ROOT%scripts\logs\sonar" mkdir "%ROOT%scripts\logs\sonar"
docker cp sonar-scanner:/reports/sonar/. "%ROOT%scripts\logs\sonar\" 2>nul

echo Done.
endlocal
