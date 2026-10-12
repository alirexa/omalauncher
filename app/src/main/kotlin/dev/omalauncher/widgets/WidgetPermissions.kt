package dev.omalauncher.widgets

import dev.omalauncher.BuildConfig
import dev.omalauncher.widgets.lang.Null
import dev.omalauncher.widgets.lang.Value
import dev.omalauncher.widgets.lang.WidgetError
import dev.omalauncher.widgets.runtime.Services
import dev.omalauncher.linux.ShellResult
import dev.omalauncher.upgrade.writeAtomically
import dev.omalauncher.widgets.lang.Program
import org.json.JSONObject
import java.io.File

/** Grants belong to the compiled bytes, never just the name or declared commands. */
data class WidgetGrants(val network: Boolean = false, val files: Boolean = false, val shell: Boolean = false, val notifications: Boolean = false)

internal class WidgetPermissions(
    private val file: File,
    private val notificationAccessEnabled: Boolean = BuildConfig.NOTIFICATION_ACCESS_ENABLED,
    private val persist: (File, ByteArray) -> Unit = { f, bytes -> writeAtomically(f, bytes) },
) {
    private var records = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }

    fun known(name: String, digest: String) = digest.isNotEmpty() && records.optJSONObject(name)?.optString("sha256") == digest

    fun get(name: String, digest: String): WidgetGrants? {
        val r = records.optJSONObject(name) ?: return null
        if (digest.isEmpty() || r.optString("sha256") != digest || r.optBoolean("blocked")) return null
        // Effective grants also scope persisted widget state. On upgrade to a reduced-access
        // build, do not restore state saved while the old notification grant was enabled.
        return WidgetGrants(r.optBoolean("network"), r.optBoolean("files"), r.optBoolean("shell"), notificationAccessEnabled && r.optBoolean("notifications"))
    }

    fun set(name: String, digest: String, grants: WidgetGrants?) {
        val next = JSONObject(records.toString())
        if (grants == null) next.put(name, JSONObject().put("sha256", digest).put("blocked", true)) else next.put(name, JSONObject()
            .put("sha256", digest).put("network", grants.network).put("files", grants.files)
            .put("shell", grants.shell).put("notifications", notificationAccessEnabled && grants.notifications))
        persist(file, next.toString().toByteArray(Charsets.UTF_8))
        records = next
    }
}

/** Linux availability does not imply command permission: file sources also need Linux. */
internal fun requiresWidgetReview(program: Program?, grants: WidgetGrants?) =
    program != null && (grants == null || (program.terminal && !grants.shell))

/** Host-side enforcement. Shell approval explicitly trusts the entire shared Linux environment. */
internal class PermissionServices(private val base: Services, private val grants: WidgetGrants) : Services by base {
    private fun requireGrant(allowed: Boolean, name: String) {
        if (!allowed) throw WidgetError("$name access is off; review this widget's permissions")
    }

    override suspend fun cmd(command: String, timeoutSeconds: Int): ShellResult {
        if (!grants.shell) return ShellResult("", "Linux command permission is off", -1)
        return base.cmd(command, timeoutSeconds)
    }
    override fun run(command: String, done: (ShellResult?) -> Unit) {
        if (!grants.shell) return done(null)
        base.run(command, done)
    }
    override suspend fun stream(command: String, onLine: (String) -> Unit) {
        if (!grants.shell) return
        base.stream(command, onLine)
    }
    override fun terminal(command: String?, fontSize: Double?): dev.omalauncher.ui.EmbeddedTerminal {
        requireGrant(grants.shell, "Linux command")
        return base.terminal(command, fontSize)
    }
    override suspend fun http(url: String, headers: Map<String, String>, timeoutSeconds: Int): ShellResult {
        if (!grants.network) return ShellResult("", "Network permission is off", -1)
        return base.http(url, headers, timeoutSeconds)
    }
    override fun guestFile(path: String): File {
        if (!grants.files) return File("/dev/null")
        return base.guestFile(path)
    }
    override fun fileUpdates(path: String): kotlinx.coroutines.flow.Flow<String> {
        if (!grants.files) return kotlinx.coroutines.flow.flowOf("")
        return base.fileUpdates(path)
    }
    override fun android(name: String): Value =
        if (name in setOf("notifications", "media") && !grants.notifications) Null else base.android(name)
    override fun androidUpdates(name: String): kotlinx.coroutines.flow.Flow<Value>? =
        if (name in setOf("notifications", "media") && !grants.notifications) null else base.androidUpdates(name)
    override fun androidImage(src: String): android.graphics.Bitmap? = if (grants.notifications) base.androidImage(src) else null
    override fun act(name: String, args: List<Value>, done: () -> Unit) {
        val allowed = when (name) {
            "run", "terminal", "app" -> grants.shell
            "notification", "media" -> grants.notifications
            "url" -> grants.network
            else -> true
        }
        if (!allowed) return done()
        base.act(name, args, done)
    }
}
