package com.jettaxp.steamd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jettaxp.steamd.data.ColorSource
import com.jettaxp.steamd.data.Palettes

/** The app itself follows the wallpaper on Android 12+ and the steamd blue elsewhere. */
@Composable
fun SteamdTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val source = if (Palettes.dynamicAvailable()) ColorSource.Wallpaper else ColorSource.Default
    MaterialTheme(colorScheme = Palettes.scheme(context, source, dark), content = content)
}
