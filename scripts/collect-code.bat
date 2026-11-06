@echo off
REM ---------------------------------------------------------------------------
REM Description: Collects source files into one text file with a table of contents and a
REM   per-extension summary -- useful for AI analysis. By default it bundles only what the
REM   running application is made of (every module's src\main tree plus every pom.xml,
REM   Dockerfile and lombok.config) into core-code.txt. --all bundles every source file of the
REM   whole project (by extension .java/.css/.yml/.properties/.xml/.sql/.imports/.bat/.json/.js/
REM   .sh/.md/.html, plus fixed root files and the .claude/ operating-context files) into
REM   all-code.txt. --claude-only bundles just the Claude-facing operating context instead.
REM Usage: scripts\collect-code.bat [--all | --claude-only]
REM   (no flags)      minimal bundle -> core-code.txt (src\main of every module + pom.xml files)
REM   --all           full project bundle -> all-code.txt (every extension above + root files)
REM   --claude-only   .claude/ rules+commands+skills + every CLAUDE.md (root + per-module) +
REM                   private/claude/memory/ only -> claude-context.txt
REM Uses: cmd.exe (dir/findstr/type/find only, no external tools).
REM Env: None.
REM Input: repo source -- recursive scan excluding the paths in the EXCLUDES variable below
REM   (target, node_modules, generated output, report folders, completed backlog tasks, ...);
REM   the default mode additionally keeps only paths under src\main (plus pom.xml files);
REM   --claude-only reads only .claude/, every CLAUDE.md, and private/claude/memory/.
REM Outputs: core-code.txt, all-code.txt or claude-context.txt in the current directory,
REM   with a console summary (file counts per extension; --all also checks a fixed list of
REM   root files).
REM Returns: 0 always.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0.."

set "CLAUDE_ONLY=0"
set "ALL=0"
for %%A in (%*) do (
    if /I "%%~A"=="--claude-only" set "CLAUDE_ONLY=1"
    if /I "%%~A"=="--all" set "ALL=1"
)

:: What each mode collects -- the one place to add an extension or a root file
set "CORE_EXTS=java css yml properties xml sql imports js html"
set "ALL_EXTS=java css yml properties xml sql imports bat json js sh md html"
set "CORE_ROOT_FILES=Dockerfile lombok.config"
set "ALL_ROOT_FILES=Dockerfile Dockerfile.claude Dockerfile.opencode lombok.config mvnw mvnw.cmd .env"

:: Paths :FindFiles and :FindFilesIn never list (findstr patterns, space separated)
set "EXCLUDES=\\target\\ \\node_modules\\ \\.git\\ \\.idea\\ \\.claude\\ \\generated\\ \\frontend\\generated\\ \\frontend\\index\.html \\bundles\\ \\screenshots\\ \\private\\ \\pw-report\\ \\integration-tests\\reports\\ \\ci\\reports\\ \\unit-tests\\reports\\ \\run-all-tests\\reports\\ \\build-and-test\\reports\\ \\sonar\\report\\ \\backlog\\completed\\tasks\\ package\.json package-lock\.json tsconfig\.json collect-code\.bat"

goto :main

:: ==========================================
::                FUNCTIONS
:: ==========================================
:: Defined before :main (and before any call site) so cmd.exe never has to seek forward
:: through the file to resolve a label on first use - combined with `setlocal`, a call to a
:: label defined later in the file has been observed to silently no-op on its very first
:: invocation only, with every later call to the same label working correctly. Backward
:: references (label already passed while reading the file) do not have this problem.

:FindFiles
:: Recursively searches for files and filters out system/generated folders.
:: Also excludes collect-code.bat itself: dumping its own content via `type` while cmd.exe is
:: still mid-execution of that same file is a known-fragile self-read pattern in cmd.exe and can
:: silently truncate the output - this script's source is always available locally anyway, no
:: need to have it re-dump itself into all-code.txt.
for /f "delims=" %%A in ('dir /S /B "%~1" 2^>nul ^| findstr /V /I "%EXCLUDES%"') do (
    echo %%A >> "%FILE_LIST%"
)
goto :EOF

:FindFilesIn
:: Like :FindFiles, but keeps only paths containing the substring in %~2 (a findstr pattern).
for /f "delims=" %%A in ('dir /S /B "%~1" 2^>nul ^| findstr /V /I "%EXCLUDES%" ^| findstr /I "%~2"') do (
    echo %%A >> "%FILE_LIST%"
)
goto :EOF

:CountFiles
:: Counts the number of occurrences in the temp list by extension/name
set "count=0"
for /f %%A in ('type "%FILE_LIST%" 2^>nul ^| find /C /I "%~1"') do set "count=%%A"
echo %~2: %count%
goto :EOF

