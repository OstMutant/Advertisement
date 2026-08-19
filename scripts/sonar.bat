@echo off
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0sonar\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
