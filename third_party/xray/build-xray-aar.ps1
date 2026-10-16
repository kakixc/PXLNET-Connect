[CmdletBinding()]
param(
    [string]$RunName = 'xray-26.7.28',
    [string]$GoExe = '',
    [string]$AndroidSdk = '',
    [string]$AndroidNdk = $env:ANDROID_NDK_HOME
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Fail([string]$Message) {
    throw "Xray Android build refused: $Message"
}

function Invoke-Checked([string]$Description, [scriptblock]$Command) {
    # Keep a helper's stdout from becoming an accidental return value of
    # Get-PinnedSource. Commands still remain visible to the operator.
    & $Command | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Fail "$Description exited with code $LASTEXITCODE"
    }
}

function Require-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        Fail "required command '$Name' is not available"
    }
    return $command.Source
}

function Assert-CleanGitSource([string]$Directory, [string]$Name) {
    $actual = (& git -C $Directory rev-parse HEAD).Trim()
    if ($actual -ne $Manifest.Sources[$Name].Commit) {
        Fail "$Name resolved to $actual, expected $($Manifest.Sources[$Name].Commit)"
    }
    $status = & git -C $Directory status --porcelain
    if ($status) {
        Fail "$Name is not a clean checkout"
    }
}

function Get-PinnedSource([string]$Name) {
    $source = $Manifest.Sources[$Name]
    $directory = Join-Path $SourceRoot $source.Directory
    if (Test-Path $directory) {
        Fail "run directory already contains $Name; use a new -RunName after inspecting it"
    }
    Invoke-Checked "clone $Name" { git clone --filter=blob:none --no-checkout $source.Url $directory }
    Invoke-Checked "checkout $Name" { git -C $directory checkout --detach $source.Commit }
    Assert-CleanGitSource $directory $Name
    return $directory
}

$RepositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$BuildRoot = Join-Path $RepositoryRoot '.build-tools/xray-build'
$RunRoot = Join-Path $BuildRoot $RunName
$ManifestPath = Join-Path $PSScriptRoot 'SOURCES.psd1'
if (-not (Test-Path -LiteralPath $ManifestPath)) {
    Fail "pinned manifest is missing: $ManifestPath"
}
if (Test-Path -LiteralPath $RunRoot) {
    Fail "run directory already exists: $RunRoot"
}
if ([string]::IsNullOrWhiteSpace($RunName) -or $RunName -match '[\\/:*?"<>|]') {
    Fail 'RunName must be a single safe directory name'
}

$Manifest = Import-PowerShellDataFile -LiteralPath $ManifestPath
if ($Manifest.Toolchain.GoVersion -ne 'go1.26.0') {
    Fail "unexpected Go pin: $($Manifest.Toolchain.GoVersion)"
}
if ($Manifest.Toolchain.Architectures -join ',' -ne 'arm,arm64,386,amd64') {
    Fail 'Android architecture pin was changed'
}

Require-Command 'git' | Out-Null
if ([string]::IsNullOrWhiteSpace($GoExe)) {
    $onPath = Get-Command 'go' -ErrorAction SilentlyContinue
    if ($null -ne $onPath) {
        $GoExe = $onPath.Source
    } else {
        $localBootstrap = Join-Path $RepositoryRoot '.build-tools/go/bin/go.exe'
        if (Test-Path -LiteralPath $localBootstrap) {
            $GoExe = $localBootstrap
        } else {
            Fail 'Go was not found; supply -GoExe or install a Go bootstrap'
        }
    }
}
if (-not (Test-Path -LiteralPath $GoExe)) {
    Fail "Go executable does not exist: $GoExe"
}
$GoExe = (Resolve-Path -LiteralPath $GoExe).Path
if ([string]::IsNullOrWhiteSpace($AndroidSdk)) {
    $AndroidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
}
if ([string]::IsNullOrWhiteSpace($AndroidSdk) -or -not (Test-Path -LiteralPath $AndroidSdk)) {
    Fail 'AndroidSdk is required and must point to an installed Android SDK'
}
if ([string]::IsNullOrWhiteSpace($AndroidNdk) -or -not (Test-Path -LiteralPath $AndroidNdk)) {
    Fail 'ANDROID_NDK_HOME is required and must point to an installed Android NDK'
}

# Keep every Go cache, tool, source checkout and output inside the ignored
# build directory. This avoids silently writing under a developer profile.
New-Item -ItemType Directory -Force -Path $RunRoot | Out-Null
$SourceRoot = Join-Path $RunRoot 'src'
$BinRoot = Join-Path $RunRoot 'bin'
$OutputRoot = Join-Path $RunRoot 'out'
$GoPathRoot = Join-Path $RunRoot 'gopath'
$GoCacheRoot = Join-Path $RunRoot 'gocache'
New-Item -ItemType Directory -Force -Path $SourceRoot, $BinRoot, $OutputRoot, $GoPathRoot, $GoCacheRoot, (Join-Path $GoPathRoot 'pkg/gomobile') | Out-Null

