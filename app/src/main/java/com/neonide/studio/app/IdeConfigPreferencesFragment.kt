package com.neonide.studio.app

import android.content.Context
import android.os.Bundle
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.neonide.studio.R
import com.neonide.studio.shared.logger.IDEFileLogger
import com.neonide.studio.shared.termux.settings.preferences.TermuxAppSharedPreferences

class IdeConfigPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = context ?: return

        preferenceManager.preferenceDataStore = IdeConfigPreferencesDataStore(ctx)
        setPreferencesFromResource(R.xml.ide_config_preferences, rootKey)

        // Ensure we can show the current file path as summary.
        val pref = findPreference<SwitchPreferenceCompat>(IdeConfigPreferencesDataStore.KEY_FILE_LOGGING)
        pref?.summaryOn = "Writes logs to ${IDEFileLogger.getLogFile()?.absolutePath ?: "/sdcard/Documents/NeonIDE/logs/ide.log"}"
    }
}

private class IdeConfigPreferencesDataStore(private val context: Context) : PreferenceDataStore() {

    companion object {
        const val KEY_FILE_LOGGING = "ide_file_logging_enabled"
    }

    private val prefs: TermuxAppSharedPreferences? = TermuxAppSharedPreferences.build(context, false)

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue
        val p = prefs ?: return defValue

        return when (key) {
            KEY_FILE_LOGGING -> p.isIdeFileLoggingEnabled
            else -> defValue
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (key == null) return
        val p = prefs ?: return

        when (key) {
            KEY_FILE_LOGGING -> {
                p.setIdeFileLoggingEnabled(value)
                if (value) {
                    IDEFileLogger.log(context, "File logging enabled")
                }
            }
        }
    }
}
