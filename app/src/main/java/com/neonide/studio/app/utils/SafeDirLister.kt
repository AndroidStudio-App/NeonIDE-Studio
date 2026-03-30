package com.neonide.studio.app.utils

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Avoid using [File.listFiles]/[File.list] on directories that may contain invalid UTF-8 byte
 * sequences in file names, which can crash the JVM/ART with:
 * "JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8".
 *
 * We instead delegate listing to a separate native process (`ls`) and parse its stdout.
 *
 * Limitations:
 * - Entries with truly non-decodable names will be replaced by U+FFFD in output; those entries
 *   will not be accessible via Java File APIs and will be skipped.
 */
object SafeDirLister {

    private fun shellEscapeSingleQuotes(s: String): String = s.replace("'", "'\\''")

    /**
     * Returns entries in [dir] using `ls -1Ap`.
     *
     * Each returned [Entry] includes whether the entry is a directory (best-effort, via trailing '/').
     */
    fun listEntries(dir: File, limit: Int = 500): List<Entry> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        // Use toybox/ls in a separate process so ART doesn't attempt to decode directory entry
        // bytes via NewStringUTF (which can abort the app).
        val cmd = "cd '${shellEscapeSingleQuotes(dir.absolutePath)}' && ls -1Ap"

        val pb = ProcessBuilder("/system/bin/sh", "-c", cmd)
        pb.redirectErrorStream(true)

        val process = runCatching { pb.start() }.getOrNull() ?: return emptyList()

        val out = runCatching {
            InputStreamReader(process.inputStream, Charset.forName("UTF-8")).readText()
        }.getOrDefault("")

        runCatching { process.waitFor() }

        val lines = out.split('\n')
            .asSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .take(limit)
            .toList()

        return lines.map { raw ->
            val isDir = raw.endsWith("/")
            val name = if (isDir) raw.removeSuffix("/") else raw
            Entry(name = name, isDirectory = isDir)
        }
    }

    fun listFiles(dir: File, limit: Int = 500): List<File> {
        return listEntries(dir, limit)
            .map { e -> File(dir, e.name) }
            .filter { it.exists() }
    }

    fun listDirs(dir: File, limit: Int = 500): List<File> {
        return listEntries(dir, limit)
            .filter { it.isDirectory }
            .map { e -> File(dir, e.name) }
            .filter { it.exists() && it.isDirectory }
    }

    data class Entry(val name: String, val isDirectory: Boolean)
}
