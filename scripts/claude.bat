@echo off
setlocal
cd /d "%~dp0.."

:: Get login (email) from first argument
set LOGIN=%1
if "%LOGIN%"=="" (
    echo Error: Please provide your login.
    echo Usage: claude.bat your.email@gmail.com [--update] [claude args...]
    exit /b 1
)

:: Parse remaining args — strip --update, pass everything else through
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
