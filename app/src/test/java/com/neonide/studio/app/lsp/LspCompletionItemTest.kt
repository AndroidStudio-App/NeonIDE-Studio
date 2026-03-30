package com.neonide.studio.app.lsp

import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import org.eclipse.lsp4j.CompletionItem
import org.junit.Test
import org.junit.Assert.*

class LspCompletionItemTest {
    @Test
    fun testMapping() {
        val lspItem = CompletionItem("test")
        lspItem.kind = org.eclipse.lsp4j.CompletionItemKind.Method
        lspItem.detail = "detail"
        
        val item = LspCompletionItem(lspItem, 0)
        assertEquals("test", item.label)
        assertEquals("detail", item.desc)
        assertEquals(CompletionItemKind.Method, item.kind)
    }
}
