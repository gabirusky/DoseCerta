package com.dosecerta.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils

object MedicationIcon {
    fun color(context: Context, selected: Int): Int =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            ColorUtils.blendARGB(selected, Color.WHITE, 0.4f)
        else selected
}
