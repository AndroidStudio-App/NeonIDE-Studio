package com.neonide.studio.app.home.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores values for the project wizard and recent projects.
 *
 * Replicates Android Code Studio behavior:
 * - recent projects are stored as comma-separated absolute paths
 * - most recent is first
 * - max 10 entries
 */
object WizardPreferences {

    private const val PREFS_NAME = "atc_wizard_prefs"
    private const val KEY_LAST_SAVE_LOCATION = "last_save_location"
    private const val KEY_RECENT_PROJECTS = "recent_projects"
    private const val MAX_RECENT_PROJECTS = 10

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSaveLocation(context: Context): String? =
        prefs(context).getString(KEY_LAST_SAVE_LOCATION, null)

    fun setLastSaveLocation(context: Context, path: String) {
        prefs(context).edit().putString(KEY_LAST_SAVE_LOCATION, path).apply()
    }

    fun addRecentProject(context: Context, projectPath: String) {
        val p = prefs(context)
        val recents = getRecentProjects(context).toMutableList()

        // Remove existing occurrence so we can add it to the front.
        recents.remove(projectPath)
        recents.add(0, projectPath)

        val trimmed = recents.take(MAX_RECENT_PROJECTS)
        p.edit().putString(KEY_RECENT_PROJECTS, trimmed.joinToString(",")).apply()
    }

    fun getRecentProjects(context: Context): List<String> {
        val projectsString = prefs(context).getString(KEY_RECENT_PROJECTS, "") ?: ""
        if (projectsString.isBlank()) return emptyList()

        return projectsString
            .split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { path ->
                val file = java.io.File(path)
                if (file.exists() && file.isDirectory) path else null
            }
    }

    fun isRecentProject(context: Context, projectPath: String): Boolean =
        getRecentProjects(context).contains(projectPath)

    /** 0 = most recent, -1 if not present */
    fun getRecentProjectRank(context: Context, projectPath: String): Int =
        getRecentProjects(context).indexOf(projectPath)
}
