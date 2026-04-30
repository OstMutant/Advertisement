@echo off
set JAVA_HOME=d:\Program Files\Java\jdk-25
set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED

rem Default command: package
rem
rem Usage examples:
rem   mvn.bat                              - build the project (default: package)
rem   mvn.bat -DskipTests                  - build without running tests
rem   mvn.bat clean package                - clean and build
rem   mvn.bat clean package -DskipTests    - clean and build without tests
rem   mvn.bat test                         - run tests only
rem   mvn.bat clean                        - clean target folder
rem   mvn.bat spring-boot:run              - run the application locally (dev profile)
rem   mvn.bat spring-boot:run -Dspring-boot.run.profiles=dev  - run with explicit dev profile

if "%~1"=="" (
    call mvnw.cmd package
) else (
    call mvnw.cmd %*
)