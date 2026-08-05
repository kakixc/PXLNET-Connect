param(
    [string]$SingBoxDirectory = "$PSScriptRoot\..\third_party\sing-box",
    [string]$GoRoot = $env:GOROOT,
    [string]$AndroidNdkHome = $env:ANDROID_NDK_HOME
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path "$GoRoot\bin\go.exe")) {
    throw "Set GOROOT to a Go SDK containing bin/go.exe."
}
if (-not (Test-Path $AndroidNdkHome)) {
    throw "Set ANDROID_NDK_HOME to an installed Android NDK."
}
if (-not (Test-Path "$SingBoxDirectory\go.mod")) {
    throw "Clone sing-box v1.14.0-beta.7 into third_party/sing-box first."
}

$env:ANDROID_NDK_HOME = $AndroidNdkHome
Push-Location $SingBoxDirectory
try {
    & "$GoRoot\bin\go.exe" run ./cmd/internal/build_libbox -target android -platform android/arm64
    if ($LASTEXITCODE -ne 0) {
        throw "libbox build failed with exit code $LASTEXITCODE"
    }
    Copy-Item -LiteralPath "libbox.aar" -Destination "$PSScriptRoot\..\app\libs\libbox.aar" -Force
    Copy-Item -LiteralPath "libbox-legacy.aar" -Destination "$PSScriptRoot\..\app\libs\libbox-legacy.aar" -Force
} finally {
    Pop-Location
}
