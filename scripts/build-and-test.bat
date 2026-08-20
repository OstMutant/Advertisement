@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to scripts/build-and-test/run.sh via WSL.
REM Usage: same as scripts/build-and-test/run.sh.
REM Uses: WSL (wsl bash scripts/build-and-test/run.sh).
REM Env: same as scripts/build-and-test/run.sh.
REM Input: same as scripts/build-and-test/run.sh.
REM Outputs: same as scripts/build-and-test/run.sh.
REM Returns: same as scripts/build-and-test/run.sh.
REM ---------------------------------------------------------------------------
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0build-and-test\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
