package com.dosecerta.alarm

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
 *
 * Interaction model (Feature 5):
 * - Take → SwipeToConfirmView (horizontal drag to right edge)
 * - Skip → Two-step inline tap with 4s revert timeout
 * - Snooze → Hold-and-slide: press to expand, drag to select minutes, release to confirm
 */
class AlarmActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlarmActivity"
        private const val SKIP_CONFIRM_TIMEOUT_MS = 4000L
    }

    private lateinit var binding: ActivityAlarmBinding
    private var medication: Medication? = null
    private var medicationId: Long = -1L
    private var scheduleId: Long = -1L
    private var scheduledTime: Long = 0L

    // ─── Skip two-step state ─────────────────────────────────────────────────
    private var skipStep = 0
    private val skipTimeoutHandler = Handler(Looper.getMainLooper())
    private val skipRevertRunnable = Runnable { revertSkipButton() }

    // ─── Snooze hold-and-slide state ────────────────────────────────────────
    private var snoozeMinutes = Constants.SNOOZE_DURATION_MINUTES
    private var isHoldingSnooze = false
    private var snoozeStartRawX = 0f
    private var snoozeSliderExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupWindowFlags()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        displayMedicationInfo()
        setupInteractions()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Window / lockscreen setup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Setup window flags to show over lockscreen.
     * Uses aggressive flags for Xiaomi/MIUI/HyperOS compatibility.
     * DO NOT REMOVE ANY FLAG — each one is required for a subset of devices.
     */
    @Suppress("DEPRECATION")
    private fun setupWindowFlags() {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { Log.d(TAG, "Keyguard dismissed") }
                override fun onDismissError() { Log.e(TAG, "Keyguard dismiss error") }
                override fun onDismissCancelled() { Log.w(TAG, "Keyguard dismiss cancelled") }
            })
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data
    // ─────────────────────────────────────────────────────────────────────────

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
        Log.d(TAG, "Alarm opened for: ${medication?.name}")
    }

    private fun displayMedicationInfo() {
        medication?.let { med ->
            binding.textMedicationName.text = med.name
            binding.textDosageInfo.text = "${med.dosage} ${med.unit} · ${getFormString(med.pharmaceuticalForm.name)}"

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.textScheduledTime.text = timeFormat.format(Date(scheduledTime))

            // C10: Tint the pill icon with the medication's stored color
            binding.imageMedIcon.setColorFilter(med.color, PorterDuff.Mode.SRC_IN)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Interactions setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupInteractions() {
        // C7: Swipe-to-confirm → Take
        binding.swipeTake.onConfirmed = {
            handleTakeAction()
        }

        // C8: Two-step skip
        binding.btnAlarmSkip.setOnClickListener {
            when (skipStep) {
                0 -> {
                    // First tap → morph to confirmation state
                    skipStep = 1
                    animateSkipToConfirmState()
                    skipTimeoutHandler.postDelayed(skipRevertRunnable, SKIP_CONFIRM_TIMEOUT_MS)
                }
                1 -> {
                    // Second tap → execute skip
                    skipTimeoutHandler.removeCallbacks(skipRevertRunnable)
                    handleSkipAction()
                }
            }
        }

        // C9: Hold-and-slide snooze
        setupSnoozeHoldSlide()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Skip two-step animation
    // ─────────────────────────────────────────────────────────────────────────

    private fun animateSkipToConfirmState() {
        binding.btnAlarmSkip.text = getString(R.string.alarm_skip_confirm)
        binding.btnAlarmSkip.animate()
            .alpha(0f)
            .setDuration(100)
            .withEndAction {
                binding.btnAlarmSkip.setBackgroundColor(
                    getColor(R.color.skip_confirm_bg)
                )
                binding.btnAlarmSkip.animate().alpha(1f).setDuration(150).start()
            }
            .start()
    }

    private fun revertSkipButton() {
        skipStep = 0
        binding.btnAlarmSkip.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                binding.btnAlarmSkip.text = getString(R.string.notification_action_skip)
                // Reset background by re-applying the outlined button style programmatically
                binding.btnAlarmSkip.setBackgroundColor(
                    android.graphics.Color.TRANSPARENT
                )
                binding.btnAlarmSkip.animate().alpha(1f).setDuration(150).start()
            }
            .start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Snooze hold-and-slide
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupSnoozeHoldSlide() {
        binding.btnAlarmSnooze.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHoldingSnooze = true
                    snoozeStartRawX = event.rawX
                    snoozeMinutes = Constants.SNOOZE_DURATION_MINUTES
                    expandSnoozeSlider()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isHoldingSnooze) {
                        val deltaX = event.rawX - snoozeStartRawX
                        updateSnoozeFromDrag(deltaX)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isHoldingSnooze) {
                        isHoldingSnooze = false
                        collapseSnoozeSlider {
                            handleSnoozeAction(snoozeMinutes)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun expandSnoozeSlider() {
        updateSnoozeLabel()
        binding.snoozeSliderOverlay.visibility = View.VISIBLE
        binding.snoozeSliderOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        snoozeSliderExpanded = true
    }

    private fun collapseSnoozeSlider(onEnd: () -> Unit) {
        binding.snoozeSliderOverlay.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                binding.snoozeSliderOverlay.visibility = View.GONE
                snoozeSliderExpanded = false
                onEnd()
            }
            .start()
    }

    /**
     * Map horizontal drag delta to snooze options array index.
     * Full left = index 0 (5 min), full right = last index (60 min).
     * Slider range is the width of the snooze FrameLayout.
     */
    private fun updateSnoozeFromDrag(deltaX: Float) {
        val options = Constants.SNOOZE_OPTIONS_MINUTES
        val sliderWidth = binding.frameSnooze.width.toFloat().coerceAtLeast(1f)
        // Normalize delta to [-0.5, +0.5] around center, then map to options
        val normalized = (deltaX / sliderWidth + 0.5f).coerceIn(0f, 1f)
        val index = (normalized * (options.size - 1)).toInt().coerceIn(0, options.size - 1)
        snoozeMinutes = options[index]
        updateSnoozeLabel()
    }

    private fun updateSnoozeLabel() {
        binding.textSnoozeValue.text = if (snoozeMinutes == 60) {
            getString(R.string.alarm_snooze_1h)
        } else {
            getString(R.string.alarm_snooze_format, snoozeMinutes)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action handlers (DB writes — same logic as before, preserved exactly)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handle Take action — called by SwipeToConfirmView.onConfirmed.
     */
    private fun handleTakeAction() {
        Log.d(TAG, "Take confirmed via swipe")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DoseCertaDatabase.getDatabase(this@AlarmActivity)
                val logDao = database.medicationLogDao()
                val alarmScheduler = AlarmScheduler(this@AlarmActivity)

                // Cancel missed reminder — user confirmed they took it
                alarmScheduler.cancelMissedReminderAlarm(medicationId, scheduleId, scheduledTime)

                val existingLog = logDao.getLog(medicationId, scheduleId, scheduledTime)
                val currentTime = System.currentTimeMillis()

                if (existingLog != null) {
                    logDao.update(existingLog.copy(status = MedicationStatus.TAKEN, actualTime = currentTime))
                } else {
                    logDao.insert(MedicationLog(medicationId = medicationId, scheduleId = scheduleId, scheduledTime = scheduledTime, actualTime = currentTime, status = MedicationStatus.TAKEN))
                }

                withContext(Dispatchers.Main) { stopAlarmAndFinish() }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling take action", e)
                withContext(Dispatchers.Main) { stopAlarmAndFinish() }
            }
        }
    }

    /**
     * Handle Skip action — called after two-step confirmation.
     */
    private fun handleSkipAction() {
        Log.d(TAG, "Skip confirmed (two-step)")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DoseCertaDatabase.getDatabase(this@AlarmActivity)
                val logDao = database.medicationLogDao()
                val alarmScheduler = AlarmScheduler(this@AlarmActivity)

                // B2: Schedule missed reminder — deliberate skip still warrants a follow-up
                val reminderHours = SettingsPreferences(this@AlarmActivity).getMissedReminderHoursSync()
                alarmScheduler.scheduleMissedReminderAlarm(medicationId, scheduleId, scheduledTime, reminderHours)

                val existingLog = logDao.getLog(medicationId, scheduleId, scheduledTime)
                val currentTime = System.currentTimeMillis()

                if (existingLog != null) {
                    logDao.update(existingLog.copy(status = MedicationStatus.SKIPPED, actualTime = currentTime))
                } else {
                    logDao.insert(MedicationLog(medicationId = medicationId, scheduleId = scheduleId, scheduledTime = scheduledTime, actualTime = currentTime, status = MedicationStatus.SKIPPED))
                }

                withContext(Dispatchers.Main) { stopAlarmAndFinish() }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling skip action", e)
                withContext(Dispatchers.Main) { stopAlarmAndFinish() }
            }
        }
    }

    /**
     * Handle Snooze action with variable minutes from hold-and-slide.
     */
    private fun handleSnoozeAction(minutes: Int) {
        Log.d(TAG, "Snooze for $minutes minutes")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmScheduler = AlarmScheduler(this@AlarmActivity)
                // C2: Pass selected minutes instead of hardcoded constant
                alarmScheduler.snoozeAlarm(medicationId, scheduleId, scheduledTime, minutes)
                withContext(Dispatchers.Main) { stopAlarmAndFinish() }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling snooze action", e)
                withContext(Dispatchers.Main) { stopAlarmAndFinish() }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Stop alarm service and finish activity.
     */
    private fun stopAlarmAndFinish() {
        // Cancel pending skip timeout to prevent post-dismiss callback
        skipTimeoutHandler.removeCallbacksAndMessages(null)

        AlarmService.stopAlarm(this)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (medicationId * 1000 + scheduleId).toInt()
        notificationManager.cancel(notificationId)
        notificationManager.cancel(AlarmService.FOREGROUND_NOTIFICATION_ID)

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // C12: Clean up handler to prevent memory leaks
        skipTimeoutHandler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing alarm
        Log.d(TAG, "Back button pressed — ignored")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun getFormString(form: String): String {
        return when (form) {
            "TABLET"    -> getString(R.string.form_tablet)
            "CAPSULE"   -> getString(R.string.form_capsule)
            "SYRUP"     -> getString(R.string.form_syrup)
            "DROPS"     -> getString(R.string.form_drops)
            "INJECTION" -> getString(R.string.form_injection)
            "CREAM"     -> getString(R.string.form_cream)
            "SPRAY"     -> getString(R.string.form_spray)
            else        -> getString(R.string.form_other)
        }
    }
}
