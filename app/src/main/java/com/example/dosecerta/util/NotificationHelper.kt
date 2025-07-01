package com.example.dosecerta.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dosecerta.MainActivity
import com.example.dosecerta.R
import com.example.dosecerta.receivers.AlarmReceiver // For notification actions

object NotificationHelper {

    private const val CHANNEL_ID = "medication_reminders"
    private const val CHANNEL_NAME = "Medication Reminders"
    private const val CHANNEL_DESCRIPTION = "Notifications for medication schedules"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH // High importance for reminders
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                // Configure channel properties like sound, vibration, lights etc.
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showMedicationNotification(
        context: Context,
        notificationId: Int, 
        logId: Int, // Pass log ID to be used in actions
        title: String,
        contentText: String
    ) {
        // Check notification permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e("NotificationHelper", "Missing POST_NOTIFICATIONS permission")
                return
            }
        }

        try {
            // Create notification channel (safe to call repeatedly)
            createNotificationChannel(context)

            // Intent to open MainActivity when notification is tapped
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("OPEN_FROM_NOTIFICATION", true)
                putExtra("NOTIFICATION_LOG_ID", logId)
            }
            val contentPendingIntent: PendingIntent = PendingIntent.getActivity(
                context, notificationId, contentIntent, 
                getPendingIntentFlags()
            )

        // --- Notification Actions ---

            // --- Notification Actions ---

            // "Taken" Action
            val takenIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.example.dosecerta.ACTION_MARK_TAKEN"
                putExtra("LOG_ID", logId)
                putExtra("NOTIFICATION_ID", notificationId)
            }
            // Use different request codes for different actions to make PendingIntents unique
            val takenPendingIntent: PendingIntent = PendingIntent.getBroadcast(
                context, notificationId * 10 + 1, takenIntent, 
                getPendingIntentFlags(true)
            )

            // "Skipped" Action  
            val skippedIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.example.dosecerta.ACTION_MARK_SKIPPED"
                putExtra("LOG_ID", logId)
                putExtra("NOTIFICATION_ID", notificationId)
            }
            val skippedPendingIntent: PendingIntent = PendingIntent.getBroadcast(
                context, notificationId * 10 + 2, skippedIntent, 
                getPendingIntentFlags(true)
            )

            // Build the notification
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_meds_black_24dp) // Use appropriate icon
                .setContentTitle(title)
                .setContentText("Time to take: $contentText")
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensure it pops up
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentPendingIntent) // Intent on tap
                .setAutoCancel(true) // Dismiss on tap
                .addAction(R.drawable.ic_check_circle, "Taken", takenPendingIntent) // Add Taken action
                .addAction(R.drawable.ic_cancel, "Skip", skippedPendingIntent) // Add Skip action
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Use default sound, vibration, lights
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
                .setTimeoutAfter(1000 * 60 * 60) // Auto-dismiss after 1 hour if no action taken

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
            
            android.util.Log.d("NotificationHelper", "Successfully posted notification ID: $notificationId for medication: $title")
            
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "SecurityException posting notification", e)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error posting notification", e)
        }
    }
    
    // Helper function to get appropriate PendingIntent flags
    private fun getPendingIntentFlags(isMutable: Boolean = false): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isMutable) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }
    
    // Helper function to cancel a specific notification
    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
            android.util.Log.d("NotificationHelper", "Cancelled notification ID: $notificationId")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error cancelling notification ID: $notificationId", e)
        }
    }
    
    // Helper function to check if notifications are enabled
    fun areNotificationsEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
} 