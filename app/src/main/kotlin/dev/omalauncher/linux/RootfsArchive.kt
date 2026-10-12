package dev.omalauncher.linux

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/** Extract only into a new, private staging directory. Links are created last. */
internal object RootfsArchive {
    private const val MAX_ENTRIES = 500_000
    private const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
    private const val MAX_FILE_BYTES = 1024L * 1024 * 1024

    fun extract(input: InputStream, directory: File, mode: (File, Int) -> Unit = { _, _ -> }, progress: (Int) -> Unit = {}, maxTotalBytes: Long = MAX_TOTAL_BYTES) {
        check(!Files.isSymbolicLink(directory.toPath())) { "Linux staging directory cannot be a link" }
        val root = directory.canonicalFile.toPath()
        check(Files.isDirectory(root, NOFOLLOW_LINKS) && directory.list().orEmpty().isEmpty()) { "Linux staging directory must be empty" }
        val links = linkedMapOf<Path, Pair<Path, Boolean>>()
        val linkModes = hashMapOf<Path, Int>()
        val seen = hashSetOf<Path>()
        var total = 0L
        var count = 0

        fun destination(name: String, absoluteGuestLink: Boolean = false): Path {
            check(name.isNotEmpty() && '\u0000' !in name) { "Invalid archive path" }
            check(absoluteGuestLink || !name.startsWith('/')) { "Absolute archive path" }
            check(name.split('/').none { it == ".." }) { "Archive path escapes Linux" }
            val p = root.resolve(name.removePrefix("/")).normalize()
            check(p.startsWith(root)) { "Archive path escapes Linux" }
            return p
        }

        TarArchiveInputStream(GZIPInputStream(input.buffered(64 * 1024))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                check(++count <= MAX_ENTRIES) { "Too many files in Linux archive" }
                val name = entry.name.removePrefix("./").trimEnd('/')
                if (name.isEmpty() || name == ".") continue
                val out = destination(name)
                if (name == "boot" || listOf("boot/", "dev/", "usr/lib/firmware/", "usr/lib/modules/").any(name::startsWith)) continue
                check(out != root && seen.add(out)) { "Duplicate archive path: $name" }
                // No links exist yet. Also reject file/directory collisions and parents reserved for a link.
                var parent = out.parent
                while (parent != root) {
                    check(parent !in links) { "Archive writes through a link" }
                    parent = parent.parent
                }
                Files.createDirectories(out.parent)
                when {
                    entry.isDirectory -> {
                        Files.createDirectories(out)
                        mode(out.toFile(), entry.mode)
                    }
                    entry.isSymbolicLink -> {
                        check(!Files.exists(out, NOFOLLOW_LINKS)) { "Archive link replaces a directory" }
                        val target = if (entry.linkName.startsWith('/')) destination(entry.linkName, true) else out.parent.resolve(entry.linkName).normalize()
                        check(target.startsWith(root) && '\u0000' !in entry.linkName) { "Archive link escapes Linux" }
                        // Store guest-absolute links relatively, so Android-side readers also stay in the rootfs.
                        links[out] = target to true
                    }
                    entry.isLink -> {
                        check(!Files.exists(out, NOFOLLOW_LINKS)) { "Archive link replaces a directory" }
                        links[out] = destination(entry.linkName.removePrefix("./")) to false
                        linkModes[out] = entry.mode
                    }
                    // isFile also returns true for links/devices with names not ending in '/'.
                    // Only materialize regular/sparse files and chmod them after they exist.
                    entry.linkFlag == TarConstants.LF_NORMAL || entry.linkFlag == TarConstants.LF_OLDNORM || entry.isSparse -> {
                        check(entry.size in 0..MAX_FILE_BYTES && entry.size <= maxTotalBytes - total) { "Linux archive exceeds size limits" }
                        total += entry.size
                        Files.newOutputStream(out, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE).use { tar.copyTo(it) }
                        mode(out.toFile(), entry.mode)
                    }
                    else -> continue // No devices, FIFOs, or sockets in Android storage.
                }
                if (count % 500 == 0) progress(count)
            }
        }
        // Hard links are copied before any symbolic link is materialized.
        val hard = links.filterValues { !it.second }.toMutableMap()
        while (hard.isNotEmpty()) {
            val ready = hard.filterValues { Files.isRegularFile(it.first, NOFOLLOW_LINKS) }
            check(ready.isNotEmpty()) { "Archive hard link has no regular file target" }
            for ((out, link) in ready) {
                val size = Files.size(link.first)
                check(size <= maxTotalBytes - total) { "Linux archive hard links exceed size limits" }
                total += size
                Files.copy(link.first, out)
                mode(out.toFile(), linkModes.getValue(out))
                hard.remove(out)
            }
        }
        // Reject cycles or chained symbolic links resolving outside the staging tree.
        for ((out, link) in links.filterValues { it.second }) Files.createSymbolicLink(out, out.parent.relativize(link.first))
        for (out in links.filterValues { it.second }.keys) {
            check(out.toFile().canonicalFile.toPath().startsWith(root)) { "Archive symbolic link escapes Linux" }
        }
    }
}
