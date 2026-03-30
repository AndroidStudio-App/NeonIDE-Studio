package com.neonide.studio.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.neonide.studio.R

class ACSHomeActivity : AppCompatActivity() {

    private lateinit var permissionCard: View
    private lateinit var btnGrantStorage: Button
    private lateinit var btnGrantInstall: Button
    private lateinit var btnGrantNotification: Button
    private lateinit var btnContinue: Button

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionUI()
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionUI()
    }

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_acs_home)

        permissionCard = findViewById(R.id.permissionCard)
        btnGrantStorage = findViewById(R.id.btnGrantStorage)
        btnGrantInstall = findViewById(R.id.btnGrantInstall)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)
        btnContinue = findViewById(R.id.btnContinue)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionUI()
    }

    private fun setupClickListeners() {
        btnGrantStorage.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    storagePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    storagePermissionLauncher.launch(intent)
                }
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

        btnGrantInstall.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse(String.format("package:%s", packageName))
                installPermissionLauncher.launch(intent)
            }
        }

        btnGrantNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    // Standard system dialog (User denied once, but not permanently)
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    // First run OR Permanently denied.
                    // We show a custom dialog to give the user a choice, preventing "nothing happens".
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission")
                        .setMessage("Notifications are required to show build status. If the system permission dialog does not appear, please enable them in Settings.")
                        .setPositiveButton("Grant") { _, _ ->
                            requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                        .setNeutralButton("Settings") { _, _ ->
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        btnContinue.setOnClickListener {
            permissionCard.visibility = View.GONE
        }
    }

    private fun refreshPermissionUI() {
        val storageGranted = checkStoragePermission()
        val installGranted = checkInstallPermission()
        val notificationGranted = checkNotificationPermission()

        updateButtonState(btnGrantStorage, storageGranted)
        updateButtonState(btnGrantInstall, installGranted)
        updateButtonState(btnGrantNotification, notificationGranted)

        val allGranted = storageGranted && installGranted && notificationGranted
        btnContinue.isEnabled = allGranted
        
        // If everything is already granted and the card is still visible, we could auto-hide 
        // or let the user click continue. The requirement says click continue.
        // If we are starting up and everything is granted, we might want to just hide it initially.
        // However, the user flow implies we show it if we need to. 
        // Let's hide it if checking on creation/resume and all are TRUE, *unless* we want to force user to click.
        // But usually, if permissions are already granted, we shouldn't block the UI.
        // Let's logic: if visible, wait for click. If we just started, check if we need to show.
        
        if (permissionCard.visibility != View.VISIBLE) {
             if (!allGranted) {
                 permissionCard.visibility = View.VISIBLE
             }
        } else {
            // If it is visible, and all granted, user can click continue.
            // We do nothing else.
        }
    }

    private fun updateButtonState(button: Button, isGranted: Boolean) {
        if (isGranted) {
            button.text = "Granted"
            button.isEnabled = false
            button.alpha = 0.5f // Visual cue
        } else {
            button.text = "Grant"
            button.isEnabled = true
            button.alpha = 1.0f
        }
    }

    private fun checkStoragePermission(): Boolean {
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        val properties = com.neonide.studio.shared.termux.settings.properties.TermuxAppSharedProperties.getProperties()
        val externalAppsAllowed = properties?.shouldAllowExternalApps() ?: false
        
        return storageGranted && externalAppsAllowed
    }

    private fun checkInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true // Below Oreo, this permission works differently or is implicitly granted for play store apps, but for side-loading logic it differs. Assuming true or not relevant for older APIs in this specific context.
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
