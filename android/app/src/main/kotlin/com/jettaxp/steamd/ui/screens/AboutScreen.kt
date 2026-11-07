package com.jettaxp.steamd.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jettaxp.steamd.BuildConfigInfo
import com.jettaxp.steamd.R
import com.jettaxp.steamd.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: UiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_about)) })
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Link(Icons.Filled.Language, stringResource(R.string.link_site), "steamd.jettax.xyz") { open("https://steamd.jettax.xyz") }
            Link(Icons.Filled.Code, stringResource(R.string.link_source), "github.com/JettaXP/steamd") { open("https://github.com/JettaXP/steamd") }
            Link(Icons.Filled.BugReport, stringResource(R.string.link_issues), "github.com/JettaXP/steamd/issues") {
                open("https://github.com/JettaXP/steamd/issues")
            }
            Text(
                stringResource(R.string.version, BuildConfigInfo.versionName(context)) + "\n" +
                    stringResource(R.string.key_info, state.keyFingerprint) + "\n" +
                    stringResource(R.string.licenses),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Link(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
