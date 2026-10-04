package com.dosecerta.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri

/** PendingIntent compares data/action/component, not extras. Full opaque IDs remain in the URI. */
object AlarmIdentity {
    const val EXTRA_OCCURRENCE_ID = "occurrence_id"
    const val EXTRA_EXACT = "scheduled_exact"
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    const val RECURRENCE = "recurrence"
    const val SNOOZE = "snooze"
    const val TIMEOUT = "timeout"
    const val FOLLOW_UP = "follow_up"
    const val SILENCE = "com.dosecerta.ACTION_SILENCE"

    fun uri(id: String, kind: String): Uri = Uri.Builder().scheme("dosecerta")
        .authority("occurrence").appendPath(id).appendPath(kind).build()

    fun intent(context: Context, target: Class<*>, id: String, kind: String): Intent =
        Intent(context, target).setData(uri(id, kind)).putExtra(EXTRA_OCCURRENCE_ID, id)
}
