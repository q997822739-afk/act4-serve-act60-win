@echo off
rem A4GmTool build script - .NET Framework 4.8 / x86 / Win7 SP1+
rem Produces a portable folder at dist\A4GmTool
setlocal EnableExtensions

set "ROOT=%~dp0"
set "MSBUILD="

if exist "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\MSBuild\Current\Bin\MSBuild.exe" set "MSBUILD=C:\Program Files\Microsoft Visual Studio\2022\Enterprise\MSBuild\Current\Bin\MSBuild.exe"
if not defined MSBUILD if exist "C:\Program Files\Microsoft Visual Studio\2022\Professional\MSBuild\Current\Bin\MSBuild.exe" set "MSBUILD=C:\Program Files\Microsoft Visual Studio\2022\Professional\MSBuild\Current\Bin\MSBuild.exe"
if not defined MSBUILD if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\MSBuild\Current\Bin\MSBuild.exe" set "MSBUILD=C:\Program Files\Microsoft Visual Studio\2022\Community\MSBuild\Current\Bin\MSBuild.exe"
if not defined MSBUILD if exist "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe" set "MSBUILD=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe"

if not defined MSBUILD (
  echo [ERROR] MSBuild.exe not found. Install Visual Studio 2022 or the Build Tools.
  exit /b 1
)

echo [1/3] Building (Release / x86 / .NET Framework 4.8)...
"%MSBUILD%" "%ROOT%src\A4GmTool.Host\A4GmTool.Host.csproj" /m /t:Rebuild /p:Configuration=Release /p:Platform=x86 /nologo /v:m
if errorlevel 1 exit /b 1

echo [2/3] Staging to dist\A4GmTool...
set "DIST=%ROOT%dist\A4GmTool"
set "BIN=%ROOT%src\A4GmTool.Host\bin\Release"
if not exist "%DIST%\web\" mkdir "%DIST%\web" || exit /b 1

for %%F in (A4GmTool.exe A4GmTool.exe.config A4GmTool.Core.dll Newtonsoft.Json.dll e_sqlite3.dll) do (
  copy /y "%BIN%\%%F" "%DIST%\" >nul || exit /b 1
)
copy /y "%ROOT%web\*.*" "%DIST%\web\" >nul || exit /b 1

echo [3/3] Done.
echo.
echo [SUCCESS] %DIST%\A4GmTool.exe
endlocal
