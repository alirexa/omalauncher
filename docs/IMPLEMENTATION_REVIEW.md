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

Remaining release gate: physical Android installation, Linux boot, and permission-dialog interaction have not been tested on a phone. Do that before marking an upstream PR ready. The patch does not isolate arbitrary approved Linux commands and is not a full security audit of every third-party package or agent.
