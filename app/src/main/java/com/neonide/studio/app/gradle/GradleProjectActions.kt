package com.neonide.studio.app.gradle

import android.content.Context
import com.neonide.studio.R
import com.neonide.studio.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File

/**
 * Core logic for "Sync project" and "Quick run" actions.
 *
 * Notes:
 * - This is a lightweight implementation that works by invoking the project's Gradle Wrapper.
 * - It does not use the Android Studio/ACS Tooling API project model.
 * - We still aim to provide the *expected* UX: ensure wrapper exists, run reasonable tasks,
 *   surface build output, and provide basic diagnostics.
 */
object GradleProjectActions {

    /**
     * Ensure that required Gradle wrapper pieces exist.
     *
     * Some imported projects have gradlew + wrapper properties but miss the wrapper jar.
     * Our templates also generate gradlew scripts that execute `java -jar gradle-wrapper.jar`.
     */
    fun ensureWrapperPresent(context: Context, projectDir: File): WrapperStatus {
        val gradlew = File(projectDir, "gradlew")
        val wrapperProps = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        val wrapperJar = File(projectDir, "gradle/wrapper/gradle-wrapper.jar")

        if (!gradlew.exists() || !wrapperProps.exists()) {
            return WrapperStatus.MissingScriptOrProps
        }

        // Make sure gradlew is executable (best-effort).
        runCatching { gradlew.setExecutable(true) }

        if (wrapperJar.exists()) {
            return WrapperStatus.Ok
        }

        // Try to repair by copying a known-good wrapper jar from app assets.
        return runCatching {
            wrapperJar.parentFile?.mkdirs()
            // Prefer new path aligned with ACS assets layout.
            val jarAsset = listOf(
                "gradle/wrapper/gradle-wrapper.jar",
                "gradle-wrapper/gradle-wrapper.jar",
            ).firstOrNull { p ->
                runCatching { context.assets.open(p).close(); true }.getOrDefault(false)
            } ?: "gradle-wrapper/gradle-wrapper.jar"

            context.assets.open(jarAsset).use { input ->
                wrapperJar.outputStream().use { out -> input.copyTo(out) }
            }
            // Also add properties as fallback if needed (should already exist)
            if (!wrapperProps.exists()) {
                wrapperProps.parentFile?.mkdirs()
                val propsAsset = listOf(
                    "gradle/wrapper/gradle-wrapper.properties",
                    "gradle-wrapper/gradle-wrapper.properties",
                ).firstOrNull { p ->
                    runCatching { context.assets.open(p).close(); true }.getOrDefault(false)
                } ?: "gradle-wrapper/gradle-wrapper.properties"
                context.assets.open(propsAsset).use { input ->
                    wrapperProps.outputStream().use { out -> input.copyTo(out) }
                }
            }
            WrapperStatus.Repaired
        }.getOrElse {
            WrapperStatus.RepairFailed
        }
    }

    enum class WrapperStatus {
        Ok,
        Repaired,
        MissingScriptOrProps,
        RepairFailed,
    }

    /** A structured sync plan. */
    data class SyncPlan(
        val args: List<String>,
        val description: String,
    )

    /** A structured build/run plan. */
    data class QuickRunPlan(
        val args: List<String>,
        val description: String,
        val expectedApkSearchDir: File?,
    )

    /**
     * Determine a reasonable "sync" command.
     *
     * We use `help` and `projects`/`tasks` to force Gradle to resolve settings and plugins.
     * `dependencies` can be too heavy on large projects.
     */
    fun createSyncPlan(): SyncPlan {
        // `projects` is a good cheap proxy for "sync": it resolves settings.gradle and includes.
        // `tasks --all` tends to trigger plugin configuration and is useful for diagnosing.
        val args = baseArgs() + listOf("projects")
        return SyncPlan(args = args, description = "Gradle projects")
    }

    /**
     * Determine a reasonable quick-run build based on common Android app tasks.
     *
     * Strategy:
     * - Prefer :app:assembleDebug
     * - Fall back to assembleDebug (single-module)
     */
    fun createQuickRunPlan(projectDir: File): QuickRunPlan {
        // Most templates are single app module called :app.
        // If user opened a different structure, Gradle will fail, but output will show.
        val tasks = listOf(":app:assembleDebug")

        val args = baseArgs() + tasks
        val apkSearchDir = File(projectDir, "app/build/outputs/apk")

        return QuickRunPlan(
            args = args,
            description = "Assemble debug",
            expectedApkSearchDir = apkSearchDir,
        )
    }

    fun baseArgs(): List<String> {
        // NOTE: don't use --no-daemon: wrapper itself downloads gradle distributions and
        // running without daemon can be slower but safer memory-wise. We still keep it off
        // by default for constrained Android env.
        return listOf(
            "--no-daemon",
            "--stacktrace",
            "--console=plain",
        )
    }

    /**
     * Build an environment map suitable for running gradlew.
     *
     * Uses TermuxShellEnvironment so binaries, TMPDIR etc match the Termux runtime.
     */
    fun getGradleEnvironment(context: Context): Map<String, String> {
        return TermuxShellEnvironment().getEnvironment(context, false)
    }

    fun wrapperStatusMessage(context: Context, status: WrapperStatus): String? {
        return when (status) {
            WrapperStatus.Ok -> null
            WrapperStatus.Repaired -> context.getString(R.string.acs_gradle_wrapper_repaired)
            WrapperStatus.MissingScriptOrProps -> context.getString(R.string.acs_gradle_wrapper_missing)
            WrapperStatus.RepairFailed -> context.getString(R.string.acs_gradle_wrapper_repair_failed)
        }
    }
}
