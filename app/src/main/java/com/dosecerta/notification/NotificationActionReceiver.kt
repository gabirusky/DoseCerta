package com.dosecerta.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for notification action buttons (Take, Skip, Snooze).
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // Get medication details from intent
        val medicationId = intent.getLongExtra(com.dosecerta.util.Constants.EXTRA_MEDICATION_ID, -1L)
        val scheduleId = intent.getLongExtra(com.dosecerta.util.Constants.EXTRA_SCHEDULE_ID, -1L)
        val scheduledTime = intent.getLongExtra(com.dosecerta.util.Constants.EXTRA_SCHEDULED_TIME, 0L)
        
        if (medicationId == -1L || scheduleId == -1L) {
            return
        }
        
        // Use goAsync() to allow background work
        val pendingResult = goAsync()
        
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                // Get database instance
                val database = com.dosecerta.data.local.DoseCertaDatabase.getDatabase(context)
                
                when (intent.action) {
                    com.dosecerta.util.Constants.ACTION_TAKE_MEDICATION -> {
                        // Log medication as TAKEN
                        val existingLog = database.medicationLogDao().getLog(medicationId, scheduleId, scheduledTime)
                        
                        if (existingLog != null) {
                            database.medicationLogDao().update(
                                existingLog.copy(
                                    status = com.dosecerta.data.model.MedicationStatus.TAKEN,
                                    actualTime = System.currentTimeMillis()
                                )
                            )
                        } else {
                            database.medicationLogDao().insert(
                                com.dosecerta.data.local.entity.MedicationLog(
                                    medicationId = medicationId,
                                    scheduleId = scheduleId,
                                    scheduledTime = scheduledTime,
                                    actualTime = System.currentTimeMillis(),
                                    status = com.dosecerta.data.model.MedicationStatus.TAKEN
                                )
                            )
                        }
                        
                        // Dismiss notification
                        val notificationHelper = com.dosecerta.notification.NotificationHelper(context)
                        notificationHelper.cancelNotification(medicationId, scheduleId)
                    }
                    
                    com.dosecerta.util.Constants.ACTION_SKIP_MEDICATION -> {
                        // Log medication as SKIPPED
                        val existingLog = database.medicationLogDao().getLog(medicationId, scheduleId, scheduledTime)
                        
                        if (existingLog != null) {
                            database.medicationLogDao().update(
                                existingLog.copy(
                                    status = com.dosecerta.data.model.MedicationStatus.SKIPPED,
                                    actualTime = System.currentTimeMillis()
                                )
                            )
                        } else {
                            database.medicationLogDao().insert(
                                com.dosecerta.data.local.entity.MedicationLog(
                                    medicationId = medicationId,
                                    scheduleId = scheduleId,
                                    scheduledTime = scheduledTime,
                                    actualTime = System.currentTimeMillis(),
                                    status = com.dosecerta.data.model.MedicationStatus.SKIPPED
                                )
                            )
                        }
                        
                        // Dismiss notification
                        val notificationHelper = com.dosecerta.notification.NotificationHelper(context)
                        notificationHelper.cancelNotification(medicationId, scheduleId)
                    }
                    
                    com.dosecerta.util.Constants.ACTION_SNOOZE_MEDICATION -> {
                        // Reschedule alarm for 10 minutes later
                        val alarmScheduler = com.dosecerta.alarm.AlarmScheduler(context)
                        alarmScheduler.snoozeAlarm(medicationId, scheduleId, scheduledTime)
                        
                        // Dismiss current notification
                        val notificationHelper = com.dosecerta.notification.NotificationHelper(context)
                        notificationHelper.cancelNotification(medicationId, scheduleId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
