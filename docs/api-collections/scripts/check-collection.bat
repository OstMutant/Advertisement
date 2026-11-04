@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to check-collection.sh (this file's own
REM   sibling) via WSL. All real logic (the containerized Node run) lives there -- this file
REM   only forwards arguments.
REM Usage: same as check-collection.sh.
REM Uses: WSL (wsl --cd + bash -l against this file's own sibling check-collection.sh).
REM Env: same as check-collection.sh.
REM Input: same as check-collection.sh.
REM Outputs: same as check-collection.sh.
REM Returns: same as check-collection.sh.
REM ---------------------------------------------------------------------------

wsl --cd "%~dp0" bash -l check-collection.sh %*
