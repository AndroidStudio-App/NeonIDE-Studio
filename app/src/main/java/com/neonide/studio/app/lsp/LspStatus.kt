package com.neonide.studio.app.lsp

/**
 * Enum representing the current status of the Language Server connection.
 */
enum class LspStatus {
    Disconnected,
    Connecting,
    Ready,
    Error
}
