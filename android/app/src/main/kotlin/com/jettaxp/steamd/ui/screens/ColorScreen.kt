package com.jettaxp.steamd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jettaxp.steamd.R
import com.jettaxp.steamd.UiState
import com.jettaxp.steamd.data.ColorSource
import com.jettaxp.steamd.data.Palettes
import com.google.android.material.color.utilities.Hct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorScreen(
    state: UiState,
    onSource: (ColorSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val source = state.colorSource
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_color)) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.color_source), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val options = listOf(R.string.color_default, R.string.color_dynamic, R.string.color_seed)
            val selected = when (source) { ColorSource.Default -> 0; ColorSource.Wallpaper -> 1; is ColorSource.Seed -> 2 }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = selected == i,
                        enabled = i != 1 || Palettes.dynamicAvailable(),
                        onClick = {
                            onSource(when (i) {
                                0 -> ColorSource.Default
                                1 -> ColorSource.Wallpaper
                                else -> source as? ColorSource.Seed ?: ColorSource.Seed(Palettes.PRESETS.first())
                            })
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(stringResource(label)) }
                }
            }
            if (!Palettes.dynamicAvailable()) {
                Text(stringResource(R.string.dynamic_unavailable), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (source == ColorSource.Wallpaper) {
                Text(stringResource(R.string.dynamic_note), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (source is ColorSource.Seed) SeedPicker(source.argb) { onSource(ColorSource.Seed(it)) }

            Text(stringResource(R.string.preview), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val scheme = remember(source) { Palettes.scheme(context, source, dark = true) }
            SteamPreview(scheme)

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SeedPicker(seed: Int, onSeed: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Palettes.PRESETS.forEach { c ->
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(c)).clickable { onSeed(c) }
                    .then(if (c == seed) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { if (c == seed) Icon(Icons.Filled.Check, null, tint = Color.White) }
        }
    }
    @Suppress("RestrictedApi")
    val hct = remember(seed) { Hct.fromInt(seed) }
    Text(stringResource(R.string.hue), style = MaterialTheme.typography.labelLarge)
    @Suppress("RestrictedApi")
    Slider(
        value = hct.hue.toFloat(),
        onValueChange = { h -> onSeed(Hct.from(h.toDouble(), 48.0, 50.0).toInt()) },
        valueRange = 0f..360f,
    )
}

@Composable
private fun SteamPreview(s: ColorScheme) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(s.surface).padding(top = 16.dp),
    ) {
        Text(stringResource(R.string.preview_menu), color = s.onSurface, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        val rows = listOf(
            Icons.Filled.Notifications to R.string.preview_notifications,
            Icons.Filled.Done to R.string.preview_confirmations,
            Icons.Filled.LibraryBooks to R.string.preview_library,
        )
        rows.forEachIndexed { i, (icon, label) ->
            Row(Modifier.fillMaxWidth().background(s.surfaceContainer).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = s.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(label), color = s.onSurface)
            }
            if (i < rows.lastIndex) HorizontalDivider(color = s.outlineVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(s.primary).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Steam Guard", color = s.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(s.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.preview_shop), color = s.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().background(s.surfaceContainerHigh).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround) {
            NavDot(Icons.Filled.Newspaper, stringResource(R.string.preview_news), false, s)
            NavDot(Icons.Filled.Shield, "Guard", true, s)
            NavDot(Icons.Filled.Sell, stringResource(R.string.preview_shop), false, s)
            NavDot(Icons.Filled.Menu, stringResource(R.string.preview_menu), false, s)
        }
    }
}

@Composable
private fun NavDot(icon: ImageVector, label: String, active: Boolean, s: ColorScheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) s.secondaryContainer else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) { Icon(icon, null, tint = if (active) s.onSecondaryContainer else s.onSurfaceVariant) }
        Text(label, color = if (active) s.onSurface else s.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}
