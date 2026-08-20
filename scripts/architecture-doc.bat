@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- same real logic as scripts/architecture-doc.sh, run via
REM   WSL instead of delegating to that file directly.
REM Usage: same as scripts/architecture-doc.sh.
REM Uses: WSL (wsl bash docs/architecture/scripts/*.sh).
REM Env: same as scripts/architecture-doc.sh.
REM Input: same as scripts/architecture-doc.sh.
REM Outputs: same as scripts/architecture-doc.sh.
REM Returns: same as scripts/architecture-doc.sh.
REM ---------------------------------------------------------------------------
set NO_CHECK=false
set NO_SCREENSHOT=false
for %%A in (%*) do (
  if "%%A"=="--no-check" set NO_CHECK=true
  if "%%A"=="--no-screenshot" set NO_SCREENSHOT=true
)

wsl bash /app/docs/architecture/scripts/generate-architecture-model.sh
if errorlevel 1 exit /b 1

if "%NO_CHECK%"=="false" (
  wsl bash /app/docs/architecture/scripts/check-architecture-model-freshness.sh
  if errorlevel 1 exit /b 1
)

if "%NO_SCREENSHOT%"=="false" (
  wsl bash /app/docs/architecture/scripts/screenshot-architecture-map.sh
)
