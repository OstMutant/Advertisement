@echo off
REM ---------------------------------------------------------------------------
REM Description: Removes build/test artifacts -- Maven target/ directories, Vaadin-generated
REM   frontend files, and test-report directories (build-and-test/integration-tests/playwright/
REM   sonar/ci). Native cmd.exe deletion (no WSL) -- reliably removes files a WSL `rm` can hit
REM   "Permission denied" on due to the Docker-Desktop-WSL2 bind-mounts quirk (see
REM   .claude/nav/adr-index.md). Each category wipes its whole report folder wholesale, not individual
REM   files inside it -- scripts\build-and-test\reports holds unit/integration/archunit output
REM   together, so --unit and --integration both wipe that same folder in full.
REM Usage: scripts\clean.bat [--build] [--unit] [--integration] [--playwright] [--sonar]
REM   (no flags)     clean everything listed below
REM   --build        Maven target/ dirs (every module) + Vaadin-generated frontend files +
REM                   scripts\ci\reports
REM   --unit         scripts\build-and-test\reports (whole) + scripts\logs\build-and-test (contents)
REM                   + scripts\logs\run-all-tests (whole)
REM   --integration  scripts\build-and-test\reports (whole) + integration-tests\reports +
REM                   scripts\logs\build-and-test (contents) + scripts\logs\run-all-tests (whole)
REM   --playwright   playwright\pw-report, playwright\screenshots,
REM                   scripts\logs\playwright (contents) + scripts\logs\run-all-tests (whole)
REM   --sonar        scripts\sonar\report
REM   Flags combine -- e.g. `--unit --integration` cleans only those two.
REM Uses: cmd.exe (rmdir/del only, no external tools).
REM Env: None.
REM Input: repo source -- a fixed list of module target/ dirs, frontend-generated paths, and
REM   report directories.
REM Outputs: deletes each listed path if present, printing "Removed <path>" for each one actually
REM   removed.
REM Returns: 0 always.
REM ---------------------------------------------------------------------------
setlocal enabledelayedexpansion
cd /d "%~dp0.."

set ROOT=%CD%\

REM scripts\logs\ is the shared parent every *.sh script's own docker cp destination lives under
REM (scripts\logs\build-and-test\, \playwright\, \sonar\, ...). Created here, once, natively, so it
REM always exists before any of those scripts run -- a docker cp destination missing two directory
REM levels at once (this parent + its own subfolder) has been confirmed to fail docker cp's own
REM auto-create, unlike a single missing level. clean.bat runs before every .bat entry point
REM delegates to WSL, so this one line covers all of them without repeating it per file.
if not exist "%ROOT%scripts\logs" mkdir "%ROOT%scripts\logs"

set DO_BUILD=
set DO_UNIT=
set DO_INTEGRATION=
set DO_PLAYWRIGHT=
set DO_SONAR=
set ANY_FLAG=

:parse
if "%~1"=="" goto afterparse
if /i "%~1"=="--build"       (set DO_BUILD=1&       set ANY_FLAG=1)
if /i "%~1"=="--unit"        (set DO_UNIT=1&        set ANY_FLAG=1)
if /i "%~1"=="--integration" (set DO_INTEGRATION=1& set ANY_FLAG=1)
if /i "%~1"=="--playwright"  (set DO_PLAYWRIGHT=1&  set ANY_FLAG=1)
if /i "%~1"=="--sonar"       (set DO_SONAR=1&       set ANY_FLAG=1)
shift
goto parse
:afterparse

if not defined ANY_FLAG (
    set DO_BUILD=1
    set DO_UNIT=1
    set DO_INTEGRATION=1
    set DO_PLAYWRIGHT=1
    set DO_SONAR=1
)

if defined DO_BUILD (
    echo Cleaning Maven build artifacts...
    for %%d in (
        marketplace-app\target
        platform-commons\target
        attachment-spring-boot-starter\target
        audit-spring-boot-starter\target
        query-lib\target
        user-spring-boot-starter\target
        advertisement-spring-boot-starter\target
        taxon-spring-boot-starter\target
        provider-profile-spring-boot-starter\target
        marketplace-orchestrator\target
        integration-tests\target
    ) do (
        if exist "%ROOT%%%d" (
            rmdir /s /q "%ROOT%%%d"
            echo   Removed %%d
        )
    )

    echo Cleaning Vaadin frontend generated files...
    for %%f in (
        marketplace-app\src\main\frontend\generated
        marketplace-app\node_modules
        marketplace-app\package.json
        marketplace-app\package-lock.json
        marketplace-app\tsconfig.json
        marketplace-app\types.d.ts
        marketplace-app\vite.config.ts
        marketplace-app\vite.generated.ts
        marketplace-app\src\main\frontend\index.html
        marketplace-app\src\main\bundles
    ) do (
        if exist "%ROOT%%%f" (
            if exist "%ROOT%%%f\" (
                rmdir /s /q "%ROOT%%%f"
            ) else (
                del /q "%ROOT%%%f"
            )
            echo   Removed %%f
        )
    )

    if exist "%ROOT%scripts\ci\reports" (
        rmdir /s /q "%ROOT%scripts\ci\reports"
        echo   Removed scripts\ci\reports
    )
)

