@echo off
REM Fast dev deploy: builds JAR locally and hot-swaps it into the running container.
REM Typically ~3-4 min vs ~7-10 min for a full prod rebuild.
REM
REM Usage:
REM   scripts\deploy-dev.bat
REM   scripts\deploy-dev.bat --reset-cache   -- clear Maven cache before building
REM   scripts\deploy-dev.bat --reset-db      -- truncate app tables before hot-swap restart
REM   scripts\deploy-dev.bat --file          -- filtered output + full log to /tmp/deploy-dev.log
REM
REM Requires: infra (DB, MinIO) and marketplace-app container already running.
REM Run scripts\deploy.bat once first if the container does not exist yet.
REM
REM Stream full app log after deploy:
REM   docker logs -f marketplace-app
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0deploy-dev.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
