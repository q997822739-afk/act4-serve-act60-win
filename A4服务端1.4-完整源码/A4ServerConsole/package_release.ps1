param([string]$OutputDir = (Join-Path $PSScriptRoot '..\A4ServerRelease'))

$ErrorActionPreference = "Stop"
$outDir = [IO.Path]::GetFullPath($OutputDir)
if (Test-Path -LiteralPath $outDir) {
    throw "Output directory already exists. Choose a new directory with -OutputDir; existing data will not be overwritten."
}

# 源码和客户端按工作区同级目录识别。
$slout = $PSScriptRoot
$msbuild = (Get-ChildItem "C:\Program Files\Microsoft Visual Studio\2022\*\MSBuild\Current\Bin\MSBuild.exe" -File)[0].FullName
$repo = (Get-ChildItem "H:\*\*A4*\pom.xml" -File)[0].Directory.FullName          # 服务端源码目录（zhu yao）
$srcTarget = Join-Path $repo "target\s1a5-0.1.0-SNAPSHOT-all.jar"
$jdk21 = (Get-ChildItem "C:\Dev\java\jdk-21*" -Directory)[0].FullName
$clientSvf = (Get-ChildItem "H:\*\DNF60A4*\Script.pvf" -File)[0].FullName

Write-Host "=== 1. Build A4Launcher (x86, latest with encrypted launcher.ini) ==="
& $msbuild (Join-Path $slout "src\A4Launcher\A4Launcher.csproj") /t:Build /p:Configuration=Release /p:Platform=x86 /v:minimal /nologo
if ($LASTEXITCODE -ne 0) { throw "A4Launcher build failed" }

Write-Host "=== 2. Refresh embedded launcher template ==="
$template = Join-Path $slout "src\A4ServerConsole\EmbeddedTemplates\A4Launcher.exe"
Copy-Item (Join-Path $slout "src\A4Launcher\bin\Release\A4Launcher.exe") $template -Force

Write-Host "=== 3. Build A4ServerConsole (Release) ==="
& $msbuild (Join-Path $slout "A4ServerConsole.sln") /t:Build /p:Configuration=Release /v:minimal /nologo
if ($LASTEXITCODE -ne 0) { throw "MSBuild failed" }
$binDir = Join-Path $slout "src\A4ServerConsole\bin\Release"

Write-Host "=== 4. Build A4GmTool ==="
$gmRoot = Join-Path $PSScriptRoot '..\A4GmTool'
& (Join-Path $gmRoot 'build.cmd')
if ($LASTEXITCODE -ne 0) { throw 'A4GmTool build failed' }

Write-Host "=== 5. Prepare clean release folder: $outDir ==="
New-Item -ItemType Directory -Path $outDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $outDir "server") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $outDir "client") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $outDir "data") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $outDir "logs") | Out-Null

Write-Host "=== 6. Copying Console / GM binaries & SQLite runtimes ==="
Copy-Item (Join-Path $gmRoot 'dist\A4GmTool') (Join-Path $outDir 'A4GmTool') -Recurse
Copy-Item (Join-Path $binDir "A4ServerConsole.exe") $outDir -Force
Copy-Item (Join-Path $binDir "A4ServerConsole.exe.config") $outDir -Force
Copy-Item (Join-Path $binDir "*.dll") $outDir -Force
Copy-Item (Join-Path $binDir "runtimes") (Join-Path $outDir "runtimes") -Recurse -Force

Write-Host "=== 7. Copying Game Server JAR & Script.pvf ==="
Copy-Item $srcTarget (Join-Path $outDir "server\s1a5-0.1.0-SNAPSHOT-all.jar") -Force
Copy-Item $clientSvf (Join-Path $outDir "client\Script.pvf") -Force

Write-Host "=== 8. Generating slim portable JRE 21 via jlink ==="
& (Join-Path $jdk21 "bin\jlink.exe") `
    --add-modules java.base,java.logging,java.sql,java.naming,java.management,java.desktop,java.xml,java.instrument,jdk.unsupported,jdk.crypto.ec,jdk.charsets `
    --strip-debug `
    --no-man-pages `
    --no-header-files `
    --compress=2 `
    --output (Join-Path $outDir "jre")
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

Write-Host "=== 9. Writing deployment guide ==="
Copy-Item (Join-Path $slout "release_guide.txt") (Join-Path $outDir "使用说明.txt") -Force

$totalMB = [math]::Round(((Get-ChildItem $outDir -Recurse -File | Measure-Object Length -Sum).Sum / 1MB), 1)
Write-Host "=== RELEASE PACKAGED OK! Total size: $totalMB MB  -> $outDir ==="
