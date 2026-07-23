@echo off
setlocal

if "%~1"=="/?" goto :usage
if "%~1"=="--help" goto :usage

wsl bash /app/scripts/ci/run.sh %*
exit /b %ERRORLEVEL%

:usage
echo Usage:
echo   run.bat                              Most extensive run (unit+integration+e2e+sonar),
echo                                         backgrounded by default
echo   run.bat --unit --integration --e2e   Chosen stages only
echo   run.bat --all --sonar                Everything
echo   run.bat --foreground                 Block and stream output instead of backgrounding
echo   run.bat --keep-reports 5             Keep last N report dirs (default 3)
echo.
echo Requires: WSL2 + Docker Desktop with WSL integration enabled.
exit /b 0
