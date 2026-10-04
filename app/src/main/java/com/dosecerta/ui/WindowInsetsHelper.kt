package com.dosecerta.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Apply system/IME padding once from the original XML padding, also on resize. */
object WindowInsetsHelper {
    fun apply(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            target.setPadding(left + safe.left, top + safe.top, right + safe.right,
                bottom + maxOf(safe.bottom, keyboard.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
