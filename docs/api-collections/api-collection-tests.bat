@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to api-collection-tests.sh (this file's own
REM   sibling) via WSL. All real logic lives there -- this file only forwards arguments.
REM Usage: same as api-collection-tests.sh.
REM Uses: WSL (wsl --cd + bash -l against this file's own sibling api-collection-tests.sh).
REM Env: same as api-collection-tests.sh.
REM Input: same as api-collection-tests.sh.
REM Outputs: same as api-collection-tests.sh.
REM Returns: same as api-collection-tests.sh.
REM ---------------------------------------------------------------------------

wsl --cd "%~dp0" bash -l api-collection-tests.sh %*
