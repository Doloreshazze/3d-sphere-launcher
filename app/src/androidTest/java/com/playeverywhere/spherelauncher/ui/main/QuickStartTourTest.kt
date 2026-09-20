package com.playeverywhere.spherelauncher.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.playeverywhere.spherelauncher.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuickStartTourTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun canTryImmediatelyWithoutChoosingHome() {
        var completed = 0
        compose.setContent { MaterialTheme { QuickStartTour { completed++ } } }
        compose.onNodeWithText(label(R.string.quick_welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.quick_try_now)).performClick()
        compose.runOnIdle { assertEquals(1, completed) }
    }

    @Test fun setupIsOptionalAndAllowsBackNavigation() {
        var completed = 0
        compose.setContent { MaterialTheme { QuickStartTour { completed++ } } }
        compose.onNodeWithText(label(R.string.quick_setup)).performClick()
        compose.onNodeWithText(label(R.string.quick_home_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.ob_btn_back)).performClick()
        compose.onNodeWithText(label(R.string.quick_welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.quick_setup)).performClick()
        compose.onNodeWithText(label(R.string.quick_try_now)).performClick()
        compose.runOnIdle { assertEquals(1, completed) }
    }
}
