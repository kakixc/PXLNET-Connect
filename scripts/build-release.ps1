param(
    [ValidateSet("other", "play")]
    [string]$Flavor = "other"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretFile = Join-Path $env:USERPROFILE ".android\pxlnet-release.secret"
$keyStoreFile = Join-Path $env:USERPROFILE ".android\pxlnet-release.jks"

if (-not (Test-Path -LiteralPath $secretFile)) {
    throw "Release secret not found: $secretFile"
}
if (-not (Test-Path -LiteralPath $keyStoreFile)) {
    throw "Release keystore not found: $keyStoreFile"
}

$securePassword = Get-Content -Raw -LiteralPath $secretFile | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$previousLocalProperties = $env:LOCAL_PROPERTIES

try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $escapedStorePath = $keyStoreFile.Replace("\", "\\")
    $properties = @(
        "KEYSTORE_FILE=$escapedStorePath"
        "KEYSTORE_PASS=$password"
        "ALIAS_NAME=pxlnet-release"
        "ALIAS_PASS=$password"
    ) -join "`n"
    $env:LOCAL_PROPERTIES = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($properties))

    $variant = $Flavor.Substring(0, 1).ToUpperInvariant() + $Flavor.Substring(1)
    & (Join-Path $projectRoot "gradlew.bat") ":app:assemble${variant}Release"
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle release build failed with exit code $LASTEXITCODE"
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $password = $null
    if ($null -eq $previousLocalProperties) {
        Remove-Item Env:LOCAL_PROPERTIES -ErrorAction SilentlyContinue
    } else {
        $env:LOCAL_PROPERTIES = $previousLocalProperties
    }
}
