package com.neonide.studio.app.bottomsheet.fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel

class AppLogsFragment : Fragment(R.layout.fragment_output_text) {

    private val vm: BottomSheetViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tv = view.findViewById<TextView>(R.id.output_text)
        vm.appLogs.observe(viewLifecycleOwner) { tv.text = it }
    }
}
