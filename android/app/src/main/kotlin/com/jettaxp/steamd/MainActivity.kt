package com.jettaxp.steamd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
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
import com.jettaxp.steamd.ui.screens.AboutScreen
import com.jettaxp.steamd.ui.screens.ColorScreen
import com.jettaxp.steamd.ui.screens.HomeScreen
import com.jettaxp.steamd.ui.screens.PatchSheet
import com.jettaxp.steamd.ui.theme.SteamdTheme

class MainActivity : ComponentActivity() {
    private val vm: SteamdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SteamdTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val items = listOf(
                                Triple(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
                                Triple(R.string.tab_color, Icons.Filled.Palette, Icons.Outlined.Palette),
                                Triple(R.string.tab_about, Icons.Filled.Info, Icons.Outlined.Info),
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
                    val m = Modifier.padding(padding)
                    when (tab) {
                        0 -> HomeScreen(state, onPatch = vm::startPatch, onRefresh = vm::refresh, modifier = m)
                        1 -> ColorScreen(state, onSource = vm::setColorSource, onStoreTheme = vm::setStoreTheme, modifier = m)
                        else -> AboutScreen(state, modifier = m)
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
