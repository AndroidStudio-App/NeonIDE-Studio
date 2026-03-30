package com.neonide.studio.app.home.open

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.neonide.studio.R
import com.neonide.studio.app.SoraEditorActivityK
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.neonide.studio.shared.termux.TermuxConstants
import com.neonide.studio.app.utils.DisplayNameUtils
import com.neonide.studio.app.utils.SafeDirLister
import com.neonide.studio.app.utils.SafeFileDeleter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OpenProjectBottomSheet : BottomSheetDialogFragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val ANDROID_DOCS_AUTHORITY = "com.android.externalstorage.documents"
        private const val TERMUX_DOCS_AUTHORITY = "com.neonide.studio.documents"
    }

    private var adapter: ProjectsListAdapter? = null

    private val startForResult =
        registerForActivityResult(StartActivityForResult()) { result ->
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

            dismiss()
            openProject(dir)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.bottomsheet_project_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.projectsRecyclerView)
        val emptyText = view.findViewById<TextView>(R.id.emptyText)
        val btnBrowse = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBrowse)
        val searchEditText = view.findViewById<TextInputEditText>(R.id.searchEditText)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val projects = loadProjects()

        if (projects.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = getString(R.string.no_projects_found)
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyText.visibility = View.GONE

            adapter = ProjectsListAdapter(
                projects = projects,
                onProjectClick = { dir ->
                    dismiss()
                    openProject(dir)
                },
                onProjectLongClick = { project ->
                    showProjectOptionsDialog(project) {
                        // refresh
                        val updated = loadProjects()
                        adapter?.updateProjects(updated)
                        if (updated.isEmpty()) {
                            recyclerView.visibility = View.GONE
                            emptyText.visibility = View.VISIBLE
                            emptyText.text = getString(R.string.no_projects_found)
                        }
                    }
                },
            )
            recyclerView.adapter = adapter

            searchEditText?.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        adapter?.filter(s?.toString() ?: "")

                        if ((adapter?.itemCount ?: 0) == 0) {
                            recyclerView.visibility = View.GONE
                            emptyText.visibility = View.VISIBLE
                            emptyText.text = getString(R.string.no_projects_match_search)
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            emptyText.visibility = View.GONE
                        }
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                }
            )
        }

        btnBrowse.setOnClickListener {
            // Don't dismiss before launching SAF picker; otherwise the fragment may be destroyed
            // and the result callback won't be delivered.
            pickDirectory()
        }
    }

    private fun loadProjects(): List<File> {
        val ctx = requireContext()

        val projectsDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects")
        val allProjectDirs = SafeDirLister.listDirs(projectsDir)
        val projectDirProjects = allProjectDirs.filter { isValidAndroidProject(it) }

        val recentProjectPaths = WizardPreferences.getRecentProjects(ctx)
        val recentProjectFiles = recentProjectPaths.mapNotNull { path ->
            val f = File(path)
            if (f.exists() && f.isDirectory && isValidAndroidProject(f)) f else null
        }

        // Debug logs to help diagnose "no projects shown" reports.
        android.util.Log.d(
            "ACS/OpenProject",
            "TERMUX_HOME_DIR=${TermuxConstants.TERMUX_HOME_DIR}; projectsDir=${projectsDir.absolutePath}; " +
                "exists=${projectsDir.exists()} isDir=${projectsDir.isDirectory}; " +
                "dirsFound=${allProjectDirs.size}; validAndroidProjects=${projectDirProjects.size}; " +
                "recentPaths=${recentProjectPaths.size}; recentValid=${recentProjectFiles.size}",
        )

        val allProjectsMap = mutableMapOf<String, File>()
        recentProjectFiles.forEach { allProjectsMap[it.absolutePath] = it }
        projectDirProjects.forEach { allProjectsMap[it.absolutePath] = it }

        return allProjectsMap.values
            .toList()
            .sortedWith(
                compareBy<File> { project ->
                    val idx = recentProjectPaths.indexOf(project.absolutePath)
                    if (idx >= 0) idx else Int.MAX_VALUE
                }.thenByDescending { it.lastModified() }
            )
    }

    private fun listDirsInDirectory(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isDirectory }
    }

    private fun isValidAndroidProject(dir: File): Boolean {
        if (!dir.isDirectory) return false

        // Be permissive: many lightweight templates or imported projects may not ship with the Gradle wrapper.
        // We still want them to show up in the list.
        val hasRootBuild = File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()
        val hasSettings = File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists()
        val hasGradlew = File(dir, "gradlew").exists() || File(dir, "gradlew.bat").exists()
        val hasWrapper = File(dir, "gradle/wrapper/gradle-wrapper.properties").exists()

        val hasAppModuleBuild =
            File(dir, "app/build.gradle").exists() || File(dir, "app/build.gradle.kts").exists()

        return hasRootBuild || hasSettings || hasGradlew || hasWrapper || hasAppModuleBuild
    }

    private fun openProject(root: File) {
        val ctx = requireContext()
        WizardPreferences.addRecentProject(ctx, root.absolutePath)
        val intent = Intent(ctx, SoraEditorActivityK::class.java)
        intent.putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, root.absolutePath)
        startActivity(intent)
    }

    private fun pickDirectory() {
        try {
            startForResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.acs_dir_picker_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun showProjectOptionsDialog(project: File, onActionComplete: () -> Unit) {
        val options = arrayOf(
            getString(R.string.backup_project),
            getString(R.string.delete_project_title_short),
            getString(R.string.rename),
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(DisplayNameUtils.safeForUi(project.name))
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> backupProject(project, onActionComplete)
                    1 -> showDeleteProjectConfirmation(project, onActionComplete)
                    2 -> showRenameDialog(project, onActionComplete)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteProjectConfirmation(project: File, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_project_title))
            .setMessage(getString(R.string.delete_project_message, DisplayNameUtils.safeForUi(project.name)))
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                dialog.dismiss()
                deleteProject(project, onDeleted)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteProject(project: File, onDeleted: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            // Avoid File.deleteRecursively() since it may crash on invalid directory entry byte sequences
            // (JNI Modified UTF-8 error). Use native rm -rf in a separate process instead.
            val deleted = runCatching { SafeFileDeleter.deleteRecursively(project) }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (deleted) {
                    Toast.makeText(requireContext(), R.string.project_deleted_success, Toast.LENGTH_SHORT).show()
                    onDeleted()
                } else {
                    Toast.makeText(requireContext(), R.string.project_delete_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showRenameDialog(project: File, onComplete: () -> Unit) {
        val ctx = requireContext()

        val inputLayout = TextInputLayout(ctx).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = getString(R.string.new_project_name)
            val pad = (ctx.resources.displayMetrics.density * 16).toInt()
            setPadding(pad, 0, pad, 0)
        }

        val input = TextInputEditText(ctx).apply {
            setText(project.name)
            setSelection(project.name.length)
        }

        inputLayout.addView(input)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.rename_project))
            .setView(inputLayout)
            .setPositiveButton(getString(R.string.rename), null)
            .setNegativeButton(getString(R.string.cancel)) { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = input.text?.toString()?.trim().orEmpty()
                when {
                    newName.isBlank() -> {
                        Toast.makeText(ctx, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                    newName == project.name -> {
                        dialog.dismiss()
                    }
                    !newName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$")) -> {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle(getString(R.string.invalid_name))
                            .setMessage(getString(R.string.invalid_project_name_message))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    else -> {
                        val newDir = File(project.parentFile, newName)
                        if (newDir.exists()) {
                            MaterialAlertDialogBuilder(ctx)
                                .setTitle(getString(R.string.name_already_exists))
                                .setMessage(getString(R.string.project_name_exists_message, newName))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        } else {
                            dialog.dismiss()
                            renameProject(project, newDir, onComplete)
                        }
                    }
                }
            }
        }

        dialog.show()

        // Show keyboard like ACS
        input.requestFocus()
        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        input.postDelayed({
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun renameProject(oldProject: File, newProject: File, onComplete: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            val renamed = runCatching { oldProject.renameTo(newProject) }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                if (renamed) {
                    // Update recents order/paths similar to ACS
                    val ctx = requireContext()
                    val recentProjects = WizardPreferences.getRecentProjects(ctx).toMutableList()
                    val oldIndex = recentProjects.indexOf(oldProject.absolutePath)
                    if (oldIndex >= 0) {
                        recentProjects.removeAt(oldIndex)
                        recentProjects.add(oldIndex, newProject.absolutePath)
                        ctx.getSharedPreferences("atc_wizard_prefs", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putString("recent_projects", recentProjects.joinToString(","))
                            .apply()
                    } else {
                        WizardPreferences.addRecentProject(ctx, newProject.absolutePath)
                    }

                    Toast.makeText(requireContext(), R.string.project_renamed_success, Toast.LENGTH_SHORT).show()
                    onComplete()
                } else {
                    Toast.makeText(requireContext(), R.string.project_rename_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun backupProject(project: File, onComplete: () -> Unit) {
        val ctx = requireContext()

        // Show an indeterminate progress dialog while backing up, similar to ACS.
        val progressBar = android.widget.ProgressBar(ctx).apply { isIndeterminate = true }
        val message = android.widget.TextView(ctx).apply {
            text = getString(R.string.backup_in_progress_message)
            setPadding(0, 16, 0, 0)
        }
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (ctx.resources.displayMetrics.density * 16).toInt()
            setPadding(pad, pad, pad, pad)
            addView(progressBar)
            addView(message)
        }

        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.backup_in_progress_title))
            .setView(container)
            .setCancelable(false)
            .create()
        progressDialog.show()

        scope.launch(Dispatchers.IO) {
            val backupDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects/backed_up_projects").apply { mkdirs() }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFileName = "${project.name}_backup_$timestamp.zip"
            val backupFile = File(backupDir, backupFileName)

            val result = runCatching {
                ZipOutputStream(backupFile.outputStream()).use { zipOut ->
                    project.walkTopDown().forEach { file ->
                        if (!file.isFile) return@forEach

                        val relativePath = file.relativeTo(project).path
                        if (
                            relativePath.startsWith("build/") ||
                            relativePath.contains("/build/") ||
                            relativePath.startsWith(".androidide/") ||
                            relativePath.startsWith(".gradle/") ||
                            relativePath.contains("/.gradle/") ||
                            relativePath.startsWith(".idea/") ||
                            relativePath.contains("/.idea/")
                        ) {
                            return@forEach
                        }

                        zipOut.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { input -> input.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                result.fold(
                    onSuccess = {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.backup_completed_title))
                            .setMessage(getString(R.string.backup_completed_message, project.name, backupFile.absolutePath))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        onComplete()
                    },
                    onFailure = { e ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.backup_failed_title))
                            .setMessage(getString(R.string.backup_failed_message, e.localizedMessage ?: e.toString()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel("OpenProjectBottomSheet destroyed")
    }
}
