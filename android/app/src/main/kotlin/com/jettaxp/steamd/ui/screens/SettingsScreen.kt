package com.jettaxp.steamd.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jettaxp.steamd.BuildConfigInfo
import com.jettaxp.steamd.R
import com.jettaxp.steamd.UiState
import com.jettaxp.steamd.data.AppLanguage
import com.jettaxp.steamd.data.AppTheme
import com.jettaxp.steamd.data.Palettes
import com.jettaxp.steamd.patcher.SteamThemePatch

class SettingsActions(
    val onLanguage: (AppLanguage) -> Unit,
    val onTheme: (AppTheme) -> Unit,
    val onDynamicColor: (Boolean) -> Unit,
    val onStoreTheme: (Boolean) -> Unit,
    val onCssUrl: (String) -> Boolean,
    val onClearOriginals: () -> Unit,
)

private enum class Dialog { None, Language, Theme, CssUrl, ClearOriginals }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(Dialog.None) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_settings)) })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Section(R.string.section_appearance)
            Item(Icons.Filled.Translate, stringResource(R.string.language), languageLabel(state.language)) { dialog = Dialog.Language }
            Item(Icons.Filled.DarkMode, stringResource(R.string.app_theme), themeLabel(state.appTheme)) { dialog = Dialog.Theme }
            SwitchItem(
                Icons.Filled.ColorLens, stringResource(R.string.app_dynamic_color),
                stringResource(if (Palettes.dynamicAvailable()) R.string.app_dynamic_color_desc else R.string.dynamic_unavailable),
                checked = state.appDynamicColor && Palettes.dynamicAvailable(),
                enabled = Palettes.dynamicAvailable(),
                onChange = actions.onDynamicColor,
            )

            Section(R.string.section_patch)
            SwitchItem(Icons.Filled.Storefront, stringResource(R.string.store_theme), stringResource(R.string.store_theme_desc),
                checked = state.storeTheme, onChange = actions.onStoreTheme)
            Item(Icons.Filled.Link, stringResource(R.string.css_url), state.cssUrl, enabled = state.storeTheme) { dialog = Dialog.CssUrl }

            Section(R.string.section_storage)
            Item(Icons.Filled.DeleteOutline, stringResource(R.string.originals),
                if (state.originalsBytes > 0) stringResource(R.string.originals_desc, Formatter.formatShortFileSize(context, state.originalsBytes))
                else stringResource(R.string.originals_empty),
                enabled = state.originalsBytes > 0) { dialog = Dialog.ClearOriginals }
            Item(Icons.Filled.Key, stringResource(R.string.signing_key), state.keyFingerprint.ifEmpty { "…" }, onClick = null)

            Section(R.string.tab_about)
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Link(Icons.Filled.Public, stringResource(R.string.link_site), "steamd.jettax.xyz") { open("https://steamd.jettax.xyz") }
            Link(Icons.Filled.Code, stringResource(R.string.link_source), "github.com/JettaXP/steamd") { open("https://github.com/JettaXP/steamd") }
            Link(Icons.Filled.BugReport, stringResource(R.string.link_issues), "github.com/JettaXP/steamd/issues") {
                open("https://github.com/JettaXP/steamd/issues")
            }
            Text(
                stringResource(R.string.version, BuildConfigInfo.versionName(context)) + "\n" + stringResource(R.string.licenses),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    when (dialog) {
        Dialog.Language -> ChoiceDialog(
            title = stringResource(R.string.language), icon = Icons.Filled.Language,
            options = AppLanguage.entries, selected = state.language, label = { languageLabel(it) },
            onSelect = { dialog = Dialog.None; if (it != state.language) actions.onLanguage(it) },
            onDismiss = { dialog = Dialog.None },
        )
        Dialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.app_theme), icon = Icons.Filled.DarkMode,
            options = AppTheme.entries, selected = state.appTheme, label = { themeLabel(it) },
            onSelect = { dialog = Dialog.None; actions.onTheme(it) },
            onDismiss = { dialog = Dialog.None },
        )
        Dialog.CssUrl -> CssUrlDialog(state.cssUrl, actions.onCssUrl) { dialog = Dialog.None }
        Dialog.ClearOriginals -> AlertDialog(
            onDismissRequest = { dialog = Dialog.None },
            icon = { Icon(Icons.Filled.DeleteOutline, null) },
            title = { Text(stringResource(R.string.originals_clear_title)) },
            text = { Text(stringResource(R.string.originals_clear_text)) },
            confirmButton = { TextButton(onClick = { dialog = Dialog.None; actions.onClearOriginals() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { dialog = Dialog.None }) { Text(stringResource(R.string.cancel)) } },
        )
        Dialog.None -> Unit
    }
}

@Composable
private fun languageLabel(l: AppLanguage) = when (l) {
    AppLanguage.System -> stringResource(R.string.follow_system)
    AppLanguage.English -> "English"
    AppLanguage.Russian -> "Русский"
}

@Composable
private fun themeLabel(t: AppTheme) = stringResource(when (t) {
    AppTheme.System -> R.string.follow_system
    AppTheme.Light -> R.string.theme_light
    AppTheme.Dark -> R.string.theme_dark
})

@Composable
private fun Section(title: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Item(icon: ImageVector, title: String, value: String, enabled: Boolean = true, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, null) },
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
    )
}

@Composable
private fun SwitchItem(icon: ImageVector, title: String, desc: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(desc) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) },
    )
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

@Composable
private fun <T> ChoiceDialog(
    title: String, icon: ImageVector, options: List<T>, selected: T,
    label: @Composable (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().height(52.dp)
                            .selectable(o == selected, role = Role.RadioButton) { onSelect(o) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = null)
                        Spacer(Modifier.size(16.dp))
                        Text(label(o), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CssUrlDialog(current: String, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Link, null) },
        title = { Text(stringResource(R.string.css_url)) },
        text = {
            Column {
                Text(stringResource(R.string.css_url_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = false }, singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.css_url_error)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (onSave(text)) onDismiss() else error = true }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            TextButton(onClick = { onSave(SteamThemePatch.DEFAULT_CSS_URL); onDismiss() }) { Text(stringResource(R.string.reset)) }
        },
    )
}
