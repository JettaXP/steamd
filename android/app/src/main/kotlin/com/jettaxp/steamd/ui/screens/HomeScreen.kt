package com.jettaxp.steamd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jettaxp.steamd.Compat
import com.jettaxp.steamd.R
import com.jettaxp.steamd.SteamStatus
import com.jettaxp.steamd.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, onPatch: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            windowInsets = TopAppBarDefaults.windowInsets,
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SteamCard(state)
            if (state.status == SteamStatus.Official) GuardCard()
            Spacer(Modifier.height(4.dp))
            val canPatch = state.steam != null && state.compat is Compat.Supported
            Button(onClick = onPatch, enabled = canPatch, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.AutoFixHigh, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (state.status == SteamStatus.Patched) R.string.repatch else R.string.patch))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SteamCard(state: UiState) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val steam = state.steam
            if (steam == null) {
                Text(stringResource(R.string.steam_not_installed), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.steam_not_installed_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(stringResource(R.string.steam_version, steam.versionName), style = MaterialTheme.typography.titleLarge)
            AssistChip(onClick = {}, label = {
                Text(stringResource(when (state.status) {
                    SteamStatus.Patched -> R.string.status_patched
                    SteamStatus.Other -> R.string.status_other
                    else -> R.string.status_official
                }))
            })
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (val c = state.compat) {
                    Compat.Checking -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.checking), style = MaterialTheme.typography.bodyMedium)
                    }
                    is Compat.Supported -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.supported, c.hermesVersion), style = MaterialTheme.typography.bodyMedium)
                    }
                    is Compat.Unsupported -> {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.unsupported, c.reason), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardCard() {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Warning, null)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.guard_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.guard_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