REM scripts\logs\build-and-test and scripts\logs\playwright are populated by build-and-test/run.sh
REM and playwright/run.sh's own WSL-side `docker cp` (not a native .bat step like
REM scripts\logs\run-all-tests gets -- see .claude/nav/adr-index.md on why that copy is only
REM intermittently reliable, not deterministically broken). Contents wiped (del, not rmdir) rather
REM than recreating the directory via native mkdir -- consistent with the same caution
REM scripts\logs\run-all-tests used before it moved to a native .bat step.
if defined DO_UNIT (
    echo Cleaning build-and-test reports...
    if exist "%ROOT%scripts\build-and-test\reports" (
        rmdir /s /q "%ROOT%scripts\build-and-test\reports"
        echo   Removed scripts\build-and-test\reports
    )
    if exist "%ROOT%scripts\logs\build-and-test" (
        del /s /q "%ROOT%scripts\logs\build-and-test\*.*" >nul 2>&1
        echo   Removed scripts\logs\build-and-test contents
    )
)

if defined DO_INTEGRATION (
    if not defined DO_UNIT (
        echo Cleaning build-and-test reports...
        if exist "%ROOT%scripts\build-and-test\reports" (
            rmdir /s /q "%ROOT%scripts\build-and-test\reports"
            echo   Removed scripts\build-and-test\reports
        )
        if exist "%ROOT%scripts\logs\build-and-test" (
            del /s /q "%ROOT%scripts\logs\build-and-test\*.*" >nul 2>&1
            echo   Removed scripts\logs\build-and-test contents
        )
    )
    if exist "%ROOT%integration-tests\reports" (
        rmdir /s /q "%ROOT%integration-tests\reports"
        echo   Removed integration-tests\reports
    )
    REM scripts\logs\integration-tests populated by integration-tests/run.sh's own raw bash
    REM `mkdir -p` (no docker cp fallback here at all, unlike the other scripts' logs -- this one
    REM runs mvn directly on the host, no container involved), so contents-only wipe, never rmdir.
    if exist "%ROOT%scripts\logs\integration-tests" (
        del /q "%ROOT%scripts\logs\integration-tests\*.*" >nul 2>&1
        echo   Removed scripts\logs\integration-tests contents
    )
)

if defined DO_PLAYWRIGHT (
    echo Cleaning Playwright artifacts...
    for %%d in (playwright\pw-report playwright\screenshots) do (
        if exist "%ROOT%%%d" (
            rmdir /s /q "%ROOT%%%d"
            echo   Removed %%d
        )
    )
    if exist "%ROOT%scripts\logs\playwright" (
        del /q "%ROOT%scripts\logs\playwright\*.*" >nul 2>&1
        echo   Removed scripts\logs\playwright contents
    )
)

REM scripts\logs\run-all-tests holds orchestrator.log/build-and-test.log/playwright.log --
REM run-all-tests.bat's own native copy step (docker cp, after run-all-tests/run.sh returns)
REM populates this, not run-all-tests/run.sh itself (see that file's own header for why). Wiped
REM wholesale, same as every other report folder above -- this one is safe to rmdir (not just
REM del) because run-all-tests.bat's own native `mkdir` recreates it, and native cmd.exe mkdir on
REM this path is not subject to the WSL bind-mounts issue the rest of this comment block exists
REM to work around. Fires on any of the three flags that feed it (--unit/--integration/
REM --playwright), guarded so it only actually runs once even when several are passed together.
if defined DO_UNIT (set CLEAN_RAT=1)
if defined DO_INTEGRATION (set CLEAN_RAT=1)
if defined DO_PLAYWRIGHT (set CLEAN_RAT=1)
if defined CLEAN_RAT (
    if exist "%ROOT%scripts\logs\run-all-tests" (
        rmdir /s /q "%ROOT%scripts\logs\run-all-tests"
        echo   Removed scripts\logs\run-all-tests
    )
)

if defined DO_SONAR (
    echo Cleaning Sonar report...
    if exist "%ROOT%scripts\sonar\report" (
        rmdir /s /q "%ROOT%scripts\sonar\report"
        echo   Removed scripts\sonar\report
    )
    REM scripts\logs\sonar populated by sonar/run.sh's own WSL-side docker cp (same
    REM intermittent-reliability status as scripts\logs\build-and-test/playwright) -- contents
    REM wiped, not the directory itself.
    if exist "%ROOT%scripts\logs\sonar" (
        del /q "%ROOT%scripts\logs\sonar\*.*" >nul 2>&1
        echo   Removed scripts\logs\sonar contents
    )
)

echo Done.
endlocal
