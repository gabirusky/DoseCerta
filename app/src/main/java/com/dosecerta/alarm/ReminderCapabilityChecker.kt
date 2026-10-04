package com.dosecerta.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class ReminderCapabilities(
    val notificationPermission: Boolean,
    val notificationsEnabled: Boolean,
    val alarmChannelEnabled: Boolean,
    val reminderChannelEnabled: Boolean,
    val exactAlarms: Boolean,
    val fullScreenIntent: Boolean
) {
    val canNotifyAlarm get() = notificationPermission && notificationsEnabled && alarmChannelEnabled
    val canNotifyReminder get() = notificationPermission && notificationsEnabled && reminderChannelEnabled
}

/** Reads effective system state, including user channel overrides. Never treats overlay as a prerequisite. */
class ReminderCapabilityChecker(private val context: Context) {
    fun check(): ReminderCapabilities {
        ReminderChannels.ensure(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        return ReminderCapabilities(
            notificationPermission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            alarmChannelEnabled = manager.getNotificationChannel(ReminderChannels.ALARM)?.importance != NotificationManager.IMPORTANCE_NONE,
            reminderChannelEnabled = manager.getNotificationChannel(ReminderChannels.REMINDER)?.importance != NotificationManager.IMPORTANCE_NONE,
            exactAlarms = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            fullScreenIntent = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()
        )
    }

    fun notificationsSettings(channel: String? = null): Intent = if (channel == null) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
    }

    fun exactAlarmSettings(): Intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())
    fun fullScreenSettings(): Intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri())
    private fun packageUri() = Uri.parse("package:${context.packageName}")
}
