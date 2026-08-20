@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to playwright/run.sh via WSL.
REM Usage: same as playwright/run.sh.
REM Uses: WSL (wsl bash playwright/run.sh).
REM Env: same as playwright/run.sh.
REM Input: same as playwright/run.sh.
REM Outputs: same as playwright/run.sh.
REM Returns: same as playwright/run.sh.
REM ---------------------------------------------------------------------------
wsl bash /app/playwright/run.sh %*
