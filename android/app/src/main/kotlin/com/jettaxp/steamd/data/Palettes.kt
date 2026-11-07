package com.jettaxp.steamd.data

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.jettaxp.steamd.patcher.SteamThemePatch
import com.jettaxp.steamd.patcher.SteamThemePatch.Role

/** Where the patch colours come from. */
sealed interface ColorSource {
    data object Default : ColorSource
    data object Wallpaper : ColorSource
    data class Seed(val argb: Int) : ColorSource
}

object Palettes {
    const val DEFAULT_SEED = 0xFF415F91.toInt()

    fun dynamicAvailable() = Build.VERSION.SDK_INT >= 31

    /** Compose colour scheme for a source (used by the app UI and the preview). */
    fun scheme(context: Context, source: ColorSource, dark: Boolean): ColorScheme = when (source) {
        ColorSource.Wallpaper -> if (dynamicAvailable()) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else fromSeed(DEFAULT_SEED, dark)
        ColorSource.Default -> fromSeed(DEFAULT_SEED, dark)
        is ColorSource.Seed -> fromSeed(source.argb, dark)
    }

    /** Material You (tonal spot, 2021 spec) scheme from a seed, same as the website and patch_steam.py. */
    @Suppress("RestrictedApi")
    fun fromSeed(seed: Int, dark: Boolean): ColorScheme {
        val s: DynamicScheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
        val m = MaterialDynamicColors()
        fun c(dc: com.google.android.material.color.utilities.DynamicColor) = Color(dc.getArgb(s))
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = c(m.primary()), onPrimary = c(m.onPrimary()),
            primaryContainer = c(m.primaryContainer()), onPrimaryContainer = c(m.onPrimaryContainer()),
            inversePrimary = c(m.inversePrimary()),
            secondary = c(m.secondary()), onSecondary = c(m.onSecondary()),
            secondaryContainer = c(m.secondaryContainer()), onSecondaryContainer = c(m.onSecondaryContainer()),
            tertiary = c(m.tertiary()), onTertiary = c(m.onTertiary()),
            tertiaryContainer = c(m.tertiaryContainer()), onTertiaryContainer = c(m.onTertiaryContainer()),
            background = c(m.background()), onBackground = c(m.onBackground()),
            surface = c(m.surface()), onSurface = c(m.onSurface()),
            surfaceVariant = c(m.surfaceVariant()), onSurfaceVariant = c(m.onSurfaceVariant()),
            surfaceTint = c(m.surfaceTint()),
            inverseSurface = c(m.inverseSurface()), inverseOnSurface = c(m.inverseOnSurface()),
            error = c(m.error()), onError = c(m.onError()),
            errorContainer = c(m.errorContainer()), onErrorContainer = c(m.onErrorContainer()),
            outline = c(m.outline()), outlineVariant = c(m.outlineVariant()),
            scrim = c(m.scrim()),
            surfaceBright = c(m.surfaceBright()), surfaceDim = c(m.surfaceDim()),
            surfaceContainerLowest = c(m.surfaceContainerLowest()), surfaceContainerLow = c(m.surfaceContainerLow()),
            surfaceContainer = c(m.surfaceContainer()), surfaceContainerHigh = c(m.surfaceContainerHigh()),
            surfaceContainerHighest = c(m.surfaceContainerHighest()),
        )
    }

    /** Patch options for a colour source: Steam's native theme always uses the dark scheme. */
    fun patchOptions(context: Context, source: ColorSource, storeTheme: Boolean): SteamThemePatch.Options {
        if (source == ColorSource.Default) return SteamThemePatch.Options(storeTheme = storeTheme)
        val s = scheme(context, source, dark = true)
        val roles = mapOf(
            Role.Primary to s.primary, Role.Surface to s.surface,
            Role.SurfaceContainerLow to s.surfaceContainerLow, Role.SurfaceContainer to s.surfaceContainer,
            Role.SurfaceContainerHigh to s.surfaceContainerHigh, Role.SecondaryContainer to s.secondaryContainer,
            Role.OnSurface to s.onSurface, Role.OnSurfaceVariant to s.onSurfaceVariant,
            Role.Outline to s.outline, Role.OutlineVariant to s.outlineVariant,
        ).mapValues { it.value.toArgb() }
        return SteamThemePatch.Options(
            palette = SteamThemePatch.paletteFrom(roles),
            primary = SteamThemePatch.hex(s.primary.toArgb()),
            storeTheme = storeTheme,
        )
    }

    /** Preset seeds offered in the UI (same as the website). */
    val PRESETS = listOf(
        0xFF6750A4.toInt(), 0xFF386A20.toInt(), 0xFFB5651D.toInt(), 0xFF984061.toInt(), 0xFF006A6A.toInt(),
    )
}
