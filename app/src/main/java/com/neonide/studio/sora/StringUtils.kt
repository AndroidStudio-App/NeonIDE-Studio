package com.neonide.studio.sora

fun CharSequence.codePointStringAt(index: Int): String {
    val cp = Character.codePointAt(this, index)
    return String(Character.toChars(cp))
}

fun String.escapeCodePointIfNecessary() =
    when (this) {
        "\n" -> "\\n"
        "\t" -> "\\t"
        "\r" -> "\\r"
        " " -> "<ws>"
        else -> this
    }
