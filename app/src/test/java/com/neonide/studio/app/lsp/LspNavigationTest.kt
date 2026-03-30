package com.neonide.studio.app.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CompletableFuture

class LspNavigationTest {

    @Test
    fun testRequestDefinition() {
        val manager = LspManager()
        val expectedUri = "file:///target.java"
        val expectedRange = Range(Position(10, 0), Position(10, 5))
        
        val mockTextDocumentService = object : TextDocumentService {
            override fun definition(params: DefinitionParams): CompletableFuture<org.eclipse.lsp4j.jsonrpc.messages.Either<List<Location>, List<LocationLink>>> {
                val location = Location(expectedUri, expectedRange)
                return CompletableFuture.completedFuture(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(listOf(location)))
            }

            override fun didOpen(params: DidOpenTextDocumentParams?) {}
            override fun didChange(params: DidChangeTextDocumentParams?) {}
            override fun didClose(params: DidCloseTextDocumentParams?) {}
            override fun didSave(params: DidSaveTextDocumentParams?) {}
        }

        val mockServer = object : LanguageServer {
            override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> = CompletableFuture.completedFuture(InitializeResult())
            override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(Any())
            override fun exit() {}
            override fun getTextDocumentService() = mockTextDocumentService
            override fun getWorkspaceService(): WorkspaceService = object : WorkspaceService {
                override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {}
                override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {}
            }
        }

        manager.setServerForTest(mockServer)

        val resultFuture = manager.requestDefinition("file:///test.java", 1, 1)
        val result = resultFuture?.get()?.left
        
        assertEquals(1, result?.size)
        assertEquals(expectedUri, result?.get(0)?.uri)
        assertEquals(expectedRange, result?.get(0)?.range)
    }

    @Test
    fun testRequestReferences() {
        val manager = LspManager()
        val expectedUri = "file:///ref.java"
        
        val mockTextDocumentService = object : TextDocumentService {
            override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
                val location = Location(expectedUri, Range(Position(5, 0), Position(5, 10)))
                return CompletableFuture.completedFuture(listOf(location))
            }

            override fun didOpen(params: DidOpenTextDocumentParams?) {}
            override fun didChange(params: DidChangeTextDocumentParams?) {}
            override fun didClose(params: DidCloseTextDocumentParams?) {}
            override fun didSave(params: DidSaveTextDocumentParams?) {}
        }

        val mockServer = object : LanguageServer {
            override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> = CompletableFuture.completedFuture(InitializeResult())
            override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(Any())
            override fun exit() {}
            override fun getTextDocumentService() = mockTextDocumentService
            override fun getWorkspaceService(): WorkspaceService = object : WorkspaceService {
                override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {}
                override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {}
            }
        }

        manager.setServerForTest(mockServer)

        val resultFuture = manager.requestReferences("file:///test.java", 1, 1)
        val result = resultFuture?.get()
        
        assertEquals(1, result?.size)
        assertEquals(expectedUri, result?.get(0)?.uri)
    }
}
