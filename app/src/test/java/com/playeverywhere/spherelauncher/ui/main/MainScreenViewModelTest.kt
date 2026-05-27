package com.playeverywhere.spherelauncher.ui.main

import com.playeverywhere.spherelauncher.data.DefaultDataRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun testDefaultDataRepository() = runTest {
    val repository = DefaultDataRepository()
    val dataList = repository.data.first()
    assertEquals(1, dataList.size)
    assertEquals("Android", dataList[0])
  }

  @Test
  fun testMainUiStateDefaults() {
    val uiState = MainUiState()
    assertEquals(SphereStyle.FLOATING_ICONS, uiState.style)
    assertTrue(uiState.isAutoDriftEnabled)
    assertEquals(ShapeType.SPHERE, uiState.shapeType)
    assertEquals("", uiState.searchQuery)
    assertTrue(uiState.isLoading)
  }
}
