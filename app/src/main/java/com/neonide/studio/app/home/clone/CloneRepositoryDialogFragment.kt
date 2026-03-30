package com.neonide.studio.app.home.clone

import android.app.Dialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neonide.studio.R
import com.neonide.studio.app.SoraEditorActivityK
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.databinding.DialogCloneRepositoryBinding
import com.neonide.studio.shared.shell.command.ExecutionCommand
import com.neonide.studio.shared.shell.command.runner.app.AppShell
import com.neonide.studio.shared.termux.TermuxConstants
import com.neonide.studio.shared.termux.shell.TermuxShellManager
import com.neonide.studio.shared.termux.shell.command.environment.TermuxShellEnvironment
import java.io.File
import java.net.URI

/**
 * Dialog that clones a git repository into Termux $HOME/projects.
 */
class CloneRepositoryDialogFragment : DialogFragment() {

    private var repoNameManuallyEdited = false
    private var lastProgressBytes: Long? = null
    private var lastProgressPercent: Int? = null
    private var lastProgressSpeedBps: Long? = null

    /** The full target directory for the in-progress clone (if any). Used for cleanup on cancel. */
    private var activeTargetDir: File? = null

    // Persisted UI state
    private val prefs by lazy { requireContext().getSharedPreferences("acs_clone_prefs", Context.MODE_PRIVATE) }
    private fun prefGetBool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun prefGetString(key: String, def: String? = null) = prefs.getString(key, def)
    private fun prefPut(block: (android.content.SharedPreferences.Editor) -> Unit) {
        prefs.edit().also(block).apply()
    }

    private val uiHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var progressPoller: Runnable? = null

    companion object {
        private const val ANDROID_DOCS_AUTHORITY = "com.android.externalstorage.documents"
        private const val TERMUX_DOCS_AUTHORITY = "com.neonide.studio.documents"
    }

    private var binding: DialogCloneRepositoryBinding? = null

    private var runningShell: AppShell? = null
    private var isCloning: Boolean = false

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

        binding?.destinationEditText?.setText(dir.absolutePath)
        binding?.destinationLayout?.error = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val inflater = LayoutInflater.from(ctx)
        binding = DialogCloneRepositoryBinding.inflate(inflater)

        val defaultProjectsDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects").absolutePath
        val lastDir = WizardPreferences.getLastSaveLocation(ctx) // keep consistent with create wizard

        // Restore previous values
        binding!!.urlEditText.setText(prefGetString("url", "") ?: "")
        binding!!.destinationEditText.setText(prefGetString("dest", lastDir ?: defaultProjectsDir) ?: (lastDir ?: defaultProjectsDir))
        binding!!.openAfterCloneCheckBox.isChecked = prefGetBool("open_after", true)
        binding!!.shallowCloneSwitch.isChecked = prefGetBool("shallow", false)
        binding!!.depthEditText.setText(prefGetString("depth", "1") ?: "1")

        // Enable depth input only when shallow clone is enabled
        binding!!.depthLayout.isEnabled = binding!!.shallowCloneSwitch.isChecked
        binding!!.shallowCloneSwitch.setOnCheckedChangeListener { _, enabled ->
            binding!!.depthLayout.isEnabled = enabled
            if (!enabled) {
                binding!!.depthLayout.error = null
            }
        }

        // Credentials section
        binding!!.useCredentialsSwitch.isChecked = prefGetBool("use_creds", false)
        binding!!.credentialsContainer.visibility = if (binding!!.useCredentialsSwitch.isChecked) View.VISIBLE else View.GONE
        binding!!.usernameEditText.setText(prefGetString("username", "") ?: "")
        // Never restore password/token for security.
        binding!!.passwordEditText.setText("")

