
package com.neonide.studio.app.lsp

import com.neonide.studio.app.lsp.server.JavaLanguageServer
import com.neonide.studio.app.lsp.server.KotlinLanguageServer
import com.neonide.studio.app.lsp.server.XMLLanguageServer
import java.io.File

object LspUtils {
    fun getServerId(file: File): String? {
        return when (file.extension) {
            "java" -> JavaLanguageServer.SERVER_ID
            "kt", "kts" -> KotlinLanguageServer.SERVER_ID
            "xml" -> XMLLanguageServer.SERVER_ID
            else -> null
        }
    }
}
