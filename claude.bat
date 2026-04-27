@echo off
setlocal

:: Get login (email) from argument
set LOGIN=%1

:: Require login to be provided
if "%LOGIN%"=="" (
    echo Error: Please provide your login.
    echo Usage: claude.bat your.email@gmail.com
    exit /b 1
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
docker run -it --rm ^
  -v "%CD%:/app" ^
  -v "%CONFIG_DIR%:/root/.claude" ^
  -v "%USERPROFILE%\.m2:/root/.m2" ^
  -v //var/run/docker.sock:/var/run/docker.sock ^
  --network host ^
  claude-j25-dev