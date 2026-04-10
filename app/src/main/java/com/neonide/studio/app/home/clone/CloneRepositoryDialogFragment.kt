package com.neonide.studio.app.home.clone

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neonide.studio.R
import com.neonide.studio.app.SoraEditorActivityK
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.shared.shell.command.ExecutionCommand
import com.neonide.studio.shared.shell.command.runner.app.AppShell
import com.neonide.studio.shared.termux.TermuxConstants
import com.neonide.studio.shared.termux.shell.TermuxShellManager
import com.neonide.studio.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File
import java.net.URI

class CloneRepositoryDialogFragment : DialogFragment() {

    private var repoNameManuallyEdited by mutableStateOf(false)

    private var lastProgressBytes: Long? = null
    private var lastProgressPercent: Int? = null
    private var lastProgressSpeedBps: Long? = null
    private var activeTargetDir: File? = null

    private val prefs by lazy { requireContext().getSharedPreferences("acs_clone_prefs", Context.MODE_PRIVATE) }
    private fun prefGetBool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun prefGetString(key: String, def: String? = null) = prefs.getString(key, def)
    private fun prefPut(block: (android.content.SharedPreferences.Editor) -> Unit) {
        prefs.edit().also(block).apply()
    }

    private val uiHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var progressPoller: Runnable? = null
    private var runningShell: AppShell? = null

    private var urlText by mutableStateOf("")
    private var destText by mutableStateOf("")
    private var repoNameText by mutableStateOf("")
    private var branchText by mutableStateOf("")
    private var usernameText by mutableStateOf("")
    private var passwordText by mutableStateOf("")
    private var depthText by mutableStateOf("1")
    private var useCreds by mutableStateOf(false)
    private var shallowClone by mutableStateOf(false)
    private var singleBranch by mutableStateOf(true)
    private var recurseSubmodules by mutableStateOf(false)
    private var shallowSubmodules by mutableStateOf(false)
    private var openAfterClone by mutableStateOf(true)

    private var isCloning by mutableStateOf(false)
    private var cloneStatus by mutableStateOf<String?>(null)

    private var urlError by mutableStateOf<String?>(null)
    private var destError by mutableStateOf<String?>(null)
    private var repoNameError by mutableStateOf<String?>(null)
    private var usernameError by mutableStateOf<String?>(null)
    private var passwordError by mutableStateOf<String?>(null)
    private var depthError by mutableStateOf<String?>(null)

    companion object {
        private const val ANDROID_DOCS_AUTHORITY = "com.android.externalstorage.documents"
        private const val TERMUX_DOCS_AUTHORITY = "com.neonide.studio.documents"
    }

