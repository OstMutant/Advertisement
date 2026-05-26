@echo off
REM Fast dev deploy: builds JAR locally and hot-swaps it into the running container.
REM Typically ~3-4 min vs ~7-10 min for a full prod rebuild.
REM
REM Usage:
REM   scripts\deploy-dev.bat
REM
REM Requires: infra (DB, MinIO) and marketplace-app container already running.
REM Run scripts\deploy.bat once first if the container does not exist yet.
REM
REM To write full Maven log to file instead of console:
REM   bash scripts/deploy-dev.sh --file   (log: /tmp/deploy-dev.log)
REM
REM Stream full app log after deploy:
REM   docker logs -f marketplace-app
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0deploy-dev.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%"
