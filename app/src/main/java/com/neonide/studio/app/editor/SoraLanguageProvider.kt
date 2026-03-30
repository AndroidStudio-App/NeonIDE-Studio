package com.neonide.studio.app.editor

import android.content.Context
import com.itsaky.androidide.treesitter.TreeSitter
import com.itsaky.androidide.treesitter.java.TSLanguageJava
import com.itsaky.androidide.treesitter.kotlin.TSLanguageKotlin
import com.itsaky.androidide.treesitter.xml.TSLanguageXml
import com.itsaky.androidide.treesitter.json.TSLanguageJson
import com.itsaky.androidide.treesitter.python.TSLanguagePython
import com.itsaky.androidide.treesitter.c.TSLanguageC
import com.itsaky.androidide.treesitter.cpp.TSLanguageCpp
import com.itsaky.androidide.treesitter.properties.TSLanguageProperties
import com.itsaky.androidide.treesitter.log.TSLanguageLog
import com.itsaky.androidide.treesitter.aidl.TSLanguageAidl
import io.github.rosemoe.sora.editor.ts.LocalsCaptureSpec
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.neonide.studio.app.editor.completion.UnifiedCompletionProvider
import com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer
import java.io.File

class SoraLanguageProvider(private val context: Context) {

    private var treeSitterLoaded = false

    private val baseProvider = LanguageProvider(
        tsFactory = { type -> createTreeSitterLanguage(type) },
        tmFactory = { type -> createTextMateLanguage(type) }
    )

    fun getLanguage(file: File): Language {
        val base = baseProvider.getLanguage(file)

        val enhanced = if (file.extension.equals("xml", ignoreCase = true)) {
            AndroidXmlLanguageEnhancer(base, file)
        } else {
            base
        }

        return UnifiedCompletionProvider(enhanced, file)
    }

    private fun createTreeSitterLanguage(type: String): Language? {
        return runCatching {
            when (type) {
                "java" -> createJavaTreeSitterLanguage()
                "kotlin" -> createKotlinTreeSitterLanguage()
                "xml" -> createXmlTreeSitterLanguage()
                "json" -> createJsonTreeSitterLanguage()
                "python" -> createPythonTreeSitterLanguage()
                "c" -> createCTreeSitterLanguage()
                "cpp" -> createCppTreeSitterLanguage()
                "properties" -> createPropertiesTreeSitterLanguage()
                "log" -> createLogTreeSitterLanguage()
                "aidl" -> createAidlTreeSitterLanguage()
                else -> null
            }
        }.getOrNull()
    }

    private fun createTextMateLanguage(type: String): Language? {
        return runCatching {
            when (type) {
                "java" -> TextMateLanguage.create("source.java", true)
                "kotlin" -> TextMateLanguage.create("source.kotlin", true)
                "python" -> TextMateLanguage.create("source.python", true)
                "html" -> TextMateLanguage.create("text.html.basic", true)
                "javascript" -> TextMateLanguage.create("source.js", true)
                "markdown" -> TextMateLanguage.create("text.html.markdown", true)
                // Provided for completeness; our bundled textmate pack currently doesn't include TS grammar,
                // but users may load it via "TM Language from file".
                "typescript" -> TextMateLanguage.create("source.typescript", true)

                "xml" -> TextMateLanguage.create("text.xml", true)
                else -> null
            }
        }.getOrNull()
    }

    private fun createJavaTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/java/highlights.scm")
        val blocks = readAssetText("tree-sitter-queries/java/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/java/brackets.scm")
        val locals = readAssetText("tree-sitter-queries/java/locals.scm")

        // IMPORTANT:
        // java/locals.scm uses capture names like: @scope, @scope.members, @reference, @definition.var
        // TsLanguageSpec default LocalsCaptureSpec expects @local.scope/@local.reference/... so we must
        // provide a matching spec. Otherwise, @scope captures are treated as highlight captures and can
        // cover the whole file, effectively disabling highlighting.
        val javaLocalsCaptureSpec = object : LocalsCaptureSpec() {
            override fun isScopeCapture(captureName: String) = captureName == "scope"
            override fun isMembersScopeCapture(captureName: String) = captureName == "scope.members"
            override fun isReferenceCapture(captureName: String) = captureName == "reference"
            override fun isDefinitionCapture(captureName: String) =
                captureName == "definition.var" || captureName == "definition.field"
        }

        val spec = TsLanguageSpec(
            language = TSLanguageJava.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
            localsCaptureSpec = javaLocalsCaptureSpec,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo "keyword"
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf("type", "type.builtin", "qualified_name")
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo arrayOf("imported_member")
            TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME) applyTo arrayOf(
                "function",
                "function.method",
                "function.builtin",
            )
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf(
                "variable",
                "variable.builtin",
                "variable.field",
            )
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf(
                "string",
                "number",
                "constant",
                "constant.builtin",
            )
            TextStyle.makeStyle(EditorColorScheme.ANNOTATION) applyTo arrayOf("attribute", "annotation")
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo "operator"
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo "comment"
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createKotlinTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/kotlin/highlights.scm")
        val blocks = readAssetText("tree-sitter-queries/kotlin/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/kotlin/brackets.scm")
        val locals = readAssetText("tree-sitter-queries/kotlin/locals.scm")

        val spec = TsLanguageSpec(
            language = TSLanguageKotlin.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo "keyword"
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf("type", "type.builtin", "constructor")
            TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME) applyTo arrayOf(
                "function.invocation",
                "function.declaration",
                "function.builtin",
            )
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf(
                "identifier",
                "property.local",
                "property.top_level",
                "property.class",
                "variable.builtin",
                "parameter",
                "constant",
            )
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo arrayOf("operator", "punctuation.special")
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo "comment"
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf("string", "number")
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createXmlTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/xml/highlights.scm")
        val blocks = readAssetText("tree-sitter-queries/xml/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/xml/brackets.scm")

