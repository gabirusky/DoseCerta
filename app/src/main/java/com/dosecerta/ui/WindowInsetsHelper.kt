package com.dosecerta.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** The activity root owns system/IME padding, including after resize. */
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
            // Descendants already sit inside this safe area. Material's
            // BottomNavigationView would otherwise add the navigation/IME
            // inset to its own padding a second time.
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(view)
    }
}
