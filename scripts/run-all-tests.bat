@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to scripts/run-all-tests/run.sh via WSL.
REM Usage: same as scripts/run-all-tests/run.sh.
REM Uses: WSL (wsl bash scripts/run-all-tests/run.sh).
REM Env: same as scripts/run-all-tests/run.sh.
REM Input: same as scripts/run-all-tests/run.sh.
REM Outputs: same as scripts/run-all-tests/run.sh.
REM Returns: same as scripts/run-all-tests/run.sh.
REM ---------------------------------------------------------------------------
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0run-all-tests\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
