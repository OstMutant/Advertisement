@echo off
setlocal

if "%~1"=="/?" goto :usage
if "%~1"=="--help" goto :usage

wsl bash /app/playwright/run.sh %*
exit /b %ERRORLEVEL%

:usage
echo Usage:
echo   run.bat                   Run all tests
echo   run.bat smoke             Run smoke.spec.js
echo   run.bat smoke --ux        Run with screenshots
echo   run.bat --ux              All tests with screenshots
echo.
echo Requires: WSL2 + Docker Desktop with WSL integration enabled.
exit /b 0
