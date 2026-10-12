package dev.omalauncher.linux

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.omalauncher.upgrade.writeAtomically
import java.io.File

/** Opt-in system defaults, never a change to the user's ~/.codex/config.toml. */
class CodexCompatibility(private val config: File) {
    var enabled by mutableStateOf(false)
        private set

    fun load() {
        // Only a complete launcher-written opt-in survives a restart. Missing/edited data fails closed.
        enabled = runCatching { config.readText() == content(true) }.getOrDefault(false)
    }

    fun saveEnabled(value: Boolean) {
        // The config is the persisted setting: a failed write must not change what the UI reports.
        writeAtomically(config, content(value).toByteArray())
        enabled = value
    }

    fun content(): String = content(enabled)

    private fun content(enabled: Boolean): String = buildString {
        append("# Written by the launcher.\n")
        if (enabled) {
            append("# Explicitly enabled PRoot compatibility: no Codex filesystem/network sandbox.\n")
            append("# PRoot is not isolation. Commands can access shared Linux data and credentials.\n")
            append("sandbox_mode = \"danger-full-access\"\n")
            // Retain any applicable approval flow/rules; this is not a promise to prompt per command.
            append("approval_policy = \"on-request\"\n")
        }
        append("[features]\ndaemon_auto_start = false\n")
    }
}
