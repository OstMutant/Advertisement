@echo off
(
  dir /S /B *.java
  dir /S /B pom.xml
  dir /S /B README.md
  dir /S /B src\main\frontend\themes\my-app\*.css
  dir /S /B src\main\resources\*
  dir /B docker-compose.app.yml
  dir /B docker-compose.db.yml
  dir /B Dockerfile
  dir /B lombok.config
  dir /B mvn.bat
  dir /B mvnw
  dir /B mvnw.cmd
) > all-code.txt

:: === Java files ===
for /R %%f in (*.java) do (
  echo ===== %%f ===== >> all-code.txt
  type "%%f" >> all-code.txt
)

:: === pom.xml ===
if exist pom.xml (
  echo ===== pom.xml ===== >> all-code.txt
  type pom.xml >> all-code.txt
)

:: === README.md ===
if exist README.md (
  echo ===== README.md ===== >> all-code.txt
  type README.md >> all-code.txt
)

:: === CSS files ===
for %%f in (src\main\frontend\themes\my-app\*.css) do (
  echo ===== %%f ===== >> all-code.txt
  type "%%f" >> all-code.txt
)

:: === Resource files ===
for /R src\main\resources %%f in (*) do (
  echo ===== %%f ===== >> all-code.txt
  type "%%f" >> all-code.txt
)

:: === Root-level files ===
for %%f in (
  docker-compose.app.yml
  docker-compose.db.yml
  docker-compose.minio.yml
  Dockerfile
  lombok.config
  mvn.bat
  mvnw
  mvnw.cmd
) do (
  if exist %%f (
    echo ===== %%f ===== >> all-code.txt
    type %%f >> all-code.txt
  )
)

:: === Summary for console only ===
echo.
echo ===== SUMMARY =====
echo Total Java files:
dir /S /B *.java | find /C /V ""
echo Total CSS files:
dir /S /B src\main\frontend\themes\my-app\*.css | find /C /V ""
echo Total resource files:
dir /S /B src\main\resources\* | find /C /V ""
echo pom.xml present:
if exist pom.xml (echo YES) else (echo NO)
echo README.md present:
if exist README.md (echo YES) else (echo NO)
echo docker-compose.app.yml present:
if exist docker-compose.app.yml (echo YES) else (echo NO)
echo docker-compose.db.yml present:
if exist docker-compose.db.yml (echo YES) else (echo NO)
echo docker-compose.minio.yml present:
if exist docker-compose.minio.yml (echo YES) else (echo NO)
echo Dockerfile present:
if exist Dockerfile (echo YES) else (echo NO)
echo lombok.config present:
if exist lombok.config (echo YES) else (echo NO)
echo mvn.bat present:
if exist mvn.bat (echo YES) else (echo NO)
echo mvnw present:
if exist mvnw (echo YES) else (echo NO)
echo mvnw.cmd present:
if exist mvnw.cmd (echo YES) else (echo NO)
