package com.neonide.studio.app.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LanguageProviderTest {

    private val dummyTsLanguage = object : EmptyLanguage() {
        override fun toString(): String = "TreeSitter"
    }
    private val dummyTmLanguage = object : EmptyLanguage() {
        override fun toString(): String = "TextMate"
    }

    @Test
    fun testGetLanguage_Java_PrefersTreeSitter() {
        val provider = LanguageProvider(
            tsFactory = { if (it == "java") dummyTsLanguage else null },
            tmFactory = { if (it == "java") dummyTmLanguage else null }
        )
        val file = File("Test.java")
        val result = provider.getLanguage(file)
        assertEquals("TreeSitter", result.toString())
    }

    @Test
    fun testGetLanguage_Java_FallsBackToTextMate() {
        val provider = LanguageProvider(
            tsFactory = { null }, // Tree-sitter fails/not available
            tmFactory = { if (it == "java") dummyTmLanguage else null }
        )
        val file = File("Test.java")
        val result = provider.getLanguage(file)
        assertEquals("TextMate", result.toString())
    }

    @Test
    fun testGetLanguage_Unknown_ReturnsEmpty() {
        val provider = LanguageProvider(
            tsFactory = { dummyTsLanguage },
            tmFactory = { dummyTmLanguage }
        )
        val file = File("Test.txt")
        val result = provider.getLanguage(file)
        assertTrue(result is EmptyLanguage)
    }
    
    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
