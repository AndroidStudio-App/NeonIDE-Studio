package com.neonide.studio.app.bottomsheet.fragments

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.SimpleStringAdapter
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment(R.layout.fragment_search) {

    private val vm: BottomSheetViewModel by activityViewModels()
    private val scope = MainScope()

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val query = view.findViewById<EditText>(R.id.query)
        val btn = view.findViewById<Button>(R.id.btn_search)
        val results = view.findViewById<RecyclerView>(R.id.results)

        val adapter = SimpleStringAdapter()
        results.layoutManager = LinearLayoutManager(requireContext())
        results.adapter = adapter

        vm.searchResults.observe(viewLifecycleOwner) { adapter.submit(it) }

        btn.setOnClickListener {
            val q = query.text?.toString()?.trim().orEmpty()
            if (q.isEmpty()) return@setOnClickListener

            val root = (activity as? com.neonide.studio.app.SoraEditorActivityK)?.getProjectRootDir()
            if (root == null) {
                vm.setSearchResults(listOf("No project open"))
                return@setOnClickListener
            }

            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    searchInDir(root, q)
                }
                vm.setSearchResults(found)
            }
        }
    }

    private fun searchInDir(root: File, query: String): List<String> {
        val results = ArrayList<String>()
        val maxFiles = 2000
        val maxMatches = 500
        var filesSeen = 0

        root.walkTopDown().forEach { f ->
            if (filesSeen >= maxFiles || results.size >= maxMatches) return@forEach
            if (!f.isFile) return@forEach

            val name = f.name
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".apk")) {
                return@forEach
            }

            filesSeen++
            runCatching {
                val lines = f.readLines()
                for (i in lines.indices) {
                    if (results.size >= maxMatches) break
                    if (lines[i].contains(query, ignoreCase = true)) {
                        results.add("${f.relativeTo(root)}:${i + 1}: ${lines[i].trim()}")
                    }
                }
            }
        }

        if (results.isEmpty()) {
            results.add("No results")
        }
        return results
    }
}
