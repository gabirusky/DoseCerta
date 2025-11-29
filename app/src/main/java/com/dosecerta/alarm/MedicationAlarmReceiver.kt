package com.dosecerta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for medication alarm events.
 */
class MedicationAlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // Get medication details from intent extras
        val medicationId = intent.getLongExtra(com.dosecerta.util.Constants.EXTRA_MEDICATION_ID, -1L)
        val scheduleId = intent.getLongExtra(com.dosecerta.util.Constants.EXTRA_SCHEDULE_ID, -1L)
        val scheduledTime = intent.getLongExtra(com.dosecerta.util.Constants.EXTRA_SCHEDULED_TIME, 0L)
        
        if (medicationId == -1L || scheduleId == -1L) {
            // Invalid data, abort
            return
        }
        
        // Use coroutine to query database and show notification
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                // Get database instance
                val database = com.dosecerta.data.local.DoseCertaDatabase.getDatabase(context)
                
                // Query medication from database
                val medication = database.medicationDao().getMedicationByIdSync(medicationId)
                
                if (medication != null && medication.isActive) {
                    // Create notification helper and show notification
                    val notificationHelper = com.dosecerta.notification.NotificationHelper(context)
                    notificationHelper.showMedicationReminder(medication, scheduleId, scheduledTime)
                    
                    // Schedule alarm to mark as missed if no action taken within 30 minutes
                    val alarmScheduler = AlarmScheduler(context)
                    alarmScheduler.scheduleMissedCheckAlarm(medicationId, scheduleId, scheduledTime)
                    
                    // Reschedule alarm for next occurrence
                    val schedule = database.scheduleDao().getScheduleById(scheduleId)
                    if (schedule != null && schedule.isActive) {
                        alarmScheduler.scheduleAlarm(medicationId, schedule)
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            }
        }
    }
}
