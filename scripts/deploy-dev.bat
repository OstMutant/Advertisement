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
REM Filtered output (key milestones only) — run from WSL or Git Bash:
REM   bash scripts/deploy-dev.sh 2>&1 | tee /tmp/deploy-dev.log | grep -E "BUILD|ERROR|Deploying|Restarting|Started"
REM
REM Stream full app log after deploy:
REM   docker logs -f marketplace-app
bash "%~dp0deploy-dev.sh"
