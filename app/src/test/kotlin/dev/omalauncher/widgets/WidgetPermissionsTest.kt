package dev.omalauncher.widgets

import android.graphics.Bitmap
import dev.omalauncher.linux.ShellResult
import dev.omalauncher.ui.EmbeddedTerminal
import dev.omalauncher.widgets.lang.Null
import dev.omalauncher.widgets.lang.Value
import dev.omalauncher.widgets.lang.Compiler
import dev.omalauncher.widgets.runtime.Services
import dev.omalauncher.widgets.runtime.WidgetInstance
import dev.omalauncher.widgets.runtime.watchFile
import dev.omalauncher.widgets.lang.WidgetError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WidgetPermissionsTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun approvalsSurviveRestartAndExpireWhenCodeChanges() {
        val file = File(temp.root, "permissions.json")
        val permissions = WidgetPermissions(file)
        assertNull(permissions.get("widget.owl", "one"))
        permissions.set("widget.owl", "one", WidgetGrants(network = true))
        assertEquals(WidgetGrants(network = true), WidgetPermissions(file).get("widget.owl", "one"))
        assertNull(permissions.get("widget.owl", "two"))
        permissions.set("widget.owl", "one", null)
        assertTrue(permissions.known("widget.owl", "one")) // explicitly blocked, including bundled widgets
        assertNull(WidgetPermissions(file).get("widget.owl", "one"))
    }
    @Test fun failedSaveDoesNotGrantAccessInMemoryOrOnRestart() {
        val file = File(temp.root, "permissions.json")
        val permissions = WidgetPermissions(file) { _, _ -> throw java.io.IOException("disk full") }
        assertThrows(java.io.IOException::class.java) { permissions.set("widget.owl", "one", WidgetGrants(shell = true)) }
        assertNull(permissions.get("widget.owl", "one"))
        assertNull(WidgetPermissions(file).get("widget.owl", "one"))
    }
    @Test fun failedRevocationKeepsThePreviouslyCommittedDecision() {
        val file = File(temp.root, "permissions.json")
        WidgetPermissions(file).set("widget.owl", "one", WidgetGrants(network = true))
        val permissions = WidgetPermissions(file) { _, _ -> throw java.io.IOException("disk full") }
        assertThrows(java.io.IOException::class.java) { permissions.set("widget.owl", "one", null) }
        assertEquals(WidgetGrants(network = true), permissions.get("widget.owl", "one"))
        assertEquals(WidgetGrants(network = true), WidgetPermissions(file).get("widget.owl", "one"))
    }
    @Test fun fileOnlyWidgetWorksWithoutCommandApproval() {
        val program = Compiler.compile("""widget "Notes" { meta { owl: 4 }; source note = file("~/notes.txt"); Text { text: note } }""")
        assertTrue(program.needsLinux)
        assertFalse(program.terminal)
        assertFalse(requiresWidgetReview(program, WidgetGrants(files = true)))
        assertTrue(requiresWidgetReview(program, null))
    }
    @Test fun embeddedTerminalStillRequiresCommandApproval() {
        val program = Compiler.compile("""widget "Shell" { meta { owl: 2 }; Terminal { } }""")
        assertTrue(program.terminal)
        assertTrue(requiresWidgetReview(program, WidgetGrants(files = true)))
        assertFalse(requiresWidgetReview(program, WidgetGrants(shell = true)))
    }

    private class Spy : Services {
        var calls = 0
        private fun touched() { calls++ }
        override suspend fun cmd(command: String, timeoutSeconds: Int) = ShellResult("ok", "", 0).also { touched() }
        override suspend fun http(url: String, headers: Map<String, String>, timeoutSeconds: Int) = ShellResult("ok", "", 200).also { touched() }
        override fun run(command: String, done: (ShellResult?) -> Unit) { touched(); done(ShellResult("ok", "", 0)) }
        override suspend fun stream(command: String, onLine: (String) -> Unit) { touched(); onLine("ok") }
        override fun android(name: String): Value = Null.also { touched() }
        override fun androidUpdates(name: String): Flow<Value> = flowOf(Null).also { touched() }
        override fun androidImage(src: String): Bitmap? { touched(); return null }
        override fun act(name: String, args: List<Value>, done: () -> Unit) { touched(); done() }
        override fun guestFile(path: String) = File(path).also { touched() }
        override fun fileUpdates(path: String) = flowOf("secret").also { touched() }
        override fun terminal(command: String?, fontSize: Double?): EmbeddedTerminal { touched(); error("not needed") }
    }

    @Test fun deniedOperationsNeverReachTheHost() = runBlocking {
        val spy = Spy()
        val services = PermissionServices(spy, WidgetGrants())
        assertEquals(-1, services.cmd("touch secret", 1).code)
        assertEquals(-1, services.http("https://attacker.example/", emptyMap(), 1).code)
        services.run("command") { assertNull(it) }
        services.stream("command") { fail("denied stream emitted data") }
        services.guestFile("secret")
        services.fileUpdates("secret")
        assertSame(Null, services.android("notifications"))
        assertSame(Null, services.android("media"))
        assertNull(services.androidUpdates("notifications"))
        assertNull(services.androidImage("media:cover"))
        for (action in listOf("run", "app", "terminal", "url", "notification", "media")) services.act(action, emptyList()) {}
        assertThrows(Exception::class.java) { services.terminal(null, null) }
        assertEquals(0, spy.calls)
    }
    @Test fun networkGrantDoesNotGrantFilesOrNotifications() = runBlocking {
        val spy = Spy()
        val services = PermissionServices(spy, WidgetGrants(network = true))
        assertEquals(200, services.http("https://example.com", emptyMap(), 1).code)
        services.guestFile("secret")
        services.android("notifications")
        services.cmd("command", 1)
        assertEquals(1, spy.calls)
    }
    @Test fun rejectedFileSourceReportsAnErrorWithoutCrashingTheWidgetScope() = runBlocking {
        val reported = CompletableDeferred<String>()
        val unhandled = CompletableDeferred<Throwable>()
        val services = object : Services by Spy() {
            override fun fileUpdates(path: String) = watchFile { throw WidgetError("blocked path") }
        }
        val program = Compiler.compile("""widget "Private" { meta { owl: 4 }; source n = file("~/bad"); Text { text: n } }""")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> unhandled.complete(e) })
        val instance = WidgetInstance(program, services, { reported.complete(it) })
        try {
            instance.start(scope) { true }
            assertTrue(withTimeout(2000) { reported.await() }.contains("blocked path"))
            assertFalse(unhandled.isCompleted)
        } finally { scope.cancel(); instance.stop() }
    }
}