    private val startForResult = registerForActivityResult(StartActivityForResult()) { result ->
        val uri = result?.data?.data ?: return@registerForActivityResult
        val ctx = requireContext()
        val pickedDir = DocumentFile.fromTreeUri(ctx, uri)
        if (pickedDir == null || !pickedDir.exists()) {
            Toast.makeText(ctx, R.string.acs_err_invalid_picked_dir, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
        val docId = DocumentsContract.getDocumentId(docUri)
        val authority = docUri.authority
        val dir: File = when (authority) {
            TERMUX_DOCS_AUTHORITY -> File(docId)
            ANDROID_DOCS_AUTHORITY -> {
                val split = docId.split(':')
                if (split.size < 2 || split[0] != "primary") {
                    Toast.makeText(ctx, R.string.acs_err_select_primary_storage, Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                File(Environment.getExternalStorageDirectory(), split[1])
            }
            else -> {
                Toast.makeText(ctx, getString(R.string.acs_err_authority_not_allowed, authority), Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
        }
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(ctx, R.string.acs_err_invalid_picked_dir, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        destText = dir.absolutePath
        destError = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val defaultProjectsDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects").absolutePath
        val lastDir = WizardPreferences.getLastSaveLocation(ctx)

        urlText = prefGetString("url", "") ?: ""
        destText = prefGetString("dest", lastDir ?: defaultProjectsDir) ?: (lastDir ?: defaultProjectsDir)
        repoNameText = inferRepoName(urlText) ?: ""
        branchText = prefGetString("branch", "") ?: ""
        useCreds = prefGetBool("use_creds", false)
        usernameText = prefGetString("username", "") ?: ""
        passwordText = ""
        shallowClone = prefGetBool("shallow", false)
        depthText = prefGetString("depth", "1") ?: "1"
        singleBranch = prefGetBool("single_branch", true)
        recurseSubmodules = prefGetBool("submodules", false)
        shallowSubmodules = prefGetBool("shallow_submodules", false)
        openAfterClone = prefGetBool("open_after", true)

        val paddingPx = (24 * ctx.resources.displayMetrics.density).toInt()
        val composeView = androidx.compose.ui.platform.ComposeView(ctx).apply {
            setPadding(paddingPx, 0, paddingPx, 0)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                MaterialTheme {
                    val state = CloneUiState(
                        urlText = urlText,
                        urlError = urlError,
                        destText = destText,
                        destError = destError,
                        repoNameText = repoNameText,
                        repoNameError = repoNameError,
                        branchText = branchText,
                        usernameText = usernameText,
                        usernameError = usernameError,
                        passwordText = passwordText,
                        passwordError = passwordError,
                        depthText = depthText,
                        depthError = depthError,
                        useCreds = useCreds,
                        shallowClone = shallowClone,
                        singleBranch = singleBranch,
                        recurseSubmodules = recurseSubmodules,
                        shallowSubmodules = shallowSubmodules,
                        openAfterClone = openAfterClone,
                        isCloning = isCloning,
                        cloneStatus = cloneStatus,
                        lastProgressPercent = lastProgressPercent,
                        lastProgressBytes = lastProgressBytes,
                        lastProgressSpeedBps = lastProgressSpeedBps,
                    )

                    val actions = CloneActions(
                        onUrlChange = { urlText = it; urlError = null; if (!repoNameManuallyEdited) repoNameText = inferRepoName(it) ?: "" },
                        onDestChange = { destText = it; destError = null },
                        onRepoNameChange = { repoNameText = it; repoNameManuallyEdited = true; repoNameError = null },
                        onBranchChange = { branchText = it },
                        onUsernameChange = { usernameText = it; usernameError = null },
                        onPasswordChange = { passwordText = it; passwordError = null },
                        onDepthChange = { depthText = it; depthError = null },
                        onUseCredsChange = { useCreds = it },
                        onShallowCloneChange = { shallowClone = it },
                        onSingleBranchChange = { singleBranch = it },
                        onRecurseSubmodulesChange = { recurseSubmodules = it; if (!it) shallowSubmodules = false },
                        onShallowSubmodulesChange = { shallowSubmodules = it },
                        onOpenAfterCloneChange = { openAfterClone = it },
                        onBrowseDest = { pickDirectory() },
                        onClone = { if (runningShell == null) startClone() },
                        onStopOrCancel = { if (isCloning) stopClone() else dismiss() }
                    )

                    CloneDialogContent(state, actions)
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(composeView)
            .setPositiveButton(R.string.acs_clone_action, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.apply {
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, (ctx.resources.displayMetrics.heightPixels * 0.88).toInt())
                clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            }

            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

            positive.setOnClickListener { if (runningShell == null) startClone() }
            negative.setOnClickListener { if (isCloning) stopClone() else dismiss() }
        }

        return dialog
    }

    private fun pickDirectory() {
        try {
            startForResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.acs_dir_picker_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun startClone() {
        val ctx = requireContext()
        val rawUrl = urlText.trim()
        val destBase = destText.trim()
        val repoNameInput = repoNameText.trim()

        prefPut {
            it.putString("url", rawUrl)
            it.putString("dest", destBase)
            it.putBoolean("open_after", openAfterClone)
            it.putBoolean("shallow", shallowClone)
            it.putString("depth", depthText.trim())
            it.putBoolean("use_creds", useCreds)
            it.putString("username", usernameText.trim())
            it.putString("branch", branchText.trim())
            it.putBoolean("single_branch", singleBranch)
            it.putBoolean("submodules", recurseSubmodules)
            it.putBoolean("shallow_submodules", shallowSubmodules)
        }

        if (rawUrl.isBlank()) { urlError = getString(R.string.acs_clone_error_empty_url); return }
        val inferred = inferRepoName(rawUrl)
        if (inferred == null) { urlError = getString(R.string.acs_clone_error_invalid_url); return }
        val repoName = repoNameInput.ifBlank { inferred }
        if (!repoName.matches(Regex("[A-Za-z0-9._-]+"))) { repoNameError = getString(R.string.invalid_name); return }

        val url = buildCloneUrl(rawUrl, useCreds, usernameText.trim(), passwordText.trim()) ?: return

        val baseDir = File(destBase)
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!baseDir.exists() || !baseDir.isDirectory) { destError = getString(R.string.acs_err_invalid_picked_dir); return }
        if (!baseDir.canWrite()) { destError = getString(R.string.acs_clone_error_destination_not_writable); return }

        val targetDir = File(baseDir, repoName)
        if (targetDir.exists()) { destError = getString(R.string.acs_clone_error_destination_exists); return }

        activeTargetDir = targetDir
        val gitPath = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR, "git")
        if (!gitPath.exists()) {
            Toast.makeText(ctx, R.string.acs_clone_error_git_not_found, Toast.LENGTH_LONG).show()
            return
        }

        lastProgressBytes = null
        lastProgressPercent = null
        lastProgressSpeedBps = null
        isCloning = true
        cloneStatus = getString(R.string.acs_clone_in_progress)

        val args = mutableListOf("clone", "--progress")
        val branch = branchText.trim()
        if (branch.isNotBlank()) {
            args += listOf("--branch", branch)
            if (singleBranch) args += "--single-branch"
        } else if (singleBranch) {
            args += "--single-branch"
        }
        if (shallowClone) {
            val depth = depthText.trim().toIntOrNull()
            if (depth == null || depth < 1) { depthError = getString(R.string.acs_clone_error_invalid_depth); isCloning = false; return }
            depthError = null
            args += listOf("--depth", depth.toString())
        }
        if (recurseSubmodules) {
            args += "--recurse-submodules"
            if (shallowSubmodules) args += "--shallow-submodules"
        }
        args += listOf(url, targetDir.absolutePath)

        val execution = ExecutionCommand(
            TermuxShellManager.getNextShellId(),
            gitPath.absolutePath,
            args.toTypedArray(),
            null,
            baseDir.absolutePath,
            ExecutionCommand.Runner.APP_SHELL.getName(),
            false,
        ).apply {
            commandLabel = "git-clone"
            commandDescription = "Clone repository"
        }

        runningShell = AppShell.execute(
            ctx.applicationContext,
            execution,
            AppShell.AppShellClient { appShell ->
                val activity = activity ?: return@AppShellClient
                activity.runOnUiThread {
                    stopProgressPolling()
                    onCloneFinished(appShell)
                }
            },
            TermuxShellEnvironment(),
            null,
            false,
        )

        startProgressPolling(execution)
        if (runningShell == null) {
            isCloning = false
            Toast.makeText(ctx, R.string.acs_clone_error_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopClone() {
        stopProgressPolling()
        runningShell?.killIfExecuting(requireContext(), true)
        runningShell = null
        isCloning = false
        val target = activeTargetDir
        activeTargetDir = null
        cloneStatus = getString(R.string.acs_clone_error_failed)
        Toast.makeText(requireContext(), R.string.acs_clone_stop, Toast.LENGTH_SHORT).show()

        if (target != null && target.exists()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.acs_clone_delete_partial)
                .setMessage(R.string.acs_clone_delete_partial_message)
                .setPositiveButton(android.R.string.ok) { _, _ -> runCatching { target.deleteRecursively() } }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun onCloneFinished(appShell: AppShell) {
        val ctx = requireContext()
        val cmd = appShell.executionCommand
        runningShell = null
        isCloning = false
        val exitCode = cmd.resultData.exitCode
        val stderr = cmd.resultData.stderr?.toString()?.trim().orEmpty()

        if (exitCode != null && exitCode == 0) {
            Toast.makeText(ctx, R.string.acs_clone_success, Toast.LENGTH_SHORT).show()
            val destBase = destText.trim()
            val repoName = repoNameText.trim().ifBlank { inferRepoName(urlText.trim()).orEmpty() }
            val projectDir = if (repoName.isNotBlank()) File(destBase, repoName) else null
            activeTargetDir = null

            if (projectDir != null && projectDir.exists()) {
                WizardPreferences.setLastSaveLocation(ctx, File(destBase).absolutePath)
                WizardPreferences.addRecentProject(ctx, projectDir.absolutePath)
                if (openAfterClone) {
                    if (!looksLikeAndroidProject(projectDir)) {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle(R.string.acs_clone_git_repository)
                            .setMessage(R.string.acs_clone_not_android_project)
                            .setPositiveButton(android.R.string.ok) { _, _ -> openProject(projectDir); dismissAllowingStateLoss() }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        return
                    }
                    openProject(projectDir)
                }
            }
            dismissAllowingStateLoss()
            return
        }

        val msg = if (stderr.isNotBlank()) getString(R.string.acs_clone_error_failed) + "\n\n" + stderr.take(800) else getString(R.string.acs_clone_error_failed)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.error)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun startProgressPolling(execution: ExecutionCommand) {
        stopProgressPolling()
        progressPoller = object : Runnable {
            override fun run() {
                if (runningShell == null) return
                val stderr = execution.resultData.stderr.toString()
                parseGitProgress(stderr)
                uiHandler.postDelayed(this, 500)
            }
        }
        uiHandler.post(progressPoller!!)
    }

    private fun stopProgressPolling() {
        progressPoller?.let { uiHandler.removeCallbacks(it) }
        progressPoller = null
    }

    private fun buildCloneUrl(rawUrl: String, useCreds: Boolean, username: String, password: String): String? {
        val url = rawUrl.trim()
        if (url.isBlank()) return null
        if (!useCreds) return url
        if (username.isBlank() || password.isBlank()) {
            if (username.isBlank()) usernameError = getString(R.string.acs_clone_username)
            if (password.isBlank()) passwordError = getString(R.string.acs_clone_password)
            return null
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") { urlError = getString(R.string.acs_clone_error_invalid_url); return null }
        val userEnc = java.net.URLEncoder.encode(username, "UTF-8")
        val passEnc = java.net.URLEncoder.encode(password, "UTF-8")
        val auth = "$userEnc:$passEnc"
        return URI(uri.scheme, auth, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
    }

    private fun inferRepoName(url: String): String? {
        val trimmed = url.trim().removeSuffix("/")
        if (trimmed.isBlank()) return null
        val scpLike = Regex("^[^@]+@[^:]+:(.+)$").find(trimmed)
        val path = when {
            scpLike != null -> scpLike.groupValues[1]
            else -> runCatching { URI(trimmed) }.getOrNull()?.path
        } ?: return null
        val parts = path.split('/').filter { it.isNotBlank() }
        val last = parts.lastOrNull() ?: return null
        val name = last.removeSuffix(".git")
        if (name.isBlank() || !name.matches(Regex("[A-Za-z0-9._-]+"))) return null
        return name
    }

    private fun openProject(projectDir: File) {
        val ctx = requireContext()
        val intent = Intent(ctx, SoraEditorActivityK::class.java)
        intent.putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, projectDir.absolutePath)
        startActivity(intent)
    }

    private fun looksLikeAndroidProject(dir: File): Boolean {
        val hasGradle = File(dir, "settings.gradle").exists() ||
                File(dir, "settings.gradle.kts").exists() ||
                File(dir, "build.gradle").exists() ||
                File(dir, "build.gradle.kts").exists()
        if (!hasGradle) return false
        return File(dir, "app/src/main/AndroidManifest.xml").exists() ||
                File(dir, "src/main/AndroidManifest.xml").exists()
    }

    private fun parseGitProgress(stderr: String) {
        val lastLines = stderr.takeLast(4000).split('\n').takeLast(20)
        val line = lastLines.lastOrNull { it.contains("Receiving objects") || it.contains("Resolving deltas") || it.contains("Counting objects") } ?: return
        val pct = Regex("(\\d{1,3})%").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
        val sizeMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(KiB|MiB|GiB|KB|MB|GB)").find(line)
        val bytes = sizeMatch?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: return@let null
            val unit = m.groupValues[2]
            convertToBytes(num, unit)
        }
        val speedMatch = Regex("\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(KiB|MiB|GiB|KB|MB|GB)/s").find(line)
        val speedBps = speedMatch?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: return@let null
            val unit = m.groupValues[2]
            convertToBytes(num, unit)
        }
        if (pct != null) lastProgressPercent = pct
        if (bytes != null) lastProgressBytes = bytes
        if (speedBps != null) lastProgressSpeedBps = speedBps
    }

    private fun convertToBytes(value: Double, unit: String): Long {
        val base = when (unit) { "KiB", "MiB", "GiB" -> 1024.0; else -> 1000.0 }
        val factor = when (unit) {
            "KiB", "KB" -> base
            "MiB", "MB" -> base * base
            "GiB", "GB" -> base * base * base
            else -> 1.0
        }
        return (value * factor).toLong()
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        val b = bytes.toDouble()
        return when {
            b >= gb -> String.format(java.util.Locale.US, "%.2f GB", b / gb)
            b >= mb -> String.format(java.util.Locale.US, "%.1f MB", b / mb)
            b >= kb -> String.format(java.util.Locale.US, "%.1f KB", b / kb)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        stopProgressPolling()
        super.onDestroyView()
    }
}
private data class CloneUiState(
    val urlText: String = "",
    val urlError: String? = null,
    val destText: String = "",
    val destError: String? = null,
    val repoNameText: String = "",
    val repoNameError: String? = null,
    val branchText: String = "",
    val usernameText: String = "",
    val usernameError: String? = null,
    val passwordText: String = "",
    val passwordError: String? = null,
    val depthText: String = "1",
    val depthError: String? = null,
    val useCreds: Boolean = false,
    val shallowClone: Boolean = false,
    val singleBranch: Boolean = true,
    val recurseSubmodules: Boolean = false,
    val shallowSubmodules: Boolean = false,
    val openAfterClone: Boolean = true,
    val isCloning: Boolean = false,
    val cloneStatus: String? = null,
    val lastProgressPercent: Int? = null,
    val lastProgressBytes: Long? = null,
    val lastProgressSpeedBps: Long? = null,
)

private data class CloneActions(
    val onUrlChange: (String) -> Unit,
    val onDestChange: (String) -> Unit,
    val onRepoNameChange: (String) -> Unit,
    val onBranchChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onDepthChange: (String) -> Unit,
    val onUseCredsChange: (Boolean) -> Unit,
    val onShallowCloneChange: (Boolean) -> Unit,
    val onSingleBranchChange: (Boolean) -> Unit,
    val onRecurseSubmodulesChange: (Boolean) -> Unit,
    val onShallowSubmodulesChange: (Boolean) -> Unit,
    val onOpenAfterCloneChange: (Boolean) -> Unit,
    val onBrowseDest: () -> Unit,
    val onClone: () -> Unit,
    val onStopOrCancel: () -> Unit,
)

@Composable
private fun CloneDialogContent(state: CloneUiState, actions: CloneActions) {
    val scrollState = rememberLazyListState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text(text = stringResource(R.string.acs_clone_git_repository), style = MaterialTheme.typography.titleLarge) }
        item { Text(text = stringResource(R.string.acs_clone_git_repository_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        if (state.isCloning) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { (state.lastProgressPercent ?: 0) / 100f })
                state.cloneStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val progressParts = remember(state.lastProgressPercent, state.lastProgressBytes, state.lastProgressSpeedBps) {
                    buildList {
                        state.lastProgressPercent?.let { add("$it%") }
                        state.lastProgressBytes?.let { add(formatBytes(it)) }
                        state.lastProgressSpeedBps?.let { add(formatBytes(it) + "/s") }
                    }
                }
                if (progressParts.isNotEmpty()) {
                    Text(text = progressParts.joinToString(" \u2022 "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { CloneUrlField(state.urlText, actions.onUrlChange, state.urlError) }
        item { CloneRepoNameField(state.repoNameText, actions.onRepoNameChange, state.repoNameError) }
        item { CloneDestField(state.destText, actions.onDestChange, state.destError, actions.onBrowseDest) }
        item { CloneCredsSwitch(state.useCreds, actions.onUseCredsChange) }

        if (state.useCreds) {
            item { CloneUsernameField(state.usernameText, actions.onUsernameChange, state.usernameError) }
            item { ClonePasswordField(state.passwordText, actions.onPasswordChange, state.passwordError) }
            item { Text(text = stringResource(R.string.acs_clone_credentials_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        item {
            CloneAdvancedSection(
                branchText = state.branchText,
                onBranchChange = actions.onBranchChange,
                singleBranch = state.singleBranch,
                onSingleBranchChange = actions.onSingleBranchChange,
                shallowClone = state.shallowClone,
                onShallowCloneChange = actions.onShallowCloneChange,
                depthText = state.depthText,
                onDepthChange = actions.onDepthChange,
                depthError = state.depthError,
                recurseSubmodules = state.recurseSubmodules,
                onRecurseSubmodulesChange = actions.onRecurseSubmodulesChange,
                shallowSubmodules = state.shallowSubmodules,
                onShallowSubmodulesChange = actions.onShallowSubmodulesChange,
                openAfterClone = state.openAfterClone,
                onOpenAfterCloneChange = actions.onOpenAfterCloneChange,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CloneUrlField(urlText: String, onUrlChange: (String) -> Unit, urlError: String?) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    OutlinedTextField(
        value = urlText, onValueChange = onUrlChange, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        label = { Text(stringResource(R.string.acs_clone_repo_url)) },
        isError = urlError != null,
        supportingText = urlError?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )
}

@Composable
private fun CloneRepoNameField(repoNameText: String, onRepoNameChange: (String) -> Unit, repoNameError: String?) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = repoNameText, onValueChange = onRepoNameChange, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        label = { Text(stringResource(R.string.acs_clone_repo_name)) },
        isError = repoNameError != null,
        supportingText = repoNameError?.let { { Text(it) } },
        singleLine = true
    )
}

@Composable
private fun CloneDestField(destText: String, onDestChange: (String) -> Unit, destError: String?, onBrowseDest: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = destText, onValueChange = onDestChange, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        label = { Text(stringResource(R.string.acs_clone_destination))) },
        isError = destError != null,
        supportingText = destError?.let { { Text(it) } },
        singleLine = true,
        trailingIcon = { IconButton(onClick = onBrowseDest) { Icon(imageVector = Icons.Default.Folder, contentDescription = stringResource(R.string.browse)) } }
    )
}

@Composable
private fun CloneCredsSwitch(useCreds: Boolean, onUseCredsChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(checked = useCreds, onCheckedChange = onUseCredsChange)
        Spacer(Modifier.width(8.dp))
        Text(text = stringResource(R.string.acs_clone_use_credentials))
    }
}

@Composable
private fun CloneUsernameField(usernameText: String, onUsernameChange: (String) -> Unit, usernameError: String?) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = usernameText, onValueChange = onUsernameChange, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        label = { Text(stringResource(R.string.acs_clone_username)) },
        isError = usernameError != null,
        supportingText = usernameError?.let { { Text(it) } },
        singleLine = true
    )
}

@Composable
private fun ClonePasswordField(passwordText: String, onPasswordChange: (String) -> Unit, passwordError: String?) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = passwordText, onValueChange = onPasswordChange, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        label = { Text(stringResource(R.string.acs_clone_password)) },
        visualTransformation = PasswordVisualTransformation(),
        isError = passwordError != null,
        supportingText = passwordError?.let { { Text(it) } },
        singleLine = true
    )
}

@Composable
private fun CloneAdvancedSection(
    branchText: String, onBranchChange: (String) -> Unit,
    singleBranch: Boolean, onSingleBranchChange: (Boolean) -> Unit,
    shallowClone: Boolean, onShallowCloneChange: (Boolean) -> Unit,
    depthText: String, onDepthChange: (String) -> Unit, depthError: String?,
    recurseSubmodules: Boolean, onRecurseSubmodulesChange: (Boolean) -> Unit,
    shallowSubmodules: Boolean, onShallowSubmodulesChange: (Boolean) -> Unit,
    openAfterClone: Boolean, onOpenAfterCloneChange: (Boolean) -> Unit,
) {
    val branchFocusRequester = remember { FocusRequester() }
    val depthFocusRequester = remember { FocusRequester() }

    Text(text = stringResource(R.string.acs_clone_advanced), style = MaterialTheme.typography.titleSmall)

    OutlinedTextField(value = branchText, onValueChange = onBranchChange, modifier = Modifier.fillMaxWidth().focusRequester(branchFocusRequester), label = { Text(stringResource(R.string.acs_clone_branch)) }, singleLine = true)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(checked = singleBranch, onCheckedChange = onSingleBranchChange); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.acs_clone_single_branch)) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(checked = shallowClone, onCheckedChange = onShallowCloneChange); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.acs_clone_shallow)) }

    if (shallowClone) {
        OutlinedTextField(value = depthText, onValueChange = onDepthChange, modifier = Modifier.fillMaxWidth().focusRequester(depthFocusRequester), label = { Text(stringResource(R.string.acs_clone_depth)) }, isError = depthError != null, supportingText = depthError?.let { { Text(it) } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(checked = recurseSubmodules, onCheckedChange = onRecurseSubmodulesChange); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.acs_clone_recurse_submodules)) }
    if (recurseSubmodules) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(checked = shallowSubmodules, onCheckedChange = onShallowSubmodulesChange); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.acs_clone_shallow_submodules)) }
    }

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = openAfterClone, onCheckedChange = onOpenAfterCloneChange)
        Text(stringResource(R.string.acs_clone_open_after))
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = 1024.0; val mb = kb * 1024; val gb = mb * 1024; val b = bytes.toDouble()
    return when {
        b >= gb -> String.format(java.util.Locale.US, "%.2f GB", b / gb)
        b >= mb -> String.format(java.util.Locale.US, "%.1f MB", b / mb)
        b >= kb -> String.format(java.util.Locale.US, "%.1f KB", b / kb)
        else -> "$bytes B"
    }
}
