package com.dosecerta.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dosecerta.R
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.notification.NotificationActionReceiver
import com.dosecerta.util.Constants

/**
 * Foreground Service that manages the alarm lifecycle.
 * Plays sound via AlarmSoundManager and shows full-screen notification.
 */
class AlarmService : Service() {
    
    companion object {
        private const val TAG = "AlarmService"
        const val CHANNEL_ID = "medication_alarm_channel"
        const val FOREGROUND_NOTIFICATION_ID = 1001
        
        // Intent extras
        const val EXTRA_MEDICATION = "medication"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
        const val EXTRA_SOUND_URI = "sound_uri"
        
        // Action to stop alarm
        const val ACTION_STOP_ALARM = "com.dosecerta.ACTION_STOP_ALARM"
        
        /**
         * Start the alarm service.
         */
        fun startAlarm(
            context: Context,
            medication: Medication,
            scheduleId: Long,
            scheduledTime: Long,
            soundUri: Uri?
        ) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_MEDICATION, medication)
                putExtra(EXTRA_MEDICATION_ID, medication.id)
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                soundUri?.let { putExtra(EXTRA_SOUND_URI, it.toString()) }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop the alarm service.
         */
        fun stopAlarm(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(intent)
        }
    }
    
    private lateinit var soundManager: AlarmSoundManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService created")
        
        soundManager = AlarmSoundManager()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarmAndService()
            return START_NOT_STICKY
        }
        
        // Get alarm data from intent
        val medication = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_MEDICATION, Medication::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_MEDICATION)
        }
        
        val medicationId = intent?.getLongExtra(EXTRA_MEDICATION_ID, -1L) ?: -1L
        val scheduleId = intent?.getLongExtra(EXTRA_SCHEDULE_ID, -1L) ?: -1L
        val scheduledTime = intent?.getLongExtra(EXTRA_SCHEDULED_TIME, 0L) ?: 0L
        val soundUriString = intent?.getStringExtra(EXTRA_SOUND_URI)
        val soundUri = soundUriString?.let { Uri.parse(it) }
        
        if (medication == null || medicationId == -1L) {
            Log.e(TAG, "Invalid medication data, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Start foreground service with notification that has full-screen intent
        val notification = createAlarmNotification(medication, medicationId, scheduleId, scheduledTime)
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        
        // Start playing alarm sound
        soundManager.start(this, soundUri)
        
        // Force launch AlarmActivity directly after becoming foreground
        // This is allowed because we're now a foreground service
        launchAlarmActivityDirectly(medication, medicationId, scheduleId, scheduledTime)
        
        Log.d(TAG, "Alarm started for medication: ${medication.name}")
        
        return START_STICKY
    }
    
    /**
     * Directly launch AlarmActivity from foreground service with a small delay.
     * The delay ensures foreground service is fully registered, which is required for
     * activity launch on Android 10+ BAL (Background Activity Launch) restrictions.
     */
    private fun launchAlarmActivityDirectly(
        medication: Medication,
        medicationId: Long,
        scheduleId: Long,
        scheduledTime: Long
    ) {
        // Use a small delay to ensure foreground service is fully registered
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val activityIntent = Intent(this, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_MEDICATION, medication)
                    putExtra(EXTRA_MEDICATION_ID, medicationId)
                    putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                }
                startActivity(activityIntent)
                Log.d(TAG, "AlarmActivity launched directly from foreground service")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch AlarmActivity directly: ${e.message}")
                // Full-screen intent in notification will serve as fallback
            }
        }, 100) // 100ms delay to ensure foreground service is registered
    }
    

    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AlarmService destroyed")
        
        soundManager.stop()
        soundManager.release()
        releaseWakeLock()
    }
    
    /**
     * Create notification channel for alarm notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarmes de Medicação",
                NotificationManager.IMPORTANCE_HIGH  // HIGH is required for full-screen intent
            ).apply {
                description = "Alarmes de lembretes de medicamentos"
                setSound(null, null) // Sound managed by MediaPlayer
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // Bypass Do Not Disturb mode
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            // Delete old channel to apply new settings (channels can't be updated)
            notificationManager.deleteNotificationChannel(CHANNEL_ID)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created with DND bypass")
        }
    }
    
    /**
     * Create the foreground notification with full-screen intent.
     */
    private fun createAlarmNotification(
        medication: Medication,
        medicationId: Long,
        scheduleId: Long,
        scheduledTime: Long
    ): Notification {
        val notificationId = generateNotificationId(medicationId, scheduleId)
        
        // Full-screen intent to open AlarmActivity
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_MEDICATION, medication)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Content intent (if user opens from notification center)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            notificationId + 1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Action buttons
        val takeIntent = createActionIntent(Constants.ACTION_TAKE_MEDICATION, medicationId, scheduleId, scheduledTime)
        val takePendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId * 10 + 1,
            takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val skipIntent = createActionIntent(Constants.ACTION_SKIP_MEDICATION, medicationId, scheduleId, scheduledTime)
        val skipPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId * 10 + 2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val snoozeIntent = createActionIntent(Constants.ACTION_SNOOZE_MEDICATION, medicationId, scheduleId, scheduledTime)
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId * 10 + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.alarm_service_notification_title))
            .setContentText("${medication.name} - ${medication.dosage} ${medication.unit}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${medication.name} - ${medication.dosage} ${medication.unit}\nToque para abrir ou use os botões abaixo."))
            .setPriority(NotificationCompat.PRIORITY_MAX)  // MAX priority for heads-up display
            .setCategory(NotificationCompat.CATEGORY_ALARM)  // ALARM category for alarm-like behavior
            .setOngoing(true) // Cannot be dismissed
            .setAutoCancel(false)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500)) // Vibration triggers heads-up
            .setOnlyAlertOnce(false) // Alert every time
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)  // This triggers activity launch over lock screen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show full content on lock screen
            .setPublicVersion(  // Explicit public version for lock screen
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.alarm_service_notification_title))
                    .setContentText("${medication.name} - ${medication.dosage} ${medication.unit}")
                    .build()
            )
            // Action buttons visible on lock screen and heads-up
            .addAction(R.drawable.ic_check, getString(R.string.notification_action_take), takePendingIntent)
            .addAction(R.drawable.ic_close, getString(R.string.notification_action_skip), skipPendingIntent)
            .addAction(R.drawable.ic_notifications, getString(R.string.notification_action_snooze), snoozePendingIntent)
            .build()
    }
    
    /**
     * Create intent for notification action.
     */
    private fun createActionIntent(
        action: String,
        medicationId: Long,
        scheduleId: Long,
        scheduledTime: Long
    ): Intent {
        return Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(Constants.EXTRA_MEDICATION_ID, medicationId)
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(Constants.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
    }
    
    /**
     * Generate unique notification ID.
     */
    private fun generateNotificationId(medicationId: Long, scheduleId: Long): Int {
        return (medicationId * 1000 + scheduleId).toInt()
    }
    
    /**
     * Acquire wake lock to keep screen on.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DoseCerta::AlarmWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }
    
    /**
     * Release wake lock.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }
    
    /**
     * Stop alarm and service.
     */
    private fun stopAlarmAndService() {
        Log.d(TAG, "Stopping alarm and service")
        soundManager.stop()
        stopForeground(true)
        stopSelf()
    }
}
