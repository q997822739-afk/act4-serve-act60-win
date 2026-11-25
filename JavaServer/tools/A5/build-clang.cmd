@echo off
rem ============================================================================
rem  ACT5 client patch -- rebuild DumpReport.dll with LLVM-MinGW (no MSVC here)
rem
rem    build-clang.cmd                 rebuild DumpReport.dll (the A5 loader shim
rem                                    + the DLL-extension host)
rem    build-clang.cmd <client dir>    same, and copy the result there
rem
rem  WHY THIS EXISTS
rem    build.bat uses MSVC (cl). This machine has no MSVC, so A5.dll itself cannot
rem    be rebuilt -- its sources use MSVC-only __try and inline asm with MSVC frame
rem    offsets. DumpReport.dll is different: it is just a tiny loader, and it CAN
rem    be rebuilt with LLVM-MinGW. The extension host rides that same chain, so
rem    A5.dll stays byte-for-byte untouched.
rem
rem  WHAT IT PRODUCES
rem    release\DumpReport.dll -- same 3 exports with the same ordinals as before
rem    (ImplicitExceptionHandler @1 / InitStackTraceLibrary @2 /
rem     RegisterExceptionCallback @3), same imports (kernel32 + A5.dll), plus the
rem    extension host that reads dll_ext.ini.
rem
rem  NOTES
rem    - A5.dll is NOT rebuilt and NOT modified. It is pulled in exactly as before
rem      (DumpReport imports A5.dll -> A5Anchor).
rem    - Import library for A5.dll is generated on the fly into obj\A5.lib.
rem    - KEEP THIS FILE PURE ASCII: cmd.exe mis-parses non-ASCII bytes.
rem ============================================================================
setlocal

set "HERE=%~dp0"
set "OUT=%~1"
if "%OUT%"=="" set "OUT=%HERE%release"

rem ---- locate 32-bit LLVM-MinGW (same path the vibration project uses) ----
set "TOOL=C:\Users\12290\AppData\Local\Microsoft\WinGet\Packages\MartinStorsjo.LLVM-MinGW.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe\llvm-mingw-20260602-ucrt-x86_64\bin"
if not exist "%TOOL%\i686-w64-mingw32-gcc.exe" set "TOOL=C:\mingw64\mingw64\bin"
if not exist "%TOOL%\i686-w64-mingw32-gcc.exe" (
    echo [ERROR] no 32-bit i686 toolchain found. Set TOOL in this file.
    exit /b 1
)

if not exist "%OUT%" mkdir "%OUT%"
if not exist "%HERE%obj" mkdir "%HERE%obj"

echo [build] tool: %TOOL%

rem ---- 1) import library for A5.dll (we link against it, we do not build it) ----
if not exist "%HERE%obj\a5.def" (
    echo LIBRARY A5.dll> "%HERE%obj\a5.def"
    echo EXPORTS>> "%HERE%obj\a5.def"
    echo     A5Anchor>> "%HERE%obj\a5.def"
)
"%TOOL%\llvm-dlltool.exe" -m i386 -d "%HERE%obj\a5.def" -l "%HERE%obj\A5.lib"
if errorlevel 1 goto :fail

rem ---- 2) link DumpReport.dll: loader shim + extension host ----
rem   -nostdlib      no CRT (imports stay kernel32 + A5.dll only)
rem   -fno-builtin   do not turn our own loops into CRT calls
rem   -fno-exceptions/-fno-rtti  nothing here throws; keeps the C++ runtime out
rem   -e _DllMain@12 raw entry point so no CRT startup is needed
echo [build] linking DumpReport.dll ...
"%TOOL%\i686-w64-mingw32-gcc.exe" -m32 -O2 -s -shared -nostdlib ^
  -fno-builtin -fno-stack-protector -fno-exceptions -fno-rtti ^
  -Wall -Wextra -Wno-unused-parameter ^
  -o "%OUT%\DumpReport.dll" ^
  "%HERE%dumpreport_shim.cpp" "%HERE%ext_host.cpp" "%HERE%dumpreport.def" ^
  -Wl,-e,_DllMain@12 -L"%HERE%obj" -lA5 -lkernel32
if errorlevel 1 goto :fail

echo [build] OK: %OUT%\DumpReport.dll
for %%F in ("%OUT%\DumpReport.dll") do echo [build] size=%%~zF bytes
echo.
echo [build] next: run verify_dumpreport.py to check exports/ordinals/imports
exit /b 0

:fail
echo [build] FAILED
exit /b 1
