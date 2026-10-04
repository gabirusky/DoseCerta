package com.dosecerta.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosecerta.alarm.AlarmIdentity
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.alarm.runAlarmWork
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult

class MarkMissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmIdentity.EXTRA_OCCURRENCE_ID) ?: return
        runAlarmWork(context, id) {
            val repository = MedicationRepository(DoseCertaDatabase.getDatabase(context))
            val result = DoseActionCoordinator(repository).timeout(id, com.dosecerta.util.SettingsPreferences(context).getMissedReminderHoursSync() * 3_600_000L)
            if (result is DoseActionResult.Success) {
                val scheduler = AlarmScheduler(context)
                scheduler.cancelOccurrence(id)
                if (repository.isOccurrenceCurrent(result.occurrence)) scheduler.scheduleFollowUp(result.occurrence)
            }
        }
    }
}
