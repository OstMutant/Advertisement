@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- same real logic as architecture-doc.sh (this file's own
REM   sibling), run via WSL instead of delegating to that file directly.
REM Usage: same as architecture-doc.sh.
REM Uses: WSL (wsl bash scripts/*.sh in this file's own scripts/ subdirectory).
REM Env: same as architecture-doc.sh.
REM Input: same as architecture-doc.sh.
REM Outputs: same as architecture-doc.sh.
REM Returns: same as architecture-doc.sh.
REM ---------------------------------------------------------------------------
set NO_CHECK=false
set NO_SCREENSHOT=false
for %%A in (%*) do (
  if "%%A"=="--no-check" set NO_CHECK=true
  if "%%A"=="--no-screenshot" set NO_SCREENSHOT=true
)

for /f "delims=" %%i in ('wsl wslpath -u "%~dp0scripts"') do set SCRIPTS_DIR=%%i

wsl bash "%SCRIPTS_DIR%/generate-architecture-model.sh"
if errorlevel 1 exit /b 1

if "%NO_CHECK%"=="false" (
  wsl bash "%SCRIPTS_DIR%/check-architecture-model-freshness.sh"
  if errorlevel 1 exit /b 1
)

if "%NO_SCREENSHOT%"=="false" (
  wsl bash "%SCRIPTS_DIR%/screenshot-architecture-map.sh"
)
