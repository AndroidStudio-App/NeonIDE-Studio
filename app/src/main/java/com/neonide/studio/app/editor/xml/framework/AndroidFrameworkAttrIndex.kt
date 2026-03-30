package com.neonide.studio.app.editor.xml.framework

import android.content.Context
import android.util.Log
import com.neonide.studio.app.gradle.AndroidSdkUtils
import com.neonide.studio.app.gradle.GradleProjectActions
import com.neonide.studio.shared.termux.TermuxConstants
import java.io.File

/**
 * Lazy, cached index of Android framework XML attributes.
 *
 * ACS uses a resource table + manifest tables. Here we approximate that by parsing
 * <sdk>/platforms/android-<api>/data/res/values/attrs.xml and extracting <attr name="...">.
 *
 * This provides a very large attribute set (thousands) so typing "a" shows many android:* entries.
 */
object AndroidFrameworkAttrIndex {

    private const val TAG = "AndroidFwAttrIndex"

    @Volatile private var cached: Set<String>? = null
    @Volatile private var cachedSource: File? = null

    /**
     * Ensure the framework attribute index is loaded.
     *
     * @return true if loaded successfully.
     */
    fun ensureLoaded(context: Context): Boolean {
        if (cached != null) return true

        synchronized(this) {
            if (cached != null) return true

            val baseEnv = runCatching { GradleProjectActions.getGradleEnvironment(context) }.getOrNull() ?: emptyMap()
            val sdkDir = AndroidSdkUtils.resolveSdkDir(baseEnv)
                ?: File(TermuxConstants.TERMUX_HOME_DIR_PATH, "android-sdk").takeIf { it.exists() }

            if (sdkDir == null || !sdkDir.exists()) {
                Log.w(TAG, "Android SDK not found; framework attr completion disabled")
                return false
            }

            val attrsFile = resolveBestAttrsXml(sdkDir) ?: run {
                Log.w(TAG, "framework attrs.xml not found under ${sdkDir.absolutePath}")
                return false
            }

            val names = runCatching { parseAttrNames(attrsFile) }.getOrElse {
                Log.e(TAG, "Failed parsing attrs.xml: ${it.message}")
                emptySet()
            }

            if (names.isEmpty()) {
                Log.w(TAG, "No attrs extracted from ${attrsFile.absolutePath}")
                return false
            }

            cached = names
            cachedSource = attrsFile
            Log.d(TAG, "Loaded ${names.size} framework attrs from ${attrsFile.name}")
            return true
        }
    }

    fun isLoaded(): Boolean = cached != null

    /** Return all loaded framework attrs (raw names, without "android:"). */
    fun allAttrs(): Set<String> = cached ?: emptySet()

    fun suggestions(prefix: String, limit: Int = 400): List<String> {
        val set = cached ?: return emptyList()
        if (prefix.isBlank()) {
            return set.asSequence().sorted().take(limit).toList()
        }

        val p = prefix.lowercase()
        return set.asSequence()
            .filter { it.contains(p) }
            .sorted()
            .take(limit)
            .toList()
    }

    private fun resolveBestAttrsXml(sdkDir: File): File? {
        val platforms = File(sdkDir, "platforms")
        if (!platforms.exists() || !platforms.isDirectory) return null

        val dirs = platforms.listFiles()?.filter { it.isDirectory && it.name.startsWith("android-") }
            ?.sortedByDescending { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
            ?: emptyList()

        for (d in dirs) {
            val f = File(d, "data/res/values/attrs.xml")
            if (f.exists() && f.isFile) return f
        }
        return null
    }

    private fun parseAttrNames(attrsXml: File): Set<String> {
        // Fast streaming parse. We only need <attr name="..."> tokens.
        // Use byte scanning to avoid heavyweight XML parsers.
        val bytes = attrsXml.inputStream().use { it.readBytes() }
        val out = HashSet<String>(5000)

        val key = "<attr name=\"".toByteArray()
        var i = 0
        while (i < bytes.size - key.size) {
            var matched = true
            for (k in key.indices) {
                if (bytes[i + k] != key[k]) {
                    matched = false
                    break
                }
            }
            if (!matched) {
                i++
                continue
            }

            val start = i + key.size
            var end = start
            while (end < bytes.size && bytes[end] != '"'.code.toByte()) {
                end++
            }
            if (end > start) {
                val name = String(bytes, start, end - start, Charsets.UTF_8)
                if (name.isNotBlank()) {
                    out.add(name)
                }
            }
            i = end + 1
        }

        return out
    }
}
