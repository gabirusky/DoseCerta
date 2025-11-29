package com.dosecerta.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.util.Constants
import kotlinx.coroutines.runBlocking

/**
 * Receiver for marking medications as MISSED when not acted upon.
 */
class MarkMissedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MarkMissedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val scheduleId = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L)
        val scheduledTime = intent.getLongExtra(Constants.EXTRA_SCHEDULED_TIME, 0L)
        
        Log.d(TAG, "Medication ID: $medicationId, Schedule ID: $scheduleId, Time: $scheduledTime")
        
        if (medicationId == -1L || scheduleId == -1L) {
            Log.e(TAG, "Invalid medication or schedule ID")
            return
        }
        
        runBlocking {
            try {
                val database = DoseCertaDatabase.getDatabase(context)
                val logDao = database.medicationLogDao()
                
                // Check if a log already exists (user may have acted before timeout)
                val existingLog = logDao.getLog(medicationId, scheduleId, scheduledTime)
                
                if (existingLog == null) {
                    // No action was taken - mark as MISSED
                    val insertedId = logDao.insert(
                        MedicationLog(
                            medicationId = medicationId,
                            scheduleId = scheduleId,
                            scheduledTime = scheduledTime,
                            actualTime = null,
                            status = MedicationStatus.MISSED
                        )
                    )
                    Log.d(TAG, "Marked as MISSED with log ID: $insertedId")
                    
                    // Schedule reminder notification 2 hours later
                    val alarmScheduler = com.dosecerta.alarm.AlarmScheduler(context)
                    alarmScheduler.scheduleMissedReminderAlarm(medicationId, scheduleId, scheduledTime)
                    Log.d(TAG, "Scheduled missed reminder for 2 hours from now")
                } else {
                    Log.d(TAG, "Log already exists with status: ${existingLog.status}, skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking as missed", e)
                e.printStackTrace()
            }
        }
    }
}
