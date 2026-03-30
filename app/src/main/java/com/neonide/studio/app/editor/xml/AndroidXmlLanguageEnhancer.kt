package com.neonide.studio.app.editor.xml

import android.os.Bundle
import com.itsaky.androidide.treesitter.TSParser
import com.itsaky.androidide.treesitter.TSQuery
import com.itsaky.androidide.treesitter.TSQueryCursor
import com.itsaky.androidide.treesitter.TreeSitter
import com.itsaky.androidide.treesitter.xml.TSLanguageXml
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.completion.snippetUpComparator
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.MyCharacter
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolPairMatch
import java.io.File

/**
 * Enhances an existing XML language with:
 * - Android XML oriented completions (tags/attrs/attr-values) even without a running XML LSP
 * - Tree-sitter based syntax diagnostics -> squiggles
 *
 * It keeps the base language's highlighting/formatting/snippets/etc.
 */
class AndroidXmlLanguageEnhancer(
    private val base: Language,
    private val file: File?
) : Language by base {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // Use same comparator as other languages in this project
        publisher.setComparator(::snippetUpComparator)

        // Always call base first (snippets / any built-in provider)
        try {
            base.requireAutoComplete(content, position, publisher, extraArguments)
        } catch (ex: CompletionCancelledException) {
            throw ex
        } catch (_: Throwable) {
            // ignore base failures
        }

        // Provide extra XML completions when it looks like XML editing.
        // This fills gaps when LemMinX isn't installed or not running.
        runCatching {
            val ctx = XmlCompletionContext.of(content, position)
            val prefix = ctx.prefix

            when (ctx.nodeType) {
                XmlCompletionContext.NodeType.TAG -> {
                    // Complete tag names
                    AndroidXmlCompletions.tagNames(prefix).forEach { tag ->
                        publisher.addItem(
                            SimpleCompletionItem(
                                /*label*/ tag,
                                /*prefixLength*/ prefix.length,
                                /*commitText*/ tag
                            )
                        )
                    }
                }

                XmlCompletionContext.NodeType.ATTRIBUTE_NAME -> {
                    AndroidXmlCompletions.attributeNames(prefix).forEach { attr ->
                        publisher.addItem(
                            XmlAttrCompletionItem(
                                label = attr,
                                desc = if (attr.startsWith("android:")) "From package 'android'" else "",
                                attrName = attr,
                                replacePrefixLength = prefix.length
                            )
                        )
                    }
                }

                XmlCompletionContext.NodeType.ATTRIBUTE_VALUE -> {
                    AndroidXmlCompletions.attributeValues(ctx.attributeName, prefix).forEach { v ->
                        publisher.addItem(
                            SimpleCompletionItem(
                                /*label*/ v,
                                /*prefixLength*/ prefix.length,
                                /*commitText*/ v
                            ).kind(io.github.rosemoe.sora.lang.completion.CompletionItemKind.Value)
                        )
                    }
                }

                else -> {
                    // no-op
                }
            }
        }

        // LSP completions are already provided by editor-lsp when attached.
        // UnifiedCompletionProvider's legacy LspManager integration is removed separately.
    }

    override fun getAnalyzeManager(): AnalyzeManager {
        val baseManager = base.analyzeManager
        // Provide base analysis; diagnostics are pushed directly to CodeEditor from the Activity.
        return baseManager
    }

    override fun getFormatter(): Formatter = base.formatter

    override fun getSymbolPairs(): SymbolPairMatch = base.symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler?> {
        val handlers = base.newlineHandlers ?: return emptyArray()
        // Ensure the array type matches Array<NewlineHandler?> (platform arrays can be nullable).
        return arrayOfNulls<NewlineHandler>(handlers.size).also { dst ->
            for (i in handlers.indices) dst[i] = handlers[i]
        }
    }

    override fun destroy() {
        base.destroy()
    }

    companion object {

        /**
         * Provide a list of Android framework attribute names to enhance completion.
         *
         * IMPORTANT: Names must NOT include the "android:" prefix (e.g. "layout_width").
         */
        fun setAndroidFrameworkAttrsProvider(provider: (() -> List<String>)?) {
            AndroidXmlCompletions.setFrameworkAttrsProvider(provider)
        }

        private const val TAG_INFO_QUERY =
            """
            (empty_element
              \"<\" @tag.open
              tag_name: (_) @element.name
              \"/\" @tag.slash
              \">\" @tag.close) @element.empty

            (end_tag_element
              (tag_start
                \"<\" @tag.start.open
                tag_name: (_) @tag.start.name
                \">\" @tag.start.close)
              (tag_end
                \"<\" @tag.end.open
                \"/\" @tag.end.slash
                tag_name: (_) @tag.end.name
                \">\" @tag.end.close)
            ) @element.with_end
            """

        private val tsInitLock = Any()
        @Volatile private var tsLoaded: Boolean = false

        private fun ensureTreeSitterLoaded() {
            if (tsLoaded) return
            synchronized(tsInitLock) {
                if (tsLoaded) return
                TreeSitter.loadLibrary()
                tsLoaded = true
            }
        }

        /**
         * Implements ACS-like behavior for typing '/':
         * - if user typed '</' -> auto-insert tag name + optional '>'
         * - if user typed '/>' -> auto-insert missing '>'
         */
        fun applyAdvancedSlashEditIfNeeded(currentFile: File?, editor: CodeEditor, e: io.github.rosemoe.sora.event.ContentChangeEvent) {
            if (e.isCausedByUndoManager) return
            if (e.action != io.github.rosemoe.sora.event.ContentChangeEvent.ACTION_INSERT) return

            val changed = e.changedText
            if (changed.length != 1 || changed[0] != '/') return

            if (currentFile?.extension?.lowercase() != "xml") return

            // We must not modify text inside the event callback; post it.
            editor.post {
                runCatching {
                    val start = e.changeStart.index
                    val end = e.changeEnd.index

                    val content = StringBuilder(editor.text.toString())
                    val openSlash = start > 0 && content[start - 1] == '<'

                    // Insert a dummy char to build a better tree for '</'
                    if (openSlash) {
                        content.insert(end, 'a')
                    }

                    val edits = computeAdvancedSlashEdits(content, start, openSlash)

                    if (edits.insertText.isNullOrEmpty()) return@runCatching

                    // Apply the computed insertion at current cursor.
                    editor.text.beginBatchEdit()
                    try {
                        editor.text.insert(e.changeEnd.line, e.changeEnd.column, edits.insertText)
                    } finally {
                        editor.text.endBatchEdit()
                    }
                }
            }
        }

        private data class SlashEdits(val insertText: String?)

        private fun computeAdvancedSlashEdits(
            content: StringBuilder,
            insertionIndex: Int,
            openSlash: Boolean
        ): SlashEdits {
            ensureTreeSitterLoaded()

            TSParser.create().use { parser ->
                parser.language = TSLanguageXml.getInstance()
                parser.parseString(content.toString()).use { tree ->
                    TSQuery.create(TSLanguageXml.getInstance(), TAG_INFO_QUERY).use { query ->
                        if (!query.canAccess() || query.errorType != com.itsaky.androidide.treesitter.TSQueryError.None) {
                            return SlashEdits(null)
                        }

                        TSQueryCursor.create().use { cursor ->
                            cursor.exec(query, tree.rootNode)
                            val matches = cursor.toList()
                            val match = findMatchAt(query, matches, insertionIndex) ?: return SlashEdits(null)

                            val captures = match.captures
                            if (!openSlash) {
                                // Empty element closing '/>' missing '>'
                                val closeCapture = captures.firstOrNull { query.getCaptureNameForId(it.index) == "tag.close" }
                                val insertText = if (closeCapture != null && closeCapture.node.startByte == closeCapture.node.endByte) {
                                    ">"
                                } else {
                                    null
                                }
                                return SlashEdits(insertText)
                            } else {
                                // Closing tag: '</' -> insert tag name and maybe '>'
                                val tagNameCapture = captures.firstOrNull { query.getCaptureNameForId(it.index) == "tag.start.name" }
                                    ?: return SlashEdits(null)

                                val nameStart = tagNameCapture.node.startByte / 2
                                val nameEnd = tagNameCapture.node.endByte / 2
                                val tagName = if (nameStart < nameEnd && nameEnd <= content.length) {
                                    content.substring(nameStart, nameEnd)
                                } else {
                                    null
                                } ?: return SlashEdits(null)

                                val closeCapture = captures.firstOrNull { query.getCaptureNameForId(it.index) == "tag.end.close" }
                                val needsGt = closeCapture != null && closeCapture.node.startByte == closeCapture.node.endByte

                                return SlashEdits(tagName + if (needsGt) ">" else "")
                            }
                        }
                    }
                }
            }
        }

        private fun findMatchAt(query: TSQuery, matches: List<com.itsaky.androidide.treesitter.TSQueryMatch>, index: Int): com.itsaky.androidide.treesitter.TSQueryMatch? {
            for (m in matches) {
                for (c in m.captures) {
                    val name = runCatching { query.getCaptureNameForId(c.index) }.getOrNull() ?: continue
                    val startCharIndex = c.node.startByte / 2
                    if (startCharIndex == index && (name == "tag.slash" || name == "tag.end.slash")) {
                        return m
                    }
                }
            }
            return null
        }

        /**
         * A simple tree-sitter based XML diagnostics provider.
         * It highlights error/missing nodes using CodeEditor diagnostics.
         */
        fun computeXmlDiagnostics(text: CharSequence): DiagnosticsContainer? {
            ensureTreeSitterLoaded()

            val src = text.toString()
            if (src.isBlank()) return null

            val diagnostics = DiagnosticsContainer(true)

            TSParser.create().use { parser ->
                parser.language = TSLanguageXml.getInstance()
                parser.parseString(src).use { tree ->
                    val root = tree.rootNode
                    if (!root.hasErrors()) return diagnostics

                    var id = 0L

                    fun visit(node: com.itsaky.androidide.treesitter.TSNode) {
                        if (node.isNull()) return

                        if (node.isError() || node.isMissing()) {
                            val start = (node.startByte / 2).coerceIn(0, src.length)
                            val end = (node.endByte / 2).coerceIn(start, src.length)

                            if (end > start) {
                                diagnostics.addDiagnostic(
                                    DiagnosticRegion(
                                        start,
                                        end,
                                        DiagnosticRegion.SEVERITY_ERROR,
                                        id++,
                                        DiagnosticDetail(
                                            "XML",
                                            if (node.isMissing()) "Missing token" else "Syntax error",
                                            null,
                                            null
                                        )
                                    )
                                )
                            }
                        }

                        val childCount = node.childCount
                        for (i in 0 until childCount) {
                            visit(node.getChild(i))
                        }
                    }

                    visit(root)
                }
            }

            return diagnostics
        }
    }
}

