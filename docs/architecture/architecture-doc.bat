@echo off
REM ---------------------------------------------------------------------------
REM Description: Windows entry point -- delegates to architecture-doc.sh (this file's own
REM   sibling) via WSL. All real logic (the containerized generation pipeline) lives there now --
REM   this file only forwards flags, it never invokes generate-architecture-model.sh or any other
REM   sub-script directly.
REM Usage: same as architecture-doc.sh.
REM Uses: WSL (wsl --cd + bash -l against this file's own sibling architecture-doc.sh).
REM Env: same as architecture-doc.sh.
REM Input: same as architecture-doc.sh.
REM Outputs: same as architecture-doc.sh.
REM Returns: same as architecture-doc.sh.
REM ---------------------------------------------------------------------------

REM `wsl --cd <windows-path>` lets wsl.exe translate the Windows path itself in one atomic step --
REM no separate `wsl wslpath -u` call, no absolute Linux path built and passed back in. Chosen
REM after a real, confirmed-live Docker-Desktop-WSL2 issue where a plain `wsl wslpath -u`/`wsl bash
REM <absolute-path>` invocation resolved through Docker Desktop's internal
REM /mnt/wsl/docker-desktop-bind-mounts/... bind-mount proxy instead of the normal drvfs path --
REM this alone did not fully avoid that proxy on every machine, which is exactly why the real fix
REM moved into architecture-doc.sh itself (a containerized pipeline, no host-side WSL path
REM dependency for the actual generation work at all -- this file only needs WSL to reach the
REM entry point script, everything past that runs inside a disposable Docker container).

wsl --cd "%~dp0" bash -l architecture-doc.sh %*
