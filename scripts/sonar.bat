@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to scripts/sonar/run.sh via WSL.
REM Usage: same as scripts/sonar/run.sh.
REM Uses: WSL (wsl bash scripts/sonar/run.sh).
REM Env: same as scripts/sonar/run.sh.
REM Input: same as scripts/sonar/run.sh.
REM Outputs: same as scripts/sonar/run.sh.
REM Returns: same as scripts/sonar/run.sh.
REM ---------------------------------------------------------------------------
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0sonar\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
