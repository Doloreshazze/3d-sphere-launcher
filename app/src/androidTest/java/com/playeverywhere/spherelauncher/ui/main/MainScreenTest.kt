package com.playeverywhere.spherelauncher.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.playeverywhere.spherelauncher.ui.main.MainScreen]. */
class MainScreenTest {

    @get:Rule 
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            MainScreen(onItemClick = { })
        }
    }

    @Test
    fun testMainScreenLaunchesAndRendersUiComponents() {
        // Verify that the MainScreen Compose tree renders without crashing
        // and displays primary launcher interface nodes
        composeTestRule.onRoot().assertExists()
        composeTestRule.onRoot().assertIsDisplayed()
    }
}
