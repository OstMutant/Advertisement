@echo off
for /f "delims=" %%i in ('wsl wslpath -u "%~dp0build\run.sh"') do set SCRIPT=%%i
wsl bash "%SCRIPT%" %*
