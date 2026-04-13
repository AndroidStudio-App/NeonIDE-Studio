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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

/**
 * DialogFragment that drives the Git Clone workflow.
 *
 * Responsibilities:
 *  - Build and host the Compose UI via [CloneDialogContent]
 *  - Persist / restore user preferences
 *  - Validate form inputs
 *  - Launch and monitor the git-clone shell process
 *  - Parse stderr for progress information
 *  - Handle completion (success / error / cancel)
 *  - Optionally open the cloned project in the editor
 *
 * UI state and composables live in [CloneUiState], [CloneActions],
 * and [CloneDialogContent] respectively.
 */
class CloneRepositoryDialogFragment : DialogFragment() {

    // -----------------------------------------------------------------------
    // Mutable Compose state (managed via mutableStateOf)
    // -----------------------------------------------------------------------

    private var repoNameManuallyEdited by mutableStateOf(false)

    private var lastProgressBytes: Long? = null
    private var lastProgressPercent: Int? = null
    private var lastProgressSpeedBps: Long? = null
    private var activeTargetDir: File? = null

    private val prefs by lazy {
        requireContext().getSharedPreferences("acs_clone_prefs", Context.MODE_PRIVATE)
    }

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

    // -----------------------------------------------------------------------
    // Directory picker result
    // -----------------------------------------------------------------------

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
                    Toast.makeText(
                        ctx,
                        R.string.acs_err_select_primary_storage,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@registerForActivityResult
                }
                File(Environment.getExternalStorageDirectory(), split[1])
            }

            else -> {
                Toast.makeText(
                    ctx,
                    getString(R.string.acs_err_authority_not_allowed, authority),
                    Toast.LENGTH_LONG,
                ).show()
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

    // -----------------------------------------------------------------------
    // Dialog creation
    // -----------------------------------------------------------------------

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val defaultProjectsDir =
            File(TermuxConstants.TERMUX_HOME_DIR, "projects").absolutePath
        val lastDir = WizardPreferences.getLastSaveLocation(ctx)

        // Restore persisted values
        urlText = prefGetString("url", "") ?: ""
        destText = prefGetString("dest", lastDir ?: defaultProjectsDir)
            ?: (lastDir ?: defaultProjectsDir)
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

        val paddingPx = (16 * ctx.resources.displayMetrics.density).toInt()
        val composeView = ComposeView(ctx).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )

            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
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
                            onUrlChange = {
                                urlText = it
                                urlError = null
                                if (!repoNameManuallyEdited) {
                                    repoNameText = inferRepoName(it) ?: ""
                                }
                            },
                            onDestChange = {
                                destText = it
                                destError = null
                            },
                            onRepoNameChange = {
                                repoNameText = it
                                repoNameManuallyEdited = true
                                repoNameError = null
                            },
                            onBranchChange = { branchText = it },
                            onUsernameChange = {
                                usernameText = it
                                usernameError = null
                            },
                            onPasswordChange = {
                                passwordText = it
                                passwordError = null
                            },
                            onDepthChange = {
                                depthText = it
                                depthError = null
                            },
                            onUseCredsChange = { useCreds = it },
                            onShallowCloneChange = { shallowClone = it },
                            onSingleBranchChange = { singleBranch = it },
                            onRecurseSubmodulesChange = {
                                recurseSubmodules = it
                                if (!it) shallowSubmodules = false
                            },
                            onShallowSubmodulesChange = { shallowSubmodules = it },
                            onOpenAfterCloneChange = { openAfterClone = it },
                            onBrowseDest = { pickDirectory() },
                            onClone = { if (runningShell == null) startClone() },
                            onStopOrCancel = { if (isCloning) stopClone() else dismiss() },
                        )

                        Scaffold(
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                            bottomBar = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { if (isCloning) stopClone() else dismiss() }) {
                                        Text(stringResource(android.R.string.cancel))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { if (runningShell == null) startClone() },
                                        enabled = urlText.trim().isNotBlank(),
                                    ) {
                                        Text(stringResource(R.string.acs_clone_action))
                                    }
                                }
                            },
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                                    .padding(horizontal = 8.dp),
                            ) {
                                CloneDialogContent(state, actions)
                            }
                        }
                    }
                }
            }
        }

        val dialog = Dialog(ctx, androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog)
        dialog.setContentView(composeView)
        dialog.setCancelable(false)
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        return dialog
    }

    // -----------------------------------------------------------------------
    // Directory picker
    // -----------------------------------------------------------------------

    private fun pickDirectory() {
        try {
            startForResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.acs_dir_picker_failed, e.message),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // -----------------------------------------------------------------------
    // Clone lifecycle
    // -----------------------------------------------------------------------

    private fun startClone() {
        val ctx = requireContext()
        val rawUrl = urlText.trim()
        val destBase = destText.trim()
        val repoNameInput = repoNameText.trim()

        // Persist preferences
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

        // --- Validation ---
        if (rawUrl.isBlank()) {
            urlError = getString(R.string.acs_clone_error_empty_url)
            return
        }
        val inferred = inferRepoName(rawUrl)
        if (inferred == null) {
            urlError = getString(R.string.acs_clone_error_invalid_url)
            return
        }
        val repoName = repoNameInput.ifBlank { inferred }
        if (!repoName.matches(Regex("[A-Za-z0-9._-]+"))) {
            repoNameError = getString(R.string.invalid_name)
            return
        }

        val url = buildCloneUrl(
            rawUrl,
            useCreds,
            usernameText.trim(),
            passwordText.trim(),
        ) ?: return

        val baseDir = File(destBase)
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!baseDir.exists() || !baseDir.isDirectory) {
            destError = getString(R.string.acs_err_invalid_picked_dir)
            return
        }
        if (!baseDir.canWrite()) {
            destError = getString(R.string.acs_clone_error_destination_not_writable)
            return
        }

        val targetDir = File(baseDir, repoName)
        if (targetDir.exists()) {
            destError = getString(R.string.acs_clone_error_destination_exists)
            return
        }

        activeTargetDir = targetDir
        val gitPath = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR, "git")
        if (!gitPath.exists()) {
            Toast.makeText(ctx, R.string.acs_clone_error_git_not_found, Toast.LENGTH_LONG).show()
            return
        }

        // Reset progress
        lastProgressBytes = null
        lastProgressPercent = null
        lastProgressSpeedBps = null
        isCloning = true
        cloneStatus = getString(R.string.acs_clone_in_progress)

        // --- Build git clone arguments ---
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
            if (depth == null || depth < 1) {
                depthError = getString(R.string.acs_clone_error_invalid_depth)
                isCloning = false
                return
            }
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
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runCatching { target.deleteRecursively() }
                }
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
            val repoName =
                repoNameText.trim().ifBlank { inferRepoName(urlText.trim()).orEmpty() }
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
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                openProject(projectDir)
                                dismissAllowingStateLoss()
                            }
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

        val msg = if (stderr.isNotBlank()) {
            getString(R.string.acs_clone_error_failed) + "\n\n" + stderr.take(800)
        } else {
            getString(R.string.acs_clone_error_failed)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.error)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // -----------------------------------------------------------------------
    // Progress polling
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildCloneUrl(
        rawUrl: String,
        useCreds: Boolean,
        username: String,
        password: String,
    ): String? {
        val url = rawUrl.trim()
        if (url.isBlank()) return null
        if (!useCreds) return url
        if (username.isBlank() || password.isBlank()) {
            if (username.isBlank()) usernameError = getString(R.string.acs_clone_username)
            if (password.isBlank()) passwordError = getString(R.string.acs_clone_password)
            return null
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") {
            urlError = getString(R.string.acs_clone_error_invalid_url)
            return null
        }
        val userEnc = java.net.URLEncoder.encode(username, "UTF-8")
        val passEnc = java.net.URLEncoder.encode(password, "UTF-8")
        val auth = "$userEnc:$passEnc"
        return URI(
            uri.scheme,
            auth,
            uri.host,
            uri.port,
            uri.path,
            uri.query,
            uri.fragment,
        ).toString()
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
        val line = lastLines.lastOrNull {
            it.contains("Receiving objects") ||
                it.contains("Resolving deltas") ||
                it.contains("Counting objects")
        } ?: return

        val pct = Regex("(\\d{1,3})%").find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(0, 100)

        val sizeMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(KiB|MiB|GiB|KB|MB|GB)").find(line)
        val bytes = sizeMatch?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: return@let null
            val unit = m.groupValues[2]
            convertToBytes(num, unit)
        }

        val speedMatch =
            Regex("\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(KiB|MiB|GiB|KB|MB|GB)/s").find(line)
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
        val base = when (unit) {
            "KiB", "MiB", "GiB" -> 1024.0
            else -> 1000.0
        }
        val factor = when (unit) {
            "KiB", "KB" -> base
            "MiB", "MB" -> base * base
            "GiB", "GB" -> base * base * base
            else -> 1.0
        }
        return (value * factor).toLong()
    }

    override fun onDestroyView() {
        stopProgressPolling()
        super.onDestroyView()
    }
}