:: ==========================================
::                  MAIN
:: ==========================================

:main

:: Output file name -- claude-context.txt for --claude-only, so the two bundle kinds never
:: silently overwrite each other.
if "%CLAUDE_ONLY%"=="1" (
    set "OUT=claude-context.txt"
) else if "%ALL%"=="1" (
    set "OUT=all-code.txt"
) else (
    set "OUT=core-code.txt"
)
echo Preparing %OUT%...

:: 1. Create a temporary file to store the list of all files
set "FILE_LIST=temp_files_list.tmp"
type nul > "%FILE_LIST%"

if "%CLAUDE_ONLY%"=="0" if "%ALL%"=="0" (
    REM Default bundle -- only what the running application is made of: every module's
    REM src\main tree, plus every pom.xml and the core root files.
    set "SCAN_EXTS=%CORE_EXTS%"
    set "ROOT_FILES=%CORE_ROOT_FILES%"
    for %%E in (%CORE_EXTS%) do call :FindFilesIn "*.%%E" "\\src\\main"
    call :FindFiles "pom.xml"
)

if "%CLAUDE_ONLY%"=="0" if "%ALL%"=="1" (
    REM --all: every file of each extension across the whole project; README.md, CLAUDE.md and
    REM docker-compose*.yml are already covered by the *.md and *.yml extensions.
    set "SCAN_EXTS=%ALL_EXTS%"
    set "ROOT_FILES=%ALL_ROOT_FILES%"
    for %%E in (%ALL_EXTS%) do call :FindFiles "*.%%E"
)

if "%CLAUDE_ONLY%"=="0" (
    for %%F in (%ROOT_FILES%) do if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
)

:: 3b. Add .claude/ rules, commands, skills, path-scoped rules, agents, and nav docs (all
:: excluded from FindFiles by pattern) -- runs in both modes, since it's exactly what
:: --claude-only mode needs too.
if "%ALL%%CLAUDE_ONLY%"=="00" goto :after_claude_context
for %%F in (.claude\rules.md .claude\DECISIONS.md) do (
    if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
)
for %%D in (commands skills rules agents nav) do (
    for /f "delims=" %%A in ('dir /S /B ".claude\%%D\*.md" 2^>nul') do (
        echo %%A >> "%FILE_LIST%"
    )
)
:after_claude_context
for %%F in (.claude\rules.md .claude\DECISIONS.md) do (
    if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\commands\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\skills\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\rules\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\agents\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\nav\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)
:after_claude_context

if "%CLAUDE_ONLY%"=="1" (
    REM 3c. Add every CLAUDE.md across the project, root plus every module -- the full-project
    REM scan above is skipped entirely in this mode, so it needs its own explicit collection call
    REM here instead of relying on the general *.md pattern to pick these up.
    call :FindFiles "CLAUDE.md"

    REM 3d. Add the Claude memory snapshot at private\claude\memory\ -- bypasses :FindFiles's own
    REM \private\ exclusion, which is deliberate for the full-project bundle, since this
    REM inclusion is intentional and scoped to --claude-only mode only.
    for /f "delims=" %%A in ('dir /S /B "private\claude\memory\*.md" 2^>nul') do (
        echo %%A >> "%FILE_LIST%"
    )
)

:: 4. Write the "Table of Contents" to the output file
echo === TABLE OF CONTENTS === > "%OUT%"
type "%FILE_LIST%" >> "%OUT%"
echo. >> "%OUT%"
echo ========================= >> "%OUT%"
echo. >> "%OUT%"

:: 5. Read the temporary list and append the content of each file
for /F "usebackq delims=" %%F in ("%FILE_LIST%") do (
    echo ===== %%~nxF [%%F] ===== >> "%OUT%"
    type "%%F" >> "%OUT%"
    echo. >> "%OUT%"
    echo. >> "%OUT%"
)

:: 6. Print summary to the console
echo.
echo ===== SUMMARY =====
if "%CLAUDE_ONLY%"=="1" (
    call :CountFiles ".claude" "Claude context files (rules/commands/skills)"
    call :CountFiles "CLAUDE.md" "CLAUDE.md files (root + per-module)"
    call :CountFiles "private\claude\memory" "Claude memory files"
) else (
    for %%E in (%SCAN_EXTS%) do call :CountFiles ".%%E" "%%E files"
    call :CountFiles "pom.xml" "  of which POM files"
    echo.
    echo Root files:
    for %%F in (README.md CLAUDE.md %ROOT_FILES%) do if exist "%%F" (echo [YES] %%F) else (echo [NO]  %%F)
)

:: Clean up the temporary file
del "%FILE_LIST%"

echo.
echo Done! Code saved to %OUT%.
goto :EOF
