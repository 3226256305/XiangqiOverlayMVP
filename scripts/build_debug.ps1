$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Push-Location $Root
try {
    & "$Root\scripts\bootstrap_wrapper.ps1"
    if (-not (Test-Path "$Root\app\src\main\jniLibs\arm64-v8a\libpikafish.so") -or -not (Test-Path "$Root\app\src\main\assets\pikafish.nnue")) {
        & "$Root\scripts\prepare_assets.ps1"
    }
    if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
        Write-Host "ANDROID_HOME/ANDROID_SDK_ROOT 未设置。Android Studio 安装 SDK 后，通常可设置为：" -ForegroundColor Yellow
        Write-Host '  $env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"'
        throw "未找到 Android SDK 环境变量"
    }
    & "$Root\gradlew.bat" assembleDebug
    $apk = "$Root\app\build\outputs\apk\debug\app-debug.apk"
    Write-Host "APK: $apk" -ForegroundColor Green
} finally { Pop-Location }
