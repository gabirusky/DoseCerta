package com.dosecerta.alarm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.Fragment

/** OEM settings activities can be absent; fallback keeps the user in control. */
object ReminderSettingsNavigator {
    fun open(fragment: Fragment, intent: Intent): Boolean {
        return try { fragment.startActivity(intent); true }
        catch (_: ActivityNotFoundException) { fallback(fragment) }
        catch (_: SecurityException) { fallback(fragment) }
    }
    private fun fallback(fragment: Fragment): Boolean = try {
        fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${fragment.requireContext().packageName}")))
        true
    } catch (_: RuntimeException) { false }
}
