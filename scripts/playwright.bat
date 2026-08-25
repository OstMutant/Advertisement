@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- runs clean.bat --playwright natively (cmd.exe, before WSL
REM   is involved -- reliably clears stale Playwright artifacts a WSL `rm` can hit "Permission
REM   denied" on due to the Docker-Desktop-WSL2 bind-mounts quirk, see .claude/nav/adr-index.md), then
REM   delegates to playwright/run.sh via WSL.
REM Usage: same as playwright/run.sh.
REM Uses: cmd.exe (clean.bat --playwright), WSL (wsl bash playwright/run.sh).
REM Env: same as playwright/run.sh.
REM Input: same as playwright/run.sh.
REM Outputs: same as playwright/run.sh.
REM Returns: same as playwright/run.sh.
REM ---------------------------------------------------------------------------
call "%~dp0clean.bat" --playwright
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0..\playwright\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
