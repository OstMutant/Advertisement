@echo off
REM Full prod deploy: builds Docker image from scratch and starts all services on port 8081.
REM
REM Usage:
REM   scripts\deploy-and-run.bat                    -- full image rebuild + start
REM   scripts\deploy-and-run.bat --no-cache         -- force rebuild ignoring Docker layer cache
REM   scripts\deploy-and-run.bat --reset            -- wipe DB/MinIO volumes, then rebuild
REM   scripts\deploy-and-run.bat --restart-infra    -- restart infra containers only (no rebuild)
REM   scripts\deploy-and-run.bat --reset-only-db         -- truncate app tables before starting the app
REM
REM Filtered output (key milestones only) — run from WSL or Git Bash:
REM   bash scripts/deploy-and-run.sh 2>&1 | tee /tmp/deploy.log | grep -E "BUILD|ERROR|Started|Waiting|ready|FAILED"
REM
REM Stream full app log after deploy:
REM   docker logs -f marketplace-app
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0deploy-and-run\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
