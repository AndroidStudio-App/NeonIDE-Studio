package com.neonide.studio.app.home.create

import com.neonide.studio.R

object ProjectTemplateRegistry {

    fun all(): List<ProjectTemplate> = listOf(
        ProjectTemplate(
            id = "no_activity",
            nameRes = R.string.tpl_no_activity_name,
            descriptionRes = R.string.tpl_no_activity_desc,
            iconRes = R.drawable.no_activity,
            kind = ProjectTemplate.Kind.NO_ACTIVITY,
        ),
        ProjectTemplate(
            id = "empty_activity",
            nameRes = R.string.tpl_empty_activity_name,
            descriptionRes = R.string.tpl_empty_activity_desc,
            iconRes = R.drawable.empty_activity,
            kind = ProjectTemplate.Kind.EMPTY_ACTIVITY,
        ),
        ProjectTemplate(
            id = "cpp_activity",
            nameRes = R.string.tpl_cpp_activity_name,
            descriptionRes = R.string.tpl_cpp_activity_desc,
            iconRes = R.drawable.cpp_activity,
            kind = ProjectTemplate.Kind.CPP_ACTIVITY,
        ),
        ProjectTemplate(
            id = "basic_activity",
            nameRes = R.string.tpl_basic_activity_name,
            descriptionRes = R.string.tpl_basic_activity_desc,
            iconRes = R.drawable.basic_activity,
            kind = ProjectTemplate.Kind.BASIC_ACTIVITY,
        ),
        ProjectTemplate(
            id = "nav_drawer_activity",
            nameRes = R.string.tpl_nav_drawer_activity_name,
            descriptionRes = R.string.tpl_nav_drawer_activity_desc,
            iconRes = R.drawable.blank_activity_drawer,
            kind = ProjectTemplate.Kind.NAV_DRAWER_ACTIVITY,
        ),
        ProjectTemplate(
            id = "bottom_nav_activity",
            nameRes = R.string.tpl_bottom_nav_activity_name,
            descriptionRes = R.string.tpl_bottom_nav_activity_desc,
            iconRes = R.drawable.bottom_navigation_activity,
            kind = ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY,
        ),
        ProjectTemplate(
            id = "tabbed_activity",
            nameRes = R.string.tpl_tabbed_activity_name,
            descriptionRes = R.string.tpl_tabbed_activity_desc,
            iconRes = R.drawable.blank_activity_tabs,
            kind = ProjectTemplate.Kind.TABBED_ACTIVITY,
        ),
        ProjectTemplate(
            id = "no_androidx_activity",
            nameRes = R.string.tpl_no_androidx_activity_name,
            descriptionRes = R.string.tpl_no_androidx_activity_desc,
            iconRes = R.drawable.empty_noandroidx,
            kind = ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY,
        ),
        ProjectTemplate(
            id = "compose_activity",
            nameRes = R.string.tpl_compose_activity_name,
            descriptionRes = R.string.tpl_compose_activity_desc,
            iconRes = R.drawable.compose_empty_activity,
            kind = ProjectTemplate.Kind.COMPOSE_ACTIVITY,
        ),
    )
}

