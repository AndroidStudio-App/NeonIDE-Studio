package com.neonide.studio.app.lsp

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import com.neonide.studio.app.lsp.server.JavaLanguageServerService
import com.neonide.studio.shared.termux.TermuxConstants
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class LspManager {

    private var server: LanguageServer? = null
    private var launcher: Launcher<LanguageServer>? = null
    private var client: LspClient = LspClient()
    private val executor = Executors.newSingleThreadExecutor()
    private var statusListener: ((LspStatus) -> Unit)? = null

    init {
        current = this
    }

    companion object {
        private const val TAG = "LspManager"

        @Volatile
        var current: LspManager? = null
    }

    @androidx.annotation.VisibleForTesting
    fun setServerForTest(server: LanguageServer) {
        this.server = server
    }

    fun setStatusListener(listener: (LspStatus) -> Unit) {
        this.statusListener = listener
    }

    fun setDiagnosticsListener(listener: LspClient.DiagnosticsListener) {
        client.setDiagnosticsListener(listener)
    }

    fun connect(): CompletableFuture<InitializeResult>? {
        val future = CompletableFuture<InitializeResult>()
        executor.execute {
            try {
                statusListener?.invoke(LspStatus.Connecting)
                val socket = LocalSocket()
                val address = LocalSocketAddress(JavaLanguageServerService.SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT)
                
                var connected = false
                val deadline = System.currentTimeMillis() + 5000 // 5 second timeout
                
                while (System.currentTimeMillis() < deadline) {
                    try {
                        socket.connect(address)
                        connected = true
                        break
                    } catch (e: IOException) {
                        Thread.sleep(200)
                    }
                }

                if (!connected) {
                    throw IOException("Failed to connect to Language Server socket after retries")
                }

                Log.d(TAG, "Connected to Language Server socket.")

                launcher = Launcher.Builder<LanguageServer>()
                    .setLocalService(client)
                    .setRemoteInterface(LanguageServer::class.java)
                    .setInput(socket.inputStream)
                    .setOutput(socket.outputStream)
                    .setExecutorService(executor)
                    .create()

                launcher?.let {
                    it.startListening()
                    server = it.remoteProxy
                }

                val initFuture = initialize()
                initFuture?.thenAccept { result ->
                    future.complete(result)
                }?.exceptionally { ex ->
                    future.completeExceptionally(ex)
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect or initialize Language Server", e)
                statusListener?.invoke(LspStatus.Error)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun initialize(): CompletableFuture<InitializeResult>? {
        val params = InitializeParams()
        params.processId = android.os.Process.myPid()

        // org.javacs requires a valid workspace root URI. If it's missing/invalid,
        // it won't be able to create the compiler and may appear to "never start".
        // We default to Termux HOME here.
        val rootPath = TermuxConstants.TERMUX_HOME_DIR_PATH

        // Ensure proper file:// URI
        params.rootUri = java.io.File(rootPath).toURI().toASCIIString()

        return server?.initialize(params)?.thenApply { result ->
            server?.initialized(org.eclipse.lsp4j.InitializedParams())
            Log.d(TAG, "LSP initialized: ${result.capabilities}")
            statusListener?.invoke(LspStatus.Ready)
            result
        }
    }

    fun getServer(): LanguageServer? = server

    fun requestCompletion(uri: String, line: Int, character: Int): CompletableFuture<org.eclipse.lsp4j.jsonrpc.messages.Either<List<org.eclipse.lsp4j.CompletionItem>, org.eclipse.lsp4j.CompletionList>>? {
        val params = org.eclipse.lsp4j.CompletionParams()
        params.textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(uri)
        params.position = org.eclipse.lsp4j.Position(line, character)
        return server?.textDocumentService?.completion(params)
    }

    fun fetchCompletionItems(uri: String, line: Int, character: Int, prefixLength: Int): CompletableFuture<List<LspCompletionItem>> {
        val future = CompletableFuture<List<LspCompletionItem>>()
        requestCompletion(uri, line, character)?.thenAccept { result ->
            val items = if (result.isLeft) {
                result.left
            } else {
                result.right.items
            }
            val converted = items.map { LspCompletionItem(it, prefixLength) }
            future.complete(converted)
        } ?: future.complete(emptyList())
        return future
    }

    fun requestDefinition(uri: String, line: Int, character: Int): CompletableFuture<org.eclipse.lsp4j.jsonrpc.messages.Either<List<org.eclipse.lsp4j.Location>, List<org.eclipse.lsp4j.LocationLink>>>? {
        val params = org.eclipse.lsp4j.DefinitionParams()
        params.textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(uri)
        params.position = org.eclipse.lsp4j.Position(line, character)
        return server?.textDocumentService?.definition(params)
    }

    fun requestReferences(uri: String, line: Int, character: Int): CompletableFuture<List<org.eclipse.lsp4j.Location>>? {
        val params = org.eclipse.lsp4j.ReferenceParams()
        params.textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(uri)
        params.position = org.eclipse.lsp4j.Position(line, character)
        params.context = org.eclipse.lsp4j.ReferenceContext(true)
        return server?.textDocumentService?.references(params)
    }
    
    fun shutdown() {
        server?.shutdown()
        server?.exit()
        executor.shutdown()
    }
}
