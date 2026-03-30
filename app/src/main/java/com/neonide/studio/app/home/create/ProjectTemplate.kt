package com.neonide.studio.app.home.create

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class ProjectTemplate(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
    val kind: Kind,
) {
    enum class Kind {
        NO_ACTIVITY,
        EMPTY_ACTIVITY,
        CPP_ACTIVITY,
        BASIC_ACTIVITY,
        NAV_DRAWER_ACTIVITY,
        BOTTOM_NAV_ACTIVITY,
        TABBED_ACTIVITY,
        NO_ANDROIDX_ACTIVITY,
        COMPOSE_ACTIVITY,
    }
}

