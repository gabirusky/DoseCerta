package com.dosecerta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.dosecerta.util.Constants

/**
 * Receiver for medication alarm events.
 * 
 * CRITICAL: This receiver starts the AlarmService IMMEDIATELY and SYNCHRONOUSLY.
 * All database work is done in AlarmService (a foreground service that won't be killed).
 * 
 * This is essential for Xiaomi/MIUI/HyperOS devices with aggressive battery optimization
 * that kill the app process before coroutines can complete.
 */
class MedicationAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MedicationAlarmReceiver"
        private const val WAKELOCK_TAG = "DoseCerta::AlarmReceiverWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 10_000L // 10 seconds max
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        // Get medication details from intent extras
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val scheduleId = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L)
        val scheduledTime = intent.getLongExtra(Constants.EXTRA_SCHEDULED_TIME, 0L)
        
        Log.d(TAG, "Alarm received - MedID: $medicationId, ScheduleID: $scheduleId, Time: $scheduledTime")
        
        if (medicationId == -1L || scheduleId == -1L) {
            Log.e(TAG, "Invalid medication or schedule ID, aborting")
            return
        }
        
        // Use the actual scheduled time or current time if not provided
        val effectiveScheduledTime = if (scheduledTime > 0) scheduledTime else System.currentTimeMillis()
        
        // CRITICAL: Acquire wake lock IMMEDIATELY to prevent CPU sleep on Xiaomi devices
        // This keeps the CPU awake while we start the foreground service
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            acquire(WAKELOCK_TIMEOUT_MS)
        }
        
        try {
            // CRITICAL: Start AlarmService IMMEDIATELY with just the IDs
            // Do NOT do any database work here - it will be done in the foreground service
            // which is protected from being killed by the system
            AlarmService.startAlarmWithIds(
                context,
                medicationId,
                scheduleId,
                effectiveScheduledTime
            )
            Log.d(TAG, "AlarmService started immediately for MedID: $medicationId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AlarmService", e)
        } finally {
            // Release wake lock - service will acquire its own
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock", e)
            }
        }
    }
}

