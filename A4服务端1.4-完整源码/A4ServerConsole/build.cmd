@echo off
setlocal
cd /d "%~dp0"

set "MSBUILD="
for %%E in (Enterprise Professional Community BuildTools) do (
    if not defined MSBUILD if exist "C:\Program Files\Microsoft Visual Studio\2022\%%E\MSBuild\Current\Bin\MSBuild.exe" (
        set "MSBUILD=C:\Program Files\Microsoft Visual Studio\2022\%%E\MSBuild\Current\Bin\MSBuild.exe"
    )
    if not defined MSBUILD if exist "C:\Program Files (x86)\Microsoft Visual Studio\2019\%%E\MSBuild\Current\Bin\MSBuild.exe" (
        set "MSBUILD=C:\Program Files (x86)\Microsoft Visual Studio\2019\%%E\MSBuild\Current\Bin\MSBuild.exe"
    )
)
if not defined MSBUILD (
    where msbuild >nul 2>nul
    if not errorlevel 1 set "MSBUILD=msbuild"
)

if not defined MSBUILD (
    echo [ERROR] MSBuild not found. Please install Visual Studio 2022 (.NET desktop development).
    pause
    exit /b 1
)

echo [INFO] Using MSBuild: %MSBUILD%
"%MSBUILD%" A4ServerConsole.sln /t:Restore /v:minimal
if errorlevel 1 goto :fail
"%MSBUILD%" A4ServerConsole.sln /t:Build /p:Configuration=Release /v:minimal
if errorlevel 1 goto :fail

echo.
echo [OK] Build succeeded! Output:
echo      src\A4ServerConsole\bin\Release\A4ServerConsole.exe
if not defined NO_PAUSE pause
exit /b 0

:fail
echo [ERROR] Build failed.
if not defined NO_PAUSE pause
exit /b 1
