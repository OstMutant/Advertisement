@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- runs clean.bat --unit --integration natively
REM   (cmd.exe, before WSL is involved -- reliably clears stale build/test reports a WSL `rm` can
REM   hit "Permission denied" on due to the Docker-Desktop-WSL2 bind-mounts quirk, see
REM   .claude/nav/adr-index.md), then delegates to scripts/build-and-test/run.sh via WSL.
REM Usage: same as scripts/build-and-test/run.sh.
REM Uses: cmd.exe (clean.bat --unit --integration), WSL (wsl bash scripts/build-and-test/run.sh).
REM Env: same as scripts/build-and-test/run.sh.
REM Input: same as scripts/build-and-test/run.sh.
REM Outputs: same as scripts/build-and-test/run.sh.
REM Returns: same as scripts/build-and-test/run.sh.
REM ---------------------------------------------------------------------------
call "%~dp0clean.bat" --unit --integration
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0build-and-test\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
