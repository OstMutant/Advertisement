@echo off
REM ---------------------------------------------------------------------------
REM Description: Runs the application locally via Maven, no Docker image rebuild -- dev profile
REM   (Vaadin dev mode) by default, or a production Vaadin build + prod profile with --prod.
REM   Requires DB and MinIO already running separately.
REM Usage: scripts\run-local.bat [--prod] [custom-JAVA_HOME-path]
REM   --prod           production Vaadin build (mvnw clean package -DskipTests), prod Spring
REM                     profile, port 8080 with hardcoded local DB/MinIO credentials
REM   (no --prod)      dev profile, Vaadin dev mode, port 8080
REM   <path>           override JAVA_HOME (default D:\Program Files\Java\jdk-25) for either mode
REM Uses: cmd.exe, mvnw.cmd, java.
REM Env: sets JAVA_HOME/PATH for the build itself; with --prod, also sets
REM   SPRING_PROFILES_ACTIVE/DB_*/S3_* for the running app -- none read from the caller's shell.
REM Input: repo source; marketplace-app\target\marketplace-app-*.jar (built by this same script
REM   before running it).
REM Outputs: running application on port 8080.
REM Returns: 0 on success; non-zero if the Maven build fails.
REM ---------------------------------------------------------------------------

set ROOT=%~dp0..
set PROD=0
set CUSTOM_JAVA_HOME=

for %%a in (%*) do (
  if "%%a"=="--prod" set PROD=1
  if not "%%a"=="--prod" set CUSTOM_JAVA_HOME=%%a
)

if not "%CUSTOM_JAVA_HOME%"=="" (
  set JAVA_HOME=%CUSTOM_JAVA_HOME%
) else (
  set JAVA_HOME=D:\Program Files\Java\jdk-25
)
set PATH=%JAVA_HOME%\bin;%PATH%

if "%PROD%"=="1" (
  echo === Building (production profile) ===
  call "%ROOT%\mvnw.cmd" clean package -DskipTests
  if errorlevel 1 exit /b 1

  echo.
  echo === Starting application (prod profile, port 8080) ===
  set SPRING_PROFILES_ACTIVE=prod
  set DB_HOST=localhost
  set DB_PORT=5432
  set DB_NAME=experiments
  set DB_USER=experiments_user
  set DB_PASSWORD=experiments_user_password
  set S3_ENDPOINT=http://localhost:9000
  set S3_BUCKET=advertisement
  set S3_ACCESS_KEY=admin
  set S3_SECRET_KEY=admin12345
  set S3_REGION=us-east-1
  set S3_PUBLIC_URL=http://localhost:9000/advertisement
  for %%f in ("%ROOT%\marketplace-app\target\marketplace-app-*.jar") do (
    java -jar "%%f"
  )
) else (
  echo === Building (dev profile) ===
  call "%ROOT%\mvnw.cmd" clean package -DskipTests
  if errorlevel 1 exit /b 1

  echo.
  echo === Starting application (dev profile, port 8080) ===
  for %%f in ("%ROOT%\marketplace-app\target\marketplace-app-*.jar") do (
    java -jar "%%f"
  )
)
