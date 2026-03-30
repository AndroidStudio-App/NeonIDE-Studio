package com.neonide.studio.app.bottomsheet

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neonide.studio.app.bottomsheet.model.NavigationItem

class NavigationResultsAdapter(
    private var items: List<NavigationItem> = emptyList(),
    private val onItemClick: (NavigationItem) -> Unit
) : RecyclerView.Adapter<NavigationResultsAdapter.VH>() {

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        tv.textSize = 12f
        return VH(tv)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tv.text = item.displayText
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    fun submit(newItems: List<NavigationItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
