package dev.omalauncher.linux

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CodexCompatibilityTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun defaultsOffAndDoesNotMigrateExistingDaemonConfigToFullAccess() {
        val config = File(temp.root, "etc/codex/config.toml")
        val mode = CodexCompatibility(config)
        mode.load()
        assertFalse(mode.enabled)
        config.parentFile!!.mkdirs()
        config.writeText("# Written by the launcher.\n[features]\ndaemon_auto_start = false\n")
        mode.load()
        assertFalse(mode.enabled)
        assertFalse(mode.content().contains("sandbox_mode"))
    }

    @Test fun optInPersistsAndRevocationRemovesOnlyLauncherSandboxDefaults() {
        val config = File(temp.root, "etc/codex/config.toml")
        val personal = File(temp.root, "root/.codex/config.toml")
        personal.parentFile!!.mkdirs()
        personal.writeText("model = \"my-model\"\n")
        val mode = CodexCompatibility(config)
        mode.saveEnabled(true)
        assertTrue(mode.enabled)
        assertTrue(config.readText().contains("sandbox_mode = \"danger-full-access\""))
        assertTrue(config.readText().contains("approval_policy = \"on-request\""))
        val reloaded = CodexCompatibility(config)
        reloaded.load()
        assertTrue(reloaded.enabled)
        reloaded.saveEnabled(false)
        assertFalse(reloaded.enabled)
        assertFalse(config.readText().contains("sandbox_mode"))
        assertFalse(config.readText().contains("approval_policy"))
        assertTrue(config.readText().contains("daemon_auto_start = false"))
        mode.load()
        assertFalse(mode.enabled)
        assertEquals("model = \"my-model\"\n", personal.readText())
    }

    @Test fun damagedSettingDoesNotEnableCompatibility() {
        val config = temp.newFile()
        config.writeText("sandbox_mode = \"danger-full-access\"")
        val mode = CodexCompatibility(config)
        mode.load()
        assertFalse(mode.enabled)
        assertFalse(mode.content().contains("danger-full-access"))
    }

    @Test fun failedRevocationKeepsEnabledStateAndReportsFailure() {
        val config = temp.newFile()
        val mode = CodexCompatibility(config)
        mode.saveEnabled(true)
        assertTrue(config.delete())
        assertTrue(config.mkdir())
        File(config, "block-rename").writeText("occupied")
        assertThrows(Exception::class.java) { mode.saveEnabled(false) }
        assertTrue(mode.enabled)
    }
}
