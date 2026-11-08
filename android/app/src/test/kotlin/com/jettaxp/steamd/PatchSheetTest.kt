package com.jettaxp.steamd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import com.jettaxp.steamd.patch.Step
import com.jettaxp.steamd.ui.screens.PatchSheet
import com.jettaxp.steamd.ui.theme.SteamdTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PatchSheetTest {
    @get:Rule val compose = createComposeRule()
    private fun str(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test
    fun stepsStayVisibleAfterConfirm() {
        var flow by mutableStateOf<PatchFlow>(PatchFlow.Confirm(update = true))
        compose.setContent { SteamdTheme { PatchSheet(flow, onConfirm = {}, onDismiss = { flow = PatchFlow.Idle }) } }
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.update_title)).assertIsDisplayed()
        flow = PatchFlow.Running(Step.Copy, needsUninstall = false)
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.step_copy)).assertIsDisplayed()
        flow = PatchFlow.Running(Step.Install, needsUninstall = false)
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.step_install)).assertIsDisplayed()
    }
}
