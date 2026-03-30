package com.neonide.studio.app.bottomsheet.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.neonide.studio.R
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

class BuildOutputFragment : Fragment(R.layout.fragment_build_output) {

    private var editor: CodeEditor? = null

    private var lastSnapshotLen: Int = 0

    private val listener: (String) -> Unit = listener@{ snapshot ->
        val ed = editor ?: return@listener

        // Handle clear/reset immediately
        if (snapshot.isEmpty()) {
            runCatching {
                ed.setText("", true, null)
            }
            lastSnapshotLen = 0
            return@listener
        }

        // Append only the delta to preserve zoom/scroll state.
        val delta = if (snapshot.length >= lastSnapshotLen) {
            snapshot.substring(lastSnapshotLen)
        } else {
            // Buffer was trimmed/reset; reload.
            lastSnapshotLen = 0
            snapshot
        }

        if (delta.isEmpty()) {
            lastSnapshotLen = snapshot.length
            return@listener
        }

        // Ensure editor uses LF separators consistently.
        val toInsert = delta.replace("\r\n", "\n")

        runCatching {
            val content: Content = ed.text
            val lastLine = content.lineCount - 1
            val lastCol = content.getColumnCount(lastLine)
            content.insert(lastLine, lastCol, toInsert)

            // Auto-scroll to end if user is already near bottom.
            // (Simple heuristic: always scroll for now.)
            ed.setSelection(content.lineCount - 1, content.getColumnCount(content.lineCount - 1))
        }

        lastSnapshotLen = snapshot.length
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ed = view.findViewById<CodeEditor>(R.id.build_output_editor)
        ed.setEditorLanguage(EmptyLanguage())
        ed.setEditable(false)
        ed.isWordwrap = false
        ed.typefaceText = android.graphics.Typeface.MONOSPACE
        ed.setTextSize(12f)

        // Enable zooming in/out
        ed.setScalable(true)

        // When inside other containers, keep horizontal scroll inside editor
        ed.setInterceptParentHorizontalScrollIfNeeded(true)

        // Initialize with empty content
        ed.setText("", true, null)
        lastSnapshotLen = 0
        editor = ed

        // Subscribe (will immediately emit current snapshot)
        BuildOutputBuffer.addListener(listener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Can't removeListener easily because lambda differs; keep local instance.
        BuildOutputBuffer.removeListener(listener)
        editor?.release()
        editor = null
    }
}
