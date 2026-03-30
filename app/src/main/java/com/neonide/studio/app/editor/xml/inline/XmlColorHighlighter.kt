package com.neonide.studio.app.editor.xml.inline

import android.graphics.Color
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.color.ConstColor

object XmlColorHighlighter {

    /**
     * Find hex colors in the given text and return highlight ranges.
     *
     * Supported:
     * - #RGB, #ARGB, #RRGGBB, #AARRGGBB
     */
    fun computeHighlights(text: CharSequence, maxHighlights: Int = 400): HighlightTextContainer? {
        val s = text.toString()
        if (s.isEmpty()) return null

        // Simple scan: we don't need regex allocation for large documents.
        val container = HighlightTextContainer()

        var line = 0
        var col = 0
        var i = 0
        var count = 0

        fun advanceTo(idx: Int) {
            while (i < idx && i < s.length) {
                val ch = s[i]
                if (ch == '\n') {
                    line++
                    col = 0
                } else {
                    col++
                }
                i++
            }
        }

        fun isHex(c: Char): Boolean =
            (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')

        // Reset scan pointers
        i = 0
        line = 0
        col = 0

        while (i < s.length && count < maxHighlights) {
            val ch = s[i]
            if (ch != '#') {
                // advance 1
                if (ch == '\n') {
                    line++
                    col = 0
                } else {
                    col++
                }
                i++
                continue
            }

            // Potential color literal
            val startIndex = i
            val startLine = line
            val startCol = col

            var j = i + 1
            while (j < s.length && isHex(s[j]) && (j - (i + 1)) < 8) {
                j++
            }

            val len = j - (i + 1)
            val isValidLen = (len == 3 || len == 4 || len == 6 || len == 8)

            if (!isValidLen) {
                // not a color; advance '#'
                col++
                i++
                continue
            }

            // Must be word-boundary-ish: next char can't be hex (we already stopped at non-hex or max)
            val literal = s.substring(startIndex, j)

            val parsed = runCatching { parseColorLiteral(literal) }.getOrNull()
            if (parsed != null) {
                val endLine = startLine
                val endCol = startCol + (j - startIndex)

                // Background = the color, Border = slightly darker or default
                val bg = ConstColor(parsed)
                val border = ConstColor(blendBorder(parsed))

                container.add(
                    HighlightTextContainer.HighlightText(
                        startLine,
                        startCol,
                        endLine,
                        endCol,
                        bg,
                        border
                    )
                )
                count++
            }

            // advance to j
            val oldI = i
            i = oldI
            advanceTo(j)
        }

        return if (container.isEmpty()) null else container
    }

    private fun parseColorLiteral(lit: String): Int {
        // Normalize #RGB/#ARGB -> #RRGGBB/#AARRGGBB
        if (!lit.startsWith('#')) throw IllegalArgumentException("not hex")
        val hex = lit.substring(1)
        val normalized = when (hex.length) {
            3 -> {
                val r = hex[0]
                val g = hex[1]
                val b = hex[2]
                "#$r$r$g$g$b$b"
            }
            4 -> {
                val a = hex[0]
                val r = hex[1]
                val g = hex[2]
                val b = hex[3]
                "#$a$a$r$r$g$g$b$b"
            }
            6 -> "#$hex"
            8 -> "#$hex"
            else -> throw IllegalArgumentException("bad length")
        }
        return Color.parseColor(normalized)
    }

    private fun blendBorder(color: Int): Int {
        // Darken a bit for border
        val a = Color.alpha(color)
        val r = (Color.red(color) * 0.75f).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * 0.75f).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * 0.75f).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
