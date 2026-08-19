@echo off
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0run-all-tests\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