private object AndroidXmlCompletions {

    @Volatile
    private var frameworkAttrsProvider: (() -> List<String>)? = null

    /**
     * Activity can inject a provider that returns raw framework attr names (without "android:").
     * This keeps editor code decoupled from Android Context.
     */
    fun setFrameworkAttrsProvider(provider: (() -> List<String>)?) {
        frameworkAttrsProvider = provider
    }

    // Minimal but useful tag list (can be expanded later)
    private val COMMON_TAGS = listOf(
        "LinearLayout",
        "RelativeLayout",
        "FrameLayout",
        "ConstraintLayout",
        "TextView",
        "EditText",
        "ImageView",
        "Button",
        "com.google.android.material.button.MaterialButton",
        "com.google.android.material.textview.MaterialTextView",
        "androidx.recyclerview.widget.RecyclerView",
        "androidx.cardview.widget.CardView",
        "androidx.core.widget.NestedScrollView",
        "ScrollView",
        "androidx.appcompat.widget.Toolbar",
        "include",
        "merge",
        "view",
        "data",
        "variable",
        "layout"
    )

    // Android XML namespace declarations
    private val XMLNS_ATTRS = listOf(
        "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
        "xmlns:tools=\"http://schemas.android.com/tools\"",
        "xmlns:app=\"http://schemas.android.com/apk/res-auto\""
    )

