@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- runs clean.bat --unit --integration --playwright
REM   natively (cmd.exe, before WSL is involved -- reliably clears stale reports/logs a WSL `rm`
REM   can hit "Permission denied" on due to the Docker-Desktop-WSL2 bind-mounts quirk, see
REM   docs/ai/adr-index.md), then delegates to scripts/run-all-tests/run.sh via WSL.
REM Usage: same as scripts/run-all-tests/run.sh.
REM Uses: cmd.exe (clean.bat --unit --integration --playwright), WSL (wsl bash scripts/run-all-tests/run.sh).
REM Env: same as scripts/run-all-tests/run.sh.
REM Input: same as scripts/run-all-tests/run.sh.
REM Outputs: same as scripts/run-all-tests/run.sh.
REM Returns: same as scripts/run-all-tests/run.sh.
REM ---------------------------------------------------------------------------
call "%~dp0clean.bat" --unit --integration --playwright
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0run-all-tests\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
