package com.neonide.studio.app.gradle

import android.content.Context
import com.neonide.studio.shared.termux.TermuxConstants
import java.io.File

object AndroidSdkUtils {

    data class SdkConfig(
        val sdkDir: File,
        val env: Map<String, String>,
    )

    /**
     * Try to resolve Android SDK location.
     *
     * Priority:
     * 1) Existing env (ANDROID_HOME/ANDROID_SDK_ROOT) if points to existing dir
     * 2) $HOME/android-sdk (common Termux layout)
     * 3) $HOME/Android/Sdk
     * 4) /sdcard/Android/Sdk (fallback)
     */
    fun resolveSdkDir(baseEnv: Map<String, String>): File? {
        val fromEnv = (baseEnv["ANDROID_SDK_ROOT"] ?: baseEnv["ANDROID_HOME"])?.trim().orEmpty()
        if (fromEnv.isNotEmpty()) {
            val f = File(fromEnv)
            if (f.exists() && f.isDirectory) return f
        }

        val home = File(baseEnv["HOME"] ?: TermuxConstants.TERMUX_HOME_DIR_PATH)

        val candidates = listOf(
            File(home, "android-sdk"),
            File(home, "Android/Sdk"),
            File("/sdcard/Android/Sdk"),
        )

        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }

    private fun ensureLocalProperty(projectDir: File, key: String, value: String) {
        val lp = File(projectDir, "local.properties")
        val prefix = "$key="

        // If already correct, do nothing.
        if (lp.exists()) {
            runCatching {
                val existing = lp.readLines().firstOrNull { it.trim().startsWith(prefix) }
                if (existing != null && existing.trim() == prefix + value) return
            }
        }

        // Merge/overwrite key line, preserve other properties.
        runCatching {
            val newLines = mutableListOf<String>()
            if (lp.exists()) {
                for (line in lp.readLines()) {
                    if (line.trim().startsWith(prefix)) continue
                    newLines.add(line)
                }
            }
            newLines.add(prefix + value)
            lp.writeText(newLines.joinToString("\n") + "\n")
        }
    }

    /** Ensure project local.properties has sdk.dir so AGP doesn't complain. */
    fun ensureSdkDir(projectDir: File, sdkDir: File) {
        ensureLocalProperty(projectDir, "sdk.dir", sdkDir.absolutePath)
    }

    /** Ensure project gradle.properties contains a property (used for aapt2 override). */
    private fun ensureGradleProperty(projectDir: File, key: String, value: String) {
        val gp = File(projectDir, "gradle.properties")
        val prefix = "$key="

        val lines = if (gp.exists()) runCatching { gp.readLines() }.getOrDefault(emptyList()) else emptyList()
        val filtered = lines.filterNot { it.trim().startsWith(prefix) }
        val newLines = filtered + (prefix + value)
        runCatching { gp.writeText(newLines.joinToString("\n") + "\n") }
    }

    /**
     * Pick best build-tools aapt2 binary that is runnable on Android (bionic/static), not a host glibc binary.
     *
     * Preference strategy:
     * - Prefer newest build-tools version where aapt2 passes ELF check for current ABI and interpreter.
     * - If newest is not runnable (e.g., x86_64 glibc), fall back to older versions until a bionic/static one is found.
     */
    fun resolveBestBionicAapt2(context: Context, sdkDir: File): File? {
        val buildToolsDir = File(sdkDir, "build-tools")
        if (!buildToolsDir.exists() || !buildToolsDir.isDirectory) return null

        // Prefer device supported ABI order.
        val supported = android.os.Build.SUPPORTED_ABIS?.toList() ?: emptyList()

        val versions = buildToolsDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()
        for (v in versions) {
            val aapt2 = File(v, "aapt2")
            if (!aapt2.exists() || !aapt2.isFile) continue

            val ok = ElfInspector.isAndroidRunnable(aapt2, supported)
            if (ok) return aapt2
        }

        return null
    }

    /** Ensure project local.properties has ndk.dir if ndk exists. */
    fun ensureNdkDir(projectDir: File, ndkDir: File) {
        ensureLocalProperty(projectDir, "ndk.dir", ndkDir.absolutePath)
    }

