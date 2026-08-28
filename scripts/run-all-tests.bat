@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- runs clean.bat --unit --integration --playwright natively
REM   (cmd.exe, before WSL is involved -- reliably clears stale reports/logs a WSL `rm` can hit
REM   "Permission denied" on due to the Docker-Desktop-WSL2 bind-mounts quirk, see
REM   .claude/nav/adr-index.md), delegates to scripts/run-all-tests/run.sh via WSL, then -- also
REM   natively, after WSL returns -- copies that run's logs out of the run-all-tests-reports
REM   container into scripts\logs\run-all-tests\ and removes the container. This last step exists
REM   because a native `docker cp`/`docker rm` (no WSL path translation involved) does not hit the
REM   same bind-mounts issue a WSL-side `docker cp` does -- run-all-tests/run.sh itself no longer
REM   attempts this copy at all, see that file's own header.
REM Usage: same as scripts/run-all-tests/run.sh.
REM Uses: cmd.exe (clean.bat --unit --integration --playwright, docker cp, docker rm), WSL
REM   (wsl bash scripts/run-all-tests/run.sh).
REM Env: same as scripts/run-all-tests/run.sh.
REM Input: same as scripts/run-all-tests/run.sh.
REM Outputs: scripts\logs\run-all-tests\orchestrator.log, build-and-test.log, playwright.log --
REM   see scripts/run-all-tests/run.sh's own header for what each contains. Otherwise same as
REM   scripts/run-all-tests/run.sh.
REM Returns: same as scripts/run-all-tests/run.sh (this file's own copy/cleanup step never changes
REM   the exit code that script already produced).
REM ---------------------------------------------------------------------------
call "%~dp0clean.bat" --unit --integration --playwright
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0run-all-tests\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
set RUN_EXIT=%ERRORLEVEL%

if not exist "%~dp0logs\run-all-tests" mkdir "%~dp0logs\run-all-tests"
docker cp run-all-tests-reports:/reports/run-all-tests/orchestrator.log "%~dp0logs\run-all-tests\orchestrator.log" >nul 2>&1
docker cp run-all-tests-reports:/reports/run-all-tests/build-and-test.log "%~dp0logs\run-all-tests\build-and-test.log" >nul 2>&1
docker cp run-all-tests-reports:/reports/run-all-tests/playwright.log "%~dp0logs\run-all-tests\playwright.log" >nul 2>&1
docker rm -f run-all-tests-reports >nul 2>&1

exit /b %RUN_EXIT%
