package dev.omalauncher.linux

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class RootfsArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private fun archive(vararg entries: TarArchiveEntry): ByteArray {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(GZIPOutputStream(out)).use { tar ->
            for (entry in entries) {
                tar.putArchiveEntry(entry)
                if (entry.isFile) tar.write(ByteArray(entry.size.toInt()) { 42 })
                tar.closeArchiveEntry()
            }
        }
        return out.toByteArray()
    }
    private fun regular(name: String) = TarArchiveEntry(name).apply { size = 3 }
    private fun symbolic(name: String, target: String) = TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply { linkName = target }
    private fun hard(name: String, target: String) = TarArchiveEntry(name, TarConstants.LF_LINK).apply { linkName = target }
    private fun extract(bytes: ByteArray): File = temp.newFolder().also { RootfsArchive.extract(bytes.inputStream(), it) }
    @Test fun preservesLinuxLinksAndHardLinks() {
        val root = extract(archive(regular("usr/bin/sh"), symbolic("bin", "/usr/bin"), hard("usr/bin/bash", "usr/bin/sh")))
        assertArrayEquals(File(root, "usr/bin/sh").readBytes(), File(root, "usr/bin/bash").readBytes())
        assertArrayEquals(File(root, "usr/bin/sh").readBytes(), File(root, "bin/sh").readBytes())
        assertFalse(Files.readSymbolicLink(File(root, "bin").toPath()).isAbsolute)
    }
    @Test fun appliesModesOnlyToMaterializedDirectoriesAndFiles() {
        val root = temp.newFolder()
        val modes = mutableListOf<String>()
        val bytes = archive(
            symbolic("bin", "/usr/bin"),
            hard("usr/bin/bash", "usr/bin/sh").apply { mode = 0x1ed },
            TarArchiveEntry("usr/bin/").apply { mode = 0x1ed },
            regular("usr/bin/sh").apply { mode = 0x1ed },
        )
        RootfsArchive.extract(bytes.inputStream(), root, mode = { file, mode ->
            assertTrue("chmod target must already exist: $file", Files.exists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            assertFalse("chmod must not follow a symbolic link", Files.isSymbolicLink(file.toPath()))
            assertEquals(0x1ed, mode)
            modes += file.relativeTo(root.canonicalFile).path
        })
        assertEquals(listOf("usr/bin", "usr/bin/sh", "usr/bin/bash"), modes)
        assertTrue(Files.isSymbolicLink(File(root, "bin").toPath()))
        assertArrayEquals(File(root, "usr/bin/sh").readBytes(), File(root, "usr/bin/bash").readBytes())
    }
    @Test fun doesNotCreateRegularFilesForDeviceOrFifoEntries() {
        val root = extract(archive(
            TarArchiveEntry("pipe", TarConstants.LF_FIFO),
            TarArchiveEntry("device", TarConstants.LF_CHR),
            TarArchiveEntry("disk", TarConstants.LF_BLK),
            regular("ordinary"),
        ))
        for (name in listOf("pipe", "device", "disk")) assertFalse(File(root, name).exists())
        assertTrue(File(root, "ordinary").isFile)
    }
    @Test fun preservesLiteralBackslashInLinuxFilenames() {
        val root = extract(archive(regular("usr/lib/systemd/system/example\\x2dname.slice")))
        assertTrue(File(root, "usr/lib/systemd/system/example\\x2dname.slice").isFile)
    }
    @Test fun countsHardLinkCopiesAgainstTheExpandedSizeLimit() {
        val root = temp.newFolder()
        val bytes = archive(regular("source"), hard("copy1", "source"), hard("copy2", "source"))
        assertThrows(Exception::class.java) { RootfsArchive.extract(bytes.inputStream(), root, maxTotalBytes = 8) }
        assertTrue(File(root, "copy1").exists())
        assertFalse(File(root, "copy2").exists())
    }
    @Test fun rejectsParentTraversalWithoutOverwritingOutsideFile() {
        val outside = temp.newFile("secret").apply { writeText("unchanged") }
        assertThrows(Exception::class.java) { extract(archive(regular("../secret"))) }
        assertEquals("unchanged", outside.readText())
    }
    @Test fun rejectsWritesThroughEarlierSymlink() {
        assertThrows(Exception::class.java) { extract(archive(symbolic("link", "usr"), regular("link/payload"))) }
    }
    @Test fun rejectsSymlinkReplacingExistingParentDirectory() {
        assertThrows(Exception::class.java) { extract(archive(regular("link/payload"), symbolic("link", "usr"))) }
    }
    @Test fun rejectsEscapingSymlink() {
        assertThrows(Exception::class.java) { extract(archive(symbolic("link", "../../secret"))) }
    }
    @Test fun rejectsEscapingHardLink() {
        assertThrows(Exception::class.java) { extract(archive(hard("link", "../secret"))) }
    }
    @Test fun rejectsDuplicateFileEntries() {
        assertThrows(Exception::class.java) { extract(archive(regular("file"), regular("file"))) }
    }
    @Test fun rejectsAbsoluteEntryNames() {
        val entry = TarArchiveEntry("/outside/payload", true).apply { size = 3 }
        assertThrows(Exception::class.java) { extract(archive(entry)) }
    }
    @Test fun rejectsNonEmptyStagingDirectory() {
        val dir = temp.newFolder()
        File(dir, "old").writeText("keep")
        assertThrows(Exception::class.java) { RootfsArchive.extract(archive(regular("file")).inputStream(), dir) }
        assertEquals("keep", File(dir, "old").readText())
    }
}