    /** Find the newest installed NDK under the SDK dir. */
    fun resolveNdkDir(sdkDir: File): File? {
        val ndkRoot = File(sdkDir, "ndk")
        if (ndkRoot.exists() && ndkRoot.isDirectory) {
            val versions = ndkRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
            if (versions.isNotEmpty()) return versions.last()
        }

        val ndkBundle = File(sdkDir, "ndk-bundle")
        return ndkBundle.takeIf { it.exists() && it.isDirectory }
    }

    /**
     * Patch <ndk>/ndk-build to a wrapper that works on Android/Termux.
     *
     * This matches the fix you provided:
     *   #!/bin/sh
     *   DIR=$(cd "$(dirname "$0")" && pwd)
     *   /data/data/com.neonide.studio/files/usr/bin/bash "$DIR/build/ndk-build" "$@"
     */
    fun ensureNdkBuildPatched(ndkDir: File) {
        val inner = File(ndkDir, "build/ndk-build")
        if (!inner.exists()) return

        val ndkBuild = File(ndkDir, "ndk-build")
        val termuxBash = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash").absolutePath

        val desired = """
            #!/bin/sh
            DIR=${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)
            $termuxBash "${'$'}DIR/build/ndk-build" "${'$'}@"
        """.trimIndent() + "\n"

        // If already patched, do nothing.
        if (ndkBuild.exists()) {
            val current = runCatching { ndkBuild.readText() }.getOrNull()
            if (current != null && current.contains(termuxBash) && current.contains("build/ndk-build")) {
                runCatching { ndkBuild.setExecutable(true) }
                return
            }
        }

        runCatching {
            ndkBuild.writeText(desired)
            ndkBuild.setExecutable(true)
        }
    }

    /**
     * Build environment overrides that mimic a typical Termux Android SDK setup.
     */
    fun buildEnvOverrides(baseEnv: Map<String, String>, sdkDir: File): Map<String, String> {
        val overrides = HashMap<String, String>()
        overrides["ANDROID_HOME"] = sdkDir.absolutePath
        overrides["ANDROID_SDK_ROOT"] = sdkDir.absolutePath

        val currentPath = baseEnv["PATH"].orEmpty()
        val parts = ArrayList<String>()

        fun addIfExists(path: File) {
            if (path.exists() && path.isDirectory) parts.add(path.absolutePath)
        }

        // cmdline-tools/latest/bin
        addIfExists(File(sdkDir, "cmdline-tools/latest/bin"))
        // platform-tools
        addIfExists(File(sdkDir, "platform-tools"))
        // build-tools/<highest>
        val buildToolsDir = File(sdkDir, "build-tools")
        val buildTools = buildToolsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        if (buildTools.isNotEmpty()) addIfExists(buildTools.last())

        // Keep whatever TermuxShellEnvironment provided as well.
        if (currentPath.isNotBlank()) parts.add(currentPath)

        overrides["PATH"] = parts.joinToString(":")
        return overrides
    }

    /**
     * Convenience: detect SDK (+ optional NDK), update local.properties, patch ndk-build, and return env overrides.
     */
    fun configureForProject(
        context: Context,
        projectDir: File,
        baseEnv: Map<String, String>,
    ): SdkConfig? {
        val sdkDir = resolveSdkDir(baseEnv) ?: return null

        ensureSdkDir(projectDir, sdkDir)

        val ndkDir = resolveNdkDir(sdkDir)
        if (ndkDir != null) {
            ensureNdkDir(projectDir, ndkDir)
            ensureNdkBuildPatched(ndkDir)
        }

        // Patch aapt2 override in gradle.properties if a compatible build-tools aapt2 is found.
        val aapt2 = resolveBestBionicAapt2(context, sdkDir)
        if (aapt2 != null) {
            ensureGradleProperty(projectDir, "android.aapt2FromMavenOverride", aapt2.absolutePath)
        }

        val env = buildEnvOverrides(baseEnv, sdkDir)
        return SdkConfig(sdkDir = sdkDir, env = env)
    }
}
