@echo off
rem Build the ACT5 client patch. Two files land in the client root:
rem
rem   A5.dll          all features: TP unwrap, TP stub, code patches, font
rem   DumpReport.dll  a few-KB shim replacing the stock crash-dump library; its
rem                   only job is to pull A5.dll into the process
rem
rem DNF.exe is not modified at all. The switches are compile-time constants in
rem A5.h; see the repository README for what they do.
rem
rem   build.bat                        SHIPPING build, default client folder
rem   build.bat "D:\path\to\client"    shipping build, somewhere else
rem   build.bat debug [folder]         same + logging into <client>\logs\A5.log
rem
rem The shipping build has NO log code at all -- A5_Log compiles to nothing, so
rem the strings never reach the binary. That matters: a session's log names 132
rem patch addresses with their before/after bytes. Shipping that is handing over
rem the whole reverse-engineering notebook.
rem
rem There is no A5.ini either. Every switch is a compile-time constant in A5.h.
rem Players cannot perceive any of them, and "configurable" only ever meant
rem "someone can turn it off".
rem
rem The stock DumpReport.dll is copied into vendor\ the first time this runs,
rem before anything overwrites it. That copy is the only way back.
rem
rem Must be x86 (DNF.exe is 32-bit), so use vcvars32, not vcvars64.
rem Order matters: A5.dll must be linked first to produce obj\A5.lib.
rem
rem KEEP THIS FILE PURE ASCII. cmd.exe mis-parses batch files containing
rem non-ASCII bytes; Chinese notes go in the repository README, not here.

setlocal

rem Finding vcvars32.bat. Set A5_VCVARS to point straight at it to skip all of
rem this. Otherwise ask vswhere.exe, which ships with every VS 2017+ installer
rem and reports where the tools actually went -- hardcoding the default install
rem path breaks the moment someone puts Visual Studio on another drive.
set "VCVARS=%A5_VCVARS%"
if not "%VCVARS%"=="" goto :vcvars_set
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" goto :vcvars_set
for /f "usebackq delims=" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSPATH=%%i"
if defined VSPATH set "VCVARS=%VSPATH%\VC\Auxiliary\Build\vcvars32.bat"
:vcvars_set

set "LOGFLAG="
set "BUILDKIND=shipping"
set "OUT=%~1"
if /i "%OUT%"=="debug" (
    set "LOGFLAG=/DA5_LOG=1"
    set "BUILDKIND=debug"
    set "OUT=%~2"
)

if not exist "%VCVARS%" (
    echo [build] vcvars32 not found: "%VCVARS%"
    echo         Install the x86 MSVC build tools, or set A5_VCVARS to the
    echo         full path of your vcvars32.bat and try again.
    exit /b 1
)
if "%OUT%"=="" (
    echo [build] pass the client folder as the first argument, e.g.
    echo         build.bat "C:\Games\DNF"
    exit /b 1
)
if not exist "%OUT%\DNF.exe" (
    echo [build] no DNF.exe in "%OUT%"
    exit /b 1
)

call "%VCVARS%" >nul
if errorlevel 1 (
    echo [build] vcvars32 init failed
    exit /b 1
)

pushd "%~dp0"
if not exist "obj" mkdir "obj"
if not exist "vendor" mkdir "vendor"

rem --- 0/3 back up the stock crash-dump library, once ---
rem The shim replaces it in place. Without this copy there is no way back to a
rem stock client folder, so it runs before the compiler does.
if exist "vendor\DumpReport.dll" goto :vendor_ok
if not exist "%OUT%\DumpReport.dll" (
    echo [build] "%OUT%\DumpReport.dll" is missing and vendor\ has no backup.
    echo         Restore it from a clean client before building.
    goto :fail
)
for %%f in ("%OUT%\DumpReport.dll") do set "DRSIZE=%%~zf"
if %DRSIZE% LSS 10000 (
    echo [build] "%OUT%\DumpReport.dll" is only %DRSIZE% bytes -- that looks like
    echo         our shim, not the stock 20480-byte library, and vendor\ has no
    echo         backup. Restore the stock file first; refusing to overwrite it.
    goto :fail
)
copy /y "%OUT%\DumpReport.dll" "vendor\DumpReport.dll" >nul
if errorlevel 1 goto :fail
echo [build] 0/3 stock DumpReport.dll (%DRSIZE% bytes) backed up to vendor\
:vendor_ok

rem Sources are UTF-8; emit string literals as GBK(936) so log text matches CN Windows.
set "CFLAGS=/nologo /W3 /O2 /MT /GS- /EHa /source-charset:utf-8 /execution-charset:.936 /D_CRT_SECURE_NO_WARNINGS %LOGFLAG% /Foobj\ /Fdobj\"

echo [build] kind: %BUILDKIND%
if "%BUILDKIND%"=="shipping" echo         no logging compiled in, no A5.ini installed

rem --- 1/2 main DLL ---
rem The import library stays in obj\; only the dll goes to the client root.
rem gdi32.lib is not needed: the font module reaches GDI through the host's own
rem import table and GetProcAddress, so A5.dll itself imports nothing from gdi32.
echo [build] 1/2 A5.dll (code patches: unwrap, tersafe stub, font hook)
cl %CFLAGS% /LD A5.cpp a5patch.cpp a5tersafe.cpp a5font.cpp /link /OUT:"%OUT%\A5.dll" /IMPLIB:"obj\A5.lib" /INCREMENTAL:NO kernel32.lib
if errorlevel 1 goto :fail

rem --- 2/3 loader shim ---
rem Replaces the stock DumpReport.dll. Export names/ordinals come from dumpreport.def.
rem No CRT: the shim only calls DisableThreadLibraryCalls and A5Anchor, has no
rem globals needing dynamic init and no exception handling. /NODEFAULTLIB with
rem DllMain as the raw entry point drops it from ~84 KB to a few KB.
echo [build] 2/2 DumpReport.dll (shim -^> A5.dll)
cl %CFLAGS% /LD dumpreport_shim.cpp /link /OUT:"%OUT%\DumpReport.dll" /DEF:dumpreport.def /IMPLIB:"obj\dumpreport.lib" /INCREMENTAL:NO /NODEFAULTLIB /ENTRY:DllMain kernel32.lib "obj\A5.lib"
if errorlevel 1 goto :fail

rem A5.ini is gone. Every switch is a compile-time constant in A5.h now, so a
rem leftover ini in the client folder does nothing -- but it still lists every
rem patch name to anyone who opens it. Say so instead of silently ignoring it.
if exist "%OUT%\A5.ini" (
    echo.
    echo [build] NOTE: "%OUT%\A5.ini" is left over and no longer read by anything.
    echo         Delete it -- it still names every patch to whoever opens it.
)

:done
echo [build] OK -^> "%OUT%"
if "%BUILDKIND%"=="debug" echo         log: "%OUT%\logs\A5.log"
popd
exit /b 0

:fail
echo [build] failed
popd
exit /b 1
