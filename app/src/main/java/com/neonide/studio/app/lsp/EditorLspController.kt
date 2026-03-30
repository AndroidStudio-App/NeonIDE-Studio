package com.neonide.studio.app.lsp

import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

/**
 * API-agnostic interface for wiring LSP features into [CodeEditor].
 *
 * The concrete implementation lives behind reflection and is only instantiated on API 26+.
 */
interface EditorLspController {
    /**
     * Attach LSP to [editor] for [file].
     *
     * @param projectRoot Optional workspace root directory. If null, the controller may fall back to
     *                    [file]'s parent. Passing a stable project root improves features like XML
     *                    schema/catalog resolution.
     */
    fun attach(editor: CodeEditor, file: File, wrapperLanguage: Language, projectRoot: File? = null): Boolean

    /** Detach currently attached LSP editor (if any). */
    fun detach()

    /** Dispose the whole LSP project and release resources. */
    fun dispose()

    /** Returns the currently attached [io.github.rosemoe.sora.lsp.editor.LspEditor], if any. */
    fun currentEditor(): io.github.rosemoe.sora.lsp.editor.LspEditor?
}