        binding!!.useCredentialsSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding!!.credentialsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Advanced defaults
        binding!!.singleBranchSwitch.isChecked = prefGetBool("single_branch", true)
        binding!!.recurseSubmodulesSwitch.isChecked = prefGetBool("submodules", false)
        binding!!.shallowSubmodulesSwitch.isChecked = prefGetBool("shallow_submodules", false)
        binding!!.shallowSubmodulesSwitch.isEnabled = false
        binding!!.recurseSubmodulesSwitch.setOnCheckedChangeListener { _, enabled ->
            binding!!.shallowSubmodulesSwitch.isEnabled = enabled
            if (!enabled) binding!!.shallowSubmodulesSwitch.isChecked = false
        }

        // Clear previous errors as user types
        binding!!.urlEditText.addTextChangedListener(clearErrorWatcher { binding!!.urlLayout.error = null })
        binding!!.destinationEditText.addTextChangedListener(clearErrorWatcher { binding!!.destinationLayout.error = null })
        binding!!.usernameEditText.addTextChangedListener(clearErrorWatcher { binding!!.usernameLayout.error = null })
        binding!!.passwordEditText.addTextChangedListener(clearErrorWatcher { binding!!.passwordLayout.error = null })
        binding!!.repoNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // If user focuses/types in repo name field, treat as manual.
                if (binding!!.repoNameEditText.hasFocus()) repoNameManuallyEdited = true
                binding!!.repoNameLayout.error = null
            }
        })

        // Auto-fill repo folder name from URL until manually edited
        binding!!.urlEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (repoNameManuallyEdited) return
                val inferred = inferRepoName(s?.toString().orEmpty()) ?: return
                binding!!.repoNameEditText.setText(inferred)
            }
        })

        // Destination browse (folder picker)
        binding!!.destinationLayout.setEndIconOnClickListener { pickDirectory() }

        // Defaults
        binding!!.repoNameEditText.setText(inferRepoName(binding!!.urlEditText.text?.toString().orEmpty()) ?: "")

        // Restore branch
        binding!!.branchEditText.setText(prefGetString("branch", "") ?: "")


        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(binding!!.root)
            .setPositiveButton(R.string.acs_clone_action, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

            positive.setOnClickListener {
                if (runningShell != null) return@setOnClickListener
                startClone()
            }

            negative.setOnClickListener {
                if (isCloning) {
                    stopClone()
                } else {
                    dismiss()
                }
            }
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

        val rawUrl = binding!!.urlEditText.text?.toString()?.trim().orEmpty()
        val destBase = binding!!.destinationEditText.text?.toString()?.trim().orEmpty()
        val repoNameInput = binding!!.repoNameEditText.text?.toString()?.trim().orEmpty()

        // Persist user input immediately (except password)
        prefPut {
            it.putString("url", rawUrl)
            it.putString("dest", destBase)
            it.putBoolean("open_after", binding!!.openAfterCloneCheckBox.isChecked)
            it.putBoolean("shallow", binding!!.shallowCloneSwitch.isChecked)
            it.putString("depth", binding!!.depthEditText.text?.toString()?.trim().orEmpty())
            it.putBoolean("use_creds", binding!!.useCredentialsSwitch.isChecked)
            it.putString("username", binding!!.usernameEditText.text?.toString()?.trim().orEmpty())
            it.putString("branch", binding!!.branchEditText.text?.toString()?.trim().orEmpty())
            it.putBoolean("single_branch", binding!!.singleBranchSwitch.isChecked)
            it.putBoolean("submodules", binding!!.recurseSubmodulesSwitch.isChecked)
            it.putBoolean("shallow_submodules", binding!!.shallowSubmodulesSwitch.isChecked)
        }

        if (rawUrl.isBlank()) {
            binding!!.urlLayout.error = getString(R.string.acs_clone_error_empty_url)
            return
        }

        // Validate URL and folder name
        val inferred = inferRepoName(rawUrl)
        if (inferred == null) {
            binding!!.urlLayout.error = getString(R.string.acs_clone_error_invalid_url)
            return
        }

        val repoName = repoNameInput.ifBlank { inferred }
        if (!repoName.matches(Regex("[A-Za-z0-9._-]+"))) {
            binding!!.repoNameLayout.error = getString(R.string.invalid_name)
            return
        }

        val url = buildCloneUrl(rawUrl)
            ?: run {
                binding!!.urlLayout.error = getString(R.string.acs_clone_error_invalid_url)
                return
            }

        val baseDir = File(destBase)
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!baseDir.exists() || !baseDir.isDirectory) {
            binding!!.destinationLayout.error = getString(R.string.acs_err_invalid_picked_dir)
            return
        }
        if (!baseDir.canWrite()) {
            binding!!.destinationLayout.error = getString(R.string.acs_clone_error_destination_not_writable)
            return
        }

        val targetDir = File(baseDir, repoName)
        if (targetDir.exists()) {
            binding!!.destinationLayout.error = getString(R.string.acs_clone_error_destination_exists)
            return
        }

        // Track for cleanup on stop
        activeTargetDir = targetDir

        val gitPath = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR, "git")
        if (!gitPath.exists()) {
            Toast.makeText(ctx, R.string.acs_clone_error_git_not_found, Toast.LENGTH_LONG).show()
            return
        }

        // Reset progress state
        lastProgressBytes = null
        lastProgressPercent = null
        lastProgressSpeedBps = null

        isCloning = true
        setUiBusy(true, getString(R.string.acs_clone_in_progress))

        val args = mutableListOf("clone", "--progress")

        // Branch options
        val branch = binding!!.branchEditText.text?.toString()?.trim().orEmpty()
        if (branch.isNotBlank()) {
            args += listOf("--branch", branch)
            if (binding!!.singleBranchSwitch.isChecked) {
                args += "--single-branch"
            }
        } else {
            if (binding!!.singleBranchSwitch.isChecked) {
                // Use single-branch with default branch
                args += "--single-branch"
            }
        }

        // Clone options
        if (binding!!.shallowCloneSwitch.isChecked) {
            val depthRaw = binding!!.depthEditText.text?.toString()?.trim().orEmpty()
            val depth = depthRaw.toIntOrNull()
            if (depth == null || depth < 1) {
                binding!!.depthLayout.error = getString(R.string.acs_clone_error_invalid_depth)
                setUiBusy(false, null)
                isCloning = false
                return
            }
            binding!!.depthLayout.error = null
            args += listOf("--depth", depth.toString())
        }

        if (binding!!.recurseSubmodulesSwitch.isChecked) {
            args += "--recurse-submodules"
            if (binding!!.shallowSubmodulesSwitch.isChecked) {
                args += "--shallow-submodules"
            }
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

        // Execute asynchronously and capture results in callback
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
            setUiBusy(false, null)
            Toast.makeText(ctx, R.string.acs_clone_error_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopClone() {
        val ctx = requireContext()
        stopProgressPolling()
        runningShell?.killIfExecuting(ctx, true)
        runningShell = null
        isCloning = false

        // Offer cleanup of partial directory
        val target = activeTargetDir
        activeTargetDir = null

        setUiBusy(false, getString(R.string.acs_clone_error_failed))
        Toast.makeText(ctx, R.string.acs_clone_stop, Toast.LENGTH_SHORT).show()

        if (target != null && target.exists()) {
            MaterialAlertDialogBuilder(ctx)
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

        setUiBusy(false, null)

        val exitCode = cmd.resultData.exitCode
        val stderr = cmd.resultData.stderr?.toString()?.trim().orEmpty()

        if (exitCode != null && exitCode == 0) {
            Toast.makeText(ctx, R.string.acs_clone_success, Toast.LENGTH_SHORT).show()

            val destBase = binding!!.destinationEditText.text?.toString()?.trim().orEmpty()
            val repoName = binding!!.repoNameEditText.text?.toString()?.trim().orEmpty().ifBlank {
                inferRepoName(binding!!.urlEditText.text?.toString()?.trim().orEmpty()).orEmpty()
            }
            val projectDir = if (repoName.isNotBlank()) File(destBase, repoName) else null

            // Clear active dir tracking
            activeTargetDir = null

            if (projectDir != null && projectDir.exists()) {
                WizardPreferences.setLastSaveLocation(ctx, File(destBase).absolutePath)
                WizardPreferences.addRecentProject(ctx, projectDir.absolutePath)

                if (binding!!.openAfterCloneCheckBox.isChecked) {
                    // If it doesn't look like an Android project, confirm before opening.
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

        // Failed clone: show a short error + keep dialog open
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

    private fun setUiBusy(busy: Boolean, status: String?) {
        val dialog = dialog as? androidx.appcompat.app.AlertDialog
        dialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy

        // Negative button becomes Stop during clone instead of dismissing immediately.
        dialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
            isEnabled = true
            text = if (busy) context.getString(R.string.acs_clone_stop) else context.getString(android.R.string.cancel)
        }

        binding?.apply {
            urlLayout.isEnabled = !busy
            repoNameLayout.isEnabled = !busy
            destinationLayout.isEnabled = !busy
            useCredentialsSwitch.isEnabled = !busy
            credentialsContainer.isEnabled = !busy
            branchLayout.isEnabled = !busy
            singleBranchSwitch.isEnabled = !busy
            recurseSubmodulesSwitch.isEnabled = !busy
            shallowSubmodulesSwitch.isEnabled = !busy && recurseSubmodulesSwitch.isChecked
            shallowCloneSwitch.isEnabled = !busy
            openAfterCloneCheckBox.isEnabled = !busy

            progress.visibility = if (busy) View.VISIBLE else View.GONE
            progress.isIndeterminate = false
            if (busy && lastProgressPercent != null) {
                progress.progress = lastProgressPercent ?: 0
            } else {
                progress.progress = 0
            }

            progressInfo.visibility = if (busy) View.VISIBLE else View.GONE
            statusText.text = status ?: ""
            progressDetailsText.text = buildProgressDetailsText()
        }
    }

    private fun clearErrorWatcher(onChange: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onChange()
        }
    }

    private fun buildCloneUrl(rawUrl: String): String? {
        val url = rawUrl.trim()
        if (url.isBlank()) return null

        // If user enabled credentials, we only support https URLs for safe embedding.
        val useCreds = binding?.useCredentialsSwitch?.isChecked == true
        if (!useCreds) return url

        val username = binding?.usernameEditText?.text?.toString()?.trim().orEmpty()
        val password = binding?.passwordEditText?.text?.toString()?.trim().orEmpty()

        if (username.isBlank() || password.isBlank()) {
            // Reuse field hints, but show as errors.
            binding?.usernameLayout?.error = if (username.isBlank()) getString(R.string.acs_clone_username) else null
            binding?.passwordLayout?.error = if (password.isBlank()) getString(R.string.acs_clone_password) else null
            return null
        }

        // Only https is supported for credentials embedding.
        // For ssh URLs, user should use SSH keys in Termux.
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") {
            binding?.urlLayout?.error = getString(R.string.acs_clone_error_invalid_url)
            return null
        }

        // URL encode user/pass for safety
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

    private fun buildProgressDetailsText(): String {
        val percent = lastProgressPercent
        val bytes = lastProgressBytes
        val speed = lastProgressSpeedBps

        val parts = mutableListOf<String>()
        if (percent != null) parts += "$percent%"
        if (bytes != null) parts += formatBytes(bytes)
        if (speed != null) parts += (formatBytes(speed) + "/s")

        return parts.joinToString(" • ")
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

    /**
     * Parse git progress lines, typically printed to stderr.
     * Examples:
     *  - "Receiving objects:  42% (1234/5678), 12.34 MiB | 1.23 MiB/s"
     *  - "Receiving objects: 100% (5678/5678), 120.3 MiB | 2.1 MiB/s"
     */
    private fun parseGitProgress(stderr: String) {
        // Work with last few lines only
        val lastLines = stderr.takeLast(4000)
            .split('\n')
            .takeLast(20)

        val line = lastLines.lastOrNull { it.contains("Receiving objects") || it.contains("Resolving deltas") || it.contains("Counting objects") }
            ?: return

        // Phase
        val phase = when {
            line.contains("Receiving objects") -> "Receiving objects…"
            line.contains("Resolving deltas") -> "Resolving deltas…"
            line.contains("Counting objects") -> "Counting objects…"
            else -> null
        }

        // Percent
        val pct = Regex("(\\d{1,3})%")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(0, 100)

        // Bytes amount (MiB/GiB/KB style)
        val sizeMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(KiB|MiB|GiB|KB|MB|GB)")
            .find(line)

        val bytes = sizeMatch?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: return@let null
            val unit = m.groupValues[2]
            convertToBytes(num, unit)
        }

        // Speed (MiB/s)
        val speedMatch = Regex("\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(KiB|MiB|GiB|KB|MB|GB)/s")
            .find(line)
        val speedBps = speedMatch?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: return@let null
            val unit = m.groupValues[2]
            convertToBytes(num, unit)
        }

        if (pct != null) lastProgressPercent = pct
        if (bytes != null) lastProgressBytes = bytes
        if (speedBps != null) lastProgressSpeedBps = speedBps

        // Update UI
        binding?.apply {
            statusText.text = phase ?: getString(R.string.acs_clone_in_progress)
            progressDetailsText.text = buildProgressDetailsText()
            if (lastProgressPercent != null) {
                progress.progress = lastProgressPercent ?: 0
            }
        }
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

    private fun startProgressPolling(execution: ExecutionCommand) {
        stopProgressPolling()

        progressPoller = object : Runnable {
            override fun run() {
                val shell = runningShell
                if (shell == null) return

                // Read stderr buffer (git writes progress to stderr)
                val stderr = execution.resultData.stderr.toString()
                parseGitProgress(stderr)

                // Continue polling
                uiHandler.postDelayed(this, 250)
            }
        }

        uiHandler.post(progressPoller!!)
    }

    private fun stopProgressPolling() {
        progressPoller?.let { uiHandler.removeCallbacks(it) }
        progressPoller = null
    }

    /**
     * Infer a folder name from the repo url.
     * Supports:
     *  - https://github.com/user/repo.git
     *  - https://github.com/user/repo
     *  - git@github.com:user/repo.git
     */
    private fun inferRepoName(url: String): String? {
        val trimmed = url.trim().removeSuffix("/")
        if (trimmed.isBlank()) return null

        // SSH scp-like syntax: git@github.com:user/repo(.git)
        val scpLike = Regex("^[^@]+@[^:]+:(.+)$").find(trimmed)
        val path = when {
            scpLike != null -> scpLike.groupValues[1]
            else -> {
                // Try URI parsing for http(s)
                runCatching { URI(trimmed) }.getOrNull()?.path
            }
        } ?: return null

        val parts = path.split('/').filter { it.isNotBlank() }
        val last = parts.lastOrNull() ?: return null
        val name = last.removeSuffix(".git")

        // Basic folder name sanity
        if (name.isBlank()) return null
        if (!name.matches(Regex("[A-Za-z0-9._-]+"))) return null

        return name
    }

    private fun openProject(projectDir: File) {
        val ctx = requireContext()
        val intent = Intent(ctx, SoraEditorActivityK::class.java)
        intent.putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, projectDir.absolutePath)
        startActivity(intent)
    }

    private fun looksLikeAndroidProject(dir: File): Boolean {
        // Basic heuristics: either Gradle settings/build files exist.
        val hasGradle = File(dir, "settings.gradle").exists() ||
            File(dir, "settings.gradle.kts").exists() ||
            File(dir, "build.gradle").exists() ||
            File(dir, "build.gradle.kts").exists()
        if (!hasGradle) return false

        // Typical Android app module
        if (File(dir, "app/src/main/AndroidManifest.xml").exists()) return true

        // It might still be a Gradle-based Android project with different module name.
        return true
    }

    override fun onDestroyView() {
        stopProgressPolling()
        super.onDestroyView()
        binding = null
    }
}

