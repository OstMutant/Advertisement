@echo off
setlocal

if "%~1"=="/?" goto :usage
if "%~1"=="--help" goto :usage

wsl bash /app/playwright/run.sh %*
exit /b %ERRORLEVEL%

:usage
echo Usage:
echo   run.bat                            Run all e2e tests
echo   run.bat e2e                        Run e2e specs (clean DB, skips spec 05 seed)
echo   run.bat e2e --full                 Run e2e specs including spec 05 seed
echo   run.bat --ux                       All tests with screenshots
echo   run.bat e2e --full --ux            Full e2e suite with screenshots
echo   run.bat 01-marketplace-empty-flow  Run a single spec file by name
echo   run.bat e2e --grep "my test"       Run only tests matching name
echo.
echo Requires: WSL2 + Docker Desktop with WSL integration enabled.
exit /b 0
