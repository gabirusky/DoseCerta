package com.example.dosecerta.util // Or service package

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.Medication // Import Medication
import com.example.dosecerta.data.model.Reminder
import com.example.dosecerta.receivers.AlarmReceiver // To be created
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit // For date difference calculation

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    // Accept Medication object along with reminders
    fun scheduleReminders(context: Context, medication: Medication, reminders: List<Reminder>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (medication.frequencyType == FrequencyType.AS_NEEDED) {
            Log.d(TAG, "Skipping scheduling for AS_NEEDED medication: ${medication.name}")
            // Ensure any existing alarms for these reminders are cancelled
            cancelReminders(context, reminders)
            return
        }

        for (reminder in reminders) {
            if (reminder.isEnabled) {
                if (!scheduleSingleReminder(context, alarmManager, medication, reminder)) {
                    Log.w(TAG, "Failed to schedule reminder ID ${reminder.id} due to permission issue")
                }
            }
        }
        Log.d(TAG, "Scheduled/Updated reminders for medication: ${medication.name}")
    }

    // Also accept Medication here
    // Returns true if scheduling was successful or permission not required,
    // false if scheduling failed due to missing SCHEDULE_EXACT_ALARM permission on Android 12+
    fun scheduleSingleReminder(context: Context, alarmManager: AlarmManager, medication: Medication, reminder: Reminder): Boolean {
        
        // Validate reminder is enabled before scheduling
        if (!reminder.isEnabled) {
            Log.d(TAG, "Reminder ID ${reminder.id} is disabled, cancelling any existing alarm")
            cancelSingleReminder(context, reminder)
            return true
        }
        
        // Calculate the next trigger time based on frequency
        val nextTriggerTime = calculateNextTriggerTime(medication, reminder)

        if (nextTriggerTime == null) {
             Log.w(TAG, "Could not calculate next trigger time for reminder ID ${reminder.id}. Skipping schedule.")
             // Potentially cancel any existing alarm for this specific reminder ID
             cancelSingleReminder(context, reminder)
             return false
        }
        
        // Validate trigger time is in the future
        val now = System.currentTimeMillis()
        if (nextTriggerTime <= now) {
            Log.w(TAG, "Calculated trigger time is in the past for reminder ID ${reminder.id}. Skipping schedule.")
            return false
        }

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.dosecerta.ACTION_TRIGGER_REMINDER"
            putExtra("REMINDER_ID", reminder.id)
            putExtra("MEDICATION_ID", reminder.medicationId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id, // Unique request code
            alarmIntent,
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             } else {
                 PendingIntent.FLAG_UPDATE_CURRENT
             }
        )

        // Schedule the alarm for the calculated next trigger time
        try {
             // Check for exact alarm permission (Android 12+ requires special access)
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
                 if (!alarmManager.canScheduleExactAlarms()) {
                     Log.e(TAG, "Missing SCHEDULE_EXACT_ALARM permission. Cannot schedule exact alarm for Reminder ID: ${reminder.id}")
                     // Try to schedule inexact alarm as fallback
                     try {
                         alarmManager.set(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
                         Log.w(TAG, "Scheduled inexact alarm as fallback for reminder ID ${reminder.id}")
                         return true
                     } catch (fallbackException: Exception) {
                         Log.e(TAG, "Fallback alarm scheduling also failed for reminder ID ${reminder.id}", fallbackException)
                         return false
                     }
                 }
             }
             
             // Schedule exact alarm based on API level
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                 alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
            } else {
                 alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
            }
            
            val triggerDate = Calendar.getInstance().apply { timeInMillis = nextTriggerTime }.time
            Log.d(TAG, "Successfully scheduled exact alarm for reminder ID ${reminder.id} at $triggerDate")
            return true
        } catch (e: SecurityException) {
             Log.e(TAG, "SecurityException: Could not schedule reminder ID ${reminder.id}. Check manifest permissions?", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error scheduling reminder ID ${reminder.id}", e)
            return false
        }
    }

    private fun calculateNextTriggerTime(medication: Medication, reminder: Reminder): Long? {
        val now = Calendar.getInstance()
        val reminderBaseTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (medication.frequencyType) {
            FrequencyType.DAILY -> {
                val triggerTime = reminderBaseTime.clone() as Calendar
                if (triggerTime.before(now) || triggerTime.timeInMillis - now.timeInMillis < 60000) { // Add 1 minute buffer
                    triggerTime.add(Calendar.DAY_OF_YEAR, 1) // Move to tomorrow if already past or too close
                }
                triggerTime.timeInMillis
            }
            FrequencyType.EVERY_X_DAYS -> {
                val interval = medication.frequencyIntervalDays
                val startDate = medication.startDate
                if (interval == null || interval <= 0 || startDate == null) {
                    Log.e(TAG, "Invalid interval ($interval) or start date ($startDate) for EVERY_X_DAYS reminder ID ${reminder.id}")
                    return null // Cannot calculate
                }

                val startCal = Calendar.getInstance().apply { time = startDate }
                // Normalize startCal to the reminder time on the start date
                startCal.set(Calendar.HOUR_OF_DAY, reminder.hour)
                startCal.set(Calendar.MINUTE, reminder.minute)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                
                val triggerTime = startCal.clone() as Calendar

                // Find the next valid trigger date starting from the anchor (start date)
                var attempts = 0
                while ((triggerTime.before(now) || triggerTime.timeInMillis - now.timeInMillis < 60000) && attempts < 365) {
                     triggerTime.add(Calendar.DAY_OF_YEAR, interval)
                     attempts++
                }
                
                if (attempts >= 365) {
                    Log.e(TAG, "Could not find valid future date for EVERY_X_DAYS reminder ID ${reminder.id} after $attempts attempts")
                    return null
                }
                
                triggerTime.timeInMillis
            }
            FrequencyType.SPECIFIC_DAYS_OF_WEEK -> {
                val daysOfWeekString = medication.frequencyDaysOfWeek
                if (daysOfWeekString.isNullOrBlank()) {
                    Log.e(TAG, "Missing days of week for SPECIFIC_DAYS_OF_WEEK reminder ID ${reminder.id}")
                    return null
                }
                // Parse days string "1,3,5" into a set of Calendar.DAY_OF_WEEK constants
                val targetDays = daysOfWeekString.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                if (targetDays.isEmpty()) {
                     Log.e(TAG, "Invalid days of week string: $daysOfWeekString for reminder ID ${reminder.id}")
                     return null
                }
                
                // Validate day values are between 1-7 (Calendar.SUNDAY to Calendar.SATURDAY)
                if (targetDays.any { it < 1 || it > 7 }) {
                    Log.e(TAG, "Invalid day of week values in: $daysOfWeekString for reminder ID ${reminder.id}")
                    return null
                }
                
                val triggerTime = reminderBaseTime.clone() as Calendar
                
                 // Start check from today (or tomorrow if time is past or too close)
                if (triggerTime.before(now) || triggerTime.timeInMillis - now.timeInMillis < 60000) {
                    triggerTime.add(Calendar.DAY_OF_YEAR, 1)
                }

                // Loop until we find a day that matches the target days (max 7 days to prevent infinite loop)
                var attempts = 0
                while (triggerTime.get(Calendar.DAY_OF_WEEK) !in targetDays && attempts < 7) {
                    triggerTime.add(Calendar.DAY_OF_YEAR, 1)
                    attempts++
                }
                
                if (attempts >= 7) {
                    Log.e(TAG, "Could not find matching day of week for reminder ID ${reminder.id} with days: $targetDays")
                    return null
                }
                
                triggerTime.timeInMillis
            }
            FrequencyType.AS_NEEDED -> {
                null // No automatic scheduling
            }
        }
    }

    // Renamed from cancelReminder to cancelSingleReminder for clarity
    fun cancelSingleReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            alarmIntent,
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
             } else {
                 PendingIntent.FLAG_NO_CREATE
             }
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for reminder ID ${reminder.id}")
        } else {
             Log.w(TAG, "Could not find PendingIntent to cancel for reminder ID ${reminder.id}")
        }
    }
    
    // Renamed for clarity
    fun cancelReminders(context: Context, reminders: List<Reminder>) {
         for (reminder in reminders) {
             cancelSingleReminder(context, reminder)
         }
    }
} 