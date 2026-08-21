@echo off
REM ---------------------------------------------------------------------------
REM Description: Collects every source file across the whole project (by extension --
REM   .java/.css/.yml/.properties/.xml/.sql/.imports/.bat/.json/.js/.sh/.md/.html, plus fixed
REM   root files and .claude/ rules/commands/skills) into a single all-code.txt, with a table of
REM   contents and a per-extension summary -- useful for AI analysis. --claude-only skips the
REM   whole-project scan and bundles just the Claude-facing operating context instead.
REM Usage: scripts\collect-code.bat [--claude-only]
REM   (no flags)      full project bundle -> all-code.txt (every extension above + root files)
REM   --claude-only   .claude/ rules+commands+skills + every CLAUDE.md (root + per-module) +
REM                   private/claude/memory/ only -> claude-context.txt
REM Uses: cmd.exe (dir/findstr/type/find only, no external tools).
REM Env: None.
REM Input: repo source -- recursive scan excluding target/node_modules/.git/.idea/.claude/
REM   generated/frontend/generated/etc (see the :FindFiles label's own exclusion list); with
REM   --claude-only, only .claude/, every CLAUDE.md, and private/claude/memory/ are read.
REM Outputs: all-code.txt in the current directory, with a console summary (file counts per
REM   extension, presence check for a fixed list of root files); with --claude-only,
REM   claude-context.txt instead, with a shorter claude-only-scoped summary.
REM Returns: 0 always.
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0.."

set "CLAUDE_ONLY=0"
if /I "%~1"=="--claude-only" set "CLAUDE_ONLY=1"

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
for /f "delims=" %%A in ('dir /S /B "%~1" 2^>nul ^| findstr /V /I "\\target\\ \\node_modules\\ \\.git\\ \\.idea\\ \\.claude\\ \\generated\\ \\frontend\\generated\\ \\frontend\\index\.html \\bundles\\ \\screenshots\\ \\private\\ \\pw-report\\ \\integration-tests\\reports\\ \\ci\\reports\\ \\unit-tests\\reports\\ \\run-all-tests\\reports\\ \\build-and-test\\reports\\ \\sonar\\report\\ \\backlog\\completed\\ package\.json package-lock\.json tsconfig\.json collect-code\.bat" ') do (
    echo %%A >> "%FILE_LIST%"
)
goto :EOF

:CountFiles
:: Counts the number of occurrences in the temp list by extension/name
set "count=0"
for /f %%A in ('type "%FILE_LIST%" 2^>nul ^| find /C /I "%~1"') do set "count=%%A"
echo %~2: %count%
goto :EOF

:CheckRootFile
:: Checks the physical presence of a file in the current directory
if exist "%~1" (
    echo [YES] %~1
) else (
    echo [NO]  %~1
)
goto :EOF

:: ==========================================
::                  MAIN
:: ==========================================

:main

:: Output file name -- claude-context.txt for --claude-only, so the two bundle kinds never
:: silently overwrite each other.
if "%CLAUDE_ONLY%"=="1" (
    set "OUT=claude-context.txt"
) else (
    set "OUT=all-code.txt"
)
echo Preparing %OUT%...

:: 1. Create a temporary file to store the list of all files
set "FILE_LIST=temp_files_list.tmp"
type nul > "%FILE_LIST%"

if "%CLAUDE_ONLY%"=="0" (
    REM 2. Collect files by extension across the entire project, including all modules
    REM The :FindFiles function automatically ignores target, node_modules, etc.
    call :FindFiles "*.java"
    call :FindFiles "*.css"
    call :FindFiles "*.yml"
    call :FindFiles "*.properties"
    call :FindFiles "*.xml"
    call :FindFiles "*.sql"
    call :FindFiles "*.imports"
    call :FindFiles "*.bat"
    call :FindFiles "*.json"
    call :FindFiles "*.js"
    call :FindFiles "*.sh"
    call :FindFiles "*.md"
    call :FindFiles "*.html"

    REM 3. Add specific root-level files -- only ones with no extension already covered by a
    REM FindFiles pattern above -- README.md/CLAUDE.md/mvn.bat/docker-compose*.yml are already
    REM collected via *.md/*.bat/*.yml, so listing them again here would duplicate their content.
    for %%F in (Dockerfile Dockerfile.ai lombok.config mvnw mvnw.cmd .env) do (
        if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
    )
)

:: 3b. Add .claude/ rules, commands, and skills (excluded from FindFiles by pattern) -- runs in
:: both modes, since it's exactly what --claude-only mode needs too.
for %%F in (.claude\rules.md) do (
    if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\commands\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\skills\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)

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
    call :CountFiles ".java" "Java files"
    call :CountFiles ".xml" "XML files (includes pom.xml)"
    call :CountFiles "pom.xml" "  of which POM files"
    call :CountFiles ".css" "CSS files"
    call :CountFiles ".yml" "YAML files"
    call :CountFiles ".properties" "Properties files"
    call :CountFiles ".sql" "SQL files"
    call :CountFiles ".imports" "Spring AutoConfig files"
    call :CountFiles ".bat" "Batch scripts"
    call :CountFiles ".json" "JSON files"
    call :CountFiles ".js" "JS files (Playwright)"
    call :CountFiles ".sh" "Shell scripts"
    call :CountFiles ".md" "Markdown files"
    call :CountFiles ".html" "HTML files"

    echo.
    echo Check root files:
    call :CheckRootFile "README.md"
    call :CheckRootFile "CLAUDE.md"
    call :CheckRootFile "Dockerfile"
    call :CheckRootFile "Dockerfile.ai"
    call :CheckRootFile ".env"
    call :CheckRootFile "scripts\deploy-and-run\docker-compose.app.yml"
    call :CheckRootFile "scripts\deploy-and-run\docker-compose.db.yml"
    call :CheckRootFile "scripts\deploy-and-run\docker-compose.minio.yml"
    call :CheckRootFile "lombok.config"
    call :CheckRootFile "scripts\deploy-and-run\reset-clean.sql"
    call :CheckRootFile ".claude\skills\doc-standards\SKILL.md"
    call :CheckRootFile ".claude\skills\deep-review\SKILL.md"
)

:: Clean up the temporary file
del "%FILE_LIST%"

echo.
echo Done! All code saved to %OUT%.
goto :EOF
