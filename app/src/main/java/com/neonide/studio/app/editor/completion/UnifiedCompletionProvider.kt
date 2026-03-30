package com.neonide.studio.app.editor.completion

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.snippetUpComparator
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import java.io.File

class UnifiedCompletionProvider(
    private val baseLanguage: Language,
    private val currentFile: File?
) : Language by baseLanguage {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        publisher.setComparator(::snippetUpComparator)

        // Get local completions (snippets, etc.) from base language.
        // LSP completions are provided by editor-lsp (see LspLanguage) when attached.
        baseLanguage.requireAutoComplete(content, position, publisher, extraArguments)
    }
}
