package com.neonide.studio.app.lsp

import org.junit.Test
import org.junit.Assert.*
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.Position
import java.util.concurrent.CompletableFuture

class LspManagerTest {
    @Test
    fun testManagerCreation() {
        val manager = LspManager()
        assertNotNull("LspManager should be instantiated", manager)
    }

    @Test
    fun testRequestCompletionNotConnected() {
        val manager = LspManager()
        val result = manager.requestCompletion("file:///test.java", 0, 0)
        assertNull("Result should be null when not connected", result)
    }

    @Test
    fun testFetchCompletionItemsNotConnected() {
        val manager = LspManager()
        val result = manager.fetchCompletionItems("file:///test.java", 0, 0, 0).get()
        assertTrue("Result should be empty when not connected", result.isEmpty())
    }

    @Test
    fun testShutdown() {
        val manager = LspManager()
        // Should not crash
        manager.shutdown()
    }
}
