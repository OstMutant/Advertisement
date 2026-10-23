@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to run-flows.sh (this file's own sibling)
REM   via WSL. All real logic (the containerized Node run) lives there -- this file only
REM   forwards arguments.
REM Usage: same as run-flows.sh.
REM Uses: WSL (wsl --cd + bash -l against this file's own sibling run-flows.sh).
REM Env: same as run-flows.sh.
REM Input: same as run-flows.sh.
REM Outputs: same as run-flows.sh.
REM Returns: same as run-flows.sh.
REM ---------------------------------------------------------------------------

wsl --cd "%~dp0" bash -l run-flows.sh %*
