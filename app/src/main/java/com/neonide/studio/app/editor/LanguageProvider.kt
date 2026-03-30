package com.neonide.studio.app.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import java.io.File

class LanguageProvider(
    private val tsFactory: (String) -> Language?,
    private val tmFactory: (String) -> Language?
) {
    fun getLanguage(file: File): Language {
        val ext = file.extension.lowercase()
        return when (ext) {
            "java" -> tsFactory("java") ?: tmFactory("java") ?: EmptyLanguage()
            "kt", "kts" -> tsFactory("kotlin") ?: tmFactory("kotlin") ?: EmptyLanguage()
            "xml" -> tsFactory("xml") ?: tmFactory("xml") ?: EmptyLanguage()

            // Tree-sitter enabled
            "json" -> tsFactory("json") ?: tmFactory("json") ?: EmptyLanguage()
            "py" -> tsFactory("python") ?: tmFactory("python") ?: EmptyLanguage()
            "c" -> tsFactory("c") ?: EmptyLanguage()
            "h" -> tsFactory("c") ?: EmptyLanguage()
            "cpp", "cc", "cxx" -> tsFactory("cpp") ?: EmptyLanguage()
            "hpp", "hh", "hxx" -> tsFactory("cpp") ?: EmptyLanguage()
            "properties" -> tsFactory("properties") ?: EmptyLanguage()
            "log" -> tsFactory("log") ?: EmptyLanguage()
            "aidl" -> tsFactory("aidl") ?: EmptyLanguage()

            // TextMate fallback
            "js" -> tmFactory("javascript") ?: EmptyLanguage()
            "html", "htm" -> tmFactory("html") ?: EmptyLanguage()
            "md", "markdown" -> tmFactory("markdown") ?: EmptyLanguage()
            "ts" -> tmFactory("typescript") ?: EmptyLanguage()

            else -> EmptyLanguage()
        }
    }
}
