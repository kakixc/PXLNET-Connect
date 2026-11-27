# Xray for Android

PXLNET Connect uses Xray for VLESS XHTTP in the `other` and `play` variants on Android 7.0 and later. Native sing-box connections continue to use libbox. The `otherLegacy` variant does not include Xray.

[`SOURCES.psd1`](SOURCES.psd1) pins Xray-core, AndroidLibXrayLite and GoMobile, along with the Go version and Android ABIs. The build script applies patches for Android socket protection and separate GoMobile Java and JNI namespaces. The XHTTP probe overlays check the response of an XHTTP transport endpoint. A probe result alone does not establish that a VLESS subscription is valid.

## Build the AAR on Windows

Install Git and a Go bootstrap that can select Go 1.26.0. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` and `ANDROID_NDK_HOME` as described in [the build guide](../../BUILDING.md). The first run needs network access to the pinned public repositories and Go modules.

```powershell
pwsh -File .\third_party\xray\build-xray-aar.ps1 -RunName connect-xray
Copy-Item -LiteralPath '.\.build-tools\xray-build\connect-xray\out\pxlnet-xray.aar' -Destination '.\app\libs\pxlnet-xray.aar'
```

Run the commands from the repository root after preparing libbox. If needed, pass `-GoExe`, `-AndroidSdk` and `-AndroidNdk` explicitly. Use a new `-RunName` for a fresh build.

The script builds `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`, verifies the archive and writes `SHA256SUMS.txt` alongside the AAR. Generated files stay under `.build-tools/xray-build/` and `app/libs/`. See [third-party notices](../../THIRD_PARTY_NOTICES.md) for upstream license references.
