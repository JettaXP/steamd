package com.jettaxp.steamd.data

import android.content.Context

/** Small persistent settings (colour source and options). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("steamd", Context.MODE_PRIVATE)

    var colorSource: ColorSource
        get() = when (prefs.getString("source", "default")) {
            "wallpaper" -> ColorSource.Wallpaper
            "seed" -> ColorSource.Seed(prefs.getInt("seed", Palettes.DEFAULT_SEED))
            else -> ColorSource.Default
        }
        set(value) {
            prefs.edit().apply {
                when (value) {
                    ColorSource.Default -> putString("source", "default")
                    ColorSource.Wallpaper -> putString("source", "wallpaper")
                    is ColorSource.Seed -> { putString("source", "seed"); putInt("seed", value.argb) }
                }
            }.apply()
        }

    var storeTheme: Boolean
        get() = prefs.getBoolean("store_theme", true)
        set(value) = prefs.edit().putBoolean("store_theme", value).apply()
}
