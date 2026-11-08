package com.jettaxp.steamd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jettaxp.steamd.PatchFlow
import com.jettaxp.steamd.R
import com.jettaxp.steamd.patch.Step
import com.jettaxp.steamd.data.SteamApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchSheet(flow: PatchFlow, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (flow == PatchFlow.Idle) return
    val running = flow is PatchFlow.Running
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !running })
    ModalBottomSheet(onDismissRequest = { if (!running) onDismiss() }, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (flow) {
                is PatchFlow.Confirm -> if (flow.update) UpdateContent(onConfirm, onDismiss) else ConfirmContent(onConfirm, onDismiss)
                is PatchFlow.Running -> Steps(flow)
                is PatchFlow.Done -> {
                    Text(stringResource(if (flow.update) R.string.update_done else R.string.patch_done), style = MaterialTheme.typography.titleMedium)
                    val ctx = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { SteamApp.launch(ctx); onDismiss() }) { Text(stringResource(R.string.open_steam)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                }
                is PatchFlow.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.patch_failed, flow.message), style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                PatchFlow.Idle -> Unit
            }
        }
    }
}

@Composable
private fun UpdateContent(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.update_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        Spacer(Modifier.size(8.dp))
        Button(onClick = onConfirm) { Text(stringResource(R.string.continue_)) }
    }
}

@Composable
private fun ConfirmContent(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var saved by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.guard_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.guard_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.uninstall_explain), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier.fillMaxWidth().toggleable(saved, role = Role.Checkbox, onValueChange = { saved = it }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = saved, onCheckedChange = null)
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.guard_confirm))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        Spacer(Modifier.size(8.dp))
        Button(onClick = onConfirm, enabled = saved) { Text(stringResource(R.string.continue_)) }
    }
}

@Composable
private fun Steps(flow: PatchFlow.Running) {
    val steps = Step.entries.filter { it != Step.Uninstall || flow.needsUninstall }
    val current = steps.indexOf(flow.step)
    steps.forEachIndexed { i, step ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                i < current -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                i == current -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                else -> Icon(Icons.Outlined.Circle, null, tint = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(when (step) {
                    Step.Copy -> R.string.step_copy
                    Step.Patch -> R.string.step_patch
                    Step.Sign -> R.string.step_sign
                    Step.Uninstall -> R.string.step_uninstall
                    Step.Install -> R.string.step_install
                }),
                style = MaterialTheme.typography.bodyLarge,
                color = if (i <= current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
