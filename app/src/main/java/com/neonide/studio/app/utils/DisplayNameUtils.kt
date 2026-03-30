package com.neonide.studio.app.utils

/**
 * Utilities for safely displaying arbitrary file names/paths in UI.
 *
 * Some storage providers (or corrupted directory entries) may yield names containing control
 * characters or malformed Unicode sequences that can crash text measurement/rendering on some
 * Android versions/skins.
 */
object DisplayNameUtils {

    /**
     * Replace non-printable control chars and unpaired surrogates with the replacement character.
     * Also trims overly long strings for safety.
     */
    fun safeForUi(input: String?, maxLen: Int = 200): String {
        if (input.isNullOrEmpty()) return ""

        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            val isControl = code in 0x00..0x1F || code == 0x7F
            val isSurrogate = ch in Char.MIN_SURROGATE..Char.MAX_SURROGATE

            sb.append(
                when {
                    isControl -> '\uFFFD'
                    // Replace any surrogate; even paired surrogates are safe, but some buggy renderers
                    // choke on them. This is conservative.
                    isSurrogate -> '\uFFFD'
                    else -> ch
                }
            )

            if (sb.length >= maxLen) {
                sb.append('…')
                break
            }
        }

        return sb.toString()
    }
}
