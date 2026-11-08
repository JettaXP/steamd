package com.jettaxp.steamd.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Category
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
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import com.jettaxp.steamd.ui.components.CheckSwitch
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
import androidx.compose.material3.CircularProgressIndicator
import com.jettaxp.steamd.R
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.animation.core.animateFloatAsState
import com.jettaxp.steamd.UpdateState
import com.jettaxp.steamd.UiState
import com.jettaxp.steamd.data.AppLanguage
import com.jettaxp.steamd.data.AppTheme
import com.jettaxp.steamd.data.ColorSource
import com.jettaxp.steamd.data.Palettes
import com.jettaxp.steamd.patcher.SteamThemePatch

class SettingsActions(
    val onLanguage: (AppLanguage) -> Unit,
    val onTheme: (AppTheme) -> Unit,
    val onDynamicColor: (Boolean) -> Unit,
    val onStoreTheme: (Boolean) -> Unit,
    val onCssUrl: (String) -> Boolean,
    val onAutoRepatch: (Boolean) -> Unit,
    val onGoogleSans: (Boolean) -> Unit = {},
    val onMaterialIcons: (Boolean) -> Unit = {},
    val onHideNews: (Boolean) -> Unit = {},
    val onClearOriginals: () -> Unit,
    val onExportKey: (android.net.Uri) -> Unit = {},
    val onImportKey: (android.net.Uri) -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
)

private enum class Dialog { None, Language, Theme, CssUrl, ClearOriginals, ImportKey }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(Dialog.None) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pkcs12")) { uri ->
        if (uri != null) actions.onExportKey(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.onImportKey(uri)
    }
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
            SwitchItem(Icons.Filled.TextFields, stringResource(R.string.google_sans), stringResource(R.string.google_sans_desc),
                checked = state.googleSans, onChange = actions.onGoogleSans)
            SwitchItem(Icons.Filled.Category, stringResource(R.string.material_icons), stringResource(R.string.material_icons_desc),
                checked = state.materialIcons, onChange = actions.onMaterialIcons)
            SwitchItem(Icons.Filled.VisibilityOff, stringResource(R.string.hide_news), stringResource(R.string.hide_news_desc),
                checked = state.hideNews, onChange = actions.onHideNews)
            Item(Icons.Filled.Link, stringResource(R.string.css_url), state.cssUrl, enabled = state.storeTheme) { dialog = Dialog.CssUrl }
            SwitchItem(
                Icons.Filled.Wallpaper, stringResource(R.string.auto_repatch),
                when {
                    !Palettes.dynamicAvailable() -> stringResource(R.string.dynamic_unavailable)
                    state.colorSource != ColorSource.Wallpaper -> stringResource(R.string.auto_repatch_needs_wallpaper)
                    !state.autoRepatch -> stringResource(R.string.auto_repatch_desc)
                    !com.jettaxp.steamd.install.Notifier.canNotify(LocalContext.current) -> stringResource(R.string.auto_no_notif)
                    else -> autoStatusText(state.autoStatus) ?: stringResource(R.string.auto_repatch_desc)
                },
                checked = state.autoRepatch && Palettes.dynamicAvailable() && state.colorSource == ColorSource.Wallpaper,
                enabled = Palettes.dynamicAvailable() && state.colorSource == ColorSource.Wallpaper,
                onChange = actions.onAutoRepatch,
            )

            Section(R.string.section_storage)
            Item(Icons.Filled.DeleteOutline, stringResource(R.string.originals),
                if (state.originalsBytes > 0) stringResource(R.string.originals_desc, Formatter.formatShortFileSize(context, state.originalsBytes))
                else stringResource(R.string.originals_empty),
                enabled = state.originalsBytes > 0) { dialog = Dialog.ClearOriginals }
            Item(Icons.Filled.Key, stringResource(R.string.signing_key), state.keyFingerprint.ifEmpty { "…" }, onClick = null)
            Item(Icons.Filled.Upload, stringResource(R.string.key_export), stringResource(R.string.key_export_desc)) {
                exportLauncher.launch("steamd-key.p12")
            }
            Item(Icons.Filled.Download, stringResource(R.string.key_import), stringResource(R.string.key_import_desc)) {
                dialog = Dialog.ImportKey
            }

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
            Section(R.string.section_updates)
            UpdateBlock(state.update, state.versionCode, actions)
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
        Dialog.ImportKey -> AlertDialog(
            onDismissRequest = { dialog = Dialog.None },
            icon = { Icon(Icons.Filled.Download, null) },
            title = { Text(stringResource(R.string.key_import)) },
            text = { Text(stringResource(R.string.key_import_warning)) },
            confirmButton = {
                TextButton(onClick = { dialog = Dialog.None; importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.continue_)) }
            },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateBlock(update: UpdateState, versionCode: Long, actions: SettingsActions) {
    val current = stringResource(R.string.update_current, versionCode)
    when (update) {
        UpdateState.Idle, UpdateState.UpToDate -> Item(Icons.Filled.SystemUpdate, stringResource(R.string.update_check),
            if (update == UpdateState.UpToDate) stringResource(R.string.update_latest, current) else current, onClick = actions.onCheckUpdate)
        UpdateState.Checking -> ListItem(
            headlineContent = { Text(stringResource(R.string.update_checking)) },
            supportingContent = { Text(current) },
            leadingContent = { Icon(Icons.Filled.SystemUpdate, null) },
            trailingContent = { CircularWavyProgressIndicator(modifier = Modifier.size(32.dp)) },
        )
        is UpdateState.Available -> Item(Icons.Filled.Download, stringResource(R.string.update_available, update.release.tag),
            stringResource(R.string.update_download, update.release.size / 1_048_576f), onClick = actions.onDownloadUpdate)
        is UpdateState.Downloading -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.update_downloading, update.release.tag), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            val progress by animateFloatAsState(update.progress, label = "update")
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("${(update.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is UpdateState.Ready -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.update_ready, update.release.tag), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = actions.onInstallUpdate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.SystemUpdate, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.update_install))
            }
        }
        is UpdateState.Failed -> Item(Icons.Filled.ErrorOutline, stringResource(R.string.update_failed),
            update.message, onClick = actions.onCheckUpdate)
    }
}

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
        trailingContent = { CheckSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
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

@Composable
private fun autoStatusText(raw: String?): String? {
    if (raw == null) return null
    val code = raw.substringBefore('|')
    val time = raw.substringAfter('|').toLongOrNull() ?: return null
    val whenText = android.text.format.DateUtils.getRelativeTimeSpanString(
        time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val what = when {
        code == "applied" -> stringResource(R.string.auto_applied)
        code == "pending" -> stringResource(R.string.auto_pending)
        code == "same" -> stringResource(R.string.auto_same)
        code.startsWith("error:") -> stringResource(R.string.auto_error, code.removePrefix("error:"))
        else -> return null
    }
    return stringResource(R.string.auto_last, whenText, what)
}
