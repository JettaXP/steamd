package com.jettaxp.steamd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jettaxp.steamd.data.AppTheme
import com.jettaxp.steamd.data.ColorSource
import com.jettaxp.steamd.data.Palettes

@Composable
fun SteamdTheme(
    theme: AppTheme = AppTheme.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (theme) { AppTheme.System -> isSystemInDarkTheme(); AppTheme.Light -> false; AppTheme.Dark -> true }
    val source = if (dynamicColor && Palettes.dynamicAvailable()) ColorSource.Wallpaper else ColorSource.Default
    MaterialTheme(colorScheme = Palettes.scheme(context, source, dark), content = content)
}
