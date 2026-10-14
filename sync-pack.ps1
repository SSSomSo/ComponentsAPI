# =====================================================================
# 同步模组内置资源并重新打包服务端推送资源包
# 权威源：mod/src/main/resources/assets （开发时改这里）
# 输出：  resourcepack/（可直接当资源包用）
#         plugin/pack/capi-pack.zip（打进插件 jar，服务端推送给玩家）
# 用法：  .\sync-pack.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModAssets = Join-Path $Root "mod\src\main\resources\assets"
$PackAssets = Join-Path $Root "resourcepack\assets"
$PackRoot   = Join-Path $Root "resourcepack"
$PluginPack = Join-Path $Root "plugin\pack"
$ZipOut     = Join-Path $PluginPack "capi-pack.zip"

# 1) 同步 assets（模组 jar 与推送资源包保持一致）
if (Test-Path $PackAssets) { Remove-Item $PackAssets -Recurse -Force }
Copy-Item $ModAssets $PackAssets -Recurse -Force
Write-Host "assets synced -> resourcepack/assets"

# 2) 打包 zip（zip 根目录即 pack.mcmeta 与 assets/）
New-Item -ItemType Directory -Force -Path $PluginPack | Out-Null
if (Test-Path $ZipOut) { Remove-Item $ZipOut -Force }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($ZipOut, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $prefixLen = $PackRoot.Length + 1
    Get-ChildItem $PackRoot -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($prefixLen).Replace([char]92, [char]47)
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $rel) | Out-Null
    }
}
finally { $zip.Dispose() }
Write-Host "generated $ZipOut"
Get-ChildItem $ZipOut | Select-Object Name, Length
