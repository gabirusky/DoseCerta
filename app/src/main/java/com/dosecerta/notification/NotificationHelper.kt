package com.dosecerta.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dosecerta.R
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.ui.MainActivity
import com.dosecerta.util.Constants

/**
 * Helper class to build and display medication notifications.
 */
class NotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    /**
     * Show a medication reminder notification with action buttons.
     */
    fun showMedicationReminder(
        medication: Medication,
        scheduleId: Long,
        scheduledTime: Long
    ) {
        val notificationId = generateNotificationId(medication.id, scheduleId)
        
        // Intent to open the app when notification is tapped
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action: Take medication
        val takeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = Constants.ACTION_TAKE_MEDICATION
            putExtra(Constants.EXTRA_MEDICATION_ID, medication.id)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val takePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action: Skip medication
        val skipIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = Constants.ACTION_SKIP_MEDICATION
            putExtra(Constants.EXTRA_MEDICATION_ID, medication.id)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action: Snooze medication (10 minutes)
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = Constants.ACTION_SNOOZE_MEDICATION
            putExtra(Constants.EXTRA_MEDICATION_ID, medication.id)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_title, medication.name))
            .setContentText(context.getString(
                R.string.notification_message,
                medication.dosage + " " + medication.unit,
                getFormString(medication.pharmaceuticalForm.name)
            ))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .addAction(
                R.drawable.ic_pill,
                context.getString(R.string.notification_action_take),
                takePendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_skip),
                skipPendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_snooze),
                snoozePendingIntent
            )
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * Cancel a specific notification.
     */
    fun cancelNotification(medicationId: Long, scheduleId: Long) {
        val notificationId = generateNotificationId(medicationId, scheduleId)
        notificationManager.cancel(notificationId)
    }
    
    /**
     * Generate a unique notification ID from medication ID and schedule ID.
     */
    private fun generateNotificationId(medicationId: Long, scheduleId: Long): Int {
        return (medicationId * 1000 + scheduleId).toInt()
    }
    
    /**
     * Get localized pharmaceutical form string.
     */
    private fun getFormString(form: String): String {
        return when (form) {
            "TABLET" -> context.getString(R.string.form_tablet)
            "CAPSULE" -> context.getString(R.string.form_capsule)
            "SYRUP" -> context.getString(R.string.form_syrup)
            "DROPS" -> context.getString(R.string.form_drops)
            "INJECTION" -> context.getString(R.string.form_injection)
            "CREAM" -> context.getString(R.string.form_cream)
            "SPRAY" -> context.getString(R.string.form_spray)
            else -> context.getString(R.string.form_other)
        }
    }
    
    /**
     * Show a reminder notification for missed medication.
     */
    fun showMissedReminderNotification(medicationName: String) {
        val contentIntent = Intent(context, com.dosecerta.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            999999,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.missed_reminder_title))
            .setContentText(context.getString(R.string.missed_reminder_message, medicationName))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(888888, notification)
    }
}
