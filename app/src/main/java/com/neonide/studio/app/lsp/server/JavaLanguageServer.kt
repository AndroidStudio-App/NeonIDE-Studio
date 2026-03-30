package com.neonide.studio.app.lsp.server

/**
 * Placeholder server metadata for Java LSP integration.
 *
 * Currently, the editor-side LSP client connects to a local (abstract) socket.
 * A background service/binary must provide the actual language server implementation.
 */
object JavaLanguageServer {
    const val SERVER_ID = "java"
}