    // A larger (still incomplete) Android XML attribute set.
    // This is a heuristic fallback when a full Android-aware XML server isn't available.
    private val COMMON_ATTRS = listOf(
        // android:
        "android:id",
        "android:tag",
        "android:layout_width",
        "android:layout_height",
        "android:layout_margin",
        "android:layout_marginStart",
        "android:layout_marginEnd",
        "android:layout_marginTop",
        "android:layout_marginBottom",
        "android:layout_marginHorizontal",
        "android:layout_marginVertical",
        "android:padding",
        "android:paddingStart",
        "android:paddingEnd",
        "android:paddingTop",
        "android:paddingBottom",
        "android:paddingHorizontal",
        "android:paddingVertical",
        "android:orientation",
        "android:gravity",
        "android:layout_gravity",
        "android:background",
        "android:foreground",
        "android:alpha",
        "android:enabled",
        "android:clickable",
        "android:focusable",
        "android:focusableInTouchMode",
        "android:visibility",
        "android:minWidth",
        "android:minHeight",

        // text
        "android:text",
        "android:textColor",
        "android:textColorHint",
        "android:textSize",
        "android:textStyle",
        "android:fontFamily",
        "android:hint",
        "android:maxLines",
        "android:ellipsize",

        // image
        "android:src",
        "android:srcCompat",
        "android:tint",
        "android:scaleType",
        "android:contentDescription",

        // constraints (androidx.constraintlayout)
        "app:layout_constraintStart_toStartOf",
        "app:layout_constraintStart_toEndOf",
        "app:layout_constraintEnd_toStartOf",
        "app:layout_constraintEnd_toEndOf",
        "app:layout_constraintTop_toTopOf",
        "app:layout_constraintTop_toBottomOf",
        "app:layout_constraintBottom_toTopOf",
        "app:layout_constraintBottom_toBottomOf",
        "app:layout_constraintHorizontal_bias",
        "app:layout_constraintVertical_bias",

        // tools:
        "tools:ignore",
        "tools:text",
        "tools:src",
        "tools:visibility",
        "tools:context",
        "tools:targetApi",
    )

