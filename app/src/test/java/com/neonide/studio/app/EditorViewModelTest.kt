package com.neonide.studio.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.neonide.studio.app.lsp.LspStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun testLspManagerLifecycle() {
        val viewModel = EditorViewModel()
        assertNotNull("LspManager should be initialized", viewModel.lspManager)
    }

    @Test
    fun testConnectionStatusUpdates() {
        val viewModel = EditorViewModel()
        assertNotNull("Connection status should be initialized", viewModel.connectionStatus.value)
        assertEquals("Initial status should be Disconnected", LspStatus.Disconnected, viewModel.connectionStatus.value)
        
        viewModel.setLspStatus(LspStatus.Connecting)
        assertEquals("Status should be Connecting", LspStatus.Connecting, viewModel.connectionStatus.value)
    }
}