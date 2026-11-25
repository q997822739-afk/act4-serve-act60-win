@echo off
rem ============================================================================
rem  S1A5 web GM tool -- start the browser GM (port 5052)
rem
rem    run-gm.cmd              start on port 5052 and open the browser
rem    run-gm.cmd 5053         start on another port
rem    run-gm.cmd 5053 nobrowser
rem
rem  It needs Java 25 (same as the server): tools\jdk25\ first, then PATH,
rem  then JAVA_HOME. THE WRITE GATE: while the game server is running, every
rem  write is refused -- stop the server first (launcher button or close the
rem  run.cmd window), write, then start it again and re-pick the character.
rem  Every write auto-backups the whole database into data\webgm-backups\.
rem
rem  The data source paths are passed EXPLICITLY (same as the launcher does): the GM also
rem  remembers the last paths in server\webgm-settings.json, and a run started with other
rem  paths must not be inherited by the next normal start.
rem
rem  KEEP THIS FILE PURE ASCII. See the same note in run.cmd.
rem ============================================================================
setlocal

set "APP_HOME=%~dp0"
rem Packaged layout: the fat jar lives in target\ ; a game\ style package has
rem it in server\ -- use whichever exists.
set "JAR=%APP_HOME%target\s1a5-0.1.0-SNAPSHOT-all.jar"
if not exist "%JAR%" set "JAR=%APP_HOME%server\s1a5-0.1.0-SNAPSHOT-all.jar"
set "PORT=5052"
set "NOBROWSER="

rem Packaged layout: the ACT5 client folder (Script.pvf + ImagePacks) sits
rem NEXT TO this folder as ..\ACT5 ; a dev checkout may have .\client.
set "CLIENT_DIR=%APP_HOME%..\ACT5"
if not exist "%CLIENT_DIR%\Script.pvf" set "CLIENT_DIR=%APP_HOME%client"

if not "%~1"=="" set "PORT=%~1"
if /i "%~2"=="nobrowser" set "NOBROWSER=--no-browser"

rem Java 25 needed. Priority: bundled jdk25\ -> PATH -> JAVA_HOME (the jar is
rem Java 25 bytecode, so a JAVA_HOME pointing at an older JDK must NOT win).
if exist "%APP_HOME%jdk25\bin\java.exe" set "JAVA=%APP_HOME%jdk25\bin\java.exe"
if not defined JAVA if exist "%APP_HOME%tools\jdk25\bin\java.exe" set "JAVA=%APP_HOME%tools\jdk25\bin\java.exe"
if not defined JAVA (
    where java >nul 2>nul
    if not errorlevel 1 set "JAVA=java"
)
if not defined JAVA if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA if exist "%JAVA_HOME%\java.exe" set "JAVA=%JAVA_HOME%\java.exe"
if not defined JAVA (
    echo [ERROR] java not found -- need Java 25 on PATH, in JAVA_HOME, or in jdk25\
    if not defined ACT5_NO_PAUSE pause
    exit /b 1
)

if not exist "%JAR%" (
    echo [ERROR] missing %JAR%
    echo         the jar ships with this package; rebuild from the source folder if missing
    if not defined ACT5_NO_PAUSE pause
    exit /b 1
)

echo [gm]      http://127.0.0.1:%PORT%
echo [java]    %JAVA%
echo.
echo           While the game server is running every write is refused.
echo           Stop it first, write, start it again, re-pick the character.
echo.

"%JAVA%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 ^
  -cp "%JAR%" com.dylan.tools.webgm.WebGmMain --port %PORT% ^
  --db "%APP_HOME%data\s1a5.db" ^
  --pvf "%CLIENT_DIR%\Script.pvf" ^
  --imagepacks "%CLIENT_DIR%\ImagePacks2;%CLIENT_DIR%\ImagePacks3;%CLIENT_DIR%\ImagePacks4" ^
  %NOBROWSER%

set "RC=%errorlevel%"
echo.
echo [exit] stopped with code %RC%
if not defined ACT5_NO_PAUSE pause
exit /b %RC%
