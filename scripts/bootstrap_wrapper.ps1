$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Tmp = Join-Path $Root ".gradle_bootstrap"
$Zip = Join-Path $Tmp "gradle.zip"
$Dir = Join-Path $Tmp "gradle"
New-Item -Force -ItemType Directory $Tmp | Out-Null
if (-not (Test-Path (Join-Path $Root "gradle\wrapper\gradle-wrapper.jar"))) {
    Write-Host "下载 Gradle 8.9 以生成 wrapper..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -OutFile $Zip -UseBasicParsing
    if (Test-Path $Dir) { Remove-Item -Recurse -Force $Dir }
    Expand-Archive -Path $Zip -DestinationPath $Dir -Force
    $gradle = Get-ChildItem -Recurse -File $Dir -Filter gradle.bat | Select-Object -First 1
    if (-not $gradle) { throw "未找到 gradle.bat" }
    Push-Location $Root
    & $gradle.FullName wrapper --gradle-version 8.9
    Pop-Location
}
Write-Host "Gradle wrapper 就绪。" -ForegroundColor Green
