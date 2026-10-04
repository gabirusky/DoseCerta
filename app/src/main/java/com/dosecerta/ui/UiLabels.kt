package com.dosecerta.ui

import android.content.Context
import com.dosecerta.R
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm

/** Shared user-facing labels; enum storage identifiers never appear in the interface. */
object UiLabels {
    fun frequency(context: Context, value: Frequency): String = context.getString(when (value) {
        Frequency.DAILY -> R.string.frequency_daily
        Frequency.EVERY_4_HOURS -> R.string.frequency_every_4_hours
        Frequency.EVERY_6_HOURS -> R.string.frequency_every_6_hours
        Frequency.EVERY_8_HOURS -> R.string.frequency_every_8_hours
        Frequency.EVERY_12_HOURS -> R.string.frequency_every_12_hours
        Frequency.WEEKLY -> R.string.ui_weekly
        Frequency.MONTHLY -> R.string.ui_monthly
        Frequency.SELECTED_DAYS -> R.string.ui_specific_days
        Frequency.AS_NEEDED -> R.string.frequency_as_needed
    })
    fun form(context: Context, value: PharmaceuticalForm): String = context.getString(when (value) {
        PharmaceuticalForm.TABLET -> R.string.form_tablet
        PharmaceuticalForm.CAPSULE -> R.string.form_capsule
        PharmaceuticalForm.SYRUP -> R.string.form_syrup
        PharmaceuticalForm.DROPS -> R.string.form_drops
        PharmaceuticalForm.INJECTION -> R.string.form_injection
        PharmaceuticalForm.CREAM -> R.string.form_cream
        PharmaceuticalForm.SPRAY -> R.string.form_spray
        PharmaceuticalForm.OTHER -> R.string.form_other
    })
}
