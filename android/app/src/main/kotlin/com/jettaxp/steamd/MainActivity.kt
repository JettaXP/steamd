package com.jettaxp.steamd

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jettaxp.steamd.ui.screens.ColorScreen
import com.jettaxp.steamd.ui.screens.HomeScreen
import com.jettaxp.steamd.ui.screens.PatchSheet
import com.jettaxp.steamd.ui.screens.SettingsActions
import com.jettaxp.steamd.ui.screens.SettingsScreen
import com.jettaxp.steamd.ui.theme.SteamdTheme

class MainActivity : AppCompatActivity() {
    private val vm: SteamdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            SteamdTheme(theme = state.appTheme, dynamicColor = state.appDynamicColor) {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val items = listOf(
                                Triple(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
                                Triple(R.string.tab_color, Icons.Filled.Palette, Icons.Outlined.Palette),
                                Triple(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
                            )
                            items.forEachIndexed { i, (label, selectedIcon, icon) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(if (tab == i) selectedIcon else icon, null) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f))
                                .togetherWith(fadeOut(tween(90)))
                        },
                        modifier = Modifier.padding(padding),
                        label = "tabs",
                    ) { current ->
                        when (current) {
                            0 -> HomeScreen(state, onPatch = vm::startPatch, onRefresh = vm::refresh)
                            1 -> ColorScreen(state, onSource = vm::setColorSource)
                            else -> SettingsScreen(
                                state,
                                SettingsActions(
                                    onLanguage = vm::setLanguage, onTheme = vm::setAppTheme, onDynamicColor = vm::setAppDynamicColor,
                                    onStoreTheme = vm::setStoreTheme, onCssUrl = vm::setCssUrl,
                                    onAutoRepatch = vm::setAutoRepatch, onClearOriginals = { vm.clearOriginals() },
                                ),
                            )
                        }
                    }
                }
                PatchSheet(state.flow, onConfirm = vm::runPatch, onDismiss = vm::dismissFlow)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.state.value.flow == PatchFlow.Idle) vm.refresh()
    }
}
