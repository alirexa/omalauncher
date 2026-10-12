package dev.omalauncher.linux

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.PosixFilePermission

/** Optional real signed upstream archive smoke test; never executes downloaded code. */
class RootfsIntegrationTest {
    @Test fun verifiesAndExtractsActualArchLinuxArmImage() {
        val path = System.getenv("OMALAUNCHER_ROOTFS_SMOKE")
        assumeTrue("Set OMALAUNCHER_ROOTFS_SMOKE to a downloaded archive", path != null)
        val archive = File(path!!)
        File("$path.sig").inputStream().use { signature ->
            File("src/main/assets/security/archlinuxarm.asc").inputStream().use { key -> RootfsSignature.verify(archive, signature, key) }
        }
        val base = System.getenv("OMALAUNCHER_ROOTFS_QA")?.let { File(it).toPath() }
        val root = (if (base != null) Files.createTempDirectory(base, "verified-linux") else Files.createTempDirectory("verified-linux")).toFile()
        try {
            archive.inputStream().use { input ->
                RootfsArchive.extract(input, root, mode = { file, mode ->
                    assertTrue("Permission target must exist: $file", Files.exists(file.toPath(), NOFOLLOW_LINKS))
                    assertTrue("Do not chmod symbolic links: $file", !Files.isSymbolicLink(file.toPath()))
                    val effective = mode and 0x1ff or if (file.isDirectory) 0x1c0 else 0x180
                    val permissions = PosixFilePermission.entries.filterIndexed { index, _ -> effective and (1 shl (8 - index)) != 0 }.toSet()
                    Files.setPosixFilePermissions(file.toPath(), permissions)
                })
            }
            assertTrue(File(root, "usr/bin/bash").isFile)
            assertTrue(File(root, "usr/bin/bash").canExecute())
            assertTrue(File(root, "etc/pacman.conf").isFile)
        } finally {
            Files.walkFileTree(root.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                    Files.delete(file)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: java.nio.file.Path, exc: java.io.IOException?): java.nio.file.FileVisitResult {
                    Files.delete(dir)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            })
        }
    }
}
