package com.neonide.studio.app.bottomsheet.fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.SimpleStringAdapter
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel

class DiagnosticsFragment : Fragment(R.layout.fragment_simple_list) {

    private val vm: BottomSheetViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = view.findViewById<RecyclerView>(R.id.list)
        val empty = view.findViewById<TextView>(R.id.empty)
        val adapter = SimpleStringAdapter()
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        vm.diagnostics.observe(viewLifecycleOwner) { items ->
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
