@echo off
rem ============================================================================
rem  ACT5 client launcher
rem
rem    client-10038.cmd                       connect 127.0.0.1:10011
rem    client-10038.cmd 10021                 pick a port
rem    client-10038.cmd 10021 192.168.1.5     pick a port and an IP
rem
rem  It lands straight on CHARACTER SELECT. That is decided by argv[0] alone:
rem  below 10 (or exactly 99) gives you the channel-select screen, anything 10 or
rem  above goes to character select. NO packet can move the client between those
rem  two screens, and this build has no channel server to point it at, so argv[0]
rem  is fixed at 10.
rem
rem  THE ACCOUNT AND PASSWORD ARE ON THE COMMAND LINE (segments 4 and 5). The
rem  client puts them in the CMD 1 body and the server checks them against the
rem  database -- there is no login api in between. The shipped data\s1a5.db has
rem  account 10038 / 123456 in it. Change ACCOUNT below and the server creates
rem  that one on first connect (run.cmd passes dbAutoCreateAccount=1), which is
rem  how you start over on a fresh character list.
rem
rem  ONE EXE. The client patches that used to need their own copy of the exe
rem  (DNF_unwrap.exe / DNF_trace.exe, 32 MB each) now live in A5.dll, which the
rem  loader pulls in through DumpReport.dll before DNF.exe's CRT even runs --
rem  DNF.exe itself is not modified. Build and deploy both with
rem
rem      build.cmd hook
rem
rem  WITHOUT A5.dll NO KILL IS REPORTED: CMD 42 DIE_MONSTER goes out wrapped
rem  in a CMD 281 and the server never sees the monster die. There is no A5.ini
rem  any more -- every switch is a compile-time constant in tools\A5\A5.h.
rem
rem  Whether the patches took effect is in <client>\logs\A5.log, and only in a
rem  debug build: "build.bat debug" compiles the logging in, the shipping build
rem  has none at all. No log file from a shipping build is normal, not a fault.
rem  See tools\A5\README.md.
rem
rem  The client has no UI to enter a server address -- the IP and port can only
rem  arrive on the command line. Double-clicking the exe will not connect here.
rem
rem  WHICH EXE: this needs the UNPACKED DNF.exe. The original protected build
rem  dies on Windows 11 after ~1.3s with 0xC000071C inside ntdll, before it draws
rem  a window, independent of the command line and of compatibility modes.
rem
rem  Two more command-line constraints: at least 13 segments, and no segment may
rem  be empty. The client splits lpCmdLine on a single '?'.
rem
rem  KEEP THIS FILE PURE ASCII -- see the same note in run.cmd. Plain gotos, no
rem  parenthesised blocks, and CRLF line endings: an LF-only batch file makes
rem  goto land on the wrong label without saying anything.
rem ============================================================================
setlocal

rem Must match CLIENT_DIR in run.cmd: the folder with DNF.exe in it.
set "CLIENT_DIR=%~dp0client"
set "EXE=DNF.exe"

set "MODE=10"
set "PORT=10011"
set "HOST=127.0.0.1"
set "ACCOUNT=10038"
set "PASSWORD=123456"
rem  argv[5] is the server group index. It only matters on the channel-select
rem  screen, which this build never opens, so any valid value does.
rem  1 = diregie = channel_info.etc group 2.
set "GROUP=1"

if not "%~1"=="" set "PORT=%~1"
if not "%~2"=="" set "HOST=%~2"

set "ARGS=%MODE%?%HOST%?%PORT%?%ACCOUNT%?%PASSWORD%?%GROUP%?1?0?0?0?0?1?0?0?0?19847"

if not exist "%CLIENT_DIR%\%EXE%" (
    echo [ERROR] not found: "%CLIENT_DIR%\%EXE%"
    echo         Edit CLIENT_DIR near the top of this file so it points at the
    echo         ACT5 client folder, the same one run.cmd uses.
    pause
    exit /b 1
)

echo [client]  %CLIENT_DIR%\%EXE%
echo [target]  %HOST%:%PORT%
echo [account] %ACCOUNT%
if exist "%CLIENT_DIR%\A5.dll" goto :hook_ok
echo [hook]    A5.dll MISSING -- no client patch is active.
echo           Kills will NOT reach the server. Run: build.cmd hook
goto :launch
:hook_ok
echo [hook]    A5.dll present
:launch
echo.

cd /d "%CLIENT_DIR%"
start "" "%EXE%" %ARGS%
exit /b %errorlevel%
