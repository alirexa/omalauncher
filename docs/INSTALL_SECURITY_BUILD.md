# Installing the security fork

This fork is an experimental build of Omalauncher with the protections described
in [SECURITY.md](SECURITY.md). It requires an arm64 Android phone with Android 10
or later. It uses the original application ID (`dev.omalauncher`) but its own
signing key, so Android will not accept it as an update to the Play Store build. A reviewed APK signed with the same fork key can replace the earlier security preview without uninstalling it.

1. Export any Linux documents or other app data you want to retain using the
   existing app before uninstalling it. Uninstalling deletes the private Linux
   environment, launcher layout, widget data and local agent credentials.
2. Switch Android's **Default apps → Home app** to your original launcher.
3. Uninstall the original Omalauncher without retaining app data.
4. Download the signed `omalauncher-security.apk` from your fork's release,
   check its published SHA-256 if possible, open it, and allow installation from
   that browser/file manager when Android asks. Keep Play Protect enabled.
5. Open Omalauncher. You can make it the default home app when satisfied.
6. In **Settings → Linux**, install a fresh environment. Download verification
   and extraction occur before the app runs Linux. Coding agents are optional;
   deselect them if you only want Linux.
7. New/shared/store widgets display a blocked tile until you tap **review**.
   Start with permissions off and enable only what the widget needs. File access
   includes Linux credentials. Command access trusts the entire shared Linux
   environment. Long-press a tile → **permissions** to revoke or change grants.

Android microphone and notification-listener access remain separate system
permissions. Leave them off unless you need those features. Granting Android
notification access does not automatically grant it to each widget.

This APK is a signed release build with debugging disabled. Tests validate the
security changes and extraction of an authentic Arch image; a physical-phone
boot/install test is still required. It does not provide isolation for arbitrary
Linux programs or guarantee every third-party package/agent is safe.

## Building and signing your own copy

Fetch the pinned, checksum-verified native dependencies:
`./scripts/fetch-proot.sh`. Install Android SDK 37, NDK 29.0.14206865, CMake 3.22.1,
JDK 25 for Gradle and JDK 17 for the owl module. Point `local.properties` at your
Android SDK. Configure your own ignored `keystore.properties` as documented in
[RELEASING.md](RELEASING.md), then run:

```
./gradlew :owl:test :app:testDebugUnitTest :app:assembleRelease
```

For the prepared local checkout, the private signing key is in
`.local/omalauncher-security.jks`; signing configuration is in
`keystore.properties`. Preserve both privately for future updates. Neither file
is committed or included in the APK. A different key requires another uninstall.
