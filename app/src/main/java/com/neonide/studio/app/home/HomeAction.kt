package com.neonide.studio.app.home

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Action shown on the Home screen.
 */
data class HomeAction(
    val id: Int,
    @param:StringRes val textRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val summaryRes: Int? = null,
    var onClick: ((HomeAction, View) -> Unit)? = null,
    var onLongClick: ((HomeAction, View) -> Boolean)? = null,
)
