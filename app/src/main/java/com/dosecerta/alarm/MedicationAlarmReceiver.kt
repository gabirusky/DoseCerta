package com.dosecerta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.notification.NotificationHelper

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmIdentity.EXTRA_OCCURRENCE_ID) ?: return
        AlarmDiagnostics.record(context, "receiver", id)
        runAlarmWork(context, id) {
            val repository = MedicationRepository(DoseCertaDatabase.getDatabase(context))
            val occurrence = repository.getOccurrence(id) ?: return@runAlarmWork
            val scheduler = AlarmScheduler(context)
            val result = DoseActionCoordinator(repository).deliver(id)
            // The next recurrence is a separate occurrence and survives snooze/confirmation of this one.
            occurrence.scheduleId?.let { scheduleId -> repository.getScheduleById(scheduleId)?.let { scheduler.scheduleAlarm(it.medicationId, it) } }
            if (result !is DoseActionResult.Success || !result.changed) {
                AlarmDiagnostics.record(context, "delivery", id, "stale_or_duplicate")
                return@runAlarmWork
            }
            scheduler.scheduleTimeout(result.occurrence)
            val capability = ReminderCapabilityChecker(context).check()
            if (!capability.canNotifyAlarm) {
                AlarmDiagnostics.record(context, "delivery", id, "notification_blocked")
                return@runAlarmWork
            }
            val exact = intent.getBooleanExtra(AlarmIdentity.EXTRA_EXACT, false)
            if (exact && capability.exactAlarms && AlarmService.startOccurrence(context, id)) return@runAlarmWork
            // An inexact alarm is not a background foreground-service launch exemption.
            NotificationHelper(context).showAlarm(result.occurrence, audibleService = false, allowFullScreen = false)
            AlarmDiagnostics.record(context, "delivery", id, "notification_only")
        }
    }
}
