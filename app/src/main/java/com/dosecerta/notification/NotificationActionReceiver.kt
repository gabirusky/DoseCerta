package com.dosecerta.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.util.Constants
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.runBlocking

/**
 * Receiver for notification action buttons (Take, Skip, Snooze).
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationAction"
        const val ACTION_SHOW_SNACKBAR = "com.dosecerta.SHOW_SNACKBAR"
        const val EXTRA_SNACKBAR_MESSAGE = "snackbar_message"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        
        // Get medication details from intent
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val scheduleId = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L)
        val scheduledTime = intent.getLongExtra(Constants.EXTRA_SCHEDULED_TIME, 0L)
        
        Log.d(TAG, "Medication ID: $medicationId, Schedule ID: $scheduleId, Time: $scheduledTime")
        
        if (medicationId == -1L || scheduleId == -1L) {
            Log.e(TAG, "Invalid medication or schedule ID")
            Toast.makeText(context, "Erro: ID inválido", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Dismiss the notification immediately
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (medicationId * 1000 + scheduleId).toInt()
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Notification dismissed: $notificationId")
        
        // Stop alarm service if it's running
        com.dosecerta.alarm.AlarmService.stopAlarm(context)
        Log.d(TAG, "Alarm service stop signal sent")
        
        // Use runBlocking to ensure database operations complete
        // This is acceptable in BroadcastReceiver for critical operations
        runBlocking {
            try {
                val database = DoseCertaDatabase.getDatabase(context)
                val logDao = database.medicationLogDao()
                val medicationDao = database.medicationDao()
                val alarmScheduler = com.dosecerta.alarm.AlarmScheduler(context)
                
                // Get medication name for toast message
                val medication = medicationDao.getMedicationByIdSync(medicationId)
                val medicationName = medication?.name ?: "Medicamento"
                
                when (intent.action) {
                    Constants.ACTION_TAKE_MEDICATION -> {
                        Log.d(TAG, "Processing TAKE action")
                        handleTakeAction(logDao, medicationId, scheduleId, scheduledTime)
                        // Cancel missed reminder — user confirmed they took it
                        alarmScheduler.cancelMissedReminderAlarm(medicationId, scheduleId, scheduledTime)
                        showToast(context, context.getString(R.string.medication_taken_toast, medicationName))
                        sendSnackbarBroadcast(context, context.getString(R.string.medication_taken_message, medicationName))
                    }
                    
                    Constants.ACTION_SKIP_MEDICATION -> {
                        Log.d(TAG, "Processing SKIP action")
                        handleSkipAction(logDao, medicationId, scheduleId, scheduledTime)
                        // B1: Schedule missed reminder — deliberate skip should still get a follow-up
                        val reminderHours = SettingsPreferences(context).getMissedReminderHoursSync()
                        alarmScheduler.scheduleMissedReminderAlarm(medicationId, scheduleId, scheduledTime, reminderHours)
                        Log.d(TAG, "Scheduled missed reminder after skip for $reminderHours hours")
                        showToast(context, context.getString(R.string.medication_skipped_toast, medicationName))
                        sendSnackbarBroadcast(context, context.getString(R.string.medication_skipped_message, medicationName))
                    }
                    
                    Constants.ACTION_SNOOZE_MEDICATION -> {
                        Log.d(TAG, "Processing SNOOZE action")
                        // For snooze, reschedule both the notification alarm and the missed reminder
                        handleSnoozeAction(context, medicationId, scheduleId, scheduledTime)
                        showToast(context, context.getString(R.string.medication_snoozed_toast, medicationName))
                        sendSnackbarBroadcast(context, context.getString(R.string.medication_snoozed_message, medicationName))
                    }

                    Constants.ACTION_DISMISS_REMINDER -> {
                        // B9: Notification already cancelled at the top of onReceive — nothing else to do
                        Log.d(TAG, "Reminder dismissed by user")
                    }
                }
                
                Log.d(TAG, "Action completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing action", e)
                e.printStackTrace()
                showToast(context, "Erro ao processar ação: ${e.message}")
            }
        }
    }
    
    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun sendSnackbarBroadcast(context: Context, message: String) {
        val broadcastIntent = Intent(ACTION_SHOW_SNACKBAR).apply {
            putExtra(EXTRA_SNACKBAR_MESSAGE, message)
        }
        context.sendBroadcast(broadcastIntent)
    }
    
    private suspend fun handleTakeAction(
        logDao: com.dosecerta.data.local.dao.MedicationLogDao,
        medicationId: Long,
        scheduleId: Long,
        scheduledTime: Long
    ) {
        val existingLog = logDao.getLog(medicationId, scheduleId, scheduledTime)
        val currentTime = System.currentTimeMillis()
        
        if (existingLog != null) {
            logDao.update(
                existingLog.copy(
                    status = MedicationStatus.TAKEN,
                    actualTime = currentTime
                )
            )
            Log.d(TAG, "Updated existing log to TAKEN")
        } else {
            val insertedId = logDao.insert(
                MedicationLog(
                    medicationId = medicationId,
                    scheduleId = scheduleId,
                    scheduledTime = scheduledTime,
                    actualTime = currentTime,
                    status = MedicationStatus.TAKEN
                )
            )
            Log.d(TAG, "Created new log as TAKEN with ID: $insertedId")
        }
    }
    
    private suspend fun handleSkipAction(
        logDao: com.dosecerta.data.local.dao.MedicationLogDao,
        medicationId: Long,
        scheduleId: Long,
        scheduledTime: Long
    ) {
        val existingLog = logDao.getLog(medicationId, scheduleId, scheduledTime)
        val currentTime = System.currentTimeMillis()
        
        if (existingLog != null) {
            logDao.update(
                existingLog.copy(
                    status = MedicationStatus.SKIPPED,
                    actualTime = currentTime
                )
            )
            Log.d(TAG, "Updated existing log to SKIPPED")
        } else {
            val insertedId = logDao.insert(
                MedicationLog(
                    medicationId = medicationId,
                    scheduleId = scheduleId,
                    scheduledTime = scheduledTime,
                    actualTime = currentTime,
                    status = MedicationStatus.SKIPPED
                )
            )
            Log.d(TAG, "Created new log as SKIPPED with ID: $insertedId")
        }
    }
    
    private fun handleSnoozeAction(
        context: Context,
        medicationId: Long,
        scheduleId: Long,
        scheduledTime: Long
    ) {
        // Reschedule alarm for 10 minutes later
        val alarmScheduler = com.dosecerta.alarm.AlarmScheduler(context)
        alarmScheduler.snoozeAlarm(medicationId, scheduleId, scheduledTime)
        Log.d(TAG, "Alarm snoozed for 10 minutes")
    }
}
