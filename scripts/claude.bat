@echo off
REM ---------------------------------------------------------------------------
REM Description: Starts a Docker container for the Claude Code dev environment -- mounts the
REM   current directory, an isolated per-login auth config folder, the Maven cache, and the
REM   Docker socket, then runs the claude-j25-dev image with --network host. Removes and recreates
REM   the claude-dev container on every run.
REM Usage: scripts\claude.bat your.email@gmail.com [--update] [claude args...]
REM   your.email@gmail.com   required -- derives an isolated per-login auth config folder
REM   --update                rebuild the claude-j25-dev image from Dockerfile.ai before starting
REM   [claude args...]        forwarded to the claude-dev container's own entrypoint
REM Uses: docker.
REM Env: USERPROFILE (Windows) -- used to derive the isolated per-login config folder path, not
REM   set by this script itself.
REM Input: Dockerfile.ai (only with --update).
REM Outputs: running claude-dev container; with --update, rebuilds the claude-j25-dev image;
REM   creates %USERPROFILE%\.claude-config-<login> if missing.
REM Returns: 0 on success; non-zero if no login argument was given, or --update's Docker build
REM   fails.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0.."

:: Get login (email) from first argument
set LOGIN=%1
if "%LOGIN%"=="" (
    echo Error: Please provide your login.
    echo Usage: claude.bat your.email@gmail.com [--update] [claude args...]
    exit /b 1
)

:: Parse remaining args -- strip --update, pass everything else through
set DO_UPDATE=0
set EXTRA_ARGS=
shift
:parse_args
if "%1"=="" goto done_args
if "%1"=="--update" (
    set DO_UPDATE=1
) else (
    set "EXTRA_ARGS=%EXTRA_ARGS% %1"
)
shift
goto parse_args
:done_args

:: Rebuild image if --update was requested
if "%DO_UPDATE%"=="1" (
    echo ===================================================
    echo   Rebuilding claude-j25-dev image...
    echo ===================================================
    docker build -f Dockerfile.ai -t claude-j25-dev .
    if errorlevel 1 (
        echo Error: Docker build failed.
        exit /b 1
    )
)

echo ===================================================
echo   Starting Claude Code for login: %LOGIN%
echo   Context and history are shared from /app/.claude
echo ===================================================

:: Create an isolated config folder specifically for this login
set "CONFIG_DIR=%USERPROFILE%\.claude-config-%LOGIN%"
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"

:: Run the container
:: 1. Mount current directory (Shared Project Context)
:: 2. Mount isolated auth config folder (Specific to %LOGIN%)
:: 3. Mount Maven cache
docker rm -f claude-dev >nul 2>&1
docker run -it --rm --name claude-dev ^
  -v "%CD%:/app" ^
  -v "%CONFIG_DIR%:/root/.claude" ^
  -v "%USERPROFILE%\.m2:/root/.m2" ^
  -v //var/run/docker.sock:/var/run/docker.sock ^
  --network host ^
  claude-j25-dev%EXTRA_ARGS%
