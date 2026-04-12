package com.neonide.studio.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.neonide.studio.app.home.HomeFragment
import com.neonide.studio.shared.termux.settings.properties.TermuxAppSharedProperties

/**
 * MainActivity for NeonIDE Studio.
 *
 * Displays a permission gate before the user can access [HomeFragment].
 * Once all required permissions are granted and the user taps "Continue",
 * the permission card is dismissed and the home screen is revealed.
 *
 * Uses Jetpack Compose for the UI while hosting the existing [HomeFragment]
 * via the FragmentManager for backward compatibility.
 */
class MainActivity : FragmentActivity() {

    private var showPermissionCard by mutableStateOf(true)
    private var showNotificationRationaleDialog by mutableStateOf(false)

    // Permission state cache — refreshed onResume and after each launcher callback
    private var storageGranted by mutableStateOf(false)
    private var installGranted by mutableStateOf(false)
    private var notificationGranted by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissionState()
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshPermissionState()
    }

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Evaluate initial permission states
        refreshPermissionState()

        // Only show permission card if not all permissions are granted
        showPermissionCard = !checkAllPermissionsGranted()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ACSHomeScreen(
                        showPermissionCard = showPermissionCard,
                        onPermissionCardDismissed = { showPermissionCard = false },
                        onGrantStorageClick = ::onGrantStorageClick,
                        onGrantInstallClick = ::onGrantInstallClick,
                        onGrantNotificationClick = ::onGrantNotificationClick,
                        storageGranted = storageGranted,
                        installGranted = installGranted,
                        notificationGranted = notificationGranted,
                        showNotificationRationaleDialog = showNotificationRationaleDialog,
                        onDismissNotificationRationale = { showNotificationRationaleDialog = false },
                        onRequestNotificationPermission = ::requestNotificationFromDialog,
                        onOpenNotificationSettings = ::openNotificationSettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    // -----------------------------------------------------------------------
    // Permission request handlers
    // -----------------------------------------------------------------------

    private fun onGrantStorageClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory("android.intent.category.DEFAULT")
                    data = Uri.parse("package:${applicationContext.packageName}")
                }
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    private fun onGrantInstallClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            installPermissionLauncher.launch(intent)
        }
    }

    private fun onGrantNotificationClick() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // User denied once but not permanently — show system dialog directly
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            } else {
                // First request OR permanently denied — show custom rationale dialog
                showNotificationRationaleDialog = true
            }
        }
    }

    private fun requestNotificationFromDialog() {
        showNotificationRationaleDialog = false
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun openNotificationSettings() {
        showNotificationRationaleDialog = false
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    // -----------------------------------------------------------------------
    // Permission checks
    // -----------------------------------------------------------------------

    private fun refreshPermissionState() {
        storageGranted = checkStoragePermission()
        installGranted = checkInstallPermission()
        notificationGranted = checkNotificationPermission()
    }

    private fun checkAllPermissionsGranted(): Boolean =
        checkStoragePermission() && checkInstallPermission() && checkNotificationPermission()

    private fun checkStoragePermission(): Boolean {
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        val properties = TermuxAppSharedProperties.getProperties()
        val externalAppsAllowed = properties?.shouldAllowExternalApps() ?: false

        return storageGranted && externalAppsAllowed
    }

    private fun checkInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

// =============================================================================
// Composable UI — Top-level functions for reuse and preview
// =============================================================================

@Composable
private fun ACSHomeScreen(
    showPermissionCard: Boolean,
    onPermissionCardDismissed: () -> Unit,
    onGrantStorageClick: () -> Unit,
    onGrantInstallClick: () -> Unit,
    onGrantNotificationClick: () -> Unit,
    storageGranted: Boolean,
    installGranted: Boolean,
    notificationGranted: Boolean,
    showNotificationRationaleDialog: Boolean,
    onDismissNotificationRationale: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Host HomeFragment inside Compose tree — renders behind the permission overlay
        AndroidView(
            factory = { context ->
                FragmentContainerView(context).apply {
                    id = androidx.fragment.R.id.fragment_container_view_tag
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val activity = view.context as? MainActivity
                activity?.let {
                    if (it.supportFragmentManager.findFragmentById(androidx.fragment.R.id.fragment_container_view_tag) == null) {
                        it.supportFragmentManager.beginTransaction()
                            .replace(androidx.fragment.R.id.fragment_container_view_tag, HomeFragment(), "homeFragment")
                            .commitNow()
                    }
                }
            },
        )

        // Permission overlay — semi-transparent, shows HomeFragment behind
        if (showPermissionCard) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
            ) {
                PermissionCard(
                    storageGranted = storageGranted,
                    installGranted = installGranted,
                    notificationGranted = notificationGranted,
                    allGranted = storageGranted && installGranted && notificationGranted,
                    onGrantStorageClick = onGrantStorageClick,
                    onGrantInstallClick = onGrantInstallClick,
                    onGrantNotificationClick = onGrantNotificationClick,
                    onContinueClick = onPermissionCardDismissed,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .wrapContentHeight(),
                )
            }
        }
    }

    if (showNotificationRationaleDialog) {
        NotificationRationaleDialog(
            onDismiss = onDismissNotificationRationale,
            onGrant = onRequestNotificationPermission,
            onSettings = onOpenNotificationSettings,
        )
    }
}

@Composable
private fun PermissionCard(
    storageGranted: Boolean,
    installGranted: Boolean,
    notificationGranted: Boolean,
    allGranted: Boolean,
    onGrantStorageClick: () -> Unit,
    onGrantInstallClick: () -> Unit,
    onGrantNotificationClick: () -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Permission required to make the app works properly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            PermissionRow(
                icon = Icons.Default.FolderOpen,
                title = "Allow Files Access",
                description = "Required to read and write project files.",
                isGranted = storageGranted,
                onGrantClick = onGrantStorageClick,
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionRow(
                icon = Icons.Default.InstallMobile,
                title = "Allow Installation",
                description = "Required to install built APKs.",
                isGranted = installGranted,
                onGrantClick = onGrantInstallClick,
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionRow(
                icon = Icons.Default.Notifications,
                title = "Allow Notification",
                description = "Required to show build status and updates.",
                isGranted = notificationGranted,
                onGrantClick = onGrantNotificationClick,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onContinueClick,
                enabled = allGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (allGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Continue")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onGrantClick,
            enabled = !isGranted,
        ) {
            Text(
                text = if (isGranted) "Granted" else "Grant",
                color = if (isGranted) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun NotificationRationaleDialog(
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Notification Permission")
        },
        text = {
            Text("Notifications are required to show build status. If the system permission dialog does not appear, please enable them in Settings.")
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text("Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = onSettings) {
                Text("Settings")
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PreviewPermissionCard() {
    MaterialTheme {
        Surface {
            PermissionCard(
                storageGranted = false,
                installGranted = true,
                notificationGranted = false,
                allGranted = false,
                onGrantStorageClick = {},
                onGrantInstallClick = {},
                onGrantNotificationClick = {},
                onContinueClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPermissionCardAllGranted() {
    MaterialTheme {
        Surface {
            PermissionCard(
                storageGranted = true,
                installGranted = true,
                notificationGranted = true,
                allGranted = true,
                onGrantStorageClick = {},
                onGrantInstallClick = {},
                onGrantNotificationClick = {},
                onContinueClick = {},
            )
        }
    }
}
