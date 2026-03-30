package com.neonide.studio.app.home.open

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neonide.studio.R
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.app.utils.DisplayNameUtils
import java.io.File

class ProjectsListAdapter(
    private var projects: List<File>,
    private val onProjectClick: (File) -> Unit,
    private val onProjectLongClick: (File) -> Unit,
) : RecyclerView.Adapter<ProjectsListAdapter.ProjectViewHolder>() {

    private var filteredProjects: List<File> = projects

    inner class ProjectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val projectName: TextView = view.findViewById(R.id.projectName)
        val projectPath: TextView = view.findViewById(R.id.projectPath)
        val recentBadge: TextView = view.findViewById(R.id.recentBadge)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = filteredProjects[position]
        holder.projectName.text = DisplayNameUtils.safeForUi(project.name)
        holder.projectPath.text = DisplayNameUtils.safeForUi(project.absolutePath, 260)

        val recentRank = WizardPreferences.getRecentProjectRank(holder.root.context, project.absolutePath)
        val isRecent = recentRank in 0..2 // top 3
        holder.recentBadge.visibility = if (isRecent) View.VISIBLE else View.GONE

        holder.root.setOnClickListener { onProjectClick(project) }
        holder.root.setOnLongClickListener {
            onProjectLongClick(project)
            true
        }
    }

    override fun getItemCount(): Int = filteredProjects.size

    fun filter(query: String) {
        filteredProjects = if (query.isBlank()) {
            projects
        } else {
            projects.filter { p ->
                p.name.contains(query, ignoreCase = true) ||
                    p.absolutePath.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun updateProjects(newProjects: List<File>) {
        projects = newProjects
        filteredProjects = newProjects
        notifyDataSetChanged()
    }
}
