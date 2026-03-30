package com.neonide.studio.app.bottomsheet.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BottomSheetViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun testNavigationResults() {
        val viewModel = BottomSheetViewModel()
        val item = NavigationItem("file:///test.java", 10, 5, "Line 10: println(\"hello\")")
        val results = listOf(item)
        
        viewModel.setNavigationResults(results)
        
        assertEquals(results, viewModel.navigationResults.value)
    }
}