        val spec = TsLanguageSpec(
            language = TSLanguageXml.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = "",
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.HTML_TAG) applyTo arrayOf("element.tag")
            TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME) applyTo arrayOf("attr.name", "attr.prefix", "xmlns.prefix", "ns_declarator", "xml_decl")
            TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_VALUE) applyTo arrayOf("attr.value")
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo "operator"
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo "comment"
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo arrayOf("text", "xml.ref", "cdata.start", "cdata.end", "cdata.data")
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createJsonTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/json/highlights.scm")

        val blocks = readAssetText("tree-sitter-queries/json/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/json/brackets.scm")
        // JSON has no locals.
        val locals = ""

        val spec = TsLanguageSpec(
            language = TSLanguageJson.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME) applyTo arrayOf("string.special.key")
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf("string", "number")
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo arrayOf("constant.builtin")
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo "comment"
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo "escape"
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createPythonTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/python/highlights.scm")

        val blocks = readAssetText("tree-sitter-queries/python/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/python/brackets.scm")
        val locals = readAssetText("tree-sitter-queries/python/locals.scm")

        val spec = TsLanguageSpec(
            language = TSLanguagePython.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo arrayOf("keyword")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf("variable", "property", "constant")
            TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME) applyTo arrayOf("function", "function.method", "function.builtin")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf("type", "type.builtin", "constructor")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf("constructor")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf("property")
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf("string", "number", "constant.builtin")
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo "comment"
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo arrayOf("operator", "punctuation.special", "escape")
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo arrayOf("embedded")
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createCTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/c/highlights.scm")

        val blocks = readAssetText("tree-sitter-queries/c/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/c/brackets.scm")
        val locals = readAssetText("tree-sitter-queries/c/locals.scm")

        val spec = TsLanguageSpec(
            language = TSLanguageC.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf("variable", "property", "label")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf("type", "type.builtin")
            TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME) applyTo arrayOf("function", "function.special")
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf("string", "number", "constant")
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo arrayOf("keyword")
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo arrayOf("operator", "delimiter")
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo "comment"
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createCppTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/cpp/highlights.scm")

        val blocks = readAssetText("tree-sitter-queries/cpp/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/cpp/brackets.scm")
        val locals = readAssetText("tree-sitter-queries/cpp/locals.scm")

        val spec = TsLanguageSpec(
            language = TSLanguageCpp.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME) applyTo arrayOf("function")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf("type")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf("variable.builtin")
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf("string", "constant")
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo arrayOf("keyword")
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createPropertiesTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/properties/highlights.scm")

        val blocks = readAssetText("tree-sitter-queries/properties/blocks.scm")
        // Properties has no bracket pairs or locals.
        val brackets = ""
        val locals = ""

        val spec = TsLanguageSpec(
            language = TSLanguageProperties.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME) applyTo arrayOf("prop.key")
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo arrayOf("prop.separator", "prop.escape")
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf("prop.value", "prop.value.continuation")
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo "comment"
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createLogTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/log/highlights.scm")

        val blocks = readAssetText("tree-sitter-queries/log/blocks.scm")
        // Log grammar has no bracket pairs or locals.
        val brackets = ""
        val locals = ""

        val spec = TsLanguageSpec(
            language = TSLanguageLog.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            // We don't have a dedicated logcat color scheme here; map priorities to existing colors.
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf(
                "err.date", "err.time", "err.pid", "err.tid", "err.priority", "err.tag", "err.msg",
                "warn.date", "warn.time", "warn.pid", "warn.tid", "warn.priority", "warn.tag", "warn.msg",
                "info.date", "info.time", "info.pid", "info.tid", "info.priority", "info.tag", "info.msg",
                "debug.date", "debug.time", "debug.pid", "debug.tid", "debug.priority", "debug.tag", "debug.msg",
                "verbose.date", "verbose.time", "verbose.pid", "verbose.tid", "verbose.priority", "verbose.tag", "verbose.msg",
            )
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo arrayOf("header")
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun createAidlTreeSitterLanguage(): TsLanguage {
        ensureTreeSitterLoaded()

        val highlights = readAssetText("tree-sitter-queries/aidl/highlights.scm")
        val blocks = readAssetText("tree-sitter-queries/aidl/blocks.scm")
        val brackets = readAssetText("tree-sitter-queries/aidl/brackets.scm")
        val locals = readAssetText("tree-sitter-queries/aidl/locals.scm")

        val spec = TsLanguageSpec(
            language = TSLanguageAidl.getInstance(),
            highlightScmSource = highlights,
            codeBlocksScmSource = blocks,
            bracketsScmSource = brackets,
            localsScmSource = locals,
        )

        return TsLanguage(spec, tab = true) {
            TextStyle.makeStyle(EditorColorScheme.KEYWORD) applyTo arrayOf("keyword")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME) applyTo arrayOf("type", "type.builtin")
            TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME) applyTo arrayOf("function.method")
            TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo arrayOf("string", "number", "constant.builtin")
            TextStyle.makeStyle(EditorColorScheme.COMMENT) applyTo arrayOf("comment")
            TextStyle.makeStyle(EditorColorScheme.ANNOTATION) applyTo arrayOf("attribute")
            TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo arrayOf("operator")
            TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR) applyTo arrayOf("variable", "constant")
            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL) applyTo ""
        }
    }

    private fun readAssetText(path: String): String {
        return context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun ensureTreeSitterLoaded() {
        if (treeSitterLoaded) return
        synchronized(this) {
            if (treeSitterLoaded) return
            TreeSitter.loadLibrary()
            treeSitterLoaded = true
        }
    }
}