$env:GOPATH = $GoPathRoot
$env:GOMODCACHE = Join-Path $GoPathRoot 'pkg/mod'
$env:GOCACHE = $GoCacheRoot
$env:GOBIN = $BinRoot
$env:GOTOOLCHAIN = $Manifest.Toolchain.GoVersion
$env:GOTELEMETRY = 'off'
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidSdk).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_NDK_HOME = (Resolve-Path -LiteralPath $AndroidNdk).Path
$pathSeparator = [IO.Path]::PathSeparator
$env:PATH = "$BinRoot$pathSeparator$(Split-Path -Parent $GoExe)$pathSeparator$env:PATH"

$goVersion = (& $GoExe version).Trim()
if ($goVersion -notmatch 'go version go1\.26\.0\s') {
    Fail "expected Go 1.26.0 after GOTOOLCHAIN selection, got: $goVersion"
}

$XrayRoot = Get-PinnedSource 'XrayCore'
$AndroidLibRoot = Get-PinnedSource 'AndroidLibXrayLite'
$MobileRoot = Get-PinnedSource 'GoMobile'

$xrayPatch = Join-Path $PSScriptRoot 'patches/xray-core-fail-closed-vpn-protector.patch'
$androidPatch = Join-Path $PSScriptRoot 'patches/android-lib-xray-fail-closed-controller.patch'
$mobilePatch = Join-Path $PSScriptRoot 'patches/go-mobile-xray-relocation.patch'
foreach ($patch in @($xrayPatch, $androidPatch, $mobilePatch)) {
    if (-not (Test-Path -LiteralPath $patch)) { Fail "patch is missing: $patch" }
}
Invoke-Checked 'verify Xray fail-closed patch' { git -C $XrayRoot apply --check --whitespace=error $xrayPatch }
Invoke-Checked 'apply Xray fail-closed patch' { git -C $XrayRoot apply --whitespace=error $xrayPatch }
Invoke-Checked 'verify Android controller patch' { git -C $AndroidLibRoot apply --check --whitespace=error $androidPatch }
Invoke-Checked 'apply Android controller patch' { git -C $AndroidLibRoot apply --whitespace=error $androidPatch }
Invoke-Checked 'verify gomobile relocation patch' { git -C $MobileRoot apply --check --unidiff-zero --whitespace=error $mobilePatch }
Invoke-Checked 'apply gomobile relocation patch' { git -C $MobileRoot apply --unidiff-zero --whitespace=error $mobilePatch }
$probeOverlays = @(
    @{ Source = 'patches/pxlnet_xhttp_probe_core.go'; Target = (Join-Path $XrayRoot 'transport/internet/splithttp/pxlnet_probe.go') },
    @{ Source = 'patches/pxlnet_xhttp_probe_binding.go'; Target = (Join-Path $AndroidLibRoot 'pxlnet_probe.go') }
)
foreach ($overlay in $probeOverlays) {
    $source = Join-Path $PSScriptRoot $overlay.Source
    if (-not (Test-Path -LiteralPath $source)) { Fail "probe overlay is missing: $source" }
    if (Test-Path -LiteralPath $overlay.Target) { Fail "probe target already exists: $($overlay.Target)" }
    Copy-Item -LiteralPath $source -Destination $overlay.Target
    if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath $overlay.Target -Algorithm SHA256).Hash) {
        Fail "probe overlay differs after copy: $($overlay.Target)"
    }
}
Invoke-Checked 'verify Xray patched tree' { git -C $XrayRoot diff --check }
Invoke-Checked 'verify Android library patched tree' { git -C $AndroidLibRoot diff --check }
Invoke-Checked 'verify gomobile patched tree' { git -C $MobileRoot diff --check }

# Build gomobile and gobind from the pinned x/mobile source. We deliberately
# skip `gomobile init`: modern init installs `gobind@latest`, which would make
# the result non-reproducible. The one directory bind needs is created above.
Push-Location $MobileRoot
try {
    Invoke-Checked 'build pinned gobind' { & $GoExe build -trimpath -o (Join-Path $BinRoot 'gobind.exe') ./cmd/gobind }
    Invoke-Checked 'build pinned gomobile' { & $GoExe build -trimpath -o (Join-Path $BinRoot 'gomobile.exe') ./cmd/gomobile }
} finally {
    Pop-Location
}
if (-not (Test-Path -LiteralPath (Join-Path $BinRoot 'gobind.exe'))) { Fail 'pinned gobind was not produced' }
if (-not (Test-Path -LiteralPath (Join-Path $BinRoot 'gomobile.exe'))) { Fail 'pinned gomobile was not produced' }

