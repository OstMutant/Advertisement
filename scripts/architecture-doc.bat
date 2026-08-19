@echo off
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
