package com.neonide.studio.app.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.neonide.studio.R
import com.neonide.studio.app.TermuxActivity
import com.neonide.studio.app.DevKitSetup
import com.neonide.studio.databinding.FragmentHomeBinding
import com.neonide.studio.app.home.create.CreateProjectBottomSheet
import com.neonide.studio.app.home.open.OpenProjectBottomSheet
import com.neonide.studio.app.home.clone.CloneRepositoryDialogFragment

class HomeFragment : Fragment() {

    private var binding: FragmentHomeBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actions = buildActions()
        binding!!.homeActions.adapter = HomeActionsAdapter(actions)
    }

    private fun buildActions(): List<HomeAction> {
        val ctx = requireContext()

        val showComingSoon = {
            Toast.makeText(ctx, R.string.msg_feature_coming_soon, Toast.LENGTH_SHORT).show()
        }

        return listOf(
            HomeAction(
                id = 0,
                textRes = R.string.acs_create_project,
                iconRes = R.drawable.ic_add,
                summaryRes = R.string.acs_create_project_summary,
                onClick = { _, _ -> CreateProjectBottomSheet().show(parentFragmentManager, "create_project") },
            ),
            HomeAction(
                id = 1,
                textRes = R.string.acs_open_existing_project,
                iconRes = R.drawable.ic_folder,
                summaryRes = R.string.acs_open_existing_project_summary,
                onClick = { _, _ -> OpenProjectBottomSheet().show(parentFragmentManager, "open_project") },
            ),
            HomeAction(
                id = 2,
                textRes = R.string.acs_clone_git_repository,
                iconRes = R.drawable.ic_git,
                summaryRes = R.string.acs_clone_git_repository_summary,
                onClick = { _, _ ->
                    CloneRepositoryDialogFragment().show(parentFragmentManager, "clone_repo")
                },
            ),
            HomeAction(
                id = 3,
                textRes = R.string.acs_terminal,
                iconRes = R.drawable.ic_terminal,
                summaryRes = R.string.acs_terminal_summary,
                onClick = { _, _ -> startActivity(Intent(ctx, TermuxActivity::class.java)) },
            ),
            HomeAction(
                id = 31,
                textRes = R.string.acs_setup_development_kit,
                iconRes = R.drawable.ic_gradle,
                summaryRes = R.string.acs_setup_development_kit_summary,
                onClick = { _, _ -> DevKitSetup.startSetup(requireActivity()) },
            ),
            HomeAction(
                id = 5,
                textRes = R.string.acs_ide_configurations,
                iconRes = R.drawable.ic_settings,
                summaryRes = R.string.acs_ide_configurations_summary,
                onClick = { _, _ -> startActivity(Intent(ctx, com.neonide.studio.app.IdeConfigActivity::class.java)) },
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
