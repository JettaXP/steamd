# steamd for Android

An Android app (Kotlin, Jetpack Compose, Material 3) that patches the Steam app on the device.

## What it does
1. Copies the installed Steam APKs (base + splits) and keeps the originals for re-patching.
2. Patches the theme in the Hermes bundle (`:patcher`, the same logic as `patch/patch_steam.py`).
3. Signs all APKs with its own key (apksig), generated once and kept in app storage.
4. Asks to uninstall the official app (Steam Guard warning first) and installs the patched one
   through `PackageInstaller`.

Colours: the steamd palette, your wallpaper (Android 12+, Material You) or a seed colour.

## Modules
| Module | |
|---|---|
| `:patcher` | Pure Kotlin/JVM: Hermes string table, theme patch, APK rebuild, signing |
| `:app` | Compose UI: Home, Colour (with a preview), About |

## Build
```sh
cd android
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :patcher:test             # unit tests
```
Tests against the real Steam bundle (not in the repository):
```sh
STEAMD_BUNDLE=index.android.bundle STEAMD_EXPECTED=bundle-from-patch_steam.py ./gradlew :patcher:test
```
Screenshots of the screens: `./gradlew :app:recordRoborazziDebug` → `app/build/outputs/roborazzi/`.

Every push to `main` builds the app in GitHub Actions (artifact `steamd-apk`). With the repository secrets
`STEAMD_KEYSTORE_BASE64` (base64 of a PKCS12 keystore, alias `steamd`) and `STEAMD_KEYSTORE_PASSWORD` the APK is a
release build signed with that key, so new builds install over old ones; `versionCode` is the workflow run number.
Without the secrets a debug APK is built.

Local signed build:
```sh
STEAMD_KEYSTORE=steamd-release.p12 STEAMD_KEYSTORE_PASSWORD=... STEAMD_VERSION_CODE=100 ./gradlew :app:assembleRelease
```
