@echo off
(
  dir /S /B *.java
  dir /S /B pom.xml
  dir /S /B README.md
  dir /S /B src\main\frontend\themes\my-app\*.css
  dir /S /B src\main\resources\*
) > all-code.txt

for /R %%f in (*.java) do (
  echo ===== %%f ===== >> all-code.txt
  type "%%f" >> all-code.txt
)

if exist pom.xml (
  echo ===== pom.xml ===== >> all-code.txt
  type pom.xml >> all-code.txt
)

if exist README.md (
  echo ===== README.md ===== >> all-code.txt
  type README.md >> all-code.txt
)

for %%f in (src\main\frontend\themes\my-app\*.css) do (
  echo ===== %%f ===== >> all-code.txt
  type "%%f" >> all-code.txt
)

for /R src\main\resources %%f in (*) do (
  echo ===== %%f ===== >> all-code.txt
  type "%%f" >> all-code.txt
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
