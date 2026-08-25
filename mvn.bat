@echo off
REM ── Header ──────────────────────────────────────────────────────────────────
REM Description: Convenience wrapper for mvnw.cmd -- sets JAVA_HOME/MAVEN_OPTS, defaults to the
REM   `package` goal when invoked with no arguments.
REM Usage: mvn.bat [maven-args...]
REM   (no args)   run `mvnw.cmd package`
REM   [any args]  forwarded verbatim to `mvnw.cmd`, e.g. mvn.bat clean package -DskipTests
REM Uses: mvnw.cmd (the Maven Wrapper).
REM Env: JAVA_HOME (hardcoded here to a specific machine path -- flagged, not fixed, changing it
REM   would alter real behavior, not just documentation), MAVEN_OPTS=--enable-native-access=ALL-UNNAMED
REM   (set unconditionally).
REM Input: None.
REM Outputs: same as whatever Maven goal was run.
REM Returns: same as mvnw.cmd's own exit code.
REM ────────────────────────────────────────────────────────────────────────────
set JAVA_HOME=d:\Program Files\Java\jdk-25
set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED

if "%~1"=="" (
    call mvnw.cmd package
) else (
    call mvnw.cmd %*
)