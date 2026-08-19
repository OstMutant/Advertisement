@echo off
REM Description: Windows entry point -- delegates to reset.sh via bash.
REM Usage: same as reset.sh.
REM Uses: bash reset.sh.
REM Env: same as reset.sh.
REM Input: same as reset.sh.
REM Outputs: same as reset.sh.
REM Returns: same as reset.sh.
bash scripts/deploy-and-run/reset.sh %*
