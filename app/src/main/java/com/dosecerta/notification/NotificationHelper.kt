package com.dosecerta.notification

import android.app.Notification
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dosecerta.R
import com.dosecerta.alarm.AlarmActivity
import com.dosecerta.alarm.AlarmDiagnostics
import com.dosecerta.alarm.AlarmIdentity
import com.dosecerta.alarm.ReminderCapabilityChecker
import com.dosecerta.alarm.ReminderChannels
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.ui.MainActivity
import com.dosecerta.util.Constants
import com.dosecerta.util.SettingsPreferences

/** Tagged notifications have a full occurrence identity; integer truncation cannot cancel another dose. */
class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun foregroundPlaceholder(): Notification {
        ReminderChannels.ensure(context)
        return NotificationCompat.Builder(context, ReminderChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(context.getString(R.string.reminder_active))
            .setContentText(context.getString(R.string.reminder_active_summary))
            .setSilent(true).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    suspend fun buildAlarm(occurrence: MedicationLog, audibleService: Boolean, allowFullScreen: Boolean): Notification {
        val capabilities = ReminderCapabilityChecker(context).check()
        val detailsOnLock = SettingsPreferences(context).getShowMedicationOnLockScreenSync()
        val channel = if (audibleService) ReminderChannels.ALARM else ReminderChannels.REMINDER
        val card = cardIntent(occurrence.occurrenceId)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(occurrence.snapshotName)
            .setContentText(context.getString(if (audibleService) R.string.reminder_due else R.string.reminder_notification_only))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(if (detailsOnLock) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(card).setOnlyAlertOnce(true).setOngoing(audibleService)
            .setDeleteIntent(actionIntent(occurrence.occurrenceId, AlarmIdentity.SILENCE))
            .addAction(0, context.getString(R.string.reminder_silence), actionIntent(occurrence.occurrenceId, AlarmIdentity.SILENCE))
            .addAction(0, context.getString(R.string.reminder_snooze_ten), actionIntent(occurrence.occurrenceId, Constants.ACTION_SNOOZE_MEDICATION))
        // The alarm channel has no sound: AlarmService owns audio. setSilent(true)
        // also suppresses visual interruption and groups the dose as a silent child.
        // Private lock-screen notifications expose only silence/snooze. Reviewing a dose opens the card.
        if (detailsOnLock) {
            builder.addAction(R.drawable.ic_check, context.getString(R.string.notification_action_take), actionIntent(occurrence.occurrenceId, Constants.ACTION_TAKE_MEDICATION))
            builder.addAction(0, context.getString(R.string.notification_action_skip), actionIntent(occurrence.occurrenceId, Constants.ACTION_SKIP_MEDICATION))
        }
        val publicVersion = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(context.getString(R.string.reminder_private_title))
            .setContentText(context.getString(R.string.reminder_private_message)).setContentIntent(card)
            .addAction(0, context.getString(R.string.reminder_silence), actionIntent(occurrence.occurrenceId, AlarmIdentity.SILENCE))
            .addAction(0, context.getString(R.string.reminder_snooze_ten), actionIntent(occurrence.occurrenceId, Constants.ACTION_SNOOZE_MEDICATION))
            .setSilent(true).build()
        builder.setPublicVersion(publicVersion)
        if (allowFullScreen && capabilities.fullScreenIntent && capabilities.canNotifyAlarm) builder.setFullScreenIntent(card, true)
        AlarmDiagnostics.record(context, "notification_build", occurrence.occurrenceId,
            if (allowFullScreen && capabilities.fullScreenIntent) "fsi_attached" else "no_fsi")
        return builder.build()
    }

    suspend fun showAlarm(occurrence: MedicationLog, audibleService: Boolean, allowFullScreen: Boolean): Boolean {
        val state = ReminderCapabilityChecker(context).check()
        if (!(if (audibleService) state.canNotifyAlarm else state.canNotifyReminder)) return false
        return try {
            manager.notify(tag(occurrence.occurrenceId), 1, buildAlarm(occurrence, audibleService, allowFullScreen))
            AlarmDiagnostics.record(context, "notification", occurrence.occurrenceId, "published")
            true
        } catch (error: RuntimeException) {
            AlarmDiagnostics.record(context, "notification", occurrence.occurrenceId, error.javaClass.simpleName)
            false
        }
    }

    suspend fun showMissedReminder(occurrence: MedicationLog): Boolean {
        if (!ReminderCapabilityChecker(context).check().canNotifyReminder) return false
        val details = SettingsPreferences(context).getShowMedicationOnLockScreenSync()
        val open = PendingIntent.getActivity(context, 0,
            AlarmIdentity.intent(context, MainActivity::class.java, occurrence.occurrenceId, "history").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, ReminderChannels.REMINDER).setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(context.getString(R.string.reminder_private_title)).setContentText(context.getString(R.string.reminder_missed_message)).build()
        val notification = NotificationCompat.Builder(context, ReminderChannels.REMINDER).setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(if (details) occurrence.snapshotName else context.getString(R.string.reminder_private_title))
            .setContentText(context.getString(R.string.reminder_missed_message)).setContentIntent(open).setPublicVersion(public)
            .setVisibility(if (details) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, context.getString(R.string.reminder_action_dismiss), actionIntent(occurrence.occurrenceId, Constants.ACTION_DISMISS_REMINDER))
            .build()
        return try { manager.notify(tag(occurrence.occurrenceId), 1, notification); true }
        catch (error: RuntimeException) { AlarmDiagnostics.record(context, "follow_up", occurrence.occurrenceId, error.javaClass.simpleName); false }
    }

    fun cancel(id: String) { manager.cancel(tag(id), 1) }
    private fun tag(id: String) = AlarmIdentity.uri(id, "notification").toString()
    private fun cardIntent(id: String): PendingIntent {
        val options = if (Build.VERSION.SDK_INT >= 35) ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle() else null
        return PendingIntent.getActivity(context, 0,
            AlarmIdentity.intent(context, AlarmActivity::class.java, id, "card")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options)
    }
    private fun actionIntent(id: String, action: String) = PendingIntent.getBroadcast(context, 0,
        AlarmIdentity.intent(context, NotificationActionReceiver::class.java, id, action).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
