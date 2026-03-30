package com.neonide.studio.app.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CompletableFuture

class LspCompletionTest {

    @Test
    fun testFetchCompletionItems() {
        val manager = LspManager()
        
        // Mock TextDocumentService
        val mockTextDocumentService = object : TextDocumentService {
            override fun completion(position: CompletionParams): CompletableFuture<org.eclipse.lsp4j.jsonrpc.messages.Either<List<CompletionItem>, CompletionList>> {
                val item = CompletionItem()
                item.label = "testCompletion"
                item.kind = CompletionItemKind.Method
                item.detail = "Test Detail"
                
                val list = CompletionList(listOf(item))
                return CompletableFuture.completedFuture(org.eclipse.lsp4j.jsonrpc.messages.Either.forRight(list))
            }

            override fun didOpen(params: DidOpenTextDocumentParams?) {}
            override fun didChange(params: DidChangeTextDocumentParams?) {}
            override fun didClose(params: DidCloseTextDocumentParams?) {}
            override fun didSave(params: DidSaveTextDocumentParams?) {}
        }

        // Mock LanguageServer
        val mockServer = object : LanguageServer {
            override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
                return CompletableFuture.completedFuture(InitializeResult(ServerCapabilities()))
            }

            override fun shutdown(): CompletableFuture<Any> {
                return CompletableFuture.completedFuture(Any())
            }

            override fun exit() {}

            override fun getTextDocumentService(): TextDocumentService {
                return mockTextDocumentService
            }

            override fun getWorkspaceService(): WorkspaceService {
                return object : WorkspaceService {
                    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {}
                    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {}
                }
            }
        }

        manager.setServerForTest(mockServer)

        val resultFuture = manager.fetchCompletionItems("file:///test.java", 1, 1, 0)
        val result = resultFuture.get()

        assertEquals(1, result.size)
        assertEquals("testCompletion", result[0].label)
        assertEquals("Test Detail", result[0].lspItem.detail)
    }
}
