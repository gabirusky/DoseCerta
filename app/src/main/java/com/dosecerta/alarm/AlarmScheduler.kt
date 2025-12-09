package com.dosecerta.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.util.Constants
import com.dosecerta.util.DateTimeUtils
import java.util.*

/**
 * Service class to schedule and manage medication alarms using AlarmManager.
 */
class AlarmScheduler(private val context: Context) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * Schedule an alarm for a specific medication schedule.
     */
    fun scheduleAlarm(medicationId: Long, schedule: Schedule) {
        // Calculate the next alarm time
        val alarmTime = calculateNextAlarmTime(schedule)
        
        if (alarmTime <= System.currentTimeMillis()) {
            // If time has already passed today, don't schedule
            return
        }
        
        val intent = createAlarmIntent(medicationId, schedule.id, alarmTime)
        val pendingIntent = createPendingIntent(intent, medicationId, schedule.id)
        
        // Schedule the alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires exact alarm permission check
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
                )
            } else {
                // Fallback to inexact alarm
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
                )
            }
        } else {
            // Pre-Android 12
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime,
                pendingIntent
            )
        }
    }
    
    /**
     * Schedule alarms for all schedules of a medication.
     */
    fun scheduleAlarmsForMedication(medicationId: Long, schedules: List<Schedule>) {
        schedules.forEach { schedule ->
            scheduleAlarm(medicationId, schedule)
        }
    }
    
    /**
     * Cancel alarm for a specific schedule.
     */
    fun cancelAlarm(medicationId: Long, scheduleId: Long) {
        val intent = createAlarmIntent(medicationId, scheduleId, 0L)
        val pendingIntent = createPendingIntent(intent, medicationId, scheduleId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    
    /**
     * Cancel all alarms for a medication.
     * This includes main reminders AND missed-check/missed-reminder alarms.
     */
    fun cancelAlarmsForMedication(medicationId: Long, schedules: List<Schedule>) {
        schedules.forEach { schedule ->
            cancelAlarm(medicationId, schedule.id)
            // Also cancel any pending missed-check and missed-reminder alarms
            cancelMissedCheckAlarmForSchedule(medicationId, schedule.id)
            cancelMissedReminderAlarmForSchedule(medicationId, schedule.id)
        }
    }
    
    /**
     * Cancel missed-check alarm without needing the original scheduledTime.
     * Uses a deterministic request code based on medicationId and scheduleId.
     */
    private fun cancelMissedCheckAlarmForSchedule(medicationId: Long, scheduleId: Long) {
        val intent = Intent(context, com.dosecerta.notification.MarkMissedReceiver::class.java).apply {
            action = Constants.ACTION_MARK_MISSED
        }
        val requestCode = ((medicationId * 1000000 + scheduleId * 1000 + 999).toInt())
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent, 
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
    
    /**
     * Cancel missed-reminder alarm without needing the original scheduledTime.
     */
    private fun cancelMissedReminderAlarmForSchedule(medicationId: Long, scheduleId: Long) {
        val intent = Intent(context, com.dosecerta.notification.MissedReminderReceiver::class.java)
        val requestCode = ((medicationId * 1000000 + scheduleId * 1000 + 998).toInt())
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
    
    /**
     * Reschedule an alarm (used for snooze functionality).
     */
    fun snoozeAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long) {
        val snoozeTime = System.currentTimeMillis() + (Constants.SNOOZE_DURATION_MINUTES * 60 * 1000)
        
        val intent = createAlarmIntent(medicationId, scheduleId, scheduledTime)
        val pendingIntent = createPendingIntent(intent, medicationId, scheduleId)
        
        // Schedule the snoozed alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                snoozeTime,
                pendingIntent
            )
        }
    }
    
    /**
     * Calculate the next alarm time for a schedule.
     */
    private fun calculateNextAlarmTime(schedule: Schedule): Long {
        val calendar = Calendar.getInstance()
        val currentTime = calendar.timeInMillis
        
        // Set time to scheduled hour and minute
        calendar.set(Calendar.HOUR_OF_DAY, schedule.timeInMinutes / 60)
        calendar.set(Calendar.MINUTE, schedule.timeInMinutes % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        var alarmTime = calendar.timeInMillis
        
        // If time has passed today, schedule for tomorrow (or next valid day)
        if (alarmTime <= currentTime) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            alarmTime = calendar.timeInMillis
        }
        
        // Check if this day of week is valid (if specific days are set)
        if (schedule.daysOfWeek.isNotEmpty()) {
            val maxAttempts = 7 // Don't loop forever
            var attempts = 0
            
            while (!schedule.daysOfWeek.contains(calendar.get(Calendar.DAY_OF_WEEK)) && attempts < maxAttempts) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                attempts++
            }
            
            alarmTime = calendar.timeInMillis
        }
        
        return alarmTime
    }
    
    /**
     * Create an intent for the alarm.
     */
    private fun createAlarmIntent(medicationId: Long, scheduleId: Long, scheduledTime: Long): Intent {
        return Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
    }
    
    /**
     * Create a PendingIntent for the alarm.
     */
    private fun createPendingIntent(intent: Intent, medicationId: Long, scheduleId: Long): PendingIntent {
        val requestCode = (medicationId * 1000 + scheduleId).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    fun scheduleMissedCheckAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long) {
        val checkTime = System.currentTimeMillis() + (Constants.MISSED_CHECK_DELAY_MINUTES * 60 * 1000L)
        val intent = Intent(context, com.dosecerta.notification.MarkMissedReceiver::class.java).apply {
            action = Constants.ACTION_MARK_MISSED
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val requestCode = ((medicationId * 1000000 + scheduleId * 1000 + 999).toInt())
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent)
        }
    }
    
    fun cancelMissedCheckAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long) {
        val intent = Intent(context, com.dosecerta.notification.MarkMissedReceiver::class.java).apply {
            action = Constants.ACTION_MARK_MISSED
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val requestCode = ((medicationId * 1000000 + scheduleId * 1000 + 999).toInt())
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    
    fun scheduleMissedReminderAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long) {
        val reminderTime = System.currentTimeMillis() + (Constants.MISSED_REMINDER_DELAY_HOURS * 60 * 60 * 1000L)
        val intent = Intent(context, com.dosecerta.notification.MissedReminderReceiver::class.java).apply {
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val requestCode = ((medicationId * 1000000 + scheduleId * 1000 + 998).toInt())
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
        }
    }
}