Push-Location $AndroidLibRoot
try {
    Invoke-Checked 'point Android library at patched Xray source' { & $GoExe mod edit "-replace=github.com/xtls/xray-core=$XrayRoot" }
    Invoke-Checked 'point Android library at patched go-mobile source' { & $GoExe mod edit "-replace=golang.org/x/mobile=$MobileRoot" }
    $aarPath = Join-Path $OutputRoot 'pxlnet-xray.aar'
    $target = 'android/arm,android/arm64,android/386,android/amd64'
    Invoke-Checked 'build four-ABI Xray AAR' {
        & (Join-Path $BinRoot 'gomobile.exe') bind "-target=$target" "-androidapi=$($Manifest.Toolchain.AndroidApi)" -trimpath '-ldflags=-s -w -buildid= -checklinkname=0' -o $aarPath ./
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $aarPath)) { Fail 'gomobile returned success but did not produce an AAR' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($aarPath)
try {
    $entryNames = @($archive.Entries | ForEach-Object FullName)
} finally {
    $archive.Dispose()
}
$expectedEntries = @(
    'jni/armeabi-v7a/libpxlxrayjni.so',
    'jni/arm64-v8a/libpxlxrayjni.so',
    'jni/x86/libpxlxrayjni.so',
    'jni/x86_64/libpxlxrayjni.so'
)
foreach ($entry in $expectedEntries) {
    if ($entry -notin $entryNames) { Fail "AAR is missing required ABI entry: $entry" }
}
if ($entryNames | Where-Object { $_ -like 'jni/*/libgojni.so' }) {
    Fail 'Xray AAR must not contain libgojni.so'
}

# Prove at the archive boundary that Gradle would not merge duplicate classes
# from the Xray companion into either bundled libbox variant. This is a build
# gate, not a Gradle exclusion or stripping workaround.
function Get-AarClassEntries([string]$AarPath) {
    $aar = [IO.Compression.ZipFile]::OpenRead($AarPath)
    try {
        $classes = $aar.Entries | Where-Object { $_.FullName -eq 'classes.jar' } | Select-Object -First 1
        if ($null -eq $classes) { Fail "AAR is missing classes.jar: $AarPath" }
        $stream = $classes.Open()
        try {
            $jar = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read, $false)
            try {
                return @($jar.Entries | Where-Object { $_.FullName.EndsWith('.class') } | ForEach-Object FullName)
            } finally { $jar.Dispose() }
        } finally { $stream.Dispose() }
    } finally { $aar.Dispose() }
}

$xrayClasses = Get-AarClassEntries $aarPath
if ($xrayClasses | Where-Object { $_ -like 'go/*' }) {
    Fail 'Xray AAR must not contain go/* classes'
}
if ($xrayClasses -notcontains 'net/pxlnet/xraybridge/go/Seq.class') {
    Fail 'Xray AAR is missing relocated net.pxlnet.xraybridge.go.Seq'
}
foreach ($libboxName in @('libbox.aar', 'libbox-legacy.aar')) {
    $libboxPath = Join-Path $RepositoryRoot "app/libs/$libboxName"
    if (-not (Test-Path -LiteralPath $libboxPath)) { Fail "required collision reference is missing: $libboxPath" }
    $duplicates = Compare-Object -ReferenceObject (Get-AarClassEntries $libboxPath) -DifferenceObject $xrayClasses -IncludeEqual -ExcludeDifferent |
        Where-Object { $_.SideIndicator -eq '==' } |
        Select-Object -ExpandProperty InputObject
    if ($duplicates) { Fail "Xray class collision with ${libboxName}: $($duplicates -join ', ')" }
}

$hash = (Get-FileHash -LiteralPath $aarPath -Algorithm SHA256).Hash.ToLowerInvariant()
$hashPath = Join-Path $OutputRoot 'SHA256SUMS.txt'
"$hash  pxlnet-xray.aar" | Set-Content -LiteralPath $hashPath -NoNewline -Encoding ascii
$result = [ordered]@{
    xrayCommit = $Manifest.Sources.XrayCore.Commit
    androidLibXrayLiteCommit = $Manifest.Sources.AndroidLibXrayLite.Commit
    goMobileCommit = $Manifest.Sources.GoMobile.Commit
    goVersion = $goVersion
    androidApi = $Manifest.Toolchain.AndroidApi
    abiEntries = $expectedEntries
    aarSha256 = $hash
}
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputRoot 'build-result.json') -Encoding utf8
Write-Host "SUCCESS: $aarPath"
Write-Host "SHA256: $hash"
