package com.dosecerta.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.util.Constants
import kotlinx.coroutines.runBlocking

/**
 * Receiver for showing reminder notification after a missed/skipped medication.
 * B5: Checks the current log status before deciding which notification to show.
 * B11: Passes medicationId/scheduleId/scheduledTime to NotificationHelper for action buttons.
 */
class MissedReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MissedReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called — showing missed medication reminder")

        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val scheduleId = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L)
        val scheduledTime = intent.getLongExtra(Constants.EXTRA_SCHEDULED_TIME, 0L)

        if (medicationId == -1L || scheduleId == -1L) {
            Log.e(TAG, "Invalid IDs")
            return
        }

        runBlocking {
            try {
                val database = DoseCertaDatabase.getDatabase(context)
                val medication = database.medicationDao().getMedicationByIdSync(medicationId)

                if (medication == null) {
                    Log.d(TAG, "Medication not found or inactive — skipping reminder")
                    return@runBlocking
                }

                // B5: Check current log status to decide which notification to show
                val logDao = database.medicationLogDao()
                val log = logDao.getLog(medicationId, scheduleId, scheduledTime)
                val notificationHelper = NotificationHelper(context)

                when (log?.status) {
                    MedicationStatus.TAKEN -> {
                        // User already took it — no notification needed
                        Log.d(TAG, "Log is TAKEN — suppressing reminder for ${medication.name}")
                    }

                    MedicationStatus.SKIPPED -> {
                        // B5/B6: Softer "reconsider" notification for intentional skips
                        notificationHelper.showSkippedReminderNotification(
                            medicationName = medication.name,
                            medicationId = medicationId,
                            scheduleId = scheduleId,
                            scheduledTime = scheduledTime
                        )
                        Log.d(TAG, "Showed skipped reminder for: ${medication.name}")
                    }

                    else -> {
                        // MISSED or null — show standard missed notification
                        // B11: Pass IDs so action buttons work
                        notificationHelper.showMissedReminderNotification(
                            medicationName = medication.name,
                            medicationId = medicationId,
                            scheduleId = scheduleId,
                            scheduledTime = scheduledTime
                        )
                        Log.d(TAG, "Showed missed reminder for: ${medication.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing reminder", e)
                e.printStackTrace()
            }
        }
    }
}
