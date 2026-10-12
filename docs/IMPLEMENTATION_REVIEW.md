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

Full `:app:lintRelease` still reports six RestrictedApi errors in `MainActivity.dispatchKeyEvent` and `LinuxWindowActivity.dispatchKeyEvent`. Those source files and the relevant AndroidX dependency version are unchanged from upstream; these are existing baseline findings, not suppressed by this patch. Full lint is therefore not reported as passing.

Remaining release gate: physical Android installation, Linux boot, and permission-dialog interaction have not been tested on a phone. Do that before marking an upstream PR ready. The patch does not isolate arbitrary approved Linux commands and is not a full security audit of every third-party package or agent.
