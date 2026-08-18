@echo off
REM ── Header ──────────────────────────────────────────────────────────────────
REM Description: Windows entry point -- delegates to scripts/ci/run.sh via WSL.
REM Usage: same as scripts/ci/run.sh.
REM Uses: WSL (wsl bash scripts/ci/run.sh).
REM Env: same as scripts/ci/run.sh.
REM Input: same as scripts/ci/run.sh.
REM Outputs: same as scripts/ci/run.sh.
REM Returns: same as scripts/ci/run.sh.
REM ────────────────────────────────────────────────────────────────────────────
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0ci\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
