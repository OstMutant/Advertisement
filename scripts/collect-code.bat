@echo off
setlocal
cd /d "%~dp0.."

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

:: Output file name
set "OUT=all-code.txt"
echo Preparing %OUT%...

:: 1. Create a temporary file to store the list of all files
set "FILE_LIST=temp_files_list.tmp"
type nul > "%FILE_LIST%"

:: 2. Collect files by extension across the entire project (including all modules)
:: The :FindFiles function automatically ignores target, node_modules, etc.
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

:: 3. Add specific root-level files -- only ones with no extension already covered by a
:: FindFiles pattern above (README.md/CLAUDE.md/mvn.bat/docker-compose*.yml are already
:: collected via *.md/*.bat/*.yml -- listing them again here would duplicate their content).
for %%F in (Dockerfile Dockerfile.ai lombok.config mvnw mvnw.cmd .env) do (
    if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
)

:: 3b. Add .claude/ rules, commands, and skills (excluded from FindFiles by pattern)
for %%F in (.claude\rules.md) do (
    if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\commands\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
)
for /f "delims=" %%A in ('dir /S /B ".claude\skills\*.md" 2^>nul') do (
    echo %%A >> "%FILE_LIST%"
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
call :CheckRootFile "scripts\infra\docker-compose.app.yml"
call :CheckRootFile "scripts\infra\docker-compose.db.yml"
call :CheckRootFile "scripts\infra\docker-compose.minio.yml"
call :CheckRootFile "lombok.config"
call :CheckRootFile "scripts\database\reset-clean.sql"
call :CheckRootFile ".claude\skills\doc-standards\SKILL.md"
call :CheckRootFile ".claude\skills\deep-review\SKILL.md"

:: Clean up the temporary file
del "%FILE_LIST%"

echo.
echo Done! All code saved to %OUT%.
goto :EOF
