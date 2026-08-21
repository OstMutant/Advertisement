@echo off
REM ── Header ──────────────────────────────────────────────────────────────────
REM Description: Starts (or reuses) a Docker container for the Claude Code dev environment --
REM   mounts the current directory, an isolated per-login auth config folder, the Maven cache, and
REM   the Docker socket, then runs the claude-j25-dev image with --network host. Self-contained,
REM   no WSL involved -- runs `docker` directly against Windows Docker Desktop's own CLI, same as
REM   the rest of this file always did. Reuses the existing claude-dev container via
REM   `docker exec` if one is already running; --recreate forces a fresh container instead.
REM Usage: scripts\claude.bat your.email@gmail.com [--update] [--recreate] [claude args...]
REM   your.email@gmail.com   required -- derives an isolated per-login auth config folder
REM   --update                rebuild the claude-j25-dev image from Dockerfile.ai before starting
REM   --recreate              force-remove and recreate claude-dev even if already running --
REM                           default behavior reuses a running container instead
REM   [claude args...]        forwarded to the claude entrypoint (a new process either way)
REM Uses: docker.
REM Env: USERPROFILE (Windows) -- used to derive the isolated per-login config folder path, not
REM   set by this script itself.
REM Input: Dockerfile.ai (only with --update).
REM Outputs: running claude-dev container (new or reused); with --update, rebuilds the
REM   claude-j25-dev image; creates %USERPROFILE%\.claude-config-<login> if missing.
REM Returns: 0 on success; non-zero if no login argument was given, or --update's Docker build
REM   fails.
REM ────────────────────────────────────────────────────────────────────────────
setlocal
cd /d "%~dp0.."

:: Get login (email) from first argument
set LOGIN=%1
if "%LOGIN%"=="" (
    echo Error: Please provide your login.
    echo Usage: claude.bat your.email@gmail.com [--update] [--recreate] [claude args...]
    exit /b 1
)

:: Parse remaining args -- strip --update/--recreate, pass everything else through
set DO_UPDATE=0
set RECREATE=0
set EXTRA_ARGS=
shift
:parse_args
if "%1"=="" goto done_args
if "%1"=="--update" (
    set DO_UPDATE=1
) else if "%1"=="--recreate" (
    set RECREATE=1
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

:: Create an isolated config folder specifically for this login
set "CONFIG_DIR=%USERPROFILE%\.claude-config-%LOGIN%"
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"

:: Reuse a running container unless --recreate was passed
set RUNNING=
for /f %%i in ('docker ps --filter "name=^claude-dev$" --filter "status=running" -q') do set RUNNING=%%i

if not "%RUNNING%"=="" if "%RECREATE%"=="0" (
    echo ===================================================
    echo   Reusing running claude-dev container for login: %LOGIN%
    echo ===================================================
    docker exec -it claude-dev claude%EXTRA_ARGS%
    goto :eof
)

echo ===================================================
echo   Starting Claude Code for login: %LOGIN%
echo   Context and history are shared from /app/.claude
echo ===================================================

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
