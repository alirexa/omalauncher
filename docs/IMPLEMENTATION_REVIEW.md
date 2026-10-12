# Implementation review

Reviewed the security patch against upstream `6bef35117a089f96598d3764e3041e59f141d178`.
The review found and corrected these issues before submission:

- File-only widgets inherited a command-permission requirement from `needsLinux`, unnecessarily requiring approval of arbitrary commands. The gate now distinguishes embedded terminals from file sources; file-only widgets run with commands off.
- Hard-link copies were omitted from the expanded archive byte budget. Each copy is now counted before it is created, with a regression test using a small budget.
- Permission persistence mutated the in-memory records before writing, and failures escaped the UI callback. Updates now use an atomic write followed by a memory commit; failures and stale reviews remain visible in the dialog.
- File watchers resolved paths only once and rejected paths could throw out of the source coroutine. Paths are re-resolved before each read and source errors are caught/reported. Regression tests cover an escaping replacement link and a live widget source error.
- Internal widget previews/error logs were readable under the broad file grant, allowing notification data to leak through cached outputs. Host file access now excludes .preview, .errors, .inbox and .check, including links resolving into those directories.
- Cross-host redirects stripped standard authentication headers but retained custom API credentials. Only public headers are now forwarded across hosts, including a regression test for X-API-Key.

Validation: 104 app tests and 73 owl tests passed (177 total, none skipped), including authentication and extraction of the genuine signed Arch Linux ARM image. Release assembly, release-critical lint and APK signature verification passed.

Full `:app:lintRelease` now passes with zero errors, 57 warnings and two hints. The six upstream RestrictedApi reports were caused by the class-level restriction on `androidx.core.app.ComponentActivity` being applied to the public `Activity.dispatchKeyEvent` callback inherited through `androidx.activity.ComponentActivity`. This was checked against the published source artifacts for core 1.18.0 and activity 1.13.0. Each of the two overrides now has a documented, method-scoped `@SuppressLint("RestrictedApi")`; keyboard routing and superclass dispatch are unchanged. No global lint suppression, baseline or dependency change was added.

The lint follow-up reran full release lint and release assembly successfully. The 177-test result above is from the preceding implementation review; tests were not rerun for the lint annotations. Physical keyboard interaction still needs a phone test.

## Reduced notification-access build

A phone installation attempt showed Play Protect's sensitive-data block. The full APK declares `BIND_NOTIFICATION_LISTENER_SERVICE`; Google's [developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance?hl=en) describes this exact warning for internet-sideloaded notification listeners. That declaration is the likely trigger, though the screenshot alone cannot prove Google's complete assessment.

`-PnotificationAccess=false` builds a separate APK with the listener service removed from both debug and release manifests. It disables the notification/media grant and settings link, and treats previously saved notification grants as off. Effective grants continue to scope persisted widget state. A regression test imports a grant from a full build and verifies that notification/media host operations remain blocked and the reduced build cannot save notification access back on. Launcher, Linux and microphone features are retained. The default full build remains available.

Validation of the reduced build: 104 app tests and 73 owl tests passed (177 passed, no failures/errors); one optional real-rootfs test was skipped, since that image extraction was covered in the preceding review and was not repeated for this feature change. Full release lint passed with zero errors, 57 warnings and two hints. Release assembly and signature verification passed. The packaged APK was checked for absence of the listener service, notification-listener binding permission, SMS permissions and accessibility-service binding permission. Debugging is disabled and the signer matches the preceding fork previews.

This removes the documented sensitive-access trigger; acceptance by Play Protect still requires testing on the phone. Keep Play Protect enabled. Phone notification widgets and other apps' media controls are unavailable in this APK. It remains an experimental build with the Linux trust limitations described below.

## Android extraction regression

Testing on a Samsung SM-G990E running Android 16 reproduced `chmod failed: ENOENT` during extraction. Commons Compress's `TarArchiveEntry.isFile` also returns true for links and special entries whose names do not end in `/`. The extractor deferred link creation but attempted to chmod those missing paths. It now applies modes immediately after creating directories, regular files and hard-link copies, and explicitly excludes device/FIFO entries from regular-file extraction. Symbolic links are never chmodded.

The new mode-callback regression failed before the fix and passes after it. The real signed-image integration test now applies actual POSIX permissions and checks the extracted shell is executable; its previous no-op callback missed this Android failure. Validation: 107 app tests and 73 owl tests passed (180 total, none skipped), including the genuine signed image. Full release lint passed with zero errors, 57 warnings and two hints; release assembly and APK signature verification passed.

The corrected full APK installed through wireless ADB on the phone without changing Play Protect settings. On-device signature verification, extraction, pacman keyring configuration and package installation completed. The app reported Arch Linux installed (2.0 GB). Its embedded terminal returned `aarch64` for `uname -m` and `codex-cli 0.162.1` for `codex --version`; launching Codex reached its interactive sign-in screen. Authentication and authenticated widget generation were not tested. The test used a supplied genuine archive/signature to avoid another full download. The temporary screen-timeout adjustment was restored afterward.

Remaining release gate: physical-phone widget permission-dialog interaction checks before marking an upstream PR ready. Notification/media behavior, physical-keyboard interaction and complete widget generation also remain untested. The patch does not isolate arbitrary approved Linux commands and is not a full security audit of every third-party package or agent.

## Opt-in Codex PRoot compatibility

Further phone use exposed a limit of the earlier sign-in test: Codex launched, but its command runner failed with `cannot establish app-server socket mount isolation`. PRoot cannot supply the namespace/mount isolation required by Codex's Linux sandbox. An explicit opt-in under **Settings → coding agents** now allows sandbox-free Codex commands, with a warning covering shared Linux files, credentials, launcher data, network access and the lack of guaranteed command prompts. It remains off for fresh installs and previous daemon-only configurations.

The mode writes the launcher-owned `/etc/codex/config.toml` atomically, leaves personal/project settings alone, preserves the daemon workaround, and removes only its sandbox/approval defaults when disabled. Configuration changes share the provisioning lock so a simultaneous theme/agent refresh cannot restore a stale setting. Failed saves stay visible without changing the UI state. Changes apply to new sessions; active sessions must be closed/reopened and user config can override system defaults.

Regression coverage exercises default-off migration, enable/reload/disable, malformed settings, personal-config preservation and write failure. Validation: 183 tests passed (110 app and 73 owl), with the optional real-rootfs test skipped; extraction code was unchanged from its preceding genuine-image test. Full release lint: zero errors, 57 warnings and two hints. Release assembly and APK signature verification passed.

On the Samsung SM-G990E, the initial setting was off, the warning/confirmation enabled it, and it persisted through a process restart. Turning it off removed the sandbox/approval fields while retaining `daemon_auto_start = false`. After re-enabling, authenticated Codex 0.162.1 ran `pwd` and read the widget README through its command tool with exit status zero. An interactive session also read README successfully and asked which widget to create. No widget files were modified in this smoke test. Complete widget creation/rendering, widget permission dialogs, notification/media behavior and physical-keyboard interaction remain untested.
