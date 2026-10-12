# Security protections and limits

## Linux installation

The archive and its detached signature are downloaded over HTTPS from
`de3.mirror.archlinuxarm.org`. Redirects to HTTP are rejected. The archive is
verified before any archive parsing or guest command execution, using a bundled
public key whose full fingerprint is pinned in code:
`68B3537F39A313B3E574D06777193F152BDBE6A6`. The fingerprint is published by
[Arch Linux ARM](https://archlinuxarm.org/about/package-signing). Missing,
invalid, weak-hash, and wrong-key signatures stop installation. Verification
applies to cached downloads and ADB-supplied images too. Supplied images are
copied into internal storage before verification and extraction; the matching
`.tar.gz.sig` must be supplied beside the original archive.

Extraction uses an empty staging directory, validates paths and link targets,
creates symbolic links only after all ordinary files and hard links, and limits
archive sizes and entry counts, including bytes created by copied hard links.
Absolute guest links are rewritten as equivalent relative links so host-side
readers do not resolve them against Android's root. Hard links are copied
because Android private storage does not permit creating them. The staged rootfs
is activated only after extraction succeeds. Pacman uses HTTPS and requires
package signatures; database signatures remain optional because Arch Linux ARM
does not sign repository databases.

Previously installed environments cannot be authenticated retroactively. A
completed or resumed installation without the verification marker is blocked,
with a removal option and a message explaining the need to back up and
reinstall. No user data is automatically removed by this migration.

## Widgets

New and edited widgets are blocked until reviewed. Permissions are tied to the
SHA-256 of the actual compiled source, not a filename, version, or static list
of commands. Long-press a tile and choose **permissions** to change or revoke
access. Store/shared widgets still require this review after installation.
Unmodified widgets bundled in the APK are trusted as packaged application code,
with notification/media access off; editing them requires review. Explicitly
blocking a bundled widget overrides its default trust.

The host enforces independent grants for web requests, Linux files,
notifications/media, and Linux commands/terminals. File-only widgets can run
with file access while command access remains off. Permission changes are
committed atomically; failed saves are reported without changing the prior
decision. Denied notification data is not returned to the widget. Denied
requests and commands do not reach the host service. Widget file paths must stay
within the approved Linux/launcher content directories, excluding the app's
private approval records. Watched paths are re-resolved before each read, and
rejected paths report a widget error instead of escaping the source coroutine.
Web requests require HTTPS, reject HTTP redirects, and forward only a small
allowlist of public headers to a different host; Authorization, cookies and
custom API keys are stripped. Widget URL actions accept only HTTP(S) browser
links. Package installation requires command approval. Persisted widget state is
scoped to source and grants, so revocation cannot expose previously saved
notification history to a newly restricted instance.

These protections do **not** sandbox arbitrary Linux code. A command grant
trusts the widget with the shared Linux environment, files, credentials and
network. Shell commands can bypass host-side file/web restrictions and can
launch background programs. Revoking a grant stops widget scheduling but cannot
undo effects or guarantee termination of already launched programs. PRoot is not
a security boundary, and processes run under the application's Linux UID. Path
validation does not offer race-proof confinement against native code sharing the
app UID. Stronger isolation needs an Android-enforced execution architecture; it
is not provided by this patch. Approve commands only for code you trust.

Optional AUR packages and coding-agent installers are third-party code. Their
authenticity and behavior are not certified by rootfs verification. Signed
images authenticate their origin, not the absence of vulnerabilities; keep
installed packages current. The release APK must still be obtained from a
trusted publisher, and source review alone cannot prove an unrelated APK
matches.

## Validation

For an APK without notification-listener access, build with
`-PnotificationAccess=false`. This removes the service declaration, disables the
grant/settings link, and limits stored widget grants so older notification access
cannot be reused. Notification and music-session widgets are unavailable in that
build; the launcher's own foreground-service notifications still work. See
[INSTALL_SECURITY_BUILD.md](INSTALL_SECURITY_BUILD.md) for the Play Protect block
and build/install instructions. Removing notification access does not certify
the other application features or guarantee Play Protect acceptance.

`./gradlew :owl:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`

The regression tests cover altered/wrong-key/weak signatures, the bundled key's
fingerprint, HTTP and downgrade rejection, archive traversal and link attacks,
normal Linux links, content-bound grants, and denied host operations.

For the optional real-image smoke test, download the image and its `.sig` using
HTTPS, then set `OMALAUNCHER_ROOTFS_SMOKE` to the archive's absolute path while
running `:app:testDebugUnitTest`. It verifies and extracts the image without
executing it. Extraction needs a case-sensitive filesystem, as the Linux image
contains filenames that differ only in letter case. On macOS, set
`OMALAUNCHER_ROOTFS_QA` to a directory on a case-sensitive test volume.
