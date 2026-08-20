@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to scripts/deploy-and-run/reset.sh via WSL.
REM Usage: same as scripts/deploy-and-run/reset.sh.
REM Uses: WSL (wsl bash scripts/deploy-and-run/reset.sh).
REM Env: same as scripts/deploy-and-run/reset.sh.
REM Input: same as scripts/deploy-and-run/reset.sh.
REM Outputs: same as scripts/deploy-and-run/reset.sh.
REM Returns: same as scripts/deploy-and-run/reset.sh.
REM ---------------------------------------------------------------------------
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0deploy-and-run\reset.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
