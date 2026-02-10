package com.dosecerta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver to reschedule alarms after device boot.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Use goAsync() to allow background work to complete
            val pendingResult = goAsync()
            
            // Reschedule all active medication alarms
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    // Get database instance
                    val database = com.dosecerta.data.local.DoseCertaDatabase.getDatabase(context)
                    
                    // Get all active schedules
                    val schedules = database.scheduleDao().getAllActiveSchedulesSync()
                    
                    // Reschedule each alarm
                    val alarmScheduler = AlarmScheduler(context)
                    schedules.forEach { schedule ->
                        alarmScheduler.scheduleAlarm(schedule.medicationId, schedule)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    // Notify that the async work is complete
                    pendingResult.finish()
                }
            }
        }
    }
}
