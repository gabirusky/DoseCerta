package com.dosecerta.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosecerta.alarm.AlarmIdentity
import com.dosecerta.alarm.runAlarmWork
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseState

class MissedReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmIdentity.EXTRA_OCCURRENCE_ID) ?: return
        runAlarmWork(context, id) {
            val repository = MedicationRepository(DoseCertaDatabase.getDatabase(context))
            val occurrence = repository.getOccurrence(id) ?: return@runAlarmWork
            if (occurrence.state != DoseState.MISSED || occurrence.reminderAt == null || !repository.isOccurrenceCurrent(occurrence)) return@runAlarmWork
            if (occurrence.reminderAt > System.currentTimeMillis()) return@runAlarmWork
            if (NotificationHelper(context).showMissedReminder(occurrence)) repository.clearReminder(id)
        }
    }
}
