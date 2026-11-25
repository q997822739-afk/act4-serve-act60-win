@echo off
rem ============================================================================
rem  S1A5 launcher -- single player
rem
rem    run.cmd              start the server on port 10011
rem    run.cmd 10021        start it on another port
rem    run.cmd trace        wireTrace=1: every raw byte goes into the log
rem
rem  ONE PROCESS. This build has no channel server, no registration, no login
rem  api: the client connects straight to this process with client-10038.cmd,
rem  reports its account in the CMD 1 body, and the server checks it against the
rem  database. That is the whole chain.
rem
rem  THE TWO THINGS YOU HAVE TO SET are right below: CLIENT_DIR (where DNF.exe
rem  and Script.pvf live) and, if 10011 is taken, the port. Everything else is
rem  either a literal on the java line at the bottom or a default in
rem  com.dylan.net.ServerConfig -- and ServerConfig is the authority on every
rem  switch not named here. There are no environment variables.
rem
rem  That is not a style preference: switches used to be read from environment
rem  variables that survived in the same cmd window between runs, so a stale
rem  value could never lose to the default. One of them once made every quest
rem  command get dropped silently, with symptoms identical to a real bug.
rem
rem  Script.pvf IS REQUIRED. Dungeon, map, item and skill ids only exist in the
rem  script package, so the server refuses to start when it cannot read it. To
rem  bring it up anyway for protocol-level work, pass -Ds1a5.pvf.enabled=0 --
rem  that is a choice, not a failure.
rem
rem  KILLING MONSTERS needs the client patch. Without A5.dll deployed, CMD 42
rem  DIE_MONSTER goes out wrapped inside a CMD 281 and the server never sees the
rem  kill. Build and deploy it with "build.cmd hook"; see tools\A5\README.md.
rem
rem  THE DATABASE IS ONE FILE: data\s1a5.db (SQLite). No host, no port, no user,
rem  no password. The repo ships a prebuilt one with the tables created, the
rem  seeds loaded and account 10038 in it. Delete it and the server builds an
rem  empty one on the next start; change the schema and you MUST delete it and
rem  re-run com.dylan.tools.InitDb, since there is no migration path.
rem  WAL is on, so the live database is three files (.db, .db-wal, .db-shm) --
rem  copy all three, or stop the server first (that folds the WAL back in).
rem
rem  ACT5_NO_PAUSE is not a game switch: setting it to anything skips the
rem  "press any key" at the end, for running this from another script.
rem
rem  KEEP THIS FILE PURE ASCII. cmd.exe mis-parses batch files that contain
rem  non-ASCII bytes and the damage accumulates with every multi-byte character.
rem  Chinese notes go in README.md, not here.
rem ============================================================================
setlocal

set "APP_HOME=%~dp0"
set "JAR=%APP_HOME%target\s1a5-0.1.0-SNAPSHOT-all.jar"

rem ------------------------------------------------------------- YOUR MACHINE
rem The ACT5 client folder: the one with DNF.exe and Script.pvf in it.
set "CLIENT_DIR=%APP_HOME%client"

rem ------------------------------------------------------------------ fixed
set "DB_URL=jdbc:sqlite:%APP_HOME%data/s1a5.db"
set "LOG_DIR=%APP_HOME%logs"
set "JVM_OPTS=-Xms256m -Xmx1g -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"
set "PORT=10011"
set "WIRE_TRACE=0"

if /i "%~1"=="trace" goto :mode_trace
if not "%~1"=="" set "PORT=%~1"
goto :mode_done

:mode_trace
rem  Everything the protocol-discovery phase used to print, back on. Loud: it
rem  logs every byte in both directions.
set "WIRE_TRACE=1"
if not "%~2"=="" set "PORT=%~2"

:mode_done
rem ------------------------------------------------------------------- checks
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] java not found on PATH
    exit /b 1
)
if not exist "%JAR%" (
    echo [ERROR] missing %JAR%
    echo         run build.cmd first
    if not defined ACT5_NO_PAUSE pause
    exit /b 1
)
if not exist "%CLIENT_DIR%\Script.pvf" (
    echo [ERROR] no Script.pvf in "%CLIENT_DIR%"
    echo         Edit CLIENT_DIR near the top of this file so it points at the
    echo         ACT5 client folder -- the one holding DNF.exe and Script.pvf.
    if not defined ACT5_NO_PAUSE pause
    exit /b 1
)

rem ---------------------------------------------------------------- banner
echo [port]    %PORT%
echo [client]  %CLIENT_DIR%
echo [db]      %DB_URL%
echo [log]     %LOG_DIR%\s1a5.log
if "%WIRE_TRACE%"=="1" echo [switch]  wireTrace=1 -- every raw byte goes into the log
echo.
echo           Start the client with client-10038.cmd once this is up.
echo           What actually took effect is in the server's own startup log.
echo.

rem ---------------------------------------------------------------- launch
rem Every -D is quoted whole -- a JDBC URL can contain a command separator.
rem consoleCharset is deliberately NOT pinned: logback falls back to
rem stdout.encoding, which is right under both cp936 and cp65001 terminals.
rem
rem  -Ds1a5.port MUST be passed, not just the trailing positional argument.
rem  ServerConfig.PORT is num("port", 10011) -- a system property. GameServer.main
rem  reads args[0] into a LOCAL variable and never writes it back, so without this
rem  switch ServerConfig.PORT stays 10011 no matter what this process listens on.
rem
rem  dbAutoCreateAccount=1 creates the account when it is missing, so changing
rem  the account name in client-10038.cmd is all it takes to start over on a
rem  fresh character list. The shipped database already has 10038 in it.
java %JVM_OPTS% ^
  "-Ds1a5.port=%PORT%" ^
  "-Ds1a5.clientDir=%CLIENT_DIR%" ^
  "-Ds1a5.dbUrl=%DB_URL%" ^
  "-Ds1a5.logDir=%LOG_DIR%" ^
  "-Ds1a5.wireTrace=%WIRE_TRACE%" ^
  -Ds1a5.dbAutoCreateAccount=1 ^
  -jar "%JAR%" %PORT%

set "RC=%errorlevel%"
echo.
echo [exit] stopped with code %RC%
if not defined ACT5_NO_PAUSE pause
exit /b %RC%
