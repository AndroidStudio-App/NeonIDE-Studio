package com.neonide.studio.app.bottomsheet

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.fragments.AppLogsFragment
import com.neonide.studio.app.bottomsheet.fragments.BuildOutputFragment
import com.neonide.studio.app.bottomsheet.fragments.DiagnosticsFragment
import com.neonide.studio.app.bottomsheet.fragments.IDELogsFragment
import com.neonide.studio.app.bottomsheet.fragments.NavigationResultsFragment
import com.neonide.studio.app.bottomsheet.fragments.SearchFragment

class EditorBottomSheetTabAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    data class Tab(val titleRes: Int, val factory: () -> Fragment)

    private val tabs = listOf(
        Tab(R.string.acs_tab_build_output) { BuildOutputFragment() },
        Tab(R.string.acs_tab_app_logs) { AppLogsFragment() },
        Tab(R.string.acs_tab_ide_logs) { IDELogsFragment() },
        Tab(R.string.acs_tab_diagnostics) { DiagnosticsFragment() },
        Tab(R.string.acs_tab_search) { SearchFragment() },
        Tab(R.string.acs_tab_references) { NavigationResultsFragment() },
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = tabs[position].factory.invoke()

    fun getTitleRes(position: Int): Int = tabs[position].titleRes
}
