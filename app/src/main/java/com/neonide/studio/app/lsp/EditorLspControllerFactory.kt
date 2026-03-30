package com.neonide.studio.app.lsp

import android.content.Context
import android.os.Build

object EditorLspControllerFactory {

    private const val IMPL_CLASS = "com.neonide.studio.app.lsp.impl.SoraEditorLspController"

    /**
     * Create the real LSP controller on API 26+.
     *
     * Uses reflection to avoid class loading/verifier issues on lower API levels.
     */
    fun createOrNoop(context: Context): EditorLspController {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return NoopEditorLspController

        return try {
            val cls = Class.forName(IMPL_CLASS)
            val ctor = cls.getDeclaredConstructor(Context::class.java)
            ctor.isAccessible = true
            ctor.newInstance(context.applicationContext) as EditorLspController
        } catch (t: Throwable) {
            // If anything goes wrong (missing class, verifier error, etc.), fall back.
            NoopEditorLspController
        }
    }
}
