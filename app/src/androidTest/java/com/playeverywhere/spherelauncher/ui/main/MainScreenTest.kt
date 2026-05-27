package com.playeverywhere.spherelauncher.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.playeverywhere.spherelauncher.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainScreen(onItemClick = { })
    }
  }

  @Test
  fun testMainScreenLaunches() {
    // Basic test to verify that the MainScreen Compose tree renders without crashing
  }
}
