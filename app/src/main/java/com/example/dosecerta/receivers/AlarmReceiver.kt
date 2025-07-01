package com.example.dosecerta.receivers

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dosecerta.MainActivity // To open app on notification click
import com.example.dosecerta.DoseCertaApplication
import com.example.dosecerta.R
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.data.model.Reminder
import com.example.dosecerta.util.NotificationHelper // We'll create this helper
import com.example.dosecerta.util.ReminderScheduler // To reschedule repeating alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class AlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received with action: ${intent.action}")
        
        when (intent.action) {
            "com.example.dosecerta.ACTION_TRIGGER_REMINDER" -> {
                val reminderId = intent.getIntExtra("REMINDER_ID", -1)
                val medicationId = intent.getIntExtra("MEDICATION_ID", -1)
                Log.d(TAG, "Processing reminder trigger for reminder ID: $reminderId, medication ID: $medicationId")

                if (reminderId != -1 && medicationId != -1) {
                    handleReminderTrigger(context, reminderId, medicationId)
                } else {
                    Log.e(TAG, "Invalid reminder/medication ID received.")
                }
            }
             "com.example.dosecerta.ACTION_MARK_TAKEN" -> {
                 val logId = intent.getIntExtra("LOG_ID", -1)
                 val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
                 Log.d(TAG, "Processing Mark Taken action for log ID: $logId, notification ID: $notificationId")
                 if (logId != -1 && notificationId != -1) {
                     markAsTaken(context, logId, notificationId)
                 }
             }
             "com.example.dosecerta.ACTION_MARK_SKIPPED" -> {
                 val logId = intent.getIntExtra("LOG_ID", -1)
                 val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
                 Log.d(TAG, "Processing Mark Skipped action for log ID: $logId, notification ID: $notificationId")
                 if (logId != -1 && notificationId != -1) {
                     markAsSkipped(context, logId, notificationId)
                 }
             }
            // Add other actions like Snooze if needed
        }
    }

    private fun handleReminderTrigger(context: Context, reminderId: Int, medicationId: Int) {
        scope.launch {
            val repository = (context.applicationContext as DoseCertaApplication).repository
            try {
                val medication = repository.getMedicationById(medicationId)
                val reminder = repository.getReminderById(reminderId)

                if (medication != null && reminder != null) {
                    // 1. Reschedule the NEXT alarm FIRST to ensure continuity
                    var rescheduleSuccess = true
                    if (reminder.isEnabled && medication.frequencyType != FrequencyType.AS_NEEDED) {
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        rescheduleSuccess = ReminderScheduler.scheduleSingleReminder(context, alarmManager, medication, reminder)
                        if (rescheduleSuccess) {
                            Log.d(TAG, "Successfully rescheduled next alarm for reminder ID: $reminderId")
                        } else {
                            Log.e(TAG, "Failed to reschedule alarm for reminder ID: $reminderId - permission issue")
                        }
                    }
                    
                    // 2. Only trigger notification if we have a valid medication/reminder
                    // Check if we already have a log for this scheduled time to avoid duplicates
                    val scheduledTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, reminder.hour)
                        set(Calendar.MINUTE, reminder.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                    
                    val existingLog = repository.findLogByMedicationAndScheduledTime(medication.id, scheduledTime)
                    if (existingLog == null) {
                        triggerNotification(context, reminder, medication)
                    } else {
                        Log.d(TAG, "Log already exists for this scheduled time, skipping notification")
                    }
                    
                    // 3. Log scheduling issues for debugging 
                    if (!rescheduleSuccess) {
                        Log.w(TAG, "Notification reliability may be compromised due to reschedule failure")
                    }
                } else {
                    Log.e(TAG, "Medication or Reminder not found in DB for IDs: Med=$medicationId, Rem=$reminderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder trigger", e)
                // Attempt to reschedule even if there was an error
                try {
                    val medication = repository.getMedicationById(medicationId)
                    val reminder = repository.getReminderById(reminderId)
                    if (medication != null && reminder != null && reminder.isEnabled) {
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        ReminderScheduler.scheduleSingleReminder(context, alarmManager, medication, reminder)
                        Log.d(TAG, "Emergency reschedule attempted after error")
                    }
                } catch (rescheduleError: Exception) {
                    Log.e(TAG, "Emergency reschedule also failed", rescheduleError)
                }
            }
        }
    }

    private suspend fun triggerNotification(context: Context, reminder: Reminder, medication: Medication) {
        val repository = (context.applicationContext as DoseCertaApplication).repository
        val notificationId = reminder.id // Use reminder ID as notification ID
        
        // Create pending log entry using safe insert/update to prevent duplicates
        val pendingLog = MedicationLog(
            medicationId = medication.id,
            medicationName = medication.name,
            dosage = medication.dosage, 
            scheduledTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time,
            logTimestamp = Date(), // Log creation time
            status = LogStatus.MISSED // Default to missed until action taken
        )
        val logId = repository.insertOrUpdateLog(pendingLog).toInt()

        // Verify notification permission before attempting to show
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing POST_NOTIFICATIONS permission, cannot show notification")
                return
            }
        }

        try {
            NotificationHelper.showMedicationNotification(
                context,
                notificationId,
                logId, // Pass log ID for actions
                medication.name,
                "${medication.dosage}"
            )
            Log.d(TAG, "Notification triggered for medication: ${medication.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification for medication: ${medication.name}", e)
        }
    }

    private fun markAsTaken(context: Context, logId: Int, notificationId: Int) {
        scope.launch {
            val repository = (context.applicationContext as DoseCertaApplication).repository
            try {
                val log = repository.getLogById(logId)
                if (log != null) {
                    val updatedLog = log.copy(status = LogStatus.TAKEN, logTimestamp = Date())
                    repository.updateLog(updatedLog)
                    
                    // Clean up any potential duplicates for this scheduled time
                    log.scheduledTime?.let { scheduledTime ->
                        repository.cleanupDuplicateLogs(log.medicationId, scheduledTime)
                    }
                    
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    Log.d(TAG, "Marked log ID $logId as TAKEN and cleaned up duplicates.")
                } else {
                    Log.e(TAG, "Could not find log ID $logId to mark as taken.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking log as taken", e)
            }
        }
    }

    private fun markAsSkipped(context: Context, logId: Int, notificationId: Int) {
        scope.launch {
            val repository = (context.applicationContext as DoseCertaApplication).repository
            try {
                val log = repository.getLogById(logId)
                if (log != null) {
                    val updatedLog = log.copy(status = LogStatus.SKIPPED, logTimestamp = Date())
                    repository.updateLog(updatedLog)
                    
                    // Clean up any potential duplicates for this scheduled time
                    log.scheduledTime?.let { scheduledTime ->
                        repository.cleanupDuplicateLogs(log.medicationId, scheduledTime)
                    }
                    
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    Log.d(TAG, "Marked log ID $logId as SKIPPED and cleaned up duplicates.")
                } else {
                    Log.e(TAG, "Could not find log ID $logId to mark as skipped.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking log as skipped", e)
            }
        }
    }
} 