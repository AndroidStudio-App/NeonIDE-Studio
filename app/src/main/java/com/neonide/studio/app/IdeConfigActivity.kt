package com.neonide.studio.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * IDE configuration screen.
 *
 * Currently hosts a small set of developer/diagnostic settings, including file logging.
 */
class IdeConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "IDE Configurations"

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, IdeConfigPreferencesFragment())
                .commit()
        }
    }
}
