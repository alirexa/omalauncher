package dev.omalauncher.linux

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
            archive.inputStream().use { RootfsArchive.extract(it, root) }
            assertTrue(File(root, "usr/bin/bash").isFile)
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
