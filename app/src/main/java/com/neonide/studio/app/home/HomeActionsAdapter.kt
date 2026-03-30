package com.neonide.studio.app.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.neonide.studio.databinding.ItemHomeActionBinding

class HomeActionsAdapter(private val actions: List<HomeAction>) :
    RecyclerView.Adapter<HomeActionsAdapter.VH>() {

    class VH(val binding: ItemHomeActionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemHomeActionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = actions.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val action = actions[position]

        // Bind visual content
        holder.binding.actionIcon.setImageResource(action.iconRes)
        holder.binding.actionTitle.setText(action.textRes)

        if (action.summaryRes != null) {
            holder.binding.actionSummary.visibility = android.view.View.VISIBLE
            holder.binding.actionSummary.setText(action.summaryRes)
        } else {
            holder.binding.actionSummary.visibility = android.view.View.GONE
        }

        // Bind interaction (actionButton is the clickable root card)
        holder.binding.actionButton.apply {
            contentDescription = context.getString(action.textRes)
            setOnClickListener { action.onClick?.invoke(action, it) }
            action.onLongClick?.let { l -> setOnLongClickListener { l(action, it) } }
        }
    }
}
