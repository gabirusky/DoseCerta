package com.dosecerta.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.databinding.ActivityAlarmBinding
import com.dosecerta.util.Constants
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen activity shown when alarm rings.
 * Displays medication info and action buttons.
 */
class AlarmActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AlarmActivity"
    }
    
    private lateinit var binding: ActivityAlarmBinding
    private var medication: Medication? = null
    private var medicationId: Long = -1L
    private var scheduleId: Long = -1L
    private var scheduledTime: Long = 0L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup window for display over lockscreen
        setupWindowFlags()
        
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get data from intent
        extractIntentData()
        
        // Display medication info
        displayMedicationInfo()
        
        // Setup button listeners
        setupButtonListeners()
    }
    
    /**
     * Setup window flags to show over lockscreen.
     * Uses aggressive flags for Xiaomi/MIUI/HyperOS compatibility.
     */
    @Suppress("DEPRECATION")
    private fun setupWindowFlags() {
        // Apply window flags BEFORE setContentView for all API levels
        // Xiaomi/MIUI requires multiple flags to work properly
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        // For API 27+ also use the newer methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    Log.d(TAG, "Keyguard dismissed successfully")
                }
                override fun onDismissError() {
                    Log.e(TAG, "Keyguard dismiss error")
                }
                override fun onDismissCancelled() {
                    Log.w(TAG, "Keyguard dismiss cancelled")
                }
            })
        }
        
        // Additional workaround: Force window to be visible
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }
    
    /**
     * Extract medication data from intent.
     */
    private fun extractIntentData() {
        medication = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(AlarmService.EXTRA_MEDICATION, Medication::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(AlarmService.EXTRA_MEDICATION)
        }
        
        medicationId = intent.getLongExtra(AlarmService.EXTRA_MEDICATION_ID, -1L)
        scheduleId = intent.getLongExtra(AlarmService.EXTRA_SCHEDULE_ID, -1L)
        scheduledTime = intent.getLongExtra(AlarmService.EXTRA_SCHEDULED_TIME, 0L)
        
        Log.d(TAG, "Alarm activity opened for medication: ${medication?.name}")
    }
    
    /**
     * Display medication information.
     */
    private fun displayMedicationInfo() {
        medication?.let { med ->
            binding.textMedicationName.text = med.name
            binding.textDosageInfo.text = "${med.dosage} ${med.unit} - ${getFormString(med.pharmaceuticalForm.name)}"
            
            // Format scheduled time - just the time, no prefix
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.textScheduledTime.text = timeFormat.format(Date(scheduledTime))
        }
    }
    
    /**
     * Setup button click listeners.
     */
    private fun setupButtonListeners() {
        binding.btnAlarmTake.setOnClickListener {
            handleTakeAction()
        }
        
        binding.btnAlarmSkip.setOnClickListener {
            handleSkipAction()
        }
        
        binding.btnAlarmSnooze.setOnClickListener {
            handleSnoozeAction()
        }
    }
    
    /**
     * Handle "Take" button click.
     */
    private fun handleTakeAction() {
        Log.d(TAG, "Take button clicked")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DoseCertaDatabase.getDatabase(this@AlarmActivity)
                val logDao = database.medicationLogDao()
                val alarmScheduler = AlarmScheduler(this@AlarmActivity)
                
                // Cancel missed reminder alarm
                alarmScheduler.cancelMissedReminderAlarm(medicationId, scheduleId, scheduledTime)
                
                // Update or insert log
                val existingLog = logDao.getLog(medicationId, scheduleId, scheduledTime)
                val currentTime = System.currentTimeMillis()
                
                if (existingLog != null) {
                    logDao.update(
                        existingLog.copy(
                            status = MedicationStatus.TAKEN,
                            actualTime = currentTime
                        )
                    )
                } else {
                    logDao.insert(
                        MedicationLog(
                            medicationId = medicationId,
                            scheduleId = scheduleId,
                            scheduledTime = scheduledTime,
                            actualTime = currentTime,
                            status = MedicationStatus.TAKEN
                        )
                    )
                }
                
                withContext(Dispatchers.Main) {
                    stopAlarmAndFinish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling take action", e)
                withContext(Dispatchers.Main) {
                    stopAlarmAndFinish()
                }
            }
        }
    }
    
    /**
     * Handle "Skip" button click.
     */
    private fun handleSkipAction() {
        Log.d(TAG, "Skip button clicked")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DoseCertaDatabase.getDatabase(this@AlarmActivity)
                val logDao = database.medicationLogDao()
                val alarmScheduler = AlarmScheduler(this@AlarmActivity)
                
                // B2: Schedule missed reminder — deliberate skip still warrants a follow-up
                val reminderHours = SettingsPreferences(this@AlarmActivity).getMissedReminderHoursSync()
                alarmScheduler.scheduleMissedReminderAlarm(medicationId, scheduleId, scheduledTime, reminderHours)
                Log.d(TAG, "Scheduled missed reminder after skip for $reminderHours hours")
                
                // Update or insert log
                val existingLog = logDao.getLog(medicationId, scheduleId, scheduledTime)
                val currentTime = System.currentTimeMillis()
                
                if (existingLog != null) {
                    logDao.update(
                        existingLog.copy(
                            status = MedicationStatus.SKIPPED,
                            actualTime = currentTime
                        )
                    )
                } else {
                    logDao.insert(
                        MedicationLog(
                            medicationId = medicationId,
                            scheduleId = scheduleId,
                            scheduledTime = scheduledTime,
                            actualTime = currentTime,
                            status = MedicationStatus.SKIPPED
                        )
                    )
                }
                
                withContext(Dispatchers.Main) {
                    stopAlarmAndFinish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling skip action", e)
                withContext(Dispatchers.Main) {
                    stopAlarmAndFinish()
                }
            }
        }
    }
    
    /**
     * Handle "Snooze" button click.
     */
    private fun handleSnoozeAction() {
        Log.d(TAG, "Snooze button clicked")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmScheduler = AlarmScheduler(this@AlarmActivity)
                
                // Snooze alarm for 10 minutes
                alarmScheduler.snoozeAlarm(medicationId, scheduleId, scheduledTime)
                
                withContext(Dispatchers.Main) {
                    stopAlarmAndFinish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling snooze action", e)
                withContext(Dispatchers.Main) {
                    stopAlarmAndFinish()
                }
            }
        }
    }
    
    /**
     * Stop alarm service and finish activity.
     */
    private fun stopAlarmAndFinish() {
        // Stop the alarm service
        AlarmService.stopAlarm(this)
        
        // Cancel notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (medicationId * 1000 + scheduleId).toInt()
        notificationManager.cancel(notificationId)
        notificationManager.cancel(AlarmService.FOREGROUND_NOTIFICATION_ID)
        
        // Finish activity
        finish()
    }
    
    /**
     * Get localized pharmaceutical form string.
     */
    private fun getFormString(form: String): String {
        return when (form) {
            "TABLET" -> getString(R.string.form_tablet)
            "CAPSULE" -> getString(R.string.form_capsule)
            "SYRUP" -> getString(R.string.form_syrup)
            "DROPS" -> getString(R.string.form_drops)
            "INJECTION" -> getString(R.string.form_injection)
            "CREAM" -> getString(R.string.form_cream)
            "SPRAY" -> getString(R.string.form_spray)
            else -> getString(R.string.form_other)
        }
    }
    
    override fun onBackPressed() {
        // Prevent back button from dismissing alarm
        // User must interact with one of the action buttons
        Log.d(TAG, "Back button pressed - ignoring")
    }
}
