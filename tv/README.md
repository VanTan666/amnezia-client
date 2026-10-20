# Amnezia TV — unofficial TV edition

A separate native Android TV target in the Amnezia VPN fork. Based on upstream tag `5.0.3.0`, commit `de93650a90739b87bb47a632872ea9d0adc9412f`.

The UI has three focus targets: Settings → Connect/Disconnect → Server. Version 0.2 uses the upstream dark palette (#0E0E11, #1C1D21, #FBB26A), upstream Amnezia mark and a central circular connect button; slogans and TV EDITION branding were removed. Focus starts on Connect, with a thick apricot ring and a shaded interior. D-pad navigation is explicit. Choosing a server while connected opens a confirmation to disconnect, then shows the server list. Pressing Back/Home leaves the VPN running. The foreground notification provides a disconnect action. Network changes trigger reconnection. Connection attempts have a 25-second timeout and can be cancelled.

## Scope

Supports imported self-hosted `vpn://` AmneziaWG profiles, including AWG 2/3 fields available in the pinned engine. It does **not** provide the full Qt app's OpenVPN/Xray, paid subscriptions, SSH provisioning, protocol editing or split-tunneling UI. Existing official Amnezia is preserved as a separate package. This target has application ID `dev.vantan.amneziatv`, Android 9+, and currently ships `armeabi-v7a` for the Mi Box.

The TV target compiles the upstream Android `protocolApi`, `wireguard`, `awg` and `utils` Kotlin source directories directly. It does not reimplement the VPN cryptography. A small upstream fix prevents unknown/unreadable handshake results from being reported as Connected. Profiles are AES-GCM encrypted with an Android Keystore key, without plaintext fallback. Android backup is disabled. No credentials belong in Git, assets, examples, APK resources, logs or build scripts.

## Build

JDK 17, Android SDK/platform/build-tools 35, Gradle 8.9 and Python 3 required. Set `ANDROID_HOME` or create untracked `local.properties` with `sdk.dir=...`. Run `GRADLE_BIN=/path/to/gradle ./build-and-install.sh`; pass `--install` and `TV=<adb-serial>` to install alongside the official app. The build downloads the pinned official APK, verifies SHA256, extracts and verifies `libwg-go.so`. It does not package any Qt components. A full source build of that engine is documented by the unchanged upstream `recipes/awg-android/conanfile.py` using `amnezia-vpn/amneziawg-android`, tag `v3.1.20260814`; its submodules contain the Go engine source.

Native binary provenance:
- Official APK SHA256: `1fdf920faa8e96eae7b5907dfe2afaaf8db7dc06597d57ebd43495e27a1c0e10`
- `libwg-go.so` SHA256: `3797906c7c12dca61e74800ea6d193f9965ac34e5b60fbe18fe2142be95fe66e`

APK: `app/build/outputs/apk/debug/app-debug.apk`. Default development signing is local Android debug signing. Preserve your local debug keystore to install subsequent updates without erasing profiles. This is an unofficial development build, not an Amnezia-endorsed release.

## Verify

`testDebugUnitTest` covers compressed AWG2 profile retention, unsupported protocols, invalid key material, oversized payload rejection and incomplete configurations. Real-device checks must also cover consent denial, visible focus, server selection, successful handshake, Android VPN state, cancellation, disconnect, and leaving the app with the tunnel active. Unit tests alone do not establish working VPN traffic.

## License

GPL-3.0; see the repository LICENSE and upstream THIRD_PARTY_LICENSES.md. Source for this TV target is in this repository, branch `tv-ui`. The pinned engine is from official Amnezia and its source/recipe are linked above. No original release APK is republished by this target.

## Проверено на Mi Box (Android 9)

20.10.2026: установка отдельным пакетом; импорт ключа AWG2 в Android Keystore; подключение с handshake и Android VPN VALIDATED (UID TV-сборки); отключение с исчезновением VPN-интерфейса; системный отказ в VPN-разрешении; открытие списка серверов и настроек. Скриншоты снимались с реальной приставки. Физическое управление пультом и длительная работа требуют пользовательской проверки. В этот репозиторий ключ, профиль и сетевые дампы не включены.

The Android vectors `amnezia_mark.xml` and `banner.xml` adapt the upstream `client/images/controls/amnezia.svg` path. User-visible app label is Amnezia; the About dialog identifies it as an unofficial build.
