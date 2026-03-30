package com.neonide.studio.app.utils

import java.io.File

/**
 * Safely delete files/directories that may contain invalid directory entry byte sequences.
 *
 * Problem:
 * - Java/Kotlin recursive deletion APIs (File.deleteRecursively, File.listFiles, etc.) can crash
 *   the runtime on some Android builds if a directory contains file names that are not valid
 *   Modified UTF-8 (JNI NewStringUTF abort):
 *   "JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8".
 *
 * Solution:
 * - Delegate deletion to a native process (`rm -rf`) so the runtime never iterates entries.
 *
 * Notes:
 * - This will remove the directory itself.
 * - Requires that `/system/bin/sh` and `rm` exist (toybox).
 */
object SafeFileDeleter {

    private fun shellEscapeSingleQuotes(s: String): String = s.replace("'", "'\\''")

    /** Returns true if deletion succeeded (best-effort). */
    fun deleteRecursively(path: File): Boolean {
        if (!path.exists()) return true

        val abs = path.absolutePath
        val cmd = "rm -rf -- '${shellEscapeSingleQuotes(abs)}'"

        val pb = ProcessBuilder("/system/bin/sh", "-c", cmd)
        pb.redirectErrorStream(true)

        val proc = runCatching { pb.start() }.getOrNull() ?: return false
        runCatching { proc.inputStream.bufferedReader().readText() } // drain
        val code = runCatching { proc.waitFor() }.getOrDefault(-1)

        // If rm succeeded, path should not exist
        if (code == 0) return !path.exists()

        // Even if code != 0, check existence (some devices return non-zero for permission warnings)
        return !path.exists()
    }
}
