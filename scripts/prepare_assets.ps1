$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Jni = Join-Path $Root "app\src\main\jniLibs\arm64-v8a"
$Assets = Join-Path $Root "app\src\main\assets"
$Tmp = Join-Path $Root ".asset_tmp"
New-Item -Force -ItemType Directory $Jni, $Assets, $Tmp | Out-Null

Write-Host "[1/2] 下载 Pikafish NNUE..."
$nnueUrl = "https://github.com/official-pikafish/Networks/releases/download/master-net/pikafish.nnue"
Invoke-WebRequest -Uri $nnueUrl -OutFile (Join-Path $Assets "pikafish.nnue") -UseBasicParsing

Write-Host "[2/2] 查询 Pikafish 最新 Android ARM64 release..."
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/official-pikafish/Pikafish/releases/latest" -Headers @{"User-Agent"="XiangqiOverlayMVP"}
$asset = $release.assets | Where-Object { $_.name -match '(?i)(android.*arm64|arm64.*android)' } | Select-Object -First 1
if (-not $asset) {
    Write-Host "最新 release 未暴露 Android ARM64 附件。可用附件如下：" -ForegroundColor Yellow
    $release.assets | ForEach-Object { Write-Host "  - $($_.name)" }
    throw "找不到 Android ARM64 release asset。请从 Pikafish GitHub Release/Actions 获取 Pikafish-Android-arm64-universal，放到 $Jni\libpikafish.so"
}

$download = Join-Path $Tmp $asset.name
Write-Host "下载 $($asset.name)"
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $download -UseBasicParsing

$candidate = $null
if ($asset.name -match '\.zip$') {
    $outDir = Join-Path $Tmp "engine"
    if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
    Expand-Archive -Path $download -DestinationPath $outDir -Force
    $candidate = Get-ChildItem -File -Recurse $outDir | Where-Object { $_.Name -match '(?i)pikafish' } | Sort-Object Length -Descending | Select-Object -First 1
} elseif ($asset.name -match '\.(tar\.gz|tgz)$') {
    $outDir = Join-Path $Tmp "engine"
    New-Item -Force -ItemType Directory $outDir | Out-Null
    tar -xf $download -C $outDir
    $candidate = Get-ChildItem -File -Recurse $outDir | Where-Object { $_.Name -match '(?i)pikafish' } | Sort-Object Length -Descending | Select-Object -First 1
} else {
    $candidate = Get-Item $download
}

if (-not $candidate) { throw "下载完成但没找到 Pikafish 可执行文件" }
Copy-Item -Force $candidate.FullName (Join-Path $Jni "libpikafish.so")
Write-Host "完成："
Write-Host "  NNUE -> app\src\main\assets\pikafish.nnue"
Write-Host "  Engine -> app\src\main\jniLibs\arm64-v8a\libpikafish.so"
Write-Host "现在可以构建 APK。" -ForegroundColor Green
