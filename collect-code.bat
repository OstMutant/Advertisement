@echo off
setlocal

:: Output file name
set "OUT=all-code.txt"
echo Preparing %OUT%...

:: 1. Create a temporary file to store the list of all files
set "FILE_LIST=temp_files_list.tmp"
type nul > "%FILE_LIST%"

:: 2. Collect files by extension across the entire project (including all modules)
:: The :FindFiles function automatically ignores target, node_modules, etc.
call :FindFiles "*.java"
call :FindFiles "pom.xml"
call :FindFiles "*.css"
call :FindFiles "*.yml"
call :FindFiles "*.properties"
call :FindFiles "*.xml"
call :FindFiles "*.sql"
call :FindFiles "*.imports"
call :FindFiles "*.js"
call :FindFiles "*.sh"
call :FindFiles "*.md"

:: 3. Add specific root-level files
for %%F in (README.md CLAUDE.md Dockerfile Dockerfile.ai claude.bat lombok.config mvn.bat mvnw mvnw.cmd docker-compose.app.yml docker-compose.db.yml docker-compose.minio.yml) do (
    if exist "%%F" echo %%~dpnxF >> "%FILE_LIST%"
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
call :CountFiles "pom.xml" "POM files"
call :CountFiles ".css" "CSS files"
call :CountFiles ".yml" "YAML files"
call :CountFiles ".properties" "Properties files"
call :CountFiles ".sql" "SQL files"
call :CountFiles ".imports" "Spring AutoConfig files"
call :CountFiles ".js" "JS files (Playwright)"
call :CountFiles ".sh" "Shell scripts"
call :CountFiles ".md" "Markdown files"

echo.
echo Check root files:
call :CheckRootFile "README.md"
call :CheckRootFile "CLAUDE.md"
call :CheckRootFile "Dockerfile"
call :CheckRootFile "Dockerfile.ai"
call :CheckRootFile "claude.bat"
call :CheckRootFile "docker-compose.app.yml"
call :CheckRootFile "docker-compose.db.yml"
call :CheckRootFile "docker-compose.minio.yml"
call :CheckRootFile "lombok.config"

:: Clean up the temporary file
del "%FILE_LIST%"

echo.
echo Done! All code saved to %OUT%.
goto :EOF

:: ==========================================
::                FUNCTIONS
:: ==========================================

:FindFiles
:: Recursively searches for files and filters out system/generated folders
for /f "delims=" %%A in ('dir /S /B "%~1" 2^>nul ^| findstr /V /I "\\target\\ \\node_modules\\ \\.git\\ \\.idea\\ \\generated\\ \\frontend\\generated\\" ') do (
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