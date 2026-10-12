package dev.omalauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.omalauncher.widgets.WidgetEngine
import dev.omalauncher.widgets.WidgetGrants
import dev.omalauncher.widgets.WidgetSpec

@Composable
internal fun WidgetPermissionsDialog(engine: WidgetEngine, spec: WidgetSpec, dismiss: () -> Unit) {
    var grants by remember(spec) { mutableStateOf(engine.grants(spec) ?: WidgetGrants()) }
    var error by remember(spec) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = dismiss) {
        OverlayPanel("${spec.name} permissions", dismiss) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("Approve only widgets you trust. Any code change requires another review.", style = Mono)
                PermissionRow("Web requests", grants.network) { grants = grants.copy(network = !grants.network) }
                PermissionRow("Linux files, including credentials", grants.files) { grants = grants.copy(files = !grants.files) }
                PermissionRow("Notifications and media", grants.notifications) { grants = grants.copy(notifications = !grants.notifications) }
                PermissionRow("Linux commands and terminals", grants.shell) { grants = grants.copy(shell = !grants.shell) }
                BasicText(
                    "Commands can read and change all Linux files, contact the internet, and affect other widgets. File and web switches cannot constrain approved commands. PRoot does not isolate untrusted programs. Turning commands off cannot undo changes or stop every program already launched.",
                    style = Mono.copy(color = Palette.red, fontSize = TextSize.Small),
                )
                BasicText("Requested operations:", style = Mono)
                for (command in spec.program?.commands.orEmpty()) BasicText(command, style = Mono.copy(fontSize = TextSize.Small))
                BasicText("Packages: " + spec.program?.requires.orEmpty().values.flatten().joinToString().ifEmpty { "none" }, style = Mono.copy(fontSize = TextSize.Small))
                error?.let { BasicText(it, style = Mono.copy(color = Palette.red, fontSize = TextSize.Small)) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BracketAction("save", Palette.accent) { error = engine.setGrants(spec, grants); if (error == null) dismiss() }
                    BracketAction("block widget", Palette.red) { error = engine.setGrants(spec, null); if (error == null) dismiss() }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, checked: Boolean, toggle: () -> Unit) {
    BasicText("${if (checked) "[x]" else "[ ]"} $label", Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 8.dp), style = Mono)
}
