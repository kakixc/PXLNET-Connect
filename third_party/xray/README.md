# Pinned Xray Android build input

This directory contains human-reviewable build inputs for the optional Xray
engine used by the XHTTP feasibility work. The Android 24+ Play/Other variants
package its AAR, but no product service or selector starts it yet. The existing
sing-box engine and Android 5 Legacy variant are unchanged.

`SOURCES.psd1` pins all upstream revisions. The three existing patches are intentional:

- Xray must fail closed when Android `VpnService.protect()` cannot protect an
  outbound socket. Continuing would risk routing Xray's own traffic into the
  VPN tunnel.
- AndroidLibXrayLite receives the controller that calls the Android callback.
- A dedicated gomobile Java/JNI namespace prevents collision with libbox's
  `go.*` classes and `libgojni.so`; this patch is a build input, not proof that
  both engines can safely run in the app.

Two additional reviewed Go overlay files under `patches/` add a bounded
XHTTP-endpoint probe. It waits for the Xray endpoint's HTTP response through
the same TLS/REALITY transport and requires its response marker; it never
fetches a third-party website. This measures the transport endpoint, **not**
VLESS credential validity or ICMP. The build script copies only these two
files into the pinned, clean source trees and verifies their hashes. The
Android probe must run in its own process with fail-closed socket protection;
these source files alone do not make the app's ping feature ready.

`build-xray-aar.ps1` clones those exact public revisions, rejects a dirty
source checkout, verifies every patch before applying it, builds one universal
Android AAR for armv7, arm64, x86, and x86_64, then records SHA-256 hashes.
All generated data remains ignored under `.build-tools/xray-build/`.

## Prerequisites

- Git and a Go bootstrap capable of selecting Go 1.26.0.
- Android SDK and NDK, exposed through `ANDROID_HOME`/`ANDROID_SDK_ROOT` and
  `ANDROID_NDK_HOME` respectively.
- Network access to the three public upstream repositories and Go modules.

Run from the repository root:

```powershell
pwsh -File third_party/xray/build-xray-aar.ps1
```

When Go or the Android SDK/NDK are not discoverable from the environment,
pass `-GoExe`, `-AndroidSdk`, and `-AndroidNdk` explicitly. A relative `-GoExe`
path is resolved before the script changes directories.

The script does not delete an existing run directory. Pick a different
`-RunName` after inspecting old output. A successful run writes the AAR and
`SHA256SUMS.txt` below `.build-tools/xray-build/<RunName>/out/`.

A clean four-ABI base run completed on 2026-10-09. A later local build with
the checked-in XHTTP probe overlays produced an AAR with SHA-256
`B7476F38DC2B592951A23C59F3B53028661B132D77ED156D8C6E3629C3898EC0`.
The overlays were hash-compared with the staged Go sources; the archive has
four JNI ABIs and no Java class overlap with either bundled libbox AAR. The
probe AAR is copied to ignored `app/libs/pxlnet-xray.aar` for local builds.
Do not commit any AAR. The updated build script has not yet been rerun from
a fresh checkout/run directory, so that reproducibility gate remains open.

The separate-process/protect tests passed on Android API 24, 36, and 37. The
Play/Other build packages both JNI libraries for four ABIs without duplicate
DEX classes; Android 5 Legacy packages only libbox. Synthetic local
Smart/Legacy XHTTP VPN egress and endpoint-probe tests passed on emulator-5556.
The endpoint response time is not VLESS credential validation or ICMP ping.
Real subscription/phone behavior, DNS/reconnect, picker UI and release checks
remain separate gates before any user APK or release claim.
