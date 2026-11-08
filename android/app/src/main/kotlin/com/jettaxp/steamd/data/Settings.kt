package com.jettaxp.steamd.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.jettaxp.steamd.patcher.SteamThemePatch

enum class AppTheme { System, Light, Dark }

enum class AppLanguage(val tag: String) { System(""), English("en"), Russian("ru") }

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

    var cssUrl: String
        get() = prefs.getString("css_url", null) ?: SteamThemePatch.DEFAULT_CSS_URL
        set(value) = prefs.edit().putString("css_url", value).apply()

    var appTheme: AppTheme
        get() = runCatching { AppTheme.valueOf(prefs.getString("app_theme", null) ?: "System") }.getOrDefault(AppTheme.System)
        set(value) = prefs.edit().putString("app_theme", value.name).apply()

    var googleSans: Boolean
        get() = prefs.getBoolean("google_sans", true)
        set(value) = prefs.edit().putBoolean("google_sans", value).apply()

    /** Steam tab bar: 1..4 of SteamThemePatch.TAB_NAMES in order. An older "hide news" switch is carried over. */
    var tabs: List<String>
        get() = prefs.getString("tabs", null)?.split(',')?.filter { it in com.jettaxp.steamd.patcher.SteamThemePatch.TAB_NAMES }
            ?.takeIf { it.size in 1..4 }
            ?: if (prefs.getBoolean("hide_news", false)) listOf("store", "guard", "notifications")
            else com.jettaxp.steamd.patcher.SteamThemePatch.DEFAULT_TABS
        set(value) = prefs.edit().putString("tabs", value.joinToString(",")).remove("hide_news").apply()

    var materialIcons: Boolean
        get() = prefs.getBoolean("material_icons", true)
        set(value) = prefs.edit().putBoolean("material_icons", value).apply()

    var autoRepatch: Boolean
        get() = prefs.getBoolean("auto_repatch", true)
        set(value) = prefs.edit().putBoolean("auto_repatch", value).apply()

    var pendingFingerprint: String?
        get() = prefs.getString("pending", null)
        set(value) = prefs.edit().putString("pending", value).apply()

    var lastAuto: String?
        get() = prefs.getString("last_auto", null)
        set(value) = prefs.edit().putString("last_auto", value).apply()

    var appliedFingerprint: String?
        get() = prefs.getString("applied", null)
        set(value) = prefs.edit().putString("applied", value).apply()

    var appDynamicColor: Boolean
        get() = prefs.getBoolean("app_dynamic", true)
        set(value) = prefs.edit().putBoolean("app_dynamic", value).apply()

    companion object {
        val CSS_URL = Regex("https://[\\w.\\-/~%]+\\.css")

        var language: AppLanguage
            get() {
                val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                return when {
                    tag.isEmpty() -> AppLanguage.System
                    tag.startsWith("ru") -> AppLanguage.Russian
                    else -> AppLanguage.English
                }
            }
            set(value) {
                AppCompatDelegate.setApplicationLocales(
                    if (value == AppLanguage.System) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(value.tag),
                )
            }
    }
}
