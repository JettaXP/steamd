package com.jettaxp.steamd

import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.jettaxp.steamd.data.ColorSource
import com.jettaxp.steamd.data.SteamInfo
import com.jettaxp.steamd.ui.screens.SettingsActions
import com.jettaxp.steamd.ui.screens.SettingsScreen
import com.jettaxp.steamd.ui.screens.ColorScreen
import com.jettaxp.steamd.ui.screens.HomeScreen
import com.jettaxp.steamd.ui.theme.SteamdTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val steam = SteamInfo("3.10.11", 10928360, File("base.apk"), emptyList(), "x")
    private val state = UiState(steam = steam, status = com.jettaxp.steamd.patch.SteamStatus.Official, compat = Compat.Supported(96), keyFingerprint = "3f2a9c01b7e4d555")

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { SteamdTheme { Scaffold { p -> androidx.compose.foundation.layout.Box(Modifier.padding(p)) { content() } } } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    @Test fun wavyProgress() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SteamdTheme {
                androidx.compose.material3.Surface {
                    androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                        androidx.compose.material3.LinearWavyProgressIndicator(progress = { 0.45f }, modifier = Modifier.fillMaxWidth())
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
                        androidx.compose.material3.LinearWavyProgressIndicator(progress = { 0.9f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(250)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wavy.png")
    }

    @Test fun home() = shot("home") { HomeScreen(state, {}, {}) }
    private val noop = SettingsActions(onLanguage = {}, onTheme = {}, onDynamicColor = {}, onStoreTheme = {}, onCssUrl = { true }, onAutoRepatch = {}, onClearOriginals = {})

    @Test fun colorSeed() = shot("color-seed") { ColorScreen(state.copy(colorSource = ColorSource.Seed(0xFFB5651D.toInt())), {}) }
    @Test fun colorDefault() = shot("color-default") { ColorScreen(state, {}) }
    @Test fun settings() = shot("settings") { SettingsScreen(state.copy(originalsBytes = 152_000_000), noop) }

    @Test @Config(qualifiers = "ru-w411dp-h891dp-night-xxhdpi")
    fun settingsRu() = shot("settings-ru") { SettingsScreen(state.copy(originalsBytes = 152_000_000), noop) }

    @Test @Config(qualifiers = "ru-w411dp-h891dp-night-xxhdpi")
    fun homeRu() = shot("home-ru") { HomeScreen(state, {}, {}) }

    @Test @Config(qualifiers = "ru-w411dp-h891dp-night-xxhdpi")
    fun colorSeedRu() = shot("color-seed-ru") { ColorScreen(state.copy(colorSource = ColorSource.Seed(0xFFB5651D.toInt())), {}) }

    @Test @Config(qualifiers = "ru-w411dp-h891dp-notnight-xxhdpi")
    fun homeRuLight() = shot("home-ru-light") { HomeScreen(state, {}, {}) }
}
