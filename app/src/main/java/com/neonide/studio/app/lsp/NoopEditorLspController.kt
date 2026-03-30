package com.neonide.studio.app.lsp

import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

object NoopEditorLspController : EditorLspController {
    override fun attach(editor: CodeEditor, file: File, wrapperLanguage: Language, projectRoot: File?): Boolean = false
    override fun detach() {}
    override fun dispose() {}
    override fun currentEditor(): io.github.rosemoe.sora.lsp.editor.LspEditor? = null
}
