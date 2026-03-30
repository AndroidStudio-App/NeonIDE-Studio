package com.neonide.studio.app.editor.xml

import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.SimpleCompletionIconDrawer
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Fast completion item for XML attributes.
 * Inserts: attrName="" and moves caret between quotes.
 */
class XmlAttrCompletionItem(
    label: CharSequence,
    desc: CharSequence,
    private val attrName: String,
    private val replacePrefixLength: Int,
) : CompletionItem(label, desc) {

    init {
        prefixLength = replacePrefixLength
        kind = CompletionItemKind.Field
        // Ensure icon is present like other completion items
        icon = SimpleCompletionIconDrawer.draw(CompletionItemKind.Field)
        sortText = attrName
        filterText = attrName
    }

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
        val startCol = (column - replacePrefixLength).coerceAtLeast(0)
        val commit = "$attrName=\"\""

        if (replacePrefixLength == 0) {
            text.insert(line, column, commit)
        } else {
            text.replace(line, startCol, line, column, commit)
        }

        // Place caret between quotes
        val caretCol = startCol + attrName.length + 2 // after ="
        editor.setSelection(line, caretCol)
    }
}
