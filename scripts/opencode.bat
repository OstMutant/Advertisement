@echo off
REM --- Header ---------------------------------------------------------------
REM Description: Starts (or reuses) a Docker container for the opencode dev environment --
REM   mounts the current directory, an isolated per-login config folder (covering both of
REM   opencode's own XDG data/config directories), the Maven cache, and the Docker socket, then
REM   runs the opencode-j25-dev image with --network host. Self-contained, no WSL involved -- runs
REM   `docker` directly against Windows Docker Desktop's own CLI, same as scripts\claude.bat.
REM   Reuses the existing opencode-dev container via `docker exec` if one is already running;
REM   --recreate forces a fresh container instead.
REM Usage: scripts\opencode.bat your.email@gmail.com [--update] [--recreate] [opencode args...]
REM   your.email@gmail.com   required -- derives an isolated per-login config folder
REM   --update                rebuild the opencode-j25-dev image from Dockerfile.opencode before
REM                           starting
REM   --recreate              force-remove and recreate opencode-dev even if already running --
REM                           default behavior reuses a running container instead
REM   [opencode args...]      forwarded to the opencode entrypoint (a new process either way)
REM Uses: docker.
REM Env: USERPROFILE (Windows) -- used to derive the isolated per-login config folder path, not
REM   set by this script itself.
REM Input: Dockerfile.opencode (only with --update).
REM Outputs: running opencode-dev container (new or reused); with --update, rebuilds the
REM   opencode-j25-dev image; creates %USERPROFILE%\.opencode-config-<login> if missing.
REM Returns: 0 on success; non-zero if no login argument was given, or --update's Docker build
REM   fails.
REM ----------------------------------------------------------------------------
setlocal
cd /d "%~dp0.."

:: Get login (email) from first argument
set LOGIN=%1
if "%LOGIN%"=="" (
    echo Error: Please provide your login.
    echo Usage: opencode.bat your.email@gmail.com [--update] [--recreate] [opencode args...]
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
    echo   Rebuilding opencode-j25-dev image...
    echo ===================================================
    docker build -f Dockerfile.opencode -t opencode-j25-dev .
    if errorlevel 1 (
        echo Error: Docker build failed.
        exit /b 1
    )
)

:: Create an isolated config folder specifically for this login
set "CONFIG_DIR=%USERPROFILE%\.opencode-config-%LOGIN%"
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"

:: Reuse a running container unless --recreate was passed
set RUNNING=
for /f %%i in ('docker ps --filter "name=^opencode-dev$" --filter "status=running" -q') do set RUNNING=%%i

if not "%RUNNING%"=="" if "%RECREATE%"=="0" (
    echo ===================================================
    echo   Reusing running opencode-dev container for login: %LOGIN%
    echo ===================================================
    docker exec -it opencode-dev opencode%EXTRA_ARGS%
    goto :eof
)

echo ===================================================
echo   Starting opencode for login: %LOGIN%
echo   Context and history are shared from the mounted config folder
echo ===================================================

:: Run the container
:: 1. Mount current directory (Shared Project Context)
:: 2. Mount isolated per-login config folder, split into opencode's own XDG data/config subpaths
:: 3. Mount Maven cache
docker rm -f opencode-dev >nul 2>&1
docker run -it --rm --name opencode-dev ^
  -v "%CD%:/app" ^
  -v "%CONFIG_DIR%:/root/.opencode-home" ^
  -e XDG_DATA_HOME=/root/.opencode-home/data ^
  -e XDG_CONFIG_HOME=/root/.opencode-home/config ^
  -v "%USERPROFILE%\.m2:/root/.m2" ^
  -v //var/run/docker.sock:/var/run/docker.sock ^
  --network host ^
  opencode-j25-dev%EXTRA_ARGS%
