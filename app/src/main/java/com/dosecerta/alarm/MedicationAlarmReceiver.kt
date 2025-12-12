package com.dosecerta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.notification.NotificationHelper
import com.dosecerta.util.Constants
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for medication alarm events.
 * When alarm fires:
 * 1. Start AlarmService with full-screen notification and looping sound
 * 2. Immediately create a MISSED log (will be updated if user takes action)
 * 3. Schedule missed reminder notification based on user settings
 * 4. Reschedule alarm for next occurrence
 */
class MedicationAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MedicationAlarmReceiver"
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
        
        // Use coroutine to query database and start alarm service
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val database = DoseCertaDatabase.getDatabase(context)
                val medication = database.medicationDao().getMedicationByIdSync(medicationId)
                
                if (medication != null && medication.isActive) {
                    // 1. Load alarm sound preference
                    val settingsPreferences = SettingsPreferences(context)
                    val soundUriString = settingsPreferences.getAlarmSoundUriSync()
                    val soundUri = soundUriString?.let { android.net.Uri.parse(it) }
                    
                    // 2. Start AlarmService with full-screen notification
                    AlarmService.startAlarm(
                        context,
                        medication,
                        scheduleId,
                        effectiveScheduledTime,
                        soundUri
                    )
                    Log.d(TAG, "AlarmService started for ${medication.name}")

                    
                    // 3. Immediately create a MISSED log (user actions will update this)
                    val logDao = database.medicationLogDao()
                    val existingLog = logDao.getLog(medicationId, scheduleId, effectiveScheduledTime)
                    
                    if (existingLog == null) {
                        val logId = logDao.insert(
                            MedicationLog(
                                medicationId = medicationId,
                                scheduleId = scheduleId,
                                scheduledTime = effectiveScheduledTime,
                                actualTime = null,
                                status = MedicationStatus.MISSED
                            )
                        )
                        Log.d(TAG, "Created MISSED log with ID: $logId")
                    } else {
                        Log.d(TAG, "Log already exists with status: ${existingLog.status}")
                    }
                    
                    // 4. Schedule missed reminder notification based on user settings
                    val reminderHours = settingsPreferences.getMissedReminderHoursSync()
                    
                    val alarmScheduler = AlarmScheduler(context)
                    alarmScheduler.scheduleMissedReminderAlarm(medicationId, scheduleId, effectiveScheduledTime, reminderHours)
                    Log.d(TAG, "Scheduled missed reminder for $reminderHours hours from now")
                    
                    // 5. Reschedule alarm for next occurrence
                    val schedule = database.scheduleDao().getScheduleById(scheduleId)
                    if (schedule != null && schedule.isActive) {
                        alarmScheduler.scheduleAlarm(medicationId, schedule)
                        Log.d(TAG, "Rescheduled alarm for next occurrence")
                    }
                } else {
                    Log.d(TAG, "Medication not found or inactive")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm", e)
                e.printStackTrace()
            }
        }
    }
}

