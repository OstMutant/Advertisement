@echo off
REM ---------------------------------------------------------------------------
REM Description: Removes build/test artifacts -- Maven target/ directories, Vaadin-generated
REM   frontend files, and test-report directories (build-and-test/integration-tests/playwright/
REM   sonar/ci). Native cmd.exe deletion (no WSL) -- reliably removes files a WSL `rm` can hit
REM   "Permission denied" on due to the Docker-Desktop-WSL2 bind-mounts quirk (see
REM   docs/ai/adr-index.md). Each category wipes its whole report folder wholesale, not individual
REM   files inside it -- scripts\build-and-test\reports holds unit/integration/archunit output
REM   together, so --unit and --integration both wipe that same folder in full.
REM Usage: scripts\clean.bat [--build] [--unit] [--integration] [--playwright] [--sonar]
REM   (no flags)     clean everything listed below
REM   --build        Maven target/ dirs (every module) + Vaadin-generated frontend files +
REM                   scripts\ci\reports
REM   --unit         scripts\build-and-test\reports (whole) + scripts\run-all-tests\reports\
REM                   build-and-test.log
REM   --integration  scripts\build-and-test\reports (whole) + integration-tests\reports
REM   --playwright   playwright\pw-report, playwright\screenshots,
REM                   scripts\run-all-tests\reports\playwright.log
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

if defined DO_UNIT (
    echo Cleaning build-and-test reports...
    if exist "%ROOT%scripts\build-and-test\reports" (
        rmdir /s /q "%ROOT%scripts\build-and-test\reports"
        echo   Removed scripts\build-and-test\reports
    )
    if exist "%ROOT%scripts\run-all-tests\reports\build-and-test.log" (
        del /q "%ROOT%scripts\run-all-tests\reports\build-and-test.log"
        echo   Removed scripts\run-all-tests\reports\build-and-test.log
    )
)

if defined DO_INTEGRATION (
    if not defined DO_UNIT (
        echo Cleaning build-and-test reports...
        if exist "%ROOT%scripts\build-and-test\reports" (
            rmdir /s /q "%ROOT%scripts\build-and-test\reports"
            echo   Removed scripts\build-and-test\reports
        )
    )
    if exist "%ROOT%integration-tests\reports" (
        rmdir /s /q "%ROOT%integration-tests\reports"
        echo   Removed integration-tests\reports
    )
)

if defined DO_PLAYWRIGHT (
    echo Cleaning Playwright artifacts...
    if exist "%ROOT%scripts\run-all-tests\reports\playwright.log" (
        del /q "%ROOT%scripts\run-all-tests\reports\playwright.log"
        echo   Removed scripts\run-all-tests\reports\playwright.log
    )
    for %%d in (playwright\pw-report playwright\screenshots) do (
        if exist "%ROOT%%%d" (
            rmdir /s /q "%ROOT%%%d"
            echo   Removed %%d
        )
    )
)

if defined DO_SONAR (
    echo Cleaning Sonar report...
    if exist "%ROOT%scripts\sonar\report" (
        rmdir /s /q "%ROOT%scripts\sonar\report"
        echo   Removed scripts\sonar\report
    )
)

echo Done.
endlocal
