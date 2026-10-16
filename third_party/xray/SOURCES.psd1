@{
    # All revisions are immutable full Git object IDs. No binary artifacts live
    # in Git; build output is deliberately confined to .build-tools/xray-build.
    Toolchain = @{
        GoVersion = 'go1.26.0'
        AndroidApi = 24
        Architectures = @('arm', 'arm64', '386', 'amd64')
    }
    Sources = @{
        XrayCore = @{
            Url = 'https://github.com/xtls/xray-core.git'
            Commit = '5ca6f4b7d4dc20a881d4330e498892697627ec0c'
            Directory = 'xray-core'
        }
        AndroidLibXrayLite = @{
            Url = 'https://github.com/2dust/AndroidLibXrayLite.git'
            Commit = 'c634d1baea97e94320c0bf6a9cf637369c4f11d4'
            Directory = 'android-lib-xray-lite'
        }
        GoMobile = @{
            Url = 'https://go.googlesource.com/mobile'
            Commit = '6129f5bee9d516e31842c9815bf24f60fa682b6e'
            Directory = 'go-mobile'
        }
    }
    Patches = @(
        'patches/xray-core-fail-closed-vpn-protector.patch',
        'patches/android-lib-xray-fail-closed-controller.patch',
        'patches/go-mobile-xray-relocation.patch'
    )
}