    fun tagNames(prefix: String): List<String> {
        if (prefix.isBlank()) return COMMON_TAGS
        val p = prefix.lowercase()
        return COMMON_TAGS.filter { it.lowercase().contains(p) }.take(80)
    }

    fun attributeNames(prefix: String): List<String> {
        val raw = prefix.trim()
        if (raw.isBlank()) return (XMLNS_ATTRS + COMMON_ATTRS)

        val p = raw.lowercase()

        // Special-case: user typed namespace name without ':' (common in ACS)
        if (p == "android") {
            val fw = frameworkAttrsProvider?.invoke().orEmpty().asSequence().map { "android:$it" }
            return (COMMON_ATTRS.asSequence().filter { it.startsWith("android:") } + fw)
                .distinct()
                .sorted()
                .take(400)
                .toList()
        }
        if (p == "tools") {
            return COMMON_ATTRS.filter { it.startsWith("tools:") }.take(200)
        }
        if (p == "app") {
            return COMMON_ATTRS.filter { it.startsWith("app:") }.take(200)
        }

        // xmlns suggestions
        if (p.startsWith("xmlns")) {
            return XMLNS_ATTRS.filter { it.lowercase().contains(p) } +
                COMMON_ATTRS.filter { it.lowercase().contains(p) }.take(80)
        }

        val fw = frameworkAttrsProvider?.invoke().orEmpty()

        // If user is typing a very short prefix (e.g., "a"), don't do expensive distinct+sorted over thousands.
        // Instead, return a capped, fast list.
        if (fw.isNotEmpty() && p.length <= 2) {
            val out = ArrayList<String>(320)

            // Prefer android framework attrs first for ACS-like feel
            for (name in fw) {
                if (out.size >= 260) break
                val full = "android:$name"
                if (full.lowercase().contains(p)) out.add(full)
            }

            // Add some common attrs too
            for (attr in COMMON_ATTRS) {
                if (out.size >= 320) break
                if (attr.lowercase().contains(p) && !out.contains(attr)) out.add(attr)
            }

            return out
        }

        // Longer prefixes: OK to do a slightly heavier pipeline
        return sequence {
            yieldAll(COMMON_ATTRS)
            for (name in fw) yield("android:$name")
        }
            .filter { it.lowercase().contains(p) }
            .distinct()
            .sorted()
            .take(350)
            .toList()
    }

