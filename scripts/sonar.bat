@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- runs clean.bat --sonar natively (cmd.exe, before WSL is
REM   involved -- reliably clears the stale local report a WSL `rm` can hit "Permission denied"
REM   on due to the Docker-Desktop-WSL2 bind-mounts quirk, see .claude/nav/adr-index.md), then
REM   delegates to scripts/sonar/run.sh via WSL.
REM Usage: same as scripts/sonar/run.sh.
REM Uses: cmd.exe (clean.bat --sonar), WSL (wsl bash scripts/sonar/run.sh).
REM Env: same as scripts/sonar/run.sh.
REM Input: same as scripts/sonar/run.sh.
REM Outputs: same as scripts/sonar/run.sh.
REM Returns: same as scripts/sonar/run.sh.
REM ---------------------------------------------------------------------------
call "%~dp0clean.bat" --sonar
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0sonar\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
