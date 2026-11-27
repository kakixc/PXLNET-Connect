# Build PXLNET Connect on Windows

[English README](README.md) · [Русский README](README.ru.md)

## Requirements

- Git, PowerShell 7 and JDK 21. Use the checked-in Gradle wrapper.
- Android SDK with the Android 37.1 platform and NDK `28.0.13004108`.
- Go `1.25.12` for libbox. The Xray build script selects Go `1.26.0` using an installed Go bootstrap.
- Network access for a first build to fetch the submodule, Gradle dependencies, pinned native sources and Go modules.

## Prepare the native libraries

Run from the repository root. Set `JAVA_HOME` to your JDK and replace the SDK and Go paths with your own.

```powershell
git submodule update --init --recursive
git clone --branch v1.14.0-beta.7 --single-branch https://github.com/SagerNet/sing-box.git third_party/sing-box
git -C third_party/sing-box apply ../../patches/sing-box-pxlnet.patch
git -C third_party/sing-box apply ../../patches/sing-box-server-probe.patch
git -C third_party/sing-box apply ../../patches/sing-box-preconnect-probe.patch

$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:ANDROID_NDK_HOME = Join-Path $env:ANDROID_HOME 'ndk\28.0.13004108'
$env:GOROOT = 'C:\Tools\Go-1.25.12'
$env:GOPATH = Join-Path (Get-Location).Path '.build-tools\gopath'
$env:GOCACHE = Join-Path (Get-Location).Path '.build-tools\gocache'
$env:GOTOOLCHAIN = 'local'
$env:Path = "$(Join-Path $env:GOROOT 'bin');$(Join-Path $env:GOPATH 'bin');$env:Path"

$go = Join-Path $env:GOROOT 'bin\go.exe'
& $go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12 github.com/sagernet/gomobile/cmd/gobind@v0.1.12
.\scripts\build-libbox.ps1

pwsh -File .\third_party\xray\build-xray-aar.ps1 -GoExe $go -RunName connect-xray
Copy-Item -LiteralPath '.\.build-tools\xray-build\connect-xray\out\pxlnet-xray.aar' -Destination '.\app\libs\pxlnet-xray.aar'
```

Apply the sing-box patches once to a fresh upstream checkout. The libbox script places `libbox.aar` and `libbox-legacy.aar` in `app/libs/`. `other` and `play` use `libbox.aar` plus `pxlnet-xray.aar`. `otherLegacy` uses `libbox-legacy.aar` only. See the [pinned Xray build instructions](third_party/xray/README.md) for additional options. Keep local SDK settings and generated AAR files out of commits.

## Build and test the APK

```powershell
.\gradlew.bat :app:testOtherDebugUnitTest :app:assembleOtherDebug
```

Debug APKs are written under `app/build/outputs/apk/other/debug/`. The debug package has the `.focuslab` suffix. Use an emulator or a test device for local verification.

For a signed release build, configure your own signing settings locally and run `:app:lintVitalOtherRelease :app:assembleOtherRelease`. The shipped APK is signed with the project's release key. Android requires a compatible signing certificate to install an update over an existing copy. Keep signing material and credentials outside version control.
