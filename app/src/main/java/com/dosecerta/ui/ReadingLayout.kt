package com.dosecerta.ui

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import com.dosecerta.R

/** Give text its own row when horizontal density would compete with large type. */
fun LinearLayout.stackForReadingSize() {
    val config = resources.configuration
    if (config.fontScale <= 1.3f && config.screenWidthDp >= 350) return
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.START
    val gap = resources.getDimensionPixelSize(R.dimen.ui_card_gap)
    var previousBottomMargin = 0
    children.forEachIndexed { index, child ->
        child.layoutParams = (child.layoutParams as LinearLayout.LayoutParams).apply {
            if (weight > 0f) width = ViewGroup.LayoutParams.MATCH_PARENT
            weight = 0f
            marginStart = 0
            marginEnd = 0
            if (index > 0) topMargin = maxOf(topMargin, gap - previousBottomMargin)
            previousBottomMargin = bottomMargin
        }
    }
}
