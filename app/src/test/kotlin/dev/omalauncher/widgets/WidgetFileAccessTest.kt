package dev.omalauncher.widgets

import dev.omalauncher.widgets.lang.WidgetError
import dev.omalauncher.widgets.runtime.watchFile
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class WidgetFileAccessTest {
    @get:Rule val temp = TemporaryFolder()
    private fun resolve(path: String, root: File) = resolveWidgetFile(path, { File(root, it.removePrefix("/")) }, listOf(root))

    @Test fun permitsFileWithinApprovedRoot() {
        val root = temp.newFolder()
        val file = File(root, "root/notes").apply { parentFile!!.mkdirs(); writeText("notes") }
        assertEquals(file.canonicalFile, resolve("~/notes", root))
    }
    @Test fun rejectsParentTraversalAndSiblingWithMatchingPrefix() {
        val root = temp.newFolder("linux")
        assertThrows(WidgetError::class.java) { resolve("../secret", root) }
        val sibling = temp.newFolder("linux-other")
        assertThrows(WidgetError::class.java) { resolveWidgetFile("/notes", { File(sibling, "notes") }, listOf(root)) }
    }
    @Test fun replacingPreviouslyAllowedFileWithEscapingLinkIsRejectedOnNextAccess() {
        val root = temp.newFolder("linux")
        val file = File(root, "notes").apply { writeText("allowed") }
        val secret = temp.newFile("secret").apply { writeText("private") }
        assertEquals("allowed", resolve("/notes", root).readText())
        file.delete()
        Files.createSymbolicLink(file.toPath(), secret.toPath())
        assertThrows(WidgetError::class.java) { resolve("/notes", root) }
    }
    @Test fun internalPreviewIsPrivateEvenInsideAnAllowedRootOrThroughALink() {
        val root = temp.newFolder()
        val privateDir = File(root, "widgets/.preview").apply { mkdirs() }
        val preview = File(privateDir, "notifications.png").apply { writeText("private notification") }
        val alias = File(root, "visible.png")
        Files.createSymbolicLink(alias.toPath(), preview.toPath())
        for (path in listOf("/widgets/.preview/notifications.png", "/visible.png")) {
            assertThrows(WidgetError::class.java) {
                resolveWidgetFile(path, { File(root, it.removePrefix("/")) }, listOf(root), listOf(privateDir))
            }
        }
    }
    @Test fun deniedWatcherPathPropagatesAsCatchableFlowError() = runBlocking {
        val seen = mutableListOf<String?>()
        val value = withTimeout(2000) {
            watchFile { throw WidgetError("blocked path") }.catch { seen += it.message; emit("") }.first()
        }
        assertEquals("", value)
        assertEquals(listOf("blocked path"), seen)
    }
}
