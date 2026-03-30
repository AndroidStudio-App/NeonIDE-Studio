package com.neonide.studio.app.home.create

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.neonide.studio.databinding.ItemProjectTemplateBinding

class TemplateGridAdapter(
    private val templates: List<ProjectTemplate>,
    private val onClick: (ProjectTemplate) -> Unit,
) : RecyclerView.Adapter<TemplateGridAdapter.VH>() {

    class VH(val binding: ItemProjectTemplateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemProjectTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = templates.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = templates[position]
        holder.binding.templateIcon.setImageResource(t.iconRes)
        holder.binding.templateName.setText(t.nameRes)
        holder.binding.root.setOnClickListener { onClick(t) }
    }
}