    fun attributeValues(attributeName: String?, prefix: String): List<String> {
        val values = when (attributeName) {
            "android:layout_width", "android:layout_height" -> listOf("match_parent", "wrap_content", "0dp")
            "android:visibility" -> listOf("visible", "invisible", "gone")
            "android:orientation" -> listOf("horizontal", "vertical")
            "android:gravity", "android:layout_gravity" -> listOf(
                "start", "end", "top", "bottom", "center", "center_vertical", "center_horizontal"
            )
            else -> emptyList()
        }

        if (values.isEmpty()) return emptyList()
        if (prefix.isBlank()) return values
        val p = prefix.lowercase()
        return values.filter { it.lowercase().startsWith(p) }
    }
}

private class XmlCompletionContext(
    val nodeType: NodeType,
    val prefix: String,
    val attributeName: String?
) {
    enum class NodeType {
        TAG,
        ATTRIBUTE_NAME,
        ATTRIBUTE_VALUE,
        OTHER
    }

    companion object {
        fun of(content: ContentReference, position: CharPosition): XmlCompletionContext {
            // Compute a prefix that includes ':' and '-' for XML identifiers.
            val line = content.getLine(position.line)
            val prefix = computePrefix(line, position.column)

            // Determine whether we are inside a tag by scanning backwards from the global index.
            // This works across line breaks (common in layout XML).
            val idx = if (position.index >= 0) position.index else content.reference.getCharIndex(position.line, position.column)

            var lastLtIndex = -1
            var lastGtIndex = -1
            runCatching {
                var i = idx - 1
                var steps = 0
                while (i >= 0 && steps < 8000) {
                    val ch = (content.reference as CharSequence)[i]
                    if (ch == '<' && lastLtIndex == -1) {
                        lastLtIndex = i
                        break
                    }
                    if (ch == '>' && lastGtIndex == -1) {
                        lastGtIndex = i
                    }
                    i--
                    steps++
                }
            }

            val insideTag = (lastLtIndex != -1) && (lastGtIndex == -1 || lastLtIndex > lastGtIndex)

            if (insideTag) {
                // Text from '<' (excluded) to cursor. We build it from the content to support multi-line tags.
                val afterLt = runCatching {
                    content.subSequence(lastLtIndex + 1, idx).toString()
                }.getOrDefault("")

                // If there is no whitespace yet, we are still inside tag name (or closing tag name)
                val hasWhitespaceBeforeCursor = afterLt.any { it.isWhitespace() }
                if (!hasWhitespaceBeforeCursor) {
                    val tagPrefix = prefix.removePrefix("/")
                    return XmlCompletionContext(NodeType.TAG, tagPrefix, null)
                }

                // Attribute value: when cursor is between quotes after '='
                val eqIndex = afterLt.lastIndexOf('=')
                if (eqIndex != -1) {
                    val openQuote = afterLt.indexOf('"', startIndex = eqIndex + 1)
                    if (openQuote != -1) {
                        val closeQuote = afterLt.indexOf('"', startIndex = openQuote + 1)
                        if (closeQuote == -1) {
                            // We are inside an unterminated quoted value
                            val attrName = extractAttributeName(afterLt)
                            return XmlCompletionContext(NodeType.ATTRIBUTE_VALUE, prefix, attrName)
                        }
                    }
                }

                // Attribute name
                return XmlCompletionContext(NodeType.ATTRIBUTE_NAME, prefix, null)
            }

            return XmlCompletionContext(NodeType.OTHER, prefix, null)
        }

        private fun computePrefix(line: CharSequence, column: Int): String {
            val end = column.coerceAtMost(line.length)
            var i = end
            while (i > 0) {
                val c = line[i - 1]
                val ok = MyCharacter.isJavaIdentifierPart(c) || c == ':' || c == '-' || c == '.' || c == '/' || c == '@'
                if (!ok) break
                i--
            }
            return line.subSequence(i, end).toString()
        }

        private fun extractAttributeName(before: String): String? {
            // Find last 'name="' pattern
            val iEq = before.lastIndexOf('=')
            if (iEq <= 0) return null

            var i = iEq - 1
            while (i >= 0 && before[i].isWhitespace()) i--
            if (i < 0) return null

            // Scan backwards to start of attr name
            var end = i + 1
            while (i >= 0) {
                val c = before[i]
                val ok = c.isLetterOrDigit() || c == ':' || c == '_' || c == '-'
                if (!ok) break
                i--
            }
            val start = i + 1
            if (start >= end) return null
            return before.substring(start, end)
        }
    }
}
