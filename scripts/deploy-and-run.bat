@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to scripts/deploy-and-run/run.sh via WSL.
REM Usage: same as scripts/deploy-and-run/run.sh.
REM Uses: WSL (wsl bash scripts/deploy-and-run/run.sh).
REM Env: same as scripts/deploy-and-run/run.sh.
REM Input: same as scripts/deploy-and-run/run.sh.
REM Outputs:
REM   Filtered console output (WSL/Git Bash): bash scripts/deploy-and-run.sh 2>&1 | tee /tmp/deploy.log | grep -E "BUILD|ERROR|Started|Waiting|ready|FAILED"
REM   Stream full app log after deploy: docker logs -f marketplace-app
REM Returns: same as scripts/deploy-and-run/run.sh.
REM ---------------------------------------------------------------------------
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0deploy-and-run\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
