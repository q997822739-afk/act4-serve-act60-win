@echo off
rem ============================================================================
rem  S1A5 build script
rem
rem    build.cmd                      package the server
rem    build.cmd hook <client>        build the CLIENT patch instead (tools\A5)
rem    build.cmd hook debug <client>  same, with logging compiled in (dev only)
rem
rem  Output: target\s1a5-0.1.0-SNAPSHOT-all.jar  (fat jar, java -jar ready)
rem
rem  "hook" is a different product entirely: A5.dll + DumpReport.dll, deployed
rem  into the client folder. DNF.exe itself is never modified. It needs the x86
rem  MSVC BuildTools (vcvars32); the Java build does not. The client folder is
rem  required -- pass the one holding DNF.exe. See tools\A5\README.md.
rem
rem  IF THE JAR CANNOT BE DELETED: the server is still running and holding it.
rem  Close the run.cmd window first. This script checks for a live java.exe and
rem  tells you before Maven fails with a confusing message.
rem
rem  KEEP THIS FILE PURE ASCII. This file USED to carry Chinese comments and it
rem  silently stopped working: double-clicking it opened a console and printed
rem  nothing at all, so the jar quietly stayed stale while everything else in
rem  the session looked fine. cmd.exe mis-parses batch files containing
rem  non-ASCII bytes -- the byte-offset desync accumulates with every
rem  multi-byte character. Chinese notes belong in README.md, not here.
rem ============================================================================
setlocal

set "APP_HOME=%~dp0"
set "JAR=%APP_HOME%target\s1a5-0.1.0-SNAPSHOT-all.jar"

if /i not "%~1"=="hook" goto :java_build
rem Pass both: "hook debug" and "hook debug <client folder>" have to work.
call "%APP_HOME%tools\A5\build.bat" %2 %3
set "RC=%errorlevel%"
if not defined ACT5_NO_PAUSE pause
exit /b %RC%
:java_build

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] mvn not found on PATH
    if not defined ACT5_NO_PAUSE pause
    exit /b 1
)

rem The fat jar is locked while the server runs, and Maven's clean then fails
rem with "Failed to delete ...", which reads like a permission problem.
tasklist /fi "imagename eq java.exe" 2>nul | find /i "java.exe" >nul
if not errorlevel 1 (
    echo [WARN ] java.exe is running -- if it is our server it holds the jar
    echo         and clean will fail. Close run.cmd first.
    echo.
)

echo [INFO] JDK   : %JAVA_HOME%
for /f "delims=" %%v in ('mvn -v 2^>nul ^| findstr /b /c:"Apache Maven"') do echo [INFO] Maven : %%v
echo.

rem -o is offline: every dependency is already in the local repo. Extra arguments
rem are passed straight through to Maven.
call mvn -o -f "%APP_HOME%pom.xml" clean package %*
if errorlevel 1 (
    echo.
    echo [ERROR] build failed
    echo         "Failed to delete ...-all.jar" means the server is still up.
    if not defined ACT5_NO_PAUSE pause
    exit /b 1
)

echo.
echo [OK] artifacts:
dir /b "%APP_HOME%target\*.jar"
echo.
for %%f in ("%JAR%") do echo [OK] jar timestamp: %%~tf
echo.
echo Next: run.cmd                start the server
echo       client-10038.cmd       start the client
echo       build.cmd hook ^<dir^>   build/deploy the client patch (A5.dll)
echo.
if not defined ACT5_NO_PAUSE pause
exit /b 0
