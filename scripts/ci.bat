@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- runs clean.bat --unit --integration --playwright --sonar
REM   natively (cmd.exe, before WSL is involved -- reliably clears stale reports a WSL `rm` can hit
REM   "Permission denied" on due to the Docker-Desktop-WSL2 bind-mounts quirk, see
REM   docs/ai/adr-index.md; deliberately excludes --build, which wipes Maven target/ and Vaadin
REM   node_modules -- CI already runs in its own isolated ci-runner container with its own cache
REM   volumes, so wiping the host's own build cache here would only slow down interleaved local
REM   dev builds for no benefit), then delegates to scripts/ci/run.sh via WSL.
REM Usage: same as scripts/ci/run.sh.
REM Uses: cmd.exe (clean.bat --unit --integration --playwright --sonar), WSL (wsl bash scripts/ci/run.sh).
REM Env: same as scripts/ci/run.sh.
REM Input: same as scripts/ci/run.sh.
REM Outputs: same as scripts/ci/run.sh.
REM Returns: same as scripts/ci/run.sh.
REM ---------------------------------------------------------------------------
call "%~dp0clean.bat" --unit --integration --playwright --sonar
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0ci\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
