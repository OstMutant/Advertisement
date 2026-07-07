@echo off
REM Run the application locally via Maven (no Docker image rebuild).
REM Requires: DB and MinIO already running (start via scripts\infra\).
REM
REM Usage:
REM   scripts\run-local.bat                                    -- dev profile, port 8080
REM   scripts\run-local.bat --prod                             -- production Vaadin build, port 8080
REM   scripts\run-local.bat --prod "C:\custom\jdk-25"          -- production, custom JAVA_HOME
REM   scripts\run-local.bat "C:\custom\jdk-25"                 -- dev, custom JAVA_HOME
REM Default JAVA_HOME: D:\Program Files\Java\jdk-25

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
