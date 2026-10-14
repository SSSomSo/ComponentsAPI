# =====================================================================
# ComponentsAPI 一键构建脚本（插件 + 模组）
# 用法：在本目录打开 PowerShell，执行  .\build-all.ps1
# 只构建模组：.\build-all.ps1 -Mod
# 只构建插件：.\build-all.ps1 -Plugin
# =====================================================================
param(
    [switch]$Mod,
    [switch]$Plugin,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

# —— 固定环境（你机器上的路径）——
$env:JAVA_HOME = "C:\Users\mxbc6\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon"
$Maven = "C:\Users\mxbc6\buildtools\maven\apache-maven-3.9.16\bin\mvn.cmd"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

$doMod = $Mod -or (-not $Plugin)
$doPlugin = $Plugin -or (-not $Mod)

if ($doPlugin) {
    Write-Host "`n=== 构建 Paper 插件 (plugin/) ===" -ForegroundColor Cyan
    & $Maven -f "$Root\plugin\pom.xml" clean package "-Dmaven.test.skip=$SkipTests"
    if ($LASTEXITCODE -ne 0) { Write-Host "插件构建失败" -ForegroundColor Red; exit 1 }
}

if ($doMod) {
    Write-Host "`n=== 构建 Fabric 客户端模组 (mod/) ===" -ForegroundColor Cyan
    Push-Location "$Root\mod"
    try {
        .\gradlew.bat clean build --console=plain
        if ($LASTEXITCODE -ne 0) { Write-Host "模组构建失败" -ForegroundColor Red; exit 1 }
    }
    finally { Pop-Location }
}

Write-Host "`n=== 构建产物 ===" -ForegroundColor Green
if ($doPlugin) {
    Get-ChildItem "$Root\plugin\target\ComponentsAPI-*.jar" |
        Where-Object { $_.Name -notlike "*-sources*" -and $_.Name -notlike "*-javadoc*" } |
        ForEach-Object { Write-Host ("插件: " + $_.FullName) -ForegroundColor Yellow }
}
if ($doMod) {
    Get-ChildItem "$Root\mod\build\libs\*.jar" |
        Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*-dev*" } |
        ForEach-Object { Write-Host ("模组: " + $_.FullName) -ForegroundColor Yellow }
}
Write-Host "`n完成。" -ForegroundColor Green
