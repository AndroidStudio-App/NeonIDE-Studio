package com.neonide.studio.app.gradle

/**
 * Extremely lightweight diagnostics extractor.
 *
 * We cannot build a full Gradle/AGP model here, but we can still show helpful
 * actionable lines (similar to what users look for in Android Studio's Build panel).
 */
object GradleDiagnostics {

    private val interestingPrefixes = listOf(
        "* What went wrong:",
        "* Exception is:",
        "FAILURE:",
        "> ",
        "Caused by:",
        "Execution failed for task",
        "A problem occurred",
        "Could not resolve",
        "Could not find",
        "SDK location not found",
        "No matching variant",
        "Android SDK",
        "Build file",
    )

    /**
     * Extract a concise list of error-ish lines.
     * Returns an empty list if nothing obvious was found.
     */
    fun extract(fullOutput: String): List<String> {
        if (fullOutput.isBlank()) return emptyList()

        val lines = fullOutput
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')

        val out = ArrayList<String>(64)
        val max = 80

        for (line in lines) {
            if (out.size >= max) break
            val trimmed = line.trimEnd()
            if (trimmed.isBlank()) continue

            val isInteresting = interestingPrefixes.any { p ->
                trimmed.startsWith(p, ignoreCase = true)
            } || trimmed.contains("error", ignoreCase = true) && !trimmed.contains("warning", ignoreCase = true)

            if (isInteresting) {
                out.add(trimmed)
            }
        }

        // If nothing found, show last few lines (often includes the failure summary)
        if (out.isEmpty()) {
            val tail = lines.takeLast(30).map { it.trimEnd() }.filter { it.isNotBlank() }
            out.addAll(tail)
        }

        // Deduplicate consecutive duplicates
        val dedup = ArrayList<String>(out.size)
        var prev: String? = null
        for (s in out) {
            if (s == prev) continue
            dedup.add(s)
            prev = s
        }

        return dedup
    }
}
