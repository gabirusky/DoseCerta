package com.dosecerta.notification

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosecerta.alarm.AlarmDiagnostics
import com.dosecerta.alarm.AlarmIdentity
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.alarm.runAlarmWork
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.util.Constants
import com.dosecerta.util.SettingsPreferences

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SHOW_SNACKBAR = "com.dosecerta.SHOW_SNACKBAR"
        const val EXTRA_SNACKBAR_MESSAGE = "snackbar_message"
    }
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmIdentity.EXTRA_OCCURRENCE_ID) ?: return
        runAlarmWork(context, id) {
            val repository = MedicationRepository(DoseCertaDatabase.getDatabase(context))
            val coordinator = DoseActionCoordinator(repository)
            val scheduler = AlarmScheduler(context)
            if (intent.action == Constants.ACTION_DISMISS_REMINDER) {
                repository.clearReminder(id)
                NotificationHelper(context).cancel(id)
                return@runAlarmWork
            }
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val requiresIdentity = intent.action in setOf(Constants.ACTION_TAKE_MEDICATION, Constants.ACTION_SKIP_MEDICATION)
            if (requiresIdentity && locked && !SettingsPreferences(context).getShowMedicationOnLockScreenSync()) {
                AlarmDiagnostics.record(context, "action", id, "authentication_required")
                return@runAlarmWork
            }
            val result = when (intent.action) {
                Constants.ACTION_TAKE_MEDICATION -> coordinator.take(id)
                Constants.ACTION_SKIP_MEDICATION -> coordinator.skip(id)
                Constants.ACTION_SNOOZE_MEDICATION -> scheduler.snoozeOccurrence(id, intent.getIntExtra(AlarmIdentity.EXTRA_SNOOZE_MINUTES, 10))
                AlarmIdentity.SILENCE -> coordinator.dismiss(id)
                else -> return@runAlarmWork
            }
            when (result) {
                is DoseActionResult.Success -> {
                    if (intent.action == Constants.ACTION_TAKE_MEDICATION || intent.action == Constants.ACTION_SKIP_MEDICATION) scheduler.cancelOccurrence(id)
                    else { com.dosecerta.alarm.AlarmService.stopAlarm(context, id); NotificationHelper(context).cancel(id) }
                    AlarmDiagnostics.record(context, "action", id, "committed")
                }
                is DoseActionResult.Rejected -> AlarmDiagnostics.record(context, "action", id, "stale")
                is DoseActionResult.Failure -> AlarmDiagnostics.record(context, "action", id, "persistence_failed")
            }
        }
    }
}
