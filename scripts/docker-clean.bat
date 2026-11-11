@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to scripts/docker-clean.sh via WSL.
REM Usage: same as scripts/docker-clean.sh.
REM Uses: WSL (wsl wslpath, wsl bash scripts/docker-clean.sh).
REM Env: same as scripts/docker-clean.sh.
REM Input: same as scripts/docker-clean.sh.
REM Outputs: same as scripts/docker-clean.sh.
REM Returns: same as scripts/docker-clean.sh.
REM ---------------------------------------------------------------------------
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0docker-clean.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
