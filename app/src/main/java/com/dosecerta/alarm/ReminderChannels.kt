package com.dosecerta.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.dosecerta.R
import com.dosecerta.util.Constants

object ReminderChannels {
    const val ALARM = "medication_alarm_channel"
    const val REMINDER = Constants.NOTIFICATION_CHANNEL_ID
    const val SERVICE = "active_alarm_service"

    /** Creation is idempotent. Existing sound, importance and DND choices belong to the user. */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(SERVICE) == null) {
            manager.createNotificationChannel(NotificationChannel(SERVICE,
                context.getString(R.string.reminder_service_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
        if (manager.getNotificationChannel(ALARM) == null) {
            manager.createNotificationChannel(NotificationChannel(ALARM,
                context.getString(R.string.reminder_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminder_alarm_channel_description)
                setSound(null, null) // AlarmService owns the only repeating audio stream.
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
        if (manager.getNotificationChannel(REMINDER) == null) {
            manager.createNotificationChannel(NotificationChannel(REMINDER,
                context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminder_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
    }
}
