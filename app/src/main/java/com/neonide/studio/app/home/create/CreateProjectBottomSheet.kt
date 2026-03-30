package com.neonide.studio.app.home.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.neonide.studio.R
import com.neonide.studio.app.SoraEditorActivityK
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.shared.termux.TermuxConstants
import com.neonide.studio.databinding.DialogCreateProjectBinding
import java.io.File

class CreateProjectBottomSheet : BottomSheetDialogFragment() {

    private var binding: DialogCreateProjectBinding? = null
    private var selectedTemplate: ProjectTemplate? = null
    private var isPackageNameManuallyEdited = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = DialogCreateProjectBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val templates = ProjectTemplateRegistry.all()
        selectedTemplate = null

        // Page visibility
        binding!!.pageTemplates.visibility = View.VISIBLE
        binding!!.pageOptions.visibility = View.GONE

        // Grid (ACS uses 3 columns)
        binding!!.templatesGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
        binding!!.templatesGrid.adapter = TemplateGridAdapter(templates) { tpl ->
            selectedTemplate = tpl

            // Show options page
            binding!!.pageTemplates.visibility = View.GONE
            binding!!.pageOptions.visibility = View.VISIBLE

            // Selected template preview (new UI)
            binding!!.selectedTemplateIcon.setImageResource(tpl.iconRes)
            binding!!.selectedTemplateName.setText(tpl.nameRes)
            binding!!.selectedTemplateDesc.setText(tpl.descriptionRes)

            // Defaults
            val suggestedName = "My" + getString(tpl.nameRes).replace(" ", "")
            binding!!.projectName.setText(suggestedName)

            // Reset manual edit flag when selecting a new template
            isPackageNameManuallyEdited = false
            updatePackageName(suggestedName, tpl)
        }

        // Auto-update package name when project name changes
        binding!!.projectName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isPackageNameManuallyEdited) {
                    selectedTemplate?.let { updatePackageName(s?.toString().orEmpty(), it) }
                }
            }
        })

        // Track manual edits to package name
        binding!!.packageName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                isPackageNameManuallyEdited = true
            }
        }
        
        binding!!.packageName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding!!.packageName.hasFocus()) {
                    isPackageNameManuallyEdited = true
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Defaults (save location)
        val defaultDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects")
        val lastDir = WizardPreferences.getLastSaveLocation(requireContext())
        binding!!.saveLocation.setText(lastDir ?: defaultDir.absolutePath)

        // Browse end icon (folder)
        binding!!.saveLocationLayout.setEndIconOnClickListener {
            // Folder picker is not implemented yet for this wizard.
            Toast.makeText(requireContext(), R.string.msg_feature_coming_soon, Toast.LENGTH_SHORT).show()
        }

        // Dropdowns
        val minSdkItems = arrayOf("21", "24", "26", "28", "29", "30", "33")
        binding!!.minSdk.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, minSdkItems))
        binding!!.minSdk.setText("21", false)

        val langItems = arrayOf("Kotlin", "Java")
        binding!!.language.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, langItems))
        binding!!.language.setText("Kotlin", false)

        // Buttons
        binding!!.btnCancel.setOnClickListener { dismiss() }
        binding!!.backButton.setOnClickListener {
            binding!!.pageOptions.visibility = View.GONE
            binding!!.pageTemplates.visibility = View.VISIBLE
        }
        binding!!.btnCreate.setOnClickListener { createProject() }
    }

    private fun createProject() {
        val ctx = requireContext()
        val tpl = selectedTemplate ?: run {
            Toast.makeText(ctx, R.string.choose_template, Toast.LENGTH_SHORT).show()
            return
        }

        val name = binding!!.projectName.text?.toString()?.trim().orEmpty()
        val pkg = binding!!.packageName.text?.toString()?.trim().orEmpty()
        val baseDir = binding!!.saveLocation.text?.toString()?.trim().orEmpty()
        val minSdk = binding!!.minSdk.text?.toString()?.trim().orEmpty().ifEmpty { "21" }
        val lang = binding!!.language.text?.toString()?.trim().orEmpty().ifEmpty { "Kotlin" }
        val useKts = binding!!.useKtsSwitch.isChecked

        if (!ProjectValidators.isValidProjectName(name)) {
            Toast.makeText(ctx, R.string.create_project_error_invalid_name, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ProjectValidators.isValidPackageName(pkg)) {
            Toast.makeText(ctx, R.string.create_project_error_invalid_package, Toast.LENGTH_SHORT).show()
            return
        }

        val base = File(baseDir)
        if (!base.exists()) {
            base.mkdirs()
        }

        val projectDir = File(base, name)
        if (projectDir.exists()) {
            Toast.makeText(ctx, R.string.create_project_error_dir_exists, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            AndroidProjectGenerator.generate(
                context = ctx,
                template = tpl,
                projectDir = projectDir,
                applicationId = pkg,
                minSdk = minSdk.toIntOrNull() ?: 21,
                language = lang,
                useKts = useKts,
            )
        } catch (t: Throwable) {
            Toast.makeText(ctx, t.message ?: "Failed", Toast.LENGTH_LONG).show()
            return
        }

        // Persist wizard preferences like ACS
        WizardPreferences.setLastSaveLocation(ctx, base.absolutePath)
        WizardPreferences.addRecentProject(ctx, projectDir.absolutePath)

        Toast.makeText(ctx, getString(R.string.create_project_success, projectDir.absolutePath), Toast.LENGTH_LONG).show()

        // Open in editor with project root
        runCatching {
            val intent = android.content.Intent(ctx, SoraEditorActivityK::class.java)
            intent.putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, projectDir.absolutePath)
            startActivity(intent)
        }

        dismiss()
    }

    private fun updatePackageName(projectName: String, template: ProjectTemplate) {
        val templateRaw = getString(template.nameRes).lowercase()
            .replace("project", "")
            .replace("activity", "")
            .trim()
            
        val templateSanitized = templateRaw.replace(Regex("[^a-z0-9]"), "")

        val projectSanitized = projectName.lowercase()
            .replace(Regex("[^a-z0-9]"), "")

        val prefix = if (templateSanitized.isNotEmpty()) "com.$templateSanitized" else "com.example"
        
        if (projectSanitized.isNotEmpty()) {
            binding!!.packageName.setText("$prefix.$projectSanitized")
        } else {
            binding!!.packageName.setText(prefix)
        }
    }
}
