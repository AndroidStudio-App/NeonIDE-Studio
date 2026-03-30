package com.neonide.studio.app.lsp

import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import org.eclipse.lsp4j.CompletionItem

class LspCompletionItem(val lspItem: CompletionItem, prefixLength: Int) :
    SimpleCompletionItem(lspItem.label, lspItem.detail ?: "", prefixLength, lspItem.insertText ?: lspItem.label) {

    init {
        this.kind = mapKind(lspItem.kind)
    }

    private fun mapKind(lspKind: org.eclipse.lsp4j.CompletionItemKind?): CompletionItemKind {
        return when (lspKind) {
            org.eclipse.lsp4j.CompletionItemKind.Method -> CompletionItemKind.Method
            org.eclipse.lsp4j.CompletionItemKind.Function -> CompletionItemKind.Function
            org.eclipse.lsp4j.CompletionItemKind.Constructor -> CompletionItemKind.Constructor
            org.eclipse.lsp4j.CompletionItemKind.Field -> CompletionItemKind.Field
            org.eclipse.lsp4j.CompletionItemKind.Variable -> CompletionItemKind.Variable
            org.eclipse.lsp4j.CompletionItemKind.Class -> CompletionItemKind.Class
            org.eclipse.lsp4j.CompletionItemKind.Interface -> CompletionItemKind.Interface
            org.eclipse.lsp4j.CompletionItemKind.Module -> CompletionItemKind.Module
            org.eclipse.lsp4j.CompletionItemKind.Property -> CompletionItemKind.Property
            org.eclipse.lsp4j.CompletionItemKind.Unit -> CompletionItemKind.Unit
            org.eclipse.lsp4j.CompletionItemKind.Value -> CompletionItemKind.Value
            org.eclipse.lsp4j.CompletionItemKind.Enum -> CompletionItemKind.Enum
            org.eclipse.lsp4j.CompletionItemKind.Keyword -> CompletionItemKind.Keyword
            org.eclipse.lsp4j.CompletionItemKind.Snippet -> CompletionItemKind.Snippet
            org.eclipse.lsp4j.CompletionItemKind.Color -> CompletionItemKind.Color
            org.eclipse.lsp4j.CompletionItemKind.File -> CompletionItemKind.File
            org.eclipse.lsp4j.CompletionItemKind.Reference -> CompletionItemKind.Reference
            org.eclipse.lsp4j.CompletionItemKind.Folder -> CompletionItemKind.Folder
            org.eclipse.lsp4j.CompletionItemKind.EnumMember -> CompletionItemKind.EnumMember
            org.eclipse.lsp4j.CompletionItemKind.Constant -> CompletionItemKind.Constant
            org.eclipse.lsp4j.CompletionItemKind.Struct -> CompletionItemKind.Struct
            org.eclipse.lsp4j.CompletionItemKind.Event -> CompletionItemKind.Event
            org.eclipse.lsp4j.CompletionItemKind.Operator -> CompletionItemKind.Operator
            org.eclipse.lsp4j.CompletionItemKind.TypeParameter -> CompletionItemKind.TypeParameter
            else -> CompletionItemKind.Text
        }
    }
}