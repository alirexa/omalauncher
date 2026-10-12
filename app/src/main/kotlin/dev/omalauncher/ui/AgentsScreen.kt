package dev.omalauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.omalauncher.Runtime
import dev.omalauncher.linux.AGENTS
import dev.omalauncher.linux.Agent
import dev.omalauncher.linux.JobState
import dev.omalauncher.linux.CodexCompatibility
import kotlinx.coroutines.launch

/** Install and launch coding agents (Claude Code, OpenCode, Codex, Grok, ...). */
@Composable
fun AgentsScreen(
    runtime: Runtime,
    onDismiss: () -> Unit,
    onOpen: (Agent) -> Unit,
) {
    val store = runtime.agents
    val scope = rememberCoroutineScope()
    OverlayPanel("coding agents", onDismiss, contentWidth = ReadingWidth) {
        BasicText(
            "Installed with mise, like Omarchy. Logins open in your browser; paste keys with the keyboard's clipboard.",
            Modifier.padding(vertical = 8.dp),
            style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (store.installed.any { it.bin == "codex" }) {
                item(key = "codex-compatibility") { CodexCompatibilityRow(store.codexCompatibility, runtime::setCodexCompatibility) }
            }
            items(AGENTS, key = { it.bin }) { agent ->
                AgentRow(
                    agent = agent,
                    installed = agent in store.installed,
                    state = store.states[agent.bin] ?: JobState.Idle,
                    onInstall = { scope.launch { store.install(agent) } },
                    onRemove = { scope.launch { store.remove(agent) } },
                    onOpen = { onOpen(agent) },
                )
            }
        }
    }
}

@Composable
private fun CodexCompatibilityRow(mode: CodexCompatibility, onSave: suspend (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(enabled: Boolean) {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                onSave(enabled)
                confirm = false
            } catch (e: Exception) {
                error = "Could not save: ${e.message}"
            } finally {
                saving = false
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(LauncherStyle.shape(12.dp)).background(Palette.surface).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("Codex PRoot compatibility · ${if (mode.enabled) "on" else "off"}", style = Mono.copy(fontSize = TextSize.Item))
        BasicText(
            "Codex's command sandbox cannot run inside this phone's Linux environment. " +
                "Compatibility mode disables Codex's filesystem and network sandbox. Commands can access shared Linux files, " +
                "agent credentials and launcher data, and may run without asking. Android's app isolation remains; PRoot adds no isolation.",
            style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small),
        )
        BasicText(
            "Applies to new Codex sessions, including widget creation and terminals. Close and reopen Codex after changing this. " +
                "Your own Codex settings may override these defaults. Turning this off does not restrict sessions already running.",
            style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small),
        )
        when {
            saving -> BasicText("saving…", style = Mono.copy(color = Palette.muted))
            mode.enabled -> BracketAction("turn off", Palette.accent) { save(false) }
            confirm -> {
                BasicText("Allow Codex to run without its command sandbox?", style = Mono.copy(color = Palette.red))
                Row {
                    BracketAction("enable", Palette.red) { save(true) }
                    BracketAction("cancel", Palette.muted) { confirm = false }
                }
            }
            else -> BracketAction("turn on", Palette.accent) { confirm = true }
        }
        error?.let { BasicText(it, style = Mono.copy(color = Palette.red, fontSize = TextSize.Small)) }
    }
}

@Composable
private fun AgentRow(
    agent: Agent,
    installed: Boolean,
    state: JobState,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(LauncherStyle.shape(12.dp))
            .background(Palette.surface)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(agent.name, style = Mono.copy(fontSize = TextSize.Item))
                BasicText(agent.description, style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small))
            }
            when {
                state is JobState.Working -> BasicText("${state.action}…", style = Mono.copy(color = Palette.accent))
                installed -> {
                    BracketAction("open", Palette.green, onOpen)
                    BracketAction("remove", Palette.red, onRemove)
                }
                else -> BracketAction("install", Palette.accent, onInstall)
            }
        }
        JobLog(state)
    }
}
